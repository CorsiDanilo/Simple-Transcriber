package com.anomalyzed.simpletranscriber.updater

import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class ReleaseAssetMetadata(
    val name: String,
    val browserDownloadUrl: String,
    val digest: String?
)

object UpdateIntegrity {
    private const val TRUSTED_RELEASE_DOWNLOAD_PREFIX =
        "https://github.com/CorsiDanilo/simple-transcription-app/releases/download/"
    private val SHA256_VALUE = Regex("^(?:sha256:)?([a-fA-F0-9]{64})$")
    private val SHA256_IN_TEXT = Regex("(?:sha256:)?([a-fA-F0-9]{64})")

    fun selectApkAsset(assets: List<ReleaseAssetMetadata>): ReleaseAssetMetadata? =
        assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                isTrustedReleaseDownloadUrl(asset.browserDownloadUrl)
        }

    fun findChecksumAsset(
        assets: List<ReleaseAssetMetadata>,
        apkAssetName: String
    ): ReleaseAssetMetadata? {
        val expectedNames = setOf(
            "$apkAssetName.sha256",
            "$apkAssetName.sha256sum",
            "$apkAssetName.sha256.txt",
            "checksums.txt",
            "SHA256SUMS"
        )

        return assets.firstOrNull { asset ->
            expectedNames.any { it.equals(asset.name, ignoreCase = true) } &&
                isTrustedReleaseDownloadUrl(asset.browserDownloadUrl)
        }
    }

    fun normalizeSha256(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        val match = SHA256_VALUE.matchEntire(trimmed) ?: return null
        return match.groupValues[1].lowercase(Locale.US)
    }

    fun parseSha256ChecksumText(text: String, apkAssetName: String): String? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        for (line in lines) {
            val exactHash = normalizeSha256(line)
            if (exactHash != null && lines.size == 1) {
                return exactHash
            }

            if (!line.contains(apkAssetName, ignoreCase = true)) {
                continue
            }

            val hashInLine = SHA256_IN_TEXT.find(line)?.groupValues?.get(1)
            val normalized = normalizeSha256(hashInLine)
            if (normalized != null) {
                return normalized
            }
        }

        return null
    }

    fun isTrustedReleaseDownloadUrl(url: String): Boolean =
        url.startsWith(TRUSTED_RELEASE_DOWNLOAD_PREFIX) &&
            !url.contains("\\") &&
            !url.contains("\u0000")

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun sha256Matches(file: File, expectedSha256: String?): Boolean {
        val normalizedExpected = normalizeSha256(expectedSha256) ?: return false
        return sha256Of(file).equals(normalizedExpected, ignoreCase = true)
    }
}
