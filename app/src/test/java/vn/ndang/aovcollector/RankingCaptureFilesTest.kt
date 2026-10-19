package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingCaptureFilesTest {
    @Test fun everyStatisticsTableHasADescriptiveStableFilename() {
        for (combo in RankScreens.COMBOS) {
            val expected = "thong-ke-${combo.slug}-page-7"
            assertEquals(expected, RankingCaptureFiles.screenshotBase("0003-${combo.slug}-07"))
            assertEquals(expected, RankingCaptureFiles.screenshotBase("${combo.slug}-07"))
            assertEquals(expected, RankingCaptureFiles.screenshotBase(expected))
            assertTrue(expected.matches(Regex("[a-z0-9-]+")))
            assertEquals("${combo.label} · Trang 7", RankingCaptureFiles.title(expected))
            assertEquals("${combo.label} · Trang 7", RankingCaptureFiles.title("0003-${combo.slug}-07"))
        }
    }

    @Test fun namesRetainModeTableAndPageWithoutMacroStepNumbers() {
        val combo = RankScreens.COMBOS.first()
        val first = RankingCaptureFiles.screenshotBase("0001-${combo.slug}-01")
        val retried = RankingCaptureFiles.screenshotBase("12345-${combo.slug}-01")
        assertEquals(first, retried)
        assertTrue(first.endsWith("-page-1"))
        assertFalse(first.startsWith("0001"))
        assertEquals(6, RankScreens.COMBOS.map { RankingCaptureFiles.screenshotBase("0001-${it.slug}-01") }.toSet().size)
    }

    @Test fun unrelatedOrMalformedNamesAreNeverRelabeled() {
        val slug = RankScreens.COMBOS.first().slug
        for (name in listOf(
            "ranking-lost",
            "0001-ranking-link-not-open",
            "hero-page-1",
            "$slug-00",
            "thong-ke-$slug-page-0",
            "$slug-999999999999",
            "prefix-$slug-01",
            "$slug-01-extra"
        )) {
            assertNull(name, RankingCaptureFiles.page(name))
            assertEquals(name, RankingCaptureFiles.screenshotBase(name))
        }
    }

    @Test fun repeatedCustomRankingStepsKeepTheirDistinctNames() {
        val slug = RankScreens.COMBOS.first().slug
        for (step in listOf("0001", "0003")) {
            val token = "$step-$slug-01"
            assertEquals(token, RankingCaptureFiles.screenshotBase(token, 2))
            assertEquals(token, RankingCaptureFiles.screenshotBase(token, 0))
        }
    }

    @Test fun statisticsPagesSortNumericallyIncludingLegacyNames() {
        val slug = RankScreens.COMBOS.first().slug
        val filenames = listOf("thong-ke-$slug-page-10.png", "thong-ke-$slug-page-1.png", "0003-$slug-02.png")
        assertEquals(listOf(filenames[1], filenames[2], filenames[0]), filenames.sortedBy(RankingCaptureFiles::sortKey))
        assertEquals("other-page-10.png", RankingCaptureFiles.sortKey("other-page-10.png"))
    }

    @Test fun zipPlanPreservesNumericStatisticsPageOrder() {
        val root = Files.createTempDirectory("ranking-names").toFile()
        val slug = RankScreens.COMBOS.first().slug
        val files = listOf(10, 2, 1).map { File(root, "thong-ke-$slug-page-$it.png").apply { writeBytes(byteArrayOf(1)) } }
        val planned = ZipFiles.plan(files, root).flatMap { it.files }
        assertEquals(listOf(files[2], files[1], files[0]), planned)
    }
}
