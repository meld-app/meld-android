package app.meld.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.meld.android.BuildConfig
import app.meld.android.R
import app.meld.android.model.ConversionResult
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.model.SelectedFileInfo
import app.meld.android.ui.DockTab
import app.meld.android.ui.MeldUiState
import app.meld.android.ui.SamplePreset
import app.meld.android.ui.components.MeldOpeningDriver
import app.meld.android.ui.components.TactileCard
import app.meld.android.ui.components.TactileChipPill
import app.meld.android.ui.components.TactileCircleIconButton
import app.meld.android.ui.components.WorkingDocumentIllustration
import app.meld.android.ui.theme.MeldDimens
import app.meld.android.ui.theme.MeldMotion
import app.meld.android.ui.theme.meldColors
import app.meld.android.ui.theme.meldSharedToolBounds
import app.meld.android.ui.theme.rememberReduceMotion
import java.io.File
import java.util.Calendar
import kotlin.math.hypot

@Composable
fun MeldStudioHomeScreen(
    uiState: MeldUiState,
    openingDriver: MeldOpeningDriver,
    onReplayIntro: () -> Unit,
    onOpenChangeFile: () -> Unit,
    onOpenTask: (MeldToolId) -> Unit,
    onSeeAllTools: () -> Unit,
    onOpenResult: (ConversionResult) -> Unit,
    onShareFile: (File) -> Unit
) {
    val colors = meldColors
    val density = LocalDensity.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning."
            hour < 17 -> "Good afternoon."
            else -> "Good evening."
        }
    }

    val ext = uiState.selectedFile?.extension?.lowercase() ?: "pdf"
    val quickTriple: Triple<MeldToolId, MeldToolId, MeldToolId> = remember(ext) {
        when (ext) {
            in setOf("png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif") ->
                Triple(MeldToolId.COMPRESS, MeldToolId.REMOVE_BG, MeldToolId.IMG_TO_PDF)
            in setOf("wav", "pcm", "mp3", "m4a", "aac", "mp4", "ogg") ->
                Triple(MeldToolId.VISUALIZE, MeldToolId.SPEED_2X, MeldToolId.PACK_ZIP)
            in setOf("csv", "tsv", "txt") ->
                Triple(MeldToolId.SHEET_TO_PDF, MeldToolId.SHEET_TO_MD, MeldToolId.TABLE_CONVERT)
            else ->
                Triple(MeldToolId.COMPRESS_PDF, MeldToolId.HANDOUT, MeldToolId.PDF_EXTRACT)
        }
    }

    val slide22Px = with(density) { 22.dp.toPx() }
    val slide26Px = with(density) { 26.dp.toPx() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Brand Row: M meld (tap to replay opening!) + Works offline pill
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            openingDriver.brandTarget = coords.boundsInRoot()
                        }
                        .graphicsLayer {
                            alpha = if (!openingDriver.showOverlay || openingDriver.dockToHome.value >= 0.96f) 1f else 0f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onReplayIntro() }
                        .padding(end = 6.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(11.dp),
                        color = colors.coral,
                        border = BorderStroke(2.dp, colors.border)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.meld_logo),
                            contentDescription = "meld",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "meld",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary
                    )
                }

                Surface(
                    modifier = Modifier.graphicsLayer {
                        alpha = if (!openingDriver.showOverlay) 1f else openingDriver.pillAlpha.value.coerceIn(0f, 1f)
                    },
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(1.8.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Works offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Human Greeting Header (staggered rh + rp from Opening.dc.html)
        item {
            val rhT = if (!openingDriver.showOverlay) 1f else openingDriver.rhAnim.value
            val rpT = if (!openingDriver.showOverlay) 1f else openingDriver.rpAnim.value
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.graphicsLayer {
                        alpha = rhT.coerceIn(0f, 1f)
                        translationY = (1f - rhT) * slide22Px
                    }
                )
                Text(
                    text = "What needs sorting today?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.graphicsLayer {
                        alpha = rpT.coerceIn(0f, 1f)
                        translationY = (1f - rpT) * slide22Px
                    }
                )
            }
        }

        // Hero Coral WORKING ON Card with Circular Clip Reveal expanding from sheet center!
        item {
            val file = uiState.selectedFile
            val clipFrac = if (!openingDriver.showOverlay) 1f else openingDriver.heroClip.value.coerceIn(0f, 1f)
            val heroRevealShape = GenericShape { size, _ ->
                if (clipFrac >= 0.99f) {
                    addRect(Rect(0f, 0f, size.width, size.height))
                } else if (clipFrac > 0.005f) {
                    val cx = with(density) { 56.dp.toPx() }
                    val cy = size.height / 2f
                    val maxRadius = hypot(size.width, size.height) * 1.1f
                    val radius = maxRadius * clipFrac
                    addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (clipFrac > 0.005f) 1f else 0f }
                    .clip(heroRevealShape)
            ) {
                TactileCard(
                    backgroundColor = colors.coral,
                    cornerRadius = 26.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { coords ->
                                    openingDriver.sheetTarget = coords.boundsInRoot()
                                }
                                .graphicsLayer {
                                    alpha = if (!openingDriver.showOverlay || openingDriver.dockToHome.value >= 0.97f) 1f else 0f
                                    rotationZ = -3f
                                }
                        ) {
                            WorkingDocumentIllustration(extension = file?.extension ?: "pdf")
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WORKING ON",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkOnAccent.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = file?.cleanTitle ?: "Lecture Deck",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.inkOnAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = file?.formattedMetaLine ?: "Tap Change file to pick one",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = colors.inkOnAccent.copy(alpha = 0.88f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onOpenChangeFile() },
                                shape = CircleShape,
                                color = colors.surface,
                                border = BorderStroke(MeldDimens.StrokeWidth, colors.inkOnAccent)
                            ) {
                                Text(
                                    text = "Change file",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Tools Section + All 31 Link (staggered qt, t1, t2, t3)
        item {
            val qtT = if (!openingDriver.showOverlay) 1f else openingDriver.qtAnim.value
            val t1T = if (!openingDriver.showOverlay) 1f else openingDriver.t1Anim.value
            val t2T = if (!openingDriver.showOverlay) 1f else openingDriver.t2Anim.value
            val t3T = if (!openingDriver.showOverlay) 1f else openingDriver.t3Anim.value

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = qtT.coerceIn(0f, 1f)
                            translationY = (1f - qtT) * slide22Px
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick tools",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "All ${MeldToolId.entries.size}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            textDecoration = TextDecoration.Underline
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSeeAllTools() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Asymmetric Bento Grid matching Reference Page 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(236.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Tall Yellow Card (`t1`)
                    val primaryTool = quickTriple.first
                    TactileCard(
                        backgroundColor = colors.butter,
                        cornerRadius = 24.dp,
                        onClick = { onOpenTask(primaryTool) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .meldSharedToolBounds("tool-${primaryTool.name}")
                            .graphicsLayer {
                                alpha = t1T.coerceIn(0f, 1f)
                                translationY = (1f - t1T) * slide26Px
                                val sc = lerp(0.92f, 1f, t1T)
                                scaleX = sc
                                scaleY = sc
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = colors.surface,
                                border = BorderStroke(1.8.dp, colors.inkOnAccent)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = iconForTool(primaryTool),
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = primaryTool.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.inkOnAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = primaryTool.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.inkOnAccent.copy(alpha = 0.85f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Right Column: 2 Stacked Pastel Cards (`t2` & `t3`)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val topRight = quickTriple.second
                        TactileCard(
                            backgroundColor = colors.mint,
                            cornerRadius = 22.dp,
                            onClick = { onOpenTask(topRight) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .meldSharedToolBounds("tool-${topRight.name}")
                                .graphicsLayer {
                                    alpha = t2T.coerceIn(0f, 1f)
                                    translationY = (1f - t2T) * slide26Px
                                    val sc = lerp(0.92f, 1f, t2T)
                                    scaleX = sc
                                    scaleY = sc
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = colors.surface,
                                    border = BorderStroke(1.8.dp, colors.inkOnAccent)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = iconForTool(topRight),
                                            contentDescription = null,
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = topRight.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.inkOnAccent
                                )
                            }
                        }

                        val bottomRight = quickTriple.third
                        TactileCard(
                            backgroundColor = colors.periwinkle,
                            cornerRadius = 22.dp,
                            onClick = { onOpenTask(bottomRight) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .meldSharedToolBounds("tool-${bottomRight.name}")
                                .graphicsLayer {
                                    alpha = t3T.coerceIn(0f, 1f)
                                    translationY = (1f - t3T) * slide26Px
                                    val sc = lerp(0.92f, 1f, t3T)
                                    scaleX = sc
                                    scaleY = sc
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = colors.surface,
                                    border = BorderStroke(1.8.dp, colors.inkOnAccent)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = iconForTool(bottomRight),
                                            contentDescription = null,
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = bottomRight.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.inkOnAccent
                                )
                            }
                        }
                    }
                }
            }
        }

        // More tools that also work on the current file (fills the lower Home area)
        item {
            val shown = setOf(quickTriple.first, quickTriple.second, quickTriple.third)
            val moreTools = remember(ext) {
                MeldToolId.entries.filter { it.acceptsExtension(ext) && it !in shown && it != MeldToolId.SCAN_QR }.take(4)
            }
            if (moreTools.isNotEmpty()) {
                val tints = listOf(
                    colors.lavender, colors.peach,
                    colors.periwinkle, colors.mint
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "More for this file",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary
                    )
                    moreTools.chunked(2).forEachIndexed { rowIdx, pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEachIndexed { colIdx, tool ->
                                TactileCard(
                                    backgroundColor = tints[(rowIdx * 2 + colIdx) % tints.size],
                                    cornerRadius = 20.dp,
                                    onClick = { onOpenTask(tool) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 64.dp)
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = iconForTool(tool),
                                            contentDescription = null,
                                            tint = colors.inkOnAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = tool.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.inkOnAccent,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Most Recent Result Strip (`rec`)
        uiState.recentResults.firstOrNull()?.let { latest ->
            item {
                val recT = if (!openingDriver.showOverlay) 1f else openingDriver.recAnim.value
                TactileCard(
                    backgroundColor = colors.surface,
                    cornerRadius = 22.dp,
                    onClick = { onOpenResult(latest) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = recT.coerceIn(0f, 1f)
                            translationY = (1f - recT) * slide22Px
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = iconForTool(latest.tool),
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = latest.tool.title + " · " + latest.outputFile.extension.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${SelectedFileInfo.formatByteCount(latest.outputSizeBytes)} · just now",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = colors.textSecondary
                            )
                        }
                        TactileCircleIconButton(
                            icon = Icons.Default.IosShare,
                            contentDescription = "Share result",
                            onClick = { onShareFile(latest.outputFile) },
                            backgroundColor = colors.surface,
                            size = 40.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeldAllToolsScreen(
    uiState: MeldUiState,
    onSelectMode: (MeldMode) -> Unit,
    onSelectCategory: (MeldCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenTask: (MeldToolId) -> Unit,
    onOpenChangeFile: () -> Unit
) {
    val colors = meldColors
    val filtered = remember(uiState.selectedMode, uiState.selectedCategory, uiState.searchQuery) {
        val q = uiState.searchQuery.trim().lowercase()
        MeldToolId.entries.filter { tool ->
            (uiState.selectedMode == MeldMode.ALL || tool.mode == uiState.selectedMode) &&
                (uiState.selectedCategory == MeldCategory.ALL || tool.category == uiState.selectedCategory) &&
                (q.isEmpty() || tool.title.lowercase().contains(q) || tool.subtitle.lowercase().contains(q) || tool.badge.lowercase().contains(q))
        }
    }

    val pastelCycle = listOf(
        colors.surface to true,
        colors.mint to false,
        colors.butter to false,
        colors.periwinkle to false,
        colors.lavender to false
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "All tools.",
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Working on ${uiState.selectedFile?.cleanTitle ?: "sample file"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenChangeFile() },
                    shape = CircleShape,
                    color = colors.coral,
                    border = BorderStroke(MeldDimens.StrokeWidth, colors.border)
                ) {
                    Text(
                        text = "Change file",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkOnAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Tactile Search Input
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Find PDF, PNG, Word, Markdown, Remove BG...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textPrimary
                    )
                },
                trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = colors.textPrimary
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.border,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedPlaceholderColor = colors.textSecondary,
                    unfocusedPlaceholderColor = colors.textSecondary,
                    cursorColor = colors.coral
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Mode Pills
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeldMode.entries.forEach { mode ->
                    TactileChipPill(
                        label = mode.label,
                        selected = uiState.selectedMode == mode,
                        onClick = { onSelectMode(mode) }
                    )
                }
            }
        }

        // Category Filter Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MeldCategory.entries.forEach { cat ->
                    TactileChipPill(
                        label = cat.label,
                        selected = uiState.selectedCategory == cat,
                        onClick = { onSelectCategory(cat) }
                    )
                }
            }
        }

        // 2-Column Tactile Action Grid
        item {
            Column(
                modifier = Modifier.animateContentSize(animationSpec = MeldMotion.snapSpring()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filtered.isEmpty()) {
                    TactileCard(
                        backgroundColor = colors.surface,
                        cornerRadius = MeldDimens.CardCorner,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No matching tools",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Try another keyword or clear your filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            TactileChipPill(
                                label = "Reset filters",
                                selected = true,
                                onClick = {
                                    onSelectMode(MeldMode.ALL)
                                    onSelectCategory(MeldCategory.ALL)
                                    onSearchChange("")
                                }
                            )
                        }
                    }
                } else {
                    filtered.chunked(2).forEachIndexed { rowIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEachIndexed { colIdx, tool ->
                            val (bg, isNeutral) = pastelCycle[(rowIdx * 2 + colIdx) % pastelCycle.size]
                            val cardInk = if (isNeutral) colors.textPrimary else colors.inkOnAccent
                            val cardSubInk = if (isNeutral) colors.textSecondary else colors.inkOnAccent.copy(alpha = 0.85f)
                            val innerChipBg = if (isNeutral) colors.warmSurface else colors.surface
                            val innerChipBorder = if (isNeutral) colors.border else colors.inkOnAccent
                            TactileCard(
                                backgroundColor = bg,
                                cornerRadius = 22.dp,
                                onClick = { onOpenTask(tool) },
                                modifier = Modifier
                                    .weight(1f)
                                    .meldSharedToolBounds("tool-${tool.name}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(34.dp),
                                            shape = CircleShape,
                                            color = innerChipBg,
                                            border = BorderStroke(1.5.dp, innerChipBorder)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = iconForTool(tool),
                                                    contentDescription = null,
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = innerChipBg,
                                            border = BorderStroke(1.2.dp, innerChipBorder)
                                        ) {
                                            Text(
                                                text = tool.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 12.sp,
                                                color = colors.textPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cardInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tool.subtitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                        color = cardSubInk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) {
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
fun MeldSettingsScreen(
    uiState: MeldUiState,
    onToggleAutoDownloads: (Boolean) -> Unit,
    onUpdateDefaultHandout: (Int) -> Unit,
    onReplayIntro: () -> Unit
) {
    val colors = meldColors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Preferences.",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Local rules and how MELD handles your files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }

        item { PrefsSectionLabel("PRIVACY") }

        item {
            TactileCard(
                backgroundColor = colors.mint,
                cornerRadius = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = colors.surface,
                        border = BorderStroke(1.8.dp, colors.inkOnAccent)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "100% On-device promise",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkOnAccent
                        )
                        Text(
                            text = "MELD requests zero internet permission. Your documents never touch a server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkOnAccent.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item { PrefsSectionLabel("OUTPUT") }

        item {
            TactileCard(
                backgroundColor = colors.surface,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = uiState.defaultSaveToDownloads,
                            role = androidx.compose.ui.semantics.Role.Switch,
                            onValueChange = onToggleAutoDownloads
                        )
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save directly to Downloads/MELD",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (uiState.defaultSaveToDownloads) {
                                "On: every result is also copied to Downloads/MELD."
                            } else {
                                "Off: files stay private inside MELD until you tap Save."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                    Switch(
                        checked = uiState.defaultSaveToDownloads,
                        onCheckedChange = null,
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

        item {
            TactileCard(
                backgroundColor = colors.surface,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Default Study Handout Layout",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2 to "2-up + notes", 3 to "3-up notes", 4 to "4-up grid").forEach { (n, label) ->
                            TactileChipPill(
                                label = label,
                                selected = uiState.options.handoutPagesPerSheet == n,
                                onClick = { onUpdateDefaultHandout(n) }
                            )
                        }
                    }
                }
            }
        }

        item { PrefsSectionLabel("EXPERIENCE") }

        item {
            TactileCard(
                backgroundColor = colors.butter,
                cornerRadius = 22.dp,
                onClick = onReplayIntro,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = colors.surface,
                        border = BorderStroke(1.8.dp, colors.inkOnAccent)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Replay Opening Animation",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkOnAccent
                        )
                        Text(
                            text = "Watch the MELD sheet fold & dock choreography",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkOnAccent.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item { PrefsSectionLabel("ABOUT") }

        item {
            TactileCard(
                backgroundColor = colors.warmSurface,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "MELD for Android v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${MeldToolId.entries.size} local utility engines • Open source under github.com/meld-app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = meldColors.textSecondary,
        modifier = Modifier
            .padding(top = 6.dp, start = 4.dp)
            .semantics { heading() }
    )
}

@Composable
fun MeldFloatingBottomDock(
    activeTab: DockTab,
    openingDriver: MeldOpeningDriver,
    onSelectTab: (DockTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = meldColors
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val slide90Px = with(density) { 90.dp.toPx() }
    val dockT = if (!openingDriver.showOverlay) 1f else openingDriver.navDockAnim.value

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = dockT.coerceIn(0f, 1f)
                translationY = (1f - dockT) * slide90Px
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        shape = CircleShape,
        color = colors.dockBg,
        border = BorderStroke(2.dp, colors.dockBorder),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple(DockTab.STUDIO, "Studio", Icons.Default.GridView),
                Triple(DockTab.ALL_TOOLS, "Tools", Icons.Default.Build),
                Triple(DockTab.SETTINGS, "Prefs", Icons.Default.Tune)
            )
            items.forEach { (tab, label, icon) ->
                val selected = tab == activeTab
                val pillBg by animateColorAsState(
                    targetValue = if (selected) colors.dockSelectedBg else Color.Transparent,
                    animationSpec = MeldMotion.snapSpring(),
                    label = "dockPillBg"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (reduceMotion) 1f else if (selected) 1.06f else 1f,
                    animationSpec = MeldMotion.popSpring(),
                    label = "dockIconScale"
                )

                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Tab
                            this.selected = selected
                        }
                        .clickable {
                            if (tab != activeTab) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTab(tab)
                            }
                        },
                    shape = CircleShape,
                    color = pillBg
                ) {
                    Column(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 76.dp, minHeight = 52.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) colors.dockSelectedContent else colors.dockUnselectedContent,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) colors.dockSelectedContent else colors.dockUnselectedContent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeldChangeFileBottomSheet(
    onDismiss: () -> Unit,
    onBrowseStorage: () -> Unit,
    onPickSample: (SamplePreset) -> Unit
) {
    val colors = meldColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.canvas
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pick a file to work on",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary
            )

            TactileCard(
                backgroundColor = colors.coral,
                cornerRadius = 20.dp,
                onClick = onBrowseStorage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = colors.surface,
                        border = BorderStroke(1.8.dp, colors.inkOnAccent)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Browse phone storage",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkOnAccent
                        )
                        Text(
                            text = "Pick any PDF, image, audio, or CSV",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkOnAccent.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Text(
                text = "Or try a built-in studio sample:",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )

            SamplePreset.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { preset ->
                        TactileCard(
                            backgroundColor = colors.surface,
                            cornerRadius = 18.dp,
                            onClick = { onPickSample(preset) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = preset.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.stageLaserRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun iconForTool(tool: MeldToolId): ImageVector = when (tool) {
    MeldToolId.IMG_TO_JPG,
    MeldToolId.IMG_TO_PNG,
    MeldToolId.IMG_TO_WEBP,
    MeldToolId.IMG_TO_BMP,
    MeldToolId.IMG_TO_TIFF -> Icons.Default.Transform
    MeldToolId.IMG_TO_PDF -> Icons.Default.PictureAsPdf
    MeldToolId.REMOVE_BG -> Icons.Default.AutoAwesome
    MeldToolId.COMPRESS, MeldToolId.COMPRESS_PDF -> Icons.Default.Compress
    MeldToolId.RESIZE_IMAGE -> Icons.Default.AspectRatio
    MeldToolId.COLLAGE -> Icons.Default.Collections
    MeldToolId.STRIP_EXIF -> Icons.Default.HideImage
    MeldToolId.SCAN_QR -> Icons.Default.QrCode2
    MeldToolId.PDF_TO_PNG, MeldToolId.PDF_TO_JPG -> Icons.Default.Image
    MeldToolId.PDF_TO_MD -> Icons.Default.Code
    MeldToolId.PDF_TO_HTML -> Icons.Default.Language
    MeldToolId.PDF_TO_DOCX -> Icons.Default.Description
    MeldToolId.PDF_TO_TXT -> Icons.Default.EditNote
    MeldToolId.HANDOUT -> Icons.Default.GridView
    MeldToolId.LOCK_STAMP -> Icons.Default.Lock
    MeldToolId.REDACT, MeldToolId.STRIP_PDF_META -> Icons.Default.VisibilityOff
    MeldToolId.PDF_EXTRACT -> Icons.Default.ContentCut
    MeldToolId.PDF_MERGE -> Icons.Default.Layers
    MeldToolId.VISUALIZE -> Icons.Default.GraphicEq
    MeldToolId.SPEED_2X -> Icons.Default.FastForward
    MeldToolId.SHEET_TO_PDF -> Icons.Default.PictureAsPdf
    MeldToolId.SHEET_TO_MD, MeldToolId.TABLE_CONVERT -> Icons.Default.TableChart
    MeldToolId.PACK_ZIP -> Icons.Default.Archive
}
