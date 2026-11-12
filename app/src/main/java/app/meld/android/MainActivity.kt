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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import app.meld.android.ui.MeldDashboardScreen
import app.meld.android.ui.MeldViewModel
import app.meld.android.ui.theme.MeldTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MeldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0D0E12")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0D0E12"))
        )

        // Handle incoming "Share to MELD" intent from other Android apps
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (sharedUri != null) {
                viewModel.importExternalUri(this, sharedUri)
            }
        }

        setContent {
            MeldTheme {
                val uiState by viewModel.uiState.collectAsState()

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        viewModel.importExternalUri(this@MainActivity, uri)
                    }
                }

                MeldDashboardScreen(
                    uiState = uiState,
                    onSelectMode = viewModel::selectMode,
                    onSelectCategory = viewModel::selectCategory,
                    onSelectTool = viewModel::selectTool,
                    onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onLoadSample = { preset ->
                        viewModel.loadSamplePreset(this@MainActivity, preset)
                    },
                    onRunTool = {
                        viewModel.executeSelectedTool(this@MainActivity)
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
                    onUpdateHandoutMode = { pagesPerSheet ->
                        viewModel.updateOptions { it.copy(handoutPagesPerSheet = pagesPerSheet) }
                    },
                    onUpdateQrPayload = { payload ->
                        viewModel.updateOptions { it.copy(qrPayloadText = payload) }
                    },
                    onUpdatePageRange = { start, end ->
                        viewModel.updateOptions { it.copy(pageRangeStart = start, pageRangeEnd = end) }
                    }
                )
            }
        }
    }

    private fun shareOutputFile(file: File) {
        if (!file.exists()) return
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
            "wav" -> "audio/wav"
            "zip" -> "application/zip"
            else -> "*/*"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share MELD Output"))
    }
}

