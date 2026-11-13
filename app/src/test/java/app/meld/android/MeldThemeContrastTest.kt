package app.meld.android

import androidx.compose.ui.graphics.Color
import app.meld.android.ui.theme.DarkMeldColors
import app.meld.android.ui.theme.LightMeldColors
import app.meld.android.ui.theme.MeldColorTokens
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MeldThemeContrastTest {

    @Test
    fun lightPaletteMeetsWcagContrast() {
        assertPaletteContrast("Light", LightMeldColors)
    }

    @Test
    fun darkPaletteMeetsWcagContrast() {
        assertPaletteContrast("Dark", DarkMeldColors)
    }

    private fun assertPaletteContrast(name: String, tokens: MeldColorTokens) {
        // Core text pairs >= 4.5:1 (WCAG AA normal text)
        assertContrastAtLeast("$name textPrimary on canvas", tokens.textPrimary, tokens.canvas, 4.5)
        assertContrastAtLeast("$name textPrimary on surface", tokens.textPrimary, tokens.surface, 4.5)
        assertContrastAtLeast("$name textPrimary on warmSurface", tokens.textPrimary, tokens.warmSurface, 4.5)
        assertContrastAtLeast("$name textSecondary on canvas", tokens.textSecondary, tokens.canvas, 4.5)
        assertContrastAtLeast("$name textSecondary on surface", tokens.textSecondary, tokens.surface, 4.5)
        assertContrastAtLeast("$name textSecondary on warmSurface", tokens.textSecondary, tokens.warmSurface, 4.5)
        assertContrastAtLeast("$name textMuted on surface", tokens.textMuted, tokens.surface, 4.5)
        assertContrastAtLeast("$name textMuted on canvas", tokens.textMuted, tokens.canvas, 4.5)

        // Pastel / saturated card ink pairs >= 4.5:1
        val pastels = listOf(
            "coral" to tokens.coral,
            "butter" to tokens.butter,
            "mint" to tokens.mint,
            "periwinkle" to tokens.periwinkle,
            "lavender" to tokens.lavender,
            "peach" to tokens.peach,
            "stageWordBlue" to tokens.stageWordBlue
        )
        for ((label, bg) in pastels) {
            assertContrastAtLeast("$name inkOnAccent on $label", tokens.inkOnAccent, bg, 4.5)
            val subtleInk = compositeOver(tokens.inkOnAccent.copy(alpha = 0.85f), bg)
            assertContrastAtLeast("$name inkOnAccent@85% on $label", subtleInk, bg, 4.5)
        }

        // Controls, dock, banners, terminal, error states >= 4.5:1
        assertContrastAtLeast("$name chipSelected", tokens.chipSelectedContent, tokens.chipSelectedBg, 4.5)
        assertContrastAtLeast("$name dockSelected", tokens.dockSelectedContent, tokens.dockSelectedBg, 4.5)
        val dockUnselected = compositeOver(tokens.dockUnselectedContent, tokens.dockBg)
        assertContrastAtLeast("$name dockUnselected on dockBg", dockUnselected, tokens.dockBg, 4.5)
        assertContrastAtLeast("$name banner", tokens.bannerContent, tokens.bannerBg, 4.5)
        assertContrastAtLeast("$name terminalText on terminalBg", tokens.terminalText, tokens.terminalBg, 4.5)
        assertContrastAtLeast("$name onError on error", tokens.onError, tokens.error, 4.5)
        assertContrastAtLeast("$name textPrimary on errorSurface", tokens.textPrimary, tokens.errorSurface, 4.5)
        assertContrastAtLeast("$name stageLaserRed on surface", tokens.stageLaserRed, tokens.surface, 4.5)

        // Non-text UI boundaries, strokes, stage glyphs >= 3.0:1
        assertContrastAtLeast("$name border on canvas", tokens.border, tokens.canvas, 3.0)
        assertContrastAtLeast("$name border on surface", tokens.border, tokens.surface, 3.0)
        assertContrastAtLeast("$name border on warmSurface", tokens.border, tokens.warmSurface, 3.0)
        assertContrastAtLeast("$name hardShadow on canvas", tokens.hardShadow, tokens.canvas, 3.0)
        assertContrastAtLeast("$name dockBorder on canvas", tokens.dockBorder, tokens.canvas, 3.0)
        assertContrastAtLeast("$name errorBorder on canvas", tokens.errorBorder, tokens.canvas, 3.0)
        assertContrastAtLeast("$name textPrimary on stageSand", tokens.textPrimary, tokens.stageSand, 4.5)
        assertContrastAtLeast("$name stageLaserRed on stageSand", tokens.stageLaserRed, tokens.stageSand, 3.0)
    }

    private fun assertContrastAtLeast(label: String, fg: Color, bg: Color, minRatio: Double) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$label contrast ${"%.2f".format(ratio)}:1 is below ${"%.1f".format(minRatio)}:1",
            ratio >= minRatio
        )
    }

    private fun compositeOver(fg: Color, bg: Color): Color {
        val a = fg.alpha.toDouble()
        val r = (fg.red * a + bg.red * (1.0 - a)).toFloat()
        val g = (fg.green * a + bg.green * (1.0 - a)).toFloat()
        val b = (fg.blue * a + bg.blue * (1.0 - a)).toFloat()
        return Color(r, g, b, 1f)
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val bright = max(l1, l2)
        val dark = min(l1, l2)
        return (bright + 0.05) / (dark + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}

