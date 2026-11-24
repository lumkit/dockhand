package io.github.lumkit.dockhand.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** 2 -> 3：删除不再使用的 note 表。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `note`")
    }
}

/** 1 -> 2：增加废纸篓恢复记录。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trash_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `original_path` TEXT NOT NULL,
                `trashed_path` TEXT NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_trash_history_trashed_path` " +
                "ON `trash_history` (`trashed_path`)"
        )
    }
}
