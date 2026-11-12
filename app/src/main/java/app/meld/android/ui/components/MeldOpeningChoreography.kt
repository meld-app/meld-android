package app.meld.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.meld.android.R
import app.meld.android.ui.theme.MeldColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class MeldOpeningDriver {
    // Targets captured in Root coordinates via onGloballyPositioned
    var rootOrigin by mutableStateOf(Offset.Zero)
    var brandTarget by mutableStateOf<Rect?>(null)
    var sheetTarget by mutableStateOf<Rect?>(null)

    // Animated timeline values (1f = completely settled / visible)
    val sheetPop = Animatable(0f)       // 0..1 overshoot pop into center stage
    val ghostFan = Animatable(0f)       // 0 -> 1 fan out -> 0 merge back
    val line0 = Animatable(0f)
    val line1 = Animatable(0f)
    val line2 = Animatable(0f)
    val line3 = Animatable(0f)
    val line4 = Animatable(0f)
    val brandPop = Animatable(0f)
    val taglineAlpha = Animatable(0f)
    val dockToHome = Animatable(0f)     // 0 = center stage, 1 = landed on real Home positions
    val heroClip = Animatable(0f)       // 0 -> 1 circular reveal from sheet center

    val pillAlpha = Animatable(0f)
    val rhAnim = Animatable(0f)
    val rpAnim = Animatable(0f)
    val qtAnim = Animatable(0f)
    val t1Anim = Animatable(0f)
    val t2Anim = Animatable(0f)
    val t3Anim = Animatable(0f)
    val recAnim = Animatable(0f)
    val navDockAnim = Animatable(0f)

    var showOverlay by mutableStateOf(true)

    suspend fun playSequence(isColdStart: Boolean = false) {
        showOverlay = true
        // Reset all
        sheetPop.snapTo(0f)
        ghostFan.snapTo(0f)
        line0.snapTo(0f)
        line1.snapTo(0f)
        line2.snapTo(0f)
        line3.snapTo(0f)
        line4.snapTo(0f)
        brandPop.snapTo(0f)
        taglineAlpha.snapTo(0f)
        dockToHome.snapTo(0f)
        heroClip.snapTo(0f)
        pillAlpha.snapTo(0f)
        rhAnim.snapTo(0f)
        rpAnim.snapTo(0f)
        qtAnim.snapTo(0f)
        t1Anim.snapTo(0f)
        t2Anim.snapTo(0f)
        t3Anim.snapTo(0f)
        recAnim.snapTo(0f)
        navDockAnim.snapTo(0f)

        // Wait for Compose layout targets + Android splash fade on cold start
        delay(if (isColdStart) 380L else 60L)

        coroutineScope {
            // 1. Center Sheet pops up (-30deg -> -5deg, scale 1.1 -> 2.1)
            launch {
                sheetPop.animateTo(1f, tween(340, easing = MeldOvershootEasing))
            }

            // 2. Staggered document lines draw left-to-right (l0..l4)
            launch {
                delay(180)
                line0.animateTo(1f, tween(170, easing = MeldSmoothEasing))
            }
            launch {
                delay(250)
                line1.animateTo(1f, tween(160, easing = MeldSmoothEasing))
            }
            launch {
                delay(305)
                line2.animateTo(1f, tween(160, easing = MeldSmoothEasing))
            }
            launch {
                delay(360)
                line3.animateTo(1f, tween(160, easing = MeldSmoothEasing))
            }
            launch {
                delay(415)
                line4.animateTo(1f, tween(160, easing = MeldSmoothEasing))
            }

            // 3. Ghost pages g1/g2 fan outward + MELD brand + tagline appear
            launch {
                delay(440)
                ghostFan.animateTo(1f, tween(300, easing = MeldOvershootEasing))
                delay(280)
                ghostFan.animateTo(0f, tween(220, easing = MeldSmoothEasing))
            }
            launch {
                delay(460)
                brandPop.animateTo(1f, tween(280, easing = MeldOvershootEasing))
            }
            launch {
                delay(620)
                taglineAlpha.animateTo(1f, tween(180))
                delay(260)
                taglineAlpha.animateTo(0f, tween(150))
            }

            // 4. Sheet & Brand dock smoothly into live Home positions + Coral hero circular clip
            launch {
                delay(1140)
                dockToHome.animateTo(1f, tween(420, easing = MeldSmoothEasing))
            }
            launch {
                delay(1160)
                heroClip.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
            }

            // 5. Staggered studio elements spring into place
            launch {
                delay(1220)
                rhAnim.animateTo(1f, tween(300, easing = MeldOvershootEasing))
            }
            launch {
                delay(1270)
                rpAnim.animateTo(1f, tween(300, easing = MeldOvershootEasing))
            }
            launch {
                delay(1340)
                qtAnim.animateTo(1f, tween(280, easing = MeldOvershootEasing))
            }
            launch {
                delay(1380)
                t1Anim.animateTo(1f, tween(320, easing = MeldOvershootEasing))
            }
            launch {
                delay(1430)
                t2Anim.animateTo(1f, tween(320, easing = MeldOvershootEasing))
            }
            launch {
                delay(1480)
                t3Anim.animateTo(1f, tween(320, easing = MeldOvershootEasing))
            }
            launch {
                delay(1460)
                navDockAnim.animateTo(1f, tween(340, easing = MeldOvershootEasing))
            }
            launch {
                delay(1520)
                pillAlpha.animateTo(1f, tween(240))
                recAnim.animateTo(1f, tween(280, easing = MeldOvershootEasing))
            }
        }

        showOverlay = false
    }
}

@Composable
fun MeldOpeningOverlay(
    driver: MeldOpeningDriver,
    modifier: Modifier = Modifier
) {
    if (!driver.showOverlay) return

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        // Cover full screen in Cream only before dock begins, then expose real Home elements seamlessly!
        val bgAlpha = (1f - driver.dockToHome.value * 1.4f).coerceIn(0f, 1f)
        if (bgAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeldColors.CreamCanvas.copy(alpha = bgAlpha))
            )
        }

        // Dimensions of the working document sheet (matches WorkingDocumentIllustration 72x90dp)
        val sheetWpx = with(density) { 72.dp.toPx() }
        val sheetHpx = with(density) { 90.dp.toPx() }

        // Center stage coordinates
        val centerSheetX = (containerW - sheetWpx) / 2f
        val centerSheetY = containerH * 0.34f - sheetHpx / 2f

        val targetSheet = driver.sheetTarget
        val destSheetX = if (targetSheet != null) targetSheet.left - driver.rootOrigin.x else centerSheetX
        val destSheetY = if (targetSheet != null) targetSheet.top - driver.rootOrigin.y else centerSheetY

        val dockT = driver.dockToHome.value
        val curSheetX = lerp(centerSheetX, destSheetX, dockT)
        val curSheetY = lerp(centerSheetY, destSheetY, dockT)
        val curSheetScale = lerp(
            lerp(1.1f, 2.05f, driver.sheetPop.value),
            1.0f,
            dockT
        )
        val curSheetRot = lerp(-30f, -4f, driver.sheetPop.value) * (1f - dockT) + (-3f * dockT)
        val curSheetAlpha = driver.sheetPop.value.coerceIn(0f, 1f)

        // Ghost Sheet 1 (g1 - fans left)
        val fan = driver.ghostFan.value
        if (fan > 0.01f) {
            val g1OffsetX = with(density) { (-54f * fan).dp.toPx() }
            val g1OffsetY = with(density) { (12f * fan).dp.toPx() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((curSheetX + g1OffsetX).roundToInt(), (curSheetY + g1OffsetY).roundToInt()) }
                    .graphicsLayer {
                        scaleX = curSheetScale
                        scaleY = curSheetScale
                        rotationZ = lerp(-5f, -16f, fan)
                        alpha = fan.coerceIn(0f, 1f)
                    }
            ) {
                WorkingDocumentIllustration(
                    extension = "pdf",
                    lineFractions = listOf(1f, 1f, 1f, 1f, 1f)
                )
            }

            // Ghost Sheet 2 (g2 - fans right)
            val g2OffsetX = with(density) { (54f * fan).dp.toPx() }
            val g2OffsetY = with(density) { (12f * fan).dp.toPx() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((curSheetX + g2OffsetX).roundToInt(), (curSheetY + g2OffsetY).roundToInt()) }
                    .graphicsLayer {
                        scaleX = curSheetScale
                        scaleY = curSheetScale
                        rotationZ = lerp(-5f, 8f, fan)
                        alpha = fan.coerceIn(0f, 1f)
                    }
            ) {
                WorkingDocumentIllustration(
                    extension = "pdf",
                    lineFractions = listOf(1f, 1f, 1f, 1f, 1f)
                )
            }
        }

        // Primary Hero Document Sheet (`sh`)
        Box(
            modifier = Modifier
                .offset { IntOffset(curSheetX.roundToInt(), curSheetY.roundToInt()) }
                .graphicsLayer {
                    scaleX = curSheetScale
                    scaleY = curSheetScale
                    rotationZ = curSheetRot
                    alpha = curSheetAlpha
                }
        ) {
            WorkingDocumentIllustration(
                extension = "pdf",
                lineFractions = listOf(
                    driver.line0.value,
                    driver.line1.value,
                    driver.line2.value,
                    driver.line3.value,
                    driver.line4.value
                )
            )
        }

        // Brand Lockup (`M meld`) flying from below center sheets to exact header slot
        val targetBrand = driver.brandTarget
        val brandWpx = targetBrand?.width ?: with(density) { 120.dp.toPx() }
        val brandHpx = targetBrand?.height ?: with(density) { 38.dp.toPx() }

        val centerBrandX = (containerW - brandWpx) / 2f
        val centerBrandY = containerH * 0.59f - brandHpx / 2f
        val destBrandX = if (targetBrand != null) targetBrand.left - driver.rootOrigin.x else centerBrandX
        val destBrandY = if (targetBrand != null) targetBrand.top - driver.rootOrigin.y else centerBrandY

        val curBrandX = lerp(centerBrandX, destBrandX, dockT)
        val curBrandY = lerp(centerBrandY, destBrandY, dockT)
        val curBrandScale = lerp(
            lerp(1.1f, 1.55f, driver.brandPop.value),
            1.0f,
            dockT
        )

        Row(
            modifier = Modifier
                .offset { IntOffset(curBrandX.roundToInt(), curBrandY.roundToInt()) }
                .graphicsLayer {
                    scaleX = curBrandScale
                    scaleY = curBrandScale
                    alpha = driver.brandPop.value.coerceIn(0f, 1f)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(11.dp),
                color = MeldColors.CoralOrange,
                border = BorderStroke(2.dp, MeldColors.InkBlack)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.meld_logo),
                    contentDescription = "meld",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "meld",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MeldColors.InkBlack
            )
        }

        // Centered Tagline (`TRANSFORM · CONVERT · ORGANIZE`)
        val tagA = driver.taglineAlpha.value.coerceIn(0f, 1f)
        if (tagA > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (containerH * 0.675f).roundToInt()) },
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "TRANSFORM · CONVERT · ORGANIZE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MeldColors.TextSecondary,
                    modifier = Modifier.graphicsLayer { alpha = tagA }
                )
            }
        }
    }
}
