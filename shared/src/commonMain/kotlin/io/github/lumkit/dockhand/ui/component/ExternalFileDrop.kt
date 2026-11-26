package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.text_drop_copy
import dockhand.shared.generated.resources.text_drop_move
import dockhand.shared.generated.resources.text_drop_unknown
import dockhand.shared.generated.resources.text_drop_unavailable
import io.github.lumkit.dockhand.ui.screen.home.PaneId
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 外部拖拽上报的动作。
 *
 * 移动由应用自己完成（先复制、再删除源），不依赖系统在 drop 后删除源文件。
 */
enum class ExternalDropAction {
    Copy,
    Move,
    Link,
}

/** 指针当前悬停的面板与拖拽意图；[action] 为 null 表示没有明确动作。 */
@Immutable
data class ExternalDropHover(
    val paneId: PaneId,
    val action: ExternalDropAction?,
)

/**
 * 外部文件拖入的控制器。
 *
 * 平台层（AWT DropTarget）把指针位置和动作写进来，UI 只读 [hover] 显示蒙层；
 * drop 通过 [bridge] 交给 ViewModel。所有 bounds 使用与 AWT 事件一致的逻辑坐标。
 */
@Stable
class ExternalDropController {

    private val paneBounds = mutableStateMapOf<PaneId, Rect>()

    var hover: ExternalDropHover? by mutableStateOf(null)
        private set

    var bridge: ExternalDropBridge? = null

    fun updatePaneBounds(paneId: PaneId, bounds: Rect) {
        paneBounds[paneId] = bounds
    }

    fun removePaneBounds(paneId: PaneId) {
        paneBounds.remove(paneId)
        if (hover?.paneId == paneId) hover = null
    }

    fun paneAt(position: Offset): PaneId? =
        paneBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(position) }?.key

    /** 拖拽移动到 [position]；返回是否落在某个面板内。 */
    fun onDragMoved(position: Offset, action: ExternalDropAction?): Boolean {
        val paneId = paneAt(position)
        if (paneId == null) {
            hover = null
            return false
        }
        val previous = hover
        hover = ExternalDropHover(paneId, action)
        if (previous?.paneId != paneId) {
            bridge?.onPaneFocused(paneId)
        }
        return true
    }

    fun onDragExited() {
        hover = null
    }

    /** 松手；返回是否已经交给上层处理。 */
    fun onDrop(position: Offset, paths: List<String>, action: ExternalDropAction?): Boolean {
        val paneId = paneAt(position)
        hover = null
        if (paneId == null || paths.isEmpty()) return false
        val bridge = bridge ?: return false
        bridge.onDrop(paneId, paths, action)
        return true
    }
}

/** 平台层与界面层的桥：焦点切换与 drop 分发。 */
interface ExternalDropBridge {
    fun onPaneFocused(paneId: PaneId)

    fun onDrop(paneId: PaneId, paths: List<String>, action: ExternalDropAction?)
}

val LocalExternalDropController = staticCompositionLocalOf<ExternalDropController?> { null }

/**
 * 拖拽悬停蒙层：面板高亮 + 动作提示 + 目标路径。
 *
 * 只是绘制层，不挂 `pointerInput`，不会吃掉面板的鼠标事件。
 */
@Composable
fun ExternalDropOverlay(
    action: ExternalDropAction?,
    destinationPath: String?,
    modifier: Modifier = Modifier,
) {
    val accent = MiuixTheme.colorScheme.primary
    val overlayShape = RoundedCornerShape(12.dp)
    val title = when {
        destinationPath == null -> Res.string.text_drop_unavailable
        action == ExternalDropAction.Copy -> Res.string.text_drop_copy
        action == ExternalDropAction.Move -> Res.string.text_drop_move
        else -> Res.string.text_drop_unknown
    }

    Box(
        modifier = modifier
            .clip(overlayShape)
            .background(accent.copy(alpha = 0.08f))
            .border(2.dp, accent, overlayShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(title),
                style = MiuixTheme.textStyles.body2,
                color = accent,
            )
            if (destinationPath != null) {
                Text(
                    text = destinationPath,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
