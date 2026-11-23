package io.github.dockhand.domain.repository

import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FileSource
import io.github.dockhand.domain.model.FileSourceKind
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

    /** 在 [parent] 下新建一个文件夹。名称冲突时抛 [io.github.dockhand.domain.model.FileBrowserException]。 */
    suspend fun createDirectory(parent: FileLocation, name: String)

    /** 在 [parent] 下新建一个空文件。名称冲突时抛 [io.github.dockhand.domain.model.FileBrowserException]。 */
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