package com.anomalyzed.simpletranscriber.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rappresenta un modello disponibile nel catalogo remoto.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val fileName: String,
    val minRamMb: Int,
    val supportedLanguages: List<String>
) {
    /** Dimensione formattata leggibile (es. "2.1 GB") */
    val formattedSize: String
        get() {
            val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format("%.1f GB", gb)
            } else {
                val mb = sizeBytes / (1024.0 * 1024.0)
                String.format("%.0f MB", mb)
            }
        }

    companion object {
        fun fromJson(json: JSONObject): ModelInfo = ModelInfo(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            description = json.getString("description"),
            sizeBytes = json.getLong("sizeBytes"),
            downloadUrl = json.getString("downloadUrl"),
            fileName = json.getString("fileName"),
            minRamMb = json.optInt("minRamMb", 0),
            supportedLanguages = json.optJSONArray("supportedLanguages")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                ?: emptyList()
        )

        fun listFromJson(jsonString: String): List<ModelInfo> {
            val root = JSONObject(jsonString)
            val modelsArray: JSONArray = root.getJSONArray("models")
            return (0 until modelsArray.length()).map { fromJson(modelsArray.getJSONObject(it)) }
        }
    }
}

/**
 * Modello arricchito con il suo stato attuale nella UI.
 */
data class ModelWithStatus(
    val info: ModelInfo,
    val status: ModelStatus
)

/**
 * Stato di un modello nel sistema.
 */
sealed class ModelStatus {
    /** Non scaricato, disponibile per il download */
    object Available : ModelStatus()

    /** Download in corso */
    data class Downloading(val progress: Float) : ModelStatus()

    /** Scaricato e pronto all'uso */
    object Downloaded : ModelStatus()

    /** Scaricato e attualmente selezionato come engine attivo */
    object Selected : ModelStatus()
}

/**
 * Progresso di un download in corso.
 */
data class DownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

    val formattedProgress: String
        get() {
            val downloadedMb = bytesDownloaded / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return String.format("%.0f / %.0f MB", downloadedMb, totalMb)
        }
}
