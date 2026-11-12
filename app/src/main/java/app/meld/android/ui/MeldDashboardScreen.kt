package app.meld.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
import app.meld.android.ui.components.MeldOpeningDriver
import app.meld.android.ui.components.MeldOpeningOverlay
import app.meld.android.ui.screens.MeldAllToolsScreen
import app.meld.android.ui.screens.MeldChangeFileBottomSheet
import app.meld.android.ui.screens.MeldFloatingBottomDock
import app.meld.android.ui.screens.MeldResultScreen
import app.meld.android.ui.screens.MeldSettingsScreen
import app.meld.android.ui.screens.MeldStudioHomeScreen
import app.meld.android.ui.screens.MeldTaskScreen
import app.meld.android.ui.theme.MeldColors
import java.io.File

@Composable
fun MeldAppRoot(
    uiState: MeldUiState,
    onSelectTab: (DockTab) -> Unit,
    onOpenTask: (MeldToolId) -> Unit,
    onOpenResult: (app.meld.android.model.ConversionResult) -> Unit,
    onNavigateHome: () -> Unit,
    onSetFileSheetVisible: (Boolean) -> Unit,
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
    onToggleAutoDownloads: (Boolean) -> Unit
) {
    val openingDriver = remember { MeldOpeningDriver() }
    var replaySeed by remember { mutableIntStateOf(0) }

    LaunchedEffect(replaySeed) {
        openingDriver.playSequence(isColdStart = (replaySeed == 0))
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MeldColors.CreamCanvas
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .onGloballyPositioned { coords ->
                    openingDriver.rootOrigin = coords.positionInRoot()
                }
        ) {
            AnimatedContent(
                targetState = uiState.route,
                transitionSpec = {
                    when {
                        targetState is MeldScreenRoute.Task && initialState is MeldScreenRoute.Home -> {
                            (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                                fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(180)))
                        }
                        targetState is MeldScreenRoute.Result -> {
                            (slideInVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it / 5 } +
                                scaleIn(initialScale = 0.94f, animationSpec = tween(260)) +
                                fadeIn(tween(240))) togetherWith fadeOut(tween(180))
                        }
                        targetState is MeldScreenRoute.Home -> {
                            (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 5 } +
                                fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(180)))
                        }
                        else -> {
                            (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 5 } + fadeOut(tween(180)))
                        }
                    }
                },
                label = "MeldScreenTransition"
            ) { route ->
                when (route) {
                    MeldScreenRoute.Home -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = uiState.activeTab,
                                transitionSpec = {
                                    (fadeIn(tween(200)) + slideInVertically(tween(220)) { 18 }) togetherWith
                                        fadeOut(tween(140))
                                },
                                label = "MeldDockTabTransition"
                            ) { tab ->
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
                                            onShareFile = onShareResult
                                        )
                                    }
                                    DockTab.ALL_TOOLS -> {
                                        MeldAllToolsScreen(
                                            uiState = uiState,
                                            onSelectMode = onSelectMode,
                                            onSelectCategory = onSelectCategory,
                                            onSearchChange = onSearchChange,
                                            onOpenTask = onOpenTask,
                                            onOpenChangeFile = { onSetFileSheetVisible(true) }
                                        )
                                    }
                                    DockTab.SETTINGS -> {
                                        MeldSettingsScreen(
                                            uiState = uiState,
                                            onToggleAutoDownloads = onToggleAutoDownloads,
                                            onUpdateDefaultHandout = onUpdateHandoutMode,
                                            onReplayIntro = replayOpening
                                        )
                                    }
                                }
                            }

                            MeldFloatingBottomDock(
                                activeTab = uiState.activeTab,
                                openingDriver = openingDriver,
                                onSelectTab = onSelectTab,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    is MeldScreenRoute.Task -> {
                        MeldTaskScreen(
                            tool = route.tool,
                            uiState = uiState,
                            onBack = onNavigateHome,
                            onRunTool = { onRunTool(route.tool) },
                            onUpdateTargetKb = onUpdateTargetKb,
                            onUpdateResizePercent = onUpdateResizePercent,
                            onToggleStudioFrame = onToggleStudioFrame,
                            onUpdateStampText = onUpdateStampText,
                            onUpdateHandoutMode = onUpdateHandoutMode,
                            onUpdateQrPayload = onUpdateQrPayload,
                            onUpdatePageRange = onUpdatePageRange,
                            onUpdateChromaTolerance = onUpdateChromaTolerance
                        )
                    }

                    is MeldScreenRoute.Result -> {
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
        }
    }
}
