package io.github.dockhand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.dockhand.data.AppContainer
import io.github.dockhand.data.rememberAppContainer
import io.github.dockhand.ui.screen.home.HomeScreen
import io.github.dockhand.ui.theme.DockhandTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("not provided") }

/**
 * 应用根组合。
 *
 * 只做三件事：拿到依赖容器、交给界面层、不掺任何平台细节。
 * 窗口样式（沉浸式标题栏、insets）由 desktopApp 通过 CompositionLocal 提供。
 */
@Composable
fun App() {
    val container = rememberAppContainer()
    val mode by container.settings.colorSchemeMode.collectAsState(initial = ColorSchemeMode.System)
    // ThemeController 的 colorSchemeMode 是只读状态，切换模式时重建一个即可
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }

    DockhandTheme(controller = controller) {
        CompositionLocalProvider(
            LocalAppContainer provides container
        ) {
            HomeScreen()
        }
    }
}
