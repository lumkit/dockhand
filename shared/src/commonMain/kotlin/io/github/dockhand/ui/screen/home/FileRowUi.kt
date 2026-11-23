package io.github.dockhand.ui.screen.home

import androidx.compose.runtime.Immutable
import io.github.dockhand.domain.model.FileEntry
import kotlinx.datetime.TimeZone

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
        )
    }
