package app.meld.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.meld.android.model.MeldCategory
import app.meld.android.model.MeldMode
import app.meld.android.model.MeldToolId
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
        ) {
            AnimatedContent(
                targetState = uiState.route,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "MeldScreenTransition"
            ) { route ->
                when (route) {
                    MeldScreenRoute.Home -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (uiState.activeTab) {
                                DockTab.STUDIO -> {
                                    MeldStudioHomeScreen(
                                        uiState = uiState,
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
                                        onUpdateDefaultHandout = onUpdateHandoutMode
                                    )
                                }
                            }

                            MeldFloatingBottomDock(
                                activeTab = uiState.activeTab,
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
