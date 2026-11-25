package io.github.lumkit.dockhand.core.archive

import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalArchiveServiceTest {
    @Test
    fun compressesFilesAndDeletesSourceWhenRequested() = runBlocking {
        val root = Files.createTempDirectory("dockhand-archive").toFile()
        try {
            val sourceDir = root.resolve("source").apply { mkdirs() }
            val outputDir = root.resolve("output").apply { mkdirs() }
            val first = sourceDir.resolve("one.txt").apply { writeText("one") }
            val second = sourceDir.resolve("two.txt").apply { writeText("two") }
            val source = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
            val entries = listOf(first, second).map {
                FileEntry(
                    id = it.path,
                    name = it.name,
                    location = FileLocation(source, it.path),
                    isDirectory = false,
                    size = it.length(),
                )
            }

            LocalArchiveService().compress(
                CompressRequest(
                    entries = entries,
                    destination = FileLocation(source, outputDir.path),
                    archiveName = "bundle",
                    deleteSource = true,
                ),
                CompressCallbacks(),
            )

            val archive = outputDir.resolve("bundle.zip")
            assertTrue(archive.isFile)
            assertEquals(2, ZipFile(archive).fileHeaders.size)
            assertFalse(first.exists())
            assertFalse(second.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reportsCompleteByteProgressForDirectoryZip() = runBlocking {
        val root = Files.createTempDirectory("dockhand-progress").toFile()
        try {
            val sourceDir = root.resolve("folder").apply { mkdirs() }
            sourceDir.resolve("one.txt").writeText("one")
            sourceDir.resolve("two.txt").writeText("two")
            val outputDir = root.resolve("output").apply { mkdirs() }
            val source = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
            val folderEntry = FileEntry(
                id = sourceDir.path,
                name = sourceDir.name,
                location = FileLocation(source, sourceDir.path),
                isDirectory = true,
                size = 6,
            )
            var lastCompleted = 0L
            var lastTotal = 0L

            LocalArchiveService().compress(
                CompressRequest(
                    entries = listOf(folderEntry),
                    destination = FileLocation(source, outputDir.path),
                    archiveName = "folder",
                ),
                CompressCallbacks(
                    onBytes = { completed, total ->
                        lastCompleted = completed
                        lastTotal = total
                    },
                ),
            )

            assertTrue(lastTotal > 0)
            assertEquals(lastTotal, lastCompleted)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun extractsZipIntoSubfolder() = runBlocking {
        val root = Files.createTempDirectory("dockhand-extract").toFile()
        try {
            val sourceDir = root.resolve("source").apply { mkdirs() }
            val outputDir = root.resolve("output").apply { mkdirs() }
            val file = sourceDir.resolve("note.txt").apply { writeText("hello") }
            val source = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
            val entry = FileEntry(
                id = file.path,
                name = file.name,
                location = FileLocation(source, file.path),
                isDirectory = false,
                size = file.length(),
            )
            val service = LocalArchiveService()
            service.compress(
                CompressRequest(
                    entries = listOf(entry),
                    destination = FileLocation(source, outputDir.path),
                    archiveName = "bundle",
                ),
                CompressCallbacks(),
            )
            val archive = outputDir.resolve("bundle.zip")
            val archiveEntry = FileEntry(
                id = archive.path,
                name = archive.name,
                location = FileLocation(source, archive.path),
                isDirectory = false,
                size = archive.length(),
            )

            service.extract(
                ExtractRequest(
                    archive = archiveEntry,
                    destinationDirectory = FileLocation(source, outputDir.path),
                    createSubfolder = true,
                    subfolderName = "unpacked",
                ),
                ExtractCallbacks(),
            )

            assertEquals("hello", outputDir.resolve("unpacked/note.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createsTarGzArchive() = runBlocking {
        val root = Files.createTempDirectory("dockhand-targz").toFile()
        try {
            val sourceDir = root.resolve("source").apply { mkdirs() }
            val outputDir = root.resolve("output").apply { mkdirs() }
            val file = sourceDir.resolve("note.txt").apply { writeText("hello") }
            val source = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
            val entry = FileEntry(
                id = file.path,
                name = file.name,
                location = FileLocation(source, file.path),
                isDirectory = false,
                size = file.length(),
            )

            LocalArchiveService().compress(
                CompressRequest(
                    entries = listOf(entry),
                    destination = FileLocation(source, outputDir.path),
                    archiveName = "note",
                    format = ArchiveFormat.TarGz,
                ),
                CompressCallbacks(),
            )

            val archive = outputDir.resolve("note.tar.gz")
            assertTrue(archive.isFile)
            GzipCompressorInputStream(archive.inputStream()).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    var entries = 0
                    while (tar.nextEntry != null) entries++
                    assertEquals(1, entries)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
