package io.github.lumkit.dockhand.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.ui.screen.home.PaneId

/**
 * 长按后要拖出的条目。
 *
 * 当前面板已有选中项且按下的条目在其中时，带走整个选择；否则只带走按下的这一项。
 * 按下的条目不是本地文件时返回空列表（MTP 没有可直接交给其他程序的路径）。
 */
internal fun dragOutEntries(
    pressed: FileEntry,
    selected: Boolean,
    selectedEntries: List<FileEntry>,
): List<FileEntry> {
    if (pressed.location.source.kind != FileSourceKind.Local) return emptyList()
    return if (selected) {
        selectedEntries.ifEmpty { listOf(pressed) }
    } else {
        listOf(pressed)
    }
}

/**
 * 面板与条目共享的长按拖出准备标记。
 *
 * 用普通可变对象而不是 Compose State：框选的指针协程需要在同一帧内读到最新值，
 * 不能等重组把 Boolean 参数刷新到子组件。
 */
internal class LongPressDragState {
    var armed: Boolean = false
}

/**
 * 长按进入拖出准备，随后移动才真正开始拖拽；松手或提前移动会取消。
 *
 * 使用 [PointerEventPass.Final]，不消费事件，因此单击、双击、普通拖拽框选都保持原行为。
 * 长按超时后先回调 [onLongPressArmed]（面板据此暂停框选），指针移动超过 slop 再回调
 * [onDragStart]；这样按住不动直接松手不会触发同目录投放。
 * [onPress] 在每次按下时回调（用于清理上一次拖拽的点击抑制和准备标记）。
 */
@Composable
internal fun Modifier.longPressDragSource(
    enabled: Boolean,
    onPress: () -> Unit,
    onLongPressArmed: () -> Unit,
    onDragStart: () -> Unit,
    onCancelled: () -> Unit,
): Modifier {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnArmed by rememberUpdatedState(onLongPressArmed)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    return if (!enabled) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val press = awaitPointerEvent(PointerEventPass.Final)
                    if (press.type != PointerEventType.Press || !press.buttons.isPrimaryPressed) {
                        continue
                    }
                    val down = press.changes.firstOrNull() ?: continue
                    if (!down.pressed) continue
                    currentOnPress()
                    val pointerId = down.id
                    val start = down.position

                    var cancelledBeforeTimeout = false
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                cancelledBeforeTimeout = true
                                return@withTimeoutOrNull
                            }
                            if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                cancelledBeforeTimeout = true
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    if (cancelledBeforeTimeout) continue

                    currentOnArmed()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null || !change.pressed) {
                            currentOnCancelled()
                            break
                        }
                        if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                            currentOnDragStart()
                            break
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把条目拖到其他程序或另一个面板。
 *
 * 只包含本地存在的文件/目录；没有可拖出的项时返回 false，拖拽不会开始。
 * [sourcePane] 用于拒绝在源面板内松手：本窗口只有另一个面板是有效目标，
 * 源面板、工具栏和空白区域的松手都不触发动作。
 * [thumbnails] 是已经加载完成的缩略图快照（按路径）；[icons] 是列表项图标快照（按路径），
 * 用于拖拽预览。图片/视频优先用缩略图，其他文件用列表项图标，两者都缺失时回退系统图标。
 * [density] 用于把 80dp × 80dp 的预览图标换算成像素。
 * [onMoveCompleted] 只在目标程序以 MOVE 完成拖拽时回调，用于删除源文件；
 * 面板内部投放会以 COPY 协商，不触发这个回调。
 */
expect fun startFileDragOut(
    entries: List<FileEntry>,
    sourcePane: PaneId,
    thumbnails: Map<String, ImageBitmap>,
    icons: Map<String, ImageBitmap>,
    density: Float,
    onMoveCompleted: (List<FileEntry>) -> Unit,
): Boolean
