package app.meld.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MeldColors {
    val Background = Color(0xFF0D0E12)
    val Surface = Color(0xFF151821)
    val SurfaceElevated = Color(0xFF1E2230)
    val CardBorder = Color(0xFF2B3042)
    val RibbonOrange = Color(0xFFFF6A2B)
    val RibbonAmber = Color(0xFFFFB347)
    val Emerald = Color(0xFF30D158)
    val Azure = Color(0xFF0A84FF)
    val Violet = Color(0xFFBF5AF2)
    val Crimson = Color(0xFFFF453A)
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF9BA3B5)
    val TextMuted = Color(0xFF636B7E)
}

private val MeldDarkColorScheme = darkColorScheme(
    primary = MeldColors.RibbonOrange,
    onPrimary = Color.White,
    secondary = MeldColors.RibbonAmber,
    onSecondary = Color.Black,
    tertiary = MeldColors.Azure,
    background = MeldColors.Background,
    onBackground = MeldColors.TextPrimary,
    surface = MeldColors.Surface,
    onSurface = MeldColors.TextPrimary,
    surfaceVariant = MeldColors.SurfaceElevated,
    onSurfaceVariant = MeldColors.TextSecondary,
    error = MeldColors.Crimson
)

private val MeldTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = 1.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.4.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun MeldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeldDarkColorScheme,
        typography = MeldTypography,
        content = content
    )
}

