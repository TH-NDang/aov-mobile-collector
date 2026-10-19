package vn.ndang.aovcollector

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

/** Production pagination/resume against semantic titles and a non-overwriting file adapter. */
class ChessItemTraversalTest {
    private class ItemScreen(
        val topic: String,
        val name: String?,
        val hash: String = "art-a",
        val failPage: Int? = null,
        val ledger: MutableSet<String> = hashSetOf(),
        val signatures: MutableMap<String, IntArray> = hashMapOf(),
        val files: MutableMap<String, IntArray> = hashMapOf()
    ) : ChessWikiReader.Screen {
        override val alive = true
        var opened = false
        var page = 0
        var owned = 0
        var released = 0
        val writes = ArrayList<String>()
        var outcome: ChessWikiReader.Outcome? = null
        private val queue = ArrayDeque<() -> Unit>()

        private fun post(action: () -> Unit) {
            queue.addLast(action)
        }

        override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) = post {
            val modal = opened
            val at = page
            owned++
            then(object : ChessWikiReader.Look {
                override val modal = modal
                override val signature = IntArray(96) { if (modal) 80 + at * 40 else 10 }
                override val words = if (modal) {
                    listOfNotNull(
                        GameScreens.Word("2 (2) 20% tỷ lệ liên xạ", .36, .285, .75, .315),
                        name?.let { GameScreens.Word(it, .36, .225, .70, .255) }
                    )
                } else {
                    listOf(GameScreens.Word("Bách khoa Cờ Liên Quân", .1, .02, .5, .1))
                }

                override fun tiles(topic: ChessWiki.Topic) = listOf(ChessWiki.Tile(.50, .50, hash))

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    released++
                    post {
                        if (modal && at + 1 == failPage) {
                            then(false)
                        } else {
                            val path =
                                ChessCaptureFiles.directoryParts(
                                    name
                                )!!.joinToString("/") + "/" + ChessCaptureFiles.screenshotBase(name) + ".png"
                            val existing = files[path]
                            if (existing != null) {
                                assertArrayEquals("Existing page was overwritten", existing, signature)
                            } else {
                                files[path] = signature.copyOf()
                                writes.add(path)
                            }
                            then(true)
                        }
                    }
                }

                override fun discard() {
                    released++
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) = post {
            if (x > .88) {
                opened = false
            } else if (x == .50 && y == .50) {
                opened = true
                page = 0
            }
            then(true)
        }

        override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = post {
            if (opened) page = (page + 1).coerceAtMost(1)
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) = post(then)

        override fun seen(id: String) = id in ledger

        override fun finished(id: String) {
            ledger.add(id)
        }

        override fun detailSignature(id: String) = signatures[id]

        override fun finishDetail(id: String, signature: IntArray): Boolean {
            signatures[id] = signature.copyOf()
            ledger.add(id)
            return true
        }

        override fun note(message: String) {}

        override fun say(message: String) {}

        fun run() {
            ChessWikiReader(
                this,
                ChessWiki.Options(
                    setOf(topic),
                    openGame = false,
                    equipmentDetails = topic == "tat-ca-trang-bi",
                    synergyDetails = topic != "tat-ca-trang-bi"
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

    @Test fun equipmentFactionAndProfessionPagesStayInTheirSingleTopicFolder() {
        for ((topic, name, folder, slug) in listOf(
            listOf("tat-ca-trang-bi", "Lệnh bài phong thần", "trang-bi/tat-ca-trang-bi", "lenh-bai-phong-than"),
            listOf("phe", "Cung thủ", "tuong-tac/phe", "cung-thu"),
            listOf("nghe", "Tên nghề", "tuong-tac/nghe", "ten-nghe")
        )) {
            val screen = ItemScreen(topic, name)
            screen.run()
            assertTrue(screen.outcome is ChessWikiReader.Outcome.Done)
            assertEquals(listOf("$folder/$slug-page-1.png", "$folder/$slug-page-2.png"), screen.writes.filter { "/$slug-page-" in it })
        }
    }

    @Test fun failedSecondPageResumesWithSameTitleDespiteChangedArtworkHash() {
        val first = ItemScreen("phe", "Cung thủ", failPage = 2)
        first.run()
        assertTrue(first.outcome is ChessWikiReader.Outcome.Failed)
        val resumed = ItemScreen(
            "phe",
            "Cung thủ",
            hash = "art-after-scroll",
            ledger = first.ledger,
            signatures = first.signatures,
            files = first.files
        )
        resumed.run()
        assertTrue(resumed.outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("tuong-tac/phe/cung-thu-page-2.png"), resumed.writes.filter { "-page-" in it })
        assertEquals(2, resumed.files.keys.count { "/cung-thu-page-" in it })
    }

    @Test fun missingVerifiedHeadingStopsWithoutCreatingNumericDescriptionFolders() {
        val screen = ItemScreen("phe", null)
        screen.run()
        assertTrue(screen.outcome is ChessWikiReader.Outcome.Failed)
        assertEquals(listOf("tuong-tac/phe/phe-page-1.png"), screen.files.keys.toList())
        assertFalse("topic:phe" in screen.ledger)
    }
}
