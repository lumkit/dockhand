package io.github.lumkit.dockhand.domain.model

/**
 * 一个可浏览的文件来源。
 *
 * 本地磁盘和每一台 MTP 设备都是一个 [FileSource]。UI 只依赖这个模型，
 * 不关心路径最终由哪个 provider 解析。
 */
enum class FileSourceKind {
    Local,
    Mtp,
}

data class FileSource(
    /** 稳定 ID；本地固定为 [LOCAL_ID]，MTP 设备使用设备序列号等稳定标识。 */
    val id: String,
    /** 展示名，例如“本机”或设备型号。 */
    val name: String,
    val kind: FileSourceKind,
) {
    companion object {
        const val LOCAL_ID = "local"
    }
}

/**
 * 一次浏览定位。
 *
 * [path] 对 UI 是不透明字符串：
 *  - 本地 provider 使用 POSIX 绝对路径；
 *  - MTP provider 可以使用以 “/” 分段的逻辑路径。
 *
 * 不要假设某个路径一定能在另一个 provider 上使用。
 */
data class FileLocation(
    val source: FileSource,
    val path: String,
) {
    init {
        require(path.isNotBlank()) { "FileLocation.path 不能为空" }
    }
}

/** UI 展示用路径：MTP 显示“设备/存储名 + 逻辑路径”。 */
fun FileLocation.displayPath(): String =
    if (source.kind == FileSourceKind.Mtp) {
        source.name + if (path == "/") "" else path
    } else {
        path
    }
