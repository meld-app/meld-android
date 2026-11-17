package app.meld.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.meld.android.engine.DocumentEngine
import app.meld.android.engine.ImageEngine
import app.meld.android.engine.PptxEngine
import app.meld.android.engine.SampleFileFactory
import app.meld.android.model.RedactionRect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class MeldEnginesInstrumentedTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        tempDir = File(context.cacheDir, "meld_android_test_${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun samplePdf_exportAllPagesZipAndSinglePagePng_succeedWithExactCounts() {
        val samplePdf = SampleFileFactory.createSamplePdf(tempDir)
        assertEquals(4, DocumentEngine.countPdfPages(samplePdf))

        val zipOut = File(tempDir, "all_pages.zip")
        val progressEvents = mutableListOf<Pair<Int, Int>>()
        val zipResult = DocumentEngine.exportPdfToImages(
            inputFile = samplePdf,
            outputFile = zipOut,
            format = "png",
            allPages = true,
            onProgress = { cur, total -> progressEvents += cur to total }
        )

        assertTrue(zipResult.isZipArchive)
        assertEquals(4, zipResult.exportedPageCount)
        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), progressEvents)
        assertTrue(zipOut.exists() && zipOut.length() > 0L)

        ZipFile(zipOut).use { zf ->
            val names = zf.entries().asSequence().map { it.name }.sorted().toList()
            assertEquals(4, names.size)
            assertTrue(names.all { it.endsWith(".png") })
        }

        val page2Png = File(tempDir, "page2.png")
        val singleResult = DocumentEngine.exportPdfToImages(
            inputFile = samplePdf,
            outputFile = page2Png,
            format = "png",
            allPages = false,
            singlePage1Based = 2
        )
        assertEquals(1, singleResult.exportedPageCount)
        assertTrue(!singleResult.isZipArchive)
        val bmp = BitmapFactory.decodeFile(page2Png.absolutePath)
        assertNotNull(bmp)
        assertTrue(bmp!!.width > 0 && bmp.height > 0)
        bmp.recycle()
    }

    @Test
    fun samplePdf_extractMarkdownHtmlDocxAndPlainText_preservesSampleContent() {
        val samplePdf = SampleFileFactory.createSamplePdf(tempDir)

        val mdOut = File(tempDir, "notes.md")
        DocumentEngine.convertPdfToMarkdown(samplePdf, mdOut)
        val mdText = mdOut.readText()
        assertTrue(mdText.contains("SLIDE 01"))
        assertTrue(mdText.contains("Module 1.1"))

        val htmlOut = File(tempDir, "reader.html")
        val htmlProgress = mutableListOf<Pair<Int, Int>>()
        DocumentEngine.convertPdfToHtml(samplePdf, htmlOut) { cur, total ->
            htmlProgress += cur to total
        }
        val htmlText = htmlOut.readText()
        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), htmlProgress)
        assertTrue(htmlText.contains("data:image/jpeg;base64,"))
        assertTrue(htmlText.contains("SLIDE 01"))

        val docxOut = File(tempDir, "doc.docx")
        DocumentEngine.convertPdfToDocx(samplePdf, docxOut)
        ZipFile(docxOut).use { zf ->
            val xml = zf.getInputStream(zf.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(xml.contains("SLIDE 01"))
        }

        val txtOut = File(tempDir, "plain.txt")
        DocumentEngine.extractPdfPlainText(samplePdf, txtOut)
        assertTrue(txtOut.readText().contains("ZERO-CLOUD LOCAL PIPELINE"))
    }

    @Test
    fun samplePdf_mergePdfsAndRedactFlatten_produceValidPdfPages() {
        val samplePdf = SampleFileFactory.createSamplePdf(tempDir)

        val mergedOut = File(tempDir, "merged.pdf")
        val mergeProgress = mutableListOf<Pair<Int, Int>>()
        DocumentEngine.mergePdfs(listOf(samplePdf, samplePdf), mergedOut) { cur, total ->
            mergeProgress += cur to total
        }
        assertEquals(listOf(1 to 2, 2 to 2), mergeProgress)
        assertEquals(8, DocumentEngine.countPdfPages(mergedOut))

        val redactedOut = File(tempDir, "redacted.pdf")
        DocumentEngine.redactAndFlattenPdf(
            inputFile = samplePdf,
            outputFile = redactedOut,
            redactionZones = listOf(RedactionRect(0.10f, 0.24f, 0.90f, 0.34f, "Sensitive"))
        )
        assertEquals(4, DocumentEngine.countPdfPages(redactedOut))

        val thumbs = app.meld.android.ui.components.renderPdfPageThumbnails(samplePdf, maxPages = 6, maxDim = 220)
        assertEquals(4, thumbs.size)
        assertTrue(thumbs.all { it.width > 0 && it.height > 0 && !it.isRecycled })
        thumbs.forEach { it.recycle() }
    }

    @Test
    fun sampleImage_removeBackgroundAndCollage_stripOuterBackdropAndKeepSubject() {
        val sampleImage = SampleFileFactory.createSampleImage(tempDir)

        val alphaOut = File(tempDir, "subject_nobg.png")
        ImageEngine.removeBackground(sampleImage, alphaOut, tolerance = 42)
        val bmp = BitmapFactory.decodeFile(alphaOut.absolutePath)
        assertNotNull(bmp)
        val nonNullBmp = bmp!!
        val cornerAlpha = (nonNullBmp.getPixel(4, 4) ushr 24) and 0xFF
        val centerAlpha = (nonNullBmp.getPixel(nonNullBmp.width / 2, nonNullBmp.height / 2) ushr 24) and 0xFF
        nonNullBmp.recycle()

        assertEquals("Outer corner backdrop should become transparent", 0, cornerAlpha)
        assertEquals("Center subject should stay opaque", 255, centerAlpha)

        val collageOut = File(tempDir, "collage.png")
        val collageProgress = mutableListOf<Pair<Int, Int>>()
        ImageEngine.createPhotoCollage(listOf(sampleImage, sampleImage), collageOut) { cur, total ->
            collageProgress += cur to total
        }
        assertEquals(listOf(1 to 2, 2 to 2), collageProgress)
        val collageBmp = BitmapFactory.decodeFile(collageOut.absolutePath)
        assertNotNull(collageBmp)
        assertTrue(collageBmp!!.width > 0 && collageBmp.height > 0)
        collageBmp.recycle()

        val multiPdfOut = File(tempDir, "multi_photos.pdf")
        val pdfProgress = mutableListOf<Pair<Int, Int>>()
        ImageEngine.convertImagesToPdf(
            listOf(sampleImage, sampleImage, sampleImage),
            multiPdfOut
        ) { cur, total ->
            pdfProgress += cur to total
        }
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), pdfProgress)
        assertEquals(3, DocumentEngine.countPdfPages(multiPdfOut))
    }

    @Test
    fun samplePptx_convertToPdfAndSlidesZip_preserveSlidesAndReportProgress() {
        val samplePptx = SampleFileFactory.createSamplePptx(tempDir)

        val pdfOut = File(tempDir, "deck.pdf")
        val pdfProgress = mutableListOf<Pair<Int, Int>>()
        val pdfSummary = PptxEngine.convertPptxToPdf(
            inputFile = samplePptx,
            outputFile = pdfOut,
            fontCacheDir = tempDir,
            onProgress = { cur, total -> pdfProgress += cur to total }
        )
        assertEquals(3, pdfSummary.slideCount)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), pdfProgress)
        assertEquals(3, DocumentEngine.countPdfPages(pdfOut))

        val slidesZip = File(tempDir, "slides.zip")
        val imgProgress = mutableListOf<Pair<Int, Int>>()
        val zipSummary = PptxEngine.renderPptxSlidesToImages(
            inputFile = samplePptx,
            outputFile = slidesZip,
            format = Bitmap.CompressFormat.PNG,
            allSlides = true,
            fontCacheDir = tempDir,
            onProgress = { cur, total -> imgProgress += cur to total }
        )
        assertTrue(zipSummary.isZipArchive)
        assertEquals(3, zipSummary.exportedSlideCount)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), imgProgress)
        ZipFile(slidesZip).use { zf ->
            assertEquals(3, zf.entries().asSequence().count())
        }

        val slide2Png = File(tempDir, "slide2.png")
        val singleSummary = PptxEngine.renderPptxSlidesToImages(
            inputFile = samplePptx,
            outputFile = slide2Png,
            format = Bitmap.CompressFormat.PNG,
            allSlides = false,
            singleSlide1Based = 2,
            fontCacheDir = tempDir
        )
        assertEquals(2, singleSummary.resolvedSingleSlide)
        assertTrue(!singleSummary.isZipArchive)
        val slideBmp = BitmapFactory.decodeFile(slide2Png.absolutePath)
        assertNotNull(slideBmp)
        assertTrue(slideBmp!!.width > 0 && slideBmp.height > 0)
        slideBmp.recycle()

        val htmlOut = File(tempDir, "deck.html")
        val htmlProgress = mutableListOf<Pair<Int, Int>>()
        val htmlSummary = PptxEngine.convertPptxToHtml(
            inputFile = samplePptx,
            outputFile = htmlOut,
            fontCacheDir = tempDir,
            onProgress = { cur, total -> htmlProgress += cur to total }
        )
        assertEquals(3, htmlSummary.slideCount)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), htmlProgress)
        val htmlText = htmlOut.readText()
        assertTrue(htmlText.contains("data:image/jpeg;base64,"))
        assertTrue(htmlText.contains("Speaker Notes"))
    }
}
