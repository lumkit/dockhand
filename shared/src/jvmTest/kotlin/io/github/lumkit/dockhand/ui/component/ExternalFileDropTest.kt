package io.github.lumkit.dockhand.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.lumkit.dockhand.ui.screen.home.PaneId
import java.awt.dnd.DnDConstants
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalFileDropTest {

    @Test
    fun mapsAwtDropActions() {
        assertEquals(ExternalDropAction.Copy, externalDropActionOf(DnDConstants.ACTION_COPY))
        assertEquals(ExternalDropAction.Move, externalDropActionOf(DnDConstants.ACTION_MOVE))
        assertEquals(ExternalDropAction.Link, externalDropActionOf(DnDConstants.ACTION_LINK))
        assertNull(externalDropActionOf(DnDConstants.ACTION_NONE))
        // 组合动作不是“明确动作”，按无动作处理。
        assertNull(externalDropActionOf(DnDConstants.ACTION_COPY_OR_MOVE))
    }

    @Test
    fun mapsJavaFileListToPaths() {
        val files = listOf(
            File("/tmp/drop-a.txt"),
            "not-a-file",
            File("/tmp/drop-b.txt"),
        )

        assertEquals(
            listOf("/tmp/drop-a.txt", "/tmp/drop-b.txt"),
            javaFileListPaths(files),
        )
        assertEquals(emptyList<String>(), javaFileListPaths(emptyList<Any>()))
    }

    @Test
    fun hoverFollowsPaneBoundsAndFocusesOnlyOnPaneChange() {
        val controller = ExternalDropController()
        val bridge = RecordingBridge()
        controller.bridge = bridge
        controller.updatePaneBounds(PaneId.Left, Rect(0f, 0f, 100f, 100f))
        controller.updatePaneBounds(PaneId.Right, Rect(100f, 0f, 200f, 100f))

        assertTrue(controller.onDragMoved(Offset(50f, 50f), ExternalDropAction.Copy))
        assertEquals(
            ExternalDropHover(PaneId.Left, ExternalDropAction.Copy),
            controller.hover,
        )
        assertEquals(listOf(PaneId.Left), bridge.focused)

        // 同一面板内移动不重复聚焦，只更新动作。
        assertTrue(controller.onDragMoved(Offset(60f, 60f), ExternalDropAction.Move))
        assertEquals(listOf(PaneId.Left), bridge.focused)
        assertEquals(
            ExternalDropHover(PaneId.Left, ExternalDropAction.Move),
            controller.hover,
        )

        // 换到右面板时聚焦一次。
        assertTrue(controller.onDragMoved(Offset(150f, 50f), ExternalDropAction.Copy))
        assertEquals(listOf(PaneId.Left, PaneId.Right), bridge.focused)

        // 移出所有面板：蒙层清空。
        assertFalse(controller.onDragMoved(Offset(500f, 500f), ExternalDropAction.Copy))
        assertNull(controller.hover)
        controller.onDragExited()
        assertNull(controller.hover)
    }

    @Test
    fun dropRoutesToPaneAndClearsHover() {
        val controller = ExternalDropController()
        val bridge = RecordingBridge()
        controller.bridge = bridge
        controller.updatePaneBounds(PaneId.Left, Rect(0f, 0f, 100f, 100f))
        controller.updatePaneBounds(PaneId.Right, Rect(100f, 0f, 200f, 100f))

        controller.onDragMoved(Offset(150f, 50f), ExternalDropAction.Move)
        assertTrue(
            controller.onDrop(
                position = Offset(150f, 50f),
                paths = listOf("/tmp/a.txt", "/tmp/b.txt"),
                action = ExternalDropAction.Move,
            ),
        )

        assertEquals(
            listOf(DropCall(PaneId.Right, listOf("/tmp/a.txt", "/tmp/b.txt"), ExternalDropAction.Move)),
            bridge.drops,
        )
        assertNull(controller.hover)

        // 没有目标面板时不分发。
        assertFalse(controller.onDrop(Offset(500f, 500f), listOf("/tmp/a.txt"), null))
        assertEquals(1, bridge.drops.size)
    }

    @Test
    fun dropWithoutBridgeIsIgnored() {
        val controller = ExternalDropController()
        controller.updatePaneBounds(PaneId.Left, Rect(0f, 0f, 100f, 100f))

        assertFalse(controller.onDrop(Offset(50f, 50f), listOf("/tmp/a.txt"), null))
    }

    @Test
    fun removingPaneClearsItsHover() {
        val controller = ExternalDropController()
        controller.updatePaneBounds(PaneId.Left, Rect(0f, 0f, 100f, 100f))
        controller.onDragMoved(Offset(50f, 50f), ExternalDropAction.Copy)

        controller.removePaneBounds(PaneId.Left)

        assertNull(controller.hover)
        assertNull(controller.paneAt(Offset(50f, 50f)))
    }

    private class RecordingBridge : ExternalDropBridge {
        val focused = mutableListOf<PaneId>()
        val drops = mutableListOf<DropCall>()

        override fun onPaneFocused(paneId: PaneId) {
            focused += paneId
        }

        override fun onDrop(paneId: PaneId, paths: List<String>, action: ExternalDropAction?) {
            drops += DropCall(paneId, paths, action)
        }
    }

    private data class DropCall(
        val paneId: PaneId,
        val paths: List<String>,
        val action: ExternalDropAction?,
    )
}
