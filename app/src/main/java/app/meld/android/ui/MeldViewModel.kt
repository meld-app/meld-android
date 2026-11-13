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
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meld.android.R
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

enum class SamplePreset(@StringRes val labelRes: Int, @StringRes val badgeRes: Int) {
    PDF_DECK(R.string.sample_pdf_label, R.string.sample_pdf_badge),
    STUDIO_IMAGE(R.string.sample_image_label, R.string.sample_image_badge),
    SYNTH_AUDIO(R.string.sample_audio_label, R.string.sample_audio_badge),
    BENCHMARK_CSV(R.string.sample_csv_label, R.string.sample_csv_badge)
}

enum class DockTab(@StringRes val labelRes: Int) {
    STUDIO(R.string.nav_studio),
    ALL_TOOLS(R.string.nav_tools),
    SETTINGS(R.string.nav_prefs)
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
    val isSelectedFileSample: Boolean = true,
    val options: ToolOptions = ToolOptions(),
    val isProcessing: Boolean = false,
    val showFileSheet: Boolean = false,
    val bannerMessage: String? = null,
    val isBannerError: Boolean = false,
    val recentResults: List<ConversionResult> = emptyList(),
    val defaultSaveToDownloads: Boolean = false
)

class MeldViewModel : ViewModel() {

    private companion object {
        const val PREFS_NAME = "meld_prefs"
        const val KEY_AUTO_DOWNLOADS = "default_save_to_downloads"
        const val KEY_HANDOUT_PAGES = "handout_pages_per_sheet"
        const val MAX_IMPORT_BYTES = 100L * 1024L * 1024L // 100 MB safety cap
        const val MAX_CACHED_FILES = 16
        val VALID_EXT_REGEX = Regex("^[A-Za-z0-9]{1,8}$")
    }

    private val _uiState = MutableStateFlow(MeldUiState())
    val uiState: StateFlow<MeldUiState> = _uiState.asStateFlow()

    private val importInFlightCount = AtomicInteger(0)
    private var activeToolJob: Job? = null
    private var activeImportJob: Job? = null
    private var prefsLoaded = false

    fun loadPersistedPreferences(context: Context) {
        if (prefsLoaded) return
        prefsLoaded = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedAuto = prefs.getBoolean(KEY_AUTO_DOWNLOADS, false)
        val savedHandout = prefs.getInt(KEY_HANDOUT_PAGES, 3).coerceIn(2, 4)
        _uiState.update { state ->
            state.copy(
                defaultSaveToDownloads = savedAuto,
                options = state.options.copy(handoutPagesPerSheet = savedHandout)
            )
        }
    }

    fun ensureDefaultFileLoaded(context: Context) {
        if (_uiState.value.selectedFile != null || importInFlightCount.get() > 0) return
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
                val pdf = SampleFileFactory.createSamplePdf(sampleDir)
                buildFileInfo(pdf)
            }
            _uiState.update { state ->
                // Never overwrite an incoming ACTION_SEND or user-picked file loaded concurrently
                if (state.selectedFile != null || importInFlightCount.get() > 0) {
                    state
                } else {
                    state.copy(
                        selectedFile = info,
                        isSelectedFileSample = true,
                        options = clampOptionsForFile(info, state.options)
                    )
                }
            }
        }
    }

    fun selectDockTab(tab: DockTab) {
        activeToolJob?.cancel()
        activeToolJob = null
        _uiState.update {
            it.copy(
                activeTab = tab,
                route = MeldScreenRoute.Home,
                isProcessing = false,
                bannerMessage = null,
                isBannerError = false
            )
        }
    }

    fun openTask(tool: MeldToolId) {
        _uiState.update { it.copy(route = MeldScreenRoute.Task(tool), bannerMessage = null, isBannerError = false) }
    }

    fun openResult(conversion: ConversionResult) {
        _uiState.update { it.copy(route = MeldScreenRoute.Result(conversion), bannerMessage = null, isBannerError = false) }
    }

    fun navigateHome() {
        activeToolJob?.cancel()
        activeToolJob = null
        _uiState.update {
            it.copy(
                route = MeldScreenRoute.Home,
                isProcessing = false,
                bannerMessage = null,
                isBannerError = false
            )
        }
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

    fun updateHandoutPagesPerSheet(context: Context, pagesPerSheet: Int) {
        val clamped = pagesPerSheet.coerceIn(2, 4)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HANDOUT_PAGES, clamped)
            .apply()
        _uiState.update { it.copy(options = it.options.copy(handoutPagesPerSheet = clamped)) }
    }

    fun toggleAutoDownloads(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_DOWNLOADS, enabled)
            .apply()
        _uiState.update { it.copy(defaultSaveToDownloads = enabled) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null, isBannerError = false) }
    }

    fun continueWithResult(conversion: ConversionResult, nextTool: MeldToolId) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { buildFileInfo(conversion.outputFile) }
            _uiState.update {
                it.copy(
                    selectedFile = info,
                    isSelectedFileSample = false,
                    options = clampOptionsForFile(info, it.options),
                    route = MeldScreenRoute.Task(nextTool),
                    bannerMessage = null,
                    isBannerError = false
                )
            }
        }
    }

    fun loadSamplePreset(context: Context, preset: SamplePreset) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showFileSheet = false, bannerMessage = null, isBannerError = false) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
                    val file = when (preset) {
                        SamplePreset.PDF_DECK -> SampleFileFactory.createSamplePdf(sampleDir)
                        SamplePreset.STUDIO_IMAGE -> SampleFileFactory.createSampleImage(sampleDir)
                        SamplePreset.SYNTH_AUDIO -> SampleFileFactory.createSampleAudioWav(sampleDir)
                        SamplePreset.BENCHMARK_CSV -> SampleFileFactory.createSampleCsv(sampleDir)
                    }
                    buildFileInfo(file)
                }
            }
            result.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        selectedFile = info,
                        isSelectedFileSample = true,
                        options = clampOptionsForFile(info, it.options),
                        bannerMessage = context.getString(R.string.vm_loaded_sample, info.cleanTitle),
                        isBannerError = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        bannerMessage = err.message ?: context.getString(R.string.vm_failed_create_sample),
                        isBannerError = true
                    )
                }
            }
        }
    }

    fun importExternalUri(context: Context, uri: Uri) {
        activeToolJob?.cancel()
        activeToolJob = null
        activeImportJob?.cancel()

        importInFlightCount.incrementAndGet()
        activeImportJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showFileSheet = false, bannerMessage = null, isBannerError = false) }
            try {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val scheme = uri.scheme?.lowercase()
                        require(scheme == "content") {
                            context.getString(R.string.vm_err_content_uri_only)
                        }
                        val selfAuthority = "${context.packageName}.fileprovider"
                        require(!uri.authority.equals(selfAuthority, ignoreCase = true)) {
                            context.getString(R.string.vm_err_internal_uri)
                        }

                        val resolver = context.contentResolver
                        var displayName = "document"
                        var declaredSize = -1L
                        resolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                if (nameIndex >= 0) {
                                    displayName = cursor.getString(nameIndex) ?: displayName
                                }
                                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                    declaredSize = cursor.getLong(sizeIndex)
                                }
                            }
                        }
                        if (declaredSize > MAX_IMPORT_BYTES) {
                            throw IllegalArgumentException(context.getString(R.string.vm_err_exceeds_limit))
                        }

                        val sanitized = displayName
                            .replace(Regex("[^A-Za-z0-9._-]"), "_")
                            .trim('.')
                            .let { candidate ->
                                if (candidate.isBlank() || candidate == "." || candidate == "..") "document" else candidate
                            }

                        val candidateExt = if (sanitized.contains('.')) sanitized.substringAfterLast('.', "") else ""
                        val withExt = if (VALID_EXT_REGEX.matches(candidateExt)) {
                            sanitized
                        } else {
                            val mime = resolver.getType(uri)
                            val inferred = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                            val baseOnly = sanitized.substringBeforeLast('.').ifBlank { "document" }
                            if (!inferred.isNullOrBlank()) "$baseOnly.$inferred" else sanitized
                        }

                        val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
                        pruneOldCacheFiles(importDir, maxKeep = MAX_CACHED_FILES)

                        val destFile = File.createTempFile("imp_", "_$withExt", importDir)
                        val stream = resolver.openInputStream(uri)
                            ?: run {
                                destFile.delete()
                                throw IllegalStateException(context.getString(R.string.vm_err_cannot_read_stream))
                            }

                        var copyCompleted = false
                        try {
                            stream.use { input ->
                                FileOutputStream(destFile).use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    var totalBytes = 0L
                                    while (true) {
                                        ensureActive()
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        totalBytes += read
                                        if (totalBytes > MAX_IMPORT_BYTES) {
                                            throw IllegalArgumentException(context.getString(R.string.vm_err_stream_exceeds_limit))
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                    output.flush()
                                }
                            }
                            if (!destFile.exists() || destFile.length() == 0L) {
                                throw IllegalStateException(context.getString(R.string.vm_err_empty_file))
                            }
                            copyCompleted = true
                        } finally {
                            if (!copyCompleted) {
                                destFile.delete()
                            }
                        }
                        buildFileInfo(destFile, friendlyDisplayName = withExt)
                    }
                }
                result.onSuccess { imported ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            selectedFile = imported,
                            isSelectedFileSample = false,
                            options = clampOptionsForFile(imported, it.options),
                            bannerMessage = context.getString(R.string.vm_ready_file, imported.cleanTitle),
                            isBannerError = false
                        )
                    }
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            bannerMessage = err.message ?: context.getString(R.string.vm_err_could_not_open),
                            isBannerError = true
                        )
                    }
                }
            } finally {
                importInFlightCount.decrementAndGet()
            }
        }
    }

    fun executeTool(context: Context, tool: MeldToolId) {
        val current = _uiState.value
        if (current.isProcessing) return
        val opts = current.options

        activeToolJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, bannerMessage = null, isBannerError = false) }

            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val startMs = System.currentTimeMillis()
                    val outDir = File(context.cacheDir, "outputs").apply { mkdirs() }
                    pruneOldCacheFiles(outDir, maxKeep = MAX_CACHED_FILES)

                    val inputFile = current.selectedFile?.file ?: autoProvideSampleForTool(context, tool)
                    val qrSlug = opts.qrPayloadText.trim().ifBlank { "meld.app" }
                        .replace(Regex("[^A-Za-z0-9_-]"), "_")
                        .take(24)
                        .ifBlank { "qr" }
                    val baseName = if (tool == MeldToolId.SCAN_QR) {
                        "qr_$qrSlug"
                    } else {
                        inputFile.nameWithoutExtension.ifBlank { "output" }
                    }
                    val uniqueTag = System.currentTimeMillis() % 100000L
                    val outFile = File(
                        outDir,
                        "${baseName}_${tool.name.lowercase()}_${uniqueTag}.${tool.outputExtension}"
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
                            val payload = opts.qrPayloadText.trim().ifBlank { "meld.app" }
                            ImageEngine.generateQrStudioCard(payload, outFile)
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
                            DocumentEngine.redactAndFlattenPdf(pdfFile, outFile, opts.redactionZones)
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
                            val audioFile = ensureAudioInput(context, inputFile)
                            MediaEngine.renderAudioWaveformPng(audioFile, outFile)
                        }
                        MeldToolId.SPEED_2X -> {
                            val audioFile = ensureAudioInput(context, inputFile)
                            MediaEngine.speed2xAudio(audioFile, outFile)
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
                            val targetForZip = if (tool.acceptsExtension(inputFile.extension) && inputFile.isFile) {
                                inputFile
                            } else {
                                autoProvideSampleForTool(context, tool)
                            }
                            MediaEngine.packToZip(targetForZip, outFile)
                        }
                    }

                    ensureActive()
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val recordedInputSize = if (tool == MeldToolId.SCAN_QR) {
                        opts.qrPayloadText.trim().ifBlank { "meld.app" }.length.toLong()
                    } else {
                        inputFile.length()
                    }
                    ConversionResult(
                        tool = tool,
                        outputFile = outFile,
                        inputSizeBytes = recordedInputSize,
                        outputSizeBytes = outFile.length(),
                        summary = context.getString(R.string.vm_result_summary_with_duration, summary, elapsed),
                        durationMs = elapsed
                    )
                }
            }

            outcome.onSuccess { result ->
                // Respect user navigation away / cancellation cleanly
                if (_uiState.value.route !is MeldScreenRoute.Task) {
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }
                val autoSavedPath = if (current.defaultSaveToDownloads) {
                    withContext(Dispatchers.IO) { persistToDownloads(context, result.outputFile) }
                } else null
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        route = MeldScreenRoute.Result(result),
                        recentResults = (listOf(result) + state.recentResults).take(12),
                        bannerMessage = autoSavedPath?.let { context.getString(R.string.vm_auto_saved_to, it) },
                        isBannerError = false
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) {
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        bannerMessage = err.message ?: context.getString(
                            R.string.vm_err_running_tool,
                            context.getString(tool.titleRes)
                        ),
                        isBannerError = true
                    )
                }
            }
        }
    }

    fun saveToDownloads(context: Context, file: File) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) { persistToDownloads(context, file) }
            _uiState.update {
                it.copy(
                    bannerMessage = if (savedPath != null) {
                        context.getString(R.string.vm_saved_to, savedPath)
                    } else {
                        context.getString(R.string.vm_saved_locally_share)
                    },
                    isBannerError = false
                )
            }
        }
    }

    private fun persistToDownloads(context: Context, file: File): String? {
        return runCatching {
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

    private fun pruneOldCacheFiles(dir: File, maxKeep: Int) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        val activeInputPath = _uiState.value.selectedFile?.file?.absolutePath
        val recentPaths = _uiState.value.recentResults.map { it.outputFile.absolutePath }.toSet()
        files.drop(maxKeep).forEach { candidate ->
            if (candidate.absolutePath != activeInputPath && candidate.absolutePath !in recentPaths) {
                candidate.delete()
            }
        }
    }

    private fun clampOptionsForFile(info: SelectedFileInfo, current: ToolOptions): ToolOptions {
        val pages = (info.pageCount ?: 4).coerceAtLeast(1)
        val start = current.pageRangeStart.coerceIn(1, pages)
        val end = current.pageRangeEnd.coerceIn(start, pages)
        val fileKb = (info.sizeBytes / 1024L).toInt().coerceAtLeast(10)
        val sensibleTargetKb = if (current.targetKb >= fileKb && fileKb >= 16) {
            (fileKb * 0.6f).toInt().coerceIn(10, fileKb - 2)
        } else {
            current.targetKb.coerceIn(10, maxOf(40, fileKb))
        }
        return current.copy(
            pageRangeStart = start,
            pageRangeEnd = end,
            targetKb = sensibleTargetKb
        )
    }

    private fun buildFileInfo(file: File, friendlyDisplayName: String? = null): SelectedFileInfo {
        val nameToUse = friendlyDisplayName ?: file.name
        val ext = file.extension.lowercase()
        val pages = if (ext == "pdf") countPdfPages(file) else null
        return SelectedFileInfo(
            file = file,
            displayName = nameToUse,
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
        return if (ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif") && file.isFile) {
            file
        } else {
            SampleFileFactory.createSampleImage(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }

    private fun ensurePdfInput(context: Context, file: File): File {
        return if (file.extension.lowercase() == "pdf" && file.isFile) {
            file
        } else {
            SampleFileFactory.createSamplePdf(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }

    private fun ensureCsvInput(context: Context, file: File): File {
        return if (file.extension.lowercase() in setOf("csv", "tsv", "txt") && file.isFile) {
            file
        } else {
            SampleFileFactory.createSampleCsv(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }

    private fun ensureAudioInput(context: Context, file: File): File {
        return if (file.extension.lowercase() in setOf("wav", "pcm", "mp3", "m4a", "aac", "ogg", "mp4") && file.isFile) {
            file
        } else {
            SampleFileFactory.createSampleAudioWav(File(context.cacheDir, "samples").apply { mkdirs() })
        }
    }
}
