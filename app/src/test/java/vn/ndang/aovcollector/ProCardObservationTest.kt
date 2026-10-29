package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProCardObservationTest {
    private fun word(text: String, left: Double, top: Double, right: Double, bottom: Double) =
        GameScreens.Word(text, left, top, right, bottom)

    private val popup = word("Hệ thống đề xuất", .15, .09, .30, .13)
    private val tab = word("Đề cử Pro", .17, .44, .25, .47)
    private val button = word("Dùng combo", .70, .396, .81, .420)
    private val rune = word("90", .625, .451, .645, .475)
    private val creator = word("NgườiĐềCử", .30, .211, .49, .235)

    private fun entries(words: List<GameScreens.Word>) = ProComboScreens.entries(2048, 922, words) { _, row ->
        row.fill(0xff303080.toInt())
    }

    @Test fun completeCreatorHeaderMayStartBeforeOldPoint325Boundary() {
        for (left in listOf(.300, .310, .324)) {
            val header = word(creator.text, left, creator.top, creator.right, creator.bottom)
            val observed = entries(listOf(popup, tab, header, button, rune))
            assertEquals("Header left=$left", 1, observed.size)
            assertEquals(creator.plain, observed.single().id)
            assertEquals(90, observed.single().signature[0])
        }
    }

    @Test fun buttonAloneOrIncompleteCardNeverBecomesRankedEntry() {
        assertTrue(entries(listOf(popup, tab, button)).isEmpty())
        assertTrue(entries(listOf(popup, tab, creator, button)).isEmpty())
        assertTrue(entries(listOf(popup, tab, rune, button)).isEmpty())
        val clippedCreator = word(creator.text, .30, .16, .49, .184)
        val clippedButton = word(button.text, .70, .345, .81, .369)
        val clippedRune = word(rune.text, .625, .400, .645, .424)
        assertTrue(entries(listOf(popup, tab, clippedCreator, clippedButton, clippedRune)).isEmpty())
        val unrelatedSideLabel = word("Menu", .17, creator.top, .25, creator.bottom)
        assertTrue(entries(listOf(popup, tab, unrelatedSideLabel, button, rune)).isEmpty())
    }

    @Test fun focusedListReplacesConflictingLowResolutionNumbersButKeepsNavigation() {
        val wrongRune = word("80", rune.left, rune.top, rune.right, rune.bottom)
        val ordinary = listOf(popup, tab, creator, button, wrongRune)
        val focused = listOf(creator, button, rune)
        val combined = ProComboScreens.withFocusedList(ordinary, focused)
        assertTrue(combined.contains(popup))
        assertTrue(combined.contains(tab))
        assertFalse(combined.contains(wrongRune))
        assertTrue(ProComboScreens.popup(combined))
        assertEquals(90, entries(combined).single().signature[0])
    }

    @Test fun unsuccessfulFocusedReadDoesNotEraseOrdinaryCardEvidence() {
        val ordinary = listOf(popup, tab, creator, button, rune)
        val combined = ProComboScreens.withFocusedList(ordinary, emptyList())
        assertEquals(ordinary, combined)
        assertEquals(1, entries(combined).size)
    }
}
