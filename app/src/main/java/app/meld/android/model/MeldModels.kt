package app.meld.android.model

import java.io.File

enum class MeldCategory(val label: String) {
    ALL("ALL"),
    PDF_DOCS("PDF & DOCS"),
    IMAGE_STUDIO("IMAGE"),
    AUDIO_VIDEO("AUDIO & VIDEO"),
    SECURITY_QR("SECURITY & QR"),
    DATA_TABLES("TABLES")
}

enum class MeldToolId(
    val title: String,
    val badge: String,
    val subtitle: String,
    val category: MeldCategory,
    val supportedExtensions: Set<String>,
    val outputExtension: String
) {
    REMOVE_BG(
        title = "REMOVE BG",
        badge = "PNG ALPHA",
        subtitle = "Isolate subject & strip background into a transparent PNG",
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COMPRESS(
        title = "COMPRESS",
        badge = "TARGET KB",
        subtitle = "Binary-search compress image or PDF to exact portal KB limit",
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "pdf"),
        outputExtension = "jpg"
    ),
    LOCK_STAMP(
        title = "LOCK / STAMP",
        badge = "WATERMARK",
        subtitle = "Stamp diagonal security watermark & flatten PDF pages",
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    HANDOUT(
        title = "HANDOUT",
        badge = "2-UP / 4-UP",
        subtitle = "Arrange slides into 2-up or 4-up printable study sheets with note lines",
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    REDACT(
        title = "REDACT",
        badge = "SANITIZE",
        subtitle = "Burn tamper-proof redaction bars over sensitive regions & flatten",
        category = MeldCategory.SECURITY_QR,
        supportedExtensions = setOf("pdf", "png", "jpg", "jpeg"),
        outputExtension = "pdf"
    ),
    SCAN_QR(
        title = "QR STUDIO",
        badge = "QR MATRIX",
        subtitle = "Generate or inspect high-contrast QR payload cards offline",
        category = MeldCategory.SECURITY_QR,
        supportedExtensions = setOf("png", "jpg", "jpeg", "pdf", "txt", "csv"),
        outputExtension = "png"
    ),
    VISUALIZE(
        title = "VISUALIZE",
        badge = "WAVEFORM",
        subtitle = "Render 1920x600 studio gradient audio waveform PNG from media",
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "mp3", "m4a", "aac", "mp4", "ogg"),
        outputExtension = "png"
    ),
    PDF_EXTRACT(
        title = "SPLIT PDF",
        badge = "PAGES",
        subtitle = "Extract selected page range into a clean standalone PDF",
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    TABLE_CONVERT(
        title = "TABLE -> JSON",
        badge = "STRUCTURED",
        subtitle = "Convert CSV spreadsheet rows into structured JSON & Markdown",
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "json"
    );

    fun acceptsExtension(ext: String): Boolean {
        val clean = ext.lowercase().trimStart('.')
        return clean.isEmpty() || supportedExtensions.contains(clean)
    }
}

data class ToolOptions(
    val targetKb: Int = 180,
    val stampText: String = "CONFIDENTIAL • MELD VERIFIED",
    val handoutPagesPerSheet: Int = 4,
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

