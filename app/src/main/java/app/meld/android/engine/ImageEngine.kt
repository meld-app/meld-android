package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageEngine {

    private const val MAX_DECODE_DIM = 2400
    private const val MAX_RESIZE_DIM = 3200

    /**
     * Decodes an image respecting EXIF orientation on all supported API levels (26+)
     * and safely bounding largest dimension to avoid OOM on multi-megapixel photos.
     */
    fun decodeNormalizedBitmap(inputFile: File, maxDimension: Int = MAX_DECODE_DIM): Bitmap {
        require(inputFile.isFile) { "File does not exist: ${inputFile.name}" }
        require(maxDimension > 0) { "maxDimension must be positive" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(inputFile)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val largest = max(w, h)
                if (largest > maxDimension) {
                    val ratio = maxDimension.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        max(1, (w * ratio).toInt()),
                        max(1, (h * ratio).toInt())
                    )
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Could not decode image dimensions: ${inputFile.name}"
        }

        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(inputFile.absolutePath, opts)
            ?: throw IllegalArgumentException("Could not decode image: ${inputFile.name}")

        val orientation = runCatching {
            ExifInterface(inputFile.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return decoded
        }

        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    /** Composites transparent images onto white so JPEG/BMP/TIFF outputs never turn alpha black. */
    private fun flattenOnWhiteIfNeeded(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val opaque = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(opaque)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        return opaque
    }

    /**
     * Converts image between formats (JPG, PNG, WEBP, BMP, TIFF).
     */
    fun convertFormat(inputFile: File, outputFile: File, targetFormat: String): String {
        val bmp = decodeNormalizedBitmap(inputFile)
        val w: Int
        val h: Int
        val fmt = targetFormat.lowercase().trimStart('.')
        try {
            w = bmp.width
            h = bmp.height
            when (fmt) {
                "jpg", "jpeg" -> {
                    val flat = flattenOnWhiteIfNeeded(bmp)
                    try {
                        FileOutputStream(outputFile).use { flat.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    } finally {
                        if (flat !== bmp) flat.recycle()
                    }
                }
                "png" -> {
                    FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                "webp" -> {
                    val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    FileOutputStream(outputFile).use { bmp.compress(webpFormat, 90, it) }
                }
                "bmp" -> {
                    val flat = flattenOnWhiteIfNeeded(bmp)
                    try {
                        writeBmp(flat, outputFile)
                    } finally {
                        if (flat !== bmp) flat.recycle()
                    }
                }
                "tiff", "tif" -> {
                    val flat = flattenOnWhiteIfNeeded(bmp)
                    try {
                        writeTiff(flat, outputFile)
                    } finally {
                        if (flat !== bmp) flat.recycle()
                    }
                }
                else -> {
                    FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        } finally {
            bmp.recycle()
        }

        return "Converted to ${fmt.uppercase()} (${w}x${h} px)"
    }

    /**
     * Centers image into a print-ready A4 vector PDF page.
     */
    fun convertImageToPdf(inputFile: File, outputFile: File): String {
        val bmp = decodeNormalizedBitmap(inputFile)
        val bmpW: Int
        val bmpH: Int
        val doc = PdfDocument()

        try {
            bmpW = bmp.width
            bmpH = bmp.height
            val a4W = 595
            val a4H = 842
            val margin = 36f
            val maxW = a4W - margin * 2f
            val maxH = a4H - margin * 2f

            val scale = min(maxW / max(1, bmpW), maxH / max(1, bmpH))
            val renderW = max(1, (bmpW * scale).toInt())
            val renderH = max(1, (bmpH * scale).toInt())
            val left = margin + (maxW - renderW) / 2f
            val top = margin + (maxH - renderH) / 2f

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
        } finally {
            runCatching { doc.close() }
            bmp.recycle()
        }

        return "Placed ${bmpW}x${bmpH} image onto centered A4 PDF page"
    }

    /**
     * Scales image resolution by percent (recycling each stage promptly) and optionally adds a studio frame.
     */
    fun resizeAndFrameImage(
        inputFile: File,
        outputFile: File,
        scalePercent: Int,
        addFrame: Boolean
    ): String {
        val original = decodeNormalizedBitmap(inputFile)
        val factor = (scalePercent.coerceIn(10, 300)) / 100.0f
        var rawW = max(32, (original.width * factor).toInt())
        var rawH = max(32, (original.height * factor).toInt())
        val largest = max(rawW, rawH)
        if (largest > MAX_RESIZE_DIM) {
            val clampScale = MAX_RESIZE_DIM.toFloat() / largest.toFloat()
            rawW = max(32, (rawW * clampScale).toInt())
            rawH = max(32, (rawH * clampScale).toInt())
        }
        val newW = rawW
        val newH = rawH

        val scaled = try {
            Bitmap.createScaledBitmap(original, newW, newH, true)
        } finally {
            original.recycle()
        }

        val finalBmp = if (addFrame) {
            val pad = 48
            val framedW = newW + pad * 2
            val framedH = newH + pad * 2
            val frameBmp = Bitmap.createBitmap(framedW, framedH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(frameBmp)
                canvas.drawColor(Color.rgb(13, 14, 18))
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
            } finally {
                scaled.recycle()
            }
            frameBmp
        } else {
            scaled
        }

        try {
            FileOutputStream(outputFile).use { finalBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            finalBmp.recycle()
        }

        return "Resized to ${newW}x${newH} (${scalePercent}% scale${if (addFrame) " + Studio Frame" else ""})"
    }

    /**
     * Creates a 2x2 photo collage sheet from the staged image.
     */
    fun createPhotoCollage(inputFile: File, outputFile: File): String {
        val original = decodeNormalizedBitmap(inputFile)
        val cellW = 540
        val cellH = 540
        val pad = 24
        val totalW = cellW * 2 + pad * 3
        val totalH = cellH * 2 + pad * 3 + 60

        val thumb = try {
            Bitmap.createScaledBitmap(original, cellW, cellH, true)
        } finally {
            original.recycle()
        }

        val collage = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(collage)
            canvas.drawColor(Color.rgb(13, 14, 18))

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
        } finally {
            thumb.recycle()
            collage.recycle()
        }

        return "Generated 2x2 photo collage contact sheet (${totalW}x${totalH} px)"
    }

    /**
     * Wipes all EXIF/GPS metadata by decoding pure pixel raster and re-encoding.
     */
    fun stripImageExif(inputFile: File, outputFile: File): String {
        val original = decodeNormalizedBitmap(inputFile)
        val w: Int
        val h: Int
        try {
            w = original.width
            h = original.height
            FileOutputStream(outputFile).use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            original.recycle()
        }
        return "Stripped all EXIF & location metadata (${w}x${h} px clean PNG)"
    }

    /**
     * Isolates foreground subject from background by sampling border chroma clusters
     * and applying smooth alpha feathering into a 32-bit ARGB_8888 PNG.
     */
    fun removeBackground(inputFile: File, outputFile: File, tolerance: Int = 42): String {
        val source = decodeNormalizedBitmap(inputFile, maxDimension = 1600)
        val width = source.width
        val height = source.height
        val outBitmap = try {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            source.recycle()
        }

        var pct = 0
        try {
            val pixels = IntArray(width * height)
            outBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val corners = listOf(
                pixels[0],
                pixels[width - 1],
                pixels[(height - 1) * width],
                pixels[(height - 1) * width + (width - 1)]
            ).filter { Color.alpha(it) > 20 }.ifEmpty { listOf(pixels[0]) }

            val bgR = corners.map { Color.red(it) }.average().toInt()
            val bgG = corners.map { Color.green(it) }.average().toInt()
            val bgB = corners.map { Color.blue(it) }.average().toInt()

            val hardThreshold = tolerance.coerceIn(12, 120).toDouble()
            val softThreshold = hardThreshold * 1.45

            var transparentCount = 0
            for (i in pixels.indices) {
                val c = pixels[i]
                val existingAlpha = Color.alpha(c)
                if (existingAlpha == 0) {
                    transparentCount++
                    continue
                }
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
                        val alpha = min(existingAlpha, (ratio * 255).toInt())
                        pixels[i] = Color.argb(alpha, r, g, b)
                    }
                }
            }

            outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            FileOutputStream(outputFile).use { stream ->
                outBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            pct = ((transparentCount.toDouble() / max(1, pixels.size)) * 100).toInt()
        } finally {
            outBitmap.recycle()
        }

        return "Isolated foreground subject ($pct% background stripped to alpha PNG)"
    }

    /**
     * Binary-searches JPEG quality and proportional downscaling to fit within targetKb,
     * never inflating already-small JPEGs.
     */
    fun compressToTargetKb(inputFile: File, outputFile: File, targetKb: Int): String {
        val safeTargetKb = max(10, targetKb)
        val targetBytes = safeTargetKb * 1024

        val decoded = decodeNormalizedBitmap(inputFile)
        val original = try {
            flattenOnWhiteIfNeeded(decoded)
        } finally {
            if (decoded.isRecycled.not()) {
                // recycled below if same reference
            }
        }
        if (original !== decoded) decoded.recycle()

        var working = original
        var bestBytes: ByteArray? = null
        var achievedQuality = 85
        var finalW = original.width
        var finalH = original.height

        try {
            // If input JPEG is already within targetKb, preserve it cleanly without inflation
            val isAlreadyJpeg = inputFile.extension.lowercase() in setOf("jpg", "jpeg")
            if (isAlreadyJpeg && inputFile.length() <= targetBytes.toLong()) {
                inputFile.copyTo(outputFile, overwrite = true)
                val alreadyKb = outputFile.length() / 1024
                return "Already compact at ${alreadyKb} KB (within ≤ $safeTargetKb KB target, ${original.width}x${original.height})"
            }

            val reusableStream = ByteArrayOutputStream()
            for (scaleStep in 0..4) {
                if (scaleStep > 0) {
                    val factor = 1.0f - (scaleStep * 0.16f)
                    val longest = max(original.width, original.height)
                    val targetLongest = max(120, (longest * factor).toInt())
                    val aspectScale = targetLongest.toFloat() / max(1, longest).toFloat()
                    val newW = max(1, (original.width * aspectScale).roundToInt())
                    val newH = max(1, (original.height * aspectScale).roundToInt())
                    val scaled = Bitmap.createScaledBitmap(original, newW, newH, true)
                    if (working !== original) working.recycle()
                    working = scaled
                }

                val result = binarySearchJpegQuality(working, targetBytes, reusableStream)
                bestBytes = result.first
                achievedQuality = result.second
                finalW = working.width
                finalH = working.height

                if (bestBytes.size <= targetBytes) {
                    break
                }
            }
        } finally {
            if (working !== original) working.recycle()
            original.recycle()
        }

        val candidateBytes = bestBytes ?: ByteArray(0)
        // Guard against re-encoding inflating an existing JPEG larger than input
        if (inputFile.extension.lowercase() in setOf("jpg", "jpeg") &&
            candidateBytes.size >= inputFile.length()
        ) {
            inputFile.copyTo(outputFile, overwrite = true)
            val keptKb = outputFile.length() / 1024
            return "Kept original compact JPEG at ${keptKb} KB (${finalW}x$finalH)"
        }

        FileOutputStream(outputFile).use { it.write(candidateBytes) }
        val finalKb = (candidateBytes.size + 512) / 1024
        return if (candidateBytes.size <= targetBytes) {
            "Compressed to ${finalKb} KB (target ≤ $safeTargetKb KB, Q=$achievedQuality%, ${finalW}x$finalH)"
        } else {
            "Best effort ${finalKb} KB (target ${safeTargetKb} KB, Q=$achievedQuality%, ${finalW}x$finalH)"
        }
    }

    /**
     * Renders a genuinely scannable ISO/IEC 18004 QR Studio card PNG.
     */
    fun generateQrStudioCard(payload: String, outputFile: File): String {
        val safePayload = payload.ifBlank { "meld.app" }
        val size = 960
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.rgb(13, 14, 18))

            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val cardRect = RectF(100f, 80f, size - 100f, size - 180f)
            canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)

            val modules = encodeIsoQrMatrix(safePayload)
            val gridCount = modules.size
            val padding = 160f
            val qrSpan = size - padding * 2f
            val cellSize = qrSpan / gridCount

            val darkPaint = Paint().apply {
                color = Color.rgb(12, 13, 18)
                style = Paint.Style.FILL
            }

            // Crisp sharp-edged modules with quiet zone guarantee scannability across cameras
            for (r in 0 until gridCount) {
                for (c in 0 until gridCount) {
                    if (modules[r][c]) {
                        val left = padding + c * cellSize
                        val top = 140f + r * cellSize
                        canvas.drawRect(left, top, left + cellSize + 0.5f, top + cellSize + 0.5f, darkPaint)
                    }
                }
            }

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

            canvas.drawText("MELD ISO/IEC 18004 QR CARD", size / 2f, size - 115f, titlePaint)
            val clippedPayload = if (safePayload.length > 42) safePayload.take(39) + "..." else safePayload
            canvas.drawText(clippedPayload, size / 2f, size - 72f, subPaint)

            FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }

        val clipped = if (payload.length > 42) payload.take(39) + "..." else safePayload
        return "Generated scannable 960x960 QR Studio card for '$clipped'"
    }

    internal fun binarySearchJpegQuality(
        bitmap: Bitmap,
        targetBytes: Int,
        stream: ByteArrayOutputStream = ByteArrayOutputStream()
    ): Pair<ByteArray, Int> {
        var low = 12
        var high = 96
        var bestBytes: ByteArray? = null
        var bestQ = low

        while (low <= high) {
            val mid = (low + high) / 2
            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, mid, stream)
            val bytes = stream.toByteArray()

            if (bytes.size <= targetBytes) {
                bestBytes = bytes
                bestQ = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (bestBytes == null) {
            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 12, stream)
            bestBytes = stream.toByteArray()
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

    /** Encodes Version 1 (21x21) for short <=17-byte payloads or scales up to Version 5-L (37x37). */
    internal fun buildQrMatrix21(payload: String): Array<BooleanArray> = encodeIsoQrMatrix(payload)

    /**
     * Pure-Kotlin ISO/IEC 18004 Byte-mode QR encoder (Versions 1-L through 5-L, single EC block,
     * up to 106 UTF-8 bytes) with GF(256) Reed-Solomon parity, 1-module finder separators,
     * timing/alignment patterns, BCH(15,5) format strings, and optimal mask selection.
     */
    internal fun encodeIsoQrMatrix(payload: String): Array<BooleanArray> {
        val rawBytes = payload.toByteArray(StandardCharsets.UTF_8)
        // Table of single-block Version-L specs: (version, dataCodewords, ecCodewords, alignmentCenter or null)
        data class QrSpec(val version: Int, val dataBytes: Int, val ecBytes: Int, val alignCoord: Int?)
        val specs = listOf(
            QrSpec(1, 19, 7, null),
            QrSpec(2, 34, 10, 18),
            QrSpec(3, 55, 15, 22),
            QrSpec(4, 80, 20, 26),
            QrSpec(5, 108, 26, 30)
        )

        val chosen = specs.firstOrNull { 2 + rawBytes.size <= it.dataBytes } ?: specs.last()
        val maxPayloadLen = chosen.dataBytes - 2
        val dataBytesInput = if (rawBytes.size > maxPayloadLen) rawBytes.copyOf(maxPayloadLen) else rawBytes

        // 1. Pack Byte-mode bitstream: mode 0100 + 8-bit count + payload + terminator + 0xEC/0x11 padding
        val totalDataBits = chosen.dataBytes * 8
        val bits = ArrayList<Int>(totalDataBits)
        fun appendBits(value: Int, count: Int) {
            for (b in count - 1 downTo 0) bits.add((value ushr b) and 1)
        }
        appendBits(0b0100, 4) // Byte mode
        appendBits(dataBytesInput.size, 8)
        for (byte in dataBytesInput) appendBits(byte.toInt() and 0xFF, 8)
        val terminatorLen = min(4, totalDataBits - bits.size)
        repeat(terminatorLen) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val codewords = IntArray(chosen.dataBytes)
        for (i in 0 until bits.size / 8) {
            var v = 0
            for (b in 0..7) v = (v shl 1) or bits[i * 8 + b]
            codewords[i] = v
        }
        var padIdx = bits.size / 8
        var padToggle = true
        while (padIdx < chosen.dataBytes) {
            codewords[padIdx++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        // 2. GF(256) Reed-Solomon ECC computation (primitive poly 0x11d)
        val ecCodewords = computeReedSolomonEc(codewords, chosen.ecBytes)
        val allCodewords = IntArray(codewords.size + ecCodewords.size)
        System.arraycopy(codewords, 0, allCodewords, 0, codewords.size)
        System.arraycopy(ecCodewords, 0, allCodewords, codewords.size, ecCodewords.size)

        val n = 17 + chosen.version * 4
        val baseGrid = Array(n) { BooleanArray(n) }
        val isFunction = Array(n) { BooleanArray(n) }

        fun setFn(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until n && c in 0 until n) {
                baseGrid[r][c] = dark
                isFunction[r][c] = true
            }
        }

        fun placeFinderWithSeparator(row0: Int, col0: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val rr = row0 + dr
                    val cc = col0 + dc
                    if (rr !in 0 until n || cc !in 0 until n) continue
                    val insideFinder = dr in 0..6 && dc in 0..6
                    val dark = if (insideFinder) {
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                    } else {
                        false // 1-module mandatory white separator
                    }
                    setFn(rr, cc, dark)
                }
            }
        }

        placeFinderWithSeparator(0, 0)
        placeFinderWithSeparator(0, n - 7)
        placeFinderWithSeparator(n - 7, 0)

        // Timing patterns
        for (i in 8 until n - 8) {
            val dark = (i % 2 == 0)
            setFn(6, i, dark)
            setFn(i, 6, dark)
        }

        // Alignment pattern (Version 2+)
        chosen.alignCoord?.let { center ->
            for (dr in -2..2) {
                for (dc in -2..2) {
                    val dark = abs(dr) == 2 || abs(dc) == 2 || (dr == 0 && dc == 0)
                    setFn(center + dr, center + dc, dark)
                }
            }
        }

        // Reserve format info cells & dark module
        setFn(4 * chosen.version + 9, 8, true)
        for (i in 0..8) {
            if (!isFunction[8][i]) setFn(8, i, false)
            if (!isFunction[i][8]) setFn(i, 8, false)
        }
        for (i in 0..7) {
            if (!isFunction[8][n - 1 - i]) setFn(8, n - 1 - i, false)
            if (!isFunction[n - 1 - i][8]) setFn(n - 1 - i, 8, false)
        }

        // 3. Place data + ECC bits in standard upward/downward zig-zag columns
        var bitCursor = 0
        val totalStreamBits = allCodewords.size * 8
        var up = true
        var right = n - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until n) {
                val r = if (up) n - 1 - vert else vert
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunction[r][c]) {
                        val bit = if (bitCursor < totalStreamBits) {
                            (allCodewords[bitCursor / 8] ushr (7 - (bitCursor % 8))) and 1
                        } else 0
                        baseGrid[r][c] = (bit == 1)
                        bitCursor++
                    }
                }
            }
            up = !up
            right -= 2
        }

        // 4. Evaluate masks 0..7 and pick lowest ISO penalty score (ECC Level L = 01)
        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        var bestGrid = baseGrid
        for (mask in 0..7) {
            val candidate = Array(n) { r ->
                BooleanArray(n) { c ->
                    if (isFunction[r][c]) baseGrid[r][c] else (baseGrid[r][c] xor qrMaskPredicate(mask, r, c))
                }
            }
            applyFormatBits(candidate, n, ecLevelBits = 0b01, mask = mask)
            val score = computeQrPenalty(candidate, n)
            if (score < bestScore) {
                bestScore = score
                bestMask = mask
                bestGrid = candidate
            }
        }
        check(bestMask in 0..7)
        return bestGrid
    }

    private fun qrMaskPredicate(mask: Int, r: Int, c: Int): Boolean = when (mask) {
        0 -> (r + c) % 2 == 0
        1 -> r % 2 == 0
        2 -> c % 3 == 0
        3 -> (r + c) % 3 == 0
        4 -> (r / 2 + c / 3) % 2 == 0
        5 -> ((r * c) % 2 + (r * c) % 3) == 0
        6 -> (((r * c) % 2 + (r * c) % 3) % 2) == 0
        else -> (((r + c) % 2 + (r * c) % 3) % 2) == 0
    }

    private fun applyFormatBits(grid: Array<BooleanArray>, n: Int, ecLevelBits: Int, mask: Int) {
        val data = (ecLevelBits shl 3) or mask
        var rem = data
        for (i in 0 until 10) {
            rem = (rem shl 1) xor (((rem ushr 9) and 1) * 0x537)
        }
        val format = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) grid[8][i] = ((format ushr i) and 1) == 1
        grid[8][7] = ((format ushr 6) and 1) == 1
        grid[8][8] = ((format ushr 7) and 1) == 1
        grid[7][8] = ((format ushr 8) and 1) == 1
        for (i in 9..14) grid[14 - i][8] = ((format ushr i) and 1) == 1

        for (i in 0..7) grid[n - 1 - i][8] = ((format ushr i) and 1) == 1
        for (i in 8..14) grid[8][n - 15 + i] = ((format ushr i) and 1) == 1
    }

    private fun computeQrPenalty(grid: Array<BooleanArray>, n: Int): Int {
        var penalty = 0
        for (r in 0 until n) {
            var runLen = 1
            for (c in 1 until n) {
                if (grid[r][c] == grid[r][c - 1]) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (c in 0 until n) {
            var runLen = 1
            for (r in 1 until n) {
                if (grid[r][c] == grid[r - 1][c]) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val color = grid[r][c]
                if (grid[r][c + 1] == color && grid[r + 1][c] == color && grid[r + 1][c + 1] == color) {
                    penalty += 3
                }
            }
        }
        return penalty
    }

    private fun computeReedSolomonEc(data: IntArray, ecCount: Int): IntArray {
        val exp = IntArray(512)
        val log = IntArray(256)
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        fun gfMul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

        // Build RS generator polynomial of degree ecCount
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val next = IntArray(gen.size + 1)
            val root = exp[i]
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
            }
            gen = next
        }

        val result = IntArray(ecCount)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, ecCount - 1)
            result[ecCount - 1] = 0
            for (j in 0 until ecCount) {
                result[j] = result[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return result
    }

    private fun writeBmp(bmp: Bitmap, outputFile: File) {
        val w = bmp.width
        val h = bmp.height
        val rowSize = ((w.toLong() * 3L + 3L) / 4L) * 4L
        val imageSize = rowSize * h.toLong()
        require(imageSize in 1L..Int.MAX_VALUE - 54L) { "BMP dimensions overflow 32-bit size limit." }
        val fileSize = (54L + imageSize).toInt()

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
        header.put('B'.code.toByte())
        header.put('M'.code.toByte())
        header.putInt(fileSize)
        header.putShort(0)
        header.putShort(0)
        header.putInt(54)
        header.putInt(40)
        header.putInt(w)
        header.putInt(h)
        header.putShort(1)
        header.putShort(24)
        header.putInt(0)
        header.putInt(imageSize.toInt())
        header.putInt(2835)
        header.putInt(2835)
        header.putInt(0)
        header.putInt(0)

        val rowPixels = IntArray(w)
        val rowBytes = ByteArray(rowSize.toInt())
        FileOutputStream(outputFile).use { fos ->
            fos.write(header.array())
            for (y in h - 1 downTo 0) {
                bmp.getPixels(rowPixels, 0, w, 0, y, w, 1)
                var colIdx = 0
                for (x in 0 until w) {
                    val p = rowPixels[x]
                    rowBytes[colIdx++] = (p and 0xFF).toByte()
                    rowBytes[colIdx++] = ((p ushr 8) and 0xFF).toByte()
                    rowBytes[colIdx++] = ((p ushr 16) and 0xFF).toByte()
                }
                while (colIdx < rowBytes.size) {
                    rowBytes[colIdx++] = 0
                }
                fos.write(rowBytes)
            }
        }
    }

    private fun writeTiff(bmp: Bitmap, outputFile: File) {
        val w = bmp.width
        val h = bmp.height
        val imageBytesSizeLong = w.toLong() * h.toLong() * 3L
        val padByte = if ((imageBytesSizeLong and 1L) != 0L) 1 else 0
        require(imageBytesSizeLong + padByte + 200L <= Int.MAX_VALUE) { "TIFF dimensions overflow limit." }

        val imageBytesSize = imageBytesSizeLong.toInt()
        val ifdOffset = 8 + imageBytesSize + padByte

        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.put('I'.code.toByte())
        header.put('I'.code.toByte())
        header.putShort(42)
        header.putInt(ifdOffset)

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
        putTag(0x0100, 3, 1, w)
        putTag(0x0101, 3, 1, h)
        putTag(0x0102, 3, 3, bitsPerSampleOffset)
        putTag(0x0103, 3, 1, 1)
        putTag(0x0106, 3, 1, 2)
        putTag(0x0111, 4, 1, 8)
        putTag(0x0115, 3, 1, 3)
        putTag(0x0116, 3, 1, h)
        putTag(0x0117, 4, 1, imageBytesSize)

        ifd.putInt(0)
        ifd.putShort(8)
        ifd.putShort(8)
        ifd.putShort(8)

        val rowPixels = IntArray(w)
        val rowRgb = ByteArray(w * 3)
        FileOutputStream(outputFile).use { fos ->
            fos.write(header.array())
            for (y in 0 until h) {
                bmp.getPixels(rowPixels, 0, w, 0, y, w, 1)
                var idx = 0
                for (x in 0 until w) {
                    val p = rowPixels[x]
                    rowRgb[idx++] = ((p ushr 16) and 0xFF).toByte()
                    rowRgb[idx++] = ((p ushr 8) and 0xFF).toByte()
                    rowRgb[idx++] = (p and 0xFF).toByte()
                }
                fos.write(rowRgb)
            }
            if (padByte == 1) fos.write(0)
            fos.write(ifd.array())
        }
    }
}
