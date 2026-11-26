package io.github.lumkit.dockhand.ui.component

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import java.awt.Container
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.Timer

private const val TAG = "[external-drop]"

/**
 * 外部文件拖入的 AWT 宿主。
 *
 * Compose Desktop 会在窗口的 Compose 根组件上安装自己的 DropTarget，并且无条件接受
 * 源上报的 drop 动作。macOS 上 Finder 拖文件默认上报 MOVE，接受 MOVE 会让 Finder 在异步
 * 复制完成前删除源文件。因此这里替换掉 Compose 的 DropTarget：
 *
 *  - 始终以 COPY 协商（acceptDrag / acceptDrop），保护源文件；
 *  - drop 时先读取原始动作作为用户意图，再交给 [ExternalDropController]。
 *
 * 移动由 ViewModel 使用 moveWithProgress 完成（先复制、成功后再删除源）。
 */
object ExternalFileDropHost {

    private val installedTargets = WeakHashMap<ComposeWindow, DropTarget>()
    private val installedFocusListeners = WeakHashMap<ComposeWindow, WindowFocusListener>()

    fun install(window: ComposeWindow, controller: ExternalDropController) {
        if (tryInstall(window, controller)) return

        // Compose 的 DropTarget 可能晚于窗口出现，短暂重试。
        var attempts = 0
        val timer = Timer(150) { event ->
            attempts++
            if (tryInstall(window, controller)) {
                (event.source as Timer).stop()
            } else if (attempts >= 20) {
                (event.source as Timer).stop()
                System.err.println(TAG + " compose drop target component not found, external drop disabled")
            }
        }
        timer.isRepeats = true
        timer.start()
    }

    fun uninstall(window: ComposeWindow, controller: ExternalDropController) {
        controller.onDragExited()
        installedTargets.remove(window)
        installedFocusListeners.remove(window)?.let(window::removeWindowFocusListener)
    }

    private fun tryInstall(window: ComposeWindow, controller: ExternalDropController): Boolean {
        val component = findDropTargetComponent(window.contentPane) ?: return false
        val target = runCatching {
            val created = DropTarget(
                component,
                DnDConstants.ACTION_COPY,
                ExternalFileDropListener(controller),
                true,
            )
            component.dropTarget = created
            created
        }.onFailure { error ->
            System.err.println(TAG + " install failed on " + component.javaClass.name + ": " + error)
        }.getOrNull() ?: return false
        installedTargets[window] = target

        // 窗口失焦（Cmd+Tab、弹窗抢焦点）时 AWT 不一定派发 dragExit，主动清掉蒙层。
        installedFocusListeners.remove(window)?.let(window::removeWindowFocusListener)
        val focusListener = object : WindowFocusListener {
            override fun windowGainedFocus(event: WindowEvent?) = Unit

            override fun windowLostFocus(event: WindowEvent?) {
                controller.onDragExited()
            }
        }
        window.addWindowFocusListener(focusListener)
        installedFocusListeners[window] = focusListener
        return true
    }

    /** 供拖出宿主复用同一个 Compose 根组件。 */
    internal fun composeDndComponent(window: ComposeWindow): JComponent? =
        findDropTargetComponent(window.contentPane)

    private fun findDropTargetComponent(container: Container): JComponent? {
        container.components.forEach { child ->
            if (child is JComponent && child.dropTarget != null) return child
            if (child is Container) {
                findDropTargetComponent(child)?.let { return it }
            }
        }
        return null
    }
}

private class ExternalFileDropListener(
    private val controller: ExternalDropController,
) : DropTargetListener {

    override fun dragEnter(dtde: DropTargetDragEvent) {
        updateHover(dtde)
    }

    override fun dragOver(dtde: DropTargetDragEvent) {
        updateHover(dtde)
    }

    override fun dropActionChanged(dtde: DropTargetDragEvent) {
        updateHover(dtde)
    }

    override fun dragExit(dte: DropTargetEvent) {
        controller.onDragExited()
    }

    override fun drop(dtde: DropTargetDropEvent) {
        // 只要 drop 落在本窗口，就说明这是应用内拖拽；源侧的 exportDone 不能再删源文件，
        // 是否移动由 ViewModel 的任务决定（macOS 可能把 COPY 误报成 MOVE）。
        ExternalFileDragHost.markInternalDrop()
        val position = dtde.location.toLogicalOffset()
        val targetPane = controller.paneAt(position)
        val sourcePane = ExternalFileDragHost.activeDragSourcePane()
        if (sourcePane != null && targetPane == sourcePane) {
            // 本窗口内松开不触发：源面板不是投放目标。
            dtde.rejectDrop()
            return
        }
        val supported = dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        if (!supported || targetPane == null) {
            dtde.rejectDrop()
            return
        }

        // 必须在 acceptDrop 之前记录用户意图：acceptDrop 会改写当前 drop 动作。
        val intent = externalDropActionOf(dtde.dropAction)
        dtde.acceptDrop(DnDConstants.ACTION_COPY)
        val files = runCatching {
            dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
        }.getOrNull().orEmpty()
        val paths = javaFileListPaths(files)
        val handled = paths.isNotEmpty() && controller.onDrop(position, paths, intent)
        dtde.dropComplete(handled)
    }

    private fun updateHover(dtde: DropTargetDragEvent) {
        val position = dtde.location.toLogicalOffset()
        val targetPane = controller.paneAt(position)
        val sourcePane = ExternalFileDragHost.activeDragSourcePane()
        if (sourcePane != null && targetPane == sourcePane) {
            // 源面板不接收本次拖出，松手不产生动作。
            controller.onDragExited()
            dtde.rejectDrag()
            return
        }
        val handled = controller.onDragMoved(
            position = position,
            action = externalDropActionOf(dtde.dropAction),
        )
        if (handled) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY)
        } else {
            dtde.rejectDrag()
        }
    }
}

/** AWT 拖拽点 → 与面板 bounds 相同的逻辑坐标。 */
internal fun Point.toLogicalOffset(): Offset = Offset(x.toFloat(), y.toFloat())

/** AWT 的 drop 动作 → 业务意图；NONE/组合动作返回 null。 */
internal fun externalDropActionOf(awtDropAction: Int): ExternalDropAction? = when (awtDropAction) {
    DnDConstants.ACTION_COPY -> ExternalDropAction.Copy
    DnDConstants.ACTION_MOVE -> ExternalDropAction.Move
    DnDConstants.ACTION_LINK -> ExternalDropAction.Link
    else -> null
}

/** javaFileListFlavor 的原始列表 → 本地绝对路径；顺序保持，忽略非 File 项。 */
internal fun javaFileListPaths(files: List<*>): List<String> =
    files.filterIsInstance<File>().map { it.absolutePath }
