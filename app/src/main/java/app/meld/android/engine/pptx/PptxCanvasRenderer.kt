package app.meld.android.engine.pptx

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Resolves OOXML font names cleanly to embedded TrueType/OpenType fonts extracted from the `.pptx`
 * (when present) or best-matching Android system families without modifying the source archive.
 */
class PptxFontResolver(
    cacheRootDir: File,
    zip: ZipFile? = null,
    embeddedFonts: List<PptxOoxmlParser.PptxEmbeddedFont> = emptyList(),
) : Closeable {

    data class ResolvedTypeface(
        val typeface: Typeface,
        val fakeBold: Boolean,
        val skewItalic: Boolean,
        val isEmbedded: Boolean,
    )

    private data class FontSlotKey(
        val familyLower: String,
        val bold: Boolean,
        val italic: Boolean,
    )

    private data class EmbeddedSlot(
        val typeface: Typeface,
        val hasRealBold: Boolean,
        val hasRealItalic: Boolean,
    )

    private val workDir: File = File(cacheRootDir, "pptx_fonts_${System.nanoTime()}")
    private val embeddedMap = LinkedHashMap<FontSlotKey, EmbeddedSlot>()
    private val embeddedRegularMap = LinkedHashMap<String, EmbeddedSlot>()
    private val loadedEmbeddedNames = LinkedHashSet<String>()
    private val fallbackMappedNames = LinkedHashSet<String>()

    init {
        if (zip != null && embeddedFonts.isNotEmpty()) {
            loadEmbeddedFonts(zip, embeddedFonts)
        }
    }

    val embeddedFontsLoadedCount: Int
        get() = loadedEmbeddedNames.size

    val fallbackFontsUsed: Set<String>
        get() = fallbackMappedNames.toSet()

    private fun loadEmbeddedFonts(
        zip: ZipFile,
        embeddedFonts: List<PptxOoxmlParser.PptxEmbeddedFont>,
    ) {
        if (!workDir.exists()) {
            workDir.mkdirs()
        }
        embeddedFonts.forEachIndexed { index, font ->
            val rawBytes = PptxOoxmlParser.readAssetBytes(zip, font.zipEntryPath) ?: return@forEachIndexed
            val decoded = PptxOoxmlParser.deobfuscateEmbeddedFont(rawBytes, font.fontKeyGuid)
                ?: return@forEachIndexed
            val ext = if (decoded.size >= 4 &&
                decoded[0] == 'O'.code.toByte() &&
                decoded[1] == 'T'.code.toByte() &&
                decoded[2] == 'T'.code.toByte() &&
                decoded[3] == 'O'.code.toByte()
            ) {
                "otf"
            } else {
                "ttf"
            }
            val outFile = File(workDir, "font_$index.$ext")
            runCatching {
                outFile.writeBytes(decoded)
                val tf = Typeface.createFromFile(outFile)
                val norm = normalizeFamily(font.typeface)
                val slot = EmbeddedSlot(
                    typeface = tf,
                    hasRealBold = font.bold || tf.isBold,
                    hasRealItalic = font.italic || tf.isItalic,
                )
                embeddedMap[FontSlotKey(norm, font.bold, font.italic)] = slot
                if (!font.bold && !font.italic) {
                    embeddedRegularMap[norm] = slot
                } else if (!embeddedRegularMap.containsKey(norm)) {
                    embeddedRegularMap[norm] = slot
                }
                loadedEmbeddedNames.add(font.typeface)
            }
        }
    }

    fun resolve(familyName: String, bold: Boolean, italic: Boolean): ResolvedTypeface {
        val norm = normalizeFamily(familyName)
        val exact = embeddedMap[FontSlotKey(norm, bold, italic)]
        if (exact != null) {
            return ResolvedTypeface(
                typeface = exact.typeface,
                fakeBold = bold && !exact.hasRealBold,
                skewItalic = italic && !exact.hasRealItalic,
                isEmbedded = true,
            )
        }

        val regular = embeddedRegularMap[norm]
        if (regular != null) {
            val style = styleMask(bold, italic)
            val derived = Typeface.create(regular.typeface, style)
            return ResolvedTypeface(
                typeface = derived,
                fakeBold = bold && !derived.isBold,
                skewItalic = italic && !derived.isItalic,
                isEmbedded = true,
            )
        }

        fallbackMappedNames.add(familyName.ifBlank { "sans-serif" })
        val systemBase = mapSystemFamily(norm)
        val style = styleMask(bold, italic)
        val tf = Typeface.create(systemBase, style)
        return ResolvedTypeface(
            typeface = tf,
            fakeBold = bold && !tf.isBold,
            skewItalic = italic && !tf.isItalic,
            isEmbedded = false,
        )
    }

    override fun close() {
        runCatching {
            if (workDir.exists()) {
                workDir.deleteRecursively()
            }
        }
    }

    companion object {
        internal fun normalizeFamily(raw: String): String {
            return raw.trim().lowercase(Locale.US)
        }

        internal fun mapSystemFamily(normLower: String): Typeface {
            return when {
                normLower.contains("mono") ||
                    normLower.contains("courier") ||
                    normLower.contains("consolas") ||
                    normLower.contains("menlo") ||
                    normLower.contains("monaco") ||
                    normLower.contains("fira code") ||
                    normLower.contains("jetbrains") ||
                    normLower.contains("source code") ||
                    normLower.contains("inconsolata") -> Typeface.MONOSPACE

                (normLower.contains("serif") && !normLower.contains("sans")) ||
                    normLower.contains("times") ||
                    normLower.contains("georgia") ||
                    normLower.contains("garamond") ||
                    normLower.contains("palatino") ||
                    normLower.contains("cambria") ||
                    normLower.contains("book antiqua") ||
                    normLower.contains("baskerville") ||
                    normLower.contains("charter") ||
                    normLower.contains("didot") ||
                    normLower.contains("bodoni") ||
                    normLower.contains("rockwell") -> Typeface.SERIF

                normLower.contains("light") || normLower.contains("thin") -> {
                    Typeface.create("sans-serif-light", Typeface.NORMAL)
                }

                else -> Typeface.SANS_SERIF
            }
        }

        private fun styleMask(bold: Boolean, italic: Boolean): Int {
            return when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        }
    }
}

/**
 * Pure Android Canvas renderer for parsed OOXML slides (`1 unit == 1 PDF point` on PdfDocument canvas).
 */
object PptxCanvasRenderer {

    private const val MAX_BITMAP_SIDE_PX = 2400

    fun renderSlide(
        canvas: Canvas,
        zip: ZipFile,
        slide: PptxOoxmlParser.PptxSlide,
        fontResolver: PptxFontResolver,
    ) {
        canvas.save()
        canvas.clipRect(0f, 0f, slide.widthPt, slide.heightPt)
        drawBackground(canvas, zip, slide)
        for (element in slide.elements) {
            when (element) {
                is PptxOoxmlParser.PptxElement.Shape -> drawShape(canvas, element, fontResolver)
                is PptxOoxmlParser.PptxElement.Picture -> drawPicture(canvas, zip, element)
                is PptxOoxmlParser.PptxElement.Table -> drawTable(canvas, element, fontResolver)
            }
        }
        canvas.restore()
    }

    fun renderSlideIntoRect(
        canvas: Canvas,
        zip: ZipFile,
        slide: PptxOoxmlParser.PptxSlide,
        fontResolver: PptxFontResolver,
        targetRect: RectF,
        borderColor: Int? = Color.rgb(180, 178, 172),
        borderWidthPt: Float = 0.75f,
    ): RectF {
        val safeW = max(1f, slide.widthPt)
        val safeH = max(1f, slide.heightPt)
        val scale = min(targetRect.width() / safeW, targetRect.height() / safeH)
        val drawW = safeW * scale
        val drawH = safeH * scale
        val left = targetRect.left + (targetRect.width() - drawW) / 2f
        val top = targetRect.top + (targetRect.height() - drawH) / 2f
        val fittedRect = RectF(left, top, left + drawW, top + drawH)

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        renderSlide(canvas, zip, slide, fontResolver)
        canvas.restore()

        if (borderColor != null && borderWidthPt > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = borderColor
                strokeWidth = borderWidthPt
            }
            canvas.drawRect(fittedRect, borderPaint)
        }
        return fittedRect
    }

    private fun drawBackground(
        canvas: Canvas,
        zip: ZipFile,
        slide: PptxOoxmlParser.PptxSlide,
    ) {
        val whitePaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawRect(0f, 0f, slide.widthPt, slide.heightPt, whitePaint)

        val bg = slide.background
        if (bg != null) {
            val paint = buildFillPaint(bg, slide.widthPt, slide.heightPt)
            canvas.drawRect(0f, 0f, slide.widthPt, slide.heightPt, paint)
        }

        val bgImagePath = slide.backgroundImagePath
        if (bgImagePath != null) {
            drawBitmapIntoRect(
                canvas = canvas,
                zip = zip,
                entryPath = bgImagePath,
                destRect = RectF(0f, 0f, slide.widthPt, slide.heightPt),
                crop = PptxOoxmlParser.CropRect(),
                targetMaxPx = max(slide.widthPt, slide.heightPt) * 2.5f,
            )
        }
    }

    private fun drawShape(
        canvas: Canvas,
        shape: PptxOoxmlParser.PptxElement.Shape,
        fontResolver: PptxFontResolver,
    ) {
        val xfrm = shape.xfrm
        val w = xfrm.width
        val h = xfrm.height
        val isLineGeom = shape.presetGeom == "line" || shape.presetGeom.startsWith("straightConnector")
        if (w <= 0f && h <= 0f) return
        if (!isLineGeom && (w <= 0.05f || h <= 0.05f)) return

        val cx = xfrm.x + w / 2f
        val cy = xfrm.y + h / 2f

        // 1. Draw geometry (fill + stroke) respecting rotation and flips
        canvas.save()
        canvas.translate(cx, cy)
        if (xfrm.rotationDeg != 0f) {
            canvas.rotate(xfrm.rotationDeg)
        }
        canvas.translate(-w / 2f, -h / 2f)

        if (isLineGeom) {
            val stroke = shape.stroke
            if (stroke != null && stroke.widthPt > 0f) {
                val startX = if (xfrm.flipH) w else 0f
                val endX = if (xfrm.flipH) 0f else w
                val startY = if (xfrm.flipV) h else 0f
                val endY = if (xfrm.flipV) 0f else h
                canvas.drawLine(startX, startY, endX, endY, buildStrokePaint(stroke))
            }
        } else {
            canvas.save()
            if (xfrm.flipH || xfrm.flipV) {
                canvas.scale(if (xfrm.flipH) -1f else 1f, if (xfrm.flipV) -1f else 1f, w / 2f, h / 2f)
            }
            val fillPaint = shape.fill?.let { buildFillPaint(it, w, h) }
            val strokePaint = shape.stroke?.takeIf { it.widthPt > 0f }?.let { buildStrokePaint(it) }
            drawShapeGeometry(canvas, shape.presetGeom, shape.cornerRadiusFrac, w, h, fillPaint, strokePaint)
            canvas.restore()
        }
        canvas.restore()

        // 2. Draw text upright along shape rotation (unflipped so labels stay readable)
        val textBody = shape.textBody
        if (textBody != null && textBody.paragraphs.isNotEmpty() && w > 2f && h > 2f) {
            canvas.save()
            canvas.translate(cx, cy)
            if (xfrm.rotationDeg != 0f) {
                canvas.rotate(xfrm.rotationDeg)
            }
            canvas.translate(-w / 2f, -h / 2f)
            drawTextBody(canvas, RectF(0f, 0f, w, h), textBody, fontResolver)
            canvas.restore()
        }
    }

    private fun drawShapeGeometry(
        canvas: Canvas,
        presetGeom: String,
        cornerRadiusFrac: Float,
        w: Float,
        h: Float,
        fillPaint: Paint?,
        strokePaint: Paint?,
    ) {
        val rect = RectF(0f, 0f, w, h)
        when (presetGeom) {
            "ellipse", "oval" -> {
                fillPaint?.let { canvas.drawOval(rect, it) }
                strokePaint?.let { canvas.drawOval(rect, it) }
            }

            "roundRect", "snip2SameRect", "snip1Rect" -> {
                val radius = min(w, h) * cornerRadiusFrac.coerceIn(0f, 0.5f)
                fillPaint?.let { canvas.drawRoundRect(rect, radius, radius, it) }
                strokePaint?.let { canvas.drawRoundRect(rect, radius, radius, it) }
            }

            "triangle" -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                fillPaint?.let { canvas.drawPath(path, it) }
                strokePaint?.let { canvas.drawPath(path, it) }
            }

            "rightArrow" -> {
                val headW = min(w * 0.42f, h * 0.72f)
                val shaftHalf = h * 0.24f
                val shaftRight = max(0f, w - headW)
                val path = Path().apply {
                    moveTo(0f, h / 2f - shaftHalf)
                    lineTo(shaftRight, h / 2f - shaftHalf)
                    lineTo(shaftRight, 0f)
                    lineTo(w, h / 2f)
                    lineTo(shaftRight, h)
                    lineTo(shaftRight, h / 2f + shaftHalf)
                    lineTo(0f, h / 2f + shaftHalf)
                    close()
                }
                fillPaint?.let { canvas.drawPath(path, it) }
                strokePaint?.let { canvas.drawPath(path, it) }
            }

            else -> {
                fillPaint?.let { canvas.drawRect(rect, it) }
                strokePaint?.let { canvas.drawRect(rect, it) }
            }
        }
    }

    private fun drawPicture(
        canvas: Canvas,
        zip: ZipFile,
        picture: PptxOoxmlParser.PptxElement.Picture,
    ) {
        val xfrm = picture.xfrm
        if (xfrm.width <= 0.5f || xfrm.height <= 0.5f) return

        val cx = xfrm.x + xfrm.width / 2f
        val cy = xfrm.y + xfrm.height / 2f
        canvas.save()
        canvas.translate(cx, cy)
        if (xfrm.rotationDeg != 0f) {
            canvas.rotate(xfrm.rotationDeg)
        }
        if (xfrm.flipH || xfrm.flipV) {
            canvas.scale(if (xfrm.flipH) -1f else 1f, if (xfrm.flipV) -1f else 1f)
        }
        canvas.translate(-xfrm.width / 2f, -xfrm.height / 2f)

        drawBitmapIntoRect(
            canvas = canvas,
            zip = zip,
            entryPath = picture.imageEntryPath,
            destRect = RectF(0f, 0f, xfrm.width, xfrm.height),
            crop = picture.crop,
            targetMaxPx = max(xfrm.width, xfrm.height) * 3f,
        )
        canvas.restore()
    }

    private fun drawBitmapIntoRect(
        canvas: Canvas,
        zip: ZipFile,
        entryPath: String,
        destRect: RectF,
        crop: PptxOoxmlParser.CropRect,
        targetMaxPx: Float,
    ) {
        val bytes = PptxOoxmlParser.readAssetBytes(zip, entryPath) ?: return
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return

        val desiredCap = targetMaxPx.roundToInt().coerceIn(256, MAX_BITMAP_SIDE_PX)
        val sampleSize = computeInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, desiredCap)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return
        try {
            val bw = bitmap.width
            val bh = bitmap.height
            val srcLeft = (bw * crop.leftFrac.coerceIn(0f, 0.9f)).roundToInt().coerceIn(0, max(0, bw - 1))
            val srcTop = (bh * crop.topFrac.coerceIn(0f, 0.9f)).roundToInt().coerceIn(0, max(0, bh - 1))
            val srcRight = (bw * (1f - crop.rightFrac.coerceIn(0f, 0.9f))).roundToInt()
                .coerceIn(srcLeft + 1, bw)
            val srcBottom = (bh * (1f - crop.bottomFrac.coerceIn(0f, 0.9f))).roundToInt()
                .coerceIn(srcTop + 1, bh)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, Rect(srcLeft, srcTop, srcRight, srcBottom), destRect, paint)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawTable(
        canvas: Canvas,
        table: PptxOoxmlParser.PptxElement.Table,
        fontResolver: PptxFontResolver,
    ) {
        if (table.rows.isEmpty() || table.colWidthsPt.isEmpty()) return

        val rawColSum = table.colWidthsPt.sum().coerceAtLeast(1f)
        val widthScale = if (table.xfrm.width > 1f) table.xfrm.width / rawColSum else 1f
        val colWidths = table.colWidthsPt.map { it * widthScale }
        val colOffsets = FloatArray(colWidths.size + 1)
        for (i in colWidths.indices) {
            colOffsets[i + 1] = colOffsets[i] + colWidths[i]
        }

        val rowOffsets = FloatArray(table.rows.size + 1)
        for (r in table.rows.indices) {
            rowOffsets[r + 1] = rowOffsets[r] + table.rows[r].heightPt.coerceAtLeast(12f)
        }

        val baseX = table.xfrm.x
        val baseY = table.xfrm.y

        for (r in table.rows.indices) {
            val row = table.rows[r]
            for (c in row.cells.indices) {
                if (c >= colWidths.size) break
                val cell = row.cells[c]
                if (cell.mergedAway) continue

                val endCol = min(colWidths.size, c + cell.colSpan.coerceAtLeast(1))
                val endRow = min(table.rows.size, r + cell.rowSpan.coerceAtLeast(1))
                val cellRect = RectF(
                    baseX + colOffsets[c],
                    baseY + rowOffsets[r],
                    baseX + colOffsets[endCol],
                    baseY + rowOffsets[endRow],
                )

                val fillColor = cell.fillArgb
                if (fillColor != null) {
                    val fillPaint = Paint().apply {
                        style = Paint.Style.FILL
                        color = fillColor
                    }
                    canvas.drawRect(cellRect, fillPaint)
                }

                val cellBody = cell.textBody
                if (cellBody != null && cellBody.paragraphs.isNotEmpty()) {
                    drawTextBody(canvas, cellRect, cellBody, fontResolver)
                }

                drawCellBorder(canvas, cellRect.left, cellRect.top, cellRect.left, cellRect.bottom, cell.strokeLeft)
                drawCellBorder(canvas, cellRect.left, cellRect.top, cellRect.right, cellRect.top, cell.strokeTop)
                drawCellBorder(canvas, cellRect.right, cellRect.top, cellRect.right, cellRect.bottom, cell.strokeRight)
                drawCellBorder(canvas, cellRect.left, cellRect.bottom, cellRect.right, cellRect.bottom, cell.strokeBottom)
            }
        }
    }

    private fun drawCellBorder(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        stroke: PptxOoxmlParser.PptxStroke?,
    ) {
        if (stroke == null || stroke.widthPt <= 0f) return
        canvas.drawLine(x1, y1, x2, y2, buildStrokePaint(stroke))
    }

    private data class BuiltParagraphLayout(
        val layout: StaticLayout,
        val bulletPrefix: String?,
        val bulletPaint: TextPaint?,
        val bulletDrawX: Float,
        val layoutStartX: Float,
        val spaceBeforePt: Float,
        val spaceAfterPt: Float,
    )

    private fun drawTextBody(
        canvas: Canvas,
        bounds: RectF,
        body: PptxOoxmlParser.PptxTextBody,
        fontResolver: PptxFontResolver,
    ) {
        val left = bounds.left + body.insetLeftPt
        val top = bounds.top + body.insetTopPt
        val right = bounds.right - body.insetRightPt
        val bottom = bounds.bottom - body.insetBottomPt
        val boxWidth = right - left
        val boxHeight = bottom - top
        if (boxWidth <= 2f || boxHeight <= 2f) return

        val layouts = buildParagraphLayouts(body, left, boxWidth, fontResolver)
        if (layouts.isEmpty()) return

        var totalHeight = 0f
        layouts.forEachIndexed { idx, item ->
            if (idx > 0) totalHeight += item.spaceBeforePt
            totalHeight += item.layout.height.toFloat()
            if (idx < layouts.lastIndex) totalHeight += item.spaceAfterPt
        }

        var cursorY = when (body.anchor) {
            PptxOoxmlParser.VerticalAnchor.TOP -> top
            PptxOoxmlParser.VerticalAnchor.CENTER -> max(top, top + (boxHeight - totalHeight) / 2f)
            PptxOoxmlParser.VerticalAnchor.BOTTOM -> max(top, bottom - totalHeight)
        }

        canvas.save()
        canvas.clipRect(bounds)
        layouts.forEachIndexed { idx, item ->
            if (idx > 0) cursorY += item.spaceBeforePt

            val bullet = item.bulletPrefix
            val bPaint = item.bulletPaint
            if (bullet != null && bPaint != null && item.layout.lineCount > 0) {
                val baselineY = cursorY + item.layout.getLineBaseline(0).toFloat()
                canvas.drawText(bullet, item.bulletDrawX, baselineY, bPaint)
            }

            canvas.save()
            canvas.translate(item.layoutStartX, cursorY)
            item.layout.draw(canvas)
            canvas.restore()

            cursorY += item.layout.height.toFloat()
            if (idx < layouts.lastIndex) cursorY += item.spaceAfterPt
        }
        canvas.restore()
    }

    private fun buildParagraphLayouts(
        body: PptxOoxmlParser.PptxTextBody,
        left: Float,
        boxWidth: Float,
        fontResolver: PptxFontResolver,
    ): List<BuiltParagraphLayout> {
        val scale = body.fontScale.coerceIn(0.4f, 1.5f)
        val built = ArrayList<BuiltParagraphLayout>(body.paragraphs.size)

        for (para in body.paragraphs) {
            val baseRun = para.runs.firstOrNull()
            val baseFontPt = ((baseRun?.fontSizePt ?: 14f) * scale).coerceAtLeast(4f)
            val baseResolved = fontResolver.resolve(
                familyName = baseRun?.fontFamily ?: "sans-serif",
                bold = baseRun?.bold ?: false,
                italic = baseRun?.italic ?: false,
            )

            val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = baseFontPt
                color = baseRun?.colorArgb ?: Color.BLACK
                typeface = baseResolved.typeface
                isFakeBoldText = baseResolved.fakeBold
                if (baseResolved.skewItalic) textSkewX = -0.25f
            }

            val bulletText = para.bulletPrefix
            val inlineBullet = bulletText != null &&
                (para.align == PptxOoxmlParser.HorizontalAlign.CENTER ||
                    para.align == PptxOoxmlParser.HorizontalAlign.RIGHT)

            val ssb = SpannableStringBuilder()
            if (inlineBullet) {
                ssb.append(bulletText).append(' ')
            }

            for (run in para.runs) {
                val cleanText = run.text.replace('\u000B', '\n')
                val start = ssb.length
                ssb.append(cleanText)
                val end = ssb.length
                if (end <= start) continue

                val resolved = fontResolver.resolve(run.fontFamily, run.bold, run.italic)
                val runPt = (run.fontSizePt * scale).coerceAtLeast(4f)
                val isSubOrSuper = run.baselinePercent != 0
                val effectivePt = if (isSubOrSuper) runPt * 0.68f else runPt

                ssb.setSpan(CustomTypefaceSpan(resolved), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(
                    RelativeSizeSpan(effectivePt / baseFontPt),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                ssb.setSpan(
                    RunStyleSpan(
                        colorArgb = run.colorArgb,
                        baselineShiftPx = if (isSubOrSuper) {
                            (-runPt * (run.baselinePercent / 100f)).roundToInt()
                        } else {
                            0
                        },
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                if (run.underline) {
                    ssb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (run.strikethrough) {
                    ssb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            if (ssb.isEmpty()) {
                // Preserve blank paragraph spacing height
                ssb.append(" ")
            }

            val leftMarginPt = para.marginLeftPt.coerceAtLeast(0f)
            val bulletDrawX: Float
            val layoutStartX: Float
            val bulletPrefixForDraw: String?
            val bulletPaint: TextPaint?

            if (bulletText != null && !inlineBullet) {
                val effectiveIndent = if (para.indentPt < 0f) para.indentPt else -12f
                val requestedMargin = max(14f, leftMarginPt)
                layoutStartX = left + requestedMargin
                bulletDrawX = max(left, left + requestedMargin + effectiveIndent)
                bulletPrefixForDraw = bulletText
                bulletPaint = TextPaint(basePaint)
            } else {
                layoutStartX = left + leftMarginPt
                bulletDrawX = left
                bulletPrefixForDraw = null
                bulletPaint = null
            }

            val availableParaWidth = (left + boxWidth - layoutStartX).roundToInt().coerceAtLeast(4)
            val androidAlign = when (para.align) {
                PptxOoxmlParser.HorizontalAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                PptxOoxmlParser.HorizontalAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                PptxOoxmlParser.HorizontalAlign.LEFT,
                PptxOoxmlParser.HorizontalAlign.JUSTIFY,
                -> Layout.Alignment.ALIGN_NORMAL
            }

            val layout = StaticLayout.Builder.obtain(ssb, 0, ssb.length, basePaint, availableParaWidth)
                .setAlignment(androidAlign)
                .setLineSpacing(0f, para.lineSpacingMult.coerceIn(0.75f, 3.0f))
                .setIncludePad(false)
                .build()

            built.add(
                BuiltParagraphLayout(
                    layout = layout,
                    bulletPrefix = bulletPrefixForDraw,
                    bulletPaint = bulletPaint,
                    bulletDrawX = bulletDrawX,
                    layoutStartX = layoutStartX,
                    spaceBeforePt = para.spaceBeforePt.coerceAtLeast(0f),
                    spaceAfterPt = para.spaceAfterPt.coerceAtLeast(0f),
                ),
            )
        }
        return built
    }

    private fun buildFillPaint(
        fill: PptxOoxmlParser.PptxFill,
        w: Float,
        h: Float,
    ): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        when (fill) {
            is PptxOoxmlParser.PptxFill.Solid -> {
                paint.color = fill.argb
            }

            is PptxOoxmlParser.PptxFill.LinearGradient -> {
                val sorted = fill.stops.sortedBy { it.posFraction }
                if (sorted.size < 2) {
                    paint.color = sorted.firstOrNull()?.argb ?: Color.TRANSPARENT
                    return paint
                }
                val rad = Math.toRadians(fill.angleDeg.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val halfSpan = (abs(dx) * w + abs(dy) * h) / 2f
                val cx = w / 2f
                val cy = h / 2f
                paint.shader = LinearGradient(
                    cx - dx * halfSpan,
                    cy - dy * halfSpan,
                    cx + dx * halfSpan,
                    cy + dy * halfSpan,
                    sorted.map { it.argb }.toIntArray(),
                    sorted.map { it.posFraction.coerceIn(0f, 1f) }.toFloatArray(),
                    Shader.TileMode.CLAMP,
                )
            }
        }
        return paint
    }

    private fun buildStrokePaint(stroke: PptxOoxmlParser.PptxStroke): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = stroke.argb
            strokeWidth = stroke.widthPt.coerceAtLeast(0.25f)
            strokeCap = Paint.Cap.SQUARE
            if (stroke.dashed) {
                val dash = max(3f, stroke.widthPt * 3.5f)
                pathEffect = DashPathEffect(floatArrayOf(dash, dash * 0.75f), 0f)
            }
        }
    }

    private fun computeInSampleSize(rawWidth: Int, rawHeight: Int, maxDimPx: Int): Int {
        var sample = 1
        while (max(rawWidth / (sample * 2), rawHeight / (sample * 2)) >= maxDimPx) {
            sample *= 2
        }
        return sample
    }

    private class CustomTypefaceSpan(
        private val resolved: PptxFontResolver.ResolvedTypeface,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            apply(tp)
        }

        override fun updateMeasureState(tp: TextPaint) {
            apply(tp)
        }

        private fun apply(tp: TextPaint) {
            tp.typeface = resolved.typeface
            tp.isFakeBoldText = resolved.fakeBold
            tp.textSkewX = if (resolved.skewItalic) -0.25f else 0f
        }
    }

    private class RunStyleSpan(
        private val colorArgb: Int,
        private val baselineShiftPx: Int,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.color = colorArgb
            tp.baselineShift = baselineShiftPx
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.baselineShift = baselineShiftPx
        }
    }
}
