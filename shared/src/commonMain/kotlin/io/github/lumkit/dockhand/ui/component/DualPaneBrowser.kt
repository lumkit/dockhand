package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.ui.screen.home.HomeUiState
import io.github.lumkit.dockhand.ui.screen.home.PaneId
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.VerticalDivider

/**
 * 双列表面板：顶部文件工具栏 + 左右两个文件列表。
 *
 * 工具栏只作用于“焦点面板”，所以所有动作都从前 [HomeUiState.focusedPane] 推导，
 * 具体导航/新建/传输由调用方注入。
 */
@Composable
fun DualPaneBrowser(
    uiState: HomeUiState,
    onPaneFocus: (PaneId) -> Unit,
    onEntryClick: (PaneId, FileEntry) -> Unit,
    onEntryDoubleClick: (PaneId, FileEntry) -> Unit,
    onEntrySecondaryClick: (PaneId, FileEntry) -> Unit,
    onEntryContextAction: (PaneId, FileEntry, FileContextAction) -> Unit,
    onRetry: (PaneId) -> Unit,
    onLoadMore: (PaneId) -> Unit,
    onBack: (PaneId) -> Unit,
    onForward: (PaneId) -> Unit,
    onGoUp: (PaneId) -> Unit,
    onCreate: (PaneId, String, Boolean) -> Unit,
    onSync: (PaneId) -> Unit,
    onDismissOperationError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusedPane = uiState.focusedPane
    val focusedState = uiState.focusedPaneState
    val otherState = uiState.pane(focusedPane.other)
    val canSync = focusedState.location != null &&
        otherState.location != focusedState.location
    // 跨 provider 复制由任务层通过临时文件中转完成。
    val leftCanTransfer = uiState.leftPane.location != null && uiState.rightPane.location != null
    val rightCanTransfer = uiState.rightPane.location != null && uiState.leftPane.location != null

    Column(modifier = modifier.fillMaxSize()) {
        FileToolbar(
            canGoBack = focusedState.canGoBack,
            canGoForward = focusedState.canGoForward,
            canGoUp = focusedState.canGoUp,
            canCreate = focusedState.location != null,
            canSync = canSync,
            operationError = uiState.operationError,
            onBack = { onBack(focusedPane) },
            onForward = { onForward(focusedPane) },
            onGoUp = { onGoUp(focusedPane) },
            onCreate = { name, createFile -> onCreate(focusedPane, name, createFile) },
            onSync = { onSync(focusedPane) },
            onRefresh = { onRetry(focusedPane) },
            onDismissOperationError = onDismissOperationError,
        )

        HorizontalDivider()

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            FileBrowserPane(
                pane = uiState.leftPane,
                isFocused = uiState.focusedPane == PaneId.Left,
                onFocus = { onPaneFocus(PaneId.Left) },
                onEntryClick = { onEntryClick(PaneId.Left, it) },
                onEntryDoubleClick = { onEntryDoubleClick(PaneId.Left, it) },
                onEntrySecondaryClick = { onEntrySecondaryClick(PaneId.Left, it) },
                onContextAction = { entry, action ->
                    onEntryContextAction(PaneId.Left, entry, action)
                },
                onRetry = { onRetry(PaneId.Left) },
                onLoadMore = { onLoadMore(PaneId.Left) },
                canTransferToOtherPane = leftCanTransfer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                overlayShape = RoundedCornerShape(0.dp),
            )

            VerticalDivider()

            FileBrowserPane(
                pane = uiState.rightPane,
                isFocused = uiState.focusedPane == PaneId.Right,
                onFocus = { onPaneFocus(PaneId.Right) },
                onEntryClick = { onEntryClick(PaneId.Right, it) },
                onEntryDoubleClick = { onEntryDoubleClick(PaneId.Right, it) },
                onEntrySecondaryClick = { onEntrySecondaryClick(PaneId.Right, it) },
                onContextAction = { entry, action ->
                    onEntryContextAction(PaneId.Right, entry, action)
                },
                onRetry = { onRetry(PaneId.Right) },
                onLoadMore = { onLoadMore(PaneId.Right) },
                canTransferToOtherPane = rightCanTransfer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                overlayShape = RoundedCornerShape(0.dp),
            )
        }
    }
}