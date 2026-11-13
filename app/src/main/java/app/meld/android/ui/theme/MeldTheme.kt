package app.meld.android.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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

/** Locked geometric shape and stroke tokens shared across light and dark themes. */
object MeldDimens {
    val CardCorner = 24.dp
    val SubCardCorner = 18.dp
    val StrokeWidth = 2.dp
}

/** Semantic warm neo-brutalist color tokens supporting both Light and Dark stationery themes. */
@Immutable
data class MeldColorTokens(
    val canvas: Color,
    val surface: Color,
    val warmSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val track: Color,
    val border: Color,
    val hardShadow: Color,
    val inkOnAccent: Color,
    val chipSelectedBg: Color,
    val chipSelectedContent: Color,
    val dockBg: Color,
    val dockBorder: Color,
    val dockSelectedBg: Color,
    val dockSelectedContent: Color,
    val dockUnselectedContent: Color,
    val coral: Color,
    val butter: Color,
    val mint: Color,
    val periwinkle: Color,
    val lavender: Color,
    val peach: Color,
    val stageSand: Color,
    val stageLine: Color,
    val stageLaserRed: Color,
    val stageWordBlue: Color,
    val stageLimeCode: Color,
    val stagePinkCode: Color,
    val terminalBg: Color,
    val terminalText: Color,
    val paperFill: Color,
    val paperStroke: Color,
    val paperLine: Color,
    val bannerBg: Color,
    val bannerContent: Color,
    val error: Color,
    val errorBorder: Color,
    val errorSurface: Color,
    val onError: Color
)

// Warm Stationery Canvas & Espresso Dark Family (Warm Greys Only — Zero Cool Greys)
internal val LightMeldColors = MeldColorTokens(
    canvas = Color(0xFFFAF4EC),
    surface = Color(0xFFFFFFFF),
    warmSurface = Color(0xFFF3ECE1),
    textPrimary = Color(0xFF141414),
    textSecondary = Color(0xFF5D5954),
    textMuted = Color(0xFF736D66),
    track = Color(0xFFD9D2C8),
    border = Color(0xFF141414),
    hardShadow = Color(0xFF141414),
    inkOnAccent = Color(0xFF141414),
    chipSelectedBg = Color(0xFF141414),
    chipSelectedContent = Color(0xFFFFFFFF),
    dockBg = Color(0xFF18181A),
    dockBorder = Color(0xFF141414),
    dockSelectedBg = Color(0xFFFF6A33),
    dockSelectedContent = Color(0xFF141414),
    dockUnselectedContent = Color(0xFFFFFFFF).copy(alpha = 0.85f),
    coral = Color(0xFFFF6A33),
    butter = Color(0xFFF3CF55),
    mint = Color(0xFF9FE0B8),
    periwinkle = Color(0xFFB5D3F8),
    lavender = Color(0xFFCBBEEB),
    peach = Color(0xFFFFE0CC),
    stageSand = Color(0xFFEFE8DA),
    stageLine = Color(0xFFD8D2C4),
    stageLaserRed = Color(0xFFC9400F),
    stageWordBlue = Color(0xFF5B8CFF),
    stageLimeCode = Color(0xFFC6F432),
    stagePinkCode = Color(0xFFFF5CA8),
    terminalBg = Color(0xFF141414),
    terminalText = Color(0xFFFFFFFF),
    paperFill = Color(0xFFFFFFFF),
    paperStroke = Color(0xFF141414),
    paperLine = Color(0xFFD8D2C4),
    bannerBg = Color(0xFF141414),
    bannerContent = Color(0xFFFFFFFF),
    error = Color(0xFFC62828),
    errorBorder = Color(0xFFC62828),
    errorSurface = Color(0xFFFFFFFF),
    onError = Color(0xFFFFFFFF)
)

internal val DarkMeldColors = MeldColorTokens(
    canvas = Color(0xFF151210),
    surface = Color(0xFF221E1B),
    warmSurface = Color(0xFF2D2823),
    textPrimary = Color(0xFFF5EFE6),
    textSecondary = Color(0xFFB8AFA4),
    textMuted = Color(0xFF9B9186),
    track = Color(0xFF3C352F),
    border = Color(0xFF8A7E72),
    hardShadow = Color(0xFF70665D),
    inkOnAccent = Color(0xFF141414),
    chipSelectedBg = Color(0xFFF5EFE6),
    chipSelectedContent = Color(0xFF141414),
    dockBg = Color(0xFF24201C),
    dockBorder = Color(0xFF8A7E72),
    dockSelectedBg = Color(0xFFF06B3A),
    dockSelectedContent = Color(0xFF141414),
    dockUnselectedContent = Color(0xFFF5EFE6).copy(alpha = 0.88f),
    coral = Color(0xFFF06B3A),
    butter = Color(0xFFE0BE4C),
    mint = Color(0xFF85CCA2),
    periwinkle = Color(0xFF93B7E2),
    lavender = Color(0xFFB4A4DA),
    peach = Color(0xFFE8B99D),
    stageSand = Color(0xFF2A2520),
    stageLine = Color(0xFF756B61),
    stageLaserRed = Color(0xFFFF6E40),
    stageWordBlue = Color(0xFF6B98FF),
    stageLimeCode = Color(0xFFC6F432),
    stagePinkCode = Color(0xFFFF5CA8),
    terminalBg = Color(0xFF12100E),
    terminalText = Color(0xFFF5EFE6),
    paperFill = Color(0xFFF4ECE0),
    paperStroke = Color(0xFF141414),
    paperLine = Color(0xFFCEC5B6),
    bannerBg = Color(0xFFF5EFE6),
    bannerContent = Color(0xFF141414),
    error = Color(0xFFC62828),
    errorBorder = Color(0xFFF26457),
    errorSurface = Color(0xFF341C1A),
    onError = Color(0xFFFFFFFF)
)

val LocalMeldColors = staticCompositionLocalOf { LightMeldColors }

/** Convenience accessor for the active semantic MELD color tokens inside Compose. */
val meldColors: MeldColorTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalMeldColors.current

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
    primary = LightMeldColors.coral,
    onPrimary = LightMeldColors.inkOnAccent,
    secondary = LightMeldColors.textPrimary,
    onSecondary = LightMeldColors.surface,
    tertiary = LightMeldColors.mint,
    background = LightMeldColors.canvas,
    onBackground = LightMeldColors.textPrimary,
    surface = LightMeldColors.surface,
    onSurface = LightMeldColors.textPrimary,
    surfaceVariant = LightMeldColors.warmSurface,
    onSurfaceVariant = LightMeldColors.textSecondary,
    outline = LightMeldColors.border,
    error = LightMeldColors.error,
    onError = LightMeldColors.onError
)

private val MeldWarmDarkScheme = darkColorScheme(
    primary = DarkMeldColors.coral,
    onPrimary = DarkMeldColors.inkOnAccent,
    secondary = DarkMeldColors.textPrimary,
    onSecondary = DarkMeldColors.canvas,
    tertiary = DarkMeldColors.mint,
    background = DarkMeldColors.canvas,
    onBackground = DarkMeldColors.textPrimary,
    surface = DarkMeldColors.surface,
    onSurface = DarkMeldColors.textPrimary,
    surfaceVariant = DarkMeldColors.warmSurface,
    onSurfaceVariant = DarkMeldColors.textSecondary,
    outline = DarkMeldColors.border,
    error = DarkMeldColors.error,
    onError = DarkMeldColors.onError
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
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun MeldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkMeldColors else LightMeldColors
    val colorScheme = if (darkTheme) MeldWarmDarkScheme else MeldWarmLightScheme
    CompositionLocalProvider(LocalMeldColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeldTypography,
            content = content
        )
    }
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
