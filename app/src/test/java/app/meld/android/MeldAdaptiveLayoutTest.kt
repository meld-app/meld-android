package app.meld.android

import app.meld.android.ui.MeldAdaptiveLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeldAdaptiveLayoutTest {

    @Test
    fun portraitPhoneUsesFloatingBottomDockAndTwoColumns() {
        val spec = MeldAdaptiveLayout.specForWindow(windowWidthDp = 411f, windowHeightDp = 923f)
        assertFalse(spec.useNavigationRail)
        assertFalse(spec.isCompactHeight)
        assertEquals(136f, spec.listBottomPadding.value)
        assertEquals(640f, spec.containerMaxWidth.value)
        assertEquals(2, MeldAdaptiveLayout.toolColumnsForGridWidth(371f))
    }

    @Test
    fun landscapePhoneUsesNavigationRailAndAdaptiveColumns() {
        val spec = MeldAdaptiveLayout.specForWindow(windowWidthDp = 850f, windowHeightDp = 380f)
        assertTrue(spec.useNavigationRail)
        assertTrue(spec.isCompactHeight)
        assertEquals(24f, spec.listBottomPadding.value)
        assertEquals(980f, spec.containerMaxWidth.value)
        assertEquals(3, MeldAdaptiveLayout.toolColumnsForGridWidth(690f))
        assertEquals(2, MeldAdaptiveLayout.toolColumnsForGridWidth(480f))
    }

    @Test
    fun shortCompactPhoneUsesNavigationRailWithoutBottomDockOverlap() {
        val spec = MeldAdaptiveLayout.specForWindow(windowWidthDp = 540f, windowHeightDp = 360f)
        assertTrue(spec.useNavigationRail)
        assertTrue(spec.isCompactHeight)
        assertEquals(2, MeldAdaptiveLayout.toolColumnsForPaneWidth(440f))
    }

    @Test
    fun tabletPortraitAndLandscapeUseNavigationRailAndThreeColumns() {
        val portraitTablet = MeldAdaptiveLayout.specForWindow(windowWidthDp = 800f, windowHeightDp = 1280f)
        assertTrue(portraitTablet.useNavigationRail)
        assertFalse(portraitTablet.isCompactHeight)
        assertEquals(3, MeldAdaptiveLayout.toolColumnsForPaneWidth(700f))

        val landscapeTablet = MeldAdaptiveLayout.specForWindow(windowWidthDp = 1280f, windowHeightDp = 800f)
        assertTrue(landscapeTablet.useNavigationRail)
        assertFalse(landscapeTablet.isCompactHeight)
        assertEquals(3, MeldAdaptiveLayout.toolColumnsForGridWidth(820f))
    }

    @Test
    fun largeFontScaleAdaptsDockClearanceAndGridColumns() {
        assertFalse(MeldAdaptiveLayout.isLargeFontScale(1.0f))
        assertFalse(MeldAdaptiveLayout.isLargeFontScale(1.3f))
        assertTrue(MeldAdaptiveLayout.isLargeFontScale(1.5f))
        assertTrue(MeldAdaptiveLayout.isLargeFontScale(2.0f))

        val portrait200 = MeldAdaptiveLayout.specForWindow(
            windowWidthDp = 411f,
            windowHeightDp = 923f,
            fontScale = 2.0f
        )
        assertFalse(portrait200.useNavigationRail)
        assertEquals(176f, portrait200.listBottomPadding.value)

        val landscape200 = MeldAdaptiveLayout.specForWindow(
            windowWidthDp = 850f,
            windowHeightDp = 380f,
            fontScale = 2.0f
        )
        assertTrue(landscape200.useNavigationRail)
        assertEquals(24f, landscape200.listBottomPadding.value)

        assertEquals(1, MeldAdaptiveLayout.toolColumnsForGridWidth(371f, fontScale = 2.0f))
        assertEquals(2, MeldAdaptiveLayout.toolColumnsForGridWidth(690f, fontScale = 2.0f))
        assertEquals(1, MeldAdaptiveLayout.toolColumnsForPaneWidth(440f, fontScale = 2.0f))
        assertEquals(2, MeldAdaptiveLayout.toolColumnsForPaneWidth(700f, fontScale = 2.0f))

        assertEquals(2, MeldAdaptiveLayout.sampleColumnsForFontScale(1.0f))
        assertEquals(1, MeldAdaptiveLayout.sampleColumnsForFontScale(2.0f))
    }
}

