package app.meld.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meld.android.model.ConversionResult
import app.meld.android.ui.MeldAdaptiveLayout
import app.meld.android.ui.theme.MeldDimens
import app.meld.android.ui.theme.MeldMotion
import app.meld.android.ui.theme.meldColors
import app.meld.android.ui.theme.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TactileCard(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MeldDimens.CardCorner,
    elevationOffset: Dp = 3.dp,
    borderColor: Color = meldColors.border,
    shadowColor: Color = meldColors.hardShadow,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val pressPx = with(density) { elevationOffset.toPx() }
    val pressProgress by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = MeldMotion.PRESS_MS, easing = MeldMotion.EaseOut),
        label = "cardPress"
    )

    Box(
        modifier = modifier.graphicsLayer {
            if (!reduceMotion && onClick != null) {
                val s = 1f - 0.018f * pressProgress
                scaleX = s
                scaleY = s
            }
        },
        propagateMinConstraints = true
    ) {
        if (elevationOffset > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = elevationOffset)
                    .clip(shape)
                    .background(shadowColor)
            )
        }

        val clickModifier = if (onClick != null) {
            Modifier
                .graphicsLayer {
                    if (!reduceMotion) {
                        translationY = pressPx * pressProgress
                    }
                }
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
        } else {
            Modifier
                .clip(shape)
        }

        Surface(
            modifier = clickModifier,
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(MeldDimens.StrokeWidth, borderColor)
        ) {
            Box(content = content)
        }
    }
}

@Composable
fun TactileCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = meldColors.surface,
    iconTint: Color = meldColors.textPrimary,
    borderColor: Color = meldColors.border,
    size: Dp = 44.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = MeldMotion.PRESS_MS, easing = MeldMotion.EaseOut),
        label = "circlePressScale"
    )

    // Visual circle can be small, but the touch target is always at least 48dp (WCAG 2.5.8 / Material).
    val touchSize = if (size < 48.dp) 48.dp else size
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            color = backgroundColor,
            border = BorderStroke(MeldDimens.StrokeWidth, borderColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TactileChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = meldColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = MeldMotion.PRESS_MS, easing = MeldMotion.EaseOut),
        label = "chipPressScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) colors.chipSelectedBg else colors.surface,
        border = BorderStroke(MeldDimens.StrokeWidth, colors.border)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.chipSelectedContent else colors.textPrimary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/**
 * Displays filter or option chips in a single horizontal scroller at standard font sizes and wraps
 * them into a multi-line FlowRow when large accessibility font scales are active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeldAdaptiveChipRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val largeFont = MeldAdaptiveLayout.isLargeFontScale(LocalDensity.current.fontScale)
    if (largeFont) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            content()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun TactilePillButton(
    text: String,
    icon: ImageVector? = null,
    backgroundColor: Color = meldColors.coral,
    contentColor: Color = if (
        backgroundColor == meldColors.surface ||
        backgroundColor == meldColors.warmSurface ||
        backgroundColor == meldColors.canvas
    ) {
        meldColors.textPrimary
    } else {
        meldColors.inkOnAccent
    },
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    customContent: (@Composable () -> Unit)? = null
) {
    val colors = meldColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val pressPx = with(density) { 3.dp.toPx() }
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = MeldMotion.PRESS_MS, easing = MeldMotion.EaseOut),
        label = "pillBtnPress"
    )

    val btnShape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier.padding(bottom = 3.dp),
        propagateMinConstraints = true
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(btnShape)
                .background(colors.hardShadow)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = height)
                .graphicsLayer {
                    if (!reduceMotion) {
                        translationY = pressPx * pressProgress
                        val s = 1f - 0.015f * pressProgress
                        scaleX = s
                        scaleY = s
                    }
                }
                .clip(btnShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
            shape = btnShape,
            color = backgroundColor,
            border = BorderStroke(MeldDimens.StrokeWidth, colors.border)
        ) {
            if (customContent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    customContent()
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
fun WorkingDocumentIllustration(
    extension: String,
    modifier: Modifier = Modifier,
    lineFractions: List<Float> = listOf(1f, 1f, 1f, 1f, 1f)
) {
    val colors = meldColors
    Canvas(modifier = modifier.size(72.dp, 90.dp)) {
        val w = size.width
        val h = size.height
        val cornerFold = 14.dp.toPx()

        val path = Path().apply {
            moveTo(2.dp.toPx(), 6.dp.toPx())
            lineTo(w - cornerFold, 3.dp.toPx())
            lineTo(w - 2.dp.toPx(), cornerFold + 5.dp.toPx())
            lineTo(w - 4.dp.toPx(), h - 2.dp.toPx())
            lineTo(5.dp.toPx(), h - 4.dp.toPx())
            close()
        }

        drawPath(path = path, color = colors.paperFill)
        drawPath(
            path = path,
            color = colors.paperStroke,
            style = Stroke(width = 2.dp.toPx())
        )

        val l0 = lineFractions.getOrElse(0) { 1f }.coerceIn(0f, 1f)
        val l1 = lineFractions.getOrElse(1) { 1f }.coerceIn(0f, 1f)
        val l2 = lineFractions.getOrElse(2) { 1f }.coerceIn(0f, 1f)
        val l3 = lineFractions.getOrElse(3) { 1f }.coerceIn(0f, 1f)
        val l4 = lineFractions.getOrElse(4) { 1f }.coerceIn(0f, 1f)

        if (l0 > 0.01f) {
            drawRoundRect(
                color = colors.paperStroke,
                topLeft = Offset(13.dp.toPx(), 16.dp.toPx()),
                size = Size((w * 0.44f) * l0, 7.5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        val lineColor = colors.paperLine
        val fullLineW = w * 0.64f
        val leftX = 13.dp.toPx()
        val ys = listOf(31.dp.toPx(), 41.dp.toPx(), 51.dp.toPx(), 61.dp.toPx())
        val widths = listOf(fullLineW * l1, fullLineW * l2, fullLineW * l3, (fullLineW * 0.78f) * l4)

        for (idx in ys.indices) {
            if (widths[idx] > 1f) {
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(leftX, ys[idx]),
                    size = Size(widths[idx], 4.2.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun TactileRulerSlider(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = meldColors
    val safeMax = maxValue.coerceAtLeast(minValue + 10)
    val rawFraction = ((value - minValue).toFloat() / (safeMax - minValue).toFloat()).coerceIn(0f, 1f)
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    var lastHapticStep by remember { mutableIntStateOf(value / 5) }

    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = if (reduceMotion) tween(80) else MeldMotion.snapSpring(),
        label = "rulerNeedleFraction"
    )

    fun dispatchValue(frac: Float) {
        val snapped = minValue + (frac * (safeMax - minValue)).roundToInt()
        val step = snapped / 5
        if (step != lastHapticStep) {
            lastHapticStep = step
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onValueChange(snapped)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(minValue, safeMax) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    dispatchValue(frac)
                }
            }
            .pointerInput(minValue, safeMax) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    dispatchValue(frac)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickCount = 24
            val usableWidth = size.width - 16.dp.toPx()
            val startX = 8.dp.toPx()
            val bottomY = size.height - 6.dp.toPx()

            for (i in 0..tickCount) {
                val x = startX + (i.toFloat() / tickCount) * usableWidth
                val tickH = if (i % 4 == 0) 28.dp.toPx() else 16.dp.toPx()
                drawLine(
                    color = colors.inkOnAccent,
                    start = Offset(x, bottomY - tickH),
                    end = Offset(x, bottomY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            val needleX = startX + animatedFraction * usableWidth
            val needleWidth = 8.dp.toPx()
            val needleHeight = 52.dp.toPx()
            drawRoundRect(
                color = colors.coral,
                topLeft = Offset(needleX - needleWidth / 2f, bottomY - needleHeight),
                size = Size(needleWidth, needleHeight),
                cornerRadius = CornerRadius(needleWidth / 2f)
            )
            drawRoundRect(
                color = colors.inkOnAccent,
                topLeft = Offset(needleX - needleWidth / 2f, bottomY - needleHeight),
                size = Size(needleWidth, needleHeight),
                cornerRadius = CornerRadius(needleWidth / 2f),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}

@Composable
fun BeforeAfterComparisonCard(
    nowLabel: String,
    afterLabel: String,
    afterRatio: Float
) {
    val colors = meldColors
    val reduceMotion = rememberReduceMotion()
    val animatedRatio by animateFloatAsState(
        targetValue = afterRatio.coerceIn(0.18f, 0.96f),
        animationSpec = if (reduceMotion) tween(100) else MeldMotion.snapSpring(),
        label = "afterBarRatio"
    )

    val largeFont = MeldAdaptiveLayout.isLargeFontScale(LocalDensity.current.fontScale)

    TactileCard(
        backgroundColor = colors.surface,
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BeforeAfterBarRow(
                label = "Now",
                sizeLabel = nowLabel,
                barColor = colors.track,
                textColor = colors.textPrimary,
                barFraction = 1f,
                largeFont = largeFont
            )
            BeforeAfterBarRow(
                label = "After",
                sizeLabel = afterLabel,
                barColor = colors.mint,
                textColor = colors.inkOnAccent,
                barFraction = animatedRatio,
                largeFont = largeFont
            )
        }
    }
}

@Composable
private fun BeforeAfterBarRow(
    label: String,
    sizeLabel: String,
    barColor: Color,
    textColor: Color,
    barFraction: Float,
    largeFont: Boolean
) {
    val colors = meldColors
    val barContent: @Composable (Modifier) -> Unit = { containerMod ->
        Box(modifier = containerMod) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .defaultMinSize(minWidth = if (largeFont) 92.dp else 68.dp, minHeight = 34.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(barColor)
                    .border(MeldDimens.StrokeWidth, colors.border, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }

    if (largeFont) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            barContent(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            barContent(Modifier.weight(1f))
        }
    }
}

@Composable
fun ResultShowcaseCard(
    result: ConversionResult,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current

    val previewState by produceState<Pair<Boolean, Bitmap?>>(
        initialValue = false to null,
        result.outputFile.absolutePath,
        result.outputSizeBytes,
        result.durationMs
    ) {
        val bmp = withContext(Dispatchers.IO) {
            val ext = result.outputFile.extension.lowercase()
            when {
                !result.outputFile.exists() -> null
                ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif") ->
                    decodeBoundedBitmap(result.outputFile, maxDim = 1080)
                ext == "pdf" ->
                    renderPdfPagePreview(result.outputFile, maxDim = 1080)
                else -> null
            }
        }
        value = true to bmp
    }
    val (previewLoaded, previewBitmap) = previewState

    DisposableEffect(previewBitmap) {
        val bmp = previewBitmap
        onDispose {
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
    }

    // Passes review-animations rule: NEVER animate from scale(0) — start from 0.90f + opacity
    val fanProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val badgeReveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val checkDraw = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val ringPulse = remember { Animatable(0f) }

    val idleBob = if (reduceMotion) 0f else run {
        val infinite = rememberInfiniteTransition(label = "resultIdleFloat")
        val p by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "idlePhase"
        )
        sin(p * 2f * PI.toFloat())
    }

    LaunchedEffect(result.outputFile.absolutePath, result.durationMs) {
        if (reduceMotion) {
            fanProgress.snapTo(1f)
            badgeReveal.snapTo(1f)
            checkDraw.snapTo(1f)
            ringPulse.snapTo(1f)
            return@LaunchedEffect
        }
        fanProgress.snapTo(0f)
        badgeReveal.snapTo(0f)
        checkDraw.snapTo(0f)
        ringPulse.snapTo(0f)

        launch {
            fanProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 280, easing = MeldMotion.EaseOut)
            )
        }
        launch {
            delay(110)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            badgeReveal.animateTo(
                1f,
                animationSpec = tween(durationMillis = 220, easing = MeldMotion.EaseOvershoot)
            )
        }
        launch {
            delay(130)
            ringPulse.animateTo(
                1f,
                animationSpec = tween(durationMillis = 480, easing = MeldMotion.EaseOut)
            )
        }
        launch {
            delay(180)
            checkDraw.animateTo(
                1f,
                animationSpec = tween(durationMillis = 200, easing = MeldMotion.EaseOut)
            )
        }
    }

    val colors = meldColors
    val density = LocalDensity.current
    val renderedPreview = previewBitmap
    val ext = result.outputFile.extension.lowercase()
    val expectsBitmapPreview = ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif", "pdf")
    val showcaseState = when {
        renderedPreview != null && !renderedPreview.isRecycled -> 2
        !previewLoaded && expectsBitmapPreview -> 0
        else -> 1
    }

    Box(modifier = modifier.fillMaxWidth()) {
        TactileCard(
            backgroundColor = colors.lavender,
            cornerRadius = 26.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(225.dp)
        ) {
            AnimatedContent(
                targetState = showcaseState,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(durationMillis = 180, easing = MeldMotion.EaseOut)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(durationMillis = 180, easing = MeldMotion.EaseOut)
                            )) togetherWith
                            fadeOut(tween(durationMillis = 120, easing = MeldMotion.EaseOut))
                    }
                },
                label = "resultPreviewEntrance"
            ) { state ->
                if (state == 2 && renderedPreview != null && !renderedPreview.isRecycled) {
                    val leftOffPx = with(density) { (-46f * fanProgress.value).dp.toPx() }
                    val rightOffPx = with(density) { (32f * fanProgress.value).dp.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight(0.88f)
                                .width(145.dp)
                                .graphicsLayer {
                                    translationX = leftOffPx
                                    rotationZ = -5f * fanProgress.value
                                    alpha = 0.6f + 0.4f * fanProgress.value
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.paperFill,
                            border = BorderStroke(2.dp, colors.paperStroke)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "MELD",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    color = colors.paperStroke
                                )
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.paperLine.copy(alpha = 0.65f))
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxHeight(0.92f)
                                .width(155.dp)
                                .graphicsLayer {
                                    translationX = rightOffPx
                                    translationY = 2.5.dp.toPx() * idleBob
                                    rotationZ = 3.5f * fanProgress.value + 1.0f * idleBob
                                    val s = 0.94f + 0.06f * fanProgress.value
                                    scaleX = s
                                    scaleY = s
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.paperFill,
                            border = BorderStroke(2.dp, colors.paperStroke)
                        ) {
                            Image(
                                bitmap = renderedPreview.asImageBitmap(),
                                contentDescription = "Result preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }
                } else if (state == 1) {
                    val previewText by produceState(
                        initialValue = result.summary,
                        result.outputFile.absolutePath,
                        result.outputSizeBytes,
                        result.durationMs
                    ) {
                        value = withContext(Dispatchers.IO) {
                            buildRichOutputPreview(result)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TactileCard(
                            backgroundColor = colors.surface,
                            cornerRadius = 16.dp,
                            elevationOffset = 2.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = 0.94f + 0.06f * fanProgress.value
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = result.outputFile.extension.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.coral
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    maxLines = 5
                                )
                            }
                        }
                    }
                } else {
                    // Blank paper frame while bitmap decodes on Dispatchers.IO so text card doesn't flash.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight(0.92f)
                                .width(155.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.paperFill.copy(alpha = 0.72f),
                            border = BorderStroke(2.dp, colors.paperStroke.copy(alpha = 0.25f))
                        ) {}
                    }
                }
            }
        }

        // Expanding Sonar Ring on completion (`ToolMotion.dc.html .rg`)
        if (!reduceMotion && ringPulse.value in 0.02f..0.98f) {
            val ringScale = 0.85f + 0.85f * ringPulse.value
            val ringAlpha = (1f - ringPulse.value) * 0.72f
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha = ringAlpha
                    }
                    .border(2.5.dp, colors.coral, CircleShape)
            )
        }

        // Signature Coral Checkmark Badge — starts from 0.90f (never scale(0)) + animates checkmark stroke
        val badgeScale = 0.90f + 0.10f * badgeReveal.value
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = (-10).dp)
                .size(52.dp)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                    alpha = badgeReveal.value.coerceIn(0f, 1f)
                },
            shape = CircleShape,
            color = colors.coral,
            border = BorderStroke(MeldDimens.StrokeWidth, colors.border)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val fullCheck = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.54f)
                    lineTo(size.width * 0.40f, size.height * 0.80f)
                    lineTo(size.width * 0.90f, size.height * 0.22f)
                }
                val pm = PathMeasure()
                pm.setPath(fullCheck, false)
                val seg = Path()
                pm.getSegment(0f, pm.length * checkDraw.value.coerceIn(0f, 1f), seg, true)
                drawPath(
                    path = seg,
                    color = colors.inkOnAccent,
                    style = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private fun decodeBoundedBitmap(file: File, maxDim: Int = 1080): Bitmap? {
    return runCatching {
        app.meld.android.engine.ImageEngine.decodeNormalizedBitmap(file, maxDimension = maxDim)
    }.getOrNull()
}

private fun readBoundedText(reader: java.io.Reader, maxChars: Int): String {
    val buf = CharArray(maxChars)
    var total = 0
    while (total < maxChars) {
        val n = reader.read(buf, total, maxChars - total)
        if (n <= 0) break
        total += n
    }
    return String(buf, 0, total)
}

private fun buildRichOutputPreview(result: ConversionResult): String {
    val file = result.outputFile
    val ext = file.extension.lowercase()
    return runCatching {
        when {
            !file.exists() -> result.summary
            ext in setOf("json", "md", "txt", "html") ->
                file.bufferedReader().use { readBoundedText(it, 240) }
            ext == "zip" -> ZipFile(file).use { zf ->
                val entries = zf.entries().asSequence().take(4).joinToString("\n") { "• ${it.name}" }
                "ZIP Archive (${zf.size()} item${if (zf.size() == 1) "" else "s"})\n$entries"
            }
            ext == "wav" && file.length() >= 44 -> {
                val hdr = ByteArray(44)
                file.inputStream().use { it.read(hdr) }
                val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
                val ch = bb.getShort(22).toInt().coerceAtLeast(1)
                val sr = bb.getInt(24).coerceAtLeast(8000)
                val dataBytes = (file.length() - 44L).coerceAtLeast(0L)
                val secs = dataBytes.toDouble() / (sr * ch * 2.0)
                "PCM WAV Audio\n${sr} Hz · ${if (ch == 2) "Stereo" else "Mono"} · ${String.format("%.1fs", secs)}"
            }
            ext == "docx" -> ZipFile(file).use { zf ->
                val docEntry = zf.getEntry("word/document.xml")
                val plain = if (docEntry != null) {
                    zf.getInputStream(docEntry).bufferedReader().use { readBoundedText(it, 4096) }
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                } else ""
                "OpenXML Word (.docx)\n${plain.take(180)}"
            }
            else -> "${result.tool.title}\n${result.summary}"
        }
    }.getOrElse { result.summary }
}

internal fun renderPdfPagePreview(pdfFile: File, maxDim: Int = 1080): Bitmap? {
    return runCatching {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val rawW = max(1, page.width)
                        val rawH = max(1, page.height)
                        val largest = max(rawW, rawH)
                        val scale = if (largest > maxDim && maxDim > 0) maxDim.toFloat() / largest else 1f
                        val w = max(1, (rawW * scale).toInt())
                        val h = max(1, (rawH * scale).toInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                } else null
            }
        }
    }.getOrNull()
}
