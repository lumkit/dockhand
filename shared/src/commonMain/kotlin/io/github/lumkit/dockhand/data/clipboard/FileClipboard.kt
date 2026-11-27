package io.github.lumkit.dockhand.data.clipboard

/**
 * 系统文件剪贴板。只处理本地文件路径；MTP 条目没有本地路径，不能放入系统剪贴板。
 */
interface FileClipboard {
    suspend fun copyLocalPaths(paths: List<String>): Boolean
    suspend fun readLocalPaths(): List<String>
}

object NoopFileClipboard : FileClipboard {
    override suspend fun copyLocalPaths(paths: List<String>): Boolean = false
    override suspend fun readLocalPaths(): List<String> = emptyList()
}
