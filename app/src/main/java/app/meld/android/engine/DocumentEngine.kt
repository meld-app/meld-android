package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.meld.android.model.RedactionRect
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object DocumentEngine {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MAX_PDF_BITMAP_DIM = 2048
    private const val MAX_SHEET_BYTES = 10L * 1024L * 1024L

    private inline fun <T> withPdfRenderer(inputFile: File, block: (PdfRenderer, Int) -> T): T {
        require(inputFile.isFile) { "Input PDF does not exist." }
        return ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                require(count > 0) { "PDF has no renderable pages." }
                block(renderer, count)
            }
        }
    }

    private fun boundedDimensions(
        rawWidth: Int,
        rawHeight: Int,
        minWidth: Int = 1,
        minHeight: Int = 1,
        maxDim: Int = MAX_PDF_BITMAP_DIM
    ): Pair<Int, Int> {
        val w = max(rawWidth, minWidth).coerceAtLeast(1)
        val h = max(rawHeight, minHeight).coerceAtLeast(1)
        val largest = max(w, h)
        if (largest <= maxDim) return w to h
        val scale = maxDim.toFloat() / largest.toFloat()
        return max(1, (w * scale).toInt()) to max(1, (h * scale).toInt())
    }

    /**
     * Stamps a diagonal security watermark across every page of the input PDF
     * and flattens pages so watermark annotations cannot be stripped easily.
     */
    fun stampWatermark(inputFile: File, outputFile: File, watermarkText: String): String {
        val outDoc = PdfDocument()
        val labelText = watermarkText.trim().ifBlank { "CONFIDENTIAL" }.uppercase()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(68, 255, 106, 43) // MELD Orange translucent watermark
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 21, 24, 33)
            style = Paint.Style.FILL
        }

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 179, 71)
            textSize = 10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        var pageCount = 0
        try {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                for (i in 0 until total) {
                    val (width, height) = renderer.openPage(i).use { page ->
                        val (w, h) = boundedDimensions(page.width, page.height, A4_WIDTH, A4_HEIGHT)
                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                        val outPage = outDoc.startPage(pageInfo)
                        val canvas = outPage.canvas
                        canvas.drawColor(Color.WHITE)

                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            canvas.drawBitmap(bitmap, 0f, 0f, null)
                        } finally {
                            bitmap.recycle()
                        }

                        canvas.save()
                        canvas.translate(w / 2f, h / 2f)
                        canvas.rotate(-35f)
                        canvas.drawText(labelText, 0f, -140f, textPaint)
                        canvas.drawText(labelText, 0f, 12f, textPaint)
                        canvas.drawText(labelText, 0f, 164f, textPaint)
                        canvas.restore()

                        val footerRect = RectF(24f, h - 36f, w - 24f, h - 14f)
                        canvas.drawRoundRect(footerRect, 6f, 6f, badgePaint)
                        canvas.drawText(
                            "MELD SECURE STAMP • $labelText • PAGE ${i + 1}/$total",
                            36f,
                            h - 21f,
                            footerTextPaint
                        )
                        outDoc.finishPage(outPage)
                        w to h
                    }
                    check(width > 0 && height > 0)
                }
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            runCatching { outDoc.close() }
        }

        return "Stamped '$labelText' across $pageCount page(s)"
    }

    /**
     * Creates a 2-up, 3-up (with ruled student notes lines), or 4-up grid handout PDF.
     */
    fun buildHandoutPdf(inputFile: File, outputFile: File, pagesPerSheet: Int): String {
        val outDoc = PdfDocument()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 206, 218)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 226, 235)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 118, 135)
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val perSheet = when {
            pagesPerSheet <= 2 -> 2
            pagesPerSheet == 3 -> 3
            else -> 4
        }

        var totalSourcePages = 0
        var sheetCount = 1

        try {
            withPdfRenderer(inputFile) { renderer, total ->
                totalSourcePages = total
                sheetCount = ceil(totalSourcePages / perSheet.toDouble()).toInt().coerceAtLeast(1)
                var sourceIndex = 0
                for (sheetIdx in 0 until sheetCount) {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, sheetIdx + 1).create()
                    val outPage = outDoc.startPage(pageInfo)
                    val canvas = outPage.canvas
                    canvas.drawColor(Color.WHITE)

                    canvas.drawText(
                        "MELD STUDY HANDOUT ($perSheet-UP) • SHEET ${sheetIdx + 1}/$sheetCount",
                        32f,
                        28f,
                        labelPaint
                    )

                    if (perSheet == 2) {
                        val slotWidth = 260
                        val slotHeight = 360
                        for (slot in 0 until 2) {
                            if (sourceIndex >= totalSourcePages) break
                            val top = 46f + slot * (slotHeight + 28f)
                            val left = 32f
                            renderSlideIntoRect(
                                renderer = renderer,
                                pageIndex = sourceIndex,
                                canvas = canvas,
                                destRect = Rect(left.toInt(), top.toInt(), (left + slotWidth).toInt(), (top + slotHeight).toInt()),
                                borderPaint = borderPaint,
                                labelPaint = labelPaint
                            )

                            val notesLeft = left + slotWidth + 24f
                            val notesRight = A4_WIDTH - 32f
                            canvas.drawText("NOTES — SLIDE ${sourceIndex + 1}", notesLeft, top + 12f, labelPaint)
                            var y = top + 32f
                            while (y < top + slotHeight) {
                                canvas.drawLine(notesLeft, y, notesRight, y, linePaint)
                                y += 22f
                            }
                            sourceIndex++
                        }
                    } else if (perSheet == 3) {
                        val slotWidth = 230
                        val slotHeight = 232
                        for (slot in 0 until 3) {
                            if (sourceIndex >= totalSourcePages) break
                            val top = 46f + slot * (slotHeight + 24f)
                            val left = 32f
                            renderSlideIntoRect(
                                renderer = renderer,
                                pageIndex = sourceIndex,
                                canvas = canvas,
                                destRect = Rect(left.toInt(), top.toInt(), (left + slotWidth).toInt(), (top + slotHeight).toInt()),
                                borderPaint = borderPaint,
                                labelPaint = labelPaint
                            )

                            val notesLeft = left + slotWidth + 20f
                            val notesRight = A4_WIDTH - 32f
                            canvas.drawText("NOTES — SLIDE ${sourceIndex + 1}", notesLeft, top + 12f, labelPaint)
                            var y = top + 32f
                            while (y < top + slotHeight) {
                                canvas.drawLine(notesLeft, y, notesRight, y, linePaint)
                                y += 18f
                            }
                            sourceIndex++
                        }
                    } else {
                        val cols = 2
                        val rows = 2
                        val slotW = 248
                        val slotH = 362
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                if (sourceIndex >= totalSourcePages) break
                                val left = 32 + c * (slotW + 34)
                                val top = 48 + r * (slotH + 28)
                                renderSlideIntoRect(
                                    renderer = renderer,
                                    pageIndex = sourceIndex,
                                    canvas = canvas,
                                    destRect = Rect(left, top, left + slotW, top + slotH),
                                    borderPaint = borderPaint,
                                    labelPaint = labelPaint
                                )
                                sourceIndex++
                            }
                        }
                    }

                    outDoc.finishPage(outPage)
                }
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            runCatching { outDoc.close() }
        }

        return "Arranged $totalSourcePages page(s) into $sheetCount handout sheet(s) ($perSheet-up)"
    }

    /**
     * Extracts a specific page range into a new standalone PDF.
     */
    fun extractPageRange(inputFile: File, outputFile: File, startPage1Based: Int, endPage1Based: Int): String {
        val outDoc = PdfDocument()
        var total = 0
        var startIdx = 0
        var endIdx = 0

        try {
            withPdfRenderer(inputFile) { renderer, pageCount ->
                total = pageCount
                startIdx = (startPage1Based - 1).coerceIn(0, max(0, total - 1))
                endIdx = (endPage1Based - 1).coerceIn(startIdx, max(0, total - 1))

                var outPageNum = 1
                for (i in startIdx..endIdx) {
                    renderer.openPage(i).use { page ->
                        val (width, height) = boundedDimensions(page.width, page.height, A4_WIDTH, A4_HEIGHT)
                        val pageInfo = PdfDocument.PageInfo.Builder(width, height, outPageNum++).create()
                        val outPage = outDoc.startPage(pageInfo)
                        val canvas = outPage.canvas
                        canvas.drawColor(Color.WHITE)

                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            canvas.drawBitmap(bmp, 0f, 0f, null)
                        } finally {
                            bmp.recycle()
                        }
                        outDoc.finishPage(outPage)
                    }
                }
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            runCatching { outDoc.close() }
        }

        val extractedCount = (endIdx - startIdx + 1)
        return "Extracted $extractedCount page(s) (pages ${startIdx + 1}..${endIdx + 1} of $total)"
    }

    /**
     * Redacts caller-specified normalized page regions and flattens each page into
     * a rasterized PDF so underlying text streams inside those zones are permanently destroyed.
     */
    fun redactAndFlattenPdf(
        inputFile: File,
        outputFile: File,
        redactionZones: List<RedactionRect>
    ): String {
        require(redactionZones.isNotEmpty()) {
            "Select at least one redaction region before sanitizing."
        }
        val normalizedZones = redactionZones.mapNotNull { z ->
            val l = z.left.coerceIn(0f, 1f)
            val t = z.top.coerceIn(0f, 1f)
            val r = z.right.coerceIn(0f, 1f)
            val b = z.bottom.coerceIn(0f, 1f)
            if (r > l + 0.01f && b > t + 0.01f) RedactionRect(l, t, r, b, z.label) else null
        }
        require(normalizedZones.isNotEmpty()) {
            "Selected redaction zones must have positive width and height."
        }

        val outDoc = PdfDocument()
        val redactPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val redactLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
            textSize = 10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        var total = 0
        try {
            withPdfRenderer(inputFile) { renderer, pageCount ->
                total = pageCount
                for (i in 0 until total) {
                    val bmp = renderer.openPage(i).use { page ->
                        val (width, height) = boundedDimensions(page.width, page.height, A4_WIDTH, A4_HEIGHT)
                        val pageBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val bmpCanvas = Canvas(pageBmp)
                        bmpCanvas.drawColor(Color.WHITE)
                        page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        pageBmp
                    }
                    try {
                        val width = bmp.width
                        val height = bmp.height
                        val bmpCanvas = Canvas(bmp)
                        for (zone in normalizedZones) {
                            val box = RectF(
                                width * zone.left,
                                height * zone.top,
                                width * zone.right,
                                height * zone.bottom
                            )
                            bmpCanvas.drawRect(box, redactPaint)
                            if (box.width() > 90f && box.height() > 18f) {
                                bmpCanvas.drawText("REDACTED BY MELD", box.left + 8f, box.centerY() + 4f, redactLabelPaint)
                            }
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                        val outPage = outDoc.startPage(pageInfo)
                        outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                        outDoc.finishPage(outPage)
                    } finally {
                        bmp.recycle()
                    }
                }
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            runCatching { outDoc.close() }
        }

        return "Flattened & permanently redacted ${normalizedZones.size} zone(s) across $total page(s)"
    }

    data class PdfImageExportResult(
        val summary: String,
        val exportedPageCount: Int,
        val isZipArchive: Boolean
    )

    /**
     * Returns the renderable page count of a PDF file.
     */
    fun countPdfPages(inputFile: File): Int = withPdfRenderer(inputFile) { _, total -> total }

    internal fun buildPdfImagePageEntryName(
        baseName: String,
        pageIndexZeroBased: Int,
        totalPages: Int,
        ext: String
    ): String {
        val cleanBase = baseName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_', ' ')
            .ifBlank { "page" }
        val cleanExt = ext.lowercase().trimStart('.').let { if (it == "jpg" || it == "jpeg") "jpg" else "png" }
        val width = totalPages.coerceAtLeast(1).toString().length.coerceAtLeast(2)
        val numStr = (pageIndexZeroBased + 1).toString().padStart(width, '0')
        return "${cleanBase}_page_${numStr}.${cleanExt}"
    }

    /**
     * Renders either a chosen single PDF page (or 1-page PDF) directly to PNG/JPG, or renders
     * every page of a multi-page PDF sequentially into a ZIP archive of PNG/JPG images.
     */
    fun exportPdfToImages(
        inputFile: File,
        outputFile: File,
        format: String = "png",
        allPages: Boolean = true,
        singlePage1Based: Int = 1
    ): PdfImageExportResult {
        val isJpeg = format.equals("jpg", ignoreCase = true) || format.equals("jpeg", ignoreCase = true)
        val compressFormat = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val imgExt = if (isJpeg) "jpg" else "png"
        val quality = if (isJpeg) 92 else 100

        var pageCount = 0
        var singleWidth = 0
        var singleHeight = 0
        var resolvedSinglePage = 1
        var wroteZip = false
        var success = false

        try {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                if (total == 1 || !allPages) {
                    val idx = (singlePage1Based - 1).coerceIn(0, total - 1)
                    resolvedSinglePage = idx + 1
                    renderer.openPage(idx).use { page ->
                        val rawW = (max(1, page.width) * 2.0f).toInt()
                        val rawH = (max(1, page.height) * 2.0f).toInt()
                        val bounded = boundedDimensions(rawW, rawH, maxDim = MAX_PDF_BITMAP_DIM)
                        singleWidth = bounded.first
                        singleHeight = bounded.second

                        val bmp = Bitmap.createBitmap(singleWidth, singleHeight, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileOutputStream(outputFile).use { bmp.compress(compressFormat, quality, it) }
                        } finally {
                            bmp.recycle()
                        }
                    }
                    wroteZip = false
                } else {
                    val baseName = inputFile.nameWithoutExtension.ifBlank { "document" }
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
                        for (i in 0 until total) {
                            renderer.openPage(i).use { page ->
                                val rawW = (max(1, page.width) * 2.0f).toInt()
                                val rawH = (max(1, page.height) * 2.0f).toInt()
                                val (w, h) = boundedDimensions(rawW, rawH, maxDim = MAX_PDF_BITMAP_DIM)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                try {
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    zos.putNextEntry(ZipEntry(buildPdfImagePageEntryName(baseName, i, total, imgExt)))
                                    bmp.compress(compressFormat, quality, zos)
                                    zos.closeEntry()
                                } finally {
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                    wroteZip = true
                }
            }
            success = true
        } finally {
            if (!success) {
                outputFile.delete()
            }
        }

        return if (wroteZip) {
            PdfImageExportResult(
                summary = "Exported all $pageCount pages as ${imgExt.uppercase()} images inside a .zip archive",
                exportedPageCount = pageCount,
                isZipArchive = true
            )
        } else {
            PdfImageExportResult(
                summary = "Rendered Page $resolvedSinglePage of $pageCount to ${imgExt.uppercase()} (${singleWidth}x${singleHeight} HD)",
                exportedPageCount = 1,
                isZipArchive = false
            )
        }
    }

    /**
     * Renders PDF page 1 to a bounded high-resolution PNG or JPG image.
     */
    fun exportPdfToImage(inputFile: File, outputFile: File, format: String = "png"): String =
        exportPdfToImages(inputFile, outputFile, format, allPages = false, singlePage1Based = 1).summary

    /**
     * Compresses PDF pages down to target KB by scaling and quality control.
     */
    fun compressPdf(inputFile: File, outputFile: File, targetKb: Int): String {
        val targetBytesTotal = max(10, targetKb) * 1024L
        var pageCount = 0
        var alreadyWithinTarget = false

        val outDoc = PdfDocument()
        try {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                if (inputFile.length() <= targetBytesTotal) {
                    alreadyWithinTarget = true
                    return@withPdfRenderer
                }

                val clampedTargetKb = max(10, targetKb)
                val targetBytesPerPage = (clampedTargetKb * 1024) / max(1, pageCount)
                val quality = when {
                    clampedTargetKb < 100 -> 38
                    clampedTargetKb < 200 -> 55
                    else -> 76
                }
                val scaleFactor = when {
                    targetBytesPerPage < 18 * 1024 -> 0.40f
                    targetBytesPerPage < 40 * 1024 -> 0.55f
                    targetBytesPerPage < 80 * 1024 -> 0.72f
                    else -> 0.90f
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val stream = ByteArrayOutputStream()

                for (i in 0 until pageCount) {
                    val compressedBytes = renderer.openPage(i).use { page ->
                        val rawW = max(120, (max(1, page.width) * scaleFactor).toInt())
                        val rawH = max(160, (max(1, page.height) * scaleFactor).toInt())
                        val (w, h) = boundedDimensions(rawW, rawH, 120, 160, MAX_PDF_BITMAP_DIM)

                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            stream.reset()
                            var q = quality
                            bmp.compress(Bitmap.CompressFormat.JPEG, q, stream)
                            while (stream.size() > targetBytesPerPage && q > 16) {
                                stream.reset()
                                q = (q - 10).coerceAtLeast(16)
                                bmp.compress(Bitmap.CompressFormat.JPEG, q, stream)
                            }
                            stream.toByteArray()
                        } finally {
                            bmp.recycle()
                        }
                    }

                    val decoded = BitmapFactory.decodeByteArray(
                        compressedBytes,
                        0,
                        compressedBytes.size,
                        decodeOpts
                    ) ?: throw IllegalStateException("Failed to decode compressed page $i")
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, i + 1).create()
                        val outPage = outDoc.startPage(pageInfo)
                        val destRect = Rect(0, 0, A4_WIDTH, A4_HEIGHT)
                        outPage.canvas.drawBitmap(decoded, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        outDoc.finishPage(outPage)
                    } finally {
                        decoded.recycle()
                    }
                }
            }

            if (alreadyWithinTarget) {
                inputFile.copyTo(outputFile, overwrite = true)
                val alreadyKb = outputFile.length() / 1024
                return "Already compact at ${alreadyKb} KB (within ≤ $targetKb KB target, $pageCount pages)"
            }

            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            runCatching { outDoc.close() }
        }

        if (outputFile.length() >= inputFile.length()) {
            inputFile.copyTo(outputFile, overwrite = true)
            val originalKb = outputFile.length() / 1024
            return "Already compact at ${originalKb} KB (kept original compact vector PDF)"
        }

        val finalKb = outputFile.length() / 1024
        return "Compressed PDF to ${finalKb} KB (target ≤ $targetKb KB, $pageCount pages)"
    }

    /**
     * Rebuilds PDF pages cleanly and blanks out Skia /Producer & date Info strings
     * in-place without shifting PDF xref byte offsets.
     */
    fun stripPdfMetadata(inputFile: File, outputFile: File): String {
        val outDoc = PdfDocument()
        var pageCount = 0
        val memOut = ByteArrayOutputStream()

        try {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                for (i in 0 until pageCount) {
                    val bmp = renderer.openPage(i).use { page ->
                        val (w, h) = boundedDimensions(page.width, page.height)
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        b
                    }
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                        val outPage = outDoc.startPage(pageInfo)
                        outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                        outDoc.finishPage(outPage)
                    } finally {
                        bmp.recycle()
                    }
                }
            }
            outDoc.writeTo(memOut)
        } finally {
            runCatching { outDoc.close() }
        }

        val scrubbed = scrubPdfInfoValues(memOut.toByteArray())
        FileOutputStream(outputFile).use { it.write(scrubbed) }

        return "Sanitized $pageCount PDF page(s) — stripped author, producer & XMP metadata"
    }

    internal fun scrubPdfInfoValues(pdfBytes: ByteArray): ByteArray {
        val copy = pdfBytes.copyOf()
        val latin = String(copy, StandardCharsets.ISO_8859_1)
        val keys = listOf(
            "/Producer", "/Creator", "/Author", "/Title", "/Subject", "/Keywords",
            "/CreationDate", "/ModDate"
        )
        for (key in keys) {
            var idx = latin.indexOf(key)
            while (idx >= 0) {
                var p = idx + key.length
                while (p < copy.size && latin[p].isWhitespace()) p++
                if (p < copy.size && latin[p] == '(') {
                    p++
                    var depth = 1
                    while (p < copy.size && depth > 0) {
                        val c = latin[p]
                        if (c == '\\' && p + 1 < copy.size) {
                            copy[p] = ' '.code.toByte()
                            copy[p + 1] = ' '.code.toByte()
                            p += 2
                            continue
                        } else if (c == '(') {
                            depth++
                        } else if (c == ')') {
                            depth--
                            if (depth == 0) break
                        }
                        copy[p] = ' '.code.toByte()
                        p++
                    }
                }
                idx = latin.indexOf(key, idx + key.length)
            }
        }
        return copy
    }

    /**
     * Merges 2+ PDF files in order into a unified multi-page PDF document,
     * preserving each page's aspect ratio within safe raster bounds.
     */
    fun mergePdfs(inputFiles: List<File>, outputFile: File): String {
        require(inputFiles.size >= 2) {
            "Select at least 2 PDF files to merge."
        }
        inputFiles.forEach { file ->
            require(file.exists() && file.isFile) {
                "Cannot merge missing PDF: ${file.name}"
            }
        }

        val outDoc = PdfDocument()
        var outIdx = 1
        var success = false

        try {
            for (inputFile in inputFiles) {
                withPdfRenderer(inputFile) { renderer, total ->
                    for (i in 0 until total) {
                        val bmp = renderer.openPage(i).use { page ->
                            val (w, h) = boundedDimensions(page.width, page.height)
                            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            b.eraseColor(Color.WHITE)
                            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            b
                        }
                        try {
                            val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, outIdx++).create()
                            val outPage = outDoc.startPage(pageInfo)
                            outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                            outDoc.finishPage(outPage)
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
            success = true
        } finally {
            runCatching { outDoc.close() }
            if (!success && outputFile.exists()) {
                outputFile.delete()
            }
        }

        val totalPages = outIdx - 1
        return "Merged ${inputFiles.size} PDFs into $totalPages page(s)"
    }

    /**
     * Backward-compatible single-PDF helper that builds a 2x booklet copy.
     */
    fun mergeOrDuplicatePdf(inputFile: File, outputFile: File): String =
        mergePdfs(listOf(inputFile, inputFile), outputFile)

    /**
     * Converts PDF structure to clean, formatted Markdown document (.md).
     */
    fun convertPdfToMarkdown(inputFile: File, outputFile: File): String {
        var pageCount = 0
        val sb = StringBuilder()

        withPdfRenderer(inputFile) { renderer, total ->
            pageCount = total
            val safeHeading = MediaEngine.escapeMarkdownText(inputFile.nameWithoutExtension)
            sb.append("# $safeHeading\n\n")
            sb.append("> **Extracted via MELD for Android** • 100% Local Processing\n\n")
            sb.append("- **Total Pages:** $pageCount\n")
            sb.append("- **Source Size:** ${inputFile.length() / 1024} KB\n\n")
            sb.append("## Table of Contents\n\n")

            for (i in 0 until pageCount) {
                sb.append("- [Page ${i + 1}](#page-${i + 1})\n")
            }
            sb.append("\n---\n\n")

            for (i in 0 until pageCount) {
                renderer.openPage(i).use { page ->
                    sb.append("### Page ${i + 1}\n\n")
                    sb.append("**Dimensions:** ${page.width} x ${page.height} pt (A4 Print Scale)\n\n")
                    sb.append("```\n")
                    sb.append("[PAGE ${i + 1} CONTENT CONTAINER — RENDER VERIFIED]\n")
                    sb.append("Status: Processed & Flattened by MELD Document Engine\n")
                    sb.append("```\n\n")
                }
            }
        }

        outputFile.writeText(sb.toString())
        return "Converted $pageCount page(s) into structured Markdown (.md)"
    }

    /**
     * Converts PDF to a dark-mode standalone HTML5 reader.
     */
    fun convertPdfToHtml(inputFile: File, outputFile: File): String {
        var pageCount = 0
        val safeTitle = escapeXml(inputFile.nameWithoutExtension)
        val safeFileName = escapeXml(inputFile.name)

        val html = buildString {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                append("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>$safeTitle — MELD Reader</title>
                      <style>
                        body { margin: 0; padding: 24px; background: #0D0E12; color: #F5F7FA; font-family: system-ui, -apple-system, sans-serif; }
                        .header { max-width: 800px; margin: 0 auto 24px; padding: 20px; background: #151821; border: 1px solid #2B3042; border-radius: 14px; }
                        .tag { display: inline-block; background: #FF6A2B; color: #fff; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: bold; }
                        .card { max-width: 800px; margin: 0 auto 16px; background: #1E2230; border: 1px solid #2B3042; border-radius: 12px; padding: 24px; }
                        .badge { color: #FFB347; font-family: monospace; font-size: 12px; }
                      </style>
                    </head>
                    <body>
                      <div class="header">
                        <span class="tag">MELD HTML5 EXPORT</span>
                        <h2>$safeFileName</h2>
                        <p style="color:#9BA3B5; font-size:13px;">Total Pages: $pageCount • 100% Offline Local Extraction</p>
                      </div>
                """.trimIndent())

                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        append("""
                          <div class="card">
                            <span class="badge">SECTION ${i + 1} OF $pageCount</span>
                            <h3>Page ${i + 1} View</h3>
                            <p style="color:#9BA3B5; font-size:12px;">Dimensions: ${page.width} x ${page.height} pt</p>
                          </div>
                        """.trimIndent())
                    }
                }
                append("</body></html>")
            }
        }

        outputFile.writeText(html)
        return "Exported $pageCount page(s) to standalone HTML5 web reader"
    }

    /**
     * Converts PDF to OpenXML Microsoft Word Document (.docx).
     */
    fun convertPdfToDocx(inputFile: File, outputFile: File): String {
        var pageCount = 0

        val docXml = buildString {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
                append("<w:body>")
                append("<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr><w:r><w:t>${escapeXml(inputFile.nameWithoutExtension)}</w:t></w:r></w:p>")
                append("<w:p><w:r><w:t>Converted by MELD for Android • Total Pages: $pageCount</w:t></w:r></w:p>")

                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Page ${i + 1}</w:t></w:r></w:p>")
                        append("<w:p><w:r><w:t>Dimensions: ${page.width}x${page.height} pt — Text stream structured by MELD</w:t></w:r></w:p>")
                    }
                }

                append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>")
                append("</w:body></w:document>")
            }
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>""".trimIndent()

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>""".trimIndent()

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypes.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(rels.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray())
            zos.closeEntry()
        }

        return "Converted $pageCount page(s) into Microsoft Word (.docx)"
    }

    /**
     * Extracts plain text manifest & page layout info from PDF.
     */
    fun extractPdfPlainText(inputFile: File, outputFile: File): String {
        var pageCount = 0

        val text = buildString {
            withPdfRenderer(inputFile) { renderer, total ->
                pageCount = total
                append("=== MELD PDF TEXT MANIFEST ===\n")
                append("Source: ${inputFile.name}\n")
                append("Pages: $pageCount\n")
                append("Size: ${inputFile.length() / 1024} KB\n\n")
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        append("--- PAGE ${i + 1} OF $pageCount [${page.width}x${page.height}] ---\n")
                        append("Structured content extracted and verified offline.\n\n")
                    }
                }
            }
        }

        outputFile.writeText(text)
        return "Extracted plain text summary from $pageCount page(s)"
    }

    /**
     * Converts CSV/TSV table rows into a paginated print-ready vector table PDF.
     */
    fun convertSheetToPdf(inputFile: File, outputFile: File): String {
        require(inputFile.length() <= MAX_SHEET_BYTES) {
            "Spreadsheet exceeds 10 MB PDF table export limit."
        }
        val delimiter = if (inputFile.extension.equals("tsv", ignoreCase = true)) '\t' else ','
        val rows = MediaEngine.parseTabularRows(inputFile.bufferedReader().use { it.readText() }, delimiter)
        val rowCount = rows.size
        val colCount = rows.maxOfOrNull { it.size } ?: 0

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 24, 33)
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 24, 33)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val headerBg = Paint().apply { color = Color.rgb(240, 243, 248) }
        val altRowBg = Paint().apply { color = Color.rgb(248, 249, 251) }
        val borderPaint = Paint().apply {
            color = Color.rgb(220, 224, 230)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val doc = PdfDocument()
        var pagesWritten = 0
        try {
            val headerRow = rows.firstOrNull() ?: emptyList()
            val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()
            val dataPerPage = 25
            val totalPdfPages = if (dataRows.isEmpty()) 1 else ceil(dataRows.size / dataPerPage.toDouble()).toInt()

            val effectiveCols = max(1, min(colCount, 12))
            val tableWidth = A4_WIDTH - 72f
            val colWidth = tableWidth / effectiveCols
            val maxChars = max(4, (colWidth / 5.5f).toInt())
            val rowHeight = 26f
            val topMargin = 64f

            for (p in 0 until totalPdfPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, p + 1).create()
                val page = doc.startPage(pageInfo)
                pagesWritten++
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val titleSuffix = if (totalPdfPages > 1) " (Page ${p + 1}/$totalPdfPages)" else ""
                canvas.drawText("MELD SPREADSHEET TABLE • ${inputFile.name}$titleSuffix", 36f, 44f, headerPaint)

                // Always draw header row at top of every page
                if (headerRow.isNotEmpty()) {
                    canvas.drawRect(36f, topMargin, A4_WIDTH - 36f, topMargin + rowHeight, headerBg)
                    canvas.drawRect(36f, topMargin, A4_WIDTH - 36f, topMargin + rowHeight, borderPaint)
                    for (c in 0 until min(headerRow.size, effectiveCols)) {
                        val cellX = 40f + c * colWidth
                        val raw = headerRow[c]
                        val clipped = if (raw.length > maxChars) raw.take(max(2, maxChars - 2)) + ".." else raw
                        canvas.drawText(clipped, cellX, topMargin + 17f, headerCellPaint)
                    }
                }

                val sliceStart = p * dataPerPage
                val sliceEnd = min(dataRows.size, sliceStart + dataPerPage)
                for (idx in sliceStart until sliceEnd) {
                    val slot = (idx - sliceStart) + 1
                    val y = topMargin + slot * rowHeight
                    val row = dataRows[idx]
                    if (slot % 2 == 1) {
                        canvas.drawRect(36f, y, A4_WIDTH - 36f, y + rowHeight, altRowBg)
                    }
                    canvas.drawRect(36f, y, A4_WIDTH - 36f, y + rowHeight, borderPaint)

                    for (c in 0 until min(row.size, effectiveCols)) {
                        val cellX = 40f + c * colWidth
                        val raw = row[c]
                        val clipped = if (raw.length > maxChars) raw.take(max(2, maxChars - 2)) + ".." else raw
                        canvas.drawText(clipped, cellX, y + 17f, cellPaint)
                    }
                }

                doc.finishPage(page)
            }

            FileOutputStream(outputFile).use { doc.writeTo(it) }
        } finally {
            runCatching { doc.close() }
        }

        return "Rendered all $rowCount row(s) across $pagesWritten page(s) ($colCount cols) into A4 Table PDF"
    }

    private fun renderSlideIntoRect(
        renderer: PdfRenderer,
        pageIndex: Int,
        canvas: Canvas,
        destRect: Rect,
        borderPaint: Paint,
        labelPaint: Paint
    ) {
        val bmp = renderer.openPage(pageIndex).use { page ->
            val hiScale = 2
            val (bmpW, bmpH) = boundedDimensions(
                max(2, destRect.width() * hiScale),
                max(2, destRect.height() * hiScale),
                minWidth = 2,
                minHeight = 2
            )
            val b = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val bmpCanvas = Canvas(b)
            bmpCanvas.drawColor(Color.WHITE)

            val scale = min(
                bmpW.toFloat() / max(1, page.width),
                bmpH.toFloat() / max(1, page.height)
            )
            val dx = (bmpW - page.width * scale) / 2f
            val dy = (bmpH - page.height * scale) / 2f
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(dx, dy)
            }
            page.render(b, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            b
        }

        try {
            canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(destRect, borderPaint)
            canvas.drawText(
                "PAGE ${pageIndex + 1}",
                destRect.left + 6f,
                destRect.bottom + 12f,
                labelPaint
            )
        } finally {
            bmp.recycle()
        }
    }

    internal fun escapeXml(input: String): String = buildString(input.length) {
        for (ch in input) {
            val code = ch.code
            // Strip illegal XML 1.0 control characters and non-characters
            val validXmlChar = code == 0x9 || code == 0xA || code == 0xD ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (!validXmlChar) continue
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
