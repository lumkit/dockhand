package io.github.dockhand.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.dockhand.data.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM note ORDER BY id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM note")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: NoteEntity): Long

    @Query("DELETE FROM note")
    suspend fun deleteAll()
}
