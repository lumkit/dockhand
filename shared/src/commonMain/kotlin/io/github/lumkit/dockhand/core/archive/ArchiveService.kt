package io.github.lumkit.dockhand.core.archive

import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLocation

enum class CompressionLevel(val zipLevel: Int) {
    Store(0),
    Fastest(1),
    Fast(3),
    Normal(5),
    Maximum(7),
    Ultra(9),
}

data class CompressRequest(
    val entries: List<FileEntry>,
    val destination: FileLocation,
    val archiveName: String,
    val level: CompressionLevel = CompressionLevel.Normal,
    val password: String = "",
    val splitSizeMb: Long? = null,
    val separateArchives: Boolean = false,
    val deleteSource: Boolean = false,
)

data class CompressCallbacks(
    val onProgress: (completed: Long?, total: Long?) -> Unit = { _, _ -> },
    val onBytes: (completed: Long, total: Long) -> Unit = { _, _ -> },
    val isCancelled: () -> Boolean = { false },
)

interface ArchiveService {
    suspend fun compress(request: CompressRequest, callbacks: CompressCallbacks)
}

object NoopArchiveService : ArchiveService {
    override suspend fun compress(request: CompressRequest, callbacks: CompressCallbacks) {
        error("当前平台不支持压缩")
    }
}
