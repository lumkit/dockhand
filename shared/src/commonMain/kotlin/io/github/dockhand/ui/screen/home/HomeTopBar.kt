package io.github.dockhand.ui.screen.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import io.github.dockhand.domain.model.FileBreadcrumb
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.breadcrumbs
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
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
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val windowState = LocalWindowState.current
    val crumbs = remember(location) { location?.breadcrumbs().orEmpty() }
    val items = remember(crumbs) {
        crumbs.map { BreadcrumbItem(path = it.path, text = it.label) }
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
            BreadcrumbBar(
                modifier = Modifier.weight(1f),
                items = items,
                onItemClick = { index ->
                    crumbs.getOrNull(index)?.let(onBreadcrumbClick)
                },
            )
        }

        actions()
    }
}