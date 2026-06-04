package com.anomalyzed.simpletranscriber.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateIntegrityTest {
    @Test
    fun normalizeSha256AcceptsGithubDigestFormat() {
        val hash = "a".repeat(64)

        assertEquals(hash, UpdateIntegrity.normalizeSha256("sha256:$hash"))
    }

    @Test
    fun normalizeSha256RejectsInvalidDigest() {
        assertNull(UpdateIntegrity.normalizeSha256("sha256:not-a-real-hash"))
        assertNull(UpdateIntegrity.normalizeSha256("b".repeat(63)))
    }

    @Test
    fun selectApkAssetRequiresTrustedGithubReleaseUrl() {
        val trusted = ReleaseAssetMetadata(
            name = "transcriber-signed.apk",
            browserDownloadUrl = "https://github.com/CorsiDanilo/simple-transcription-app/releases/download/v1.2.0/transcriber-signed.apk",
            digest = "sha256:${"c".repeat(64)}"
        )
        val untrusted = ReleaseAssetMetadata(
            name = "evil.apk",
            browserDownloadUrl = "https://example.com/evil.apk",
            digest = "sha256:${"d".repeat(64)}"
        )

        assertEquals(trusted, UpdateIntegrity.selectApkAsset(listOf(untrusted, trusted)))
    }

    @Test
    fun checksumSidecarParserRequiresMatchingApkNameForMultiLineFile() {
        val expectedHash = "e".repeat(64)
        val otherHash = "f".repeat(64)
        val checksumText = """
            $otherHash  other.apk
            $expectedHash  transcriber-signed.apk
        """.trimIndent()

        assertEquals(
            expectedHash,
            UpdateIntegrity.parseSha256ChecksumText(checksumText, "transcriber-signed.apk")
        )
        assertNull(UpdateIntegrity.parseSha256ChecksumText(checksumText, "missing.apk"))
    }

    @Test
    fun sha256MatchesFailsClosedForWrongHash() {
        val tempFile = File.createTempFile("transcriber-update", ".apk")
        tempFile.writeText("not really an apk")

        try {
            val correctHash = UpdateIntegrity.sha256Of(tempFile)

            assertTrue(UpdateIntegrity.sha256Matches(tempFile, correctHash))
            assertFalse(UpdateIntegrity.sha256Matches(tempFile, "0".repeat(64)))
            assertFalse(UpdateIntegrity.sha256Matches(tempFile, null))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun checksumAssetCanBeLocatedByApkSpecificSidecarName() {
        val sidecar = ReleaseAssetMetadata(
            name = "transcriber-signed.apk.sha256",
            browserDownloadUrl = "https://github.com/CorsiDanilo/simple-transcription-app/releases/download/v1.2.0/transcriber-signed.apk.sha256",
            digest = null
        )

        assertNotNull(UpdateIntegrity.findChecksumAsset(listOf(sidecar), "transcriber-signed.apk"))
    }
}
