package io.github.lumkit.dockhand.data

import io.github.lumkit.dockhand.data.database.createDatabase
import io.github.lumkit.dockhand.data.datastore.SettingsRepository
import io.github.lumkit.dockhand.data.datastore.createSettingsDataStore
import androidx.compose.ui.input.key.Key
import io.github.lumkit.dockhand.data.repository.TrashHistoryRepository
import io.github.lumkit.dockhand.shortcut.DefaultShortcutBindings
import io.github.lumkit.dockhand.shortcut.ShortcutAction
import io.github.lumkit.dockhand.shortcut.ShortcutKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 Room 与 DataStore 的初始化链路真的能落盘、能读回。
 * 用临时目录，不碰用户真实数据。
 */
class PersistenceTest {

    @Test
    fun roomRoundTrip() = runBlocking {
        val dir = Files.createTempDirectory("dockhand-room").toFile()
        val database = createDatabase(File(dir, "test.db"))
        try {
            val history = TrashHistoryRepository(database.trashHistoryDao())
            assertTrue(history.observeAll().first().isEmpty())

            history.record("/tmp/a.txt", "/tmp/.Trash/a.txt")
            history.record("/tmp/b.txt", "/tmp/.Trash/b.txt")

            val stored = history.observeAll().first()
            assertEquals(listOf("/tmp/.Trash/b.txt", "/tmp/.Trash/a.txt"), stored.map { it.trashedPath })
            assertEquals("/tmp/a.txt", history.findByTrashedPath("/tmp/.Trash/a.txt")?.originalPath)
            assertTrue(File(dir, "test.db").exists())

            history.clear()
            assertTrue(history.observeAll().first().isEmpty())
        } finally {
            database.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun shortcutPreferencesRoundTrip() = runBlocking {
        val dir = Files.createTempDirectory("dockhand-shortcuts").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = SettingsRepository(
                createSettingsDataStore(File(dir, "settings.preferences_pb"), scope)
            )
            val custom = ShortcutKey(Key.B, meta = true, shift = true)

            settings.setShortcuts(ShortcutAction.SelectAll, listOf(custom))

            assertEquals(
                listOf(custom),
                settings.shortcuts.first()[ShortcutAction.SelectAll],
            )

            settings.setShortcuts(ShortcutAction.SelectAll, emptyList())
            assertEquals(
                emptyList(),
                settings.shortcuts.first()[ShortcutAction.SelectAll],
            )

            settings.resetShortcuts()
            assertEquals(
                listOf(DefaultShortcutBindings.getValue(ShortcutAction.SelectAll)),
                settings.shortcuts.first()[ShortcutAction.SelectAll],
            )
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun dataStoreRoundTrip() = runBlocking {
        val dir = Files.createTempDirectory("dockhand-datastore").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(dir, "settings.preferences_pb")
        try {
            val settings = SettingsRepository(createSettingsDataStore(file, scope))
            assertEquals(ColorSchemeMode.System, settings.colorSchemeMode.first())

            settings.setColorSchemeMode(ColorSchemeMode.Dark)
            assertEquals(ColorSchemeMode.Dark, settings.colorSchemeMode.first())

            // 写真的落到了磁盘上：文件存在且内容里有 "Dark"
            assertTrue(file.exists())
            assertTrue(file.readBytes().toString(Charsets.ISO_8859_1).contains("Dark"))
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}