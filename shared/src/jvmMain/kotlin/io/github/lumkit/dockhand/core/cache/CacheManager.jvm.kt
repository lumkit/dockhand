package io.github.lumkit.dockhand.core.cache

import io.github.lumkit.dockhand.core.platform.defaultAppDataDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LocalCacheManager(
    private val dataDir: File = defaultAppDataDirectory(),
) : CacheManager {
    private val pending = AtomicBoolean(false)

    override suspend fun breakdown(): CacheBreakdown = withContext(Dispatchers.IO) {
        CacheBreakdown(
            preferencesBytes = fileSize(File(dataDir, "settings.preferences_pb")),
            databaseBytes = databaseFiles().sumOf { fileSize(it) },
            thumbnailBytes = fileSize(File(dataDir, "video-thumbnails")),
        )
    }

    override fun markClearPending() {
        pending.set(true)
    }

    override fun clearIfPending() {
        if (!pending.getAndSet(false)) return
        File(dataDir, "settings.preferences_pb").delete()
        databaseFiles().forEach { it.delete() }
        File(dataDir, "video-thumbnails").deleteRecursively()
    }

    private fun databaseFiles(): List<File> =
        dataDir.listFiles()
            .orEmpty()
            .filter { it.name == "dockhand.db" || it.name.startsWith("dockhand.db-") }

    private fun fileSize(file: File): Long = when {
        file.isFile -> file.length()
        file.isDirectory -> file.listFiles().orEmpty().sumOf { fileSize(it) }
        else -> 0L
    }
}
