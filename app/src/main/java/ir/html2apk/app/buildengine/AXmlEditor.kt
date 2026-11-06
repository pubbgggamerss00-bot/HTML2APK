package ir.html2apk.app.buildengine

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Minimal editor for Android *compiled binary XML* (AndroidManifest.xml inside an APK).
 *
 * It parses the string pool (UTF-8 or UTF-16), the optional resource-id map and all
 * XML node chunks, applies patch operations and re-serializes the document.
 *
 * Design rules:
 *  - Existing string-pool indices are never changed; new strings are only appended
 *    (deduplicated), so every reference inside the document stays valid.
 *  - Attribute *names* that already exist in the template manifest keep their
 *    resource-id mapping; newly created android attribute names get their resource id
 *    appended to the RES_XML_RESOURCE_MAP chunk.
 */
object AXml {

    // ---- chunk types -------------------------------------------------------
    const val RES_STRING_POOL_TYPE = 0x0001
    const val RES_XML_TYPE = 0x0003
    const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    const val RES_XML_START_ELEMENT_TYPE = 0x0102
    const val RES_XML_END_ELEMENT_TYPE = 0x0103
    const val RES_XML_CDATA_TYPE = 0x0104

    const val UTF8_FLAG = 0x100

    // ---- Res_value data types ----------------------------------------------
    const val TYPE_STRING = 0x03
    const val TYPE_INT_DEC = 0x10

    // ---- well known android attribute resource ids -------------------------
    const val ANDROID_LABEL = 0x01010001
    const val ANDROID_NAME = 0x01010003
    const val ANDROID_SCREEN_ORIENTATION = 0x0101001E
    const val ANDROID_VERSION_CODE = 0x0101021B
    const val ANDROID_VERSION_NAME = 0x0101021C

    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    const val INTERNET_PERMISSION = "android.permission.INTERNET"

    // orientation enum values used by android:screenOrientation
    const val ORIENTATION_LANDSCAPE = 0
    const val ORIENTATION_PORTRAIT = 1
    const val ORIENTATION_UNSPECIFIED = -1

    // ======================================================================
    // Document model
    // ======================================================================

    class Attr(
        var nsIdx: Int,
        var nameIdx: Int,
        var rawValueIdx: Int,
        var valueType: Int,
        var valueData: Int,
    )

    sealed class Node {
        class StartNamespace(val prefixIdx: Int, val uriIdx: Int) : Node()
        class EndNamespace(val prefixIdx: Int, val uriIdx: Int) : Node()
        class StartElement(val nsIdx: Int, val nameIdx: Int, val attrs: MutableList<Attr>) : Node()
        class EndElement(val nsIdx: Int, val nameIdx: Int) : Node()
        class Raw(val bytes: ByteArray) : Node()
    }

    class Document {
        val strings = mutableListOf<String>()
        var resourceIds = IntArray(0)
        val nodes = mutableListOf<Node>()

        fun stringIndex(s: String): Int = strings.indexOf(s)

        fun stringIndexOrCreate(s: String): Int {
            val existing = strings.indexOf(s)
            if (existing >= 0) return existing
            strings.add(s)
            return strings.size - 1
        }
    }

    // ======================================================================
    // Parser
    // ======================================================================

    fun parse(bytes: ByteArray): Document {
        val doc = Document()
        val p = ParserState(bytes)

        val xmlType = p.u16(0)
        require(xmlType == RES_XML_TYPE) { "not a binary Android XML file (type=0x%04X)".format(xmlType) }
        val xmlSize = p.i32(4)
        val end = xmlSize
        require(end <= bytes.size) { "binary XML size out of bounds" }

        var chunkStart = 8
        while (chunkStart + 8 <= end) {
            val type = p.u16(chunkStart)
            val size = p.i32(chunkStart + 4)
            require(size >= 8 && chunkStart + size <= end) { "corrupt chunk at $chunkStart" }
            when (type) {
                RES_STRING_POOL_TYPE -> parseStringPool(p, chunkStart, doc)
                RES_XML_RESOURCE_MAP_TYPE -> {
                    val n = (size - 8) / 4
                    val ids = IntArray(n)
                    for (i in 0 until n) ids[i] = p.i32(chunkStart + 8 + 4 * i)
                    doc.resourceIds = ids
                }
                RES_XML_START_NAMESPACE_TYPE -> {
                    doc.nodes.add(Node.StartNamespace(p.i32(chunkStart + 16), p.i32(chunkStart + 20)))
                }
                RES_XML_END_NAMESPACE_TYPE -> {
                    doc.nodes.add(Node.EndNamespace(p.i32(chunkStart + 16), p.i32(chunkStart + 20)))
                }
                RES_XML_START_ELEMENT_TYPE -> parseStartElement(p, chunkStart, doc)
                RES_XML_END_ELEMENT_TYPE -> {
                    doc.nodes.add(Node.EndElement(p.i32(chunkStart + 16), p.i32(chunkStart + 20)))
                }
                else -> doc.nodes.add(Node.Raw(bytes.copyOfRange(chunkStart, chunkStart + size)))
            }
            chunkStart += size
        }
        return doc
    }

    private class ParserState(val bytes: ByteArray) {
        fun u16(offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        fun i32(offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        fun u16At(offset: Int): Int = u16(offset)
    }

    private fun parseStringPool(p: ParserState, chunkStart: Int, doc: Document) {
        val stringCount = p.i32(chunkStart + 8)
        val flags = p.i32(chunkStart + 16)
        val stringsStart = p.i32(chunkStart + 20)
        val utf8 = (flags and UTF8_FLAG) != 0
        val offsetsStart = chunkStart + 28
        val dataStart = chunkStart + stringsStart

        for (i in 0 until stringCount) {
            val off = p.i32(offsetsStart + 4 * i)
            var pos = dataStart + off
            if (utf8) {
                val r16 = readVarint8(p.bytes, pos)
                val u16len = r16.first
                val r8 = readVarint8(p.bytes, r16.second)
                val u8len = r8.first
                val start = r8.second
                doc.strings.add(String(p.bytes, start, u8len, Charset.forName("UTF-8")))
                // silence unused warning for u16len (informational only)
                check(u16len >= 0)
            } else {
                val r = readVarint16(p.bytes, pos)
                val u16len = r.first
                val start = r.second
                doc.strings.add(String(p.bytes, start, u16len * 2, Charset.forName("UTF-16LE")))
            }
        }
    }

    /** UTF-8 pool length varint: high bit of first byte => 2 bytes, big-endian-ish 15 bit. */
    private fun readVarint8(b: ByteArray, pos: Int): Pair<Int, Int> {
        val first = b[pos].toInt() and 0xFF
        if (first and 0x80 != 0) {
            return Pair(((first and 0x7F) shl 8) or (b[pos + 1].toInt() and 0xFF), pos + 2)
        }
        return Pair(first, pos + 1)
    }

    /** UTF-16 pool length varint (in 16-bit units): 0x8000 flag => 2 units. */
    private fun readVarint16(b: ByteArray, pos: Int): Pair<Int, Int> {
        val first = (b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8)
        if (first and 0x8000 != 0) {
            val second = (b[pos + 2].toInt() and 0xFF) or ((b[pos + 3].toInt() and 0xFF) shl 8)
            return Pair(((first and 0x7FFF) shl 16) or second, pos + 4)
        }
        return Pair(first, pos + 2)
    }

    private fun parseStartElement(p: ParserState, chunkStart: Int, doc: Document) {
        val ns = p.i32(chunkStart + 16)
        val name = p.i32(chunkStart + 20)
        val attrStart = p.u16(chunkStart + 24)   // offset from attrExt start to first attribute
        val attrSize = p.u16(chunkStart + 26)    // size of one attribute entry
        val attrCount = p.u16(chunkStart + 28)
        val attrsBase = chunkStart + 16 + attrStart
        val attrs = mutableListOf<Attr>()
        for (i in 0 until attrCount) {
            val base = attrsBase + i * attrSize
            attrs.add(
                Attr(
                    nsIdx = p.i32(base),
                    nameIdx = p.i32(base + 4),
                    rawValueIdx = p.i32(base + 8),
                    valueType = p.bytes[base + 15].toInt() and 0xFF,
                    valueData = p.i32(base + 16),
                )
            )
        }
        doc.nodes.add(Node.StartElement(ns, name, attrs))
    }

    // ======================================================================
    // Serializer
    // ======================================================================

    fun serialize(doc: Document): ByteArray {
        val pool = serializeStringPool(doc.strings)
        val map = if (doc.resourceIds.isNotEmpty()) serializeResourceMap(doc.resourceIds) else ByteArray(0)
        val nodes = serializeNodes(doc.nodes)
        val total = 8 + pool.size + map.size + nodes.size
        val out = ByteArrayOutputStream(total)
        out.write(le16(RES_XML_TYPE))
        out.write(le16(8)) // headerSize
        out.write(le32(total))
        out.write(pool)
        out.write(map)
        out.write(nodes)
        return out.toByteArray()
    }

    private fun serializeStringPool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = data.size()
            val u8 = s.toByteArray(Charsets.UTF_8)
            writeVarint8(data, s.length)      // utf16 length
            writeVarint8(data, u8.size)       // utf8 byte length
            data.write(u8)
            data.write(0)
        }
        while (data.size() % 4 != 0) data.write(0)

        val stringsStart = 28 + 4 * strings.size
        val chunkSize = stringsStart + data.size()
        val out = ByteArrayOutputStream(chunkSize)
        out.write(le16(RES_STRING_POOL_TYPE))
        out.write(le16(28))                   // headerSize
        out.write(le32(chunkSize))
        out.write(le32(strings.size))         // stringCount
        out.write(le32(0))                    // styleCount
        out.write(le32(UTF8_FLAG))            // flags
        out.write(le32(stringsStart))
        out.write(le32(0))                    // stylesStart
        for (o in offsets) out.write(le32(o))
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    private fun writeVarint8(out: ByteArrayOutputStream, v: Int) {
        if (v < 0x80) {
            out.write(v)
        } else {
            out.write((v shr 8) or 0x80)
            out.write(v and 0xFF)
        }
    }

    private fun serializeResourceMap(ids: IntArray): ByteArray {
        val out = ByteArrayOutputStream(8 + ids.size * 4)
        out.write(le16(RES_XML_RESOURCE_MAP_TYPE))
        out.write(le16(8))
        out.write(le32(8 + ids.size * 4))
        for (id in ids) out.write(le32(id))
        return out.toByteArray()
    }

    private fun serializeNodes(nodes: List<Node>): ByteArray {
        val out = ByteArrayOutputStream()
        for (node in nodes) {
            when (node) {
                is Node.StartNamespace -> {
                    out.write(le16(RES_XML_START_NAMESPACE_TYPE))
                    out.write(le16(24))
                    out.write(le32(24))
                    out.write(le32(0))      // line
                    out.write(le32(-1))     // comment
                    out.write(le32(node.prefixIdx))
                    out.write(le32(node.uriIdx))
                }
                is Node.EndNamespace -> {
                    out.write(le16(RES_XML_END_NAMESPACE_TYPE))
                    out.write(le16(24))
                    out.write(le32(24))
                    out.write(le32(0))
                    out.write(le32(-1))
                    out.write(le32(node.prefixIdx))
                    out.write(le32(node.uriIdx))
                }
                is Node.StartElement -> {
                    val size = 36 + 20 * node.attrs.size
                    out.write(le16(RES_XML_START_ELEMENT_TYPE))
                    out.write(le16(36))
                    out.write(le32(size))
                    out.write(le32(0))      // line
                    out.write(le32(-1))     // comment
                    out.write(le32(node.nsIdx))
                    out.write(le32(node.nameIdx))
                    out.write(le16(20))     // attributeStart
                    out.write(le16(20))     // attributeSize
                    out.write(le16(node.attrs.size))
                    out.write(le16(0))      // idIndex
                    out.write(le16(0))      // classIndex
                    out.write(le16(0))      // styleIndex
                    for (a in node.attrs) {
                        out.write(le32(a.nsIdx))
                        out.write(le32(a.nameIdx))
                        out.write(le32(a.rawValueIdx))
                        out.write(le16(8))          // Res_value.size
                        out.write(0)                // res0
                        out.write(a.valueType)      // dataType
                        out.write(le32(a.valueData))
                    }
                }
                is Node.EndElement -> {
                    out.write(le16(RES_XML_END_ELEMENT_TYPE))
                    out.write(le16(24))
                    out.write(le32(24))
                    out.write(le32(0))
                    out.write(le32(-1))
                    out.write(le32(node.nsIdx))
                    out.write(le32(node.nameIdx))
                }
                is Node.Raw -> out.write(node.bytes)
            }
        }
        return out.toByteArray()
    }

    private fun le16(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )
}

// ======================================================================
// Manifest editor (high level patch operations)
// ======================================================================

data class ManifestPatch(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val label: String,
    val screenOrientation: Int,
    val internetPermission: Boolean,
)

class ManifestEditor(private val doc: AXml.Document) {

    private val knownAttrIds = mapOf(
        "label" to AXml.ANDROID_LABEL,
        "name" to AXml.ANDROID_NAME,
        "screenOrientation" to AXml.ANDROID_SCREEN_ORIENTATION,
        "versionCode" to AXml.ANDROID_VERSION_CODE,
        "versionName" to AXml.ANDROID_VERSION_NAME,
    )

    private fun element(name: String): AXml.Node.StartElement =
        doc.nodes.filterIsInstance<AXml.Node.StartElement>()
            .firstOrNull { doc.strings.getOrNull(it.nameIdx) == name }
            ?: throw IllegalStateException("element <$name> not found in template manifest")

    private fun androidNsIdx(): Int = doc.stringIndex(AXml.ANDROID_NS)

    /** Index of an android attribute name string; creates the string + resource-id entry if needed. */
    private fun androidAttrNameIdxOrCreate(attrName: String): Int {
        val idx = doc.stringIndexOrCreate(attrName)
        val id = knownAttrIds[attrName]
        if (id != null) {
            if (doc.resourceIds.size < doc.strings.size) {
                val padded = doc.resourceIds.copyOf(doc.strings.size)
                doc.resourceIds = padded
            }
            doc.resourceIds[idx] = id
        }
        return idx
    }

    /** Ensure resource-id map covers the whole string pool (zero-padded). */
    private fun ensureResourceMapCoversPool() {
        if (doc.resourceIds.size < doc.strings.size) {
            doc.resourceIds = doc.resourceIds.copyOf(doc.strings.size)
        }
    }

    private fun setStringAttr(
        element: AXml.Node.StartElement,
        attrName: String,
        value: String,
        namespaced: Boolean,
    ) {
        val nsIdx = if (namespaced) androidNsIdx() else -1
        val existing = element.attrs.firstOrNull {
            (if (namespaced) it.nsIdx == nsIdx else it.nsIdx == -1) &&
                doc.strings.getOrNull(it.nameIdx) == attrName
        }
        val valueIdx = doc.stringIndexOrCreate(value)
        if (existing != null) {
            existing.rawValueIdx = valueIdx
            existing.valueType = AXml.TYPE_STRING
            existing.valueData = valueIdx
        } else {
            val nameIdx = if (namespaced) androidAttrNameIdxOrCreate(attrName) else doc.stringIndexOrCreate(attrName)
            element.attrs.add(AXml.Attr(nsIdx, nameIdx, valueIdx, AXml.TYPE_STRING, valueIdx))
            ensureResourceMapCoversPool()
        }
    }

    private fun setIntAttr(
        element: AXml.Node.StartElement,
        attrName: String,
        value: Int,
    ) {
        val nsIdx = androidNsIdx()
        val existing = element.attrs.firstOrNull {
            it.nsIdx == nsIdx && doc.strings.getOrNull(it.nameIdx) == attrName
        }
        if (existing != null) {
            existing.rawValueIdx = -1
            existing.valueType = AXml.TYPE_INT_DEC
            existing.valueData = value
        } else {
            val nameIdx = androidAttrNameIdxOrCreate(attrName)
            element.attrs.add(AXml.Attr(nsIdx, nameIdx, -1, AXml.TYPE_INT_DEC, value))
            ensureResourceMapCoversPool()
        }
    }

    fun setPackageName(pkg: String) {
        setStringAttr(element("manifest"), "package", pkg, namespaced = false)
    }

    fun setVersionCode(code: Int) {
        setIntAttr(element("manifest"), "versionCode", code)
    }

    fun setVersionName(name: String) {
        setStringAttr(element("manifest"), "versionName", name, namespaced = true)
    }

    fun setLabel(label: String) {
        setStringAttr(element("application"), "label", label, namespaced = true)
    }

    fun setScreenOrientation(value: Int) {
        setIntAttr(element("activity"), "screenOrientation", value)
    }

    /** Removes (or ensures) the INTERNET uses-permission element. */
    fun setInternetPermission(enabled: Boolean) {
        val androidNs = doc.stringIndex(AXml.ANDROID_NS)
        val nodes = doc.nodes
        var startIdx = -1
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n is AXml.Node.StartElement && doc.strings.getOrNull(n.nameIdx) == "uses-permission") {
                val nameAttr = n.attrs.firstOrNull {
                    it.nsIdx == androidNs && doc.strings.getOrNull(it.nameIdx) == "name"
                }
                if (nameAttr != null) {
                    val v = nameAttr.valueData
                    if (v in doc.strings.indices && doc.strings[v] == AXml.INTERNET_PERMISSION) {
                        startIdx = i
                        break
                    }
                }
            }
        }
        if (enabled) {
            if (startIdx == -1) insertInternetPermission()
        } else {
            if (startIdx != -1) {
                // remove StartElement and its matching EndElement
                var endIdx = -1
                for (j in startIdx + 1 until nodes.size) {
                    val n = nodes[j]
                    if (n is AXml.Node.EndElement && doc.strings.getOrNull(n.nameIdx) == "uses-permission") {
                        endIdx = j
                        break
                    }
                }
                require(endIdx != -1) { "unbalanced uses-permission element" }
                nodes.removeAt(endIdx)
                nodes.removeAt(startIdx)
            }
        }
    }

    private fun insertInternetPermission() {
        val realNsIdx = doc.stringIndex(AXml.ANDROID_NS)
        require(realNsIdx >= 0) { "android namespace missing from template manifest" }
        val permIdx = doc.stringIndexOrCreate(AXml.INTERNET_PERMISSION)
        val nameIdx = androidAttrNameIdxOrCreate("name")
        val attr = AXml.Attr(realNsIdx, nameIdx, permIdx, AXml.TYPE_STRING, permIdx)
        val start = AXml.Node.StartElement(-1, doc.stringIndexOrCreate("uses-permission"), mutableListOf(attr))
        val end = AXml.Node.EndElement(-1, start.nameIdx)
        val appIdx = doc.nodes.indexOfFirst {
            it is AXml.Node.StartElement && doc.strings.getOrNull(it.nameIdx) == "application"
        }
        val insertAt = if (appIdx >= 0) appIdx else doc.nodes.size
        doc.nodes.add(insertAt, start)
        doc.nodes.add(insertAt + 1, end)
        ensureResourceMapCoversPool()
    }

    fun serialize(): ByteArray {
        ensureResourceMapCoversPool()
        return AXml.serialize(doc)
    }
}

object AXmlEditor {
    /** Parses the template's compiled manifest, applies [patch] and returns the new binary XML. */
    fun patchManifest(templateManifest: ByteArray, patch: ManifestPatch): ByteArray {
        val doc = AXml.parse(templateManifest)
        val editor = ManifestEditor(doc)
        editor.setPackageName(patch.packageName)
        editor.setVersionCode(patch.versionCode)
        editor.setVersionName(patch.versionName)
        editor.setLabel(patch.label)
        editor.setScreenOrientation(patch.screenOrientation)
        editor.setInternetPermission(patch.internetPermission)
        return editor.serialize()
    }
}
