package app.meld.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.meld.android.engine.ImageEngine
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import app.meld.android.ui.theme.MeldColors
import app.meld.android.ui.theme.MeldMotion
import app.meld.android.ui.theme.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Opposing compression indicator arrows for Compress / Compress PDF that physically squeeze inward as target KB drops.
 */
@Composable
fun CompressionRatioPill(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val squeeze by animateFloatAsState(
        targetValue = (1f - ratio).coerceIn(0.1f, 0.9f),
        animationSpec = if (reduceMotion) tween(80) else MeldMotion.snapSpring(),
        label = "compressionSqueeze"
    )
    val gapOffset = 4f * (1f - squeeze)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MeldColors.PureWhite,
        border = BorderStroke(1.6.dp, MeldColors.InkBlack)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val midX = size.width / 2f
                val midY = size.height / 2f
                val topY = midY - 2.dp.toPx() - gapOffset.dp.toPx()
                val botY = midY + 2.dp.toPx() + gapOffset.dp.toPx()
                // Center horizontal plate
                drawLine(MeldColors.CoralOrange, Offset(2f, midY), Offset(size.width - 2f, midY), strokeWidth = 2.dp.toPx())
                // Top downward arrow
                drawLine(MeldColors.InkBlack, Offset(midX, 0f), Offset(midX, topY), strokeWidth = 1.8.dp.toPx())
                // Bottom upward arrow
                drawLine(MeldColors.InkBlack, Offset(midX, size.height), Offset(midX, botY), strokeWidth = 1.8.dp.toPx())
            }
            Text(
                text = "-${(squeeze * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MeldColors.InkBlack
            )
        }
    }
}

/**
 * Smoothly animated diagram morphing between 2-up, 3-up (Mac layout), and 4-up grids.
 */
@Composable
fun AnimatedHandoutDiagram(
    pagesPerSheet: Int,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val slideCountTarget = pagesPerSheet.coerceIn(2, 4).toFloat()
    val animMode by animateFloatAsState(
        targetValue = slideCountTarget,
        animationSpec = if (reduceMotion) tween(100) else MeldMotion.snapSpring(),
        label = "handoutModeMorph"
    )

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = MeldColors.SubCardCorner,
        elevationOffset = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(175.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height
            val gridBlend = ((animMode - 3f).coerceIn(0f, 1f)) // 0 when 2 or 3, 1 when 4
            val verticalRows = animMode.coerceIn(2f, 3f)

            val pad = 8.dp.toPx()
            val cardWLeft = lerp(w * 0.42f, (w - pad) / 2f, gridBlend)
            val cardHList = (h - (verticalRows - 1f) * pad) / verticalRows
            val cardHGrid = (h - pad) / 2f

            // Up to 4 mini slide cards interpolating cleanly
            for (i in 0 until 4) {
                val showAlpha = when (i) {
                    0, 1 -> 1f
                    2 -> (animMode - 2f).coerceIn(0f, 1f)
                    else -> gridBlend
                }
                if (showAlpha <= 0.02f) continue

                val listX = 0f
                val listY = i.toFloat() * (cardHList + pad)
                val gridX = (i % 2) * (cardWLeft + pad)
                val gridY = (i / 2) * (cardHGrid + pad)

                val x = lerp(listX, gridX, gridBlend)
                val y = lerp(listY, gridY, gridBlend)
                val cw = cardWLeft
                val ch = lerp(cardHList, cardHGrid, gridBlend)

                drawRoundRect(
                    color = MeldColors.WarmSurface.copy(alpha = showAlpha),
                    topLeft = Offset(x, y),
                    size = Size(cw, ch),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawRoundRect(
                    color = MeldColors.InkBlack.copy(alpha = showAlpha),
                    topLeft = Offset(x, y),
                    size = Size(cw, ch),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(1.6.dp.toPx())
                )

                // Coral accent header bar inside mini slide
                drawRoundRect(
                    color = MeldColors.CoralOrange.copy(alpha = showAlpha),
                    topLeft = Offset(x + 6.dp.toPx(), y + 6.dp.toPx()),
                    size = Size(cw * 0.35f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )

                // Ruled note lines on the right for 2-up / 3-up (fade cleanly when entering 4-up)
                val notesAlpha = (1f - gridBlend) * showAlpha
                if (notesAlpha > 0.04f && i < 3) {
                    val lineStart = cardWLeft + 14.dp.toPx()
                    for (l in 1..3) {
                        val ly = y + (ch * l / 4f)
                        drawLine(
                            color = MeldColors.TrackGray.copy(alpha = notesAlpha),
                            start = Offset(lineStart, ly),
                            end = Offset(w, ly),
                            strokeWidth = 1.6.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Split PDF visual stage where pages inside [startPage..endPage] lift up cleanly.
 */
@Composable
fun SplitPageSpreadStage(
    totalPages: Int,
    startPage: Int,
    endPage: Int,
    onSelectPage: (Int) -> Unit
) {
    val visibleCount = totalPages.coerceIn(2, 5)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    TactileCard(
        backgroundColor = MeldColors.PeriwinkleBlue,
        cornerRadius = MeldColors.CardCorner,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "EXTRACT RANGE",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.InkBlack.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pages $startPage to $endPage",
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                color = MeldColors.InkBlack
            )
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (pageNum in 1..visibleCount) {
                    val included = pageNum in startPage..endPage
                    val lift by animateFloatAsState(
                        targetValue = if (included) 1f else 0f,
                        animationSpec = if (reduceMotion) tween(100) else MeldMotion.snapSpring(),
                        label = "pageLift$pageNum"
                    )
                    val liftPx = with(density) { (-6f * lift).dp.toPx() }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp)
                            .graphicsLayer {
                                translationY = liftPx
                                val s = 0.92f + 0.08f * lift
                                scaleX = s
                                scaleY = s
                                alpha = 0.45f + 0.55f * lift
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPage(pageNum) },
                        shape = RoundedCornerShape(12.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(if (included) 2.dp else 1.4.dp, MeldColors.InkBlack)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (included) MeldColors.CoralOrange else MeldColors.TrackGray)
                            )
                            Text(
                                text = "P$pageNum",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MeldColors.InkBlack
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kinetic Rubber Stamp card that triggers a snappy asymmetric stamp impact whenever stampText updates.
 */
@Composable
fun StampSlamPreview(
    stampText: String,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val slamScale = remember { Animatable(1f) }

    LaunchedEffect(stampText) {
        if (!reduceMotion && stampText.isNotEmpty()) {
            slamScale.snapTo(1.16f)
            slamScale.animateTo(1f, tween(durationMillis = 190, easing = MeldMotion.EaseOut))
        }
    }

    TactileCard(
        backgroundColor = MeldColors.PeachCream,
        cornerRadius = MeldColors.CardCorner,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            TactileCard(
                backgroundColor = MeldColors.PureWhite,
                cornerRadius = MeldColors.SubCardCorner,
                elevationOffset = 2.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                rotationZ = -10f
                                scaleX = slamScale.value
                                scaleY = slamScale.value
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MeldColors.CoralOrange.copy(alpha = 0.16f),
                        border = BorderStroke(2.dp, MeldColors.CoralOrange)
                    ) {
                        Text(
                            text = stampText.ifBlank { "WATERMARK" }.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MeldColors.CoralOrange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live split wipe stage demonstrating original background vs alpha checkerboard strip for Remove BG.
 */
@Composable
fun RemoveBgCurtainStage(
    tolerance: Int,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val norm by animateFloatAsState(
        targetValue = ((tolerance - 15f) / 80f).coerceIn(0.15f, 0.88f),
        animationSpec = if (reduceMotion) tween(80) else MeldMotion.snapSpring(),
        label = "removeBgCurtain"
    )

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = MeldColors.SubCardCorner,
        elevationOffset = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val splitX = size.width * norm
            // Left: original dark photo background
            drawRect(
                color = Color(0xFF282D3C),
                topLeft = Offset.Zero,
                size = Size(splitX, size.height)
            )
            // Right: clean alpha transparency checkerboard
            clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                val cell = 12.dp.toPx()
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        val isAlt = (r + c) % 2 == 0
                        drawRect(
                            color = if (isAlt) Color(0xFFEDE7DE) else Color(0xFFFFFFFF),
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell, cell)
                        )
                    }
                }
            }
            // Centered subject emblem across the wipe
            drawRoundRect(
                color = MeldColors.CoralOrange,
                topLeft = Offset(size.width / 2f - 28.dp.toPx(), size.height / 2f - 28.dp.toPx()),
                size = Size(56.dp.toPx(), 56.dp.toPx()),
                cornerRadius = CornerRadius(14.dp.toPx())
            )
            drawRoundRect(
                color = MeldColors.InkBlack,
                topLeft = Offset(size.width / 2f - 28.dp.toPx(), size.height / 2f - 28.dp.toPx()),
                size = Size(56.dp.toPx(), 56.dp.toPx()),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(2.dp.toPx())
            )
            // Wipe divider line
            drawLine(
                color = MeldColors.InkBlack,
                start = Offset(splitX, 0f),
                end = Offset(splitX, size.height),
                strokeWidth = 2.4.dp.toPx()
            )
        }
    }
}

/**
 * Interactive scaling frame preview for Resize & Frame.
 */
@Composable
fun ResizeFrameStage(
    percent: Int,
    addFrame: Boolean,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val scaleFrac by animateFloatAsState(
        targetValue = (percent / 200f).coerceIn(0.22f, 1f),
        animationSpec = if (reduceMotion) tween(80) else MeldMotion.snapSpring(),
        label = "resizeScaleFrac"
    )
    val framePad by animateFloatAsState(
        targetValue = if (addFrame) 1f else 0f,
        animationSpec = tween(MeldMotion.UI_MS, easing = MeldMotion.EaseOut),
        label = "resizeFramePad"
    )

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = MeldColors.SubCardCorner,
        elevationOffset = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val baseW = size.width * 0.50f
            val baseH = size.height * 0.58f
            // Dashed 100% baseline guide
            drawRoundRect(
                color = MeldColors.TextMuted,
                topLeft = Offset((size.width - baseW) / 2f, (size.height - baseH) / 2f),
                size = Size(baseW, baseH),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            )
            val boxW = (size.width * 0.86f) * scaleFrac
            val boxH = (size.height * 0.88f) * scaleFrac
            val left = (size.width - boxW) / 2f
            val top = (size.height - boxH) / 2f

            if (framePad > 0.02f) {
                val pad = 5.dp.toPx() * framePad
                drawRoundRect(
                    color = MeldColors.InkBlack.copy(alpha = framePad),
                    topLeft = Offset(left - pad, top - pad),
                    size = Size(boxW + pad * 2, boxH + pad * 2),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            drawRoundRect(
                color = MeldColors.CoralOrange,
                topLeft = Offset(left, top),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(7.dp.toPx())
            )
            drawRoundRect(
                color = MeldColors.InkBlack,
                topLeft = Offset(left, top),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(7.dp.toPx()),
                style = Stroke(1.8.dp.toPx())
            )
        }
    }
}

/**
 * Animated QR Matrix with center-outward ripple on text edit.
 */
@Composable
fun QrMatrixStage(
    payload: String,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val matrix = remember(payload) { ImageEngine.buildQrMatrix21(payload.ifEmpty { "meld.app" }) }
    val ripple = remember { Animatable(1f) }

    LaunchedEffect(payload) {
        if (!reduceMotion) {
            ripple.snapTo(0.88f)
            ripple.animateTo(1f, tween(durationMillis = 190, easing = MeldMotion.EaseOut))
        }
    }

    TactileCard(
        backgroundColor = MeldColors.SoftLavender,
        cornerRadius = MeldColors.CardCorner,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            TactileCard(
                backgroundColor = MeldColors.PureWhite,
                cornerRadius = MeldColors.SubCardCorner,
                elevationOffset = 2.dp,
                modifier = Modifier
                    .size(165.dp)
                    .graphicsLayer {
                        scaleX = ripple.value
                        scaleY = ripple.value
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    val cell = size.width / 21f
                    for (r in 0 until 21) {
                        for (c in 0 until 21) {
                            if (matrix[r][c]) {
                                val isFinderCenter =
                                    (r in 2..4 && c in 2..4) ||
                                        (r in 2..4 && c in 16..18) ||
                                        (r in 16..18 && c in 2..4)
                                drawRect(
                                    color = if (isFinderCenter) MeldColors.CoralOrange else MeldColors.InkBlack,
                                    topLeft = Offset(c * cell, r * cell),
                                    size = Size(cell * 0.92f, cell * 0.92f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tailored visual stages for Audio Waveform, Speed 2X, Redact/Sanitize, Collage, Merge, Zip, and Format Conversions.
 */
@Composable
fun SpecializedToolShowcaseCard(
    tool: MeldToolId,
    file: SelectedFileInfo?
) {
    val reduceMotion = rememberReduceMotion()
    val sourceExt = (file?.extension?.takeIf { it.isNotBlank() } ?: "FILE").uppercase()

    when (tool) {
        MeldToolId.VISUALIZE -> {
            val phase by if (reduceMotion) {
                remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            } else {
                val infinite = rememberInfiniteTransition(label = "waveAnim")
                infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = (2f * PI).toFloat(),
                    animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
                    label = "wavePhase"
                )
            }

            TactileCard(
                backgroundColor = MeldColors.DockInk,
                cornerRadius = MeldColors.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "LIVE STUDIO WAVEFORM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeldColors.ButterYellow
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                        val bars = 30
                        val gap = 4.dp.toPx()
                        val bw = (size.width - (bars - 1) * gap) / bars
                        val midY = size.height / 2f
                        val grad = Brush.verticalGradient(
                            listOf(MeldColors.ButterYellow, MeldColors.CoralOrange, MeldColors.SoftLavender)
                        )
                        for (i in 0 until bars) {
                            val amp = 0.25f + 0.7f * abs(sin(i * 0.42f + phase))
                            val bh = size.height * amp
                            drawRoundRect(
                                brush = grad,
                                topLeft = Offset(i * (bw + gap), midY - bh / 2f),
                                size = Size(bw, bh),
                                cornerRadius = CornerRadius(bw / 2f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Exports crisp 1920×600 dark-mode waveform PNG",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeldColors.PureWhite.copy(alpha = 0.85f)
                    )
                }
            }
        }

        MeldToolId.SPEED_2X -> {
            val beat by if (reduceMotion) {
                remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
            } else {
                val infinite = rememberInfiniteTransition(label = "speed2xAnim")
                infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                    label = "speedBeat"
                )
            }

            TactileCard(
                backgroundColor = MeldColors.ButterYellow,
                cornerRadius = MeldColors.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = MeldColors.InkBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2.0x DOUBLE TEMPO STREAM", style = MaterialTheme.typography.labelSmall, color = MeldColors.InkBlack)
                    }

                    TactileCard(
                        backgroundColor = MeldColors.PureWhite,
                        cornerRadius = MeldColors.SubCardCorner,
                        elevationOffset = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpeedTrackRow(label = "1.0x", progress = beat, barColor = MeldColors.TrackGray)
                            SpeedTrackRow(label = "2.0x", progress = (beat * 2f) % 1f, barColor = MeldColors.CoralOrange)
                        }
                    }
                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeldColors.InkBlack.copy(alpha = 0.85f)
                    )
                }
            }
        }

        MeldToolId.REDACT, MeldToolId.STRIP_PDF_META, MeldToolId.STRIP_EXIF -> {
            val wipe = remember { Animatable(if (reduceMotion) 1f else 0.15f) }
            LaunchedEffect(tool) {
                if (!reduceMotion) {
                    wipe.snapTo(0.15f)
                    wipe.animateTo(1f, tween(durationMillis = 280, easing = MeldMotion.EaseOut))
                }
            }

            TactileCard(
                backgroundColor = MeldColors.PeachCream,
                cornerRadius = MeldColors.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TactileCard(
                        backgroundColor = MeldColors.PureWhite,
                        cornerRadius = MeldColors.SubCardCorner,
                        elevationOffset = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (tool == MeldToolId.STRIP_EXIF) "EXIF: GPS • Camera Model" else "ID: MELD-9940 • AUTHOR",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MeldColors.TextSecondary
                                )
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MeldColors.CoralOrange, modifier = Modifier.size(16.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f * wipe.value)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MeldColors.InkBlack),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "SANITIZED",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MeldColors.CoralOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.56f * wipe.value)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MeldColors.InkBlack)
                            )
                        }
                    }
                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MeldColors.InkBlack
                    )
                }
            }
        }

        MeldToolId.COLLAGE -> {
            val snap = remember { Animatable(if (reduceMotion) 1f else 0.90f) }
            LaunchedEffect(tool) {
                if (!reduceMotion) {
                    snap.snapTo(0.90f)
                    snap.animateTo(1f, tween(240, easing = MeldMotion.EaseOut))
                }
            }
            TactileCard(
                backgroundColor = MeldColors.SoftLavender,
                cornerRadius = MeldColors.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .graphicsLayer {
                                scaleX = snap.value
                                scaleY = snap.value
                            },
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(MeldColors.CoralOrange, MeldColors.ButterYellow).forEach { c ->
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MeldColors.PureWhite)
                                )
                            }
                        }
                    }
                    Text(tool.subtitle, style = MaterialTheme.typography.titleMedium, color = MeldColors.InkBlack)
                }
            }
        }

        MeldToolId.PDF_MERGE, MeldToolId.PACK_ZIP -> {
            val merge = remember { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(tool) {
                if (!reduceMotion) {
                    merge.snapTo(0f)
                    merge.animateTo(1f, tween(280, easing = MeldMotion.EaseOut))
                }
            }
            TactileCard(
                backgroundColor = MeldColors.PeriwinkleBlue,
                cornerRadius = MeldColors.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val gap = lerp(24f, 6f, merge.value).dp
                        Surface(
                            modifier = Modifier.size(54.dp, 68.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MeldColors.PureWhite,
                            border = BorderStroke(2.dp, MeldColors.InkBlack)
                        ) {}
                        Spacer(modifier = Modifier.width(gap))
                        Surface(
                            modifier = Modifier.size(54.dp, 68.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (tool == MeldToolId.PACK_ZIP) MeldColors.CoralOrange else MeldColors.ButterYellow,
                            border = BorderStroke(2.dp, MeldColors.InkBlack)
                        ) {
                            if (tool == MeldToolId.PACK_ZIP) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Archive, contentDescription = null, tint = MeldColors.InkBlack)
                                }
                            }
                        }
                    }
                    Text(tool.subtitle, style = MaterialTheme.typography.titleMedium, color = MeldColors.InkBlack)
                }
            }
        }

        else -> {
            // All Format Conversions (PDF -> PNG/JPG/MD/HTML/DOCX/TXT, Image formats, Table conversions)
            val cardColor = when (tool.outputExtension) {
                "pdf" -> MeldColors.PeriwinkleBlue
                "png", "jpg", "webp", "bmp", "tiff" -> MeldColors.ButterYellow
                "docx", "md", "html", "txt" -> MeldColors.MintSage
                else -> MeldColors.SoftLavender
            }

            val arrowShift by if (reduceMotion) {
                remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            } else {
                val infinite = rememberInfiniteTransition(label = "arrowPulse")
                infinite.animateFloat(
                    initialValue = -2f,
                    targetValue = 3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(750, easing = MeldMotion.EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "arrowSlide"
                )
            }
            val density = LocalDensity.current
            val arrowPx = with(density) { arrowShift.dp.toPx() }

            TactileCard(
                backgroundColor = cardColor,
                cornerRadius = MeldColors.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MeldColors.PureWhite,
                            border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
                        ) {
                            Text(
                                text = sourceExt,
                                style = MaterialTheme.typography.titleMedium,
                                color = MeldColors.InkBlack,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MeldColors.InkBlack,
                            modifier = Modifier.graphicsLayer { translationX = arrowPx }
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MeldColors.InkBlack,
                            border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
                        ) {
                            Text(
                                text = tool.outputExtension.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MeldColors.PureWhite,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MeldColors.InkBlack
                    )
                    Text(
                        text = "Processed cleanly offline right on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeldColors.InkBlack.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedTrackRow(
    label: String,
    progress: Float,
    barColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MeldColors.InkBlack,
            modifier = Modifier.width(38.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(CircleShape)
                .background(MeldColors.WarmSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}
