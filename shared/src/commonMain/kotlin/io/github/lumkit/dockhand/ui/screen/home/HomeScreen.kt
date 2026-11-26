package io.github.lumkit.dockhand.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lumkit.dockhand.LocalAppContainer
import io.github.lumkit.dockhand.domain.model.FileBreadcrumb
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLayoutMode
import io.github.lumkit.dockhand.domain.model.FileSortMode
import io.github.lumkit.dockhand.ui.component.ExternalDropAction
import io.github.lumkit.dockhand.ui.component.ExternalDropBridge
import io.github.lumkit.dockhand.ui.component.FileContextAction
import io.github.lumkit.dockhand.ui.component.LocalExternalDropController
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.shortcut.DefaultShortcutRegistry
import io.github.lumkit.dockhand.shortcut.ShortcutAction
import io.github.lumkit.dockhand.shortcut.ShortcutBinding
import io.github.lumkit.dockhand.shortcut.ShortcutDispatcher
import io.github.lumkit.dockhand.shortcut.ShortcutKey
import io.github.lumkit.dockhand.shortcut.ShortcutRegistry
import io.github.lumkit.dockhand.ui.component.DualPaneBrowser
import io.github.lumkit.dockhand.task.AppTask
import io.github.lumkit.dockhand.task.TaskState
import io.github.lumkit.dockhand.ui.component.CompressRequestDialog
import io.github.lumkit.dockhand.ui.component.ExtractPasswordDialog
import io.github.lumkit.dockhand.ui.component.ExtractRequestDialog
import io.github.lumkit.dockhand.ui.component.LocalOpenWithSelect
import io.github.lumkit.dockhand.ui.component.LocalOpenWithState
import io.github.lumkit.dockhand.ui.component.CopyRequestDialog
import io.github.lumkit.dockhand.ui.component.DeleteRequestDialog
import io.github.lumkit.dockhand.ui.component.MoveRequestDialog
import io.github.lumkit.dockhand.ui.component.SelectionMode
import io.github.lumkit.dockhand.ui.component.RenameRequestDialog
import io.github.lumkit.dockhand.ui.component.TaskProgressDialog
import io.github.lumkit.dockhand.ui.component.TrashRequestDialog
import io.github.lumkit.dockhand.ui.component.FileInfoDialogContent
import io.github.lumkit.dockhand.ui.component.FileInfoDialogWindow
import io.github.lumkit.dockhand.ui.component.LocalInlineRenameBounds
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.action_retry
import dockhand.shared.generated.resources.file_info_title
import dockhand.shared.generated.resources.ic_backend_task
import dockhand.shared.generated.resources.ic_home
import dockhand.shared.generated.resources.menu_arrangement
import dockhand.shared.generated.resources.menu_hidden_items
import dockhand.shared.generated.resources.menu_layout_grid
import dockhand.shared.generated.resources.menu_layout_list
import dockhand.shared.generated.resources.menu_sort
import dockhand.shared.generated.resources.menu_sort_modified_asc
import dockhand.shared.generated.resources.menu_sort_modified_desc
import dockhand.shared.generated.resources.menu_sort_name_asc
import dockhand.shared.generated.resources.menu_sort_name_desc
import dockhand.shared.generated.resources.menu_sort_size_asc
import dockhand.shared.generated.resources.menu_sort_size_desc
import dockhand.shared.generated.resources.menu_sort_type_asc
import dockhand.shared.generated.resources.menu_sort_type_desc
import dockhand.shared.generated.resources.menu_view
import dockhand.shared.generated.resources.task_cancel_message
import dockhand.shared.generated.resources.task_cancel_title
import dockhand.shared.generated.resources.task_cancelled
import dockhand.shared.generated.resources.task_completed
import dockhand.shared.generated.resources.task_failed
import dockhand.shared.generated.resources.task_progress_unknown
import dockhand.shared.generated.resources.task_running
import dockhand.shared.generated.resources.task_waiting
import dockhand.shared.generated.resources.task_eta_days_hours
import dockhand.shared.generated.resources.task_eta_hours_minutes
import dockhand.shared.generated.resources.task_eta_less_than_second
import dockhand.shared.generated.resources.task_eta_minutes_seconds
import dockhand.shared.generated.resources.task_eta_seconds
import dockhand.shared.generated.resources.task_speed
import dockhand.shared.generated.resources.task_speed_eta
import dockhand.shared.generated.resources.tasks_badge_overflow
import dockhand.shared.generated.resources.tasks_empty
import dockhand.shared.generated.resources.tasks_tooltip_title
import dockhand.shared.generated.resources.text_loading_more
import dockhand.shared.generated.resources.text_more
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.TooltipState
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.LocalWindowState
import top.yukonga.miuix.kmp.window.MiuixCaptionBar

@Composable
fun HomeScreen() {
    val container = LocalAppContainer.current
    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(
            files = container.files,
            settings = container.settings,
            terminalLauncher = container.terminalLauncher,
            cacheManager = container.cacheManager,
            archiveService = container.archiveService,
            fileOpener = container.fileOpener,
        )
    }
    val uiState by homeViewModel.state.collectAsState()
    val tasks by homeViewModel.tasks.collectAsState()

    val externalDropController = LocalExternalDropController.current
    DisposableEffect(externalDropController) {
        externalDropController?.bridge = object : ExternalDropBridge {
            override fun onPaneFocused(paneId: PaneId) {
                homeViewModel.onPaneFocus(paneId)
            }

            override fun onDrop(paneId: PaneId, paths: List<String>, action: ExternalDropAction?) {
                homeViewModel.onExternalFilesDropped(paneId, paths, action)
            }
        }
        onDispose { externalDropController?.bridge = null }
    }

    LaunchedEffect(uiState.language) {
        homeViewModel.onLanguageChanged()
    }

    HomeScreenContent(
        uiState = uiState,
        tasks = tasks,
        onCancelTask = homeViewModel::onCancelTask,
        onDismissTask = homeViewModel::onDismissTask,
        onActiveTaskBackground = homeViewModel::onActiveTaskBackground,
        onActiveTaskCancel = homeViewModel::onActiveTaskCancel,
        onTaskProgressOpen = homeViewModel::onTaskProgressOpen,
        onSelectAll = homeViewModel::onSelectAll,
        onClearSelection = homeViewModel::onClearSelectionShortcut,
        onSelectPrevious = homeViewModel::onSelectPrevious,
        onSelectNext = homeViewModel::onSelectNext,
        onSelectLeft = homeViewModel::onSelectLeft,
        onSelectRight = homeViewModel::onSelectRight,
        onOpenContextMenu = homeViewModel::onOpenContextMenu,
        onShortcutCopy = homeViewModel::onShortcutCopy,
        onShortcutMove = homeViewModel::onShortcutMove,
        onShortcutDelete = homeViewModel::onShortcutDelete,
        onShortcutRename = homeViewModel::onShortcutRename,
        onShortcutCompress = homeViewModel::onShortcutCompress,
        onShortcutTrash = homeViewModel::onShortcutTrash,
        onExtendSelectionUp = homeViewModel::onExtendSelectionUp,
        onExtendSelectionDown = homeViewModel::onExtendSelectionDown,
        onExtendSelectionLeft = homeViewModel::onExtendSelectionLeft,
        onExtendSelectionRight = homeViewModel::onExtendSelectionRight,
        onInlineRenameStarted = homeViewModel::onInlineRenameStarted,
        onGridColumnsChanged = homeViewModel::onGridColumnsChanged,
        onContextMenuRequest = homeViewModel::onContextMenuRequest,
        onContextMenuDismiss = homeViewModel::onContextMenuDismiss,
        onShortcutAdd = homeViewModel::onShortcutAdd,
        onShortcutRemove = homeViewModel::onShortcutRemove,
        onResetShortcuts = homeViewModel::onResetShortcuts,
        onSnackbarShown = homeViewModel::onSnackbarShown,
        onSnackbarRetry = homeViewModel::onSnackbarRetry,
        onPlaceClick = homeViewModel::onPlaceClick,
        onPaneFocus = homeViewModel::onPaneFocus,
        onEntryClick = homeViewModel::onEntryClick,
        onEntryDoubleClick = homeViewModel::onEntryDoubleClick,
        onEntrySecondaryClick = homeViewModel::onEntrySecondaryClick,
        onSelectionChange = homeViewModel::onSelectionChange,
        onSelectionClear = homeViewModel::onSelectionClear,
        onEntryContextAction = homeViewModel::onEntryContextAction,
        onInlineRename = { _, entry, name -> homeViewModel.onInlineRename(entry, name) },
        onDismissFileInfo = homeViewModel::onDismissFileInfo,
        onCopyRequestBackgroundChange = homeViewModel::onCopyRequestBackgroundChange,
        onCopyRequestDismiss = homeViewModel::onCopyRequestDismiss,
        onCopyRequestConfirm = homeViewModel::onCopyRequestConfirm,
        onMoveRequestBackgroundChange = homeViewModel::onMoveRequestBackgroundChange,
        onMoveRequestDismiss = homeViewModel::onMoveRequestDismiss,
        onMoveRequestConfirm = homeViewModel::onMoveRequestConfirm,
        onDeleteRequestBackgroundChange = homeViewModel::onDeleteRequestBackgroundChange,
        onDeleteRequestDismiss = homeViewModel::onDeleteRequestDismiss,
        onDeleteRequestConfirm = homeViewModel::onDeleteRequestConfirm,
        onRenameRequestNameChange = homeViewModel::onRenameRequestNameChange,
        onRenameRequestBackgroundChange = homeViewModel::onRenameRequestBackgroundChange,
        onRenameRequestDismiss = homeViewModel::onRenameRequestDismiss,
        onRenameRequestConfirm = homeViewModel::onRenameRequestConfirm,
        onCompressRequestNameChange = homeViewModel::onCompressRequestNameChange,
        onCompressRequestFormatChange = homeViewModel::onCompressRequestFormatChange,
        onCompressRequestLevelChange = homeViewModel::onCompressRequestLevelChange,
        onCompressRequestPasswordChange = homeViewModel::onCompressRequestPasswordChange,
        onCompressRequestSplitChange = homeViewModel::onCompressRequestSplitChange,
        onCompressRequestSeparateChange = homeViewModel::onCompressRequestSeparateChange,
        onCompressRequestDeleteSourceChange = homeViewModel::onCompressRequestDeleteSourceChange,
        onCompressRequestToOtherPaneChange = homeViewModel::onCompressRequestToOtherPaneChange,
        onCompressRequestBackgroundChange = homeViewModel::onCompressRequestBackgroundChange,
        onCompressRequestDismiss = homeViewModel::onCompressRequestDismiss,
        onCompressRequestConfirm = homeViewModel::onCompressRequestConfirm,
        onExtractRequestModeChange = homeViewModel::onExtractRequestModeChange,
        onExtractRequestFolderNameChange = homeViewModel::onExtractRequestFolderNameChange,
        onExtractRequestToOtherPaneChange = homeViewModel::onExtractRequestToOtherPaneChange,
        onExtractRequestDeleteSourceChange = homeViewModel::onExtractRequestDeleteSourceChange,
        onExtractRequestBackgroundChange = homeViewModel::onExtractRequestBackgroundChange,
        onExtractRequestDismiss = homeViewModel::onExtractRequestDismiss,
        onExtractRequestConfirm = homeViewModel::onExtractRequestConfirm,
        onExtractPasswordChange = homeViewModel::onExtractPasswordChange,
        onExtractPasswordDismiss = homeViewModel::onExtractPasswordDismiss,
        onExtractPasswordConfirm = homeViewModel::onExtractPasswordConfirm,
        onOpenWithApplicationSelected = homeViewModel::onOpenWithApplicationSelected,
        onTrashRequestBackgroundChange = homeViewModel::onTrashRequestBackgroundChange,
        onTrashRequestDismiss = homeViewModel::onTrashRequestDismiss,
        onTrashRequestConfirm = homeViewModel::onTrashRequestConfirm,
        onPlaceMove = homeViewModel::onMovePlace,
        onMtpStorageClick = homeViewModel::onMtpStorageClick,
        onMtpShortcutMove = homeViewModel::onMoveMtpShortcut,
        onStartupPathChange = homeViewModel::onStartupPathChange,
        onRestoreWindowSizeChange = homeViewModel::onRestoreWindowSizeChange,
        onColorSchemeModeChange = homeViewModel::onColorSchemeModeChange,
        onLanguageChange = homeViewModel::onLanguageChange,
        onKeyColorChange = homeViewModel::onKeyColorChange,
        onInterfaceScaleChange = homeViewModel::onInterfaceScaleChange,
        onShowHiddenItemsChange = homeViewModel::onShowHiddenItemsChange,
        onClearCache = homeViewModel::onClearCache,
        onRefreshCacheSize = homeViewModel::refreshCacheSize,
        onFileLayoutModeChange = homeViewModel::onFileLayoutModeChange,
        onFileSortModeChange = homeViewModel::onFileSortModeChange,
        onBreadcrumbClick = homeViewModel::onBreadcrumbClick,
        onBreadcrumbTerminal = homeViewModel::onBreadcrumbTerminal,
        onRetry = homeViewModel::onRefresh,
        onLoadMore = homeViewModel::onLoadMore,
        onBack = homeViewModel::onBack,
        onForward = homeViewModel::onForward,
        onGoUp = homeViewModel::onGoUp,
        onCreate = homeViewModel::onCreate,
        onSync = homeViewModel::onSyncOtherPane,
        onDismissOperationError = homeViewModel::onDismissOperationError,
        shortcuts = remember(uiState.shortcuts) {
            ShortcutRegistry(
                uiState.shortcuts.flatMap { (action, keys) ->
                    keys.map { key -> ShortcutBinding(action, key) }
                }
            )
        },
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    tasks: List<AppTask>,
    onCancelTask: (Long) -> Unit,
    onDismissTask: (Long) -> Unit,
    onActiveTaskBackground: () -> Unit,
    onActiveTaskCancel: () -> Unit,
    onTaskProgressOpen: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectPrevious: () -> Boolean,
    onSelectNext: () -> Boolean,
    onSelectLeft: () -> Boolean,
    onSelectRight: () -> Boolean,
    onOpenContextMenu: () -> Boolean,
    onShortcutCopy: () -> Boolean,
    onShortcutMove: () -> Boolean,
    onShortcutDelete: () -> Boolean,
    onShortcutRename: () -> Boolean,
    onShortcutCompress: () -> Boolean,
    onShortcutTrash: () -> Boolean,
    onExtendSelectionUp: () -> Boolean,
    onExtendSelectionDown: () -> Boolean,
    onExtendSelectionLeft: () -> Boolean,
    onExtendSelectionRight: () -> Boolean,
    onInlineRenameStarted: (PaneId) -> Unit,
    onGridColumnsChanged: (PaneId, Int) -> Unit,
    onContextMenuRequest: (PaneId, String) -> Unit,
    onContextMenuDismiss: (PaneId) -> Unit,
    onShortcutAdd: (ShortcutAction, ShortcutKey) -> Unit,
    onShortcutRemove: (ShortcutAction, ShortcutKey) -> Unit,
    onResetShortcuts: () -> Unit,
    onSnackbarShown: () -> Unit,
    onSnackbarRetry: () -> Unit,
    onPlaceClick: (FilePlace) -> Unit,
    onPaneFocus: (PaneId) -> Unit,
    onEntryClick: (PaneId, FileEntry, SelectionMode) -> Unit,
    onEntryDoubleClick: (PaneId, FileEntry) -> Unit,
    onEntrySecondaryClick: (PaneId, FileEntry) -> Unit,
    onSelectionChange: (PaneId, Set<String>, String?) -> Unit,
    onSelectionClear: (PaneId) -> Unit,
    onEntryContextAction: (PaneId, FileEntry, FileContextAction) -> Unit,
    onInlineRename: (PaneId, FileEntry, String) -> Unit,
    onDismissFileInfo: (Long) -> Unit,
    onCopyRequestBackgroundChange: (Boolean) -> Unit,
    onCopyRequestDismiss: () -> Unit,
    onCopyRequestConfirm: () -> Unit,
    onMoveRequestBackgroundChange: (Boolean) -> Unit,
    onMoveRequestDismiss: () -> Unit,
    onMoveRequestConfirm: () -> Unit,
    onDeleteRequestBackgroundChange: (Boolean) -> Unit,
    onDeleteRequestDismiss: () -> Unit,
    onDeleteRequestConfirm: () -> Unit,
    onRenameRequestNameChange: (String) -> Unit,
    onRenameRequestBackgroundChange: (Boolean) -> Unit,
    onRenameRequestDismiss: () -> Unit,
    onRenameRequestConfirm: () -> Unit,
    onCompressRequestNameChange: (String) -> Unit,
    onCompressRequestFormatChange: (io.github.lumkit.dockhand.core.archive.ArchiveFormat) -> Unit,
    onCompressRequestLevelChange: (io.github.lumkit.dockhand.core.archive.CompressionLevel) -> Unit,
    onCompressRequestPasswordChange: (String) -> Unit,
    onCompressRequestSplitChange: (String) -> Unit,
    onCompressRequestSeparateChange: (Boolean) -> Unit,
    onCompressRequestDeleteSourceChange: (Boolean) -> Unit,
    onCompressRequestToOtherPaneChange: (Boolean) -> Unit,
    onCompressRequestBackgroundChange: (Boolean) -> Unit,
    onCompressRequestDismiss: () -> Unit,
    onCompressRequestConfirm: () -> Unit,
    onExtractRequestModeChange: (io.github.lumkit.dockhand.ui.screen.home.ExtractDestinationMode) -> Unit,
    onExtractRequestFolderNameChange: (String) -> Unit,
    onExtractRequestToOtherPaneChange: (Boolean) -> Unit,
    onExtractRequestDeleteSourceChange: (Boolean) -> Unit,
    onExtractRequestBackgroundChange: (Boolean) -> Unit,
    onExtractRequestDismiss: () -> Unit,
    onExtractRequestConfirm: () -> Unit,
    onExtractPasswordChange: (String) -> Unit,
    onExtractPasswordDismiss: () -> Unit,
    onExtractPasswordConfirm: () -> Unit,
    onOpenWithApplicationSelected: (FileEntry, io.github.lumkit.dockhand.core.platform.ExternalApplication) -> Unit,
    onTrashRequestBackgroundChange: (Boolean) -> Unit,
    onTrashRequestDismiss: () -> Unit,
    onTrashRequestConfirm: () -> Unit,
    onPlaceMove: (FilePlace, Int) -> Boolean,
    onMtpStorageClick: (FilePlace, Boolean) -> Unit,
    onMtpShortcutMove: (String, Int) -> Boolean,
    onStartupPathChange: (PaneId, String) -> Unit,
    onRestoreWindowSizeChange: (Boolean) -> Unit,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    onLanguageChange: (io.github.lumkit.dockhand.core.app.AppLanguage) -> Unit,
    onKeyColorChange: (Color) -> Unit,
    onInterfaceScaleChange: (Int) -> Unit,
    onShowHiddenItemsChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onFileLayoutModeChange: (FileLayoutMode) -> Unit,
    onFileSortModeChange: (FileSortMode) -> Unit,
    onBreadcrumbClick: (FileBreadcrumb) -> Unit,
    onBreadcrumbTerminal: (FileBreadcrumb, Boolean) -> Unit,
    onBreadcrumbSecondaryClick: (FileBreadcrumb) -> Unit = {},
    onRetry: (PaneId) -> Unit,
    onLoadMore: (PaneId) -> Unit,
    onBack: (PaneId) -> Unit,
    onForward: (PaneId) -> Unit,
    onGoUp: (PaneId) -> Unit,
    onCreate: (PaneId, String, Boolean) -> Unit,
    onSync: (PaneId) -> Unit,
    onDismissOperationError: () -> Unit,
    shortcuts: ShortcutRegistry = DefaultShortcutRegistry,
) {
    val windowState = LocalWindowState.current
    val windowPlacement = windowState.placement
    val direction = LocalLayoutDirection.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showProgressCancelConfirm by remember { mutableStateOf(false) }
    val inlineRenameBounds = remember { mutableStateOf<Rect?>(null) }
    val focusManager = LocalFocusManager.current
    val retryLabel = stringResource(Res.string.action_retry)
    val currentUiState by rememberUpdatedState(uiState)
    val currentSelectAll by rememberUpdatedState(onSelectAll)
    val currentClearSelection by rememberUpdatedState(onClearSelection)
    val currentSelectPrevious by rememberUpdatedState(onSelectPrevious)
    val currentSelectNext by rememberUpdatedState(onSelectNext)
    val currentSelectLeft by rememberUpdatedState(onSelectLeft)
    val currentSelectRight by rememberUpdatedState(onSelectRight)
    val currentOpenContextMenu by rememberUpdatedState(onOpenContextMenu)
    val currentShortcutCopy by rememberUpdatedState(onShortcutCopy)
    val currentShortcutMove by rememberUpdatedState(onShortcutMove)
    val currentShortcutDelete by rememberUpdatedState(onShortcutDelete)
    val currentShortcutRename by rememberUpdatedState(onShortcutRename)
    val currentShortcutCompress by rememberUpdatedState(onShortcutCompress)
    val currentShortcutTrash by rememberUpdatedState(onShortcutTrash)
    val currentExtendUp by rememberUpdatedState(onExtendSelectionUp)
    val currentExtendDown by rememberUpdatedState(onExtendSelectionDown)
    val currentExtendLeft by rememberUpdatedState(onExtendSelectionLeft)
    val currentExtendRight by rememberUpdatedState(onExtendSelectionRight)
    val currentShortcuts by rememberUpdatedState(shortcuts)
    val shortcutHandlerOwner = remember { Any() }
    DisposableEffect(shortcutHandlerOwner) {
        ShortcutDispatcher.setHandler(shortcutHandlerOwner) { event ->
            val modalOpen = inlineRenameBounds.value != null ||
                    currentUiState.activeTaskId != null ||
                    currentUiState.copyRequest != null ||
                    currentUiState.moveRequest != null ||
                    currentUiState.deleteRequest != null ||
                    currentUiState.renameRequest != null ||
                    currentUiState.compressRequest != null ||
                    currentUiState.extractRequest != null ||
                    currentUiState.extractPasswordRequest != null ||
                    currentUiState.openWith != null ||
                    currentUiState.trashRequest != null
            if (modalOpen) {
                false
            } else {
                when (currentShortcuts.resolve(event)) {
                    ShortcutAction.SelectAll -> {
                        currentSelectAll()
                        true
                    }

                    ShortcutAction.ClearSelection -> {
                        currentClearSelection()
                        true
                    }

                    ShortcutAction.SelectPrevious -> currentSelectPrevious()
                    ShortcutAction.SelectNext -> currentSelectNext()
                    ShortcutAction.SelectLeft -> currentSelectLeft()
                    ShortcutAction.SelectRight -> currentSelectRight()
                    ShortcutAction.OpenContextMenu -> currentOpenContextMenu()
                    ShortcutAction.Copy -> currentShortcutCopy()
                    ShortcutAction.Move -> currentShortcutMove()
                    ShortcutAction.Delete -> currentShortcutDelete()
                    ShortcutAction.Rename -> currentShortcutRename()
                    ShortcutAction.Compress -> currentShortcutCompress()
                    ShortcutAction.Trash -> currentShortcutTrash()
                    ShortcutAction.ExtendSelectionUp -> currentExtendUp()
                    ShortcutAction.ExtendSelectionDown -> currentExtendDown()
                    ShortcutAction.ExtendSelectionLeft -> currentExtendLeft()
                    ShortcutAction.ExtendSelectionRight -> currentExtendRight()
                    null -> false
                }
            }
        }
        onDispose { ShortcutDispatcher.clearHandler(shortcutHandlerOwner) }
    }

    LaunchedEffect(uiState.snackbarMessage, uiState.snackbarCanRetry) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (uiState.snackbarCanRetry) retryLabel else null,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onSnackbarRetry()
        }
        onSnackbarShown()
    }

    CompositionLocalProvider(
        LocalInlineRenameBounds provides inlineRenameBounds,
        LocalOpenWithState provides uiState.openWith,
        LocalOpenWithSelect provides onOpenWithApplicationSelected,
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(inlineRenameBounds) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                val bounds = inlineRenameBounds.value
                                if (
                                    bounds != null &&
                                    event.changes.any { !bounds.contains(it.position) }
                                ) {
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    }
                },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                MiuixCaptionBar {
                    if (windowPlacement != WindowPlacement.Fullscreen) {
                        HomeTopBar(
                            location = uiState.focusedLocation,
                            onBreadcrumbClick = onBreadcrumbClick,
                            onBreadcrumbSecondaryClick = onBreadcrumbSecondaryClick,
                            onBreadcrumbTerminal = onBreadcrumbTerminal,
                        ) {
                            TopBarActions(
                                tasks = tasks,
                                onCancelTask = onCancelTask,
                                onDismissTask = onDismissTask,
                                onTaskProgressOpen = onTaskProgressOpen,
                                layoutMode = uiState.fileLayoutMode,
                                sortMode = uiState.fileSortMode,
                                showHiddenItems = uiState.showHiddenItems,
                                onLayoutModeChange = onFileLayoutModeChange,
                                onSortModeChange = onFileSortModeChange,
                                onShowHiddenItemsChange = onShowHiddenItemsChange,
                            )
                        }
                    }
                }
            },
            containerColor = MiuixTheme.colorScheme.surface,
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
                        .width(280.dp),
                    color = MiuixTheme.colorScheme.surface,
                ) {
                    HomeDrawerContent(
                        places = uiState.places,
                        activeLocation = uiState.focusedLocation,
                        placeOrder = uiState.drawerPlaceOrder,
                        mtpExpandedSources = uiState.mtpExpandedSources,
                        mtpShortcutOrder = uiState.mtpShortcutOrder,
                        startupLeftPath = uiState.startupLeftPath,
                        startupRightPath = uiState.startupRightPath,
                        onStartupPathChange = onStartupPathChange,
                        restoreWindowSize = uiState.restoreWindowSize,
                        onRestoreWindowSizeChange = onRestoreWindowSizeChange,
                        colorSchemeMode = uiState.colorSchemeMode,
                        language = uiState.language,
                        onLanguageChange = onLanguageChange,
                        keyColor = uiState.keyColor,
                        interfaceScale = uiState.interfaceScale,
                        showHiddenItems = uiState.showHiddenItems,
                        shortcutBindings = uiState.shortcuts,
                        onShortcutAdd = onShortcutAdd,
                        onShortcutRemove = onShortcutRemove,
                        onResetShortcuts = onResetShortcuts,
                        onColorSchemeModeChange = onColorSchemeModeChange,
                        onKeyColorChange = onKeyColorChange,
                        onInterfaceScaleChange = onInterfaceScaleChange,
                        onShowHiddenItemsChange = onShowHiddenItemsChange,
                        cacheBreakdown = uiState.cacheBreakdown,
                        onClearCache = onClearCache,
                        onRefreshCacheSize = onRefreshCacheSize,
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
                                onBreadcrumbSecondaryClick = onBreadcrumbSecondaryClick,
                                onBreadcrumbTerminal = onBreadcrumbTerminal,
                            ) {
                                TopBarActions(
                                    tasks = tasks,
                                    onCancelTask = onCancelTask,
                                    onDismissTask = onDismissTask,
                                    onTaskProgressOpen = onTaskProgressOpen,
                                    layoutMode = uiState.fileLayoutMode,
                                    sortMode = uiState.fileSortMode,
                                    showHiddenItems = uiState.showHiddenItems,
                                    onLayoutModeChange = onFileLayoutModeChange,
                                    onSortModeChange = onFileSortModeChange,
                                    onShowHiddenItemsChange = onShowHiddenItemsChange,
                                )
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
                                onSelectionChange = onSelectionChange,
                                onSelectionClear = onSelectionClear,
                                onContextMenuRequest = onContextMenuRequest,
                                onContextMenuDismiss = onContextMenuDismiss,
                                onInlineRenameStarted = onInlineRenameStarted,
                                onGridColumnsChanged = onGridColumnsChanged,
                                onEntryContextAction = onEntryContextAction,
                                onInlineRename = onInlineRename,
                                onRetry = onRetry,
                                onLoadMore = onLoadMore,
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

                CopyRequestDialog(
                    request = uiState.copyRequest,
                    onBackgroundChange = onCopyRequestBackgroundChange,
                    onDismiss = onCopyRequestDismiss,
                    onConfirm = onCopyRequestConfirm,
                )

                MoveRequestDialog(
                    request = uiState.moveRequest,
                    onBackgroundChange = onMoveRequestBackgroundChange,
                    onDismiss = onMoveRequestDismiss,
                    onConfirm = onMoveRequestConfirm,
                )

                DeleteRequestDialog(
                    request = uiState.deleteRequest,
                    onBackgroundChange = onDeleteRequestBackgroundChange,
                    onDismiss = onDeleteRequestDismiss,
                    onConfirm = onDeleteRequestConfirm,
                )

                RenameRequestDialog(
                    request = uiState.renameRequest,
                    onNameChange = onRenameRequestNameChange,
                    onBackgroundChange = onRenameRequestBackgroundChange,
                    onDismiss = onRenameRequestDismiss,
                    onConfirm = onRenameRequestConfirm,
                )

                ExtractRequestDialog(
                    request = uiState.extractRequest,
                    onModeChange = onExtractRequestModeChange,
                    onFolderNameChange = onExtractRequestFolderNameChange,
                    onToOtherPaneChange = onExtractRequestToOtherPaneChange,
                    onDeleteSourceChange = onExtractRequestDeleteSourceChange,
                    onBackgroundChange = onExtractRequestBackgroundChange,
                    onDismissRequest = onExtractRequestDismiss,
                    onConfirm = onExtractRequestConfirm,
                )

                ExtractPasswordDialog(
                    request = uiState.extractPasswordRequest,
                    onPasswordChange = onExtractPasswordChange,
                    onDismissRequest = onExtractPasswordDismiss,
                    onConfirm = onExtractPasswordConfirm,
                )

                CompressRequestDialog(
                    request = uiState.compressRequest,
                    onNameChange = onCompressRequestNameChange,
                    onFormatChange = onCompressRequestFormatChange,
                    onLevelChange = onCompressRequestLevelChange,
                    onPasswordChange = onCompressRequestPasswordChange,
                    onSplitChange = onCompressRequestSplitChange,
                    onSeparateChange = onCompressRequestSeparateChange,
                    onDeleteSourceChange = onCompressRequestDeleteSourceChange,
                    onToOtherPaneChange = onCompressRequestToOtherPaneChange,
                    onBackgroundChange = onCompressRequestBackgroundChange,
                    onDismissRequest = onCompressRequestDismiss,
                    onConfirm = onCompressRequestConfirm,
                )

                TrashRequestDialog(
                    request = uiState.trashRequest,
                    onBackgroundChange = onTrashRequestBackgroundChange,
                    onDismiss = onTrashRequestDismiss,
                    onConfirm = onTrashRequestConfirm,
                )

                TaskProgressDialog(
                    task = uiState.activeTaskId?.let { id -> tasks.firstOrNull { it.id == id } },
                    onBackground = onActiveTaskBackground,
                    onCancel = { showProgressCancelConfirm = true },
                )

                val activeTask = uiState.activeTaskId?.let { id ->
                    tasks.firstOrNull { it.id == id }
                }
                LaunchedEffect(uiState.activeTaskId) {
                    if (uiState.activeTaskId == null) {
                        showProgressCancelConfirm = false
                    }
                }
                OverlayDialog(
                    show = showProgressCancelConfirm && activeTask != null,
                    title = stringResource(Res.string.task_cancel_title),
                    summary = activeTask?.let {
                        stringResource(Res.string.task_cancel_message, it.title)
                    },
                    onDismissRequest = { showProgressCancelConfirm = false },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            text = stringResource(Res.string.action_confirm),
                            onClick = {
                                showProgressCancelConfirm = false
                                onActiveTaskCancel()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            text = stringResource(Res.string.action_cancel),
                            onClick = { showProgressCancelConfirm = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    uiState.fileInfoDialogs.forEachIndexed { index, dialog ->
        key(dialog.id) {
            FileInfoDialogWindow(
                title = stringResource(Res.string.file_info_title),
                width = 300.dp,
                height = 800.dp,
                order = index,
                scale = uiState.interfaceScale / 100f,
                onCloseRequest = { onDismissFileInfo(dialog.id) },
            ) {
                FileInfoDialogContent(
                    state = dialog,
                    onClose = { onDismissFileInfo(dialog.id) },
                )
            }
        }
    }
}

@Composable
private fun sortMenuItems(
    selected: FileSortMode,
    onSelected: (FileSortMode) -> Unit,
): List<DropdownItem> = listOf(
    FileSortMode.NameAsc to Res.string.menu_sort_name_asc,
    FileSortMode.NameDesc to Res.string.menu_sort_name_desc,
    FileSortMode.ModifiedDesc to Res.string.menu_sort_modified_desc,
    FileSortMode.ModifiedAsc to Res.string.menu_sort_modified_asc,
    FileSortMode.SizeDesc to Res.string.menu_sort_size_desc,
    FileSortMode.SizeAsc to Res.string.menu_sort_size_asc,
    FileSortMode.TypeAsc to Res.string.menu_sort_type_asc,
    FileSortMode.TypeDesc to Res.string.menu_sort_type_desc,
).map { (mode, label) ->
    DropdownItem(
        text = stringResource(label),
        selected = selected == mode,
        onClick = { onSelected(mode) },
    )
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun TopBarActions(
    tasks: List<AppTask>,
    onCancelTask: (Long) -> Unit,
    onDismissTask: (Long) -> Unit,
    onTaskProgressOpen: (Long) -> Unit,
    layoutMode: FileLayoutMode,
    sortMode: FileSortMode,
    showHiddenItems: Boolean,
    onLayoutModeChange: (FileLayoutMode) -> Unit,
    onSortModeChange: (FileSortMode) -> Unit,
    onShowHiddenItemsChange: (Boolean) -> Unit,
) {
    var pendingCancel by remember { mutableStateOf<AppTask?>(null) }
    var popupShow by remember { mutableStateOf(false) }
    val runningBackgroundTasks = tasks.count {
        it.isBackground && (it.state == TaskState.Running || it.state == TaskState.Waiting)
    }

    Box {
        TooltipBox(
            text = stringResource(Res.string.tasks_tooltip_title)
        ) {
            BadgedBox(
                badge = {
                    if (runningBackgroundTasks > 0) {
                        Badge {
                            Text(
                                text = if (runningBackgroundTasks > 99) {
                                    stringResource(Res.string.tasks_badge_overflow)
                                } else {
                                    runningBackgroundTasks.toString()
                                },
                                style = MiuixTheme.textStyles.footnote2,
                            )
                        }
                    }
                },
            ) {
                IconButton(
                    onClick = { popupShow = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_backend_task),
                        contentDescription = stringResource(Res.string.tasks_tooltip_title),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        OverlayListPopup(
            show = popupShow,
            alignment = PopupPositionProvider.Align.Start,
            onDismissRequest = { popupShow = false } // 关闭弹窗菜单
        ) {
            Card(
                modifier = Modifier.width(300.dp)
            ) {
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.tasks_empty),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .width(300.dp)
                            .heightIn(max = 320.dp)
                            .verticalScrollBar(listState),
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                onOpen = {
                                    popupShow = false
                                    onTaskProgressOpen(task.id)
                                },
                                onClose = {
                                    if (task.state == TaskState.Running || task.state == TaskState.Waiting) {
                                        pendingCancel = task
                                    } else {
                                        onDismissTask(task.id)
                                    }
                                    popupShow = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // TODO 更多菜单，多级菜单实现
    var moreMenuShow by remember { mutableStateOf(false) }
    TooltipBox(text = stringResource(Res.string.text_more)) {
        IconButton(
            onClick = { moreMenuShow = true },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = stringResource(Res.string.text_more),
                modifier = Modifier.size(20.dp),
            )
        }

        OverlayCascadingListPopup(
            show = moreMenuShow,
            entries = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = stringResource(Res.string.menu_arrangement),
                            children = listOf(
                                DropdownItem(
                                    text = stringResource(Res.string.menu_layout_list),
                                    selected = layoutMode == FileLayoutMode.List,
                                    onClick = {
                                        onLayoutModeChange(FileLayoutMode.List)
                                        moreMenuShow = false
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(Res.string.menu_layout_grid),
                                    selected = layoutMode == FileLayoutMode.Grid,
                                    onClick = {
                                        onLayoutModeChange(FileLayoutMode.Grid)
                                        moreMenuShow = false
                                    },
                                ),
                            ),
                        ),
                        DropdownItem(
                            text = stringResource(Res.string.menu_sort),
                            children = sortMenuItems(
                                selected = sortMode,
                                onSelected = {
                                    onSortModeChange(it)
                                    moreMenuShow = false
                                },
                            ),
                        ),
                        DropdownItem(
                            text = stringResource(Res.string.menu_view),
                            children = listOf(
                                DropdownItem(
                                    text = stringResource(Res.string.menu_hidden_items),
                                    selected = showHiddenItems,
                                    onClick = {
                                        onShowHiddenItemsChange(!showHiddenItems)
                                        moreMenuShow = false
                                    },
                                ),
                            ),
                        ),
                    )
                )
            ),
            onDismissRequest = { moreMenuShow = false },
            alignment = PopupPositionProvider.Align.End,
        )
    }

    val taskToCancel = pendingCancel
    OverlayDialog(
        show = taskToCancel != null,
        title = stringResource(Res.string.task_cancel_title),
        summary = taskToCancel?.let {
            stringResource(Res.string.task_cancel_message, it.title)
        },
        onDismissRequest = { pendingCancel = null },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(Res.string.action_confirm),
                onClick = {
                    taskToCancel?.let { onCancelTask(it.id) }
                    pendingCancel = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            TextButton(
                text = stringResource(Res.string.action_cancel),
                onClick = { pendingCancel = null },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun formatEta(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return when {
        value <= 0 -> stringResource(Res.string.task_eta_less_than_second)
        value < 60 -> stringResource(Res.string.task_eta_seconds, value)
        value < 3600 -> stringResource(
            Res.string.task_eta_minutes_seconds,
            value / 60,
            value % 60,
        )

        value < 86_400 -> stringResource(
            Res.string.task_eta_hours_minutes,
            value / 3600,
            (value % 3600) / 60,
        )

        else -> stringResource(
            Res.string.task_eta_days_hours,
            value / 86_400,
            (value % 86_400) / 3600,
        )
    }
}

@Composable
private fun TaskRow(
    task: AppTask,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val status = when (task.state) {
        TaskState.Waiting -> stringResource(Res.string.task_waiting)
        TaskState.Running -> stringResource(Res.string.task_running)
        TaskState.Completed -> stringResource(Res.string.task_completed)
        TaskState.Failed -> stringResource(Res.string.task_failed)
        TaskState.Cancelled -> stringResource(Res.string.task_cancelled)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = task.title,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = task.error ?: task.detail ?: status,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (task.state == TaskState.Running) {
                val fraction = task.progress.fraction
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth(),
                )

                val speed = task.speedBytesPerSecond
                if (speed != null) {
                    val speedText = formatFileSize(speed.toLong())
                    Text(
                        text = task.etaSeconds?.let { eta ->
                            stringResource(
                                Res.string.task_speed_eta,
                                speedText,
                                formatEta(eta),
                            )
                        } ?: stringResource(Res.string.task_speed, speedText),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else if (task.state == TaskState.Waiting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                Text(
                    text = task.waitingMessage ?: stringResource(Res.string.task_waiting),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Close,
                contentDescription = stringResource(Res.string.task_cancel_title),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}