package io.github.lumkit.dockhand.domain.repository

import io.github.lumkit.dockhand.core.platform.createTaskTempDirectory
import io.github.lumkit.dockhand.core.platform.deleteTaskTempPath
import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.domain.model.FileInfo
import io.github.lumkit.dockhand.domain.model.FileListPage
import io.github.lumkit.dockhand.domain.model.FileUsage
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.toBasicFileInfo
import kotlinx.coroutines.CancellationException
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

    suspend fun info(entry: FileEntry): FileInfo = entry.toBasicFileInfo()

    suspend fun usage(entry: FileEntry): FileUsage? = null

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

    /**
     * 可报告进度、可取消的复制入口。默认退回 [copy]（无进度）。
     */
    suspend fun copyWithProgress(
        entries: List<FileEntry>,
        destinationDirectory: FileLocation,
        overwrite: Boolean = false,
        onProgress: (completed: Long?, total: Long?) -> Unit = { _, _ -> },
        onBytes: (completed: Long, total: Long) -> Unit = { _, _ -> },
        onWaiting: (deviceName: String?) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) {
        copy(entries, destinationDirectory, overwrite)
        onProgress(entries.size.toLong(), entries.size.toLong())
    }
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

    override suspend fun info(entry: FileEntry): FileInfo =
        providerFor(entry.location.source).info(entry)

    override suspend fun usage(entry: FileEntry): FileUsage? =
        providerFor(entry.location.source).usage(entry)

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

    override suspend fun copyWithProgress(
        entries: List<FileEntry>,
        destinationDirectory: FileLocation,
        overwrite: Boolean,
        onProgress: (completed: Long?, total: Long?) -> Unit,
        onBytes: (completed: Long, total: Long) -> Unit,
        onWaiting: (deviceName: String?) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        if (entries.isEmpty()) return
        val destinationProvider = providerFor(destinationDirectory.source)
        val tempDirectory = createTaskTempDirectory("dockhand-copy-")
        val createdRoots = mutableListOf<FileLocation>()
        val tempCounter = intArrayOf(0)
        try {
            entries.forEachIndexed { index, entry ->
                if (isCancelled()) throw CancellationException("复制已取消")
                val targetName = uniqueTargetName(
                    destinationProvider = destinationProvider,
                    destinationParent = destinationDirectory,
                    sourceName = entry.name,
                    isDirectory = entry.isDirectory,
                )
                val target = FileLocation(
                    source = destinationDirectory.source,
                    path = joinPath(destinationDirectory.path, targetName),
                )
                val sourceProvider = providerFor(entry.location.source)
                try {
                    val copiedRoot = copyEntry(
                        entry = entry,
                        target = target,
                        destinationParent = destinationDirectory,
                        sourceProvider = sourceProvider,
                        destinationProvider = destinationProvider,
                        overwrite = overwrite,
                        tempDirectory = tempDirectory,
                        tempCounter = tempCounter,
                        onBytes = onBytes,
                        onWaiting = onWaiting,
                        isCancelled = isCancelled,
                    )
                    copiedRoot?.let(createdRoots::add)
                } catch (e: CancellationException) {
                    runCatching { destinationProvider.delete(target, recursive = true) }
                    throw e
                } catch (e: Exception) {
                    runCatching { destinationProvider.delete(target, recursive = true) }
                    throw e
                }
                onProgress(index + 1L, entries.size.toLong())
            }
        } catch (e: Throwable) {
            createdRoots.asReversed().forEach { root ->
                runCatching { destinationProvider.delete(root, recursive = true) }
            }
            throw e
        } finally {
            deleteTaskTempPath(tempDirectory)
        }
    }

    private suspend fun copyEntry(
        entry: FileEntry,
        target: FileLocation,
        destinationParent: FileLocation,
        sourceProvider: FileSystemProvider,
        destinationProvider: FileSystemProvider,
        overwrite: Boolean,
        tempDirectory: String,
        tempCounter: IntArray,
        onBytes: (Long, Long) -> Unit,
        onWaiting: (String?) -> Unit,
        isCancelled: () -> Boolean,
    ): FileLocation? {
        if (isCancelled()) throw CancellationException("复制已取消")
        val targetName = target.path.substringAfterLast('/')
        if (
            sourceProvider === destinationProvider &&
            sourceProvider.kind == FileSourceKind.Local &&
            targetName == entry.name
        ) {
            sourceProvider.copy(entry.location, destinationParent, overwrite)
            return target
        }

        if (entry.isDirectory) {
            if (overwrite) runCatching { destinationProvider.delete(target, recursive = true) }
            destinationProvider.createDirectory(destinationParent, targetName)
            sourceProvider.list(entry.location).forEach { child ->
                copyEntry(
                    entry = child,
                    target = FileLocation(target.source, joinPath(target.path, child.name)),
                    destinationParent = target,
                    sourceProvider = sourceProvider,
                    destinationProvider = destinationProvider,
                    overwrite = overwrite,
                    tempDirectory = tempDirectory,
                    tempCounter = tempCounter,
                    onBytes = onBytes,
                    onWaiting = onWaiting,
                    isCancelled = isCancelled,
                )
            }
            return target
        }

        val sourceLocalPath = sourceProvider.localPath(entry.location)
        val destinationLocalPath = destinationProvider.localPath(target)
        if (sourceLocalPath != null && destinationProvider.kind == FileSourceKind.Mtp) {
            destinationProvider.importFromLocal(
                localPath = sourceLocalPath,
                destination = target,
                overwrite = overwrite,
                onProgress = onBytes,
                onWaiting = onWaiting,
            )
            return target
        }
        if (sourceProvider.kind == FileSourceKind.Mtp && destinationLocalPath != null) {
            sourceProvider.exportToLocal(
                source = entry.location,
                localPath = destinationLocalPath,
                onProgress = onBytes,
                onWaiting = onWaiting,
            )
            return target
        }

        val tempPath = joinLocalPath(
            tempDirectory,
            "${tempCounter[0]++}-${entry.name.replace('/', '_')}",
        )
        try {
            sourceProvider.exportToLocal(entry.location, tempPath, onBytes, onWaiting)
            if (isCancelled()) throw CancellationException("复制已取消")
            destinationProvider.importFromLocal(tempPath, target, overwrite, onBytes, onWaiting)
        } finally {
            deleteTaskTempPath(tempPath)
        }
        return target
    }

    private suspend fun uniqueTargetName(
        destinationProvider: FileSystemProvider,
        destinationParent: FileLocation,
        sourceName: String,
        isDirectory: Boolean,
    ): String {
        if (!destinationProvider.exists(FileLocation(destinationParent.source, joinPath(destinationParent.path, sourceName)))) {
            return sourceName
        }
        val lastDot = sourceName.lastIndexOf('.')
        val extension = if (!isDirectory && lastDot > 0) {
            sourceName.substring(lastDot + 1).takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val stem = if (extension != null) sourceName.substring(0, lastDot) else sourceName
        var index = 0
        while (true) {
            val suffix = if (index == 0) "_副本" else "_副本$index"
            val candidate = buildString {
                append(stem)
                append(suffix)
                extension?.let {
                    append('.')
                    append(it)
                }
            }
            val location = FileLocation(destinationParent.source, joinPath(destinationParent.path, candidate))
            if (!destinationProvider.exists(location)) return candidate
            index++
        }
    }

    private fun joinPath(parent: String, name: String): String {
        val base = parent.trimEnd('/')
        return if (base.isEmpty()) "/$name" else "$base/$name"
    }

    private fun joinLocalPath(parent: String, name: String): String {
        val base = parent.trimEnd('/')
        return "$base/$name"
    }

    private fun providerFor(source: FileSource): FileSystemProvider =
        providers.firstOrNull { it.supports(source) }
            ?: throw FileBrowserException("没有可用的文件后端：${source.name}")
}