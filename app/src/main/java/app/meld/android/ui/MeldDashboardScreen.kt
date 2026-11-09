package app.meld.android.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meld.android.R
import app.meld.android.model.ConversionResult
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import app.meld.android.ui.theme.MeldColors
import java.io.File

@Composable
fun MeldDashboardScreen(
    uiState: MeldUiState,
    onSelectCategory: (MeldCategory) -> Unit,
    onSelectTool: (MeldToolId) -> Unit,
    onPickFile: () -> Unit,
    onLoadSample: (SamplePreset) -> Unit,
    onRunTool: () -> Unit,
    onShareResult: (File) -> Unit,
    onUpdateTargetKb: (Int) -> Unit,
    onUpdateStampText: (String) -> Unit,
    onUpdateHandoutMode: (Int) -> Unit,
    onUpdateQrPayload: (String) -> Unit
) {
    val visibleTools = remember(uiState.selectedCategory) {
        MeldToolId.entries.filter {
            uiState.selectedCategory == MeldCategory.ALL || it.category == uiState.selectedCategory
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MeldColors.Background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. MELD Brand Header
            item {
                MeldTopBrandHeader()
            }

            // 2. File Staging & One-Tap Sample Bar
            item {
                MeldFileStagingCard(
                    selectedFile = uiState.selectedFile,
                    statusMessage = uiState.statusMessage,
                    isProcessing = uiState.isProcessing,
                    onPickFile = onPickFile,
                    onLoadSample = onLoadSample
                )
            }

            // 3. Category Filter Strip
            item {
                MeldCategoryFilterStrip(
                    selectedCategory = uiState.selectedCategory,
                    onSelectCategory = onSelectCategory
                )
            }

            // 4. Active Tool Inspector & Parameter Controls
            item {
                MeldActiveToolWorkbench(
                    tool = uiState.selectedTool,
                    options = uiState.options,
                    isProcessing = uiState.isProcessing,
                    onRunTool = onRunTool,
                    onUpdateTargetKb = onUpdateTargetKb,
                    onUpdateStampText = onUpdateStampText,
                    onUpdateHandoutMode = onUpdateHandoutMode,
                    onUpdateQrPayload = onUpdateQrPayload
                )
            }

            // 5. Live Output Preview Card (when available)
            item {
                AnimatedVisibility(visible = uiState.lastResult != null) {
                    uiState.lastResult?.let { result ->
                        MeldOutputPreviewCard(
                            result = result,
                            onShareResult = { onShareResult(result.outputFile) }
                        )
                    }
                }
            }

            // 6. All Tools Grid (2-column radial studio explorer feel)
            item {
                Text(
                    text = "STUDIO UTILITY ENGINE (${visibleTools.size} TOOLS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeldColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleTools.chunked(2).forEach { rowTools ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowTools.forEach { tool ->
                                MeldToolTile(
                                    tool = tool,
                                    isSelected = tool == uiState.selectedTool,
                                    onClick = { onSelectTool(tool) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTools.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeldTopBrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(MeldColors.Surface, MeldColors.SurfaceElevated)
                )
            )
            .border(1.dp, MeldColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.meld_logo),
            contentDescription = "MELD Ribbon Logo",
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MELD",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MeldColors.TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MeldColors.RibbonOrange.copy(alpha = 0.18f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ANDROID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeldColors.RibbonOrange
                    )
                }
            }
            Text(
                text = "TRANSFORM • CONVERT • ORGANIZE",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.TextSecondary
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MeldColors.Emerald.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "100% LOCAL",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.Emerald
            )
        }
    }
}

@Composable
private fun MeldFileStagingCard(
    selectedFile: SelectedFileInfo?,
    statusMessage: String,
    isProcessing: Boolean,
    onPickFile: () -> Unit,
    onLoadSample: (SamplePreset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeldColors.Surface),
        border = BorderStroke(1.dp, MeldColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFile?.displayName ?: "NO FILE STAGED YET",
                        style = MaterialTheme.typography.titleMedium,
                        color = MeldColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (selectedFile != null) {
                            "${selectedFile.extension.uppercase()} • ${selectedFile.formattedSize}"
                        } else {
                            "Pick any PDF, Image, Audio, or CSV — or load a built-in test file"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeldColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MeldColors.RibbonOrange),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MeldColors.RibbonOrange
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BROWSE", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "INSTANT 1-TAP TEST SAMPLES:",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SamplePreset.entries.forEach { preset ->
                    Surface(
                        modifier = Modifier.clickable { onLoadSample(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = MeldColors.SurfaceElevated,
                        border = BorderStroke(1.dp, MeldColors.CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeldColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = preset.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MeldColors.RibbonAmber
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MeldColors.RibbonOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeldColors.RibbonAmber
                )
            }
        }
    }
}

@Composable
private fun MeldCategoryFilterStrip(
    selectedCategory: MeldCategory,
    onSelectCategory: (MeldCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MeldCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            Surface(
                modifier = Modifier.clickable { onSelectCategory(category) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MeldColors.RibbonOrange else MeldColors.Surface,
                border = BorderStroke(
                    1.dp,
                    if (selected) MeldColors.RibbonOrange else MeldColors.CardBorder
                )
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else MeldColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MeldActiveToolWorkbench(
    tool: MeldToolId,
    options: app.meld.android.model.ToolOptions,
    isProcessing: Boolean,
    onRunTool: () -> Unit,
    onUpdateTargetKb: (Int) -> Unit,
    onUpdateStampText: (String) -> Unit,
    onUpdateHandoutMode: (Int) -> Unit,
    onUpdateQrPayload: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeldColors.SurfaceElevated),
        border = BorderStroke(1.5.dp, MeldColors.RibbonOrange.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = iconForTool(tool),
                        contentDescription = null,
                        tint = MeldColors.RibbonOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.TextPrimary
                        )
                        Text(
                            text = tool.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeldColors.TextSecondary
                        )
                    }
                }
            }

            // Contextual Tool Controls
            when (tool) {
                MeldToolId.COMPRESS -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "TARGET PORTAL LIMIT: ${options.targetKb} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeldColors.RibbonAmber
                    )
                    Slider(
                        value = options.targetKb.toFloat(),
                        onValueChange = { onUpdateTargetKb(it.toInt()) },
                        valueRange = 40f..800f,
                        colors = SliderDefaults.colors(
                            thumbColor = MeldColors.RibbonOrange,
                            activeTrackColor = MeldColors.RibbonOrange
                        )
                    )
                }

                MeldToolId.LOCK_STAMP -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = options.stampText,
                        onValueChange = onUpdateStampText,
                        label = { Text("Diagonal Security Stamp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                MeldToolId.HANDOUT -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2 to "2-UP + NOTE LINES", 4 to "4-UP STUDY GRID").forEach { (mode, label) ->
                            val active = options.handoutPagesPerSheet == mode
                            OutlinedButton(
                                onClick = { onUpdateHandoutMode(mode) },
                                border = BorderStroke(
                                    1.dp,
                                    if (active) MeldColors.RibbonOrange else MeldColors.CardBorder
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (active) MeldColors.RibbonOrange.copy(alpha = 0.2f) else Color.Transparent,
                                    contentColor = if (active) MeldColors.RibbonOrange else MeldColors.TextSecondary
                                )
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                MeldToolId.SCAN_QR -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = options.qrPayloadText,
                        onValueChange = onUpdateQrPayload,
                        label = { Text("QR Code Payload / URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> Unit
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRunTool,
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeldColors.RibbonOrange,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isProcessing) "PROCESSING..." else "EXECUTE ${tool.title} (OFFLINE)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MeldOutputPreviewCard(
    result: ConversionResult,
    onShareResult: () -> Unit
) {
    val previewBitmap = remember(result.outputFile.absolutePath, result.outputSizeBytes) {
        val ext = result.outputFile.extension.lowercase()
        if (ext in setOf("png", "jpg", "jpeg", "webp") && result.outputFile.exists()) {
            BitmapFactory.decodeFile(result.outputFile.absolutePath)
        } else {
            null
        }
    }

    val textPreview = remember(result.outputFile.absolutePath, result.outputSizeBytes) {
        val ext = result.outputFile.extension.lowercase()
        if (ext == "json" && result.outputFile.exists()) {
            result.outputFile.readText().take(420)
        } else {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeldColors.Surface),
        border = BorderStroke(1.dp, MeldColors.Emerald.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MeldColors.Emerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = result.outputFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.TextPrimary
                        )
                        Text(
                            text = "${SelectedFileInfo.formatByteCount(result.outputSizeBytes)} • Completed in ${result.durationMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeldColors.Emerald
                        )
                    }
                }

                Button(
                    onClick = onShareResult,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeldColors.Emerald,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE / SAVE", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (previewBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Output Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A0B0E)),
                    contentScale = ContentScale.Fit
                )
            } else if (textPreview != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0B0E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = textPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MeldColors.RibbonAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun MeldToolTile(
    tool: MeldToolId,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MeldColors.SurfaceElevated else MeldColors.Surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MeldColors.RibbonOrange else MeldColors.CardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = iconForTool(tool),
                    contentDescription = null,
                    tint = if (isSelected) MeldColors.RibbonOrange else MeldColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MeldColors.RibbonOrange.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tool.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MeldColors.RibbonAmber,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                color = MeldColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MeldColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
        }
    }
}

private fun iconForTool(tool: MeldToolId): ImageVector = when (tool) {
    MeldToolId.REMOVE_BG -> Icons.Default.AutoAwesome
    MeldToolId.COMPRESS -> Icons.Default.Compress
    MeldToolId.LOCK_STAMP -> Icons.Default.Lock
    MeldToolId.HANDOUT -> Icons.Default.GridView
    MeldToolId.REDACT -> Icons.Default.VisibilityOff
    MeldToolId.SCAN_QR -> Icons.Default.QrCode2
    MeldToolId.VISUALIZE -> Icons.Default.GraphicEq
    MeldToolId.PDF_EXTRACT -> Icons.Default.Description
    MeldToolId.TABLE_CONVERT -> Icons.Default.TableChart
}

