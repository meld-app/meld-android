package app.meld.android.model

import java.io.File

enum class MeldMode(val label: String) {
    ALL("All"),
    CONVERT("Convert"),
    TOOLS("Tools")
}

enum class MeldCategory(val label: String) {
    ALL("All"),
    PDF_DOCS("PDFs"),
    IMAGE_STUDIO("Images"),
    AUDIO_VIDEO("Audio"),
    DATA_TABLES("Tables & Zip")
}

enum class MeldToolId(
    val title: String,
    val badge: String,
    val subtitle: String,
    val ctaLabel: String,
    val resultHeadline: String,
    val mode: MeldMode,
    val category: MeldCategory,
    val supportedExtensions: Set<String>,
    val outputExtension: String
) {
    // ========================
    // IMAGE CONVERSIONS
    // ========================
    IMG_TO_JPG(
        title = "To JPG",
        badge = "JPG",
        subtitle = "Save any PNG, WebP, BMP, or TIFF as a compact JPEG",
        ctaLabel = "Convert to JPG",
        resultHeadline = "JPEG ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "jpg"
    ),
    IMG_TO_PNG(
        title = "To PNG",
        badge = "PNG",
        subtitle = "Convert into a crisp lossless 32-bit PNG",
        ctaLabel = "Convert to PNG",
        resultHeadline = "PNG ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "png"
    ),
    IMG_TO_WEBP(
        title = "To WebP",
        badge = "WebP",
        subtitle = "Turn an image into a lighter modern WebP file",
        ctaLabel = "Convert to WebP",
        resultHeadline = "WebP ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "webp"
    ),
    IMG_TO_BMP(
        title = "To BMP",
        badge = "BMP",
        subtitle = "Export uncompressed 24-bit Windows Bitmap",
        ctaLabel = "Convert to BMP",
        resultHeadline = "Bitmap ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "bmp"
    ),
    IMG_TO_TIFF(
        title = "To TIFF",
        badge = "TIFF",
        subtitle = "Export archival uncompressed RGB TIFF",
        ctaLabel = "Convert to TIFF",
        resultHeadline = "TIFF ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "tiff"
    ),
    IMG_TO_PDF(
        title = "Image to PDF",
        badge = "A4 PDF",
        subtitle = "Center an image cleanly onto a print-ready A4 page",
        ctaLabel = "Create PDF",
        resultHeadline = "PDF ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff"),
        outputExtension = "pdf"
    ),

    // ========================
    // IMAGE STUDIO TOOLS
    // ========================
    REMOVE_BG(
        title = "Remove BG",
        badge = "Alpha",
        subtitle = "Lift the subject cleanly onto a transparent PNG",
        ctaLabel = "Remove background",
        resultHeadline = "Background removed.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COMPRESS(
        title = "Compress Img",
        badge = "Target KB",
        subtitle = "Squeeze an image under any portal upload limit",
        ctaLabel = "Compress image",
        resultHeadline = "Compressed cleanly.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "jpg"
    ),
    RESIZE_IMAGE(
        title = "Resize & Frame",
        badge = "Scale",
        subtitle = "Scale dimensions & add an optional studio border",
        ctaLabel = "Resize image",
        resultHeadline = "Resized cleanly.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COLLAGE(
        title = "Photo Collage",
        badge = "2x2 Grid",
        subtitle = "Arrange a 2x2 studio contact sheet with borders",
        ctaLabel = "Build collage",
        resultHeadline = "Collage ready.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    STRIP_EXIF(
        title = "Strip EXIF",
        badge = "Privacy",
        subtitle = "Wipe GPS location & camera tags before sharing",
        ctaLabel = "Strip metadata",
        resultHeadline = "Metadata wiped.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    SCAN_QR(
        title = "QR Studio",
        badge = "QR Card",
        subtitle = "Generate a crisp offline QR card for any link",
        ctaLabel = "Create QR card",
        resultHeadline = "QR card ready.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "pdf", "txt", "csv"),
        outputExtension = "png"
    ),

    // ========================
    // PDF CONVERSIONS
    // ========================
    PDF_TO_PNG(
        title = "PDF to PNG",
        badge = "HD PNG",
        subtitle = "Render PDF pages as crisp lossless images",
        ctaLabel = "Export PNG",
        resultHeadline = "Image ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "png"
    ),
    PDF_TO_JPG(
        title = "PDF to JPG",
        badge = "HD JPG",
        subtitle = "Save PDF pages as shareable JPEG photos",
        ctaLabel = "Export JPG",
        resultHeadline = "JPEG ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "jpg"
    ),
    PDF_TO_MD(
        title = "PDF to Markdown",
        badge = "MD",
        subtitle = "Extract document structure into clean Markdown notes",
        ctaLabel = "Convert to Markdown",
        resultHeadline = "Markdown ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "md"
    ),
    PDF_TO_HTML(
        title = "PDF to HTML5",
        badge = "Web Doc",
        subtitle = "Build a standalone offline HTML5 reader page",
        ctaLabel = "Export HTML5",
        resultHeadline = "Reader ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "html"
    ),
    PDF_TO_DOCX(
        title = "PDF to Word",
        badge = "DOCX",
        subtitle = "Convert PDF into an editable Microsoft Word file",
        ctaLabel = "Export to Word",
        resultHeadline = "Word file ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "docx"
    ),
    PDF_TO_TXT(
        title = "PDF to Text",
        badge = "TXT",
        subtitle = "Pull plain text & page summary from your PDF",
        ctaLabel = "Extract text",
        resultHeadline = "Text extracted.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "txt"
    ),

    // ========================
    // PDF STUDIO TOOLS
    // ========================
    COMPRESS_PDF(
        title = "Compress",
        badge = "Target KB",
        subtitle = "Squeeze a PDF under any upload limit",
        ctaLabel = "Compress PDF",
        resultHeadline = "Compressed cleanly.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    HANDOUT(
        title = "Handout",
        badge = "2/3/4-up",
        subtitle = "Arrange slides into 2, 3, or 4-up notes sheets",
        ctaLabel = "Create handout",
        resultHeadline = "Handout ready.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    PDF_EXTRACT(
        title = "Split",
        badge = "Pages",
        subtitle = "Keep just the page range you actually need",
        ctaLabel = "Extract pages",
        resultHeadline = "Pages split.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    LOCK_STAMP(
        title = "Lock & Stamp",
        badge = "Watermark",
        subtitle = "Stamp a diagonal security watermark across pages",
        ctaLabel = "Stamp PDF",
        resultHeadline = "PDF stamped.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    REDACT(
        title = "Redact",
        badge = "Sanitize",
        subtitle = "Black out private zones & flatten pages permanently",
        ctaLabel = "Redact & flatten",
        resultHeadline = "PDF redacted.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    PDF_MERGE(
        title = "Merge Booklet",
        badge = "Combine",
        subtitle = "Combine pages into a unified multi-page deck",
        ctaLabel = "Build booklet",
        resultHeadline = "Booklet merged.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    STRIP_PDF_META(
        title = "Sanitize PDF",
        badge = "No Meta",
        subtitle = "Strip author, producer & hidden XMP properties",
        ctaLabel = "Strip PDF metadata",
        resultHeadline = "Metadata stripped.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),

    // ========================
    // AUDIO, TABLES & ARCHIVE
    // ========================
    VISUALIZE(
        title = "Waveform PNG",
        badge = "Waveform",
        subtitle = "Turn audio into a high-res gradient waveform banner",
        ctaLabel = "Render waveform",
        resultHeadline = "Waveform ready.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "mp3", "m4a", "aac", "mp4", "ogg"),
        outputExtension = "png"
    ),
    SPEED_2X(
        title = "Speed 2x",
        badge = "2.0x",
        subtitle = "Double audio playback speed locally in WAV",
        ctaLabel = "Speed up 2x",
        resultHeadline = "2x audio ready.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "mp3", "m4a", "mp4"),
        outputExtension = "wav"
    ),
    SHEET_TO_PDF(
        title = "Table to PDF",
        badge = "A4 PDF",
        subtitle = "Render CSV data into a crisp printable table PDF",
        ctaLabel = "Export table PDF",
        resultHeadline = "Table PDF ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "pdf"
    ),
    SHEET_TO_MD(
        title = "Table to MD",
        badge = "Markdown",
        subtitle = "Format spreadsheet rows into a Markdown table",
        ctaLabel = "Convert to Markdown",
        resultHeadline = "Markdown table ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "md"
    ),
    TABLE_CONVERT(
        title = "Table to JSON",
        badge = "JSON",
        subtitle = "Parse spreadsheet rows into clean structured JSON",
        ctaLabel = "Convert to JSON",
        resultHeadline = "JSON ready.",
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "json"
    ),
    PACK_ZIP(
        title = "Zip Archive",
        badge = "ZIP",
        subtitle = "Pack your working file into a neat .zip archive",
        ctaLabel = "Create ZIP",
        resultHeadline = "Archive packed.",
        mode = MeldMode.TOOLS,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("pdf", "png", "jpg", "jpeg", "webp", "csv", "txt", "wav", "json", "md"),
        outputExtension = "zip"
    );

    fun acceptsExtension(ext: String): Boolean {
        val clean = ext.lowercase().trimStart('.')
        return clean.isNotEmpty() && supportedExtensions.contains(clean)
    }

    fun followUpTools(): List<MeldToolId> = when (outputExtension) {
        "pdf" -> listOf(COMPRESS_PDF, PDF_EXTRACT, LOCK_STAMP).filter { it != this }
        "png", "jpg", "jpeg", "webp" -> listOf(COMPRESS, RESIZE_IMAGE, IMG_TO_PDF).filter { it != this }
        else -> listOf(PACK_ZIP).filter { it != this }
    }
}

data class ToolOptions(
    val targetKb: Int = 40,
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
    val sizeBytes: Long,
    val pageCount: Int? = null
) {
    val cleanTitle: String
        get() = displayName
            .substringBeforeLast('.')
            .removePrefix("MELD_")
            .removeSuffix("_4Pages")
            .replace('_', ' ')

    val formattedSize: String
        get() = formatByteCount(sizeBytes)

    val formattedMetaLine: String
        get() = if (pageCount != null && pageCount > 0) {
            "$pageCount page${if (pageCount == 1) "" else "s"} · $formattedSize"
        } else {
            "${extension.uppercase()} · $formattedSize"
        }

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
