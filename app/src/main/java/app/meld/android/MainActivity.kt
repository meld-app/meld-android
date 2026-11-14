package app.meld.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import app.meld.android.R
import app.meld.android.ui.MeldAppRoot
import app.meld.android.ui.MeldViewModel
import app.meld.android.ui.theme.MeldTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MeldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lightCanvas = android.graphics.Color.parseColor("#FAF4EC")
        val darkCanvas = android.graphics.Color.parseColor("#151210")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(lightCanvas, darkCanvas),
            navigationBarStyle = SystemBarStyle.auto(lightCanvas, darkCanvas)
        )

        // Handle incoming "Share to MELD" intent on fresh launch (skip on rotation/history relaunch)
        val fromHistory = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) {
            handleIncomingIntent(intent)
        }

        setContent {
            MeldTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.loadPersistedPreferences(this@MainActivity)
                    viewModel.ensureDefaultFileLoaded(this@MainActivity)
                }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        viewModel.importExternalUri(this@MainActivity, uri)
                    }
                }

                val multiFilePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris: List<Uri> ->
                    if (uris.isNotEmpty()) {
                        val activeTool = (uiState.route as? app.meld.android.ui.MeldScreenRoute.Task)?.tool
                        if (activeTool != null && activeTool.supportsBatchQueue) {
                            viewModel.appendExternalUrisToBatch(this@MainActivity, activeTool, uris)
                        }
                    }
                }

                MeldAppRoot(
                    uiState = uiState,
                    onSelectTab = viewModel::selectDockTab,
                    onOpenTask = viewModel::openTask,
                    onOpenResult = viewModel::openResult,
                    onNavigateHome = viewModel::navigateHome,
                    onSetFileSheetVisible = viewModel::setFileSheetVisible,
                    onClearBanner = viewModel::clearBanner,
                    onPickExternalFile = {
                        viewModel.setFileSheetVisible(false)
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onPickBatchFiles = { tool ->
                        val mimeTypes = when (tool) {
                            app.meld.android.model.MeldToolId.PDF_MERGE -> arrayOf("application/pdf")
                            app.meld.android.model.MeldToolId.COLLAGE -> arrayOf("image/*")
                            else -> arrayOf("*/*")
                        }
                        multiFilePickerLauncher.launch(mimeTypes)
                    },
                    onAddBatchSample = { tool ->
                        viewModel.appendSampleToBatch(this@MainActivity, tool)
                    },
                    onAddBatchRecent = { tool, conv ->
                        viewModel.appendRecentResultToBatch(this@MainActivity, tool, conv)
                    },
                    onDuplicateBatchItem = { tool, index ->
                        viewModel.duplicateBatchItem(this@MainActivity, tool, index)
                    },
                    onMoveBatchItem = viewModel::moveBatchItem,
                    onRemoveBatchItem = viewModel::removeBatchItem,
                    onLoadSample = { preset ->
                        viewModel.loadSamplePreset(this@MainActivity, preset)
                    },
                    onSelectMode = viewModel::selectMode,
                    onSelectCategory = viewModel::selectCategory,
                    onSearchChange = viewModel::updateSearchQuery,
                    onRunTool = { tool ->
                        viewModel.executeTool(this@MainActivity, tool)
                    },
                    onContinueTool = viewModel::continueWithResult,
                    onSaveToDownloads = { file ->
                        viewModel.saveToDownloads(this@MainActivity, file)
                    },
                    onShareResult = { outputFile ->
                        shareOutputFile(outputFile)
                    },
                    onUpdateTargetKb = { kb ->
                        viewModel.updateOptions { it.copy(targetKb = kb) }
                    },
                    onUpdateResizePercent = { pct ->
                        viewModel.updateOptions { it.copy(resizePercent = pct) }
                    },
                    onToggleStudioFrame = { enabled ->
                        viewModel.updateOptions { it.copy(addStudioFrame = enabled) }
                    },
                    onUpdateStampText = { text ->
                        viewModel.updateOptions { it.copy(stampText = text) }
                    },
                    onUpdateQrPayload = { payload ->
                        viewModel.updateOptions { it.copy(qrPayloadText = payload) }
                    },
                    onUpdatePageRange = { start, end ->
                        viewModel.updateOptions { it.copy(pageRangeStart = start, pageRangeEnd = end) }
                    },
                    onUpdatePdfImageScope = { allPages, pageNumber ->
                        viewModel.updateOptions {
                            it.copy(pdfImageAllPages = allPages, pdfImagePage = pageNumber)
                        }
                    },
                    onUpdateChromaTolerance = { tol ->
                        viewModel.updateOptions { it.copy(chromaTolerance = tol) }
                    },
                    onUpdateRedactionZones = { zones ->
                        viewModel.updateOptions { it.copy(redactionZones = zones) }
                    },
                    onToggleAutoDownloads = { enabled ->
                        viewModel.toggleAutoDownloads(this@MainActivity, enabled)
                    },
                    onUpdateHandoutMode = { pagesPerSheet ->
                        viewModel.updateHandoutPagesPerSheet(this@MainActivity, pagesPerSheet)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        when (incoming?.action) {
            Intent.ACTION_SEND -> {
                val sharedUri = IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)
                // Mark consumed so configuration changes will not re-import stale intents
                incoming.action = null
                if (sharedUri != null) {
                    viewModel.importExternalUri(this, sharedUri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val sharedUris = IntentCompat.getParcelableArrayListExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)
                incoming.action = null
                if (!sharedUris.isNullOrEmpty()) {
                    viewModel.importMultipleIncomingUris(this, sharedUris)
                }
            }
        }
    }

    private fun shareOutputFile(file: File) {
        if (!file.exists()) return
        runCatching {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val mimeType = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "tiff", "tif" -> "image/tiff"
                "json" -> "application/json"
                "md", "txt" -> "text/plain"
                "html" -> "text/html"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "wav" -> "audio/wav"
                "zip" -> "application/zip"
                else -> "*/*"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        }
    }
}
