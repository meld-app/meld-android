package app.meld.android

import app.meld.android.engine.ImageEngine
import app.meld.android.engine.MediaEngine
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
        val matrix = ImageEngine.buildQrMatrix21("https://github.com/meld-app")
        assertEquals(21, matrix.size)
        assertEquals(21, matrix[0].size)
        assertTrue(matrix[0][0])
        assertTrue(matrix[3][3])
        assertTrue(matrix[0][20])
        assertTrue(matrix[3][17])
        assertTrue(matrix[20][0])
        assertTrue(matrix[17][3])
    }

    @Test
    fun byteFormatter_formatsHumanReadableSizes() {
        assertEquals("512 B", SelectedFileInfo.formatByteCount(512))
        assertEquals("2.0 KB", SelectedFileInfo.formatByteCount(2048))
    }
}
