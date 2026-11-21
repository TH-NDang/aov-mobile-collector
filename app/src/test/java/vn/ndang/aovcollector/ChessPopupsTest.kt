package vn.ndang.aovcollector

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Popups over the chess lobby, on the way to the encyclopedia. */
class ChessPopupsTest {
    /** [words] read on it, the X the pixels report, and the one place a tap closes it (null: nowhere). */
    private class Popup(val words: List<GameScreens.Word>, val pixelX: Pair<Double, Double>?, val leavesAt: Pair<Double, Double>?)

    /** The chess lobby under [popups], top one first: until they are gone, a tap anywhere else does nothing. */
    private class Lobby(popups: List<Popup>, private val lobbyX: Pair<Double, Double>? = null, private val homeWorks: Boolean = true) :
        ChessWikiReader.Screen {
        private val shown = popups.toMutableList()
        private val clock = ChessTestClockQueue()
        private val completed = HashSet<String>()
        private var wiki = false
        val taps = ArrayList<Pair<Double, Double>>()
        val behind = ArrayList<Pair<Double, Double>>()
        var outcome: ChessWikiReader.Outcome? = null
        override val alive get() = outcome == null
        override val monotonicMs get() = clock.now

        private fun near(a: Pair<Double, Double>, b: Pair<Double, Double>) = abs(a.first - b.first) < .02 && abs(a.second - b.second) < .03

        override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
            val top = shown.firstOrNull()
            then(object : ChessWikiReader.Look {
                override val words = when {
                    wiki -> listOf(word("Bách khoa", .15, .06), word("Cờ Liên Quân", .35, .06))
                    else -> LOBBY_WORDS + top?.words.orEmpty()
                }
                override val closeButton = if (top != null) top.pixelX else lobbyX
                override val signature = IntArray(96) { 60 }
                override val modal = false

                override fun tiles(topic: ChessWiki.Topic) = emptyList<ChessWiki.Tile>()

                override fun keep(name: String, then: (Boolean) -> Unit) = then(true)

                override fun discard() {}
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            val at = x to y
            taps += at
            val top = shown.firstOrNull()
            when {
                top == null -> if (homeWorks && near(at, HOME)) wiki = true
                top.leavesAt != null && near(at, top.leavesAt) -> shown.removeAt(0)
                else -> behind += at
            }
            then(true)
        }

        override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

        override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

        override fun seen(id: String) = id in completed

        override fun finished(id: String) {
            completed.add(id)
        }

        override fun note(message: String) {}

        override fun say(message: String) {}

        fun run(): Lobby {
            ChessWikiReader(this, ChessWiki.Options(setOf("quy-trinh"), openGame = false), { outcome = it }).start()
            clock.drain()
            return this
        }
    }

    private companion object {
        fun word(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .06, y - .02, x + .06, y + .02)

        // The user's screenshot as ML Kit read it on an emulator, misreadings included: the dimmed lobby around the popup...
        val HOME = .10 to .36
        val LOBBY_WORDS = listOf(
            word("Shor", .10, .23),
            word("Trang chu", HOME.first, HOME.second),
            word("Linh thủ", .10, .48),
            word("Nhật ky", .10, .60),
            word("Chat", .10, .92),
            word("Cờ Liên quân", .21, .06),
            word("2ID phòng", .62, .04),
            word("74/100", .77, .69),
            word("Huản luyên", .89, .19),
            word("doanh", .87, .22),
            word("Đấu thường", .87, .49),
            word("Đẫu hang", .87, .59),
            word("Mùa hang mới", .87, .65),
            word("Lập phòng", .87, .74)
        )

        // ...and the popup itself. Its X was found at (.7185, .1856) in the pixels.
        val NOTES_X = .719 to .186
        val NOTES_WORDS = listOf(
            word("Ghi chú cập nhật", .34, .186),
            word("1,Cơ chế giai doạn hoàn toàn mới: Số hiệp sẽ dược tính riêng theo từng giai", .49, .27),
            word("đoạn. Mỗi khi bước vào giai doạn mới, số hiệp sẽ được reset, Nhip độ giao", .49, .34),
            word("tranh sẽ rõ ràng hơn, chiến thuất sắc nét hơn!", .40, .40),
            word("X", .72, .19),
            word("2. Hệ thống chúc phúc mới: Thêm khoảng 60 hiệu ứng chúc phúc ngầu nhiên", .49, .54),
            word("hoàn toàn mớil Trong mỗi ván, bạn có thế lựa chọn 3 loại chúc phúc khác nhau", .50, .60),
            word("dế kết hơp, mang dến trải nghiêm mới mẻi", .40, .67),
            word("3. Ra måt 2 tướng 6 vàng mới: Thánh Quang Đạo Sư Edras và Chúa Té tin tiết", .50, .80)
        )
    }

    private fun Lobby.assertReachedTheEncyclopedia() {
        assertTrue(outcome.toString(), outcome is ChessWikiReader.Outcome.Done)
        assertTrue("Tapped behind a popup: $behind", behind.isEmpty())
    }

    @Test fun theUsersUpdateNotesAreClosedByTheirXBeforeTheLobbyIsTouched() {
        val game = Lobby(listOf(Popup(NOTES_WORDS, NOTES_X, NOTES_X))).run()
        game.assertReachedTheEncyclopedia()
        assertEquals(NOTES_X, game.taps[0])
        assertEquals(HOME, game.taps[1])
    }

    @Test fun theNotesCloseAtTheMeasuredPlaceWhenThePixelsShowNoX() {
        val game = Lobby(listOf(Popup(NOTES_WORDS, null, NOTES_X))).run()
        game.assertReachedTheEncyclopedia()
        assertEquals(NOTES_X, game.taps[0])
    }

    @Test fun anIconOfTheDimmedTopBarIsNotTakenForTheNotesX() {
        val game = Lobby(listOf(Popup(NOTES_WORDS, .9075 to .042, NOTES_X))).run()
        game.assertReachedTheEncyclopedia()
        assertEquals(NOTES_X, game.taps[0])
    }

    @Test fun aLineSayingATapLeavesIsTappedAtOnce() {
        val line = .5 to .91
        val game = Lobby(listOf(Popup(listOf(word("Chạm để tiếp tục", line.first, line.second)), null, line))).run()
        game.assertReachedTheEncyclopedia()
        assertEquals(line, game.taps[0])
    }

    @Test fun anUnreadPopupIsClosedByItsXOnceTheLobbyProvesDeaf() {
        // A popup with no words to know it by: only the taps on Trang chủ doing nothing show it is there.
        val x = .78 to .22
        val game = Lobby(listOf(Popup(listOf(word("Sự kiện mới", .34, .186)), x, x))).run()
        assertTrue(game.outcome.toString(), game.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf(HOME, HOME, x, HOME), game.taps.take(4))
    }

    @Test fun aGuideWithOnlyAButtonIsPressedOnceTheLobbyProvesDeaf() {
        val button = .5 to .75
        val game = Lobby(listOf(Popup(listOf(word("Đã rõ", button.first, button.second)), null, button))).run()
        assertTrue(game.outcome.toString(), game.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf(HOME, HOME, button, HOME), game.taps.take(4))
    }

    @Test fun anXIsPreferredToAButton() {
        val x = .78 to .22
        val game = Lobby(listOf(Popup(listOf(word("Đi đến", .5, .75), word("Nhận", .35, .75)), x, x))).run()
        assertTrue(game.outcome.toString(), game.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(x, game.taps[2])
    }

    @Test fun popupsOneAfterAnotherAreAllLeft() {
        val line = .5 to .91
        val button = .5 to .75
        val popups = listOf(
            Popup(NOTES_WORDS, NOTES_X, NOTES_X),
            Popup(listOf(word("Ấn để tiếp tục", line.first, line.second)), null, line),
            Popup(listOf(word("Nhận thưởng", button.first, button.second)), null, button)
        )
        val game = Lobby(popups).run()
        assertTrue(game.outcome.toString(), game.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(NOTES_X, game.taps[0])
        assertTrue(line in game.taps)
        assertTrue(button in game.taps)
    }

    @Test fun aPopupThatWillNotCloseStopsTheRunWithoutEndlessTaps() {
        val game = Lobby(listOf(Popup(NOTES_WORDS, NOTES_X, null))).run()
        val failed = game.outcome as ChessWikiReader.Outcome.Failed
        assertTrue(failed.reason, failed.reason.contains("popup"))
        assertTrue(game.taps.toString(), game.taps.size <= ChessWikiReader.MAX_POPUP_TRIES)
        assertTrue("Never taps the lobby behind", game.taps.all { it == NOTES_X })
    }

    @Test fun aPurchaseBoxIsLeftForThePlayer() {
        val confirm = .6 to .72
        val box = listOf(word("Mua gói quà với 100 Quân huy?", .5, .4), word("Xác nhận", confirm.first, confirm.second))
        val game = Lobby(listOf(Popup(box, null, confirm))).run()
        assertTrue(game.outcome is ChessWikiReader.Outcome.Failed)
        assertFalse("Pressed a purchase's button", confirm in game.taps)
    }

    @Test fun anXLikeShapeInAnOpenLobbyIsNeverTapped() {
        val game = Lobby(emptyList(), lobbyX = .8 to .2).run()
        assertTrue(game.outcome.toString(), game.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(HOME, game.taps[0])
        assertFalse((.8 to .2) in game.taps)
    }
}
