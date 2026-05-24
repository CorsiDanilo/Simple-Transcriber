package com.anomalyzed.simpletranscriber.data

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Repository per gestire il catalogo remoto dei modelli LiteRT e il loro ciclo di vita
 * (download, cancellazione, selezione) sul device.
 */
class ModelRepository(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    /** Download attualmente in corso, indicizzati per modelId */
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    @Volatile
    private var cancelledDownloads = mutableSetOf<String>()
    private val lastProgressEmits = ConcurrentHashMap<String, Pair<Long, Float>>()

    // ── Catalogo remoto ──────────────────────────────────────────────

    /**
     * Scarica e parsa il catalogo JSON dall'endpoint remoto.
     */
    suspend fun fetchModelCatalog(catalogUrl: String): Result<List<ModelInfo>> =
        withContext(Dispatchers.IO) {
            val models = buildGemmaCatalog() + buildWhisperCatalog()
            /*
            val oldModels = listOf(
                ModelInfo(
                    id = "gemma-4-e2b",
                    displayName = "Gemma 4 E2B",
                    description = "2B parameters • Optimized for speed • Best for most devices",
                    sizeBytes = 2583085056, // ~2.4 GB
                    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                    fileName = "gemma-4-E2B-it.litertlm",
                    minRamMb = 4096,
                    supportedLanguages = listOf("en", "it", "es", "fr", "de", "pt", "ru", "zh", "ja", "ar")
                ),
                ModelInfo(
                    id = "gemma-4-e4b",
                    displayName = "Gemma 4 E4B",
                    description = "4B parameters • 8-bit Quantization • High accuracy • Requires 8GB+ RAM",
                    sizeBytes = 4085252096, // ~3.8 GB
                    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                    fileName = "gemma-4-E4B-it.litertlm",
                    minRamMb = 6144,
                    supportedLanguages = listOf("en", "it", "es", "fr", "de", "pt", "ru", "zh", "ja", "ar")
                )
            )
            */
            Result.success(models)
        }

    private fun buildGemmaCatalog(): List<ModelInfo> = listOf(
        ModelInfo(
            id = "gemma-4-e2b",
            backend = ModelBackend.LITERT,
            displayName = "Gemma 4 E2B",
            description = "LiteRT-LM - fast on-device refinement - audio-capable",
            sizeBytes = gb(2.59),
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            minRamMb = 4096,
            supportedLanguages = listOf("en", "it", "es", "fr", "de", "pt", "ru", "zh", "ja", "ar")
        ),
        ModelInfo(
            id = "gemma-4-e4b",
            backend = ModelBackend.LITERT,
            displayName = "Gemma 4 E4B",
            description = "LiteRT-LM - higher quality - high RAM",
            sizeBytes = gb(3.66),
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "gemma-4-E4B-it.litertlm",
            minRamMb = 6144,
            supportedLanguages = listOf("en", "it", "es", "fr", "de", "pt", "ru", "zh", "ja", "ar")
        )
    )

    private fun buildWhisperCatalog(): List<ModelInfo> {
        fun whisper(
            id: String,
            displayName: String,
            sizeBytes: Long,
            minRamMb: Int,
            quantization: String = "",
            englishOnly: Boolean = false,
            diarization: Boolean = false
        ): ModelInfo {
            val fileName = "ggml-$id.bin"
            val languageLabel = if (englishOnly) "English only" else "Multilingual"
            val quantLabel = if (quantization.isNotBlank()) "$quantization quantized" else "Full precision"
            val diarizationLabel = if (diarization) " - speaker turns" else ""
            return ModelInfo(
                id = "whisper-$id",
                backend = ModelBackend.WHISPER_CPP,
                displayName = displayName,
                description = "Whisper.cpp - $languageLabel - $quantLabel$diarizationLabel",
                sizeBytes = sizeBytes,
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName",
                fileName = fileName,
                minRamMb = minRamMb,
                supportedLanguages = if (englishOnly) listOf("en") else listOf("multi"),
                quantization = quantization,
                isEnglishOnly = englishOnly
            )
        }

        return listOf(
            whisper("tiny", "Whisper Tiny", mib(75), 512),
            whisper("tiny-q5_1", "Whisper Tiny Q5_1", mib(31), 512, "Q5_1"),
            whisper("tiny-q8_0", "Whisper Tiny Q8_0", mib(42), 512, "Q8_0"),
            whisper("tiny.en", "Whisper Tiny EN", mib(75), 512, englishOnly = true),
            whisper("tiny.en-q5_1", "Whisper Tiny EN Q5_1", mib(31), 512, "Q5_1", englishOnly = true),
            whisper("tiny.en-q8_0", "Whisper Tiny EN Q8_0", mib(42), 512, "Q8_0", englishOnly = true),
            whisper("base", "Whisper Base", mib(142), 1024),
            whisper("base-q5_1", "Whisper Base Q5_1", mib(57), 1024, "Q5_1"),
            whisper("base-q8_0", "Whisper Base Q8_0", mib(78), 1024, "Q8_0"),
            whisper("base.en", "Whisper Base EN", mib(142), 1024, englishOnly = true),
            whisper("base.en-q5_1", "Whisper Base EN Q5_1", mib(57), 1024, "Q5_1", englishOnly = true),
            whisper("base.en-q8_0", "Whisper Base EN Q8_0", mib(78), 1024, "Q8_0", englishOnly = true),
            whisper("small", "Whisper Small", mib(466), 2048),
            whisper("small-q5_1", "Whisper Small Q5_1", mib(181), 2048, "Q5_1"),
            whisper("small-q8_0", "Whisper Small Q8_0", mib(252), 2048, "Q8_0"),
            whisper("small.en", "Whisper Small EN", mib(466), 2048, englishOnly = true),
            whisper("small.en-q5_1", "Whisper Small EN Q5_1", mib(181), 2048, "Q5_1", englishOnly = true),
            whisper("small.en-q8_0", "Whisper Small EN Q8_0", mib(252), 2048, "Q8_0", englishOnly = true),
            whisper("small.en-tdrz", "Whisper Small EN TDRZ", mib(465), 2048, englishOnly = true, diarization = true),
            whisper("medium", "Whisper Medium", gb(1.5), 3072),
            whisper("medium-q5_0", "Whisper Medium Q5_0", mib(514), 3072, "Q5_0"),
            whisper("medium-q8_0", "Whisper Medium Q8_0", mib(785), 3072, "Q8_0"),
            whisper("medium.en", "Whisper Medium EN", gb(1.5), 3072, englishOnly = true),
            whisper("medium.en-q5_0", "Whisper Medium EN Q5_0", mib(514), 3072, "Q5_0", englishOnly = true),
            whisper("medium.en-q8_0", "Whisper Medium EN Q8_0", mib(785), 3072, "Q8_0", englishOnly = true),
            whisper("large-v1", "Whisper Large v1", gb(2.9), 4096),
            whisper("large-v2", "Whisper Large v2", gb(2.9), 4096),
            whisper("large-v2-q5_0", "Whisper Large v2 Q5_0", gb(1.1), 4096, "Q5_0"),
            whisper("large-v2-q8_0", "Whisper Large v2 Q8_0", gb(1.5), 4096, "Q8_0"),
            whisper("large-v3", "Whisper Large v3", gb(2.9), 4096),
            whisper("large-v3-q5_0", "Whisper Large v3 Q5_0", gb(1.1), 4096, "Q5_0"),
            whisper("large-v3-turbo", "Whisper Large v3 Turbo", gb(1.5), 4096),
            whisper("large-v3-turbo-q5_0", "Whisper Large v3 Turbo Q5_0", mib(547), 4096, "Q5_0"),
            whisper("large-v3-turbo-q8_0", "Whisper Large v3 Turbo Q8_0", mib(834), 4096, "Q8_0")
        )
    }

    private fun mib(value: Int): Long = value.toLong() * 1024L * 1024L

    private fun gb(value: Double): Long = (value * 1024.0 * 1024.0 * 1024.0).toLong()

    // ── Stato modelli ────────────────────────────────────────────────

    /**
     * Combina il catalogo con lo stato locale (scaricato/in download/selezionato).
     */
    fun getModelsWithStatus(
        catalog: List<ModelInfo>,
        selectedModelId: String
    ): List<ModelWithStatus> {
        val downloads = _activeDownloads.value
        return catalog.map { model ->
            val status = when {
                model.id == selectedModelId && isModelDownloaded(model) -> ModelStatus.Selected
                downloads.containsKey(model.id) -> ModelStatus.Downloading(
                    downloads[model.id]?.fraction ?: 0f
                )
                isModelDownloaded(model) -> ModelStatus.Downloaded
                else -> ModelStatus.Available
            }
            ModelWithStatus(model, status)
        }
    }

    /**
     * Verifica se un modello è presente sul device.
     */
    fun isModelDownloaded(model: ModelInfo): Boolean {
        val file = File(modelsDir, model.fileName)
        return file.exists() && file.length() > 0
    }

    /**
     * Restituisce il path assoluto del modello, o null se non scaricato.
     */
    fun getModelPath(model: ModelInfo): String? {
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /**
     * Trova il path di un modello dato il suo ID e il catalogo.
     */
    fun getModelPathById(modelId: String, catalog: List<ModelInfo>): String? {
        val model = catalog.find { it.id == modelId } ?: return null
        return getModelPath(model)
    }

    // ── Download ─────────────────────────────────────────────────────

    /**
     * Scarica un modello dal suo URL con reporting del progresso.
     * Aggiorna [activeDownloads] durante il download.
     */
    suspend fun downloadModel(model: ModelInfo): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        // Reset flag cancellazione per questo modello
        cancelledDownloads.remove(model.id)

        try {
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"

            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        Exception("Download failed: HTTP $responseCode")
                    )
                }

                val totalBytes = connection.contentLengthLong.let {
                    if (it > 0) it else model.sizeBytes
                }

                val inputStream = BufferedInputStream(connection.inputStream, 8192)
                val outputStream = FileOutputStream(tempFile)

                var bytesDownloaded = 0L
                val buffer = ByteArray(8192)
                var bytesRead: Int

                // Emetti progresso iniziale
                updateProgress(model.id, 0L, totalBytes)

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Controlla cancellazione
                            if (cancelledDownloads.contains(model.id) || !coroutineContext.isActive) {
                                tempFile.delete()
                                removeProgress(model.id)
                                return@withContext Result.failure(
                                    CancellationException("Download cancelled")
                                )
                            }

                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            updateProgress(model.id, bytesDownloaded, totalBytes)
                        }
                    }
                }

                // Rinomina temp → finale
                if (tempFile.renameTo(targetFile)) {
                    removeProgress(model.id)
                    Result.success(targetFile)
                } else {
                    tempFile.delete()
                    removeProgress(model.id)
                    Result.failure(Exception("Failed to finalize downloaded file"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            removeProgress(model.id)
            Result.failure(e)
        } catch (e: Exception) {
            tempFile.delete()
            removeProgress(model.id)
            Result.failure(e)
        }
    }

    /**
     * Annulla un download in corso.
     */
    fun cancelDownload(modelId: String) {
        cancelledDownloads.add(modelId)
    }

    // ── Eliminazione ─────────────────────────────────────────────────

    /**
     * Elimina un modello scaricato dal device.
     */
    fun deleteModel(model: ModelInfo): Boolean {
        val file = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")
        tempFile.delete()
        return file.delete()
    }

    // ── Storage info ─────────────────────────────────────────────────

    /**
     * Spazio disponibile in bytes sulla partizione dati.
     */
    fun getAvailableStorageBytes(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Spazio disponibile formattato (es. "12.4 GB").
     */
    fun getFormattedAvailableStorage(): String {
        val bytes = getAvailableStorageBytes()
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format("%.1f GB", gb)
    }

    /**
     * RAM totale del device in MB.
     */
    fun getDeviceRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }

    // ── Helpers privati ──────────────────────────────────────────────

    private fun updateProgress(modelId: String, bytesDownloaded: Long, totalBytes: Long) {
        val fraction = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastProgressEmits[modelId]
        val shouldEmit = last == null ||
            fraction >= 1f ||
            fraction - last.second >= 0.01f ||
            now - last.first >= 500L

        if (!shouldEmit) return

        lastProgressEmits[modelId] = now to fraction
        val current = _activeDownloads.value.toMutableMap()
        current[modelId] = DownloadProgress(modelId, bytesDownloaded, totalBytes)
        _activeDownloads.value = current
    }

    private fun removeProgress(modelId: String) {
        lastProgressEmits.remove(modelId)
        val current = _activeDownloads.value.toMutableMap()
        current.remove(modelId)
        _activeDownloads.value = current
    }
}
