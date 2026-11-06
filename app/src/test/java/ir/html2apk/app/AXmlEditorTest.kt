package ir.html2apk.app

import ir.html2apk.app.buildengine.AXml
import ir.html2apk.app.buildengine.AXmlEditor
import ir.html2apk.app.buildengine.ArscPatcher
import ir.html2apk.app.buildengine.ManifestPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * AXML tests.
 *
 * 1. Round-trip tests against an independently hand-built binary XML fixture
 *    (built here directly from the AOSP ResStringPool / ResXMLTree spec).
 * 2. Tests against the *real* aapt2 output of the template module: CI extracts
 *    AndroidManifest.xml + resources.arsc from the built template APK into
 *    app/src/test/resources (files real_template_manifest.bin / real_resources_arsc.bin);
 *    locally (absent fixture) these tests skip.
 */
class AXmlEditorTest {

    // ------------------------------------------------------------------
    // Independent fixture builder (does NOT reuse the production serializer)
    // ------------------------------------------------------------------

    private class Fixture {
        val strings = mutableListOf<String>()
        val ids = mutableListOf<Int>()

        fun str(s: String, id: Int = 0): Int {
            val i = strings.indexOf(s)
            if (i >= 0) return i
            strings.add(s)
            ids.add(id)
            return strings.size - 1
        }

        fun pool(): ByteArray {
            val data = ByteArrayOutputStream()
            val offsets = IntArray(strings.size)
            strings.forEachIndexed { i, s ->
                offsets[i] = data.size()
                val u8 = s.toByteArray(Charsets.UTF_8)
                // utf16 length varint
                writeVar(data, s.length)
                writeVar(data, u8.size)
                data.write(u8)
                data.write(0)
            }
            while (data.size() % 4 != 0) data.write(0)

            val out = ByteArrayOutputStream()
            out.write(le16(0x0001))
            out.write(le16(28))
            out.write(le32(28 + 4 * strings.size + data.size()))
            out.write(le32(strings.size))
            out.write(le32(0))          // styleCount
            out.write(le32(0x100))      // UTF8_FLAG
            out.write(le32(28 + 4 * strings.size))
            out.write(le32(0))
            for (o in offsets) out.write(le32(o))
            out.write(data.toByteArray())
            return out.toByteArray()
        }

        private fun writeVar(out: ByteArrayOutputStream, v: Int) {
            if (v < 0x80) out.write(v) else {
                out.write((v shr 8) or 0x80)
                out.write(v and 0xFF)
            }
        }

        fun resourceMap(): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(le16(0x0180))
            out.write(le16(8))
            out.write(le32(8 + ids.size * 4))
            for (id in ids) out.write(le32(id))
            return out.toByteArray()
        }
    }

    private fun le16(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun nodeHeader(type: Int, size: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(le16(type))
        out.write(le16(if (type == 0x0102) 36 else 24))
        out.write(le32(size))
        out.write(le32(0))   // line
        out.write(le32(-1))  // comment
        return out.toByteArray()
    }

    private fun attr(nsIdx: Int, nameIdx: Int, rawIdx: Int, type: Int, data: Int): ByteArray {
        val out = ByteArrayOutputStream(20)
        out.write(le32(nsIdx))
        out.write(le32(nameIdx))
        out.write(le32(rawIdx))
        out.write(le16(8))          // Res_value.size
        out.write(0)                // res0
        out.write(type)
        out.write(le32(data))
        return out.toByteArray()
    }

    /** Builds a compiled manifest mirroring the template module's source manifest. */
    private fun buildFixture(): ByteArray {
        val f = Fixture()
        val prefix = f.str("android")
        val uri = f.str(AXml.ANDROID_NS)
        val manifest = f.str("manifest")
        val package_ = f.str("package")
        val templatePkg = f.str("ir.html2apk.template")
        val versionCode = f.str("versionCode", AXml.ANDROID_VERSION_CODE)
        val versionName = f.str("versionName", AXml.ANDROID_VERSION_NAME)
        val v10 = f.str("1.0")
        val usesPerm = f.str("uses-permission")
        val internet = f.str(AXml.INTERNET_PERMISSION)
        val name = f.str("name", AXml.ANDROID_NAME)
        val application = f.str("application")
        val label = f.str("label", AXml.ANDROID_LABEL)
        val htmlApp = f.str("HTML App")
        val activity = f.str("activity")
        val mainActivity = f.str("ir.html2apk.template.MainActivity")
        val orientation = f.str("screenOrientation", AXml.ANDROID_SCREEN_ORIENTATION)
        val exported = f.str("exported", 0x01010010)
        val trueStr = f.str("true")

        val body = ByteArrayOutputStream()

        // start namespace
        body.write(nodeHeader(0x0100, 24))
        body.write(le32(prefix))
        body.write(le32(uri))

        // manifest start
        val manifestAttrs = ByteArrayOutputStream().apply {
            write(attr(-1, package_, templatePkg, AXml.TYPE_STRING, templatePkg))
            write(attr(uri, versionCode, -1, AXml.TYPE_INT_DEC, 1))
            write(attr(uri, versionName, v10, AXml.TYPE_STRING, v10))
        }
        body.write(nodeHeader(0x0102, 36 + 20 * 3))
        body.write(le32(-1))
        body.write(le32(manifest))
        body.write(le16(20))
        body.write(le16(20))
        body.write(le16(3))
        body.write(le16(0))
        body.write(le16(0))
        body.write(le16(0))
        body.write(manifestAttrs.toByteArray())

        // uses-permission
        val permAttrs = ByteArrayOutputStream().apply {
            write(attr(uri, name, internet, AXml.TYPE_STRING, internet))
        }
        body.write(nodeHeader(0x0102, 36 + 20))
        body.write(le32(-1))
        body.write(le32(usesPerm))
        body.write(le16(20))
        body.write(le16(20))
        body.write(le16(1))
        body.write(le16(0))
        body.write(le16(0))
        body.write(le16(0))
        body.write(permAttrs.toByteArray())
        body.write(nodeHeader(0x0103, 24))
        body.write(le32(-1))
        body.write(le32(usesPerm))

        // application start
        val appAttrs = ByteArrayOutputStream().apply {
            write(attr(uri, label, htmlApp, AXml.TYPE_STRING, htmlApp))
        }
        body.write(nodeHeader(0x0102, 36 + 20))
        body.write(le32(-1))
        body.write(le32(application))
        body.write(le16(20))
        body.write(le16(20))
        body.write(le16(1))
        body.write(le16(0))
        body.write(le16(0))
        body.write(le16(0))
        body.write(appAttrs.toByteArray())

        // activity start
        val actAttrs = ByteArrayOutputStream().apply {
            write(attr(uri, name, mainActivity, AXml.TYPE_STRING, mainActivity))
            write(attr(uri, orientation, -1, AXml.TYPE_INT_DEC, 1))
            write(attr(uri, exported, trueStr, 0x12, -1))
        }
        body.write(nodeHeader(0x0102, 36 + 20 * 3))
        body.write(le32(-1))
        body.write(le32(activity))
        body.write(le16(20))
        body.write(le16(20))
        body.write(le16(3))
        body.write(le16(0))
        body.write(le16(0))
        body.write(le16(0))
        body.write(actAttrs.toByteArray())

        // end application, end manifest, end namespace
        body.write(nodeHeader(0x0103, 24))
        body.write(le32(-1))
        body.write(le32(application))
        body.write(nodeHeader(0x0103, 24))
        body.write(le32(-1))
        body.write(le32(manifest))
        body.write(nodeHeader(0x0101, 24))
        body.write(le32(prefix))
        body.write(le32(uri))

        val total = 8 + f.pool().size + f.resourceMap().size + body.size()
        val out = ByteArrayOutputStream(total)
        out.write(le16(0x0003))
        out.write(le16(8))
        out.write(le32(total))
        out.write(f.pool())
        out.write(f.resourceMap())
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // helpers to read attributes back from a parsed document
    // ------------------------------------------------------------------

    private fun findElement(doc: AXml.Document, name: String): AXml.Node.StartElement =
        doc.nodes.filterIsInstance<AXml.Node.StartElement>()
            .first { doc.strings.getOrNull(it.nameIdx) == name }

    private fun stringAttr(
        doc: AXml.Document,
        element: AXml.Node.StartElement,
        attrName: String,
        namespaced: Boolean,
    ): String? {
        val nsIdx = doc.stringIndex(AXml.ANDROID_NS)
        val a = element.attrs.firstOrNull {
            (if (namespaced) it.nsIdx == nsIdx else it.nsIdx == -1) &&
                doc.strings.getOrNull(it.nameIdx) == attrName
        } ?: return null
        val v = a.valueData
        return if (v in doc.strings.indices) doc.strings[v] else null
    }

    private fun intAttr(
        doc: AXml.Document,
        element: AXml.Node.StartElement,
        attrName: String,
    ): Int? {
        val nsIdx = doc.stringIndex(AXml.ANDROID_NS)
        val a = element.attrs.firstOrNull {
            it.nsIdx == nsIdx && doc.strings.getOrNull(it.nameIdx) == attrName
        } ?: return null
        return a.valueData
    }

    // ------------------------------------------------------------------
    // round-trip on the hand-built fixture
    // ------------------------------------------------------------------

    @Test
    fun `parses hand-built fixture`() {
        val doc = AXml.parse(buildFixture())
        val manifest = findElement(doc, "manifest")
        assertEquals("ir.html2apk.template", stringAttr(doc, manifest, "package", namespaced = false))
        assertEquals(1, intAttr(doc, manifest, "versionCode"))
        assertEquals("1.0", stringAttr(doc, manifest, "versionName", namespaced = true))
        assertEquals("HTML App", stringAttr(doc, findElement(doc, "application"), "label", namespaced = true))
        assertEquals(1, intAttr(doc, findElement(doc, "activity"), "screenOrientation"))
    }

    @Test
    fun `serialize parse round trip is stable`() {
        val original = buildFixture()
        val once = AXml.serialize(AXml.parse(original))
        val twice = AXml.serialize(AXml.parse(once))
        assertTrue(once.contentEquals(twice))
    }

    @Test
    fun `patchManifest applies all changes`() {
        val patched = AXmlEditor.patchManifest(
            buildFixture(),
            ManifestPatch(
                packageName = "com.example.myapp",
                versionCode = 7,
                versionName = "2.5.1",
                label = "My App",
                screenOrientation = 0,
                internetPermission = false,
            )
        )
        val doc = AXml.parse(patched)
        val manifest = findElement(doc, "manifest")
        assertEquals("com.example.myapp", stringAttr(doc, manifest, "package", namespaced = false))
        assertEquals(7, intAttr(doc, manifest, "versionCode"))
        assertEquals("2.5.1", stringAttr(doc, manifest, "versionName", namespaced = true))
        assertEquals("My App", stringAttr(doc, findElement(doc, "application"), "label", namespaced = true))
        assertEquals(0, intAttr(doc, findElement(doc, "activity"), "screenOrientation"))
        // uses-permission INTERNET removed
        val permCount = doc.nodes.count {
            it is AXml.Node.StartElement && doc.strings.getOrNull(it.nameIdx) == "uses-permission"
        }
        assertEquals(0, permCount)
    }

    @Test
    fun `patchManifest keeps other strings intact`() {
        val patched = AXmlEditor.patchManifest(
            buildFixture(),
            ManifestPatch(
                packageName = "a.b",
                versionCode = 2,
                versionName = "2.0",
                label = "X",
                screenOrientation = -1,
                internetPermission = true,
            )
        )
        val doc = AXml.parse(patched)
        // activity name must survive
        assertEquals(
            "ir.html2apk.template.MainActivity",
            stringAttr(doc, findElement(doc, "activity"), "name", namespaced = true)
        )
        // permission kept
        val perm = findElement(doc, "uses-permission")
        assertEquals(AXml.INTERNET_PERMISSION, stringAttr(doc, perm, "name", namespaced = true))
    }

    @Test
    fun `resource map is preserved and extendable`() {
        val patched = AXmlEditor.patchManifest(
            buildFixture(),
            ManifestPatch("a.b", 1, "1.0", "L", -1, true)
        )
        val doc = AXml.parse(patched)
        assertTrue(doc.resourceIds.isNotEmpty())
        // versionCode id must still be mapped
        val vcIdx = doc.stringIndex("versionCode")
        if (vcIdx in doc.resourceIds.indices) {
            assertEquals(AXml.ANDROID_VERSION_CODE, doc.resourceIds[vcIdx])
        }
    }

    // ------------------------------------------------------------------
    // real aapt2 output (CI fixture — skipped when absent)
    // ------------------------------------------------------------------

    private fun resource(name: String): ByteArray? =
        javaClass.getResourceAsStream("/$name")?.use { it.readBytes() }

    @Test
    fun `real template manifest parses and patches`() {
        val bytes = resource("real_template_manifest.bin") ?: run {
            assumeTrue("real manifest fixture not present (local run)", false)
            return
        }
        val doc = AXml.parse(bytes)
        val manifest = findElement(doc, "manifest")
        assertEquals("ir.html2apk.template", stringAttr(doc, manifest, "package", namespaced = false))
        assertEquals(1, intAttr(doc, manifest, "versionCode"))

        val patched = AXmlEditor.patchManifest(
            bytes,
            ManifestPatch("com.example.real", 42, "9.9", "Real", 0, false)
        )
        val doc2 = AXml.parse(patched)
        val manifest2 = findElement(doc2, "manifest")
        assertEquals("com.example.real", stringAttr(doc2, manifest2, "package", namespaced = false))
        assertEquals(42, intAttr(doc2, manifest2, "versionCode"))
        assertEquals("9.9", stringAttr(doc2, manifest2, "versionName", namespaced = true))
        assertEquals("Real", stringAttr(doc2, findElement(doc2, "application"), "label", namespaced = true))
        assertEquals(0, intAttr(doc2, findElement(doc2, "activity"), "screenOrientation"))
        val permCount = doc2.nodes.count {
            it is AXml.Node.StartElement && doc2.strings.getOrNull(it.nameIdx) == "uses-permission"
        }
        assertEquals(0, permCount)
    }

    @Test
    fun `real resources arsc package patched without resizing`() {
        val bytes = resource("real_resources_arsc.bin") ?: run {
            assumeTrue("real arsc fixture not present (local run)", false)
            return
        }
        val patched = ArscPatcher.patchPackageName(bytes, "com.example.real")
        assertEquals(bytes.size, patched.size)

        // read the name back
        val bb = java.nio.ByteBuffer.wrap(patched).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val headerSize = bb.getShort(2).toInt() and 0xFFFF
        val nameStart = headerSize + 12
        val sb = StringBuilder()
        for (i in 0 until 128) {
            val c = bb.getChar(nameStart + i * 2)
            if (c.code == 0) break
            sb.append(c)
        }
        assertEquals("com.example.real", sb.toString())
    }
}
