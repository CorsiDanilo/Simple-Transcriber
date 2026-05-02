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

    // ── Catalogo remoto ──────────────────────────────────────────────

    /**
     * Scarica e parsa il catalogo JSON dall'endpoint remoto.
     */
    suspend fun fetchModelCatalog(catalogUrl: String): Result<List<ModelInfo>> =
        withContext(Dispatchers.IO) {
            val models = listOf(
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
            Result.success(models)
        }

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
        val current = _activeDownloads.value.toMutableMap()
        current[modelId] = DownloadProgress(modelId, bytesDownloaded, totalBytes)
        _activeDownloads.value = current
    }

    private fun removeProgress(modelId: String) {
        val current = _activeDownloads.value.toMutableMap()
        current.remove(modelId)
        _activeDownloads.value = current
    }
}
