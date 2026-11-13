package app.meld.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.ui.components.MeldOpeningDriver
import app.meld.android.ui.components.MeldOpeningOverlay
import app.meld.android.ui.screens.MeldAllToolsScreen
import app.meld.android.ui.screens.MeldChangeFileBottomSheet
import app.meld.android.ui.screens.MeldFloatingBottomDock
import app.meld.android.ui.screens.MeldNavigationRail
import app.meld.android.ui.screens.MeldResultScreen
import app.meld.android.ui.screens.MeldSettingsScreen
import app.meld.android.ui.screens.MeldStudioHomeScreen
import app.meld.android.ui.screens.MeldTaskScreen
import app.meld.android.ui.theme.LocalMeldRouteAnimScope
import app.meld.android.ui.theme.LocalMeldSharedScope
import app.meld.android.ui.theme.MeldMotion
import app.meld.android.ui.theme.meldColors
import app.meld.android.ui.theme.rememberReduceMotion
import java.io.File

@Composable
fun MeldAppRoot(
    uiState: MeldUiState,
    onSelectTab: (DockTab) -> Unit,
    onOpenTask: (MeldToolId) -> Unit,
    onOpenResult: (app.meld.android.model.ConversionResult) -> Unit,
    onNavigateHome: () -> Unit,
    onSetFileSheetVisible: (Boolean) -> Unit,
    onClearBanner: () -> Unit = {},
    onPickExternalFile: () -> Unit,
    onLoadSample: (SamplePreset) -> Unit,
    onSelectMode: (MeldMode) -> Unit,
    onSelectCategory: (MeldCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onRunTool: (MeldToolId) -> Unit,
    onContinueTool: (app.meld.android.model.ConversionResult, MeldToolId) -> Unit,
    onSaveToDownloads: (File) -> Unit,
    onShareResult: (File) -> Unit,
    onUpdateTargetKb: (Int) -> Unit,
    onUpdateResizePercent: (Int) -> Unit,
    onToggleStudioFrame: (Boolean) -> Unit,
    onUpdateStampText: (String) -> Unit,
    onUpdateHandoutMode: (Int) -> Unit,
    onUpdateQrPayload: (String) -> Unit,
    onUpdatePageRange: (Int, Int) -> Unit,
    onUpdateChromaTolerance: (Int) -> Unit,
    onUpdateRedactionZones: (List<app.meld.android.model.RedactionRect>) -> Unit = {},
    onToggleAutoDownloads: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    // Play opening choreography automatically on first launch only; subsequent launches go straight to Studio.
    val hasSeenOpening = remember(context) {
        context.getSharedPreferences("meld_prefs", Context.MODE_PRIVATE)
            .getBoolean("has_seen_opening_choreography", false)
    }
    val openingDriver = remember(hasSeenOpening, reduceMotion) {
        MeldOpeningDriver(initiallyVisible = !hasSeenOpening && !reduceMotion)
    }
    var replaySeed by remember { mutableIntStateOf(0) }
    var coldPlayedInSession by remember { mutableIntStateOf(0) }

    LaunchedEffect(replaySeed, reduceMotion) {
        val isReplay = replaySeed > 0
        val alreadySeen = hasSeenOpening || coldPlayedInSession > 0
        if (!isReplay && !alreadySeen) {
            coldPlayedInSession++
            context.getSharedPreferences("meld_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_seen_opening_choreography", true)
                .apply()
        }
        openingDriver.playSequence(
            isColdStart = !isReplay,
            reduceMotion = reduceMotion,
            skipToRest = !isReplay && alreadySeen
        )
    }

    val replayOpening: () -> Unit = {
        onSelectTab(DockTab.STUDIO)
        onNavigateHome()
        replaySeed++
    }

    // Handle Android system back button inside Task & Result sub-screens
    BackHandler(enabled = uiState.route != MeldScreenRoute.Home) {
        onNavigateHome()
    }

    val colors = meldColors
    val fontScale = LocalDensity.current.fontScale

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.canvas
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .imePadding()
        ) {
            val adaptiveSpec = remember(maxWidth, maxHeight, fontScale) {
                MeldAdaptiveLayout.specForWindow(maxWidth.value, maxHeight.value, fontScale)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = adaptiveSpec.containerMaxWidth)
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        openingDriver.rootOrigin = coords.positionInRoot()
                    }
            ) {
                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalMeldSharedScope provides this) {
                        AnimatedContent(
                            targetState = uiState.route,
                            transitionSpec = {
                                if (reduceMotion) {
                                    fadeIn(tween(110)) togetherWith fadeOut(tween(90))
                                } else {
                                    when {
                                        targetState is MeldScreenRoute.Task && initialState is MeldScreenRoute.Home -> {
                                            fadeIn(tween(MeldMotion.UI_MS, easing = MeldMotion.EaseOut)) togetherWith
                                                fadeOut(tween(MeldMotion.EXIT_MS, easing = MeldMotion.EaseOut))
                                        }
                                        targetState is MeldScreenRoute.Result -> {
                                            (slideInVertically(MeldMotion.popSpring()) { it / 6 } +
                                                scaleIn(initialScale = 0.94f, animationSpec = tween(MeldMotion.UI_MS, easing = MeldMotion.EaseOut)) +
                                                fadeIn(tween(MeldMotion.UI_MS))) togetherWith fadeOut(tween(MeldMotion.EXIT_MS))
                                        }
                                        targetState is MeldScreenRoute.Home -> {
                                            fadeIn(tween(MeldMotion.UI_MS, easing = MeldMotion.EaseOut)) togetherWith
                                                fadeOut(tween(MeldMotion.EXIT_MS, easing = MeldMotion.EaseOut))
                                        }
                                        else -> {
                                            (slideInHorizontally(tween(MeldMotion.UI_MS, easing = MeldMotion.EaseOut)) { it / 4 } +
                                                fadeIn(tween(MeldMotion.UI_MS))) togetherWith
                                                (slideOutHorizontally(tween(MeldMotion.EXIT_MS)) { -it / 5 } +
                                                    fadeOut(tween(MeldMotion.EXIT_MS)))
                                        }
                                    }
                                }
                            },
                            label = "MeldScreenTransition"
                        ) { route ->
                            val routeAnimScope = this
                            when (route) {
                                MeldScreenRoute.Home -> {
                                    if (adaptiveSpec.useNavigationRail) {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            MeldNavigationRail(
                                                activeTab = uiState.activeTab,
                                                openingDriver = openingDriver,
                                                onSelectTab = onSelectTab,
                                                compactHeight = adaptiveSpec.isCompactHeight
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                MeldHomeTabContent(
                                                    uiState = uiState,
                                                    reduceMotion = reduceMotion,
                                                    openingDriver = openingDriver,
                                                    routeAnimScope = routeAnimScope,
                                                    adaptiveSpec = adaptiveSpec,
                                                    replayOpening = replayOpening,
                                                    onSetFileSheetVisible = onSetFileSheetVisible,
                                                    onOpenTask = onOpenTask,
                                                    onSelectTab = onSelectTab,
                                                    onOpenResult = onOpenResult,
                                                    onShareResult = onShareResult,
                                                    onSelectMode = onSelectMode,
                                                    onSelectCategory = onSelectCategory,
                                                    onSearchChange = onSearchChange,
                                                    onToggleAutoDownloads = onToggleAutoDownloads,
                                                    onUpdateHandoutMode = onUpdateHandoutMode,
                                                    modifier = Modifier
                                                        .widthIn(max = adaptiveSpec.contentMaxWidth)
                                                        .fillMaxSize()
                                                )
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            MeldHomeTabContent(
                                                uiState = uiState,
                                                reduceMotion = reduceMotion,
                                                openingDriver = openingDriver,
                                                routeAnimScope = routeAnimScope,
                                                adaptiveSpec = adaptiveSpec,
                                                replayOpening = replayOpening,
                                                onSetFileSheetVisible = onSetFileSheetVisible,
                                                onOpenTask = onOpenTask,
                                                onSelectTab = onSelectTab,
                                                onOpenResult = onOpenResult,
                                                onShareResult = onShareResult,
                                                onSelectMode = onSelectMode,
                                                onSelectCategory = onSelectCategory,
                                                onSearchChange = onSearchChange,
                                                onToggleAutoDownloads = onToggleAutoDownloads,
                                                onUpdateHandoutMode = onUpdateHandoutMode,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            MeldFloatingBottomDock(
                                                activeTab = uiState.activeTab,
                                                openingDriver = openingDriver,
                                                onSelectTab = onSelectTab,
                                                modifier = Modifier.align(Alignment.BottomCenter)
                                            )
                                        }
                                    }
                                }

                                is MeldScreenRoute.Task -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(max = 680.dp)
                                                .fillMaxSize()
                                        ) {
                                            CompositionLocalProvider(LocalMeldRouteAnimScope provides routeAnimScope) {
                                                MeldTaskScreen(
                                                    tool = route.tool,
                                                    uiState = uiState,
                                                    onBack = onNavigateHome,
                                                    onRunTool = { onRunTool(route.tool) },
                                                    onOpenChangeFile = { onSetFileSheetVisible(true) },
                                                    onUpdateTargetKb = onUpdateTargetKb,
                                                    onUpdateResizePercent = onUpdateResizePercent,
                                                    onToggleStudioFrame = onToggleStudioFrame,
                                                    onUpdateStampText = onUpdateStampText,
                                                    onUpdateHandoutMode = onUpdateHandoutMode,
                                                    onUpdateQrPayload = onUpdateQrPayload,
                                                    onUpdatePageRange = onUpdatePageRange,
                                                    onUpdateChromaTolerance = onUpdateChromaTolerance,
                                                    onUpdateRedactionZones = onUpdateRedactionZones
                                                )
                                            }
                                        }
                                    }
                                }

                                is MeldScreenRoute.Result -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(max = 680.dp)
                                                .fillMaxSize()
                                        ) {
                                            MeldResultScreen(
                                                result = route.conversion,
                                                bannerMessage = uiState.bannerMessage,
                                                onClose = onNavigateHome,
                                                onContinueTool = { nextTool -> onContinueTool(route.conversion, nextTool) },
                                                onSaveToDownloads = onSaveToDownloads,
                                                onShare = onShareResult
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Signature Opening Animation Overlay (shown on Studio launch & Replay)
                if (uiState.route == MeldScreenRoute.Home && uiState.activeTab == DockTab.STUDIO) {
                    MeldOpeningOverlay(driver = openingDriver)
                }

                if (uiState.showFileSheet) {
                    MeldChangeFileBottomSheet(
                        onDismiss = { onSetFileSheetVisible(false) },
                        onBrowseStorage = onPickExternalFile,
                        onPickSample = onLoadSample
                    )
                }

                if (uiState.route == MeldScreenRoute.Home && uiState.bannerMessage != null && !openingDriver.showOverlay) {
                    LaunchedEffect(uiState.bannerMessage) {
                        kotlinx.coroutines.delay(2800L)
                        onClearBanner()
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp, start = 20.dp, end = 20.dp)
                            .clickable { onClearBanner() },
                        shape = CircleShape,
                        color = if (uiState.isBannerError) colors.error else colors.bannerBg,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = uiState.bannerMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.isBannerError) colors.onError else colors.bannerContent,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeldHomeTabContent(
    uiState: MeldUiState,
    reduceMotion: Boolean,
    openingDriver: MeldOpeningDriver,
    routeAnimScope: androidx.compose.animation.AnimatedVisibilityScope,
    adaptiveSpec: MeldAdaptiveSpec,
    replayOpening: () -> Unit,
    onSetFileSheetVisible: (Boolean) -> Unit,
    onOpenTask: (MeldToolId) -> Unit,
    onSelectTab: (DockTab) -> Unit,
    onOpenResult: (app.meld.android.model.ConversionResult) -> Unit,
    onShareResult: (File) -> Unit,
    onSelectMode: (MeldMode) -> Unit,
    onSelectCategory: (MeldCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleAutoDownloads: (Boolean) -> Unit,
    onUpdateHandoutMode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = uiState.activeTab,
        modifier = modifier,
        transitionSpec = {
            // Peer navigation: cross-fade only, no directional slide between tabs
            if (reduceMotion) {
                fadeIn(tween(80)) togetherWith fadeOut(tween(60))
            } else {
                fadeIn(tween(150, easing = MeldMotion.EaseOut)) togetherWith
                    fadeOut(tween(110, easing = MeldMotion.EaseOut))
            }
        },
        label = "MeldDockTabTransition"
    ) { tab ->
        CompositionLocalProvider(
            LocalMeldRouteAnimScope provides if (tab == uiState.activeTab) routeAnimScope else null
        ) {
            when (tab) {
                DockTab.STUDIO -> {
                    MeldStudioHomeScreen(
                        uiState = uiState,
                        openingDriver = openingDriver,
                        onReplayIntro = replayOpening,
                        onOpenChangeFile = { onSetFileSheetVisible(true) },
                        onOpenTask = onOpenTask,
                        onSeeAllTools = { onSelectTab(DockTab.ALL_TOOLS) },
                        onOpenResult = onOpenResult,
                        onShareFile = onShareResult,
                        contentStartPadding = adaptiveSpec.listStartPadding,
                        contentBottomPadding = adaptiveSpec.listBottomPadding
                    )
                }
                DockTab.ALL_TOOLS -> {
                    MeldAllToolsScreen(
                        uiState = uiState,
                        onSelectMode = onSelectMode,
                        onSelectCategory = onSelectCategory,
                        onSearchChange = onSearchChange,
                        onOpenTask = onOpenTask,
                        onOpenChangeFile = { onSetFileSheetVisible(true) },
                        contentStartPadding = adaptiveSpec.listStartPadding,
                        contentBottomPadding = adaptiveSpec.listBottomPadding
                    )
                }
                DockTab.SETTINGS -> {
                    MeldSettingsScreen(
                        uiState = uiState,
                        onToggleAutoDownloads = onToggleAutoDownloads,
                        onUpdateDefaultHandout = onUpdateHandoutMode,
                        onReplayIntro = replayOpening,
                        contentStartPadding = adaptiveSpec.listStartPadding,
                        contentBottomPadding = adaptiveSpec.listBottomPadding
                    )
                }
            }
        }
    }
}
