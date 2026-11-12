package app.meld.android.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meld.android.engine.DocumentEngine
import app.meld.android.engine.ImageEngine
import app.meld.android.engine.MediaEngine
import app.meld.android.engine.SampleFileFactory
import app.meld.android.model.ConversionResult
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import app.meld.android.model.ToolOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class SamplePreset(val label: String, val badge: String) {
    PDF_DECK("Lecture Deck.pdf", "4 pages"),
    STUDIO_IMAGE("Studio Subject.png", "1080×1080"),
    SYNTH_AUDIO("Studio Synth.wav", "PCM WAV"),
    BENCHMARK_CSV("Benchmark.csv", "5 rows")
}

enum class DockTab(val label: String) {
    STUDIO("Studio"),
    ALL_TOOLS("Files"),
    SETTINGS("Settings")
}

sealed class MeldScreenRoute {
    data object Home : MeldScreenRoute()
    data class Task(val tool: MeldToolId) : MeldScreenRoute()
    data class Result(val conversion: ConversionResult) : MeldScreenRoute()
}

data class MeldUiState(
    val activeTab: DockTab = DockTab.STUDIO,
    val route: MeldScreenRoute = MeldScreenRoute.Home,
    val selectedMode: MeldMode = MeldMode.ALL,
    val selectedCategory: MeldCategory = MeldCategory.ALL,
    val searchQuery: String = "",
    val selectedFile: SelectedFileInfo? = null,
    val options: ToolOptions = ToolOptions(),
    val isProcessing: Boolean = false,
    val showFileSheet: Boolean = false,
    val bannerMessage: String? = null,
    val recentResults: List<ConversionResult> = emptyList(),
    val defaultSaveToDownloads: Boolean = true
)

class MeldViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MeldUiState())
    val uiState: StateFlow<MeldUiState> = _uiState.asStateFlow()

    fun ensureDefaultFileLoaded(context: Context) {
        if (_uiState.value.selectedFile != null) return
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
                val pdf = SampleFileFactory.createSamplePdf(sampleDir)
                buildFileInfo(pdf)
            }
            _uiState.update { it.copy(selectedFile = info) }
        }
    }

    fun selectDockTab(tab: DockTab) {
        _uiState.update { it.copy(activeTab = tab, route = MeldScreenRoute.Home, bannerMessage = null) }
    }

    fun openTask(tool: MeldToolId) {
        _uiState.update { it.copy(route = MeldScreenRoute.Task(tool), bannerMessage = null) }
    }

    fun openResult(conversion: ConversionResult) {
        _uiState.update { it.copy(route = MeldScreenRoute.Result(conversion), bannerMessage = null) }
    }

    fun navigateHome() {
        _uiState.update { it.copy(route = MeldScreenRoute.Home, bannerMessage = null) }
    }

    fun setFileSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showFileSheet = visible) }
    }

    fun selectMode(mode: MeldMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun selectCategory(category: MeldCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateOptions(transform: (ToolOptions) -> ToolOptions) {
        _uiState.update { it.copy(options = transform(it.options)) }
    }

    fun toggleAutoDownloads(enabled: Boolean) {
        _uiState.update { it.copy(defaultSaveToDownloads = enabled) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun continueWithResult(conversion: ConversionResult, nextTool: MeldToolId) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { buildFileInfo(conversion.outputFile) }
            _uiState.update {
                it.copy(
                    selectedFile = info,
                    route = MeldScreenRoute.Task(nextTool),
                    bannerMessage = null
                )
            }
        }
    }

    fun loadSamplePreset(context: Context, preset: SamplePreset) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showFileSheet = false) }
            val file = withContext(Dispatchers.IO) {
                val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
                when (preset) {
                    SamplePreset.PDF_DECK -> SampleFileFactory.createSamplePdf(sampleDir)
                    SamplePreset.STUDIO_IMAGE -> SampleFileFactory.createSampleImage(sampleDir)
                    SamplePreset.SYNTH_AUDIO -> SampleFileFactory.createSampleAudioWav(sampleDir)
                    SamplePreset.BENCHMARK_CSV -> SampleFileFactory.createSampleCsv(sampleDir)
                }
            }
            val info = withContext(Dispatchers.IO) { buildFileInfo(file) }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    selectedFile = info,
                    bannerMessage = "Loaded ${info.cleanTitle}"
                )
            }
        }
    }

    fun importExternalUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showFileSheet = false) }
            val imported = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                var displayName = "document"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                }
                val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
                val destFile = File(importDir, displayName)
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                buildFileInfo(destFile)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    selectedFile = imported,
                    bannerMessage = "Ready: ${imported.cleanTitle}"
                )
            }
        }
    }

    fun executeTool(context: Context, tool: MeldToolId) {
        val current = _uiState.value
        val opts = current.options

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, bannerMessage = null) }

            runCatching {
                withContext(Dispatchers.IO) {
                    val startMs = System.currentTimeMillis()
                    val outDir = File(context.cacheDir, "outputs").apply { mkdirs() }

                    val inputFile = current.selectedFile?.file ?: autoProvideSampleForTool(context, tool)
                    val baseName = inputFile.nameWithoutExtension
                    val outFile = File(
                        outDir,
                        "${baseName}_${tool.name.lowercase()}.${tool.outputExtension}"
                    )

                    val summary = when (tool) {
                        // Image Conversions
                        MeldToolId.IMG_TO_JPG,
                        MeldToolId.IMG_TO_PNG,
                        MeldToolId.IMG_TO_WEBP,
                        MeldToolId.IMG_TO_BMP,
                        MeldToolId.IMG_TO_TIFF -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.convertFormat(imgFile, outFile, tool.outputExtension)
                        }
                        MeldToolId.IMG_TO_PDF -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.convertImageToPdf(imgFile, outFile)
                        }

                        // Image Tools
                        MeldToolId.REMOVE_BG -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.removeBackground(imgFile, outFile, opts.chromaTolerance)
                        }
                        MeldToolId.COMPRESS -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.compressToTargetKb(imgFile, outFile, opts.targetKb)
                        }
                        MeldToolId.RESIZE_IMAGE -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.resizeAndFrameImage(
                                imgFile,
                                outFile,
                                opts.resizePercent,
                                opts.addStudioFrame
                            )
                        }
                        MeldToolId.COLLAGE -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.createPhotoCollage(imgFile, outFile)
                        }
                        MeldToolId.STRIP_EXIF -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.stripImageExif(imgFile, outFile)
                        }
                        MeldToolId.SCAN_QR -> {
                            ImageEngine.generateQrStudioCard(opts.qrPayloadText, outFile)
                        }

                        // PDF Conversions
                        MeldToolId.PDF_TO_PNG -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.exportPdfToImage(pdfFile, outFile, "png")
                        }
                        MeldToolId.PDF_TO_JPG -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.exportPdfToImage(pdfFile, outFile, "jpg")
                        }
                        MeldToolId.PDF_TO_MD -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.convertPdfToMarkdown(pdfFile, outFile)
                        }
                        MeldToolId.PDF_TO_HTML -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.convertPdfToHtml(pdfFile, outFile)
                        }
                        MeldToolId.PDF_TO_DOCX -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.convertPdfToDocx(pdfFile, outFile)
                        }
                        MeldToolId.PDF_TO_TXT -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.extractPdfPlainText(pdfFile, outFile)
                        }

                        // PDF Tools
                        MeldToolId.COMPRESS_PDF -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.compressPdf(pdfFile, outFile, opts.targetKb)
                        }
                        MeldToolId.HANDOUT -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.buildHandoutPdf(pdfFile, outFile, opts.handoutPagesPerSheet)
                        }
                        MeldToolId.LOCK_STAMP -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.stampWatermark(pdfFile, outFile, opts.stampText)
                        }
                        MeldToolId.REDACT -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.redactAndFlattenPdf(pdfFile, outFile)
                        }
                        MeldToolId.PDF_EXTRACT -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.extractPageRange(
                                pdfFile,
                                outFile,
                                opts.pageRangeStart,
                                opts.pageRangeEnd
                            )
                        }
                        MeldToolId.PDF_MERGE -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.mergeOrDuplicatePdf(pdfFile, outFile)
                        }
                        MeldToolId.STRIP_PDF_META -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.stripPdfMetadata(pdfFile, outFile)
                        }

                        // Audio, Tables & Archive
                        MeldToolId.VISUALIZE -> {
                            MediaEngine.renderAudioWaveformPng(inputFile, outFile)
                        }
                        MeldToolId.SPEED_2X -> {
                            MediaEngine.speed2xAudio(inputFile, outFile)
                        }
                        MeldToolId.SHEET_TO_PDF -> {
                            val csvFile = ensureCsvInput(context, inputFile)
                            DocumentEngine.convertSheetToPdf(csvFile, outFile)
                        }
                        MeldToolId.SHEET_TO_MD -> {
                            val csvFile = ensureCsvInput(context, inputFile)
                            MediaEngine.convertCsvToMarkdownTable(csvFile, outFile)
                        }
                        MeldToolId.TABLE_CONVERT -> {
                            val csvFile = ensureCsvInput(context, inputFile)
                            MediaEngine.convertCsvToJson(csvFile, outFile)
                        }
                        MeldToolId.PACK_ZIP -> {
                            MediaEngine.packToZip(inputFile, outFile)
                        }
                    }

                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    ConversionResult(
                        tool = tool,
                        outputFile = outFile,
                        inputSizeBytes = inputFile.length(),
                        outputSizeBytes = outFile.length(),
                        summary = "$summary. Took $elapsed ms.",
                        durationMs = elapsed
                    )
                }
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        route = MeldScreenRoute.Result(result),
                        recentResults = (listOf(result) + state.recentResults).take(12)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        bannerMessage = err.message ?: "Something went wrong while running ${tool.title}."
                    )
                }
            }
        }
    }

    fun saveToDownloads(context: Context, file: File) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MELD")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Downloads.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                            "Downloads/MELD/${file.name}"
                        } else null
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "MELD"
                        ).apply { mkdirs() }
                        val dest = File(dir, file.name)
                        file.copyTo(dest, overwrite = true)
                        "Downloads/MELD/${file.name}"
                    }
                }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    bannerMessage = if (savedPath != null) {
                        "Saved to $savedPath"
                    } else {
                        "Saved locally — tap Share to export"
                    }
                )
            }
        }
    }

    private fun buildFileInfo(file: File): SelectedFileInfo {
        val ext = file.extension.lowercase()
        val pages = if (ext == "pdf") countPdfPages(file) else null
        return SelectedFileInfo(
            file = file,
            displayName = file.name,
            extension = ext,
            sizeBytes = file.length(),
            pageCount = pages
        )
    }

    private fun countPdfPages(file: File): Int? {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrNull()
    }

    private fun autoProvideSampleForTool(context: Context, tool: MeldToolId): File {
        val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
        return when (tool.category) {
            MeldCategory.IMAGE_STUDIO -> SampleFileFactory.createSampleImage(sampleDir)
            MeldCategory.PDF_DOCS -> SampleFileFactory.createSamplePdf(sampleDir)
            MeldCategory.AUDIO_VIDEO -> SampleFileFactory.createSampleAudioWav(sampleDir)
            MeldCategory.DATA_TABLES -> {
                if (tool == MeldToolId.PACK_ZIP) {
                    SampleFileFactory.createSamplePdf(sampleDir)
                } else {
                    SampleFileFactory.createSampleCsv(sampleDir)
                }
            }
            MeldCategory.ALL -> SampleFileFactory.createSampleImage(sampleDir)
        }
    }

    private fun ensureImageInput(context: Context, file: File): File {
        val ext = file.extension.lowercase()
        return if (ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff")) {
            file
        } else {
            SampleFileFactory.createSampleImage(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }

    private fun ensurePdfInput(context: Context, file: File): File {
        return if (file.extension.lowercase() == "pdf") {
            file
        } else {
            SampleFileFactory.createSamplePdf(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }

    private fun ensureCsvInput(context: Context, file: File): File {
        return if (file.extension.lowercase() in setOf("csv", "tsv", "txt")) {
            file
        } else {
            SampleFileFactory.createSampleCsv(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }
}
