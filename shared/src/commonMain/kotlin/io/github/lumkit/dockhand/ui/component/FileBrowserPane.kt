package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_compress
import dockhand.shared.generated.resources.action_copy_to_left
import dockhand.shared.generated.resources.action_copy_to_right
import dockhand.shared.generated.resources.action_delete
import dockhand.shared.generated.resources.action_move_to_left
import dockhand.shared.generated.resources.action_move_to_right
import dockhand.shared.generated.resources.action_move_to_trash
import dockhand.shared.generated.resources.action_rename
import dockhand.shared.generated.resources.action_services
import dockhand.shared.generated.resources.action_terminal_tab_file
import dockhand.shared.generated.resources.action_terminal_tab_folder
import dockhand.shared.generated.resources.action_terminal_window_file
import dockhand.shared.generated.resources.action_terminal_window_folder
import dockhand.shared.generated.resources.action_view_info
import dockhand.shared.generated.resources.text_empty_folder
import dockhand.shared.generated.resources.text_loading_more
import dockhand.shared.generated.resources.text_read_failed
import dockhand.shared.generated.resources.text_retry
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.ui.screen.home.FilePaneState
import io.github.lumkit.dockhand.ui.screen.home.FileRowUi
import io.github.lumkit.dockhand.ui.screen.home.PaneId
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.window.MiuixDialogWindow

private const val INACTIVE_PANE_ALPHA = 0.55f

/**
 * 一个文件浏览面板。
 *
 * 焦点通过两层表达：
 *  - 失焦面板整体降低透明度；
 *  - 失焦面板盖一层 innerShadow，明确“当前输入只作用于另一侧”。
 *
 * 鼠标进入面板不再切换焦点；单击面板或条目才设置为焦点列表。
 * 双击目录进入目录；双击文件的打开动作和右键操作窗口后续接入。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun FileBrowserPane(
    pane: FilePaneState,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryDoubleClick: (FileEntry) -> Unit,
    onEntrySecondaryClick: (FileEntry) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    canTransferToOtherPane: Boolean = false,
    canMoveToOtherPane: Boolean = false,
    onContextAction: (FileEntry, FileContextAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    overlayShape: Shape = RoundedCornerShape(0.dp),
) {
    val currentOnFocus by rememberUpdatedState(onFocus)
    var contextMenuEntryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pane.location) {
        contextMenuEntryId = null
    }

    Box(
        modifier = modifier
            // 初始 pass 只观察、不消费，滚动条拖动也会先切到当前面板。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            currentOnFocus()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnFocus() })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isFocused) 1f else INACTIVE_PANE_ALPHA)
        ) {
            AnimatedVisibility(visible = pane.isLoading && pane.entries.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    pane.isLoading && pane.entries.isEmpty() -> {
                        PaneStateBox {
                            InfiniteProgressIndicator()
                        }
                    }

                    pane.errorMessage != null -> {
                        PaneStateBox {
                            ErrorState(
                                message = pane.errorMessage,
                                onRetry = onRetry,
                            )
                        }
                    }

                    pane.isEmpty -> {
                        PaneStateBox {
                            EmptyState()
                        }
                    }

                    else -> {
                        val listState = rememberLazyListState()
                        val currentHasMore by rememberUpdatedState(pane.hasMore)
                        val currentLoadingMore by rememberUpdatedState(pane.isLoadingMore)
                        val currentLoadMore by rememberUpdatedState(onLoadMore)

                        LaunchedEffect(listState) {
                            snapshotFlow {
                                val layoutInfo = listState.layoutInfo
                                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                val total = layoutInfo.totalItemsCount
                                total > 0 && lastVisible >= total - 3
                            }
                                .distinctUntilChanged()
                                .collect { nearEnd ->
                                    if (nearEnd && currentHasMore && !currentLoadingMore) {
                                        currentLoadMore()
                                    }
                                }
                        }

                        LaunchedEffect(pane.location) {
                            listState.scrollToItem(0)
                        }

                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress }
                                .distinctUntilChanged()
                                .collect { scrolling ->
                                    if (scrolling) contextMenuEntryId = null
                                }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollBar(listState),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            items(pane.entries, key = { it.id }) { row ->
                                FileEntryRow(
                                    row = row,
                                    paneId = pane.id,
                                    selected = row.id in pane.selectedIds,
                                    canTransferToOtherPane = canTransferToOtherPane,
                                    canMoveToOtherPane = canMoveToOtherPane,
                                    showContextMenu = contextMenuEntryId == row.id,
                                    onClick = { onEntryClick(row.entry) },
                                    onDoubleClick = { onEntryDoubleClick(row.entry) },
                                    onSecondaryClick = {
                                        onEntrySecondaryClick(row.entry)
                                        contextMenuEntryId = row.id
                                    },
                                    onDismissContextMenu = { contextMenuEntryId = null },
                                    onContextAction = { action ->
                                        onContextAction(row.entry, action)
                                    },
                                )
                            }

                            if (pane.isLoadingMore) {
                                item(key = "load-more") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        InfiniteProgressIndicator(size = 20.dp)
                                        Text(
                                            text = stringResource(Res.string.text_loading_more),
                                            modifier = Modifier.padding(start = 8.dp),
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .innerShadow(
                        shape = overlayShape,
                        shadow = Shadow(
                            radius = 4.dp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    )
            )
        }
    }
}

@Composable
private fun FileEntryRow(
    row: FileRowUi,
    paneId: PaneId,
    selected: Boolean,
    canTransferToOtherPane: Boolean,
    canMoveToOtherPane: Boolean,
    showContextMenu: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onContextAction: (FileContextAction) -> Unit,
) {
    val rowColor = if (selected) {
        MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        Color.Transparent
    }

    val isLocal = row.entry.location.source.kind == FileSourceKind.Local
    val infoLabel = stringResource(Res.string.action_view_info)
    val copyLabel = stringResource(
        if (paneId == PaneId.Left) Res.string.action_copy_to_right else Res.string.action_copy_to_left
    )
    val moveLabel = stringResource(
        if (paneId == PaneId.Left) Res.string.action_move_to_right else Res.string.action_move_to_left
    )
    val trashLabel = stringResource(Res.string.action_move_to_trash)
    val deleteLabel = stringResource(Res.string.action_delete)
    val renameLabel = stringResource(Res.string.action_rename)
    val compressLabel = stringResource(Res.string.action_compress)
    val servicesLabel = stringResource(Res.string.action_services)
    val terminalTabLabel = stringResource(
        if (row.isDirectory) Res.string.action_terminal_tab_folder else Res.string.action_terminal_tab_file
    )
    val terminalWindowLabel = stringResource(
        if (row.isDirectory) Res.string.action_terminal_window_folder else Res.string.action_terminal_window_file
    )

    fun dispatch(action: FileContextAction) {
        onContextAction(action)
        onDismissContextMenu()
    }

    val entries = buildList {
        add(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = infoLabel,
                        onClick = { dispatch(FileContextAction.ViewInfo) },
                    )
                )
            )
        )

        if (isLocal) {
            val fileActions = buildList {
                if (canTransferToOtherPane) {
                    add(
                        DropdownItem(
                            text = copyLabel,
                            onClick = { dispatch(FileContextAction.Copy) },
                        )
                    )
                    if (canMoveToOtherPane) {
                        add(
                            DropdownItem(
                                text = moveLabel,
                                onClick = { dispatch(FileContextAction.Move) },
                            )
                        )
                    }
                }
                add(DropdownItem(text = trashLabel, onClick = { dispatch(FileContextAction.Trash) }))
                add(DropdownItem(text = deleteLabel, onClick = { dispatch(FileContextAction.Delete) }))
                add(DropdownItem(text = renameLabel, onClick = { dispatch(FileContextAction.Rename) }))
                add(DropdownItem(text = compressLabel, onClick = { dispatch(FileContextAction.Compress) }))
            }
            add(DropdownEntry(items = fileActions))
            add(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = servicesLabel,
                            children = listOf(
                                DropdownItem(
                                    text = terminalTabLabel,
                                    onClick = { dispatch(FileContextAction.TerminalTab) },
                                ),
                                DropdownItem(
                                    text = terminalWindowLabel,
                                    onClick = { dispatch(FileContextAction.TerminalWindow) },
                                ),
                            ),
                        )
                    )
                )
            )
        } else if (canTransferToOtherPane) {
            add(
                DropdownEntry(
                    items = buildList {
                        add(
                            DropdownItem(
                                text = copyLabel,
                                onClick = { dispatch(FileContextAction.Copy) },
                            )
                        )
                        if (canMoveToOtherPane) {
                            add(
                                DropdownItem(
                                    text = moveLabel,
                                    onClick = { dispatch(FileContextAction.Move) },
                                )
                            )
                        }
                        add(
                            DropdownItem(
                                text = deleteLabel,
                                onClick = { dispatch(FileContextAction.Delete) },
                            )
                        )
                    }
                )
            )
        }
    }

    Box {
        BasicComponent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .squircleClip(10.dp)
                .background(rowColor)
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = onDoubleClick,
                )
                .pointerInput(onSecondaryClick) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (
                                event.type == PointerEventType.Press &&
                                event.buttons.isSecondaryPressed
                            ) {
                                event.changes.forEach { it.consume() }
                                onSecondaryClick()
                            }
                        }
                    }
                },
            startAction = {
                Icon(
                    imageVector = if (row.isDirectory) MiuixIcons.Folder else MiuixIcons.File,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (row.isDirectory) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            },
            title = row.name,
            // summary 在列表加载时已经格式化好，LazyColumn 里只读字符串。
            summary = row.summary,
            // 单击/双击/右键都由上面的 modifier 处理。
            onClick = null,
        )

        OverlayCascadingListPopup(
            show = showContextMenu,
            entries = entries,
            onDismissRequest = onDismissContextMenu,
            alignment = when (paneId) {
                PaneId.Left -> PopupPositionProvider.Align.End
                PaneId.Right -> PopupPositionProvider.Align.Start
            },
        )
    }
}

@Composable
private fun PaneStateBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.Folder,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(Res.string.text_empty_folder),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.text_read_failed),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        TooltipBox(
            text = stringResource(Res.string.text_retry)
        ) {
            IconButton(
                onClick = onRetry,
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}