package io.github.lumkit.dockhand.core.archive

import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
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
}
