package app.meld.android.engine.pptx

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin OOXML PresentationML + DrawingML parser and style inheritance model.
 * Preserves slide dimensions, Master/Layout/Theme hierarchy, coordinates, tables,
 * embedded media, speaker notes, and embedded font references without mutating input files.
 */
object PptxOoxmlParser {

    const val EMU_PER_PT: Float = 12700f
    private const val MAX_XML_BYTES = 16 * 1024 * 1024
    private const val MAX_ASSET_BYTES = 32 * 1024 * 1024
    private const val MAX_TOTAL_READ_BYTES = 128L * 1024L * 1024L

    data class Xfrm(
        val x: Float = 0f,
        val y: Float = 0f,
        val width: Float = 0f,
        val height: Float = 0f,
        val rotationDeg: Float = 0f,
        val flipH: Boolean = false,
        val flipV: Boolean = false
    )

    data class GradientStop(val posFraction: Float, val argb: Int)

    sealed class PptxFill {
        data class Solid(val argb: Int) : PptxFill()
        data class LinearGradient(val angleDeg: Float, val stops: List<GradientStop>) : PptxFill()
    }

    data class PptxStroke(
        val widthPt: Float,
        val argb: Int,
        val dashed: Boolean = false
    )

    enum class VerticalAnchor { TOP, CENTER, BOTTOM }
    enum class HorizontalAlign { LEFT, CENTER, RIGHT, JUSTIFY }

    data class TextStyleLevel(
        val fontFamily: String? = null,
        val fontSizePt: Float? = null,
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val colorArgb: Int? = null,
        val align: HorizontalAlign? = null,
        val bulletChar: String? = null,
        val bulletAutoNum: Boolean = false,
        val bulletNone: Boolean = false,
        val marginLeftPt: Float? = null,
        val indentPt: Float? = null,
        val spaceBeforePt: Float? = null,
        val spaceAfterPt: Float? = null,
        val lineSpacingMult: Float? = null
    ) {
        fun mergedOver(base: TextStyleLevel?): TextStyleLevel = if (base == null) this else TextStyleLevel(
            fontFamily = fontFamily ?: base.fontFamily,
            fontSizePt = fontSizePt ?: base.fontSizePt,
            bold = bold ?: base.bold,
            italic = italic ?: base.italic,
            colorArgb = colorArgb ?: base.colorArgb,
            align = align ?: base.align,
            bulletChar = when {
                bulletNone -> null
                bulletAutoNum -> null
                bulletChar != null -> bulletChar
                else -> base.bulletChar
            },
            bulletAutoNum = if (bulletNone) false else if (bulletAutoNum || bulletChar != null) bulletAutoNum else base.bulletAutoNum,
            bulletNone = bulletNone,
            marginLeftPt = marginLeftPt ?: base.marginLeftPt,
            indentPt = indentPt ?: base.indentPt,
            spaceBeforePt = spaceBeforePt ?: base.spaceBeforePt,
            spaceAfterPt = spaceAfterPt ?: base.spaceAfterPt,
            lineSpacingMult = lineSpacingMult ?: base.lineSpacingMult
        )
    }

    data class PptxTextRun(
        val text: String,
        val fontFamily: String,
        val fontSizePt: Float,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strikethrough: Boolean,
        val baselinePercent: Int,
        val colorArgb: Int
    )

    data class PptxParagraph(
        val level: Int,
        val align: HorizontalAlign,
        val bulletPrefix: String?,
        val marginLeftPt: Float,
        val indentPt: Float,
        val spaceBeforePt: Float,
        val spaceAfterPt: Float,
        val lineSpacingMult: Float,
        val runs: List<PptxTextRun>
    )

    data class PptxTextBody(
        val anchor: VerticalAnchor,
        val wrap: Boolean,
        val insetLeftPt: Float,
        val insetTopPt: Float,
        val insetRightPt: Float,
        val insetBottomPt: Float,
        val fontScale: Float,
        val paragraphs: List<PptxParagraph>
    )

    data class CropRect(
        val leftFrac: Float = 0f,
        val topFrac: Float = 0f,
        val rightFrac: Float = 0f,
        val bottomFrac: Float = 0f
    )

    sealed class PptxElement {
        data class Shape(
            val presetGeom: String,
            val cornerRadiusFrac: Float,
            val xfrm: Xfrm,
            val fill: PptxFill?,
            val stroke: PptxStroke?,
            val textBody: PptxTextBody?,
            val placeholderType: String? = null,
            val isFromMasterOrLayout: Boolean = false
        ) : PptxElement()

        data class Picture(
            val xfrm: Xfrm,
            val imageEntryPath: String,
            val crop: CropRect
        ) : PptxElement()

        data class TableCell(
            val colSpan: Int,
            val rowSpan: Int,
            val mergedAway: Boolean,
            val fillArgb: Int?,
            val strokeLeft: PptxStroke?,
            val strokeTop: PptxStroke?,
            val strokeRight: PptxStroke?,
            val strokeBottom: PptxStroke?,
            val textBody: PptxTextBody?
        )

        data class TableRow(
            val heightPt: Float,
            val cells: List<TableCell>
        )

        data class Table(
            val xfrm: Xfrm,
            val colWidthsPt: List<Float>,
            val rows: List<TableRow>
        ) : PptxElement()
    }

    data class PptxSlide(
        val index1Based: Int,
        val entryPath: String,
        val widthPt: Float,
        val heightPt: Float,
        val hidden: Boolean,
        val background: PptxFill?,
        val backgroundImagePath: String?,
        val elements: List<PptxElement>,
        val notesText: String
    ) {
        fun extractHeading(): String? {
            val slideShapes = elements
                .filterIsInstance<PptxElement.Shape>()
                .filter { !it.isFromMasterOrLayout && it.textBody != null }

            val placeholderTitle = slideShapes.firstOrNull {
                it.placeholderType == "title" || it.placeholderType == "ctrTitle"
            }?.firstNonBlankLine()
            if (!placeholderTitle.isNullOrBlank()) return placeholderTitle

            var bestText: String? = null
            var bestScore = -1f
            for (shape in slideShapes) {
                val firstPara = shape.textBody?.paragraphs?.firstOrNull { p ->
                    p.runs.any { it.text.isNotBlank() }
                } ?: continue
                val line = firstPara.runs.joinToString("") { it.text }.trim()
                if (line.isBlank()) continue
                val maxPt = firstPara.runs.maxOfOrNull { it.fontSizePt } ?: 14f
                // Prefer prominent headings higher on the slide over small corner pills/badges
                val score = maxPt * 10f - (shape.xfrm.y * 0.02f)
                if (score > bestScore) {
                    bestScore = score
                    bestText = line
                }
            }
            return bestText
        }

        private fun PptxElement.Shape.firstNonBlankLine(): String? {
            return textBody?.paragraphs
                ?.asSequence()
                ?.map { p -> p.runs.joinToString("") { it.text }.trim() }
                ?.firstOrNull { it.isNotBlank() }
        }
    }

    data class PptxEmbeddedFont(
        val typeface: String,
        val bold: Boolean,
        val italic: Boolean,
        val zipEntryPath: String,
        val fontKeyGuid: String?
    )

    data class PptxPresentation(
        val slideWidthPt: Float,
        val slideHeightPt: Float,
        val slides: List<PptxSlide>,
        val embeddedFonts: List<PptxEmbeddedFont>,
        val usedFonts: Set<String>
    )

    internal data class ThemeData(
        val schemeColors: Map<String, Int>,
        val majorLatinFont: String,
        val minorLatinFont: String
    )

    private data class PlaceholderTemplate(
        val type: String?,
        val idx: String?,
        val xfrm: Xfrm?,
        val presetGeom: String?,
        val cornerRadiusFrac: Float?,
        val fill: PptxFill?,
        val stroke: PptxStroke?,
        val listStyles: Map<Int, TextStyleLevel>,
        val anchor: VerticalAnchor?,
        val insets: FloatArray?
    )

    private data class SlideMasterData(
        val entryPath: String,
        val rels: Map<String, String>,
        val theme: ThemeData,
        val clrMap: Map<String, String>,
        val background: PptxFill?,
        val backgroundImagePath: String?,
        val decorativeElements: List<PptxElement>,
        val placeholders: List<PlaceholderTemplate>,
        val titleStyles: Map<Int, TextStyleLevel>,
        val bodyStyles: Map<Int, TextStyleLevel>,
        val otherStyles: Map<Int, TextStyleLevel>
    )

    private data class SlideLayoutData(
        val entryPath: String,
        val rels: Map<String, String>,
        val master: SlideMasterData,
        val showMasterSp: Boolean,
        val background: PptxFill?,
        val backgroundImagePath: String?,
        val decorativeElements: List<PptxElement>,
        val placeholders: List<PlaceholderTemplate>
    )

    fun countSlides(inputFile: File): Int? {
        if (!inputFile.isFile) return null
        return runCatching {
            ZipFile(inputFile).use { zip ->
                val presEntry = zip.getEntry("ppt/presentation.xml") ?: return null
                val bytes = readEntryBytesBounded(zip, presEntry, MAX_XML_BYTES)
                val doc = parseXmlSafe(bytes)
                val sldIds = doc.documentElement.firstChildNamed("sldIdLst")?.childElementsNamed("sldId").orEmpty()
                if (sldIds.isNotEmpty()) {
                    sldIds.size
                } else {
                    zip.entries().asSequence().count { e ->
                        !e.isDirectory && e.name.matches(Regex("ppt/slides/slide\\d+\\.xml"))
                    }
                }
            }
        }.getOrNull()
    }

    fun parse(inputFile: File): PptxPresentation {
        require(inputFile.isFile) { "Input PPTX file does not exist." }
        ZipFile(inputFile).use { zip ->
            val tracker = ByteReadTracker()
            val presEntry = zip.getEntry("ppt/presentation.xml")
                ?: throw IllegalArgumentException("Invalid PPTX archive: missing ppt/presentation.xml")
            val presDoc = parseXmlSafe(readEntryBytesBounded(zip, presEntry, MAX_XML_BYTES, tracker))
            val presRoot = presDoc.documentElement
            val presRels = parseRelationships(zip, "ppt/presentation.xml", tracker)

            val sldSz = presRoot.firstChildNamed("sldSz")
            val slideW = emuAttrToPt(sldSz, "cx", fallbackPt = 960f).coerceIn(144f, 4096f)
            val slideH = emuAttrToPt(sldSz, "cy", fallbackPt = 540f).coerceIn(144f, 4096f)

            val defaultPresStyles = parseListStyles(presRoot.firstChildNamed("defaultTextStyle"), defaultTheme(), defaultClrMap())
            val embeddedFonts = parseEmbeddedFonts(presRoot, presRels)

            val slideRelIds = presRoot.firstChildNamed("sldIdLst")
                ?.childElementsNamed("sldId")
                ?.mapNotNull { el ->
                    attr(el, "r:id", "id")?.takeIf { el.hasAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id") || el.hasAttribute("r:id") }
                        ?: el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id").takeIf { it.isNotBlank() }
                        ?: el.getAttribute("r:id").takeIf { it.isNotBlank() }
                }.orEmpty()

            val orderedSlidePaths = if (slideRelIds.isNotEmpty()) {
                slideRelIds.mapNotNull { rid -> presRels[rid] }
            } else {
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                    .map { it.name }
                    .sortedBy { name ->
                        Regex("\\d+").find(name)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                    }
                    .toList()
            }
            require(orderedSlidePaths.isNotEmpty()) { "Presentation has no readable slides." }

            val masterCache = mutableMapOf<String, SlideMasterData>()
            val layoutCache = mutableMapOf<String, SlideLayoutData>()
            val usedFonts = linkedSetOf<String>()

            val slides = orderedSlidePaths.mapIndexedNotNull { idx, slidePath ->
                val slideEntry = resolveSafeEntry(zip, slidePath) ?: return@mapIndexedNotNull null
                val slideDoc = parseXmlSafe(readEntryBytesBounded(zip, slideEntry, MAX_XML_BYTES, tracker))
                val slideRoot = slideDoc.documentElement
                val slideRels = parseRelationships(zip, slidePath, tracker)

                val layoutPath = slideRels.values.firstOrNull { it.contains("slideLayouts/") }
                val layout = layoutPath?.let { loadSlideLayout(zip, it, layoutCache, masterCache, defaultPresStyles, tracker) }
                val master = layout?.master ?: loadFallbackMaster(zip, masterCache, defaultPresStyles, tracker)
                val theme = master?.theme ?: defaultTheme()
                val clrMap = parseColorMapOvr(slideRoot, master?.clrMap ?: defaultClrMap())

                val hidden = attr(slideRoot, "show") == "0"
                val showMasterSp = attr(slideRoot, "showMasterSp") != "0"

                val cSld = slideRoot.firstChildNamed("cSld")
                val slideBgPair = parseBackground(cSld?.firstChildNamed("bg"), slideRels, theme, clrMap)
                val effectiveBg = slideBgPair.first ?: layout?.background ?: master?.background
                val effectiveBgImage = slideBgPair.second ?: layout?.backgroundImagePath ?: master?.backgroundImagePath

                val elements = mutableListOf<PptxElement>()
                if (showMasterSp && master != null && (layout == null || layout.showMasterSp)) {
                    elements += master.decorativeElements
                }
                if (layout != null) {
                    elements += layout.decorativeElements
                }

                val spTree = cSld?.firstChildNamed("spTree")
                if (spTree != null) {
                    parseShapeTree(
                        spTree = spTree,
                        rels = slideRels,
                        theme = theme,
                        clrMap = clrMap,
                        layoutPlaceholders = layout?.placeholders.orEmpty(),
                        master = master,
                        defaultPresStyles = defaultPresStyles,
                        groupTransform = null,
                        collectFonts = usedFonts,
                        out = elements
                    )
                }

                val notesPath = slideRels.values.firstOrNull { it.contains("notesSlides/") }
                val notesText = notesPath?.let { extractNotesText(zip, it, tracker) }.orEmpty()

                PptxSlide(
                    index1Based = idx + 1,
                    entryPath = slidePath,
                    widthPt = slideW,
                    heightPt = slideH,
                    hidden = hidden,
                    background = effectiveBg,
                    backgroundImagePath = effectiveBgImage,
                    elements = elements,
                    notesText = notesText
                )
            }

            require(slides.isNotEmpty()) { "Presentation has no valid slides." }

            // Also include any embedded font typefaces in usedFonts report
            embeddedFonts.forEach { usedFonts.add(it.typeface) }

            return PptxPresentation(
                slideWidthPt = slideW,
                slideHeightPt = slideH,
                slides = slides,
                embeddedFonts = embeddedFonts,
                usedFonts = usedFonts
            )
        }
    }

    /**
     * Reads a verified image entry cleanly up to MAX_ASSET_BYTES for rendering.
     */
    fun readAssetBytes(zip: ZipFile, entryPath: String): ByteArray? {
        val entry = resolveSafeEntry(zip, entryPath) ?: return null
        return runCatching {
            readEntryBytesBounded(zip, entry, MAX_ASSET_BYTES)
        }.getOrNull()
    }

    /**
     * De-obfuscates PowerPoint/ECMA-376 `.odttf` embedded fonts (first 32 bytes XORed with 16-byte GUID key).
     * Validates SFNT magic header (`00 01 00 00`, `OTTO`, `true`, `ttcf`) so raw TTF/OTF bytes also pass untouched
     * and corrupted headers are never passed to Android's font loader.
     */
    fun deobfuscateEmbeddedFont(rawBytes: ByteArray, guidHint: String?): ByteArray? {
        if (rawBytes.size < 16) return null
        if (hasValidSfntHeader(rawBytes)) return rawBytes.copyOf()

        val guidHex = extractGuidHex32(guidHint) ?: return null
        val guidBytes = ByteArray(16) { idx ->
            guidHex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }

        // Standard OPC Part 2 order reverses the 16-byte GUID; also check Windows mixed-endian GUID if needed.
        val reversed = guidBytes.reversedArray()
        val mixedLe = byteArrayOf(
            guidBytes[3], guidBytes[2], guidBytes[1], guidBytes[0],
            guidBytes[5], guidBytes[4],
            guidBytes[7], guidBytes[6],
            guidBytes[8], guidBytes[9], guidBytes[10], guidBytes[11],
            guidBytes[12], guidBytes[13], guidBytes[14], guidBytes[15]
        )
        val mixedReversed = mixedLe.reversedArray()

        var bestCandidate: ByteArray? = null
        var bestScore = -1
        for (key in listOf(reversed, guidBytes, mixedReversed, mixedLe)) {
            val candidate = rawBytes.copyOf()
            val xorLen = min(32, candidate.size)
            for (i in 0 until xorLen) {
                candidate[i] = (candidate[i].toInt() xor key[i % 16].toInt()).toByte()
            }
            val score = sfntValidityScore(candidate)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
                if (score >= 16) break
            }
        }
        return bestCandidate
    }

    private fun sfntValidityScore(bytes: ByteArray): Int {
        if (!hasValidSfntHeader(bytes)) return -1
        var score = 1
        if (bytes.size >= 16) {
            val numTables = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
            if (numTables in 1..128) {
                score += 2
                val searchRange = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
                val entrySelector = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
                val rangeShift = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
                val maxPow2 = Integer.highestOneBit(numTables)
                val expectedSearch = maxPow2 * 16
                val expectedSelector = Integer.numberOfTrailingZeros(maxPow2)
                val expectedRange = numTables * 16 - expectedSearch
                if (searchRange == expectedSearch &&
                    entrySelector == expectedSelector &&
                    rangeShift == expectedRange
                ) {
                    score += 10
                }
            }
            val tagPrintable = (12..15).all { idx ->
                val b = bytes[idx].toInt() and 0xFF
                b in 0x20..0x7E
            }
            if (tagPrintable) score += 3
        }
        return score
    }

    internal fun hasValidSfntHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val isTtf = b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00
        val isOtf = b0 == 'O'.code && b1 == 'T'.code && b2 == 'T'.code && b3 == 'O'.code
        val isTrue = b0 == 't'.code && b1 == 'r'.code && b2 == 'u'.code && b3 == 'e'.code
        val isTtc = b0 == 't'.code && b1 == 't'.code && b2 == 'c'.code && b3 == 'f'.code
        return isTtf || isOtf || isTrue || isTtc
    }

    private fun extractGuidHex32(hint: String?): String? {
        if (hint.isNullOrBlank()) return null
        val cleaned = hint.replace(Regex("[^0-9A-Fa-f]"), "")
        return if (cleaned.length >= 32) cleaned.take(32) else null
    }

    /**
     * Evaluates OOXML color modifiers (`alpha`, `tint`, `shade`, `lumMod`, `lumOff`) on an ARGB base color.
     */
    internal fun applyColorModifiers(baseRgb: Int, colorElement: Element): Int {
        var r = (baseRgb shr 16) and 0xFF
        var g = (baseRgb shr 8) and 0xFF
        var b = baseRgb and 0xFF
        var a = 255

        for (child in childElements(colorElement)) {
            val valAttr = attr(child, "val")?.toIntOrNull() ?: continue
            val frac = (valAttr / 100000.0).coerceIn(0.0, 2.0)
            when (child.localNameOrRaw()) {
                "alpha" -> a = (255.0 * frac.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, 255)
                "tint" -> {
                    val f = frac.coerceIn(0.0, 1.0)
                    r = (r * f + 255.0 * (1.0 - f)).roundToInt().coerceIn(0, 255)
                    g = (g * f + 255.0 * (1.0 - f)).roundToInt().coerceIn(0, 255)
                    b = (b * f + 255.0 * (1.0 - f)).roundToInt().coerceIn(0, 255)
                }
                "shade" -> {
                    val f = frac.coerceIn(0.0, 1.0)
                    r = (r * f).roundToInt().coerceIn(0, 255)
                    g = (g * f).roundToInt().coerceIn(0, 255)
                    b = (b * f).roundToInt().coerceIn(0, 255)
                }
                "lumMod" -> {
                    val hsl = rgbToHsl(r, g, b)
                    hsl[2] = (hsl[2] * frac).coerceIn(0.0, 1.0)
                    val out = hslToRgb(hsl[0], hsl[1], hsl[2])
                    r = out[0]; g = out[1]; b = out[2]
                }
                "lumOff" -> {
                    val hsl = rgbToHsl(r, g, b)
                    hsl[2] = (hsl[2] + (valAttr / 100000.0)).coerceIn(0.0, 1.0)
                    val out = hslToRgb(hsl[0], hsl[1], hsl[2])
                    r = out[0]; g = out[1]; b = out[2]
                }
            }
        }
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun parseEmbeddedFonts(presRoot: Element, presRels: Map<String, String>): List<PptxEmbeddedFont> {
        val listEl = presRoot.firstChildNamed("embeddedFontLst") ?: return emptyList()
        val out = mutableListOf<PptxEmbeddedFont>()
        for (fontEl in listEl.childElementsNamed("embeddedFont")) {
            val typeface = attr(fontEl.firstChildNamed("font"), "typeface") ?: continue
            val variants = listOf(
                Triple("regular", false, false),
                Triple("bold", true, false),
                Triple("italic", false, true),
                Triple("boldItalic", true, true)
            )
            for ((tag, isBold, isItalic) in variants) {
                val variantEl = fontEl.firstChildNamed(tag) ?: continue
                val rid = relIdAttr(variantEl) ?: continue
                val path = presRels[rid] ?: continue
                val fontKey = attr(variantEl, "fontKey")
                    ?: path.substringAfterLast('/').substringBeforeLast('.')
                out += PptxEmbeddedFont(
                    typeface = typeface,
                    bold = isBold,
                    italic = isItalic,
                    zipEntryPath = path,
                    fontKeyGuid = fontKey
                )
            }
        }
        return out
    }

    private fun loadFallbackMaster(
        zip: ZipFile,
        masterCache: MutableMap<String, SlideMasterData>,
        defaultPresStyles: Map<Int, TextStyleLevel>,
        tracker: ByteReadTracker
    ): SlideMasterData? {
        val firstMaster = zip.entries().asSequence()
            .firstOrNull { !it.isDirectory && it.name.matches(Regex("ppt/slideMasters/slideMaster\\d+\\.xml")) }
            ?.name ?: return null
        return loadSlideMaster(zip, firstMaster, masterCache, defaultPresStyles, tracker)
    }

    private fun loadSlideLayout(
        zip: ZipFile,
        layoutPath: String,
        layoutCache: MutableMap<String, SlideLayoutData>,
        masterCache: MutableMap<String, SlideMasterData>,
        defaultPresStyles: Map<Int, TextStyleLevel>,
        tracker: ByteReadTracker
    ): SlideLayoutData? {
        layoutCache[layoutPath]?.let { return it }
        val entry = resolveSafeEntry(zip, layoutPath) ?: return null
        val doc = parseXmlSafe(readEntryBytesBounded(zip, entry, MAX_XML_BYTES, tracker))
        val root = doc.documentElement
        val rels = parseRelationships(zip, layoutPath, tracker)

        val masterPath = rels.values.firstOrNull { it.contains("slideMasters/") }
        val master = (masterPath?.let { loadSlideMaster(zip, it, masterCache, defaultPresStyles, tracker) }
            ?: loadFallbackMaster(zip, masterCache, defaultPresStyles, tracker)) ?: return null

        val clrMap = parseColorMapOvr(root, master.clrMap)
        val showMasterSp = attr(root, "showMasterSp") != "0"
        val cSld = root.firstChildNamed("cSld")
        val bgPair = parseBackground(cSld?.firstChildNamed("bg"), rels, master.theme, clrMap)

        val decorative = mutableListOf<PptxElement>()
        val placeholders = mutableListOf<PlaceholderTemplate>()
        cSld?.firstChildNamed("spTree")?.let { spTree ->
            collectMasterOrLayoutShapes(spTree, rels, master.theme, clrMap, decorative, placeholders)
        }

        return SlideLayoutData(
            entryPath = layoutPath,
            rels = rels,
            master = master,
            showMasterSp = showMasterSp,
            background = bgPair.first,
            backgroundImagePath = bgPair.second,
            decorativeElements = decorative,
            placeholders = placeholders
        ).also { layoutCache[layoutPath] = it }
    }

    private fun loadSlideMaster(
        zip: ZipFile,
        masterPath: String,
        masterCache: MutableMap<String, SlideMasterData>,
        defaultPresStyles: Map<Int, TextStyleLevel>,
        tracker: ByteReadTracker
    ): SlideMasterData? {
        masterCache[masterPath]?.let { return it }
        val entry = resolveSafeEntry(zip, masterPath) ?: return null
        val doc = parseXmlSafe(readEntryBytesBounded(zip, entry, MAX_XML_BYTES, tracker))
        val root = doc.documentElement
        val rels = parseRelationships(zip, masterPath, tracker)

        val themePath = rels.values.firstOrNull { it.contains("theme/") }
        val theme = themePath?.let { loadTheme(zip, it, tracker) } ?: defaultTheme()
        val clrMap = parseClrMap(root.firstChildNamed("clrMap"), defaultClrMap())

        val txStyles = root.firstChildNamed("txStyles")
        val titleStyles = parseListStyles(txStyles?.firstChildNamed("titleStyle"), theme, clrMap)
        val bodyStyles = parseListStyles(txStyles?.firstChildNamed("bodyStyle"), theme, clrMap)
        val otherStyles = parseListStyles(txStyles?.firstChildNamed("otherStyle"), theme, clrMap)
            .ifEmpty { defaultPresStyles }

        val cSld = root.firstChildNamed("cSld")
        val bgPair = parseBackground(cSld?.firstChildNamed("bg"), rels, theme, clrMap)
        val decorative = mutableListOf<PptxElement>()
        val placeholders = mutableListOf<PlaceholderTemplate>()
        cSld?.firstChildNamed("spTree")?.let { spTree ->
            collectMasterOrLayoutShapes(spTree, rels, theme, clrMap, decorative, placeholders)
        }

        return SlideMasterData(
            entryPath = masterPath,
            rels = rels,
            theme = theme,
            clrMap = clrMap,
            background = bgPair.first,
            backgroundImagePath = bgPair.second,
            decorativeElements = decorative,
            placeholders = placeholders,
            titleStyles = titleStyles,
            bodyStyles = bodyStyles,
            otherStyles = otherStyles
        ).also { masterCache[masterPath] = it }
    }

    private fun loadTheme(zip: ZipFile, themePath: String, tracker: ByteReadTracker): ThemeData {
        val entry = resolveSafeEntry(zip, themePath) ?: return defaultTheme()
        val doc = parseXmlSafe(readEntryBytesBounded(zip, entry, MAX_XML_BYTES, tracker))
        val elementsEl = doc.documentElement.firstChildNamed("themeElements") ?: return defaultTheme()

        val clrSchemeMap = defaultTheme().schemeColors.toMutableMap()
        elementsEl.firstChildNamed("clrScheme")?.let { clrEl ->
            for (child in childElements(clrEl)) {
                val colorName = child.localNameOrRaw()
                val rgb = resolveSimpleColorChild(child)
                if (rgb != null) clrSchemeMap[colorName] = rgb
            }
        }

        var majorFont = "Calibri"
        var minorFont = "Calibri"
        elementsEl.firstChildNamed("fontScheme")?.let { fEl ->
            attr(fEl.firstChildNamed("majorFont")?.firstChildNamed("latin"), "typeface")
                ?.takeIf { it.isNotBlank() }?.let { majorFont = it }
            attr(fEl.firstChildNamed("minorFont")?.firstChildNamed("latin"), "typeface")
                ?.takeIf { it.isNotBlank() }?.let { minorFont = it }
        }

        return ThemeData(clrSchemeMap, majorFont, minorFont)
    }

    private fun collectMasterOrLayoutShapes(
        spTree: Element,
        rels: Map<String, String>,
        theme: ThemeData,
        clrMap: Map<String, String>,
        decorativeOut: MutableList<PptxElement>,
        placeholdersOut: MutableList<PlaceholderTemplate>
    ) {
        for (child in childElements(spTree)) {
            when (child.localNameOrRaw()) {
                "sp" -> {
                    val phEl = child.firstChildNamed("nvSpPr")?.firstChildNamed("nvPr")?.firstChildNamed("ph")
                    val spPr = child.firstChildNamed("spPr")
                    val xfrm = parseXfrm(spPr?.firstChildNamed("xfrm"))
                    val geomPair = parseGeometry(spPr?.firstChildNamed("prstGeom"))
                    val fill = parseFill(spPr, child.firstChildNamed("style"), theme, clrMap)
                    val stroke = parseStroke(spPr?.firstChildNamed("ln"), child.firstChildNamed("style"), theme, clrMap)
                    val txBody = child.firstChildNamed("txBody")
                    val lstStyles = parseListStyles(txBody?.firstChildNamed("lstStyle"), theme, clrMap)
                    val (anchor, insets) = parseBodyPropsMeta(txBody?.firstChildNamed("bodyPr"))

                    if (phEl != null) {
                        placeholdersOut += PlaceholderTemplate(
                            type = attr(phEl, "type"),
                            idx = attr(phEl, "idx"),
                            xfrm = xfrm,
                            presetGeom = geomPair?.first,
                            cornerRadiusFrac = geomPair?.second,
                            fill = fill,
                            stroke = stroke,
                            listStyles = lstStyles,
                            anchor = anchor,
                            insets = insets
                        )
                    } else if (xfrm != null && xfrm.width > 0f && xfrm.height > 0f) {
                        val parsedBody = if (txBody != null && txBodyHasVisibleText(txBody)) {
                            parseTextBody(
                                txBody = txBody,
                                lstStyles = lstStyles,
                                inheritedDefaults = emptyMap(),
                                anchor = anchor ?: VerticalAnchor.TOP,
                                insets = insets ?: floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f),
                                theme = theme,
                                clrMap = clrMap,
                                collectFonts = emptySet<String>().toMutableSet()
                            )
                        } else null
                        if (fill != null || stroke != null || parsedBody != null) {
                            decorativeOut += PptxElement.Shape(
                                presetGeom = geomPair?.first ?: "rect",
                                cornerRadiusFrac = geomPair?.second ?: 0.12f,
                                xfrm = xfrm,
                                fill = fill,
                                stroke = stroke,
                                textBody = parsedBody,
                                placeholderType = null,
                                isFromMasterOrLayout = true
                            )
                        }
                    }
                }
                "pic" -> {
                    val phEl = child.firstChildNamed("nvPicPr")?.firstChildNamed("nvPr")?.firstChildNamed("ph")
                    if (phEl == null) {
                        parsePicture(child, rels, null)?.let { decorativeOut += it }
                    }
                }
                "grpSp" -> {
                    parseGroupAsElements(
                        grpEl = child,
                        rels = rels,
                        theme = theme,
                        clrMap = clrMap,
                        layoutPlaceholders = null,
                        master = null,
                        parentGroup = null,
                        collectFonts = emptySet<String>().toMutableSet(),
                        out = decorativeOut,
                        isFromMasterOrLayout = true
                    )
                }
            }
        }
    }

    private fun parseShapeTree(
        spTree: Element,
        rels: Map<String, String>,
        theme: ThemeData,
        clrMap: Map<String, String>,
        layoutPlaceholders: List<PlaceholderTemplate>,
        master: SlideMasterData?,
        defaultPresStyles: Map<Int, TextStyleLevel>,
        groupTransform: GroupTransform?,
        collectFonts: MutableSet<String>,
        out: MutableList<PptxElement>,
        isFromMasterOrLayout: Boolean = false
    ) {
        for (child in childElements(spTree)) {
            when (child.localNameOrRaw()) {
                "sp", "cxnSp" -> {
                    parseShape(
                        spEl = child,
                        isConnector = child.localNameOrRaw() == "cxnSp",
                        theme = theme,
                        clrMap = clrMap,
                        layoutPlaceholders = layoutPlaceholders,
                        master = master,
                        defaultPresStyles = defaultPresStyles,
                        groupTransform = groupTransform,
                        collectFonts = collectFonts,
                        isFromMasterOrLayout = isFromMasterOrLayout
                    )?.let { out += it }
                }
                "pic" -> parsePicture(child, rels, groupTransform)?.let { out += it }
                "graphicFrame" -> parseGraphicFrame(child, theme, clrMap, defaultPresStyles, groupTransform, collectFonts)?.let { out += it }
                "grpSp" -> parseGroupAsElements(
                    grpEl = child,
                    rels = rels,
                    theme = theme,
                    clrMap = clrMap,
                    layoutPlaceholders = layoutPlaceholders,
                    master = master,
                    parentGroup = groupTransform,
                    collectFonts = collectFonts,
                    out = out,
                    defaultPresStyles = defaultPresStyles,
                    isFromMasterOrLayout = isFromMasterOrLayout
                )
            }
        }
    }

    private data class GroupTransform(
        val outX: Float,
        val outY: Float,
        val outW: Float,
        val outH: Float,
        val chX: Float,
        val chY: Float,
        val chW: Float,
        val chH: Float
    ) {
        fun map(xfrm: Xfrm): Xfrm {
            val scaleX = if (chW > 0f) outW / chW else 1f
            val scaleY = if (chH > 0f) outH / chH else 1f
            return xfrm.copy(
                x = outX + (xfrm.x - chX) * scaleX,
                y = outY + (xfrm.y - chY) * scaleY,
                width = xfrm.width * scaleX,
                height = xfrm.height * scaleY
            )
        }
    }

    private fun parseGroupAsElements(
        grpEl: Element,
        rels: Map<String, String>,
        theme: ThemeData,
        clrMap: Map<String, String>,
        layoutPlaceholders: List<PlaceholderTemplate>?,
        master: SlideMasterData?,
        parentGroup: GroupTransform?,
        collectFonts: MutableSet<String>,
        out: MutableList<PptxElement>,
        defaultPresStyles: Map<Int, TextStyleLevel> = emptyMap(),
        isFromMasterOrLayout: Boolean = false
    ) {
        val xfrmEl = grpEl.firstChildNamed("grpSpPr")?.firstChildNamed("xfrm")
        val rawOuter = parseXfrm(xfrmEl)
        val chOff = xfrmEl?.firstChildNamed("chOff")
        val chExt = xfrmEl?.firstChildNamed("chExt")

        val nextGroup = if (rawOuter != null && chOff != null && chExt != null) {
            val mappedOuter = parentGroup?.map(rawOuter) ?: rawOuter
            GroupTransform(
                outX = mappedOuter.x,
                outY = mappedOuter.y,
                outW = mappedOuter.width,
                outH = mappedOuter.height,
                chX = emuAttrToPt(chOff, "x", 0f),
                chY = emuAttrToPt(chOff, "y", 0f),
                chW = emuAttrToPt(chExt, "cx", mappedOuter.width),
                chH = emuAttrToPt(chExt, "cy", mappedOuter.height)
            )
        } else {
            parentGroup
        }

        parseShapeTree(
            spTree = grpEl,
            rels = rels,
            theme = theme,
            clrMap = clrMap,
            layoutPlaceholders = layoutPlaceholders.orEmpty(),
            master = master,
            defaultPresStyles = defaultPresStyles,
            groupTransform = nextGroup,
            collectFonts = collectFonts,
            out = out,
            isFromMasterOrLayout = isFromMasterOrLayout
        )
    }

    private fun parseShape(
        spEl: Element,
        isConnector: Boolean,
        theme: ThemeData,
        clrMap: Map<String, String>,
        layoutPlaceholders: List<PlaceholderTemplate>,
        master: SlideMasterData?,
        defaultPresStyles: Map<Int, TextStyleLevel>,
        groupTransform: GroupTransform?,
        collectFonts: MutableSet<String>,
        isFromMasterOrLayout: Boolean = false
    ): PptxElement.Shape? {
        val nvPr = spEl.firstChildNamed(if (isConnector) "nvCxnSpPr" else "nvSpPr")?.firstChildNamed("nvPr")
        val phEl = nvPr?.firstChildNamed("ph")
        val phType = attr(phEl, "type")
        val phIdx = attr(phEl, "idx")

        val layoutMatch = if (phEl != null) matchPlaceholder(layoutPlaceholders, phType, phIdx) else null
        val masterMatch = if (phEl != null) matchPlaceholder(master?.placeholders.orEmpty(), phType, phIdx) else null

        val spPr = spEl.firstChildNamed("spPr")
        val ownXfrm = parseXfrm(spPr?.firstChildNamed("xfrm"))
        val resolvedXfrm = (ownXfrm ?: layoutMatch?.xfrm ?: masterMatch?.xfrm)
            ?.let { groupTransform?.map(it) ?: it }
            ?: return null

        val geom = parseGeometry(spPr?.firstChildNamed("prstGeom"))
        val presetGeom = geom?.first ?: layoutMatch?.presetGeom ?: masterMatch?.presetGeom ?: if (isConnector) "line" else "rect"
        val cornerRadius = geom?.second ?: layoutMatch?.cornerRadiusFrac ?: masterMatch?.cornerRadiusFrac ?: 0.12f

        val styleEl = spEl.firstChildNamed("style")
        val fill = if (isConnector) null else {
            parseFill(spPr, styleEl, theme, clrMap, fallback = layoutMatch?.fill ?: masterMatch?.fill)
        }
        val stroke = parseStroke(
            spPr?.firstChildNamed("ln"),
            styleEl,
            theme,
            clrMap,
            fallback = layoutMatch?.stroke ?: masterMatch?.stroke,
            forceVisibleIfConnector = isConnector
        )

        val masterTxRole = when (phType) {
            "title", "ctrTitle" -> master?.titleStyles.orEmpty()
            "body", "subTitle" -> master?.bodyStyles.orEmpty()
            null -> emptyMap()
            else -> master?.otherStyles.orEmpty()
        }

        val combinedDefaults = mutableMapOf<Int, TextStyleLevel>()
        for (lvl in 0..8) {
            val basePres = defaultPresStyles[lvl] ?: defaultPresStyles[0]
            val mTx = masterTxRole[lvl]?.mergedOver(basePres) ?: basePres
            val mPh = masterMatch?.listStyles?.get(lvl)?.mergedOver(mTx) ?: mTx
            val lPh = layoutMatch?.listStyles?.get(lvl)?.mergedOver(mPh) ?: mPh
            if (lPh != null) combinedDefaults[lvl] = lPh
        }

        val txBody = spEl.firstChildNamed("txBody")
        val bodyPr = txBody?.firstChildNamed("bodyPr")
        val (ownAnchor, ownInsets) = parseBodyPropsMeta(bodyPr)
        val anchor = ownAnchor ?: layoutMatch?.anchor ?: masterMatch?.anchor ?: if (phType == "ctrTitle") VerticalAnchor.CENTER else VerticalAnchor.TOP
        val insets = ownInsets ?: layoutMatch?.insets ?: masterMatch?.insets ?: floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)

        val slideLstStyle = parseListStyles(txBody?.firstChildNamed("lstStyle"), theme, clrMap)
        val parsedBody = if (txBody != null && txBodyHasVisibleText(txBody)) {
            parseTextBody(txBody, slideLstStyle, combinedDefaults, anchor, insets, theme, clrMap, collectFonts)
        } else null

        if (resolvedXfrm.width <= 0f && resolvedXfrm.height <= 0f) return null
        if (fill == null && stroke == null && parsedBody == null) return null

        return PptxElement.Shape(
            presetGeom = presetGeom,
            cornerRadiusFrac = cornerRadius,
            xfrm = resolvedXfrm,
            fill = fill,
            stroke = stroke,
            textBody = parsedBody,
            placeholderType = phType,
            isFromMasterOrLayout = isFromMasterOrLayout
        )
    }

    private fun parsePicture(
        picEl: Element,
        rels: Map<String, String>,
        groupTransform: GroupTransform?
    ): PptxElement.Picture? {
        val spPr = picEl.firstChildNamed("spPr")
        val rawXfrm = parseXfrm(spPr?.firstChildNamed("xfrm")) ?: return null
        val xfrm = groupTransform?.map(rawXfrm) ?: rawXfrm
        if (xfrm.width <= 0f || xfrm.height <= 0f) return null

        val blipFill = picEl.firstChildNamed("blipFill")
        val blip = blipFill?.firstChildNamed("blip") ?: return null
        val embedRid = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
            .takeIf { it.isNotBlank() }
            ?: attr(blip, "r:embed")
            ?: return null
        val imagePath = rels[embedRid] ?: return null

        val srcRect = blipFill.firstChildNamed("srcRect")
        val crop = CropRect(
            leftFrac = (attr(srcRect, "l")?.toFloatOrNull() ?: 0f) / 100000f,
            topFrac = (attr(srcRect, "t")?.toFloatOrNull() ?: 0f) / 100000f,
            rightFrac = (attr(srcRect, "r")?.toFloatOrNull() ?: 0f) / 100000f,
            bottomFrac = (attr(srcRect, "b")?.toFloatOrNull() ?: 0f) / 100000f
        )

        return PptxElement.Picture(
            xfrm = xfrm,
            imageEntryPath = imagePath,
            crop = crop
        )
    }

    private fun parseGraphicFrame(
        frameEl: Element,
        theme: ThemeData,
        clrMap: Map<String, String>,
        defaultPresStyles: Map<Int, TextStyleLevel>,
        groupTransform: GroupTransform?,
        collectFonts: MutableSet<String>
    ): PptxElement.Table? {
        val rawXfrm = parseXfrm(frameEl.firstChildNamed("xfrm")) ?: return null
        val xfrm = groupTransform?.map(rawXfrm) ?: rawXfrm
        val tblEl = frameEl.firstChildNamed("graphic")
            ?.firstChildNamed("graphicData")
            ?.firstChildNamed("tbl") ?: return null

        val tblPr = tblEl.firstChildNamed("tblPr")
        val firstRowStyled = attr(tblPr, "firstRow") != "0"

        val gridCols = tblEl.firstChildNamed("tblGrid")?.childElementsNamed("gridCol").orEmpty()
        val rawColWidths = gridCols.map { emuAttrToPt(it, "w", 80f).coerceAtLeast(12f) }
        if (rawColWidths.isEmpty()) return null
        val totalColW = rawColWidths.sum()
        val scaleW = if (xfrm.width > 0f && totalColW > 0f) xfrm.width / totalColW else 1f
        val colWidthsPt = rawColWidths.map { it * scaleW }

        val rowEls = tblEl.childElementsNamed("tr")
        if (rowEls.isEmpty()) return null

        val parsedRows = rowEls.mapIndexed { rIdx, trEl ->
            val hPt = emuAttrToPt(trEl, "h", 28f).coerceAtLeast(18f)
            val cells = trEl.childElementsNamed("tc").map { tcEl ->
                val gridSpan = (attr(tcEl, "gridSpan")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val rowSpan = (attr(tcEl, "rowSpan")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val mergedAway = attr(tcEl, "hMerge") == "1" || attr(tcEl, "vMerge") == "1"
                val tcPr = tcEl.firstChildNamed("tcPr")

                val explicitFill = parseSolidFillOnly(tcPr?.firstChildNamed("solidFill"), theme, clrMap)
                val defaultFill = if (explicitFill == null && tcPr?.firstChildNamed("noFill") == null) {
                    if (firstRowStyled && rIdx == 0) {
                        theme.schemeColors["accent1"]?.let { (0xFF shl 24) or (it and 0x00FFFFFF) }
                    } else if (rIdx % 2 == 1) {
                        0x14888888
                    } else null
                } else null

                val borderDefault = PptxStroke(0.75f, 0xFF9E9E9E.toInt())
                val sL = parseStroke(tcPr?.firstChildNamed("lnL"), null, theme, clrMap) ?: if (tcPr?.firstChildNamed("lnL")?.firstChildNamed("noFill") == null) borderDefault else null
                val sT = parseStroke(tcPr?.firstChildNamed("lnT"), null, theme, clrMap) ?: if (tcPr?.firstChildNamed("lnT")?.firstChildNamed("noFill") == null) borderDefault else null
                val sR = parseStroke(tcPr?.firstChildNamed("lnR"), null, theme, clrMap) ?: if (tcPr?.firstChildNamed("lnR")?.firstChildNamed("noFill") == null) borderDefault else null
                val sB = parseStroke(tcPr?.firstChildNamed("lnB"), null, theme, clrMap) ?: if (tcPr?.firstChildNamed("lnB")?.firstChildNamed("noFill") == null) borderDefault else null

                val txBody = tcEl.firstChildNamed("txBody")
                val anchor = when (attr(tcPr, "anchor")) {
                    "ctr" -> VerticalAnchor.CENTER
                    "b" -> VerticalAnchor.BOTTOM
                    else -> VerticalAnchor.CENTER
                }
                val marL = emuAttrToPt(tcPr, "marL", 5.5f)
                val marT = emuAttrToPt(tcPr, "marT", 3.5f)
                val marR = emuAttrToPt(tcPr, "marR", 5.5f)
                val marB = emuAttrToPt(tcPr, "marB", 3.5f)
                val headerDefaults = if (firstRowStyled && rIdx == 0 && explicitFill == null) {
                    mapOf(0 to TextStyleLevel(fontFamily = theme.minorLatinFont, fontSizePt = 12f, bold = true, colorArgb = 0xFFFFFFFF.toInt()))
                } else {
                    mapOf(0 to (defaultPresStyles[0]?.copy(fontSizePt = 11.5f) ?: TextStyleLevel(fontFamily = theme.minorLatinFont, fontSizePt = 11.5f, colorArgb = resolveMappedColor("dk1", theme, clrMap) or (0xFF shl 24))))
                }

                val body = if (txBody != null && txBodyHasVisibleText(txBody)) {
                    parseTextBody(txBody, emptyMap(), headerDefaults, anchor, floatArrayOf(marL, marT, marR, marB), theme, clrMap, collectFonts)
                } else null

                PptxElement.TableCell(
                    colSpan = gridSpan,
                    rowSpan = rowSpan,
                    mergedAway = mergedAway,
                    fillArgb = explicitFill ?: defaultFill,
                    strokeLeft = sL,
                    strokeTop = sT,
                    strokeRight = sR,
                    strokeBottom = sB,
                    textBody = body
                )
            }
            PptxElement.TableRow(hPt, cells)
        }

        val totalTableH = parsedRows.sumOf { it.heightPt.toDouble() }.toFloat().coerceAtLeast(xfrm.height)
        return PptxElement.Table(
            xfrm = xfrm.copy(width = colWidthsPt.sum(), height = totalTableH),
            colWidthsPt = colWidthsPt,
            rows = parsedRows
        )
    }

    private fun parseTextBody(
        txBody: Element,
        lstStyles: Map<Int, TextStyleLevel>,
        inheritedDefaults: Map<Int, TextStyleLevel>,
        anchor: VerticalAnchor,
        insets: FloatArray,
        theme: ThemeData,
        clrMap: Map<String, String>,
        collectFonts: MutableSet<String>
    ): PptxTextBody {
        val bodyPr = txBody.firstChildNamed("bodyPr")
        val wrap = attr(bodyPr, "wrap") != "none"
        val normAutofit = bodyPr?.firstChildNamed("normAutofit")
        val fontScale = (attr(normAutofit, "fontScale")?.toFloatOrNull() ?: 100000f) / 100000f

        val paragraphs = mutableListOf<PptxParagraph>()
        val autoCounters = IntArray(9) { 1 }

        for (pEl in txBody.childElementsNamed("p")) {
            val pPr = pEl.firstChildNamed("pPr")
            val level = (attr(pPr, "lvl")?.toIntOrNull() ?: 0).coerceIn(0, 8)
            val inherited = (lstStyles[level] ?: lstStyles[0])
                ?.mergedOver(inheritedDefaults[level] ?: inheritedDefaults[0])
                ?: inheritedDefaults[level] ?: inheritedDefaults[0]
            val pLevelStyle = parseParagraphStyleLevel(pPr, theme, clrMap).mergedOver(inherited)

            val align = pLevelStyle.align ?: HorizontalAlign.LEFT
            val marLeft = pLevelStyle.marginLeftPt ?: (level * 18f)
            val indent = pLevelStyle.indentPt ?: 0f
            val spcBef = pLevelStyle.spaceBeforePt ?: 0f
            val spcAft = pLevelStyle.spaceAfterPt ?: 4f
            val lineSp = pLevelStyle.lineSpacingMult ?: 1.15f

            val bulletPrefix = when {
                pLevelStyle.bulletNone -> null
                pLevelStyle.bulletAutoNum -> "${autoCounters[level]++}."
                !pLevelStyle.bulletChar.isNullOrEmpty() -> pLevelStyle.bulletChar
                else -> null
            }

            val defaultRunLevel = pEl.firstChildNamed("endParaRPr")?.let { parseRunProps(it, theme, clrMap) }
            val runs = mutableListOf<PptxTextRun>()
            for (child in childElements(pEl)) {
                when (child.localNameOrRaw()) {
                    "r", "fld" -> {
                        val tText = child.firstChildNamed("t")?.textContent ?: ""
                        if (tText.isNotEmpty()) {
                            val rPr = child.firstChildNamed("rPr")
                            val rStyle = if (rPr != null) parseRunProps(rPr, theme, clrMap) else null
                            runs += buildTextRun(tText, rStyle, pLevelStyle, defaultRunLevel, theme, clrMap, collectFonts)
                        }
                    }
                    "br" -> {
                        runs += buildTextRun("\n", null, pLevelStyle, defaultRunLevel, theme, clrMap, collectFonts)
                    }
                }
            }

            if (runs.isEmpty()) {
                runs += buildTextRun("", null, pLevelStyle, defaultRunLevel, theme, clrMap, collectFonts)
            }

            paragraphs += PptxParagraph(
                level = level,
                align = align,
                bulletPrefix = bulletPrefix,
                marginLeftPt = marLeft,
                indentPt = indent,
                spaceBeforePt = spcBef,
                spaceAfterPt = spcAft,
                lineSpacingMult = lineSp,
                runs = runs
            )
        }

        return PptxTextBody(
            anchor = anchor,
            wrap = wrap,
            insetLeftPt = insets[0],
            insetTopPt = insets[1],
            insetRightPt = insets[2],
            insetBottomPt = insets[3],
            fontScale = fontScale.coerceIn(0.4f, 1.5f),
            paragraphs = paragraphs
        )
    }

    private fun buildTextRun(
        text: String,
        rStyle: RunProps?,
        pStyle: TextStyleLevel,
        fallbackEndProps: RunProps?,
        theme: ThemeData,
        clrMap: Map<String, String>,
        collectFonts: MutableSet<String>
    ): PptxTextRun {
        val rawFamily = rStyle?.fontFamily
            ?: pStyle.fontFamily
            ?: fallbackEndProps?.fontFamily
            ?: theme.minorLatinFont
        val resolvedFamily = resolveThemeFontToken(rawFamily, theme)
        collectFonts.add(resolvedFamily)

        val sizePt = rStyle?.fontSizePt
            ?: pStyle.fontSizePt
            ?: fallbackEndProps?.fontSizePt
            ?: 18f
        val bold = rStyle?.bold ?: pStyle.bold ?: fallbackEndProps?.bold ?: false
        val italic = rStyle?.italic ?: pStyle.italic ?: fallbackEndProps?.italic ?: false
        val underline = rStyle?.underline ?: false
        val strike = rStyle?.strikethrough ?: false
        val baseline = rStyle?.baselinePercent ?: 0
        val defaultTextColor = resolveMappedColor("tx1", theme, clrMap) or (0xFF shl 24)
        val colorArgb = rStyle?.colorArgb ?: pStyle.colorArgb ?: fallbackEndProps?.colorArgb ?: defaultTextColor

        return PptxTextRun(
            text = text,
            fontFamily = resolvedFamily,
            fontSizePt = sizePt.coerceIn(6f, 200f),
            bold = bold,
            italic = italic,
            underline = underline,
            strikethrough = strike,
            baselinePercent = baseline,
            colorArgb = colorArgb
        )
    }

    private data class RunProps(
        val fontFamily: String?,
        val fontSizePt: Float?,
        val bold: Boolean?,
        val italic: Boolean?,
        val underline: Boolean,
        val strikethrough: Boolean,
        val baselinePercent: Int,
        val colorArgb: Int?
    )

    private fun parseRunProps(rPr: Element, theme: ThemeData, clrMap: Map<String, String>): RunProps {
        val latin = rPr.firstChildNamed("latin") ?: rPr.firstChildNamed("ea") ?: rPr.firstChildNamed("cs")
        val fontFamily = attr(latin, "typeface")?.takeIf { it.isNotBlank() }
        val sz = attr(rPr, "sz")?.toFloatOrNull()?.div(100f)
        val bAttr = attr(rPr, "b")?.let { it == "1" || it == "true" }
        val iAttr = attr(rPr, "i")?.let { it == "1" || it == "true" }
        val uAttr = attr(rPr, "u")?.let { it != "none" && it.isNotBlank() } ?: false
        val sAttr = attr(rPr, "strike")?.let { it != "noStrike" && it.isNotBlank() } ?: false
        val baseline = attr(rPr, "baseline")?.toIntOrNull()?.div(1000) ?: 0
        val colorArgb = parseSolidFillOnly(rPr.firstChildNamed("solidFill"), theme, clrMap)
        return RunProps(fontFamily, sz, bAttr, iAttr, uAttr, sAttr, baseline, colorArgb)
    }

    private fun parseListStyles(lstEl: Element?, theme: ThemeData, clrMap: Map<String, String>): Map<Int, TextStyleLevel> {
        if (lstEl == null) return emptyMap()
        val map = mutableMapOf<Int, TextStyleLevel>()
        for (i in 0..8) {
            lstEl.firstChildNamed("lvl${i + 1}pPr")?.let { pPr ->
                map[i] = parseParagraphStyleLevel(pPr, theme, clrMap)
            }
        }
        lstEl.firstChildNamed("defPPr")?.let { pPr ->
            if (!map.containsKey(0)) map[0] = parseParagraphStyleLevel(pPr, theme, clrMap)
        }
        return map
    }

    private fun parseParagraphStyleLevel(pPr: Element?, theme: ThemeData, clrMap: Map<String, String>): TextStyleLevel {
        if (pPr == null) return TextStyleLevel()
        val defR = pPr.firstChildNamed("defRPr")?.let { parseRunProps(it, theme, clrMap) }
        val align = when (attr(pPr, "algn")) {
            "ctr" -> HorizontalAlign.CENTER
            "r" -> HorizontalAlign.RIGHT
            "just" -> HorizontalAlign.JUSTIFY
            "l" -> HorizontalAlign.LEFT
            else -> null
        }
        val buNone = pPr.firstChildNamed("buNone") != null
        val buChar = attr(pPr.firstChildNamed("buChar"), "char")
        val buAuto = pPr.firstChildNamed("buAutoNum") != null
        val marL = attr(pPr, "marL")?.toFloatOrNull()?.div(EMU_PER_PT)
        val indent = attr(pPr, "indent")?.toFloatOrNull()?.div(EMU_PER_PT)
        val spcBef = pPr.firstChildNamed("spcBef")?.firstChildNamed("spcPts")?.let { attr(it, "val")?.toFloatOrNull()?.div(100f) }
        val spcAft = pPr.firstChildNamed("spcAft")?.firstChildNamed("spcPts")?.let { attr(it, "val")?.toFloatOrNull()?.div(100f) }
        val lnSpc = pPr.firstChildNamed("lnSpc")?.firstChildNamed("spcPct")?.let { attr(it, "val")?.toFloatOrNull()?.div(100000f) }

        return TextStyleLevel(
            fontFamily = defR?.fontFamily,
            fontSizePt = defR?.fontSizePt,
            bold = defR?.bold,
            italic = defR?.italic,
            colorArgb = defR?.colorArgb,
            align = align,
            bulletChar = buChar,
            bulletAutoNum = buAuto,
            bulletNone = buNone,
            marginLeftPt = marL,
            indentPt = indent,
            spaceBeforePt = spcBef,
            spaceAfterPt = spcAft,
            lineSpacingMult = lnSpc
        )
    }

    private fun parseBodyPropsMeta(bodyPr: Element?): Pair<VerticalAnchor?, FloatArray?> {
        if (bodyPr == null) return null to null
        val anchor = when (attr(bodyPr, "anchor")) {
            "ctr" -> VerticalAnchor.CENTER
            "b" -> VerticalAnchor.BOTTOM
            "t" -> VerticalAnchor.TOP
            else -> null
        }
        val hasAnyInset = bodyPr.hasAttribute("lIns") || bodyPr.hasAttribute("tIns") || bodyPr.hasAttribute("rIns") || bodyPr.hasAttribute("bIns")
        val insets = if (hasAnyInset) {
            floatArrayOf(
                emuAttrToPt(bodyPr, "lIns", 7.2f),
                emuAttrToPt(bodyPr, "tIns", 3.6f),
                emuAttrToPt(bodyPr, "rIns", 7.2f),
                emuAttrToPt(bodyPr, "bIns", 3.6f)
            )
        } else null
        return anchor to insets
    }

    private fun matchPlaceholder(
        templates: List<PlaceholderTemplate>,
        type: String?,
        idx: String?
    ): PlaceholderTemplate? {
        if (templates.isEmpty()) return null
        if (idx != null) {
            templates.firstOrNull { it.idx == idx && (type == null || it.type == type) }?.let { return it }
            templates.firstOrNull { it.idx == idx }?.let { return it }
        }
        if (type != null) {
            templates.firstOrNull { it.type == type }?.let { return it }
        }
        return null
    }

    private fun parseBackground(
        bgEl: Element?,
        rels: Map<String, String>,
        theme: ThemeData,
        clrMap: Map<String, String>
    ): Pair<PptxFill?, String?> {
        if (bgEl == null) return null to null
        val bgPr = bgEl.firstChildNamed("bgPr")
        if (bgPr != null) {
            bgPr.firstChildNamed("blipFill")?.firstChildNamed("blip")?.let { blip ->
                val rid = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                    .takeIf { it.isNotBlank() } ?: attr(blip, "r:embed")
                val path = rid?.let { rels[it] }
                if (path != null) return null to path
            }
            bgPr.firstChildNamed("gradFill")?.let { gf ->
                parseLinearGradient(gf, theme, clrMap)?.let { return it to null }
            }
            parseSolidFillOnly(bgPr.firstChildNamed("solidFill"), theme, clrMap)?.let {
                return PptxFill.Solid(it) to null
            }
        }
        bgEl.firstChildNamed("bgRef")?.let { bgRef ->
            resolveColorContainer(bgRef, theme, clrMap)?.let {
                return PptxFill.Solid(it) to null
            }
        }
        return null to null
    }

    private fun parseFill(
        spPr: Element?,
        styleEl: Element?,
        theme: ThemeData,
        clrMap: Map<String, String>,
        fallback: PptxFill? = null
    ): PptxFill? {
        if (spPr?.firstChildNamed("noFill") != null) return null
        spPr?.firstChildNamed("solidFill")?.let { sf ->
            parseSolidFillOnly(sf, theme, clrMap)?.let { return PptxFill.Solid(it) }
        }
        spPr?.firstChildNamed("gradFill")?.let { gf ->
            parseLinearGradient(gf, theme, clrMap)?.let { return it }
        }
        if (spPr != null) {
            styleEl?.firstChildNamed("fillRef")?.let { fillRef ->
                val idx = attr(fillRef, "idx")?.toIntOrNull() ?: 0
                if (idx > 0) {
                    resolveColorContainer(fillRef, theme, clrMap)?.let { return PptxFill.Solid(it) }
                }
            }
        }
        return fallback
    }

    private fun parseLinearGradient(gfEl: Element, theme: ThemeData, clrMap: Map<String, String>): PptxFill.LinearGradient? {
        val stops = gfEl.firstChildNamed("gsLst")?.childElementsNamed("gs")?.mapNotNull { gs ->
            val pos = ((attr(gs, "pos")?.toFloatOrNull() ?: 0f) / 100000f).coerceIn(0f, 1f)
            val argb = resolveColorContainer(gs, theme, clrMap) ?: return@mapNotNull null
            GradientStop(pos, argb)
        }?.sortedBy { it.posFraction }.orEmpty()
        if (stops.size < 2) return null
        val angleDeg = (attr(gfEl.firstChildNamed("lin"), "ang")?.toFloatOrNull() ?: 0f) / 60000f
        return PptxFill.LinearGradient(angleDeg, stops)
    }

    private fun parseStroke(
        lnEl: Element?,
        styleEl: Element?,
        theme: ThemeData,
        clrMap: Map<String, String>,
        fallback: PptxStroke? = null,
        forceVisibleIfConnector: Boolean = false
    ): PptxStroke? {
        if (lnEl?.firstChildNamed("noFill") != null) return null
        if (lnEl != null) {
            val wPt = (attr(lnEl, "w")?.toFloatOrNull()?.div(EMU_PER_PT) ?: 1f).coerceIn(0.4f, 24f)
            val dashed = attr(lnEl.firstChildNamed("prstDash"), "val").let { it != null && it != "solid" }
            val explicitColor = parseSolidFillOnly(lnEl.firstChildNamed("solidFill"), theme, clrMap)
            if (explicitColor != null) return PptxStroke(wPt, explicitColor, dashed)
            if (forceVisibleIfConnector || lnEl.hasAttribute("w")) {
                val styleColor = styleEl?.firstChildNamed("lnRef")?.let { resolveColorContainer(it, theme, clrMap) }
                    ?: (resolveMappedColor("tx1", theme, clrMap) or (0xFF shl 24))
                return PptxStroke(wPt, styleColor, dashed)
            }
        }
        styleEl?.firstChildNamed("lnRef")?.let { lnRef ->
            val idx = attr(lnRef, "idx")?.toIntOrNull() ?: 0
            if (idx > 0) {
                resolveColorContainer(lnRef, theme, clrMap)?.let { return PptxStroke(1f, it, false) }
            }
        }
        return fallback
    }

    private fun parseSolidFillOnly(solidFillEl: Element?, theme: ThemeData, clrMap: Map<String, String>): Int? =
        solidFillEl?.let { resolveColorContainer(it, theme, clrMap) }

    private fun resolveColorContainer(container: Element, theme: ThemeData, clrMap: Map<String, String>): Int? {
        container.firstChildNamed("srgbClr")?.let { el ->
            val rgb = attr(el, "val")?.toIntOrNull(16) ?: return null
            return applyColorModifiers(rgb, el)
        }
        container.firstChildNamed("schemeClr")?.let { el ->
            val name = attr(el, "val") ?: return null
            val baseRgb = resolveMappedColor(name, theme, clrMap)
            return applyColorModifiers(baseRgb, el)
        }
        container.firstChildNamed("sysClr")?.let { el ->
            val rgb = attr(el, "lastClr")?.toIntOrNull(16) ?: 0x1A1A1A
            return applyColorModifiers(rgb, el)
        }
        container.firstChildNamed("prstClr")?.let { el ->
            val rgb = namedColorRgb(attr(el, "val")) ?: 0x1A1A1A
            return applyColorModifiers(rgb, el)
        }
        return null
    }

    private fun resolveMappedColor(name: String, theme: ThemeData, clrMap: Map<String, String>): Int {
        val mapped = clrMap[name] ?: name
        return theme.schemeColors[mapped] ?: theme.schemeColors[name] ?: when (name) {
            "lt1", "bg1" -> 0xFFFFFF
            "dk1", "tx1" -> 0x1A1A1A
            else -> 0x4A5568
        }
    }

    private fun resolveSimpleColorChild(colorSlot: Element): Int? {
        colorSlot.firstChildNamed("srgbClr")?.let { return attr(it, "val")?.toIntOrNull(16) }
        colorSlot.firstChildNamed("sysClr")?.let { return attr(it, "lastClr")?.toIntOrNull(16) }
        return null
    }

    private fun parseGeometry(prstGeom: Element?): Pair<String, Float>? {
        if (prstGeom == null) return null
        val prst = attr(prstGeom, "prst") ?: "rect"
        var adj = 0.16667f
        val gd = prstGeom.firstChildNamed("avLst")?.firstChildNamed("gd")
        attr(gd, "fmla")?.removePrefix("val ")?.toFloatOrNull()?.let { v ->
            adj = (v / 100000f).coerceIn(0.02f, 0.5f)
        }
        return prst to adj
    }

    private fun parseXfrm(xfrmEl: Element?): Xfrm? {
        if (xfrmEl == null) return null
        val off = xfrmEl.firstChildNamed("off")
        val ext = xfrmEl.firstChildNamed("ext")
        if (off == null && ext == null) return null
        return Xfrm(
            x = emuAttrToPt(off, "x", 0f),
            y = emuAttrToPt(off, "y", 0f),
            width = emuAttrToPt(ext, "cx", 0f).coerceAtLeast(0f),
            height = emuAttrToPt(ext, "cy", 0f).coerceAtLeast(0f),
            rotationDeg = (attr(xfrmEl, "rot")?.toFloatOrNull() ?: 0f) / 60000f,
            flipH = attr(xfrmEl, "flipH") == "1",
            flipV = attr(xfrmEl, "flipV") == "1"
        )
    }

    private fun resolveThemeFontToken(typeface: String, theme: ThemeData): String = when (typeface) {
        "+mj-lt", "+mj-ea", "+mj-cs" -> theme.majorLatinFont
        "+mn-lt", "+mn-ea", "+mn-cs" -> theme.minorLatinFont
        else -> typeface
    }

    private fun extractNotesText(zip: ZipFile, notesPath: String, tracker: ByteReadTracker): String {
        val entry = resolveSafeEntry(zip, notesPath) ?: return ""
        return runCatching {
            val doc = parseXmlSafe(readEntryBytesBounded(zip, entry, MAX_XML_BYTES, tracker))
            val cSld = doc.documentElement.firstChildNamed("cSld")
            val spTree = cSld?.firstChildNamed("spTree") ?: return ""
            val parts = mutableListOf<String>()
            for (sp in spTree.childElementsNamed("sp")) {
                val phType = attr(sp.firstChildNamed("nvSpPr")?.firstChildNamed("nvPr")?.firstChildNamed("ph"), "type")
                if (phType == "sldNum" || phType == "sldImg") continue
                val txBody = sp.firstChildNamed("txBody") ?: continue
                for (p in txBody.childElementsNamed("p")) {
                    val line = childElements(p)
                        .filter { it.localNameOrRaw() == "r" || it.localNameOrRaw() == "fld" }
                        .joinToString("") { it.firstChildNamed("t")?.textContent ?: "" }
                        .trim()
                    if (line.isNotEmpty()) parts += line
                }
            }
            parts.joinToString("\n")
        }.getOrDefault("")
    }

    private fun parseRelationships(zip: ZipFile, ownerPartPath: String, tracker: ByteReadTracker): Map<String, String> {
        val dir = ownerPartPath.substringBeforeLast('/', "")
        val file = ownerPartPath.substringAfterLast('/')
        val relsPath = if (dir.isEmpty()) "_rels/$file.rels" else "$dir/_rels/$file.rels"
        val entry = resolveSafeEntry(zip, relsPath) ?: return emptyMap()
        return runCatching {
            val doc = parseXmlSafe(readEntryBytesBounded(zip, entry, MAX_XML_BYTES, tracker))
            val map = mutableMapOf<String, String>()
            for (rel in doc.documentElement.childElementsNamed("Relationship")) {
                val id = attr(rel, "Id") ?: continue
                val target = attr(rel, "Target") ?: continue
                val targetMode = attr(rel, "TargetMode")
                if (targetMode.equals("External", ignoreCase = true)) continue
                normalizeOpcTarget(dir, target)?.let { map[id] = it }
            }
            map
        }.getOrDefault(emptyMap())
    }

    internal fun normalizeOpcTarget(baseDir: String, rawTarget: String): String? {
        val cleaned = rawTarget.trim().replace('\\', '/')
        if (cleaned.isEmpty() || cleaned.contains(':')) return null
        val combined = if (cleaned.startsWith("/")) {
            cleaned.trimStart('/')
        } else if (baseDir.isEmpty()) {
            cleaned
        } else {
            "$baseDir/$cleaned"
        }
        val stack = ArrayDeque<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (stack.isEmpty()) return null
                    stack.removeLast()
                }
                else -> stack.addLast(segment)
            }
        }
        return stack.joinToString("/")
    }

    private fun resolveSafeEntry(zip: ZipFile, path: String): ZipEntry? {
        if (path.isBlank() || path.contains("..") || path.startsWith("/")) return null
        return zip.getEntry(path)
    }

    private class ByteReadTracker {
        var totalBytes: Long = 0L
    }

    private fun readEntryBytesBounded(
        zip: ZipFile,
        entry: ZipEntry,
        maxBytes: Int,
        tracker: ByteReadTracker? = null
    ): ByteArray {
        require(entry.size <= maxBytes) { "ZIP entry ${entry.name} exceeds size limit" }
        zip.getInputStream(entry).use { input ->
            return readStreamBounded(input, maxBytes, tracker, entry.name)
        }
    }

    private fun readStreamBounded(
        input: InputStream,
        maxBytes: Int,
        tracker: ByteReadTracker?,
        label: String
    ): ByteArray {
        val buf = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = input.read(buf)
            if (read == -1) break
            if (out.size() + read > maxBytes) {
                throw IllegalArgumentException("Entry $label exceeds unpacked limit")
            }
            if (tracker != null) {
                tracker.totalBytes += read
                if (tracker.totalBytes > MAX_TOTAL_READ_BYTES) {
                    throw IllegalArgumentException("Presentation archive exceeds unpacked memory budget")
                }
            }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    internal fun parseXmlSafe(xmlBytes: ByteArray): Document {
        val head = String(xmlBytes, 0, min(xmlBytes.size, 2048), StandardCharsets.UTF_8).uppercase(Locale.ROOT)
        require(!head.contains("<!DOCTYPE") && !head.contains("<!ENTITY")) {
            "Unsafe XML DOCTYPE/ENTITY declaration rejected."
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
        }
        return builder.parse(ByteArrayInputStream(xmlBytes))
    }

    private fun txBodyHasVisibleText(txBody: Element): Boolean =
        txBody.childElementsNamed("p").any { p ->
            childElements(p).any { ch ->
                val n = ch.localNameOrRaw()
                (n == "r" || n == "fld") && !(ch.firstChildNamed("t")?.textContent).isNullOrBlank()
            }
        }

    private fun emuAttrToPt(el: Element?, name: String, fallbackPt: Float): Float {
        val raw = attr(el, name)?.toFloatOrNull() ?: return fallbackPt
        return raw / EMU_PER_PT
    }

    private fun attr(el: Element?, name: String, secondary: String? = null): String? {
        if (el == null) return null
        if (el.hasAttribute(name)) return el.getAttribute(name)
        val local = name.substringAfter(':')
        val attrs = el.attributes
        for (i in 0 until attrs.length) {
            val n = attrs.item(i)
            val nodeLocal = n.localName ?: n.nodeName.substringAfter(':')
            if (nodeLocal == local || (secondary != null && nodeLocal == secondary)) {
                return n.nodeValue
            }
        }
        return null
    }

    private fun relIdAttr(el: Element): String? =
        el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            .takeIf { it.isNotBlank() }
            ?: attr(el, "r:id")

    private fun Node.localNameOrRaw(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.firstChildNamed(localName: String): Element? {
        var cur = firstChild
        while (cur != null) {
            if (cur is Element && cur.localNameOrRaw() == localName) return cur
            cur = cur.nextSibling
        }
        return null
    }

    private fun Element.childElementsNamed(localName: String): List<Element> {
        val list = mutableListOf<Element>()
        var cur = firstChild
        while (cur != null) {
            if (cur is Element && cur.localNameOrRaw() == localName) list += cur
            cur = cur.nextSibling
        }
        return list
    }

    private fun childElements(el: Element): List<Element> {
        val list = mutableListOf<Element>()
        var cur = el.firstChild
        while (cur != null) {
            if (cur is Element) list += cur
            cur = cur.nextSibling
        }
        return list
    }

    private fun parseClrMap(clrMapEl: Element?, fallback: Map<String, String>): Map<String, String> {
        if (clrMapEl == null) return fallback
        val out = fallback.toMutableMap()
        val attrs = clrMapEl.attributes
        for (i in 0 until attrs.length) {
            val item = attrs.item(i)
            val key = item.localName ?: item.nodeName.substringAfter(':')
            if (!item.nodeValue.isNullOrBlank()) out[key] = item.nodeValue
        }
        return out
    }

    private fun parseColorMapOvr(root: Element, base: Map<String, String>): Map<String, String> {
        val ovr = root.firstChildNamed("clrMapOvr")?.firstChildNamed("overrideClrMapping") ?: return base
        return parseClrMap(ovr, base)
    }

    private fun defaultClrMap(): Map<String, String> = mapOf(
        "bg1" to "lt1",
        "tx1" to "dk1",
        "bg2" to "lt2",
        "tx2" to "dk2",
        "accent1" to "accent1",
        "accent2" to "accent2",
        "accent3" to "accent3",
        "accent4" to "accent4",
        "accent5" to "accent5",
        "accent6" to "accent6",
        "hlink" to "hlink",
        "folHlink" to "folHlink"
    )

    private fun defaultTheme(): ThemeData = ThemeData(
        schemeColors = mapOf(
            "dk1" to 0x1A1A1A,
            "lt1" to 0xFFFFFF,
            "dk2" to 0x3A4352,
            "lt2" to 0xF3ECE1,
            "accent1" to 0xF25F3A,
            "accent2" to 0x8DE0AF,
            "accent3" to 0xF6D365,
            "accent4" to 0xB8C7F9,
            "accent5" to 0xDFD0FB,
            "accent6" to 0x506173,
            "hlink" to 0x0563C1,
            "folHlink" to 0x954F72
        ),
        majorLatinFont = "Calibri",
        minorLatinFont = "Calibri"
    )

    private fun namedColorRgb(name: String?): Int? = when (name?.lowercase(Locale.ROOT)) {
        "black" -> 0x000000
        "white" -> 0xFFFFFF
        "red" -> 0xFF0000
        "green" -> 0x008000
        "blue" -> 0x0000FF
        "yellow" -> 0xFFFF00
        "gray", "grey" -> 0x808080
        "darkgray", "darkgrey" -> 0x404040
        "lightgray", "lightgrey" -> 0xD3D3D3
        else -> null
    }

    private fun rgbToHsl(r: Int, g: Int, b: Int): DoubleArray {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val l = (maxC + minC) / 2.0
        if (abs(maxC - minC) < 1e-6) return doubleArrayOf(0.0, 0.0, l)
        val d = maxC - minC
        val s = if (l > 0.5) d / (2.0 - maxC - minC) else d / (maxC + minC)
        val h = when (maxC) {
            rf -> ((gf - bf) / d + (if (gf < bf) 6.0 else 0.0)) / 6.0
            gf -> ((bf - rf) / d + 2.0) / 6.0
            else -> ((rf - gf) / d + 4.0) / 6.0
        }
        return doubleArrayOf(h, s, l)
    }

    private fun hslToRgb(h: Double, s: Double, l: Double): IntArray {
        if (s <= 1e-6) {
            val v = (l * 255.0).roundToInt().coerceIn(0, 255)
            return intArrayOf(v, v, v)
        }
        val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
        val p = 2.0 * l - q
        val r = (hueToRgb(p, q, h + 1.0 / 3.0) * 255.0).roundToInt().coerceIn(0, 255)
        val g = (hueToRgb(p, q, h) * 255.0).roundToInt().coerceIn(0, 255)
        val b = (hueToRgb(p, q, h - 1.0 / 3.0) * 255.0).roundToInt().coerceIn(0, 255)
        return intArrayOf(r, g, b)
    }

    private fun hueToRgb(p: Double, q: Double, tIn: Double): Double {
        var t = tIn
        if (t < 0.0) t += 1.0
        if (t > 1.0) t -= 1.0
        return when {
            t < 1.0 / 6.0 -> p + (q - p) * 6.0 * t
            t < 0.5 -> q
            t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
            else -> p
        }
    }
}
