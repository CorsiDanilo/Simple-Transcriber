package com.example.simpletranscriberapp.engine

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

/**
 * Engine di trascrizione che usa l'API cloud di Gemini.
 * Refactor della logica originariamente inline nel TranscriberViewModel.
 */
class CloudEngine(private val apiKey: String) : TranscriptionEngine {

    override suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error("API Key is missing. Please enter it first.")
        }

        return try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
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

            val response = generativeModel.generateContent(requestContent)
            val text = response.text?.trim()

            if (!text.isNullOrBlank()) {
                TranscriptionResult.Success(text)
            } else {
                TranscriptionResult.Error("Gemini returned no text. Check audio volume or content.")
            }
        } catch (e: Exception) {
            TranscriptionResult.Error(e.localizedMessage ?: "Cloud transcription failed.")
        }
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    override fun displayName(): String = "Cloud (Gemini)"
}
