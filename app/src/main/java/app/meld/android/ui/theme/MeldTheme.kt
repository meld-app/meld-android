package app.meld.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MeldColors {
    // Tactile Warm Stationery Canvas
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

    val StrokeWidth = 2.dp
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
