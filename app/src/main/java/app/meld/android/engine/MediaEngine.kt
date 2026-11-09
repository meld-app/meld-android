package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

object MediaEngine {

    /**
     * Extracts amplitude envelope bars from an audio/media file and renders a 1920x600
     * studio dark-mode waveform PNG with MELD orange-amber gradient bars.
     */
    fun renderAudioWaveformPng(inputFile: File, outputFile: File, barCount: Int = 96): String {
        val rawBytes = if (inputFile.exists()) inputFile.readBytes() else ByteArray(0)
        val amplitudes = extractAmplitudeEnvelope(rawBytes, barCount)

        val width = 1920
        val height = 600
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Studio dark background
        canvas.drawColor(Color.rgb(13, 14, 18))

        // Subtle horizontal studio grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 155, 163, 181)
            strokeWidth = 1f
        }
        val centerY = height * 0.54f
        canvas.drawLine(64f, centerY, width - 64f, centerY, gridPaint)
        canvas.drawLine(64f, centerY - 140f, width - 64f, centerY - 140f, gridPaint)
        canvas.drawLine(64f, centerY + 140f, width - 64f, centerY + 140f, gridPaint)

        // Header metadata
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 247, 250)
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 71)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText("MELD AUDIO WAVEFORM STUDIO • ${inputFile.name}", 64f, 54f, titlePaint)
        canvas.drawText("${amplitudes.size} BANDS • 1920x600 HD PNG • 100% LOCAL", 64f, 84f, metaPaint)

        // Waveform gradient paint
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                centerY - 210f,
                0f,
                centerY + 210f,
                intArrayOf(
                    Color.rgb(255, 179, 71),
                    Color.rgb(255, 106, 43),
                    Color.rgb(191, 90, 242)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val leftPad = 72f
        val rightPad = 72f
        val usableWidth = width - leftPad - rightPad
        val slotWidth = usableWidth / amplitudes.size
        val barWidth = max(4f, slotWidth * 0.62f)
        val maxHalfHeight = 200f

        for (i in amplitudes.indices) {
            val amp = amplitudes[i].coerceIn(0.06f, 1.0f)
            val halfH = amp * maxHalfHeight
            val x = leftPad + i * slotWidth
            val rect = RectF(x, centerY - halfH, x + barWidth, centerY + halfH)
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint)
        }

        FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        return "Rendered 1920x600 studio waveform PNG (${amplitudes.size} amplitude bands)"
    }

    /**
     * Converts CSV content into a formatted JSON array of objects.
     */
    fun convertCsvToJson(inputFile: File, outputFile: File): String {
        val text = inputFile.readText()
        val jsonResult = csvStringToJson(text)
        outputFile.writeText(jsonResult.first)
        return "Converted ${jsonResult.second} CSV data row(s) into structured JSON"
    }

    internal fun extractAmplitudeEnvelope(bytes: ByteArray, barCount: Int): FloatArray {
        val count = max(8, barCount)
        val out = FloatArray(count)

        if (bytes.size < 128) {
            for (i in 0 until count) {
                out[i] = (0.25f + 0.65f * abs(sin(i * 0.24))).toFloat()
            }
            return out
        }

        // Skip standard 44-byte WAV header if present
        val startOffset = if (bytes.size > 44 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte()
        ) 44 else 0

        val payloadLen = max(1, bytes.size - startOffset)
        val step = max(2, payloadLen / count)

        var maxPeak = 1f
        for (i in 0 until count) {
            val chunkStart = startOffset + (i * step)
            val chunkEnd = (chunkStart + step).coerceAtMost(bytes.size - 1)
            var sumAbs = 0L
            var samples = 0
            var idx = chunkStart
            while (idx + 1 < chunkEnd) {
                val low = bytes[idx].toInt() and 0xFF
                val high = bytes[idx + 1].toInt()
                val pcm16 = (high shl 8) or low
                sumAbs += abs(pcm16)
                samples++
                idx += 2
            }
            val avg = if (samples > 0) (sumAbs.toFloat() / samples) else 1024f
            out[i] = avg
            if (avg > maxPeak) maxPeak = avg
        }

        for (i in 0 until count) {
            out[i] = (out[i] / maxPeak).coerceIn(0.08f, 1.0f)
        }
        return out
    }

    internal fun csvStringToJson(csvText: String): Pair<String, Int> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return "[]" to 0

        val headers = parseCsvRow(lines.first())
        val rows = lines.drop(1).map { parseCsvRow(it) }

        val sb = StringBuilder()
        sb.append("[\n")
        for ((rIdx, row) in rows.withIndex()) {
            sb.append("  {\n")
            for ((cIdx, header) in headers.withIndex()) {
                val rawVal = row.getOrNull(cIdx) ?: ""
                val escapedKey = header.replace("\"", "\\\"")
                val numericVal = rawVal.toDoubleOrNull()
                val formattedVal = if (numericVal != null && !rawVal.startsWith("0")) {
                    rawVal
                } else {
                    "\"${rawVal.replace("\"", "\\\"")}\""
                }
                val comma = if (cIdx < headers.lastIndex) "," else ""
                sb.append("    \"$escapedKey\": $formattedVal$comma\n")
            }
            val rowComma = if (rIdx < rows.lastIndex) "," else ""
            sb.append("  }$rowComma\n")
        }
        sb.append("]\n")
        return sb.toString() to rows.size
    }

    internal fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}

