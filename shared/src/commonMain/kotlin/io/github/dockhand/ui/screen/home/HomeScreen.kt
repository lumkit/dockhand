package io.github.dockhand.ui.screen.home

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dockhand.LocalAppContainer
import io.github.dockhand.domain.model.FileBreadcrumb
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.ui.component.FileContextAction
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.ui.component.DualPaneBrowser
import io.github.dockhand.task.AppTask
import io.github.dockhand.task.TaskState
import io.github.dockhand.ui.component.CopyRequestDialog
import io.github.dockhand.ui.component.FileInfoDialogContent
import io.github.dockhand.ui.component.FileInfoDialogWindow
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.file_info_title
import dockhand.shared.generated.resources.ic_backend_task
import dockhand.shared.generated.resources.ic_home
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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
        )
    }
    val uiState by homeViewModel.state.collectAsState()
    val tasks by homeViewModel.tasks.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        tasks = tasks,
        onCancelTask = homeViewModel::onCancelTask,
        onDismissTask = homeViewModel::onDismissTask,
        onSnackbarShown = homeViewModel::onSnackbarShown,
        onPlaceClick = homeViewModel::onPlaceClick,
        onPaneFocus = homeViewModel::onPaneFocus,
        onEntryClick = homeViewModel::onEntryClick,
        onEntryDoubleClick = homeViewModel::onEntryDoubleClick,
        onEntrySecondaryClick = homeViewModel::onEntrySecondaryClick,
        onEntryContextAction = homeViewModel::onEntryContextAction,
        onDismissFileInfo = homeViewModel::onDismissFileInfo,
        onCopyRequestBackgroundChange = homeViewModel::onCopyRequestBackgroundChange,
        onCopyRequestDismiss = homeViewModel::onCopyRequestDismiss,
        onCopyRequestConfirm = homeViewModel::onCopyRequestConfirm,
        onPlaceMove = homeViewModel::onMovePlace,
        onMtpStorageClick = homeViewModel::onMtpStorageClick,
        onMtpShortcutMove = homeViewModel::onMoveMtpShortcut,
        onStartupPathChange = homeViewModel::onStartupPathChange,
        onRestoreWindowSizeChange = homeViewModel::onRestoreWindowSizeChange,
        onColorSchemeModeChange = homeViewModel::onColorSchemeModeChange,
        onKeyColorChange = homeViewModel::onKeyColorChange,
        onInterfaceScaleChange = homeViewModel::onInterfaceScaleChange,
        onBreadcrumbClick = homeViewModel::onBreadcrumbClick,
        onRetry = homeViewModel::onRefresh,
        onLoadMore = homeViewModel::onLoadMore,
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
    tasks: List<AppTask>,
    onCancelTask: (Long) -> Unit,
    onDismissTask: (Long) -> Unit,
    onSnackbarShown: () -> Unit,
    onPlaceClick: (FilePlace) -> Unit,
    onPaneFocus: (PaneId) -> Unit,
    onEntryClick: (PaneId, FileEntry) -> Unit,
    onEntryDoubleClick: (PaneId, FileEntry) -> Unit,
    onEntrySecondaryClick: (PaneId, FileEntry) -> Unit,
    onEntryContextAction: (PaneId, FileEntry, FileContextAction) -> Unit,
    onDismissFileInfo: (Long) -> Unit,
    onCopyRequestBackgroundChange: (Boolean) -> Unit,
    onCopyRequestDismiss: () -> Unit,
    onCopyRequestConfirm: () -> Unit,
    onPlaceMove: (FilePlace, Int) -> Boolean,
    onMtpStorageClick: (FilePlace, Boolean) -> Unit,
    onMtpShortcutMove: (String, Int) -> Boolean,
    onStartupPathChange: (PaneId, String) -> Unit,
    onRestoreWindowSizeChange: (Boolean) -> Unit,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    onKeyColorChange: (Color) -> Unit,
    onInterfaceScaleChange: (Int) -> Unit,
    onBreadcrumbClick: (FileBreadcrumb) -> Unit,
    onBreadcrumbSecondaryClick: (FileBreadcrumb) -> Unit = {},
    onRetry: (PaneId) -> Unit,
    onLoadMore: (PaneId) -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSnackbarShown()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MiuixCaptionBar {
                if (windowPlacement != WindowPlacement.Fullscreen) {
                    HomeTopBar(
                        location = uiState.focusedLocation,
                        onBreadcrumbClick = onBreadcrumbClick,
                        onBreadcrumbSecondaryClick = onBreadcrumbSecondaryClick,
                    ) {
                        TopBarActions(
                            tasks = tasks,
                            onCancelTask = onCancelTask,
                            onDismissTask = onDismissTask,
                        )
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
                    startupLeftPath = uiState.startupLeftPath,
                    startupRightPath = uiState.startupRightPath,
                    onStartupPathChange = onStartupPathChange,
                    restoreWindowSize = uiState.restoreWindowSize,
                    onRestoreWindowSizeChange = onRestoreWindowSizeChange,
                    colorSchemeMode = uiState.colorSchemeMode,
                    keyColor = uiState.keyColor,
                    interfaceScale = uiState.interfaceScale,
                    onColorSchemeModeChange = onColorSchemeModeChange,
                    onKeyColorChange = onKeyColorChange,
                    onInterfaceScaleChange = onInterfaceScaleChange,
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
                        ) {
                            TopBarActions(
                                tasks = tasks,
                                onCancelTask = onCancelTask,
                                onDismissTask = onDismissTask,
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
                            onEntryContextAction = onEntryContextAction,
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
private fun TopBarActions(
    tasks: List<AppTask>,
    onCancelTask: (Long) -> Unit,
    onDismissTask: (Long) -> Unit,
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
    
//    IconButton(
//        onClick = { },
//        modifier = Modifier.size(32.dp),
//    ) {
//        Icon(
//            imageVector = MiuixIcons.More,
//            contentDescription = null,
//            modifier = Modifier.size(20.dp),
//        )
//    }

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
            .clickable {}
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