package io.github.lumkit.dockhand.domain.repository

import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileInfo
import io.github.lumkit.dockhand.domain.model.FileListPage
import io.github.lumkit.dockhand.domain.model.FileUsage
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.domain.model.toBasicFileInfo
import kotlinx.coroutines.flow.Flow

/**
 * 一种文件后端（本地磁盘、MTP、云盘……）。
 *
 * UI 和 [FileBrowserRepository] 不直接调用平台 API；新增传输方式时只要实现这个接口，
 * 再在 AppContainer 里注册即可。
 */
interface FileSystemProvider {
    val kind: FileSourceKind

    /** 当前可用的侧边栏入口；磁盘/设备插拔通过 Flow 推送。 */
    fun observePlaces(): Flow<List<FilePlace>>

    /**
     * 监听一个目录的内容变化。
     *
     * 只要目录内的直接子项可能发生了变化就发出一个值；provider 负责合并短时间内的连续事件。
     * 不支持监听的 provider 返回空 Flow（UI 仍可手动刷新）。
     */
    fun observeChanges(location: FileLocation): Flow<Unit>

    /** 列出目录内容。调用方负责排序，provider 只返回原始条目。 */
    suspend fun list(location: FileLocation): List<FileEntry>

    /**
     * 分页列出目录内容；默认实现基于 [list] 切片。
     * MTP 等后端可以覆盖，实现“先排序索引、再分页”。
     */
    suspend fun listPage(
        location: FileLocation,
        offset: Int,
        limit: Int,
    ): FileListPage {
        val all = list(location)
        val page = all.drop(offset).take(limit)
        return FileListPage(
            entries = page,
            hasMore = offset + page.size < all.size,
            total = all.size,
        )
    }

    /** 读取详细属性；默认只返回 [FileEntry] 已有信息。 */
    suspend fun info(entry: FileEntry): FileInfo = entry.toBasicFileInfo()

    /** 统计目录磁盘占用/项目数；不支持或无需统计时返回 null。 */
    suspend fun usage(entry: FileEntry): FileUsage? = null

    /** 把一个文件导出到本机路径；跨 provider 复制时使用。 */
    suspend fun exportToLocal(
        source: FileLocation,
        localPath: String,
        onProgress: (completed: Long, total: Long) -> Unit = { _, _ -> },
        onWaiting: (deviceName: String?) -> Unit = {},
    ): Unit = throw FileBrowserException("该文件系统不支持导出：${source.source.name}")

    /** 把本机文件导入到目标路径；跨 provider 复制时使用。 */
    suspend fun importFromLocal(
        localPath: String,
        destination: FileLocation,
        overwrite: Boolean,
        onProgress: (completed: Long, total: Long) -> Unit = { _, _ -> },
        onWaiting: (deviceName: String?) -> Unit = {},
    ): Unit = throw FileBrowserException("该文件系统不支持导入：${destination.source.name}")

    /** 本地 provider 可提供实际路径，供跨 provider 直接传输。 */
    fun localPath(location: FileLocation): String? = null

    /** provider 原生复制（例如 MTP 设备端 copy_object）；不支持时返回 false。 */
    suspend fun copyNative(source: FileLocation, destinationDirectory: FileLocation): Boolean = false

    /** 删除文件/目录，供任务回滚使用。 */
    suspend fun delete(
        location: FileLocation,
        recursive: Boolean,
        isCancelled: () -> Boolean = { false },
        onWaiting: (deviceName: String?) -> Unit = {},
    ): Unit = throw FileBrowserException("该文件系统不支持删除：${location.source.name}")

    /** 在同一目录内重命名文件/目录。 */
    suspend fun rename(
        location: FileLocation,
        newName: String,
        isCancelled: () -> Boolean = { false },
        onWaiting: (deviceName: String?) -> Unit = {},
    ): Unit = throw FileBrowserException("该文件系统不支持重命名：${location.source.name}")

    /** 目标是否已经存在。默认返回 false，支持覆盖检查的 provider 应覆盖。 */
    suspend fun exists(location: FileLocation): Boolean = false

    /** 在 [parent] 下新建一个文件夹。名称冲突时抛 [io.github.lumkit.dockhand.domain.model.FileBrowserException]。 */
    suspend fun createDirectory(parent: FileLocation, name: String)

    /** 在 [parent] 下新建一个空文件。名称冲突时抛 [io.github.lumkit.dockhand.domain.model.FileBrowserException]。 */
    suspend fun createFile(parent: FileLocation, name: String)

    /**
     * 把 [source] 复制进 [destinationDirectory]，保持原文件名。
     * 目录需要递归复制。目标已存在且 [overwrite] 为 false 时抛错。
     */
    suspend fun copy(
        source: FileLocation,
        destinationDirectory: FileLocation,
        overwrite: Boolean = false,
    )

    fun supports(source: FileSource): Boolean = source.kind == kind
}