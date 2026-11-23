package io.github.dockhand

import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        width = 1000.dp,
        height = 600.dp,
    )

    MiuixWindow(
        onCloseRequest = ::exitApplication,
        title = "Dockhand",
        state = windowState,
        properties = MiuixWindowProperties.default(
            minSize = DpSize(1000.dp, 600.dp),
            backgroundType = MiuixWindowBackgroundType.Vibrancy,
            maximizeOrRestoreButtonEnabled = false,
        ),
    ) {
        App()
    }
}