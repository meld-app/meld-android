package app.meld.android.engine

import java.io.File

/**
 * JNI bridge to the optional `libmeld_native.so` Rust core for lossless structural PDF
 * manipulation, zero-re-encode image metadata stripping, and off-heap background removal.
 *
 * When the native library is unavailable (e.g., JVM host unit tests or unsupported ABIs),
 * callers transparently fall back to the pure-Kotlin implementations.
 */
object MeldNativeBridge {

    val isAvailable: Boolean = runCatching {
        System.loadLibrary("meld_native")
        true
    }.getOrDefault(false)

    @JvmStatic
    private external fun nativeMergePdfs(inputPaths: Array<String>, outputPath: String): Int

    @JvmStatic
    private external fun nativeSplitPdf(
        inputPath: String,
        startPage1Based: Int,
        endPage1Based: Int,
        outputPath: String
    ): Int

    @JvmStatic
    private external fun nativeSanitizePdf(inputPath: String, outputPath: String): Int

    @JvmStatic
    private external fun nativeStripImageMetadataLossless(
        inputPath: String,
        outputPath: String
    ): Int

    @JvmStatic
    private external fun nativeRemoveBackgroundRgba(
        pixels: IntArray,
        width: Int,
        height: Int,
        tolerance: Int
    ): Int

    fun mergePdfs(inputFiles: List<File>, outputFile: File): Int? {
        if (!isAvailable || inputFiles.size < 2) return null
        return runCatching {
            val paths = inputFiles.map { it.absolutePath }.toTypedArray()
            val pages = nativeMergePdfs(paths, outputFile.absolutePath)
            if (pages > 0 && outputFile.isFile && outputFile.length() > 0L) pages else null
        }.getOrNull()
    }

    fun splitPdf(
        inputFile: File,
        startPage1Based: Int,
        endPage1Based: Int,
        outputFile: File
    ): Int? {
        if (!isAvailable) return null
        return runCatching {
            val pages = nativeSplitPdf(
                inputFile.absolutePath,
                startPage1Based,
                endPage1Based,
                outputFile.absolutePath
            )
            if (pages > 0 && outputFile.isFile && outputFile.length() > 0L) pages else null
        }.getOrNull()
    }

    fun sanitizePdf(inputFile: File, outputFile: File): Int? {
        if (!isAvailable) return null
        return runCatching {
            val pages = nativeSanitizePdf(inputFile.absolutePath, outputFile.absolutePath)
            if (pages > 0 && outputFile.isFile && outputFile.length() > 0L) pages else null
        }.getOrNull()
    }

    fun stripImageMetadataLossless(inputFile: File, outputFile: File): Int? {
        if (!isAvailable) return null
        return runCatching {
            val stripped = nativeStripImageMetadataLossless(
                inputFile.absolutePath,
                outputFile.absolutePath
            )
            if (stripped >= 0 && outputFile.isFile && outputFile.length() > 0L) stripped else null
        }.getOrNull()
    }

    fun removeBackgroundPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        tolerance: Int
    ): Int? {
        if (!isAvailable || width <= 0 || height <= 0 || pixels.size != width * height) return null
        return runCatching {
            val transparent = nativeRemoveBackgroundRgba(pixels, width, height, tolerance)
            if (transparent >= 0) transparent else null
        }.getOrNull()
    }
}
