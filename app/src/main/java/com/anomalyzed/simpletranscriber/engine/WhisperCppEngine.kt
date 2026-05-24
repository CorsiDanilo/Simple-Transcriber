package com.anomalyzed.simpletranscriber.engine

import com.anomalyzed.simpletranscriber.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperCppEngine(
    private val modelPath: String?,
    private val modelDisplayName: String = "Whisper.cpp"
) : TranscriptionEngine {

    private val audioPreprocessor = AudioPreprocessor()
    private var whisperContext: WhisperContext? = null

    override suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult {
        if (modelPath == null) {
            return TranscriptionResult.Error("No Whisper model selected. Please download and select a Whisper model first.")
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return TranscriptionResult.Error("Whisper model file not found at: $modelPath")
        }

        return try {
            onProgress("Preparing audio...")
            val pcmBytes = audioPreprocessor.decodeToRawPcm(audioBytes, mimeType)
            val audioData = pcm16ToFloatArray(pcmBytes)

            onProgress("Loading Whisper model...")
            val context = getOrCreateContext()

            onProgress("Transcribing with Whisper...")
            val transcript = context.transcribeData(
                data = audioData,
                languageCode = language.toWhisperLanguageCode(),
                onProgress = { progress ->
                    onProgress("Transcribing with Whisper... $progress%")
                },
                onNewSegment = { partialText ->
                    if (partialText.isNotBlank()) {
                        onPartialText(partialText)
                    }
                }
            )
            if (transcript.isNotBlank()) {
                onPartialText(transcript)
                TranscriptionResult.Success(transcript)
            } else {
                TranscriptionResult.Error("Whisper returned no text. Check audio volume or content.")
            }
        } catch (e: Exception) {
            TranscriptionResult.Error("Whisper.cpp error: ${e.localizedMessage ?: e.message}")
        }
    }

    override fun isAvailable(): Boolean =
        modelPath?.let { File(it).exists() } == true

    override fun displayName(): String = modelDisplayName

    override suspend fun refineText(
        text: String,
        language: String,
        onPartialText: (String) -> Unit
    ): String = text

    override fun release() {
        val contextToRelease = whisperContext ?: return
        whisperContext = null
        kotlinx.coroutines.runBlocking {
            contextToRelease.release()
        }
    }

    private suspend fun getOrCreateContext(): WhisperContext = withContext(Dispatchers.Default) {
        whisperContext ?: WhisperContext.createContextFromFile(modelPath!!).also {
            whisperContext = it
        }
    }

    private fun pcm16ToFloatArray(pcmBytes: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcmBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        return FloatArray(shortBuffer.remaining()) { index ->
            shortBuffer.get(index) / 32768.0f
        }
    }

    private fun String.toWhisperLanguageCode(): String {
        return when (trim().lowercase()) {
            "italian", "italiano", "it" -> "it"
            "english", "inglese", "en" -> "en"
            "spanish", "spagnolo", "es" -> "es"
            "french", "francese", "fr" -> "fr"
            "german", "tedesco", "de" -> "de"
            "portuguese", "portoghese", "pt" -> "pt"
            "russian", "russo", "ru" -> "ru"
            "chinese", "cinese", "zh" -> "zh"
            "japanese", "giapponese", "ja" -> "ja"
            "arabic", "arabo", "ar" -> "ar"
            else -> "auto"
        }
    }
}
