package io.github.lumkit.dockhand.ui.screen.home

import androidx.compose.runtime.Immutable
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.ic_file_folder
import dockhand.shared.generated.resources.ic_file_generic
import dockhand.shared.generated.resources.ic_file_ai
import dockhand.shared.generated.resources.ic_file_avi
import dockhand.shared.generated.resources.ic_file_bmp
import dockhand.shared.generated.resources.ic_file_crd
import dockhand.shared.generated.resources.ic_file_csv
import dockhand.shared.generated.resources.ic_file_dll
import dockhand.shared.generated.resources.ic_file_doc
import dockhand.shared.generated.resources.ic_file_docx
import dockhand.shared.generated.resources.ic_file_dwg
import dockhand.shared.generated.resources.ic_file_eps
import dockhand.shared.generated.resources.ic_file_exe
import dockhand.shared.generated.resources.ic_file_flv
import dockhand.shared.generated.resources.ic_file_giff
import dockhand.shared.generated.resources.ic_file_html
import dockhand.shared.generated.resources.ic_file_iso
import dockhand.shared.generated.resources.ic_file_java
import dockhand.shared.generated.resources.ic_file_jpg
import dockhand.shared.generated.resources.ic_file_mdb
import dockhand.shared.generated.resources.ic_file_mid
import dockhand.shared.generated.resources.ic_file_mov
import dockhand.shared.generated.resources.ic_file_mp3
import dockhand.shared.generated.resources.ic_file_mp4
import dockhand.shared.generated.resources.ic_file_mpeg
import dockhand.shared.generated.resources.ic_file_pdf
import dockhand.shared.generated.resources.ic_file_png
import dockhand.shared.generated.resources.ic_file_ppt
import dockhand.shared.generated.resources.ic_file_ps
import dockhand.shared.generated.resources.ic_file_psd
import dockhand.shared.generated.resources.ic_file_pub
import dockhand.shared.generated.resources.ic_file_rar
import dockhand.shared.generated.resources.ic_file_raw
import dockhand.shared.generated.resources.ic_file_rss
import dockhand.shared.generated.resources.ic_file_svg
import dockhand.shared.generated.resources.ic_file_tiff
import dockhand.shared.generated.resources.ic_file_txt
import dockhand.shared.generated.resources.ic_file_wav
import dockhand.shared.generated.resources.ic_file_wma
import dockhand.shared.generated.resources.ic_file_xml
import dockhand.shared.generated.resources.ic_file_xsl
import dockhand.shared.generated.resources.ic_file_zip
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.DrawableResource

/**
 * 列表行在进入 LazyColumn 之前就已经准备好的 UI 数据。
 *
 * [summary] 是预格式化好的副标题，LazyColumn 只读字符串，不做时间/大小计算。
 * [FileEntry] 保留下来用于点击导航和选中逻辑。
 */
@Immutable
data class FileRowUi(
    val entry: FileEntry,
    val summary: String?,
    val icon: DrawableResource,
    val thumbnailPath: String? = null,
) {
    val id: String get() = entry.id
    val name: String get() = entry.name
    val isDirectory: Boolean get() = entry.isDirectory
}

/**
 * 在 IO/Default 线程把一页 [FileEntry] 转成可直接渲染的 [FileRowUi]。
 * 时区只取一次，避免每一行都查系统时区。
 */
internal fun List<FileEntry>.toRowUi(
    timeZone: TimeZone,
    itemCountTexts: Map<Int, String>,
): List<FileRowUi> =
    map { entry ->
        FileRowUi(
            entry = entry,
            summary = fileEntrySummary(
                entry = entry,
                timeZone = timeZone,
                itemCountText = entry.childCount?.let(itemCountTexts::get),
            ),
            icon = entry.fileIcon(),
            thumbnailPath = entry.thumbnailPath(),
        )
    }

private val THUMBNAIL_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg",
)

private fun FileEntry.thumbnailPath(): String? =
    if (
        !isDirectory &&
        location.source.kind == FileSourceKind.Local &&
        extension?.lowercase() in THUMBNAIL_EXTENSIONS
    ) {
        location.path
    } else {
        null
    }

/** 图标在构建行数据时决定，LazyColumn 内不再做扩展名判断。 */
private fun FileEntry.fileIcon(): DrawableResource {
    if (isDirectory) return Res.drawable.ic_file_folder
    return when (extension?.lowercase()) {
        "ai" -> Res.drawable.ic_file_ai
        "avi" -> Res.drawable.ic_file_avi
        "bmp" -> Res.drawable.ic_file_bmp
        "crd" -> Res.drawable.ic_file_crd
        "csv" -> Res.drawable.ic_file_csv
        "dll" -> Res.drawable.ic_file_dll
        "doc" -> Res.drawable.ic_file_doc
        "docx" -> Res.drawable.ic_file_docx
        "dwg" -> Res.drawable.ic_file_dwg
        "eps" -> Res.drawable.ic_file_eps
        "exe" -> Res.drawable.ic_file_exe
        "flv" -> Res.drawable.ic_file_flv
        "gif" -> Res.drawable.ic_file_giff
        "html", "htm" -> Res.drawable.ic_file_html
        "iso" -> Res.drawable.ic_file_iso
        "java", "kt", "kts" -> Res.drawable.ic_file_java
        "jpg", "jpeg", "heic" -> Res.drawable.ic_file_jpg
        "mdb" -> Res.drawable.ic_file_mdb
        "mid", "midi" -> Res.drawable.ic_file_mid
        "mov" -> Res.drawable.ic_file_mov
        "mp3", "m4a" -> Res.drawable.ic_file_mp3
        "mp4", "mkv" -> Res.drawable.ic_file_mp4
        "mpeg", "mpg" -> Res.drawable.ic_file_mpeg
        "pdf" -> Res.drawable.ic_file_pdf
        "png", "webp" -> Res.drawable.ic_file_png
        "ppt", "pptx" -> Res.drawable.ic_file_ppt
        "ps" -> Res.drawable.ic_file_ps
        "psd" -> Res.drawable.ic_file_psd
        "pub" -> Res.drawable.ic_file_pub
        "rar" -> Res.drawable.ic_file_rar
        "raw" -> Res.drawable.ic_file_raw
        "rss" -> Res.drawable.ic_file_rss
        "svg" -> Res.drawable.ic_file_svg
        "tiff", "tif" -> Res.drawable.ic_file_tiff
        "txt", "md", "log" -> Res.drawable.ic_file_txt
        "wav" -> Res.drawable.ic_file_wav
        "wma" -> Res.drawable.ic_file_wma
        "xml", "json" -> Res.drawable.ic_file_xml
        "xsl", "xslt" -> Res.drawable.ic_file_xsl
        "zip", "7z", "gz", "tar" -> Res.drawable.ic_file_zip
        else -> Res.drawable.ic_file_generic
    }
}
