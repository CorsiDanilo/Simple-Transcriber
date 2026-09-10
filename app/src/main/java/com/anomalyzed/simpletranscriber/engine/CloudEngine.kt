package com.anomalyzed.simpletranscriber.engine

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Engine di trascrizione che usa l'API cloud di Google (Gemini)
 * con supporto al fallback automatico trasparente tra modelli ordinati dal più recente al meno recente.
 */
class CloudEngine(
    private val apiKey: String,
    private val modelName: String = "gemini-2.5-flash",
    private val fallbackModels: List<String> = emptyList()
) : TranscriptionEngine {

    var lastSuccessfulModel: String? = null
        private set

    companion object {
        private val versionRegex = Regex("(?:gemini|gemma)-?(\\d+(?:\\.\\d+)?)")

        /**
         * Ordina i modelli semanticamente dal più recente al meno recente (Latest-First):
         * 1. 'latest' in cima
         * 2. Versione numerica decrescente (es. 2.5 > 2.0 > 1.5 > 1.0)
         * 3. Brand: Gemini prima di Gemma
         * 4. Flavor: Flash prima di Pro
         * 5. Nome inverso per tie-breaking
         */
        fun sortModelsNewestFirst(models: List<String>): List<String> {
            return models.distinct().sortedWith { a, b ->
                val aName = a.lowercase()
                val bName = b.lowercase()

                val aLatest = if (aName.contains("latest")) 0 else 1
                val bLatest = if (bName.contains("latest")) 0 else 1
                if (aLatest != bLatest) return@sortedWith aLatest.compareTo(bLatest)

                val aVer = extractVersion(aName)
                val bVer = extractVersion(bName)
                if (aVer != bVer) return@sortedWith bVer.compareTo(aVer)

                val aBrand = if (aName.contains("gemini")) 1 else 2
                val bBrand = if (bName.contains("gemini")) 1 else 2
                if (aBrand != bBrand) return@sortedWith aBrand.compareTo(bBrand)

                val aFlavor = when {
                    aName.contains("flash") -> 1
                    aName.contains("pro") -> 2
                    else -> 3
                }
                val bFlavor = when {
                    bName.contains("flash") -> 1
                    bName.contains("pro") -> 2
                    else -> 3
                }
                if (aFlavor != bFlavor) return@sortedWith aFlavor.compareTo(bFlavor)

                bName.compareTo(aName)
            }
        }

        private fun extractVersion(name: String): Double {
            val match = versionRegex.find(name)
            if (match != null) {
                val valStr = match.groupValues[1]
                val fullMatch = match.value
                val idx = name.indexOf(fullMatch) + fullMatch.length
                if (idx < name.length && name[idx] == 'b') {
                    return 1.0
                }
                return valStr.toDoubleOrNull() ?: 1.0
            }
            return 1.0
        }
    }

    /**
     * Costruisce la sequenza di modelli candidati per il fallback:
     * 1. Il modello selezionato dall'utente (se specificato)
     * 2. Tutti gli altri modelli ordinati dal più recente al meno recente
     */
    private val candidateModels: List<String> by lazy {
        val selected = modelName.trim()
        val otherCandidates = mutableListOf<String>()

        for (m in fallbackModels) {
            val clean = m.trim()
            if (clean.isNotBlank() && clean != selected && !otherCandidates.contains(clean)) {
                otherCandidates.add(clean)
            }
        }

        val defaultFallbacks = listOf(
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-flash-lite-latest",
            "gemini-2.5-pro",
            "gemini-1.5-pro"
        )
        for (fallback in defaultFallbacks) {
            if (fallback != selected && !otherCandidates.contains(fallback)) {
                otherCandidates.add(fallback)
            }
        }

        val sortedOthers = sortModelsNewestFirst(otherCandidates)

        val result = mutableListOf<String>()
        if (selected.isNotBlank()) {
            result.add(selected)
        }
        for (m in sortedOthers) {
            if (!result.contains(m)) {
                result.add(m)
            }
        }
        result
    }

    private fun isEnglish(language: String): Boolean {
        val lang = language.trim().lowercase()
        return lang.startsWith("en") || lang.contains("ing") || (!lang.startsWith("it") && Locale.getDefault().language != "it")
    }

    private fun summarizeError(e: Throwable, isEn: Boolean): String {
        val msg = e.message ?: e.localizedMessage ?: if (isEn) "Unknown error" else "Errore sconosciuto"
        return when {
            msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || msg.contains("Quota", ignoreCase = true) ->
                if (isEn) "Quota exceeded / Rate limit (429)" else "Quota esaurita / Rate limit (429)"
            msg.contains("500") || msg.contains("INTERNAL", ignoreCase = true) ->
                if (isEn) "Google server error (500)" else "Errore server Google (500)"
            msg.contains("503") || msg.contains("UNAVAILABLE", ignoreCase = true) ->
                if (isEn) "Service unavailable (503)" else "Servizio non disponibile (503)"
            msg.contains("404") || msg.contains("NOT_FOUND", ignoreCase = true) ->
                if (isEn) "Model not supported (404)" else "Modello non supportato (404)"
            else -> {
                val prefix = if (isEn) "API error: " else "Errore API: "
                val firstLine = msg.lineSequence().firstOrNull()?.trim() ?: if (isEn) "API error" else "Errore API"
                val truncated = if (firstLine.length > 45) firstLine.take(45) + "..." else firstLine
                if (truncated.startsWith("Errore", ignoreCase = true) || truncated.startsWith("Error", ignoreCase = true)) truncated else prefix + truncated
            }
        }
    }

    override suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult {
        val isEn = isEnglish(language)
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error(if (isEn) "API Key is missing. Please enter it first." else "Chiave API mancante. Inseriscila prima di procedere.")
        }

        val promptText = if (isEn) {
            "Transcribe this audio faithfully and translate it into $language. " +
            "Then refine the transcript by fixing punctuation, syntax, grammar, and obvious transcription errors " +
            "without changing the original meaning, structure, or tone. " +
            "Respond ONLY with the final refined text in $language language, no introductory or concluding sentences."
        } else {
            "Trascrivi fedelmente questo audio e traducilo in lingua $language. " +
            "Quindi perfeziona la trascrizione correggendo punteggiatura, sintassi, grammatica ed evidenti errori di trascrizione " +
            "senza alterare il significato, la struttura o il tono originale. " +
            "Rispondi SOLO con il testo finale perfezionato in lingua $language, senza alcuna frase introduttiva o conclusiva."
        }

        val requestContent = content {
            blob(mimeType, audioBytes)
            text(promptText)
        }

        var lastError: Exception? = null

        for (i in candidateModels.indices) {
            val currentModel = candidateModels[i]
            var text = ""
            var receivedAnyChunk = false
            try {
                Log.i("CloudEngine", "Attempting transcribe with model: '$currentModel' (${i + 1}/${candidateModels.size})")
                val generativeModel = GenerativeModel(
                    modelName = currentModel,
                    apiKey = apiKey
                )

                val responseStream = generativeModel.generateContentStream(requestContent)
                responseStream.collect { chunk ->
                    val chunkText = chunk.text ?: ""
                    if (chunkText.isNotEmpty()) {
                        receivedAnyChunk = true
                        text += chunkText
                        onPartialText(text)
                    }
                }
                text = text.trim()

                if (receivedAnyChunk && text.isNotBlank()) {
                    lastSuccessfulModel = currentModel
                    return TranscriptionResult.Success(text)
                } else {
                    throw IllegalStateException(if (isEn) "Google API returned no text. Check audio volume or content." else "L'API Google non ha restituito testo. Controlla il volume dell'audio.")
                }
            } catch (e: Exception) {
                lastError = e
                Log.w("CloudEngine", "Gemini query error with model '$currentModel': ${e.message}")

                val errStr = e.message ?: ""
                if (errStr.contains("API_KEY_INVALID", ignoreCase = true) || errStr.contains("API key not valid", ignoreCase = true)) {
                    val keyErr = if (isEn) "Invalid API Key: ${e.localizedMessage}" else "Chiave API non valida: ${e.localizedMessage}"
                    return TranscriptionResult.Error(keyErr)
                }

                if (i + 1 < candidateModels.size) {
                    val nextModel = candidateModels[i + 1]
                    val errSummary = summarizeError(e, isEn)
                    val retryMsg = if (isEn) {
                        "Model '$currentModel' unavailable ($errSummary). Retrying with '$nextModel'..."
                    } else {
                        "Modello '$currentModel' non disponibile ($errSummary). Tentativo con '$nextModel' in corso..."
                    }
                    onProgress(retryMsg)
                    delay(300)
                    continue
                }
            }
        }

        val errDetail = lastError?.localizedMessage ?: lastError?.message ?: if (isEn) "All candidate models failed." else "Tutti i modelli candidati hanno fallito."
        val finalErrMsg = if (isEn) {
            "All candidate Gemini models failed. Last error: $errDetail"
        } else {
            "Tutti i modelli Gemini hanno fallito. Ultimo errore: $errDetail"
        }
        return TranscriptionResult.Error(finalErrMsg)
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    override fun displayName(): String = "Cloud (${lastSuccessfulModel ?: modelName})"

    override fun performsRefinementDuringTranscription(): Boolean = true

    override suspend fun refineText(
        text: String, 
        language: String,
        onPartialText: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val isEn = isEnglish(language)
        if (apiKey.isBlank()) return@withContext text

        val prompt = if (isEn) {
            "Fix the punctuation, syntax, and grammatical errors of the following transcribed text, keeping the original meaning intact. Respond ONLY with the corrected text in $language language:\n\n$text"
        } else {
            "Correggi punteggiatura, sintassi ed errori grammaticali del seguente testo trascritto, mantenendo intatto il significato originale. Rispondi SOLO con il testo corretto in lingua $language:\n\n$text"
        }

        for (i in candidateModels.indices) {
            val currentModel = candidateModels[i]
            try {
                Log.i("CloudEngine", "Attempting refineText with model: '$currentModel' (${i + 1}/${candidateModels.size})")
                val generativeModel = GenerativeModel(
                    modelName = currentModel,
                    apiKey = apiKey
                )
                val responseStream = generativeModel.generateContentStream(prompt)
                var refinedText = ""
                var receivedAnyChunk = false
                responseStream.collect { chunk ->
                    val chunkText = chunk.text ?: ""
                    if (chunkText.isNotEmpty()) {
                        receivedAnyChunk = true
                        refinedText += chunkText
                        onPartialText(refinedText)
                    }
                }
                val refined = refinedText.trim()
                if (receivedAnyChunk && refined.isNotBlank()) {
                    lastSuccessfulModel = currentModel
                    return@withContext refined
                }
            } catch (e: Exception) {
                Log.w("CloudEngine", "Gemini refineText error with model '$currentModel': ${e.message}")
                val errStr = e.message ?: ""
                if (errStr.contains("API_KEY_INVALID", ignoreCase = true) || errStr.contains("API key not valid", ignoreCase = true)) {
                    return@withContext text
                }
                if (i + 1 < candidateModels.size) {
                    delay(300)
                    continue
                }
            }
        }
        text
    }

    override suspend fun summarizeAudio(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult {
        val isEn = isEnglish(language)
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error(if (isEn) "API Key is missing. Please enter it first." else "Chiave API mancante. Inseriscila prima di procedere.")
        }

        val promptText = if (isEn) {
            "You are a summarization assistant. Analyze this audio and provide the structured summary directly with the most relevant key points in bullet points.\n" +
            "STRICT RULES:\n" +
            "1. Start IMMEDIATELY and EXCLUSIVELY with the summary content or bullet points.\n" +
            "2. It is STRICTLY FORBIDDEN to include introductory sentences, greetings, or preambles (do NOT write 'Here is the summary...', 'Certainly', 'Below is...', 'In summary:').\n" +
            "3. No concluding remarks or final comments.\n" +
            "4. Respond in $language language."
        } else {
            "Sei un assistente per riassunti. Analizza questo audio e fornisci direttamente il riassunto strutturato con i punti chiave più rilevanti in elenco puntato.\n" +
            "REGOLE FERREE:\n" +
            "1. Inizia SUBITO ed ESCLUSIVAMENTE con il contenuto del riassunto o i punti elenco.\n" +
            "2. È SEVERAMENTE VIETATO inserire frasi introduttive, saluti o preamboli (vietato scrivere 'Ecco il riassunto...', 'Ecco una sintesi', 'Certamente', 'Di seguito...', 'In sintesi:').\n" +
            "3. Nessuna conclusione o commento finale.\n" +
            "4. Rispondi in lingua $language."
        }

        val requestContent = content {
            blob(mimeType, audioBytes)
            text(promptText)
        }

        var lastError: Exception? = null

        for (i in candidateModels.indices) {
            val currentModel = candidateModels[i]
            var text = ""
            var receivedAnyChunk = false
            try {
                Log.i("CloudEngine", "Attempting summarizeAudio with model: '$currentModel' (${i + 1}/${candidateModels.size})")
                val generativeModel = GenerativeModel(
                    modelName = currentModel,
                    apiKey = apiKey
                )

                val responseStream = generativeModel.generateContentStream(requestContent)
                responseStream.collect { chunk ->
                    val chunkText = chunk.text ?: ""
                    if (chunkText.isNotEmpty()) {
                        receivedAnyChunk = true
                        text += chunkText
                        onPartialText(cleanSummaryText(text))
                    }
                }
                text = cleanSummaryText(text)

                if (receivedAnyChunk && text.isNotBlank()) {
                    lastSuccessfulModel = currentModel
                    return TranscriptionResult.Success(text)
                } else {
                    throw IllegalStateException(if (isEn) "Google API returned no text. Check audio volume or content." else "L'API Google non ha restituito testo. Controlla il volume dell'audio.")
                }
            } catch (e: Exception) {
                lastError = e
                Log.w("CloudEngine", "Gemini summarizeAudio error with model '$currentModel': ${e.message}")

                val errStr = e.message ?: ""
                if (errStr.contains("API_KEY_INVALID", ignoreCase = true) || errStr.contains("API key not valid", ignoreCase = true)) {
                    val keyErr = if (isEn) "Invalid API Key: ${e.localizedMessage}" else "Chiave API non valida: ${e.localizedMessage}"
                    return TranscriptionResult.Error(keyErr)
                }

                if (i + 1 < candidateModels.size) {
                    val nextModel = candidateModels[i + 1]
                    val errSummary = summarizeError(e, isEn)
                    val retryMsg = if (isEn) {
                        "Model '$currentModel' unavailable ($errSummary). Retrying with '$nextModel'..."
                    } else {
                        "Modello '$currentModel' non disponibile ($errSummary). Tentativo con '$nextModel' in corso..."
                    }
                    onProgress(retryMsg)
                    delay(300)
                    continue
                }
            }
        }

        val errDetail = lastError?.localizedMessage ?: lastError?.message ?: if (isEn) "All candidate models failed." else "Tutti i modelli candidati hanno fallito."
        val finalErrMsg = if (isEn) {
            "All candidate Gemini models failed. Last error: $errDetail"
        } else {
            "Tutti i modelli Gemini hanno fallito. Ultimo errore: $errDetail"
        }
        return TranscriptionResult.Error(finalErrMsg)
    }

    override suspend fun summarizeText(
        text: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult {
        val isEn = isEnglish(language)
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error(if (isEn) "API Key is missing. Please enter it first." else "Chiave API mancante. Inseriscila prima di procedere.")
        }

        val prompt = if (isEn) {
            "You are a summarization assistant. Carefully read the following transcribed text and provide the structured summary directly with the most relevant key points in bullet points.\n" +
            "STRICT RULES:\n" +
            "1. Start IMMEDIATELY and EXCLUSIVELY with the summary content or bullet points.\n" +
            "2. It is STRICTLY FORBIDDEN to include introductory sentences, greetings, or preambles (do NOT write 'Here is the summary...', 'Certainly', 'Below is...', 'In summary:').\n" +
            "3. No concluding remarks or final comments.\n" +
            "4. Respond in $language language.\n\n" +
            "Text to summarize:\n$text"
        } else {
            "Sei un assistente per riassunti. Leggi attentamente il seguente testo trascritto e fornisci direttamente il riassunto strutturato con i punti chiave più rilevanti in elenco puntato.\n" +
            "REGOLE FERREE:\n" +
            "1. Inizia SUBITO ed ESCLUSIVAMENTE con il contenuto del riassunto o i punti elenco.\n" +
            "2. È SEVERAMENTE VIETATO inserire frasi introduttive, saluti o preamboli (vietato scrivere 'Ecco il riassunto...', 'Ecco una sintesi', 'Certamente', 'Di seguito...', 'In sintesi:').\n" +
            "3. Nessuna conclusione o commento finale.\n" +
            "4. Rispondi in lingua $language.\n\n" +
            "Testo da riassumere:\n$text"
        }

        var lastError: Exception? = null

        for (i in candidateModels.indices) {
            val currentModel = candidateModels[i]
            var resultText = ""
            var receivedAnyChunk = false
            try {
                Log.i("CloudEngine", "Attempting summarizeText with model: '$currentModel' (${i + 1}/${candidateModels.size})")
                val generativeModel = GenerativeModel(
                    modelName = currentModel,
                    apiKey = apiKey
                )

                val responseStream = generativeModel.generateContentStream(prompt)
                responseStream.collect { chunk ->
                    val chunkText = chunk.text ?: ""
                    if (chunkText.isNotEmpty()) {
                        receivedAnyChunk = true
                        resultText += chunkText
                        onPartialText(cleanSummaryText(resultText))
                    }
                }
                resultText = cleanSummaryText(resultText)

                if (receivedAnyChunk && resultText.isNotBlank()) {
                    lastSuccessfulModel = currentModel
                    return TranscriptionResult.Success(resultText)
                } else {
                    throw IllegalStateException(if (isEn) "Google API returned empty summary." else "L'API Google ha restituito un riassunto vuoto.")
                }
            } catch (e: Exception) {
                lastError = e
                Log.w("CloudEngine", "Gemini summarizeText error with model '$currentModel': ${e.message}")

                val errStr = e.message ?: ""
                if (errStr.contains("API_KEY_INVALID", ignoreCase = true) || errStr.contains("API key not valid", ignoreCase = true)) {
                    val keyErr = if (isEn) "Invalid API Key: ${e.localizedMessage}" else "Chiave API non valida: ${e.localizedMessage}"
                    return TranscriptionResult.Error(keyErr)
                }

                if (i + 1 < candidateModels.size) {
                    val nextModel = candidateModels[i + 1]
                    val errSummary = summarizeError(e, isEn)
                    val retryMsg = if (isEn) {
                        "Model '$currentModel' unavailable ($errSummary). Retrying with '$nextModel'..."
                    } else {
                        "Modello '$currentModel' non disponibile ($errSummary). Tentativo con '$nextModel' in corso..."
                    }
                    onProgress(retryMsg)
                    delay(300)
                    continue
                }
            }
        }

        val errDetail = lastError?.localizedMessage ?: lastError?.message ?: if (isEn) "All candidate models failed." else "Tutti i modelli candidati hanno fallito."
        val finalErrMsg = if (isEn) {
            "All candidate Gemini models failed. Last error: $errDetail"
        } else {
            "Tutti i modelli Gemini hanno fallito. Ultimo errore: $errDetail"
        }
        return TranscriptionResult.Error(finalErrMsg)
    }

    private fun cleanSummaryText(rawText: String): String {
        var cleaned = rawText.trim()
        val preambleRegex = Regex(
            """^(?:(?:Certamente|Sicuramente|Certo)[!,.]?\s*)?(?:Ecco|Di seguito|Questo è|Qui c['’]è|In sintesi|In breve|Here is|Below is|This is)\s+(?:il|un|la|una|i|le|lo|gli|a|the)?\s*(?:breve\s+)?(?:riassunto|sintesi|punti chiave|punti principali|panoramica|summary|key points)[^:\n]*:?\s*\n*""",
            RegexOption.IGNORE_CASE
        )
        cleaned = cleaned.replace(preambleRegex, "").trim()

        val singleLinePreambleRegex = Regex(
            """^(?:Ecco|Di seguito|Here is)\b[^:\n]*:\s*""",
            RegexOption.IGNORE_CASE
        )
        cleaned = cleaned.replace(singleLinePreambleRegex, "").trim()

        return cleaned
    }
}
