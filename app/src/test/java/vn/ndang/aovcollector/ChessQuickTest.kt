package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fast capture on a drawn Bách khoa: tabs with pages, items whose drawers have pages, the hero list measured by
 * [ListNavigator] on textured profiles, and the game's Save Photo box. Entered through [ChessWikiReader] as the app does.
 */
class ChessQuickTest {
    private companion object {
        const val HEIGHT = 640
        val SURE = .386 to .557
        val CANCEL = .567 to .557
        val SKILL = .405 to .52

        fun near(a: Pair<Double, Double>, x: Double, y: Double, dx: Double = .02, dy: Double = .03) =
            abs(a.first - x) < dx && abs(a.second - y) < dy

        /** The same textured world as the list tests: fifteen RGB-band values per pixel row at a scroll offset. */
        fun profile(offset: Int): FloatArray {
            val layout = ChessWiki.HERO_LAYOUT
            val top = (layout.profileTop * HEIGHT).toInt()
            val bottom = (layout.profileBottom * HEIGHT).toInt()
            return FloatArray((bottom - top) * 15) { i ->
                val worldY = top + i / 15 + offset
                var bits = (worldY * 73856093) xor ((i % 15 + 1) * 19349663)
                bits = (bits xor (bits ushr 13)) * 83492791
                (24 + (bits ushr 16 and 191)).toFloat()
            }
        }

        /** A blessing picture as [ChessPixels.blessingArt] gives it: 256 cells, far apart for different letters. */
        fun art(name: Char) = String(CharArray(256) { if ((it * 31 + name.code * 17) % 7 < 3) '1' else '0' })

        val PHE = (0 until 3).map { .236 + .0562 * it to .297 }
        val NGHE = (0 until 2).map { .236 + .0562 * it to .547 }
        val ITEMS = listOf(.324 to .24, .388 to .24, .453 to .24)
    }

    /**
     * [pages]: pages of a tab (or of a section without tabs), each a view the drags move through; [openEnd]: tabs whose
     * last page still shows something at its bottom edge. [blessings]: the pictures on each Chúc phúc page.
     */
    private class Wiki(
        val heroes: Int = 7,
        val pages: Map<String, Int> = emptyMap(),
        val openEnd: Set<String> = emptySet(),
        val tiles: Map<String, List<Pair<Double, Double>>> = mapOf("phe" to PHE, "nghe" to NGHE, "tat-ca-trang-bi" to ITEMS),
        val drawerPages: Int = 1,
        val heroDrawerPages: Int = 2,
        val savePhoto: Boolean = true,
        val pressOpens: Boolean = false,
        var stuckCloses: Int = 0,
        var lostFirstDrag: Boolean = false,
        val blessings: List<List<Char>> = listOf(listOf('a', 'b', 'c', 'd'), listOf('c', 'd', 'e', 'f'), listOf('e', 'f', 'g')),
        /** How far each page drag moves the Chúc phúc list, as its profile shows; null: a list that cannot be measured. */
        val blessingMove: Double? = null,
        var strayDialog: Boolean = false,
        /** Save Photo shows only from the second look after the press. */
        val slowBox: Boolean = false,
        /** Pages of the hero list whose bottom strip is plain though rows follow: the lower part of coloured cards. */
        val plainBottoms: Set<Int> = emptySet(),
        /** Looks at Trang bị that still show item pictures as loading squares. */
        var loadingLooks: Int = 0,
        /** Looks at a tab's first page while the game is still drawing it: each differs from the one before. */
        var drawingLooks: Int = 0,
        /** A card whose portrait is not drawn for [hiddenLooks] looks while it is where it can be tapped. */
        val hiddenCard: Int = -1,
        var hiddenLooks: Int = 0,
        /** Looks at each opened drawer before its picture is drawn. */
        val slowArt: Int = 0,
        /** Drags of the hero list the game shows late: the first look after each still shows the list where it was. */
        var laggyDrags: Int = 0,
        ledger: Set<String> = emptySet()
    ) : ChessWikiReader.Screen {
        private val clock = ChessTestClockQueue()
        val done = ledger.toMutableSet()
        val saved = ArrayList<String>()
        val marks = ArrayList<String>()
        val presses = ArrayList<String>()
        val opened = ArrayList<String>()
        val notes = ArrayList<String>()

        /** Images kept while the screen was not drawn yet: loading squares, a tab still drawing, a drawer's picture. */
        val early = ArrayList<String>()
        var ocrLooks = 0
        var looks = 0
        var outcome: ChessWikiReader.Outcome? = null
        var inWiki = false

        private var section: String? = null
        private var tab: String? = null
        private var page = 0
        private var offset = 0
        private var drawer: String? = null
        private var drawerPage = 0
        private var dialog: String? = null
        private var coming: String? = null
        private var artLooks = 0
        private var lateOffset: Int? = null
        private val layout = ChessWiki.HERO_LAYOUT
        private val maxOffset = ((layout.row0 + ((heroes - 1) / 5) * layout.pitch - .60) * HEIGHT).roundToInt().coerceAtLeast(0)

        override val alive get() = outcome == null
        override val monotonicMs get() = clock.now

        private val view get() = tab ?: section ?: "landing"
        private val heroList get() = tab == "tat-ca-quan-co"

        private fun pagesOf(v: String) = pages[v] ?: if (v == "tat-ca-quan-co") 3 else 1

        private fun drawerPagesOf(d: String) = if (d.startsWith("hero:")) heroDrawerPages else drawerPages

        private fun cardAt(x: Double, y: Double): Int? = (0 until heroes).firstOrNull {
            val (cx, cy) = layout.centre(it, offset.toDouble() / HEIGHT)
            abs(cx - x) < .03 && abs(cy - y) < .05
        }

        private fun tileAt(x: Double, y: Double): String? {
            for ((key, list) in tiles) {
                val topic = ChessWiki.TOPICS.first { it.key == key }
                if (topic.section != section || (topic.tabX != 0.0 && tab != key)) continue
                list.indexOfFirst { near(it, x, y) }.takeIf { it >= 0 }?.let { return "$key:${it + 1}" }
            }
            if (section == "chuc-phuc") {
                icons().indexOfFirst { near(it.first, x, y) }.takeIf { it >= 0 }?.let { return "art:" + icons()[it].second }
            }
            return null
        }

        /** Blessing pictures on the current page, two to a row. */
        private fun icons() = blessings.getOrElse(page) { emptyList() }.mapIndexed { i, c ->
            (.277 + .35 * (i % 2) to .40 + .26 * (i / 2)) to
                c
        }

        override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
            looks++
            // A late drag shows from the second look after it.
            lateOffset?.let {
                if (it < 0) {
                    offset = -it - 1
                    lateOffset = null
                } else {
                    lateOffset = -it - 1
                }
            }
            if (words) ocrLooks++
            if (strayDialog && inWiki && dialog == null && drawer == null) {
                strayDialog = false
                dialog = "stray"
            }
            val d = drawer
            val late = coming
            coming = null
            val onPage = inWiki && d == null && dialog == null
            // Trang bị has no tabs: its page is the section's.
            val loading = onPage && view == "trang-bi" && loadingLooks > 0
            if (loading) loadingLooks--
            val drawing = onPage && page == 0 && drawingLooks > 0
            if (drawing) drawingLooks--
            val artDrawn = d == null || artLooks <= 0
            if (d != null && artLooks > 0) artLooks--
            val hide = if (onPage &&
                heroList &&
                hiddenLooks > 0 &&
                hiddenCard in 0 until heroes &&
                layout.centre(hiddenCard, offset.toDouble() / HEIGHT).second in layout.top..layout.bottom
            ) {
                hiddenLooks--
                hiddenCard
            } else {
                -1
            }
            val shown = when {
                dialog != null -> "dialog:$dialog:" + (d ?: view)
                d != null -> "drawer:$d:$drawerPage"
                heroList -> "list:$offset:$page"
                else -> "page:$view:$page"
            } + (if (loading) ":loading:$loadingLooks" else "") + (if (drawing) ":drawing:$drawingLooks" else "")
            // Unrelated values for every different view, as different screens give.
            val random = java.util.Random(shown.hashCode().toLong())
            val listProfile = if (heroList && d == null && dialog == null) profile(offset) else floatArrayOf()
            val wiki = inWiki
            then(object : ChessWikiReader.Look {
                override val words = if (words && !wiki) {
                    listOf(GameScreens.Word("Bách khoa", .05, .05, .2, .09), GameScreens.Word("Cờ Liên Quân", .2, .05, .4, .09))
                } else {
                    emptyList()
                }
                override val signature = IntArray(64) { random.nextInt(256) }
                override val modal = d != null && dialog == null
                override val height = HEIGHT
                override val heroProfile = listProfile
                override val heroCardsBelow = listProfile.isNotEmpty() &&
                    (0 until heroes).any { layout.centre(it, offset.toDouble() / HEIGHT).second in .36..1.14 }
                override val blankBelow = when {
                    d != null -> drawerPage == drawerPagesOf(d) - 1
                    heroList -> page in plainBottoms || page == pagesOf(view) - 1
                    else -> page == pagesOf(view) - 1 && view !in openEnd
                }
                override val drawerArt = artDrawn
                override val saveDialog = if (dialog != null) ChessPixels.Dialog(SURE, CANCEL) else null

                override fun loadingTiles() = if (loading) 4 else 0

                override fun hasHeroCard(x: Double, y: Double) = d == null && heroList && cardAt(x, y).let { it != null && it != hide }

                override fun tiles(topic: ChessWiki.Topic) = this@Wiki.tiles[topic.key].orEmpty().mapIndexed { i, (x, y) ->
                    ChessWiki.Tile(x, y, "${topic.key}-$i")
                }

                override fun blessingIcons() = icons().map { (at, c) -> ChessWiki.Tile(at.first, at.second, art(c)) }

                override fun blessingProfile() = blessingMove?.takeIf { d == null }?.let { profile((page * it * HEIGHT).roundToInt()) }
                    ?: floatArrayOf()

                override fun skillIcons() = if (d?.startsWith("hero:") == true && drawerPage == 0) {
                    listOf(ChessWiki.Tile(SKILL.first, SKILL.second, "skill"))
                } else {
                    emptyList()
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    saved += name
                    if (loading || drawing || !artDrawn) early += name
                    then(true)
                }

                override fun discard() {}
            })
            inWiki = true
            if (late != null) dialog = late
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            val at = x to y
            val d = drawer
            when {
                dialog != null -> when {
                    near(SURE, x, y) -> {
                        presses += "sure:$dialog"
                        dialog = null
                    }
                    near(CANCEL, x, y) -> {
                        presses += "cancel:$dialog"
                        dialog = null
                    }
                }
                d != null -> if (near(.902 to .081, x, y)) {
                    if (stuckCloses > 0) stuckCloses-- else drawer = null
                }
                x < .2 -> ChessWiki.TOPICS.firstOrNull { abs(it.sidebarY - y) < .03 }?.let { t ->
                    section = t.section
                    tab = ChessWiki.TOPICS.first { it.section == t.section }.takeIf { it.tabX != 0.0 }?.key
                    page = 0
                    offset = 0
                }
                y < .21 -> ChessWiki.TOPICS.firstOrNull { it.section == section && it.tabX != 0.0 && abs(it.tabX - x) < .03 }?.let {
                    tab = it.key
                    page = 0
                    offset = 0
                }
                heroList -> cardAt(x, y)?.let { open("hero:$it") }
                else -> tileAt(at.first, at.second)?.let { open(it) }
            }
            then(true)
        }

        private fun open(item: String) {
            drawer = item
            drawerPage = 0
            artLooks = slowArt
            opened += item
        }

        override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) {
            val d = drawer
            when {
                abs(x - toX) > .1 || dialog != null -> Unit
                d != null -> drawerPage = minOf(drawerPage + 1, drawerPagesOf(d) - 1)
                lostFirstDrag -> lostFirstDrag = false
                else -> page = minOf(page + 1, pagesOf(view) - 1)
            }
            then(true)
        }

        override fun dragHeroList(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
            if (drawer == null && dialog == null) {
                val to = ((lateOffset?.let { if (it < 0) -it - 1 else it } ?: offset) + (d * HEIGHT).roundToInt()).coerceIn(0, maxOffset)
                if (laggyDrags > 0 && !fling) {
                    laggyDrags--
                    lateOffset = to
                } else {
                    offset = to
                    lateOffset = null
                }
            }
            then(true)
        }

        override fun longPress(x: Double, y: Double, then: (Boolean) -> Unit) {
            val d = drawer
            val item = when {
                d != null -> if (near(SKILL, x, y, dy = .06)) "skill:$d" else null
                heroList -> cardAt(x, y)?.let { "hero:$it" }
                else -> tileAt(x, y)
            }
            if (item != null) {
                when {
                    pressOpens && d == null -> open(item)
                    savePhoto && slowBox -> coming = item
                    savePhoto -> dialog = item
                }
            }
            then(true)
        }

        override fun markNative(group: String, item: String) {
            marks += "$group:$item"
        }

        override fun later(ms: Long, then: () -> Unit) = clock.later(ms, then)

        override fun seen(id: String) = id in done

        override fun finished(id: String) {
            done += id
        }

        override fun note(message: String) {
            notes += message
        }

        override fun say(message: String) {}

        fun run(options: ChessWiki.Options): Wiki {
            ChessWikiReader(screen = this, options = options.copy(openGame = false, quick = true), finish = { outcome = it }).start()
            clock.drain()
            return this
        }
    }

    private fun options(vararg topics: String, heroDetails: Boolean = false, details: Boolean = false, native: Set<String> = emptySet()) =
        ChessWiki.Options(
            topics.toSet(),
            heroDetails = heroDetails,
            synergyDetails = details,
            equipmentDetails = details,
            nativeGroups = native
        )

    private fun Wiki.assertDone() = assertTrue(outcome.toString() + " " + notes, outcome is ChessWikiReader.Outcome.Done)

    @Test fun savesEveryPageOfEveryTabWithoutReadingText() {
        val wiki = Wiki(
            pages = mapOf("quy-trinh" to 1, "nhiem-vu" to 3, "tuong-tac" to 1)
        ).run(options("quy-trinh", "danh-sach-nhiem-vu", "phe"))
        wiki.assertDone()
        assertEquals(
            listOf(
                "chess__thong-tin__quy-trinh__trang__001",
                "chess__tuong-tac__phe__trang__001",
                "chess__nhiem-vu__danh-sach-nhiem-vu__trang__001",
                "chess__nhiem-vu__danh-sach-nhiem-vu__trang__002",
                "chess__nhiem-vu__danh-sach-nhiem-vu__trang__003"
            ),
            wiki.saved
        )
        // Only the way in reads text; the encyclopedia is read from pixels.
        assertEquals(1, wiki.ocrLooks)
        // One look to come in; two at each tab's first page, saved once two looks match; one per later page. A page
        // whose bottom is plain is not dragged past.
        assertEquals(9, wiki.looks)
    }

    @Test fun aTabWhoseLastPageShowsMoreIsDraggedOnceMoreAndStops() {
        val wiki = Wiki(pages = mapOf("nhiem-vu" to 2), openEnd = setOf("nhiem-vu")).run(options("danh-sach-nhiem-vu"))
        wiki.assertDone()
        assertEquals(2, wiki.saved.size)
        assertEquals(5, wiki.looks)
    }

    @Test fun theHeroListGoesOnPastAPlainBottomStrip() {
        // From three gold the cards are nearly the background's colour, so the strip under a row's names can be
        // plain with rows still to come: the user's list stopped there.
        val wiki = Wiki(pages = mapOf("tat-ca-quan-co" to 5), plainBottoms = setOf(1, 2)).run(options("tat-ca-quan-co"))
        wiki.assertDone()
        assertEquals((1..5).map { "chess__quan-co__tat-ca-quan-co__trang__%03d".format(it) }, wiki.saved)
    }

    @Test fun itemPicturesStillLoadingAreWaitedFor() {
        val wiki = Wiki(loadingLooks = 3).run(options("tat-ca-trang-bi", details = true))
        wiki.assertDone()
        assertTrue(wiki.early.toString(), wiki.early.isEmpty())
        assertEquals(listOf("tat-ca-trang-bi:1", "tat-ca-trang-bi:2", "tat-ca-trang-bi:3"), wiki.opened)
        assertTrue(wiki.notes.toString(), wiki.notes.any { "chưa tải" in it })
        // Pictures that never load are not waited for forever: the page is saved as it is, with a note.
        val never = Wiki(loadingLooks = 99).run(options("tat-ca-trang-bi"))
        never.assertDone()
        assertEquals(listOf("chess__trang-bi__tat-ca-trang-bi__trang__001"), never.saved)
        assertTrue(never.notes.toString(), never.notes.any { "chụp như đang thấy" in it })
    }

    @Test fun aTabStillBeingDrawnIsSavedOnceItStandsStill() {
        val wiki = Wiki(pages = mapOf("nhiem-vu" to 2), drawingLooks = 3).run(options("danh-sach-nhiem-vu"))
        wiki.assertDone()
        assertTrue(wiki.early.toString(), wiki.early.isEmpty())
        assertEquals(
            listOf("chess__nhiem-vu__danh-sach-nhiem-vu__trang__001", "chess__nhiem-vu__danh-sach-nhiem-vu__trang__002"),
            wiki.saved
        )
    }

    @Test fun aDrawerSlowToDrawItsPictureIsWaitedFor() {
        val wiki = Wiki(slowArt = 4).run(options("phe", details = true))
        wiki.assertDone()
        assertEquals(listOf("phe:1", "phe:2", "phe:3"), wiki.opened)
        assertTrue(wiki.early.toString(), wiki.early.isEmpty())
    }

    @Test fun aPortraitNotDrawnYetIsNotTakenForTheEndOfTheList() {
        // The sixth card, first of the second row, shows no portrait on the two looks after the list comes to it.
        val wiki = Wiki(heroes = 17, hiddenCard = 5, hiddenLooks = 2).run(options("tat-ca-quan-co", heroDetails = true))
        wiki.assertDone()
        assertEquals((0 until 17).map { "hero:$it" }, wiki.opened)
        assertTrue(wiki.notes.toString(), wiki.notes.any { "đếm lại" in it })
    }

    @Test fun aDragTheGameShowsLateIsMeasuredOnceTheListRests() {
        // The game busy loading the next row draws the list's move a look late; measured early, every later card
        // would be looked for in the wrong place.
        val wiki = Wiki(heroes = 18, laggyDrags = 3).run(options("tat-ca-quan-co", heroDetails = true))
        wiki.assertDone()
        assertEquals((0 until 18).map { "hero:$it" }, wiki.opened)
    }

    @Test fun theEndOfTheHeroListIsTakenWhenADragNoLongerMovesIt() {
        val wiki = Wiki(heroes = 12).run(options("tat-ca-quan-co", heroDetails = true))
        wiki.assertDone()
        assertEquals((0 until 12).map { "hero:$it" }, wiki.opened)
        assertTrue(wiki.notes.toString(), wiki.notes.any { "hết danh sách sau ô 12" in it })
        assertTrue(wiki.notes.toString(), wiki.notes.none { "đếm lại" in it })
    }

    @Test fun aTabsFirstDragThatChangedNothingIsTriedOnceMore() {
        val wiki = Wiki(pages = mapOf("nhiem-vu" to 2), lostFirstDrag = true).run(options("danh-sach-nhiem-vu"))
        wiki.assertDone()
        assertEquals(
            listOf("chess__nhiem-vu__danh-sach-nhiem-vu__trang__001", "chess__nhiem-vu__danh-sach-nhiem-vu__trang__002"),
            wiki.saved
        )
    }

    @Test fun opensEachItemAndNamesItByItsPlace() {
        val wiki = Wiki(drawerPages = 2).run(options("phe", "tat-ca-trang-bi", details = true))
        wiki.assertDone()
        assertEquals(listOf("phe:1", "phe:2", "phe:3", "tat-ca-trang-bi:1", "tat-ca-trang-bi:2", "tat-ca-trang-bi:3"), wiki.opened)
        assertTrue(wiki.saved.containsAll(listOf("chess__tuong-tac__phe__phe__phe-001__001", "chess__tuong-tac__phe__phe__phe-001__002")))
        assertTrue(wiki.saved.contains("chess__trang-bi__tat-ca-trang-bi__vat-pham__vat-pham-003__002"))
        // Every drawer page once: 2 pages x 6 items, plus the two tabs.
        assertEquals(14, wiki.saved.size)
        assertEquals(wiki.saved.size, wiki.saved.toSet().size)
        // Named by ChessCaptureFiles as readable files in the tab's folder.
        assertEquals("phe-001-page-2", ChessCaptureFiles.screenshotBase("chess__tuong-tac__phe__phe__phe-001__002"))
        assertEquals(
            "vat-pham-003-page-1",
            ChessCaptureFiles.screenshotBase("chess__trang-bi__tat-ca-trang-bi__vat-pham__vat-pham-003__001")
        )
    }

    @Test fun aDrawerTheXDidNotCloseIsClosedAgainBeforeTheNextItem() {
        val wiki = Wiki(stuckCloses = 1).run(options("phe", details = true))
        wiki.assertDone()
        assertEquals(listOf("phe:1", "phe:2", "phe:3"), wiki.opened)
        assertEquals(
            listOf(
                "chess__tuong-tac__phe__trang__001",
                "chess__tuong-tac__phe__phe__phe-001__001",
                "chess__tuong-tac__phe__phe__phe-002__001",
                "chess__tuong-tac__phe__phe__phe-003__001"
            ),
            wiki.saved
        )
    }

    @Test fun walksTheHeroListCardByCard() {
        val wiki = Wiki(heroes = 7).run(options("tat-ca-quan-co", heroDetails = true))
        wiki.assertDone()
        assertEquals((0 until 7).map { "hero:$it" }, wiki.opened)
        for (n in 1..7) {
            val name = "quan-co-%03d".format(n)
            assertTrue(name, wiki.saved.contains("chess__quan-co__tat-ca-quan-co__tuong__${name}__001"))
            assertTrue(name, wiki.saved.contains("chess__quan-co__tat-ca-quan-co__tuong__${name}__002"))
        }
        assertTrue(wiki.saved.contains("chess__quan-co__tat-ca-quan-co__trang__001"))
        assertEquals(wiki.saved.size, wiki.saved.toSet().size)
        assertEquals(1, wiki.ocrLooks)
        assertEquals("quan-co-002-detail-1", ChessCaptureFiles.screenshotBase("chess__quan-co__tat-ca-quan-co__tuong__quan-co-002__001"))
    }

    @Test fun savesHeroPicturesAndSkillsWithSureAndRecordsThem() {
        val wiki = Wiki(heroes = 3).run(options("tat-ca-quan-co", native = setOf("quan-co", "chieu-quan-co")))
        wiki.assertDone()
        assertEquals(
            listOf(1, 2, 3).flatMap { n ->
                val name = "quan-co-%03d".format(n)
                listOf("quan-co:$name-head", "chieu-quan-co:$name-skill-1")
            },
            wiki.marks
        )
        assertTrue(wiki.presses.all { it.startsWith("sure:") })
        // Opened only for the skill: its first page is kept, nothing dragged.
        assertFalse(wiki.saved.any { it.endsWith("__002") && "__tuong__" in it })
    }

    @Test fun aPressThatOpensTheDrawerIsTakenAsTheDrawer() {
        val wiki = Wiki(pressOpens = true, savePhoto = false).run(options("phe", details = true, native = setOf("phe")))
        wiki.assertDone()
        assertTrue(wiki.marks.isEmpty())
        assertEquals(3, wiki.saved.count { "__phe__phe-" in it })
        // The press opened each drawer; no tap was needed on top.
        assertEquals(listOf("phe:1", "phe:2", "phe:3"), wiki.opened)
    }

    @Test fun aSavePhotoBoxSlowToAppearIsWaitedFor() {
        val wiki = Wiki(slowBox = true).run(options("tat-ca-trang-bi", native = setOf("trang-bi")))
        wiki.assertDone()
        assertEquals((1..3).map { "trang-bi:vat-pham-%03d".format(it) }, wiki.marks)
    }

    @Test fun noSavePhotoBoxMeansNothingIsRecorded() {
        val wiki = Wiki(savePhoto = false).run(options("tat-ca-trang-bi", native = setOf("trang-bi")))
        wiki.assertDone()
        assertTrue(wiki.marks.isEmpty())
        assertTrue(wiki.presses.isEmpty())
        assertEquals(3, wiki.notes.count { it.startsWith("Không có Save Photo") })
    }

    @Test fun aBlessingAlreadySavedOnThePageBeforeIsNotPressedAgain() {
        val wiki = Wiki(pages = mapOf("thuong" to 3)).run(options("thuong", native = setOf("chuc-phuc-thuong")))
        wiki.assertDone()
        assertEquals("abcdefg".map { "sure:art:$it" }, wiki.presses)
        assertEquals((1..7).map { "chuc-phuc-thuong:chuc-phuc-%03d".format(it) }, wiki.marks)
    }

    /**
     * Thường and Truyền thuyết on the user's phone: the cards at the bottom of a page come back cut by the tabs, their
     * pictures partly hidden (f, g here), and different blessings can share a picture (three e). By where the measured
     * drag took them, the cut ones are not pressed again and the third e is not taken for one seen before.
     */
    @Test fun blessingsAreToldByWhereTheDragTookThemNotByTheirPicture() {
        val pages = listOf(listOf('a', 'b', 'c', 'd'), listOf('f', 'g', 'e', 'e'), listOf('e', 'e', 'e'))
        val wiki = Wiki(pages = mapOf("thuong" to 3), blessings = pages, blessingMove = .26)
            .run(options("thuong", native = setOf("chuc-phuc-thuong")))
        wiki.assertDone()
        assertEquals("abcdeee".map { "sure:art:$it" }, wiki.presses)
        // Without the measure, by the picture: the cut cards are pressed again and the third e is missed.
        val byArt = Wiki(pages = mapOf("thuong" to 3), blessings = pages).run(options("thuong", native = setOf("chuc-phuc-thuong")))
        assertEquals("abcdfgee".map { "sure:art:$it" }, byArt.presses)
    }

    @Test fun aStraySavePhotoIsCancelledNeverConfirmed() {
        val wiki = Wiki(strayDialog = true).run(options("quy-trinh"))
        wiki.assertDone()
        assertEquals(listOf("cancel:stray"), wiki.presses)
        assertEquals(listOf("chess__thong-tin__quy-trinh__trang__001"), wiki.saved)
    }

    @Test fun aResumedRunSkipsFinishedTabsItemsAndPictures() {
        val ledger = setOf(
            "quick:topic:quy-trinh",
            "quick:item:phe:1",
            "quick:native:phe:phe-002"
        )
        val wiki = Wiki(ledger = ledger).run(options("quy-trinh", "phe", details = true, native = setOf("phe")))
        wiki.assertDone()
        assertFalse(wiki.saved.any { "quy-trinh" in it })
        assertEquals(listOf("phe:2", "phe:3"), wiki.opened)
        assertEquals(listOf("phe:phe-001", "phe:phe-003"), wiki.marks)
    }

    @Test fun teachingShowsTheFastCapturesDragPaths() {
        // Slow timing chosen for the OCR reader does not change the fast capture's drags.
        val options = ChessWiki.Options(setOf("danh-sach-nhiem-vu"), fast = false, quick = true)
        val steps = CalibrationCatalog.steps(org.json.JSONObject(BuiltInMacros.chess(options)))
        val drag = steps.single { it.action.id == "chess.v1.topic.danh-sach-nhiem-vu.scroll.0" }.action
        assertEquals(listOf(CalibrationPoint(.76, .90), CalibrationPoint(.76, .30)), drag.points)
        assertEquals(350L, drag.durationMs)
        assertEquals(120L, drag.holdMs)
        assertFalse(steps.any { it.action.id == "chess.v1.topic.danh-sach-nhiem-vu.scroll.2" })
    }

    @Test fun theOldReaderStaysWhenTheFastCaptureIsOff() {
        assertFalse(ChessWiki.Options(setOf("quy-trinh")).quick)
        val json = org.json.JSONObject(BuiltInMacros.chess(ChessWiki.Options(setOf("quy-trinh"), quick = false)))
        assertFalse(BuiltInMacros.chessOptions(json).quick)
        // A macro saved before the choice existed gets the fast capture.
        json.remove("chessQuick")
        assertTrue(BuiltInMacros.chessOptions(json).quick)
    }
}
