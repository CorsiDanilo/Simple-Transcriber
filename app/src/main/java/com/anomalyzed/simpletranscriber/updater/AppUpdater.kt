package com.anomalyzed.simpletranscriber.updater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdater {

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/CorsiDanilo/simple-transcription-app/releases/latest"
        private const val TAG = "AppUpdater"
    }

    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val json = JSONObject(response)

                val tagName = json.optString("tag_name", "")
                val changelog = json.optString("body", "Bug fixes and improvements.")
                
                // Get the first asset download url (which is the apk)
                val assets = json.optJSONArray("assets")
                var downloadUrl: String? = null
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name", "").endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                // Clean 'v' prefix if exists (e.g. "v1.0.1" -> "1.0.1")
                val latestVersion = tagName.removePrefix("v")
                val currentCleanVersion = currentVersionName.removePrefix("v")

                val updateAvailable = isNewerVersion(latestVersion, currentCleanVersion)

                return@withContext UpdateInfo(
                    updateAvailable = updateAvailable,
                    versionName = latestVersion,
                    changelog = changelog,
                    downloadUrl = downloadUrl
                )
            } else {
                Log.e(TAG, "Failed to fetch update. HTTP code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}")
        }
        
        return@withContext UpdateInfo(false, "", "", null)
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
