package io.github.lumkit.dockhand.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trash_history",
    indices = [Index(value = ["trashed_path"], unique = true)],
)
data class TrashHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "original_path")
    val originalPath: String,
    @ColumnInfo(name = "trashed_path")
    val trashedPath: String,
)
