package io.github.lumkit.dockhand.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MacTerminalLauncher : TerminalLauncher {
    override suspend fun open(directory: String, newWindow: Boolean) = withContext(Dispatchers.IO) {
        if (newWindow) {
            // Terminal 支持直接打开目录；不会先启动 shell 再执行 cd。
            runProcess(listOf("open", "-a", "Terminal", directory))
        } else {
            val command = "cd -- " + shellQuote(directory) + "; clear"
            val scriptCommand = command
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            val appleScript = """
                tell application "Terminal" to activate
                tell application "System Events"
                    keystroke "t" using {command down}
                end tell
                delay 0.2
                tell application "Terminal"
                    do script "$scriptCommand" in front window
                    activate
                end tell
            """.trimIndent()
            runProcess(listOf("osascript", "-e", appleScript))
        }
    }

    private fun runProcess(command: List<String>) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException(
                output.trim().ifBlank { "打开终端失败，退出码 $exitCode" }
            )
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
