package io.github.lumkit.dockhand.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

internal actual fun Key.normalizedKeyCode(): Long = nativeKeyCode.toLong()
