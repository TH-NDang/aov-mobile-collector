package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class ChessCaptureFilesTest {
    private val source="a".repeat(64)
    private val another="b".repeat(64)
    private val folder="run-1/quan-co/tat-ca-quan-co/tuong/bolt-baron/"

    @Test fun heroDetailsUseCleanNumberedNamesWithoutChangingRoutingTokens() {
        for(page in 1..12) {
            val token="chess__quan-co__tat-ca-quan-co__tuong__bolt-baron__%03d".format(page)
            assertEquals("bolt-baron-detail-$page",ChessCaptureFiles.screenshotBase(token))
            // CollectorService stamps every routed chess capture with the macro step number.
            assertEquals("bolt-baron-detail-$page",ChessCaptureFiles.screenshotBase("0001-$token"))
            assertEquals("bolt-baron-detail-$page",ChessCaptureFiles.screenshotBase("10000-$token"))
        }
        val longHero="a".repeat(45)
        assertEquals("$longHero-detail-1",ChessCaptureFiles.screenshotBase(
            "chess__quan-co__tat-ca-quan-co__tuong__${longHero}__001"))
    }

    @Test fun otherCapturesAndMalformedTokensAreUntouched() {
        listOf("hero-001-skill-1-detail","chess__quan-co__tat-ca-quan-co__trang__001",
            "chess__quan-co__tat-ca-quan-co__hang__001",
            "chess__tuong-tac__phe__phe__ma-toc__001",
            "chess__quan-co__tat-ca-quan-co__tuong__../violet__001",
            "chess__quan-co__tat-ca-quan-co__tuong__violet__000",
            "oops-chess__quan-co__tat-ca-quan-co__tuong__violet__001",
            "0001-chess__quan-co__tat-ca-quan-co__trang__001",
            "chess__quan-co__tat-ca-quan-co__tuong__violet__999999999999",
            "chess__quan-co__tat-ca-quan-co__tuong__violet__001__extra").forEach {
            assertEquals(it,ChessCaptureFiles.screenshotBase(it))
        }
    }

    @Test fun onlyHeroPortraitMarkersGetCleanNativeNames() {
        assertEquals("violet-head",ChessCaptureFiles.nativeHeadBase("quan-co","violet-head"))
        assertNull(ChessCaptureFiles.nativeHeadBase("chieu-quan-co","violet-head"))
        assertNull(ChessCaptureFiles.nativeHeadBase("quan-co","violet-skill-1"))
        assertNull(ChessCaptureFiles.nativeHeadBase("quan-co","../violet-head"))
        assertNull(ChessCaptureFiles.nativeHeadBase("quan-co","Violet Head"))
        val longHero="a".repeat(45)+"-head"
        assertEquals(longHero,ChessCaptureFiles.nativeHeadBase("quan-co",longHero))
    }

    @Test fun firstNativePortraitUsesExactRequestedBasename() {
        assertEquals("violet-head.jpg",ChessCaptureFiles.nativeHeadTarget(
            "violet-head","jpg",source,emptySet(),emptyMap()))
    }

    @Test fun aKnownSourceResumesItsClaimWithOrWithoutACompletedFile() {
        val claim=mapOf("violet-head.png" to source)
        for(existing in listOf(emptySet(),setOf("violet-head.png")))
            assertEquals("violet-head.png",ChessCaptureFiles.nativeHeadTarget(
                "violet-head","png",source,existing,claim))
    }

    @Test fun anUnknownExistingImageNeverCountsAsTheSameSource() {
        val expected="violet-head-${source.take(14)}.png"
        assertEquals(expected,ChessCaptureFiles.nativeHeadTarget("violet-head","png",source,
            setOf("violet-head.png"),emptyMap()))
        // The original unclaimed conflict can disappear; restart still uses the persisted claim.
        assertEquals(expected,ChessCaptureFiles.nativeHeadTarget("violet-head","png",source,
            emptySet(),mapOf(expected to source)))
    }

    @Test fun anUnknownPartialFileAlsoKeepsItsNameUntilItsSourceIsProven() {
        assertEquals("violet-head-${source.take(14)}.png",ChessCaptureFiles.nativeHeadTarget(
            "violet-head","png",source,setOf("violet-head.png.part"),emptyMap()))
        assertEquals("violet-head.png",ChessCaptureFiles.nativeHeadTarget(
            "violet-head","png",source,setOf("violet-head.png.part"),mapOf("violet-head.png" to source)))
    }

    @Test fun aDifferentSourceCannotStealAnUnfinishedClaim() {
        assertEquals("violet-head-${source.take(14)}.webp",ChessCaptureFiles.nativeHeadTarget(
            "violet-head","webp",source,emptySet(),mapOf("violet-head.webp" to another)))
    }

    @Test fun disambiguationAlsoHandlesMatchingShortHashPrefixesAndManyCollisions() {
        val shortCollision=source.take(14)+"c".repeat(50)
        val stem="violet-head-${source.take(14)}"
        val claims=mutableMapOf("violet-head.png" to another,"$stem.png" to shortCollision)
        for(i in 2..11) claims["$stem-$i.png"]=another
        val selected=ChessCaptureFiles.nativeHeadTarget("violet-head","png",source,emptySet(),claims)
        assertEquals("$stem-12.png",selected)
        claims[selected]=source
        assertEquals(selected,ChessCaptureFiles.nativeHeadTarget("violet-head","png",source,emptySet(),claims))
    }

    @Test fun historyUsesExactDetailAndHeadBasenamesWithoutAnotherSequenceNumber() {
        assertEquals("bolt-baron-detail-2",ChessCaptureFiles.exactDisplayName(folder+"bolt-baron-detail-2.png"))
        assertEquals(ChessCaptureFiles.Detail("bolt-baron",2),ChessCaptureFiles.detail(folder+"bolt-baron-detail-2.png"))
        val native="anh-goc-co-lien-quan/quan-co/anh-quan-co/run-1/"
        assertEquals("bolt-baron-head",ChessCaptureFiles.exactDisplayName(native+"bolt-baron-head.jpg"))
        val duplicate="bolt-baron-head-${source.take(14)}-12"
        assertEquals(duplicate,ChessCaptureFiles.exactDisplayName(native+"$duplicate.jpg"))
    }

    @Test fun genericDetailFilesCannotPretendToBeChessHeroDetails() {
        listOf("run-1/bolt-baron-detail-2.png",folder+"violet-detail-2.png",
            folder+"bolt-baron-detail-0.png",folder+"bolt-baron-detail-01.png",
            folder+"bolt-baron-detail-2.jpg",folder+"../bolt-baron-detail-2.png").forEach {
            assertNull(it,ChessCaptureFiles.detail(it))
            assertNull(it,ChessCaptureFiles.exactDisplayName(it))
        }
        assertNull(ChessCaptureFiles.exactDisplayName(
            "anh-goc-co-lien-quan/quan-co/chieu-quan-co/run-1/violet-head.png"))
    }
}
