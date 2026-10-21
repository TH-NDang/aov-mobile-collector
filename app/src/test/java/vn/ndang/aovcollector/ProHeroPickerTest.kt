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
        var offset = 0
        var toggles = 0
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
            val rows = (0 until ceil(count / 8.0).toInt()).map {
                val top = row0 + it * BoardScreens.ROW_PITCH - snapshotOffset.toDouble() / height
                max(BoardScreens.VIEW_TOP, top) to min(BoardScreens.VIEW_BOTTOM, top + BoardScreens.CARD)
            }.filter { it.second - it.first > .02 }
            then(object : ProHeroPicker.Look {
                override val words = if (picker) {
                    listOf(
                        GameScreens.Word("Chọn tướng", .16, .095, .25, .14),
                        GameScreens.Word("Tất cả", .20, .215, .24, .24)
                    )
                } else {
                    emptyList()
                }
                override val preparation = !picker
                override val ownedFilter = if (readable) BoardScreens.OwnedFilter(checked, .814, .229) else null
                override val allSelected = all
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
                "open" -> picker = true
                "all" -> all = true
                "owned-filter" -> {
                    checked = !checked
                    toggles++
                }
                "scroll.reset", "scroll.next" -> if (moveWorks) {
                    offset = (offset + ((action.points.first().y - action.points.last().y) * height).roundToInt()).coerceIn(0, maxOffset)
                }
                "card" -> {
                    val point = action.points.single()
                    val col = BoardScreens.COLUMNS.indices.minBy { kotlin.math.abs(BoardScreens.COLUMNS[it] - point.x) }
                    val row = ((point.y + offset.toDouble() / height - row0 - BoardScreens.CARD / 2) / BoardScreens.ROW_PITCH).roundToInt()
                    selected += row * 8 + col
                    if (selectionWorks) picker = false
                }
            }
            if (cancelled) alive = false
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) = then()

        fun run(index: Int): ProHeroPicker.Outcome? {
            var result: ProHeroPicker.Outcome? = null
            ProHeroPicker(this, index) { result = it }.start()
            return result
        }
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
}
