package app.meld.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meld.android.model.ConversionResult
import app.meld.android.ui.theme.MeldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

val MeldOvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
val MeldSmoothEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Composable
fun TactileCard(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevationOffset: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressOffsetY by animateDpAsState(
        targetValue = if (onClick != null && isPressed) elevationOffset else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "tactileCardPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.988f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "tactileCardScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
    ) {
        // Hard Neo-Brutalist Ink Shadow Base (matching `box-shadow: 0 3px 0 #1B1A17`)
        if (elevationOffset > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = elevationOffset)
                    .clip(shape)
                    .background(MeldColors.InkBlack)
            )
        }

        val clickModifier = if (onClick != null) {
            Modifier
                .fillMaxSize()
                .offset(y = pressOffsetY)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        } else {
            Modifier
                .fillMaxSize()
                .clip(shape)
        }

        Surface(
            modifier = clickModifier,
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
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
    backgroundColor: Color = MeldColors.PureWhite,
    iconTint: Color = MeldColors.InkBlack,
    size: Dp = 44.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "circlePressScale"
    )

    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
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

@Composable
fun TactileChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chipPressScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (selected) MeldColors.InkBlack else MeldColors.PureWhite,
        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MeldColors.PureWhite else MeldColors.InkBlack,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun TactilePillButton(
    text: String,
    icon: ImageVector? = null,
    backgroundColor: Color = MeldColors.CoralOrange,
    contentColor: Color = MeldColors.InkBlack,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    customContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetY by animateDpAsState(
        targetValue = if (enabled && isPressed) 3.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "btnPressOffset"
    )

    Box(
        modifier = modifier
            .height(height + 3.dp)
    ) {
        // Hard bottom ledge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = 3.dp)
                .clip(CircleShape)
                .background(MeldColors.InkBlack)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = pressOffsetY)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            shape = CircleShape,
            color = backgroundColor,
            border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
        ) {
            if (customContent != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    customContent()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
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
                        color = contentColor
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
    Canvas(modifier = modifier.size(72.dp, 90.dp)) {
        val w = size.width
        val h = size.height
        val cornerFold = 14.dp.toPx()

        // Slight paper page with crisp ink border
        val path = Path().apply {
            moveTo(2.dp.toPx(), 6.dp.toPx())
            lineTo(w - cornerFold, 3.dp.toPx())
            lineTo(w - 2.dp.toPx(), cornerFold + 5.dp.toPx())
            lineTo(w - 4.dp.toPx(), h - 2.dp.toPx())
            lineTo(5.dp.toPx(), h - 4.dp.toPx())
            close()
        }

        drawPath(path = path, color = Color(0xFFFFFFFF))
        drawPath(
            path = path,
            color = MeldColors.InkBlack,
            style = Stroke(width = 2.dp.toPx())
        )

        val l0 = lineFractions.getOrElse(0) { 1f }.coerceIn(0f, 1f)
        val l1 = lineFractions.getOrElse(1) { 1f }.coerceIn(0f, 1f)
        val l2 = lineFractions.getOrElse(2) { 1f }.coerceIn(0f, 1f)
        val l3 = lineFractions.getOrElse(3) { 1f }.coerceIn(0f, 1f)
        val l4 = lineFractions.getOrElse(4) { 1f }.coerceIn(0f, 1f)

        // Header ink bar inside page (l0)
        if (l0 > 0.01f) {
            drawRoundRect(
                color = MeldColors.InkBlack,
                topLeft = Offset(13.dp.toPx(), 16.dp.toPx()),
                size = Size((w * 0.44f) * l0, 7.5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Four soft content lines (l1..l4) matching Opening.dc.html
        val lineColor = Color(0xFFD8D2C4)
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
    val safeMax = maxValue.coerceAtLeast(minValue + 10)
    val rawFraction = ((value - minValue).toFloat() / (safeMax - minValue).toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "rulerNeedleFraction"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(minValue, safeMax) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val snapped = minValue + (frac * (safeMax - minValue)).roundToInt()
                    onValueChange(snapped)
                }
            }
            .pointerInput(minValue, safeMax) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val snapped = minValue + (frac * (safeMax - minValue)).roundToInt()
                    onValueChange(snapped)
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
                    color = MeldColors.InkBlack,
                    start = Offset(x, bottomY - tickH),
                    end = Offset(x, bottomY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            val needleX = startX + animatedFraction * usableWidth
            val needleWidth = 8.dp.toPx()
            val needleHeight = 52.dp.toPx()
            drawRoundRect(
                color = MeldColors.CoralOrange,
                topLeft = Offset(needleX - needleWidth / 2f, bottomY - needleHeight),
                size = Size(needleWidth, needleHeight),
                cornerRadius = CornerRadius(needleWidth / 2f)
            )
            drawRoundRect(
                color = MeldColors.InkBlack,
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
    val animatedRatio by animateFloatAsState(
        targetValue = afterRatio.coerceIn(0.18f, 0.96f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "afterBarRatio"
    )

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.TextSecondary,
                    modifier = Modifier.width(52.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(MeldColors.TrackGray)
                        .border(MeldColors.StrokeWidth, MeldColors.InkBlack, CircleShape),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = nowLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MeldColors.InkBlack,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "After",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.TextSecondary,
                    modifier = Modifier.width(52.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatio)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(MeldColors.MintSage)
                            .border(MeldColors.StrokeWidth, MeldColors.InkBlack, CircleShape),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = afterLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MeldColors.InkBlack,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultShowcaseCard(
    result: ConversionResult,
    modifier: Modifier = Modifier
) {
    val previewBitmap = remember(result.outputFile.absolutePath, result.outputSizeBytes) {
        val ext = result.outputFile.extension.lowercase()
        when {
            !result.outputFile.exists() -> null
            ext in setOf("png", "jpg", "jpeg", "webp") ->
                BitmapFactory.decodeFile(result.outputFile.absolutePath)
            ext == "pdf" ->
                renderFirstPdfPage(result.outputFile)
            else -> null
        }
    }

    // Celebratory entrance animation
    val fanProgress = remember { Animatable(0f) }
    val checkScale = remember { Animatable(0f) }

    LaunchedEffect(result.outputFile.absolutePath, result.durationMs) {
        fanProgress.snapTo(0f)
        checkScale.snapTo(0f)
        launch {
            fanProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 540, easing = MeldOvershootEasing)
            )
        }
        launch {
            delay(180)
            checkScale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        TactileCard(
            backgroundColor = MeldColors.SoftLavender,
            cornerRadius = 26.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(225.dp)
        ) {
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val leftOffset = (-46f * fanProgress.value).dp
                    val leftRot = -5f * fanProgress.value
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight(0.88f)
                            .width(145.dp)
                            .offset(x = leftOffset)
                            .rotate(leftRot),
                        shape = RoundedCornerShape(14.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(2.dp, MeldColors.InkBlack)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MELD STUDIO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MeldColors.InkBlack
                            )
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MeldColors.WarmSurface)
                                )
                            }
                        }
                    }

                    val rightOffset = (32f * fanProgress.value).dp
                    val rightRot = 3.5f * fanProgress.value
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .width(155.dp)
                            .offset(x = rightOffset)
                            .rotate(rightRot),
                        shape = RoundedCornerShape(14.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(2.dp, MeldColors.InkBlack)
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Result preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }
            } else {
                val previewText = remember(result.outputFile.absolutePath) {
                    val ext = result.outputFile.extension.lowercase()
                    if (ext in setOf("json", "md", "txt", "html") && result.outputFile.exists()) {
                        result.outputFile.readText().take(240)
                    } else {
                        "${result.tool.title}\nOutput: ${result.outputFile.name}"
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TactileCard(
                        backgroundColor = MeldColors.PureWhite,
                        cornerRadius = 16.dp,
                        elevationOffset = 2.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 0.90f + 0.10f * fanProgress.value
                                scaleY = 0.90f + 0.10f * fanProgress.value
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = result.outputFile.extension.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MeldColors.CoralOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeldColors.InkBlack,
                                maxLines = 5
                            )
                        }
                    }
                }
            }
        }

        // Bouncy Coral Checkmark Badge in top-right corner
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = (-10).dp)
                .size(52.dp)
                .graphicsLayer {
                    scaleX = checkScale.value
                    scaleY = checkScale.value
                    alpha = checkScale.value.coerceIn(0f, 1f)
                },
            shape = CircleShape,
            color = MeldColors.CoralOrange,
            border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MeldColors.InkBlack,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun renderFirstPdfPage(pdfFile: File): Bitmap? {
    return runCatching {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                } else null
            }
        }
    }.getOrNull()
}
