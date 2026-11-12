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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val StageSand = Color(0xFFEFE8DA)
private val StageDarkLine = Color(0xFFD8D2C4)
private val StageLaserRed = Color(0xFFC9400F)
private val StageWordBlue = Color(0xFF5B8CFF)
private val StageLimeCode = Color(0xFFC6F432)
private val StagePinkCode = Color(0xFFFF5CA8)

@Composable
private fun rememberLoopPhase(durationMs: Int = 3200): Float {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) return 0.55f
    val infinite = rememberInfiniteTransition(label = "meldToolStageLoop")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stagePhase"
    )
    return phase
}

/** Normalized segment helper mapped smoothly into 0f..1f for timeline keyframes. */
private fun seg(phase: Float, start: Float, end: Float): Float =
    ((phase - start) / (end - start)).coerceIn(0f, 1f)

/** Recessed Warm-Sand kinetic stage container shared across tool motion previews. */
@Composable
fun MeldStageViewport(
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
    backgroundColor: Color = StageSand,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.8.dp, MeldColors.InkBlack),
        content = { Box(modifier = Modifier.fillMaxSize(), content = content) }
    )
}

/** Reusable crisp miniature paper sheet glyph styled after ToolMotion.dc.html `.s`. */
@Composable
private fun MiniSheetGlyph(
    modifier: Modifier = Modifier,
    accentHeader: Color = MeldColors.InkBlack,
    lineFractions: List<Float> = listOf(1f, 1f, 1f, 1f),
    badgeText: String? = null,
    badgeColor: Color = MeldColors.CoralOrange
) {
    Surface(
        modifier = modifier.size(width = 58.dp, height = 74.dp),
        shape = RoundedCornerShape(7.dp),
        color = MeldColors.PureWhite,
        border = BorderStroke(1.8.dp, MeldColors.InkBlack)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentHeader)
                )
                lineFractions.forEach { frac ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac.coerceIn(0.25f, 1f))
                            .height(3.dp)
                            .background(StageDarkLine)
                    )
                }
            }
            if (badgeText != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = badgeColor,
                    border = BorderStroke(1.2.dp, MeldColors.InkBlack)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeldColors.InkBlack,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/** Miniature sunlit sky + sun + mountain Polaroid illustration used by photo/image stages. */
@Composable
private fun MiniPolaroidGlyph(
    modifier: Modifier = Modifier,
    formatBadge: String? = null,
    badgeColor: Color = MeldColors.CoralOrange
) {
    Box(modifier = modifier.size(width = 76.dp, height = 92.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 66.dp, height = 84.dp),
            shape = RoundedCornerShape(8.dp),
            color = MeldColors.PureWhite,
            border = BorderStroke(1.8.dp, MeldColors.InkBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.2.dp, MeldColors.InkBlack, RoundedCornerShape(4.dp))
                    .background(MeldColors.PeriwinkleBlue)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = MeldColors.ButterYellow,
                        radius = size.minDimension * 0.16f,
                        center = Offset(size.width * 0.25f, size.height * 0.26f)
                    )
                    val hill = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.12f, size.height)
                        lineTo(size.width * 0.56f, size.height * 0.42f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path = hill, color = MeldColors.MintSage)
                }
            }
        }
        if (formatBadge != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { rotationZ = 8f },
                shape = RoundedCornerShape(7.dp),
                color = badgeColor,
                border = BorderStroke(1.5.dp, MeldColors.InkBlack)
            ) {
                Text(
                    text = formatBadge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MeldColors.InkBlack,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** Opposing compression indicator badge showing live % reduction. */
@Composable
fun CompressionRatioPill(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val clamped = ratio.coerceIn(0.10f, 1.0f)
    val animatedGap by animateFloatAsState(
        targetValue = if (reduceMotion) 0.5f else clamped,
        animationSpec = tween(durationMillis = MeldMotion.UI_MS, easing = MeldMotion.EaseOut),
        label = "compressGap"
    )
    val pctSaved = ((1f - clamped) * 100f).roundToInt().coerceIn(0, 95)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MeldColors.PureWhite,
        border = BorderStroke(1.6.dp, MeldColors.InkBlack)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val centerY = size.height / 2f
                val stroke = 2.dp.toPx()
                val travel = 3.5.dp.toPx() * (1f - animatedGap)
                drawLine(
                    color = MeldColors.CoralOrange,
                    start = Offset(2.dp.toPx(), centerY),
                    end = Offset(size.width - 2.dp.toPx(), centerY),
                    strokeWidth = stroke
                )
                drawLine(
                    color = MeldColors.InkBlack,
                    start = Offset(size.width / 2f, 1.dp.toPx()),
                    end = Offset(size.width / 2f, centerY - 4.dp.toPx() + travel),
                    strokeWidth = stroke
                )
                drawLine(
                    color = MeldColors.InkBlack,
                    start = Offset(size.width / 2f, size.height - 1.dp.toPx()),
                    end = Offset(size.width / 2f, centerY + 4.dp.toPx() - travel),
                    strokeWidth = stroke
                )
            }
            Text(
                text = "-$pctSaved%",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MeldColors.InkBlack,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 1. COMPRESS & COMPRESS_PDF: Opposing press plates physically clamp the document sheet
 * while the pill badge below swaps from current KB (white) to target KB (Mint Sage).
 */
@Composable
fun CompressPlatesStage(
    sourceBytes: Long,
    targetKb: Int,
    modifier: Modifier = Modifier
) {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val clampIn = seg(phase, 0.18f, 0.42f)
    val releaseOut = seg(phase, 0.84f, 0.98f)
    val clamp = if (reduceMotion) 1f else (clampIn * (1f - releaseOut))
    val swapped = if (reduceMotion) true else phase in 0.36f..0.88f

    val ratio = (targetKb * 1024f / max(1024f, sourceBytes.toFloat())).coerceIn(0.18f, 0.96f)
    val targetScaleX = lerp(0.56f, 0.84f, (ratio - 0.18f) / 0.78f)

    MeldStageViewport(modifier = modifier, height = 140.dp) {
        val travelPx = with(density) { 46.dp.toPx() * clamp }

        // Left squeeze plate (`cpl`)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 52.dp)
                .width(8.dp)
                .height(78.dp)
                .graphicsLayer { translationX = travelPx }
                .clip(RoundedCornerShape(4.dp))
                .background(MeldColors.InkBlack)
        )

        // Right squeeze plate (`cpr`)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 52.dp)
                .width(8.dp)
                .height(78.dp)
                .graphicsLayer { translationX = -travelPx }
                .clip(RoundedCornerShape(4.dp))
                .background(MeldColors.InkBlack)
        )

        // Squeezing document sheet (`cps`)
        MiniSheetGlyph(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-8).dp)
                .graphicsLayer {
                    scaleX = lerp(1f, targetScaleX, clamp)
                    scaleY = lerp(1f, 1.07f, clamp)
                }
        )

        // Swapping Size Pill below (`cpa` -> `cpb`)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            shape = CircleShape,
            color = if (swapped) MeldColors.MintSage else MeldColors.PureWhite,
            border = BorderStroke(1.6.dp, MeldColors.InkBlack)
        ) {
            Text(
                text = if (swapped) "$targetKb KB" else SelectedFileInfo.formatByteCount(sourceBytes),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum"
                ),
                color = MeldColors.InkBlack,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * 2. HANDOUT: Stepped frame drop (`hf1..hfN`) + sweeping ruled lecture lines (`hl1..hlN`)
 * that immediately re-choreograph across 2-up, 3-up, and 4-up modes.
 */
@Composable
fun AnimatedHandoutDiagram(
    pagesPerSheet: Int,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val phase = rememberLoopPhase(3200)
    val density = LocalDensity.current

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = MeldColors.SubCardCorner,
        modifier = modifier
            .fillMaxWidth()
            .height(175.dp)
    ) {
        if (pagesPerSheet <= 3) {
            val rows = pagesPerSheet.coerceIn(2, 3)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until rows) {
                    val dropStart = 0.06f + r * 0.11f
                    val dropT = if (reduceMotion) 1f else seg(phase, dropStart, dropStart + 0.16f)
                    val outT = if (reduceMotion) 0f else seg(phase, 0.88f, 0.98f)
                    val frameVis = dropT * (1f - outT)
                    val lineDraw = if (reduceMotion) 1f else seg(phase, dropStart + 0.10f, dropStart + 0.26f) * (1f - outT)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    alpha = frameVis
                                    translationY = with(density) { (-18.dp.toPx()) * (1f - frameVis) }
                                    val sc = 0.90f + 0.10f * frameVis
                                    scaleX = sc
                                    scaleY = sc
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = MeldColors.WarmSurface,
                            border = BorderStroke(1.5.dp, MeldColors.InkBlack)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(MeldColors.CoralOrange)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = lineDraw.coerceAtLeast(0.01f)
                                    alpha = lineDraw
                                },
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(StageDarkLine)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 4-up grid staggered drop
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0..1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0..1) {
                            val idx = r * 2 + c
                            val start = 0.05f + idx * 0.08f
                            val dropT = if (reduceMotion) 1f else seg(phase, start, start + 0.15f)
                            val outT = if (reduceMotion) 0f else seg(phase, 0.88f, 0.98f)
                            val vis = dropT * (1f - outT)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        alpha = vis
                                        translationY = with(density) { (-14.dp.toPx()) * (1f - vis) }
                                        val sc = 0.90f + 0.10f * vis
                                        scaleX = sc
                                        scaleY = sc
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = MeldColors.WarmSurface,
                                border = BorderStroke(1.5.dp, MeldColors.InkBlack)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(MeldColors.CoralOrange)
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
 * 3. SPLIT PDF: Dashed Coral cut sweeps down & slices the sheet in two (`-7°` / `+7°`),
 * above interactive tappable page pills P1..PN.
 */
@Composable
fun SplitPageSpreadStage(
    totalPages: Int,
    startPage: Int,
    endPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visiblePages = minOf(totalPages.coerceAtLeast(2), 6)
    val reduceMotion = rememberReduceMotion()
    val phase = rememberLoopPhase(3200)
    val density = LocalDensity.current

    val cutDraw = if (reduceMotion) 1f else seg(phase, 0.10f, 0.34f)
    val cutFade = if (reduceMotion) 0f else seg(phase, 0.38f, 0.48f)
    val splitOut = if (reduceMotion) 1f else (seg(phase, 0.36f, 0.58f) * (1f - seg(phase, 0.82f, 0.96f)))

    TactileCard(
        backgroundColor = MeldColors.PeriwinkleBlue,
        cornerRadius = MeldColors.CardCorner,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Laser Cut Visual Stage (`ToolMotion.dc.html` Split)
            MeldStageViewport(height = 116.dp) {
                val sepPx = with(density) { 18.dp.toPx() * splitOut }
                // Left half sheet (`sL`)
                MiniSheetGlyph(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = -sepPx - 2.dp.toPx()
                            rotationZ = -7f * splitOut
                        },
                    lineFractions = listOf(0.95f, 0.9f, 0.85f)
                )
                // Right half sheet (`sR`) peeking right
                MiniSheetGlyph(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = sepPx + 2.dp.toPx()
                            rotationZ = 7f * splitOut
                        },
                    accentHeader = MeldColors.CoralOrange,
                    lineFractions = listOf(0.8f, 0.9f, 0.7f)
                )
                // Sweeping dashed laser cut (`cut`)
                val dashPx = with(density) { 5.dp.toPx() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = size.width / 2f
                    val topY = 14.dp.toPx()
                    val fullLen = size.height - 28.dp.toPx()
                    val currentAlpha = (if (cutDraw > 0.02f) 1f - cutFade else 0f).coerceIn(0f, 1f)
                    if (currentAlpha > 0.01f) {
                        drawLine(
                            color = StageLaserRed.copy(alpha = currentAlpha),
                            start = Offset(x, topY),
                            end = Offset(x, topY + fullLen * cutDraw),
                            strokeWidth = 2.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx * 0.7f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "EXTRACT RANGE",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.InkBlack.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Pages $startPage to $endPage",
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                color = MeldColors.InkBlack
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (page in 1..visiblePages) {
                    val selected = page in startPage..endPage
                    val scale by animateFloatAsState(
                        targetValue = if (reduceMotion || !selected) 1f else 1.04f,
                        animationSpec = tween(durationMillis = MeldMotion.UI_MS, easing = MeldMotion.EaseOut),
                        label = "pageScale"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPage(page) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MeldColors.PureWhite else MeldColors.PureWhite.copy(alpha = 0.45f),
                        border = BorderStroke(
                            width = if (selected) 2.2.dp else 1.2.dp,
                            color = if (selected) MeldColors.InkBlack else MeldColors.InkBlack.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) MeldColors.CoralOrange else MeldColors.InkBlack.copy(alpha = 0.2f))
                            )
                            Text(
                                text = "P$page",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) MeldColors.InkBlack else MeldColors.InkBlack.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. LOCK_STAMP: Bordered watermark stamp slams down onto sheet, triggers a 3-step micro-shake,
 * and pops a Coral padlock badge onto the corner (`ToolMotion.dc.html` Lock / Stamp).
 */
@Composable
fun StampSlamPreview(
    stampText: String,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val phase = rememberLoopPhase(3200)
    val keyBounce = remember { Animatable(1f) }
    val density = LocalDensity.current

    LaunchedEffect(stampText) {
        if (!reduceMotion) {
            keyBounce.snapTo(1.18f)
            keyBounce.animateTo(1f, tween(220, easing = MeldMotion.EaseOvershoot))
        }
    }

    val slamIn = if (reduceMotion) 1f else seg(phase, 0.16f, 0.30f)
    val slamFade = if (reduceMotion) 0f else seg(phase, 0.90f, 0.98f)
    val stampAlpha = (slamIn * (1f - slamFade)).coerceIn(0f, 1f)
    val stampScale = if (reduceMotion) 1f else lerp(1.75f, 1.0f, slamIn) * keyBounce.value

    // Micro-shake right at impact (`0.29..0.36`)
    val shakePx = if (!reduceMotion && phase in 0.29f..0.36f) {
        val dir = if (((phase * 1000).toInt() % 2) == 0) 1f else -1f
        with(density) { 2.5.dp.toPx() * dir }
    } else 0f

    val lockVis = if (reduceMotion) 1f else (seg(phase, 0.48f, 0.60f) * (1f - seg(phase, 0.88f, 0.98f)))

    TactileCard(
        backgroundColor = MeldColors.PeachCream,
        cornerRadius = MeldColors.CardCorner,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            MeldStageViewport(height = 148.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = shakePx
                            translationY = shakePx * 0.6f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MiniSheetGlyph(
                        modifier = Modifier.size(width = 88.dp, height = 108.dp)
                    )

                    Surface(
                        modifier = Modifier.graphicsLayer {
                            scaleX = stampScale
                            scaleY = stampScale
                            rotationZ = -14f
                            alpha = stampAlpha
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MeldColors.PureWhite.copy(alpha = 0.88f),
                        border = BorderStroke(2.2.dp, StageLaserRed)
                    ) {
                        Text(
                            text = stampText.ifBlank { "CONFIDENTIAL" }.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            color = StageLaserRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    // Lock badge popping onto upper-right sheet corner
                    Surface(
                        modifier = Modifier
                            .offset(x = 38.dp, y = (-42).dp)
                            .size(34.dp)
                            .graphicsLayer {
                                val sc = 0.90f + 0.10f * lockVis
                                scaleX = sc
                                scaleY = sc
                                alpha = lockVis.coerceIn(0f, 1f)
                            },
                        shape = CircleShape,
                        color = MeldColors.CoralOrange,
                        border = BorderStroke(1.8.dp, MeldColors.InkBlack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MeldColors.InkBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 5. REMOVE_BG: Checkerboard alpha stage with horizontal ink curtain sweep. */
@Composable
fun RemoveBgCurtainStage(
    tolerance: Int,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val targetSweep = ((tolerance - 15) / 80f).coerceIn(0.20f, 0.95f)
    val animatedSweep by animateFloatAsState(
        targetValue = if (reduceMotion) targetSweep else targetSweep,
        animationSpec = tween(durationMillis = MeldMotion.UI_MS, easing = MeldMotion.EaseOut),
        label = "curtainSweep"
    )
    val phase = rememberLoopPhase(3200)
    val pulse = if (reduceMotion) 1f else 0.96f + 0.04f * sin(phase * 2f * PI.toFloat())

    MeldStageViewport(
        modifier = modifier,
        height = 112.dp,
        backgroundColor = MeldColors.PureWhite
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sq = 11.dp.toPx()
            val cols = (size.width / sq).toInt() + 1
            val rows = (size.height / sq).toInt() + 1
            for (r in 0..rows) {
                for (c in 0..cols) {
                    if ((r + c) % 2 == 0) {
                        drawRect(
                            color = MeldColors.WarmSurface,
                            topLeft = Offset(c * sq, r * sq),
                            size = Size(sq, sq)
                        )
                    }
                }
            }
            val splitX = size.width * (1f - animatedSweep)
            if (splitX > 2f) {
                drawRect(
                    color = MeldColors.DockInk,
                    topLeft = Offset.Zero,
                    size = Size(splitX, size.height)
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(54.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
            shape = RoundedCornerShape(14.dp),
            color = MeldColors.CoralOrange,
            border = BorderStroke(2.2.dp, MeldColors.InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "α",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MeldColors.InkBlack
                )
            }
        }
    }
}

/** 6. RESIZE_IMAGE: Live aspect scale + dark studio border frame toggle inside warm-sand stage. */
@Composable
fun ResizeFrameStage(
    percent: Int,
    addFrame: Boolean,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val norm = ((percent - 25) / 175f).coerceIn(0f, 1f)
    val frameScale by animateFloatAsState(
        targetValue = 0.42f + 0.50f * norm,
        animationSpec = tween(durationMillis = MeldMotion.UI_MS, easing = MeldMotion.EaseOut),
        label = "resizeScale"
    )
    val borderWidth by animateFloatAsState(
        targetValue = if (addFrame) 6f else 2f,
        animationSpec = tween(durationMillis = MeldMotion.PRESS_MS, easing = MeldMotion.EaseOut),
        label = "resizeFrameStroke"
    )
    val density = LocalDensity.current

    MeldStageViewport(modifier = modifier, height = 108.dp) {
        val dashPx = with(density) { 5.dp.toPx() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxW = size.width * 0.56f
            val boxH = size.height * 0.68f
            val left = (size.width - boxW) / 2f
            val top = (size.height - boxH) / 2f
            drawRoundRect(
                color = MeldColors.InkBlack.copy(alpha = 0.45f),
                topLeft = Offset(left, top),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
                )
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.56f)
                .fillMaxHeight(0.68f)
                .graphicsLayer {
                    val s = if (reduceMotion) 0.42f + 0.50f * norm else frameScale
                    scaleX = s
                    scaleY = s
                },
            shape = RoundedCornerShape(10.dp),
            color = MeldColors.CoralOrange,
            border = BorderStroke(borderWidth.dp, MeldColors.InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$percent%",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                    color = MeldColors.InkBlack
                )
            }
        }
    }
}

/** 7. SCAN_QR: 21x21 live matrix with vertical scanner laser & subtle coral finder pulse. */
@Composable
fun QrMatrixStage(
    payload: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(payload) { ImageEngine.buildQrMatrix21(payload.ifEmpty { "meld.app" }) }
    val reduceMotion = rememberReduceMotion()
    val reveal = remember(payload) { Animatable(if (reduceMotion) 1f else 0.15f) }
    val phase = rememberLoopPhase(2600)

    LaunchedEffect(payload, reduceMotion) {
        if (reduceMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0.15f)
            reveal.animateTo(1f, tween(durationMillis = 320, easing = MeldMotion.EaseOut))
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
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            TactileCard(
                backgroundColor = MeldColors.PureWhite,
                cornerRadius = MeldColors.SubCardCorner,
                modifier = Modifier.size(168.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        val cell = size.width / 21f
                        val sweep = reveal.value
                        val pulseScale = if (reduceMotion) 1f else 0.92f + 0.08f * sin(phase * 2f * PI.toFloat())
                        for (r in 0 until 21) {
                            for (c in 0 until 21) {
                                if (matrix[r][c]) {
                                    val threshold = (r + c) / 42f
                                    if (threshold <= sweep) {
                                        val isFinder = (r in 2..4 && c in 2..4) ||
                                            (r in 2..4 && c in 16..18) ||
                                            (r in 16..18 && c in 2..4)
                                        val s = if (isFinder) cell * 0.92f * pulseScale else cell * 0.92f
                                        drawRect(
                                            color = if (isFinder) MeldColors.CoralOrange else MeldColors.InkBlack,
                                            topLeft = Offset(c * cell + (cell - s) / 2f, r * cell + (cell - s) / 2f),
                                            size = Size(s, s)
                                        )
                                    }
                                }
                            }
                        }
                        if (!reduceMotion) {
                            val scanY = size.height * phase
                            drawLine(
                                color = MeldColors.CoralOrange.copy(alpha = 0.65f),
                                start = Offset(0f, scanY),
                                end = Offset(size.width, scanY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Master animated stage dispatcher covering EVERY remaining tool in `MeldToolId`
 * (zero generic fallbacks!).
 */
@Composable
fun SpecializedToolShowcaseCard(
    tool: MeldToolId,
    file: SelectedFileInfo?,
    modifier: Modifier = Modifier
) {
    val sourceExt = (file?.extension?.takeIf { it.isNotBlank() } ?: "FILE").uppercase()
    val cardColor = when (tool.outputExtension) {
        "pdf" -> MeldColors.PeriwinkleBlue
        "png", "jpg", "webp", "bmp", "tiff" -> MeldColors.ButterYellow
        "docx" -> MeldColors.PeriwinkleBlue
        "md", "html", "txt", "json" -> MeldColors.MintSage
        else -> MeldColors.SoftLavender
    }

    TactileCard(
        backgroundColor = cardColor,
        cornerRadius = MeldColors.CardCorner,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (tool) {
                MeldToolId.VISUALIZE -> AudioWaveformStage()
                MeldToolId.SPEED_2X -> SpeedRaceStage()
                MeldToolId.REDACT -> RedactSweepStage()
                MeldToolId.STRIP_PDF_META -> SanitizeScanStage(isExif = false)
                MeldToolId.STRIP_EXIF -> SanitizeScanStage(isExif = true)
                MeldToolId.COLLAGE -> CollageGridAssembleStage()
                MeldToolId.PDF_MERGE -> MergeBookletStage()
                MeldToolId.PACK_ZIP -> ZipPackStage()
                MeldToolId.PDF_TO_DOCX -> SheetFlipWordStage()
                MeldToolId.PDF_TO_MD -> SheetToCodeTerminalStage(tag = "#", accentColor = StageLimeCode)
                MeldToolId.PDF_TO_HTML -> SheetToCodeTerminalStage(tag = "</>", accentColor = MeldColors.PeriwinkleBlue)
                MeldToolId.PDF_TO_TXT -> SheetToCodeTerminalStage(tag = "Aa", accentColor = MeldColors.MintSage)
                MeldToolId.PDF_TO_PNG, MeldToolId.PDF_TO_JPG,
                MeldToolId.IMG_TO_JPG, MeldToolId.IMG_TO_PNG,
                MeldToolId.IMG_TO_WEBP, MeldToolId.IMG_TO_BMP,
                MeldToolId.IMG_TO_TIFF -> PolaroidFormatMorphStage(
                    sourceExt = sourceExt,
                    targetExt = tool.outputExtension.uppercase()
                )
                MeldToolId.IMG_TO_PDF -> PhotoToPdfStackStage(sourceExt = sourceExt)
                MeldToolId.SHEET_TO_PDF -> TableTransformStage(targetExt = "PDF", badgeColor = MeldColors.CoralOrange)
                MeldToolId.SHEET_TO_MD -> TableTransformStage(targetExt = "# MD", badgeColor = StageLimeCode)
                MeldToolId.TABLE_CONVERT -> TableTransformStage(targetExt = "{JSON}", badgeColor = MeldColors.ButterYellow)
                else -> PolaroidFormatMorphStage(sourceExt = sourceExt, targetExt = tool.outputExtension.uppercase())
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MeldColors.InkBlack
                )
                Text(
                    text = "Runs completely offline on your phone with studio print precision.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.InkBlack.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** 8. REDACT: Sequential left-to-right ink blackout bars over sensitive lines. */
@Composable
private fun RedactSweepStage() {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val reset = if (reduceMotion) 0f else seg(phase, 0.88f, 0.98f)
    val b1 = if (reduceMotion) 1f else seg(phase, 0.14f, 0.30f) * (1f - reset)
    val b2 = if (reduceMotion) 1f else seg(phase, 0.28f, 0.44f) * (1f - reset)
    val b3 = if (reduceMotion) 1f else seg(phase, 0.42f, 0.58f) * (1f - reset)

    MeldStageViewport(height = 144.dp) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 88.dp, height = 108.dp),
            shape = RoundedCornerShape(10.dp),
            color = MeldColors.PureWhite,
            border = BorderStroke(1.8.dp, MeldColors.InkBlack)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MeldColors.InkBlack)
                )
                listOf(b1, b2, b3).forEachIndexed { idx, sweep ->
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (idx == 2) 0.72f else 0.92f)
                                .height(4.dp)
                                .align(Alignment.CenterStart)
                                .background(StageDarkLine)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (idx == 2) 0.76f else 1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = sweep.coerceAtLeast(0.001f)
                                }
                                .clip(RoundedCornerShape(3.dp))
                                .background(MeldColors.InkBlack)
                        )
                    }
                }
            }
        }
    }
}

/** 9. SANITIZE (`STRIP_PDF_META` / `STRIP_EXIF`): Laser beam sweeps down, pill tags peel away & vanish. */
@Composable
private fun SanitizeScanStage(isExif: Boolean) {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val labels = if (isExif) listOf("camera", "gps", "exif") else listOf("author", "producer", "xmp")
    val density = LocalDensity.current

    val scanSweep = if (reduceMotion) 0.5f else seg(phase, 0.12f, 0.48f)
    val scanVisible = if (reduceMotion) true else phase in 0.12f..0.52f

    MeldStageViewport(height = 148.dp) {
        if (isExif) {
            MiniPolaroidGlyph(modifier = Modifier.align(Alignment.Center))
        } else {
            MiniSheetGlyph(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 74.dp, height = 96.dp)
            )
        }

        // Laser scan bar (`scn`)
        if (scanVisible) {
            val topOff = with(density) { (18.dp.toPx() + 106.dp.toPx() * scanSweep).roundToInt() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, topOff) }
                    .width(96.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MeldColors.CoralOrange)
            )
        }

        // Metadata pills peeling outward (`tg1`, `tg2`, `tg3`)
        val tagConfigs = listOf(
            Triple(Alignment.TopStart, Offset(-22f, -14f), -12f),
            Triple(Alignment.CenterEnd, Offset(22f, -8f), 12f),
            Triple(Alignment.BottomStart, Offset(-16f, 16f), 10f)
        )
        labels.forEachIndexed { idx, label ->
            val start = 0.44f + idx * 0.05f
            val peelT = if (reduceMotion) 0.35f else seg(phase, start, start + 0.22f)
            val resetT = if (reduceMotion) 0f else seg(phase, 0.90f, 0.99f)
            val away = peelT * (1f - resetT)
            val (align, vec, rot) = tagConfigs[idx]
            val padModifier = when (idx) {
                0 -> Modifier.padding(start = 24.dp, top = 26.dp)
                1 -> Modifier.padding(end = 20.dp)
                else -> Modifier.padding(start = 34.dp, bottom = 22.dp)
            }
            Surface(
                modifier = Modifier
                    .align(align)
                    .then(padModifier)
                    .graphicsLayer {
                        translationX = with(density) { vec.x.dp.toPx() * away }
                        translationY = with(density) { vec.y.dp.toPx() * away }
                        rotationZ = rot * away
                        alpha = (1f - away).coerceIn(0f, 1f)
                    },
                shape = CircleShape,
                color = MeldColors.PureWhite,
                border = BorderStroke(1.5.dp, MeldColors.InkBlack)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeldColors.InkBlack,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** 10. PDF_MERGE: Three sheets converge from left/top/right, ink spine draws down, stack tilts. */
@Composable
private fun MergeBookletStage() {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val inT = if (reduceMotion) 1f else seg(phase, 0.08f, 0.36f)
    val outT = if (reduceMotion) 0f else seg(phase, 0.84f, 0.98f)
    val join = inT * (1f - outT)
    val spineDraw = if (reduceMotion) 1f else (seg(phase, 0.44f, 0.56f) * (1f - outT))
    val tiltT = if (reduceMotion) 1f else (seg(phase, 0.56f, 0.68f) * (1f - outT))

    MeldStageViewport(height = 148.dp) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -4f * tiltT
                    val s = 1f + 0.08f * tiltT
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center
        ) {
            MiniSheetGlyph(
                modifier = Modifier.graphicsLayer {
                    translationX = with(density) { lerp(-72f, -6f, join).dp.toPx() }
                    rotationZ = lerp(-9f, -4f, join)
                }
            )
            MiniSheetGlyph(
                modifier = Modifier.graphicsLayer {
                    translationX = with(density) { lerp(72f, 6f, join).dp.toPx() }
                    rotationZ = lerp(9f, 4f, join)
                },
                accentHeader = MeldColors.CoralOrange
            )
            MiniSheetGlyph(
                modifier = Modifier.graphicsLayer {
                    translationY = with(density) { lerp(-24f, 0f, join).dp.toPx() }
                    alpha = join.coerceAtLeast(0.25f)
                }
            )
            // Ink binding spine (`sp`)
            Box(
                modifier = Modifier
                    .offset(x = (-28).dp)
                    .width(5.dp)
                    .height(74.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleY = spineDraw.coerceAtLeast(0.001f)
                        alpha = spineDraw
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(MeldColors.InkBlack)
            )
        }
    }
}

/** 11. PDF_TO_PNG / PDF_TO_JPG / IMG_TO_*: Flash -> sheet becomes framed Polaroid + Format Sticker. */
@Composable
private fun PolaroidFormatMorphStage(
    sourceExt: String,
    targetExt: String
) {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val reset = if (reduceMotion) 0f else seg(phase, 0.90f, 0.99f)
    val polaroidIn = if (reduceMotion) 1f else (seg(phase, 0.28f, 0.42f) * (1f - reset))
    val sheetVis = if (reduceMotion) 0f else (1f - seg(phase, 0.28f, 0.38f)).coerceAtLeast(reset)
    val flashAlpha = if (reduceMotion || phase !in 0.24f..0.38f) 0f else sin(((phase - 0.24f) / 0.14f) * PI.toFloat()) * 0.85f
    val stickerIn = if (reduceMotion) 1f else (seg(phase, 0.48f, 0.62f) * (1f - reset))

    MeldStageViewport(height = 148.dp) {
        if (sheetVis > 0.02f) {
            MiniSheetGlyph(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = sheetVis
                        val sc = 0.85f + 0.15f * sheetVis
                        scaleX = sc
                        scaleY = sc
                    },
                badgeText = sourceExt.take(4)
            )
        }

        if (polaroidIn > 0.02f) {
            MiniPolaroidGlyph(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = polaroidIn
                        val sc = 0.78f + 0.22f * polaroidIn
                        scaleX = sc
                        scaleY = sc
                        rotationZ = lerp(-8f, 3f, polaroidIn)
                    }
            )
        }

        if (stickerIn > 0.02f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 34.dp, y = (-34).dp)
                    .graphicsLayer {
                        val sc = lerp(0.82f, 1.0f, stickerIn)
                        scaleX = sc
                        scaleY = sc
                        rotationZ = 8f
                        alpha = stickerIn
                    },
                shape = RoundedCornerShape(8.dp),
                color = MeldColors.CoralOrange,
                border = BorderStroke(1.8.dp, MeldColors.InkBlack)
            ) {
                Text(
                    text = targetExt,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MeldColors.InkBlack,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        if (flashAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }
    }
}

/** 12. IMG_TO_PDF: Polaroid card folds smoothly into crisp PDF sheet. */
@Composable
private fun PhotoToPdfStackStage(sourceExt: String) {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val morph = if (reduceMotion) 1f else (seg(phase, 0.22f, 0.44f) * (1f - seg(phase, 0.86f, 0.98f)))
    val density = LocalDensity.current

    MeldStageViewport(height = 148.dp) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniPolaroidGlyph(
                formatBadge = sourceExt.take(4),
                badgeColor = MeldColors.ButterYellow,
                modifier = Modifier.graphicsLayer {
                    translationX = with(density) { lerp(0f, -6.dp.toPx(), morph) }
                    rotationZ = -4f
                }
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MeldColors.InkBlack,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .graphicsLayer { scaleX = 0.85f + 0.15f * morph }
            )
            MiniSheetGlyph(
                modifier = Modifier
                    .size(width = 66.dp, height = 84.dp)
                    .graphicsLayer {
                        val sc = 0.90f + 0.10f * morph
                        scaleX = sc
                        scaleY = sc
                    },
                badgeText = "PDF",
                badgeColor = MeldColors.CoralOrange
            )
        }
    }
}

/** 13. PDF_TO_DOCX: True 3D Y-axis perspective card flip (`white PDF sheet` -> `blue W Word page`). */
@Composable
private fun SheetFlipWordStage() {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val flipOut = if (reduceMotion) 1f else seg(phase, 0.20f, 0.42f)
    val backIn = if (reduceMotion) 1f else seg(phase, 0.42f, 0.62f)
    val returnBack = if (reduceMotion) 0f else seg(phase, 0.86f, 0.98f)
    val showWord = if (reduceMotion) true else (phase in 0.42f..0.92f)

    val rotY = when {
        reduceMotion -> 0f
        phase < 0.42f -> lerp(0f, 90f, flipOut)
        phase < 0.86f -> lerp(-90f, 0f, backIn)
        else -> lerp(0f, -90f, returnBack)
    }

    MeldStageViewport(height = 148.dp) {
        if (!showWord) {
            MiniSheetGlyph(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 76.dp, height = 98.dp)
                    .graphicsLayer {
                        cameraDistance = 12f * density.density
                        rotationY = rotY
                    },
                badgeText = "PDF",
                badgeColor = MeldColors.CoralOrange
            )
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 76.dp, height = 98.dp)
                    .graphicsLayer {
                        cameraDistance = 12f * density.density
                        rotationY = rotY
                    },
                shape = RoundedCornerShape(10.dp),
                color = StageWordBlue,
                border = BorderStroke(2.dp, MeldColors.InkBlack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "W",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeldColors.InkBlack
                    )
                }
            }
        }
    }
}

/** 14. PDF_TO_MD / PDF_TO_HTML / PDF_TO_TXT: Mini-sheet -> Dark Code Card typing neon lines + syntax glyph. */
@Composable
private fun SheetToCodeTerminalStage(
    tag: String,
    accentColor: Color
) {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val reset = if (reduceMotion) 0f else seg(phase, 0.88f, 0.98f)
    val lineFracs = listOf(0.62f, 0.86f, 0.50f, 0.76f)
    val lineColors = listOf(StageLimeCode, MeldColors.PeriwinkleBlue, StagePinkCode, MeldColors.MintSage)

    MeldStageViewport(height = 148.dp) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniSheetGlyph()
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MeldColors.InkBlack,
                modifier = Modifier.size(20.dp)
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp),
                shape = RoundedCornerShape(10.dp),
                color = MeldColors.InkBlack
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth()
                    ) {
                        lineFracs.forEachIndexed { idx, w ->
                            val start = 0.08f + idx * 0.08f
                            val draw = if (reduceMotion) 1f else (seg(phase, start, start + 0.14f) * (1f - reset))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(w)
                                    .height(5.dp)
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        scaleX = draw.coerceAtLeast(0.01f)
                                    }
                                    .clip(CircleShape)
                                    .background(lineColors[idx])
                            )
                        }
                    }
                    Text(
                        text = tag,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = accentColor,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

/** 15. COLLAGE: Four tilted Polaroids glide smoothly into a 2x2 locked grid. */
@Composable
private fun CollageGridAssembleStage() {
    val phase = rememberLoopPhase(3200)
    val reduceMotion = rememberReduceMotion()
    val snap = if (reduceMotion) 1f else (seg(phase, 0.12f, 0.38f) * (1f - seg(phase, 0.86f, 0.98f)))
    val colors = listOf(MeldColors.ButterYellow, MeldColors.MintSage, MeldColors.PeriwinkleBlue, MeldColors.PeachCream)
    val tilts = listOf(-8f, 6f, 7f, -6f)

    MeldStageViewport(height = 148.dp) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .size(116.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (r in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in 0..1) {
                        val idx = r * 2 + c
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    rotationZ = lerp(tilts[idx], 0f, snap)
                                    val sc = lerp(0.84f, 1.0f, snap)
                                    scaleX = sc
                                    scaleY = sc
                                },
                            shape = RoundedCornerShape(7.dp),
                            color = colors[idx],
                            border = BorderStroke(1.6.dp, MeldColors.InkBlack)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 3.dp)
                                        .size(width = 14.dp, height = 4.dp)
                                        .background(MeldColors.CoralOrange)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 16. VISUALIZE: 18 pulsing studio equalizer gradient bars inside dark viewport. */
@Composable
private fun AudioWaveformStage() {
    val phase = rememberLoopPhase(1800)
    val reduceMotion = rememberReduceMotion()
    MeldStageViewport(height = 148.dp, backgroundColor = MeldColors.InkBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LIVE STUDIO WAVEFORM",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.ButterYellow
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val count = 18
                val gap = 4.dp.toPx()
                val barW = (size.width - gap * (count - 1)) / count
                val brush = Brush.verticalGradient(
                    listOf(MeldColors.ButterYellow, MeldColors.CoralOrange, MeldColors.SoftLavender)
                )
                for (i in 0 until count) {
                    val norm = if (reduceMotion) {
                        0.32f + 0.58f * abs(sin(i * 0.72f))
                    } else {
                        val wave = sin(phase * 2f * PI.toFloat() + i * 0.55f)
                        0.22f + 0.72f * (0.5f + 0.5f * wave)
                    }
                    val h = (size.height * norm).coerceAtLeast(10.dp.toPx())
                    val top = (size.height - h) / 2f
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(i * (barW + gap), top),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f)
                    )
                }
            }
            Text(
                text = "1920×600 PNG waveform render",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.PureWhite.copy(alpha = 0.75f)
            )
        }
    }
}

/** 17. SPEED_2X: Dual tempo playhead streams comparing 1.0x vs 2.0x throughput. */
@Composable
private fun SpeedRaceStage() {
    val phase = rememberLoopPhase(2400)
    val reduceMotion = rememberReduceMotion()
    val slow = if (reduceMotion) 0.45f else phase
    val fast = if (reduceMotion) 0.90f else (phase * 2f) % 1f

    MeldStageViewport(height = 144.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = MeldColors.InkBlack,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "2.0x DOUBLE TEMPO STREAM",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MeldColors.InkBlack
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeldColors.PureWhite,
                border = BorderStroke(1.6.dp, MeldColors.InkBlack),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(Triple("1.0x", slow, MeldColors.WarmSurface), Triple("2.0x", fast, MeldColors.CoralOrange))
                        .forEach { (label, frac, fill) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                    color = MeldColors.InkBlack,
                                    modifier = Modifier.width(34.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(CircleShape)
                                        .background(StageSand)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac.coerceIn(0.08f, 1f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(fill)
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

/** 18. SHEET_TO_PDF / SHEET_TO_MD / TABLE_CONVERT: 3x3 spreadsheet lights up & exports formatted card. */
@Composable
private fun TableTransformStage(
    targetExt: String,
    badgeColor: Color
) {
    val phase = rememberLoopPhase(2800)
    val reduceMotion = rememberReduceMotion()
    val activeRow = if (reduceMotion) 1 else ((phase * 3f).toInt() % 3)

    MeldStageViewport(height = 144.dp) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(width = 112.dp, height = 84.dp),
                shape = RoundedCornerShape(10.dp),
                color = MeldColors.PureWhite,
                border = BorderStroke(1.8.dp, MeldColors.InkBlack)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (r in 0..2) {
                        val lit = r == activeRow
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (lit) MeldColors.ButterYellow else StageSand)
                                        .border(1.dp, MeldColors.InkBlack.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MeldColors.InkBlack
            )

            Surface(
                modifier = Modifier.size(width = 76.dp, height = 64.dp),
                shape = RoundedCornerShape(12.dp),
                color = badgeColor,
                border = BorderStroke(1.8.dp, MeldColors.InkBlack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = targetExt,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeldColors.InkBlack
                    )
                }
            }
        }
    }
}

/** 19. PACK_ZIP: File pills drop into Lavender archive while interlocking zipper teeth clasp. */
@Composable
private fun ZipPackStage() {
    val phase = rememberLoopPhase(2800)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    MeldStageViewport(height = 148.dp) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple("PDF", MeldColors.CoralOrange, 0f),
                    Triple("PNG", MeldColors.ButterYellow, 0.14f),
                    Triple("TXT", MeldColors.MintSage, 0.28f)
                ).forEach { (ext, col, offset) ->
                    val pulse = if (reduceMotion) 0f else sin(((phase + offset) % 1f) * 2f * PI.toFloat())
                    Surface(
                        modifier = Modifier.graphicsLayer {
                            translationX = with(density) { (6.dp.toPx() * (0.5f + 0.5f * pulse)) }
                        },
                        shape = RoundedCornerShape(7.dp),
                        color = col,
                        border = BorderStroke(1.5.dp, MeldColors.InkBlack)
                    ) {
                        Text(
                            text = ext,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MeldColors.InkBlack,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MeldColors.InkBlack
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp),
                shape = RoundedCornerShape(14.dp),
                color = MeldColors.SoftLavender,
                border = BorderStroke(1.8.dp, MeldColors.InkBlack)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Alternating zipper teeth clasping vertically down the center
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val teeth = 6
                        val step = size.height / (teeth + 1)
                        val teethDrawn = if (reduceMotion) teeth else ((phase * (teeth + 2)).toInt()).coerceAtMost(teeth)
                        for (i in 0 until teethDrawn) {
                            val side = if (i % 2 == 0) -6.dp.toPx() else 2.dp.toPx()
                            drawRoundRect(
                                color = MeldColors.InkBlack,
                                topLeft = Offset(size.width * 0.24f + side, (i + 0.7f) * step),
                                size = Size(6.dp.toPx(), 4.dp.toPx()),
                                cornerRadius = CornerRadius(1.5.dp.toPx())
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 14.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(1.6.dp, MeldColors.InkBlack)
                    ) {
                        Text(
                            text = ".ZIP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MeldColors.InkBlack,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
