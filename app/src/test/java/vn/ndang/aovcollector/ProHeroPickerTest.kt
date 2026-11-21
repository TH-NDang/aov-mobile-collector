package vn.ndang.aovcollector

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProHeroPickerTest {
    private class Fixture(val count: Int) : ProHeroPicker.Screen {
        override var alive = true
        var picker = false
        var checked = true
        var all = true
        var readable = true
        var moveWorks = true
        var selectionWorks = true
        var cancelled = false

        /**
         * Readings that miss every word, the frame whole on the picture: [blankStart] on the first look,
         * [blankAfterScroll] after each scroll on. [goneAfterScroll]: looks after each scroll that are not the frame at
         * all. [afterScroll]: what happens on the screen at each scroll.
         */
        var blankStart = 0
        var blankAfterScroll = 0
        var goneAfterScroll = 0
        var afterScroll: (Fixture.() -> Unit)? = null
        private var blanks = 0
        private var gones = 0

        /** Chọn tướng opens at its top every time, as Chiến khu's does once Đã nhận is cleared. */
        var opensAtTop = false
        val notes = mutableListOf<String>()
        var offset = 0
        var toggles = 0
        var shortSlowDrags = 0
        val selected = mutableListOf<Int>()
        val actions = mutableListOf<String>()
        private val height = 600
        private val row0 = .296
        private val maxOffset get() = max(
            0,
            ((row0 + (ceil(count / 8.0) - 1) * BoardScreens.ROW_PITCH + BoardScreens.CARD - .88) * height).roundToInt()
        )

        override fun look(then: (ProHeroPicker.Look?) -> Unit) {
            val snapshotOffset = offset
            val gone = gones > 0
            if (gone) gones--
            val blank = gone || blanks > 0
            if (blanks > 0) blanks--
            val rows = (0 until ceil(count / 8.0).toInt()).map {
                val top = row0 + it * BoardScreens.ROW_PITCH - snapshotOffset.toDouble() / height
                max(BoardScreens.VIEW_TOP, top) to min(BoardScreens.VIEW_BOTTOM, top + BoardScreens.CARD)
            }.filter { it.second - it.first > .02 }
            then(object : ProHeroPicker.Look {
                override val words = if (picker && !blank) {
                    listOf(
                        GameScreens.Word("Chọn tướng", .16, .095, .25, .14),
                        GameScreens.Word("Tất cả", .20, .215, .24, .24)
                    )
                } else {
                    emptyList()
                }
                override val preparation = !picker && !blank
                override val ownedFilter = if (readable && !blank) BoardScreens.OwnedFilter(checked, .814, .229) else null
                override val allSelected = all && !blank
                override val title = if (picker && !gone) TITLE else OTHER

                override fun header(words: List<GameScreens.Word>) = if (picker && !gone) {
                    all to (if (readable) BoardScreens.OwnedFilter(checked, .814, .229) else null)
                } else {
                    false to null
                }

                override val rows = rows
                override val height = this@Fixture.height
                override val profile = if (readable) {
                    FloatArray(
                        ((BoardScreens.VIEW_BOTTOM * height).toInt() - (BoardScreens.VIEW_TOP * height).toInt()) * 15
                    ) { i ->
                        // Stable, non-periodic artwork features. Repeatable integer translations exercise the production matcher.
                        val seed = (i / 15 + snapshotOffset) * 1103515245 + (i % 15) * 12345
                        ((seed xor (seed ushr 16)) and 255).toFloat()
                    }
                } else {
                    floatArrayOf()
                }

                override fun card(col: Int, top: Double, bottom: Double): Boolean {
                    val row = ((top + snapshotOffset.toDouble() / height - row0) / BoardScreens.ROW_PITCH).roundToInt()
                    return row >= 0 && row * 8 + col < count
                }

                override fun done(keep: String?) {}
            })
        }

        override fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) {
            actions += action.id
            when (action.id.substringAfter("pro.v1.picker.")) {
                "open" -> {
                    picker = true
                    if (opensAtTop) offset = 0
                }
                "all" -> all = true
                "owned-filter" -> {
                    checked = !checked
                    toggles++
                }
                // As on the user's phone: a short slow drag resting on a card chooses that hero.
                "scroll.reset", "scroll.next", "scroll.probe-flick", "scroll.reset-flick" -> if (action.holdMs > 0 &&
                    kotlin.math.abs(action.points.first().y - action.points.last().y) < BoardReader.MIN_SLOW_DRAG - .001
                ) {
                    shortSlowDrags++
                    picker = false
                } else if (moveWorks) {
                    offset = (offset + ((action.points.first().y - action.points.last().y) * height).roundToInt()).coerceIn(0, maxOffset)
                }
                "card" -> {
                    val point = action.points.single()
                    val col = BoardScreens.COLUMNS.indices.minBy { kotlin.math.abs(BoardScreens.COLUMNS[it] - point.x) }
                    val row = ((point.y + offset.toDouble() / height - row0 - BoardScreens.CARD / 2) / BoardScreens.ROW_PITCH).roundToInt()
                    selected += row * 8 + col
                    if (selectionWorks) picker = false
                    blanks = 0
                    gones = 0
                }
            }
            if (action.id.endsWith("scroll.next")) {
                blanks = blankAfterScroll
                gones = goneAfterScroll
                afterScroll?.invoke(this)
            }
            if (cancelled) alive = false
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) = then()

        override fun note(text: String) {
            notes += text
        }

        fun run(index: Int, memory: ProHeroPicker.Memory = ProHeroPicker.Memory()): ProHeroPicker.Outcome? {
            var result: ProHeroPicker.Outcome? = null
            blanks = blankStart
            ProHeroPicker(this, index, memory) { result = it }.start()
            return result
        }

        fun flicks() = actions.count { it.endsWith("probe-flick") || it.endsWith("reset-flick") }

        private companion object {
            /** Title strips as [ProHeroPicker.title] gives them: the frame's own, and anything else. */
            fun strip(by: Long) = FloatArray(990) { (((it + 1) * by).toInt() ushr 7 and 255).toFloat() }

            val TITLE = strip(2654435761L)
            val OTHER = strip(2246822519L)
        }
    }

    /**
     * The reader missed the header's words on every look after the first scroll, six times over, with the frame whole
     * on the picture: the user's run stopped twice at hero 104. A frame read whole once is known again by its title
     * strip, and Tất cả and Đã nhận are then looked at where they were read.
     */
    @Test fun aGridWhoseWordsAreNotReadIsKnownByItsPicture() {
        val f = Fixture(120).apply { blankAfterScroll = 99 }
        assertEquals(ProHeroPicker.Outcome.Selected(103), f.run(103))
        assertEquals(1, f.notes.count { it.contains("nhận theo hình") })
        // What changed meanwhile shows on the picture: Đã nhận ticked, or another tab, is not the grid to scroll.
        val ticked = Fixture(120).apply {
            blankAfterScroll = 99
            afterScroll = { checked = true }
        }
        assertTrue(ticked.run(103) is ProHeroPicker.Outcome.Failed)
        assertTrue(ticked.notes.any { it.contains("Đã nhận đang bật") })
        val tab = Fixture(120).apply {
            blankAfterScroll = 99
            afterScroll = { all = false }
        }
        assertTrue(tab.run(103) is ProHeroPicker.Outcome.Failed)
        assertTrue(tab.notes.any { it.contains("Tất cả chưa được chọn") })
        // A frame never read whole has nothing to be known by: its words are asked for, as before.
        assertTrue(Fixture(120).apply { readable = false }.run(103) is ProHeroPicker.Outcome.Failed)
    }

    /** A look that is not the frame at all is taken again, after a scroll as on opening, and stops the run at the third. */
    @Test fun aLookThatIsNotTheGridIsTakenAgain() {
        val scrolled = Fixture(120).apply { goneAfterScroll = ProHeroPicker.READS - 1 }
        assertEquals(ProHeroPicker.Outcome.Selected(60), scrolled.run(60))
        assertTrue(scrolled.notes.any { it.contains("đọc lại") && it.contains("không đọc được") })
        val lost = Fixture(120).apply { goneAfterScroll = ProHeroPicker.READS }
        assertTrue(lost.run(60) is ProHeroPicker.Outcome.Failed)
        assertTrue(lost.notes.any { it.contains("mất lưới sau khi cuộn") })
        assertEquals(ProHeroPicker.Outcome.Selected(5), Fixture(120).apply { blankStart = ProHeroPicker.READS - 1 }.run(5))
        assertTrue(Fixture(120).apply { blankStart = ProHeroPicker.READS }.run(5) is ProHeroPicker.Outcome.Failed)
    }

    @Test fun recognizesOneCardTailThatTheMajorityGridDetectorCannotSee() {
        val width = 1200
        val height = 600
        val reader = { y: Int, row: IntArray ->
            row.fill(0xff303070.toInt())
            if (y in 180..261) for (x in 235..295) row[x] = 0xffccaa44.toInt()
        }
        val rows = ProHeroPicker.rows(width, height, reader)
        assertEquals(1, rows.size)
        assertEquals(.30, rows.single().first, .003)
        assertEquals(.137, rows.single().second - rows.single().first, .003)
    }

    @Test fun ignoredMidlistScrollIsNotEnd() {
        val f = Fixture(61).apply { moveWorks = false }
        assertTrue(f.run(40) is ProHeroPicker.Outcome.Failed)
        assertTrue(f.selected.isEmpty())
    }

    @Test fun traversesEveryCardInReadingOrderAndConfirmsShortLastRow() {
        val f = Fixture(19)
        for (i in 0 until 19) assertEquals(ProHeroPicker.Outcome.Selected(i), f.run(i))
        assertSame(ProHeroPicker.Outcome.End, f.run(19))
        assertEquals((0 until 19).toList(), f.selected)
        assertEquals(1, f.toggles)
    }

    @Test fun noScrollRestsAShortSlowDragOnACard() {
        val f = Fixture(61)
        for (i in listOf(0, 9, 24, 40, 60)) assertEquals(ProHeroPicker.Outcome.Selected(i), f.run(i))
        assertEquals(0, f.shortSlowDrags)
    }

    @Test fun measuresClampedScrollAndResetsFromRetainedPosition() {
        val f = Fixture(61)
        for (i in listOf(24, 40, 60, 0, 8)) assertEquals(ProHeroPicker.Outcome.Selected(i), f.run(i))
        assertEquals(listOf(24, 40, 60, 0, 8), f.selected)
        assertEquals(1, f.toggles)
    }

    @Test fun unknownCheckboxIsNotUncheckedOrEnd() {
        val f = Fixture(30).apply { readable = false }
        assertTrue(f.run(0) is ProHeroPicker.Outcome.Failed)
        assertEquals(0, f.toggles)
        assertTrue(f.selected.isEmpty())
    }

    @Test fun selectsAllBeforeReadingIndexesAndLeavesOffFilterOff() {
        val f = Fixture(30).apply {
            checked = false
            all = false
        }
        assertEquals(ProHeroPicker.Outcome.Selected(0), f.run(0))
        assertTrue(f.actions.contains("pro.v1.picker.all"))
        assertEquals(0, f.toggles)
    }

    @Test fun ignoredSelectionNeverRetriesOrPretendsSelected() {
        val f = Fixture(30).apply { selectionWorks = false }
        assertTrue(f.run(0) is ProHeroPicker.Outcome.Failed)
        assertEquals(listOf(0), f.selected)
    }

    @Test fun cancellationStopsAfterInflightGesture() {
        val f = Fixture(30).apply { cancelled = true }
        assertNull(f.run(0))
        assertEquals(listOf("pro.v1.picker.open"), f.actions)
    }

    @Test fun emptyUnrecognizedGridDoesNotMeanEnd() {
        val f = Fixture(0)
        assertTrue(f.run(0) is ProHeroPicker.Outcome.Failed)
    }

    /** The user's run probed and flicked back up for every hero; one proven top serves the picks after it. */
    @Test fun picksAfterTheFirstNeitherProbeNorFlickBack() {
        val f = Fixture(61)
        val memory = ProHeroPicker.Memory()
        assertEquals(ProHeroPicker.Outcome.Selected(0), f.run(0, memory))
        val first = f.flicks()
        assertTrue("$first", first in 3..4)
        for (i in 1 until 30) assertEquals(ProHeroPicker.Outcome.Selected(i), f.run(i, memory))
        assertEquals((0 until 30).toList(), f.selected)
        assertEquals(first, f.flicks())
        assertTrue(f.notes.any { it.contains("còn ở chỗ lượt trước") })
        assertEquals(0, f.shortSlowDrags)
    }

    @Test fun aListOpeningAtItsTopIsTakenAsTheTopProvenBefore() {
        val f = Fixture(61).apply { opensAtTop = true }
        val memory = ProHeroPicker.Memory()
        for (i in listOf(0, 60, 52, 2)) assertEquals(ProHeroPicker.Outcome.Selected(i), f.run(i, memory))
        assertEquals(listOf(0, 60, 52, 2), f.selected)
        // Only the first pick probes and flicks back up.
        assertEquals(1, f.actions.count { it.endsWith("probe-flick") })
        assertTrue(f.notes.any { it.contains("mở ở đầu danh sách") })
    }

    @Test fun aListThatMovedSinceIsFoundAgainFromItsTop() {
        val f = Fixture(61)
        val memory = ProHeroPicker.Memory()
        assertEquals(ProHeroPicker.Outcome.Selected(40), f.run(40, memory))
        // Moved by hand somewhere else: neither where it was left nor its top.
        f.offset = 37
        assertEquals(ProHeroPicker.Outcome.Selected(9), f.run(9, memory))
        assertEquals(listOf(40, 9), f.selected)
    }
}
