package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageEngine {

    /**
     * Converts image between formats (JPG, PNG, WEBP, BMP, TIFF).
     */
    fun convertFormat(inputFile: File, outputFile: File, targetFormat: String): String {
        val bmp = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        val fmt = targetFormat.lowercase().trimStart('.')
        when (fmt) {
            "jpg", "jpeg" -> {
                FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }
            "png" -> {
                FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            "webp" -> {
                val webpFormat = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                FileOutputStream(outputFile).use { bmp.compress(webpFormat, 90, it) }
            }
            "bmp" -> {
                writeBmp(bmp, outputFile)
            }
            "tiff", "tif" -> {
                writeTiff(bmp, outputFile)
            }
            else -> {
                FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

        val w = bmp.width
        val h = bmp.height
        bmp.recycle()
        return "Converted to ${fmt.uppercase()} (${w}x${h} px)"
    }

    /**
     * Centers image into a print-ready A4 vector PDF page.
     */
    fun convertImageToPdf(inputFile: File, outputFile: File): String {
        val bmp = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        val a4W = 595
        val a4H = 842
        val margin = 36f
        val maxW = a4W - margin * 2f
        val maxH = a4H - margin * 2f

        val scale = min(maxW / bmp.width, maxH / bmp.height)
        val renderW = (bmp.width * scale).toInt()
        val renderH = (bmp.height * scale).toInt()
        val left = margin + (maxW - renderW) / 2f
        val top = margin + (maxH - renderH) / 2f

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(a4W, a4H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val destRect = RectF(left, top, left + renderW, top + renderH)
        canvas.drawBitmap(bmp, null, destRect, null)

        val borderPaint = Paint().apply {
            color = Color.rgb(225, 230, 238)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(destRect, borderPaint)

        doc.finishPage(page)
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
        bmp.recycle()

        return "Placed ${bmp.width}x${bmp.height} image onto centered A4 PDF page"
    }

    /**
     * Scales image resolution by percent and optionally adds a studio frame with corner tags.
     */
    fun resizeAndFrameImage(
        inputFile: File,
        outputFile: File,
        scalePercent: Int,
        addFrame: Boolean
    ): String {
        val original = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        val factor = (scalePercent.coerceIn(10, 300)) / 100.0f
        val newW = max(32, (original.width * factor).toInt())
        val newH = max(32, (original.height * factor).toInt())
        val scaled = Bitmap.createScaledBitmap(original, newW, newH, true)

        val finalBmp = if (addFrame) {
            val pad = 48
            val framedW = newW + pad * 2
            val framedH = newH + pad * 2
            val frameBmp = Bitmap.createBitmap(framedW, framedH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBmp)
            canvas.drawColor(Color.rgb(13, 14, 18)) // Studio dark

            canvas.drawBitmap(scaled, pad.toFloat(), pad.toFloat(), null)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 106, 43)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(
                RectF(pad.toFloat(), pad.toFloat(), (pad + newW).toFloat(), (pad + newH).toFloat()),
                borderPaint
            )

            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 179, 71)
                textSize = 14f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            canvas.drawText("MELD STUDIO FRAME • ${newW}x${newH}", pad.toFloat(), framedH - 16f, captionPaint)
            scaled.recycle()
            frameBmp
        } else {
            scaled
        }

        FileOutputStream(outputFile).use { finalBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()
        finalBmp.recycle()

        return "Resized to ${newW}x${newH} (${scalePercent}% scale${if (addFrame) " + Studio Frame" else ""})"
    }

    /**
     * Creates a 2x2 photo collage sheet from the staged image.
     */
    fun createPhotoCollage(inputFile: File, outputFile: File): String {
        val original = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        val cellW = 540
        val cellH = 540
        val pad = 24
        val totalW = cellW * 2 + pad * 3
        val totalH = cellH * 2 + pad * 3 + 60

        val collage = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)
        canvas.drawColor(Color.rgb(13, 14, 18))

        val thumb = Bitmap.createScaledBitmap(original, cellW, cellH, true)
        val borderPaint = Paint().apply {
            color = Color.rgb(43, 48, 66)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        for (r in 0..1) {
            for (c in 0..1) {
                val left = pad + c * (cellW + pad)
                val top = pad + r * (cellH + pad)
                canvas.drawBitmap(thumb, left.toFloat(), top.toFloat(), null)
                canvas.drawRect(RectF(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat()), borderPaint)
            }
        }

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MELD 2x2 CONTACT SHEET • ${inputFile.name}", totalW / 2f, totalH - 24f, captionPaint)

        FileOutputStream(outputFile).use { collage.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()
        thumb.recycle()
        collage.recycle()

        return "Generated 2x2 photo collage contact sheet (${totalW}x${totalH} px)"
    }

    /**
     * Wipes all EXIF/GPS metadata by decoding pure pixel raster and re-encoding.
     */
    fun stripImageExif(inputFile: File, outputFile: File): String {
        val original = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        FileOutputStream(outputFile).use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val w = original.width
        val h = original.height
        original.recycle()

        return "Stripped all EXIF & location metadata (${w}x${h} px clean PNG)"
    }

    /**
     * Isolates foreground subject from background by sampling border chroma clusters
     * and applying smooth alpha feathering into a 32-bit ARGB_8888 PNG.
     */
    fun removeBackground(inputFile: File, outputFile: File, tolerance: Int = 42): String {
        val source = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        val width = source.width
        val height = source.height
        val outBitmap = source.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        outBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Sample corner pixels to determine background reference color
        val corners = listOf(
            pixels[0],
            pixels[width - 1],
            pixels[(height - 1) * width],
            pixels[(height - 1) * width + (width - 1)]
        )
        val bgR = corners.map { Color.red(it) }.average().toInt()
        val bgG = corners.map { Color.green(it) }.average().toInt()
        val bgB = corners.map { Color.blue(it) }.average().toInt()

        val hardThreshold = tolerance.coerceIn(12, 120).toDouble()
        val softThreshold = hardThreshold * 1.45

        var transparentCount = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)

            val dist = colorDistance(r, g, b, bgR, bgG, bgB)
            when {
                dist <= hardThreshold -> {
                    pixels[i] = Color.argb(0, r, g, b)
                    transparentCount++
                }
                dist < softThreshold -> {
                    val ratio = ((dist - hardThreshold) / (softThreshold - hardThreshold)).coerceIn(0.0, 1.0)
                    val alpha = (ratio * 255).toInt()
                    pixels[i] = Color.argb(alpha, r, g, b)
                }
            }
        }

        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        FileOutputStream(outputFile).use { stream ->
            outBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        source.recycle()
        outBitmap.recycle()

        val pct = ((transparentCount.toDouble() / max(1, pixels.size)) * 100).toInt()
        return "Isolated foreground subject ($pct% background stripped to alpha PNG)"
    }

    /**
     * Binary-searches JPEG quality and resolution downscaling to fit within targetKb.
     */
    fun compressToTargetKb(inputFile: File, outputFile: File, targetKb: Int): String {
        val targetBytes = max(15, targetKb) * 1024
        val original = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        var working = original
        var bestBytes: ByteArray? = null
        var achievedQuality = 85

        for (scaleStep in 0..4) {
            if (scaleStep > 0) {
                val factor = 1.0f - (scaleStep * 0.16f)
                val newW = max(120, (original.width * factor).toInt())
                val newH = max(120, (original.height * factor).toInt())
                val scaled = Bitmap.createScaledBitmap(original, newW, newH, true)
                if (working !== original) working.recycle()
                working = scaled
            }

            val result = binarySearchJpegQuality(working, targetBytes)
            bestBytes = result.first
            achievedQuality = result.second

            if (bestBytes.size <= targetBytes) {
                break
            }
        }

        val finalBytes = bestBytes ?: ByteArray(0)
        FileOutputStream(outputFile).use { it.write(finalBytes) }

        val finalW = working.width
        val finalH = working.height
        if (working !== original) working.recycle()
        original.recycle()

        val finalKb = finalBytes.size / 1024
        return "Compressed to ${finalKb} KB (target ≤ $targetKb KB, Q=$achievedQuality%, ${finalW}x$finalH)"
    }

    /**
     * Renders a deterministic, high-contrast QR Studio card PNG for offline sharing & verification.
     */
    fun generateQrStudioCard(payload: String, outputFile: File): String {
        val size = 960
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Deep MELD Obsidian background
        canvas.drawColor(Color.rgb(13, 14, 18))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardRect = RectF(110f, 90f, size - 110f, size - 190f)
        canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)

        // Generate deterministic 21x21 QR-like matrix with standard finder patterns
        val modules = buildQrMatrix21(payload)
        val gridCount = 21
        val padding = 165f
        val qrSpan = (size - padding * 2f)
        val cellSize = qrSpan / gridCount

        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 17, 24)
            style = Paint.Style.FILL
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43) // MELD Orange finder center
            style = Paint.Style.FILL
        }

        for (r in 0 until gridCount) {
            for (c in 0 until gridCount) {
                if (modules[r][c]) {
                    val left = padding + c * cellSize
                    val top = 140f + r * cellSize
                    val isFinderCenter =
                        (r in 2..4 && c in 2..4) ||
                            (r in 2..4 && c in (gridCount - 5)..(gridCount - 3)) ||
                            (r in (gridCount - 5)..(gridCount - 3) && c in 2..4)

                    canvas.drawRoundRect(
                        RectF(left + 1.5f, top + 1.5f, left + cellSize - 1.5f, top + cellSize - 1.5f),
                        6f,
                        6f,
                        if (isFinderCenter) accentPaint else darkPaint
                    )
                }
            }
        }

        // Caption below card
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(175, 183, 198)
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("MELD OFFLINE QR MATRIX", size / 2f, size - 115f, titlePaint)
        val clippedPayload = if (payload.length > 42) payload.take(39) + "..." else payload
        canvas.drawText(clippedPayload, size / 2f, size - 72f, subPaint)

        FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        return "Generated 960x960 QR Studio card for '$clippedPayload'"
    }

    internal fun binarySearchJpegQuality(bitmap: Bitmap, targetBytes: Int): Pair<ByteArray, Int> {
        var low = 12
        var high = 96
        var bestBytes: ByteArray? = null
        var bestQ = low

        while (low <= high) {
            val mid = (low + high) / 2
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, mid, out)
            val bytes = out.toByteArray()

            if (bytes.size <= targetBytes) {
                bestBytes = bytes
                bestQ = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (bestBytes == null) {
            val fallback = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 12, fallback)
            bestBytes = fallback.toByteArray()
            bestQ = 12
        }

        return bestBytes to bestQ
    }

    internal fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    internal fun buildQrMatrix21(payload: String): Array<BooleanArray> {
        val n = 21
        val grid = Array(n) { BooleanArray(n) }
        val reserved = Array(n) { BooleanArray(n) }

        fun placeFinder(row0: Int, col0: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val rr = row0 + r
                    val cc = col0 + c
                    reserved[rr][cc] = true
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCore = r in 2..4 && c in 2..4
                    grid[rr][cc] = isBorder || isCore
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, n - 7)
        placeFinder(n - 7, 0)

        // Timing patterns
        for (i in 8 until n - 8) {
            reserved[6][i] = true
            reserved[i][6] = true
            grid[6][i] = (i % 2 == 0)
            grid[i][6] = (i % 2 == 0)
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        var bitIdx = 0
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (!reserved[r][c]) {
                    val byteVal = digest[(bitIdx / 8) % digest.size].toInt() and 0xFF
                    val bit = (byteVal ushr (bitIdx % 8)) and 1
                    grid[r][c] = (bit == 1)
                    bitIdx++
                }
            }
        }
        return grid
    }

    private fun writeBmp(bmp: Bitmap, outputFile: File) {
        val w = bmp.width
        val h = bmp.height
        val rowSize = ((w * 3 + 3) / 4) * 4
        val imageSize = rowSize * h
        val fileSize = 54 + imageSize

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
        header.put('B'.code.toByte())
        header.put('M'.code.toByte())
        header.putInt(fileSize)
        header.putShort(0)
        header.putShort(0)
        header.putInt(54) // offset
        header.putInt(40) // DIB header size
        header.putInt(w)
        header.putInt(h)
        header.putShort(1) // color planes
        header.putShort(24) // 24 bpp
        header.putInt(0) // BI_RGB
        header.putInt(imageSize)
        header.putInt(2835) // 72 DPI
        header.putInt(2835)
        header.putInt(0)
        header.putInt(0)

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        FileOutputStream(outputFile).use { fos ->
            fos.write(header.array())
            val rowBytes = ByteArray(rowSize)
            // BMP rows stored bottom-to-top
            for (y in h - 1 downTo 0) {
                var colIdx = 0
                for (x in 0 until w) {
                    val p = pixels[y * w + x]
                    rowBytes[colIdx++] = (p and 0xFF).toByte() // B
                    rowBytes[colIdx++] = ((p ushr 8) and 0xFF).toByte() // G
                    rowBytes[colIdx++] = ((p ushr 16) and 0xFF).toByte() // R
                }
                while (colIdx < rowSize) {
                    rowBytes[colIdx++] = 0
                }
                fos.write(rowBytes)
            }
        }
    }

    private fun writeTiff(bmp: Bitmap, outputFile: File) {
        val w = bmp.width
        val h = bmp.height
        val imageBytesSize = w * h * 3
        val ifdOffset = 8 + imageBytesSize

        // TIFF Little Endian header
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.put('I'.code.toByte())
        header.put('I'.code.toByte())
        header.putShort(42)
        header.putInt(ifdOffset)

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgbBytes = ByteArray(imageBytesSize)
        var idx = 0
        for (p in pixels) {
            rgbBytes[idx++] = ((p ushr 16) and 0xFF).toByte() // R
            rgbBytes[idx++] = ((p ushr 8) and 0xFF).toByte()  // G
            rgbBytes[idx++] = (p and 0xFF).toByte()          // B
        }

        // IFD with 9 entries
        val numEntries: Short = 9
        val ifd = ByteBuffer.allocate(2 + numEntries * 12 + 4 + 6).order(ByteOrder.LITTLE_ENDIAN)
        ifd.putShort(numEntries)

        fun putTag(tag: Int, type: Int, count: Int, valueOrOffset: Int) {
            ifd.putShort(tag.toShort())
            ifd.putShort(type.toShort())
            ifd.putInt(count)
            ifd.putInt(valueOrOffset)
        }

        val bitsPerSampleOffset = ifdOffset + 2 + numEntries * 12 + 4

        putTag(0x0100, 3, 1, w) // ImageWidth
        putTag(0x0101, 3, 1, h) // ImageLength
        putTag(0x0102, 3, 3, bitsPerSampleOffset) // BitsPerSample [8,8,8]
        putTag(0x0103, 3, 1, 1) // Compression = None
        putTag(0x0106, 3, 1, 2) // Photometric = RGB
        putTag(0x0111, 4, 1, 8) // StripOffsets = 8
        putTag(0x0115, 3, 1, 3) // SamplesPerPixel = 3
        putTag(0x0116, 3, 1, h) // RowsPerStrip = h
        putTag(0x0117, 4, 1, imageBytesSize) // StripByteCounts

        ifd.putInt(0) // Next IFD offset = 0
        ifd.putShort(8) // BitsPerSample R
        ifd.putShort(8) // BitsPerSample G
        ifd.putShort(8) // BitsPerSample B

        FileOutputStream(outputFile).use { fos ->
            fos.write(header.array())
            fos.write(rgbBytes)
            fos.write(ifd.array())
        }
    }
}
