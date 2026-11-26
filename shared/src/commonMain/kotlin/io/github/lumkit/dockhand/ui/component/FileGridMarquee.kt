package io.github.lumkit.dockhand.ui.component

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 网格单元坐标；按行优先展开成条目索引，行列取值可能落在条目之外。 */
internal data class GridCell(val row: Int, val column: Int)

/**
 * 一次网格框选会话。
 *
 * [start] / [current] 记录的是根坐标下的指针位置，绘制选框时直接使用；
 * 选区由 [anchor] 与当前指针命中的单元围成的行列矩形推导。
 * 指针回退时矩形收缩，内容滚动时命中的单元随内容移动，
 * 两种情况都只依赖实时布局，不需要记录“曾经选过什么”。
 */
internal data class GridMarquee(
    /** 按下时的指针位置，选框的固定端。 */
    val start: Offset,
    /** 当前指针位置，选框的活动端。 */
    val current: Offset,
    /** 按下时命中的单元，作为扩选的锚点。 */
    val anchor: GridCell,
)

/** 一个可见条目在网格坐标系里的位置；只保留命中计算需要的字段。 */
internal data class GridVisibleItem(
    val row: Int,
    val column: Int,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
)

/**
 * 把网格坐标系里的点换算成网格单元。
 *
 * 行列位置只从 [items]（当前可见条目）推导，不依赖累计滚动偏移：
 * 内容滚动后可见行的位置随之变化，同一个点会落在更靠后的行上，
 * 所以指针不动时选区也会跟随滚动继续扩张，指针回退时又会收缩。
 *
 * 点落在网格空白处时按行高/列宽外推：行允许越出当前内容
 * （从空白处起框时锚点是虚拟行），列始终夹紧到网格范围内。
 */
internal fun resolveGridCell(
    x: Float,
    y: Float,
    items: List<GridVisibleItem>,
    columns: Int,
    rowSpacing: Float,
    columnSpacing: Float,
): GridCell? {
    if (items.isEmpty()) return null
    val safeColumns = columns.coerceAtLeast(1)

    val rowTops = items.groupBy { it.row }.mapValues { (_, cells) -> cells.minOf { it.offsetY } }
    val visibleRows = rowTops.keys.sorted()
    val rowStride = if (visibleRows.size >= 2) {
        rowTops.getValue(visibleRows[1]) - rowTops.getValue(visibleRows[0])
    } else {
        items.maxOf { it.height } + rowSpacing
    }.coerceAtLeast(1f)
    val row = visibleRows.first() +
            floor((y - rowTops.getValue(visibleRows.first())) / rowStride).toInt()

    val columnLefts = items.groupBy { it.column }
        .mapValues { (_, cells) -> cells.minOf { it.offsetX } }
    val visibleColumns = columnLefts.keys.sorted()
    val columnStride = if (visibleColumns.size >= 2) {
        columnLefts.getValue(visibleColumns[1]) - columnLefts.getValue(visibleColumns[0])
    } else {
        items.maxOf { it.width } + columnSpacing
    }.coerceAtLeast(1f)
    val column = visibleColumns.first() +
            floor((x - columnLefts.getValue(visibleColumns.first())) / columnStride).toInt()

    return GridCell(row.coerceAtLeast(0), column.coerceIn(0, safeColumns - 1))
}

/**
 * 锚点单元与指针单元围成的矩形内的条目索引。
 *
 * 行可以越出内容范围（锚点落在空白处），越界部分在这里裁掉；
 * 最后一行不满时只取真实存在的条目，避免把矩形压缩成错误的列。
 */
internal fun marqueeItemIndices(
    anchorRow: Int,
    anchorColumn: Int,
    pointerRow: Int,
    pointerColumn: Int,
    columns: Int,
    itemCount: Int,
): List<Int> {
    if (itemCount <= 0) return emptyList()
    val safeColumns = columns.coerceAtLeast(1)
    val fromRow = min(anchorRow, pointerRow)
    val toRow = max(anchorRow, pointerRow)
    val fromColumn = min(anchorColumn, pointerColumn)
    val toColumn = max(anchorColumn, pointerColumn)
    // 锚点可能落在内容下方的虚拟行，先裁掉不可能有内容的行，避免空转。
    val lastRowWithItems = (itemCount - 1) / safeColumns
    val result = ArrayList<Int>()
    for (row in fromRow..min(toRow, lastRowWithItems)) {
        val rowStart = row * safeColumns + fromColumn
        val rowEnd = min(row * safeColumns + toColumn, itemCount - 1)
        if (rowStart !in 0..rowEnd) continue
        for (index in rowStart..rowEnd) result.add(index)
    }
    return result
}
