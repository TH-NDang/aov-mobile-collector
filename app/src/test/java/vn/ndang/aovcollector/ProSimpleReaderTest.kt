package vn.ndang.aovcollector

import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun word(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .02, x + .04, y + .02)

class ProSimpleReaderTest {
    /**
     * Chuẩn bị and the Hệ thống đề xuất popup with [cards] Pro recommendations, measured on the user's screenshots:
     * buttons at x .757, the first at y .41 and the next .366 lower at the top of the list. A drag moves the list
     * by [slip] of the finger's travel (touch slop); [blind] buttons are not read in that many frames.
     */
    private class Game(val cards: Int, val slip: Double = .85, var blind: Int = 0, var deafApplies: Int = 0) : ProSimpleReader.Screen {
        var popup = false
        var proTab = false
        var offset = 0.0
        val applied = ArrayList<Int>()
        val photos = ArrayList<Pair<String, Int?>>()
        val notes = ArrayList<String>()
        var outcome: ProSimpleReader.Outcome? = null
        private val queue = ArrayDeque<() -> Unit>()
        override val alive get() = outcome == null

        private val maxOffset get() = maxOf(0.0, (cards - 2) * ProSimpleReader.PITCH)

        private fun buttonY(i: Int) = .41 + i * ProSimpleReader.PITCH - offset

        private fun words(): List<GameScreens.Word> = if (!popup) {
            listOf(
                word("Chuẩn bị", .17, .05),
                word("Trang bị", .45, .20),
                word("Phù hiệu", .45, .45),
                word("Phụ trợ", .45, .62),
                word("Ph.án khác", .86, .92)
            )
        } else {
            val shown = (0 until cards).filter { buttonY(it) in .18..0.92 }
            val read = if (blind > 0) {
                blind--
                shown.drop(1)
            } else {
                shown
            }
            listOf(word("Hệ thống đề xuất", .22, .12), word("Đề cử Pro", .20, .455)) +
                read.map { word("Dùng combo", .757, buttonY(it)) }
        }

        override fun look(then: (ProSimpleReader.Look?) -> Unit) = queue.addLast {
            val shown = words()
            val applyRank = applied.lastOrNull()
            then(object : ProSimpleReader.Look {
                override val words = shown
                override val proSelected = popup && proTab

                override fun done(keep: String?) {
                    if (keep != null) notes += "kept $keep"
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    photos += name to (if (popup) null else applyRank)
                    then(true)
                }
            })
        }

        override fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) = queue.addLast {
            val p = action.points
            when (action.id) {
                "pro.v1.other" -> if (!popup) popup = true
                "pro.v1.tab" -> proTab = true
                "pro.v2.apply" -> if (popup && proTab) {
                    if (deafApplies > 0) {
                        deafApplies--
                    } else {
                        val rank = (0 until cards).single { abs(buttonY(it) - target!!.y) < .02 }
                        assertTrue("Dùng combo must be whole in the list", buttonY(rank) in ProSimpleReader.TAP)
                        applied += rank
                        popup = false
                        offset = 0.0
                    }
                }
                "pro.v2.list.step" -> offset = (offset + (p[0].y - p[1].y) * slip).coerceIn(0.0, maxOffset)
                "pro.v2.list.top" -> offset = 0.0
                else -> error("unexpected ${action.id}")
            }
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) {
            queue.addLast(then)
        }

        override fun persist() = true

        override fun note(text: String) {
            notes += text
        }

        fun run(state: JSONObject = JSONObject()): Game {
            ProSimpleReader(this, state) { outcome = it }.start()
            var steps = 0
            while (queue.isNotEmpty()) {
                check(++steps < 20_000) { "stuck" }
                queue.removeFirst().invoke()
            }
            return this
        }
    }

    @Test fun takesTheFirstFiveRecommendationsByPlaceThenKeepsTheFirst() {
        val game = Game(cards = 7).run()
        assertEquals(ProSimpleReader.Outcome.Done(5, false), game.outcome)
        assertEquals((1..5).map { "$it" to it - 1 }, game.photos)
        assertEquals(listOf(0, 1, 2, 3, 4, 0), game.applied)
    }

    @Test fun fewerRecommendationsEndAtTheListEnd() {
        val three = Game(cards = 3).run()
        assertEquals(ProSimpleReader.Outcome.Done(3, false), three.outcome)
        assertEquals(listOf(0, 1, 2, 0), three.applied)
        val one = Game(cards = 1).run()
        assertEquals(ProSimpleReader.Outcome.Done(1, false), one.outcome)
        assertEquals(listOf(0), one.applied)
    }

    @Test fun anEmptyListKeepsItsPictureAndGoesOn() {
        val game = Game(cards = 0).run()
        assertEquals(ProSimpleReader.Outcome.Done(0, true), game.outcome)
        assertEquals(listOf("khong-co-de-cu" to null), game.photos)
        assertTrue(game.applied.isEmpty())
    }

    @Test fun touchSlopAndAMissedButtonDoNotChangeWhichRecommendationIsTaken() {
        for (slip in listOf(.6, .75, 1.0)) {
            val game = Game(cards = 6, slip = slip, blind = 4).run()
            assertEquals(ProSimpleReader.Outcome.Done(5, false), game.outcome)
            assertEquals(listOf(0, 1, 2, 3, 4, 0), game.applied)
        }
    }

    @Test fun anIgnoredDungComboIsTappedOnceMore() {
        val game = Game(cards = 2, deafApplies = 1).run()
        assertEquals(ProSimpleReader.Outcome.Done(2, false), game.outcome)
        assertEquals(listOf(0, 1, 0), game.applied)
        assertTrue(game.notes.any { it.contains("thử lại một lần") })
    }

    @Test fun aResumedHeroGoesOnFromTheNextPhoto() {
        val game = Game(cards = 4).run(JSONObject().put("captured", 2))
        assertEquals(ProSimpleReader.Outcome.Done(4, false), game.outcome)
        assertEquals(listOf("3" to 2, "4" to 3), game.photos)
    }

    @Test fun placesComeFromWhereButtonsSitNotFromCountingThem() {
        fun button(y: Double) = GameScreens.Word("Dùng combo", .72, y - .02, .79, y + .02)
        val top = ProSimpleReader.ranked(listOf(button(.41), button(.776)), 0.0)!!
        // A frame half a card down in which OCR missed the middle button.
        val after = listOf(button(.27), button(1.002))
        val moved = ProSimpleReader.shift(top, after)!!
        assertEquals(.14, moved, .005)
        assertEquals(listOf(0, 2), ProSimpleReader.ranked(after, moved)!!.map { it.rank })
        // The first card missed at the top: the one seen is still the second.
        assertEquals(listOf(1), ProSimpleReader.ranked(listOf(button(.776)), 0.0)!!.map { it.rank })
        // Off the card grid: not the top of the list.
        assertEquals(null, ProSimpleReader.ranked(listOf(button(.58)), 0.0))
    }
}
