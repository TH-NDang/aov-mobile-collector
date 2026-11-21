package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingLoadTest {
    private class Screen(val tableSwitch: Boolean = false) : RankingReader.Screen {
        var active = true
        override val alive get() = active
        var selected = false
        var menu = false
        var reads = 0
        var acquired = 0
        var consumed = 0
        var saved = 0
        var completed = 0
        var time = 0L
        var savedAt = 0L
        var savedRead = 0
        var cancelOnWait = false
        var values: (Int) -> Int = { 10 }
        var rows: (Int) -> Boolean = { true }
        val queue = ArrayDeque<() -> Unit>()
        val combo = RankScreens.Combo(
            if (tableSwitch) RankScreens.Table.FIGURES else RankScreens.Table.RATES,
            if (tableSwitch) RankScreens.Mode.ALL else RankScreens.Mode.ELITE
        )
        var result: RankingReader.Outcome? = null

        private fun word(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .015, x + .04, y + .015)

        override fun look(words: Boolean, then: (RankingReader.Look?) -> Unit) {
            acquired++
            if (selected) reads++
            val currentRead = reads
            val content = buildList {
                add(word("Đổi bảng xếp hạng", .83, .2))
                add(word(if (selected && tableSwitch) "Sát thương" else "Tỷ lệ thắng", .5, .3))
                add(word(if (selected) combo.mode.label else RankScreens.Mode.ALL.label, .56, .2))
                if (menu) RankScreens.Mode.values().forEachIndexed { i, m -> add(word(m.label, .6, .313 + i * .065)) }
                if (rows(reads)) add(word("49,25 22,54 4,69", .5, .6))
            }
            then(object : RankingReader.Look {
                override val words = content
                override val lobby = false
                override val signature = IntArray(8) { values(currentRead) }
                override val menuOverlay = menu
                private var used = false

                override fun done(keep: String?) {
                    check(!used)
                    used = true
                    consumed++
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    done(null)
                    saved++
                    savedAt = time
                    savedRead = currentRead
                    active = false
                    then(true)
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            if (tableSwitch || menu) {
                selected = true
                menu = false
            } else {
                menu = true
            }
            then(true)
        }

        override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) = error("Unexpected drag before capture")

        override fun later(ms: Long, then: () -> Unit) {
            queue.addLast {
                time += ms
                if (cancelOnWait && ms == RankingReader.SETTLE_INTERVAL_MS) active = false
                then()
            }
        }

        override fun finished(combo: String) {
            completed++
        }

        fun run(): Screen {
            RankingReader(this, wanted = listOf(combo)) { result = it }.start()
            while (queue.isNotEmpty()) queue.removeFirst().invoke()
            assertEquals(acquired, consumed)
            return this
        }
    }

    @Test fun bothSwitchesRestThenObserveStableRowsBeforeFirstSave() {
        for (table in listOf(false, true)) {
            val screen = Screen(table).run()
            assertEquals(1, screen.saved)
            assertTrue(screen.savedAt >= RankingReader.SWITCH_LOAD_MS + RankingReader.SETTLE_INTERVAL_MS)
            assertEquals(2, screen.savedRead)
        }
    }

    @Test fun lateRowsAndChangingImagesDelayTheSavedFrame() {
        val screen = Screen().apply {
            rows = { it >= 2 }
            values = { if (it < 4) it * 30 else 120 }
        }.run()
        assertEquals(1, screen.saved)
        assertEquals(5, screen.savedRead)
    }

    @Test fun emptyOrContinuouslyChangingDataTimesOutWithoutSavingOrCheckpoint() {
        for (empty in listOf(false, true)) {
            val screen = Screen().apply {
                rows = { !empty }
                values = { it * 30 }
            }.run()
            assertTrue(screen.result is RankingReader.Outcome.Failed)
            assertEquals(RankingReader.MAX_SETTLE_READS, screen.reads)
            assertEquals(0, screen.saved)
            assertEquals(0, screen.completed)
        }
    }

    @Test fun tableSignatureIncludesPortraitAndCounterImageRegionsAtDifferentSizes() {
        for ((width, height) in listOf(1280 to 720, 2048 to 922, 2400 to 1080)) {
            val empty = RankScreens.signature(width, height) { _, row -> row.fill(0xff303060.toInt()) }
            for (region in listOf(.10..0.16, .75..0.90)) {
                val loaded = RankScreens.signature(width, height) { y, row ->
                    row.fill(0xff303060.toInt())
                    if (y.toDouble() / height in .4..0.9) {
                        for (x in (region.start * width).toInt()..(region.endInclusive * width).toInt()) row[x] = 0xffeeeeee.toInt()
                    }
                }
                assertTrue(!RankScreens.same(empty, loaded))
            }
        }
    }

    @Test fun resumedSelectedTableStillWaitsAndRejectsEmptyData() {
        val screen = Screen().apply {
            selected = true
            rows = { false }
        }.run()
        assertTrue(screen.result is RankingReader.Outcome.Failed)
        assertEquals(0, screen.saved)
        assertTrue(screen.time >= RankingReader.SWITCH_LOAD_MS)
    }

    @Test fun cancellationDuringSettlingDoesNotReadOrSaveAgain() {
        val screen = Screen().apply { cancelOnWait = true }.run()
        assertEquals(1, screen.reads)
        assertEquals(0, screen.saved)
        assertEquals(0, screen.completed)
    }
}
