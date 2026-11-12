package app.meld.android.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MeldColors {
    // Tactile Warm Stationery Canvas (Warm Grey Family Only — Zero Cool Greys)
    val CreamCanvas = Color(0xFFFAF4EC)
    val WarmSurface = Color(0xFFF3ECE1)
    val PureWhite = Color(0xFFFFFFFF)
    val InkBlack = Color(0xFF141414)
    val DockInk = Color(0xFF18181A)
    val TextPrimary = Color(0xFF141414)
    val TextSecondary = Color(0xFF5D5954)
    val TextMuted = Color(0xFF8C867E)
    val TrackGray = Color(0xFFD9D2C8)

    // Signature Tactile Accents
    val CoralOrange = Color(0xFFFF6A33)
    val ButterYellow = Color(0xFFF3CF55)
    val MintSage = Color(0xFF9FE0B8)
    val PeriwinkleBlue = Color(0xFFB5D3F8)
    val SoftLavender = Color(0xFFCBBEEB)
    val PeachCream = Color(0xFFFFE0CC)

    // Locked Shape Scale
    val CardCorner = 24.dp
    val SubCardCorner = 18.dp
    val StrokeWidth = 2.dp
}

object MeldMotion {
    // Exact curves from Emil Kowalski & Appllama motion standards
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val EaseOvershoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    const val PRESS_MS = 140
    const val UI_MS = 220
    const val EXIT_MS = 160

    fun <T> snapSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> popSpring() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = 380f
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

private val MeldWarmLightScheme = lightColorScheme(
    primary = MeldColors.CoralOrange,
    onPrimary = Color.White,
    secondary = MeldColors.InkBlack,
    onSecondary = Color.White,
    tertiary = MeldColors.MintSage,
    background = MeldColors.CreamCanvas,
    onBackground = MeldColors.InkBlack,
    surface = MeldColors.PureWhite,
    onSurface = MeldColors.InkBlack,
    surfaceVariant = MeldColors.WarmSurface,
    onSurfaceVariant = MeldColors.TextSecondary,
    error = Color(0xFFD9381E)
)

private val MeldTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun MeldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeldWarmLightScheme,
        typography = MeldTypography,
        content = content
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalMeldSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalMeldRouteAnimScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Connects a home/catalog tool tile cleanly to its Task hero card via shared bounds. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.meldSharedToolBounds(key: String): Modifier {
    val reduceMotion = rememberReduceMotion()
    val sharedScope = LocalMeldSharedScope.current
    val animScope = LocalMeldRouteAnimScope.current
    if (reduceMotion || sharedScope == null || animScope == null) return this
    return with(sharedScope) {
        this@meldSharedToolBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope,
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
        )
    }
}
