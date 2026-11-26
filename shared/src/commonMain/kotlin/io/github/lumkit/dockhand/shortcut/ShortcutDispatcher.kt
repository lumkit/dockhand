package io.github.lumkit.dockhand.shortcut

import androidx.compose.ui.input.key.KeyEvent

/**
 * 窗口级快捷键分发器。
 *
 * 处理器带有 owner token。旧页面销毁时只能清除自己注册的处理器，
 * 不能把新页面刚注册的处理器清空。
 */
object ShortcutDispatcher {
    @Volatile
    private var handlerOwner: Any? = null

    @Volatile
    private var handler: ((KeyEvent) -> Boolean)? = null

    @Volatile
    private var captureOwner: Any? = null

    @Volatile
    private var captureHandler: ((KeyEvent) -> Boolean)? = null

    fun setHandler(owner: Any, handler: ((KeyEvent) -> Boolean)?) {
        handlerOwner = owner
        this.handler = handler
    }

    fun clearHandler(owner: Any) {
        if (handlerOwner === owner) {
            handlerOwner = null
            handler = null
        }
    }

    /** 快捷键录制期间优先截获按键。 */
    fun setCaptureHandler(owner: Any, handler: ((KeyEvent) -> Boolean)?) {
        captureOwner = owner
        captureHandler = handler
    }

    fun clearCaptureHandler(owner: Any) {
        if (captureOwner === owner) {
            captureOwner = null
            captureHandler = null
        }
    }

    fun dispatch(event: KeyEvent): Boolean {
        val capture = captureHandler
        if (capture?.invoke(event) == true) return true
        return handler?.invoke(event) == true
    }
}
