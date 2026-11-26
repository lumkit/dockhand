package io.github.lumkit.dockhand.ui.component

import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalFileDragTest {

    private val local = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
    private val mtp = FileSource("mtp:1", "Pixel", FileSourceKind.Mtp)

    @Test
    fun unselectedEntryDragsOnlyItself() {
        val pressed = entry("a.txt", local)
        val other = entry("b.txt", local)

        assertEquals(
            listOf(pressed),
            dragOutEntries(pressed, selected = false, selectedEntries = listOf(pressed, other)),
        )
    }

    @Test
    fun selectedEntryDragsWholeSelectionInOrder() {
        val first = entry("a.txt", local)
        val second = entry("b.txt", local)
        val third = entry("c.txt", local)

        assertEquals(
            listOf(first, second, third),
            dragOutEntries(second, selected = true, selectedEntries = listOf(first, second, third)),
        )
    }

    @Test
    fun selectedEntryWithEmptySelectionFallsBackToItself() {
        val entry = entry("a.txt", local)

        assertEquals(
            listOf(entry),
            dragOutEntries(entry, selected = true, selectedEntries = emptyList()),
        )
    }

    @Test
    fun mtpEntryCannotBeDraggedOut() {
        val mtpEntry = entry("photo.jpg", mtp)

        assertTrue(dragOutEntries(mtpEntry, selected = true, selectedEntries = listOf(mtpEntry)).isEmpty())
        assertTrue(dragOutEntries(mtpEntry, selected = false, selectedEntries = emptyList()).isEmpty())
    }

    private fun entry(name: String, source: FileSource) = FileEntry(
        id = source.id + "/" + name,
        name = name,
        location = FileLocation(source, "/tmp/" + name),
        isDirectory = false,
    )
}
