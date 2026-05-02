package com.example.simpletranscriberapp.engine

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

/**
 * Engine di trascrizione che usa l'API cloud di Gemini.
 * Refactor della logica originariamente inline nel TranscriberViewModel.
 */
class CloudEngine(
    private val apiKey: String,
    private val modelName: String = "gemini-flash-latest"
) : TranscriptionEngine {

    override suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error("API Key is missing. Please enter it first.")
        }

        return try {
            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )

            val requestContent = content {
                blob(mimeType, audioBytes)
                text(
                    "Transcribe this audio exactly word-for-word and translate it into $language. " +
                    "Respond ONLY with the text in $language language, no introductory or concluding sentences. " +
                    "Maintain the original structure and tone."
                )
            }

            val responseStream = generativeModel.generateContentStream(requestContent)
            var text = ""
            responseStream.collect { chunk ->
                text += chunk.text ?: ""
                onPartialText(text)
            }
            text = text.trim()

            if (text.isNotBlank()) {
                TranscriptionResult.Success(text)
            } else {
                TranscriptionResult.Error("Gemini returned no text. Check audio volume or content.")
            }
        } catch (e: Exception) {
            TranscriptionResult.Error(e.localizedMessage ?: "Cloud transcription failed.")
        }
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    override fun displayName(): String = "Cloud ($modelName)"

    override suspend fun refineText(
        text: String, 
        language: String,
        onPartialText: (String) -> Unit
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext text
        try {
            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )
            val responseStream = generativeModel.generateContentStream(
                "Fix the punctuation, syntax, and grammatical errors of the following transcribed text, keeping the original meaning intact. Respond ONLY with the corrected text in $language language:\n\n$text"
            )
            var refinedText = ""
            responseStream.collect { chunk ->
                refinedText += chunk.text ?: ""
                onPartialText(refinedText)
            }
            val refined = refinedText.trim()
            if (refined.isNotBlank()) refined else text
        } catch (e: Exception) {
            text
        }
    }
}
