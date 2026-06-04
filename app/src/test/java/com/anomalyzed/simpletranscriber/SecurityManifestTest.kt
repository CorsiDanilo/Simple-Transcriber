package com.anomalyzed.simpletranscriber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SecurityManifestTest {
    @Test
    fun downloadReceiverIsNotExported() {
        val manifest = parseXml(moduleFile("src/main/AndroidManifest.xml"))
        val receiver = manifest.elements("receiver").first {
            it.androidAttribute("name") == ".updater.DownloadReceiver"
        }

        assertEquals("false", receiver.androidAttribute("exported"))
    }

    @Test
    fun backupRulesExcludeSettingsDatastoreFromBackupAndDeviceTransfer() {
        val manifest = parseXml(moduleFile("src/main/AndroidManifest.xml"))
        val application = manifest.elements("application").single()

        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))

        val backupRules = parseXml(moduleFile("src/main/res/xml/backup_rules.xml"))
        assertTrue(
            backupRules.elements("exclude").any {
                it.attribute("domain") == "file" &&
                    it.attribute("path") == "datastore/settings.preferences_pb"
            }
        )

        val dataExtractionRules = parseXml(moduleFile("src/main/res/xml/data_extraction_rules.xml"))
        val cloudBackup = dataExtractionRules.elements("cloud-backup").single()
        val deviceTransfer = dataExtractionRules.elements("device-transfer").single()

        assertDatastoreExcluded(cloudBackup)
        assertDatastoreExcluded(deviceTransfer)
    }

    private fun assertDatastoreExcluded(parent: Element) {
        assertTrue(
            parent.elements("exclude").any {
                it.attribute("domain") == "file" &&
                    it.attribute("path") == "datastore/settings.preferences_pb"
            }
        )
    }

    private fun moduleFile(path: String): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            workingDirectory.resolve(path),
            workingDirectory.resolve("app").resolve(path)
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find $path from $workingDirectory")
    }

    private fun parseXml(file: File): Document {
        assertTrue("Missing ${file.path}", file.isFile)
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
    }

    private fun Document.elements(tagName: String): List<Element> =
        documentElement.elements(tagName)

    private fun Element.elements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NS, name)

    private fun Element.attribute(name: String): String = getAttribute(name)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
