package io.github.lumkit.dockhand.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.ui.screen.home.fileIcon
import org.jetbrains.compose.resources.DrawableResource

/**
 * 拖拽预览的图像缓存。
 *
 * - [putThumbnail]：AsyncImage 加载成功后把缩略图 painter 渲染成 ImageBitmap；
 * - [putIcon]：列表/网格条目的图标 painter（SVG 资源）渲染成 ImageBitmap；
 * - 拖拽开始时分别按路径/资源取快照，交给 AWT 宿主绘制预览。
 *
 * 缩略图按路径最多保留 [MAX_THUMBNAILS] 个；图标按资源最多 [MAX_ICONS] 个。
 * 只在 UI 线程读写（AsyncImage.onSuccess、LaunchedEffect 和长按拖出都在 UI 线程）。
 */
internal object DragPreviewCache {

    private const val MAX_THUMBNAILS = 128
    private const val MAX_ICONS = 64
    private const val MAX_RENDER_SIZE = 256
    private const val MIN_RENDER_SIZE = 48

    private val thumbnails = LinkedHashMap<String, ImageBitmap>()
    private val icons = HashMap<DrawableResource, ImageBitmap>()
    private var thumbnailRenderSize = 0
    private var iconRenderSize = 0

    fun putThumbnail(path: String, painter: Painter, sizePx: Int) {
        val size = sizePx.coerceIn(MIN_RENDER_SIZE, MAX_RENDER_SIZE)
        if (size != thumbnailRenderSize) {
            thumbnails.clear()
            thumbnailRenderSize = size
        }
        val image = runCatching { painter.toDragImage(size) }.getOrNull() ?: return
        thumbnails[path] = image
        while (thumbnails.size > MAX_THUMBNAILS) {
            val oldest = thumbnails.keys.firstOrNull() ?: break
            thumbnails.remove(oldest)
        }
    }

    fun putIcon(resource: DrawableResource, painter: Painter, sizePx: Int) {
        val size = sizePx.coerceIn(MIN_RENDER_SIZE, MAX_RENDER_SIZE)
        if (size != iconRenderSize) {
            icons.clear()
            iconRenderSize = size
        }
        if (icons.containsKey(resource)) return
        val image = runCatching { painter.toDragIcon(size) }.getOrNull() ?: return
        icons[resource] = image
        while (icons.size > MAX_ICONS) {
            val oldest = icons.keys.firstOrNull() ?: break
            icons.remove(oldest)
        }
    }

    fun thumbnailSnapshot(paths: Collection<String>): Map<String, ImageBitmap> =
        paths.mapNotNull { path -> thumbnails[path]?.let { path to it } }.toMap()

    /** 非图片/视频条目用列表项自己的图标；找不到时由宿主回退系统图标。 */
    fun iconSnapshot(entries: List<FileEntry>): Map<String, ImageBitmap> =
        entries.mapNotNull { entry ->
            icons[entry.fileIcon()]?.let { entry.location.path to it }
        }.toMap()
}

private fun Painter.toDragImage(sizePx: Int): ImageBitmap {
    val image = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        with(this@toDragImage) {
            draw(Size(sizePx.toFloat(), sizePx.toFloat()))
        }
    }
    return image
}

/** 列表图标的 Image 使用 ContentScale.Fit；预览按同样的比例居中绘制，避免 SVG 被拉伸。 */
private fun Painter.toDragIcon(sizePx: Int): ImageBitmap {
    val image = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        val intrinsic = this@toDragIcon.intrinsicSize
        if (intrinsic.width > 0f && intrinsic.height > 0f) {
            val scale = min(sizePx / intrinsic.width, sizePx / intrinsic.height)
            val width = intrinsic.width * scale
            val height = intrinsic.height * scale
            translate((sizePx - width) / 2f, (sizePx - height) / 2f) {
                with(this@toDragIcon) {
                    draw(Size(width, height))
                }
            }
        } else {
            with(this@toDragIcon) {
                draw(Size(sizePx.toFloat(), sizePx.toFloat()))
            }
        }
    }
    return image
}
