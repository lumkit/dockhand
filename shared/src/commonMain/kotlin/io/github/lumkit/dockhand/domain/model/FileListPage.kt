package io.github.lumkit.dockhand.domain.model

/** 一次分页加载的结果。 */
data class FileListPage(
    val entries: List<FileEntry>,
    val hasMore: Boolean,
    val total: Int? = null,
)
