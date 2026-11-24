package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
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
import dockhand.shared.generated.resources.action_restore
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
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
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.window.MiuixDialogWindow
import kotlin.math.max
import kotlin.math.min

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
    onEntryClick: (FileEntry, SelectionMode) -> Unit,
    onEntryDoubleClick: (FileEntry) -> Unit,
    onEntrySecondaryClick: (FileEntry) -> Unit,
    onSelectionChange: (Set<String>, String?) -> Unit = { _, _ -> },
    onSelectionClear: () -> Unit = {},
    onContextMenuRequest: (String) -> Unit = {},
    onContextMenuDismiss: () -> Unit = {},
    onInlineRenameStarted: () -> Unit = {},
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    canTransferToOtherPane: Boolean = false,
    canMoveToOtherPane: Boolean = false,
    canTrash: Boolean = true,
    restorableTrashPaths: Set<String> = emptySet(),
    onInlineRename: (FileEntry, String) -> Unit = { _, _ -> },
    onContextAction: (FileEntry, FileContextAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    overlayShape: Shape = RoundedCornerShape(0.dp),
) {
    val currentOnFocus by rememberUpdatedState(onFocus)
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentEntries by rememberUpdatedState(pane.entries)
    val listState = rememberLazyListState()
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var dragPointerLocal by remember { mutableStateOf<Offset?>(null) }
    var dragAnchorIndex by remember { mutableStateOf<Int?>(null) }

    fun rowIndexAt(localPoint: Offset): Int? {
        if (currentEntries.isEmpty()) return null
        val visible = listState.layoutInfo.visibleItemsInfo
            .filter { it.index in currentEntries.indices }
        if (visible.isEmpty()) return null
        val hit = visible.firstOrNull { info ->
            localPoint.y >= info.offset && localPoint.y <= info.offset + info.size
        }
        if (hit != null) return hit.index
        return visible.minByOrNull { info ->
            kotlin.math.abs(localPoint.y - (info.offset + info.size / 2f))
        }?.index
    }

    fun updateMarqueeSelection() {
        val pointer = dragPointerLocal ?: return
        val anchor = dragAnchorIndex ?: rowIndexAt(pointer)?.also {
            dragAnchorIndex = it
        } ?: return
        val current = rowIndexAt(pointer) ?: return
        val from = min(anchor, current)
        val to = max(anchor, current)
        currentOnSelectionChange(
            currentEntries.subList(from, to + 1).map { it.id }.toSet(),
            currentEntries.getOrNull(anchor)?.id,
        )
    }

    val isDragging = dragStart != null && dragEnd != null
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (true) {
            updateMarqueeSelection()
            withFrameNanos { }
        }
    }

    LaunchedEffect(pane.location) {
        onContextMenuDismiss()
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
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        currentOnFocus()
                        onSelectionClear()
                    }
                )
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { listCoordinates = it }
                    .pointerInput(pane.id) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitPointerEventScope {
                            while (true) {
                                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                if (
                                    downEvent.type != PointerEventType.Press ||
                                    !downEvent.buttons.isPrimaryPressed
                                ) {
                                    continue
                                }
                                val down = downEvent.changes.firstOrNull() ?: continue
                                val coordinates = listCoordinates
                                val start = coordinates?.localToRoot(down.position) ?: down.position
                                var dragging = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (event.type == PointerEventType.Scroll) {
                                        updateMarqueeSelection()
                                        continue
                                    }
                                    val point = coordinates?.localToRoot(change.position) ?: change.position
                                    if (!dragging && (point - start).getDistance() > touchSlop) {
                                        dragging = true
                                        dragAnchorIndex = rowIndexAt(down.position)
                                        dragStart = start
                                    }
                                    if (dragging) {
                                        dragEnd = point
                                        dragPointerLocal = change.position
                                        updateMarqueeSelection()
                                        if (event.type != PointerEventType.Scroll) {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                    if (!change.pressed) break
                                }
                                dragStart = null
                                dragEnd = null
                                dragPointerLocal = null
                                dragAnchorIndex = null
                            }
                        }
                    }
            ) {
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
                        val edgeScrollThreshold = with(LocalDensity.current) { 36.dp.toPx() }
                        val edgeScrollMaxStep = with(LocalDensity.current) { 28.dp.toPx() }
                        LaunchedEffect(isDragging) {
                            if (!isDragging) return@LaunchedEffect
                            while (true) {
                                val pointer = dragPointerLocal
                                val viewportHeight = listCoordinates?.size?.height
                                if (pointer != null && viewportHeight != null) {
                                    val y = pointer.y
                                    val delta = when {
                                        y < edgeScrollThreshold -> {
                                            val intensity = ((edgeScrollThreshold - y) / edgeScrollThreshold)
                                                .coerceIn(0f, 1f)
                                            -edgeScrollMaxStep * intensity
                                        }
                                        y > viewportHeight - edgeScrollThreshold -> {
                                            val intensity = (
                                                (y - (viewportHeight - edgeScrollThreshold)) /
                                                    edgeScrollThreshold
                                                ).coerceIn(0f, 1f)
                                            edgeScrollMaxStep * intensity
                                        }
                                        else -> 0f
                                    }
                                    if (delta != 0f) {
                                        listState.scrollBy(delta)
                                    }
                                }
                                withFrameNanos { }
                            }
                        }
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

                        LaunchedEffect(pane.selectedIds) {
                            if (pane.selectedIds.size != 1) return@LaunchedEffect
                            val index = pane.entries.indexOfFirst { it.id in pane.selectedIds }
                            if (index < 0) return@LaunchedEffect
                            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
                            if (!visible) {
                                listState.animateScrollToItem(index)
                            }
                        }

                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress }
                                .distinctUntilChanged()
                                .collect { scrolling ->
                                    if (scrolling) onContextMenuDismiss()
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
                                    canTrash = canTrash,
                                    canRestore = row.entry.location.path in restorableTrashPaths,
                                    onInlineRename = { name -> onInlineRename(row.entry, name) },
                                    startInlineRename = pane.inlineRenameEntryId == row.id,
                                    onInlineRenameStarted = onInlineRenameStarted,
                                    showContextMenu = pane.contextMenuEntryId == row.id,
                                    onClick = {
                                        val modifiers = windowInfo.keyboardModifiers
                                        val mode = when {
                                            modifiers.isShiftPressed -> SelectionMode.Range
                                            modifiers.isAltPressed || modifiers.isMetaPressed ->
                                                SelectionMode.Toggle
                                            else -> SelectionMode.Replace
                                        }
                                        onEntryClick(row.entry, mode)
                                    },
                                    onDoubleClick = { onEntryDoubleClick(row.entry) },
                                    onSecondaryClick = {
                                        onEntrySecondaryClick(row.entry)
                                        onContextMenuRequest(row.id)
                                    },
                                    onDismissContextMenu = onContextMenuDismiss,
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
                                            style = MiuixTheme.textStyles.body1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }

                        val start = dragStart
                        val end = dragEnd
                        if (start != null && end != null) {
                            val origin = listCoordinates?.boundsInRoot()?.topLeft ?: Offset.Zero
                            val left = min(start.x, end.x) - origin.x
                            val top = min(start.y, end.y) - origin.y
                            val width = max(start.x, end.x) - min(start.x, end.x)
                            val height = max(start.y, end.y) - min(start.y, end.y)
                            val selectionColor = MiuixTheme.colorScheme.primary
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawRect(
                                    color = selectionColor.copy(alpha = 0.12f),
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                )
                                drawRect(
                                    color = selectionColor,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    style = Stroke(width = 1.dp.toPx()),
                                )
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
    canTrash: Boolean,
    canRestore: Boolean,
    onInlineRename: (String) -> Unit,
    startInlineRename: Boolean,
    onInlineRenameStarted: () -> Unit,
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
    val focusManager = LocalFocusManager.current
    val infoLabel = stringResource(Res.string.action_view_info)
    val copyLabel = stringResource(
        if (paneId == PaneId.Left) Res.string.action_copy_to_right else Res.string.action_copy_to_left
    )
    val moveLabel = stringResource(
        if (paneId == PaneId.Left) Res.string.action_move_to_right else Res.string.action_move_to_left
    )
    val trashLabel = stringResource(Res.string.action_move_to_trash)
    val restoreLabel = stringResource(Res.string.action_restore)
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

    var editing by remember(row.id) { mutableStateOf(false) }
    var editFieldWasFocused by remember(row.id) { mutableStateOf(false) }
    val editState = rememberTextFieldState(row.name)
    val focusRequester = remember { FocusRequester() }
    val inlineBounds = LocalInlineRenameBounds.current

    fun commitInlineRename() {
        if (!editing) return
        editing = false
        inlineBounds?.value = null
        onInlineRename(editState.text.toString().trim())
    }

    DisposableEffect(editing) {
        onDispose {
            if (editing) inlineBounds?.value = null
        }
    }

    LaunchedEffect(startInlineRename) {
        if (startInlineRename) {
            editing = true
            onInlineRenameStarted()
        }
    }

    LaunchedEffect(editing) {
        if (!editing) {
            editFieldWasFocused = false
            return@LaunchedEffect
        }
        editFieldWasFocused = false
        editState.setTextAndPlaceCursorAtEnd(row.name)
        val selectionEnd = if (row.isDirectory) {
            row.name.length
        } else {
            row.name.lastIndexOf('.').takeIf { it > 0 } ?: row.name.length
        }
        editState.edit {
            selection = TextRange(0, selectionEnd)
        }
        focusRequester.requestFocus()
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
                if (canTrash) {
                    add(DropdownItem(text = trashLabel, onClick = { dispatch(FileContextAction.Trash) }))
                }
                if (canRestore) {
                    add(DropdownItem(text = restoreLabel, onClick = { dispatch(FileContextAction.Restore) }))
                }
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
                        add(
                            DropdownItem(
                                text = renameLabel,
                                onClick = { dispatch(FileContextAction.Rename) },
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
                .alpha(if (row.entry.isHidden) 0.45f else 1f)
                .combinedClickable(
                    onClick = {
                        if (editing) {
                            commitInlineRename()
                        } else {
                            focusManager.clearFocus()
                        }
                        onClick()
                    },
                    onDoubleClick = onDoubleClick,
                )
                .pointerInput(onSecondaryClick, editing) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (
                                !editing &&
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
                if (row.thumbnailModel != null) {
                    AsyncImage(
                        model = row.thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .squircleClip(8.dp),
                        placeholder = painterResource(row.icon),
                        error = painterResource(row.icon),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        painter = painterResource(row.icon),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            },
            endActions = {
                if (row.isDirectory) {
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        ) {
            if (editing) {
                BasicTextField(
                    state = editState,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { coordinates ->
                            if (editing) {
                                inlineBounds?.value = coordinates.boundsInRoot()
                            }
                        }
                        .onFocusChanged { focusState ->
                            if (!editing) return@onFocusChanged
                            if (focusState.isFocused) {
                                editFieldWasFocused = true
                            } else if (editFieldWasFocused) {
                                commitInlineRename()
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                commitInlineRename()
                                true
                            } else {
                                false
                            }
                        }
                        .background(
                            color = MiuixTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    textStyle = MiuixTheme.textStyles.body1.copy(
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                )
            } else {
                Text(
                    text = row.name,
                    modifier = if (selected) {
                        Modifier.combinedClickable(
                                indication = null,
                                interactionSource = null,
                                onDoubleClick = onDoubleClick,
                            ) {
                                editing = true
                            }
                    } else {
                        Modifier
                    },
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = row.summary.orEmpty(),
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

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