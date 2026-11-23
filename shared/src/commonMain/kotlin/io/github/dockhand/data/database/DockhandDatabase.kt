package io.github.dockhand.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.dockhand.data.database.dao.NoteDao
import io.github.dockhand.data.database.entity.NoteEntity

/**
 * 数据库定义。实际实例由平台层创建（JVM 上用 [androidx.sqlite.SQLiteDriver] + BundledSQLite）。
 */
@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DockhandDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
