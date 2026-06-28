package com.anomalyzed.simpletranscriber.updater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdater {

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/CorsiDanilo/Simple-Transcriber/releases/latest"
        private const val TAG = "AppUpdater"
    }

    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection(GITHUB_API_URL, "application/vnd.github.v3+json")

            connection.useIfOk { response ->
                val json = JSONObject(response)

                val tagName = json.optString("tag_name", "")
                val changelog = json.optString("body", "Bug fixes and improvements.")
                val assets = parseReleaseAssets(json.optJSONArray("assets"))
                val apkAsset = UpdateIntegrity.selectApkAsset(assets)
                val expectedSha256 = apkAsset?.let { asset ->
                    UpdateIntegrity.normalizeSha256(asset.digest)
                        ?: resolveChecksumAssetSha256(assets, asset.name)
                }
                val downloadUrl = if (expectedSha256 != null) apkAsset?.browserDownloadUrl else null

                // Clean 'v' prefix if exists (e.g. "v1.0.1" -> "1.0.1")
                val latestVersion = tagName.removePrefix("v")
                val currentCleanVersion = currentVersionName.removePrefix("v")

                val updateAvailable = isNewerVersion(latestVersion, currentCleanVersion)
                if (updateAvailable && downloadUrl == null) {
                    Log.e(TAG, "Update found but no trusted APK asset with SHA-256 metadata was available")
                }

                return@withContext UpdateInfo(
                    updateAvailable = updateAvailable && downloadUrl != null,
                    versionName = latestVersion,
                    changelog = changelog,
                    downloadUrl = downloadUrl,
                    expectedSha256 = expectedSha256
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}")
        }
        
        return@withContext UpdateInfo(false, "", "", null, null)
    }

    private fun parseReleaseAssets(assets: JSONArray?): List<ReleaseAssetMetadata> {
        if (assets == null) return emptyList()

        return buildList {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                add(
                    ReleaseAssetMetadata(
                        name = asset.optString("name", ""),
                        browserDownloadUrl = asset.optString("browser_download_url", ""),
                        digest = asset.optString("digest", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun resolveChecksumAssetSha256(
        assets: List<ReleaseAssetMetadata>,
        apkAssetName: String
    ): String? {
        val checksumAsset = UpdateIntegrity.findChecksumAsset(assets, apkAssetName) ?: return null
        return try {
            val connection = openConnection(checksumAsset.browserDownloadUrl, "text/plain")
            connection.useIfOk { response ->
                UpdateIntegrity.parseSha256ChecksumText(response, apkAssetName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch update checksum: ${e.message}")
            null
        }
    }

    private fun openConnection(urlString: String, accept: String): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", accept)
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return connection
    }

    private inline fun <T> HttpURLConnection.useIfOk(block: (String) -> T): T? {
        return try {
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                block(response)
            } else {
                Log.e(TAG, "Failed to fetch update data. HTTP code: $responseCode")
                null
            }
        } finally {
            disconnect()
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val l = if (i < latestParts.size) latestParts[i] else 0
                val c = if (i < currentParts.size) currentParts[i] else 0
                
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing versions", e)
        }
        return false
    }
}
