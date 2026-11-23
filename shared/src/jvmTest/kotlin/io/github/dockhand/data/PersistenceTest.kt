package io.github.dockhand.data

import io.github.dockhand.data.database.createDatabase
import io.github.dockhand.data.datastore.SettingsRepository
import io.github.dockhand.data.datastore.createSettingsDataStore
import io.github.dockhand.data.repository.NoteRepository
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
            val notes = NoteRepository(database.noteDao())
            assertTrue(notes.observeNotes().first().isEmpty())

            notes.addNote("第一条")
            notes.addNote("第二条")

            val stored = notes.observeNotes().first()
            assertEquals(listOf("第二条", "第一条"), stored.map { it.text })
            assertEquals(2, notes.observeCount().first())
            assertTrue(File(dir, "test.db").exists())
        } finally {
            database.close()
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