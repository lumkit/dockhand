package io.github.lumkit.dockhand.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.lumkit.dockhand.data.database.dao.TrashHistoryDao
import io.github.lumkit.dockhand.data.database.entity.TrashHistoryEntity

/**
 * 数据库定义。实际实例由平台层创建（JVM 上用 [androidx.sqlite.SQLiteDriver] + BundledSQLite）。
 *
 * 版本历史：
 * - 1: note
 * - 2: 增加 trash_history，记录移入废纸篓前的原始路径
 * - 3: 删除无用的 note
 */
@Database(
    entities = [TrashHistoryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class DockhandDatabase : RoomDatabase() {
    abstract fun trashHistoryDao(): TrashHistoryDao
}
