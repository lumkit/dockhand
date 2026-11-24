package io.github.lumkit.dockhand.ui.screen.home

import io.github.lumkit.dockhand.domain.model.FileEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

/** 把字节数格式化成 1 KB / 1.5 MB / 2 GB 这样的短文本。 */
internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }

    val rounded = round(value * 10) / 10
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return "$number ${units[unitIndex]}"
}

/**
 * 把 epoch milliseconds 按系统时区格式化成 `yyyy-MM-dd HH:mm:ss`。
 *
 * 这个函数只应在列表加载阶段调用一次，不要在 LazyColumn item 里调用。
 * [timeZone] 参数用于测试和“一次加载只取一次时区”的场景。
 */
internal fun formatModifiedTime(
    epochMillis: Long?,
    timeZone: TimeZone,
): String? {
    if (epochMillis == null) return null

    val dateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val year = dateTime.year.toString().padStart(4, '0')
    val month = (dateTime.month.ordinal + 1).toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    val second = dateTime.second.toString().padStart(2, '0')
    return "$year-$month-$day $hour:$minute:$second"
}

/**
 * 文件行的副标题。
 *
 * - 文件夹：时间 + `x项`；子项数量未获取时只显示时间。
 * - 文件：时间 + 文件大小；任一项缺失时只显示已有项。
 * - 两项都缺失时返回 null。
 *
 * 这个函数只应在列表加载阶段调用一次，不要在 LazyColumn item 里调用。
 */
internal fun fileEntrySummary(
    entry: FileEntry,
    timeZone: TimeZone,
    itemCountText: String? = null,
): String? {
    val modified = formatModifiedTime(entry.modifiedAt, timeZone)
    val detail = if (entry.isDirectory) {
        itemCountText
    } else {
        entry.size?.let(::formatFileSize)
    }
    val parts = listOfNotNull(modified, detail)
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
