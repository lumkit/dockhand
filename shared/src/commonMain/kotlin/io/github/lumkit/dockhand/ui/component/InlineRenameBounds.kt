package io.github.lumkit.dockhand.ui.component

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect

/** 行内重命名编辑框在根布局中的位置；用于点击编辑框外部时退出编辑。 */
internal val LocalInlineRenameBounds = compositionLocalOf<MutableState<Rect?>?> { null }
