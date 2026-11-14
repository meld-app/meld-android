package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.meld.android.engine.pptx.PptxCanvasRenderer
import app.meld.android.engine.pptx.PptxFontResolver
import app.meld.android.engine.pptx.PptxOoxmlParser
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure Kotlin OOXML `.pptx` processing engine:
 * - Never mutates source `.pptx` archives, inner slide XML, layouts, themes, or media.
 * - Extracts & de-obfuscates embedded fonts (`.odttf`/`.ttf`/`.otf`) and maps unembedded families cleanly.
 * - Converts decks to exact-dimension PDF, PNG/JPG slides, Lecture Handout PDFs, Markdown, TXT, and sanitized `.pptx`.
 */
object PptxEngine {

    private const val MAX_RENDER_IMAGE_SIDE_PX = 2160f

    data class PptxPdfRenderSummary(
        val outputFile: File,
        val slideCount: Int,
        val slideWidthPt: Float,
        val slideHeightPt: Float,
        val embeddedFontsUsed: Int,
        val mappedFonts: Set<String>,
    )

    data class PptxTextExtractSummary(
        val outputFile: File,
        val slideCount: Int,
        val tableCount: Int,
        val noteCount: Int,
        val characterCount: Int,
    )

    data class PptxSanitizeSummary(
        val outputFile: File,
        val slideCount: Int,
        val preservedEntries: Int,
        val strippedNotesCount: Int,
        val strippedCommentsCount: Int,
        val cleanedMetadataFiles: Int,
    )

    fun countSlides(inputFile: File): Int? {
        return PptxOoxmlParser.countSlides(inputFile)
    }

    fun convertPptxToPdf(
        inputFile: File,
        outputFile: File,
        fontCacheDir: File = outputFile.parentFile ?: inputFile.parentFile ?: File("."),
    ): PptxPdfRenderSummary {
        val presentation = PptxOoxmlParser.parse(inputFile)
        if (presentation.slides.isEmpty()) {
            throw IllegalArgumentException("Presentation contains no readable slides")
        }
        outputFile.parentFile?.mkdirs()

        val pdf = PdfDocument()
        try {
            ZipFile(inputFile).use { zip ->
                PptxFontResolver(fontCacheDir, zip, presentation.embeddedFonts).use { fontResolver ->
                    for (slide in presentation.slides) {
                        val pageW = slide.widthPt.roundToInt().coerceIn(72, 4320)
                        val pageH = slide.heightPt.roundToInt().coerceIn(72, 4320)
                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, slide.index1Based).create()
                        val page = pdf.startPage(pageInfo)
                        val canvas = page.canvas
                        val scaleX = pageW / max(1f, slide.widthPt)
                        val scaleY = pageH / max(1f, slide.heightPt)
                        if (scaleX != 1f || scaleY != 1f) {
                            canvas.scale(scaleX, scaleY)
                        }
                        PptxCanvasRenderer.renderSlide(canvas, zip, slide, fontResolver)
                        pdf.finishPage(page)
                    }

                    FileOutputStream(outputFile).use { fos ->
                        pdf.writeTo(fos)
                    }

                    return PptxPdfRenderSummary(
                        outputFile = outputFile,
                        slideCount = presentation.slides.size,
                        slideWidthPt = presentation.slideWidthPt,
                        slideHeightPt = presentation.slideHeightPt,
                        embeddedFontsUsed = fontResolver.embeddedFontsLoadedCount,
                        mappedFonts = fontResolver.fallbackFontsUsed,
                    )
                }
            }
        } finally {
            pdf.close()
        }
    }

    fun renderPptxSlideToImage(
        inputFile: File,
        outputFile: File,
        slideNumber1Based: Int = 1,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 92,
        fontCacheDir: File = outputFile.parentFile ?: inputFile.parentFile ?: File("."),
    ): PptxPdfRenderSummary {
        val presentation = PptxOoxmlParser.parse(inputFile)
        if (presentation.slides.isEmpty()) {
            throw IllegalArgumentException("Presentation contains no readable slides")
        }
        val slideIdx = (slideNumber1Based - 1).coerceIn(0, presentation.slides.lastIndex)
        val slide = presentation.slides[slideIdx]
        outputFile.parentFile?.mkdirs()

        val maxSlideDim = max(1f, max(slide.widthPt, slide.heightPt))
        val scale = (MAX_RENDER_IMAGE_SIDE_PX / maxSlideDim).coerceIn(1.5f, 3.0f)
        val bmpW = (slide.widthPt * scale).roundToInt().coerceIn(64, 4096)
        val bmpH = (slide.heightPt * scale).roundToInt().coerceIn(64, 4096)

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        try {
            ZipFile(inputFile).use { zip ->
                PptxFontResolver(fontCacheDir, zip, presentation.embeddedFonts).use { fontResolver ->
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.save()
                    canvas.scale(scale, scale)
                    PptxCanvasRenderer.renderSlide(canvas, zip, slide, fontResolver)
                    canvas.restore()

                    FileOutputStream(outputFile).use { fos ->
                        val ok = bitmap.compress(format, quality.coerceIn(50, 100), fos)
                        if (!ok) {
                            throw IllegalStateException("Failed to encode slide image")
                        }
                    }

                    return PptxPdfRenderSummary(
                        outputFile = outputFile,
                        slideCount = presentation.slides.size,
                        slideWidthPt = slide.widthPt,
                        slideHeightPt = slide.heightPt,
                        embeddedFontsUsed = fontResolver.embeddedFontsLoadedCount,
                        mappedFonts = fontResolver.fallbackFontsUsed,
                    )
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun buildPptxHandoutPdf(
        inputFile: File,
        outputFile: File,
        slidesPerPage: Int = 2,
        fontCacheDir: File = outputFile.parentFile ?: inputFile.parentFile ?: File("."),
    ): PptxPdfRenderSummary {
        val perPage = when (slidesPerPage) {
            3 -> 3
            4 -> 4
            else -> 2
        }
        val presentation = PptxOoxmlParser.parse(inputFile)
        if (presentation.slides.isEmpty()) {
            throw IllegalArgumentException("Presentation contains no readable slides")
        }
        outputFile.parentFile?.mkdirs()

        val isLandscape = perPage == 4
        val sheetW = if (isLandscape) 842 else 595
        val sheetH = if (isLandscape) 595 else 842
        val margin = 36f
        val headerH = 26f
        val footerH = 22f

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 53, 47)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(85, 82, 76)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 208, 202)
            strokeWidth = 0.75f
        }

        val pdf = PdfDocument()
        try {
            ZipFile(inputFile).use { zip ->
                PptxFontResolver(fontCacheDir, zip, presentation.embeddedFonts).use { fontResolver ->
                    val chunks = presentation.slides.chunked(perPage)
                    val totalSheets = chunks.size

                    chunks.forEachIndexed { sheetIdx, chunk ->
                        val pageInfo = PdfDocument.PageInfo.Builder(sheetW, sheetH, sheetIdx + 1).create()
                        val page = pdf.startPage(pageInfo)
                        val canvas = page.canvas
                        canvas.drawColor(Color.WHITE)

                        canvas.drawText(
                            "${inputFile.nameWithoutExtension}  •  Handout (${perPage}-up)",
                            margin,
                            margin + 12f,
                            labelPaint,
                        )

                        val bodyTop = margin + headerH
                        val bodyBottom = sheetH - margin - footerH
                        val bodyLeft = margin
                        val bodyRight = sheetW - margin
                        val bodyW = bodyRight - bodyLeft
                        val bodyH = bodyBottom - bodyTop

                        when (perPage) {
                            2 -> {
                                val gap = 22f
                                val slotH = (bodyH - gap) / 2f
                                chunk.forEachIndexed { idx, slide ->
                                    val top = bodyTop + idx * (slotH + gap)
                                    canvas.drawText("Slide ${slide.index1Based}", bodyLeft, top + 10f, labelPaint)
                                    val rect = RectF(bodyLeft, top + 16f, bodyRight, top + slotH)
                                    PptxCanvasRenderer.renderSlideIntoRect(canvas, zip, slide, fontResolver, rect)
                                }
                            }

                            3 -> {
                                val gapY = 18f
                                val slotH = (bodyH - gapY * 2f) / 3f
                                val slideColW = bodyW * 0.50f
                                val notesLeft = bodyLeft + slideColW + 18f
                                val notesRight = bodyRight

                                chunk.forEachIndexed { idx, slide ->
                                    val top = bodyTop + idx * (slotH + gapY)
                                    canvas.drawText("Slide ${slide.index1Based}", bodyLeft, top + 10f, labelPaint)
                                    val slideRect = RectF(bodyLeft, top + 15f, bodyLeft + slideColW, top + slotH)
                                    PptxCanvasRenderer.renderSlideIntoRect(canvas, zip, slide, fontResolver, slideRect)

                                    canvas.drawText("Notes", notesLeft, top + 10f, labelPaint)
                                    val notePreviewLines = wrapTextToWidth(
                                        text = slide.notesText,
                                        paint = notePaint,
                                        maxWidth = notesRight - notesLeft,
                                        maxLines = 4,
                                    )
                                    val lineStep = ((slotH - 18f) / 6f).coerceAtLeast(14f)
                                    for (lineIdx in 1..6) {
                                        val ly = top + 16f + lineIdx * lineStep
                                        if (ly <= top + slotH) {
                                            val preview = notePreviewLines.getOrNull(lineIdx - 1)
                                            if (preview != null) {
                                                canvas.drawText(preview, notesLeft, ly - 3f, notePaint)
                                            }
                                            canvas.drawLine(notesLeft, ly, notesRight, ly, linePaint)
                                        }
                                    }
                                }
                            }

                            else -> {
                                val gapX = 18f
                                val gapY = 18f
                                val cellW = (bodyW - gapX) / 2f
                                val cellH = (bodyH - gapY) / 2f
                                chunk.forEachIndexed { idx, slide ->
                                    val col = idx % 2
                                    val row = idx / 2
                                    val left = bodyLeft + col * (cellW + gapX)
                                    val top = bodyTop + row * (cellH + gapY)
                                    canvas.drawText("Slide ${slide.index1Based}", left, top + 10f, labelPaint)
                                    val rect = RectF(left, top + 15f, left + cellW, top + cellH)
                                    PptxCanvasRenderer.renderSlideIntoRect(canvas, zip, slide, fontResolver, rect)
                                }
                            }
                        }

                        canvas.drawText(
                            "Page ${sheetIdx + 1} of $totalSheets",
                            margin,
                            sheetH - margin + 10f,
                            notePaint,
                        )
                        pdf.finishPage(page)
                    }

                    FileOutputStream(outputFile).use { fos ->
                        pdf.writeTo(fos)
                    }

                    return PptxPdfRenderSummary(
                        outputFile = outputFile,
                        slideCount = presentation.slides.size,
                        slideWidthPt = presentation.slideWidthPt,
                        slideHeightPt = presentation.slideHeightPt,
                        embeddedFontsUsed = fontResolver.embeddedFontsLoadedCount,
                        mappedFonts = fontResolver.fallbackFontsUsed,
                    )
                }
            }
        } finally {
            pdf.close()
        }
    }

    fun convertPptxToMarkdown(
        inputFile: File,
        outputFile: File,
    ): PptxTextExtractSummary {
        val presentation = PptxOoxmlParser.parse(inputFile)
        if (presentation.slides.isEmpty()) {
            throw IllegalArgumentException("Presentation contains no readable slides")
        }
        outputFile.parentFile?.mkdirs()

        val sb = StringBuilder()
        sb.append("# ").append(MediaEngine.escapeMarkdownText(inputFile.nameWithoutExtension))
            .append(" (").append(presentation.slides.size).append(" slides)\n\n")

        var tableCount = 0
        var noteCount = 0

        for (slide in presentation.slides) {
            val heading = slide.extractHeading()
            sb.append("## Slide ").append(slide.index1Based)
            if (!heading.isNullOrBlank()) {
                sb.append(": ").append(MediaEngine.escapeMarkdownText(heading))
            }
            if (slide.hidden) {
                sb.append(" _(Hidden)_")
            }
            sb.append("\n\n")

            var headingSkipped = false
            for (element in slide.elements) {
                when (element) {
                    is PptxOoxmlParser.PptxElement.Shape -> {
                        val body = element.textBody ?: continue
                        for (para in body.paragraphs) {
                            val rawLine = para.runs.joinToString("") { it.text }.trim()
                            if (rawLine.isEmpty()) continue
                            if (!headingSkipped && heading != null && rawLine == heading) {
                                headingSkipped = true
                                continue
                            }
                            val formattedRuns = formatMarkdownRuns(para.runs)
                            if (para.bulletPrefix != null) {
                                val indent = "  ".repeat(para.level.coerceIn(0, 4))
                                sb.append(indent).append("- ").append(formattedRuns).append('\n')
                            } else {
                                sb.append(formattedRuns).append("\n\n")
                            }
                        }
                        if (body.paragraphs.any { it.bulletPrefix != null }) {
                            sb.append('\n')
                        }
                    }

                    is PptxOoxmlParser.PptxElement.Table -> {
                        tableCount++
                        appendMarkdownTable(sb, element)
                    }

                    is PptxOoxmlParser.PptxElement.Picture -> {
                        val assetName = element.imageEntryPath.substringAfterLast('/')
                        sb.append("_[Embedded image: ")
                            .append(MediaEngine.escapeMarkdownText(assetName))
                            .append("]_\n\n")
                    }
                }
            }

            if (slide.notesText.isNotBlank()) {
                noteCount++
                sb.append("> **Speaker Notes:** ")
                    .append(MediaEngine.escapeMarkdownText(slide.notesText.replace('\n', ' ')))
                    .append("\n\n")
            }
        }

        val content = sb.toString().trimEnd() + "\n"
        outputFile.writeText(content, StandardCharsets.UTF_8)
        return PptxTextExtractSummary(
            outputFile = outputFile,
            slideCount = presentation.slides.size,
            tableCount = tableCount,
            noteCount = noteCount,
            characterCount = content.length,
        )
    }

    fun extractPptxPlainText(
        inputFile: File,
        outputFile: File,
    ): PptxTextExtractSummary {
        val presentation = PptxOoxmlParser.parse(inputFile)
        if (presentation.slides.isEmpty()) {
            throw IllegalArgumentException("Presentation contains no readable slides")
        }
        outputFile.parentFile?.mkdirs()

        val sb = StringBuilder()
        var tableCount = 0
        var noteCount = 0

        for (slide in presentation.slides) {
            sb.append("=== Slide ").append(slide.index1Based)
            if (slide.hidden) sb.append(" [Hidden]")
            sb.append(" ===\n")

            for (element in slide.elements) {
                when (element) {
                    is PptxOoxmlParser.PptxElement.Shape -> {
                        val body = element.textBody ?: continue
                        for (para in body.paragraphs) {
                            val rawLine = para.runs.joinToString("") { it.text }.trim()
                            if (rawLine.isEmpty()) continue
                            val indent = "  ".repeat(para.level.coerceIn(0, 4))
                            if (para.bulletPrefix != null) {
                                sb.append(indent).append(para.bulletPrefix).append(' ').append(rawLine).append('\n')
                            } else {
                                sb.append(rawLine).append('\n')
                            }
                        }
                    }

                    is PptxOoxmlParser.PptxElement.Table -> {
                        tableCount++
                        for (row in element.rows) {
                            val rowCells = row.cells.filterNot { it.mergedAway }.map { cell ->
                                cell.textBody?.paragraphs
                                    ?.joinToString(" ") { p -> p.runs.joinToString("") { it.text }.trim() }
                                    ?.trim()
                                    .orEmpty()
                            }
                            sb.append(rowCells.joinToString("\t")).append('\n')
                        }
                    }

                    is PptxOoxmlParser.PptxElement.Picture -> Unit
                }
            }

            if (slide.notesText.isNotBlank()) {
                noteCount++
                sb.append("\n[Speaker Notes]\n").append(slide.notesText.trim()).append('\n')
            }
            sb.append('\n')
        }

        val content = sb.toString().trimEnd() + "\n"
        outputFile.writeText(content, StandardCharsets.UTF_8)
        return PptxTextExtractSummary(
            outputFile = outputFile,
            slideCount = presentation.slides.size,
            tableCount = tableCount,
            noteCount = noteCount,
            characterCount = content.length,
        )
    }

    /**
     * Produces a privacy-cleaned `.pptx` copy:
     * - Strips `docProps/core.xml` author/creator/timestamps/revisions & `docProps/app.xml` Company/Manager.
     * - Drops `docProps/custom.xml`, speaker notes (`ppt/notesSlides/...`), and comments (`ppt/comments/...`,
     *   `ppt/commentAuthors.xml`) plus removes their relationships from `*.rels` and `[Content_Types].xml`.
     * - Copies every slide XML, layout, master, theme, embedded font, and media entry byte-for-byte untouched.
     */
    fun sanitizePptx(
        inputFile: File,
        outputFile: File,
        stripNotes: Boolean = true,
        stripComments: Boolean = true,
    ): PptxSanitizeSummary {
        val slideCount = countSlides(inputFile)
            ?: throw IllegalArgumentException("File is not a valid OOXML .pptx archive")
        outputFile.parentFile?.mkdirs()

        var preservedEntries = 0
        var strippedNotesCount = 0
        var strippedCommentsCount = 0
        var cleanedMetadataFiles = 0
        val removedPartPaths = HashSet<String>()

        ZipFile(inputFile).use { zip ->
            val entries = zip.entries().toList()
            for (entry in entries) {
                val name = entry.name.trimStart('/')
                if (name.contains("..")) continue
                if (shouldStripEntry(name, stripNotes, stripComments)) {
                    removedPartPaths.add("/$name")
                }
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
                for (entry in entries) {
                    if (entry.isDirectory) continue
                    val name = entry.name.trimStart('/')
                    if (name.isEmpty() || name.contains("..")) continue

                    when {
                        shouldStripEntry(name, stripNotes, stripComments) -> {
                            when {
                                isNotesPart(name) && !name.endsWith(".rels") -> strippedNotesCount++
                                isCommentsPart(name) && !name.endsWith(".rels") -> strippedCommentsCount++
                            }
                        }

                        name.equals("docProps/core.xml", ignoreCase = true) -> {
                            cleanedMetadataFiles++
                            val raw = readEntryBytesCapped(zip, entry)
                            val scrubbed = scrubCorePropertiesXml(raw)
                            writeZipEntryBytes(zos, name, scrubbed)
                        }

                        name.equals("docProps/app.xml", ignoreCase = true) -> {
                            cleanedMetadataFiles++
                            val raw = readEntryBytesCapped(zip, entry)
                            val scrubbed = scrubAppPropertiesXml(raw)
                            writeZipEntryBytes(zos, name, scrubbed)
                        }

                        name.equals("[Content_Types].xml", ignoreCase = true) -> {
                            val raw = readEntryBytesCapped(zip, entry)
                            val cleaned = cleanContentTypesXml(raw, removedPartPaths)
                            writeZipEntryBytes(zos, name, cleaned)
                        }

                        name.endsWith(".rels", ignoreCase = true) -> {
                            val raw = readEntryBytesCapped(zip, entry)
                            val cleaned = cleanRelsXml(raw, name, stripNotes, stripComments)
                            writeZipEntryBytes(zos, name, cleaned)
                        }

                        else -> {
                            // Byte-for-byte preservation of slide*.xml, slideLayouts, slideMasters, theme, fonts, media
                            preservedEntries++
                            zos.putNextEntry(ZipEntry(name))
                            zip.getInputStream(entry).use { input ->
                                input.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
        }

        return PptxSanitizeSummary(
            outputFile = outputFile,
            slideCount = slideCount,
            preservedEntries = preservedEntries,
            strippedNotesCount = strippedNotesCount,
            strippedCommentsCount = strippedCommentsCount,
            cleanedMetadataFiles = cleanedMetadataFiles,
        )
    }

    private fun shouldStripEntry(name: String, stripNotes: Boolean, stripComments: Boolean): Boolean {
        if (name.equals("docProps/custom.xml", ignoreCase = true)) return true
        if (stripNotes && isNotesPart(name)) return true
        if (stripComments && isCommentsPart(name)) return true
        return false
    }

    private fun isNotesPart(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.startsWith("ppt/notesslides/")
    }

    private fun isCommentsPart(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.startsWith("ppt/comments/") ||
            lower.startsWith("ppt/authors/") ||
            lower == "ppt/commentauthors.xml"
    }

    private fun readEntryBytesCapped(zip: ZipFile, entry: ZipEntry): ByteArray {
        val maxXmlBytes = 16 * 1024 * 1024
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        zip.getInputStream(entry).use { input ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxXmlBytes) {
                    throw IllegalArgumentException("XML entry exceeds safety limit: ${entry.name}")
                }
                baos.write(buf, 0, n)
            }
        }
        return baos.toByteArray()
    }

    private fun writeZipEntryBytes(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun scrubCorePropertiesXml(rawBytes: ByteArray): ByteArray {
        val doc = runCatching { PptxOoxmlParser.parseXmlSafe(rawBytes) }.getOrElse { return rawBytes }
        val root = doc.documentElement ?: return rawBytes
        val piiTags = setOf("creator", "lastModifiedBy", "created", "modified", "lastPrinted")
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val local = child.localName ?: child.tagName.substringAfter(':')
            when (local) {
                in piiTags -> child.textContent = ""
                "revision" -> child.textContent = "1"
            }
        }
        return serializeXml(doc)
    }

    private fun scrubAppPropertiesXml(rawBytes: ByteArray): ByteArray {
        val doc = runCatching { PptxOoxmlParser.parseXmlSafe(rawBytes) }.getOrElse { return rawBytes }
        val root = doc.documentElement ?: return rawBytes
        val stripTags = setOf("Company", "Manager", "Template")
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val local = child.localName ?: child.tagName.substringAfter(':')
            if (local in stripTags) {
                child.textContent = ""
            }
        }
        return serializeXml(doc)
    }

    private fun cleanContentTypesXml(rawBytes: ByteArray, removedPartPaths: Set<String>): ByteArray {
        if (removedPartPaths.isEmpty()) return rawBytes
        val doc = runCatching { PptxOoxmlParser.parseXmlSafe(rawBytes) }.getOrElse { return rawBytes }
        val root = doc.documentElement ?: return rawBytes
        val toRemove = ArrayList<Element>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            val local = el.localName ?: el.tagName.substringAfter(':')
            if (local == "Override") {
                val partName = el.getAttribute("PartName")
                if (removedPartPaths.any { it.equals(partName, ignoreCase = true) }) {
                    toRemove.add(el)
                }
            }
        }
        for (el in toRemove) {
            root.removeChild(el)
        }
        return serializeXml(doc)
    }

    private fun cleanRelsXml(
        rawBytes: ByteArray,
        relsEntryName: String,
        stripNotes: Boolean,
        stripComments: Boolean,
    ): ByteArray {
        val doc = runCatching { PptxOoxmlParser.parseXmlSafe(rawBytes) }.getOrElse { return rawBytes }
        val root = doc.documentElement ?: return rawBytes
        val baseDir = relsEntryName.substringBeforeLast("/_rels/", "")
        val toRemove = ArrayList<Element>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            val local = el.localName ?: el.tagName.substringAfter(':')
            if (local != "Relationship") continue
            val type = el.getAttribute("Type").lowercase(Locale.US)
            val target = el.getAttribute("Target")
            val resolved = PptxOoxmlParser.normalizeOpcTarget(baseDir, target)?.lowercase(Locale.US).orEmpty()

            val shouldDrop = resolved == "docprops/custom.xml" ||
                type.endsWith("/custom-properties") ||
                (stripNotes && (type.endsWith("/notesslide") || resolved.startsWith("ppt/notesslides/"))) ||
                (stripComments && (
                    type.endsWith("/comments") ||
                        type.endsWith("/commentauthors") ||
                        resolved.startsWith("ppt/comments/") ||
                        resolved.startsWith("ppt/authors/") ||
                        resolved == "ppt/commentauthors.xml"
                    ))
            if (shouldDrop) {
                toRemove.add(el)
            }
        }
        for (el in toRemove) {
            root.removeChild(el)
        }
        return serializeXml(doc)
    }

    private fun serializeXml(doc: Document): ByteArray {
        val tf = TransformerFactory.newInstance()
        runCatching { tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        val transformer = tf.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun formatMarkdownRuns(runs: List<PptxOoxmlParser.PptxTextRun>): String {
        val sb = StringBuilder()
        for (run in runs) {
            val raw = run.text.replace('\u000B', ' ')
            if (raw.isEmpty()) continue
            val escaped = MediaEngine.escapeMarkdownText(raw)
            val trimmed = escaped.trim()
            if (trimmed.isEmpty()) {
                sb.append(escaped)
                continue
            }
            val leading = escaped.takeWhile { it.isWhitespace() }
            val trailing = escaped.reversed().takeWhile { it.isWhitespace() }.reversed()
            var core = trimmed
            if (run.strikethrough) core = "~~$core~~"
            if (run.bold && run.italic) {
                core = "***$core***"
            } else if (run.bold) {
                core = "**$core**"
            } else if (run.italic) {
                core = "*$core*"
            }
            sb.append(leading).append(core).append(trailing)
        }
        return sb.toString().trim()
    }

    private fun appendMarkdownTable(sb: StringBuilder, table: PptxOoxmlParser.PptxElement.Table) {
        if (table.rows.isEmpty()) return
        val visibleRows = table.rows.map { row ->
            row.cells.filterNot { it.mergedAway }.map { cell ->
                val raw = cell.textBody?.paragraphs
                    ?.joinToString(" ") { p -> p.runs.joinToString("") { it.text }.trim() }
                    ?.trim()
                    .orEmpty()
                MediaEngine.escapeMarkdownCell(raw).ifBlank { " " }
            }
        }.filter { it.isNotEmpty() }
        if (visibleRows.isEmpty()) return

        val colCount = max(1, visibleRows.maxOf { it.size })
        val header = List(colCount) { idx -> visibleRows.first().getOrNull(idx) ?: " " }
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(colCount) { "---" }.joinToString(" | ")).append(" |\n")
        for (row in visibleRows.drop(1)) {
            val padded = List(colCount) { idx -> row.getOrNull(idx) ?: " " }
            sb.append("| ").append(padded.joinToString(" | ")).append(" |\n")
        }
        sb.append('\n')
    }

    private fun wrapTextToWidth(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int,
    ): List<String> {
        if (text.isBlank() || maxWidth <= 20f || maxLines <= 0) return emptyList()
        val words = text.trim().split(Regex("\\s+"))
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    if (lines.size >= maxLines) return lines
                }
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) {
            lines.add(current.toString())
        }
        return lines
    }
}
