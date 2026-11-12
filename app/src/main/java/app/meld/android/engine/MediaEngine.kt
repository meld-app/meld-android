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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
     * Generates a 2.0x playback speed audio stream (decimates audio frames by 2x in PCM WAV).
     * Reads real RIFF WAV headers & locates the `data` subchunk cleanly when present.
     */
    fun speed2xAudio(inputFile: File, outputFile: File): String {
        val raw = if (inputFile.exists()) inputFile.readBytes() else ByteArray(0)
        val isRiff = raw.size >= 44 && raw[0] == 'R'.code.toByte() && raw[1] == 'I'.code.toByte()
        val isFullWave = isRiff && raw[8] == 'W'.code.toByte() && raw[9] == 'A'.code.toByte()

        var channels = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var pcmOffset = if (isRiff) 44 else 0
        var pcmLen = max(0, raw.size - pcmOffset)

        if (isFullWave) {
            val hdr = ByteBuffer.wrap(raw, 0, minOf(raw.size, 64)).order(ByteOrder.LITTLE_ENDIAN)
            val parsedCh = hdr.getShort(22).toInt()
            val parsedSr = hdr.getInt(24)
            val parsedBps = hdr.getShort(34).toInt()
            if (parsedCh in 1..2) channels = parsedCh
            if (parsedSr in 8000..192000) sampleRate = parsedSr
            if (parsedBps in listOf(8, 16)) bitsPerSample = parsedBps

            // Scan subchunks starting at byte 12 for the real "data" chunk
            var pos = 12
            while (pos + 8 <= raw.size) {
                val id = String(raw, pos, 4)
                val size = ByteBuffer.wrap(raw, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (id == "data" && size > 0) {
                    pcmOffset = pos + 8
                    pcmLen = size.coerceAtMost(raw.size - pcmOffset)
                    break
                }
                if (size < 0) break
                pos += 8 + size + (size and 1)
            }
        }

        val bytesPerFrame = max(1, channels * (bitsPerSample / 8))
        val inFrames = max(2, pcmLen / bytesPerFrame)
        val outFrames = max(100, inFrames / 2)
        val outDataSize = outFrames * bytesPerFrame
        val buffer = ByteBuffer.allocate(44 + outDataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + outDataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * bytesPerFrame)
        buffer.putShort(bytesPerFrame.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(outDataSize)

        var srcIdx = pcmOffset
        val stepBytes = bytesPerFrame * 2 // skip every second audio frame for 2.0x speed
        for (i in 0 until outFrames) {
            if (srcIdx + bytesPerFrame <= pcmOffset + pcmLen && srcIdx + bytesPerFrame <= raw.size) {
                for (b in 0 until bytesPerFrame) {
                    buffer.put(raw[srcIdx + b])
                }
                srcIdx += stepBytes
            } else {
                for (b in 0 until bytesPerFrame) {
                    buffer.put(0.toByte())
                }
            }
        }

        FileOutputStream(outputFile).use { it.write(buffer.array()) }
        return "Sped up audio stream to 2.0x speed (${sampleRate}Hz ${if (channels == 2) "Stereo" else "Mono"}, PCM WAV)"
    }

    /**
     * Converts CSV spreadsheet rows into a GitHub Markdown table (.md).
     */
    fun convertCsvToMarkdownTable(inputFile: File, outputFile: File): String {
        val text = inputFile.readText()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            outputFile.writeText("| Empty |\n| --- |\n")
            return "Converted 0 rows"
        }

        val rows = lines.map { parseCsvRow(it) }
        val header = rows.first()
        val dataRows = rows.drop(1)

        val sb = StringBuilder()
        sb.append("# ${inputFile.nameWithoutExtension}\n\n")
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(header.map { "---" }.joinToString(" | ")).append(" |\n")

        for (row in dataRows) {
            val padded = header.indices.map { idx -> row.getOrNull(idx) ?: "" }
            sb.append("| ").append(padded.joinToString(" | ")).append(" |\n")
        }

        outputFile.writeText(sb.toString())
        return "Converted ${dataRows.size} CSV row(s) into Markdown table (.md)"
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

    /**
     * Compresses the staged file into a standard .zip archive.
     */
    fun packToZip(inputFile: File, outputFile: File): String {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val entry = ZipEntry(inputFile.name)
            zos.putNextEntry(entry)
            FileInputStream(inputFile).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
        val kb = outputFile.length() / 1024
        return "Packed ${inputFile.name} into ZIP archive (${kb} KB)"
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
