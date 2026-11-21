package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class NativeSaveEvidenceTest {
    @Test fun aVerifiedOwningDetailCanEstablishNoPrompt() {
        assertEquals(
            NativeSaveEvidence.Scene.DETAIL,
            NativeSaveEvidence.scene("quillenchieuthuctuoi", true, true)
        )
    }

    @Test fun aRecognizedEncyclopediaPageCanEstablishNoPrompt() {
        assertEquals(
            NativeSaveEvidence.Scene.PAGE,
            NativeSaveEvidence.scene("bachkhoacolienquantrangbi", false, false)
        )
        assertEquals(
            NativeSaveEvidence.Scene.PAGE,
            NativeSaveEvidence.scene("bachkhoatatcaquanco", false, false)
        )
    }

    @Test fun partialSavePromptAlwaysOverridesVisibleBackgroundAndDetail() {
        for (fragment in listOf("savephoto", "save", "photo", "sure", "cancel")) {
            assertEquals(
                fragment,
                NativeSaveEvidence.Scene.PROMPT,
                NativeSaveEvidence.scene("bachkhoacolienquanquillen" + fragment, true, true)
            )
            assertEquals(
                fragment,
                NativeSaveEvidence.Scene.PROMPT,
                NativeSaveEvidence.scene("bachkhoatatcaquanco" + fragment, false, false)
            )
        }
    }

    @Test fun emptyOcrUnknownScreensAndUnverifiedModalsAreNotAbsence() {
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN, NativeSaveEvidence.scene("", true, true))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN, NativeSaveEvidence.scene("colienquan", false, false))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN, NativeSaveEvidence.scene("quillen", true, false))
        assertEquals(
            NativeSaveEvidence.Scene.UNKNOWN,
            NativeSaveEvidence.scene("bachkhoacolienquan", true, false)
        )
    }

    @Test fun unchangedCurrentDrawerCanSurviveMissingTitleButNotContradiction() {
        val before = IntArray(2560) { (it * 13) % 240 }
        assertTrue(NativeSaveEvidence.preservedDetail("lumburr", null, before, before.copyOf()))
        assertFalse(NativeSaveEvidence.preservedDetail("lumburr", "Raz", before, before.copyOf()))
        assertTrue(NativeSaveEvidence.preservedDetail("lumburr", "Lumburr", before, before.copyOf()))
    }

    @Test fun aCutDescriptionLineInTheHeadingStripDoesNotContradictTheHero() {
        val before = IntArray(2560) { (it * 13) % 240 }
        val moved = IntArray(2560) { (it * 31) % 240 }
        // A scrolled Lumburr drawer: its skill text, not another hero's name, sits where the title was.
        assertTrue(NativeSaveEvidence.preservedDetail("lumburr", "địch trúng chiêu sẽ chịu", before, before.copyOf()))
        assertFalse(NativeSaveEvidence.preservedDetail("lumburr", "địch trúng chiêu sẽ chịu", before, moved))
        assertFalse(NativeSaveEvidence.preservedDetail("hayate", "Tel’Annas", before, before.copyOf()))
        assertTrue(NativeSaveEvidence.headingLike("Wonder Woman"))
        assertFalse(NativeSaveEvidence.headingLike("Hayate liên tục phóng"))
    }

    @Test fun aLongNameCutByOcrStillMatchesItsOwnHeroOnly() {
        val before = IntArray(2560) { (it * 13) % 240 }
        val moved = IntArray(2560) { (it * 31) % 240 }
        // The user's run: "Florentin" read twice on Florentino's drawer, which then never counted as verified.
        assertTrue(NativeSaveEvidence.preservedDetail("florentino", "Florentin", before, moved))
        assertTrue(ChessWiki.sameHeroTitle("Wonder Wom", "wonder-woman"))
        assertTrue(ChessWiki.sameHeroTitle("Lumburr", "lumburr"))
        // Too short, too much cut, longer than the name, or another hero: still a contradiction.
        assertFalse(NativeSaveEvidence.preservedDetail("florentino", "Floren", before, before.copyOf()))
        assertFalse(ChessWiki.sameHeroTitle("Omen", "omega"))
        assertFalse(ChessWiki.sameHeroTitle("Ra", "raz"))
        assertFalse(ChessWiki.sameHeroTitle("Florentinos", "florentino"))
        assertFalse(ChessWiki.sameHeroTitle("Raz", "lumburr"))
        val words = listOf(
            GameScreens.Word("Hồi kiếm", .40, .30, .48, .33),
            GameScreens.Word("Florentin", .42, .23, .56, .26)
        )
        assertEquals("Florentin", ChessWiki.modalHeroTitle(words, "florentino"))
    }

    @Test fun changedDrawerMissingReferenceAndSparseEvidenceNeverAuthorizeContinuation() {
        val before = IntArray(2560) { (it * 13) % 240 }
        val moved = IntArray(2560) { (it * 31) % 240 }
        assertFalse(NativeSaveEvidence.preservedDetail("lumburr", null, before, moved))
        assertFalse(NativeSaveEvidence.preservedDetail("lumburr", null, null, before))
        assertFalse(NativeSaveEvidence.preservedDetail("lumburr", null, intArrayOf(1), intArrayOf(1)))
        assertFalse(NativeSaveEvidence.preservedDetail("lumburr", null, before, IntArray(1000)))
    }

    @Test fun visualFallbackDoesNotOverrideSavePromptOrUnknownOcr() {
        val before = IntArray(2560) { (it * 13) % 240 }
        val verified = NativeSaveEvidence.preservedDetail("lumburr", null, before, before.copyOf())
        assertEquals(NativeSaveEvidence.Scene.PROMPT, NativeSaveEvidence.scene("savephoto", true, verified))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN, NativeSaveEvidence.scene("", true, verified))
        assertEquals(NativeSaveEvidence.Scene.UNKNOWN, NativeSaveEvidence.scene("unknown", false, verified))
    }
}
