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
     * by [slip] of the finger's travel (touch slop); [blind] buttons are not read in that many frames. After Dùng
     * combo, Chuẩn bị's panel keeps changing for [settle] looks; after the Pro tab is opened, its list shows no
     * button for [loading] looks.
     */
    private class Game(
        val cards: Int,
        val slip: Double = .85,
        var blind: Int = 0,
        var deafApplies: Int = 0,
        val settle: Int = 0,
        val loading: Int = 0,
        /** Frames of the scrolled list in which no button is read at all, as the phone's pills once were. */
        var unseen: Int = 0,
        /**
         * Looks at the Pro list, once [restlessAfter] combos are applied, in which it is not at rest: its buttons off
         * the card grid, as on a game answering slowly.
         */
        var restless: Int = 0,
        val restlessAfter: Int = 0
    ) : ProSimpleReader.Screen {
        var popup = false
        var proTab = false
        var offset = 0.0
        val applied = ArrayList<Int>()
        val photos = ArrayList<Pair<String, Int?>>()
        val notes = ArrayList<String>()
        var outcome: ProSimpleReader.Outcome? = null

        // Looks since the last Dùng combo, whether each photo was taken once Chuẩn bị had settled, loading looks left.
        var drawn = 0
        val settled = ArrayList<Boolean>()
        var loadingLeft = 0
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
            val shown = if (loadingLeft > 0 && proTab) {
                loadingLeft--
                emptyList()
            } else {
                (0 until cards).filter { buttonY(it) in .18..0.92 }
            }
            val read = when {
                unseen > 0 && proTab && offset > 0 -> {
                    unseen--
                    emptyList()
                }
                blind > 0 -> {
                    blind--
                    shown.drop(1)
                }
                else -> shown
            }
            val wobble = if (proTab && restless > 0 && applied.size >= restlessAfter) {
                restless--
                .1
            } else {
                0.0
            }
            listOf(word("Hệ thống đề xuất", .22, .12), word("Đề cử Pro", .20, .455)) +
                read.map { word("Dùng combo", .757, buttonY(it) + wobble) }
        }

        override fun look(then: (ProSimpleReader.Look?) -> Unit) = queue.addLast {
            val shown = words()
            val applyRank = applied.lastOrNull()
            if (!popup) drawn++
            val now = drawn
            then(object : ProSimpleReader.Look {
                override val words = shown
                override val proSelected = popup && proTab
                override val panel = when {
                    popup -> IntArray(4) { 7 }
                    now <= settle -> IntArray(4) { now * 10 + it }
                    else -> IntArray(4) { 100 + (applyRank ?: 0) }
                }

                override fun done(keep: String?) {
                    if (keep != null) notes += "kept $keep"
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    photos += name to (if (popup) null else applyRank)
                    if (!popup) settled += now > settle
                    then(true)
                }
            })
        }

        override fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) = queue.addLast {
            val p = action.points
            when (action.id) {
                "pro.v1.other" -> if (!popup) popup = true
                "pro.v1.tab" -> {
                    proTab = true
                    loadingLeft = loading
                }
                "pro.v2.apply" -> if (popup && proTab) {
                    if (deafApplies > 0) {
                        deafApplies--
                    } else {
                        val rank = (0 until cards).single { abs(buttonY(it) - target!!.y) < .02 }
                        assertTrue("Dùng combo must be whole in the list", buttonY(rank) in ProSimpleReader.TAP)
                        applied += rank
                        drawn = 0
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

        fun run(state: JSONObject = JSONObject(), max: Int = ProSimpleReader.MAX): Game {
            ProSimpleReader(this, state, max) { outcome = it }.start()
            var steps = 0
            while (queue.isNotEmpty()) {
                check(++steps < 20_000) { "stuck" }
                queue.removeFirst().invoke()
            }
            return this
        }
    }

    @Test fun aListLostWhileScrollingIsSoughtAgainFromItsTop() {
        // Two frames in a row with no button read after a drag: the cards are counted again from the list's top,
        // and the hero's other combos are still taken.
        val game = Game(cards = 7, unseen = 2).run()
        assertEquals(ProSimpleReader.Outcome.Done(5, false), game.outcome)
        assertEquals((1..5).map { "$it" to it - 1 }, game.photos)
        assertTrue(game.notes.toString(), game.notes.any { "về đầu danh sách tìm lại" in it })
    }

    /**
     * The list not at rest at its top within the six flicks, as when the game answers slowly: the user's hero 89 was
     * left with two photos of three. It is waited for and sought again more slowly; a hero still cut short is marked,
     * so that the run comes back to it.
     */
    @Test fun aListNotAtRestAtItsTopIsWaitedForThenTheHeroIsMarked() {
        val slow = Game(cards = 7, restless = 9, restlessAfter = 2).run(max = 3)
        assertEquals(ProSimpleReader.Outcome.Done(3, false), slow.outcome)
        assertEquals(listOf("1" to 0, "2" to 1, "3" to 2), slow.photos)
        assertTrue(slow.notes.toString(), slow.notes.any { "thử lại chậm hơn" in it })
        val state = JSONObject()
        val never = Game(cards = 7, restless = 10_000, restlessAfter = 2).run(state, max = 3)
        assertEquals(ProSimpleReader.Outcome.Done(2, false), never.outcome)
        assertEquals(3, state.getInt("lost"))
        assertTrue(never.notes.toString(), never.notes.any { "bỏ qua từ đề cử 3" in it } && "kept pro-top" in never.notes)
        // The list's own end is not a hero cut short.
        val ended = JSONObject()
        assertEquals(ProSimpleReader.Outcome.Done(2, false), Game(cards = 2).run(ended, max = 3).outcome)
        assertTrue(!ended.has("lost"))
    }

    @Test fun takesTheFirstFiveRecommendationsByPlaceThenKeepsTheFirst() {
        val game = Game(cards = 7).run()
        assertEquals(ProSimpleReader.Outcome.Done(5, false), game.outcome)
        assertEquals((1..5).map { "$it" to it - 1 }, game.photos)
        assertEquals(listOf(0, 1, 2, 3, 4, 0), game.applied)
    }

    @Test fun takesAsManyCombosAsAsked() {
        val two = Game(cards = 7).run(max = 2)
        assertEquals(ProSimpleReader.Outcome.Done(2, false), two.outcome)
        assertEquals(listOf("1" to 0, "2" to 1), two.photos)
        assertEquals(listOf(0, 1, 0), two.applied)
        val all = Game(cards = 7).run(max = BuiltInMacros.ALL)
        assertEquals(ProSimpleReader.Outcome.Done(7, false), all.outcome)
        assertEquals((0 until 7).toList() + 0, all.applied)
    }

    @Test fun chuanBiIsPhotographedOnlyOnceTheComboHasFinishedDrawing() {
        val game = Game(cards = 4, settle = 3).run()
        assertEquals(ProSimpleReader.Outcome.Done(4, false), game.outcome)
        assertEquals(4, game.settled.size)
        assertTrue("taken while drawing: ${game.settled}", game.settled.all { it })
        // A panel that never stops changing is still taken, with a line in the log.
        val restless = Game(cards = 1, settle = 50).run()
        assertEquals(ProSimpleReader.Outcome.Done(1, false), restless.outcome)
        assertTrue(restless.notes.any { it.contains("vẫn đổi") })
    }

    @Test fun aSlowProTabIsWaitedForBeforeCallingItEmpty() {
        val game = Game(cards = 3, loading = 5).run()
        assertEquals(ProSimpleReader.Outcome.Done(3, false), game.outcome)
        assertEquals(listOf(0, 1, 2, 0), game.applied)
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
