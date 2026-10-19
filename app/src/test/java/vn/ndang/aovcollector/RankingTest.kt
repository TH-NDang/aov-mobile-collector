package vn.ndang.aovcollector

import java.io.InputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ndang.aovcollector.RankScreens.Mode
import vn.ndang.aovcollector.RankScreens.Page
import vn.ndang.aovcollector.RankScreens.Table

class RankingTest {
    /** Screenshots from the user's phone (2400x1080), the UID line and other players' chat blanked. */
    private fun pixels(name: String): Triple<Int, Int, IntArray> {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/rank-$name.jpg")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java).invoke(null, it)!!
        }
        val type = image.javaClass
        val w = type.getMethod("getWidth").invoke(image) as Int
        val h = type.getMethod("getHeight").invoke(image) as Int
        val px = type.getMethod(
            "getRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        ).invoke(image, 0, 0, w, h, null, 0, w) as IntArray
        return Triple(w, h, px)
    }

    private fun rows(p: Triple<Int, Int, IntArray>) = { y: Int, row: IntArray -> System.arraycopy(p.third, y * p.first, row, 0, p.first) }

    @Test fun theLobbyIsKnownBeforeTappingTheButtonBesideTúiĐồ() {
        // The live game, and a test build whose button shows another icon in the same place.
        for (name in listOf("lobby", "lobby-rd")) {
            val p = pixels(name)
            assertEquals(name, GameScreens.Kind.LOBBY, GameScreens.read(p.first, p.second, rows(p)).kind)
        }
        val table = pixels("ranking-rates")
        assertTrue(GameScreens.read(table.first, table.second, rows(table)).kind != GameScreens.Kind.LOBBY)
    }

    @Test fun aScrollThatMovesNothingIsTold() {
        val rates = pixels("ranking-rates")
        val figures = pixels("ranking-figures")
        val a = RankScreens.signature(rates.first, rates.second, rows(rates))
        assertTrue(RankScreens.same(a, RankScreens.signature(rates.first, rates.second, rows(rates))))
        assertFalse(RankScreens.same(a, RankScreens.signature(figures.first, figures.second, rows(figures))))
        // The same table one row further down.
        val shifted = Triple(
            rates.first,
            rates.second,
            IntArray(rates.third.size) {
                val y =
                    it / rates.first + 133
                if (y < rates.second) rates.third[y * rates.first + it % rates.first] else 0
            }
        )
        assertFalse(RankScreens.same(a, RankScreens.signature(shifted.first, shifted.second, rows(shifted))))
    }

    private fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .05, y - .02, x + .05, y + .02)

    @Test fun readsTheTableAndTheKindOfMatchFromTheirWords() {
        // Words as ML Kit reads them on the user's screenshots, on the phone of the CI emulator.
        val rates =
            listOf(
                w("< Bảng Phân Bậc Tướng O", .217, .08),
                w("Đấu hạng (Tất cả) v", .567, .202),
                w("Đổi bảng xếp hạng", .835, .202),
                w("Tỷ lệ thẩng *", .454, .319),
                w("Tý lệ pick +", .564, .317)
            )
        assertEquals(Page.RANKING, RankScreens.page(rates, false))
        assertEquals(Table.RATES, RankScreens.table(rates))
        assertEquals(Mode.ALL, RankScreens.mode(RankScreens.modeButton(rates)))
        val figures = listOf(w("Đấu 5v5", .57, .2), w("Sát thương", .48, .29), w("mỗi trận", .48, .33), w("Vàng", .69, .32))
        assertEquals(Table.FIGURES, RankScreens.table(figures))
        assertEquals(Mode.FIVE, RankScreens.mode(RankScreens.modeButton(figures)))
        val open = rates + listOf(
            w("Đấu hạng (Tất cả)", .563, .314),
            w("Đấu 5v5", .53, .376),
            w("124.2 Đấu hang (Tinh Anh trở lên)", .565, .441)
        )
        val button = RankScreens.modeButton(open)!!
        assertEquals(.202, button.y, .001)
        assertEquals(.441, RankScreens.option(open, button, Mode.ELITE)!!.y, .001)
        assertEquals(.376, RankScreens.option(open, button, Mode.FIVE)!!.y, .001)
        assertEquals(Page.HEROES, RankScreens.page(listOf(w("Bảng xếp hạng đầy đủ >", .485, .082)), false))
        assertEquals(Page.COMMUNITY, RankScreens.page(listOf(w("uÊNQUÂN", .083, .905), w("MIC CHECK #6: GAM - FPL", .293, .085)), false))
        assertEquals(.081, RankScreens.logo(listOf(w("UÊNGUÂN", .081, .904)))!!.x, .001)
        assertEquals(Page.COMMUNITY, RankScreens.page(listOf(w("S Garena Liên Quân Mobile", .232, .409)), false))
        assertEquals(Page.PANEL, RankScreens.page(listOf(w("Tính năng khác", .115, .219), w("Cộng đòng", .154, .521)), false))
        assertEquals(Page.LOBBY, RankScreens.page(emptyList(), true))
    }

    private fun landing() = listOf(w("Bảng Phân Bậc Tướng", .25, .08), w("LIÊNQUÂN", .083, .9), w("Bảng xếp hạng tỷ lệ thắng", .27, .22))

    private fun box(text: String, left: Double, top: Double, right: Double, bottom: Double) =
        GameScreens.Word(text, left, top, right, bottom)

    private fun link() = box("Bảng xếp hạng đầy đủ >", .406, .064, .567, .101)

    private fun parent() = box("Bảng Phân Bậc Tướng Bảng xếp hạng đầy đủ Tỷ lệ thắng Đánh giá", .03, .02, .95, .98)

    private fun splitLink() = listOf(
        box("đủ >", .548, .065, .567, .101),
        box("Bảng", .406, .064, .438, .101),
        box("xếp hạng", .443, .064, .509, .101),
        box("đầy", .516, .065, .543, .101)
    )

    @Test fun fullRankingLinkPrefersTheTightLabelOverAnAggregatedAccessibilityParent() {
        val tight = link()
        assertEquals(tight, RankScreens.fullRankingLink(listOf(parent()) + landing() + tight))
        assertEquals(Page.HEROES, RankScreens.page(listOf(parent()), false))
        assertNull(RankScreens.fullRankingLink(listOf(parent())))
        assertTrue(RankScreens.needsFullRankingOcr(listOf(parent())))
        val broad = box("Bảng xếp hạng đầy đủ", .32, .025, .59, .145)
        assertEquals(tight, RankScreens.fullRankingLink(listOf(broad, tight)))
        val headerParent = box("Bảng xếp hạng đầy đủ", .36, .045, .60, .115)
        assertEquals(tight, RankScreens.fullRankingLink(listOf(headerParent, tight)))
    }

    @Test fun joinsAdjacentHeaderFragmentsAndKeepsTheTapInsideTheirVisibleBounds() {
        val target = RankScreens.fullRankingLink(landing() + splitLink())!!
        assertTrue(target.x in .406..0.567)
        assertTrue(target.y in .064..0.101)
        assertEquals(Page.HEROES, RankScreens.page(landing() + splitLink(), false))
        // A useful surviving suffix is enough in this header, including missing Vietnamese accents.
        val suffix = box("xép hạng đẩy đủ >", .443, .064, .567, .101)
        assertEquals(suffix, RankScreens.fullRankingLink(listOf(suffix)))
        val end = box("đầy đủ >", .516, .064, .567, .101)
        assertNull(RankScreens.fullRankingLink(listOf(end)))
        assertEquals(end, RankScreens.fullRankingLink(landing() + end))
        val short = box("hạng đầy đủ", .48, .064, .567, .101)
        assertNull(RankScreens.fullRankingLink(listOf(short)))
        assertEquals(short, RankScreens.fullRankingLink(landing() + short))
    }

    @Test fun aMissingGoldLinkDoesNotMisclassifyTheHeroLandingPageAsCommunity() {
        assertEquals(Page.HEROES, RankScreens.page(landing(), false))
        assertNull(RankScreens.fullRankingLink(landing()))
        assertTrue(RankScreens.needsFullRankingOcr(landing()))
        assertFalse(RankScreens.needsFullRankingOcr(listOf(w("Đổi bảng xếp hạng", .835, .2))))
    }

    @Test fun rejectsAbsentAmbiguousMisplacedAndDisconnectedLinkText() {
        assertNull(RankScreens.fullRankingLink(emptyList()))
        assertNull(RankScreens.fullRankingLink(landing() + box("Bảng xếp hạng", .406, .064, .509, .101)))
        for (bad in listOf(
            box("Bảng xếp hạng đầy đủ", .4, .4, .57, .44),
            box("Bảng xếp hạng đầy đủ", -.1, .06, .57, .1),
            box("Bảng xếp hạng đầy đủ", .4, .06, 1.2, .1),
            box("Bảng xếp hạng đầy đủ", Double.NaN, .06, .57, .1)
        )) {
            assertNull(RankScreens.fullRankingLink(listOf(bad)))
        }
        val second = box("Bảng xếp hạng đầy đủ", .59, .064, .69, .101)
        assertNull(RankScreens.fullRankingLink(listOf(link(), second)))
        assertNull(RankScreens.fullRankingLink(listOf(link(), box("Bảng xếp hạng đầy đủ", .55, .064, .69, .101))))
        // A title and a separate article suffix must never be joined into a link.
        assertNull(RankScreens.fullRankingLink(listOf(box("Bảng xếp hạng", .32, .06, .43, .1), box("đầy đủ", .59, .06, .65, .1))))
        assertNull(RankScreens.fullRankingLink(listOf(box("Bảng xếp hạng", .40, .03, .51, .06), box("đầy đủ", .51, .11, .57, .145))))
    }

    /**
     * The game from the lobby to the table: taps move between pages, the table scrolls by [length] screens and
     * keeps its headings (and the switches above) only at the top unless [sticky].
     */
    private class Web(
        var page: Page,
        var table: Table = Table.RATES,
        var mode: Mode = Mode.ALL,
        val length: Double = 1.0,
        val sticky: Boolean = false,
        val cut: Int = Int.MAX_VALUE,
        var stalls: Int = 0,
        var heroWords: List<GameScreens.Word>? = null,
        var ignoredLinkTaps: Int = 0,
        var sideWorks: Boolean = true,
        val reading: ((List<GameScreens.Word>, Int) -> List<GameScreens.Word>)? = null,
        val failSaveAt: Int = 0,
        val pauseOnSave: Boolean = false,
        var ignoredModeTaps: Int = 0,
        val popupAfterSave: Boolean = false
    ) : RankingReader.Screen {
        var offset = 0.0
        var open = false
        var looks = 0
        var linkTaps = 0
        var sideTaps = 0
        var released = 0
        var wordLooks = 0
        var saves = 0
        var paused = false
        val tapPoints = ArrayList<Pair<Double, Double>>()
        val notes = ArrayList<String>()
        val savedMenus = ArrayList<Boolean>()
        val savedFrames = ArrayList<RankScreens.Combo>()
        val savedAtLooks = ArrayList<Int>()
        val drags = ArrayList<Double>()
        val kept = ArrayList<String>()
        val tapped = ArrayList<String>()
        val finished = ArrayList<String>()
        val pending = ArrayDeque<() -> Unit>()
        var outcome: RankingReader.Outcome? = null
        override val alive get() = !paused && outcome == null && looks < cut

        private fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .05, y - .02, x + .05, y + .02)

        fun words(): List<GameScreens.Word> = when (page) {
            Page.PANEL -> listOf(w("Tính năng khác", .12, .21), w("Hiệu ứng", .15, .37), w("Clan", .32, .37), w("Cộng đồng", .15, .52))
            Page.COMMUNITY -> listOf(w("MIC CHECK #6", .3, .09), w("LIÊNQUÂN", .083, .9))
            Page.HEROES -> heroWords ?: listOf(
                w("Bảng Phân Bậc Tướng", .25, .08),
                w("Bảng xếp hạng đầy đủ >", .485, .082),
                w("LIÊNQUÂN", .083, .9)
            )
            Page.RANKING -> buildList {
                if (offset == 0.0 || sticky) {
                    add(w("Bảng Phân Bậc Tướng", .22, .077))
                    add(w("Đổi bảng xếp hạng", .835, .2))
                    add(w(mode.label, .57, .2))
                    if (table ==
                        Table.RATES
                    ) {
                        add(w("Tỷ lệ thắng", .45, .32))
                        add(w("Tỷ lệ pick", .56, .32))
                    } else {
                        add(w("Sát thương", .48, .29))
                        add(w("Vàng", .69, .32))
                    }
                    if (open) Mode.values().forEachIndexed { i, m -> add(w(m.label, .6, .313 + .065 * i)) }
                }
                add(w("Omega", .13, .41))
            }
            else -> emptyList()
        }

        override fun look(words: Boolean, then: (RankingReader.Look?) -> Unit) {
            looks++
            val shown = if (words) {
                wordLooks++
                reading?.invoke(words(), wordLooks) ?: words()
            } else {
                emptyList()
            }
            val frameCombo = RankScreens.Combo(table, mode)
            val atLook = looks
            val menuInFrame = open
            val signature = IntArray(8) { (offset * 1000).toInt() + table.ordinal * 5000 + mode.ordinal * 20000 }
            then(object : RankingReader.Look {
                override val words = shown
                override val lobby = page == Page.LOBBY
                override val signature = signature
                override val menuOverlay = menuInFrame
                var consumed = false

                fun consume() {
                    assertFalse("A frame must be consumed exactly once", consumed)
                    consumed = true
                    released++
                }

                override fun done(keep: String?) {
                    consume()
                    if (keep != null) kept += keep
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    consume()
                    saves++
                    pending.addLast {
                        val ok = saves != failSaveAt
                        if (ok) {
                            kept += name
                            savedFrames += frameCombo
                            savedAtLooks += atLook
                            savedMenus += menuInFrame
                        }
                        if (pauseOnSave) paused = true
                        if (popupAfterSave) open = true
                        then(ok)
                    }
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            tapPoints += x to y
            val hit = words().firstOrNull { abs(it.x - x) < .001 && abs(it.y - y) < .001 }
            tapped += hit?.text ?: "%.3f,%.3f".format(x, y)
            when (page) {
                Page.LOBBY -> if (x == RankScreens.MORE_X && y == RankScreens.MORE_Y) page = Page.PANEL
                Page.PANEL -> if (hit?.plain == "congdong") page = Page.COMMUNITY
                Page.COMMUNITY -> if (abs(y - RankScreens.SIDE_Y) < .001) {
                    sideTaps++
                    if (sideWorks) page = Page.HEROES
                }
                Page.HEROES -> if (if (heroWords ==
                        null
                    ) {
                        hit?.plain?.contains("bangxephangdaydu") == true
                    } else {
                        x in .406..0.567 && y in .064..0.101
                    }
                ) {
                    linkTaps++
                    if (ignoredLinkTaps > 0) ignoredLinkTaps-- else page = Page.RANKING
                }
                Page.RANKING -> when {
                    hit == null -> Unit
                    hit.plain.contains("doibangxephang") -> table = if (table == Table.RATES) Table.FIGURES else Table.RATES
                    hit.y > .25 && open -> {
                        mode = Mode.values().first { hit.plain.contains(it.key) }
                        open = false
                    }
                    hit.plain.startsWith("dau") -> if (ignoredModeTaps > 0) ignoredModeTaps-- else open = !open
                }
                else -> Unit
            }
            pending.addLast { then(true) }
        }

        override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
            drags += d
            // A page loading more rows ignores [stalls] scrolls past its first screen, as on the user's phone.
            if (page ==
                Page.RANKING
            ) {
                offset =
                    when {
                        d < 0 -> maxOf(0.0, offset - 3)
                        offset > .5 && stalls > 0 -> {
                            stalls--
                            offset
                        } else -> minOf(length, offset + d)
                    }
            }
            pending.addLast { then(true) }
        }

        override fun later(ms: Long, then: () -> Unit) {
            pending.addLast(then)
        }

        override fun finished(combo: String) {
            finished += combo
        }

        override fun note(text: String) {
            notes += text
        }

        fun run(skip: Set<String> = emptySet(), wanted: List<RankScreens.Combo> = RankScreens.COMBOS): Web {
            RankingReader(this, skip, wanted) {
                outcome =
                    it
            }.start()
            var guard = 0
            while (outcome == null && looks < cut) {
                (pending.removeFirstOrNull() ?: break).invoke()
                check(++guard < 100000)
            }
            assertEquals("Every acquired frame must be consumed", looks, released)
            return this
        }
    }

    @Test fun alreadyOpenMenuSelectsTheWantedModeWithoutTogglingItClosed() {
        val combo = RankScreens.Combo(Table.RATES, Mode.FIVE)
        val web = Web(Page.RANKING).apply { open = true }.run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf(Mode.FIVE.label), web.tapped)
        assertTrue(web.savedFrames.all { it == combo })
        assertTrue(web.savedMenus.none { it })
        assertTrue(web.notes.any { it.contains("Bấm ${Mode.FIVE.label}") })
    }

    @Test fun evenTheAlreadySelectedModeMustCloseItsMenuBeforeAnyCapture() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ALL)
        val web = Web(Page.RANKING).apply { open = true }.run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf(Mode.ALL.label), web.tapped)
        assertTrue(web.savedMenus.none { it })
        assertTrue(web.savedAtLooks.first() > 1)
    }

    @Test fun missingFirstMenuOcrFrameIsRereadWithoutBlindClosingAndReopening() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ELITE)
        val web = Web(Page.RANKING, reading = { words, n ->
            if (n == 2) words.filterNot { it.y > .27 && it.plain.startsWith("dau") } else words
        })
            .run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf(Mode.ALL.label, Mode.ELITE.label), web.tapped)
        assertTrue(web.savedFrames.all { it == combo })
        assertTrue(web.savedMenus.none { it })
        assertTrue(web.notes.any { it.contains("mục thấy=không có") })
    }

    @Test fun ignoredOpeningTapStopsWithActionableModeFailureInsteadOfToggling() {
        val web = Web(Page.RANKING, ignoredModeTaps = 1).run(wanted = listOf(RankScreens.Combo(Table.RATES, Mode.FIVE)))
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertEquals(listOf(Mode.ALL.label), web.tapped)
        assertEquals(1 + RankingReader.MAX_MENU_READS, web.looks)
        assertTrue(web.kept.isEmpty())
        assertTrue(web.finished.isEmpty())
        assertTrue((web.outcome as RankingReader.Outcome.Failed).reason.contains(Mode.FIVE.label))
    }

    @Test fun permanentlyUnreadableOrPartialMenuIsBoundedAndNeverCaptured() {
        for (onlyWanted in listOf(false, true)) {
            val web = Web(Page.RANKING, reading = { words, n ->
                if (n > 1) {
                    words.filterNot {
                        it.y > .27 && it.plain.startsWith("dau") && (!onlyWanted || RankScreens.mode(it) != Mode.FIVE)
                    }
                } else {
                    words
                }
            }).run(wanted = listOf(RankScreens.Combo(Table.RATES, Mode.FIVE)))
            assertTrue(web.outcome is RankingReader.Outcome.Failed)
            assertEquals(listOf(Mode.ALL.label), web.tapped)
            assertTrue(web.kept.isEmpty())
            assertTrue(web.finished.isEmpty())
            assertEquals(1 + RankingReader.MAX_MENU_READS, web.looks)
        }
    }

    @Test fun pauseDuringMenuReadResumesFromAnAlreadyOpenDropdown() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ELITE)
        val stopped = Web(Page.RANKING, cut = 2).run(wanted = listOf(combo))
        assertNull(stopped.outcome)
        assertTrue(stopped.open)
        assertTrue(stopped.kept.isEmpty())
        val resumed = Web(Page.RANKING).apply { open = true }.run(wanted = listOf(combo))
        assertTrue(resumed.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf(Mode.ELITE.label), resumed.tapped)
        assertTrue(resumed.savedMenus.none { it })
        assertTrue(resumed.savedFrames.all { it == combo })
    }

    @Test fun selectedModeMustBeReconfirmedAfterPickingAndCannotUseStaleWords() {
        val web = Web(Page.RANKING, reading = { words, n -> if (n >= 3) emptyList() else words })
            .run(wanted = listOf(RankScreens.Combo(Table.RATES, Mode.FIVE)))
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertEquals(listOf(Mode.ALL.label, Mode.FIVE.label), web.tapped)
        assertTrue(web.kept.isEmpty())
        assertTrue(web.finished.isEmpty())
    }

    @Test fun pixelMenuVetoStopsSavingWhenEveryOptionWasMissedButModeAlreadyMatches() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ALL)
        val recovered = Web(Page.RANKING, reading = { words, n ->
            if (n == 1) {
                words.filterNot {
                    it.y > .27 && it.plain.startsWith("dau")
                }
            } else {
                words
            }
        }).apply { open = true }.run(wanted = listOf(combo))
        assertTrue(recovered.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf(Mode.ALL.label), recovered.tapped)
        assertTrue(recovered.savedMenus.none { it })
        assertTrue(recovered.savedAtLooks.first() > 2)
        val unreadable = Web(Page.RANKING, reading = { words, _ ->
            words.filterNot {
                it.y > .27 && it.plain.startsWith("dau")
            }
        }).apply { open = true }.run(wanted = listOf(combo))
        assertTrue(unreadable.outcome is RankingReader.Outcome.Failed)
        assertTrue(unreadable.kept.isEmpty())
        assertTrue(unreadable.tapped.isEmpty())
    }

    @Test fun menuAppearingDuringScrollingCannotBeSavedOrCountedAsTheEndOfTheTable() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ALL)
        val web = Web(Page.RANKING, popupAfterSave = true).run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertEquals(listOf(combo), web.savedFrames)
        assertEquals(listOf(false), web.savedMenus)
        assertTrue(web.finished.isEmpty())
        assertTrue((web.outcome as RankingReader.Outcome.Failed).reason.contains("đang che bảng"))
    }

    @Test fun pixelMenuVetoUsesRectangleContrastNotLettersOrAbsoluteScreenBrightness() {
        val width = 600
        val height = 270
        for (dx in listOf(-.025, 0.0, .025)) {
            for (shade in listOf(0, 20)) {
                fun color(r: Int, g: Int, b: Int) = ((r + shade) shl 16) or ((g + shade) shl 8) or (b + shade)
                for (open in listOf(false, true)) {
                    val px = IntArray(width * height) { i ->
                        val x = (i % width).toDouble() / width
                        val y = (i / width).toDouble() / height
                        if (open && x in (.484 + dx)..(.706 + dx) && y in .265..0.497) color(43, 47, 118) else color(65, 71, 165)
                    }
                    assertEquals(
                        "open=$open dx=$dx shade=$shade",
                        open,
                        RankScreens.menuOverlay(width, height) { y, row ->
                            System.arraycopy(
                                px,
                                y * width,
                                row,
                                0,
                                width
                            )
                        }
                    )
                }
            }
        }
        for (name in listOf("ranking-rates", "ranking-figures")) {
            val p = pixels(name)
            assertFalse(name, RankScreens.menuOverlay(p.first, p.second, rows(p)))
        }
    }

    @Test fun allSixSelectionsHandleAlreadyOpenMenusAndShiftedControls() {
        for (combo in RankScreens.COMBOS) {
            val web = Web(Page.RANKING, table = combo.table, mode = Mode.ALL, length = 0.0).apply {
                open = true
            }.run(wanted = listOf(combo))
            assertTrue(combo.label, web.outcome is RankingReader.Outcome.Done)
            assertEquals(listOf(combo), web.savedFrames)
            assertEquals(listOf(false), web.savedMenus)
            assertEquals(listOf(combo.slug), web.finished)
            for (dx in listOf(-.025, .025)) {
                val shifted = web.words().map { box(it.text, it.left + dx, it.top, it.right + dx, it.bottom) }
                assertEquals(combo.mode, RankScreens.mode(RankScreens.modeButton(shifted)))
                assertEquals(combo.table, RankScreens.table(shifted))
            }
        }
    }

    @Test fun controlCropCanRecoverUnreadableModeAndSwitchWithoutMisreadingLanding() {
        val withoutControls = listOf(w("Bảng Phân Bậc Tướng", .22, .08), w("Tỷ lệ pick", .56, .32))
        assertTrue(RankScreens.needsModeOcr(withoutControls))
        assertFalse(RankScreens.needsModeOcr(landing()))
        assertFalse(RankScreens.needsModeOcr(listOf(w("Đấu 5v5", .55, .2))))
    }

    @Test fun reconstructsModeAndOptionFromAdjacentOcrElementsWithoutJoiningRows() {
        val buttonParts = listOf(
            box("Đấu", .497, .185, .525, .222),
            box("hạng", .530, .185, .562, .222),
            box("(Tất", .567, .185, .593, .222),
            box("cả)", .598, .185, .616, .222)
        )
        val options = listOf(
            box("Đấu", .50, .422, .525, .46),
            box("hạng", .530, .422, .562, .46),
            box("(Tinh", .568, .422, .60, .46),
            box("Anh", .605, .422, .634, .46),
            box("trở lên)", .64, .422, .69, .46),
            box("Đấu", .50, .36, .525, .40),
            box("5v5", .53, .36, .557, .40)
        )
        val words = buttonParts + options
        val button = RankScreens.modeButton(words)!!
        assertEquals(Mode.ALL, RankScreens.mode(button))
        val option = RankScreens.option(words, button, Mode.ELITE)!!
        assertEquals(Mode.ELITE, RankScreens.mode(option))
        assertTrue(option.x in .50..0.69)
        assertTrue(option.y in .422..0.46)
        assertEquals(listOf(Mode.FIVE, Mode.ELITE), RankScreens.menuModes(words, button))
        assertNull(RankScreens.option(buttonParts + options.filterNot { it.text == "Anh" }, button, Mode.ELITE))
        val diagnostic = RankScreens.selectionSummary(words + box("UID: 123456789", .3, .95, .7, .99))
        assertFalse(diagnostic.contains("123456789"))
        assertFalse(diagnostic.contains("UID"))
    }

    @Test fun aVerifiedSelectedTableIsSavedBeforeAnotherOcrFrameCanLoseTheSwitch() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ALL)
        val web = Web(Page.RANKING, reading = { words, n ->
            if (n % 2 == 0) words.filterNot { it.plain.contains("doibangxephang") } else words
        })
            .run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(1, web.wordLooks)
        assertEquals(1, web.savedAtLooks.first())
        assertEquals(List(4) { combo }, web.savedFrames)
        assertTrue(web.tapped.isEmpty())
    }

    @Test fun readableTableAndModeCanRecoverWhenOnlyTheSwitchLabelIsMissing() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ALL)
        val web = Web(Page.RANKING, reading = { words, _ -> words.filterNot { it.plain.contains("doibangxephang") } })
            .run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(4, web.kept.size)
        assertTrue(web.tapped.isEmpty())
    }

    @Test fun changingRecognitionDuringPreparationIsBoundedAndCannotSaveAnUnverifiedTable() {
        val wanted = listOf(RankScreens.Combo(Table.FIGURES, Mode.ALL))
        val web = Web(Page.RANKING, reading = { words, n ->
            if (n % 2 == 0) {
                emptyList()
            } else {
                words.filterNot {
                    it.plain == "vang" || it.plain.contains("satthuong") || it.plain.contains("tyle")
                }
            }
        }).run(wanted = wanted)
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertTrue(web.looks <= RankingReader.MAX_TRIES + 2)
        assertTrue(web.kept.isEmpty())
        assertTrue(web.finished.isEmpty())
        assertTrue(web.tapped.isEmpty())
    }

    @Test fun afterSwitchingNeverSavesTheOldVerifiedTableWhenTheNewPageCannotBeRead() {
        val web = Web(Page.RANKING, reading = { words, n -> if (n == 1) words else emptyList() })
            .run(wanted = listOf(RankScreens.Combo(Table.FIGURES, Mode.ALL)))
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertEquals(listOf("Đổi bảng xếp hạng"), web.tapped)
        assertTrue(web.looks <= RankingReader.MAX_TRIES + 2)
        assertTrue(web.kept.isEmpty())
        assertTrue(web.finished.isEmpty())
    }

    @Test fun pauseDuringPreparationDoesNotSaveAndResumeVerifiesTheSelectionAgain() {
        val combo = RankScreens.Combo(Table.FIGURES, Mode.FIVE)
        val stopped = Web(Page.RANKING, cut = 2).run(wanted = listOf(combo))
        assertNull(stopped.outcome)
        assertTrue(stopped.kept.isEmpty())
        assertTrue(stopped.finished.isEmpty())
        val resumed = Web(Page.RANKING, Table.RATES, Mode.ALL).run(wanted = listOf(combo))
        assertTrue(resumed.outcome is RankingReader.Outcome.Done)
        assertEquals(List(4) { combo }, resumed.savedFrames)
    }

    @Test fun temporaryUnreadableSecondTableDoesNotRecaptureTheFinishedFirstTable() {
        val first = RankScreens.Combo(Table.RATES, Mode.ALL)
        val second = RankScreens.Combo(Table.FIGURES, Mode.ALL)
        val web = Web(Page.RANKING, reading = { words, n -> if (n == 2 || n == 3) emptyList() else words })
            .run(wanted = listOf(first, second))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf(first.slug, second.slug), web.finished)
        assertEquals(List(4) { first } + List(4) { second }, web.savedFrames)
        assertEquals(8, web.kept.distinct().size)
    }

    @Test fun aTemporaryUnreadableFrameAfterSwitchingRecoversWithoutRestartingNavigation() {
        val combo = RankScreens.Combo(Table.FIGURES, Mode.ALL)
        val web = Web(Page.RANKING, reading = { words, n -> if (n == 2) emptyList() else words }).run(wanted = listOf(combo))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf("Đổi bảng xếp hạng"), web.tapped)
        assertEquals(List(4) { combo }, web.savedFrames)
    }

    @Test fun failedPngSaveStopsBeforeScrollingOrCompletingTheTable() {
        for (failAt in listOf(1, 2)) {
            val web = Web(Page.RANKING, failSaveAt = failAt).run(wanted = listOf(RankScreens.Combo(Table.RATES, Mode.ALL)))
            assertTrue(web.outcome is RankingReader.Outcome.Failed)
            assertEquals(failAt - 1, web.kept.size)
            assertEquals(failAt - 1, web.drags.size)
            assertTrue(web.finished.isEmpty())
            assertTrue((web.outcome as RankingReader.Outcome.Failed).reason.contains("Không lưu được ảnh"))
        }
    }

    @Test fun pauseDuringSavingDoesNotScrollAndResumeRechecksTheCurrentTable() {
        val combo = RankScreens.Combo(Table.RATES, Mode.ALL)
        val stopped = Web(Page.RANKING, pauseOnSave = true).run(wanted = listOf(combo))
        assertNull(stopped.outcome)
        assertTrue(stopped.drags.isEmpty())
        assertTrue(stopped.finished.isEmpty())
        val resumed = Web(Page.RANKING, Table.FIGURES).run(wanted = listOf(combo))
        assertTrue(resumed.outcome is RankingReader.Outcome.Done)
        assertEquals(listOf("Đổi bảng xếp hạng"), resumed.tapped)
        assertEquals(List(4) { combo }, resumed.savedFrames)
    }

    @Test fun controlsRejectBroadAccessibilityParentsAndUseTheirCompactVisibleLabels() {
        val parent = box("Đổi bảng xếp hạng Đấu hạng (Tất cả) Tỷ lệ thắng", .03, .02, .95, .98)
        val wrongMode = box("Đấu 5v5 Đấu hạng (Tất cả)", .34, .02, .80, .45)
        val web = Web(Page.RANKING, reading = { words, _ -> listOf(parent, wrongMode) + words })
            .run(wanted = listOf(RankScreens.Combo(Table.FIGURES, Mode.FIVE)))
        assertTrue(web.outcome is RankingReader.Outcome.Done)
        assertEquals(.835, web.tapPoints.first().first, .001)
        assertTrue(web.tapPoints.all { it.second < .5 })
        assertTrue(web.savedFrames.all { it.table == Table.FIGURES && it.mode == Mode.FIVE })
        assertNull(RankScreens.switchButton(listOf(parent)))
        assertNull(RankScreens.modeButton(listOf(wrongMode)))
        assertNull(RankScreens.table(listOf(parent)))
    }

    @Test fun aShortModeFragmentCannotHideTheCompleteOverlappingModeLabel() {
        val complete = box("Đấu hạng (Tất cả)", .47, .17, .64, .22)
        val fragment = box("Đấu hạng", .47, .17, .54, .22)
        val ambiguous = box("Đấu 5v5 Đấu hạng (Tất cả)", .43, .17, .70, .22)
        assertEquals(complete, RankScreens.modeButton(listOf(fragment, ambiguous, complete)))
        assertNull(RankScreens.mode(ambiguous))
        assertNull(RankScreens.modeButton(listOf(fragment, ambiguous)))
    }

    @Test fun goesFromTheLobbyToTheTableAndPhotographsBothTablesForEachKindOfMatch() {
        val web = Web(Page.LOBBY).run()
        assertEquals(24, (web.outcome as RankingReader.Outcome.Done).pages)
        assertEquals(listOf("0.084,0.890", "Cộng đồng", "0.083,0.348", "Bảng xếp hạng đầy đủ >"), web.tapped.take(4))
        // Four photos per table: scrolled .4 of a screen at a time down a list one screen longer than the screen.
        val order =
            listOf(
                "ty-le-dau-hang-tat-ca",
                "ty-le-dau-5v5",
                "ty-le-dau-hang-tinh-anh",
                "chi-so-dau-hang-tat-ca",
                "chi-so-dau-5v5",
                "chi-so-dau-hang-tinh-anh"
            )
        assertEquals(order, web.finished)
        assertEquals(order.flatMap { c -> (1..4).map { "$c-%02d".format(it) } }, web.kept)
        assertEquals(1, web.tapped.count { it == "Đổi bảng xếp hạng" })
    }

    @Test fun boundedNavigationUsesTightAndSplitLabelsEvenWithParentNodesFirst() {
        for (labels in listOf(listOf(link()), splitLink(), listOf(box("xếp hạng đầy đủ", .443, .064, .567, .101)))) {
            val web = Web(Page.HEROES, heroWords = listOf(parent()) + landing() + labels).run(wanted = emptyList())
            assertTrue(web.outcome is RankingReader.Outcome.Done)
            assertEquals(1, web.linkTaps)
            assertEquals(0, web.sideTaps)
            assertTrue(web.tapPoints.single().first in .406..0.567)
            assertTrue(web.tapPoints.single().second in .064..0.101)
        }
    }

    @Test fun missingOrAmbiguousFullLinkWaitsThenStopsWithoutTappingTheSideTab() {
        for (labels in listOf(emptyList(), listOf(parent()), listOf(link(), box("Bảng xếp hạng đầy đủ", .59, .064, .69, .101)))) {
            val web = Web(Page.HEROES, heroWords = landing() + labels).run(wanted = emptyList())
            assertTrue(web.outcome is RankingReader.Outcome.Failed)
            assertEquals(listOf("ranking-link-unreadable"), web.kept)
            assertTrue(web.tapped.isEmpty())
            assertEquals(RankingReader.MAX_WAITS + 1, web.looks)
        }
    }

    @Test fun aCommunityTabThatDoesNotOpenIsNotBlindlyTappedAgain() {
        val web = Web(Page.COMMUNITY, sideWorks = false).run(wanted = emptyList())
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertEquals(1, web.sideTaps)
        assertEquals(1, web.tapPoints.size)
        assertEquals(listOf("ranking-link-unreadable"), web.kept)
    }

    @Test fun fullLinkRetriesAreBoundedAndWaitForTheWebPageBeforeReobservingTheTarget() {
        val recovered = Web(Page.HEROES, heroWords = landing() + link(), ignoredLinkTaps = 1).run(wanted = emptyList())
        assertTrue(recovered.outcome is RankingReader.Outcome.Done)
        assertEquals(2, recovered.linkTaps)
        assertEquals(0, recovered.sideTaps)
        assertTrue(recovered.looks >= RankingReader.MAX_WAITS + 3)
        val stuck = Web(Page.HEROES, heroWords = landing() + link(), ignoredLinkTaps = 99).run(wanted = emptyList())
        assertTrue(stuck.outcome is RankingReader.Outcome.Failed)
        assertEquals(RankingReader.MAX_LINK_TAPS, stuck.linkTaps)
        assertEquals(0, stuck.sideTaps)
        assertEquals(listOf("ranking-link-not-open"), stuck.kept)
    }

    @Test fun startsWithTheTableShownAndSkipsWhatAnEarlierTryFinished() {
        val web = Web(Page.RANKING, Table.FIGURES, Mode.FIVE, length = .5).run(skip = setOf("ty-le-dau-5v5", "ty-le-dau-hang-tat-ca"))
        assertEquals("chi-so-dau-5v5", web.finished.first())
        assertEquals(4, web.finished.size)
        assertTrue(web.kept.none { it.startsWith("ty-le-dau-5v5") || it.startsWith("ty-le-dau-hang-tat-ca") })
        assertEquals(listOf("chi-so-dau-5v5-01", "chi-so-dau-5v5-02"), web.kept.take(2))
    }

    @Test fun keepsHeadingsThatStayOnScreen() {
        // A table whose headings stay while the rows scroll under them.
        val web = Web(Page.RANKING, length = .8, sticky = true).run()
        assertEquals(6, web.finished.size)
        assertEquals(18, (web.outcome as RankingReader.Outcome.Done).pages)
    }

    @Test fun stopsOnAScreenItDoesNotKnow() {
        val web = Web(Page.OTHER).run()
        assertTrue(web.outcome is RankingReader.Outcome.Failed)
        assertEquals(listOf("ranking-unknown"), web.kept)
        assertTrue(web.tapped.isEmpty())
    }

    @Test fun stopsWhenThePauseCutsIt() {
        val web = Web(Page.RANKING, cut = 6).run()
        assertNull(web.outcome)
        assertTrue(web.kept.size <= 4)
    }

    @Test fun aScrollThatShowsNothingNewDoesNotEndTheList() {
        // Two ignored scrolls in a row in each table, as when two lists ended a third of the way down on the phone.
        val web = Web(Page.RANKING, length = 2.0, stalls = 2).run(wanted = listOf(RankScreens.Combo(Table.FIGURES, Mode.ALL)))
        assertEquals(listOf("chi-so-dau-hang-tat-ca"), web.finished)
        // 0, .4 … 2.0: six pages, the whole list.
        assertEquals((1..6).map { "chi-so-dau-hang-tat-ca-%02d".format(it) }, web.kept)
    }

    @Test fun takesOnlyTheChosenTables() {
        val wanted = listOf(RankScreens.Combo(Table.RATES, Mode.ELITE), RankScreens.Combo(Table.FIGURES, Mode.FIVE))
        val web = Web(Page.RANKING).run(wanted = wanted)
        assertEquals(listOf("ty-le-dau-hang-tinh-anh", "chi-so-dau-5v5"), web.finished)
        assertEquals(8, (web.outcome as RankingReader.Outcome.Done).pages)
    }

    @Test fun estimatesFromEarlierCleanRunsThenFromThisOne() {
        // Nothing done yet: the kept pace of earlier runs, 30 s a hero.
        assertEquals(3000.0, RunTimes.remaining(100, 0, 0.0, 30.0)!!, .001)
        // Halfway to trusting this run after 5 heroes at 20 s.
        assertEquals(95 * 25.0, RunTimes.remaining(95, 5, 100.0, 30.0)!!, .001)
        // After ten, this run's own pace alone; with no earlier run, its own from the first hero.
        assertEquals(90 * 20.0, RunTimes.remaining(90, 10, 200.0, 30.0)!!, .001)
        assertEquals(99 * 20.0, RunTimes.remaining(99, 1, 20.0, null)!!, .001)
        assertNull(RunTimes.remaining(100, 0, 0.0, null))
        // A new clean run moves the kept time halfway.
        assertEquals(25.0, RunTimes.blend(30.0, 20.0), .001)
        assertEquals(20.0, RunTimes.blend(null, 20.0), .001)
    }
}
