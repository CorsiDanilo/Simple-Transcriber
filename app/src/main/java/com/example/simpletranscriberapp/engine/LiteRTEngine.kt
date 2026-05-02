package com.example.simpletranscriberapp.engine

import android.content.Context
import java.io.File
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

/**
 * Engine di trascrizione che usa LiteRT-LM per l'inferenza on-device
 * con modelli scaricati dall'utente (es. Gemma 4 E2B).
 *
 * Gestisce automaticamente:
 * - Preprocessing audio (conversione a 16kHz mono PCM)
 * - Splitting audio lungo (> 30s) in segmenti
 * - Concatenazione risultati dei segmenti
 * - Reporting del progresso via callback
 */
class LiteRTEngine(
    private val context: Context,
    val modelPath: String?,
    private val modelDisplayName: String = "Local Model"
) : TranscriptionEngine {

    private val audioPreprocessor = AudioPreprocessor()
    private var engine: Engine? = null
    private val engineMutex = Mutex()
    private val TAG = "LiteRTEngine"

    private suspend fun getOrInitEngine(onProgress: (String) -> Unit): Engine {
        return engine ?: engineMutex.withLock {
            engine ?: run {
                onProgress("Initializing engine & optimizing 976 layers... this may take up to 60s")
                Log.d(TAG, "Starting engine initialization for model: $modelPath")
                
                // Smart cache clearing: only delete if something went wrong or cache is tiny
                try {
                    val modelFile = File(modelPath!!)
                    val modelDir = modelFile.parentFile
                    if (modelDir?.exists() == true) {
                        val cacheFiles = modelDir.listFiles { _, name -> 
                            name.contains(".xnnpack_cache_") 
                        }
                        cacheFiles?.forEach { 
                            if (it.length() < 1024 * 1024) { // Less than 1MB is likely a failed/partial cache
                                Log.d(TAG, "Deleting potentially corrupted XNNPack cache: ${it.name} (${it.length()} bytes)")
                                it.delete() 
                            } else {
                                Log.d(TAG, "Keeping existing XNNPack cache: ${it.name} (${it.length() / 1024 / 1024} MB)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to manage XNNPack cache", e)
                }
                
                // Aggressive memory cleanup for large 8-bit/4B models
                Log.d(TAG, "Preparing memory for large model load...")
                Runtime.getRuntime().gc()
                Thread.sleep(100)
                System.gc()
                
                val config = EngineConfig(
                    modelPath = modelPath!!,
                    backend = Backend.CPU(), // You could try Backend.GPU() if the device is high-end
                    audioBackend = Backend.CPU()
                )
                Log.d(TAG, "Engine config created with CPU backend + audioBackend=CPU")
                
                try {
                    withContext(Dispatchers.Default) {
                        Engine(config).also { 
                            it.initialize()
                            Log.d(TAG, "Engine initialized successfully")
                            engine = it 
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize LiteRT-LM engine", e)
                    throw e
                }
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
        if (modelPath == null) {
            return TranscriptionResult.Error("No model selected. Please download and select a model first.")
        }

        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            return TranscriptionResult.Error("Model file not found at: $modelPath")
        }

        return try {
            // Step 1: Preprocessing
            onProgress("Preparing audio...")
            Log.d(TAG, "Input audio size: ${audioBytes.size} bytes, mime: $mimeType")
            val pcmBytes = audioPreprocessor.decodeToRawPcm(audioBytes, mimeType)
            Log.d(TAG, "Decoded PCM size: ${pcmBytes.size} bytes")
            
            val segments = audioPreprocessor.splitIntoSegments(pcmBytes)
            Log.d(TAG, "Split into ${segments.size} segments")

            val totalSegments = segments.size
            val results = mutableListOf<String>()

            // Step 2: Trascrivi ogni segmento
            for ((index, segment) in segments.withIndex()) {
                if (totalSegments > 1) {
                    onProgress("Transcribing segment ${index + 1}/$totalSegments...")
                } else {
                    onProgress("Transcribing...")
                }

                val segmentResult = transcribeSegment(segment, language, onProgress)
                when (segmentResult) {
                    is TranscriptionResult.Success -> {
                        results.add(segmentResult.text)
                        onPartialText(results.joinToString(" "))
                    }
                    is TranscriptionResult.Error -> {
                        if (totalSegments > 1) {
                            // Per audio multi-segmento, segnala l'errore ma continua
                            onProgress("⚠️ Segment ${index + 1} failed, continuing...")
                        } else {
                            return segmentResult
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                TranscriptionResult.Error("All segments failed to transcribe.")
            } else {
                TranscriptionResult.Success(results.joinToString(" "))
            }
        } catch (e: Exception) {
            TranscriptionResult.Error("Local transcription error: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Trascrizione di un singolo segmento PCM usando LiteRT-LM.
     *
     * TODO: Questa è l'implementazione placeholder. L'integrazione reale richiede:
     * 1. Inizializzare l'Engine LiteRT-LM con il modello
     * 2. Creare una sessione conversazionale
     * 3. Inviare l'audio con il prompt di trascrizione
     * 4. Raccogliere la risposta in streaming
     *
     * L'implementazione finale dipende dalla versione esatta dell'SDK LiteRT-LM
     * e dal formato del modello scaricato.
     */
    private suspend fun transcribeSegment(
        pcmBytes: ByteArray,
        language: String,
        onProgress: (String) -> Unit
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        try {
            val litertEngine = getOrInitEngine(onProgress)
            
            litertEngine.createConversation().use { conversation ->
                // Crea il contenuto multimodale usando il formato Gemma 4 (<|audio|>)
                // Crea il contenuto multimodale. 
                // NOTA: Con l'SDK LiteRT-LM e Gemma 4, l'aggiunta di Content.AudioBytes 
                // solitamente associa automaticamente l'audio alla richiesta.
                // Rimuoviamo il tag <|audio|> esplicito dal testo per evitare che il modello
                // si aspetti un secondo input audio (causando l'errore "Provided less audio").
                val wavData = audioPreprocessor.addWavHeader(pcmBytes)
                Log.d(TAG, "Sending segment: ${pcmBytes.size} bytes (with WAV header: ${wavData.size} bytes)")
                
                val contents = listOf(
                    Content.AudioBytes(wavData),
                    Content.Text("Transcribe the audio. Output ONLY the transcript text. Language: $language")
                )
                
                Log.d(TAG, "Sending multimodal message to model...")
                val message = Message.user(Contents.of(contents))
                val response = conversation.sendMessage(message)
                Log.d(TAG, "Response received from model")
                
                // Estraiamo il testo dai contenuti (Contents.contents è una List<Content>)
                val resultText = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                
                TranscriptionResult.Success(resultText.trim())
            }
        } catch (e: Exception) {
            TranscriptionResult.Error("LiteRT-LM error: ${e.localizedMessage ?: e.message}")
        }
    }

    override fun isAvailable(): Boolean {
        if (modelPath == null) return false
        return java.io.File(modelPath).exists()
    }

    override fun displayName(): String = modelDisplayName

    override suspend fun refineText(
        text: String, 
        language: String,
        onPartialText: (String) -> Unit
    ): String = withContext(Dispatchers.Default) {
        try {
            val litertEngine = getOrInitEngine { }
            litertEngine.createConversation().use { conversation ->
                val contents = listOf(
                    Content.Text("Fix the punctuation, syntax, and grammatical errors of the following transcribed text, keeping the original meaning intact. Respond ONLY with the corrected text in $language language:\n\n$text")
                )
                val message = Message.user(Contents.of(contents))
                val response = conversation.sendMessage(message)
                
                val resultText = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                
                val refined = resultText.trim()
                val finalRefined = if (refined.isNotBlank()) refined else text
                onPartialText(finalRefined)
                finalRefined
            }
        } catch (e: Exception) {
            text
        }
    }
}
