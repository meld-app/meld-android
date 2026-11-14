package app.meld.android.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.meld.android.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meld.android.model.ConversionResult
import app.meld.android.model.MeldToolId
import app.meld.android.model.RedactionRect
import app.meld.android.model.SelectedFileInfo
import app.meld.android.ui.MeldAdaptiveLayout
import app.meld.android.ui.MeldUiState
import app.meld.android.ui.components.AnimatedHandoutDiagram
import app.meld.android.ui.components.BeforeAfterComparisonCard
import app.meld.android.ui.components.CompressPlatesStage
import app.meld.android.ui.components.CompressionRatioPill
import app.meld.android.ui.components.MeldAdaptiveChipRow
import app.meld.android.ui.components.QrMatrixStage
import app.meld.android.ui.components.RedactSweepStage
import app.meld.android.ui.components.RemoveBgCurtainStage
import app.meld.android.ui.components.ResizeFrameStage
import app.meld.android.ui.components.ResultShowcaseCard
import app.meld.android.ui.components.SpecializedToolShowcaseCard
import app.meld.android.ui.components.SplitPageSpreadStage
import app.meld.android.ui.components.StampSlamPreview
import app.meld.android.ui.components.TactileCard
import app.meld.android.ui.components.TactileChipPill
import app.meld.android.ui.components.TactileCircleIconButton
import app.meld.android.ui.components.TactilePillButton
import app.meld.android.ui.components.TactileRulerSlider
import app.meld.android.ui.components.renderPdfPagePreview
import app.meld.android.ui.theme.MeldDimens
import app.meld.android.ui.theme.MeldMotion
import app.meld.android.ui.theme.meldColors
import app.meld.android.ui.theme.meldSharedToolBounds
import app.meld.android.ui.theme.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun MeldTaskScreen(
    tool: MeldToolId,
    uiState: MeldUiState,
    onBack: () -> Unit,
    onRunTool: () -> Unit,
    onOpenChangeFile: () -> Unit = {},
    onUpdateTargetKb: (Int) -> Unit,
    onUpdateResizePercent: (Int) -> Unit,
    onToggleStudioFrame: (Boolean) -> Unit,
    onUpdateStampText: (String) -> Unit,
    onUpdateHandoutMode: (Int) -> Unit,
    onUpdateQrPayload: (String) -> Unit,
    onUpdatePageRange: (Int, Int) -> Unit,
    onUpdateChromaTolerance: (Int) -> Unit,
    onUpdateRedactionZones: (List<RedactionRect>) -> Unit = {}
) {
    val colors = meldColors
    val file = uiState.selectedFile
    val options = uiState.options
    val usesSampleFallback = file != null && tool != MeldToolId.SCAN_QR && !tool.acceptsExtension(file.extension)
    val fileSubtitle = when {
        tool == MeldToolId.SCAN_QR -> stringResource(R.string.qr_task_subtitle)
        file != null -> stringResource(
            R.string.file_info_inline,
            file.cleanTitle,
            file.localizedMetaLine()
        )
        else -> stringResource(R.string.built_in_sample_ready)
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
                            contentDescription = stringResource(R.string.cd_back),
                            onClick = onBack,
                            size = 44.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(tool.titleRes),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary
                        )
                    }
                    Text(
                        text = fileSubtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = colors.textSecondary
                    )
                }
            }

            // Contextual Tactile Card per Tool (shared-bounds morph target from Home/Tools grid tile)
            item {
                Box(modifier = Modifier.meldSharedToolBounds("tool-${tool.name}")) {
                    when (tool) {
                        MeldToolId.COMPRESS, MeldToolId.COMPRESS_PDF -> {
                            CompressTaskControls(
                                sourceBytes = file?.sizeBytes ?: (72L * 1024L),
                                targetKb = options.targetKb,
                                onUpdateTargetKb = onUpdateTargetKb
                            )
                        }

                        MeldToolId.HANDOUT, MeldToolId.PPTX_HANDOUT -> {
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

                        MeldToolId.REDACT -> {
                            RedactTaskControls(
                                file = file,
                                selectedZones = options.redactionZones,
                                onUpdateZones = onUpdateRedactionZones
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
            }

            uiState.bannerMessage?.let { msg ->
                item {
                    if (uiState.isBannerError) {
                        ErrorShakeBanner(message = msg)
                    } else {
                        InfoStatusBanner(message = msg)
                    }
                }
            }

            if (usesSampleFallback) {
                item {
                    TactileCard(
                        backgroundColor = colors.warmSurface,
                        cornerRadius = MeldDimens.SubCardCorner,
                        onClick = if (uiState.isProcessing) null else onOpenChangeFile,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.incompatible_file_notice,
                                    file.extension.uppercase()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.change_short),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    textDecoration = TextDecoration.Underline
                                ),
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
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
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.privacy_banner_task),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Fixed Bottom Tactile Coral Action Button with Hard Shadow & Press Bounce
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TactilePillButton(
                text = stringResource(tool.ctaLabelRes),
                icon = iconForTool(tool),
                backgroundColor = colors.coral,
                enabled = !uiState.isProcessing && (tool != MeldToolId.REDACT || options.redactionZones.isNotEmpty()),
                onClick = onRunTool,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(),
                customContent = if (uiState.isProcessing) {
                    {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.4.dp,
                                color = colors.inkOnAccent
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.working_on_it),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkOnAccent
                            )
                        }
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompressTaskControls(
    sourceBytes: Long,
    targetKb: Int,
    onUpdateTargetKb: (Int) -> Unit
) {
    val sourceKb = max(40, (sourceBytes / 1024.0).toInt())
    val maxSliderKb = max(30, minOf(sourceKb, 2000))
    var showCustomInput by remember { mutableStateOf(false) }
    var customText by remember(showCustomInput) { mutableStateOf(targetKb.toString()) }
    val displayKb by animateIntAsState(
        targetValue = targetKb,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "displayTargetKb"
    )
    val largeFont = MeldAdaptiveLayout.isLargeFontScale(LocalDensity.current.fontScale)

    val colors = meldColors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Signature Butter Yellow Card with Tick Ruler
        TactileCard(
            backgroundColor = colors.butter,
            cornerRadius = MeldDimens.CardCorner,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                CompressPlatesStage(
                    sourceBytes = sourceBytes,
                    targetKb = targetKb
                )
                Spacer(modifier = Modifier.height(14.dp))
                val aimForBlock: @Composable () -> Unit = {
                    Column {
                        Text(
                            text = stringResource(R.string.aim_for_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkOnAccent.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$displayKb",
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                fontSize = 54.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkOnAccent,
                                lineHeight = 56.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.unit_kb),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkOnAccent,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }
                if (largeFont) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        aimForBlock()
                        CompressionRatioPill(
                            ratio = targetKb.toFloat() / max(1, sourceKb).toFloat()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        aimForBlock()
                        CompressionRatioPill(
                            ratio = targetKb.toFloat() / max(1, sourceKb).toFloat()
                        )
                    }
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
                        text = stringResource(R.string.kb_value_format, 10),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkOnAccent
                    )
                    Text(
                        text = stringResource(R.string.kb_value_format, maxSliderKb),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkOnAccent
                    )
                }
            }
        }

        // Now vs After Comparison Pill Bars
        BeforeAfterComparisonCard(
            nowLabel = SelectedFileInfo.formatByteCount(sourceBytes),
            afterLabel = stringResource(R.string.kb_value_format, targetKb),
            afterRatio = (targetKb.toFloat() / max(1, sourceKb).toFloat())
        )

        // Presets Row matching Reference Page 2
        val presets = listOf(25, 40, 60)
        MeldAdaptiveChipRow(horizontalSpacing = 10.dp) {
            presets.forEach { preset ->
                TactileChipPill(
                    label = stringResource(R.string.kb_value_format, preset),
                    selected = !showCustomInput && targetKb == preset,
                    onClick = {
                        showCustomInput = false
                        customText = preset.toString()
                        onUpdateTargetKb(preset)
                    }
                )
            }
            TactileChipPill(
                label = stringResource(R.string.custom_preset_label),
                selected = showCustomInput || !presets.contains(targetKb),
                onClick = {
                    showCustomInput = !showCustomInput
                    if (showCustomInput) customText = targetKb.toString()
                }
            )
        }

        AnimatedVisibility(visible = showCustomInput) {
            OutlinedTextField(
                value = customText,
                onValueChange = { txt ->
                    val digitsOnly = txt.filter { it.isDigit() }.take(4)
                    customText = digitsOnly
                    digitsOnly.toIntOrNull()?.let { v ->
                        if (v >= 10) {
                            onUpdateTargetKb(v.coerceIn(10, maxSliderKb))
                        }
                    }
                },
                label = { Text(stringResource(R.string.custom_kb_input_label, maxSliderKb)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.border,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.textPrimary,
                    unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.coral
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
    val colors = meldColors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = colors.mint,
            cornerRadius = MeldDimens.CardCorner,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = stringResource(R.string.study_layout_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkOnAccent.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (pagesPerSheet) {
                        2 -> stringResource(R.string.study_layout_2up_desc)
                        3 -> stringResource(R.string.study_layout_3up_desc)
                        else -> stringResource(R.string.study_layout_4up_desc)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.inkOnAccent
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Morphing Mini Sheet Paper Visual Diagram
                AnimatedHandoutDiagram(pagesPerSheet = pagesPerSheet)
            }
        }

        MeldAdaptiveChipRow(horizontalSpacing = 10.dp) {
            listOf(
                2 to R.string.handout_2_up,
                3 to R.string.handout_3_up_long,
                4 to R.string.handout_4_up
            ).forEach { (mode, labelRes) ->
                TactileChipPill(
                    label = stringResource(labelRes),
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
    val safeTotal = max(1, totalPages)
    val largeFont = MeldAdaptiveLayout.isLargeFontScale(LocalDensity.current.fontScale)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SplitPageSpreadStage(
            totalPages = safeTotal,
            startPage = startPage,
            endPage = endPage,
            onSelectPage = { tapped ->
                when {
                    tapped < startPage -> onUpdateRange(tapped, endPage)
                    tapped > endPage -> onUpdateRange(startPage, tapped)
                    tapped == startPage && startPage < endPage -> onUpdateRange(tapped + 1, endPage)
                    tapped == endPage && endPage > startPage -> onUpdateRange(startPage, tapped - 1)
                    else -> onUpdateRange(tapped, tapped)
                }
            }
        )

        if (largeFont) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PageStepperCard(
                    title = stringResource(R.string.from_page_label),
                    value = startPage,
                    onMinus = { onUpdateRange(max(1, startPage - 1), endPage) },
                    onPlus = { onUpdateRange(minOf(endPage, startPage + 1), endPage) },
                    modifier = Modifier.fillMaxWidth()
                )
                PageStepperCard(
                    title = stringResource(R.string.to_page_label),
                    value = endPage,
                    onMinus = { onUpdateRange(startPage, max(startPage, endPage - 1)) },
                    onPlus = { onUpdateRange(startPage, minOf(safeTotal, endPage + 1)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PageStepperCard(
                    title = stringResource(R.string.from_page_label),
                    value = startPage,
                    onMinus = { onUpdateRange(max(1, startPage - 1), endPage) },
                    onPlus = { onUpdateRange(minOf(endPage, startPage + 1), endPage) },
                    modifier = Modifier.weight(1f)
                )
                PageStepperCard(
                    title = stringResource(R.string.to_page_label),
                    value = endPage,
                    onMinus = { onUpdateRange(startPage, max(startPage, endPage - 1)) },
                    onPlus = { onUpdateRange(startPage, minOf(safeTotal, endPage + 1)) },
                    modifier = Modifier.weight(1f)
                )
            }
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
    val colors = meldColors
    TactileCard(
        backgroundColor = colors.surface,
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
                color = colors.textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TactileCircleIconButton(
                    icon = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.cd_decrease),
                    onClick = onMinus,
                    size = 34.dp
                )
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.textPrimary
                )
                TactileCircleIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_increase),
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
    val colors = meldColors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StampSlamPreview(stampText = stampText)

        OutlinedTextField(
            value = stampText,
            onValueChange = onUpdateStampText,
            label = { Text(stringResource(R.string.watermark_input_label)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.border,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedLabelColor = colors.textPrimary,
                unfocusedLabelColor = colors.textSecondary,
                cursorColor = colors.coral
            ),
            modifier = Modifier.fillMaxWidth()
        )

        MeldAdaptiveChipRow {
            listOf(
                R.string.watermark_preset_confidential,
                R.string.watermark_preset_verified,
                R.string.watermark_preset_draft,
                R.string.watermark_preset_internal
            ).forEach { presetRes ->
                val preset = stringResource(presetRes)
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
    val colors = meldColors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TactileCard(
            backgroundColor = colors.butter,
            cornerRadius = MeldDimens.CardCorner,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                ResizeFrameStage(percent = percent, addFrame = addFrame)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.scale_output_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkOnAccent.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.percent_value, percent),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.inkOnAccent
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
                    Text(
                        text = stringResource(R.string.percent_value, 25),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkOnAccent
                    )
                    Text(
                        text = stringResource(R.string.percent_value, 200),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkOnAccent
                    )
                }
            }
        }

        TactileCard(
            backgroundColor = colors.surface,
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.add_studio_border_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.add_studio_border_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = addFrame,
                    onCheckedChange = onToggleFrame,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.inkOnAccent,
                        checkedTrackColor = colors.coral,
                        checkedBorderColor = colors.border,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.warmSurface,
                        uncheckedBorderColor = colors.border
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
    val removeColors = meldColors
    TactileCard(
        backgroundColor = removeColors.butter,
        cornerRadius = MeldDimens.CardCorner,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            RemoveBgCurtainStage(tolerance = tolerance)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.edge_feathering_label),
                style = MaterialTheme.typography.labelSmall,
                color = removeColors.inkOnAccent.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$tolerance",
                style = TextStyle(fontFeatureSettings = "tnum"),
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = removeColors.inkOnAccent
            )
            Text(
                text = stringResource(R.string.edge_feathering_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = removeColors.inkOnAccent.copy(alpha = 0.85f)
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
    val colors = meldColors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QrMatrixStage(payload = payload)

        OutlinedTextField(
            value = payload,
            onValueChange = onUpdatePayload,
            label = { Text(stringResource(R.string.qr_payload_label)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.border,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedLabelColor = colors.textPrimary,
                unfocusedLabelColor = colors.textSecondary,
                cursorColor = colors.coral
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class RedactionPreset(
    val rect: RedactionRect,
    @StringRes val labelRes: Int
)

private fun RedactionRect.matchesBounds(other: RedactionRect): Boolean =
    left == other.left && top == other.top && right == other.right && bottom == other.bottom

@Composable
private fun RedactTaskControls(
    file: SelectedFileInfo?,
    selectedZones: List<RedactionRect>,
    onUpdateZones: (List<RedactionRect>) -> Unit
) {
    val presetZones = remember {
        listOf(
            RedactionPreset(RedactionRect(0.08f, 0.08f, 0.92f, 0.17f), R.string.redact_zone_header_id),
            RedactionPreset(RedactionRect(0.10f, 0.24f, 0.90f, 0.34f), R.string.redact_zone_upper_strip),
            RedactionPreset(RedactionRect(0.10f, 0.42f, 0.90f, 0.56f), R.string.redact_zone_mid_block),
            RedactionPreset(RedactionRect(0.10f, 0.76f, 0.90f, 0.88f), R.string.redact_zone_footer_sig),
            RedactionPreset(RedactionRect(0.04f, 0.04f, 0.96f, 0.96f), R.string.redact_zone_full_page)
        )
    }

    val pdfPreview by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        file?.file?.absolutePath,
        file?.sizeBytes
    ) {
        value = withContext(Dispatchers.IO) {
            if (file != null && file.extension.equals("pdf", ignoreCase = true) && file.file.isFile) {
                renderPdfPagePreview(file.file, maxDim = 640)
            } else null
        }
    }

    DisposableEffect(pdfPreview) {
        val bmp = pdfPreview
        onDispose {
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        }
    }

    val colors = meldColors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RedactSweepStage()

        if (pdfPreview != null) {
            TactileCard(
                backgroundColor = colors.surface,
                cornerRadius = MeldDimens.CardCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.redaction_targets_header, selectedZones.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(595f / 842f)
                            .background(colors.paperFill)
                    ) {
                        pdfPreview?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_redact_preview),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (zone in selectedZones) {
                                val l = size.width * zone.left
                                val t = size.height * zone.top
                                val w = size.width * (zone.right - zone.left)
                                val h = size.height * (zone.bottom - zone.top)
                                drawRect(
                                    color = colors.inkOnAccent.copy(alpha = 0.88f),
                                    topLeft = Offset(l, t),
                                    size = Size(w, h)
                                )
                                drawRect(
                                    color = colors.coral,
                                    topLeft = Offset(l, t),
                                    size = Size(w, h),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }

        MeldAdaptiveChipRow(horizontalSpacing = 10.dp) {
            presetZones.forEach { preset ->
                val isSelected = selectedZones.any { it.matchesBounds(preset.rect) }
                TactileChipPill(
                    label = stringResource(preset.labelRes),
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedZones.filterNot { it.matchesBounds(preset.rect) }
                        } else {
                            selectedZones + preset.rect
                        }
                        onUpdateZones(updated)
                    }
                )
            }
        }
    }
}

@Composable
private fun GenericConversionTaskCard(
    tool: MeldToolId,
    file: SelectedFileInfo?
) {
    SpecializedToolShowcaseCard(tool = tool, file = file)
}

@Composable
private fun InfoStatusBanner(message: String) {
    val colors = meldColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MeldDimens.SubCardCorner),
        color = colors.mint,
        border = BorderStroke(2.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                color = colors.inkOnAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.paperFill,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkOnAccent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ErrorShakeBanner(message: String) {
    val colors = meldColors
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val shakeOffset = remember(message) { Animatable(0f) }
    val badgePop = remember(message) { Animatable(if (reduceMotion) 1f else 0.90f) }

    LaunchedEffect(message, reduceMotion) {
        if (reduceMotion) {
            shakeOffset.snapTo(0f)
            badgePop.snapTo(1f)
            return@LaunchedEffect
        }
        badgePop.animateTo(1f, tween(durationMillis = MeldMotion.UI_MS, easing = MeldMotion.EaseOvershoot))
        val peaks = listOf(-7f, 7f, -5f, 5f, -3f, 0f)
        for (p in peaks) {
            shakeOffset.animateTo(p, tween(durationMillis = 40, easing = MeldMotion.EaseOut))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = with(density) { shakeOffset.value.dp.toPx() }
            },
        shape = RoundedCornerShape(MeldDimens.SubCardCorner),
        color = colors.errorSurface,
        border = BorderStroke(2.dp, colors.errorBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = badgePop.value
                        scaleY = badgePop.value
                    },
                shape = CircleShape,
                color = colors.error
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onError
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
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
    val colors = meldColors
    val largeFont = MeldAdaptiveLayout.isLargeFontScale(LocalDensity.current.fontScale)
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
                        contentDescription = stringResource(R.string.cd_close),
                        onClick = onClose,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.result_heading),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textPrimary
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
                        text = stringResource(result.tool.resultHeadlineRes),
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = colors.textSecondary
                    )
                }
            }

            // Crisp White Output Filename Strip
            item {
                TactileCard(
                    backgroundColor = colors.surface,
                    cornerRadius = 18.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (largeFont) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = result.outputFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = SelectedFileInfo.formatByteCount(result.outputSizeBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    } else {
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
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = SelectedFileInfo.formatByteCount(result.outputSizeBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (bannerMessage != null) {
                item {
                    TactileCard(
                        backgroundColor = colors.mint,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bannerMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkOnAccent,
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
                            text = stringResource(R.string.keep_going_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textSecondary
                        )
                        MeldAdaptiveChipRow(horizontalSpacing = 10.dp) {
                            followUps.forEach { nextTool ->
                                TactileChipPill(
                                    label = stringResource(nextTool.titleRes),
                                    selected = false,
                                    onClick = { onContinueTool(nextTool) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fixed Dual Bottom Tactile Buttons: Save (Coral) + Share (White/Surface)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TactilePillButton(
                text = stringResource(R.string.action_save),
                icon = Icons.Default.Download,
                backgroundColor = colors.coral,
                onClick = { onSaveToDownloads(result.outputFile) },
                modifier = Modifier.weight(1f)
            )

            TactilePillButton(
                text = stringResource(R.string.action_share),
                icon = Icons.Default.IosShare,
                backgroundColor = colors.surface,
                contentColor = colors.textPrimary,
                onClick = { onShare(result.outputFile) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
