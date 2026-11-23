package io.github.dockhand.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dockhand.LocalAppContainer
import io.github.dockhand.domain.model.FileBreadcrumb
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.ui.component.DualPaneBrowser
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.LocalWindowState
import top.yukonga.miuix.kmp.window.MiuixCaptionBar

@Composable
fun HomeScreen() {
    val container = LocalAppContainer.current
    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(files = container.files, settings = container.settings)
    }
    val uiState by homeViewModel.state.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onPlaceClick = homeViewModel::onPlaceClick,
        onPaneFocus = homeViewModel::onPaneFocus,
        onEntryClick = homeViewModel::onEntryClick,
        onEntryDoubleClick = homeViewModel::onEntryDoubleClick,
        onEntrySecondaryClick = homeViewModel::onEntrySecondaryClick,
        onPlaceMove = homeViewModel::onMovePlace,
        onMtpStorageClick = homeViewModel::onMtpStorageClick,
        onMtpShortcutMove = homeViewModel::onMoveMtpShortcut,
        onBreadcrumbClick = homeViewModel::onBreadcrumbClick,
        onRetry = homeViewModel::onRefresh,
        onBack = homeViewModel::onBack,
        onForward = homeViewModel::onForward,
        onGoUp = homeViewModel::onGoUp,
        onCreate = homeViewModel::onCreate,
        onSync = homeViewModel::onSyncOtherPane,
        onDismissOperationError = homeViewModel::onDismissOperationError,
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onPlaceClick: (FilePlace) -> Unit,
    onPaneFocus: (PaneId) -> Unit,
    onEntryClick: (PaneId, FileEntry) -> Unit,
    onEntryDoubleClick: (PaneId, FileEntry) -> Unit,
    onEntrySecondaryClick: (PaneId, FileEntry) -> Unit,
    onPlaceMove: (FilePlace, Int) -> Boolean,
    onMtpStorageClick: (FilePlace, Boolean) -> Unit,
    onMtpShortcutMove: (String, Int) -> Boolean,
    onBreadcrumbClick: (FileBreadcrumb) -> Unit,
    onRetry: (PaneId) -> Unit,
    onBack: (PaneId) -> Unit,
    onForward: (PaneId) -> Unit,
    onGoUp: (PaneId) -> Unit,
    onCreate: (PaneId, String, Boolean) -> Unit,
    onSync: (PaneId) -> Unit,
    onDismissOperationError: () -> Unit,
) {
    val windowState = LocalWindowState.current
    val windowPlacement = windowState.placement
    val direction = LocalLayoutDirection.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MiuixCaptionBar {
                if (windowPlacement != WindowPlacement.Fullscreen) {
                    HomeTopBar(
                        location = uiState.focusedLocation,
                        onBreadcrumbClick = onBreadcrumbClick,
                    ) {
                        TopBarActions()
                    }
                }
            }
        }
    ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize()) {
            val drawerPaddingValues = PaddingValues(
                start = paddingValues.calculateStartPadding(direction),
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
            )
            val contentPaddingValues = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
                end = paddingValues.calculateEndPadding(direction),
            )

            // 左侧：真实地点列表
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
            ) {
                HomeDrawerContent(
                    places = uiState.places,
                    activeLocation = uiState.focusedLocation,
                    placeOrder = uiState.drawerPlaceOrder,
                    mtpExpandedSources = uiState.mtpExpandedSources,
                    mtpShortcutOrder = uiState.mtpShortcutOrder,
                    onPlaceMove = onPlaceMove,
                    onMtpStorageClick = onMtpStorageClick,
                    onMtpShortcutMove = onMtpShortcutMove,
                    onPlaceClick = onPlaceClick,
                    modifier = Modifier.padding(drawerPaddingValues),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                if (windowPlacement == WindowPlacement.Fullscreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        HomeTopBar(
                            location = uiState.focusedLocation,
                            onBreadcrumbClick = onBreadcrumbClick,
                        ) {
                            TopBarActions()
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPaddingValues)
                        .squircleClip(
                            topStart = 12.dp,
                            topEnd = 0.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ) {
                    if (uiState.isBootstrapping) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uiState.placesError != null) {
                                Text(
                                    text = uiState.placesError,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            } else {
                                InfiniteProgressIndicator()
                            }
                        }
                    } else {
                        DualPaneBrowser(
                            uiState = uiState,
                            onPaneFocus = onPaneFocus,
                            onEntryClick = onEntryClick,
                            onEntryDoubleClick = onEntryDoubleClick,
                            onEntrySecondaryClick = onEntrySecondaryClick,
                            onRetry = onRetry,
                            onBack = onBack,
                            onForward = onForward,
                            onGoUp = onGoUp,
                            onCreate = onCreate,
                            onSync = onSync,
                            onDismissOperationError = onDismissOperationError,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarActions() {
    // 更多菜单的内容（新建、排序、显示隐藏文件……）在后续迭代接入。
    IconButton(
        onClick = { },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.More,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}