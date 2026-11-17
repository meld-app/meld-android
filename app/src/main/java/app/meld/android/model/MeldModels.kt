package app.meld.android.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.meld.android.R
import java.io.File
import java.util.Locale

enum class MeldMode(@StringRes val labelRes: Int) {
    ALL(R.string.mode_all),
    CONVERT(R.string.mode_convert),
    TOOLS(R.string.mode_tools)
}

enum class MeldCategory(@StringRes val labelRes: Int) {
    ALL(R.string.category_all),
    PDF_DOCS(R.string.category_pdfs),
    SLIDES(R.string.category_slides),
    IMAGE_STUDIO(R.string.category_images),
    AUDIO_VIDEO(R.string.category_audio),
    DATA_TABLES(R.string.category_tables_zip)
}

enum class MeldToolId(
    @StringRes val titleRes: Int,
    @StringRes val badgeRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val ctaLabelRes: Int,
    @StringRes val resultHeadlineRes: Int,
    val mode: MeldMode,
    val category: MeldCategory,
    val supportedExtensions: Set<String>,
    val outputExtension: String
) {
    // ========================
    // IMAGE CONVERSIONS
    // ========================
    IMG_TO_JPG(
        titleRes = R.string.tool_img_to_jpg_title,
        badgeRes = R.string.tool_img_to_jpg_badge,
        subtitleRes = R.string.tool_img_to_jpg_subtitle,
        ctaLabelRes = R.string.tool_img_to_jpg_cta,
        resultHeadlineRes = R.string.tool_img_to_jpg_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "jpg"
    ),
    IMG_TO_PNG(
        titleRes = R.string.tool_img_to_png_title,
        badgeRes = R.string.tool_img_to_png_badge,
        subtitleRes = R.string.tool_img_to_png_subtitle,
        ctaLabelRes = R.string.tool_img_to_png_cta,
        resultHeadlineRes = R.string.tool_img_to_png_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "png"
    ),
    IMG_TO_WEBP(
        titleRes = R.string.tool_img_to_webp_title,
        badgeRes = R.string.tool_img_to_webp_badge,
        subtitleRes = R.string.tool_img_to_webp_subtitle,
        ctaLabelRes = R.string.tool_img_to_webp_cta,
        resultHeadlineRes = R.string.tool_img_to_webp_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "webp"
    ),
    IMG_TO_BMP(
        titleRes = R.string.tool_img_to_bmp_title,
        badgeRes = R.string.tool_img_to_bmp_badge,
        subtitleRes = R.string.tool_img_to_bmp_subtitle,
        ctaLabelRes = R.string.tool_img_to_bmp_cta,
        resultHeadlineRes = R.string.tool_img_to_bmp_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "bmp"
    ),
    IMG_TO_TIFF(
        titleRes = R.string.tool_img_to_tiff_title,
        badgeRes = R.string.tool_img_to_tiff_badge,
        subtitleRes = R.string.tool_img_to_tiff_subtitle,
        ctaLabelRes = R.string.tool_img_to_tiff_cta,
        resultHeadlineRes = R.string.tool_img_to_tiff_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "tiff"
    ),
    IMG_TO_PDF(
        titleRes = R.string.tool_img_to_pdf_title,
        badgeRes = R.string.tool_img_to_pdf_badge,
        subtitleRes = R.string.tool_img_to_pdf_subtitle,
        ctaLabelRes = R.string.tool_img_to_pdf_cta,
        resultHeadlineRes = R.string.tool_img_to_pdf_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif"),
        outputExtension = "pdf"
    ),

    // ========================
    // IMAGE STUDIO TOOLS
    // ========================
    REMOVE_BG(
        titleRes = R.string.tool_remove_bg_title,
        badgeRes = R.string.tool_remove_bg_badge,
        subtitleRes = R.string.tool_remove_bg_subtitle,
        ctaLabelRes = R.string.tool_remove_bg_cta,
        resultHeadlineRes = R.string.tool_remove_bg_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COMPRESS(
        titleRes = R.string.tool_compress_img_title,
        badgeRes = R.string.tool_compress_img_badge,
        subtitleRes = R.string.tool_compress_img_subtitle,
        ctaLabelRes = R.string.tool_compress_img_cta,
        resultHeadlineRes = R.string.tool_compress_img_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "jpg"
    ),
    RESIZE_IMAGE(
        titleRes = R.string.tool_resize_image_title,
        badgeRes = R.string.tool_resize_image_badge,
        subtitleRes = R.string.tool_resize_image_subtitle,
        ctaLabelRes = R.string.tool_resize_image_cta,
        resultHeadlineRes = R.string.tool_resize_image_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    COLLAGE(
        titleRes = R.string.tool_collage_title,
        badgeRes = R.string.tool_collage_badge,
        subtitleRes = R.string.tool_collage_subtitle,
        ctaLabelRes = R.string.tool_collage_cta,
        resultHeadlineRes = R.string.tool_collage_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    STRIP_EXIF(
        titleRes = R.string.tool_strip_exif_title,
        badgeRes = R.string.tool_strip_exif_badge,
        subtitleRes = R.string.tool_strip_exif_subtitle,
        ctaLabelRes = R.string.tool_strip_exif_cta,
        resultHeadlineRes = R.string.tool_strip_exif_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
        outputExtension = "png"
    ),
    SCAN_QR(
        titleRes = R.string.tool_scan_qr_title,
        badgeRes = R.string.tool_scan_qr_badge,
        subtitleRes = R.string.tool_scan_qr_subtitle,
        ctaLabelRes = R.string.tool_scan_qr_cta,
        resultHeadlineRes = R.string.tool_scan_qr_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.IMAGE_STUDIO,
        supportedExtensions = setOf("png", "jpg", "jpeg", "pdf", "txt", "csv"),
        outputExtension = "png"
    ),

    // ========================
    // PDF CONVERSIONS
    // ========================
    PDF_TO_PNG(
        titleRes = R.string.tool_pdf_to_png_title,
        badgeRes = R.string.tool_pdf_to_png_badge,
        subtitleRes = R.string.tool_pdf_to_png_subtitle,
        ctaLabelRes = R.string.tool_pdf_to_png_cta,
        resultHeadlineRes = R.string.tool_pdf_to_png_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "png"
    ),
    PDF_TO_JPG(
        titleRes = R.string.tool_pdf_to_jpg_title,
        badgeRes = R.string.tool_pdf_to_jpg_badge,
        subtitleRes = R.string.tool_pdf_to_jpg_subtitle,
        ctaLabelRes = R.string.tool_pdf_to_jpg_cta,
        resultHeadlineRes = R.string.tool_pdf_to_jpg_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "jpg"
    ),
    PDF_TO_MD(
        titleRes = R.string.tool_pdf_to_md_title,
        badgeRes = R.string.tool_pdf_to_md_badge,
        subtitleRes = R.string.tool_pdf_to_md_subtitle,
        ctaLabelRes = R.string.tool_pdf_to_md_cta,
        resultHeadlineRes = R.string.tool_pdf_to_md_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "md"
    ),
    PDF_TO_HTML(
        titleRes = R.string.tool_pdf_to_html_title,
        badgeRes = R.string.tool_pdf_to_html_badge,
        subtitleRes = R.string.tool_pdf_to_html_subtitle,
        ctaLabelRes = R.string.tool_pdf_to_html_cta,
        resultHeadlineRes = R.string.tool_pdf_to_html_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "html"
    ),
    PDF_TO_DOCX(
        titleRes = R.string.tool_pdf_to_docx_title,
        badgeRes = R.string.tool_pdf_to_docx_badge,
        subtitleRes = R.string.tool_pdf_to_docx_subtitle,
        ctaLabelRes = R.string.tool_pdf_to_docx_cta,
        resultHeadlineRes = R.string.tool_pdf_to_docx_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "docx"
    ),
    PDF_TO_TXT(
        titleRes = R.string.tool_pdf_to_txt_title,
        badgeRes = R.string.tool_pdf_to_txt_badge,
        subtitleRes = R.string.tool_pdf_to_txt_subtitle,
        ctaLabelRes = R.string.tool_pdf_to_txt_cta,
        resultHeadlineRes = R.string.tool_pdf_to_txt_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "txt"
    ),

    // ========================
    // PDF STUDIO TOOLS
    // ========================
    COMPRESS_PDF(
        titleRes = R.string.tool_compress_pdf_title,
        badgeRes = R.string.tool_compress_pdf_badge,
        subtitleRes = R.string.tool_compress_pdf_subtitle,
        ctaLabelRes = R.string.tool_compress_pdf_cta,
        resultHeadlineRes = R.string.tool_compress_pdf_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    HANDOUT(
        titleRes = R.string.tool_handout_title,
        badgeRes = R.string.tool_handout_badge,
        subtitleRes = R.string.tool_handout_subtitle,
        ctaLabelRes = R.string.tool_handout_cta,
        resultHeadlineRes = R.string.tool_handout_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    PDF_EXTRACT(
        titleRes = R.string.tool_pdf_extract_title,
        badgeRes = R.string.tool_pdf_extract_badge,
        subtitleRes = R.string.tool_pdf_extract_subtitle,
        ctaLabelRes = R.string.tool_pdf_extract_cta,
        resultHeadlineRes = R.string.tool_pdf_extract_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    LOCK_STAMP(
        titleRes = R.string.tool_lock_stamp_title,
        badgeRes = R.string.tool_lock_stamp_badge,
        subtitleRes = R.string.tool_lock_stamp_subtitle,
        ctaLabelRes = R.string.tool_lock_stamp_cta,
        resultHeadlineRes = R.string.tool_lock_stamp_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    REDACT(
        titleRes = R.string.tool_redact_title,
        badgeRes = R.string.tool_redact_badge,
        subtitleRes = R.string.tool_redact_subtitle,
        ctaLabelRes = R.string.tool_redact_cta,
        resultHeadlineRes = R.string.tool_redact_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    PDF_MERGE(
        titleRes = R.string.tool_pdf_merge_title,
        badgeRes = R.string.tool_pdf_merge_badge,
        subtitleRes = R.string.tool_pdf_merge_subtitle,
        ctaLabelRes = R.string.tool_pdf_merge_cta,
        resultHeadlineRes = R.string.tool_pdf_merge_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),
    STRIP_PDF_META(
        titleRes = R.string.tool_strip_pdf_meta_title,
        badgeRes = R.string.tool_strip_pdf_meta_badge,
        subtitleRes = R.string.tool_strip_pdf_meta_subtitle,
        ctaLabelRes = R.string.tool_strip_pdf_meta_cta,
        resultHeadlineRes = R.string.tool_strip_pdf_meta_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.PDF_DOCS,
        supportedExtensions = setOf("pdf"),
        outputExtension = "pdf"
    ),

    // ========================
    // AUDIO, TABLES & ARCHIVE
    // ========================
    VISUALIZE(
        titleRes = R.string.tool_visualize_title,
        badgeRes = R.string.tool_visualize_badge,
        subtitleRes = R.string.tool_visualize_subtitle,
        ctaLabelRes = R.string.tool_visualize_cta,
        resultHeadlineRes = R.string.tool_visualize_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "pcm", "mp3", "m4a", "aac", "mp4", "ogg"),
        outputExtension = "png"
    ),
    SPEED_2X(
        titleRes = R.string.tool_speed_2x_title,
        badgeRes = R.string.tool_speed_2x_badge,
        subtitleRes = R.string.tool_speed_2x_subtitle,
        ctaLabelRes = R.string.tool_speed_2x_cta,
        resultHeadlineRes = R.string.tool_speed_2x_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.AUDIO_VIDEO,
        supportedExtensions = setOf("wav", "pcm", "mp3", "m4a", "aac", "mp4", "ogg"),
        outputExtension = "wav"
    ),
    SHEET_TO_PDF(
        titleRes = R.string.tool_sheet_to_pdf_title,
        badgeRes = R.string.tool_sheet_to_pdf_badge,
        subtitleRes = R.string.tool_sheet_to_pdf_subtitle,
        ctaLabelRes = R.string.tool_sheet_to_pdf_cta,
        resultHeadlineRes = R.string.tool_sheet_to_pdf_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "pdf"
    ),
    SHEET_TO_MD(
        titleRes = R.string.tool_sheet_to_md_title,
        badgeRes = R.string.tool_sheet_to_md_badge,
        subtitleRes = R.string.tool_sheet_to_md_subtitle,
        ctaLabelRes = R.string.tool_sheet_to_md_cta,
        resultHeadlineRes = R.string.tool_sheet_to_md_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "md"
    ),
    TABLE_CONVERT(
        titleRes = R.string.tool_table_convert_title,
        badgeRes = R.string.tool_table_convert_badge,
        subtitleRes = R.string.tool_table_convert_subtitle,
        ctaLabelRes = R.string.tool_table_convert_cta,
        resultHeadlineRes = R.string.tool_table_convert_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("csv", "tsv", "txt"),
        outputExtension = "json"
    ),
    PACK_ZIP(
        titleRes = R.string.tool_pack_zip_title,
        badgeRes = R.string.tool_pack_zip_badge,
        subtitleRes = R.string.tool_pack_zip_subtitle,
        ctaLabelRes = R.string.tool_pack_zip_cta,
        resultHeadlineRes = R.string.tool_pack_zip_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.DATA_TABLES,
        supportedExtensions = setOf("pdf", "pptx", "png", "jpg", "jpeg", "webp", "csv", "txt", "wav", "json", "md"),
        outputExtension = "zip"
    ),

    // ========================
    // PPTX & SLIDES STUDIO
    // ========================
    PPTX_TO_PDF(
        titleRes = R.string.tool_pptx_to_pdf_title,
        badgeRes = R.string.tool_pptx_to_pdf_badge,
        subtitleRes = R.string.tool_pptx_to_pdf_subtitle,
        ctaLabelRes = R.string.tool_pptx_to_pdf_cta,
        resultHeadlineRes = R.string.tool_pptx_to_pdf_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "pdf"
    ),
    PPTX_TO_PNG(
        titleRes = R.string.tool_pptx_to_png_title,
        badgeRes = R.string.tool_pptx_to_png_badge,
        subtitleRes = R.string.tool_pptx_to_png_subtitle,
        ctaLabelRes = R.string.tool_pptx_to_png_cta,
        resultHeadlineRes = R.string.tool_pptx_to_png_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "png"
    ),
    PPTX_TO_JPG(
        titleRes = R.string.tool_pptx_to_jpg_title,
        badgeRes = R.string.tool_pptx_to_jpg_badge,
        subtitleRes = R.string.tool_pptx_to_jpg_subtitle,
        ctaLabelRes = R.string.tool_pptx_to_jpg_cta,
        resultHeadlineRes = R.string.tool_pptx_to_jpg_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "jpg"
    ),
    PPTX_TO_MD(
        titleRes = R.string.tool_pptx_to_md_title,
        badgeRes = R.string.tool_pptx_to_md_badge,
        subtitleRes = R.string.tool_pptx_to_md_subtitle,
        ctaLabelRes = R.string.tool_pptx_to_md_cta,
        resultHeadlineRes = R.string.tool_pptx_to_md_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "md"
    ),
    PPTX_TO_TXT(
        titleRes = R.string.tool_pptx_to_txt_title,
        badgeRes = R.string.tool_pptx_to_txt_badge,
        subtitleRes = R.string.tool_pptx_to_txt_subtitle,
        ctaLabelRes = R.string.tool_pptx_to_txt_cta,
        resultHeadlineRes = R.string.tool_pptx_to_txt_result,
        mode = MeldMode.CONVERT,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "txt"
    ),
    PPTX_HANDOUT(
        titleRes = R.string.tool_pptx_handout_title,
        badgeRes = R.string.tool_pptx_handout_badge,
        subtitleRes = R.string.tool_pptx_handout_subtitle,
        ctaLabelRes = R.string.tool_pptx_handout_cta,
        resultHeadlineRes = R.string.tool_pptx_handout_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "pdf"
    ),
    PPTX_SANITIZE(
        titleRes = R.string.tool_pptx_sanitize_title,
        badgeRes = R.string.tool_pptx_sanitize_badge,
        subtitleRes = R.string.tool_pptx_sanitize_subtitle,
        ctaLabelRes = R.string.tool_pptx_sanitize_cta,
        resultHeadlineRes = R.string.tool_pptx_sanitize_result,
        mode = MeldMode.TOOLS,
        category = MeldCategory.SLIDES,
        supportedExtensions = setOf("pptx"),
        outputExtension = "pptx"
    );

    val supportsBatchQueue: Boolean
        get() = this == PDF_MERGE || this == COLLAGE || this == PACK_ZIP

    val maxBatchCount: Int
        get() = when (this) {
            COLLAGE -> 9
            PDF_MERGE, PACK_ZIP -> 12
            else -> 1
        }

    fun acceptsExtension(ext: String): Boolean {
        val clean = ext.lowercase().trimStart('.')
        return clean.isNotEmpty() && supportedExtensions.contains(clean)
    }

    fun followUpTools(actualExtension: String = outputExtension): List<MeldToolId> =
        when (actualExtension.lowercase().trimStart('.')) {
            "pdf" -> listOf(COMPRESS_PDF, PDF_EXTRACT, LOCK_STAMP).filter { it != this }
            "pptx" -> listOf(PPTX_TO_PDF, PPTX_HANDOUT, PPTX_TO_MD).filter { it != this }
            "png", "jpg", "jpeg", "webp" -> listOf(COMPRESS, RESIZE_IMAGE, IMG_TO_PDF).filter { it != this }
            "zip" -> emptyList()
            else -> listOf(PACK_ZIP).filter { it != this }
        }
}

data class RedactionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String = ""
)

enum class ProgressUnit {
    PAGE,
    SLIDE,
    PHOTO,
    PDF_FILE,
    ARCHIVE_FILE
}

data class ProcessingProgress(
    val current: Int,
    val total: Int,
    val unit: ProgressUnit = ProgressUnit.PAGE
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
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
    val pdfImageAllPages: Boolean = true,
    val pdfImagePage: Int = 1,
    val pptxImageAllSlides: Boolean = true,
    val pptxImageSlide: Int = 1,
    val chromaTolerance: Int = 42,
    val redactionZones: List<RedactionRect> = listOf(
        RedactionRect(0.10f, 0.24f, 0.90f, 0.34f)
    )
)

data class SelectedFileInfo(
    val file: File,
    val displayName: String,
    val extension: String,
    val sizeBytes: Long,
    val pageCount: Int? = null,
    val slideCount: Int? = null
) {
    val cleanTitle: String
        get() = displayName
            .substringBeforeLast('.')
            .removePrefix("MELD_")
            .removeSuffix("_4Pages")
            .removeSuffix("_3Slides")
            .replace('_', ' ')
            .ifBlank { displayName }

    val formattedSize: String
        get() = formatByteCount(sizeBytes)

    @Composable
    fun localizedMetaLine(): String = when {
        slideCount != null && slideCount > 0 -> {
            val slidesLabel = pluralStringResource(R.plurals.file_slide_count, slideCount, slideCount)
            stringResource(R.string.file_meta_pages_size, slidesLabel, formattedSize)
        }
        pageCount != null && pageCount > 0 -> {
            val pagesLabel = pluralStringResource(R.plurals.file_page_count, pageCount, pageCount)
            stringResource(R.string.file_meta_pages_size, pagesLabel, formattedSize)
        }
        else -> stringResource(R.string.file_meta_ext_size, extension.uppercase(), formattedSize)
    }

    val formattedMetaLine: String
        get() = when {
            slideCount != null && slideCount > 0 ->
                "$slideCount slide${if (slideCount == 1) "" else "s"} · $formattedSize"
            pageCount != null && pageCount > 0 ->
                "$pageCount page${if (pageCount == 1) "" else "s"} · $formattedSize"
            else -> "${extension.uppercase()} · $formattedSize"
        }

    companion object {
        fun formatByteCount(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.2f MB", mb)
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

data class HomeLayoutConfig(
    val isCustom: Boolean = false,
    val quickTools: List<MeldToolId> = DEFAULT_QUICK_TOOLS,
    val gridTools: List<MeldToolId> = DEFAULT_GRID_TOOLS
) {
    fun resolveQuickTools(ext: String): Triple<MeldToolId, MeldToolId, MeldToolId> {
        if (isCustom) {
            val q = normalized().quickTools
            return Triple(q[0], q[1], q[2])
        }
        return defaultQuickTripleForExtension(ext)
    }

    fun resolveGridTools(ext: String, defaultMaxCount: Int): List<MeldToolId> {
        if (isCustom) {
            return normalized().gridTools
        }
        return defaultGridToolsForExtension(ext, defaultMaxCount)
    }

    fun materializeForEditing(ext: String, defaultMaxCount: Int = 4): HomeLayoutConfig {
        if (isCustom) return normalized()
        val triple = defaultQuickTripleForExtension(ext)
        val initialQuick = listOf(triple.first, triple.second, triple.third)
        val initialGrid = defaultGridToolsForExtension(ext, defaultMaxCount)
        return HomeLayoutConfig(
            isCustom = false,
            quickTools = initialQuick,
            gridTools = initialGrid
        ).normalized()
    }

    fun swapQuickTool(slotIndex: Int, newTool: MeldToolId, currentExt: String = "pdf"): HomeLayoutConfig {
        val base = materializeForEditing(currentExt)
        if (slotIndex !in 0..2) return base.copy(isCustom = true)
        val updatedQuick = base.quickTools.toMutableList()
        val updatedGrid = base.gridTools.toMutableList()
        val oldTool = updatedQuick[slotIndex]
        if (oldTool == newTool) return base.copy(isCustom = true)

        val otherQuickIdx = updatedQuick.indexOf(newTool)
        val existingGridIdx = updatedGrid.indexOf(newTool)
        when {
            otherQuickIdx >= 0 -> {
                updatedQuick[otherQuickIdx] = oldTool
                updatedQuick[slotIndex] = newTool
            }
            existingGridIdx >= 0 -> {
                updatedGrid[existingGridIdx] = oldTool
                updatedQuick[slotIndex] = newTool
            }
            else -> {
                updatedQuick[slotIndex] = newTool
            }
        }
        return HomeLayoutConfig(
            isCustom = true,
            quickTools = updatedQuick,
            gridTools = updatedGrid
        ).normalized()
    }

    fun moveQuickTool(index: Int, delta: Int, currentExt: String = "pdf"): HomeLayoutConfig {
        val base = materializeForEditing(currentExt)
        val updated = swapAdjacentOrClamped(base.quickTools, index, delta) ?: return base.copy(isCustom = true)
        return base.copy(isCustom = true, quickTools = updated).normalized()
    }

    fun swapGridTool(slotIndex: Int, newTool: MeldToolId, currentExt: String = "pdf"): HomeLayoutConfig {
        val base = materializeForEditing(currentExt)
        if (slotIndex !in base.gridTools.indices) return base.copy(isCustom = true)
        val updatedQuick = base.quickTools.toMutableList()
        val updatedGrid = base.gridTools.toMutableList()
        val oldTool = updatedGrid[slotIndex]
        if (oldTool == newTool) return base.copy(isCustom = true)

        val quickIdx = updatedQuick.indexOf(newTool)
        val otherGridIdx = updatedGrid.indexOf(newTool)
        when {
            quickIdx >= 0 -> {
                updatedQuick[quickIdx] = oldTool
                updatedGrid[slotIndex] = newTool
            }
            otherGridIdx >= 0 -> {
                updatedGrid[otherGridIdx] = oldTool
                updatedGrid[slotIndex] = newTool
            }
            else -> {
                updatedGrid[slotIndex] = newTool
            }
        }
        return HomeLayoutConfig(
            isCustom = true,
            quickTools = updatedQuick,
            gridTools = updatedGrid
        ).normalized()
    }

    fun moveGridTool(index: Int, delta: Int, currentExt: String = "pdf"): HomeLayoutConfig {
        val base = materializeForEditing(currentExt)
        val updated = swapAdjacentOrClamped(base.gridTools, index, delta) ?: return base.copy(isCustom = true)
        return base.copy(isCustom = true, gridTools = updated).normalized()
    }

    fun addGridTool(tool: MeldToolId, currentExt: String = "pdf"): HomeLayoutConfig {
        val base = materializeForEditing(currentExt)
        if (tool in base.quickTools || tool in base.gridTools || base.gridTools.size >= MAX_HOME_GRID_TOOLS) {
            return base.copy(isCustom = true)
        }
        return base.copy(
            isCustom = true,
            gridTools = base.gridTools + tool
        ).normalized()
    }

    fun removeGridTool(tool: MeldToolId, currentExt: String = "pdf"): HomeLayoutConfig {
        val base = materializeForEditing(currentExt)
        return base.copy(
            isCustom = true,
            gridTools = base.gridTools.filterNot { it == tool }
        ).normalized()
    }

    private fun normalized(): HomeLayoutConfig {
        val distinctQuick = quickTools.distinct().take(3).toMutableList()
        for (fallback in DEFAULT_QUICK_TOOLS + MeldToolId.entries) {
            if (distinctQuick.size >= 3) break
            if (fallback !in distinctQuick) distinctQuick.add(fallback)
        }
        val quickSet = distinctQuick.toSet()
        val distinctGrid = gridTools
            .distinct()
            .filter { it !in quickSet }
            .take(MAX_HOME_GRID_TOOLS)
        return copy(quickTools = distinctQuick, gridTools = distinctGrid)
    }

    companion object {
        const val MAX_HOME_GRID_TOOLS = 8

        val DEFAULT_QUICK_TOOLS: List<MeldToolId> = listOf(
            MeldToolId.COMPRESS_PDF,
            MeldToolId.HANDOUT,
            MeldToolId.PDF_EXTRACT
        )

        val DEFAULT_GRID_TOOLS: List<MeldToolId> = listOf(
            MeldToolId.PDF_TO_PNG,
            MeldToolId.PDF_TO_JPG,
            MeldToolId.PDF_TO_MD,
            MeldToolId.PDF_TO_HTML
        )

        fun defaultQuickTripleForExtension(ext: String): Triple<MeldToolId, MeldToolId, MeldToolId> =
            when (ext.lowercase()) {
                "pptx" ->
                    Triple(MeldToolId.PPTX_TO_PDF, MeldToolId.PPTX_HANDOUT, MeldToolId.PPTX_SANITIZE)
                in setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif") ->
                    Triple(MeldToolId.COMPRESS, MeldToolId.REMOVE_BG, MeldToolId.IMG_TO_PDF)
                in setOf("wav", "pcm", "mp3", "m4a", "aac", "mp4", "ogg") ->
                    Triple(MeldToolId.VISUALIZE, MeldToolId.SPEED_2X, MeldToolId.PACK_ZIP)
                in setOf("csv", "tsv", "txt") ->
                    Triple(MeldToolId.SHEET_TO_PDF, MeldToolId.SHEET_TO_MD, MeldToolId.TABLE_CONVERT)
                else ->
                    Triple(MeldToolId.COMPRESS_PDF, MeldToolId.HANDOUT, MeldToolId.PDF_EXTRACT)
            }

        fun defaultGridToolsForExtension(ext: String, maxCount: Int): List<MeldToolId> {
            val triple = defaultQuickTripleForExtension(ext)
            val shown = setOf(triple.first, triple.second, triple.third)
            return MeldToolId.entries
                .filter { it.acceptsExtension(ext) && it !in shown && it != MeldToolId.SCAN_QR }
                .take(maxCount)
        }

        private fun swapAdjacentOrClamped(
            list: List<MeldToolId>,
            index: Int,
            delta: Int
        ): List<MeldToolId>? {
            if (index !in list.indices || delta == 0) return null
            val target = (index + delta).coerceIn(0, list.lastIndex)
            if (target == index) return null
            val mutable = list.toMutableList()
            val item = mutable[index]
            mutable[index] = mutable[target]
            mutable[target] = item
            return mutable
        }

        fun serializeTools(tools: List<MeldToolId>): String = tools.joinToString(",") { it.name }

        fun deserializeTools(raw: String?): List<MeldToolId> {
            if (raw.isNullOrBlank()) return emptyList()
            val seen = LinkedHashSet<MeldToolId>()
            raw.split(',').forEach { token ->
                val trimmed = token.trim()
                if (trimmed.isNotEmpty()) {
                    MeldToolId.entries.firstOrNull { it.name == trimmed }?.let { seen.add(it) }
                }
            }
            return seen.toList()
        }

        fun fromPreferences(isCustom: Boolean, quickCsv: String?, gridCsv: String?): HomeLayoutConfig {
            if (!isCustom) return HomeLayoutConfig()
            val quick = deserializeTools(quickCsv).ifEmpty { DEFAULT_QUICK_TOOLS }
            val grid = if (gridCsv != null) deserializeTools(gridCsv) else DEFAULT_GRID_TOOLS
            return HomeLayoutConfig(
                isCustom = true,
                quickTools = quick,
                gridTools = grid
            ).normalized()
        }
    }
}
