package app.meld.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.meld.android.engine.DocumentEngine
import app.meld.android.engine.ImageEngine
import app.meld.android.engine.MediaEngine
import app.meld.android.engine.SampleFileFactory
import app.meld.android.model.ConversionResult
import app.meld.android.model.MeldCategory
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
    PDF_DECK("Lecture Deck.pdf", "4 PAGES"),
    STUDIO_IMAGE("Studio Subject.png", "1080x1080"),
    SYNTH_AUDIO("Studio Synth.wav", "PCM WAV"),
    BENCHMARK_CSV("Benchmark.csv", "5 ROWS")
}

data class MeldUiState(
    val selectedCategory: MeldCategory = MeldCategory.ALL,
    val selectedTool: MeldToolId = MeldToolId.REMOVE_BG,
    val selectedFile: SelectedFileInfo? = null,
    val options: ToolOptions = ToolOptions(),
    val isProcessing: Boolean = false,
    val statusMessage: String = "Select a file or tap a built-in sample below to begin.",
    val lastResult: ConversionResult? = null,
    val history: List<ConversionResult> = emptyList()
)

class MeldViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MeldUiState())
    val uiState: StateFlow<MeldUiState> = _uiState.asStateFlow()

    fun selectCategory(category: MeldCategory) {
        _uiState.update { state ->
            val matchingTools = MeldToolId.entries.filter {
                category == MeldCategory.ALL || it.category == category
            }
            val nextTool = if (matchingTools.contains(state.selectedTool)) {
                state.selectedTool
            } else {
                matchingTools.firstOrNull() ?: state.selectedTool
            }
            state.copy(selectedCategory = category, selectedTool = nextTool)
        }
    }

    fun selectTool(tool: MeldToolId) {
        _uiState.update { it.copy(selectedTool = tool) }
    }

    fun updateOptions(transform: (ToolOptions) -> ToolOptions) {
        _uiState.update { it.copy(options = transform(it.options)) }
    }

    fun loadSamplePreset(context: Context, preset: SamplePreset) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Preparing ${preset.label}...") }
            val resultPair = withContext(Dispatchers.IO) {
                val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
                when (preset) {
                    SamplePreset.PDF_DECK ->
                        SampleFileFactory.createSamplePdf(sampleDir) to MeldToolId.HANDOUT
                    SamplePreset.STUDIO_IMAGE ->
                        SampleFileFactory.createSampleImage(sampleDir) to MeldToolId.REMOVE_BG
                    SamplePreset.SYNTH_AUDIO ->
                        SampleFileFactory.createSampleAudioWav(sampleDir) to MeldToolId.VISUALIZE
                    SamplePreset.BENCHMARK_CSV ->
                        SampleFileFactory.createSampleCsv(sampleDir) to MeldToolId.TABLE_CONVERT
                }
            }
            val file = resultPair.first
            val recommendedTool = resultPair.second
            val info = SelectedFileInfo(
                file = file,
                displayName = file.name,
                extension = file.extension.lowercase(),
                sizeBytes = file.length()
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    selectedFile = info,
                    selectedTool = recommendedTool,
                    statusMessage = "Loaded ${info.displayName} (${info.formattedSize}) • Ready for ${recommendedTool.title}"
                )
            }
        }
    }

    fun importExternalUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Importing file...") }
            val imported = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                var displayName = "imported_file"
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
                destFile
            }

            val ext = imported.extension.lowercase()
            val matchingTool = MeldToolId.entries.firstOrNull { it.acceptsExtension(ext) }
                ?: _uiState.value.selectedTool

            val info = SelectedFileInfo(
                file = imported,
                displayName = imported.name,
                extension = ext,
                sizeBytes = imported.length()
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    selectedFile = info,
                    selectedCategory = matchingTool.category,
                    selectedTool = matchingTool,
                    statusMessage = "Staged ${info.displayName} (${info.formattedSize}) • Ready for ${matchingTool.title}"
                )
            }
        }
    }

    fun executeSelectedTool(context: Context) {
        val current = _uiState.value
        val tool = current.selectedTool
        val opts = current.options

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Running ${tool.title} locally..."
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    val startMs = System.currentTimeMillis()
                    val outDir = File(context.cacheDir, "outputs").apply { mkdirs() }

                    // Ensure we have a valid input file (auto-generate matching sample if none picked)
                    val inputFile = current.selectedFile?.file ?: autoProvideSampleForTool(context, tool)
                    val baseName = inputFile.nameWithoutExtension
                    val outFile = File(
                        outDir,
                        "${baseName}_${tool.name.lowercase()}.${tool.outputExtension}"
                    )

                    val summary = when (tool) {
                        MeldToolId.REMOVE_BG -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.removeBackground(imgFile, outFile, opts.chromaTolerance)
                        }
                        MeldToolId.COMPRESS -> {
                            val imgFile = ensureImageInput(context, inputFile)
                            ImageEngine.compressToTargetKb(imgFile, outFile, opts.targetKb)
                        }
                        MeldToolId.LOCK_STAMP -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.stampWatermark(pdfFile, outFile, opts.stampText)
                        }
                        MeldToolId.HANDOUT -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.buildHandoutPdf(pdfFile, outFile, opts.handoutPagesPerSheet)
                        }
                        MeldToolId.REDACT -> {
                            val pdfFile = ensurePdfInput(context, inputFile)
                            DocumentEngine.redactAndFlattenPdf(pdfFile, outFile)
                        }
                        MeldToolId.SCAN_QR -> {
                            ImageEngine.generateQrStudioCard(opts.qrPayloadText, outFile)
                        }
                        MeldToolId.VISUALIZE -> {
                            MediaEngine.renderAudioWaveformPng(inputFile, outFile)
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
                        MeldToolId.TABLE_CONVERT -> {
                            val csvFile = ensureCsvInput(context, inputFile)
                            MediaEngine.convertCsvToJson(csvFile, outFile)
                        }
                    }

                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    ConversionResult(
                        tool = tool,
                        outputFile = outFile,
                        inputSizeBytes = inputFile.length(),
                        outputSizeBytes = outFile.length(),
                        summary = summary,
                        durationMs = elapsed
                    )
                }
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        lastResult = result,
                        history = (listOf(result) + state.history).take(12),
                        statusMessage = "✓ ${result.summary} (${result.durationMs} ms)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "⚠ Error: ${err.message ?: "Conversion failed"}"
                    )
                }
            }
        }
    }

    private fun autoProvideSampleForTool(context: Context, tool: MeldToolId): File {
        val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
        return when (tool) {
            MeldToolId.REMOVE_BG, MeldToolId.COMPRESS -> SampleFileFactory.createSampleImage(sampleDir)
            MeldToolId.LOCK_STAMP, MeldToolId.HANDOUT, MeldToolId.REDACT, MeldToolId.PDF_EXTRACT ->
                SampleFileFactory.createSamplePdf(sampleDir)
            MeldToolId.VISUALIZE -> SampleFileFactory.createSampleAudioWav(sampleDir)
            MeldToolId.TABLE_CONVERT -> SampleFileFactory.createSampleCsv(sampleDir)
            MeldToolId.SCAN_QR -> SampleFileFactory.createSampleImage(sampleDir)
        }
    }

    private fun ensureImageInput(context: Context, file: File): File {
        val ext = file.extension.lowercase()
        return if (ext in setOf("png", "jpg", "jpeg", "webp")) {
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

