package io.github.lumkit.dockhand.data.file

import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.domain.repository.DefaultFileBrowserRepository
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalCopyEngineTest {
    @Test
    fun movesFileAndDeletesSource() = runBlocking {
        val root = Files.createTempDirectory("dockhand-move")
        try {
            val home = Files.createDirectories(root.resolve("home"))
            val provider = LocalFileSystemProvider(
                homeDirectory = home,
                volumesDirectory = root.resolve("Volumes"),
            )
            Files.writeString(home.resolve("note.txt"), "hello")
            val destination = Files.createDirectories(home.resolve("destination"))
            val repository = DefaultFileBrowserRepository(listOf(provider))
            val sourceLocation = FileLocation(
                FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local),
                home.toString(),
            )
            val destinationLocation = sourceLocation.copy(path = destination.toString())
            val entry = provider.list(sourceLocation).first { it.name == "note.txt" }

            repository.moveWithProgress(
                entries = listOf(entry),
                destinationDirectory = destinationLocation,
            )

            assertEquals("hello", Files.readString(destination.resolve("note.txt")))
            assertEquals(false, Files.exists(home.resolve("note.txt")))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun copiesNestedDirectoryThroughRepository() = runBlocking {
        val root = Files.createTempDirectory("dockhand-copy")
        try {
            val home = Files.createDirectories(root.resolve("home"))
            val provider = LocalFileSystemProvider(
                homeDirectory = home,
                volumesDirectory = root.resolve("Volumes"),
            )
            val source = Files.createDirectories(home.resolve("source"))
            Files.createDirectories(source.resolve("nested"))
            Files.writeString(source.resolve("nested/a.txt"), "hello")
            val destination = Files.createDirectories(home.resolve("destination"))
            val repository = DefaultFileBrowserRepository(listOf(provider))
            val sourceLocation = FileLocation(
                FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local),
                home.toString(),
            )
            val destinationLocation = sourceLocation.copy(path = destination.toString())
            val entry = provider.list(sourceLocation).first { it.name == "source" }

            repository.copyWithProgress(
                entries = listOf(entry),
                destinationDirectory = destinationLocation,
            )
            repository.copyWithProgress(
                entries = listOf(entry),
                destinationDirectory = destinationLocation,
            )
            repository.copyWithProgress(
                entries = listOf(entry),
                destinationDirectory = destinationLocation,
            )

            assertEquals(
                "hello",
                Files.readString(destination.resolve("source/nested/a.txt")),
            )
            assertEquals(
                "hello",
                Files.readString(destination.resolve("source_副本/nested/a.txt")),
            )
            assertEquals(
                "hello",
                Files.readString(destination.resolve("source_副本1/nested/a.txt")),
            )
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
