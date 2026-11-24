package io.github.lumkit.dockhand.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.lumkit.dockhand.data.database.entity.TrashHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashHistoryDao {
    @Query("SELECT * FROM trash_history ORDER BY id DESC")
    fun observeAll(): Flow<List<TrashHistoryEntity>>

    @Query("SELECT * FROM trash_history WHERE trashed_path = :trashedPath LIMIT 1")
    suspend fun findByTrashedPath(trashedPath: String): TrashHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrashHistoryEntity)

    @Query("DELETE FROM trash_history WHERE trashed_path = :trashedPath")
    suspend fun deleteByTrashedPath(trashedPath: String)

    @Query("DELETE FROM trash_history")
    suspend fun deleteAll()
}
