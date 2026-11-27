package io.github.lumkit.dockhand.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.lumkit.dockhand.shortcut.ShortcutDispatcher

/**
 * 在 Dialog 或 Sheet 显示期间暂停窗口级快捷键，关闭后恢复。
 *
 * 输入框需要自己处理 Cmd+A、Cmd+C 等编辑快捷键；模态层存在时，
 * 窗口级快捷键分发器直接放行按键，避免抢焦点控件的按键。
 */
@Composable
fun DialogModalityEffect(open: Boolean = true) {
    val owner = remember { Any() }
    DisposableEffect(owner, open) {
        if (open) {
            ShortcutDispatcher.setModal(owner, true)
        }
        onDispose {
            ShortcutDispatcher.setModal(owner, false)
        }
    }
}
