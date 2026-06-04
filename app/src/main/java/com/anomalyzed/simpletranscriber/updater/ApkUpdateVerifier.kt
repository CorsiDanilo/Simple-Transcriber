package com.anomalyzed.simpletranscriber.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

object ApkUpdateVerifier {
    private const val TAG = "ApkUpdateVerifier"

    fun verify(context: Context, apkFile: File, expectedSha256: String): Boolean {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            Log.e(TAG, "Downloaded update is missing or empty")
            return false
        }

        if (!UpdateIntegrity.sha256Matches(apkFile, expectedSha256)) {
            Log.e(TAG, "Downloaded update SHA-256 does not match release metadata")
            return false
        }

        val archiveInfo = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            signingFlags()
        )
        if (archiveInfo == null) {
            Log.e(TAG, "Downloaded APK package metadata could not be read")
            return false
        }

        if (archiveInfo.packageName != context.packageName) {
            Log.e(TAG, "Downloaded APK package name does not match this app")
            return false
        }

        val installedInfo = context.packageManager.getPackageInfo(context.packageName, signingFlags())
        val archiveCertificates = signingCertificateSha256s(archiveInfo)
        val installedCertificates = signingCertificateSha256s(installedInfo)
        if (archiveCertificates.isEmpty() || installedCertificates.isEmpty()) {
            Log.e(TAG, "Could not read APK signing certificates")
            return false
        }

        if (archiveCertificates.none { it in installedCertificates }) {
            Log.e(TAG, "Downloaded APK signing certificate does not match installed app")
            return false
        }

        return true
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256s(packageInfo: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners ?: emptyArray()
            } else {
                signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners ?: emptyArray()
            }
        } else {
            packageInfo.signatures ?: emptyArray()
        }

        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.toSet()
    }
}
