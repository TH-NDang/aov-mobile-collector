package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChessBlessingsTest {
    private fun title(text: String, x: Double, y: Double) = GameScreens.Word(text, x, y - .012, x + .15, y + .012)

    private fun tiles(words: List<GameScreens.Word>) = ChessBlessings.tiles(words, { true }, { _, y -> y + .025 })

    @Test fun identitySurvivesScrollOffsetsAndAccentOrCaseChanges() {
        val a = tiles(listOf(title("Giây phút huy hoàng", .33, .38))).single()
        val b = tiles(listOf(title("GIAY PHUT HUY HOANG", .33, .67))).single()
        assertEquals(a.id, b.id)
        assertNotEquals(a.y, b.y)
        assertEquals("Giây phút huy hoàng", a.label)
    }

    @Test fun columnsFollowTheirTitlesAndPartialRowsWaitUntilFullyVisible() {
        val words = listOf(
            title("Right", .68, .38),
            title("Left", .33, .38),
            title("Next", .33, .65),
            title("Clipped top", .33, .26),
            title("Clipped bottom", .68, .98)
        )
        val found = tiles(words)
        assertEquals(listOf("Left", "Right", "Next"), found.map { it.label })
        assertEquals(listOf(.275, .615, .275), found.map { it.x })
        assertEquals(2, ChessBlessings.tiles(words, { true }, { _, y -> if (y < .6) y + .025 else null }).size)
    }

    @Test fun noUnverifiedHashFallbackOrDescriptionTitle() {
        val words = listOf(title("Khi bạn đạt cấp 9 nhận 40 vàng", .33, .42))
        assertTrue(ChessBlessings.tiles(words, { false }, { _, y -> y }).isEmpty())
        assertTrue(ChessBlessings.tiles(emptyList(), { true }, { _, y -> y }).isEmpty())
        assertTrue(ChessBlessings.cyan(0xff00a6ef.toInt()))
        assertFalse(ChessBlessings.cyan(0xff7893bc.toInt()))
        assertFalse(ChessBlessings.cyan(0xff16478c.toInt()))
        assertFalse(ChessBlessings.cyan(0xffeeeeee.toInt()))
    }

    @Test fun splitHeadingIsJoinedWithinColumnOnly() {
        val words = listOf(
            GameScreens.Word("Giây phút", .33, .36, .40, .39),
            GameScreens.Word("huy hoàng", .405, .36, .53, .39),
            title("Cá voi sát thủ 1", .68, .375)
        )
        assertEquals(listOf("Giây phút huy hoàng", "Cá voi sát thủ 1"), tiles(words).map { it.label })
    }

    @Test fun fullNamesDoNotCollideWhenTheyShareTheFirstFortyEightCharacters() {
        val prefix = "Chúc phúc huyền thoại với một tên dài được nhận diện "
        assertNotEquals(ChessBlessings.key(prefix + "1"), ChessBlessings.key(prefix + "2"))
    }

    @Test fun iconMeasurementTracksScrollingAndRejectsClippedPictograms() {
        fun at(top: Int, bottom: Int, title: Double) = ChessBlessings.iconY(1000, 1000, .275, title) { x, y ->
            if (x in 250..300 && y in top..bottom) 0xffeeeeee.toInt() else 0xff16478c.toInt()
        }
        assertEquals(.395, at(315, 475, .377)!!, .001)
        assertEquals(.665, at(585, 745, .645)!!, .001)
        assertNull(at(858, 999, .915))
        assertNull(at(220, 380, .30))
        assertNull(at(390, 410, .38))
    }

    @Test fun wrappedTitleProducesOneSaveAndRowsStayLeftToRightDespiteIconHeight() {
        val words = listOf(title("First line", .33, .38), title("second line", .33, .42), title("Right", .68, .38))
        val found = ChessBlessings.tiles(words, { true }, { x, _ -> if (x < .5) .405 else .395 })
        assertEquals(listOf("First line second line", "Right"), found.map { it.label })
    }

    @Test fun aliasesNeverSubstituteArtworkForVerifiedBlessingNames() {
        val first = setOf(ChessBlessings.key("Cá voi sát thủ 1"), "same-art")
        val second = setOf(ChessBlessings.key("Cá voi sát thủ 2"), "same-art")
        val group = "chuc-phuc-thuong"
        assertFalse(ChessBlessings.sameSave(group, first, second))
        assertFalse(ChessBlessings.sameSave(group, setOf("same-art"), first))
        assertTrue(ChessBlessings.sameSave(group, first, setOf(first.first(), "new-art")))
        assertFalse(ChessBlessings.sameSave(group, setOf("other-art"), first))
        assertTrue(ChessBlessings.sameSave("chieu-quan-co", setOf("old-skill"), setOf("old-skill", "new-skill")))
    }

    @Test fun oneAnonymousLegacyIconCannotSuppressTwoDifferentRecognizedTitles() {
        val group = "chuc-phuc-thuong"
        val anonymous = setOf("shared-icon-hash")
        val first = setOf("shared-icon-hash", ChessBlessings.key("Cá voi sát thủ 1"))
        val second = setOf("shared-icon-hash", ChessBlessings.key("Cá voi sát thủ 2"))
        assertFalse(ChessBlessings.sameSave(group, anonymous, first))
        assertFalse(ChessBlessings.sameSave(group, anonymous, second))
        assertFalse(ChessBlessings.sameSave(group, first, second))
        assertTrue(ChessBlessings.sameSave(group, first, first))
        assertTrue(
            "Legacy-only lookups retain their old identity semantics",
            ChessBlessings.sameSave(group, anonymous, setOf("shared-icon-hash", "another-probe"))
        )
    }

    @Test fun crossRunReuseRequiresSameNameGroupAndIntactReadableOriginal() {
        val dir = Files.createTempDirectory("blessing-index").toFile()
        try {
            val root = File(dir, "originals").apply { mkdirs() }
            val index = File(dir, BlessingPhotoIndex.FILE)
            val picture = File(root, "thuong/old-run/blessing.jpg").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val group = "chuc-phuc-thuong"
            val key = ChessBlessings.key("Giây phút huy hoàng")
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, key))
            BlessingPhotoIndex.record(index, root, group, key, picture)
            assertTrue(BlessingPhotoIndex.hasSaved(index, root, group, key))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, "chuc-phuc-truyen-thuyet", key))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, ChessBlessings.key("Nấc thang vinh diệu")))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, "same-art"))
            picture.writeBytes(byteArrayOf(4, 3, 2, 1))
            assertFalse("Same size replacement must not count as obtained", BlessingPhotoIndex.hasSaved(index, root, group, key))
            picture.writeBytes(byteArrayOf(1, 2, 3))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, key))
            picture.delete()
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, key))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun art(shift: Int = 0) = "blessing-art-v1:500:" + (0 until 256).joinToString("") {
        if ((it + shift) % 16 in 3..10) "1" else "0"
    }

    @Test fun reportedPhiPhifDriftRequiresFullIconEvidenceAndKeepsNumericVariants() {
        val group = "chuc-phuc-thuong"
        val first = setOf(ChessBlessings.key("Hoàn phí 5 vàng"), art())
        val noisy = setOf(ChessBlessings.key("Hoàn phif 5 vàng"), art())
        assertTrue(ChessBlessings.sameSave(group, first, noisy))
        assertTrue(ChessBlessings.sameSave(group, noisy, first))
        assertFalse(ChessBlessings.sameSave(group, first, noisy.filterNot { it.startsWith("blessing-art") }.toSet()))
        assertFalse(ChessBlessings.sameSave(group, first, setOf(ChessBlessings.key("Hoàn phif 5 vàng"), art(7))))
        for (name in listOf(
            "Hoàn phí 4 vàng",
            "Hoàn phí 15 vàng",
            "Hoàn phí vàng",
            "Hoàn phiif 5 vàng",
            "Hoàn khi 5 vàng",
            "Hoàn phí 5 bạc"
        )) {
            assertFalse(name, ChessBlessings.sameSave(group, first, setOf(ChessBlessings.key(name), art())))
        }
        assertFalse(ChessBlessings.sameTitle(ChessBlessings.key("Phi vàng"), ChessBlessings.key("Phif vàng")))
        assertFalse(ChessBlessings.sameTitle(ChessBlessings.key("Hoàn phí 5 vàng"), ChessBlessings.key("Hoàn phiff 5 vàng")))
    }

    @Test fun journalMergingNeverChainsOcrAliasesOrChangesNonBlessingBehavior() {
        val group = "chuc-phuc-thuong"
        val a = setOf(ChessBlessings.key("Hoàn phí 5 vàng"), art())
        val b = setOf(ChessBlessings.key("Hoàn phif 5 vàng"), art())
        val c = setOf(ChessBlessings.key("Hoàn phiff 5 vàng"), art())
        assertTrue(ChessBlessings.sameSave(group, a, b))
        val merged = ChessBlessings.mergeSavedNames(group, a, b)
        assertEquals(a, merged)
        assertFalse(ChessBlessings.sameSave(group, merged, c))
        assertEquals(a + b, ChessBlessings.mergeSavedNames("chieu-quan-co", a, b))
        assertEquals(a + b, ChessBlessings.mergeSavedNames(group, setOf("legacy"), a + b) - "legacy")
    }

    @Test fun wholeIconSignatureSurvivesTranslationAndRejectsClippedOrBlankArt() {
        fun signature(y: Double) = ChessBlessings.artwork(1000, 1000, .275, y) { x, yy ->
            val dy = yy - (y * 1000).toInt()
            if (x in 250..300 && dy in -70..70 && (x in 250..260 || dy in -10..10)) {
                0xffffffff.toInt()
            } else {
                0xff16478c.toInt()
            }
        }
        val first = signature(.4)!!
        val scrolled = signature(.7)!!
        assertEquals(first, scrolled)
        val stretched = first.replace(Regex("blessing-art-v1:[0-9]+:"), "blessing-art-v1:900:")
        assertFalse(ChessBlessings.sameArtwork(setOf(first), setOf(stretched)))
        assertTrue(ChessBlessings.sameArtwork(setOf(first), setOf(scrolled)))
        assertNull(ChessBlessings.artwork(1000, 1000, .275, .4) { _, _ -> 0xff16478c.toInt() })
        assertNull(ChessBlessings.artwork(1000, 1000, .275, .4) { _, _ -> 0xffffffff.toInt() })
        assertFalse(ChessBlessings.sameArtwork(setOf("shared-icon-hash"), setOf("shared-icon-hash")))
    }

    @Test fun crossRunOcrDriftNeedsRecordedArtworkAndStillVerifiesOriginalBytes() {
        val dir = Files.createTempDirectory("blessing-ocr-index").toFile()
        try {
            val root = File(dir, "originals").apply { mkdirs() }
            val index = File(dir, BlessingPhotoIndex.FILE)
            val file = File(root, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val group = "chuc-phuc-thuong"
            val key = ChessBlessings.key("Hoàn phí 5 vàng")
            val noisy = ChessBlessings.key("Hoàn phif 5 vàng")
            BlessingPhotoIndex.record(index, root, group, key, file)
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, noisy, setOf(art())))
            BlessingPhotoIndex.record(index, root, group, key, file, setOf(art()))
            assertTrue(BlessingPhotoIndex.hasSaved(index, root, group, noisy, setOf(art())))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, noisy))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, ChessBlessings.key("Hoàn phí 4 vàng"), setOf(art())))
            file.writeBytes(byteArrayOf(3, 2, 1))
            assertFalse(BlessingPhotoIndex.hasSaved(index, root, group, noisy, setOf(art())))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun corruptIndexDoesNotAuthorizeSkippingOrOverwriteItself() {
        val dir = Files.createTempDirectory("blessing-corrupt").toFile()
        try {
            val index = File(dir, BlessingPhotoIndex.FILE).apply { writeText("broken") }
            try {
                BlessingPhotoIndex.hasSaved(index, dir, "chuc-phuc-thuong", ChessBlessings.key("Known title"))
                fail("Corrupt deduplication evidence must be reported")
            } catch (_: org.json.JSONException) {}
            assertEquals("broken", index.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
