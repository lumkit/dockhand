package io.github.dockhand.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileBreadcrumbTest {

    private val local = FileSource(
        id = FileSource.LOCAL_ID,
        name = "本机",
        kind = FileSourceKind.Local,
    )
    private val mtp = FileSource(
        id = "mtp:pixel",
        name = "Pixel 9",
        kind = FileSourceKind.Mtp,
    )

    @Test
    fun localRootOnlyHasSourceCrumb() {
        val crumbs = FileLocation(local, "/").breadcrumbs()

        assertEquals(listOf(FileBreadcrumb("本机", "/")), crumbs)
    }

    @Test
    fun localPathSplitsIntoClickableSegments() {
        val crumbs = FileLocation(local, "/Users/lumkit/Documents").breadcrumbs()

        assertEquals(
            listOf(
                FileBreadcrumb("本机", "/"),
                FileBreadcrumb("Users", "/Users"),
                FileBreadcrumb("lumkit", "/Users/lumkit"),
                FileBreadcrumb("Documents", "/Users/lumkit/Documents"),
            ),
            crumbs,
        )
    }

    @Test
    fun externalVolumeStartsAtVolumeRoot() {
        val crumbs = FileLocation(local, "/Volumes/lumkit-ssd-2t/Projects").breadcrumbs()

        assertEquals(
            listOf(
                FileBreadcrumb("lumkit-ssd-2t", "/Volumes/lumkit-ssd-2t"),
                FileBreadcrumb("Projects", "/Volumes/lumkit-ssd-2t/Projects"),
            ),
            crumbs,
        )
    }

    @Test
    fun mtpPathStartsAtDeviceName() {
        val crumbs = FileLocation(mtp, "/Internal storage/DCIM/Camera").breadcrumbs()

        assertEquals(
            listOf(
                FileBreadcrumb("Pixel 9", "/"),
                FileBreadcrumb("Internal storage", "/Internal storage"),
                FileBreadcrumb("DCIM", "/Internal storage/DCIM"),
                FileBreadcrumb("Camera", "/Internal storage/DCIM/Camera"),
            ),
            crumbs,
        )
    }

    @Test
    fun parentBreadcrumbIsNullAtRoot() {
        assertNull(FileLocation(local, "/").parentBreadcrumb())
        assertEquals(
            FileBreadcrumb("Users", "/Users"),
            FileLocation(local, "/Users/lumkit").parentBreadcrumb(),
        )
    }
}
