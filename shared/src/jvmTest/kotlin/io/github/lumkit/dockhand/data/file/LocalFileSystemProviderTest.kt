package io.github.lumkit.dockhand.data.file

import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FilePlaceKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalFileSystemProviderTest {

    @Test
    fun placesAndListingComeFromTheFileSystem(): Unit = runBlocking {
        val root = Files.createTempDirectory("dockhand-local")
        try {
            val home = Files.createDirectories(root.resolve("home"))
            Files.createDirectories(home.resolve("Documents"))
            Files.writeString(home.resolve("Documents/inside.txt"), "x")
            Files.writeString(home.resolve("Documents/.hidden"), "x")
            Files.createDirectories(home.resolve("zdir"))
            Files.writeString(home.resolve("b.txt"), "b")
            Files.writeString(home.resolve("A.txt"), "a")
            Files.writeString(home.resolve(".hidden"), "hidden")

            val provider = LocalFileSystemProvider(
                homeDirectory = home,
                volumesDirectory = root.resolve("Volumes"),
            )

            val places = provider.observePlaces().first()
            val homePlace = places.first { it.kind == FilePlaceKind.Home }
            assertEquals(home.toAbsolutePath().normalize().toString(), homePlace.location.path)
            assertTrue(places.any { it.kind == FilePlaceKind.Documents })

            val entries = provider.list(homePlace.location)
            assertEquals(
                listOf("Documents", "zdir", "A.txt", "b.txt"),
                entries.map { it.name },
            )
            assertTrue(entries.first().isDirectory)
            assertEquals(1, entries.first { it.name == "Documents" }.childCount)
            assertEquals(0, entries.first { it.name == "zdir" }.childCount)
            assertEquals("a", Files.readString(Path.of(entries.first { it.name == "A.txt" }.location.path)))
            assertTrue(entries.none { it.name == ".hidden" })

            provider.setShowHiddenItems(true)
            val withHidden = provider.list(homePlace.location)
            assertTrue(withHidden.any { it.name == ".hidden" && it.isHidden })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun copyingFileAndDirectoryCreatesTargets(): Unit = runBlocking {
        val root = Files.createTempDirectory("dockhand-copy")
        try {
            val home = Files.createDirectories(root.resolve("home"))
            Files.writeString(home.resolve("note.txt"), "hello")
            val folder = Files.createDirectories(home.resolve("folder"))
            Files.writeString(folder.resolve("inner.txt"), "inner")
            val target = Files.createDirectories(home.resolve("target"))

            val provider = LocalFileSystemProvider(
                homeDirectory = home,
                volumesDirectory = root.resolve("Volumes"),
            )
            val homeLocation = provider.observePlaces().first()
                .first { it.kind == FilePlaceKind.Home }
                .location
            val entries = provider.list(homeLocation)
            val targetLocation = FileLocation(homeLocation.source, target.toString())

            provider.copy(entries.first { it.name == "note.txt" }.location, targetLocation)
            assertEquals("hello", Files.readString(target.resolve("note.txt")))

            provider.copy(entries.first { it.name == "folder" }.location, targetLocation)
            assertEquals("inner", Files.readString(target.resolve("folder").resolve("inner.txt")))

            assertFailsWith<FileBrowserException> {
                provider.copy(entries.first { it.name == "note.txt" }.location, targetLocation)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun creatingFolderCreatesDirectoryAndRejectsDuplicates(): Unit = runBlocking {
        val root = Files.createTempDirectory("dockhand-mkdir")
        try {
            val home = Files.createDirectories(root.resolve("home"))
            val provider = LocalFileSystemProvider(
                homeDirectory = home,
                volumesDirectory = root.resolve("Volumes"),
            )
            val homeLocation = provider.observePlaces().first()
                .first { it.kind == FilePlaceKind.Home }
                .location

            provider.createDirectory(homeLocation, "NewFolder")
            provider.createFile(homeLocation, "note.txt")

            assertTrue(Files.isDirectory(home.resolve("NewFolder")))
            assertTrue(Files.isRegularFile(home.resolve("note.txt")))
            assertTrue(provider.list(homeLocation).any { it.name == "NewFolder" })

            assertFailsWith<FileBrowserException> {
                provider.createDirectory(homeLocation, "NewFolder")
            }
            assertFailsWith<FileBrowserException> {
                provider.createFile(homeLocation, "note.txt")
            }
            assertFailsWith<FileBrowserException> {
                provider.createDirectory(homeLocation, "a/b")
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun observesDirectoryChanges(): Unit = runBlocking {
        val root = Files.createTempDirectory("dockhand-watch")
        try {
            val provider = LocalFileSystemProvider(
                homeDirectory = root,
                volumesDirectory = root.resolve("Volumes"),
            )
            val homeLocation = provider.observePlaces().first()
                .first { it.kind == FilePlaceKind.Home }
                .location
            val received = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.IO) {
                provider.observeChanges(homeLocation).collect {
                    received.complete(Unit)
                }
            }

            delay(300)
            Files.createDirectory(root.resolve("Changed"))
            withTimeout(5_000) { received.await() }
            job.cancel()
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun listingMissingPathReturnsUserFacingError(): Unit = runBlocking {
        val root = Files.createTempDirectory("dockhand-missing")
        try {
            val provider = LocalFileSystemProvider(
                homeDirectory = root,
                volumesDirectory = root.resolve("Volumes"),
            )
            val homeLocation = provider.observePlaces().first()
                .first { it.kind == FilePlaceKind.Home }
                .location

            assertFailsWith<FileBrowserException> {
                provider.list(homeLocation.copy(path = root.resolve("missing").toString()))
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}