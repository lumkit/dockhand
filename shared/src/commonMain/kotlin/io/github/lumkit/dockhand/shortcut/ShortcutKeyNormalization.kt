package io.github.lumkit.dockhand.shortcut

import androidx.compose.ui.input.key.Key

/**
 * 归一化后的按键码，用于消除 Compose 桌面端 Key 中包含的 key location 信息。
 * 快捷键录制、默认绑定和 AWT 事件可能分别使用裸 keycode 或打包后的 keycode。
 */
internal expect fun Key.normalizedKeyCode(): Long
