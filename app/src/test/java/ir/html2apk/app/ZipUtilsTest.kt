package ir.html2apk.app

import ir.html2apk.app.buildengine.ZipSlipException
import ir.html2apk.app.buildengine.ZipUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun `extracts a normal zip`() {
        val bytes = zipOf(
            mapOf(
                "index.html" to "<h1>hi</h1>",
                "css/style.css" to "body{}",
                "js/app.js" to "console.log(1)",
            )
        )
        val dir = tmp.newFolder("ok")
        val files = ZipUtils.extract(ByteArrayInputStream(bytes), dir)

        assertEquals(setOf("index.html", "css/style.css", "js/app.js"), files.toSet())
        assertEquals("<h1>hi</h1>", java.io.File(dir, "index.html").readText())
        assertEquals("body{}", java.io.File(dir, "css/style.css").readText())
    }

    @Test
    fun `blocks path traversal entry`() {
        val bytes = zipOf(mapOf("../evil.txt" to "boom"))
        val dir = tmp.newFolder("traversal")
        try {
            ZipUtils.extract(ByteArrayInputStream(bytes), dir)
            assertTrue("ZipSlipException expected", false)
        } catch (e: ZipSlipException) {
            assertTrue(e.message!!.contains("../evil.txt"))
        }
    }

    @Test
    fun `blocks nested path traversal entry`() {
        val bytes = zipOf(mapOf("a/b/../../../evil.txt" to "boom"))
        val dir = tmp.newFolder("nested")
        try {
            ZipUtils.extract(ByteArrayInputStream(bytes), dir)
            assertTrue("ZipSlipException expected", false)
        } catch (e: ZipSlipException) {
        }
    }

    @Test
    fun `blocks absolute path entry`() {
        val bytes = zipOf(mapOf("/etc/passwd" to "boom"))
        val dir = tmp.newFolder("abs")
        try {
            ZipUtils.extract(ByteArrayInputStream(bytes), dir)
            assertTrue("ZipSlipException expected", false)
        } catch (e: ZipSlipException) {
        }
    }

    @Test
    fun `blocks windows style separator`() {
        val bytes = zipOf(mapOf("a\\evil.txt" to "boom"))
        val dir = tmp.newFolder("win")
        try {
            ZipUtils.extract(ByteArrayInputStream(bytes), dir)
            assertTrue("ZipSlipException expected", false)
        } catch (e: ZipSlipException) {
        }
    }

    @Test
    fun `blocks colon entry`() {
        val bytes = zipOf(mapOf("C:evil.txt" to "boom"))
        val dir = tmp.newFolder("colon")
        try {
            ZipUtils.extract(ByteArrayInputStream(bytes), dir)
            assertTrue("ZipSlipException expected", false)
        } catch (e: ZipSlipException) {
        }
    }

    @Test
    fun `finds root when index at zip root`() {
        val bytes = zipOf(mapOf("index.html" to "<p/>", "app.js" to ""))
        val dir = tmp.newFolder("root1")
        ZipUtils.extract(ByteArrayInputStream(bytes), dir)
        assertEquals(dir, ZipUtils.findRootWithIndexHtml(dir))
    }

    @Test
    fun `finds single top level folder containing index`() {
        val bytes = zipOf(mapOf("MySite/index.html" to "<p/>", "MySite/style.css" to ""))
        val dir = tmp.newFolder("root2")
        ZipUtils.extract(ByteArrayInputStream(bytes), dir)
        val root = ZipUtils.findRootWithIndexHtml(dir)
        assertNotNull(root)
        assertEquals("MySite", root!!.name)
    }

    @Test
    fun `returns null when index missing`() {
        val bytes = zipOf(mapOf("a.html" to "<p/>", "b/index.html" to "<p/>"))
        val dir = tmp.newFolder("root3")
        ZipUtils.extract(ByteArrayInputStream(bytes), dir)
        assertNull(ZipUtils.findRootWithIndexHtml(dir))
    }
}
