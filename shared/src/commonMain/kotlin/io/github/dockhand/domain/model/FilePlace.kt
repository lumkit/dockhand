package io.github.dockhand.domain.model

/** 侧边栏入口的语义类型，UI 据此选择图标并本地化名称。 */
enum class FilePlaceKind {
    Root,
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
}

enum class FilePlaceGroup {
    Local,
    Mtp,
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
)
