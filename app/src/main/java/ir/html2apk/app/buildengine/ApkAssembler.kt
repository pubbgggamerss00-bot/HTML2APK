package ir.html2apk.app.buildengine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Patches the package name inside `resources.arsc` in place.
 *
 * The ResTable_package chunk's `name` field is a fixed-size char16[128] array —
 * overwriting it never shifts any other structure. The package chunk is located by
 * walking the chunk chain after the table header (modern aapt2 emits a global
 * string pool chunk between the table header and the first package chunk).
 */
object ArscPatcher {

    fun patchPackageName(arsc: ByteArray, newPackage: String): ByteArray {
        val out = arsc.copyOf()
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        val tableType = buf.getShort(0).toInt() and 0xFFFF
        require(tableType == 0x0002) { "invalid resources.arsc (type=0x%04X)".format(tableType) }
        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        val tableSize = buf.getInt(4)
        require(tableSize <= out.size) { "resources.arsc size field out of bounds" }

        // walk chunks after the table header looking for ResTable_package (0x0200)
        var pkgStart = -1
        var pos = headerSize
        while (pos + 8 <= tableSize) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > tableSize) break
            if (type == 0x0200) {
                pkgStart = pos
                break
            }
            pos += chunkSize
        }
        require(pkgStart != -1) { "unexpected resources.arsc layout (no package chunk)" }

        // ResTable_package: header(8) + id(4) + name char16[128] (256 bytes)
        val nameStart = pkgStart + 12
        require(nameStart + 256 <= out.size) { "resources.arsc too small" }

        val chars = newPackage.toCharArray()
        require(chars.size <= 128) { "package name too long" }
        for (i in 0 until 128) {
            buf.putChar(nameStart + i * 2, if (i < chars.size) chars[i] else 0.toChar())
        }
        return out
    }
}

/**
 * Rebuilds the template APK into the final unsigned (but aligned) APK:
 *  - patches the compiled AndroidManifest.xml (package/version/label/orientation/permission)
 *  - patches resources.arsc package name
 *  - replaces mipmap icon entries with the user's icon (exact entry paths taken from
 *    the template's own ZIP listing — no filename guessing)
 *  - replaces assets/index.html and adds the user's other web files under assets/
 *  - strips any pre-existing META-INF signature files
 */
object ApkAssembler {

    const val ICON_ENTRY_REGEX = "^res/mipmap-(mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi)/ic_launcher\\.png$"

    data class TemplateInfo(
        val entries: LinkedHashMap<String, ByteArray>,
        val iconEntryPaths: List<String>,
    )

    /** Reads the template APK once and returns its entries (META-INF stripped). */
    fun readTemplate(templateApk: ByteArray): TemplateInfo {
        val entries = LinkedHashMap<String, ByteArray>()
        val iconPaths = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(templateApk)).use { zis ->
            while (true) {
                val entry: ZipEntry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.startsWith("META-INF/")) continue
                entries[name] = zis.readBytes()
                if (Regex(ICON_ENTRY_REGEX).matches(name)) iconPaths.add(name)
                zis.closeEntry()
            }
        }
        require(entries.containsKey("AndroidManifest.xml")) { "template APK lacks AndroidManifest.xml" }
        require(entries.containsKey("classes.dex")) { "template APK lacks classes.dex" }
        return TemplateInfo(entries, iconPaths)
    }

    data class AssembleResult(
        val bytes: ByteArray,
        val entryCount: Int,
        val totalBytes: Long,
    )

    fun assemble(
        template: TemplateInfo,
        manifestBytes: ByteArray,
        arscBytes: ByteArray,
        iconReplacements: Map<String, ByteArray>,
        webRoot: File,
    ): AssembleResult {
        val out = ByteArrayOutputStream((template.entries.values.sumOf { it.size } + 1) shl 16)
        var entryCount = 0

        ApkZipWriter(out).let { writer ->
            // 1) patched manifest
            writer.putEntry("AndroidManifest.xml", manifestBytes)
            entryCount++

            // 2) patched resources.arsc (STORED + aligned — required on Android 11+)
            if (template.entries.containsKey("resources.arsc")) {
                writer.putEntry("resources.arsc", arscBytes)
                entryCount++
            }

            // 3) all remaining template entries (dex, res...). The placeholder
            //    assets/index.html is always replaced by the user's web content below.
            for ((name, data) in template.entries) {
                when (name) {
                    "AndroidManifest.xml", "resources.arsc", "assets/index.html" -> continue
                    in iconReplacements -> writer.putEntry(name, iconReplacements.getValue(name))
                    else -> writer.putEntry(name, data)
                }
                entryCount++
            }

            // 4) user's web content under assets/ (index.html replaces the placeholder)
            val files = webRoot.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(webRoot).path }
            var added = 0
            for (f in files) {
                val rel = f.relativeTo(webRoot).invariantSeparatorsPath
                writer.putEntry("assets/$rel", f.readBytes())
                added++
                entryCount++
            }
            check(added > 0) { "no web content to embed" }

            writer.finish()
        }

        val bytes = out.toByteArray()
        return AssembleResult(bytes, entryCount, bytes.size.toLong())
    }
}
