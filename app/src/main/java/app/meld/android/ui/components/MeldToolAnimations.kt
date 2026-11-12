package app.meld.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay
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

// Exact ToolMotion.dc.html S-curve & overshoot easing curves
private val TravelCurve = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val PopCurve = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Maps normalized progress within [start..end] through a non-linear easing curve. */
private fun seg(phase: Float, start: Float, end: Float): Float =
    ((phase - start) / (end - start)).coerceIn(0f, 1f)

private fun easeTravel(phase: Float, start: Float, end: Float): Float =
    TravelCurve.transform(seg(phase, start, end))

private fun easePop(phase: Float, start: Float, end: Float): Float =
    PopCurve.transform(seg(phase, start, end))

private fun easeOutSeg(phase: Float, start: Float, end: Float): Float =
    MeldMotion.EaseOut.transform(seg(phase, start, end))

private val LeftHalfClip = GenericShape { size, _ ->
    addRect(Rect(0f, 0f, size.width * 0.505f, size.height))
}

private val RightHalfClip = GenericShape { size, _ ->
    addRect(Rect(size.width * 0.495f, 0f, size.width, size.height))
}

/**
 * Driven 0f..1f cycle that resets cleanly whenever `replayKey` is bumped (tap-to-replay!).
 */
@Composable
private fun rememberLoopPhase(durationMs: Int = 3200, replayKey: Int = 0): Float {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) return 0.56f
    val anim = remember(replayKey, durationMs) { Animatable(0f) }
    LaunchedEffect(replayKey, durationMs) {
        while (true) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
            )
        }
    }
    return anim.value
}

/**
 * Recessed Warm-Sand kinetic stage container shared across all tool previews.
 * Supports tap-to-replay with tactile press physics so every tool stage feels interactive.
 */
@Composable
fun MeldStageViewport(
    modifier: Modifier = Modifier,
    height: Dp = 156.dp,
    backgroundColor: Color = StageSand,
    onTapReplay: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (!reduceMotion && onTapReplay != null && isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = MeldMotion.PRESS_MS, easing = MeldMotion.EaseOut),
        label = "stagePressScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (onTapReplay != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTapReplay()
                        }
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.8.dp, MeldColors.InkBlack),
        content = { Box(modifier = Modifier.fillMaxSize(), content = content) }
    )
}

/** Crisp miniature stationery sheet glyph matching ToolMotion.dc.html `.s`. */
@Composable
private fun MiniSheetGlyph(
    modifier: Modifier = Modifier,
    width: Dp = 58.dp,
    height: Dp = 74.dp,
    accentHeader: Color = MeldColors.InkBlack,
    lineFractions: List<Float> = listOf(1f, 1f, 1f, 1f),
    badgeText: String? = null,
    badgeColor: Color = MeldColors.CoralOrange
) {
    Surface(
        modifier = modifier.size(width = width, height = height),
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

/** Sunlit sky + golden sun + mint mountain Polaroid illustration. */
@Composable
private fun MiniPolaroidGlyph(
    modifier: Modifier = Modifier,
    formatBadge: String? = null,
    badgeColor: Color = MeldColors.CoralOrange
) {
    Box(modifier = modifier.size(width = 78.dp, height = 94.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 68.dp, height = 88.dp),
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
                    val hill = Path().apply {
                        moveTo(size.width * 0.08f, size.height)
                        lineTo(size.width * 0.56f, size.height * 0.38f)
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
 * 1. COMPRESS & COMPRESS_PDF (`ToolMotion.dc.html` Compress):
 * Plates start wide at ±76dp, travel inward on `TravelCurve` to clamp flush at the edges of the
 * squashed sheet (`scale(0.55x, 1.08y)` impact over-squash -> `0.60x, 1.04y` settle),
 * while the pill cross-dissolves from white source KB to Mint Sage target KB.
 */
@Composable
fun CompressPlatesStage(
    sourceBytes: Long,
    targetKb: Int,
    modifier: Modifier = Modifier
) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val ratio = (targetKb * 1024f / max(1024f, sourceBytes.toFloat())).coerceIn(0.18f, 0.96f)
    val settledScaleX = lerp(0.56f, 0.80f, (ratio - 0.18f) / 0.78f)
    val squashDipScaleX = (settledScaleX - 0.06f).coerceAtLeast(0.48f)

    // Keyframe matching ToolMotion.dc.html: 0..25% open -> 25..40% slam in -> 40..46% settle -> 85..100% release
    val plateNorm = when {
        reduceMotion -> 1f
        phase < 0.24f -> 0f
        phase < 0.42f -> easeTravel(phase, 0.24f, 0.42f)
        phase < 0.85f -> 1f
        else -> 1f - easeTravel(phase, 0.85f, 0.99f)
    }
    val sheetScaleX = when {
        reduceMotion -> settledScaleX
        phase < 0.24f -> 1f
        phase < 0.40f -> lerp(1f, squashDipScaleX, easeTravel(phase, 0.24f, 0.40f))
        phase < 0.48f -> lerp(squashDipScaleX, settledScaleX, easeOutSeg(phase, 0.40f, 0.48f))
        phase < 0.85f -> settledScaleX
        else -> lerp(settledScaleX, 1f, easeTravel(phase, 0.85f, 0.99f))
    }
    val sheetScaleY = when {
        reduceMotion -> 1.04f
        phase < 0.24f -> 1f
        phase < 0.40f -> lerp(1f, 1.09f, easeTravel(phase, 0.24f, 0.40f))
        phase < 0.48f -> lerp(1.09f, 1.04f, easeOutSeg(phase, 0.40f, 0.48f))
        phase < 0.85f -> 1.04f
        else -> lerp(1.04f, 1f, easeTravel(phase, 0.85f, 0.99f))
    }
    val pillSwap = when {
        reduceMotion -> 1f
        phase < 0.32f -> 0f
        phase < 0.40f -> easeOutSeg(phase, 0.32f, 0.40f)
        phase < 0.88f -> 1f
        else -> 1f - easeOutSeg(phase, 0.88f, 0.96f)
    }

    MeldStageViewport(modifier = modifier, height = 152.dp, onTapReplay = { replay++ }) {
        // Center-anchored plates track exact edge of the squeezed 58dp sheet
        val sheetHalfW = 29.dp * sheetScaleX
        val outerGap = lerp(46f, 6f, plateNorm).dp

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -(sheetHalfW + outerGap + 4.dp), y = (-10).dp)
                .width(8.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MeldColors.InkBlack)
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (sheetHalfW + outerGap + 4.dp), y = (-10).dp)
                .width(8.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MeldColors.InkBlack)
        )

        MiniSheetGlyph(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-10).dp)
                .graphicsLayer {
                    scaleX = sheetScaleX
                    scaleY = sheetScaleY
                }
        )

        // Swapping pills at bottom (cross-fade opacity + gentle scale settle)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - pillSwap).coerceIn(0f, 1f)
                    val s = 0.94f + 0.06f * (1f - pillSwap)
                    scaleX = s
                    scaleY = s
                },
                shape = CircleShape,
                color = MeldColors.PureWhite,
                border = BorderStroke(1.6.dp, MeldColors.InkBlack)
            ) {
                Text(
                    text = SelectedFileInfo.formatByteCount(sourceBytes),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                    color = MeldColors.InkBlack,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 3.dp)
                )
            }
            Surface(
                modifier = Modifier.graphicsLayer {
                    alpha = pillSwap.coerceIn(0f, 1f)
                    val s = 0.92f + 0.08f * pillSwap
                    scaleX = s
                    scaleY = s
                },
                shape = CircleShape,
                color = MeldColors.MintSage,
                border = BorderStroke(1.6.dp, MeldColors.InkBlack)
            ) {
                Text(
                    text = "$targetKb KB",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                    color = MeldColors.InkBlack,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * 2. HANDOUT (`ToolMotion.dc.html` Handout):
 * Spring-eased (`PopCurve`) frame drops from above + `TravelCurve` ruled note-line sweep draw
 * across 2-up, 3-up, and 4-up modes.
 */
@Composable
fun AnimatedHandoutDiagram(
    pagesPerSheet: Int,
    modifier: Modifier = Modifier
) {
    var replay by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()
    val phase = rememberLoopPhase(3200, replay)
    val density = LocalDensity.current

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = MeldColors.SubCardCorner,
        onClick = { replay++ },
        modifier = modifier
            .fillMaxWidth()
            .height(175.dp)
    ) {
        val outT = if (reduceMotion) 0f else easeTravel(phase, 0.88f, 0.98f)
        if (pagesPerSheet <= 3) {
            val rows = pagesPerSheet.coerceIn(2, 3)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until rows) {
                    val start = 0.06f + r * 0.10f
                    val dropPop = if (reduceMotion) 1f else easePop(phase, start, start + 0.15f)
                    val dropVis = if (reduceMotion) 1f else (seg(phase, start, start + 0.10f) * (1f - outT))
                    val dropY = with(density) { (-30.dp.toPx()) * (1f - dropPop) * (1f - outT) + (-24.dp.toPx() * outT) }
                    val lineDraw = if (reduceMotion) 1f else (easeTravel(phase, start + 0.10f, start + 0.25f) * (1f - outT))

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
                                    alpha = dropVis.coerceIn(0f, 1f)
                                    translationY = dropY
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = MeldColors.WarmSurface,
                            border = BorderStroke(1.5.dp, MeldColors.InkBlack)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(MeldColors.InkBlack)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = lineDraw.coerceAtLeast(0.005f)
                                    alpha = lineDraw.coerceIn(0f, 1f)
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
                            val dropPop = if (reduceMotion) 1f else easePop(phase, start, start + 0.14f)
                            val vis = if (reduceMotion) 1f else (seg(phase, start, start + 0.09f) * (1f - outT))
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        alpha = vis.coerceIn(0f, 1f)
                                        translationY = with(density) { (-20.dp.toPx()) * (1f - dropPop) }
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = MeldColors.WarmSurface,
                                border = BorderStroke(1.5.dp, MeldColors.InkBlack)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(MeldColors.InkBlack)
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
 * 3. SPLIT PDF (`ToolMotion.dc.html` Split):
 * True clipped left/right halves of ONE single sheet (`LeftHalfClip` + `RightHalfClip`):
 * glowing dashed red laser cuts vertically from top to bottom, then the two halves smoothly slice
 * apart (`-22dp / -8°` left, `+22dp / +8°` right) and spring back flush!
 */
@Composable
fun SplitPageSpreadStage(
    totalPages: Int,
    startPage: Int,
    endPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visiblePages = minOf(totalPages.coerceAtLeast(1), 6)
    var replay by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()
    val phase = rememberLoopPhase(3200, replay)
    val density = LocalDensity.current

    val cutDraw = if (reduceMotion) 1f else easeTravel(phase, 0.10f, 0.34f)
    val cutFade = if (reduceMotion) 0f else seg(phase, 0.38f, 0.46f)
    val apart = when {
        reduceMotion -> 0.85f
        phase < 0.36f -> 0f
        phase < 0.56f -> easeTravel(phase, 0.36f, 0.56f)
        phase < 0.84f -> 1f
        else -> 1f - easeTravel(phase, 0.84f, 0.98f)
    }

    TactileCard(
        backgroundColor = MeldColors.PeriwinkleBlue,
        cornerRadius = MeldColors.CardCorner,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            MeldStageViewport(height = 132.dp, onTapReplay = { replay++ }) {
                val slidePx = with(density) { 21.dp.toPx() * apart }

                // Left clipped half of the SAME sheet (`sL`)
                MiniSheetGlyph(
                    width = 64.dp,
                    height = 82.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = -slidePx
                            rotationZ = -8f * apart
                        }
                        .clip(LeftHalfClip)
                )

                // Right clipped half of the SAME sheet (`sR`)
                MiniSheetGlyph(
                    width = 64.dp,
                    height = 82.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = slidePx
                            rotationZ = 8f * apart
                        }
                        .clip(RightHalfClip)
                )

                // Top-to-bottom dashed coral-red cut seam (`cut`)
                val dashPx = with(density) { 5.dp.toPx() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = size.width / 2f
                    val topY = 16.dp.toPx()
                    val fullLen = size.height - 32.dp.toPx()
                    val cutAlpha = (if (cutDraw > 0.02f) 1f - cutFade else 0f).coerceIn(0f, 1f)
                    if (cutAlpha > 0.01f) {
                        drawLine(
                            color = StageLaserRed.copy(alpha = cutAlpha),
                            start = Offset(x, topY),
                            end = Offset(x, topY + fullLen * cutDraw),
                            strokeWidth = 2.4.dp.toPx(),
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
 * 4. LOCK_STAMP (`ToolMotion.dc.html` Lock / Stamp):
 * Heavy bordered stamp slams down from `scale(2.5)` -> `scale(0.92)` overshoot -> `1.0`,
 * shaking the entire sheet stage cleanly on impact before the Coral padlock circle pops on.
 */
@Composable
fun StampSlamPreview(
    stampText: String,
    modifier: Modifier = Modifier
) {
    var replay by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()
    val phase = rememberLoopPhase(3200, replay)
    val density = LocalDensity.current

    val fadeOutEnd = if (reduceMotion) 0f else easeOutSeg(phase, 0.90f, 0.98f)
    val slamT = if (reduceMotion) 1f else easePop(phase, 0.16f, 0.34f)
    val stampAlpha = if (reduceMotion) 1f else (seg(phase, 0.16f, 0.23f) * (1f - fadeOutEnd))
    val stampScale = if (reduceMotion) 1f else lerp(2.45f, 1.0f, slamT)

    // 4-step damped impact shake (`shk`: 29%..37%)
    val shakeX = if (!reduceMotion && phase in 0.28f..0.37f) {
        val step = ((phase - 0.28f) / 0.022f).toInt()
        when (step) {
            0 -> 3.5f
            1 -> -3.5f
            2 -> 2.0f
            else -> -1.5f
        }
    } else 0f
    val shakeY = if (!reduceMotion && phase in 0.28f..0.37f) shakeX * 0.65f else 0f

    val lockPop = if (reduceMotion) 1f else easePop(phase, 0.48f, 0.62f)
    val lockAlpha = if (reduceMotion) 1f else (seg(phase, 0.48f, 0.54f) * (1f - fadeOutEnd))

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
            MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = with(density) { shakeX.dp.toPx() }
                            translationY = with(density) { shakeY.dp.toPx() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MiniSheetGlyph(width = 76.dp, height = 98.dp)

                    Surface(
                        modifier = Modifier.graphicsLayer {
                            scaleX = stampScale
                            scaleY = stampScale
                            rotationZ = -18f
                            alpha = stampAlpha.coerceIn(0f, 1f)
                        },
                        shape = RoundedCornerShape(5.dp),
                        color = MeldColors.PureWhite.copy(alpha = 0.85f),
                        border = BorderStroke(2.2.dp, StageLaserRed)
                    ) {
                        Text(
                            text = stampText.ifBlank { "LOCKED" }.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = StageLaserRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    // Coral padlock popping onto corner (starts from 0.88f, never scale(0))
                    Surface(
                        modifier = Modifier
                            .offset(x = 34.dp, y = (-38).dp)
                            .size(34.dp)
                            .graphicsLayer {
                                val s = 0.88f + 0.12f * lockPop
                                scaleX = s
                                scaleY = s
                                alpha = lockAlpha.coerceIn(0f, 1f)
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

/** 5. REMOVE_BG: Checkerboard alpha wipe + breathing subject square. */
@Composable
fun RemoveBgCurtainStage(
    tolerance: Int,
    modifier: Modifier = Modifier
) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(2800, replay)
    val reduceMotion = rememberReduceMotion()
    val targetSweep = ((tolerance - 15) / 80f).coerceIn(0.22f, 0.95f)
    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = tween(durationMillis = MeldMotion.UI_MS, easing = MeldMotion.EaseOut),
        label = "curtainSweep"
    )
    val breathe = if (reduceMotion) 1f else 0.96f + 0.04f * sin(phase * 2f * PI.toFloat())

    MeldStageViewport(
        modifier = modifier,
        height = 120.dp,
        backgroundColor = MeldColors.PureWhite,
        onTapReplay = { replay++ }
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
                .size(56.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
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

/** 6. RESIZE_IMAGE: Live scaled frame with dashed crop bounds & dark border toggle. */
@Composable
fun ResizeFrameStage(
    percent: Int,
    addFrame: Boolean,
    modifier: Modifier = Modifier
) {
    var replay by remember { mutableIntStateOf(0) }
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

    MeldStageViewport(modifier = modifier, height = 114.dp, onTapReplay = { replay++ }) {
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

/** 7. SCAN_QR: 21x21 staggered ripple reveal + live vertical scanner sweep. */
@Composable
fun QrMatrixStage(
    payload: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(payload) { ImageEngine.buildQrMatrix21(payload.ifEmpty { "meld.app" }) }
    val reduceMotion = rememberReduceMotion()
    val reveal = remember(payload) { Animatable(if (reduceMotion) 1f else 0.15f) }
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(2600, replay)

    LaunchedEffect(payload, reduceMotion, replay) {
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
                onClick = { replay++ },
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
                                color = MeldColors.CoralOrange.copy(alpha = 0.72f),
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

/** Master animated stage dispatcher covering EVERY remaining tool in `MeldToolId`. */
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
                    text = "Runs completely offline on your phone · Tap stage to replay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.InkBlack.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** 8. REDACT (`ToolMotion.dc.html` Redact): Keeps top context visible while ink marker strokes whip over middle lines. */
@Composable
internal fun RedactSweepStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val reset = if (reduceMotion) 0f else easeTravel(phase, 0.88f, 0.98f)
    val b1 = if (reduceMotion) 1f else easeTravel(phase, 0.15f, 0.30f) * (1f - reset)
    val b2 = if (reduceMotion) 1f else easeTravel(phase, 0.27f, 0.42f) * (1f - reset)

    MeldStageViewport(height = 152.dp, onTapReplay = { replay++ }) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 74.dp, height = 96.dp),
            shape = RoundedCornerShape(8.dp),
            color = MeldColors.PureWhite,
            border = BorderStroke(1.8.dp, MeldColors.InkBlack)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MeldColors.InkBlack)
                )
                // Context line 1 stays intact
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(3.5.dp)
                        .background(StageDarkLine)
                )
                // Sensitive lines 2 & 3 get whipped black by blackout bars
                listOf(b1, b2).forEach { sweep ->
                    Box(modifier = Modifier.fillMaxWidth().height(9.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .height(3.5.dp)
                                .align(Alignment.CenterStart)
                                .background(StageDarkLine)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = sweep.coerceAtLeast(0.001f)
                                }
                                .clip(RoundedCornerShape(2.dp))
                                .background(MeldColors.InkBlack)
                        )
                    }
                }
                // Footer context line stays clean
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(3.5.dp)
                        .background(StageDarkLine)
                )
            }
        }
    }
}

/**
 * 9. SANITIZE (`ToolMotion.dc.html` Sanitize):
 * Coral laser scan beam (`scn`) with warm glow trail sweeps top-to-bottom across sheet/Polaroid;
 * once scanned, metadata pills (`author`/`producer`/`xmp` or `camera`/`gps`/`exif`) peel diagonally outward & vanish.
 */
@Composable
private fun SanitizeScanStage(isExif: Boolean) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val labels = if (isExif) listOf("camera", "gps", "exif") else listOf("author", "producer", "xmp")
    val density = LocalDensity.current

    val scanSweep = if (reduceMotion) 0.55f else easeTravel(phase, 0.14f, 0.46f)
    val scanVisible = if (reduceMotion) true else phase in 0.14f..0.50f

    MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
        if (isExif) {
            MiniPolaroidGlyph(modifier = Modifier.align(Alignment.Center))
        } else {
            MiniSheetGlyph(
                width = 68.dp,
                height = 90.dp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (scanVisible) {
            val topOff = with(density) { (18.dp.toPx() + 108.dp.toPx() * scanSweep).roundToInt() }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, topOff - with(density) { 10.dp.toPx().roundToInt() }) }
                    .width(96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MeldColors.CoralOrange.copy(alpha = 0.28f))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MeldColors.CoralOrange)
                )
            }
        }

        val tagConfigs = listOf(
            Triple(Alignment.TopStart, Offset(-28f, -18f), -12f),
            Triple(Alignment.CenterEnd, Offset(28f, -10f), 12f),
            Triple(Alignment.BottomStart, Offset(-20f, 20f), 10f)
        )
        labels.forEachIndexed { idx, label ->
            val start = 0.46f + idx * 0.05f
            val away = if (reduceMotion) 0.4f else easeTravel(phase, start, start + 0.24f)
            val reenter = if (reduceMotion) 1f else easeOutSeg(phase, 0.90f, 0.99f)
            val visAlpha = if (reduceMotion) 0.7f else if (phase < 0.88f) (1f - away) else reenter
            val (align, vec, rot) = tagConfigs[idx]
            val padMod = when (idx) {
                0 -> Modifier.padding(start = 28.dp, top = 28.dp)
                1 -> Modifier.padding(end = 22.dp)
                else -> Modifier.padding(start = 40.dp, bottom = 24.dp)
            }
            Surface(
                modifier = Modifier
                    .align(align)
                    .then(padMod)
                    .graphicsLayer {
                        translationX = with(density) { vec.x.dp.toPx() * away * (1f - reenter) }
                        translationY = with(density) { vec.y.dp.toPx() * away * (1f - reenter) }
                        rotationZ = rot * away * (1f - reenter)
                        alpha = visAlpha.coerceIn(0f, 1f)
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

/**
 * 10. PDF_MERGE (`ToolMotion.dc.html` Merge / Booklet):
 * Three distinct sheets start wide apart (`-84dp / -10°`, `-34dp Y`, `+84dp / +10°`),
 * spring-join tightly together (`PopCurve`), a thick ink spine zips down the left binding seam (`TravelCurve`),
 * and the bound booklet kicks into `-4° / 1.09x`.
 */
@Composable
private fun MergeBookletStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val inT = if (reduceMotion) 1f else easePop(phase, 0.08f, 0.36f)
    val outT = if (reduceMotion) 0f else easeTravel(phase, 0.82f, 0.97f)
    val join = inT * (1f - outT)
    val spineDraw = if (reduceMotion) 1f else (easeTravel(phase, 0.44f, 0.56f) * (1f - outT))
    val tiltKick = when {
        reduceMotion -> 1f
        phase < 0.56f -> 0f
        phase < 0.65f -> lerp(0f, 1.25f, easeOutSeg(phase, 0.56f, 0.65f))
        phase < 0.74f -> lerp(1.25f, 1.0f, easeOutSeg(phase, 0.65f, 0.74f))
        phase < 0.85f -> 1f
        else -> 1f - easeTravel(phase, 0.85f, 0.96f)
    }

    MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -4f * tiltKick
                    val s = 1f + 0.09f * tiltKick
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center
        ) {
            MiniSheetGlyph(
                modifier = Modifier.graphicsLayer {
                    translationX = with(density) { lerp(-88f, -5f, join).dp.toPx() }
                    rotationZ = lerp(-10f, -5f, join)
                }
            )
            MiniSheetGlyph(
                modifier = Modifier.graphicsLayer {
                    translationX = with(density) { lerp(88f, 5f, join).dp.toPx() }
                    rotationZ = lerp(10f, 5f, join)
                },
                accentHeader = MeldColors.CoralOrange
            )
            MiniSheetGlyph(
                modifier = Modifier.graphicsLayer {
                    translationY = with(density) { lerp(-32f, 0f, join).dp.toPx() }
                    alpha = (0.35f + 0.65f * join).coerceIn(0f, 1f)
                }
            )
            // Left binding seam spine (`sp`)
            Box(
                modifier = Modifier
                    .offset(x = (-27).dp)
                    .width(5.dp)
                    .height(74.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleY = spineDraw.coerceAtLeast(0.001f)
                        alpha = spineDraw.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(MeldColors.InkBlack)
            )
        }
    }
}

/** 11. PDF_TO_PNG / PDF_TO_JPG / IMG_TO_*: Camera flash morph -> sunlit Polaroid + angled format sticker. */
@Composable
private fun PolaroidFormatMorphStage(
    sourceExt: String,
    targetExt: String
) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val reset = if (reduceMotion) 0f else easeTravel(phase, 0.90f, 0.99f)
    val polaroidIn = if (reduceMotion) 1f else (easePop(phase, 0.30f, 0.44f) * (1f - reset))
    val sheetVis = if (reduceMotion) 0f else (1f - easeTravel(phase, 0.28f, 0.36f)).coerceAtLeast(reset)
    val flashAlpha = if (reduceMotion || phase !in 0.25f..0.40f) 0f else sin(((phase - 0.25f) / 0.15f) * PI.toFloat()) * 0.92f
    val stickerIn = if (reduceMotion) 1f else (easePop(phase, 0.50f, 0.64f) * (1f - reset))

    MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
        if (sheetVis > 0.02f) {
            MiniSheetGlyph(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = sheetVis.coerceIn(0f, 1f)
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
                        alpha = polaroidIn.coerceIn(0f, 1f)
                        val sc = 0.72f + 0.28f * polaroidIn
                        scaleX = sc
                        scaleY = sc
                        rotationZ = lerp(-10f, 3f, polaroidIn)
                    }
            )
        }

        if (stickerIn > 0.02f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 34.dp, y = (-34).dp)
                    .graphicsLayer {
                        val sc = 0.86f + 0.14f * stickerIn
                        scaleX = sc
                        scaleY = sc
                        rotationZ = 8f
                        alpha = stickerIn.coerceIn(0f, 1f)
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

/** 12. IMG_TO_PDF: Polaroid slides into a clean corner-folded PDF sheet with Coral sticker. */
@Composable
private fun PhotoToPdfStackStage(sourceExt: String) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val morph = if (reduceMotion) 1f else (easePop(phase, 0.20f, 0.44f) * (1f - easeTravel(phase, 0.86f, 0.98f)))
    val density = LocalDensity.current

    MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
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
                    .graphicsLayer { scaleX = 0.88f + 0.12f * morph }
            )
            MiniSheetGlyph(
                width = 66.dp,
                height = 84.dp,
                modifier = Modifier.graphicsLayer {
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

/**
 * 13. PDF_TO_DOCX (`ToolMotion.dc.html` PDF to Word):
 * Fast 2.6s 3D perspective card flip (`rotationY`): white PDF sheet snaps 0° -> 90° (`TravelCurve`),
 * then Cobalt `#5B8CFF` W Word page snaps `-90° -> 0°` with `PopCurve` spring bounce!
 */
@Composable
private fun SheetFlipWordStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(2600, replay)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    val showWord = if (reduceMotion) true else (phase in 0.34f..0.90f)
    val rotY = when {
        reduceMotion -> 0f
        phase < 0.14f -> 0f
        phase < 0.34f -> lerp(0f, 90f, easeTravel(phase, 0.14f, 0.34f))
        phase < 0.54f -> lerp(-90f, 0f, easePop(phase, 0.34f, 0.54f))
        phase < 0.84f -> 0f
        phase < 0.92f -> lerp(0f, 90f, easeTravel(phase, 0.84f, 0.92f))
        else -> lerp(-90f, 0f, easeOutSeg(phase, 0.92f, 1.0f))
    }

    MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
        if (!showWord) {
            MiniSheetGlyph(
                width = 76.dp,
                height = 98.dp,
                modifier = Modifier
                    .align(Alignment.Center)
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
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeldColors.InkBlack
                    )
                }
            }
        }
    }
}

/** 14. PDF_TO_MD / PDF_TO_HTML / PDF_TO_TXT: Mini-sheet -> dark code terminal typing neon syntax lines + blinking cursor. */
@Composable
private fun SheetToCodeTerminalStage(
    tag: String,
    accentColor: Color
) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val reset = if (reduceMotion) 0f else easeTravel(phase, 0.88f, 0.98f)
    val lineFracs = listOf(0.62f, 0.86f, 0.50f, 0.76f)
    val lineColors = listOf(StageLimeCode, MeldColors.PeriwinkleBlue, StagePinkCode, MeldColors.MintSage)

    MeldStageViewport(height = 152.dp, onTapReplay = { replay++ }) {
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
                    .height(94.dp),
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
                            val draw = if (reduceMotion) 1f else (easeTravel(phase, start, start + 0.14f) * (1f - reset))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(w)
                                    .height(5.dp)
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        scaleX = draw.coerceAtLeast(0.005f)
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

/** 15. COLLAGE: Four scattered tilted Polaroids spring-snap into a tight 2x2 contact grid. */
@Composable
private fun CollageGridAssembleStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(3200, replay)
    val reduceMotion = rememberReduceMotion()
    val snap = if (reduceMotion) 1f else (easePop(phase, 0.12f, 0.40f) * (1f - easeTravel(phase, 0.86f, 0.98f)))
    val colors = listOf(MeldColors.ButterYellow, MeldColors.MintSage, MeldColors.PeriwinkleBlue, MeldColors.PeachCream)
    val tilts = listOf(-12f, 10f, 11f, -9f)
    val spreadX = listOf(-16f, 16f, -16f, 16f)
    val spreadY = listOf(-12f, -12f, 12f, 12f)
    val density = LocalDensity.current

    MeldStageViewport(height = 156.dp, onTapReplay = { replay++ }) {
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
                                    translationX = with(density) { lerp(spreadX[idx], 0f, snap).dp.toPx() }
                                    translationY = with(density) { lerp(spreadY[idx], 0f, snap).dp.toPx() }
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

/** 16. VISUALIZE: 18 pulsing studio equalizer gradient bars inside dark studio viewport. */
@Composable
private fun AudioWaveformStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(1800, replay)
    val reduceMotion = rememberReduceMotion()

    MeldStageViewport(height = 152.dp, backgroundColor = MeldColors.InkBlack, onTapReplay = { replay++ }) {
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
                    .height(68.dp)
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

/** 17. SPEED_2X: Dual playhead tempo comparison streams (`1.0x` vs `2.0x`). */
@Composable
private fun SpeedRaceStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(2400, replay)
    val reduceMotion = rememberReduceMotion()
    val slow = if (reduceMotion) 0.45f else phase
    val fast = if (reduceMotion) 0.90f else (phase * 2f) % 1f

    MeldStageViewport(height = 148.dp, onTapReplay = { replay++ }) {
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

/** 18. SHEET_TO_PDF / SHEET_TO_MD / TABLE_CONVERT: Structured 3x3 spreadsheet row scan -> output badge. */
@Composable
private fun TableTransformStage(
    targetExt: String,
    badgeColor: Color
) {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(2800, replay)
    val reduceMotion = rememberReduceMotion()
    val activeRow = if (reduceMotion) 1 else ((phase * 3f).toInt() % 3)

    MeldStageViewport(height = 148.dp, onTapReplay = { replay++ }) {
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

/** 19. PACK_ZIP: File pills ease smoothly into Lavender archive as zipper teeth clasp top-to-bottom. */
@Composable
private fun ZipPackStage() {
    var replay by remember { mutableIntStateOf(0) }
    val phase = rememberLoopPhase(2800, replay)
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    MeldStageViewport(height = 152.dp, onTapReplay = { replay++ }) {
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
                            translationX = with(density) { (7.dp.toPx() * (0.5f + 0.5f * pulse)) }
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
