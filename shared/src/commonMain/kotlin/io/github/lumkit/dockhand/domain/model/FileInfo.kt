package io.github.lumkit.dockhand.domain.model

/**
 * 文件/目录的详细信息。
 *
 * 不同文件系统能提供的字段不同；未知字段保持 null，UI 负责隐藏或显示“未知”。
 */
data class FileInfo(
    val name: String,
    val parentPath: String,
    val isDirectory: Boolean,
    /** 文件大小；目录为递归磁盘占用。 */
    val sizeBytes: Long? = null,
    /** 目录递归项目数（包含子目录），文件为 null。 */
    val itemCount: Int? = null,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
    /** 例如 drwxr-xr-x。 */
    val permissionSymbol: String? = null,
    /** 例如 755。 */
    val permissionCode: String? = null,
    val owner: String? = null,
    val group: String? = null,
)

/** 可能是耗时的磁盘占用/项目数统计。 */
data class FileUsage(
    val sizeBytes: Long,
    val itemCount: Int? = null,
)

/** 没有平台扩展信息时，用 [FileEntry] 已有字段构造基础信息。 */
fun FileEntry.toBasicFileInfo(): FileInfo = FileInfo(
    name = name,
    parentPath = location.path.parentPath(),
    isDirectory = isDirectory,
    sizeBytes = if (isDirectory) null else size,
    itemCount = if (isDirectory) childCount else null,
    modifiedAt = modifiedAt,
)

private fun String.parentPath(): String {
    val normalized = trimEnd('/')
    if (normalized.isEmpty()) return "/"
    val index = normalized.lastIndexOf('/')
    return when {
        index < 0 -> "/"
        index == 0 -> "/"
        else -> normalized.substring(0, index)
    }
}
