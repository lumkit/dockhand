package io.github.lumkit.dockhand.data.repository

import io.github.lumkit.dockhand.data.database.dao.NoteDao
import io.github.lumkit.dockhand.data.database.entity.NoteEntity
import io.github.lumkit.dockhand.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val dao: NoteDao,
) {
    fun observeNotes(): Flow<List<Note>> =
        dao.observeAll().map { rows -> rows.map { Note(id = it.id, text = it.text) } }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun addNote(text: String) {
        dao.insert(NoteEntity(text = text))
    }

    suspend fun clear() = dao.deleteAll()
}
