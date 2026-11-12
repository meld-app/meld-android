package app.meld.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meld.android.engine.ImageEngine
import app.meld.android.model.ConversionResult
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import app.meld.android.ui.MeldUiState
import app.meld.android.ui.components.BeforeAfterComparisonCard
import app.meld.android.ui.components.ResultShowcaseCard
import app.meld.android.ui.components.TactileCard
import app.meld.android.ui.components.TactileChipPill
import app.meld.android.ui.components.TactileCircleIconButton
import app.meld.android.ui.components.TactileRulerSlider
import app.meld.android.ui.theme.MeldColors
import java.io.File
import kotlin.math.max

@Composable
fun MeldTaskScreen(
    tool: MeldToolId,
    uiState: MeldUiState,
    onBack: () -> Unit,
    onRunTool: () -> Unit,
    onUpdateTargetKb: (Int) -> Unit,
    onUpdateResizePercent: (Int) -> Unit,
    onToggleStudioFrame: (Boolean) -> Unit,
    onUpdateStampText: (String) -> Unit,
    onUpdateHandoutMode: (Int) -> Unit,
    onUpdateQrPayload: (String) -> Unit,
    onUpdatePageRange: (Int, Int) -> Unit,
    onUpdateChromaTolerance: (Int) -> Unit
) {
    val file = uiState.selectedFile
    val options = uiState.options
    val fileSubtitle = if (file != null) {
        "${file.cleanTitle} · ${file.formattedMetaLine}"
    } else {
        "Built-in studio sample ready"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable Task Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Circular < + Tool Title + Friendly Subtitle
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TactileCircleIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack,
                            size = 44.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MeldColors.InkBlack
                        )
                    }
                    Text(
                        text = fileSubtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = MeldColors.TextSecondary
                    )
                }
            }

            // Contextual Tactile Card per Tool
            item {
                when (tool) {
                    MeldToolId.COMPRESS, MeldToolId.COMPRESS_PDF -> {
                        CompressTaskControls(
                            sourceBytes = file?.sizeBytes ?: (72L * 1024L),
                            targetKb = options.targetKb,
                            onUpdateTargetKb = onUpdateTargetKb
                        )
                    }

                    MeldToolId.HANDOUT -> {
                        HandoutTaskControls(
                            pagesPerSheet = options.handoutPagesPerSheet,
                            onUpdateHandoutMode = onUpdateHandoutMode
                        )
                    }

                    MeldToolId.PDF_EXTRACT -> {
                        SplitPdfTaskControls(
                            totalPages = file?.pageCount ?: 4,
                            startPage = options.pageRangeStart,
                            endPage = options.pageRangeEnd,
                            onUpdateRange = onUpdatePageRange
                        )
                    }

                    MeldToolId.LOCK_STAMP -> {
                        StampTaskControls(
                            stampText = options.stampText,
                            onUpdateStampText = onUpdateStampText
                        )
                    }

                    MeldToolId.RESIZE_IMAGE -> {
                        ResizeTaskControls(
                            percent = options.resizePercent,
                            addFrame = options.addStudioFrame,
                            onUpdatePercent = onUpdateResizePercent,
                            onToggleFrame = onToggleStudioFrame
                        )
                    }

                    MeldToolId.REMOVE_BG -> {
                        RemoveBgTaskControls(
                            tolerance = options.chromaTolerance,
                            onUpdateTolerance = onUpdateChromaTolerance
                        )
                    }

                    MeldToolId.SCAN_QR -> {
                        QrTaskControls(
                            payload = options.qrPayloadText,
                            onUpdatePayload = onUpdateQrPayload
                        )
                    }

                    else -> {
                        GenericConversionTaskCard(
                            tool = tool,
                            file = file
                        )
                    }
                }
            }

            // Privacy Guarantee Line
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MeldColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stays on your phone. Nothing is uploaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeldColors.TextSecondary
                    )
                }
            }
        }

        // Fixed Bottom Tactile Coral Action Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeldColors.CreamCanvas)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !uiState.isProcessing) { onRunTool() },
                shape = CircleShape,
                color = MeldColors.CoralOrange,
                border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.4.dp,
                            color = MeldColors.InkBlack
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Working on it...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.InkBlack
                        )
                    } else {
                        Icon(
                            imageVector = iconForTool(tool),
                            contentDescription = null,
                            tint = MeldColors.InkBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = tool.ctaLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MeldColors.InkBlack
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressTaskControls(
    sourceBytes: Long,
    targetKb: Int,
    onUpdateTargetKb: (Int) -> Unit
) {
    val sourceKb = max(72, (sourceBytes / 1024.0).toInt())
    val maxSliderKb = max(120, sourceKb.coerceAtMost(800))
    var showCustomInput by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Signature Butter Yellow Card with Tick Ruler
        TactileCard(
            backgroundColor = MeldColors.ButterYellow,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Text(
                    text = "AIM FOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeldColors.InkBlack.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$targetKb",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeldColors.InkBlack,
                        lineHeight = 56.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "KB",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeldColors.InkBlack,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                TactileRulerSlider(
                    value = targetKb.coerceIn(10, maxSliderKb),
                    minValue = 10,
                    maxValue = maxSliderKb,
                    onValueChange = {
                        showCustomInput = false
                        onUpdateTargetKb(it)
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10 KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeldColors.InkBlack
                    )
                    Text(
                        text = SelectedFileInfo.formatByteCount(sourceBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MeldColors.InkBlack
                    )
                }
            }
        }

        // Now vs After Comparison Pill Bars
        BeforeAfterComparisonCard(
            nowLabel = SelectedFileInfo.formatByteCount(sourceBytes),
            afterLabel = "$targetKb KB",
            afterRatio = (targetKb.toFloat() / max(1, sourceKb).toFloat())
        )

        // Presets Row matching Reference Page 2
        val presets = listOf(25, 40, 60)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { preset ->
                TactileChipPill(
                    label = "$preset KB",
                    selected = !showCustomInput && targetKb == preset,
                    onClick = {
                        showCustomInput = false
                        onUpdateTargetKb(preset)
                    }
                )
            }
            TactileChipPill(
                label = "Custom",
                selected = showCustomInput || !presets.contains(targetKb),
                onClick = { showCustomInput = !showCustomInput }
            )
        }

        AnimatedVisibility(visible = showCustomInput) {
            OutlinedTextField(
                value = targetKb.toString(),
                onValueChange = { txt ->
                    txt.toIntOrNull()?.let { v -> onUpdateTargetKb(v.coerceIn(10, 2000)) }
                },
                label = { Text("Custom size limit (KB)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MeldColors.PureWhite,
                    unfocusedContainerColor = MeldColors.PureWhite,
                    focusedBorderColor = MeldColors.InkBlack,
                    unfocusedBorderColor = MeldColors.InkBlack
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HandoutTaskControls(
    pagesPerSheet: Int,
    onUpdateHandoutMode: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = MeldColors.MintSage,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "STUDY LAYOUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeldColors.InkBlack.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (pagesPerSheet) {
                        2 -> "2 slides + generous note lines"
                        3 -> "3 slides + ruled lecture notes"
                        else -> "4 slides compact 2x2 grid"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MeldColors.InkBlack
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Mini Sheet Paper Visual Diagram
                TactileCard(
                    backgroundColor = MeldColors.PureWhite,
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val w = size.width
                        val h = size.height
                        if (pagesPerSheet <= 3) {
                            val rows = pagesPerSheet
                            val boxH = (h - (rows - 1) * 8.dp.toPx()) / rows
                            val boxW = w * 0.42f
                            for (r in 0 until rows) {
                                val y = r * (boxH + 8.dp.toPx())
                                drawRoundRect(
                                    color = MeldColors.WarmSurface,
                                    topLeft = Offset(0f, y),
                                    size = Size(boxW, boxH),
                                    cornerRadius = CornerRadius(6.dp.toPx())
                                )
                                drawRoundRect(
                                    color = MeldColors.InkBlack,
                                    topLeft = Offset(0f, y),
                                    size = Size(boxW, boxH),
                                    cornerRadius = CornerRadius(6.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                                )
                                // Ruled note lines
                                val lineStart = boxW + 14.dp.toPx()
                                for (l in 1..3) {
                                    val ly = y + (boxH * l / 4f)
                                    drawLine(
                                        color = MeldColors.TrackGray,
                                        start = Offset(lineStart, ly),
                                        end = Offset(w, ly),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                            }
                        } else {
                            val cellW = (w - 10.dp.toPx()) / 2f
                            val cellH = (h - 10.dp.toPx()) / 2f
                            for (r in 0..1) {
                                for (c in 0..1) {
                                    val x = c * (cellW + 10.dp.toPx())
                                    val y = r * (cellH + 10.dp.toPx())
                                    drawRoundRect(
                                        color = MeldColors.WarmSurface,
                                        topLeft = Offset(x, y),
                                        size = Size(cellW, cellH),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = MeldColors.InkBlack,
                                        topLeft = Offset(x, y),
                                        size = Size(cellW, cellH),
                                        cornerRadius = CornerRadius(6.dp.toPx()),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(2 to "2-up + notes", 3 to "3-up (Mac style)", 4 to "4-up grid").forEach { (mode, label) ->
                TactileChipPill(
                    label = label,
                    selected = pagesPerSheet == mode,
                    onClick = { onUpdateHandoutMode(mode) }
                )
            }
        }
    }
}

@Composable
private fun SplitPdfTaskControls(
    totalPages: Int,
    startPage: Int,
    endPage: Int,
    onUpdateRange: (Int, Int) -> Unit
) {
    val safeTotal = max(2, totalPages)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = MeldColors.PeriwinkleBlue,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "EXTRACT RANGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeldColors.InkBlack.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pages $startPage to $endPage",
                    style = MaterialTheme.typography.displayMedium,
                    color = MeldColors.InkBlack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Out of $safeTotal pages in this document",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.InkBlack.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageStepperCard(
                title = "From page",
                value = startPage,
                onMinus = { onUpdateRange(max(1, startPage - 1), endPage) },
                onPlus = { onUpdateRange(minOf(endPage, startPage + 1), endPage) },
                modifier = Modifier.weight(1f)
            )
            PageStepperCard(
                title = "To page",
                value = endPage,
                onMinus = { onUpdateRange(startPage, max(startPage, endPage - 1)) },
                onPlus = { onUpdateRange(startPage, minOf(safeTotal, endPage + 1)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PageStepperCard(
    title: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = 20.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MeldColors.TextSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TactileCircleIconButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    onClick = onMinus,
                    size = 34.dp
                )
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MeldColors.InkBlack
                )
                TactileCircleIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Increase",
                    onClick = onPlus,
                    size = 34.dp
                )
            }
        }
    }
}

@Composable
private fun StampTaskControls(
    stampText: String,
    onUpdateStampText: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TactileCard(
            backgroundColor = MeldColors.PeachCream,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                TactileCard(
                    backgroundColor = MeldColors.PureWhite,
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stampText.ifBlank { "WATERMARK" }.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MeldColors.CoralOrange.copy(alpha = 0.72f),
                            modifier = Modifier.rotate(-14f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = stampText,
            onValueChange = onUpdateStampText,
            label = { Text("Diagonal Watermark Text") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MeldColors.PureWhite,
                unfocusedContainerColor = MeldColors.PureWhite,
                focusedBorderColor = MeldColors.InkBlack,
                unfocusedBorderColor = MeldColors.InkBlack
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("CONFIDENTIAL", "MELD VERIFIED", "DRAFT COPY", "INTERNAL ONLY").forEach { preset ->
                TactileChipPill(
                    label = preset,
                    selected = stampText == preset,
                    onClick = { onUpdateStampText(preset) }
                )
            }
        }
    }
}

@Composable
private fun ResizeTaskControls(
    percent: Int,
    addFrame: Boolean,
    onUpdatePercent: (Int) -> Unit,
    onToggleFrame: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = MeldColors.ButterYellow,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "SCALE OUTPUT TO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeldColors.InkBlack.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$percent%",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MeldColors.InkBlack
                )
                Spacer(modifier = Modifier.height(10.dp))
                TactileRulerSlider(
                    value = percent.coerceIn(25, 200),
                    minValue = 25,
                    maxValue = 200,
                    onValueChange = onUpdatePercent
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("25%", style = MaterialTheme.typography.labelMedium, color = MeldColors.InkBlack)
                    Text("200%", style = MaterialTheme.typography.labelMedium, color = MeldColors.InkBlack)
                }
            }
        }

        TactileCard(
            backgroundColor = MeldColors.PureWhite,
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Dark Studio Border",
                        style = MaterialTheme.typography.titleMedium,
                        color = MeldColors.InkBlack
                    )
                    Text(
                        text = "Frames your image neatly with dimension stamp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeldColors.TextSecondary
                    )
                }
                Switch(
                    checked = addFrame,
                    onCheckedChange = onToggleFrame,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MeldColors.PureWhite,
                        checkedTrackColor = MeldColors.CoralOrange
                    )
                )
            }
        }
    }
}

@Composable
private fun RemoveBgTaskControls(
    tolerance: Int,
    onUpdateTolerance: (Int) -> Unit
) {
    TactileCard(
        backgroundColor = MeldColors.ButterYellow,
        cornerRadius = 26.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "EDGE FEATHERING",
                style = MaterialTheme.typography.labelSmall,
                color = MeldColors.InkBlack.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$tolerance",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MeldColors.InkBlack
            )
            Text(
                text = "Higher values strip slightly more backdrop halo into alpha",
                style = MaterialTheme.typography.bodyMedium,
                color = MeldColors.InkBlack.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            TactileRulerSlider(
                value = tolerance.coerceIn(15, 95),
                minValue = 15,
                maxValue = 95,
                onValueChange = onUpdateTolerance
            )
        }
    }
}

@Composable
private fun QrTaskControls(
    payload: String,
    onUpdatePayload: (String) -> Unit
) {
    val matrix = remember(payload) { ImageEngine.buildQrMatrix21(payload.ifEmpty { "meld.app" }) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = MeldColors.SoftLavender,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TactileCard(
                    backgroundColor = MeldColors.PureWhite,
                    cornerRadius = 18.dp,
                    modifier = Modifier.size(165.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        val cell = size.width / 21f
                        for (r in 0 until 21) {
                            for (c in 0 until 21) {
                                if (matrix[r][c]) {
                                    val isFinderCenter =
                                        (r in 2..4 && c in 2..4) ||
                                            (r in 2..4 && c in 16..18) ||
                                            (r in 16..18 && c in 2..4)
                                    drawRect(
                                        color = if (isFinderCenter) MeldColors.CoralOrange else MeldColors.InkBlack,
                                        topLeft = Offset(c * cell, r * cell),
                                        size = Size(cell * 0.92f, cell * 0.92f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = payload,
            onValueChange = onUpdatePayload,
            label = { Text("Link or text to encode") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MeldColors.PureWhite,
                unfocusedContainerColor = MeldColors.PureWhite,
                focusedBorderColor = MeldColors.InkBlack,
                unfocusedBorderColor = MeldColors.InkBlack
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GenericConversionTaskCard(
    tool: MeldToolId,
    file: SelectedFileInfo?
) {
    val sourceExt = (file?.extension?.takeIf { it.isNotBlank() } ?: "FILE").uppercase()
    val cardColor = when (tool.outputExtension) {
        "pdf" -> MeldColors.PeriwinkleBlue
        "png", "jpg", "webp" -> MeldColors.ButterYellow
        "docx", "md", "html", "txt" -> MeldColors.MintSage
        else -> MeldColors.SoftLavender
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = cardColor,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
                    ) {
                        Text(
                            text = sourceExt,
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.InkBlack,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MeldColors.InkBlack
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MeldColors.InkBlack,
                        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
                    ) {
                        Text(
                            text = tool.outputExtension.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.PureWhite,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MeldColors.InkBlack
                )
                Text(
                    text = "Everything runs offline right here on your device with crisp print & studio fidelity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.InkBlack.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun MeldResultScreen(
    result: ConversionResult,
    bannerMessage: String?,
    onClose: () -> Unit,
    onContinueTool: (MeldToolId) -> Unit,
    onSaveToDownloads: (File) -> Unit,
    onShare: (File) -> Unit
) {
    val followUps = remember(result.tool) { result.tool.followUpTools() }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Circular X + Result
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TactileCircleIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onClose,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MeldColors.InkBlack
                    )
                }
            }

            // Soft Lavender Showcase Card with Overlapping Orange Checkmark Badge
            item {
                ResultShowcaseCard(
                    result = result,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Celebratory Editorial Headline + Summary
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = result.tool.resultHeadline,
                        style = MaterialTheme.typography.displayMedium,
                        color = MeldColors.InkBlack
                    )
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = MeldColors.TextSecondary
                    )
                }
            }

            // Crisp White Output Filename Strip
            item {
                TactileCard(
                    backgroundColor = MeldColors.PureWhite,
                    cornerRadius = 18.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.outputFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.InkBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = SelectedFileInfo.formatByteCount(result.outputSizeBytes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeldColors.TextSecondary
                        )
                    }
                }
            }

            if (bannerMessage != null) {
                item {
                    TactileCard(
                        backgroundColor = MeldColors.MintSage,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bannerMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MeldColors.InkBlack,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Keep Going Chaining Pills
            if (followUps.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Keep going",
                            style = MaterialTheme.typography.titleMedium,
                            color = MeldColors.TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            followUps.forEach { nextTool ->
                                TactileChipPill(
                                    label = nextTool.title,
                                    selected = false,
                                    onClick = { onContinueTool(nextTool) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fixed Dual Bottom Buttons: Save (Coral) + Share (White)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeldColors.CreamCanvas)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .clickable { onSaveToDownloads(result.outputFile) },
                shape = CircleShape,
                color = MeldColors.CoralOrange,
                border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MeldColors.InkBlack,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeldColors.InkBlack
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .clickable { onShare(result.outputFile) },
                shape = CircleShape,
                color = MeldColors.PureWhite,
                border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = null,
                        tint = MeldColors.InkBlack,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeldColors.InkBlack
                    )
                }
            }
        }
    }
}
