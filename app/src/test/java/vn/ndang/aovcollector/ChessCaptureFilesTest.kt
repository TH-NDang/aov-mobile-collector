package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class ChessCaptureFilesTest {
    private val source = "a".repeat(64)
    private val another = "b".repeat(64)
    private val folder = "run-1/quan-co/tat-ca-quan-co/tuong/bolt-baron/"

    @Test fun heroDetailsUseCleanNumberedNamesWithoutChangingRoutingTokens() {
        for (page in 1..12) {
            val token = "chess__quan-co__tat-ca-quan-co__tuong__bolt-baron__%03d".format(page)
            assertEquals("bolt-baron-detail-$page", ChessCaptureFiles.screenshotBase(token))
            // CollectorService stamps every routed chess capture with the macro step number.
            assertEquals("bolt-baron-detail-$page", ChessCaptureFiles.screenshotBase("0001-$token"))
            assertEquals("bolt-baron-detail-$page", ChessCaptureFiles.screenshotBase("10000-$token"))
        }
        val longHero = "a".repeat(45)
        assertEquals(
            "$longHero-detail-1",
            ChessCaptureFiles.screenshotBase(
                "chess__quan-co__tat-ca-quan-co__tuong__${longHero}__001"
            )
        )
    }

    @Test fun otherCapturesAndMalformedTokensAreUntouched() {
        listOf(
            "hero-001-skill-1-detail",

            "chess__quan-co__tat-ca-quan-co__tuong__../violet__001",
            "chess__quan-co__tat-ca-quan-co__tuong__violet__000",
            "oops-chess__quan-co__tat-ca-quan-co__tuong__violet__001",
            "chess__quan-co__tat-ca-quan-co__tuong__violet__999999999999",
            "chess__quan-co__tat-ca-quan-co__tuong__violet__001__extra"
        ).forEach {
            assertEquals(it, ChessCaptureFiles.screenshotBase(it))
        }
    }

    @Test fun onlyHeroPortraitMarkersGetCleanNativeNames() {
        assertEquals("violet-head", ChessCaptureFiles.nativeHeadBase("quan-co", "violet-head"))
        assertNull(ChessCaptureFiles.nativeHeadBase("chieu-quan-co", "violet-head"))
        assertNull(ChessCaptureFiles.nativeHeadBase("quan-co", "violet-skill-1"))
        assertNull(ChessCaptureFiles.nativeHeadBase("quan-co", "../violet-head"))
        assertNull(ChessCaptureFiles.nativeHeadBase("quan-co", "Violet Head"))
        val longHero = "a".repeat(45) + "-head"
        assertEquals(longHero, ChessCaptureFiles.nativeHeadBase("quan-co", longHero))
    }

    @Test fun firstNativePortraitUsesExactRequestedBasename() {
        assertEquals(
            "violet-head.jpg",
            ChessCaptureFiles.nativeHeadTarget(
                "violet-head",
                "jpg",
                source,
                emptySet(),
                emptyMap()
            )
        )
    }

    @Test fun aKnownSourceResumesItsClaimWithOrWithoutACompletedFile() {
        val claim = mapOf("violet-head.png" to source)
        for (existing in listOf(emptySet(), setOf("violet-head.png"))) {
            assertEquals(
                "violet-head.png",
                ChessCaptureFiles.nativeHeadTarget(
                    "violet-head",
                    "png",
                    source,
                    existing,
                    claim
                )
            )
        }
    }

    @Test fun anUnknownExistingImageNeverCountsAsTheSameSource() {
        val expected = "violet-head-${source.take(14)}.png"
        assertEquals(
            expected,
            ChessCaptureFiles.nativeHeadTarget(
                "violet-head",
                "png",
                source,
                setOf("violet-head.png"),
                emptyMap()
            )
        )
        // The original unclaimed conflict can disappear; restart still uses the persisted claim.
        assertEquals(
            expected,
            ChessCaptureFiles.nativeHeadTarget(
                "violet-head",
                "png",
                source,
                emptySet(),
                mapOf(expected to source)
            )
        )
    }

    @Test fun anUnknownPartialFileAlsoKeepsItsNameUntilItsSourceIsProven() {
        assertEquals(
            "violet-head-${source.take(14)}.png",
            ChessCaptureFiles.nativeHeadTarget(
                "violet-head",
                "png",
                source,
                setOf("violet-head.png.part"),
                emptyMap()
            )
        )
        assertEquals(
            "violet-head.png",
            ChessCaptureFiles.nativeHeadTarget(
                "violet-head",
                "png",
                source,
                setOf("violet-head.png.part"),
                mapOf("violet-head.png" to source)
            )
        )
    }

    @Test fun aDifferentSourceCannotStealAnUnfinishedClaim() {
        assertEquals(
            "violet-head-${source.take(14)}.webp",
            ChessCaptureFiles.nativeHeadTarget(
                "violet-head",
                "webp",
                source,
                emptySet(),
                mapOf("violet-head.webp" to another)
            )
        )
    }

    @Test fun disambiguationAlsoHandlesMatchingShortHashPrefixesAndManyCollisions() {
        val shortCollision = source.take(14) + "c".repeat(50)
        val stem = "violet-head-${source.take(14)}"
        val claims = mutableMapOf("violet-head.png" to another, "$stem.png" to shortCollision)
        for (i in 2..11) claims["$stem-$i.png"] = another
        val selected = ChessCaptureFiles.nativeHeadTarget("violet-head", "png", source, emptySet(), claims)
        assertEquals("$stem-12.png", selected)
        claims[selected] = source
        assertEquals(selected, ChessCaptureFiles.nativeHeadTarget("violet-head", "png", source, emptySet(), claims))
    }

    @Test fun historyUsesExactDetailAndHeadBasenamesWithoutAnotherSequenceNumber() {
        assertEquals("bolt-baron-detail-2", ChessCaptureFiles.exactDisplayName(folder + "bolt-baron-detail-2.png"))
        assertEquals(ChessCaptureFiles.Detail("bolt-baron", 2), ChessCaptureFiles.detail(folder + "bolt-baron-detail-2.png"))
        val native = "anh-goc-co-lien-quan/quan-co/anh-quan-co/run-1/"
        assertEquals("bolt-baron-head", ChessCaptureFiles.exactDisplayName(native + "bolt-baron-head.jpg"))
        val duplicate = "bolt-baron-head-${source.take(14)}-12"
        assertEquals(duplicate, ChessCaptureFiles.exactDisplayName(native + "$duplicate.jpg"))
    }

    @Test fun genericDetailFilesCannotPretendToBeChessHeroDetails() {
        listOf(
            "run-1/bolt-baron-detail-2.png",
            folder + "violet-detail-2.png",
            folder + "bolt-baron-detail-0.png",
            folder + "bolt-baron-detail-01.png",
            folder + "bolt-baron-detail-2.jpg",
            folder + "../bolt-baron-detail-2.png"
        ).forEach {
            assertNull(it, ChessCaptureFiles.detail(it))
            assertNull(it, ChessCaptureFiles.exactDisplayName(it))
        }
        assertNull(
            ChessCaptureFiles.exactDisplayName(
                "anh-goc-co-lien-quan/quan-co/chieu-quan-co/run-1/violet-head.png"
            )
        )
    }

    @Test fun nativeSkillNamesUseSuccessfulEventOrderAndNoPixelHash() {
        assertEquals("arduin-skill-1", ChessCaptureFiles.nativeBase("chieu-quan-co", "arduin-skill-30acada4409079", 1))
        assertEquals("arduin-skill-2", ChessCaptureFiles.nativeBase("chieu-quan-co", "arduin-skill-e0254dd220873a", 2))
        assertEquals("trang-bi-icon-3", ChessCaptureFiles.nativeBase("trang-bi", "0e2b88dec4851b", 3))
        assertEquals("giay-phut-huy-hoang", ChessCaptureFiles.nativeBase("chuc-phuc-thuong", "Giây phút huy hoàng", 1))
        assertEquals("arduin-skill-1.jpg", ChessCaptureFiles.nativeTarget("arduin-skill-1", "jpg", source, emptySet(), emptyMap()))
        val path = "anh-goc-co-lien-quan/quan-co/chieu-quan-co/run-1/arduin-skill-1.jpg"
        assertEquals("arduin-skill-1", ChessCaptureFiles.exactDisplayName(path))
    }

    @Test fun equipmentAndSynergyDetailsHaveReadableFilenames() {
        assertEquals(
            "lenh-bai-phong-than-page-1",
            ChessCaptureFiles.screenshotBase(
                "0002-chess__trang-bi__tat-ca-trang-bi__vat-pham__lenh-bai-phong-than-575f2fce__001"
            )
        )
        assertEquals("ma-toc-page-2", ChessCaptureFiles.screenshotBase("chess__tuong-tac__phe__phe__ma-toc__002"))
    }

    @Test fun allNonHeroPagesShareOneTopicFolderAndKeepPageNumbers() {
        for ((section, topic, kind, name) in listOf(
            listOf("trang-bi", "tat-ca-trang-bi", "vat-pham", "lenh-bai-phong-than"),
            listOf("tuong-tac", "phe", "phe", "cung-thu"),
            listOf("tuong-tac", "nghe", "nghe", "do-don")
        )) {
            for (page in 1..3) {
                val token = "0002-chess__${section}__${topic}__${kind}__${name}__%03d".format(page)
                assertEquals(listOf(section, topic), ChessCaptureFiles.directoryParts(token))
                val filename = ChessCaptureFiles.screenshotBase(token) + ".png"
                assertEquals("$name-page-$page.png", filename)
                assertEquals("$name-page-$page", ChessCaptureFiles.exactDisplayName("run-1/$section/$topic/$filename"))
            }
        }
        assertEquals(
            listOf("quan-co", "tat-ca-quan-co", "tuong", "violet"),
            ChessCaptureFiles.directoryParts(
                "chess__quan-co__tat-ca-quan-co__tuong__violet__001"
            )
        )
        assertEquals("violet-detail-1", ChessCaptureFiles.screenshotBase("chess__quan-co__tat-ca-quan-co__tuong__violet__001"))
        assertNull(ChessCaptureFiles.directoryParts("chess__../evil__tat-ca-trang-bi__vat-pham__name__001"))
    }

    @Test fun everyChessTabUsesCleanPageNamesAndExactlyItsTopicFolder() {
        ChessWiki.TOPICS.forEach { topic ->
            for (page in 1..2) {
                val token = "0002-" + ChessWiki.filename(topic, "trang", page)
                assertEquals(topic.key + "-page-$page", ChessCaptureFiles.screenshotBase(token))
                assertEquals(listOf(topic.section, topic.key), ChessCaptureFiles.directoryParts(token))
                val path = "run-1/${topic.section}/${topic.key}/${topic.key}-page-$page.png"
                assertEquals(topic.key + "-page-$page", ChessCaptureFiles.exactDisplayName(path))
                assertFalse(ChessCaptureFiles.isDetailCapture(token))
            }
        }
        val hero = ChessWiki.heroOverviewFilename(ChessWiki.TOPICS.first { it.key == "tat-ca-quan-co" }, 2)
        assertEquals("tat-ca-quan-co-page-2", ChessCaptureFiles.screenshotBase(hero))
        assertEquals(listOf("quan-co", "tat-ca-quan-co"), ChessCaptureFiles.directoryParts(hero))
        assertTrue(ChessCaptureFiles.isDetailCapture("chess__tuong-tac__phe__phe__cung-thu__001"))
    }

    @Test fun runLocalNativeImagesKeepExactNamesInEveryGroup() {
        val names = mapOf(
            "quan-co" to "violet-head",
            "chieu-quan-co" to "violet-skill-2",
            "phe" to "ma-toc",
            "nghe" to "cung-thu",
            "trang-bi" to "trang-bi-icon-3",
            "chuc-phuc-thuong" to "giay-phut-huy-hoang",
            "chuc-phuc-huyen-thoai-su-thi" to "nac-thang-vinh-dieu",
            "chuc-phuc-truyen-thuyet" to "chuc-phuc-icon-1"
        )
        names.forEach { (group, base) ->
            for (extension in listOf("png", "jpg", "jpeg", "webp")) {
                val relative = GamePhotoImport.groupPath(group) + "/$base.$extension"
                val path = "chess-run-1/anh-goc/$relative"
                assertEquals(relative, ChessCaptureFiles.nativeRelativePath(path))
                assertEquals(group, ChessCaptureFiles.nativeGroup(path))
                assertEquals(base, ChessCaptureFiles.exactDisplayName(path))
            }
        }
        val collision = "violet-skill-2-${source.take(14)}-12"
        assertEquals(
            collision,
            ChessCaptureFiles.exactDisplayName(
                "chess-run-1/anh-goc/quan-co/chieu-quan-co/$collision.png"
            )
        )
    }

    @Test fun runLocalNativeFoldersHaveLabelsWithoutInventingAnotherRun() {
        assertEquals("Ảnh gốc", ChessCaptureFiles.nativeFolderTitle("run-1/anh-goc"))
        assertEquals("Quân cờ", ChessCaptureFiles.nativeFolderTitle("run-1/anh-goc/quan-co"))
        assertEquals("Tất cả quân cờ", ChessCaptureFiles.nativeFolderTitle("run-1/anh-goc/quan-co/anh-quan-co"))
        assertEquals("Chiêu quân cờ", ChessCaptureFiles.nativeFolderTitle("run-1/anh-goc/quan-co/chieu-quan-co"))
        assertEquals("Thường", ChessCaptureFiles.nativeFolderTitle("run-1/anh-goc/chuc-phuc/thuong"))
        assertNull(ChessCaptureFiles.nativeFolderTitle("run-1/anh-goc/tuong-tac/phe/muc-khac"))
        assertNull(ChessCaptureFiles.nativeFolderTitle("run-1/tuong-tac/phe/muc-khac"))
        assertEquals("Ảnh gốc Cờ Liên Quân · Pictures", ChessCaptureFiles.nativeFolderTitle("anh-goc-co-lien-quan"))
        assertEquals(
            "Lượt run 1",
            ChessCaptureFiles.nativeFolderTitle(
                "anh-goc-co-lien-quan/tuong-tac/phe/run-1"
            )
        )
    }

    @Test fun nativePathsDoNotAcceptScreenshotFoldersOrTraversal() {
        listOf(
            "run-1/quan-co/anh-quan-co/violet-head.jpg",
            "run-1/anh-goc/quan-co/anh-quan-co/run-2/violet-head.jpg",
            "run-1/anh-goc/quan-co/anh-quan-co/../violet-head.jpg",
            "run-1//anh-goc/quan-co/anh-quan-co/violet-head.jpg",
            "run-1/anh-goc/unknown/violet-head.jpg",
            "run-1/anh-goc/quan-co/anh-quan-co/violet-head.jpg.part"
        ).forEach {
            assertNull(it, ChessCaptureFiles.nativeGroup(it))
            assertNull(it, ChessCaptureFiles.exactDisplayName(it))
        }
        assertNull(ChessCaptureFiles.nativeRelativePath("run-1/quan-co/tat-ca-quan-co"))
        assertNull(ChessCaptureFiles.nativeRelativePath("../anh-goc/quan-co"))
    }
}
