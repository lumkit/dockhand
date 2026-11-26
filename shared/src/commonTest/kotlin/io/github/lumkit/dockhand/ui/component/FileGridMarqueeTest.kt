package io.github.lumkit.dockhand.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileGridMarqueeTest {

    @Test
    fun marqueeShrinksWhenPointerMovesBack() {
        val expanded = marqueeItemIndices(
            anchorRow = 0,
            anchorColumn = 0,
            pointerRow = 2,
            pointerColumn = 2,
            columns = 4,
            itemCount = 20,
        )
        val retracted = marqueeItemIndices(
            anchorRow = 0,
            anchorColumn = 0,
            pointerRow = 1,
            pointerColumn = 1,
            columns = 4,
            itemCount = 20,
        )

        // 选区完全由锚点到当前指针的矩形决定，指针回退后多出来的条目必须重新变回未选中。
        assertEquals(listOf(0, 1, 2, 4, 5, 6, 8, 9, 10), expanded)
        assertEquals(listOf(0, 1, 4, 5), retracted)
        assertTrue(expanded.containsAll(retracted))
    }

    @Test
    fun marqueeFlipsAroundAnchorWhenPointerCrossesIt() {
        val indices = marqueeItemIndices(
            anchorRow = 2,
            anchorColumn = 1,
            pointerRow = 1,
            pointerColumn = 0,
            columns = 3,
            itemCount = 12,
        )

        // 反向拖过锚点相当于交换矩形两端，仍然按行列取块。
        assertEquals(listOf(3, 4, 6, 7), indices)
    }

    @Test
    fun marqueeSelectsWholeGridWhenLastRowIsIncomplete() {
        val indices = marqueeItemIndices(
            anchorRow = 0,
            anchorColumn = 0,
            pointerRow = 2,
            pointerColumn = 3,
            columns = 4,
            itemCount = 10,
        )

        assertEquals((0 until 10).toList(), indices)
    }

    @Test
    fun marqueeIgnoresVirtualAnchorBelowContent() {
        val indices = marqueeItemIndices(
            anchorRow = 6,
            anchorColumn = 0,
            pointerRow = 1,
            pointerColumn = 2,
            columns = 3,
            itemCount = 10,
        )

        // 锚点落在内容下方时只会选到真实存在的行，不会越界。
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9), indices)
    }

    @Test
    fun marqueeClampsRowsWhenPointerIsFarBelowContent() {
        val indices = marqueeItemIndices(
            anchorRow = 0,
            anchorColumn = 0,
            pointerRow = 1000,
            pointerColumn = 2,
            columns = 3,
            itemCount = 10,
        )

        assertEquals((0 until 10).toList(), indices)
    }

    @Test
    fun marqueeSelectionIsEmptyWithoutItems() {
        assertEquals(emptyList(), marqueeItemIndices(0, 0, 3, 3, columns = 2, itemCount = 0))
    }

    @Test
    fun resolveCellFollowsScrollWithStationaryPointer() {
        val beforeScroll = gridItems(rows = 0..3) { row -> 8f + row * 110f }
        val afterScroll = gridItems(rows = 1..4) { row -> 8f + (row - 1) * 110f - 110f }
        val pointerX = 128f // 第 1 列
        val pointerY = 300f

        assertEquals(
            GridCell(row = 2, column = 1),
            resolveGridCell(pointerX, pointerY, beforeScroll, columns = 3, rowSpacing = 10f, columnSpacing = 10f),
        )
        // 内容上滚 110px 后同一个指针落在下一行：选区跟随滚动继续扩张。
        assertEquals(
            GridCell(row = 4, column = 1),
            resolveGridCell(pointerX, pointerY, afterScroll, columns = 3, rowSpacing = 10f, columnSpacing = 10f),
        )
    }

    @Test
    fun resolveCellRetractsWhenContentScrollsBack() {
        val scrolledBack = gridItems(rows = 0..3) { row -> 8f + row * 110f }

        // 滚回原位后同一个指针重新命中前面的行，指针不动时选区也会回退。
        assertEquals(
            GridCell(row = 2, column = 1),
            resolveGridCell(128f, 300f, scrolledBack, columns = 3, rowSpacing = 10f, columnSpacing = 10f),
        )
    }

    @Test
    fun resolveCellExtrapolatesBelowContentAndClampsColumns() {
        val items = gridItems(rows = 0..0) { 8f }

        // 指针在最后一行下方时按行高外推：行可以是虚拟行，列必须留在网格内。
        assertEquals(
            GridCell(row = 5, column = 3),
            resolveGridCell(1000f, 560f, items, columns = 4, rowSpacing = 10f, columnSpacing = 10f),
        )
        assertEquals(
            GridCell(row = 0, column = 0),
            resolveGridCell(-50f, 20f, items, columns = 4, rowSpacing = 10f, columnSpacing = 10f),
        )
    }

    @Test
    fun resolveCellUsesColumnSpacingWhenOnlyOneColumnIsVisible() {
        val items = listOf(
            GridVisibleItem(row = 0, column = 0, offsetX = 8f, offsetY = 8f, width = 100f, height = 100f),
        )

        // 只有一列可见时没有实测列间距，用传入的列间距外推。
        assertEquals(
            GridCell(row = 0, column = 1),
            resolveGridCell(130f, 20f, items, columns = 4, rowSpacing = 10f, columnSpacing = 10f),
        )
    }

    @Test
    fun resolveCellIsNullWithoutVisibleItems() {
        assertNull(resolveGridCell(0f, 0f, emptyList(), columns = 3, rowSpacing = 10f, columnSpacing = 10f))
    }

    private fun gridItems(
        rows: IntRange,
        columns: Int = 3,
        itemWidth: Float = 100f,
        itemHeight: Float = 100f,
        columnGap: Float = 10f,
        contentPadding: Float = 8f,
        rowTop: (Int) -> Float,
    ): List<GridVisibleItem> = buildList {
        rows.forEach { row ->
            repeat(columns) { column ->
                add(
                    GridVisibleItem(
                        row = row,
                        column = column,
                        offsetX = contentPadding + column * (itemWidth + columnGap),
                        offsetY = rowTop(row),
                        width = itemWidth,
                        height = itemHeight,
                    ),
                )
            }
        }
    }
}
