package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import coil3.compose.AsyncImage
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
import dockhand.shared.generated.resources.action_extract
import dockhand.shared.generated.resources.action_open
import dockhand.shared.generated.resources.action_open_with
import dockhand.shared.generated.resources.open_with_empty
import dockhand.shared.generated.resources.open_with_loading
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
import io.github.lumkit.dockhand.core.archive.archiveFormatForName
import io.github.lumkit.dockhand.ui.component.LocalOpenWithSelect
import io.github.lumkit.dockhand.ui.component.LocalOpenWithState
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLayoutMode
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.domain.model.displayPath
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

/** 网格视图的固定尺寸；框选命中计算需要和实际布局保持一致。 */
private val GRID_ITEM_MIN_WIDTH = 112.dp
private val GRID_ITEM_HEIGHT = 156.dp
private val GRID_ITEM_SPACING = 6.dp

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
    onDragMoveOut: (List<FileEntry>) -> Unit = {},
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
    val externalDrop = LocalExternalDropController.current
    val density = LocalDensity.current
    val scrollBarTouchWidth = with(density) { 28.dp.toPx() }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var dragPointerLocal by remember { mutableStateOf<Offset?>(null) }
    var dragAnchorIndex by remember { mutableStateOf<Int?>(null) }
    val longPressDragArmed = remember { LongPressDragState() }

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

    DisposableEffect(pane.id, externalDrop) {
        onDispose { externalDrop?.removePaneBounds(pane.id) }
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
            // 外部拖拽用 AWT 逻辑坐标命中面板，这里统一换算成同一坐标空间。
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                val scale = density.density
                externalDrop?.updatePaneBounds(
                    pane.id,
                    Rect(
                        left = bounds.left / scale,
                        top = bounds.top / scale,
                        right = bounds.right / scale,
                        bottom = bounds.bottom / scale,
                    ),
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
                                        if (longPressDragArmed.armed) {
                                            // 长按已进入拖出准备，交给条目启动文件拖拽；本次按下不再框选。
                                            break
                                        }
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
                                    onDragMoveOut = onDragMoveOut,
                                    longPressDragArmed = longPressDragArmed,
                                    onLongPressArmedChanged = { longPressDragArmed.armed = it },
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
                                            selectedEntries = pane.selectedEntries,
                                            onSelectForDrag = {
                                                onEntryClick(row.entry, SelectionMode.Replace)
                                            },
                                            onLongPressArmedChanged = {
                                                longPressDragArmed.armed = it
                                            },
                                            onDragMoveOut = onDragMoveOut,
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

        val dropHover = externalDrop?.hover?.takeIf { it.paneId == pane.id }
        if (dropHover != null) {
            ExternalDropOverlay(
                action = dropHover.action,
                destinationPath = pane.location?.displayPath(),
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp),
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
    selectedEntries: List<FileEntry>,
    onSelectForDrag: () -> Unit,
    onLongPressArmedChanged: (Boolean) -> Unit,
    onDragMoveOut: (List<FileEntry>) -> Unit,
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
    val density = LocalDensity.current
    val previewSizePx = with(density) { 80.dp.roundToPx() }
    val iconPainter = painterResource(row.icon)
    LaunchedEffect(row.icon, previewSizePx) {
        DragPreviewCache.putIcon(row.icon, iconPainter, previewSizePx)
    }
    val focusManager = LocalFocusManager.current
    val infoLabel = stringResource(Res.string.action_view_info)
    val openLabel = stringResource(Res.string.action_open)
    val openWithLabel = stringResource(Res.string.action_open_with)
    val openWithState = LocalOpenWithState.current
    val openWithSelect = LocalOpenWithSelect.current
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
    val extractLabel = stringResource(Res.string.action_extract)
    val isArchive = archiveFormatForName(row.name) != null
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
    var suppressClickAfterDrag by remember(row.id) { mutableStateOf(false) }
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
                add(DropdownItem(text = openLabel, onClick = { dispatch(FileContextAction.Open) }))
                if (!row.isDirectory) {
                    val current = openWithState?.takeIf { it.entry.id == row.entry.id }
                    val children = when {
                        current == null || current.loading ->
                            listOf(DropdownItem(text = stringResource(Res.string.open_with_loading), enabled = false))
                        current.applications.isEmpty() ->
                            listOf(DropdownItem(text = stringResource(Res.string.open_with_empty), enabled = false))
                        else -> current.applications.map { app ->
                            DropdownItem(
                                text = app.name,
                                icon = app.iconPath?.let { iconPath ->
                                    { modifier ->
                                        AsyncImage(
                                            model = iconPath,
                                            contentDescription = null,
                                            modifier = modifier.size(24.dp),
                                        )
                                    }
                                },
                                onClick = { openWithSelect(row.entry, app) },
                            )
                        }
                    }
                    add(DropdownItem(text = openWithLabel, children = children))
                }
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
                if (isArchive) {
                    add(DropdownItem(text = extractLabel, onClick = { dispatch(FileContextAction.Extract) }))
                }
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

    val dragSource = Modifier.longPressDragSource(
        enabled = !editing && isLocal,
        onPress = {
            suppressClickAfterDrag = false
            onLongPressArmedChanged(false)
        },
        onLongPressArmed = { onLongPressArmedChanged(true) },
        onDragStart = {
            onLongPressArmedChanged(false)
            val entries = dragOutEntries(row.entry, selected, selectedEntries)
            if (!selected && entries.isNotEmpty()) onSelectForDrag()
            suppressClickAfterDrag = startFileDragOut(
                entries = entries,
                sourcePane = paneId,
                thumbnails = DragPreviewCache.thumbnailSnapshot(entries.map { it.location.path }),
                icons = DragPreviewCache.iconSnapshot(entries),
                density = density.density,
                onMoveCompleted = onDragMoveOut,
            )
        },
        onCancelled = { onLongPressArmedChanged(false) },
    )

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
                        if (suppressClickAfterDrag) {
                            suppressClickAfterDrag = false
                        } else {
                            if (editing) {
                                commitInlineRename()
                            } else {
                                focusManager.clearFocus()
                            }
                            onClick()
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
                .then(dragSource),
            startAction = {
                if (row.thumbnailModel != null) {
                    // 初始就用 Crop：占位图标是方形，不会裁切；同时保证 onSuccess 缓存的是裁切后的缩略图。
                    var isError by remember(row) { mutableStateOf(false) }

                    AsyncImage(
                        model = row.thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .squircleClip(8.dp),
                        placeholder = iconPainter,
                        error = iconPainter,
                        contentScale = if (isError) ContentScale.Fit else ContentScale.Crop,
                        onError = { isError = true },
                        onSuccess = { state ->
                            isError = false
                            DragPreviewCache.putThumbnail(row.entry.location.path, state.painter, previewSizePx)
                        },
                    )
                } else {
                    Image(
                        painter = iconPainter,
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
    onDragMoveOut: (List<FileEntry>) -> Unit,
    longPressDragArmed: LongPressDragState,
    onLongPressArmedChanged: (Boolean) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val windowInfo = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current
    val currentHasMore by rememberUpdatedState(pane.hasMore)
    val currentLoadingMore by rememberUpdatedState(pane.isLoadingMore)
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentEntries by rememberUpdatedState(pane.entries)
    val inlineBounds = LocalInlineRenameBounds.current
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    /** 正在进行的框选；非空即表示按住鼠标拖动中。 */
    var marquee by remember { mutableStateOf<GridMarquee?>(null) }
    val isMarqueeActive = marquee != null
    val density = LocalDensity.current
    val scrollBarTouchWidth = with(density) { 28.dp.toPx() }
    val currentGridSpacingPx by rememberUpdatedState(with(density) { GRID_ITEM_SPACING.toPx() })
    val edgeScrollThresholdPx = with(density) { 36.dp.toPx() }
    val edgeScrollStepPx = with(density) { 28.dp.toPx() }

    /** 把根坐标换算成网格单元；只做坐标转换，命中计算在 [resolveGridCell] 里。 */
    fun gridCellAt(rootPoint: Offset): GridCell? {
        val entries = currentEntries
        if (entries.isEmpty()) return null
        val origin = gridCoordinates?.boundsInRoot()?.topLeft ?: return null
        val layoutInfo = gridState.layoutInfo
        // row/column 为 -1 表示正在动画退场的条目，不参与命中计算。
        val visible = layoutInfo.visibleItemsInfo
            .filter { it.index in entries.indices && it.row >= 0 && it.column >= 0 }
            .map { info ->
                GridVisibleItem(
                    row = info.row,
                    column = info.column,
                    offsetX = info.offset.x.toFloat(),
                    offsetY = info.offset.y.toFloat(),
                    width = info.size.width.toFloat(),
                    height = info.size.height.toFloat(),
                )
            }
        return resolveGridCell(
            x = rootPoint.x - origin.x,
            y = rootPoint.y - origin.y,
            items = visible,
            columns = layoutInfo.maxSpan,
            rowSpacing = layoutInfo.mainAxisItemSpacing.toFloat(),
            columnSpacing = currentGridSpacingPx,
        )
    }

    /** 用当前选框重算选区；选框缩小时跑出选框的条目会重新变为未选中。 */
    fun applyGridMarquee(session: GridMarquee) {
        val entries = currentEntries
        if (entries.isEmpty()) return
        val cell = gridCellAt(session.current) ?: return
        val columns = gridState.layoutInfo.maxSpan.coerceAtLeast(1)
        val anchorColumn = session.anchor.column.coerceIn(0, columns - 1)
        val indices = marqueeItemIndices(
            anchorRow = session.anchor.row,
            anchorColumn = anchorColumn,
            pointerRow = cell.row,
            pointerColumn = cell.column,
            columns = columns,
            itemCount = entries.size,
        )
        val selected = buildSet(indices.size) { indices.forEach { add(entries[it].id) } }
        val anchorIndex = (session.anchor.row * columns + anchorColumn)
            .coerceIn(0, entries.lastIndex)
        currentOnSelectionChange(selected, entries[anchorIndex].id)
    }

    // 拖动期间逐帧刷新：跟随指针移动，也跟随列表滚动继续扩选或回退反选。
    LaunchedEffect(isMarqueeActive) {
        if (!isMarqueeActive) return@LaunchedEffect
        while (true) {
            val session = marquee ?: break
            applyGridMarquee(session)
            // 指针贴近上下边缘时自动滚动；滚动后指针落到新的行上，选区随之扩张。
            val bounds = gridCoordinates?.boundsInRoot()
            if (bounds != null) {
                val pointerY = session.current.y - bounds.top
                val delta = when {
                    pointerY < edgeScrollThresholdPx -> {
                        val intensity = ((edgeScrollThresholdPx - pointerY) / edgeScrollThresholdPx)
                            .coerceIn(0f, 1f)
                        -edgeScrollStepPx * intensity
                    }

                    pointerY > bounds.height - edgeScrollThresholdPx -> {
                        val intensity = (
                                (pointerY - (bounds.height - edgeScrollThresholdPx)) /
                                        edgeScrollThresholdPx
                                ).coerceIn(0f, 1f)
                        edgeScrollStepPx * intensity
                    }

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
        // maxSpan 是网格的权威列数；用可见条目推断列数在条目不足一行时会偏小。
        snapshotFlow { gridState.layoutInfo.maxSpan }
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
                            // 右侧拖动条自行处理拖动，框选不能抢事件。
                            continue
                        }
                        val pressRoot = coordinates?.localToRoot(down.position) ?: down.position
                        if (inlineBounds?.value?.contains(pressRoot) == true) {
                            // 行内重命名输入框内部的拖动用于选择文字，不能启动框选。
                            continue
                        }
                        val anchor = gridCellAt(pressRoot) ?: continue

                        var session: GridMarquee? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll) {
                                // 滚轮交给列表自己滚动，选区由逐帧循环跟随内容移动。
                                continue
                            }
                            val change = event.changes.firstOrNull() ?: break
                            val point = coordinates?.localToRoot(change.position) ?: change.position
                            if (session == null && (point - pressRoot).getDistance() > viewConfiguration.touchSlop) {
                                if (longPressDragArmed.armed) {
                                    // 长按已进入拖出准备，交给条目启动文件拖拽；本次按下不再框选。
                                    break
                                }
                                session = GridMarquee(start = pressRoot, current = point, anchor = anchor)
                                marquee = session
                            }
                            val active = session
                            if (active != null) {
                                session = active.copy(current = point)
                                marquee = session
                                applyGridMarquee(session)
                                event.changes.forEach { it.consume() }
                            }
                            if (!change.pressed) break
                        }
                        marquee = null
                    }
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(GRID_ITEM_MIN_WIDTH),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollBar(gridState),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_ITEM_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_ITEM_SPACING),
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
                    selectedEntries = pane.selectedEntries,
                    onSelectForDrag = { onEntryClick(row.entry, SelectionMode.Replace) },
                    onLongPressArmedChanged = onLongPressArmedChanged,
                    onDragMoveOut = onDragMoveOut,
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

        // 选框固定在指针按下的屏幕位置，内容滚动时画框不动，选区照常随滚动扩张。
        val session = marquee
        val bounds = gridCoordinates?.boundsInRoot()
        if (session != null && bounds != null) {
            val start = session.start - bounds.topLeft
            val end = session.current - bounds.topLeft
            // 夹紧到网格区域，指针拖到窗口外时选框不会画到面板之外。
            val left = min(start.x, end.x).coerceIn(0f, bounds.width)
            val top = min(start.y, end.y).coerceIn(0f, bounds.height)
            val right = max(start.x, end.x).coerceIn(0f, bounds.width)
            val bottom = max(start.y, end.y).coerceIn(0f, bounds.height)
            val width = right - left
            val height = bottom - top
            if (width > 0f && height > 0f) {
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
    selectedEntries: List<FileEntry>,
    onSelectForDrag: () -> Unit,
    onLongPressArmedChanged: (Boolean) -> Unit,
    onDragMoveOut: (List<FileEntry>) -> Unit,
) {
    val color = if (selected) {
        MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        Color.Transparent
    }
    val focusManager = LocalFocusManager.current
    val inlineBounds = LocalInlineRenameBounds.current
    val isLocal = row.entry.location.source.kind == FileSourceKind.Local
    val density = LocalDensity.current
    val previewSizePx = with(density) { 80.dp.roundToPx() }
    val iconPainter = painterResource(row.icon)
    LaunchedEffect(row.icon, previewSizePx) {
        DragPreviewCache.putIcon(row.icon, iconPainter, previewSizePx)
    }
    var editing by remember(row.id) { mutableStateOf(false) }
    var suppressNextClick by remember(row.id) { mutableStateOf(false) }
    var suppressClickAfterDrag by remember(row.id) { mutableStateOf(false) }
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

    val dragSource = Modifier.longPressDragSource(
        enabled = !editing && isLocal,
        onPress = {
            suppressClickAfterDrag = false
            onLongPressArmedChanged(false)
        },
        onLongPressArmed = { onLongPressArmedChanged(true) },
        onDragStart = {
            onLongPressArmedChanged(false)
            val entries = dragOutEntries(row.entry, selected, selectedEntries)
            if (!selected && entries.isNotEmpty()) onSelectForDrag()
            suppressClickAfterDrag = startFileDragOut(
                entries = entries,
                sourcePane = paneId,
                thumbnails = DragPreviewCache.thumbnailSnapshot(entries.map { it.location.path }),
                icons = DragPreviewCache.iconSnapshot(entries),
                density = density.density,
                onMoveCompleted = onDragMoveOut,
            )
        },
        onCancelled = { onLongPressArmedChanged(false) },
    )

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(GRID_ITEM_HEIGHT)
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
                        } else if (suppressClickAfterDrag) {
                            suppressClickAfterDrag = false
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
                .then(dragSource)
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
                    placeholder = iconPainter,
                    error = iconPainter,
                    contentScale = if (isError) ContentScale.Fit else ContentScale.Crop,
                    onError = { isError = true },
                    onSuccess = { state ->
                        isError = false
                        DragPreviewCache.putThumbnail(row.entry.location.path, state.painter, previewSizePx)
                    },
                )
            } else {
                Image(
                    painter = iconPainter,
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
    val extractLabel = stringResource(Res.string.action_extract)
    val isArchive = archiveFormatForName(entry.name) != null
    val servicesLabel = stringResource(Res.string.action_services)
    val infoLabel = stringResource(Res.string.action_view_info)
    val openLabel = stringResource(Res.string.action_open)
    val openWithLabel = stringResource(Res.string.action_open_with)
    val openWithState = LocalOpenWithState.current
    val openWithSelect = LocalOpenWithSelect.current
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
                add(DropdownItem(text = openLabel, onClick = { onAction(FileContextAction.Open) }))
                if (!entry.isDirectory) {
                    val current = openWithState?.takeIf { it.entry.id == entry.entry.id }
                    val children = when {
                        current == null || current.loading ->
                            listOf(DropdownItem(text = stringResource(Res.string.open_with_loading), enabled = false))
                        current.applications.isEmpty() ->
                            listOf(DropdownItem(text = stringResource(Res.string.open_with_empty), enabled = false))
                        else -> current.applications.map { app ->
                            DropdownItem(
                                text = app.name,
                                icon = app.iconPath?.let { iconPath ->
                                    { modifier ->
                                        AsyncImage(
                                            model = iconPath,
                                            contentDescription = null,
                                            modifier = modifier.size(24.dp),
                                        )
                                    }
                                },
                                onClick = { openWithSelect(entry.entry, app) },
                            )
                        }
                    }
                    add(DropdownItem(text = openWithLabel, children = children))
                }
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
                if (isArchive) {
                    add(DropdownItem(text = extractLabel, onClick = { onAction(FileContextAction.Extract) }))
                }
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