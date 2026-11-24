package io.github.lumkit.dockhand.core.platform

/** 在平台终端中打开目录。 */
interface TerminalLauncher {
    /**
     * @param directory 要进入的本地目录绝对路径
     * @param newWindow true 打开新窗口，false 优先在现有窗口新建标签页
     */
    suspend fun open(directory: String, newWindow: Boolean)
}

object NoopTerminalLauncher : TerminalLauncher {
    override suspend fun open(directory: String, newWindow: Boolean) {
        error("当前平台不支持打开终端")
    }
}
