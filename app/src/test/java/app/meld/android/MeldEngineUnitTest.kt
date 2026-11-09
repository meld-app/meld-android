package app.meld.android

import app.meld.android.engine.ImageEngine
import app.meld.android.engine.MediaEngine
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeldEngineUnitTest {

    @Test
    fun toolCatalog_hasAllNineCoreTools() {
        assertEquals(9, MeldToolId.entries.size)
        assertTrue(MeldToolId.REMOVE_BG.acceptsExtension("png"))
        assertTrue(MeldToolId.HANDOUT.acceptsExtension(".PDF"))
        assertTrue(MeldToolId.VISUALIZE.acceptsExtension("wav"))
        assertTrue(MeldToolId.TABLE_CONVERT.acceptsExtension("csv"))
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
        // Top-left finder outer corner & center bit must be true
        assertTrue(matrix[0][0])
        assertTrue(matrix[3][3])
        // Top-right finder outer corner & center bit must be true
        assertTrue(matrix[0][20])
        assertTrue(matrix[3][17])
        // Bottom-left finder outer corner & center bit must be true
        assertTrue(matrix[20][0])
        assertTrue(matrix[17][3])
    }

    @Test
    fun byteFormatter_formatsHumanReadableSizes() {
        assertEquals("512 B", SelectedFileInfo.formatByteCount(512))
        assertEquals("2.0 KB", SelectedFileInfo.formatByteCount(2048))
    }
}

