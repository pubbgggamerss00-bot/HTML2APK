package ir.html2apk.app.buildengine

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Thrown when a ZIP entry tries to escape the extraction directory (Zip-Slip attack). */
class ZipSlipException(entryName: String) :
    SecurityException("ورودی ناامن در ZIP: $entryName")

/**
 * Hardened ZIP extractor: blocks Zip-Slip (path traversal), absolute paths,
 * windows-style separators, oversized archives and zip bombs.
 * Pure JVM (java.util.zip) so it is unit-testable.
 */
object ZipUtils {

    // v3.4.0 limits: «حداکثر ۲۵۶ مگابایت — index.html باید در ریشه ZIP باشد»
    const val MAX_ENTRIES = 10000
    const val MAX_TOTAL_BYTES: Long = 256L * 1024 * 1024
    const val MAX_SINGLE_FILE_BYTES: Long = 256L * 1024 * 1024

    /** Extracts [input] into [targetDir]; returns the list of extracted file names. */
    fun extract(
        input: InputStream,
        targetDir: File,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ): List<String> {
        val extracted = mutableListOf<String>()
        val canonicalRoot = targetDir.canonicalPath + File.separator
        var total = 0L
        var count = 0

        ZipInputStream(BufferedInputStream(input)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name ?: ""
                validateName(name)

                val target = File(targetDir, name)
                val canonicalTarget = target.canonicalPath
                if (canonicalTarget != targetDir.canonicalPath &&
                    !canonicalTarget.startsWith(canonicalRoot)
                ) {
                    throw ZipSlipException(name)
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }

                count++
                if (count > MAX_ENTRIES) {
                    // v3.4.0 message: «تعداد ورودی‌های ZIP بیش از حد مجاز است (…)»
                    throw IllegalStateException("تعداد ورودی‌های ZIP بیش از حد مجاز است ($MAX_ENTRIES)")
                }

                target.parentFile?.mkdirs()
                var fileBytes = 0L
                FileOutputStream(target).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = zis.read(buf)
                        if (read <= 0) break
                        fileBytes += read
                        total += read
                        if (fileBytes > MAX_SINGLE_FILE_BYTES || total > maxTotalBytes) {
                            // v3.4.0 message: «حجم کل محتوای ZIP بیش از حد مجاز است (ZIP Bomb)»
                            throw IllegalStateException(
                                "حجم کل محتوای ZIP بیش از حد مجاز است (ZIP Bomb)"
                            )
                        }
                        fos.write(buf, 0, read)
                    }
                }
                extracted.add(name)
                zis.closeEntry()
            }
        }
        return extracted
    }

    private fun validateName(name: String) {
        if (name.isEmpty()) throw ZipSlipException(name)
        if (name.contains('\u0000')) throw ZipSlipException(name)
        if (name.contains('\\')) throw ZipSlipException(name)
        if (name.startsWith("/")) throw ZipSlipException(name)
        if (name.contains(':')) throw ZipSlipException(name)
        val segments = name.split('/')
        if (segments.any { it == ".." }) throw ZipSlipException(name)
    }

    /**
     * Finds the directory that should be used as the web root:
     * the ZIP root itself, or — if the ZIP wraps everything in a single folder —
     * that folder, provided it contains index.html. Returns null when missing.
     */
    fun findRootWithIndexHtml(extractDir: File): File? {
        if (File(extractDir, "index.html").isFile) return extractDir
        val dirs = extractDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (dirs.size == 1 && File(dirs[0], "index.html").isFile) return dirs[0]
        return null
    }
}
