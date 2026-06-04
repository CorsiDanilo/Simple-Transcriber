package com.anomalyzed.simpletranscriber.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class DownloadReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DownloadReceiver"

        @Volatile
        private var pendingUpdate: PendingUpdate? = null

        fun setPendingDownload(downloadId: Long, expectedSha256: String, versionName: String) {
            pendingUpdate = PendingUpdate(downloadId, expectedSha256, versionName)
        }

        fun clearPendingDownload(downloadId: Long? = null) {
            val pending = pendingUpdate
            if (downloadId == null || pending?.downloadId == downloadId) {
                pendingUpdate = null
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE != action) {
            return
        }

        val pending = pendingUpdate
        if (pending == null) {
            Log.w(TAG, "Ignoring download completion with no pending update")
            return
        }

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId != pending.downloadId) {
            Log.w(TAG, "Ignoring unexpected download id")
            return
        }

        val query = DownloadManager.Query().setFilterById(downloadId)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(query)

        cursor.use {
            if (!it.moveToFirst()) {
                clearPendingDownload(downloadId)
                return
            }

            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) {
                clearPendingDownload(downloadId)
                return
            }

            when (it.getInt(statusIndex)) {
                DownloadManager.STATUS_SUCCESSFUL -> handleSuccessfulDownload(context, it, pending)
                DownloadManager.STATUS_FAILED -> {
                    Log.e(TAG, "Download failed")
                    clearPendingDownload(downloadId)
                }
            }
        }
    }

    private fun handleSuccessfulDownload(
        context: Context,
        cursor: android.database.Cursor,
        pending: PendingUpdate
    ) {
        try {
            val file = downloadedFileFromCursor(context, cursor)
            if (file == null) {
                Log.e(TAG, "Downloaded update path is invalid")
                clearPendingDownload(pending.downloadId)
                return
            }

            if (!ApkUpdateVerifier.verify(context, file, pending.expectedSha256)) {
                file.delete()
                clearPendingDownload(pending.downloadId)
                return
            }

            clearPendingDownload(pending.downloadId)
            installApk(context, file)
        } catch (e: Exception) {
            clearPendingDownload(pending.downloadId)
            Log.e(TAG, "Failed reading downloaded file", e)
        }
    }

    private fun downloadedFileFromCursor(
        context: Context,
        cursor: android.database.Cursor
    ): File? {
        val uriStringIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        if (uriStringIndex < 0) return null

        val uriString = cursor.getString(uriStringIndex) ?: return null
        val downloadedUri = Uri.parse(uriString)
        if (downloadedUri.scheme != "file") return null

        val path = downloadedUri.path ?: return null
        val file = File(path)
        if (!isInAppDownloadsDir(context, file)) return null

        return file
    }

    private fun isInAppDownloadsDir(context: Context, file: File): Boolean {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        val downloadsPath = downloadsDir.canonicalPath
        val filePath = file.canonicalPath
        return filePath.startsWith("$downloadsPath${File.separator}")
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

    private data class PendingUpdate(
        val downloadId: Long,
        val expectedSha256: String,
        val versionName: String
    )
}
