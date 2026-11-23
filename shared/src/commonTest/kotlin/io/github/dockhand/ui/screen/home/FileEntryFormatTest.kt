package io.github.dockhand.ui.screen.home

import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FileSource
import io.github.dockhand.domain.model.FileSourceKind
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileEntryFormatTest {

    private val source = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
    private val parent = FileLocation(source, "/tmp")
    private val timestamp = 1704164645000L // 2024-01-02 03:04:05 UTC

    @Test
    fun folderShowsTimeAndChildCount() {
        val entry = folder(childCount = 3)

        assertEquals(
            "2024-01-02 03:04:05 · 3项",
            fileEntrySummary(entry, TimeZone.UTC, itemCountText = "3项"),
        )
    }

    @Test
    fun folderHidesMissingChildCount() {
        val entry = folder(childCount = null)

        assertEquals(
            "2024-01-02 03:04:05",
            fileEntrySummary(entry, TimeZone.UTC),
        )
    }

    @Test
    fun folderShowsCountWhenTimeIsMissing() {
        val entry = folder(childCount = 0).copy(modifiedAt = null)

        assertEquals("0项", fileEntrySummary(entry, TimeZone.UTC, itemCountText = "0项"))
    }

    @Test
    fun fileShowsTimeAndSize() {
        val entry = file(size = 1536)

        assertEquals(
            "2024-01-02 03:04:05 · 1.5 KB",
            fileEntrySummary(entry, TimeZone.UTC),
        )
    }

    @Test
    fun fileHidesMissingSize() {
        val entry = file(size = null)

        assertEquals(
            "2024-01-02 03:04:05",
            fileEntrySummary(entry, TimeZone.UTC),
        )
    }

    @Test
    fun noMetadataMeansNoSummaryRow() {
        val entry = file(size = null).copy(modifiedAt = null)

        assertNull(fileEntrySummary(entry, TimeZone.UTC))
    }

    @Test
    fun timeFormatPadsEveryComponent() {
        assertEquals(
            "2024-01-02 03:04:05",
            formatModifiedTime(timestamp, TimeZone.UTC),
        )
        assertNull(formatModifiedTime(null, TimeZone.UTC))
    }

    @Test
    fun fileSizeUsesReadableUnits() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("1 MB", formatFileSize(1024 * 1024))
    }

    private fun folder(childCount: Int?) = FileEntry(
        id = parent.path + "/folder",
        name = "folder",
        location = parent,
        isDirectory = true,
        childCount = childCount,
        modifiedAt = timestamp,
    )

    private fun file(size: Long?) = FileEntry(
        id = parent.path + "/file.txt",
        name = "file.txt",
        location = parent,
        isDirectory = false,
        size = size,
        modifiedAt = timestamp,
    )
}