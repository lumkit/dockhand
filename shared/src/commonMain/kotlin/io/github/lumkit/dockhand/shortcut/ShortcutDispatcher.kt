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

    /**
     * 当前打开的 Dialog、Sheet 等模态层的 owner。
     * 存在任意模态层时，窗口级快捷键不处理按键，让焦点控件优先处理，
     * 例如输入框的 Cmd+A、Cmd+C。
     */
    private val modalOwners = mutableSetOf<Any>()

    @Volatile
    private var onAllModalsClosed: (() -> Unit)? = null

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

    fun setModal(owner: Any, open: Boolean) {
        val wasOpen = modalOwners.isNotEmpty()
        if (open) {
            modalOwners.add(owner)
        } else {
            modalOwners.remove(owner)
        }
        if (wasOpen && modalOwners.isEmpty()) {
            onAllModalsClosed?.invoke()
        }
    }

    /**
     * 最后一个模态层关闭后回调，用于把焦点还给主窗口。
     */
    fun setModalClosedCallback(callback: (() -> Unit)?) {
        onAllModalsClosed = callback
    }

    fun dispatch(event: KeyEvent): Boolean {
        val capture = captureHandler
        if (capture?.invoke(event) == true) return true
        if (modalOwners.isNotEmpty()) return false
        return handler?.invoke(event) == true
    }
}