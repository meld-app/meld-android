package app.meld.android

import app.meld.android.engine.PptxEngine
import app.meld.android.engine.SampleFileFactory
import app.meld.android.engine.pptx.PptxOoxmlParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Pure-JVM unit tests verifying MELD's OOXML PPTX parser, structural/font/geometry fidelity,
 * Markdown/TXT extraction, byte-preserving privacy sanitization, `.odttf` font de-obfuscation,
 * color modifier math, and ZIP/XML security protections.
 */
class PptxEngineUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun samplePptxParsesExactDimensionsThemeAndElementsWithoutMutatingInput() {
        val sample = SampleFileFactory.createSamplePptx(tempFolder.root)
        val shaBefore = sha256Hex(sample)

        assertEquals(3, PptxOoxmlParser.countSlides(sample))

        val pres = PptxOoxmlParser.parse(sample)
        assertEquals(960f, pres.slideWidthPt, 0.01f)
        assertEquals(540f, pres.slideHeightPt, 0.01f)
        assertEquals(3, pres.slides.size)

        // Theme fonts +mj-lt / +mn-lt should resolve to distinct fonts recorded on presentation
        assertTrue(pres.usedFonts.contains("Georgia"))
        assertTrue(pres.usedFonts.contains("Helvetica Neue"))

        val slide1 = pres.slides[0]
        assertEquals(1, slide1.index1Based)
        assertFalse(slide1.hidden)
        assertEquals("MELD Offline Slide Engine", slide1.extractHeading())
        assertTrue(slide1.notesText.contains("byte-preserving sanitization"))

        val slide1Texts = slide1.elements
            .filterIsInstance<PptxOoxmlParser.PptxElement.Shape>()
            .flatMap { it.textBody?.paragraphs.orEmpty() }
            .joinToString("\n") { p -> p.runs.joinToString("") { it.text } }
        assertTrue(slide1Texts.contains("MELD 100% OFFLINE DECK"))
        assertTrue(slide1Texts.contains("SLIDE STUDIO"))
        assertTrue(slide1Texts.contains("Zero-cloud OOXML parsing"))

        // Slide 1 hero badge group transform checks (container at x=60pt, y=72pt, w=180pt, h=48pt)
        val badgeShape = slide1.elements
            .filterIsInstance<PptxOoxmlParser.PptxElement.Shape>()
            .firstOrNull { shape ->
                shape.textBody?.paragraphs?.any { p ->
                    p.runs.any { it.text.contains("SLIDE STUDIO") }
                } == true
            }
        assertNotNull(badgeShape)
        assertEquals(60f, badgeShape!!.xfrm.x, 0.1f)
        assertEquals(72f, badgeShape.xfrm.y, 0.1f)
        assertEquals(180f, badgeShape.xfrm.width, 0.1f)
        assertEquals(48f, badgeShape.xfrm.height, 0.1f)

        // Slide 2: inherits placeholder title geometry + superscript + picture asset
        val slide2 = pres.slides[1]
        assertEquals("Architecture & Format Fidelity", slide2.extractHeading())
        val pic = slide2.elements.filterIsInstance<PptxOoxmlParser.PptxElement.Picture>().firstOrNull()
        assertNotNull(pic)
        assertEquals("ppt/media/image1.png", pic!!.imageEntryPath)
        ZipFile(sample).use { zip ->
            val pngBytes = PptxOoxmlParser.readAssetBytes(zip, pic.imageEntryPath)
            assertNotNull(pngBytes)
            assertEquals(0x89.toByte(), pngBytes!![0])
            assertEquals('P'.code.toByte(), pngBytes[1])
        }

        // Slide 3: OOXML 4x3 table with dark header fill
        val slide3 = pres.slides[2]
        val table = slide3.elements.filterIsInstance<PptxOoxmlParser.PptxElement.Table>().firstOrNull()
        assertNotNull(table)
        assertEquals(3, table!!.colWidthsPt.size)
        assertEquals(4, table.rows.size)
        val firstHeaderCell = table.rows[0].cells[0]
        assertEquals(0xFF1A1A1A.toInt(), firstHeaderCell.fillArgb)
        assertEquals("Tool", firstHeaderCell.textBody?.paragraphs?.firstOrNull()?.runs?.firstOrNull()?.text)

        assertEquals(shaBefore, sha256Hex(sample))
    }

    @Test
    fun markdownAndPlainTextPreserveStructureTablesAndSpeakerNotes() {
        val sample = SampleFileFactory.createSamplePptx(tempFolder.root)
        val shaBefore = sha256Hex(sample)

        val mdOut = File(tempFolder.root, "deck.md")
        val mdSummary = PptxEngine.convertPptxToMarkdown(sample, mdOut)
        assertEquals(3, mdSummary.slideCount)
        assertEquals(1, mdSummary.tableCount)
        assertEquals(1, mdSummary.noteCount)

        val mdText = mdOut.readText(StandardCharsets.UTF_8)
        assertTrue(mdText.contains("# MELD\\_Deck\\_3Slides (3 slides)") || mdText.contains("# MELD"))
        assertTrue(mdText.contains("## Slide 1: MELD Offline Slide Engine"))
        assertTrue(mdText.contains("**Read-Only Source: **") || mdText.contains("**Read-Only Source:**"))
        assertTrue(mdText.contains("| Tool | Guarantee | Target |"))
        assertTrue(mdText.contains("| --- | --- | --- |"))
        assertTrue(mdText.contains("> **Speaker Notes:**"))
        assertTrue(mdText.contains("_[Embedded image: image1.png]_"))

        val txtOut = File(tempFolder.root, "deck.txt")
        val txtSummary = PptxEngine.extractPptxPlainText(sample, txtOut)
        assertEquals(3, txtSummary.slideCount)
        val plain = txtOut.readText(StandardCharsets.UTF_8)
        assertTrue(plain.contains("=== Slide 1 ==="))
        assertTrue(plain.contains("Tool\tGuarantee\tTarget"))
        assertTrue(plain.contains("[Speaker Notes]"))

        assertEquals(shaBefore, sha256Hex(sample))
    }

    @Test
    fun sanitizePptxRemovesAuthorMetadataAndNotesWhileKeepingSlideXmlAndMediaByteIdentical() {
        val sample = SampleFileFactory.createSamplePptx(tempFolder.root)
        val shaBefore = sha256Hex(sample)

        val preservedPaths = listOf(
            "ppt/presentation.xml",
            "ppt/theme/theme1.xml",
            "ppt/slideMasters/slideMaster1.xml",
            "ppt/slideLayouts/slideLayout1.xml",
            "ppt/slides/slide1.xml",
            "ppt/slides/slide2.xml",
            "ppt/slides/slide3.xml",
            "ppt/media/image1.png",
        )
        val originalEntryBytes = HashMap<String, ByteArray>()
        ZipFile(sample).use { zip ->
            for (path in preservedPaths) {
                originalEntryBytes[path] = zip.getInputStream(zip.getEntry(path)).use { it.readBytes() }
            }
        }

        val sanitized = File(tempFolder.root, "sanitized.pptx")
        val summary = PptxEngine.sanitizePptx(sample, sanitized, stripNotes = true, stripComments = true)

        assertEquals(3, summary.slideCount)
        assertEquals(1, summary.strippedNotesCount)
        assertTrue(summary.cleanedMetadataFiles >= 2)
        assertEquals(shaBefore, sha256Hex(sample))

        ZipFile(sanitized).use { outZip ->
            assertNull(outZip.getEntry("ppt/notesSlides/notesSlide1.xml"))
            assertNull(outZip.getEntry("docProps/custom.xml"))

            val slide1Rels = outZip.getInputStream(outZip.getEntry("ppt/slides/_rels/slide1.xml.rels"))
                .use { it.readBytes().toString(StandardCharsets.UTF_8) }
            assertFalse(slide1Rels.contains("notesSlide"))

            val contentTypes = outZip.getInputStream(outZip.getEntry("[Content_Types].xml"))
                .use { it.readBytes().toString(StandardCharsets.UTF_8) }
            assertFalse(contentTypes.contains("notesSlide1.xml"))

            val coreXml = outZip.getInputStream(outZip.getEntry("docProps/core.xml"))
                .use { it.readBytes().toString(StandardCharsets.UTF_8) }
            assertFalse(coreXml.contains("MELD Studio Author"))
            assertFalse(coreXml.contains("MELD Review Team"))
            assertFalse(coreXml.contains("2026-11-14T10:00:00Z"))

            val appXml = outZip.getInputStream(outZip.getEntry("docProps/app.xml"))
                .use { it.readBytes().toString(StandardCharsets.UTF_8) }
            assertFalse(appXml.contains("MELD Private Workspace"))
            assertFalse(appXml.contains("Architecture Lead"))

            for (path in preservedPaths) {
                val entry = outZip.getEntry(path)
                assertNotNull("Missing preserved entry $path", entry)
                val outBytes = outZip.getInputStream(entry).use { it.readBytes() }
                assertArrayEquals("Entry $path changed during sanitize!", originalEntryBytes[path], outBytes)
            }
        }

        val reparsed = PptxOoxmlParser.parse(sanitized)
        assertEquals(3, reparsed.slides.size)
        assertEquals("", reparsed.slides[0].notesText)
    }

    @Test
    fun customDeckPreservesSldIdLstOrderHiddenFlagGroupScalingAndTableMerges() {
        val customPptx = tempFolder.newFile("custom_order.pptx")
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
              <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
              <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
            </Types>
        """.trimIndent()
        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
            </Relationships>
        """.trimIndent()
        // Note sldIdLst puts slide2 FIRST (rId3) and slide1 SECOND (rId2)
        val presXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:sldIdLst>
                <p:sldId id="257" r:id="rId3"/>
                <p:sldId id="256" r:id="rId2"/>
              </p:sldIdLst>
              <p:sldSz cx="9144000" cy="6858000"/>
            </p:presentation>
        """.trimIndent()
        val presRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
            </Relationships>
        """.trimIndent()
        // slide2 is shown=0 (hidden) and tests nested group 2x scale transform + merged table cell
        val slide2Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" show="0">
              <p:cSld><p:spTree>
                <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                <p:grpSpPr/>
                <p:grpSp>
                  <p:nvGrpSpPr><p:cNvPr id="2" name="ScaledGroup"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr><a:xfrm><a:off x="127000" y="127000"/><a:ext cx="2540000" cy="1270000"/><a:chOff x="0" y="0"/><a:chExt cx="1270000" cy="635000"/></a:xfrm></p:grpSpPr>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="3" name="ScaledChild"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                    <p:spPr><a:xfrm><a:off x="127000" y="127000"/><a:ext cx="635000" cy="254000"/></a:xfrm><a:prstGeom prst="ellipse"/></p:spPr>
                    <p:txBody><a:bodyPr/><a:p><a:r><a:t>Hidden First Slide</a:t></a:r></a:p></p:txBody>
                  </p:sp>
                </p:grpSp>
                <p:graphicFrame>
                  <p:nvGraphicFramePr><p:cNvPr id="4" name="MergedTbl"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>
                  <p:xfrm><a:off x="0" y="1270000"/><a:ext cx="2540000" cy="1270000"/></p:xfrm>
                  <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl>
                    <a:tblGrid><a:gridCol w="1270000"/><a:gridCol w="1270000"/></a:tblGrid>
                    <a:tr h="635000">
                      <a:tc gridSpan="2"><a:txBody><a:bodyPr/><a:p><a:r><a:t>Merged Header</a:t></a:r></a:p></a:txBody></a:tc>
                      <a:tc hMerge="1"/>
                    </a:tr>
                  </a:tbl></a:graphicData></a:graphic>
                </p:graphicFrame>
              </p:spTree></p:cSld>
            </p:sld>
        """.trimIndent()
        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld><p:spTree>
                <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                <p:grpSpPr/>
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="SecondSlideBox"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                  <p:spPr><a:xfrm><a:off x="127000" y="127000"/><a:ext cx="1270000" cy="635000"/></a:xfrm></p:spPr>
                  <p:txBody><a:bodyPr/><a:p><a:r><a:t>Second In Deck</a:t></a:r></a:p></p:txBody>
                </p:sp>
              </p:spTree></p:cSld>
            </p:sld>
        """.trimIndent()

        writeZip(
            customPptx,
            listOf(
                "[Content_Types].xml" to contentTypes.toByteArray(StandardCharsets.UTF_8),
                "_rels/.rels" to rootRels.toByteArray(StandardCharsets.UTF_8),
                "ppt/presentation.xml" to presXml.toByteArray(StandardCharsets.UTF_8),
                "ppt/_rels/presentation.xml.rels" to presRels.toByteArray(StandardCharsets.UTF_8),
                "ppt/slides/slide1.xml" to slide1Xml.toByteArray(StandardCharsets.UTF_8),
                "ppt/slides/slide2.xml" to slide2Xml.toByteArray(StandardCharsets.UTF_8),
            ),
        )

        val pres = PptxOoxmlParser.parse(customPptx)
        // Standard 4:3 (9144000 x 6858000 EMU == 720 x 540 pt)
        assertEquals(720f, pres.slideWidthPt, 0.01f)
        assertEquals(540f, pres.slideHeightPt, 0.01f)
        assertEquals(2, pres.slides.size)

        // Presentation sldIdLst placed slide2 first & marked hidden
        val first = pres.slides[0]
        assertEquals("ppt/slides/slide2.xml", first.entryPath)
        assertTrue(first.hidden)
        assertEquals("Hidden First Slide", first.extractHeading())

        // Group container at (10pt, 10pt) with 2x scale maps child (10,10,50,20) -> (30,30,100,40)
        val childShape = first.elements.filterIsInstance<PptxOoxmlParser.PptxElement.Shape>().first()
        assertEquals("ellipse", childShape.presetGeom)
        assertEquals(30f, childShape.xfrm.x, 0.01f)
        assertEquals(30f, childShape.xfrm.y, 0.01f)
        assertEquals(100f, childShape.xfrm.width, 0.01f)
        assertEquals(40f, childShape.xfrm.height, 0.01f)

        val tbl = first.elements.filterIsInstance<PptxOoxmlParser.PptxElement.Table>().first()
        assertEquals(2, tbl.rows[0].cells[0].colSpan)
        assertFalse(tbl.rows[0].cells[0].mergedAway)
        assertTrue(tbl.rows[0].cells[1].mergedAway)

        assertEquals("ppt/slides/slide1.xml", pres.slides[1].entryPath)
        assertFalse(pres.slides[1].hidden)
    }

    @Test
    fun deobfuscateEmbeddedFontRestoresOpcXorReversedAndMixedEndianKeys() {
        val guid = "{A1B2C3D4-E5F6-0718-293A-4B5C6D7E8F90}"
        val rawGuid = byteArrayOf(
            0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0xD4.toByte(),
            0xE5.toByte(), 0xF6.toByte(), 0x07, 0x18,
            0x29, 0x3A, 0x4B, 0x5C, 0x6D, 0x7E, 0x8F.toByte(), 0x90.toByte(),
        )

        val plainTtf = buildSyntheticSfnt(byteArrayOf(0x00, 0x01, 0x00, 0x00), 64)
        val opcReversedKey = rawGuid.reversedArray()
        val obfTtf = plainTtf.copyOf()
        for (i in 0 until 32) {
            obfTtf[i] = (obfTtf[i].toInt() xor opcReversedKey[i % 16].toInt()).toByte()
        }
        val restoredTtf = PptxOoxmlParser.deobfuscateEmbeddedFont(obfTtf, guid)
        assertArrayEquals(plainTtf, restoredTtf)

        // Also test OpenType 'OTTO' header with Windows mixed-endian GUID layout
        val plainOtf = buildSyntheticSfnt(byteArrayOf('O'.code.toByte(), 'T'.code.toByte(), 'T'.code.toByte(), 'O'.code.toByte()), 48)
        val mixedKey = byteArrayOf(
            rawGuid[3], rawGuid[2], rawGuid[1], rawGuid[0],
            rawGuid[5], rawGuid[4],
            rawGuid[7], rawGuid[6],
            rawGuid[8], rawGuid[9], rawGuid[10], rawGuid[11],
            rawGuid[12], rawGuid[13], rawGuid[14], rawGuid[15],
        ).reversedArray()
        val obfOtf = plainOtf.copyOf()
        for (i in 0 until 32) {
            obfOtf[i] = (obfOtf[i].toInt() xor mixedKey[i % 16].toInt()).toByte()
        }
        val restoredOtf = PptxOoxmlParser.deobfuscateEmbeddedFont(obfOtf, guid)
        assertArrayEquals(plainOtf, restoredOtf)

        // Plain SFNT passes through untouched, invalid payloads return null
        assertArrayEquals(plainTtf, PptxOoxmlParser.deobfuscateEmbeddedFont(plainTtf, null))
        assertNull(PptxOoxmlParser.deobfuscateEmbeddedFont(ByteArray(8), guid))
        assertNull(PptxOoxmlParser.deobfuscateEmbeddedFont(ByteArray(32) { 0x55 }, null))
    }

    @Test
    fun applyColorModifiersComputesAlphaTintAndShadeAccurately() {
        val xmlDoc = PptxOoxmlParser.parseXmlSafe(
            """
            <a:srgbClr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" val="804020">
              <a:alpha val="50000"/>
              <a:tint val="50000"/>
            </a:srgbClr>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        )
        // Base (128, 64, 32), 50% tint toward white -> (192, 160, 144), 50% alpha -> 128 (0x80)
        val out = PptxOoxmlParser.applyColorModifiers(0xFF804020.toInt(), xmlDoc.documentElement)
        val a = (out ushr 24) and 0xFF
        val r = (out ushr 16) and 0xFF
        val g = (out ushr 8) and 0xFF
        val b = out and 0xFF
        assertTrue(a in 127..128)
        assertTrue(r in 191..192)
        assertTrue(g in 159..160)
        assertTrue(b in 143..144)

        val shadeDoc = PptxOoxmlParser.parseXmlSafe(
            """
            <a:srgbClr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" val="FFFFFF">
              <a:shade val="50000"/>
            </a:srgbClr>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        )
        val shaded = PptxOoxmlParser.applyColorModifiers(0xFFFFFFFF.toInt(), shadeDoc.documentElement)
        assertEquals(255, (shaded ushr 24) and 0xFF)
        assertEquals(128, (shaded ushr 16) and 0xFF)
    }

    @Test
    fun securityRejectsXxeDoctypeAndZipTraversalTargets() {
        val xxeXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root>&xxe;</root>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        var threwXxe = false
        try {
            PptxOoxmlParser.parseXmlSafe(xxeXml)
        } catch (_: IllegalArgumentException) {
            threwXxe = true
        }
        assertTrue("Expected XXE DOCTYPE rejection", threwXxe)

        assertNull(PptxOoxmlParser.normalizeOpcTarget("ppt/slides", "../../../etc/passwd"))
        assertEquals(
            "ppt/media/image1.png",
            PptxOoxmlParser.normalizeOpcTarget("ppt/slides", "../media/image1.png"),
        )
    }

    private fun buildSyntheticSfnt(magic: ByteArray, size: Int): ByteArray {
        val bytes = ByteArray(size) { (it * 3 + 7).toByte() }
        System.arraycopy(magic, 0, bytes, 0, 4)
        // Standard SFNT offset table: numTables=10, searchRange=128, entrySelector=3, rangeShift=32
        bytes[4] = 0x00; bytes[5] = 0x0A
        bytes[6] = 0x00; bytes[7] = 0x80.toByte()
        bytes[8] = 0x00; bytes[9] = 0x03
        bytes[10] = 0x00; bytes[11] = 0x20
        // Printable ASCII first table tag: "head"
        bytes[12] = 'h'.code.toByte(); bytes[13] = 'e'.code.toByte()
        bytes[14] = 'a'.code.toByte(); bytes[15] = 'd'.code.toByte()
        return bytes
    }

    private fun writeZip(file: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((path, data) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
