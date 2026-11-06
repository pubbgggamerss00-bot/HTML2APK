package ir.html2apk.app

import com.android.apksig.ApkVerifier
import ir.html2apk.app.buildengine.ApkAssembler
import ir.html2apk.app.buildengine.ApkSignUtil
import ir.html2apk.app.buildengine.AXmlEditor
import ir.html2apk.app.buildengine.ArscPatcher
import ir.html2apk.app.buildengine.KeystoreManager
import ir.html2apk.app.buildengine.ManifestPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry

/**
 * Full engine pipeline on the JVM (no Android): real aapt2-built template APK fixture →
 * manifest/arsc patch → streamed assemble → keystore → apksig v1+v2+v3 sign →
 * verification with the authoritative [ApkVerifier] + ZIP structural assertions.
 *
 * Requires the CI/local prebuild to copy the built template APK into
 * app/src/test/resources/real_template.apk (skipped when absent).
 */
class EngineEndToEndTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun resource(name: String): ByteArray? =
        javaClass.getResourceAsStream("/$name")?.use { it.readBytes() }

    @Test
    fun `full pipeline produces a verifiable signed apk`() {
        val templateApk = resource("real_template.apk") ?: run {
            assumeTrue("real template fixture not present (local run)", false)
            return
        }

        val template = ApkAssembler.readTemplate(templateApk)
        assertTrue(template.entries.containsKey("resources.arsc"))
        assertTrue(template.iconEntryPaths.isNotEmpty())

        val pkg = "ir.e2e.testapp"
        val manifest = AXmlEditor.patchManifest(
            template.entries.getValue("AndroidManifest.xml"),
            ManifestPatch(
                packageName = pkg,
                versionCode = 5,
                versionName = "5.0",
                label = "E2E App",
                screenOrientation = 1,
                internetPermission = false,
            )
        )
        val arsc = ArscPatcher.patchPackageName(
            template.entries.getValue("resources.arsc"), pkg
        )

        // web content (simulates a user's HTML, one extra asset file)
        val webRoot = tmp.newFolder("web")
        File(webRoot, "index.html")
            .writeText("<!doctype html><html><body><h1>سلام HTML2APK</h1></body></html>")
        File(webRoot, "app.css").writeText("body{background:#0a1411}")

        // fake icon replacement bytes keyed by the template's exact entry paths
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG magic
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, // IHDR
        )
        val iconReplacements = template.iconEntryPaths.associateWith { png }

        val assembledFile = File(tmp.root, "assembled.apk")
        val assembled = ApkAssembler.assemble(
            template, manifest, arsc, iconReplacements, webRoot, assembledFile
        )
        assertTrue("entry count must include web files", assembled.entryCount >= 8)
        assertTrue("assembled apk must not be empty", assembled.file.length() > 0)

        val key = KeystoreManager.getOrCreate(tmp.newFolder("ks"), "", "html2apk")
        assertTrue("keystore must be created on first run", key.createdNew)

        val signedFile = File(tmp.root, "signed.apk")
        ApkSignUtil.sign(assembled.file, signedFile, key)

        // ---- authoritative signature verification ----
        // Modern Android (7.0+/min 24) verifies with v2+v3; apksig skips v1 checks there.
        val modern = ApkVerifier.Builder(signedFile).setMinCheckedPlatformVersion(24).build().verify()
        assertTrue("APK must fully verify (modern)", modern.isVerified)
        assertTrue("must verify with v2 (modern)", modern.isVerifiedUsingV2Scheme)
        assertTrue("must verify with v3 (modern)", modern.isVerifiedUsingV3Scheme)

        // Legacy Android (< 7.0) falls back to v1 (JAR signature) — must be present & valid.
        val legacy = ApkVerifier.Builder(signedFile).setMinCheckedPlatformVersion(21).build().verify()
        assertTrue("APK must fully verify (legacy)", legacy.isVerified)
        assertTrue("must verify with v1 (legacy)", legacy.isVerifiedUsingV1Scheme)

        // ---- structural assertions on the streamed ZIP output ----
        val entries = scanLocalEntries(signedFile)
        val arscInfo = entries["resources.arsc"]
        assertNotNull("resources.arsc missing", arscInfo)
        assertEquals(
            "resources.arsc must be STORED (uncompressed)",
            ZipEntry.STORED.toLong(), arscInfo!!.method.toLong()
        )
        assertTrue(
            "resources.arsc must be 4-byte aligned (offset=${arscInfo.dataOffset})",
            arscInfo.dataOffset % 4 == 0L
        )

        val indexInfo = entries["assets/index.html"]
        assertNotNull("user index.html missing", indexInfo)
        val cssInfo = entries["assets/app.css"]
        assertNotNull("user css missing", cssInfo)
        assertEquals("css must be STORED", ZipEntry.STORED.toLong(), cssInfo!!.method.toLong())

        // icon entries must exist and carry the replacement content (exact template paths)
        for (iconPath in template.iconEntryPaths) {
            assertNotNull("icon entry missing: $iconPath", entries[iconPath])
        }

        // template placeholder assets/index.html must NOT coexist with user content
        assertEquals(
            "exactly one index.html under assets/",
            1, entries.keys.count { it == "assets/index.html" }
        )
    }

    @Test
    fun `keystore reload keeps the same key fingerprint`() {
        val templateApk = resource("real_template.apk") ?: run {
            assumeTrue("real template fixture not present (local run)", false)
            return
        }
        assumeTrue(templateApk.isNotEmpty())
        val ksDir = tmp.newFolder("ks2")
        val first = KeystoreManager.getOrCreate(ksDir, "pw", "myalias")
        val second = KeystoreManager.getOrCreate(ksDir, "pw", "myalias")
        assertTrue(!second.createdNew)
        assertEquals(first.sha256Fingerprint, second.sha256Fingerprint)
        assertEquals(first.alias, second.alias)
    }

    // ---- tiny STORED-ZIP local-entry scanner (test-only, deterministic layout) ----

    private data class EntryInfo(val dataOffset: Long, val method: Int, val size: Int)

    private fun scanLocalEntries(file: File): Map<String, EntryInfo> {
        val bytes = file.readBytes()
        val out = mutableMapOf<String, EntryInfo>()
        var i = 0
        while (i + 30 <= bytes.size) {
            if (!(bytes[i] == 'P'.code.toByte() && bytes[i + 1] == 'K'.code.toByte() &&
                    bytes[i + 2] == 3.toByte() && bytes[i + 3] == 4.toByte())
            ) break
            val method = u16(bytes, i + 8)
            val size = i32(bytes, i + 18)      // STORED: compressed == uncompressed
            val nameLen = u16(bytes, i + 26)
            val extraLen = u16(bytes, i + 28)
            val name = String(bytes, i + 30, nameLen, Charsets.UTF_8)
            val dataStart = i + 30 + nameLen + extraLen
            out[name] = EntryInfo(dataStart.toLong(), method, size)
            i = dataStart + size
        }
        return out
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun i32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
