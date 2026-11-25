package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.lumkit.dockhand.domain.model.FileLayoutMode
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
    layoutMode: FileLayoutMode = FileLayoutMode.List,
    onGridColumnsChanged: (Int) -> Unit = {},
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
    val currentLayoutMode by rememberUpdatedState(layoutMode)
    val listState = rememberLazyListState()
    val inlineRenameBounds = LocalInlineRenameBounds.current
    val scrollBarTouchWidth = with(LocalDensity.current) { 28.dp.toPx() }
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
                                if (currentLayoutMode == FileLayoutMode.Grid) continue
                                val down = downEvent.changes.firstOrNull() ?: continue
                                val coordinates = listCoordinates
                                val width = coordinates?.size?.width
                                if (
                                    width != null &&
                                    down.position.x >= width - scrollBarTouchWidth
                                ) {
                                    // 右侧拖动条自行处理拖动，框选不能抢事件。
                                    continue
                                }
                                val start = coordinates?.localToRoot(down.position) ?: down.position
                                if (inlineRenameBounds?.value?.contains(start) == true) {
                                    // 行内重命名输入框内部拖动用于选择文字，不能启动框选。
                                    continue
                                }
                                var dragging = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Scroll) {
                                        updateMarqueeSelection()
                                        continue
                                    }
                                    val change = event.changes.firstOrNull() ?: break
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
                        Crossfade(
                            targetState = layoutMode,
                            animationSpec = tween(durationMillis = 180),
                            label = "file-layout",
                        ) { mode ->
                            if (mode == FileLayoutMode.Grid) {
                                FileGridContent(
                                    pane = pane,
                                    canTransferToOtherPane = canTransferToOtherPane,
                                    canMoveToOtherPane = canMoveToOtherPane,
                                    canTrash = canTrash,
                                    onEntryClick = onEntryClick,
                                    onEntryDoubleClick = onEntryDoubleClick,
                                    onEntrySecondaryClick = onEntrySecondaryClick,
                                    onContextMenuRequest = onContextMenuRequest,
                                    onContextMenuDismiss = onContextMenuDismiss,
                                    onContextAction = onContextAction,
                                    onLoadMore = onLoadMore,
                                    onSelectionChange = onSelectionChange,
                                    onInlineRename = onInlineRename,
                                    onInlineRenameStarted = onInlineRenameStarted,
                                    onGridColumnsChanged = onGridColumnsChanged,
                                )
                            } else {
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

                                LaunchedEffect(pane.selectionLeadId) {
                                    val leadId = pane.selectionLeadId ?: return@LaunchedEffect
                                    val index = pane.entries.indexOfFirst { it.id == leadId }
                                    if (index < 0) return@LaunchedEffect
                                    val info = listState.layoutInfo
                                    val visible = info.visibleItemsInfo
                                    val viewportStart = info.viewportStartOffset
                                    val viewportEnd = info.viewportEndOffset
                                    val viewportHeight = viewportEnd - viewportStart
                                    val item = visible.firstOrNull { it.index == index }
                                    if (item != null) {
                                        when {
                                            item.offset < viewportStart ->
                                                listState.animateScrollToItem(index, 0)
                                            item.offset + item.size > viewportEnd ->
                                                listState.animateScrollToItem(
                                                    index,
                                                    -(viewportHeight - item.size).coerceAtLeast(0),
                                                )
                                        }
                                    } else {
                                        val first = visible.firstOrNull()?.index ?: 0
                                        val last = visible.lastOrNull()?.index ?: 0
                                        val itemHeight = visible.firstOrNull()?.size ?: 44
                                        when {
                                            index < first -> listState.animateScrollToItem(index, 0)
                                            index > last -> listState.animateScrollToItem(
                                                index,
                                                -(viewportHeight - itemHeight).coerceAtLeast(0),
                                            )
                                        }
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
                    var isError by remember(row) { mutableStateOf(true) }

                    AsyncImage(
                        model = row.thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .squircleClip(8.dp),
                        placeholder = painterResource(row.icon),
                        error = painterResource(row.icon),
                        contentScale = if (isError) ContentScale.Fit else ContentScale.Crop,
                        onError = { isError = true },
                        onSuccess = { isError = false },
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

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun FileGridContent(
    pane: FilePaneState,
    canTransferToOtherPane: Boolean,
    canMoveToOtherPane: Boolean,
    canTrash: Boolean,
    onEntryClick: (FileEntry, SelectionMode) -> Unit,
    onEntryDoubleClick: (FileEntry) -> Unit,
    onEntrySecondaryClick: (FileEntry) -> Unit,
    onContextMenuRequest: (String) -> Unit,
    onContextMenuDismiss: () -> Unit,
    onContextAction: (FileEntry, FileContextAction) -> Unit,
    onLoadMore: () -> Unit,
    onSelectionChange: (Set<String>, String?) -> Unit,
    onInlineRename: (FileEntry, String) -> Unit,
    onInlineRenameStarted: () -> Unit,
    onGridColumnsChanged: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val windowInfo = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current
    val currentHasMore by rememberUpdatedState(pane.hasMore)
    val currentLoadingMore by rememberUpdatedState(pane.isLoadingMore)
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val inlineBounds = LocalInlineRenameBounds.current
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var dragPointerLocal by remember { mutableStateOf<Offset?>(null) }
    var dragAnchorId by remember { mutableStateOf<String?>(null) }
    var dragAnchorIndex by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val isDragging = dragging
    val density = LocalDensity.current
    val scrollBarTouchWidth = with(density) { 28.dp.toPx() }

    fun gridIndexAt(rootPoint: Offset): Int? {
        val origin = gridCoordinates?.boundsInRoot()?.topLeft ?: return null
        val visible = gridState.layoutInfo.visibleItemsInfo
            .filter { it.index in pane.entries.indices }
        if (visible.isEmpty()) return null
        val hit = visible.firstOrNull { info ->
            val left = origin.x + info.offset.x
            val top = origin.y + info.offset.y
            rootPoint.x in left..(left + info.size.width) &&
                    rootPoint.y in top..(top + info.size.height)
        }
        if (hit != null) return hit.index
        // 指针在空白处时取最近的可见项，滚动后索引会随内容移动。
        return visible.minByOrNull { info ->
            val cy = origin.y + info.offset.y + info.size.height / 2f
            kotlin.math.abs(cy - rootPoint.y)
        }?.index
    }

    fun updateGridSelection() {
        val anchor = dragAnchorIndex ?: return
        val end = dragEnd ?: return
        val current = gridIndexAt(end) ?: return
        val from = min(anchor, current)
        val to = max(anchor, current)
        currentOnSelectionChange(
            pane.entries.subList(from, to + 1).map { it.id }.toSet(),
            dragAnchorId ?: pane.entries.getOrNull(anchor)?.id,
        )
    }

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        val edge = with(density) { 36.dp.toPx() }
        val maxStep = with(density) { 28.dp.toPx() }
        while (true) {
            updateGridSelection()
            val pointer = dragPointerLocal
            val height = gridCoordinates?.size?.height
            if (pointer != null && height != null) {
                val delta = when {
                    pointer.y < edge -> -maxStep * ((edge - pointer.y) / edge).coerceIn(0f, 1f)
                    pointer.y > height - edge ->
                        maxStep * ((pointer.y - (height - edge)) / edge).coerceIn(0f, 1f)

                    else -> 0f
                }
                if (delta != 0f) gridState.scrollBy(delta)
            }
            withFrameNanos { }
        }
    }

    LaunchedEffect(pane.location) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(pane.selectionLeadId) {
        val leadId = pane.selectionLeadId ?: return@LaunchedEffect
        val index = pane.entries.indexOfFirst { it.id == leadId }
        if (index < 0) return@LaunchedEffect
        val info = gridState.layoutInfo
        val visible = info.visibleItemsInfo
        val viewportStart = info.viewportStartOffset
        val viewportEnd = info.viewportEndOffset
        val viewportHeight = viewportEnd - viewportStart
        val item = visible.firstOrNull { it.index == index }
        if (item != null) {
            when {
                item.offset.y < viewportStart ->
                    gridState.animateScrollToItem(index, 0)
                item.offset.y + item.size.height > viewportEnd ->
                    gridState.animateScrollToItem(
                        index,
                        -(viewportHeight - item.size.height).coerceAtLeast(0),
                    )
            }
        } else {
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            val itemHeight = visible.firstOrNull()?.size?.height ?: 156
            when {
                index < first -> gridState.animateScrollToItem(index, 0)
                index > last -> gridState.animateScrollToItem(
                    index,
                    -(viewportHeight - itemHeight).coerceAtLeast(0),
                )
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.map { it.offset.x }.distinct().size
        }
            .distinctUntilChanged()
            .collect { columns ->
                if (columns > 0) onGridColumnsChanged(columns)
            }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && currentHasMore && !currentLoadingMore) currentLoadMore()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { gridCoordinates = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        val point = event.changes.firstOrNull()?.position ?: continue
                        val root = gridCoordinates?.localToRoot(point) ?: point
                        val bounds = inlineBounds?.value
                        if (bounds != null && !bounds.contains(root)) {
                            // 点击编辑框以外的网格区域时先提交当前行内重命名。
                            focusManager.clearFocus()
                        }
                    }
                }
            }
            .pointerInput(pane.id) {
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
                        val coordinates = gridCoordinates
                        val width = coordinates?.size?.width
                        if (width != null && down.position.x >= width - scrollBarTouchWidth) {
                            continue
                        }
                        val startRoot = coordinates?.localToRoot(down.position) ?: down.position
                        if (inlineBounds?.value?.contains(startRoot) == true) continue
                        val anchorIndex = gridIndexAt(startRoot)
                        val anchor = pane.entries.getOrNull(anchorIndex ?: -1)?.id
                        var active = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll) {
                                if (active) updateGridSelection()
                                continue
                            }
                            val change = event.changes.firstOrNull() ?: break
                            val point = coordinates?.localToRoot(change.position) ?: change.position
                            if (!active && (point - startRoot).getDistance() > viewConfiguration.touchSlop) {
                                active = true
                                dragging = true
                                dragAnchorId = anchor
                                dragAnchorIndex = anchorIndex
                                dragStart = startRoot
                            }
                            if (active) {
                                dragEnd = point
                                dragPointerLocal = change.position
                                updateGridSelection()
                                if (event.type != PointerEventType.Scroll) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            if (!change.pressed) break
                        }
                        dragging = false
                        dragStart = null
                        dragEnd = null
                        dragPointerLocal = null
                        dragAnchorId = null
                        dragAnchorIndex = null
                    }
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollBar(gridState),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            gridItems(pane.entries, key = { it.id }) { row ->
                FileGridItem(
                    row = row,
                    paneId = pane.id,
                    selected = row.id in pane.selectedIds,
                    startInlineRename = pane.inlineRenameEntryId == row.id,
                    canTransferToOtherPane = canTransferToOtherPane,
                    canMoveToOtherPane = canMoveToOtherPane,
                    canTrash = canTrash,
                    showContextMenu = pane.contextMenuEntryId == row.id,
                    onDismissContextMenu = onContextMenuDismiss,
                    onContextAction = { action -> onContextAction(row.entry, action) },
                    onClick = {
                        val modifiers = windowInfo.keyboardModifiers
                        val mode = when {
                            modifiers.isShiftPressed -> SelectionMode.Range
                            modifiers.isAltPressed || modifiers.isMetaPressed -> SelectionMode.Toggle
                            else -> SelectionMode.Replace
                        }
                        onEntryClick(row.entry, mode)
                    },
                    onDoubleClick = { onEntryDoubleClick(row.entry) },
                    onSecondaryClick = {
                        onEntrySecondaryClick(row.entry)
                        onContextMenuRequest(row.id)
                    },
                    onInlineRename = { name -> onInlineRename(row.entry, name) },
                    onInlineRenameStarted = onInlineRenameStarted,
                )
            }
            if (pane.isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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

        val marqueeStart = dragStart
        val marqueeEnd = dragEnd
        if (marqueeStart != null && marqueeEnd != null) {
            val origin = gridCoordinates?.boundsInRoot()?.topLeft ?: Offset.Zero
            val left = min(marqueeStart.x, marqueeEnd.x) - origin.x
            val top = min(marqueeStart.y, marqueeEnd.y) - origin.y
            val width = max(marqueeStart.x, marqueeEnd.x) - min(marqueeStart.x, marqueeEnd.x)
            val height = max(marqueeStart.y, marqueeEnd.y) - min(marqueeStart.y, marqueeEnd.y)
            val color = MiuixTheme.colorScheme.primary
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    color = color.copy(alpha = 0.12f),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                )
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

    }
}

@Composable
private fun FileGridItem(
    row: FileRowUi,
    paneId: PaneId,
    selected: Boolean,
    startInlineRename: Boolean,
    canTransferToOtherPane: Boolean,
    canMoveToOtherPane: Boolean,
    canTrash: Boolean,
    showContextMenu: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onContextAction: (FileContextAction) -> Unit,
    onInlineRename: (String) -> Unit,
    onInlineRenameStarted: () -> Unit,
) {
    val color = if (selected) {
        MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        Color.Transparent
    }
    val focusManager = LocalFocusManager.current
    val inlineBounds = LocalInlineRenameBounds.current
    var editing by remember(row.id) { mutableStateOf(false) }
    var suppressNextClick by remember(row.id) { mutableStateOf(false) }
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var editFieldWasFocused by remember(row.id) { mutableStateOf(false) }
    val editState = rememberTextFieldState(row.name)
    val focusRequester = remember { FocusRequester() }

    fun commitInlineRename() {
        if (!editing) return
        editing = false
        inlineBounds?.value = null
        onInlineRename(editState.text.toString().trim())
    }

    DisposableEffect(row.id) {
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

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .squircleClip(12.dp)
                .background(color)
                .alpha(if (row.entry.isHidden) 0.45f else 1f)
                .onGloballyPositioned { itemCoordinates = it }
                .pointerInput(editing) {
                    if (!editing) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Press) continue
                            val point = event.changes.firstOrNull()?.position ?: continue
                            val root = itemCoordinates?.localToRoot(point) ?: point
                            val bounds = inlineBounds?.value
                            if (bounds == null || !bounds.contains(root)) {
                                // 编辑中点击非输入框区域只提交，不能在同一次点击里重新进入编辑。
                                suppressNextClick = true
                            }
                        }
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (suppressNextClick) {
                            suppressNextClick = false
                            commitInlineRename()
                        } else if (editing) {
                            commitInlineRename()
                        } else {
                            focusManager.clearFocus()
                            if (selected) editing = true else onClick()
                        }
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
                }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (row.thumbnailModel != null) {
                var isError by remember(row) { mutableStateOf(false) }

                AsyncImage(
                    model = row.thumbnailModel,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).squircleClip(12.dp),
                    placeholder = painterResource(row.icon),
                    error = painterResource(row.icon),
                    contentScale = if (isError) ContentScale.Fit else ContentScale.Crop,
                    onError = { isError = true },
                    onSuccess = { isError = false },
                )
            } else {
                Image(
                    painter = painterResource(row.icon),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (editing) {
                BasicTextField(
                    state = editState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { coordinates ->
                            if (editing) inlineBounds?.value = coordinates.boundsInRoot()
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
                    textStyle = MiuixTheme.textStyles.footnote1.copy(
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                )
            } else {
                Text(
                    text = row.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface,
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
                    }.fillMaxWidth(),
                )
            }
            if (!row.summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.summary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        if (showContextMenu) {
            FileGridContextMenu(
                paneId = paneId,
                entry = row,
                canTransferToOtherPane = canTransferToOtherPane,
                canMoveToOtherPane = canMoveToOtherPane,
                canTrash = canTrash,
                onDismiss = onDismissContextMenu,
                onAction = { action ->
                    onContextAction(action)
                    onDismissContextMenu()
                },
            )
        }
    }
}

@Composable
private fun FileGridContextMenu(
    paneId: PaneId,
    entry: FileRowUi,
    canTransferToOtherPane: Boolean,
    canMoveToOtherPane: Boolean,
    canTrash: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileContextAction) -> Unit,
) {
    val isLocal = entry.entry.location.source.kind == FileSourceKind.Local
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
    val infoLabel = stringResource(Res.string.action_view_info)
    val terminalTabLabel = stringResource(
        if (entry.isDirectory) Res.string.action_terminal_tab_folder else Res.string.action_terminal_tab_file
    )
    val terminalWindowLabel = stringResource(
        if (entry.isDirectory) Res.string.action_terminal_window_folder else Res.string.action_terminal_window_file
    )

    val entries = buildList {
        add(
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = infoLabel, onClick = { onAction(FileContextAction.ViewInfo) })
                )
            )
        )
        if (isLocal) {
            add(DropdownEntry(items = buildList {
                if (canTransferToOtherPane) {
                    add(DropdownItem(text = copyLabel, onClick = { onAction(FileContextAction.Copy) }))
                    if (canMoveToOtherPane) {
                        add(DropdownItem(text = moveLabel, onClick = { onAction(FileContextAction.Move) }))
                    }
                }
                if (canTrash) {
                    add(DropdownItem(text = trashLabel, onClick = { onAction(FileContextAction.Trash) }))
                }
                add(DropdownItem(text = deleteLabel, onClick = { onAction(FileContextAction.Delete) }))
                add(DropdownItem(text = renameLabel, onClick = { onAction(FileContextAction.Rename) }))
                add(DropdownItem(text = compressLabel, onClick = { onAction(FileContextAction.Compress) }))
            }))
            add(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = servicesLabel,
                            children = listOf(
                                DropdownItem(
                                    text = terminalTabLabel,
                                    onClick = { onAction(FileContextAction.TerminalTab) },
                                ),
                                DropdownItem(
                                    text = terminalWindowLabel,
                                    onClick = { onAction(FileContextAction.TerminalWindow) },
                                ),
                            ),
                        )
                    )
                )
            )
        }
    }

    OverlayCascadingListPopup(
        show = true,
        entries = entries,
        onDismissRequest = onDismiss,
        alignment = if (paneId == PaneId.Left) {
            PopupPositionProvider.Align.End
        } else {
            PopupPositionProvider.Align.Start
        },
    )
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