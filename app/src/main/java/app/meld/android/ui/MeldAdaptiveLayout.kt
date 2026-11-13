package app.meld.android.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MeldAdaptiveSpec(
    val useNavigationRail: Boolean,
    val isCompactHeight: Boolean,
    val containerMaxWidth: Dp,
    val contentMaxWidth: Dp,
    val listBottomPadding: Dp,
    val listStartPadding: Dp
)

/**
 * Pure window/pane size breakpoints driving MELD's adaptive navigation (floating bottom dock vs.
 * side navigation rail) and responsive 2-3 column tool grids across phones, foldables, and tablets.
 */
object MeldAdaptiveLayout {
    const val COMPACT_HEIGHT_DP = 500f
    const val RAIL_MIN_WIDTH_FOR_SHORT_HEIGHT_DP = 480f
    const val MEDIUM_WIDTH_DP = 600f
    const val THREE_COLUMN_PANE_WIDTH_DP = 560f
    const val THREE_COLUMN_GRID_WIDTH_DP = 520f
    const val LARGE_FONT_SCALE_THRESHOLD = 1.45f

    fun isLargeFontScale(fontScale: Float): Boolean = fontScale >= LARGE_FONT_SCALE_THRESHOLD

    fun specForWindow(
        windowWidthDp: Float,
        windowHeightDp: Float,
        fontScale: Float = 1f
    ): MeldAdaptiveSpec {
        val compactHeight = windowHeightDp < COMPACT_HEIGHT_DP
        val isCompactLandscape = compactHeight && windowWidthDp >= RAIL_MIN_WIDTH_FOR_SHORT_HEIGHT_DP
        val isWideScreen = windowWidthDp >= MEDIUM_WIDTH_DP
        val useRail = isCompactLandscape || isWideScreen
        val largeFont = isLargeFontScale(fontScale)
        return MeldAdaptiveSpec(
            useNavigationRail = useRail,
            isCompactHeight = compactHeight,
            containerMaxWidth = if (useRail) 980.dp else 640.dp,
            contentMaxWidth = if (useRail) 860.dp else 640.dp,
            listBottomPadding = when {
                useRail -> 24.dp
                largeFont -> 176.dp
                else -> 136.dp
            },
            listStartPadding = if (useRail) 12.dp else 20.dp
        )
    }

    fun toolColumnsForPaneWidth(paneWidthDp: Float, fontScale: Float = 1f): Int {
        val base = when {
            paneWidthDp >= THREE_COLUMN_PANE_WIDTH_DP -> 3
            else -> 2
        }
        return if (isLargeFontScale(fontScale)) (base - 1).coerceAtLeast(1) else base
    }

    fun toolColumnsForGridWidth(gridWidthDp: Float, fontScale: Float = 1f): Int {
        val base = when {
            gridWidthDp >= THREE_COLUMN_GRID_WIDTH_DP -> 3
            else -> 2
        }
        return if (isLargeFontScale(fontScale)) (base - 1).coerceAtLeast(1) else base
    }

    fun sampleColumnsForFontScale(fontScale: Float): Int =
        if (isLargeFontScale(fontScale)) 1 else 2
}
