package app.meld.android

import app.meld.android.engine.DocumentEngine
import app.meld.android.engine.ImageEngine
import app.meld.android.engine.MediaEngine
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipFile

class MeldEngineUnitTest {

    @Test
    fun toolCatalog_hasFullMacParitySuite() {
        assertTrue(MeldToolId.entries.size >= 29)
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.CONVERT && it.category == MeldCategory.IMAGE_STUDIO })
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.CONVERT && it.category == MeldCategory.PDF_DOCS })
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.TOOLS && it.category == MeldCategory.PDF_DOCS })

        assertTrue(MeldToolId.IMG_TO_WEBP.acceptsExtension("png"))
        assertTrue(MeldToolId.IMG_TO_PDF.acceptsExtension("jpg"))
        assertTrue(MeldToolId.PDF_TO_DOCX.acceptsExtension(".PDF"))
        assertTrue(MeldToolId.PDF_TO_HTML.acceptsExtension("pdf"))
        assertTrue(MeldToolId.COMPRESS_PDF.acceptsExtension("pdf"))
        assertTrue(MeldToolId.SPEED_2X.acceptsExtension("wav"))
    }

    @Test
    fun csvToJson_convertsRowsAndPreservesNumbers() {
        val sampleCsv = """
            Name,Score,Status
            Alice,95,Passed
            Bob,88.5,"Passed, Honors"
        """.trimIndent()

        val (json, rowCount) = MediaEngine.csvStringToJson(sampleCsv)
        assertEquals(2, rowCount)
        assertTrue(json.contains("\"Name\": \"Alice\""))
        assertTrue(json.contains("\"Score\": 95"))
        assertTrue(json.contains("\"Status\": \"Passed, Honors\""))
    }

    @Test
    fun csvToMarkdownTable_formatsValidGithubTable() {
        val tempDir = Files.createTempDirectory("meld_test_md").toFile()
        val csvFile = File(tempDir, "grades.csv").apply {
            writeText("Student,Grade\nOm,A+\nAlice,A")
        }
        val mdFile = File(tempDir, "grades.md")
        val summary = MediaEngine.convertCsvToMarkdownTable(csvFile, mdFile)

        val mdText = mdFile.readText()
        assertTrue(summary.contains("2 CSV row(s)"))
        assertTrue(mdText.contains("| Student | Grade |"))
        assertTrue(mdText.contains("| --- | --- |"))
        assertTrue(mdText.contains("| Om | A+ |"))
        tempDir.deleteRecursively()
    }

    @Test
    fun speed2xAudio_producesValidHalfLengthWav() {
        val tempDir = Files.createTempDirectory("meld_test_wav").toFile()
        val inWav = File(tempDir, "input.wav")
        // 44 byte header + 2000 bytes PCM payload (1000 samples)
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        val payload = ByteArray(2000) { idx -> (idx % 127).toByte() }
        inWav.writeBytes(header + payload)

        val outWav = File(tempDir, "output_2x.wav")
        val summary = MediaEngine.speed2xAudio(inWav, outWav)

        assertTrue(summary.contains("2.0x speed"))
        assertTrue(outWav.exists())
        val outBytes = outWav.readBytes()
        assertEquals('R'.code.toByte(), outBytes[0])
        assertEquals('I'.code.toByte(), outBytes[1])
        // 44 header + 500 16-bit samples = 1044 bytes
        assertEquals(1044, outBytes.size)
        tempDir.deleteRecursively()
    }

    @Test
    fun packToZip_archivesFileCleanly() {
        val tempDir = Files.createTempDirectory("meld_test_zip").toFile()
        val src = File(tempDir, "hello.txt").apply { writeText("MELD Offline Packaging") }
        val zipOut = File(tempDir, "hello.zip")

        MediaEngine.packToZip(src, zipOut)
        assertTrue(zipOut.exists())
        ZipFile(zipOut).use { zf ->
            val entry = zf.getEntry("hello.txt")
            assertTrue(entry != null)
            val content = zf.getInputStream(entry).bufferedReader().readText()
            assertEquals("MELD Offline Packaging", content)
        }
        tempDir.deleteRecursively()
    }

    @Test
    fun waveformEnvelope_normalizesIntoValidRange() {
        val dummyPcm = ByteArray(1024) { idx -> ((idx * 17) % 255).toByte() }
        val bars = MediaEngine.extractAmplitudeEnvelope(dummyPcm, 48)
        assertEquals(48, bars.size)
        assertTrue(bars.all { it in 0.05f..1.0f })
    }

    @Test
    fun qrMatrix21_preservesFinderCorners() {
        val v1Matrix = ImageEngine.buildQrMatrix21("https://meld.app")
        assertEquals(21, v1Matrix.size)
        assertEquals(21, v1Matrix[0].size)
        assertTrue(v1Matrix[0][0])
        assertTrue(v1Matrix[3][3])
        assertTrue(v1Matrix[0][20])
        assertTrue(v1Matrix[3][17])
        assertTrue(v1Matrix[20][0])
        assertTrue(v1Matrix[17][3])

        val v2Matrix = ImageEngine.buildQrMatrix21("https://github.com/meld-app")
        assertEquals(25, v2Matrix.size)
        assertEquals(25, v2Matrix[0].size)
        assertTrue(v2Matrix[0][0])
        assertTrue(v2Matrix[0][24])
        assertTrue(v2Matrix[24][0])
    }

    @Test
    fun byteFormatter_formatsHumanReadableSizes() {
        assertEquals("512 B", SelectedFileInfo.formatByteCount(512))
        assertEquals("2.0 KB", SelectedFileInfo.formatByteCount(2048))
    }

    @Test
    fun emptyExtension_isNotMarkedCompatible() {
        assertFalse(MeldToolId.COMPRESS_PDF.acceptsExtension(""))
        assertFalse(MeldToolId.REMOVE_BG.acceptsExtension("pdf"))
    }

    @Test
    fun speed2xAudio_preservesStereo44kHzAndFindsDataSubchunk() {
        val tempDir = Files.createTempDirectory("meld_test_stereo").toFile()
        val inWav = File(tempDir, "stereo44.wav")
        // Build RIFF WAVE header + JUNK chunk + fmt chunk + data chunk (44100 Hz, 2ch, 16-bit)
        val payload = ByteArray(1600) { idx -> (idx % 99).toByte() }
        val junkChunkSize = 12
        val totalBytes = 44 + 8 + junkChunkSize + payload.size
        val bb = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(totalBytes - 8)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1) // PCM
        bb.putShort(2) // Stereo
        bb.putInt(44100)
        bb.putInt(44100 * 4)
        bb.putShort(4)
        bb.putShort(16)
        bb.put("JUNK".toByteArray())
        bb.putInt(junkChunkSize)
        bb.put(ByteArray(junkChunkSize))
        bb.put("data".toByteArray())
        bb.putInt(payload.size)
        bb.put(payload)
        inWav.writeBytes(bb.array())

        val outWav = File(tempDir, "stereo_out.wav")
        val summary = MediaEngine.speed2xAudio(inWav, outWav)
        assertTrue(summary.contains("44100Hz"))
        assertTrue(summary.contains("Stereo"))

        val outBytes = outWav.readBytes()
        val outHdr = ByteBuffer.wrap(outBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, outHdr.getShort(22).toInt())
        assertEquals(44100, outHdr.getInt(24))
        // 400 stereo frames * 4 bytes = 1600 -> halved -> 200 frames * 4 bytes = 800 bytes + 44 header
        assertEquals(844, outBytes.size)
        tempDir.deleteRecursively()
    }

    @Test
    fun followUpTools_neverSuggestsSelf() {
        MeldToolId.entries.forEach { tool ->
            assertFalse("${tool.name} should not suggest itself", tool.followUpTools().contains(tool))
        }
    }

    @Test
    fun speed2xAudio_handlesLargeMalformedRiffChunkSizeWithoutOverflow() {
        val tempDir = Files.createTempDirectory("meld_test_riff_overflow").toFile()
        val badWav = File(tempDir, "overflow.wav")
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(0x7FFFFFF8)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(22050)
        bb.putInt(44100)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(0)
        badWav.writeBytes(bb.array())

        val outWav = File(tempDir, "overflow_out.wav")
        val summary = MediaEngine.speed2xAudio(badWav, outWav)
        assertTrue(summary.contains("2.0x speed"))
        assertTrue(outWav.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun csvStringToJson_escapesBackslashesControlCharsAndNonFiniteNumbers() {
        val csv = "k,path,f1,f2,zero\n1,C:\\\\dir\\a,NaN,Infinity,0"
        val (json, count) = MediaEngine.csvStringToJson(csv)
        assertEquals(1, count)
        assertTrue(json.contains("\"NaN\""))
        assertTrue(json.contains("\"Infinity\""))
        assertTrue(json.contains("\"zero\": 0"))
        assertTrue(json.contains("C:\\\\"))
    }

    @Test
    fun parseTabularRows_preservesRfc4180QuotesMultilineAndTsv() {
        val rfc = MediaEngine.parseTabularRows("\"He said \"\"hi\"\"\",next\n\"line1\nline2\",ok")
        assertEquals(2, rfc.size)
        assertEquals("He said \"hi\"", rfc[0][0])
        assertEquals("line1\nline2", rfc[1][0])

        val tsv = MediaEngine.parseTabularRows("c1\tc2\nv1\tv2", '\t')
        assertEquals(2, tsv.size)
        assertEquals(listOf("v1", "v2"), tsv[1])
    }

    @Test
    fun csvToMarkdownTable_escapesPipesAndLinks() {
        val tempDir = Files.createTempDirectory("meld_test_md_inj").toFile()
        val csvFile = File(tempDir, "notes.csv").apply {
            writeText("User,Note\nAlice,\"a | b | [link](https://example.invalid)\"")
        }
        val mdFile = File(tempDir, "notes.md")
        MediaEngine.convertCsvToMarkdownTable(csvFile, mdFile)
        val mdText = mdFile.readText()
        assertTrue(mdText.contains("a \\| b \\| \\[link\\](https://example.invalid)"))
        tempDir.deleteRecursively()
    }

    @Test
    fun waveformEnvelope_evenStepPreventsByteSwappedAmplitudeSpike() {
        val wavOdd = ByteArray(44 + 288)
        wavOdd[0] = 'R'.code.toByte()
        wavOdd[1] = 'I'.code.toByte()
        for (i in 44 until wavOdd.size step 2) {
            wavOdd[i] = 1
            wavOdd[i + 1] = 0
        }
        val bars = MediaEngine.extractAmplitudeEnvelope(wavOdd, 96)
        assertEquals(bars[0], bars[1], 0.001f)
        assertEquals(bars[1], bars[2], 0.001f)
    }

    @Test
    fun qrMatrix_preservesWhiteFinderSeparatorsAndEncodesDistinctInputs() {
        val qr1 = ImageEngine.buildQrMatrix21("https://github.com/meld-app")
        val qr2 = ImageEngine.buildQrMatrix21("https://example.com/other")
        val n = qr1.size
        assertTrue(n >= 21)
        // Top-left 1-module white separator row (row 7 cols 0..7) and col (col 7 rows 0..7)
        for (i in 0..7) {
            assertFalse("Separator cell (7,$i) must be white", qr1[7][i])
            assertFalse("Separator cell ($i,7) must be white", qr1[i][7])
        }
        var diff = 0
        for (r in 0 until minOf(qr1.size, qr2.size)) {
            for (c in 0 until minOf(qr1.size, qr2.size)) {
                if (qr1[r][c] != qr2[r][c]) diff++
            }
        }
        assertTrue("QR payload variation should change matrix bits", diff > 20)
    }

    @Test
    fun packToZip_rejectsMissingInputWithoutCreatingEmptyZip() {
        val tempDir = Files.createTempDirectory("meld_test_missing_zip").toFile()
        val missing = File(tempDir, "does_not_exist.txt")
        val outZip = File(tempDir, "out.zip")
        var threw = false
        try {
            MediaEngine.packToZip(missing, outZip)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(outZip.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun scrubPdfInfoValues_erasesProducerWhilePreservingByteLength() {
        val fakePdf = "%PDF-1.4\n1 0 obj\n<< /Producer (Skia/PDF m120) /CreationDate (D:20261112) >>\nendobj\n"
            .toByteArray(StandardCharsets.ISO_8859_1)
        val scrubbed = DocumentEngine.scrubPdfInfoValues(fakePdf)
        assertEquals(fakePdf.size, scrubbed.size)
        val asText = String(scrubbed, StandardCharsets.ISO_8859_1)
        assertFalse(asText.contains("Skia/PDF"))
        assertFalse(asText.contains("20261112"))
    }

    @Test
    fun escapeXml_stripsIllegalXml10ControlChars() {
        val raw = "A\u0001\u0008B&C<D>\t\n"
        val escaped = DocumentEngine.escapeXml(raw)
        assertEquals("AB&amp;C&lt;D&gt;\t\n", escaped)
    }

    @Test
    fun extensionParity_allImageAndAudioToolsAligned() {
        listOf(
            MeldToolId.IMG_TO_JPG,
            MeldToolId.IMG_TO_PNG,
            MeldToolId.IMG_TO_WEBP,
            MeldToolId.IMG_TO_BMP,
            MeldToolId.IMG_TO_TIFF,
            MeldToolId.IMG_TO_PDF
        ).forEach { tool ->
            assertTrue("${tool.name} should accept .tif", tool.acceptsExtension("tif"))
        }
        assertEquals(MeldToolId.VISUALIZE.supportedExtensions, MeldToolId.SPEED_2X.supportedExtensions)
    }
}
