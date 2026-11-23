package io.github.dockhand.core.platform

import java.io.File

private const val APP_DIR_NAME = "io.github.dockhand"

/**
 * 应用数据目录。macOS 惯例：~/Library/Application Support/<bundle id>
 * 之后要上 App Sandbox 时会自动落到容器里，无需改代码。
 */
fun defaultAppDataDirectory(): File {
    val home = System.getProperty("user.home")
    return File(home, "Library/Application Support/$APP_DIR_NAME")
}
