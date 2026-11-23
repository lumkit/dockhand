package io.github.dockhand.domain.repository

import io.github.dockhand.domain.model.FileBrowserException
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileListPage
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FileSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/**
 * 界面层的统一文件访问入口。
 *
 * 所有 provider 的侧边栏入口在这里合并；文件操作按 [FileLocation.source] 路由。
 */
interface FileBrowserRepository {
    fun observePlaces(): Flow<List<FilePlace>>

    suspend fun list(location: FileLocation): List<FileEntry>

    suspend fun listPage(
        location: FileLocation,
        offset: Int,
        limit: Int,
    ): FileListPage {
        val all = list(location)
        val page = all.drop(offset).take(limit)
        return FileListPage(page, offset + page.size < all.size, all.size)
    }

    fun observeChanges(location: FileLocation): Flow<Unit>

    suspend fun createDirectory(parent: FileLocation, name: String)

    suspend fun createFile(parent: FileLocation, name: String)

    /**
     * 把 [entries] 复制到 [destinationDirectory]。
     *
     * 当前骨架只支持同一个 provider 内部复制；跨 provider（例如 MTP -> 本地）
     * 需要后续的流式传输实现，见架构文档。
     */
    suspend fun copy(
        entries: List<FileEntry>,
        destinationDirectory: FileLocation,
        overwrite: Boolean = false,
    )
}

class DefaultFileBrowserRepository(
    private val providers: List<FileSystemProvider>,
) : FileBrowserRepository {

    override fun observePlaces(): Flow<List<FilePlace>> {
        if (providers.isEmpty()) return flowOf(emptyList())
        return combine(
            providers.map { provider ->
                // 每个 provider 先发一个空列表，避免某个后端首帧较慢时整个侧边栏被阻塞。
                provider.observePlaces().onStart { emit(emptyList()) }
            }
        ) { groups ->
            groups.flatMap { it }.distinctBy { it.id }
        }.distinctUntilChanged()
    }

    override suspend fun list(location: FileLocation): List<FileEntry> =
        providerFor(location.source).list(location)

    override suspend fun listPage(
        location: FileLocation,
        offset: Int,
        limit: Int,
    ): FileListPage = providerFor(location.source).listPage(location, offset, limit)

    override fun observeChanges(location: FileLocation): Flow<Unit> =
        providerFor(location.source).observeChanges(location)

    override suspend fun createDirectory(parent: FileLocation, name: String) =
        providerFor(parent.source).createDirectory(parent, name)

    override suspend fun createFile(parent: FileLocation, name: String) =
        providerFor(parent.source).createFile(parent, name)

    override suspend fun copy(
        entries: List<FileEntry>,
        destinationDirectory: FileLocation,
        overwrite: Boolean,
    ) {
        if (entries.isEmpty()) return
        val targetProvider = providerFor(destinationDirectory.source)
        for (entry in entries) {
            val sourceProvider = providerFor(entry.location.source)
            if (sourceProvider !== targetProvider) {
                throw FileBrowserException(
                    "暂不支持跨设备复制，请先接入流式传输层：${entry.location.source.name} -> ${destinationDirectory.source.name}"
                )
            }
            sourceProvider.copy(entry.location, destinationDirectory, overwrite)
        }
    }

    private fun providerFor(source: FileSource): FileSystemProvider =
        providers.firstOrNull { it.supports(source) }
            ?: throw FileBrowserException("没有可用的文件后端：${source.name}")
}