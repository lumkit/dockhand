package io.github.lumkit.dockhand.domain.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class CopyNamePlanTest {

    @Test
    fun freshFileStartsAtFirstCopySuffix() {
        val plan = planCopyName("report.txt", isDirectory = false)

        assertEquals("report", plan.baseStem)
        assertEquals("txt", plan.extension)
        assertEquals(0, plan.startIndex)
        assertEquals("report_副本.txt", plan.candidate(0))
        assertEquals("report_副本1.txt", plan.candidate(1))
    }

    @Test
    fun existingCopySuffixContinuesWithNextNumber() {
        val plan = planCopyName("report_副本.txt", isDirectory = false)

        assertEquals("report", plan.baseStem)
        assertEquals(1, plan.startIndex)
        assertEquals("report_副本1.txt", plan.candidate(1))
    }

    @Test
    fun numberedSuffixContinuesFromNextNumber() {
        val plan = planCopyName("report_副本2.txt", isDirectory = false)

        assertEquals("report", plan.baseStem)
        assertEquals(3, plan.startIndex)
        assertEquals("report_副本3.txt", plan.candidate(3))
    }

    @Test
    fun stackedLegacySuffixIsCollapsed() {
        val plan = planCopyName("report_副本_副本.txt", isDirectory = false)

        assertEquals("report", plan.baseStem)
        assertEquals(1, plan.startIndex)
        assertEquals("report_副本1.txt", plan.candidate(1))
    }

    @Test
    fun directoriesAndMultiDotNamesKeepTheirShape() {
        val directory = planCopyName("report_副本", isDirectory = true)
        assertEquals("report", directory.baseStem)
        assertEquals(null, directory.extension)
        assertEquals(1, directory.startIndex)
        assertEquals("report_副本1", directory.candidate(1))

        val multiDot = planCopyName("report.final_副本.txt", isDirectory = false)
        assertEquals("report.final", multiDot.baseStem)
        assertEquals("txt", multiDot.extension)
        assertEquals(1, multiDot.startIndex)
        assertEquals("report.final_副本1.txt", multiDot.candidate(1))
    }

    @Test
    fun nameWithoutCopySuffixIsNotStripped() {
        val plan = planCopyName("report_副本-final.txt", isDirectory = false)

        assertEquals("report_副本-final", plan.baseStem)
        assertEquals(0, plan.startIndex)
        assertEquals("report_副本-final_副本.txt", plan.candidate(0))
    }
}
