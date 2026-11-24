package io.github.lumkit.dockhand

import androidx.compose.ui.Alignment
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lumkit.dockhand.data.createAppContainer
import io.github.lumkit.dockhand.shortcut.ShortcutDispatcher
import io.github.lumkit.dockhand.ui.appLogoPainter
import io.github.lumkit.dockhand.domain.model.WindowSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.window.MiuixWindow
import top.yukonga.miuix.kmp.window.MiuixWindowBackgroundType
import top.yukonga.miuix.kmp.window.MiuixWindowProperties

/**
 * 窗口装饰交给 miuix-window：
 *  - macOS 上它保持系统装饰（红黄绿按钮原生绘制），把 caption bar 画在标题栏区域；
 *  - caption bar 高度 40dp、按钮区 74dp 由它算好，全屏时自动变成 0；
 *  - 拖动、双击缩放、最大/还原由它内部的 AWT 监听器处理。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val container = remember { createAppContainer() }
    DisposableEffect(container) {
        onDispose { container.close() }
    }
    val savedSize = remember(container) {
        runBlocking {
            if (!container.settings.restoreWindowSize.first()) {
                null
            } else {
                container.settings.windowSize.first()
            }
        }
    }
    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        width = max(1000, savedSize?.widthDp ?: 1000).dp,
        height = max(600, savedSize?.heightDp ?: 600).dp,
    )

    MiuixWindow(
        onCloseRequest = {
            if (windowState.placement == WindowPlacement.Floating) {
                runBlocking {
                    if (container.settings.restoreWindowSize.first()) {
                        container.settings.setWindowSize(
                            WindowSize(
                                widthDp = windowState.size.width.value.roundToInt(),
                                heightDp = windowState.size.height.value.roundToInt(),
                            )
                        )
                    }
                }
            }
            exitApplication()
        },
        title = "Dockhand",
        icon = appLogoPainter(),
        state = windowState,
        onPreviewKeyEvent = { event -> ShortcutDispatcher.dispatch(event) },
        properties = MiuixWindowProperties.default(
            minSize = DpSize(1000.dp, 600.dp),
            backgroundType = MiuixWindowBackgroundType.Vibrancy,
            maximizeOrRestoreButtonEnabled = false,
        ),
    ) {
        App(container)
    }
}