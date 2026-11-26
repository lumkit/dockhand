package io.github.lumkit.dockhand.ui.component

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.ui.screen.home.PaneId
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.Timer
import javax.swing.filechooser.FileSystemView
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "[external-drag]"

/**
 * 面板文件拖出的 AWT 宿主。
 *
 * Compose 的拖出 API 在桌面端只支持“按住即拖”，无法区分长按和普通框选；
 * 因此这里直接复用 Compose 根组件的 AWT 传输链路：
 *
 *  - 在根组件上安装 [FileDragTransferHandler]；
 *  - 长按由 Compose 侧检测（[startFileDragOut]），检测到后调用 [start]；
 *  - 传输数据使用 javaFileListFlavor，目标程序按自己的规则复制或移动；
 *  - 目标程序以 MOVE 完成时，源文件由 [FileDragTransferHandler.exportDone] 回调删除；
 *    面板内部投放会先在 [ExternalFileDropHost] 标记，避免和面板的复制/移动任务重复删除。
 */
object ExternalFileDragHost {

    private var installedWindow: ComposeWindow? = null
    private var installedComponent: JComponent? = null
    private val handler = FileDragTransferHandler()
    private val internalDrop = AtomicBoolean(false)
    private var dragSourcePane: PaneId? = null

    fun install(window: ComposeWindow) {
        if (tryInstall(window)) return

        // Compose 的根组件可能晚于窗口出现，短暂重试。
        var attempts = 0
        val timer = Timer(150) { event ->
            attempts++
            if (tryInstall(window)) {
                (event.source as Timer).stop()
            } else if (attempts >= 20) {
                (event.source as Timer).stop()
                System.err.println(TAG + " compose component not found, external drag disabled")
            }
        }
        timer.isRepeats = true
        timer.start()
    }

    fun uninstall(window: ComposeWindow) {
        if (installedWindow !== window) return
        installedComponent?.let { component ->
            if (component.transferHandler === handler) {
                component.transferHandler = null
            }
        }
        installedComponent = null
        installedWindow = null
    }

    private fun tryInstall(window: ComposeWindow): Boolean {
        val component = ExternalFileDropHost.composeDndComponent(window) ?: return false
        if (installedWindow === window && installedComponent === component) return true
        installedComponent?.let { previous ->
            if (previous.transferHandler === handler) {
                previous.transferHandler = null
            }
        }
        component.transferHandler = handler
        installedWindow = window
        installedComponent = component
        return true
    }

    /**
     * 面板内投放时由 [ExternalFileDropHost] 标记。
     *
     * macOS 上 Java → Java 的拖拽可能把目标接受的 COPY 误报成 MOVE；
     * 面板内部投放的移动/复制由 ViewModel 的任务完成，源侧不能再删一次。
     */
    internal fun markInternalDrop() {
        internalDrop.set(true)
    }

    internal fun consumeInternalDrop(): Boolean = internalDrop.getAndSet(false)

    /** 当前拖出的源面板；外部拖入时为 null。 */
    internal fun activeDragSourcePane(): PaneId? = dragSourcePane

    internal fun clearActiveDrag() {
        dragSourcePane = null
    }

    /** 拖拽结束时指针是否仍在本窗口内；用于兜底，避免窗口内松手误删源。 */
    internal fun isPointerInsideWindow(): Boolean {
        val component = installedComponent ?: return false
        val screenPoint = MouseInfo.getPointerInfo()?.location ?: return false
        SwingUtilities.convertPointFromScreen(screenPoint, component)
        return component.isShowing && component.contains(screenPoint)
    }

    fun start(
        entries: List<FileEntry>,
        sourcePane: PaneId,
        thumbnails: Map<String, ImageBitmap>,
        icons: Map<String, ImageBitmap>,
        density: Float,
        onMoveCompleted: (List<FileEntry>) -> Unit,
    ): Boolean {
        val component = installedComponent ?: return false
        val localEntries = localEntriesForDrag(entries)
        if (localEntries.isEmpty()) return false
        val previews = localEntries.map { entry ->
            (thumbnails[entry.location.path] ?: icons[entry.location.path])?.toDragPreviewImage()
        }
        if (!handler.begin(
                files = localEntries.map { File(it.location.path) },
                entries = localEntries,
                previews = previews,
                density = density,
                onMoveCompleted = onMoveCompleted,
            )
        ) {
            return false
        }
        dragSourcePane = sourcePane
        internalDrop.set(false)
        if (component.transferHandler !== handler) {
            // Compose 的拖放管理器可能在窗口初始化时再次安装自己的 transferHandler。
            component.transferHandler = handler
        }

        val point = currentPointerPoint(component)
        return runCatching {
            handler.exportAsDrag(
                component,
                MouseEvent(
                    component,
                    MouseEvent.MOUSE_DRAGGED,
                    System.currentTimeMillis(),
                    0,
                    point.x,
                    point.y,
                    0,
                    false,
                ),
                TransferHandler.MOVE,
            )
            true
        }.getOrElse { error ->
            System.err.println(TAG + " start drag failed: " + error)
            handler.cancelPending()
            clearActiveDrag()
            false
        }
    }

    private fun currentPointerPoint(component: JComponent): Point {
        val point = MouseInfo.getPointerInfo()?.location ?: return Point(0, 0)
        SwingUtilities.convertPointFromScreen(point, component)
        return point
    }
}

private class FileDragTransferHandler : TransferHandler() {

    private var files: List<File> = emptyList()
    private var entries: List<FileEntry> = emptyList()
    private var onMoveCompleted: ((List<FileEntry>) -> Unit)? = null
    private var dragImage: Image? = null
    private var dragImageOffset: Point = Point(0, 0)
    private var active = false

    fun begin(
        files: List<File>,
        entries: List<FileEntry>,
        previews: List<BufferedImage?>,
        density: Float,
        onMoveCompleted: (List<FileEntry>) -> Unit,
    ): Boolean {
        if (active) return false
        this.files = files
        this.entries = entries
        this.onMoveCompleted = onMoveCompleted
        this.dragImage = runCatching { buildDragImage(files, previews, density) }.getOrNull()
        this.dragImageOffset = dragImageOffset(density)
        this.active = true
        return true
    }

    fun cancelPending() {
        reset()
    }

    override fun getSourceActions(component: JComponent?): Int =
        TransferHandler.COPY or TransferHandler.MOVE or TransferHandler.LINK

    override fun getDragImage(): Image? = dragImage

    override fun getDragImageOffset(): Point = dragImageOffset

    override fun createTransferable(component: JComponent?): Transferable? =
        files.takeIf { it.isNotEmpty() }?.let(::FileListTransferable)

    override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
        val completedEntries = entries
        val callback = onMoveCompleted
        val internal = ExternalFileDragHost.consumeInternalDrop()
        val pointerInsideWindow = ExternalFileDragHost.isPointerInsideWindow()
        reset()
        if (shouldDeleteMovedSource(action, internal, pointerInsideWindow)) {
            callback?.invoke(completedEntries)
        }
    }

    private fun reset() {
        files = emptyList()
        entries = emptyList()
        onMoveCompleted = null
        dragImage = null
        dragImageOffset = Point(0, 0)
        active = false
        ExternalFileDragHost.clearActiveDrag()
    }
}

private const val DRAG_ICON_DP = 80
private const val DRAG_OFFSET_DP = 8
private const val DRAG_MAX_ICONS = 5
private const val DRAG_BADGE_HEIGHT_DP = 18
private const val DRAG_BADGE_GAP_DP = 6
private const val DRAG_PADDING_DP = 2
private const val DRAG_BADGE_FONT_DP = 12

/**
 * 生成拖拽预览：最多 5 个 80dp × 80dp 的缩略图/列表图标，按 8dp 偏移叠放，
 * 下方 Badge 显示拖出总数。
 *
 * 图片/视频优先使用 AsyncImage 缩略图，其他文件使用列表项图标；两者都缺失时才用
 * 系统文件图标。预览按 density 渲染成像素图，macOS 按屏幕缩放显示为逻辑尺寸。
 */
private fun buildDragImage(
    files: List<File>,
    previews: List<BufferedImage?>,
    density: Float,
): Image {
    val icons = files.take(DRAG_MAX_ICONS)
    val thumbnails = previews.take(DRAG_MAX_ICONS)
    val count = files.size

    val iconSize = (DRAG_ICON_DP * density).roundToInt().coerceAtLeast(1)
    val offset = (DRAG_OFFSET_DP * density).roundToInt()
    val padding = (DRAG_PADDING_DP * density).roundToInt()
    val badgeHeight = (DRAG_BADGE_HEIGHT_DP * density).roundToInt().coerceAtLeast(1)
    val badgeGap = (DRAG_BADGE_GAP_DP * density).roundToInt()
    val cornerRadius = 10f * density
    val stackSize = iconSize + offset * (icons.size.coerceAtLeast(1) - 1)

    val font = Font(
        Font.SANS_SERIF,
        Font.BOLD,
        (DRAG_BADGE_FONT_DP * density).roundToInt().coerceAtLeast(1),
    )
    val badgeText = count.toString()
    val measureGraphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
    val badgeWidth = try {
        measureGraphics.font = font
        max(
            (24 * density).roundToInt(),
            measureGraphics.fontMetrics.stringWidth(badgeText) + (14 * density).roundToInt(),
        )
    } finally {
        measureGraphics.dispose()
    }

    val width = max(stackSize, badgeWidth) + padding * 2
    val height = stackSize + badgeGap + badgeHeight + padding * 2
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics: Graphics2D = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val fileSystemView = FileSystemView.getFileSystemView()
        val systemIcons = icons.mapIndexed { index, file ->
            if (thumbnails.getOrNull(index) != null) {
                null
            } else {
                runCatching {
                    fileSystemView.getSystemIcon(file, iconSize, iconSize)
                }.getOrNull()
            }
        }

        // 倒序绘制，第一个条目位于最上层、左上角。
        for (index in icons.indices.reversed()) {
            val x = padding + index * offset
            val y = padding + index * offset
            val thumbnail = thumbnails.getOrNull(index)
            val drewThumbnail = thumbnail != null && runCatching {
                val previousClip = graphics.clip
                graphics.clip = RoundRectangle2D.Float(
                    x.toFloat(),
                    y.toFloat(),
                    iconSize.toFloat(),
                    iconSize.toFloat(),
                    cornerRadius,
                    cornerRadius,
                )
                try {
                    graphics.drawImage(thumbnail, x, y, iconSize, iconSize, null)
                } finally {
                    graphics.clip = previousClip
                }
            }.isSuccess
            if (drewThumbnail) continue

            val icon = systemIcons[index]
            val painted = icon != null && runCatching {
                icon.paintIcon(null, graphics, x, y)
            }.isSuccess
            if (!painted) {
                graphics.color = Color(0, 0, 0, 36)
                graphics.fillRoundRect(x, y, iconSize, iconSize, cornerRadius.toInt(), cornerRadius.toInt())
                graphics.color = Color(0, 0, 0, 70)
                graphics.drawRoundRect(x, y, iconSize - 1, iconSize - 1, cornerRadius.toInt(), cornerRadius.toInt())
            }
        }

        val badgeX = (width - badgeWidth) / 2
        val badgeY = height - padding - badgeHeight
        graphics.color = Color(0, 0, 0, 200)
        graphics.fillRoundRect(
            badgeX,
            badgeY,
            badgeWidth,
            badgeHeight,
            badgeHeight,
            badgeHeight,
        )
        graphics.color = Color(255, 255, 255, 245)
        graphics.font = font
        val metrics = graphics.fontMetrics
        val textX = badgeX + (badgeWidth - metrics.stringWidth(badgeText)) / 2
        val textY = badgeY + (badgeHeight - metrics.height) / 2 + metrics.ascent
        graphics.drawString(badgeText, textX, textY)
    } finally {
        graphics.dispose()
    }
    return image
}

/** macOS 的 dragImageOffset 使用逻辑点，其他平台用图像像素。 */
private fun dragImageOffset(density: Float): Point {
    val padding = (DRAG_PADDING_DP * density).roundToInt()
    val iconSize = (DRAG_ICON_DP * density).roundToInt()
    val center = padding + iconSize / 2
    return if (isMacOs()) {
        val logical = (center / density).roundToInt()
        Point(logical, logical)
    } else {
        Point(center, center)
    }
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

/** 目标以 MOVE 完成、不是面板内部投放、且指针已离开本窗口时，源文件才需要删除。 */
internal fun shouldDeleteMovedSource(
    action: Int,
    internalDrop: Boolean,
    pointerInsideWindow: Boolean,
): Boolean = !internalDrop && !pointerInsideWindow && (action and TransferHandler.MOVE) != 0

/** Compose 缩略图 → AWT 拖拽预览位图；读取失败时返回 null，由系统图标兜底。 */
internal fun ImageBitmap.toDragPreviewImage(): BufferedImage? = runCatching {
    val pixels = toPixelMap()
    if (pixels.width <= 0 || pixels.height <= 0) return@runCatching null
    val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
    val offset = pixels.bufferOffset
    val stride = pixels.stride
    if (offset == 0 && stride == pixels.width) {
        image.setRGB(0, 0, pixels.width, pixels.height, pixels.buffer, 0, pixels.width)
    } else {
        val row = IntArray(pixels.width)
        for (y in 0 until pixels.height) {
            System.arraycopy(pixels.buffer, offset + y * stride, row, 0, pixels.width)
            image.setRGB(0, y, pixels.width, 1, row, 0, pixels.width)
        }
    }
    image
}.getOrNull()

/** 只保留本地存在的文件/目录，去重后保持原顺序。 */
internal fun localEntriesForDrag(entries: List<FileEntry>): List<FileEntry> =
    entries.asSequence()
        .filter { it.location.source.kind == FileSourceKind.Local }
        .filter { File(it.location.path).exists() }
        .distinctBy { it.location.path }
        .toList()

/** 拖给其他程序的 AWT 传输对象：只提供 javaFileListFlavor。 */
internal class FileListTransferable(
    private val files: List<File>,
) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        require(flavor == DataFlavor.javaFileListFlavor) { "Unsupported flavor: " + flavor }
        return files
    }
}

actual fun startFileDragOut(
    entries: List<FileEntry>,
    sourcePane: PaneId,
    thumbnails: Map<String, ImageBitmap>,
    icons: Map<String, ImageBitmap>,
    density: Float,
    onMoveCompleted: (List<FileEntry>) -> Unit,
): Boolean = ExternalFileDragHost.start(
    entries = entries,
    sourcePane = sourcePane,
    thumbnails = thumbnails,
    icons = icons,
    density = density,
    onMoveCompleted = onMoveCompleted,
)
