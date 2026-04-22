package com.example.simpletranscriberapp.engine

/**
 * Risultato di una trascrizione.
 */
sealed class TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult()
    data class Error(val message: String) : TranscriptionResult()
}

/**
 * Interfaccia Strategy per i diversi backend di trascrizione.
 *
 * Ogni implementazione (Cloud, AICore, LiteRT) deve fornire la logica di trascrizione
 * e indicare se è attualmente disponibile per l'uso.
 */
interface TranscriptionEngine {

    /**
     * Esegue la trascrizione dell'audio.
     *
     * @param audioBytes Bytes grezzi dell'audio
     * @param mimeType MIME type dell'audio (es. "audio/ogg", "audio/mp4")
     * @param language Lingua target per la trascrizione/traduzione
     * @param onProgress Callback opzionale per messaggi di stato intermedi
     *                   (es. "Transcribing segment 2/4...")
     */
    suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit = {}
    ): TranscriptionResult

    /**
     * Indica se questo engine è attualmente disponibile per l'uso.
     * - Cloud: true se l'API key è configurata
     * - AICore: true se il device supporta AICore
     * - LiteRT: true se un modello è scaricato e selezionato
     */
    fun isAvailable(): Boolean

    /**
     * Nome visualizzabile dell'engine (es. "Cloud (Gemini)", "AICore", "Gemma 4 E2B")
     */
    fun displayName(): String
}

/**
 * Enum per identificare il tipo di engine di trascrizione.
 */
enum class EngineType(val key: String, val label: String) {
    CLOUD("cloud", "Cloud (Gemini API)"),
    AICORE("aicore", "AICore (On-Device)"),
    LITERT("litert", "Local Model (LiteRT-LM)");

    companion object {
        fun fromKey(key: String): EngineType =
            entries.firstOrNull { it.key == key } ?: CLOUD
    }
}
