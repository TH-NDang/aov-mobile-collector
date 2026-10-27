package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class ChessWikiTest {
    private fun oneCardProfile(): FloatArray {
        val random = java.util.Random(901)
        return FloatArray(750 * 15) { random.nextFloat() * 255 }
    }

    @Test fun overlappingSkillProbesAreOneTargetButSeparatedIconsRemainDistinct() {
        val targets = ChessWiki.mergeSkillProbes(
            listOf(
                ChessWiki.Tile(.405, .55, "lower-patch"),
                ChessWiki.Tile(.405, .50, "upper-patch"),
                ChessWiki.Tile(.405, .66, "another-icon")
            )
        )
        assertEquals(2, targets.size)
        assertEquals(.525, targets[0].y, .0001)
        assertEquals(setOf("upper-patch", "lower-patch"), targets[0].aliases)
        assertEquals(.66, targets[1].y, .0001)
        assertEquals(
            2,
            ChessWiki.mergeSkillProbes(
                listOf(
                    ChessWiki.Tile(.405, .50, "same-y-left"),
                    ChessWiki.Tile(.55, .50, "same-y-right")
                )
            ).size
        )
        assertTrue(ChessWiki.mergeSkillProbes(emptyList()).isEmpty())
    }

    @Test fun saturatedBackgroundIsNotMistakenForAnIllustratedSkill() {
        assertFalse(ChessWiki.skillProbeTextured(IntArray(49) { 0xff343576.toInt() }))
        assertFalse(
            ChessWiki.skillProbeTextured(
                IntArray(49) {
                    val shade = 88 + it % 3
                    0xff000000.toInt() or (shade shl 16) or (shade shl 8) or shade
                }
            )
        )
        assertTrue(
            ChessWiki.skillProbeTextured(
                IntArray(49) {
                    val shade = 20 + (it * 37) % 215
                    0xff000000.toInt() or (shade shl 16) or (shade shl 8) or shade
                }
            )
        )
    }

    @Test fun selectionUsesOnlyChosenTabs() {
        val opts = ChessWiki.Options(setOf("truyen-thuyet", "tat-ca-quan-co"), openGame = false)
        assertEquals(listOf("tat-ca-quan-co", "truyen-thuyet"), opts.topics().map { it.key })
        assertNull(ChessWiki.detailType(opts.topics()[0], opts))
        assertEquals("quan-co", opts.topics()[0].section)
    }

    @Test fun detailsRequireExplicitOptIn() {
        val opts = ChessWiki.Options(
            setOf("tat-ca-quan-co", "phe", "tat-ca-trang-bi"),
            openGame = false,
            heroDetails = true,
            synergyDetails = true,
            equipmentDetails = true
        )
        assertEquals("tuong", ChessWiki.detailType(ChessWiki.TOPICS.first { it.key == "tat-ca-quan-co" }, opts))
        assertEquals("phe", ChessWiki.detailType(ChessWiki.TOPICS.first { it.key == "phe" }, opts))
        assertEquals("vat-pham", ChessWiki.detailType(ChessWiki.TOPICS.first { it.key == "tat-ca-trang-bi" }, opts))
    }

    @Test fun invalidSelectionsAreRejected() {
        try {
            ChessWiki.Options(emptySet())
            fail("empty selection was accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            ChessWiki.Options(setOf("unknown"))
            fail("unknown tab was accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun filenamesDescribeNestedCategoriesAndDetails() {
        val t = ChessWiki.TOPICS.first { it.key == "tat-ca-quan-co" }
        assertEquals("chess__quan-co__tat-ca-quan-co__trang__001", ChessWiki.filename(t, "trang", 1))
        assertEquals(
            "chess__quan-co__tat-ca-quan-co__tuong__violet-abc12345__002",
            ChessWiki.filename(t, "tuong", 2, "violet-abc12345")
        )
        assertEquals("quyen-truong-rhea", ChessWiki.safeName("Quyền trượng Rhea"))
    }

    @Test fun scrollChangeIgnoresSmallAnimationAndSeesMovement() {
        val base = IntArray(96) { 55 }
        assertFalse(ChessWiki.changed(base, IntArray(96) { 58 }))
        assertTrue(ChessWiki.changed(base, IntArray(96) { 61 }))
    }

    @Test fun textScrollIsNotAveragedAwayByTheUniformBackground() {
        val base = IntArray(2560) { 60 }
        val moved = base.copyOf().apply { for (i in 400 until 440) this[i] = 110 }
        assertTrue(ChessWiki.changed(base, moved))
        assertFalse(ChessWiki.changed(base, base.copyOf()))
        assertFalse(ChessWiki.changed(base, IntArray(2560) { 66 }))
    }

    @Test fun readerKeepsSecondTextPageBeforeMarkingRoundRulesFinished() {
        var scrolled = false
        val kept = ArrayList<String>()
        val done = HashSet<String>()
        var outcome: ChessWikiReader.Outcome? = null
        val screen = object : ChessWikiReader.Screen {
            override val alive = true

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                then(object : ChessWikiReader.Look {
                    override val words = listOf(GameScreens.Word("Bách khoa Cờ Liên Quân", .1, .02, .6, .1))
                    override val signature = IntArray(2560) { if (scrolled && it in 400 until 440) 110 else 60 }
                    override val modal = false

                    override fun tiles(topic: ChessWiki.Topic) = emptyList<ChessWiki.Tile>()

                    override fun keep(name: String, then: (Boolean) -> Unit) {
                        kept += name
                        then(true)
                    }

                    override fun discard() {}
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) = then(true)

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) {
                scrolled = true
                then(true)
            }

            private val clock = ChessTestClockQueue()
            override val monotonicMs get() = clock.now

            override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

            fun drain() = clock.drain()

            override fun seen(id: String) = id in done

            override fun finished(id: String) {
                done += id
            }

            override fun note(message: String) {}

            override fun say(message: String) {}
        }
        ChessWikiReader(screen, ChessWiki.Options(setOf("thang-thua-vong"), openGame = false)) { outcome = it }.start()
        screen.drain()
        assertTrue(outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("chess__thong-tin__thang-thua-vong__trang__001", "chess__thong-tin__thang-thua-vong__trang__002"), kept)
        assertTrue("topic:thang-thua-vong" in done)
    }

    @Test fun originalEquipmentImagesAreTaggedAutomaticallyFromCurrentTab() = equipmentNative(ChessWikiReader.NativeResult.SAVED)

    @Test fun verifiedAbsentEquipmentProbeKeepsCollectingWithoutMarkingASavedPhoto() = equipmentNative(ChessWikiReader.NativeResult.ABSENT)

    private fun equipmentNative(nativeResult: ChessWikiReader.NativeResult) {
        val observed = listOf(
            GameScreens.Word("Bách khoa", .15, .03, .3, .09),
            GameScreens.Word("Cờ Liên Quân", .35, .03, .6, .09)
        )
        val stored = ArrayList<String>()
        val native = ArrayList<String>()
        val finished = HashSet<String>()
        var outcome: ChessWikiReader.Outcome? = null
        val screen = object : ChessWikiReader.Screen {
            override val alive = true

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                then(object : ChessWikiReader.Look {
                    override val words = observed
                    override val signature = IntArray(96) { 65 }
                    override val modal = false

                    override fun tiles(topic: ChessWiki.Topic) = emptyList<ChessWiki.Tile>()

                    override fun nativeTiles(topic: ChessWiki.Topic) = listOf(ChessWiki.Tile(.4, .55, "icon-1"))

                    override fun keep(name: String, then: (Boolean) -> Unit) {
                        stored.add(name)
                        then(true)
                    }

                    override fun discard() {}
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) = then(true)

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

            override fun nativeSave(
                group: String,
                item: String,
                x: Double,
                y: Double,
                preserveDetail: Boolean,
                aliases: Set<String>,
                reference: NativeSaveEvidence.Reference?,
                then: (ChessWikiReader.NativeResult) -> Unit
            ) {
                native.add(group)
                then(nativeResult)
            }

            private val clock = ChessTestClockQueue()
            override val monotonicMs get() = clock.now

            override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

            fun drain() = clock.drain()

            override fun seen(id: String) = id in finished

            override fun finished(id: String) {
                finished.add(id)
            }

            override fun note(message: String) {}

            override fun say(message: String) {}
        }
        ChessWikiReader(
            screen,
            ChessWiki.Options(
                setOf("tat-ca-trang-bi"),
                openGame = false,
                nativeGroups = setOf("trang-bi")
            ),
            { outcome = it }
        ).start()
        screen.drain()
        assertTrue(outcome is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("trang-bi"), native)
        assertEquals(
            nativeResult == ChessWikiReader.NativeResult.SAVED,
            finished.any { it.startsWith("native:trang-bi:") }
        )
        assertEquals(1, stored.size)
    }

    @Test fun readerCapturesChosenPageThenStopsAtEnd() {
        val titles = listOf(
            GameScreens.Word("Bách khoa", .2, .02, .4, .08),
            GameScreens.Word("Cờ Liên Quân", .4, .03, .65, .1)
        )
        val kept = ArrayList<String>()
        val finished = HashSet<String>()
        var result: ChessWikiReader.Outcome? = null
        val screen = object : ChessWikiReader.Screen {
            override val alive = true

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                then(object : ChessWikiReader.Look {
                    override val words = titles
                    override val signature = IntArray(96) { 60 }
                    override val modal = false

                    override fun tiles(topic: ChessWiki.Topic) = emptyList<ChessWiki.Tile>()

                    override fun keep(name: String, then: (Boolean) -> Unit) {
                        kept.add(name)
                        then(true)
                    }

                    override fun discard() {}
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) = then(true)

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

            private val clock = ChessTestClockQueue()
            override val monotonicMs get() = clock.now

            override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

            fun drain() = clock.drain()

            override fun seen(id: String) = id in finished

            override fun finished(id: String) {
                finished.add(id)
            }

            override fun note(message: String) {}

            override fun say(message: String) {}
        }
        ChessWikiReader(screen, ChessWiki.Options(setOf("quy-trinh"), openGame = false), { result = it }).start()
        screen.drain()
        assertTrue(result is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("chess__thong-tin__quy-trinh__trang__001"), kept)
        assertTrue("topic:quy-trinh" in finished)
    }

    @Test fun wikiNavigationHandlesFirstChessAccountOnboardingAfterModeSelection() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .045, y - .02, x + .045, y + .02)
        val experience = listOf(
            w("Hoan nghênh bạn đến với Cờ Liên quân", .34, .22),
            w("Đã chơi game Autochess chưa", .30, .25),
            w("Tân thủ", .17, .65),
            w("Kỳ cựu", .333, .655)
        )
        val tip = listOf(
            w("Bạn có thể đấu thử trong Đấu luyện", .32, .31),
            w("nâng cao trình độ Cờ Liên quân", .31, .34),
            w("Đã rõ", .250, .577)
        )
        val wiki = listOf(w("Bách khoa", .16, .06), w("Cờ Liên Quân", .40, .06))
        var stage = 0
        var result: ChessWikiReader.Outcome? = null
        val taps = ArrayList<Pair<Double, Double>>()
        val done = HashSet<String>()
        val screen = object : ChessWikiReader.Screen {
            override val alive = true

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                then(object : ChessWikiReader.Look {
                    override val words = when (stage) {
                        0 -> experience
                        1 -> tip
                        else -> wiki
                    }
                    override val signature = IntArray(96) { 88 }
                    override val modal = false

                    override fun tiles(topic: ChessWiki.Topic) = emptyList<ChessWiki.Tile>()

                    override fun keep(name: String, then: (Boolean) -> Unit) = then(true)

                    override fun discard() {}
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                taps.add(x to y)
                stage++
                then(true)
            }

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

            private val clock = ChessTestClockQueue()
            override val monotonicMs get() = clock.now

            override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

            fun drain() = clock.drain()

            override fun seen(id: String) = id in done

            override fun finished(id: String) {
                done.add(id)
            }

            override fun note(message: String) {}

            override fun say(message: String) {}
        }
        ChessWikiReader(screen, ChessWiki.Options(setOf("quy-trinh"), openGame = false), { result = it }).start()
        screen.drain()
        assertTrue(result is ChessWikiReader.Outcome.Done)
        assertEquals(.333, taps[0].first, .001)
        assertEquals(GameScreens.CHESS_VETERAN_Y, taps[0].second, .001)
        assertEquals(.250, taps[1].first, .001)
        assertEquals(.577, taps[1].second, .001)
        assertTrue("topic:quy-trinh" in done)
    }

    @Test fun modeEntryUsesTheRealButtonEvenNextToQuest() {
        fun w(label: String, x: Double, y: Double) = GameScreens.Word(label, x - .035, y - .02, x + .035, y + .02)
        val p = ChessWiki.modeEntryPoint(
            listOf(
                w("N.vụ", .515, .941),
                w("Chọn chế độ", .634, .940),
                w("Đấu thường", .706, .941)
            )
        )
        assertNotNull(p)
        assertEquals(.634, p!!.first, .001)
        assertEquals(.895, p.second, .001)
        assertTrue(p.first > .57)
    }

    @Test fun modeEntryFallbackDoesNotReuseQuestCoordinatesWhenOcrMissesModeLabel() {
        fun w(label: String, x: Double, y: Double) = GameScreens.Word(label, x - .025, y - .025, x + .025, y + .025)
        val fromOtherLobbyWords = ChessWiki.modeEntryPoint(
            listOf(
                w("N.vụ", .515, .943),
                w("Túi đồ", .16, .943)
            )
        )
        assertNotNull(fromOtherLobbyWords)
        assertEquals(.635, fromOtherLobbyWords!!.first, .001)
        assertEquals(.895, fromOtherLobbyWords.second, .001)
    }

    @Test fun modeEntryDoesNotTapOnUnknownScreens() {
        val unrelated = listOf(
            GameScreens.Word("N.vụ", .47, .20, .53, .25),
            GameScreens.Word("Chọn", .08, .02, .14, .08)
        )
        assertNull(ChessWiki.modeEntryPoint(unrelated))
    }

    @Test fun modeNavigationHandlesSplitVietnameseOcr() {
        fun word(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .02, x + .04, y + .02)
        // The actual screenshot places "Chọn chế độ" to the right of N.vụ, not on its icon.
        val lobby = listOf(
            word("Túi đồ", .16, .942),
            word("N.vụ", .515, .94),
            word("Chọn chế độ", .635, .943)
        )
        val choose = listOf(
            word("Chế độ giải", .14, .34),
            word("trí", .14, .39),
            word("Cờ Liên Quân", .52, .83),
            word("Bắt đầu", .85, .68)
        )
        val chessLobby = listOf(word("Trang chủ", .105, .30), word("Cờ Liên Quân", .15, .05))
        val wiki = listOf(word("Bách khoa", .15, .06), word("Cờ Liên Quân", .35, .06))
        val taps = ArrayList<Pair<Double, Double>>()
        val completed = HashSet<String>()
        var stage = 0
        var outcome: ChessWikiReader.Outcome? = null
        val screen = object : ChessWikiReader.Screen {
            override val alive = true

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                then(object : ChessWikiReader.Look {
                    override val words = when (stage) {
                        0 -> lobby
                        1, 2 -> choose
                        3 -> chessLobby
                        else -> wiki
                    }
                    override val signature = IntArray(96) { 60 }
                    override val modal = false

                    override fun tiles(topic: ChessWiki.Topic) = emptyList<ChessWiki.Tile>()

                    override fun keep(name: String, then: (Boolean) -> Unit) = then(true)

                    override fun discard() {}
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                taps.add(x to y)
                if (stage < 4) stage++
                then(true)
            }

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

            private val clock = ChessTestClockQueue()
            override val monotonicMs get() = clock.now

            override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

            fun drain() = clock.drain()

            override fun seen(id: String) = id in completed

            override fun finished(id: String) {
                completed.add(id)
            }

            override fun note(message: String) {}

            override fun say(message: String) {}
        }
        ChessWikiReader(screen, ChessWiki.Options(setOf("quy-trinh"), openGame = false), { outcome = it }).start()
        screen.drain()
        assertTrue(outcome is ChessWikiReader.Outcome.Done)
        assertEquals(4, stage)
        assertEquals(ChessWiki.MODE_X, taps[0].first, .001)
        assertEquals(ChessWiki.MODE_Y, taps[0].second, .001)
        assertTrue("Must never tap N.vụ", taps[0].first > .57)
        assertEquals(.507, taps[1].first, .001)
        assertEquals(.85, taps[2].first, .05)
        assertTrue("topic:quy-trinh" in completed)
    }

    @Test fun chessHeroCardsReadNamesInsteadOfRemainingRoleTextAfterScroll() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .045, y - .014, x + .045, y + .014)
        val first = ChessWiki.heroNameTiles(
            listOf(
                w("Đấu sĩ", .421, .638), w("A Đấu sĩ", .421, .638),
                w("© Pháo thủ", .275, .638), w("Giải Điệu H...", .275, .694),
                w("4 Giai Điệu H.", .275, .694), w("9 Giai Điệu H..", .713, .694),
                w("Giai Điệu H", .713, .694), w("Mị Ảnh Dướ", .567, .694),
                w("Raz", .713, .583), w("Arduin", .421, .588),
                w("Lumburr", .858, .590), w("Hayate", .567, .580), w("Violet", .275, .586)
            )
        )
        assertEquals(listOf("Violet", "Arduin", "Hayate", "Raz", "Lumburr"), first.map { it.label })
        assertEquals(1, ChessWiki.heroRows(first).size)
        val second = ChessWiki.heroNameTiles(
            listOf(
                w("Pháo thủ", .275, .235),
                w("Đấu sĩ", .421, .235),
                w("Giai Điệu H...", .71, .280),
                w("D’Arcy", .858, .808),
                w("Helen", .567, .795),
                w("Max", .421, .804),
                w("Fennik", .275, .803),
                w("Krizzix", .713, .799)
            )
        )
        assertEquals(listOf("Fennik", "Max", "Helen", "Krizzix", "D’Arcy"), second.map { it.label })
        assertEquals("hero-fennik", second.first().id)
        assertTrue(second.all { it.y > .59 }) // never tap the cropped-off previous row
    }

    @Test fun chessHeroRowsSortByXEvenWhenOcrYIsSlightlyDifferent() {
        val first = listOf(
            ChessWiki.Tile(.567, .416, "hero-hayate", "Hayate"),
            ChessWiki.Tile(.858, .419, "hero-lumburr", "Lumburr"),
            ChessWiki.Tile(.275, .422, "hero-violet", "Violet"),
            ChessWiki.Tile(.421, .411, "hero-arduin", "Arduin"),
            ChessWiki.Tile(.713, .410, "hero-raz", "Raz")
        )
        assertEquals(
            listOf("Violet", "Arduin", "Hayate", "Raz", "Lumburr"),
            ChessWiki.heroRows(first).single().map { it.label }
        )
    }

    @Test fun chessMissingCardNameUsesRolePositionWithoutCallingTheHeroDauSi() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .045, y - .014, x + .045, y + .014)
        val cards = ChessWiki.heroNameTiles(
            listOf(
                w("Violet", .275, .584),
                w("A Đấu sĩ", .421, .638),
                w("Hayate", .567, .584)
            )
        )
        assertEquals(3, cards.size)
        assertEquals(listOf("Violet", null, "Hayate"), cards.map { it.label })
        assertEquals(.421, cards[1].x, .001)
        assertEquals(.408, cards[1].y, .001)
        assertTrue(cards[1].id.startsWith("unread-"))
    }

    @Test fun chessModalHeroTitleIgnoresSkillHeading() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .055, y - .014, x + .055, y + .014)
        val popup = listOf(w("Pháo đại", .50, .495), w("Pháo thủ", .44, .38), w("Violet", .407, .283))
        assertEquals("Violet", ChessWiki.modalHeroTitle(popup))
        assertNull(ChessWiki.modalHeroTitle(listOf(w("Pháo đại", .50, .495))))
    }

    @Test fun heroPriceOcrNeverCreatesAnotherHeroWhenContinuingOldRuns() {
        for (text in listOf("Violet 1", "Violet1", "Violet O1", "Violet 01", "Violet © 1")) {
            assertEquals("Violet", ChessWiki.heroName(text))
        }
        assertEquals("Arduin", ChessWiki.heroName("Arduin1"))
        val ids = setOf(
            "item:tat-ca-quan-co:hero-violet",
            "item:tat-ca-quan-co:hero-violet-o1",
            "item:tat-ca-quan-co:hero-arduin",
            "topic:tat-ca-quan-co"
        )
            .map { ChessWiki.heroIdentity(it) }.toSet()
        assertEquals(2, ids.count { it.startsWith("item:") })
        assertTrue("topic:tat-ca-quan-co" in ids)
        assertFalse(ChessWiki.isHeroName("4 Giai Điệu H."))
        assertFalse(ChessWiki.isHeroName("Giai Điệu H"))
    }

    @Test fun chessHeroTitlePrefersTheExpectedCardEvenWhenSkillOcrAppearsFirst() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .045, y - .014, x + .045, y + .014)
        val contradictory = listOf(
            w("Pháo đại", .49, .265), // noisy global OCR box wrongly placed in heading strip
            w("Violet 1", .400, .241),
            w("Pháo thủ", .45, .318)
        )
        assertEquals("Violet", ChessWiki.modalHeroTitle(contradictory, "Violet"))
        assertNull(ChessWiki.modalHeroTitle(listOf(w("Pháo đại", .49, .425)), "Violet"))
    }

    @Test fun chessCollectorRereadsHeaderBeforeMislabelingVioletAsSkill() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .038, y - .015, x + .038, y + .015)
        val title = listOf(w("Bách khoa", .20, .06), w("Cờ Liên Quân", .45, .06))
        val card = ChessWiki.Tile(.275, .414, "hero-violet", "Violet")
        val saved = ArrayList<String>()
        val done = HashSet<String>()
        var opened = false
        var verified = 0
        var outcome: ChessWikiReader.Outcome? = null
        val screen = object : ChessWikiReader.Screen {
            override val alive = true

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                val popup = opened
                then(object : ChessWikiReader.Look {
                    override val words = if (!words) {
                        emptyList()
                    } else if (popup) {
                        listOf(w("Pháo đại", .49, .265))
                    } else {
                        title + listOf(w("Violet", .275, .584))
                    }
                    override val signature = IntArray(96) { 42 }
                    override val modal = popup
                    override val height = 1000
                    override val heroProfile = if (popup) floatArrayOf() else oneCardProfile()

                    override fun hasHeroCard(x: Double, y: Double) =
                        !popup && kotlin.math.abs(x - .275) < .02 && kotlin.math.abs(y - .415) < .06

                    override fun tiles(topic: ChessWiki.Topic) = if (popup) {
                        emptyList()
                    } else {
                        listOf(card)
                    }

                    override fun skillIcons() = listOf(ChessWiki.Tile(.405, .525, "loaded"))

                    override fun nativeIconSignature(x: Double, y: Double) = NativePhotoReceipt.signature(1000, 640, x, y) { px, py ->
                        val value = ((px / 4 + py / 5) % 2) * 180 + 30
                        (value shl 16) or (value shl 8) or value
                    }

                    override fun keep(name: String, then: (Boolean) -> Unit) {
                        saved.add(name)
                        then(true)
                    }

                    override fun discard() {}
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                if (x > .87 && y < .13) {
                    opened = false
                } else if (kotlin.math.abs(x - card.x) < .01 && y > .30) {
                    opened = true
                }
                then(true)
            }

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

            override fun readHeroTitle(then: (String?) -> Unit) {
                verified++
                then("Violet")
            }

            private val clock = ChessTestClockQueue()
            override val monotonicMs get() = clock.now

            override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

            fun drain() = clock.drain()

            override fun seen(id: String) = id in done

            override fun finished(id: String) {
                done.add(id)
            }

            override fun note(message: String) {}

            override fun say(message: String) {}
        }
        ChessWikiReader(
            screen,
            ChessWiki.Options(
                setOf("tat-ca-quan-co"),
                openGame = false,
                heroDetails = true
            ),
            { outcome = it }
        ).start()
        screen.drain()
        assertTrue(outcome is ChessWikiReader.Outcome.Done)
        assertEquals(1, verified)
        assertTrue(saved.any { "__tuong__violet__001" in it })
        assertFalse(saved.any { "__tuong__phao-dai" in it })
        assertTrue("item:tat-ca-quan-co:hero-violet" in done)
    }

    @Test fun chessCollectorCorrectsRoleLabelFromVerifiedArduinPopupAndDeduplicates() {
        for (firstLabel in listOf("A Đấu sĩ", null)) {
            fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .015, x + .04, y + .015)
            val seen = hashSetOf("item:tat-ca-quan-co:hero-violet") // resume an existing run
            val saved = ArrayList<String>()
            val messages = ArrayList<String>()
            var opened = false
            var page = 1
            var reads = 0
            var outcome: ChessWikiReader.Outcome? = null
            val screen = object : ChessWikiReader.Screen {
                override val alive = true
                override val completedHeroes get() = seen.count { it.startsWith("item:tat-ca-quan-co:hero-") }

                override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                    val modal = opened
                    then(object : ChessWikiReader.Look {
                        override val words = if (!words) {
                            emptyList()
                        } else if (modal) {
                            listOf(w("Arduin", .41, .267))
                        } else {
                            listOf(
                                w("Bách khoa", .20, .06),
                                w("Cờ Liên Quân", .45, .06)
                            )
                        }
                        override val signature = IntArray(96) { page * 42 }
                        override val modal = modal
                        override val height = 1000
                        override val heroProfile = if (modal) floatArrayOf() else oneCardProfile()

                        override fun hasHeroCard(x: Double, y: Double) =
                            !modal && kotlin.math.abs(x - .275) < .02 && kotlin.math.abs(y - .415) < .06

                        // Simulate an old/noisy card detection, then another OCR spelling after scrolling.
                        override fun tiles(topic: ChessWiki.Topic) = listOf(
                            ChessWiki.Tile(
                                .275,
                                .408,
                                if (page == 1) "unread-0-408" else "hero-ardvin",
                                if (page == 1) firstLabel else "Ardvin"
                            )
                        )

                        override fun skillIcons() = listOf(ChessWiki.Tile(.405, .525, "loaded"))

                        override fun nativeIconSignature(x: Double, y: Double) = NativePhotoReceipt.signature(1000, 640, x, y) { px, py ->
                            val value = ((px / 4 + py / 5) % 2) * 180 + 30
                            (value shl 16) or (value shl 8) or value
                        }

                        override fun keep(name: String, then: (Boolean) -> Unit) {
                            saved.add(name)
                            then(true)
                        }

                        override fun discard() {}
                    })
                }

                override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                    if (x > .87 && y < .13) {
                        opened = false
                    } else if (kotlin.math.abs(x - .275) < .01 && y > .30) {
                        opened = true
                    }
                    then(true)
                }

                override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) {
                    then(true)
                }

                override fun readHeroTitle(then: (String?) -> Unit) {
                    reads++
                    then("Arduin")
                }

                private val clock = ChessTestClockQueue()
                override val monotonicMs get() = clock.now

                override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

                fun drain() = clock.drain()

                override fun seen(id: String) = id in seen

                override fun finished(id: String) {
                    seen.add(id)
                }

                override fun note(message: String) {}

                override fun say(message: String) {
                    messages.add(message)
                }
            }
            ChessWikiReader(
                screen,
                ChessWiki.Options(
                    setOf("tat-ca-quan-co"),
                    openGame = false,
                    heroDetails = true
                ),
                { outcome = it }
            ).start()
            screen.drain()
            assertEquals(ChessWikiReader.Outcome.Done(2, 1), outcome)
            assertEquals(2, reads) // one opening; two matching reads resolve the conflicting label
            assertEquals(1, saved.count { "__tuong__arduin__001" in it })
            assertFalse(saved.any { "__tuong__a-dau-si" in it || "__tuong__ardvin" in it })
            assertEquals(
                setOf("item:tat-ca-quan-co:hero-violet", "item:tat-ca-quan-co:hero-arduin"),
                seen.filter { it.startsWith("item:") }.toSet()
            )
            assertTrue(messages.any { it.contains("đã lưu 1 tướng") && it.contains("tổng chưa xác định") })
            assertTrue(messages.any { it.contains("đã lưu 2 tướng") && it.contains("tổng chưa xác định") })
            assertFalse(messages.any { it.contains("/1") || it.contains("đã lưu 3 tướng") })
        }
    }

    @Test fun chessCollectorKeepsRowsButDoesNotGuessWhenCroppedTitlesAreUnreliable() {
        for (readings in listOf(
            listOf(null, null, null),
            listOf("Raz", "Arduin", "Raz"),
            listOf("A Đấu sĩ", "A Đấu sĩ", "A Đấu sĩ")
        )) {
            fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .015, x + .04, y + .015)
            val saved = ArrayList<String>()
            val done = HashSet<String>()
            var opened = false
            var reads = 0
            var outcome: ChessWikiReader.Outcome? = null
            val screen = object : ChessWikiReader.Screen {
                override val alive = true

                override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                    val popup = opened
                    then(object : ChessWikiReader.Look {
                        override val words = if (!words || popup) {
                            emptyList()
                        } else {
                            listOf(
                                w("Bách khoa", .20, .06),
                                w("Cờ Liên Quân", .45, .06)
                            )
                        }
                        override val signature = IntArray(96) { 42 }
                        override val modal = popup
                        override val height = 1000
                        override val heroProfile = if (popup) floatArrayOf() else oneCardProfile()

                        override fun hasHeroCard(x: Double, y: Double) =
                            !popup && kotlin.math.abs(x - .275) < .02 && kotlin.math.abs(y - .415) < .06

                        override fun tiles(topic: ChessWiki.Topic) = listOf(
                            ChessWiki.Tile(.275, .408, "hero-violet", "Violet")
                        )

                        override fun skillIcons() = listOf(ChessWiki.Tile(.405, .525, "loaded"))

                        override fun nativeIconSignature(x: Double, y: Double) = NativePhotoReceipt.signature(1000, 640, x, y) { px, py ->
                            val value = ((px / 4 + py / 5) % 2) * 180 + 30
                            (value shl 16) or (value shl 8) or value
                        }

                        override fun keep(name: String, then: (Boolean) -> Unit) {
                            saved.add(name)
                            then(true)
                        }

                        override fun discard() {}
                    })
                }

                override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                    if (kotlin.math.abs(x - .275) < .01 && y > .30) opened = true
                    then(true)
                }

                override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) = then(true)

                override fun readHeroTitle(then: (String?) -> Unit) = then(readings[reads++])

                private val clock = ChessTestClockQueue()
                override val monotonicMs get() = clock.now

                override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

                fun drain() = clock.drain()

                override fun seen(id: String) = id in done

                override fun finished(id: String) {
                    done.add(id)
                }

                override fun note(message: String) {}

                override fun say(message: String) {}
            }
            ChessWikiReader(
                screen,
                ChessWiki.Options(
                    setOf("tat-ca-quan-co"),
                    openGame = false,
                    heroDetails = true
                ),
                { outcome = it }
            ).start()
            screen.drain()
            assertTrue(outcome is ChessWikiReader.Outcome.Failed)
            assertEquals(3, reads)
            assertTrue(saved.any { "__trang__" in it })
            assertFalse(saved.any { "__hang__" in it })
            assertFalse(saved.any { "__tuong__" in it })
            assertFalse(done.any { it.startsWith("item:") })
        }
    }
}
