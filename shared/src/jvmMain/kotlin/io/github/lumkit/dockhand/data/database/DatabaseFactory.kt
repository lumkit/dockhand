package io.github.lumkit.dockhand.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * 创建 Room 数据库。
 *
 * BundledSQLiteDriver 自带 SQLite 二进制（不需要系统库），
 * 因此后面上 App Store 时要在打包阶段处理原生库签名，这点和 JNA 同理。
 */
fun createDatabase(file: File): DockhandDatabase =
    Room.databaseBuilder<DockhandDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
