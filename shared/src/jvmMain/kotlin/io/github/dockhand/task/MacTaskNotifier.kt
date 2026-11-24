package io.github.dockhand.task

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * macOS 上的系统通知。
 *
 * AWT 无法查询通知权限是否已授权；这里只判断 Tray 是否可用。
 * 系统拒绝展示时无法可靠感知，UI 仍保留 snackbar 兜底通道。
 */
class MacTaskNotifier : TaskNotifier {
    private val tray: SystemTray? = if (SystemTray.isSupported()) SystemTray.getSystemTray() else null
    private val icon: TrayIcon? = tray?.let {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        TrayIcon(image, "Dockhand").apply {
            isImageAutoSize = true
            runCatching { it.add(this) }
        }
    }

    override suspend fun notify(title: String, message: String): Boolean {
        val trayIcon = icon ?: return false
        return runCatching {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
            true
        }.getOrDefault(false)
    }
}
