package io.github.lumkit.dockhand.domain.model

/**
 * 文件列表中的一行。
 *
 * [id] 在同一个列表内唯一，可直接作为 LazyColumn 的 key；
 * [location] 是这一项自身的定位，用于继续向下导航或发起传输。
 */
data class FileEntry(
    val id: String,
    val name: String,
    val location: FileLocation,
    val isDirectory: Boolean,
    /** 文件字节数；目录为 null，避免为了展示去递归统计。 */
    val size: Long? = null,
    /**
     * 目录的直接子项数量（只统计可见项）。
     * 后端没有提供时为 null，UI 不展示 “x项”。
     */
    val childCount: Int? = null,
    /** 最后修改时间，Unix epoch milliseconds；未知时为 null。 */
    val modifiedAt: Long? = null,
    /** 不包含点的扩展名，例如 `png`；没有扩展名时为 null。 */
    val extension: String? = null,
    val isHidden: Boolean = false,
    /** macOS 应用包：目录形态，但可以运行并显示应用图标。 */
    val isMacApp: Boolean = false,
)