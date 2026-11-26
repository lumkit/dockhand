package io.github.lumkit.dockhand.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionCompareTest {

    @Test
    fun stableIsNewerThanPrerelease() {
        assertTrue(compareVersions("0.1.0", "0.1.0-beta.1") > 0)
        assertTrue(compareVersions("0.1.0-beta.1", "0.1.0") < 0)
    }

    @Test
    fun prereleaseNumbersAreComparedNumerically() {
        assertTrue(compareVersions("0.1.0-beta.2", "0.1.0-beta.1") > 0)
        assertTrue(compareVersions("0.1.0-beta.10", "0.1.0-beta.2") > 0)
    }

    @Test
    fun leadingVAndMissingSegmentsAreIgnored() {
        assertTrue(compareVersions("v0.2.0", "0.1.9") > 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
    }

    @Test
    fun sameVersionIsEqualAndInvalidIsNeutral() {
        assertEquals(0, compareVersions("0.1.0-beta.1", "0.1.0-beta.1"))
        assertEquals(0, compareVersions("dev", "0.1.0"))
    }
}
