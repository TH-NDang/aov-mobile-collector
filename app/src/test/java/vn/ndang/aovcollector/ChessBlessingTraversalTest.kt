package vn.ndang.aovcollector

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

/** Runs the production reader against overlap, partial failure and restart schedules. */
class ChessBlessingTraversalTest {
    private class BlessingScreen(
        val pages: List<List<ChessWiki.Tile>>,
        val ledger: MutableSet<String> = hashSetOf(),
        val journal: MutableMap<String, Set<String>> = hashMapOf(),
        val fail: String? = null,
        val absent: String? = null,
        val interruptAfter: String? = null,
        val alreadyObtained: Set<String> = emptySet()
    ) : ChessWikiReader.Screen {
        override var alive = true
        var page = 0
        var owned = 0
        var released = 0
        var outcome: ChessWikiReader.Outcome? = null
        val attempts = ArrayList<String>()
        val exports = ArrayList<String>()
        val captured = ArrayList<String>()
        private val queue = ArrayDeque<() -> Unit>()

        private fun post(action: () -> Unit) {
            queue.addLast(action)
        }

        override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) = post {
            owned++
            val at = page
            then(object : ChessWikiReader.Look {
                override val words = listOf(GameScreens.Word("Bách khoa Cờ Liên Quân", .1, .02, .5, .1))
                override val signature = IntArray(96) { 20 + at * 35 }
                override val modal = false

                override fun tiles(topic: ChessWiki.Topic) = pages[at]

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    captured.add(name)
                    released++
                    post { then(true) }
                }

                override fun discard() {
                    released++
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) = post { then(true) }

        override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = post {
            page = (page + 1).coerceAtMost(pages.lastIndex)
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) = post(then)

        override fun seen(id: String) = id in ledger

        override fun finished(id: String) {
            ledger.add(id)
        }

        override fun note(message: String) {}

        override fun say(message: String) {}

        override fun nativeSave(
            group: String,
            item: String,
            x: Double,
            y: Double,
            preserveDetail: Boolean,
            aliases: Set<String>,
            then: (ChessWikiReader.NativeResult) -> Unit
        ) = post {
            assertEquals("chuc-phuc-thuong", group)
            assertFalse(preserveDetail)
            assertTrue(ChessBlessings.key(item) in aliases)
            attempts.add(item)
            when {
                journal.values.any {
                    ChessBlessings.sameSave(group, it, aliases + item)
                } ||
                    item in alreadyObtained -> then(ChessWikiReader.NativeResult.SAVED)
                item == fail -> then(ChessWikiReader.NativeResult.FAILED)
                item == absent -> then(ChessWikiReader.NativeResult.ABSENT)
                else -> {
                    exports.add(item)
                    journal[item] = aliases + item
                    if (item == interruptAfter) alive = false
                    then(ChessWikiReader.NativeResult.SAVED)
                }
            }
        }

        fun run() {
            ChessWikiReader(
                this,
                ChessWiki.Options(
                    setOf("thuong"),
                    openGame = false,
                    nativeGroups = setOf("chuc-phuc-thuong")
                )
            ) { outcome = it }.start()
            var steps = 0
            while (queue.isNotEmpty()) {
                assertTrue(++steps < 1000)
                queue.removeFirst().invoke()
            }
            assertEquals(owned, released)
        }
    }

    private fun tile(name: String, y: Double, hash: String) = ChessWiki.Tile(.275, y, ChessBlessings.key(name), name, setOf(hash))

    private fun pages() = listOf(
        listOf(tile("Giây phút huy hoàng", .4, "a-first"), tile("Nấc thang vinh diệu", .67, "b-first")),
        listOf(tile("Nấc thang vinh diệu", .34, "b-scrolled"), tile("Vòng quay bạc ngàn", .62, "c-first")),
        listOf(tile("Vòng quay bạc ngàn", .39, "c-bottom"))
    )

    @Test fun overlapWithDifferentHashesExportsEachNamedBlessingOnlyOnce() {
        val screen = BlessingScreen(pages())
        screen.run()
        assertTrue(screen.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("Giây phút huy hoàng", "Nấc thang vinh diệu", "Vòng quay bạc ngàn"), screen.exports)
        assertEquals(screen.exports, screen.attempts)
        assertEquals(3, screen.captured.size)
    }

    @Test fun differentNamesWithSharedArtworkAreNotSkipped() {
        val screen = BlessingScreen(
            listOf(
                listOf(
                    tile("Cá voi sát thủ 1", .4, "same-art"),
                    tile("Cá voi sát thủ 2", .67, "same-art")
                )
            )
        )
        screen.run()
        assertEquals(listOf("Cá voi sát thủ 1", "Cá voi sát thủ 2"), screen.exports)
    }

    @Test fun interruptionAfterJournalBeforeReaderCheckpointDoesNotExportAgain() {
        val first = BlessingScreen(pages(), interruptAfter = "Nấc thang vinh diệu")
        first.run()
        assertNull(first.outcome)
        assertFalse("native:chuc-phuc-thuong:" + ChessBlessings.key("Nấc thang vinh diệu") in first.ledger)
        val resume = BlessingScreen(pages(), first.ledger, first.journal)
        resume.run()
        assertTrue(resume.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("Vòng quay bạc ngàn"), resume.exports)
    }

    @Test fun noisyHeadingAfterScrollAndRestartDoesNotRepeatNativeExport() {
        val art = "blessing-art-v1:500:" + "0000111100001111".repeat(16)
        val firstTile = tile("Hoàn phí 5 vàng", .67, art)
        val noisyTile = tile("Hoàn phif 5 vàng", .34, art)
        val tier = tile("Hoàn phí 4 vàng", .67, art)
        val overlapping = listOf(listOf(firstTile), listOf(noisyTile, tier))
        val first = BlessingScreen(overlapping, interruptAfter = firstTile.label)
        first.run()
        assertEquals(listOf(firstTile.label), first.exports)
        val resumed = BlessingScreen(overlapping, first.ledger, first.journal)
        resumed.run()
        assertTrue(resumed.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf(tier.label), resumed.exports)
        assertEquals(2, resumed.journal.size)
    }

    @Test fun failedOrAbsentConfirmedTitleRemainsRetryableAndTopicIsNotCompleted() {
        for (absent in listOf(false, true)) {
            val name = "Nấc thang vinh diệu"
            val first = BlessingScreen(pages(), fail = if (absent) null else name, absent = if (absent) name else null)
            first.run()
            assertTrue(first.outcome is ChessWikiReader.Outcome.Failed)
            assertFalse("topic:thuong" in first.ledger)
            assertFalse("native:chuc-phuc-thuong:" + ChessBlessings.key(name) in first.ledger)
            val resume = BlessingScreen(pages(), first.ledger, first.journal)
            resume.run()
            assertEquals(listOf(name, "Vòng quay bạc ngàn"), resume.exports)
        }
    }

    @Test fun freshRunReusesObtainedBlessingsButStillCapturesCurrentPages() {
        val screen = BlessingScreen(pages(), alreadyObtained = setOf("Giây phút huy hoàng", "Nấc thang vinh diệu"))
        screen.run()
        assertTrue(screen.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("Vòng quay bạc ngàn"), screen.exports)
        assertEquals(3, screen.captured.size)
    }
}
