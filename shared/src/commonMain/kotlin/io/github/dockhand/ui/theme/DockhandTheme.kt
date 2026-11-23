package io.github.dockhand.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 把 [ColorSchemeMode] 解析成“当前是否深色”，供开关等 UI 使用。 */
@Composable
fun ColorSchemeMode.isDarkTheme(): Boolean = when (this) {
    ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> isSystemInDarkTheme()
    ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
    ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
}

/**
 * 应用主题。颜色方案由 miuix 的 [ThemeController] 统一决定：
 * 它按 [ThemeController.colorSchemeMode] 在 light/dark 之间选择，Monet* 模式还会基于种子色生成。
 */
@Composable
fun DockhandTheme(
    controller: ThemeController,
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    MiuixTheme(
        controller = controller,
        content = content,
        textStyles = MiuixTheme.textStyles.let {
            if (fontFamily == null) {
                it
            } else {
                it.copy(
                    main = MiuixTheme.textStyles.main.copy(
                        fontFamily = fontFamily
                    ),
                    paragraph = MiuixTheme.textStyles.paragraph.copy(
                        fontFamily = fontFamily
                    ),
                    body1 = MiuixTheme.textStyles.body1.copy(
                        fontFamily = fontFamily
                    ),
                    body2 = MiuixTheme.textStyles.body2.copy(
                        fontFamily = fontFamily
                    ),
                    button = MiuixTheme.textStyles.button.copy(
                        fontFamily = fontFamily
                    ),
                    footnote1 = MiuixTheme.textStyles.footnote1.copy(
                        fontFamily = fontFamily
                    ),
                    footnote2 = MiuixTheme.textStyles.footnote2.copy(
                        fontFamily = fontFamily
                    ),
                    headline1 = MiuixTheme.textStyles.headline1.copy(
                        fontFamily = fontFamily
                    ),
                    headline2 = MiuixTheme.textStyles.headline2.copy(
                        fontFamily = fontFamily
                    ),
                    subtitle = MiuixTheme.textStyles.subtitle.copy(
                        fontFamily = fontFamily
                    ),
                    title1 = MiuixTheme.textStyles.title1.copy(
                        fontFamily = fontFamily
                    ),
                    title2 = MiuixTheme.textStyles.title2.copy(
                        fontFamily = fontFamily
                    ),
                    title3 = MiuixTheme.textStyles.main.copy(
                        fontFamily = fontFamily
                    ),
                    title4 = MiuixTheme.textStyles.title4.copy(
                        fontFamily = fontFamily
                    )
                )
            }
        },
    )
}
