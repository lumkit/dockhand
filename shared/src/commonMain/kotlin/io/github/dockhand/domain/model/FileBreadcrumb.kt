package io.github.dockhand.domain.model

/** HomeTopBar 中的一段面包屑。 */
data class FileBreadcrumb(
    val label: String,
    /** 点击这一段后应导航到的路径。 */
    val path: String,
)

/**
 * 把 [FileLocation] 拆成面包屑。
 *
 * 约定：
 *  - “本机”根路径显示来源名；
 *  - `/Volumes/<磁盘名>` 从磁盘根开始显示，避免顶栏出现冗长的 `Volumes` 段；
 *  - MTP 路径从设备名开始，路径本身按 “/” 分段用于展示。
 */
fun FileLocation.breadcrumbs(): List<FileBreadcrumb> {
    val segments = path.replace('\\', '/')
        .split('/')
        .filter { it.isNotEmpty() }

    val isLocalVolume = source.kind == FileSourceKind.Local &&
        segments.size >= 2 &&
        segments[0] == "Volumes"

    val rootPath: String
    val rootLabel: String
    val remaining: List<String>

    if (isLocalVolume) {
        rootPath = "/Volumes/${segments[1]}"
        rootLabel = segments[1]
        remaining = segments.drop(2)
    } else {
        rootPath = "/"
        rootLabel = source.name
        remaining = segments
    }

    val crumbs = mutableListOf(FileBreadcrumb(rootLabel, rootPath))
    var currentPath = rootPath
    for (segment in remaining) {
        currentPath = if (currentPath == "/") "/$segment" else "$currentPath/$segment"
        crumbs += FileBreadcrumb(segment, currentPath)
    }
    return crumbs
}

/** 当前目录的父级；已经是根时返回 null。 */
fun FileLocation.parentBreadcrumb(): FileBreadcrumb? {
    val crumbs = breadcrumbs()
    return if (crumbs.size > 1) crumbs[crumbs.lastIndex - 1] else null
}
