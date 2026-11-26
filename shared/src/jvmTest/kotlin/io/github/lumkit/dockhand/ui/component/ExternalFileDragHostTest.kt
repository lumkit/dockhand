package io.github.lumkit.dockhand.ui.component

import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFileDragHostTest {

    @Test
    fun moveCompletionDeletesOnlyExternalMoves() {
        assertTrue(shouldDeleteMovedSource(TransferHandler.MOVE, internalDrop = false, pointerInsideWindow = false))
        assertFalse(shouldDeleteMovedSource(TransferHandler.COPY, internalDrop = false, pointerInsideWindow = false))
        assertFalse(shouldDeleteMovedSource(TransferHandler.LINK, internalDrop = false, pointerInsideWindow = false))
        assertFalse(shouldDeleteMovedSource(TransferHandler.NONE, internalDrop = false, pointerInsideWindow = false))
        assertFalse(shouldDeleteMovedSource(TransferHandler.MOVE, internalDrop = true, pointerInsideWindow = false))
        assertFalse(shouldDeleteMovedSource(TransferHandler.MOVE, internalDrop = false, pointerInsideWindow = true))
    }

    @Test
    fun localEntriesForDragKeepsExistingLocalEntriesInOrder() {
        val root = Files.createTempDirectory("dockhand-drag-out")
        try {
            val fileA = Files.writeString(root.resolve("a.txt"), "a").toFile()
            val fileB = Files.createDirectory(root.resolve("b")).toFile()
            val missing = File(root.toFile(), "missing.txt")
            val local = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
            val mtp = FileSource("mtp:1", "Pixel", FileSourceKind.Mtp)
            val entries = listOf(
                entry(fileA, local),
                entry(missing, local),
                entry(fileA, local),
                entry(File(root.toFile(), "photo.jpg"), mtp),
                entry(fileB, local),
            )

            val resolved = localEntriesForDrag(entries)

            assertEquals(
                listOf(fileA.absolutePath, fileB.absolutePath),
                resolved.map { it.location.path },
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun transferableExposesJavaFileListFlavor() {
        val file = Files.createTempFile("dockhand-drag", ".txt").toFile()
        try {
            val transferable = FileListTransferable(listOf(file))

            assertEquals(
                listOf(DataFlavor.javaFileListFlavor),
                transferable.transferDataFlavors.toList(),
            )
            assertTrue(transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            assertFalse(transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
            assertEquals(listOf(file), transferable.getTransferData(DataFlavor.javaFileListFlavor))
            assertFailsWith<IllegalArgumentException> {
                transferable.getTransferData(DataFlavor.stringFlavor)
            }
        } finally {
            file.delete()
        }
    }

    private fun entry(file: File, source: FileSource) = FileEntry(
        id = file.absolutePath,
        name = file.name,
        location = FileLocation(source, file.absolutePath),
        isDirectory = file.isDirectory,
    )
}
