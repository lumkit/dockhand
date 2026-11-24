package io.github.lumkit.dockhand.ui.screen.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_copy_path
import io.github.lumkit.dockhand.domain.model.FileBreadcrumb
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.domain.model.breadcrumbs
import io.github.lumkit.dockhand.ui.clipboard.copyText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.window.LocalWindowState

/**
 * 顶栏：只展示“当前焦点面板”的路径。
 *
 * 路径 → 面包屑的转换是纯函数（[breadcrumbs]），
 * 所以 HomeScreen 只要把 [location] 传进来，路径就会随焦点/导航自动更新。
 */
@Composable
fun HomeTopBar(
    location: FileLocation?,
    onBreadcrumbClick: (FileBreadcrumb) -> Unit,
    onBreadcrumbSecondaryClick: (FileBreadcrumb) -> Unit = {},
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val windowState = LocalWindowState.current
    val crumbs = remember(location) { location?.breadcrumbs().orEmpty() }
    val items = remember(crumbs) {
        crumbs.map { BreadcrumbItem(path = it.path, text = it.label) }
    }
    val breadcrumbListState = rememberLazyListState()
    val density = LocalDensity.current
    val insideMarginPx = with(density) { 12.dp.toPx() }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var contextMenuCrumb by remember { mutableStateOf<FileBreadcrumb?>(null) }
    // 只有本地/挂载磁盘的路径可以复制；MTP 没有本机绝对路径。
    val canCopyPath = location?.source?.kind == FileSourceKind.Local
    val lastItemInfo = breadcrumbListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == items.lastIndex }
    val anchorX = with(density) {
        (insideMarginPx + (lastItemInfo?.offset ?: 0)).toDp()
    }
    val anchorWidth = with(density) {
        (lastItemInfo?.size ?: 0).coerceAtLeast(1).toDp()
    }

    LaunchedEffect(location) {
        contextMenuCrumb = null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
            .combinedClickable(
                indication = null,
                interactionSource = null,
                onClick = {},
                onDoubleClick = {
                    windowState.placement = when (windowState.placement) {
                        WindowPlacement.Floating -> WindowPlacement.Maximized
                        WindowPlacement.Maximized -> WindowPlacement.Floating
                        WindowPlacement.Fullscreen -> WindowPlacement.Fullscreen
                    }
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (items.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    // Miuix BreadcrumbBar 只暴露 onItemClick；在父节点用 Initial pass
                    // 观察右键，并把 x 坐标映射回可见的 breadcrumb item。
                    .pointerInput(items, breadcrumbListState, insideMarginPx, canCopyPath) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (
                                    event.type == PointerEventType.Press &&
                                    event.buttons.isSecondaryPressed
                                ) {
                                    val x = (event.changes.firstOrNull()?.position?.x ?: 0f) - insideMarginPx
                                    val index = breadcrumbListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info ->
                                            x >= info.offset && x <= info.offset + info.size
                                        }
                                        ?.index
                                    val crumb = index?.let(crumbs::getOrNull)
                                    if (
                                        canCopyPath &&
                                        index == items.lastIndex &&
                                        crumb != null
                                    ) {
                                        contextMenuCrumb = crumb
                                        onBreadcrumbSecondaryClick(crumb)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
            ) {
                BreadcrumbBar(
                    modifier = Modifier.fillMaxWidth(),
                    items = items,
                    listState = breadcrumbListState,
                    onItemClick = { index ->
                        crumbs.getOrNull(index)?.let(onBreadcrumbClick)
                    },
                )

                // 锚定在最后一个 breadcrumb item 下方；OverlayListPopup 会根据
                // 这个锚点的父布局计算下拉位置。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = anchorX, y = 8.dp)
                        .width(anchorWidth)
                        .height(32.dp)
                ) {
                    OverlayListPopup(
                        show = canCopyPath && contextMenuCrumb != null,
                        alignment = PopupPositionProvider.Align.Start,
                        onDismissRequest = { contextMenuCrumb = null },
                    ) {
                        ListPopupColumn {
                            BasicComponent(
                                title = stringResource(Res.string.action_copy_path),
                                onClick = {
                                    val path = contextMenuCrumb?.path
                                    contextMenuCrumb = null
                                    if (path != null) {
                                        clipboardScope.launch {
                                            clipboard.copyText(path)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        actions()
    }
}