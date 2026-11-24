package io.github.lumkit.dockhand.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.lumkit.dockhand.data.database.entity.TrashHistoryEntity
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseMigrationTest {

    @Test
    fun opensVersion1DatabaseAndMigratesToVersion3() = runBlocking {
        val file = File.createTempFile("dockhand-migration", ".db")
        try {
            val connection = BundledSQLiteDriver().open(file.absolutePath)
            try {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL)"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table " +
                        "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                connection.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                        "VALUES(42, 'd7c55ca0914d2aabc4067879f78bb329')"
                )
                connection.execSQL("PRAGMA user_version = 1")
            } finally {
                connection.close()
            }

            val database = createDatabase(file)
            try {
                database.trashHistoryDao().insert(
                    TrashHistoryEntity(
                        originalPath = "/tmp/a.txt",
                        trashedPath = "/tmp/.Trash/a.txt",
                    )
                )
                assertTrue(database.trashHistoryDao().findByTrashedPath("/tmp/.Trash/a.txt") != null)
            } finally {
                database.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun migration1To2CreatesTrashHistoryTableAndIndex() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execSQL(
                "CREATE TABLE `note` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT NOT NULL)"
            )

            MIGRATION_1_2.migrate(connection)

            val table = connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'trash_history'"
            )
            try {
                assertTrue(table.step())
            } finally {
                table.close()
            }

            val index = connection.prepare(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'index' AND name = 'index_trash_history_trashed_path'"
            )
            try {
                assertTrue(index.step())
            } finally {
                index.close()
            }
        } finally {
            connection.close()
        }
    }
}
