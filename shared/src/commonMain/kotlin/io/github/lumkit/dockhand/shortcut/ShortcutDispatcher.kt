package io.github.lumkit.dockhand.shortcut

import androidx.compose.ui.input.key.KeyEvent

/**
 * 窗口级快捷键分发器。
 *
 * Compose 的普通 onKeyEvent 依赖焦点，某些桌面窗口在焦点落到子节点时
 * 根节点收不到按键。窗口层统一把未消费的按键交给这里，再由 HomeScreen
 * 注册的处理器解析。
 */
object ShortcutDispatcher {
    private var handler: ((KeyEvent) -> Boolean)? = null
    private var captureHandler: ((KeyEvent) -> Boolean)? = null

    fun setHandler(handler: ((KeyEvent) -> Boolean)?) {
        this.handler = handler
    }

    /** 快捷键录制期间优先截获按键。 */
    fun setCaptureHandler(handler: ((KeyEvent) -> Boolean)?) {
        captureHandler = handler
    }

    fun dispatch(event: KeyEvent): Boolean =
        captureHandler?.invoke(event) == true || handler?.invoke(event) == true
}
