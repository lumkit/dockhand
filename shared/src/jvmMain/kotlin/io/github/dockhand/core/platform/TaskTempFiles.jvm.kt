package io.github.dockhand.core.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

actual fun createTaskTempDirectory(prefix: String): String =
    Files.createTempDirectory(prefix).toAbsolutePath().toString()

actual fun deleteTaskTempPath(path: String) {
    val target = Path.of(path)
    if (!Files.exists(target)) return
    if (Files.isDirectory(target)) {
        Files.walk(target).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    } else {
        Files.deleteIfExists(target)
    }
}
