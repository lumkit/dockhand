package io.github.lumkit.dockhand.shortcut

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortcutKeyTest {
    @OptIn(InternalComposeUiApi::class)
    @Test
    fun packedKeyMatchesRawShortcutKey() {
        val packed = Key(
            nativeKeyCode = AwtKeyEvent.VK_A,
            nativeKeyLocation = KEY_LOCATION_STANDARD,
        )
        val event = KeyEvent(
            key = packed,
            type = KeyEventType.KeyDown,
            isMetaPressed = true,
        )
        assertTrue(ShortcutKey(Key.A, meta = true).matches(event))
    }

    @Test
    fun displayUsesReadableKeyNames() {
        assertEquals("⌘+A", ShortcutKey(Key.A, meta = true).display())
        assertEquals("Esc", ShortcutKey(Key.Escape).display())
        assertEquals("↑", ShortcutKey(Key.DirectionUp).display())
    }
}