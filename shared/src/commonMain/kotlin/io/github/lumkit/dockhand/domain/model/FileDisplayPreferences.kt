package io.github.lumkit.dockhand.domain.model

/** 文件列表的排列方式。 */
enum class FileLayoutMode {
    List,
    Grid,
}

/** 文件列表的排序方式。目录始终排在文件前面。 */
enum class FileSortMode {
    NameAsc,
    NameDesc,
    ModifiedDesc,
    ModifiedAsc,
    SizeDesc,
    SizeAsc,
    TypeAsc,
    TypeDesc,
}

fun fileEntryComparator(mode: FileSortMode): Comparator<FileEntry> {
    val byName = compareBy<FileEntry> { it.name.lowercase() }
    val contentOrder: Comparator<FileEntry> = when (mode) {
        FileSortMode.NameAsc -> byName
        FileSortMode.NameDesc -> byName.reversed()
        FileSortMode.ModifiedDesc -> compareByDescending { it.modifiedAt ?: Long.MIN_VALUE }
        FileSortMode.ModifiedAsc -> compareBy { it.modifiedAt ?: Long.MAX_VALUE }
        FileSortMode.SizeDesc -> compareByDescending { it.size ?: Long.MIN_VALUE }
        FileSortMode.SizeAsc -> compareBy { it.size ?: Long.MAX_VALUE }
        FileSortMode.TypeAsc -> compareBy<FileEntry> { it.extension?.lowercase().orEmpty() }.then(byName)
        FileSortMode.TypeDesc -> compareByDescending<FileEntry> { it.extension?.lowercase().orEmpty() }.then(byName)
    }
    return compareByDescending<FileEntry> { it.isDirectory }.then(contentOrder)
}

fun Iterable<FileEntry>.sortedFor(mode: FileSortMode): List<FileEntry> =
    sortedWith(fileEntryComparator(mode))
