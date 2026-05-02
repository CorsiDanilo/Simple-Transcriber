package com.anomalyzed.simpletranscriber.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class DownloadReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DownloadReceiver"
        var enqueuedDownloadId: Long = -1L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            
            if (enqueuedDownloadId != -1L && enqueuedDownloadId != downloadId) {
                return
            }

            val query = DownloadManager.Query()
            query.setFilterById(downloadId)
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0) {
                    val status = cursor.getInt(statusIndex)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        try {
                            val uriStringIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            if (uriStringIndex >= 0) {
                                val uriString = cursor.getString(uriStringIndex)
                                val downloadedUri = Uri.parse(uriString)
                                val file = File(downloadedUri.path!!)
                                
                                enqueuedDownloadId = -1L
                                installApk(context, file)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed reading downloaded file", e)
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Log.e(TAG, "Download failed")
                        enqueuedDownloadId = -1L
                    }
                }
            }
            cursor.close()
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
}
