package app.meld.android

import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.ui.DockTab
import app.meld.android.ui.SamplePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeldLocalizationResourcesTest {

    @Test
    fun allToolAndFilterEnumsBindNonZeroDistinctStringResources() {
        val tools = MeldToolId.entries
        assertEquals(38, tools.size)

        tools.forEach { tool ->
            assertTrue("${tool.name} titleRes", tool.titleRes != 0)
            assertTrue("${tool.name} badgeRes", tool.badgeRes != 0)
            assertTrue("${tool.name} subtitleRes", tool.subtitleRes != 0)
            assertTrue("${tool.name} ctaLabelRes", tool.ctaLabelRes != 0)
            assertTrue("${tool.name} resultHeadlineRes", tool.resultHeadlineRes != 0)
        }

        assertEquals(tools.size, tools.map { it.titleRes }.toSet().size)
        assertEquals(tools.size, tools.map { it.subtitleRes }.toSet().size)
        assertEquals(tools.size, tools.map { it.ctaLabelRes }.toSet().size)
        assertEquals(tools.size, tools.map { it.resultHeadlineRes }.toSet().size)

        assertEquals(MeldMode.entries.size, MeldMode.entries.map { it.labelRes }.toSet().size)
        assertEquals(MeldCategory.entries.size, MeldCategory.entries.map { it.labelRes }.toSet().size)
        assertEquals(DockTab.entries.size, DockTab.entries.map { it.labelRes }.toSet().size)
        assertEquals(SamplePreset.entries.size, SamplePreset.entries.map { it.labelRes }.toSet().size)
    }
}
