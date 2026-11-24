package io.github.dockhand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    App(rememberAppContainer())
}

@Composable
fun App(container: AppContainer) {
    val mode by container.settings.colorSchemeMode.collectAsState(initial = ColorSchemeMode.System)
    val keyColor by container.settings.keyColor.collectAsState(initial = null)
    val interfaceScale by container.settings.interfaceScale.collectAsState(initial = 100)
    // ThemeController 的 colorSchemeMode 是只读状态，切换模式时重建一个即可
    val controller = remember(mode, keyColor) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor ?: Color(0xFF6750A4),
        )
    }
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, interfaceScale) {
        Density(
            density = baseDensity.density * interfaceScale / 100f,
            fontScale = baseDensity.fontScale,
        )
    }

    DockhandTheme(controller = controller) {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalDensity provides scaledDensity,
        ) {
            HomeScreen()
        }
    }
}
