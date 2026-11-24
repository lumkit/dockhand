package io.github.lumkit.dockhand.data.repository

import io.github.lumkit.dockhand.data.database.dao.TrashHistoryDao
import io.github.lumkit.dockhand.data.database.entity.TrashHistoryEntity
import kotlinx.coroutines.flow.Flow

class TrashHistoryRepository(
    private val dao: TrashHistoryDao,
) {
    fun observeAll(): Flow<List<TrashHistoryEntity>> = dao.observeAll()

    suspend fun findByTrashedPath(trashedPath: String): TrashHistoryEntity? =
        dao.findByTrashedPath(trashedPath)

    suspend fun record(originalPath: String, trashedPath: String) {
        dao.insert(
            TrashHistoryEntity(
                originalPath = originalPath,
                trashedPath = trashedPath,
            )
        )
    }

    suspend fun remove(trashedPath: String) {
        dao.deleteByTrashedPath(trashedPath)
    }

    suspend fun clear() {
        dao.deleteAll()
    }
}
