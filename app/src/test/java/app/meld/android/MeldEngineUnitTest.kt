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
        assertTrue(MeldToolId.entries.size >= 38)
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.CONVERT && it.category == MeldCategory.IMAGE_STUDIO })
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.CONVERT && it.category == MeldCategory.PDF_DOCS })
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.TOOLS && it.category == MeldCategory.PDF_DOCS })
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.CONVERT && it.category == MeldCategory.SLIDES })
        assertTrue(MeldToolId.entries.any { it.mode == MeldMode.TOOLS && it.category == MeldCategory.SLIDES })

        assertTrue(MeldToolId.IMG_TO_WEBP.acceptsExtension("png"))
        assertTrue(MeldToolId.IMG_TO_PDF.acceptsExtension("jpg"))
        assertTrue(MeldToolId.PDF_TO_DOCX.acceptsExtension(".PDF"))
        assertTrue(MeldToolId.PDF_TO_HTML.acceptsExtension("pdf"))
        assertTrue(MeldToolId.COMPRESS_PDF.acceptsExtension("pdf"))
        assertTrue(MeldToolId.SPEED_2X.acceptsExtension("wav"))
        assertTrue(MeldToolId.PPTX_TO_PDF.acceptsExtension("pptx"))
        assertTrue(MeldToolId.PPTX_SANITIZE.acceptsExtension(".PPTX"))
        assertTrue(MeldToolId.PACK_ZIP.acceptsExtension("pptx"))
        assertTrue(MeldToolId.PPTX_SANITIZE.followUpTools().contains(MeldToolId.PPTX_TO_PDF))

        val pptxInfo = SelectedFileInfo(
            file = File("MELD_Deck_3Slides.pptx"),
            displayName = "MELD_Deck_3Slides.pptx",
            extension = "pptx",
            sizeBytes = 4096L,
            slideCount = 3
        )
        assertEquals("Deck", pptxInfo.cleanTitle)
        assertTrue(pptxInfo.formattedMetaLine.startsWith("3 slides"))
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

    @Test
    fun buildUniqueZipEntryNames_sanitizesPathsAndDeduplicatesCaseInsensitively() {
        val unique = MediaEngine.buildUniqueZipEntryNames(
            listOf(
                "folder/sub/report.pdf",
                "other\\REPORT.PDF",
                "..",
                ".",
                "notes\u0001.txt",
                "notes.txt",
                "NOTES.txt"
            )
        )
        assertEquals(
            listOf(
                "report.pdf",
                "REPORT_2.PDF",
                "archived_file_3",
                "archived_file_4",
                "notes_.txt",
                "notes.txt",
                "NOTES_2.txt"
            ),
            unique
        )
    }

    @Test
    fun packFilesAndEntriesToZip_writesAllEntriesAndRejectsMissingInput() {
        val tempDir = Files.createTempDirectory("meld_test_multi_zip").toFile()
        try {
            val docA = File(tempDir, "notes.txt").apply { writeText("alpha") }
            val subDir = File(tempDir, "nested").apply { mkdirs() }
            val docB = File(subDir, "notes.txt").apply { writeText("beta") }
            val outZip = File(tempDir, "bundle.zip")

            MediaEngine.packEntriesToZip(
                listOf(
                    MediaEngine.ZipInputEntry(docA, "notes.txt"),
                    MediaEngine.ZipInputEntry(docB, "notes.txt")
                ),
                outZip
            )

            assertTrue(outZip.exists())
            java.util.zip.ZipFile(outZip).use { zip ->
                val names = zip.entries().toList().map { it.name }
                assertEquals(listOf("notes.txt", "notes_2.txt"), names)
                assertEquals("alpha", zip.getInputStream(zip.getEntry("notes.txt")).bufferedReader().readText())
                assertEquals("beta", zip.getInputStream(zip.getEntry("notes_2.txt")).bufferedReader().readText())
            }

            val packFilesZip = File(tempDir, "multi.zip")
            MediaEngine.packFilesToZip(listOf(docA, docB), packFilesZip)
            java.util.zip.ZipFile(packFilesZip).use { zip ->
                assertEquals(2, zip.size())
            }

            val missing = File(tempDir, "missing.txt")
            val abortedZip = File(tempDir, "aborted.zip")
            var threw = false
            try {
                MediaEngine.packFilesToZip(listOf(docA, missing), abortedZip)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
            assertFalse(abortedZip.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun collageGridLayoutAndCenterCropBounds_coverExpectedCounts() {
        assertEquals(2 to 2, ImageEngine.collageGridLayout(1))
        assertEquals(2 to 1, ImageEngine.collageGridLayout(2))
        assertEquals(2 to 2, ImageEngine.collageGridLayout(3))
        assertEquals(2 to 2, ImageEngine.collageGridLayout(4))
        assertEquals(3 to 2, ImageEngine.collageGridLayout(5))
        assertEquals(3 to 2, ImageEngine.collageGridLayout(6))
        assertEquals(3 to 3, ImageEngine.collageGridLayout(7))
        assertEquals(3 to 3, ImageEngine.collageGridLayout(9))

        val wideCrop = ImageEngine.computeCenterCropBounds(srcWidth = 1600, srcHeight = 900)
        assertEquals(ImageEngine.CropBounds(left = 350, top = 0, right = 1250, bottom = 900), wideCrop)

        val tallCrop = ImageEngine.computeCenterCropBounds(srcWidth = 900, srcHeight = 1600)
        assertEquals(ImageEngine.CropBounds(left = 0, top = 350, right = 900, bottom = 1250), tallCrop)

        val squareCrop = ImageEngine.computeCenterCropBounds(srcWidth = 800, srcHeight = 800)
        assertEquals(ImageEngine.CropBounds(left = 0, top = 0, right = 800, bottom = 800), squareCrop)
    }

    @Test
    fun batchQueueMetadata_matchesSupportedToolsAndLimits() {
        assertTrue(MeldToolId.PDF_MERGE.supportsBatchQueue)
        assertTrue(MeldToolId.COLLAGE.supportsBatchQueue)
        assertTrue(MeldToolId.PACK_ZIP.supportsBatchQueue)
        assertFalse(MeldToolId.IMG_TO_PNG.supportsBatchQueue)

        assertEquals(12, MeldToolId.PDF_MERGE.maxBatchCount)
        assertEquals(9, MeldToolId.COLLAGE.maxBatchCount)
        assertEquals(12, MeldToolId.PACK_ZIP.maxBatchCount)
    }

    @Test
    fun floodFillRemoveBg_stripsOuterBackdropAndProtectsEnclosedInteriorWhite() {
        fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

        val w = 7
        val h = 7
        val bgWhite = argb(255, 248, 248, 248)
        val coralOutline = argb(255, 230, 80, 30)
        val interiorHighlight = argb(255, 250, 250, 250)
        val pixels = IntArray(w * h) { bgWhite }

        for (y in 1..5) {
            for (x in 1..5) {
                val isContour = x == 1 || x == 5 || y == 1 || y == 5
                pixels[y * w + x] = if (isContour) coralOutline else interiorHighlight
            }
        }

        val stripped = ImageEngine.applyFloodFillBackgroundRemoval(pixels, w, h, tolerance = 42)
        // Outer perimeter ring of 7x7 minus inner 5x5 = 49 - 25 = 24 background pixels
        assertEquals(24, stripped)
        assertEquals(0, (pixels[0] ushr 24) and 0xFF)
        assertEquals(0, (pixels[6 * w + 6] ushr 24) and 0xFF)
        // Foreground contour and interior white pixels inside the closed outline stay opaque
        assertEquals(255, (pixels[1 * w + 1] ushr 24) and 0xFF)
        assertEquals(255, (pixels[3 * w + 3] ushr 24) and 0xFF)
        assertEquals(255, (pixels[2 * w + 4] ushr 24) and 0xFF)
    }

    @Test
    fun floodFillRemoveBg_handlesSubtleGradientBackdropWithoutEatingDarkSubject() {
        fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

        val w = 7
        val h = 7
        val pixels = IntArray(w * h) { idx ->
            val y = idx / w
            val v = (246 - y * 4).coerceIn(0, 255)
            argb(255, v, v, v)
        }
        val subjectColor = argb(255, 28, 34, 48)
        for (y in 2..4) {
            for (x in 2..4) {
                pixels[y * w + x] = subjectColor
            }
        }

        val stripped = ImageEngine.applyFloodFillBackgroundRemoval(pixels, w, h, tolerance = 35)
        // All 40 surrounding gradient backdrop pixels are stripped; 9 center subject pixels stay opaque
        assertEquals(40, stripped)
        assertEquals(0, (pixels[6 * w] ushr 24) and 0xFF)
        assertEquals(255, (pixels[3 * w + 3] ushr 24) and 0xFF)
    }

    @Test
    fun buildPdfImagePageEntryName_padsPageNumbersAndNormalizesFormat() {
        assertEquals(
            "Lecture_Deck_page_01.png",
            DocumentEngine.buildPdfImagePageEntryName("Lecture Deck", 0, 4, "png")
        )
        assertEquals(
            "Lecture_Deck_page_04.jpg",
            DocumentEngine.buildPdfImagePageEntryName("dir/Lecture Deck", 3, 4, "jpeg")
        )
        assertEquals(
            "Annual_Report_page_005.jpg",
            DocumentEngine.buildPdfImagePageEntryName("Annual Report", 4, 120, "jpg")
        )
    }

    @Test
    fun followUpTools_adaptsToZipOutputForMultiPagePdfImageExport() {
        assertTrue(MeldToolId.PDF_TO_PNG.followUpTools("png").contains(MeldToolId.COMPRESS))
        assertTrue(MeldToolId.PDF_TO_PNG.followUpTools("zip").isEmpty())
    }
}
