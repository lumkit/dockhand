package io.github.lumkit.dockhand.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * 当前窗口高度的指定比例，用于限制底部 sheet 的最大高度。
 * 读取 containerDpSize，窗口尺寸变化时会触发重组并实时更新。
 */
@Composable
internal fun windowSheetMaxHeight(fraction: Float = 0.8f): Dp =
    LocalWindowInfo.current.containerDpSize.height * fraction
