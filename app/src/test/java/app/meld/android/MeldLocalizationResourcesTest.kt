package app.meld.android

import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.ui.DockTab
import app.meld.android.ui.SamplePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MeldLocalizationResourcesTest {

    @Test
    fun allToolAndFilterEnumsBindNonZeroDistinctStringResources() {
        val tools = MeldToolId.entries
        assertEquals(40, tools.size)

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

    @Test
    fun spanishLocaleMatchesAllDefaultKeysAndFormatSpecifiers() {
        val baseStringsFile = locateResFile("values/strings.xml")
        val esStringsFile = locateResFile("values-es/strings.xml")

        val baseStrings = parseStrings(baseStringsFile)
        val esStrings = parseStrings(esStringsFile)
        assertEquals("Spanish strings key set must match default", baseStrings.keys, esStrings.keys)

        val formatRegex = Regex("%\\d+\\$[dsf]|%%")
        baseStrings.forEach { (key, baseText) ->
            val esText = esStrings.getValue(key)
            assertTrue("Spanish value blank for $key", esText.isNotBlank())
            val baseSpecs = formatRegex.findAll(baseText).map { it.value }.sorted().toList()
            val esSpecs = formatRegex.findAll(esText).map { it.value }.sorted().toList()
            assertEquals("Format specifiers mismatch for <string name=\"$key\">", baseSpecs, esSpecs)
        }

        val basePlurals = parsePlurals(baseStringsFile)
        val esPlurals = parsePlurals(esStringsFile)
        assertEquals("Spanish plurals key set must match default", basePlurals.keys, esPlurals.keys)

        basePlurals.forEach { (key, quantities) ->
            val esQuantities = esPlurals.getValue(key)
            assertTrue("Missing 'one' quantity for $key in values-es", esQuantities.containsKey("one"))
            assertTrue("Missing 'many' quantity for $key in values-es", esQuantities.containsKey("many"))
            assertTrue("Missing 'other' quantity for $key in values-es", esQuantities.containsKey("other"))
            quantities.forEach { (qty, text) ->
                val esQtyText = esQuantities.getValue(qty)
                val baseSpecs = formatRegex.findAll(text).map { it.value }.sorted().toList()
                val esSpecs = formatRegex.findAll(esQtyText).map { it.value }.sorted().toList()
                assertEquals("Format specifiers mismatch for <plurals name=\"$key\" qty=\"$qty\">", baseSpecs, esSpecs)
            }
            val otherSpecs = formatRegex.findAll(quantities.getValue("other")).map { it.value }.sorted().toList()
            val manySpecs = formatRegex.findAll(esQuantities.getValue("many")).map { it.value }.sorted().toList()
            assertEquals("Format specifiers mismatch for <plurals name=\"$key\" qty=\"many\">", otherSpecs, manySpecs)
        }
    }

    private fun locateResFile(relativePath: String): File {
        val direct = File("src/main/res/$relativePath")
        if (direct.isFile) return direct
        val fromRoot = File("app/src/main/res/$relativePath")
        if (fromRoot.isFile) return fromRoot
        error("Could not locate res/$relativePath from ${File(".").absolutePath}")
    }

    private fun parseStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            map[el.getAttribute("name")] = el.textContent
        }
        return map
    }

    private fun parsePlurals(file: File): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("plurals")
        val map = LinkedHashMap<String, Map<String, String>>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val qtyMap = LinkedHashMap<String, String>()
            val items = el.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                qtyMap[item.getAttribute("quantity")] = item.textContent
            }
            map[el.getAttribute("name")] = qtyMap
        }
        return map
    }
}
