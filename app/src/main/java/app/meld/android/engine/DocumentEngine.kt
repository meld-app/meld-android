package app.meld.android.engine

import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object DocumentEngine {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    /**
     * Stamps a diagonal security watermark across every page of the input PDF
     * and flattens pages so watermark annotations cannot be stripped easily.
     */
    fun stampWatermark(inputFile: File, outputFile: File, watermarkText: String): String {
        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val outDoc = PdfDocument()

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

        val pageCount = renderer.pageCount
        try {
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val width = max(page.width, A4_WIDTH)
                val height = max(page.height, A4_HEIGHT)

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val outPage = outDoc.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                page.close()

                // Diagonal repeating watermark
                canvas.save()
                canvas.translate(width / 2f, height / 2f)
                canvas.rotate(-35f)
                canvas.drawText(watermarkText.uppercase(), 0f, -140f, textPaint)
                canvas.drawText(watermarkText.uppercase(), 0f, 12f, textPaint)
                canvas.drawText(watermarkText.uppercase(), 0f, 164f, textPaint)
                canvas.restore()

                // Bottom security footer pill
                val footerRect = RectF(24f, height - 36f, width - 24f, height - 14f)
                canvas.drawRoundRect(footerRect, 6f, 6f, badgePaint)
                canvas.drawText(
                    "MELD SECURE STAMP • ${watermarkText.uppercase()} • PAGE ${i + 1}/$pageCount",
                    36f,
                    height - 21f,
                    footerTextPaint
                )

                outDoc.finishPage(outPage)
            }

            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
            renderer.close()
            pfd.close()
        }

        return "Stamped '$watermarkText' across $pageCount page(s)"
    }

    /**
     * Creates a 2-up (with ruled student note lines) or 4-up grid handout PDF.
     */
    fun buildHandoutPdf(inputFile: File, outputFile: File, pagesPerSheet: Int): String {
        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val outDoc = PdfDocument()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 206, 218)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 229, 236)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 118, 135)
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val totalSourcePages = renderer.pageCount
        val perSheet = if (pagesPerSheet <= 2) 2 else 4
        val sheetCount = ceil(totalSourcePages / perSheet.toDouble()).toInt().coerceAtLeast(1)

        try {
            var sourceIndex = 0
            for (sheetIdx in 0 until sheetCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, sheetIdx + 1).create()
                val outPage = outDoc.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                // Header
                canvas.drawText(
                    "MELD STUDY HANDOUT ($perSheet-UP) • SHEET ${sheetIdx + 1}/$sheetCount",
                    32f,
                    28f,
                    labelPaint
                )

                if (perSheet == 2) {
                    // Left column: 2 slides stacked vertically; Right column: ruled note lines
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

                        // Draw ruled note lines next to the slide
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
                } else {
                    // 2x2 grid (4-up)
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

            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
            renderer.close()
            pfd.close()
        }

        return "Arranged $totalSourcePages page(s) into $sheetCount handout sheet(s) ($perSheet-up)"
    }

    /**
     * Extracts a specific page range into a new standalone PDF.
     */
    fun extractPageRange(inputFile: File, outputFile: File, startPage1Based: Int, endPage1Based: Int): String {
        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val outDoc = PdfDocument()

        val total = renderer.pageCount
        val startIdx = (startPage1Based - 1).coerceIn(0, max(0, total - 1))
        val endIdx = (endPage1Based - 1).coerceIn(startIdx, max(0, total - 1))

        try {
            var outPageNum = 1
            for (i in startIdx..endIdx) {
                val page = renderer.openPage(i)
                val width = max(page.width, A4_WIDTH)
                val height = max(page.height, A4_HEIGHT)

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, outPageNum++).create()
                val outPage = outDoc.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                bmp.recycle()
                page.close()

                outDoc.finishPage(outPage)
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
            renderer.close()
            pfd.close()
        }

        val extractedCount = (endIdx - startIdx + 1)
        return "Extracted $extractedCount page(s) (pages ${startIdx + 1}..${endIdx + 1} of $total)"
    }

    /**
     * Redacts sensitive header/ID zones and flattens each page into a rasterized PDF
     * so underlying text streams are permanently removed.
     */
    fun redactAndFlattenPdf(inputFile: File, outputFile: File): String {
        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
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

        val total = renderer.pageCount
        try {
            for (i in 0 until total) {
                val page = renderer.openPage(i)
                val width = max(page.width, A4_WIDTH)
                val height = max(page.height, A4_HEIGHT)

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val bmpCanvas = Canvas(bmp)
                bmpCanvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                // Burn irreversible redaction bars onto the bitmap before writing to PDF
                val boxes = listOf(
                    RectF(width * 0.55f, height * 0.10f, width * 0.90f, height * 0.16f),
                    RectF(width * 0.12f, height * 0.78f, width * 0.62f, height * 0.84f)
                )
                for (box in boxes) {
                    bmpCanvas.drawRect(box, redactPaint)
                    bmpCanvas.drawText("REDACTED BY MELD", box.left + 8f, box.centerY() + 4f, redactLabelPaint)
                }

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val outPage = outDoc.startPage(pageInfo)
                outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                outDoc.finishPage(outPage)
                bmp.recycle()
            }
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
            renderer.close()
            pfd.close()
        }

        return "Flattened & sanitized $total page(s) with permanent raster redaction"
    }

    private fun renderSlideIntoRect(
        renderer: PdfRenderer,
        pageIndex: Int,
        canvas: Canvas,
        destRect: Rect,
        borderPaint: Paint,
        labelPaint: Paint
    ) {
        val page = renderer.openPage(pageIndex)
        val bmp = Bitmap.createBitmap(
            max(1, destRect.width()),
            max(1, destRect.height()),
            Bitmap.Config.ARGB_8888
        )
        val bmpCanvas = Canvas(bmp)
        bmpCanvas.drawColor(Color.WHITE)

        val scale = min(
            destRect.width().toFloat() / max(1, page.width),
            destRect.height().toFloat() / max(1, page.height)
        )
        val dx = (destRect.width() - page.width * scale) / 2f
        val dy = (destRect.height() - page.height * scale) / 2f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }

        page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        canvas.drawBitmap(bmp, destRect.left.toFloat(), destRect.top.toFloat(), null)
        canvas.drawRect(destRect, borderPaint)
        canvas.drawText(
            "PAGE ${pageIndex + 1}",
            destRect.left + 6f,
            destRect.bottom + 12f,
            labelPaint
        )
        bmp.recycle()
    }
}

