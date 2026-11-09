package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object SampleFileFactory {

    /**
     * Generates a 4-page multi-slide PDF in cacheDir for immediate one-tap testing
     * of LOCK/STAMP, HANDOUT (2-up/4-up), SPLIT PDF, and REDACT.
     */
    fun createSamplePdf(dir: File): File {
        val outFile = File(dir, "MELD_Lecture_Deck_4Pages.pdf")
        val doc = PdfDocument()

        val titles = listOf(
            "SLIDE 01 • SYSTEMS ARCHITECTURE",
            "SLIDE 02 • ZERO-CLOUD LOCAL PIPELINE",
            "SLIDE 03 • BINARY-SEARCH COMPRESSION",
            "SLIDE 04 • STUDENT HANDOUT & REDACTION"
        )

        val bgPaint = Paint().apply { color = Color.WHITE }
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 24, 33)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 52, 68)
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 40, 40)
            textSize = 12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        for ((idx, slideTitle) in titles.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)
            canvas.drawRoundRect(RectF(36f, 40f, 559f, 150f), 16f, 16f, bannerPaint)
            canvas.drawRoundRect(RectF(36f, 40f, 52f, 150f), 8f, 8f, accentPaint)
            canvas.drawText(slideTitle, 72f, 102f, titlePaint)

            // Simulated sensitive ID zone at top-right (targeted by REDACT)
            canvas.drawText("DOC-ID: MELD-9940-${idx + 101}", 345f, 118f, idPaint)

            // Diagram cards
            for (card in 0..2) {
                val top = 195f + card * 165f
                val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(228, 232, 240)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(RectF(44f, top, 551f, top + 135f), 14f, 14f, cardBorder)
                canvas.drawText(
                    "Module ${idx + 1}.${card + 1} — Hardware-Accelerated Native Execution",
                    64f,
                    top + 42f,
                    bodyPaint
                )
                canvas.drawText(
                    "• 100% On-device PDF, Image, Audio & Table processing with zero telemetry.",
                    64f,
                    top + 76f,
                    bodyPaint
                )
                canvas.drawText(
                    "• Built with Native Kotlin + Jetpack Compose for Android.",
                    64f,
                    top + 104f,
                    bodyPaint
                )
            }

            // Footer zone (also covered by redaction test)
            canvas.drawText(
                "CONFIDENTIAL TOKEN: USR-KEY-8821-MELD • PAGE ${idx + 1} OF 4",
                80f,
                680f,
                idPaint
            )

            doc.finishPage(page)
        }

        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * Generates a high-resolution studio subject image on a clean chroma background
     * for testing REMOVE BG and COMPRESS.
     */
    fun createSampleImage(dir: File): File {
        val outFile = File(dir, "MELD_Studio_Subject.png")
        val width = 1080
        val height = 1080
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Uniform studio backdrop (easily isolated by REMOVE BG)
        canvas.drawColor(Color.rgb(26, 32, 46))

        // Foreground vibrant geometric emblem in the center
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 71)
            style = Paint.Style.FILL
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 24, 33)
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawRoundRect(RectF(220f, 220f, 860f, 860f), 140f, 140f, outerPaint)
        canvas.drawCircle(540f, 540f, 230f, innerPaint)
        canvas.drawRoundRect(RectF(390f, 465f, 690f, 615f), 36f, 36f, corePaint)
        canvas.drawText("MELD", 540f, 560f, labelPaint)

        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return outFile
    }

    /**
     * Generates a synthetic 16-bit PCM WAV file for testing the VISUALIZE waveform tool.
     */
    fun createSampleAudioWav(dir: File): File {
        val outFile = File(dir, "MELD_Studio_Synth.wav")
        val sampleRate = 22050
        val numSamples = sampleRate * 2 // 2 seconds
        val dataSize = numSamples * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF WAVE header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = (0.35 + 0.65 * kotlin.math.abs(sin(t * 4.5)))
            val wave = sin(2.0 * Math.PI * 220.0 * t) * 0.6 + sin(2.0 * Math.PI * 440.0 * t) * 0.4
            val sample = (wave * envelope * 28000).toInt().coerceIn(-32767, 32767).toShort()
            buffer.putShort(sample)
        }

        FileOutputStream(outFile).use { it.write(buffer.array()) }
        return outFile
    }

    /**
     * Generates a structured CSV spreadsheet for testing TABLE -> JSON conversion.
     */
    fun createSampleCsv(dir: File): File {
        val outFile = File(dir, "MELD_Benchmark_Report.csv")
        val csv = """
            Tool,Category,Platform,Offline,AvgSpeedMs
            REMOVE BG,Image Studio,Android + macOS,true,145
            COMPRESS,Image Studio,Android + macOS,true,92
            LOCK / STAMP,PDF & Docs,Android + macOS,true,180
            HANDOUT 4-UP,PDF & Docs,Android + macOS,true,210
            VISUALIZE,Audio & Video,Android + macOS,true,115
        """.trimIndent()
        outFile.writeText(csv)
        return outFile
    }
}

