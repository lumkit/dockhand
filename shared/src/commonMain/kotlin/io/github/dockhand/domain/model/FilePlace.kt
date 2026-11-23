package io.github.dockhand.domain.model

/** 侧边栏入口的语义类型，UI 据此选择图标并本地化名称。 */
enum class FilePlaceKind {
    Home,
    Desktop,
    Documents,
    Downloads,
    Pictures,
    Music,
    Movies,
    Volume,
    MtpDevice,
    MtpStorage,
    MtpCamera,
    MtpDownloads,
    MtpMovies,
    MtpMusic,
    MtpPictures,
}

enum class FilePlaceGroup {
    Local,
    Mounted,
    Mtp,
}

val DEFAULT_FILE_PLACE_GROUP_ORDER = listOf(
    FilePlaceGroup.Local,
    FilePlaceGroup.Mounted,
    FilePlaceGroup.Mtp,
)

val DEFAULT_MTP_SHORTCUT_ORDER = listOf(
    "camera",
    "downloads",
    "movies",
    "music",
    "pictures",
)

/**
 * 按保存的 place id 顺序排列；未出现在 ids 里的新地点保持原顺序并追加在末尾。
 */
fun List<FilePlace>.orderedByIds(ids: List<String>): List<FilePlace> {
    if (ids.isEmpty()) return this
    val rank = ids.withIndex().associate { (index, id) -> id to index }
    return withIndex()
        .sortedWith(
            compareBy(
                { rank[it.value.id] ?: Int.MAX_VALUE },
                { it.index },
            )
        )
        .map { it.value }
}

/**
 * 侧边栏中的一个“位置”，是导航目的地，不一定是文件系统根。
 */
data class FilePlace(
    val id: String,
    /** 动态名称（用户名、磁盘名、设备名）；固定入口由 UI 按 [kind] 本地化。 */
    val label: String,
    val location: FileLocation,
    val kind: FilePlaceKind,
    val group: FilePlaceGroup,
    /** 挂载磁盘/MTP 存储的总容量；未知为 null。 */
    val totalBytes: Long? = null,
    /** 挂载磁盘/MTP 存储的可用容量；未知为 null。 */
    val freeBytes: Long? = null,
)
