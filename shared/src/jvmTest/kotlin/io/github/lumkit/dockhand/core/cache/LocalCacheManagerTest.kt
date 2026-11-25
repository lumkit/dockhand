package io.github.lumkit.dockhand.core.cache

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalCacheManagerTest {
    @Test
    fun reportsBreakdownAndClearsOnlyWhenPending() = runBlocking {
        val root = Files.createTempDirectory("dockhand-cache").toFile()
        try {
            root.resolve("video-thumbnails").mkdirs()
            root.resolve("video-thumbnails/a.png").writeBytes(ByteArray(8))
            root.resolve("dockhand.db").writeBytes(ByteArray(16))
            root.resolve("dockhand.db-wal").writeBytes(ByteArray(4))
            root.resolve("settings.preferences_pb").writeBytes(ByteArray(2))

            val manager = LocalCacheManager(root)
            val breakdown = manager.breakdown()
            assertEquals(2, breakdown.preferencesBytes)
            assertEquals(20, breakdown.databaseBytes)
            assertEquals(8, breakdown.thumbnailBytes)
            assertEquals(30, breakdown.totalBytes)

            manager.clearIfPending()
            assertTrue(root.resolve("dockhand.db").exists())

            manager.markClearPending()
            manager.clearIfPending()
            assertFalse(root.resolve("dockhand.db").exists())
            assertFalse(root.resolve("settings.preferences_pb").exists())
            assertFalse(root.resolve("video-thumbnails").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
