package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingControlSourceTest {
    private fun word(text: String, left: Double, top: Double, right: Double, bottom: Double) =
        GameScreens.Word(text, left, top, right, bottom)

    private val misplaced = word("Đấu hạng (Tất cả)", .32, .12, .40, .14)
    private val visible = word("Đấu hạng (Tất cả)", .48, .18, .62, .22)

    @Test fun focusedControlCannotBeReplacedByASmallerMisplacedNode() {
        val selected = RankScreens.modeButton(RankScreens.withFocusedModes(listOf(misplaced, visible), listOf(visible)))!!
        assertEquals(.55, selected.x, .0001)
        assertEquals(.20, selected.y, .0001)
    }

    @Test fun unreadableCropDoesNotFallBackToAnUnverifiedNode() {
        assertNull(RankScreens.modeButton(RankScreens.withFocusedModes(listOf(misplaced, visible), emptyList())))
    }

    @Test fun staleOptionsCannotInventAnOpenMenu() {
        val stale = word("Đấu hạng (Tinh Anh trở lên)", .50, .42, .70, .46)
        val words = RankScreens.withFocusedModes(listOf(misplaced, stale), listOf(visible))
        assertTrue(RankScreens.menuModes(words, RankScreens.modeButton(words)!!).isEmpty())
    }

    @Test fun focusedMenuRowsAndTableHeadingsRetainOrdinaryNavigation() {
        val heading = word("Tỷ lệ pick", .52, .30, .60, .34)
        val option = word("Đấu hạng (Tinh Anh trở lên)", .50, .42, .70, .46)
        val switch = word("Đổi bảng xếp hạng", .80, .18, .90, .22)
        val words = RankScreens.withFocusedModes(listOf(misplaced, switch), listOf(visible, option, heading))
        val button = RankScreens.modeButton(words)!!
        assertEquals(RankScreens.Table.RATES, RankScreens.table(words))
        assertEquals(switch, RankScreens.switchButton(words))
        assertEquals(.60, RankScreens.option(words, button, RankScreens.Mode.ELITE)!!.x, .0001)
    }

    @Test fun fragmentedUnverifiedModeTextCannotJoinTheFocusedLabel() {
        val fragments = listOf(word("Đấu", .32, .12, .34, .14), word("hạng (Tất cả)", .345, .12, .40, .14))
        val words = RankScreens.withFocusedModes(fragments, listOf(visible))
        assertEquals(.55, RankScreens.modeButton(words)!!.x, .0001)
    }

    @Test fun partialCropCannotReconstructAControlFromUnverifiedFragments() {
        val ordinary = listOf(word("hạng Tất", .515, .18, .59, .22), word("cả", .595, .18, .62, .22))
        val focused = listOf(word("Đấu", .48, .18, .51, .22))
        assertNull(RankScreens.modeButton(RankScreens.withFocusedModes(ordinary, focused)))
    }

    @Test fun bothTablesRemainRecognizableFromTheirFocusedHeadings() {
        val title = word("Bảng Phân Bậc Tướng", .10, .05, .40, .10)
        val switch = word("Đổi bảng xếp hạng", .80, .18, .90, .22)
        val headings = listOf(
            RankScreens.Table.RATES to word("Tỷ lệ pick", .52, .30, .60, .34),
            RankScreens.Table.FIGURES to word("Sát thương", .46, .28, .58, .32)
        )
        for ((table, heading) in headings) {
            val words = RankScreens.withFocusedModes(listOf(misplaced, title, switch), listOf(visible, heading))
            assertEquals(table, RankScreens.table(words))
            assertEquals(RankScreens.Page.RANKING, RankScreens.page(words, false))
            assertTrue(title in words)
            assertEquals(switch, RankScreens.switchButton(words))
        }
    }
}
