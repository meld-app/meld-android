package app.meld.android.model

import java.io.File

enum class MeldMode(val label: String) {
    ALL("ALL STUDIO"),
    CONVERT("⇧ CONVERT"),
    TOOLS("⌘ TOOLS")
}

enum class MeldCategory(val label: String) {
    ALL("ALL"),
    PDF_DOCS("PDF & DOCS"),
    IMAGE_STUDIO("IMAGE"),
    AUDIO_VIDEO("AUDIO & VIDEO"),
    DATA_TABLES("TABLES & ZIP")
}

enum class MeldToolId(
    val title: String,
    val badge: String,
    val subtitle: String,
    val mode: MeldMode,
    val category: MeldCategory,
    val supportedExtensions: Set<String>,
    val outputExtension: String
) {
    // ========================
    // IMAGE CONVERSIONS (⇧ CONVERT)
    // ========================
    IMG_TO_JPG(
        title = "CONVERT -> JPG",
        badge = "JPG",
        subtitle = "Convert PNG, WebP, BMP, or TIFF image to standard JPEG",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "jpg"
    ),
    IMG_TO_PNG(
        title = "CONVERT -> PNG",
        badge = "PNG",
        subtitle = "Convert any image to lossless 32-bit PNG",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "png"
    ),
    IMG_TO_WEBP(
        title = "CONVERT -> WEBP",
        badge = "WEBP",
        subtitle = "Convert image to modern high-efficiency WebP format",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "webp"
    ),
    IMG_TO_BMP(
        title = "CONVERT -> BMP",
        badge = "BMP",
        subtitle = "Export uncompressed 32-bit Windows Bitmap (.bmp)",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "bmp"
    ),
    IMG_TO_TIFF(
        title = "CONVERT -> TIFF",
        badge = "TIFF",
        subtitle = "Export archival uncompressed RGB TIFF (.tiff) image",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "tiff"
    ),
    IMG_TO_PDF(
        title = "IMAGE -> PDF",
        badge = "A4 PDF",
        subtitle = "Convert image into a centered, print-ready A4 PDF document",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff"),
        outputExtension = "pdf"
    ),

    // ========================
    // IMAGE STUDIO TOOLS (⌘ TOOLS)
    // ========================
    REMOVE_BG(
        title = "REMOVE BG",
        badge = "PNG ALPHA",
        subtitle = "Isolate subject & strip background into a transparent PNG",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COMPRESS(
        title = "COMPRESS IMG",
        badge = "TARGET KB",
        subtitle = "Binary-search compress image to exact portal KB limit",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "jpg"
    ),
    RESIZE_IMAGE(
        title = "RESIZE & FRAME",
        badge = "SCALE %",
        subtitle = "Scale image resolution & add optional studio border frame",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COLLAGE(
        title = "PHOTO COLLAGE",
        badge = "2x2 GRID",
        subtitle = "Generate a 2x2 studio contact sheet collage with MELD caption",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    STRIP_EXIF(
        title = "STRIP EXIF",
        badge = "PRIVACY",
        subtitle = "Wipe GPS, camera serial & EXIF metadata into a clean PNG",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    SCAN_QR(
        title = "QR STUDIO",
        badge = "QR MATRIX",
        subtitle = "Generate high-contrast 960x960 offline QR payload cards",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "pdf", "txt", "csv"),
        outputExtension = "png"
    ),

    // ========================
    // PDF CONVERSIONS (⇧ CONVERT)
    // ========================
    PDF_TO_PNG(
        title = "PDF -> PNG",
        badge = "HD PNG",
        subtitle = "Render PDF page as high-resolution lossless PNG image",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "png"
    ),
    PDF_TO_JPG(
        title = "PDF -> JPG",
        badge = "HD JPG",
        subtitle = "Render PDF page as high-resolution JPEG image",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "jpg"
    ),
    PDF_TO_MD(
        title = "PDF -> MARKDOWN",
        badge = "MD DOC",
        subtitle = "Convert PDF document structure & pages into clean Markdown (.md)",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "md"
    ),
    PDF_TO_HTML(
        title = "PDF -> HTML5",
        badge = "WEB DOC",
        subtitle = "Export PDF as a dark-mode standalone HTML5 reader with page cards",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "html"
    ),
    PDF_TO_DOCX(
        title = "PDF -> WORD",
        badge = "DOCX",
        subtitle = "Export PDF into an OpenXML Microsoft Word (.docx) document",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "docx"
    ),
    PDF_TO_TXT(
        title = "PDF -> TEXT",
        badge = "PLAIN TXT",
        subtitle = "Extract plain-text summary & page manifest from PDF",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "txt"
    ),

    // ========================
    // PDF STUDIO TOOLS (⌘ TOOLS)
    // ========================
    COMPRESS_PDF(
        title = "COMPRESS PDF",
        badge = "TARGET KB",
        subtitle = "Downsample & re-encode PDF pages to fit portal upload limits",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    HANDOUT(
        title = "HANDOUT",
        badge = "2/3/4-UP",
        subtitle = "Arrange slides into 2-up, 3-up (with notes), or 4-up study sheets",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    LOCK_STAMP(
        title = "LOCK / STAMP",
        badge = "WATERMARK",
        subtitle = "Stamp diagonal security watermark & flatten PDF pages",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    REDACT(
        title = "REDACT PDF",
        badge = "SANITIZE",
        subtitle = "Burn tamper-proof redaction bars over sensitive regions & flatten",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    PDF_EXTRACT(
        title = "SPLIT PDF",
        badge = "PAGES",
        subtitle = "Extract selected page range into a clean standalone PDF",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    PDF_MERGE(
        title = "MERGE / BOOKLET",
        badge = "COMBINE",
        subtitle = "Combine & duplicate PDF pages into a unified multi-page deck",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    STRIP_PDF_META(
        title = "SANITIZE PDF",
        badge = "NO META",
        subtitle = "Strip author, producer & hidden XMP metadata by raster-rebuilding",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),

    // ========================
    // AUDIO, VIDEO, TABLES & ARCHIVE
    // ========================
    VISUALIZE(
        title = "VISUALIZE",
        badge = "WAVEFORM",
        subtitle = "Render 1920x600 studio gradient audio waveform PNG from media",
        mode = MeldMode.TOOLS,
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "mp3", "m4a", "aac", "mp4", "ogg"),
        outputExtension = "png"
    ),
    SPEED_2X(
        title = "SPEED 2X",
        badge = "2.0x FAST",
        subtitle = "Double playback speed (2x time-decimated WAV audio stream)",
        mode = MeldMode.TOOLS,
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "mp3", "m4a", "mp4"),
        outputExtension = "wav"
    ),
    SHEET_TO_PDF(
        title = "TABLE -> PDF",
        badge = "VECTOR PDF",
        subtitle = "Render CSV/TSV spreadsheet into a formatted A4 vector table PDF",
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "pdf"
    ),
    SHEET_TO_MD(
        title = "TABLE -> MD",
        badge = "MARKDOWN",
        subtitle = "Convert CSV spreadsheet rows into a GitHub Markdown table (.md)",
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "md"
    ),
    TABLE_CONVERT(
        title = "TABLE -> JSON",
        badge = "STRUCTURED",
        subtitle = "Convert CSV spreadsheet rows into structured JSON objects",
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "json"
    ),
    PACK_ZIP(
        title = "ARCHIVE -> ZIP",
        badge = "ZIP PACK",
        subtitle = "Compress staged file into a standard .zip archive",
        mode = MeldMode.TOOLS,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("pdf", "png", "jpg", "jpeg", "webp", "csv", "txt", "wav", "json", "md"),
        outputExtension = "zip"
    );

    fun acceptsExtension(ext: String): Boolean {
        val clean = ext.lowercase().trimStart('.')
        return clean.isEmpty() || supportedExtensions.contains(clean)
    }
}

data class ToolOptions(
    val targetKb: Int = 180,
    val resizePercent: Int = 75,
    val addStudioFrame: Boolean = true,
    val stampText: String = "CONFIDENTIAL • MELD VERIFIED",
    val handoutPagesPerSheet: Int = 3,
    val qrPayloadText: String = "https://github.com/meld-app",
    val pageRangeStart: Int = 1,
    val pageRangeEnd: Int = 2,
    val chromaTolerance: Int = 42
)

data class SelectedFileInfo(
    val file: File,
    val displayName: String,
    val extension: String,
    val sizeBytes: Long
) {
    val formattedSize: String
        get() = formatByteCount(sizeBytes)

    companion object {
        fun formatByteCount(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format("%.2f MB", mb)
        }
    }
}

data class ConversionResult(
    val tool: MeldToolId,
    val outputFile: File,
    val inputSizeBytes: Long,
    val outputSizeBytes: Long,
    val summary: String,
    val durationMs: Long
)
