package ir.html2apk.app.buildengine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * Minimal ZIP writer used to rebuild the output APK.
 *
 * Every entry is written STORED (uncompressed) and 4-byte aligned via an extra field —
 * this satisfies the Android 11+ requirement that `resources.arsc` must be stored
 * uncompressed and aligned (zipalign -v 4 equivalent), without needing any external tool.
 */
class ApkZipWriter(private val out: OutputStream) {

    private class Record(
        val name: ByteArray,
        val crc: Long,
        val size: Int,
        val offset: Long,
        val extraLen: Int,
    )

    private val records = mutableListOf<Record>()
    private var bytesWritten = 0L

    fun putEntry(name: String, data: ByteArray) {
        val nameBytes = prepareEntryName(name)

        val crc = CRC32()
        crc.update(data)
        val crcValue = crc.value

        val localOffset = writeLocalHeader(nameBytes, crcValue, data.size)
        out.write(data)
        bytesWritten += 30L + nameBytes.size + extraLenOfLast + data.size
        records.add(Record(nameBytes, crcValue, data.size, localOffset, extraLenOfLast))
    }

    /**
     * STORED entry streamed straight from a file — never loads the whole content in RAM.
     * Two passes over the file: pass 1 computes CRC + size (needed *before* the local
     * header in a STORED, descriptor-less entry), pass 2 streams the bytes out.
     */
    fun putEntryFile(name: String, file: File) {
        val nameBytes = prepareEntryName(name)
        require(file.isFile) { "entry source is not a file: $file" }
        require(file.length() <= Int.MAX_VALUE) { "entry too large for a single APK entry: $file" }

        // pass 1 — CRC + size
        val crc = CRC32()
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                crc.update(buf, 0, r)
            }
        }
        val crcValue = crc.value
        val size = file.length().toInt()

        val localOffset = writeLocalHeader(nameBytes, crcValue, size)

        // pass 2 — stream data
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                out.write(buf, 0, r)
            }
        }
        bytesWritten += 30L + nameBytes.size + extraLenOfLast + size
        records.add(Record(nameBytes, crcValue, size, localOffset, extraLenOfLast))
    }

    private fun prepareEntryName(name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= 255) { "zip entry name too long: $name" }
        require(nameBytes.isNotEmpty()) { "zip entry name must not be empty" }
        return nameBytes
    }

    private var extraLenOfLast: Int = 0

    /** Writes the local file header + alignment padding; returns the entry's local offset. */
    private fun writeLocalHeader(nameBytes: ByteArray, crcValue: Long, size: Int): Long {
        val base = bytesWritten + 30 + nameBytes.size
        var extraLen = 0
        var pad = 0
        if (base % 4 != 0L) {
            pad = ((4 - ((base + 4) % 4)) % 4).toInt()
            extraLen = 4 + pad // 2-byte id + 2-byte size + pad payload
        }
        val dataStart = base + extraLen
        check(dataStart % 4 == 0L) { "alignment calculation failed" }
        extraLenOfLast = extraLen

        val localOffset = bytesWritten

        val header = ByteArrayOutputStream(30)
        header.write(le32(0x04034B50))  // local file header signature
        header.write(le16(20))          // version needed to extract
        header.write(le16(0))           // general purpose flags (no descriptor)
        header.write(le16(0))           // compression method: STORED
        header.write(le16(DOS_TIME))
        header.write(le16(DOS_DATE))
        header.write(le32(crcValue.toInt()))
        header.write(le32(size))        // compressed size
        header.write(le32(size))        // uncompressed size
        header.write(le16(nameBytes.size))
        header.write(le16(extraLen))
        out.write(header.toByteArray())
        out.write(nameBytes)

        if (extraLen > 0) {
            val extra = ByteArray(extraLen)
            // extra field id 0xD935 (android zipalign), size = pad, payload zeros
            extra[0] = 0x35
            extra[1] = 0xD9.toByte()
            extra[2] = (pad and 0xFF).toByte()
            extra[3] = ((pad shr 8) and 0xFF).toByte()
            out.write(extra)
        }
        return localOffset
    }

    fun finish() {
        val cdStart = bytesWritten
        var cdSize = 0L
        for (r in records) {
            // NOTE: bytesWritten is intentionally not touched here (it tracks the
            // local-header region); the CD size is counted explicitly, because the
            // EOCD below must declare it correctly — a wrong value makes apksig
            // (and the whole platform) reject the APK as a malformed ZIP.
            cdSize += 46L + r.name.size + r.extraLen
            val cd = ByteArrayOutputStream(46)
            cd.write(le32(0x02014B50))  // central directory signature
            cd.write(le16(20))          // version made by
            cd.write(le16(20))          // version needed
            cd.write(le16(0))           // flags
            cd.write(le16(0))           // method STORED
            cd.write(le16(DOS_TIME))
            cd.write(le16(DOS_DATE))
            cd.write(le32(r.crc.toInt()))
            cd.write(le32(r.size))
            cd.write(le32(r.size))
            cd.write(le16(r.name.size))
            cd.write(le16(r.extraLen))
            cd.write(le16(0))           // comment len
            cd.write(le16(0))           // disk number start
            cd.write(le16(0))           // internal attrs
            cd.write(le32(0))           // external attrs
            cd.write(le32(r.offset.toInt()))
            out.write(cd.toByteArray())
            out.write(r.name)
            if (r.extraLen > 0) {
                val extra = ByteArray(r.extraLen)
                extra[0] = 0x35
                extra[1] = 0xD9.toByte()
                val pad = r.extraLen - 4
                extra[2] = (pad and 0xFF).toByte()
                extra[3] = ((pad shr 8) and 0xFF).toByte()
                out.write(extra)
            }
        }

        val eocd = ByteArrayOutputStream(22)
        eocd.write(le32(0x06054B50))
        eocd.write(le16(0))                 // number of this disk
        eocd.write(le16(0))                 // disk with cd
        eocd.write(le16(records.size))
        eocd.write(le16(records.size))
        eocd.write(le32(cdSize.toInt()))
        eocd.write(le32(cdStart.toInt()))
        eocd.write(le16(0))                 // comment len
        out.write(eocd.toByteArray())
        out.flush()
    }

    companion object {
        // fixed timestamp: 2024-01-01 12:00 (deterministic output)
        private const val DOS_DATE = ((2024 - 1980) shl 9) or (1 shl 5) or 1
        private const val DOS_TIME = (12 shl 11)

        private fun le16(v: Int): ByteArray =
            byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

        private fun le32(v: Int): ByteArray = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
    }
}
