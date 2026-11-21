package vn.ndang.aovcollector

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs [ListNavigator] against a drawn All-heroes screen whose swipes slide on by a random amount. */
class HeroGridTest {
    private class FakeList(val heroes: Int, seed: Int, val slide: ClosedFloatingPointRange<Double>) {
        val w = 2400
        val h = 1080
        var offset = 0.0

        /** What the game shows: the list, a hero page opened from it, or the lobby, which has no back arrow. */
        enum class Page { LIST, HERO, LOBBY }

        var page = Page.LIST
        val rows = (heroes + 4) / 5
        val maxOffset = maxOf(0.0, (HeroGrid.ROW0 + (rows - 1) * HeroGrid.PITCH - .78) * h)
        private val random = Random(seed)
        private val particles = List(400) { random.nextInt(w) * h + random.nextInt(h) }.toSet()

        fun cardAt(x: Int, y: Int): Int? {
            if (y < .12 * h) return null
            val cy = y + offset
            for (c in 0 until 5) {
                if (abs(x - HeroGrid.columns[c] * w) > .053 * w) continue
                val r = Math.round((cy / h - HeroGrid.ROW0) / HeroGrid.PITCH).toInt()
                if (r < 0 || r >= rows || abs(cy - (HeroGrid.ROW0 + r * HeroGrid.PITCH) * h) > .185 * h) return null
                return (r * 5 + c).takeIf { it < heroes }
            }
            return null
        }

        /** The white "<" at the top left, drawn from the stroke ends measured on the game's screens. */
        private fun arrow(x: Int, y: Int): Boolean {
            if (x > .13 * w || y > .11 * h) return false
            val tx = .0758 * w
            val ty = .0546 * h
            val ex = .1054 * w
            return listOf(.0296 * h, .0806 * h).any { ey ->
                val dx = ex - tx
                val dy = ey - ty
                val t = (((x - tx) * dx + (y - ty) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
                hypot(x - tx - t * dx, y - ty - t * dy) < .005 * h
            }
        }

        // Static gradient background with particles; cards scroll and some are greyed out like unowned heroes.
        fun readRow(y: Int, row: IntArray) {
            if (page != Page.LIST) {
                // Hero art and the lobby: detailed everywhere, but not white.
                for (x in 0 until w) {
                    val n =
                        ((x * 73856093) xor (y * 19349663)) ushr 8 and 127
                    row[x] =
                        if (page == Page.HERO && arrow(x, y)) -1 else (0xff shl 24) or (n shl 16) or ((n / 2) shl 8) or (40 + n)
                }
                return
            }
            for (x in 0 until w) {
                if (arrow(x, y)) {
                    row[x] = -1
                    continue
                }
                val i = cardAt(x, y)
                row[x] =
                    if (i ==
                        null
                    ) {
                        if (x * h + y in
                            particles
                        ) {
                            0xffd0c8ff.toInt()
                        } else {
                            (0xff shl 24) or ((30 + 20 * y / h) shl 16) or (26 shl 8) or (60 + 30 * y / h)
                        }
                    } else {
                        val n = ((x * 73856093) xor ((y + offset.toInt()) * 19349663) xor (i * 83492791)) ushr 8 and 63
                        val base = 40 + (i * 37) % 150
                        val v = if (i % 7 ==
                            3
                        ) {
                            base / 4 + n / 6
                        } else {
                            (base + n - 32).coerceIn(0, 255)
                        }
                        (0xff shl 24) or (v shl 16) or (((v + i * 11) % 256) shl 8) or
                            ((v * 2 + i) % 256)
                    }
            }
        }

        /** The game slides on after the finger lifts and loses a few pixels to its drag threshold. */
        fun drag(d: Double, fling: Boolean) {
            val factor = if (fling) random.nextDouble(3.0, 5.0) else random.nextDouble(slide.start, slide.endInclusive)
            offset = (offset + d * h * factor - 15 * Math.signum(d)).coerceIn(0.0, maxOffset)
        }
    }

    private class Run(
        heroes: Int,
        seed: Int,
        slide: ClosedFloatingPointRange<Double>,
        startAt: Double = 0.0,
        val names: List<String> = List(heroes) {
            "Hero $it"
        },
        // As the hero list: only the page tapped on is kept, and a card far up is reached from the top. Else as chess.
        private val heroList: Boolean = false,
        // How much lower than the app's grid the game writes the names of a row: rows spaced otherwise than measured.
        private val lower: (Int) -> Double = { 0.0 }
    ) {
        val list = FakeList(heroes, seed, slide).apply { offset = maxOffset * startAt }
        val state = ListNavigator.State()
        val tapped = ArrayList<Int?>()
        val kept = ArrayList<String>()
        val notes = ArrayList<String>()

        // Cards whose names ML Kit does not read.
        val blind = HashSet<Int>()
        var swipes = 0
        var flicks = 0
        var backs = 0

        /** Looks after which a hero page that is still closing shows the list by itself. */
        var closingLooks = 0
        val screen = object : ListNavigator.Screen {
            override val alive = true
            override val keepsPassedPages = !heroList
            override val flicksToTop = heroList

            override fun note(text: String) {
                notes += text
            }

            override fun look(then: (ListNavigator.Shot?) -> Unit) {
                val grid = HeroGrid.isGrid(list.w, list.h, list::readRow)
                val profile = if (grid) HeroGrid.profile(list.w, list.h, list::readRow) else FloatArray(0)
                val arrow = !grid && HeroGrid.hasBackArrow(list.w, list.h, list::readRow)
                if (closingLooks > 0 && --closingLooks == 0) list.page = FakeList.Page.LIST
                then(object : ListNavigator.Shot {
                    override val profile = profile
                    override val height = list.h
                    override val backArrow = arrow

                    override fun hasCard(x: Double, y: Double) = HeroGrid.hasCard(list.w, list.h, x, y, list::readRow)

                    override fun done(keep: String?) {
                        if (keep != null) kept += keep
                    }

                    // Each card's name across its bottom, as ML Kit reads it, for the cards on screen.
                    override fun words(then: (List<GameScreens.Word>) -> Unit) = then(
                        if (list.page !=
                            FakeList.Page.LIST
                        ) {
                            emptyList()
                        } else {
                            names.indices.filter { it !in blind }.mapNotNull { i ->
                                val y =
                                    (HeroGrid.ROW0 + (i / 5) * HeroGrid.PITCH) + HeroFinder.NAME_BELOW + lower(i / 5) - list.offset / list.h
                                val x = HeroGrid.columns[i % 5]
                                if (y < .15 || y > .98) null else GameScreens.Word(names[i], x - .04, y - .015, x + .04, y + .015)
                            }
                        }
                    )
                })
            }

            override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                swipes++
                if (fling) flicks++
                if (list.page ==
                    FakeList.Page.LIST
                ) {
                    list.drag(d, fling)
                }
                then(true)
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                val card = if (list.page == FakeList.Page.LIST) {
                    list.cardAt(
                        (x * (list.w - 1)).toInt(),
                        (y * (list.h - 1)).toInt()
                    )
                } else {
                    null
                }
                tapped += card
                if (card != null) list.page = FakeList.Page.HERO
                then(true)
            }

            override fun back(then: (Boolean) -> Unit) {
                backs++
                list.page =
                    if (list.page == FakeList.Page.HERO) FakeList.Page.LIST else FakeList.Page.LOBBY
                then(true)
            }

            override fun later(ms: Long, then: () -> Unit) {
                then()
            }
        }

        /**
         * Picks cards in order like a batch run, with the macro's back tap after each hero; the game ignores
         * that tap after the cards in [ignoredBack]. Returns the cards actually tapped and how the run ended.
         */
        fun collect(limit: Int, ignoredBack: Set<Int> = emptySet()): Pair<List<Int?>, String> {
            for (card in 0 until limit) {
                var outcome: ListNavigator.Outcome? = null
                ListNavigator(card, state, screen) { outcome = it }.start()
                when (val o = outcome) {
                    is ListNavigator.Outcome.Tapped -> if (card !in ignoredBack) list.page = FakeList.Page.LIST
                    is ListNavigator.Outcome.End -> return tapped to "end at $card"
                    is ListNavigator.Outcome.Missing -> return tapped to "missing at $card"
                    is ListNavigator.Outcome.Failed -> return tapped to "failed at $card: ${o.reason}"
                    null -> return tapped to "no outcome at $card"
                }
            }
            return tapped to "limit"
        }

        /** Taps card [card] alone, then is back on the list as after the macro's back tap; how it ended. */
        fun pick(card: Int): ListNavigator.Outcome? {
            var outcome: ListNavigator.Outcome? = null
            ListNavigator(card, state, screen) { outcome = it }.start()
            list.page = FakeList.Page.LIST
            return outcome
        }

        /**
         * Runs blocks [from] to [to] of a pass down the list for [wanted] ([HeroSweep]), each hero's page closed by the
         * macro's back tap: the hero each block took, null where it took none. [saved]: where [HeroIndex] last saw a name.
         */
        fun sweep(
            wanted: List<String>,
            pass: HeroSweep.State = HeroSweep.State(),
            from: Int = 1,
            to: Int = wanted.size,
            saved: (String) -> Int = { -1 }
        ): List<String?> = (from..to).map { b ->
            var outcome: ListNavigator.Outcome? = null
            val key = "hero-%03d".format(b)
            HeroSweep(
                pass,
                wanted,
                key,
                { list.heroes },
                saved,
                { card, approve, watch, then -> ListNavigator(card, state, screen, approve, watch, then).start() },
                note = { notes += it }
            ) { outcome = it }.start()
            list.page = FakeList.Page.LIST
            if (outcome is ListNavigator.Outcome.Tapped) pass.picked[key] else null
        }

        /** Finds [name] with [HeroSearch] from card [hint], or from card [near] when unknown: the card tapped and how it ended. */
        fun search(name: String, hint: Int, near: Int = -1): Pair<Int?, String> {
            var result: Pair<ListNavigator.Outcome, Int?>? = null
            HeroSearch(
                name,
                hint,
                { card, approve, then -> ListNavigator(card, state, screen, approve, finish = then).start() },
                note = { notes += it },
                near = near
            ) { o, card ->
                result =
                    o to card
            }.start()
            val (o, card) = result ?: return null to "no outcome"
            return tapped.lastOrNull() to (if (o is ListNavigator.Outcome.Tapped) "tapped $card" else o.toString())
        }
    }

    @Test fun keepsHeroNamesWithTheirPlacesOnTheList() {
        val e = HeroIndex.parse("0\tFlowborn\n3\tFlowborn\n7\tBolt Baron\n-1\tViolet\nx\tQi\n5\t12/100\n9\tChưa rõ tên\n")
        assertEquals(listOf("0 Flowborn", "3 Flowborn", "7 Bolt Baron", "-1 Qi", "-1 Violet"), e.map { "${it.index} ${it.name}" })
        assertEquals(listOf(7), HeroIndex.find(e, "Bolt Barom").map { it.index })
        assertEquals(listOf(0, 3), HeroIndex.find(e, "flowborn").map { it.index })
        assertEquals(listOf<Int>(), HeroIndex.find(e, "Violet").map { it.index })
        assertEquals(listOf("Bolt Baron", "Flowborn", "Qi", "Violet"), HeroIndex.names(e))
        // Seen on the list: placed, no longer a name alone; a card read again takes the name read.
        val placed = HeroIndex.put(HeroIndex.put(e, 8, "Violet"), 7, "Tamyn")
        assertEquals(listOf("0 Flowborn", "3 Flowborn", "7 Tamyn", "8 Violet", "-1 Qi"), placed.map { "${it.index} ${it.name}" })
        assertEquals(placed.map { "${it.index} ${it.name}" }, HeroIndex.parse(HeroIndex.format(placed)).map { "${it.index} ${it.name}" })
        // A name alone adds nothing for a hero already placed.
        assertEquals(5, HeroIndex.put(placed, -1, "Flowborn").size)
    }

    @Test fun findsAHeroByNameWhereverTheListPutsIt() {
        val names = List(61) { "Hero $it" }.toMutableList().also {
            it[47] = "Bolt Baron"
            it[3] = "Flowborn"
            it[0] = "Flowborn"
        }
        // Where it was last seen.
        assertEquals(47 to "tapped 47", Run(61, 2, 1.0..1.3, names = names).search("Bolt Baron", 47))
        // A new hero pushed it one card on: the card at the old place is another, and the list is searched.
        assertEquals(47 to "tapped 47", Run(61, 2, 1.0..1.3, names = names).search("Bolt Baron", 46))
        // Never seen: down the list from the top, read as ML Kit may, a letter off.
        assertEquals(47 to "tapped 47", Run(61, 2, 1.0..1.3, names = names).search("Bolt Barom", -1))
        // Pushed a row on, off the screen of the old place: found on the next screens.
        assertEquals(47 to "tapped 47", Run(61, 2, 1.0..1.3, names = names).search("Bolt Baron", 43))
        // Moved back towards the top: past the end of the list, then back up.
        assertEquals(47 to "tapped 47", Run(61, 2, 1.0..1.3, names = names).search("Bolt Baron", 56))
        // Two cards of one name: the one remembered.
        assertEquals(3 to "tapped 3", Run(61, 2, 1.0..1.3, names = names).search("Flowborn", 3))
        // Not on the list: to its end.
        val run = Run(61, 2, 1.0..1.3, names = names)
        val (card, end) = run.search("Tamyn", 10)
        assertEquals(null to "End", card to end.substringAfterLast('$').substringBefore('@'))
    }

    private fun assertCollectsAll(heroes: Int, seed: Int, slide: ClosedFloatingPointRange<Double>, startAt: Double = 0.0) {
        val (tapped, end) = Run(heroes, seed, slide, startAt).collect(heroes + 3)
        assertEquals("tapped cards", (0 until heroes).toList(), tapped)
        assertEquals("end at $heroes", end)
    }

    @Test fun oddLastRowIsCollectedOnceAndTheRunStopsAtTheEnd() = assertCollectsAll(61, 6, 1.0..1.3)

    @Test fun fullRosterWithFourCardsInTheLastRow() = assertCollectsAll(129, 1, 1.0..1.3)

    @Test fun shortListNeedsNoScroll() = assertCollectsAll(7, 4, 1.0..1.3)

    @Test fun startsMidListByReturningToTheTop() = assertCollectsAll(43, 8, 1.0..1.3, startAt = .6)

    /** If resting the finger does not stop the slide, swipes go 1.5-2.3 times as far; smaller steps must still keep count. */
    @Test fun keepsCountWhenSwipesSlideFarBeyondTheDrag() = assertCollectsAll(129, 3, 1.5..2.3)

    @Test fun shiftIsMeasuredBothWaysAndRejectedWithoutOverlap() {
        val list = FakeList(60, 9, 1.0..1.0)
        list.offset = 500.0
        val a = HeroGrid.profile(list.w, list.h, list::readRow)
        list.offset = 1250.0
        assertEquals(750, HeroGrid.shift(a, HeroGrid.profile(list.w, list.h, list::readRow)).px)
        list.offset = 320.0
        assertEquals(-180, HeroGrid.shift(a, HeroGrid.profile(list.w, list.h, list::readRow)).px)
        list.offset = 2000.0
        assertFalse(HeroGrid.shift(a, HeroGrid.profile(list.w, list.h, list::readRow)).reliable)
    }

    /** The run in the report stopped on Edras's page: the game had ignored the macro's back tap. */
    @Test fun returnsToTheListWhenTheGameIgnoredTheBackTap() {
        val run = Run(61, 5, 1.0..1.3)
        val (tapped, end) = run.collect(64, ignoredBack = setOf(4, 17, 40))
        assertEquals("tapped cards", (0 until 61).toList(), tapped)
        assertEquals("end at 61", end)
        assertEquals(3, run.backs)
        assertEquals(
            "each screen left with the arrow is kept for the log",
            listOf("not-list-1", "not-list-1", "not-list-1"),
            run.kept.filter {
                it.startsWith("not-list")
            }
        )
    }

    @Test fun waitsForAListThatIsStillAppearingInsteadOfTappingBack() {
        val run = Run(7, 4, 1.0..1.3).apply {
            list.page = FakeList.Page.HERO
            closingLooks = 1
        }
        assertEquals((0 until 7).toList() to "end at 7", run.collect(10))
        assertEquals(0, run.backs)
    }

    @Test fun doesNotTapWhereTheGameShowsNoBackArrow() {
        val run = Run(20, 2, 1.0..1.3).apply { list.page = FakeList.Page.LOBBY }
        val (tapped, end) = run.collect(5)
        assertTrue(tapped.isEmpty())
        assertEquals("failed at 0: Màn hình hiện tại không phải danh sách Tất cả tướng. Mở danh sách rồi bấm Tiếp tục.", end)
        assertEquals(0, run.backs)
        assertEquals(0, run.swipes)
    }

    /**
     * Crops of real 2400x1080 screens, placed at the top left of an otherwise black screen. They are binary
     * PPM files (gzipped), since unit tests compile against android.jar, which has no image decoder.
     */
    @Test fun backArrowIsFoundOnRealScreensOnly() {
        fun arrow(name: String): Boolean {
            val input = DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/screens/$name.ppm.gz")!!))

            fun token() = buildString {
                while (true) {
                    val c = input.readUnsignedByte().toChar()
                    if (c.isWhitespace()) {
                        if (isNotEmpty()) break
                    } else {
                        append(c)
                    }
                }
            }
            check(token() == "P6")
            val w = token().toInt()
            val h = token().toInt()
            check(token() == "255")
            val rgb = ByteArray(w * h * 3).also { input.readFully(it) }
            return HeroGrid.hasBackArrow(2400, 1080) { y, row ->
                for (x in row.indices) {
                    row[x] = if (x < w && y < h) {
                        val i = (y * w + x) * 3
                        (0xff shl 24) or ((rgb[i].toInt() and 255) shl 16) or ((rgb[i + 1].toInt() and 255) shl 8) or
                            (rgb[i + 2].toInt() and 255)
                    } else {
                        0xff000000.toInt()
                    }
                }
            }
        }
        for (name in listOf("back-arrow-list", "back-arrow-skill-panel", "back-arrow-edras")) assertTrue(name, arrow(name))
        for (name in listOf("no-arrow-hair", "no-arrow-currency", "no-arrow-card")) assertFalse(name, arrow(name))
    }

    @Test fun anotherScreenIsNotTakenForTheList() {
        val busy = IntArray(2400) { x -> (0xff shl 24) or (((x * 7919) and 255) shl 8) }
        assertFalse(HeroGrid.isGrid(2400, 1080) { _, row -> busy.copyInto(row) })
        val list = FakeList(20, 2, 1.0..1.0)
        assertTrue(HeroGrid.isGrid(list.w, list.h, list::readRow))
    }

    /**
     * A match with little overlap can beat the true move by chance: the chess hero list on the user's phone, dragged
     * 413 px, matched at 650 px, the edge of the search. Within the moves the drag can have made, it is never taken.
     */
    @Test fun aMoveBeyondTheDragsReachIsNotTaken() {
        val rows = 810
        val random = Random(7)
        val before = FloatArray(rows * 15) { random.nextInt(256).toFloat() }
        val after = FloatArray(rows * 15)
        for (y in 0 until rows) {
            for (k in 0 until 15) {
                after[y * 15 + k] = when {
                    // The edge rows happen to look like the screen far below.
                    y < 160 -> before[(y + 650) * 15 + k]
                    y + 413 < rows -> before[(y + 413) * 15 + k] + random.nextInt(-6, 7)
                    else -> random.nextInt(256).toFloat()
                }
            }
        }
        val anywhere = HeroGrid.shift(before, after)
        assertEquals(650, anywhere.px)
        assertTrue(anywhere.reliable)
        val reached = HeroGrid.shift(before, after, -32..518)
        assertTrue("${reached.px}", reached.px != 650)
    }

    /**
     * The user's run of 35 heroes by name stopped looking for Biron, card 1, after Bijan, card 102 of 130: a pick was
     * allowed 16 drags, and the top was 18 away. From that far down the list now goes to the top in a few flicks.
     */
    @Test fun aCardNearTheTopIsReachedFromTheTopNotDraggedUpTo() {
        val run = Run(130, 11, 1.0..1.02, heroList = true)
        assertTrue(run.pick(101) is ListNavigator.Outcome.Tapped)
        // Down from the top in drags of .6 of the screen: 14 for the 7.9 screens to card 102.
        assertTrue("${run.swipes - run.flicks}", run.swipes - run.flicks <= 15)
        val swipes = run.swipes
        val flicks = run.flicks
        assertTrue(run.pick(0) is ListNavigator.Outcome.Tapped)
        assertEquals(listOf<Int?>(101, 0), run.tapped)
        assertTrue(run.notes.any { it.contains("vuốt nhanh lên đầu") })
        // Flicks to the top and the one that shows it is there; not one drag up.
        assertEquals(run.swipes - swipes, run.flicks - flicks)
    }

    /** Without the way by the top, as on the chess lists, a pick is allowed the drags its way takes, not 16 and five a row. */
    @Test fun aLongWayUpIsAllowedTheDragsItTakes() {
        // Drags that move the list half as far as the finger: 27 up from card 102 to card 1.
        val run = Run(130, 11, .5..0.52)
        assertTrue(run.pick(101) is ListNavigator.Outcome.Tapped)
        val swipes = run.swipes
        val flicks = run.flicks
        assertEquals(ListNavigator.Outcome.Tapped, run.pick(0))
        assertTrue("${run.swipes - swipes}", run.swipes - swipes > 16)
        assertEquals(flicks, run.flicks)
        assertEquals(listOf<Int?>(101, 0), run.tapped)
    }

    @Test fun theTopIsTakenOnlyWhenItIsTheShorterWay() {
        val l = HeroGrid.DEFAULT_LAYOUT

        fun at(card: Int) = l.centre(card, 0.0).second - l.park
        // Bijan (card 102) to Biron (card 1).
        assertTrue(ListNavigator.nearerTheTop(l, 0, at(101)))
        assertEquals(14, ListNavigator.drags(l, 0, at(101), 1.0))
        // Aleister (card 89) to Allain (card 38): about as quick either way, so the drags up.
        assertFalse(ListNavigator.nearerTheTop(l, 37, at(88)))
        // Down the list, or a row up, never.
        assertFalse(ListNavigator.nearerTheTop(l, 101, 0.0))
        assertFalse(ListNavigator.nearerTheTop(l, 60, at(66)))
    }

    /** Each page passed was written as a 2 MB PNG on the thread that measures the next one; the hero list keeps the page it taps. */
    @Test fun theHeroListKeepsOnlyThePageItTapsOn() {
        val run = Run(130, 11, 1.0..1.02, heroList = true)
        run.pick(101)
        assertEquals(1, run.kept.size)
        // The chess encyclopedia's list still keeps every page.
        val chess = Run(130, 11, 1.0..1.02)
        chess.pick(101)
        assertTrue("${chess.kept}", chess.kept.size > 10)
    }

    @Test fun aHeroNeverSeenIsLookedForFromWhereTheListIs() {
        val names = List(130) { "Hero $it" }.toMutableList().also {
            it[47] = "Bolt Baron"
            it[112] = "Tamyn"
        }
        val below = Run(130, 4, 1.0..1.02, names = names)
        below.pick(80)
        val swipes = below.swipes
        assertEquals(112 to "tapped 112", below.search("Tamyn", -1, HeroSearch.middle(below.state.offset)))
        assertTrue("${below.swipes - swipes}", below.swipes - swipes <= 8)
        assertTrue(below.notes.any { it.contains("dò từ chỗ danh sách đang đứng") })
        // Above it: on to the end of the list, then back up.
        val above = Run(130, 4, 1.0..1.02, names = names)
        above.pick(80)
        assertEquals(47 to "tapped 47", above.search("Bolt Baron", -1, HeroSearch.middle(above.state.offset)))
    }

    /** Names of four letters, matched letter for letter: Aaro, Abro, ... */
    private fun name(card: Int) = "${'A' + card / 26}${'a' + card % 26}ro"

    private val roster = List(130) { name(it) }

    /** The cards of the user's 35 heroes on the list, in the order typed (Edras 69, Aleister 89, Allain 38, ...). */
    private val typed = listOf(
        68, 88, 37, 94, 77, 92, 114, 89, 101, 0, 123, 73, 65, 119, 79, 129, 112, 63, 91, 100, 127, 118, 107, 117, 57,
        80, 76, 85, 86, 64, 83, 105, 72, 84, 3
    )

    /**
     * The places saved before can be another account's, in another order: the pass reads the names on the way and
     * takes each hero as the list shows it, top first, whatever order they were typed in and wherever they were saved.
     */
    @Test fun heroesByNameAreTakenAsOnePassDownTheListMeetsThem() {
        val run = Run(130, 5, 1.0..1.02, names = roster, heroList = true)
        val elsewhere = { _: String -> 7 }
        val taken = run.sweep(typed.map { name(it) }, saved = elsewhere)
        assertEquals(typed.sorted().map { name(it) }, taken)
        assertEquals(typed.sorted(), run.tapped)
        // Down once: 7.9 screens to the last row in drags of .6, a few more to see the end, and the flicks to the top.
        assertTrue("${run.swipes - run.flicks}", run.swipes - run.flicks <= 18)
        assertTrue(run.notes.any { it.startsWith("Thấy Aaro ở ô 1") })
    }

    /** The run stopped after block 4 took its hero, and the app started again: the same hero, then on down. */
    @Test fun aPassContinuedLaterKeepsEachBlocksHero() {
        val wanted = typed.map { name(it) }
        val run = Run(130, 5, 1.0..1.02, names = roster, heroList = true)
        val pass = HeroSweep.State()
        run.sweep(wanted, pass, to = 4)
        val again = HeroSweep.State.parse(pass.json().toString())
        run.state.synced = false
        run.state.ref = null
        val rest = run.sweep(wanted, again, from = 4)
        assertEquals(typed.sorted().drop(3).map { name(it) }, rest)
        // Block 4's hero tapped twice, every other once.
        val sorted = typed.sorted()
        assertEquals(sorted.take(4) + sorted.drop(3), run.tapped)
    }

    /** A name the pass never read: looked for once at its saved place; one not on the list at all is left out. */
    @Test fun aHeroThePassDidNotReadIsLookedForOnceWhereItWasSaved() {
        val run = Run(130, 5, 1.0..1.02, names = roster, heroList = true)
        run.blind += 40
        // Asked after the end of the list; by then ML Kit reads the card.
        val saved = { n: String ->
            run.blind.clear()
            if (n == name(40)) 40 else -1
        }
        val pass = HeroSweep.State()
        val taken = run.sweep(listOf("Xyzw", name(40), name(90)), pass, saved = saved)
        assertEquals(listOf(name(90), null, name(40)), taken)
        assertEquals(listOf("Xyzw"), pass.missing)
        assertTrue(run.notes.any { it == "Đã đi hết danh sách; chưa thấy: Xyzw, ${name(40)}" })
        assertTrue(run.notes.any { it == "Không thấy Xyzw trong danh sách; bỏ qua tướng này" })
    }

    /** Under an unowned hero's name the game writes "Chưa có", and the account's UID is faint at the bottom. */
    @Test fun onlyTheNameLineIsTakenForACardsName() {
        val y = HeroGrid.ROW0 + HeroFinder.NAME_BELOW

        fun w(t: String, x: Double, at: Double) = GameScreens.Word(t, x - .04, at - .015, x + .04, at + .015)
        val words =
            listOf(w("Laville", .337, y), w("Chưa có", .337, y + .057), w("Chưa có", .459, y + .057), w("UID 1754638552", .459, .98))
        assertEquals(listOf(0 to "Laville"), HeroFinder.cards(words, 0.0).map { it.first to it.second.text })
        // The rows whose names a screen shows: two at the top; none past the last of 130.
        assertEquals(0..1, HeroSweep.rows(0.0, 130))
        assertTrue(HeroSweep.rows(30.0, 130).isEmpty())
    }

    /**
     * The user's run of 10/11 read no name past card 70: the app spaced the rows 450 px apart, the game 446.5, and the
     * names it read sat off the line it expected. Names off their grid together are read where they are, and the list's
     * offset is set right by them, so the pass goes on to the end with rows spaced either way.
     */
    @Test fun namesOffTheGridTogetherAreStillReadDownToTheEnd() {
        // Off by .0033 a row, as 450 px for 446.5; the list ends where its last row is drawn, the names of the first off most.
        for (perRow in listOf(.0033, -.0033)) {
            val run = Run(130, 5, 1.0..1.02, names = roster, heroList = true, lower = { (it - 25) * perRow })
            val taken = run.sweep(typed.map { name(it) })
            assertEquals("$perRow", typed.sorted().map { name(it) }, taken)
            assertEquals("$perRow", typed.sorted(), run.tapped)
        }
    }

    @Test fun namesAreTakenOnTheLineMostOfThemShare() {
        // The list .08 further down than counted: every name .08 higher than expected, and text on a card's art.
        val y = HeroGrid.ROW0 + HeroGrid.PITCH + HeroFinder.NAME_BELOW - .08

        fun w(t: String, x: Double, at: Double) = GameScreens.Word(t, x - .04, at - .015, x + .04, at + .015)
        val words = listOf(w("Preyta", .337, y), w("Krizzix", .459, y), w("Yan", .580, y), w("Bolt Baron", .702, y), w("AIC", .823, y - .2))
        val read = HeroFinder.read(words, 0.0)
        assertEquals(listOf(5 to "Preyta", 6 to "Krizzix", 7 to "Yan", 8 to "Bolt Baron"), read.cards.map { it.first to it.second.text })
        assertEquals(-.08, read.line!!, 1e-9)
        // Two heroes a letter apart are two heroes; a letter misread in a name is the same name.
        assertFalse(HeroFinder.same("keera", "veera"))
        assertTrue(HeroFinder.same("superman", "supenman"))
        assertTrue(HeroFinder.same("kahlii", "kahlij"))
    }

    /** Each block is the next hero met down the list: no name or saved place in its step. */
    @Test fun namedHeroesAreBlocksOfOnePassDownTheList() {
        val typedNames = listOf("Edras", "Aleister", "Allain", "Aya", "Biron", "Bijan")
        val json = org.json.JSONObject(BuiltInMacros.named(typedNames))
        assertTrue(json.getBoolean("sweep"))
        assertEquals(typedNames, BuiltInMacros.heroNames(json))
        val steps = json.getJSONArray("steps")
        val finds = (0 until steps.length()).map { steps.getJSONObject(it) }.filter { it.getString("type") == "find" }
        assertEquals((1..6).map { "hero-%03d".format(it) }, finds.map { it.getString("key") })
        assertTrue(finds.all { it.getBoolean("sweep") && !it.has("name") && !it.has("hint") })
        // Each block's steps still end where the next block's "find" begins.
        finds.dropLast(1).zip(finds.drop(1)).forEach { (a, b) -> assertEquals(b, steps.getJSONObject(a.getInt("skipTo"))) }
        Macro(json.toString())
    }

    /** The user's run of 10/11 asked for "Superman" and "Supenman": both read Superman's card, and he was taken twice. */
    @Test fun aNameReadAsOneAlreadyAskedForIsOneHero() {
        val json = org.json.JSONObject(BuiltInMacros.named(listOf("Superman", "Aya", "Supenman", "Keera", "Veera")))
        assertEquals(listOf("Superman", "Aya", "Keera", "Veera"), BuiltInMacros.heroNames(json))
        // A run made before: the block after Superman's does not take him again.
        val pass = HeroSweep.State()
        pass.picked["hero-001"] = "Superman"
        assertEquals(listOf("Aya"), pass.left(listOf("Superman", "Aya", "Supenman")))
    }
}
