package io.github.lumkit.dockhand.core.platform

import java.io.File

private const val APP_DIR_NAME = "io.github.lumkit.dockhand"
private const val LEGACY_APP_DIR_NAME = "io.github.dockhand"

/**
 * 应用数据目录。macOS 惯例：~/Library/Application Support/<bundle id>
 * 之后要上 App Sandbox 时会自动落到容器里，无需改代码。
 */
fun defaultAppCacheDirectory(): File {
    val home = System.getProperty("user.home")
    return File(home, "Library/Caches/$APP_DIR_NAME").apply { mkdirs() }
}

fun defaultAppDataDirectory(): File {
    val home = System.getProperty("user.home")
    val directory = File(home, "Library/Application Support/$APP_DIR_NAME")
    if (!directory.exists()) {
        val legacy = File(home, "Library/Application Support/$LEGACY_APP_DIR_NAME")
        if (legacy.isDirectory) {
            runCatching { legacy.copyRecursively(directory, overwrite = false) }
        }
    }
    return directory
}
