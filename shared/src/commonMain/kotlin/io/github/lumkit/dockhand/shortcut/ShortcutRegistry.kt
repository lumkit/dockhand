package io.github.lumkit.dockhand.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** 可绑定快捷键的应用动作。 */
enum class ShortcutAction {
    SelectAll,
    ClearSelection,
    SelectPrevious,
    SelectNext,
    SelectLeft,
    SelectRight,
    OpenContextMenu,
    Copy,
    Move,
    Delete,
    Rename,
    Compress,
    Trash,
    ExtendSelectionUp,
    ExtendSelectionDown,
    ExtendSelectionLeft,
    ExtendSelectionRight,
}

/** 一个按键组合；meta 对应 macOS Command。 */
data class ShortcutKey(
    val key: Key,
    val meta: Boolean = false,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    fun matches(event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown &&
            event.key == key &&
            event.isMetaPressed == meta &&
            event.isCtrlPressed == ctrl &&
            event.isShiftPressed == shift &&
            event.isAltPressed == alt
}

data class ShortcutBinding(
    val action: ShortcutAction,
    val key: ShortcutKey,
)

/**
 * 通用快捷键映射。
 *
 * 新增动作时只需在 [ShortcutAction] 增加枚举并注册 binding，
 * UI 层统一通过 [resolve] 分发，不需要在各处硬编码按键判断。
 */
val DefaultShortcutBindings: Map<ShortcutAction, ShortcutKey> = mapOf(
    ShortcutAction.SelectAll to ShortcutKey(Key.A, meta = true),
    ShortcutAction.ClearSelection to ShortcutKey(Key.Escape),
    ShortcutAction.SelectPrevious to ShortcutKey(Key.DirectionUp, alt = true),
    ShortcutAction.SelectNext to ShortcutKey(Key.DirectionDown, alt = true),
    ShortcutAction.SelectLeft to ShortcutKey(Key.DirectionLeft, alt = true),
    ShortcutAction.SelectRight to ShortcutKey(Key.DirectionRight, alt = true),
    ShortcutAction.OpenContextMenu to ShortcutKey(Key.Enter, meta = true, shift = true),
    ShortcutAction.Copy to ShortcutKey(Key.C, meta = true, shift = true),
    ShortcutAction.Move to ShortcutKey(Key.M, meta = true, shift = true),
    ShortcutAction.Delete to ShortcutKey(Key.Backspace, meta = true, shift = true),
    ShortcutAction.Rename to ShortcutKey(Key.R, meta = true, shift = true),
    ShortcutAction.Compress to ShortcutKey(Key.K, meta = true, shift = true),
    ShortcutAction.Trash to ShortcutKey(Key.Backspace, meta = true),
    ShortcutAction.ExtendSelectionUp to ShortcutKey(Key.DirectionUp, shift = true),
    ShortcutAction.ExtendSelectionDown to ShortcutKey(Key.DirectionDown, shift = true),
    ShortcutAction.ExtendSelectionLeft to ShortcutKey(Key.DirectionLeft, shift = true),
    ShortcutAction.ExtendSelectionRight to ShortcutKey(Key.DirectionRight, shift = true),
)

fun ShortcutKey.encode(): String =
    listOf(
        key.keyCode.toString(),
        if (meta) "1" else "0",
        if (ctrl) "1" else "0",
        if (shift) "1" else "0",
        if (alt) "1" else "0",
    ).joinToString(":")

fun encodeShortcuts(keys: List<ShortcutKey>): String =
    keys.joinToString("|") { it.encode() }

fun decodeShortcuts(value: String?): List<ShortcutKey> {
    if (value.isNullOrEmpty()) return emptyList()
    return value.split('|').mapNotNull(::decodeShortcut)
}

fun decodeShortcut(value: String?): ShortcutKey? {
    if (value.isNullOrBlank()) return null
    val parts = value.split(':')
    if (parts.size != 5) return null
    val keyCode = parts[0].toLongOrNull() ?: return null
    return ShortcutKey(
        key = Key(keyCode),
        meta = parts[1] == "1",
        ctrl = parts[2] == "1",
        shift = parts[3] == "1",
        alt = parts[4] == "1",
    )
}

fun ShortcutKey.display(): String {
    val parts = mutableListOf<String>()
    if (meta) parts += "⌘"
    if (ctrl) parts += "⌃"
    if (alt) parts += "⌥"
    if (shift) parts += "⇧"
    parts += when (key) {
            Key.A -> "A"
            Key.B -> "B"
            Key.C -> "C"
            Key.D -> "D"
            Key.E -> "E"
            Key.F -> "F"
            Key.G -> "G"
            Key.H -> "H"
            Key.I -> "I"
            Key.J -> "J"
            Key.K -> "K"
            Key.L -> "L"
            Key.M -> "M"
            Key.N -> "N"
            Key.O -> "O"
            Key.P -> "P"
            Key.Q -> "Q"
            Key.R -> "R"
            Key.S -> "S"
            Key.T -> "T"
            Key.U -> "U"
            Key.V -> "V"
            Key.W -> "W"
            Key.X -> "X"
            Key.Y -> "Y"
            Key.Z -> "Z"
            Key.Escape -> "Esc"
            Key.Enter -> "Enter"
            Key.Spacebar -> "Space"
            Key.Delete -> "Delete"
            Key.Backspace -> "Backspace"
            Key.Tab -> "Tab"
            Key.DirectionUp -> "↑"
            Key.DirectionDown -> "↓"
            Key.DirectionLeft -> "←"
            Key.DirectionRight -> "→"
            else -> key.keyCode.toString()
        }
    return parts.joinToString("+")
}

fun Key.isModifierKey(): Boolean = this == Key.ShiftLeft ||
    this == Key.ShiftRight ||
    this == Key.CtrlLeft ||
    this == Key.CtrlRight ||
    this == Key.AltLeft ||
    this == Key.AltRight ||
    this == Key.MetaLeft ||
    this == Key.MetaRight

class ShortcutRegistry(
    bindings: List<ShortcutBinding>,
) {
    private val bindings = bindings.toList()

    fun resolve(event: KeyEvent): ShortcutAction? =
        bindings.firstOrNull { it.key.matches(event) }?.action
}

val DefaultShortcutRegistry = ShortcutRegistry(
    DefaultShortcutBindings.map { (action, key) -> ShortcutBinding(action, key) }
)
