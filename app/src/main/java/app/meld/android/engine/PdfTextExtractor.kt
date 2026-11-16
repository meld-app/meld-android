package app.meld.android.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Pure-Kotlin best-effort PDF text extractor supporting uncompressed and FlateDecode streams,
 * literal and hex strings, and `/ToUnicode` `bfchar`/`bfrange` CMaps for API < 35 and JVM tests.
 */
internal object PdfTextExtractor {

    private const val MAX_PDF_BYTES = 64L * 1024L * 1024L
    private const val MAX_DECOMPRESSED_STREAM_BYTES = 4 * 1024 * 1024
    private val HEX_CHARS = "0123456789abcdefABCDEF".toSet()

    private data class PdfObj(
        val id: Int,
        val dict: String,
        val streamBytes: ByteArray?
    )

    private data class ToUnicodeCMap(
        val byteCodeLengths: List<Int>,
        val mapping: Map<String, String>
    )

    private sealed interface Tok {
        data class Literal(val value: String) : Tok
        data class Hex(val hex: String) : Tok
        data class ArrayTok(val items: List<Tok>) : Tok
        data class Name(val name: String) : Tok
        data class Num(val value: Float) : Tok
    }

    fun extractPages(inputFile: File, expectedPageCount: Int = 0): List<String> {
        if (!inputFile.isFile || inputFile.length() == 0L || inputFile.length() > MAX_PDF_BYTES) {
            return emptyList()
        }
        val raw = runCatching { inputFile.readBytes() }.getOrNull() ?: return emptyList()
        return extractPagesFromBytes(raw, expectedPageCount)
    }

    internal fun extractPagesFromBytes(pdfBytes: ByteArray, expectedPageCount: Int = 0): List<String> {
        if (pdfBytes.isEmpty()) return emptyList()
        val text = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val objects = parseObjects(text, pdfBytes)
        if (objects.isEmpty()) return emptyList()

        val cmapsByObjId = HashMap<Int, ToUnicodeCMap>()
        for (obj in objects.values) {
            val stream = obj.streamBytes ?: continue
            if (!obj.dict.contains("/Type") && obj.dict.contains("/Subtype")) continue
            val decoded = if (isFlateEncoded(obj.dict)) inflateFlateBounded(stream) ?: continue else stream
            val asStr = String(decoded, StandardCharsets.ISO_8859_1)
            if (asStr.contains("beginbfchar") || asStr.contains("beginbfrange")) {
                cmapsByObjId[obj.id] = parseToUnicodeCMap(asStr)
            }
        }

        val pageObjs = collectOrderedPageObjects(objects)
        if (pageObjs.isEmpty()) return emptyList()

        val fontMapsCache = HashMap<Int, Map<String, ToUnicodeCMap>>()
        val pages = pageObjs.map { pageObj ->
            val fontMap = resolvePageFonts(pageObj, objects, cmapsByObjId, fontMapsCache)
            val contentRefs = parseContentStreamRefs(pageObj.dict)
            if (contentRefs.isEmpty()) {
                ""
            } else {
                val combined = StringBuilder()
                for (refId in contentRefs) {
                    val contentObj = objects[refId] ?: continue
                    val stream = contentObj.streamBytes ?: continue
                    val decoded = if (isFlateEncoded(contentObj.dict)) {
                        inflateFlateBounded(stream) ?: continue
                    } else {
                        stream
                    }
                    val extracted = interpretContentStream(String(decoded, StandardCharsets.ISO_8859_1), fontMap)
                    if (extracted.isNotBlank()) {
                        if (combined.isNotEmpty()) combined.append('\n')
                        combined.append(extracted)
                    }
                }
                normalizeExtractedText(combined.toString())
            }
        }

        return if (expectedPageCount > 0 && pages.size > expectedPageCount) {
            pages.take(expectedPageCount)
        } else {
            pages
        }
    }

    private fun parseObjects(text: String, bytes: ByteArray): Map<Int, PdfObj> {
        val result = LinkedHashMap<Int, PdfObj>()
        val headerRegex = Regex("""(?:^|[\r\n])\s*(\d+)\s+\d+\s+obj\b""")
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = headerRegex.find(text, searchFrom) ?: break
            val id = match.groupValues[1].toIntOrNull()
            if (id == null) {
                searchFrom = match.range.last + 1
                continue
            }
            val bodyStart = match.range.last + 1
            val streamMarker = findKeywordBeforeEndObj(text, bodyStart)
            if (streamMarker != null) {
                val dictStr = text.substring(bodyStart, streamMarker.first)
                val streamDataStart = skipStreamNewline(bytes, streamMarker.second)
                val endStreamIdx = text.indexOf("endstream", streamDataStart)
                if (endStreamIdx > streamDataStart) {
                    var streamDataEnd = endStreamIdx
                    if (streamDataEnd > streamDataStart && bytes[streamDataEnd - 1] == '\n'.code.toByte()) streamDataEnd--
                    if (streamDataEnd > streamDataStart && bytes[streamDataEnd - 1] == '\r'.code.toByte()) streamDataEnd--
                    val slice = bytes.copyOfRange(streamDataStart, streamDataEnd.coerceAtLeast(streamDataStart))
                    result[id] = PdfObj(id, dictStr, slice)
                    val endObjIdx = text.indexOf("endobj", endStreamIdx)
                    searchFrom = if (endObjIdx >= 0) endObjIdx + 6 else endStreamIdx + 9
                    continue
                }
            }
            val endObjIdx = text.indexOf("endobj", bodyStart)
            if (endObjIdx < 0) break
            result[id] = PdfObj(id, text.substring(bodyStart, endObjIdx), null)
            searchFrom = endObjIdx + 6
        }
        return result
    }

    private fun findKeywordBeforeEndObj(text: String, start: Int): Pair<Int, Int>? {
        val endObjIdx = text.indexOf("endobj", start)
        val limit = if (endObjIdx >= 0) endObjIdx else text.length
        val streamIdx = text.indexOf("stream", start)
        if (streamIdx in start until limit) {
            val after = streamIdx + 6
            if (after < text.length && (text[after] == '\r' || text[after] == '\n')) {
                return streamIdx to after
            }
        }
        return null
    }

    private fun skipStreamNewline(bytes: ByteArray, idxAfterKeyword: Int): Int {
        var i = idxAfterKeyword
        if (i < bytes.size && bytes[i] == '\r'.code.toByte()) i++
        if (i < bytes.size && bytes[i] == '\n'.code.toByte()) i++
        return i
    }

    private fun isFlateEncoded(dict: String): Boolean =
        dict.contains("/FlateDecode") || dict.contains("/Fl")

    private fun inflateFlateBounded(compressed: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream(minOf(compressed.size * 2, 65536))
            val buf = ByteArray(8192)
            while (!inflater.finished() && out.size() < MAX_DECOMPRESSED_STREAM_BYTES) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun collectOrderedPageObjects(objects: Map<Int, PdfObj>): List<PdfObj> {
        val catalog = objects.values.firstOrNull { Regex("""/Type\s*/Catalog\b""").containsMatchIn(it.dict) }
        val pagesRootId = catalog?.let { extractSingleRef(it.dict, "/Pages") }
            ?: objects.values.firstOrNull {
                Regex("""/Type\s*/Pages\b""").containsMatchIn(it.dict) && !it.dict.contains("/Parent")
            }?.id

        if (pagesRootId != null) {
            val ordered = ArrayList<PdfObj>()
            val visited = HashSet<Int>()
            fun walk(nodeId: Int) {
                if (!visited.add(nodeId)) return
                val node = objects[nodeId] ?: return
                val kids = extractArrayRefs(node.dict, "/Kids")
                if (kids.isNotEmpty() || Regex("""/Type\s*/Pages\b""").containsMatchIn(node.dict)) {
                    for (kidId in kids) walk(kidId)
                } else if (Regex("""/Type\s*/Page\b""").containsMatchIn(node.dict)) {
                    ordered.add(node)
                }
            }
            walk(pagesRootId)
            if (ordered.isNotEmpty()) return ordered
        }

        return objects.values.filter {
            Regex("""/Type\s*/Page\b""").containsMatchIn(it.dict) &&
                !Regex("""/Type\s*/Pages\b""").containsMatchIn(it.dict)
        }
    }

    private fun resolvePageFonts(
        pageObj: PdfObj,
        objects: Map<Int, PdfObj>,
        cmapsByObjId: Map<Int, ToUnicodeCMap>,
        cache: MutableMap<Int, Map<String, ToUnicodeCMap>>
    ): Map<String, ToUnicodeCMap> {
        val merged = LinkedHashMap<String, ToUnicodeCMap>()
        val chain = ArrayList<PdfObj>()
        val visited = HashSet<Int>()
        var curr: PdfObj? = pageObj
        while (curr != null && visited.add(curr.id)) {
            chain.add(curr)
            val parentId = extractSingleRef(curr.dict, "/Parent")
            curr = parentId?.let { objects[it] }
        }
        for (node in chain.asReversed()) {
            merged.putAll(extractFontsFromNode(node, objects, cmapsByObjId, cache))
        }
        return merged
    }

    private fun extractFontsFromNode(
        node: PdfObj,
        objects: Map<Int, PdfObj>,
        cmapsByObjId: Map<Int, ToUnicodeCMap>,
        cache: MutableMap<Int, Map<String, ToUnicodeCMap>>
    ): Map<String, ToUnicodeCMap> {
        cache[node.id]?.let { return it }

        val resourcesBody = extractSingleRef(node.dict, "/Resources")?.let { objects[it]?.dict }
            ?: extractNestedDict(node.dict, "/Resources")
            ?: return emptyMap<String, ToUnicodeCMap>().also { cache[node.id] = it }

        val fontBody = extractSingleRef(resourcesBody, "/Font")?.let { objects[it]?.dict }
            ?: extractNestedDict(resourcesBody, "/Font")
            ?: return emptyMap<String, ToUnicodeCMap>().also { cache[node.id] = it }

        val result = LinkedHashMap<String, ToUnicodeCMap>()
        val entryRegex = Regex("""(/[A-Za-z0-9_.+-]+)\s+(\d+)\s+\d+\s+R\b""")
        for (m in entryRegex.findAll(fontBody)) {
            val fontName = m.groupValues[1]
            val fontObjId = m.groupValues[2].toIntOrNull() ?: continue
            val fontObj = objects[fontObjId] ?: continue
            val toUniId = extractSingleRef(fontObj.dict, "/ToUnicode") ?: continue
            cmapsByObjId[toUniId]?.let { result[fontName] = it }
        }
        cache[node.id] = result
        return result
    }

    private fun parseContentStreamRefs(pageDict: String): List<Int> {
        extractSingleRef(pageDict, "/Contents")?.let { return listOf(it) }
        return extractArrayRefs(pageDict, "/Contents")
    }

    private fun extractSingleRef(dict: String, key: String): Int? =
        Regex("""${Regex.escape(key)}\s+(\d+)\s+\d+\s+R\b""").find(dict)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractArrayRefs(dict: String, key: String): List<Int> {
        val m = Regex("""${Regex.escape(key)}\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(dict) ?: return emptyList()
        return Regex("""(\d+)\s+\d+\s+R\b""").findAll(m.groupValues[1])
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
    }

    private fun extractNestedDict(text: String, key: String): String? {
        val m = Regex("""${Regex.escape(key)}\s*<<""").find(text) ?: return null
        var depth = 1
        var i = m.range.last + 1
        val start = i
        while (i < text.length - 1 && depth > 0) {
            if (text[i] == '<' && text[i + 1] == '<') {
                depth++
                i += 2
            } else if (text[i] == '>' && text[i + 1] == '>') {
                depth--
                if (depth == 0) return text.substring(start, i)
                i += 2
            } else {
                i++
            }
        }
        return null
    }

    private fun parseToUnicodeCMap(cmapStr: String): ToUnicodeCMap {
        val map = HashMap<String, String>()
        val keyLengths = HashSet<Int>()

        val bfCharRegex = Regex("""beginbfchar\s*(.*?)\s*endbfchar""", RegexOption.DOT_MATCHES_ALL)
        val pairRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
        for (block in bfCharRegex.findAll(cmapStr)) {
            for (pair in pairRegex.findAll(block.groupValues[1])) {
                val srcHex = pair.groupValues[1].uppercase()
                val dstHex = pair.groupValues[2]
                keyLengths.add(srcHex.length / 2)
                map[srcHex] = decodeUtf16BeHex(dstHex)
            }
        }

        val bfRangeRegex = Regex("""beginbfrange\s*(.*?)\s*endbfrange""", RegexOption.DOT_MATCHES_ALL)
        val simpleRangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
        val arrayRangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\[([^\]]*)\]""")
        for (block in bfRangeRegex.findAll(cmapStr)) {
            val content = block.groupValues[1]
            for (m in simpleRangeRegex.findAll(content)) {
                val lo = m.groupValues[1].toIntOrNull(16) ?: continue
                val hi = m.groupValues[2].toIntOrNull(16) ?: continue
                val dstStart = m.groupValues[3].toLongOrNull(16) ?: continue
                val hexWidth = m.groupValues[1].length
                keyLengths.add(hexWidth / 2)
                val count = (hi - lo + 1).coerceIn(0, 4096)
                for (offset in 0 until count) {
                    val srcKey = (lo + offset).toString(16).uppercase().padStart(hexWidth, '0')
                    val unicodeCodePoint = (dstStart + offset).toInt()
                    if (unicodeCodePoint in 1..0x10FFFF) {
                        map[srcKey] = String(Character.toChars(unicodeCodePoint))
                    }
                }
            }
            for (m in arrayRangeRegex.findAll(content)) {
                val lo = m.groupValues[1].toIntOrNull(16) ?: continue
                val hi = m.groupValues[2].toIntOrNull(16) ?: continue
                val hexWidth = m.groupValues[1].length
                keyLengths.add(hexWidth / 2)
                val dstItems = Regex("""<([0-9A-Fa-f]+)>""").findAll(m.groupValues[3]).toList()
                val limit = minOf(hi - lo + 1, dstItems.size).coerceAtMost(4096)
                for (offset in 0 until limit) {
                    val srcKey = (lo + offset).toString(16).uppercase().padStart(hexWidth, '0')
                    map[srcKey] = decodeUtf16BeHex(dstItems[offset].groupValues[1])
                }
            }
        }

        val sortedLengths = keyLengths.filter { it > 0 }.sortedDescending().ifEmpty { listOf(1) }
        return ToUnicodeCMap(sortedLengths, map)
    }

    private fun decodeUtf16BeHex(hex: String): String {
        val clean = hex.filter { it in HEX_CHARS }
        if (clean.isEmpty()) return ""
        val padded = if (clean.length % 2 == 1) "0$clean" else clean
        val bytes = ByteArray(padded.length / 2)
        for (i in bytes.indices) {
            bytes[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return if (bytes.size >= 2) {
            String(bytes, StandardCharsets.UTF_16BE)
        } else {
            (bytes[0].toInt() and 0xFF).toChar().toString()
        }
    }

    private fun interpretContentStream(stream: String, fontMap: Map<String, ToUnicodeCMap>): String {
        val out = StringBuilder()
        var currentFont: String? = null
        var fontSize = 12f
        var curY = Float.NaN

        val stack = ArrayList<Tok>()
        var i = 0
        val n = stream.length
        var inTextBlock = false

        fun decodeTokenText(tok: Tok): String {
            val cmap = currentFont?.let { fontMap[it] }
            return when (tok) {
                is Tok.Literal -> tok.value
                is Tok.Hex -> decodeHexWithCMap(tok.hex, cmap)
                else -> ""
            }
        }

        fun handleNewlineIfMovedY(newY: Float, threshold: Float) {
            if (!curY.isNaN() && abs(newY - curY) > threshold) {
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
            }
            curY = newY
        }

        while (i < n) {
            val c = stream[i]
            when {
                c.isWhitespace() -> i++
                c == '%' -> {
                    while (i < n && stream[i] != '\n' && stream[i] != '\r') i++
                }
                c == '(' -> {
                    val sb = StringBuilder()
                    var depth = 1
                    i++
                    while (i < n && depth > 0) {
                        val ch = stream[i++]
                        if (ch == '\\' && i < n) {
                            when (val esc = stream[i++]) {
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'b' -> sb.append('\b')
                                '(', ')', '\\' -> sb.append(esc)
                                in '0'..'7' -> {
                                    val oct = StringBuilder().append(esc)
                                    repeat(2) {
                                        if (i < n && stream[i] in '0'..'7') oct.append(stream[i++])
                                    }
                                    sb.append(oct.toString().toInt(8).toChar())
                                }
                                else -> sb.append(esc)
                            }
                        } else if (ch == '(') {
                            depth++
                            sb.append(ch)
                        } else if (ch == ')') {
                            depth--
                            if (depth > 0) sb.append(ch)
                        } else {
                            sb.append(ch)
                        }
                    }
                    if (inTextBlock) stack.add(Tok.Literal(sb.toString()))
                }
                c == '<' -> {
                    if (i + 1 < n && stream[i + 1] == '<') {
                        i += 2
                        var depth = 1
                        while (i < n - 1 && depth > 0) {
                            if (stream[i] == '<' && stream[i + 1] == '<') { depth++; i += 2 }
                            else if (stream[i] == '>' && stream[i + 1] == '>') { depth--; i += 2 }
                            else i++
                        }
                    } else {
                        i++
                        val hexSb = StringBuilder()
                        while (i < n && stream[i] != '>') {
                            val h = stream[i++]
                            if (h in HEX_CHARS) hexSb.append(h)
                        }
                        if (i < n && stream[i] == '>') i++
                        if (inTextBlock) stack.add(Tok.Hex(hexSb.toString()))
                    }
                }
                c == '[' -> {
                    i++
                    val items = ArrayList<Tok>()
                    while (i < n && stream[i] != ']') {
                        val sub = stream[i]
                        when {
                            sub.isWhitespace() -> i++
                            sub == '(' -> {
                                val sb = StringBuilder()
                                var depth = 1
                                i++
                                while (i < n && depth > 0) {
                                    val ch = stream[i++]
                                    if (ch == '\\' && i < n) {
                                        when (val esc = stream[i++]) {
                                            'n' -> sb.append('\n')
                                            'r' -> sb.append('\r')
                                            't' -> sb.append('\t')
                                            '(', ')', '\\' -> sb.append(esc)
                                            else -> sb.append(esc)
                                        }
                                    } else if (ch == '(') { depth++; sb.append(ch) }
                                    else if (ch == ')') { depth--; if (depth > 0) sb.append(ch) }
                                    else sb.append(ch)
                                }
                                items.add(Tok.Literal(sb.toString()))
                            }
                            sub == '<' -> {
                                i++
                                val hexSb = StringBuilder()
                                while (i < n && stream[i] != '>') {
                                    val h = stream[i++]
                                    if (h in HEX_CHARS) hexSb.append(h)
                                }
                                if (i < n && stream[i] == '>') i++
                                items.add(Tok.Hex(hexSb.toString()))
                            }
                            else -> {
                                val startNum = i
                                while (i < n && !stream[i].isWhitespace() && stream[i] != ']' && stream[i] != '<' && stream[i] != '(') i++
                                stream.substring(startNum, i).toFloatOrNull()?.let { items.add(Tok.Num(it)) }
                            }
                        }
                    }
                    if (i < n && stream[i] == ']') i++
                    if (inTextBlock) stack.add(Tok.ArrayTok(items))
                }
                c == '/' -> {
                    val startName = i
                    i++
                    while (i < n && !stream[i].isWhitespace() && stream[i] !in charArrayOf('/', '[', ']', '<', '>', '(', ')')) {
                        i++
                    }
                    if (inTextBlock) stack.add(Tok.Name(stream.substring(startName, i)))
                }
                else -> {
                    val startTok = i
                    while (i < n && !stream[i].isWhitespace() && stream[i] !in charArrayOf('[', ']', '<', '>', '(', ')', '/')) {
                        i++
                    }
                    val tokenStr = stream.substring(startTok, i)
                    val asNum = tokenStr.toFloatOrNull()
                    if (asNum != null) {
                        if (inTextBlock) stack.add(Tok.Num(asNum))
                    } else {
                        when (tokenStr) {
                            "BT" -> {
                                inTextBlock = true
                                curY = Float.NaN
                                stack.clear()
                            }
                            "ET" -> {
                                if (inTextBlock && out.isNotEmpty() && out.last() != '\n') out.append('\n')
                                inTextBlock = false
                                stack.clear()
                            }
                            else -> if (inTextBlock) {
                                when (tokenStr) {
                                    "Tf" -> {
                                        if (stack.size >= 2) {
                                            (stack[stack.size - 2] as? Tok.Name)?.let { currentFont = it.name }
                                            (stack.last() as? Tok.Num)?.let { fontSize = it.value.coerceAtLeast(1f) }
                                        }
                                    }
                                    "Td", "TD" -> {
                                        if (stack.size >= 2) {
                                            val tx = (stack[stack.size - 2] as? Tok.Num)?.value ?: 0f
                                            val ty = (stack.last() as? Tok.Num)?.value ?: 0f
                                            if (abs(ty) > fontSize * 0.35f) {
                                                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                                                curY = if (curY.isNaN()) ty else curY + ty
                                            } else if (tx > fontSize * 1.25f && out.isNotEmpty() && !out.last().isWhitespace()) {
                                                out.append(' ')
                                            }
                                        }
                                    }
                                    "Tm" -> {
                                        if (stack.size >= 6) {
                                            val newY = (stack[stack.size - 1] as? Tok.Num)?.value
                                            if (newY != null) handleNewlineIfMovedY(newY, fontSize * 0.35f)
                                        }
                                    }
                                    "T*" -> {
                                        if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                                    }
                                    "Tj" -> {
                                        stack.lastOrNull()?.let { out.append(decodeTokenText(it)) }
                                    }
                                    "'", "\"" -> {
                                        if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                                        stack.lastOrNull()?.let { out.append(decodeTokenText(it)) }
                                    }
                                    "TJ" -> {
                                        val arr = stack.lastOrNull() as? Tok.ArrayTok
                                        arr?.items?.forEach { item ->
                                            when (item) {
                                                is Tok.Num -> if (item.value < -150f && out.isNotEmpty() && !out.last().isWhitespace()) {
                                                    out.append(' ')
                                                }
                                                else -> out.append(decodeTokenText(item))
                                            }
                                        }
                                    }
                                }
                                stack.clear()
                            }
                        }
                    }
                }
            }
        }
        return normalizeExtractedText(out.toString())
    }

    private fun decodeHexWithCMap(rawHex: String, cmap: ToUnicodeCMap?): String {
        val hex = rawHex.uppercase()
        if (hex.isEmpty()) return ""
        if (cmap != null && cmap.mapping.isNotEmpty()) {
            val sb = StringBuilder()
            var pos = 0
            while (pos < hex.length) {
                var matched = false
                for (byteLen in cmap.byteCodeLengths) {
                    val hexLen = byteLen * 2
                    if (pos + hexLen <= hex.length) {
                        val chunk = hex.substring(pos, pos + hexLen)
                        val mapped = cmap.mapping[chunk]
                        if (mapped != null) {
                            sb.append(mapped)
                            pos += hexLen
                            matched = true
                            break
                        }
                    }
                }
                if (!matched) {
                    val step = minOf(2, hex.length - pos)
                    val code = hex.substring(pos, pos + step).toIntOrNull(16) ?: 0
                    if (code in 32..126) sb.append(code.toChar())
                    pos += step
                }
            }
            return sb.toString()
        }
        val sb = StringBuilder()
        var pos = 0
        while (pos + 2 <= hex.length) {
            val code = hex.substring(pos, pos + 2).toIntOrNull(16) ?: 0
            if (code in 32..126 || code in 160..255) sb.append(code.toChar())
            pos += 2
        }
        return sb.toString()
    }

    private fun normalizeExtractedText(raw: String): String =
        raw.lines()
            .map { it.replace(Regex("""[ \t]+"""), " ").trim() }
            .filterIndexed { idx, line -> line.isNotEmpty() || (idx > 0 && idx < raw.lines().lastIndex) }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
}
