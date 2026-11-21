package vn.ndang.aovcollector

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ndang.aovcollector.BoardScreens.Page
import vn.ndang.aovcollector.BoardScreens.Tab

class BoardTest {
    /** Screenshots from the user's phone (2400x1080), the UID line and players' names and pictures blurred. */
    private fun pixels(name: String, extension: String = "jpg"): Triple<Int, Int, IntArray> {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/board-$name.$extension")!!.use {
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

    @Test fun findsTheRowsOfTheHeroGrid() {
        val top = pixels("picker")
        val bands = BoardScreens.rows(top.first, top.second, rows(top))
        // Three whole rows, 0.1768 apart; the sliver of a fourth at the window's bottom is too thin to count.
        assertEquals(.3426, bands[0].first, .004)
        assertEquals(.5194, bands[1].first, .004)
        assertEquals(.6963, bands[2].first, .004)
        assertTrue(bands.take(3).all { abs(it.second - it.first - BoardScreens.CARD) < .005 })
        assertEquals(3, bands.size)
        for (col in 0 until 8) {
            assertTrue(
                "column $col",
                BoardScreens.card(top.first, top.second, col, bands[0].first, bands[0].second, rows(top))
            )
        }
        // Scrolled a little: the same rows 0.047 higher.
        val scrolled = pixels("picker-scrolled")
        val moved = BoardScreens.rows(scrolled.first, scrolled.second, rows(scrolled))
        assertEquals(.2954, moved[0].first, .004)
        assertEquals(.4704, moved[1].first, .004)
    }

    @Test fun finalRowWithOneToFourHeroesStillHasAVisibleBand() {
        val width = 1536
        val height = 691
        for (lastCount in 1..4) {
            val pixels = IntArray(width * height) { 0xff33337e.toInt() }

            fun draw(row: Int, cards: Int) {
                val top = ((.3426 + row * BoardScreens.ROW_PITCH) * height).toInt()
                val bottom = (top + BoardScreens.CARD * height).toInt()
                for (col in 0 until cards) {
                    val cx = (BoardScreens.COLUMNS[col] * width).toInt()
                    val half = (.022 * width).toInt()
                    for (y in top until bottom) {
                        for (x in cx - half..cx + half) {
                            pixels[y * width + x] = 0xffd8a842.toInt()
                        }
                    }
                }
            }
            draw(0, 8)
            draw(1, lastCount)
            val frame = Triple(width, height, pixels)
            val detected = BoardScreens.rows(width, height, rows(frame))
            assertEquals("last row with $lastCount hero(es)", 2, detected.size)
            assertTrue(BoardScreens.card(width, height, 0, detected[1].first, detected[1].second, rows(frame)))
            for (col in 1 until 8) {
                assertEquals(
                    "final row $lastCount, col $col",
                    col < lastCount,
                    BoardScreens.card(width, height, col, detected[1].first, detected[1].second, rows(frame))
                )
            }
        }
    }

    @Test fun portraitShiftMeasuresRealMovementNotRepeatingBorders() {
        val original = pixels("picker")
        val moved = pixels("picker-scrolled")
        val source = BoardPickerMotion.profile(original.first, original.second, rows(original))
        val shifted = BoardPickerMotion.profile(moved.first, moved.second, rows(moved))
        val distance = BoardPickerMotion.displacement(source, shifted, original.second, 1)
        assertTrue("must measure 1-row partial movement from actual art: $distance", distance != null && distance in .03..0.07)
        assertFalse(BoardPickerMotion.sameViewport(source, shifted))
        assertTrue(BoardPickerMotion.sameViewport(source, source.copyOf()))
        assertTrue(BoardPickerMotion.validTop(BoardScreens.rows(original.first, original.second, rows(original))))
        assertEquals(null, BoardPickerMotion.occupiedPrefix(listOf(true, false, true, false, false, false, false, false)))
        assertEquals(1, BoardPickerMotion.occupiedPrefix(listOf(true, false, false, false, false, false, false, false)))
        assertEquals(8, BoardPickerMotion.occupiedPrefix(List(8) { true }))
    }

    @Test fun theRankedPageIsNeverTakenForTheLobby() {
        // Its own "Đấu hạng" sits where the lobby's is: a tap meant for the lobby there would start a match.
        for (name in listOf("ranked", "ranked-new")) {
            val p = pixels(name)
            assertFalse(name, GameScreens.isLobby(p.first, p.second, rows(p)))
        }
        assertEquals(Page.RANKED, BoardScreens.page(ranked, true))
        assertEquals(Page.OTHER, BoardScreens.page(listOf(w("ĐẤU HẠNG", .85, .9)), true))
    }

    @Test fun aListScrolledByARowIsTold() {
        val p = pixels("list")
        val a = BoardScreens.signature(p.first, p.second, rows(p))
        assertTrue(BoardScreens.same(a, BoardScreens.signature(p.first, p.second, rows(p))))
        val shifted = Triple(
            p.first,
            p.second,
            IntArray(p.third.size) {
                val y =
                    it / p.first + 165
                if (y < p.second) p.third[y * p.first + it % p.first] else 0
            }
        )
        assertFalse(BoardScreens.same(a, BoardScreens.signature(shifted.first, shifted.second, rows(shifted))))
    }

    private fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .015, x + .04, y + .015)

    private val ranked =
        listOf(w("Thời hạn mùa giải", .2, .141), w("Đấu đỉnh cao", .16, .26), w("Chiến khu", .15, .56), w("Đấu hạng", .795, .867))
    private val pickerWords = listOf(w("Chọn tướng", .2, .117), GameScreens.Word("Đã nhận", .759, .212, .802, .242))

    /** Synthetic square/tick drawn at runtime. No pixels from the new user attachments are published. */
    private fun syntheticFilter(checked: Boolean = true): Triple<Int, Int, IntArray> {
        val p = IntArray(2048 * 922) { 0xff33337e.toInt() }
        for (y in 194..228) {
            for (x in 1651..1685) {
                p[y * 2048 + x] =
                    if (x < 1654 || x > 1682 || y < 197 || y > 225) 0xff4c61c2.toInt() else 0xff292765.toInt()
            }
        }

        fun stroke(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (i in 0..100) {
                val x = x0 + (x1 - x0) * i / 100
                val y = y0 + (y1 - y0) * i / 100
                for (dy in -2..2) for (dx in -2..2) p[(y + dy) * 2048 + x + dx] = 0xffc9f5ff.toInt()
            }
        }
        if (checked) {
            stroke(1656, 211, 1665, 220)
            stroke(1665, 220, 1679, 203)
        }
        return Triple(2048, 922, p)
    }

    @Test fun detectsOwnedTickOnLegacyFixtureAndSyntheticSizes() {
        for (source in listOf(pixels("picker"), syntheticFilter())) {
            for (scale in listOf(.5, 1.0, 1.5)) {
                val width = (source.first * scale).toInt()
                val height = (source.second * scale).toInt()
                val p = Triple(
                    width,
                    height,
                    IntArray(width * height) {
                        val x = it % width
                        val y = it / width
                        source.third[(y * source.second / height) * source.first + x * source.first / width]
                    }
                )
                val filter = BoardScreens.ownedFilter(width, height, pickerWords, rows(p))
                assertTrue("${source.first} at $scale: $filter", filter?.checked == true)
                assertEquals(.814, filter!!.x, .004)
                assertEquals(.229, filter.y, .006)
            }
        }
    }

    @Test fun requiresAVisibleEmptyBoxBeforeCallingOwnedFilterOff() {
        val off = syntheticFilter(false)
        assertEquals(false, BoardScreens.ownedFilter(off.first, off.second, pickerWords, rows(off))?.checked)
        assertNull(BoardScreens.ownedFilter(off.first, off.second, pickerWords.take(1), rows(off)))
        assertNull(BoardScreens.ownedFilter(off.first, off.second, pickerWords.drop(1), rows(off)))
        val blank = Triple(off.first, off.second, IntArray(off.third.size) { 0xff33337e.toInt() })
        assertNull(BoardScreens.ownedFilter(blank.first, blank.second, pickerWords, rows(blank)))
        // A large accessibility parent mentioning the label is not a checkbox anchor.
        assertNull(
            BoardScreens.ownedFilter(
                off.first,
                off.second,
                pickerWords.take(1) + GameScreens.Word("Đã nhận", 0.0, 0.0, 1.0, 1.0),
                rows(off)
            )
        )
    }

    @Test fun acceptsTheObservedFinalLetterOcrErrorOnlyWithAVisibleCheckbox() {
        val words = pickerWords.take(1) + GameScreens.Word("Đã nhậi", .759, .212, .802, .242)
        for (checked in listOf(true, false)) {
            val p = syntheticFilter(checked)
            assertEquals(checked, BoardScreens.ownedFilter(p.first, p.second, words, rows(p))?.checked)
        }
        val blank = Triple(2048, 922, IntArray(2048 * 922) { 0xff33337e.toInt() })
        assertNull(BoardScreens.ownedFilter(blank.first, blank.second, words, rows(blank)))
    }

    @Test fun readsTheSeasonScreensAsMlKitDoes() {
        // As read on the emulator from the user's screenshots.
        assertEquals("An mở Isử mùa", BoardScreens.season(listOf(w("HỐLÚC MÙA XUÂN", .511, .451), w("An mở Isử mùa", .5, .917)))!!.text)
        assertEquals(.853, BoardScreens.season(listOf(w("Mở mùa mới nhận Ruby *50", .5, .707), w("Mở mùa mới", .5, .853)))!!.y, .001)
        assertEquals(Page.SEASON, BoardScreens.page(listOf(w("An màn hình dế tiếp tục", .498, .805)), false))
        assertEquals(
            Page.RANKED,
            BoardScreens.page(
                listOf(
                    w("Thời hạn mua giá", .191, .14),
                    w("Đầu đinh cao", .159, .257),
                    w("7 Chiến khu", .142, .558),
                    w("Đấu hang", .796, .867)
                ),
                false
            )
        )
        // The province list with pieces of the dialog's lines read among it.
        val r = BoardScreens.region(
            listOf(
                w("Việt Nam", .345, .484),
                w("Mien Trung", .49, .485),
                w("Khu vực", .64, .485),
                w("Thanh Hóa", .648, .544),
                w("Xác hn", .586, .782),
                w("Nghệ An", .648, .602),
                w("ainh sửa vào", .596, .699),
                w("Khánh Hòa", .648, .878)
            )
        )
        assertEquals(2, r.open)
        assertEquals(listOf("thanhhoa", "nghean", "khanhhoa"), r.options.map { it.plain })
    }

    @Test fun readsTheRegionDialog() {
        val dialog = listOf(w("Vào chiến khu", .33, .191), w("Chưa chỉnh", .41, .789), w("Xác nhận", .587, .789))
        val start = BoardScreens.region(dialog + w("Quốc gia", .344, .486))
        assertEquals("quocgia", start.boxes[0])
        assertNull(start.open)
        val area = BoardScreens.region(
            dialog +
                listOf(
                    w("Việt Nam", .344, .486),
                    w("Khu vực", .49, .486),
                    w("Miền Bắc", .5, .544),
                    w("Miền Trung", .5, .6),
                    w("Miền Nam", .5, .656),
                    w("Khác", .5, .711)
                )
        )
        assertEquals(1, area.open)
        assertEquals(listOf("mienbac", "mientrung", "miennam", "khac"), area.options.map { it.plain })
        assertEquals(.587, area.confirm!!.x, .001)
    }

    /**
     * The game from the lobby to the ranking: [seasons] review screens on the way, the region dialog when [regionSet]
     * is false, [heroes] cards in "Chọn tướng", lists [length] screens long.
     */
    private class Game(
        var page: Page = Page.LOBBY,
        val heroes: Int = 10,
        val seasons: Boolean = false,
        var regionSet: Boolean = true,
        val length: Double = 1.0,
        var gridScroll: Double = 0.0,
        val cut: Int = Int.MAX_VALUE,
        var owned: Boolean? = false,
        val ownedHeroes: Int = 14,
        val ignoreFilterTaps: Boolean = false,
        val ownedOnReopen: Boolean = false,
        val ignorePickerTaps: Boolean = false,
        var allSelected: Boolean = true,
        val reselectOtherTab: Boolean = false,
        // Each list's players; looks a player's page takes to load; ranks whose eye opens no UID box.
        val players: Int = 60,
        val profileLoads: Int = 0,
        val noBox: Set<Int> = emptySet(),
        // Android keeps the clipboard from the app: every read gives nothing.
        val hiddenClipboard: Boolean = false,
        // The card still open on the way back; the list back at its top after a player's page.
        val cardStays: Boolean = true,
        val resetOnBack: Boolean = false,
        /** Chọn tướng opens at its top every time, as the user's game does once Đã nhận is cleared. */
        val pickerOpensAtTop: Boolean = false
    ) : BoardReader.Screen {
        var looks = 0
        var outcome: BoardReader.Outcome? = null
        val kept = ArrayList<String>()
        val taps = ArrayList<Pair<Double, Double>>()
        val chosen = ArrayList<Int>()
        val finished = ArrayList<Int>()
        val names = HashMap<Int, String?>()
        val pending = ArrayDeque<() -> Unit>()
        var filterTaps = 0
        var gridDrags = 0
        var shortSlowDrags = 0
        var allTaps = 0
        override val alive get() = outcome == null && looks < cut

        // The season's review: its screens in turn, the trailer showing no words.
        var season = if (seasons) {
            listOf(
                "Ấn mở l.sử mùa" to (.5 to .92), "Bước kế" to (.87 to .913), "Bước kế" to (.87 to .913),
                "Mở mùa mới" to (.5 to .855),
                "Ấn màn hình để tiếp tục" to (.5 to .936), "" to (0.0 to 0.0), "" to (0.0 to 0.0), "Ấn màn hình để tiếp tục" to (.5 to .8),
                "Ấn màn hình để tiếp tục" to (.5 to .95)
            )
        } else {
            emptyList()
        }
        var seasonAt = 0

        // The region dialog.
        var country: String? = null
        var area: String? = null
        var province: String? = null
        var open = -1
        var provinceScroll = 0
        val provinces =
            listOf("Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Trị", "Đà Nẵng", "Quảng Ngãi", "Khánh Hòa", "Gia Lai", "Đắk Lắk", "Lâm Đồng")

        // The ranking.
        var hero = 0
        var tab = 0
        var offset = 0.0

        // A player: the card open over the list, their page, the eye's box over it, and how long the page has loaded.
        var card: Int? = null
        var profileOf: Int? = null
        var box = false
        var profileLooks = 0
        val opened = ArrayList<Int>()
        val notes = ArrayList<String>()

        // What the game's copy icons put on the clipboard, and the names and UIDs the reader recorded.
        var clipboard: String? = null
        val ids = LinkedHashMap<String, Triple<String?, String?, Boolean>>()

        override fun clearClipboard() {
            clipboard = null
        }

        override fun copied(then: (String?) -> Unit) {
            pending.addLast { then(if (hiddenClipboard) null else clipboard) }
        }

        override fun playerIds(key: String, name: String?, uid: String?, uidCopied: Boolean) {
            ids[key] = Triple(name, uid, uidCopied)
        }

        private fun uidOf(rank: Int) = "120000000000%04d".format(rank)

        // Whether each page and picture kept had finished loading when it was taken.
        val loadedShots = ArrayList<Boolean>()

        override fun note(text: String) {
            notes += text
        }

        private fun rowY(rank: Int) = BoardPlayerPixels.ROW0 + (rank - 1) * BoardPlayerPixels.PITCH - offset

        /** The list's picture column as it scrolls: the same content at the same place in the list, different elsewhere. */
        private fun listProfile(h: Int): FloatArray {
            val y0 = (BoardPlayerPixels.LIST_TOP * h).toInt()
            val y1 = (BoardPlayerPixels.LIST_BOTTOM * h).toInt()
            val out = FloatArray((y1 - y0) * 15)
            for (y in y0 until y1) {
                // Each pixel row of the list has its own colours, which move with it when the list scrolls.
                val world = kotlin.math.floor(y + offset * h).toInt()
                for (k in 0 until 15) {
                    var x = world * 73856093 xor k * 19349663 xor (tab * 31 + hero) * 83492791
                    x = x xor (x ushr 13)
                    x *= 0x5bd1e995
                    x = x xor (x ushr 15)
                    out[(y - y0) * 15 + k] = ((x and 0x7fffffff) % 160 + 40).toFloat()
                }
            }
            return out
        }

        private fun facts(): BoardPlayerPixels.Facts {
            val h = 540
            val list = page == Page.BOARD
            val rank = profileOf ?: 0
            val pageLoaded = page == Page.PROFILE && profileLooks > profileLoads
            return BoardPlayerPixels.Facts(
                height = h,
                rows = if (list) {
                    (1..players).map {
                        rowY(it)
                    }.filter { it in BoardPlayerPixels.ROW_TOP..BoardPlayerPixels.ROW_BOTTOM }
                } else {
                    emptyList()
                },
                profile = if (list) listProfile(h) else FloatArray(0),
                card = list && card != null,
                panelLum = if (page == Page.PROFILE) 76.0 else 78.0,
                panel = when {
                    page != Page.PROFILE -> IntArray(192)
                    // Loading: it changes from one look to the next.
                    !pageLoaded -> IntArray(192) { profileLooks * 13 + it }
                    else -> IntArray(192) { rank * 3 + it % 7 }
                },
                stats = pageLoaded,
                avatarDetail = if (pageLoaded) 9.5 else .3,
                photo = IntArray(256) { 40 },
                photoDetail = .1,
                uidCopy = if (page == Page.PROFILE && box) .566 else null
            )
        }

        private fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .04, y - .015, x + .04, y + .015)

        private val rowZero = .3426
        private val visibleHeroes get() = if (owned == true) minOf(heroes, ownedHeroes) else heroes
        private val rowCount get() = (visibleHeroes + 7) / 8
        private val maxScroll get() = maxOf(
            0.0,
            rowZero + (rowCount - 1) * BoardScreens.ROW_PITCH + BoardScreens.CARD + .02 - BoardScreens.VIEW_BOTTOM
        )

        fun words(): List<GameScreens.Word> = when (page) {
            Page.LOBBY -> listOf(w("Túi đồ", .15, .92), w("Tướng", .27, .92), w("ĐẤU HẠNG", .85, .9))
            Page.RANKED -> listOf(
                w("Thời hạn mùa giải", .2, .141),
                w("Đấu đỉnh cao", .16, .26),
                w("Chiến khu", .15, .464),
                w("Đấu hạng", .795, .867)
            )
            Page.SEASON -> season[seasonAt].let { (t, at) -> if (t.isEmpty()) emptyList() else listOf(w(t, at.first, at.second)) }
            Page.REGION -> buildList {
                add(w("Vào chiến khu", .33, .191))
                add(w("Chưa chỉnh", .41, .789))
                add(w("Xác nhận", .587, .789))
                add(w(country ?: "Quốc gia", .344, .486))
                if (country !=
                    null
                ) {
                    add(w(area ?: "Khu vực", .49, .486))
                }
                if (area != null) add(w(province ?: "Khu vực", .64, .486))
                when (open) {
                    0 -> add(w("Việt Nam", .352, .544))
                    1 -> listOf("Miền Bắc", "Miền Trung", "Miền Nam", "Khác").forEachIndexed { i, t -> add(w(t, .5, .544 + .0556 * i)) }
                    2 -> provinces.drop(provinceScroll).take(7).forEachIndexed { i, t -> add(w(t, .648, .544 + .0556 * i)) }
                }
            }
            Page.BOARD -> listOf(
                w("Chiến khu", .17, .057),
                w("Đắk Lắk", .43, .146),
                w("Miền Trung", .53, .146),
                w("Việt Nam", .645, .146),
                w("Toàn máy chủ", .75, .146),
                w("Hero$hero", .1575, .569),
                w("L.sử lực chiến cao nhất", .158, .667)
            )
            Page.PICKER -> listOf(w("Chọn tướng", .2, .117), w("Tất cả", .215, .227), w("Đã nhận", .78, .227))
            Page.PROFILE -> listOf(w("Thông tin kiện tướng", .215, .056), w("Số 1 máy chủHayate", .17, .34), w("Trận", .124, .75)) +
                if (box) listOf(GameScreens.Word("UID " + uidOf(profileOf ?: 0), .415, .331, .555, .361)) else emptyList()
            Page.TOP -> listOf(
                w("Số 1 máy chủ", .5, .08),
                w("Top chiến lực", .5, .157),
                w("Miền Trung", .27, .163),
                w("Hero$hero", .5, .74),
                w("Đổi tướng", .5, .853)
            )
            else -> emptyList()
        }

        private fun bands(): List<Pair<Double, Double>> = (0 until rowCount).mapNotNull { r ->
            val top = rowZero + r * BoardScreens.ROW_PITCH - gridScroll
            val bottom = top + BoardScreens.CARD
            if (bottom <= BoardScreens.VIEW_TOP ||
                top >= BoardScreens.VIEW_BOTTOM
            ) {
                null
            } else {
                maxOf(top, BoardScreens.VIEW_TOP) to minOf(bottom, BoardScreens.VIEW_BOTTOM)
            }
        }

        private fun rowAt(y: Double) = floor((y - rowZero + gridScroll) / BoardScreens.ROW_PITCH).toInt()

        /**
         * Different heroes have different RGB artwork, even though every card's border and
         * vertical pitch are identical. A profile made only from row geometry would repeat.
         * This synthetic scrollable artwork shifts by the ACTUAL clamped gridScroll.
         */
        private fun portraits(): FloatArray {
            val h = 540
            val y0 = (BoardScreens.VIEW_TOP * h).toInt()
            val y1 = (BoardScreens.VIEW_BOTTOM * h).toInt()
            val out = FloatArray((y1 - y0) * 15)
            for (y in y0 until y1) {
                val world = y + gridScroll * h
                val relative = world - rowZero * h
                val rowId = floor(relative / (BoardScreens.ROW_PITCH * h)).toInt()
                val local = relative - rowId * BoardScreens.ROW_PITCH * h
                for (col in 0..4) {
                    val portrait = rowId >= 0 &&
                        rowId < rowCount &&
                        rowId * 8 + col < visibleHeroes &&
                        local >= 0.0 &&
                        local < BoardScreens.CARD * h
                    for (channel in 0..2) {
                        val at = (y - y0) * 15 + col * 3 + channel
                        out[at] = if (!portrait) {
                            43.0f + channel * 6
                        } else {
                            val seed = (rowId * 53 + col * 83 + channel * 41) % 125
                            (
                                65.0 + seed + 18.0 * kotlin.math.sin(local / 13 + col + channel) +
                                    7.0 * kotlin.math.cos(local / 23 + channel)
                                ).toFloat()
                        }
                    }
                }
            }
            return out
        }

        override fun look(words: Boolean, grid: Boolean, then: (BoardReader.Look?) -> Unit) {
            looks++
            if (page == Page.PROFILE) profileLooks++
            val seen = facts()
            val loaded = page == Page.PROFILE && profileLooks > profileLoads
            val shown = if (words) words() else emptyList()
            val bands = if (page == Page.PICKER) bands() else emptyList()
            val signature = IntArray(8) { (hero * 100000 + tab * 10000 + (offset * 1000).toInt()) }
            then(object : BoardReader.Look {
                override val words = shown
                override val lobby = page == Page.LOBBY
                override val signature = signature
                override val rows = bands
                override val ownedFilter = if (page == Page.PICKER) owned?.let { BoardScreens.OwnedFilter(it, .814, .229) } else null
                override val allSelected = page == Page.PICKER && this@Game.allSelected
                override val pickerProfile = if (page == Page.PICKER) portraits() else FloatArray(0)
                override val pickerHeight = 540

                override fun card(col: Int, top: Double, bottom: Double): Boolean {
                    val mid = (top + bottom) / 2
                    val row = rowAt(mid)
                    val local = mid - rowZero + gridScroll - row * BoardScreens.ROW_PITCH
                    return col in 0..7 &&
                        row >= 0 &&
                        row * 8 + col < visibleHeroes &&
                        local in 0.0..BoardScreens.CARD
                }

                override val facts = seen

                override fun done(keep: String?) {
                    if (keep != null) kept += keep
                    if (keep != null && keep.contains("-hang-")) loadedShots += loaded
                }
            })
        }

        val calibrationActions = ArrayList<CalibrationAction>()

        override fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) {
            calibrationActions.add(action)
            super.calibrated(action, then)
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            taps += x to y
            val hit = words().firstOrNull { abs(it.x - x) < .01 && abs(it.y - y) < .01 }
            when (page) {
                Page.LOBBY -> if (hit?.plain == "dauhang") page = if (seasons) Page.SEASON else Page.RANKED
                Page.SEASON -> if (hit != null) {
                    seasonAt++
                    if (seasonAt >= season.size) page = Page.RANKED
                }
                Page.RANKED -> if (hit?.plain == "chienkhu") page = if (regionSet) Page.BOARD else Page.REGION
                Page.REGION -> {
                    val col = BoardScreens.REGION_X.indices.firstOrNull { abs(BoardScreens.REGION_X[it] - x) < .02 }
                    when {
                        hit != null && abs(hit.y - BoardScreens.REGION_Y) > .03 && hit.plain != "xacnhan" && hit.plain != "chuachinh" -> {
                            when (open) {
                                0 ->
                                    country =
                                        hit.text
                                1 -> area = hit.text
                                2 -> province = hit.text
                            }
                            open = -1
                        }
                        hit?.plain == "xacnhan" -> if (province != null) {
                            regionSet = true
                            page = Page.BOARD
                        }
                        col != null && abs(y - BoardScreens.REGION_Y) < .01 -> open = col
                    }
                }
                Page.BOARD -> when {
                    // An open card takes the tap: its person icon opens the player's page, a tap beside it closes it.
                    card != null -> if (abs(x - BoardPlayerPixels.PROFILE_X) < .01 && abs(y - BoardPlayerPixels.PROFILE_Y) < .01) {
                        profileOf = card
                        page = Page.PROFILE
                        profileLooks = 0
                        box = false
                        if (!cardStays) card = null
                    } else {
                        check(x < .77) { "a card's add-friend or report icon tapped" }
                        card = null
                    }
                    abs(x - BoardPlayerPixels.ROW_X) < .01 -> (1..players).firstOrNull { abs(rowY(it) - y) < .03 }?.let {
                        card = it
                        opened += it
                    }
                    abs(x - BoardScreens.SWAP_X) < .01 && abs(y - BoardScreens.SWAP_Y) < .01 -> if (!ignorePickerTaps) {
                        page = Page.PICKER
                        if (pickerOpensAtTop) gridScroll = 0.0
                        if (ownedOnReopen) {
                            owned = true
                        }
                        if (reselectOtherTab) allSelected = false
                    }
                    abs(y - BoardScreens.TAB_Y) < .01 -> {
                        tab = Tab.values().indexOfFirst { abs(it.x - x) < .01 }
                        offset = 0.0
                    }
                    abs(x - Tab.TOP.x) < .01 && abs(y - Tab.TOP.y) < .01 -> page = Page.TOP
                }
                // Its own "Đổi tướng" must never be tapped: the hero is chosen on the ranking.
                Page.TOP -> {
                    check(abs(y - .853) > .03) { "Đổi tướng tapped" }
                    if (abs(x - BoardScreens.TOP_BACK_X) < .01 &&
                        abs(y - BoardScreens.TOP_BACK_Y) < .01
                    ) {
                        page = Page.BOARD
                    }
                }
                Page.PICKER -> when {
                    abs(x - .215) < .02 && abs(y - .227) < .02 -> {
                        allSelected = true
                        allTaps++
                        gridScroll = 0.0
                    }
                    abs(x - BoardScreens.PICKER_CLOSE_X) < .01 && abs(y - BoardScreens.PICKER_CLOSE_Y) < .01 -> page = Page.BOARD
                    abs(x - .814) < .01 && abs(y - .229) < .01 -> {
                        filterTaps++
                        if (!ignoreFilterTaps) {
                            owned = owned?.not()
                            gridScroll = 0.0
                        }
                    }
                    y > BoardScreens.VIEW_TOP && y < BoardScreens.VIEW_BOTTOM -> {
                        val col = BoardScreens.COLUMNS.indexOfFirst { abs(it - x) < .01 }
                        val i =
                            rowAt(y) * 8 + col
                        if (col >= 0 && i < visibleHeroes) {
                            hero = i
                            chosen += i
                            page = Page.BOARD
                            tab = 0
                            offset = 0.0
                        }
                    }
                }
                // The eye's box takes the next tap: its copy icon copies the UID, anything else closes it. Without it,
                // the name's copy icon copies the name, the eye opens the box, the arrow goes back.
                Page.PROFILE -> when {
                    box -> if (abs(x - .566) < .006 && abs(y - .346) < .02) clipboard = uidOf(profileOf ?: 0) else box = false
                    abs(x - BoardPlayerPixels.NAME_COPY_X) < .006 && abs(y - BoardPlayerPixels.NAME_COPY_Y) < .01 ->
                        clipboard = "Người chơi $profileOf"
                    abs(x - BoardPlayerPixels.EYE_X) < .006 && abs(y - BoardPlayerPixels.EYE_Y) < .01 -> box = profileOf !in noBox
                    abs(x - BoardPlayerPixels.BACK_X) < .01 && abs(y - BoardPlayerPixels.BACK_Y) < .01 -> {
                        profileOf = null
                        page = Page.BOARD
                        if (resetOnBack) offset = 0.0
                    }
                    else -> check(false) { "a tap on the player's page at $x, $y" }
                }
                else -> Unit
            }
            pending.addLast { then(true) }
        }

        override fun drag(x: Double, from: Double, to: Double, fling: Boolean, then: (Boolean) -> Unit) {
            when (page) {
                // As on the user's phone: a short slow drag resting on a card chooses that hero and closes the picker.
                Page.PICKER -> if (!fling && abs(from - to) < BoardReader.MIN_SLOW_DRAG - .001) {
                    shortSlowDrags++
                    page = Page.BOARD
                } else {
                    gridDrags++
                    gridScroll = if (fling && to > from) 0.0 else (gridScroll + (from - to)).coerceIn(0.0, maxScroll)
                }
                Page.BOARD -> offset = minOf(length, offset + (from - to))
                Page.REGION -> if (open == 2) provinceScroll = minOf(provinces.size - 7, provinceScroll + 3)
                else -> Unit
            }
            pending.addLast { then(true) }
        }

        override fun later(ms: Long, then: () -> Unit) {
            if (page == Page.SEASON &&
                season[seasonAt].first.isEmpty()
            ) {
                seasonAt++
            }
            pending.addLast(then)
        }

        override fun hero(index: Int, name: String?) {
            names[index] = name
        }

        override fun finished(index: Int) {
            finished += index
        }

        fun run(
            heroesWanted: Int = 300,
            wanted: List<BoardScreens.Want> = Tab.values().map {
                BoardScreens.Want(it, 3)
            },
            first: Int = 0,
            openPlayers: Boolean = false,
            donePlayers: Set<String> = emptySet()
        ): Game {
            BoardReader(this, heroesWanted, wanted, first, openPlayers, donePlayers) { outcome = it }.start()
            var guard = 0
            while (outcome == null && looks < cut) {
                (pending.removeFirstOrNull() ?: break).invoke()
                check(++guard < 200000)
            }
            return this
        }
    }

    @Test fun goesFromTheLobbyThroughTheSeasonAndTheRegionToEveryHerosRanking() {
        // Top five: two screens a tab.
        val game = Game(seasons = true, regionSet = false, heroes = 10).run(wanted = Tab.values().map { BoardScreens.Want(it, 5) })
        assertEquals(10, (game.outcome as BoardReader.Outcome.Done).heroes)
        val cards = game.calibrationActions.filter { it.id == "board.v1.picker.card" }
        assertEquals(10, cards.size)
        assertTrue(cards.all { it.relative })
        assertTrue(cards.map { it.points }.distinct().size > 1)
        assertTrue(game.calibrationActions.any { it.id == "board.v1.region.option.2" && it.relative })
        assertTrue(game.calibrationActions.any { it.id == "board.v1.ranking.scroll" && it.points.size == 2 && it.holdMs == 400L })
        assertEquals((0 until 10).toList(), game.chosen)
        assertEquals((0 until 10).toList(), game.finished)
        // Every tab of every hero, two screens each, and its "Số 1 máy chủ".
        assertEquals(
            (1..10).flatMap { h ->
                Tab.values().flatMap { t ->
                    (
                        1..(
                            if (t ==
                                Tab.TOP
                            ) {
                                1
                            } else {
                                2
                            }
                            )
                        ).map { "hero-%03d-%s-%02d".format(h, t.slug, it) }
                }
            },
            game.kept
        )
        assertEquals("Hero3", game.names[3])
        // Việt Nam, Miền Trung and Đắk Lắk, found after scrolling the provinces.
        assertEquals(listOf("Việt Nam", "Miền Trung", "Đắk Lắk"), listOf(game.country, game.area, game.province))
        // Never the ranked page's "Đấu hạng", which starts a match, nor "Chưa chỉnh".
        assertTrue(game.taps.none { abs(it.first - .795) < .03 && abs(it.second - .867) < .03 })
        assertTrue(game.taps.none { abs(it.first - .41) < .02 && abs(it.second - .789) < .02 })
    }

    @Test fun scrollsTheGridToEachHeroInTurn() {
        // 30 heroes in four rows, the grid left scrolled part way by an earlier visit.
        val game = Game(page = Page.BOARD, heroes = 30, gridScroll = .2).run(wanted = listOf(BoardScreens.Want(Tab.SERVER, 3)))
        assertEquals((0 until 30).toList(), game.chosen)
        assertEquals(30, game.kept.size)
        assertEquals("hero-030-may-chu-01", game.kept.last())
    }

    @Test fun aPickerOpeningAtItsTopIsNotFlungBackForEveryHero() {
        // The user's game: Chọn tướng opens at its top again for each hero. Once the top has been proven, a grid that
        // looks the same is taken as it, and the probe and the flings back up are not done again.
        val game = Game(page = Page.BOARD, heroes = 30, gridScroll = .2, pickerOpensAtTop = true)
            .run(wanted = listOf(BoardScreens.Want(Tab.SERVER, 1)))
        assertTrue(game.outcome.toString(), game.outcome is BoardReader.Outcome.Done)
        assertEquals((0 until 30).toList(), game.chosen)
        assertEquals(1, game.calibrationActions.count { it.id == "board.v1.picker.scroll.probe-flick" })
        assertTrue(game.calibrationActions.count { it.id == "board.v1.picker.scroll.reset" } <= 3)
    }

    @Test fun theGridsTopRowIsWhereTheGameNowPutsIt() {
        // Today's game puts the first row's top at .294, with or without Đã nhận; an older screenshot had .343.
        for (name in listOf("picker", "picker-owned")) {
            val p = pixels(name)
            assertTrue(name, BoardPickerMotion.validTop(BoardScreens.rows(p.first, p.second, rows(p))))
        }
        // A row cut by the window's top is not the first row of the grid.
        assertFalse(BoardPickerMotion.validTop(listOf(BoardScreens.VIEW_TOP to BoardScreens.VIEW_TOP + BoardScreens.CARD - .01)))
    }

    @Test fun turnsOffOwnedOnlyBeforeIndexingAndCollectsTheWholeRoster() {
        val game = Game(
            page = Page.BOARD,
            heroes = 30,
            owned = true,
            ownedHeroes = 14,
            gridScroll = .2
        ).run(wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)))
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals((0 until 30).toList(), game.chosen)
        assertEquals(1, game.filterTaps)
        assertEquals(false, game.owned)
    }

    @Test fun fullFinalRowOf128HeroesEndsOnlyAfterTwoVerifiedScrollProbes() {
        val game = Game(page = Page.BOARD, heroes = 128, owned = false).run(
            heroesWanted = 300,
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3))
        )
        assertTrue("outcome=${game.outcome}", game.outcome is BoardReader.Outcome.Done)
        assertEquals((0 until 128).toList(), game.chosen)
        assertEquals(128, (game.outcome as BoardReader.Outcome.Done).heroes)
        assertEquals("hero-128-quoc-gia-01", game.kept.last())
    }

    @Test fun collectsComplete129HeroRosterIncludingSingleLastCard() {
        val game = Game(page = Page.BOARD, heroes = 129, owned = false).run(
            heroesWanted = 300,
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3))
        )
        assertTrue("result=${game.outcome}", game.outcome is BoardReader.Outcome.Done)
        assertEquals("no missing or repeated hero", (0 until 129).toList(), game.chosen)
        assertEquals((0 until 129).toList(), game.finished)
        assertEquals(129, (game.outcome as BoardReader.Outcome.Done).heroes)
        assertEquals(129, game.kept.size)
        assertEquals("hero-129-quoc-gia-01", game.kept.last())
    }

    @Test fun collectsRemainderOf137HeroRosterAfterResumingMidScroll() {
        val game = Game(page = Page.PICKER, heroes = 137, owned = false, gridScroll = 1.1).run(
            heroesWanted = 300,
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
            first = 125
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals((125 until 137).toList(), game.chosen)
        assertEquals((125 until 137).toList(), game.finished)
        assertEquals(12, (game.outcome as BoardReader.Outcome.Done).heroes)
    }

    @Test fun switchesBackToAllTabEveryTimeGameReopensAnotherRole() {
        val game = Game(
            page = Page.BOARD,
            heroes = 17,
            owned = false,
            allSelected = false,
            reselectOtherTab = true
        ).run(
            heroesWanted = 17,
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3))
        )
        assertEquals((0 until 17).toList(), game.chosen)
        assertEquals(17, game.allTaps)
    }

    @Test fun doesNotToggleAnAlreadyOffFilterAndRechecksEveryOpening() {
        val off = Game(page = Page.BOARD, heroes = 2, owned = false).run(wanted = BoardScreens.DEFAULT)
        assertEquals(listOf(0, 1), off.chosen)
        assertEquals(0, off.filterTaps)
        val reset = Game(
            page = Page.BOARD,
            heroes = 18,
            owned = false,
            ownedOnReopen = true
        ).run(heroesWanted = 18, wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)))
        assertEquals((0 until 18).toList(), reset.chosen)
        assertEquals(18, reset.filterTaps)
    }

    @Test fun missingOrStuckFilterStopsBeforeScrollingOrCapturingAnyHero() {
        val missing = Game(page = Page.BOARD, owned = null).run()
        assertTrue(missing.outcome is BoardReader.Outcome.Failed)
        assertEquals(0, missing.filterTaps)
        assertEquals(0, missing.gridDrags)
        assertTrue(missing.chosen.isEmpty())
        assertEquals(listOf("board-filter"), missing.kept)
        val stuck = Game(page = Page.BOARD, owned = true, ignoreFilterTaps = true).run()
        assertTrue(stuck.outcome is BoardReader.Outcome.Failed)
        assertEquals(BoardReader.MAX_FILTER_TAPS, stuck.filterTaps)
        assertEquals(0, stuck.gridDrags)
        assertTrue(stuck.chosen.isEmpty())
    }

    @Test fun clearingTheOwnedFilterNeverRestsAShortSlowDragOnACard() {
        // The user's run: Đã nhận cleared, then a .12 slow probe chose Hayate and closed Chọn tướng.
        val game = Game(page = Page.BOARD, heroes = 30, owned = true).run(
            heroesWanted = 18,
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3))
        )
        assertTrue(game.outcome.toString(), game.outcome is BoardReader.Outcome.Done)
        assertEquals((0 until 18).toList(), game.chosen)
        assertEquals(0, game.shortSlowDrags)
    }

    @Test fun resumesAnOpenPickerWithTheFilterAlreadyClearedOrReenabled() {
        for (owned in listOf(false, true)) {
            val resumed = Game(page = Page.PICKER, heroes = 30, owned = owned, gridScroll = .2).run(
                heroesWanted = 18,
                wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
                first = 14
            )
            assertEquals(listOf(14, 15, 16, 17), resumed.chosen)
            assertEquals(if (owned) 1 else 0, resumed.filterTaps)
        }
    }

    @Test fun pausingAfterClearingTheFilterDoesNotSelectAnyHero() {
        val paused = Game(page = Page.BOARD, heroes = 30, owned = true, cut = 3).run()
        assertNull(paused.outcome)
        assertEquals(false, paused.owned)
        assertTrue(paused.chosen.isEmpty())
        val resumed = Game(page = paused.page, heroes = 30, owned = paused.owned).run(heroesWanted = 2, wanted = BoardScreens.DEFAULT)
        assertEquals(listOf(0, 1), resumed.chosen)
        assertEquals(0, resumed.filterTaps)
    }

    @Test fun anIgnoredOpenPickerTapIsBounded() {
        val game = Game(page = Page.BOARD, ignorePickerTaps = true).run()
        assertTrue(game.outcome is BoardReader.Outcome.Failed)
        assertTrue(game.chosen.isEmpty())
        assertEquals(2, game.taps.size)
    }

    @Test fun stopsAtTheHeroCountAskedAndResumesAfterTheLastDone() {
        assertEquals(
            listOf(0, 1),
            Game(page = Page.BOARD, heroes = 30).run(heroesWanted = 2, wanted = listOf(BoardScreens.Want(Tab.PROVINCE, 3))).chosen
        )
        val resumed = Game(
            page = Page.RANKED,
            heroes = 30
        ).run(heroesWanted = 12, wanted = listOf(BoardScreens.Want(Tab.PROVINCE, 3)), first = 9)
        assertEquals(listOf(9, 10, 11), resumed.chosen)
    }

    @Test fun scrollsEachListToItsEnd() {
        // 999 pages asked: down to the end, 1.0 screen below the first at .36 a scroll.
        val game = Game(page = Page.BOARD, heroes = 1, length = 1.0).run(wanted = listOf(BoardScreens.Want(Tab.COUNTRY, BoardScreens.ALL)))
        assertEquals(listOf("hero-001-quoc-gia-01", "hero-001-quoc-gia-02", "hero-001-quoc-gia-03", "hero-001-quoc-gia-04"), game.kept)
    }

    @Test fun stopsWhenThePauseCutsIt() {
        val game = Game(page = Page.BOARD, heroes = 30, cut = 12).run()
        assertNull(game.outcome)
        assertTrue(game.chosen.size <= 2)
    }

    @Test fun takesVietNamsTopThreeAndTheServersTopTenWhenAsked() {
        // By default: Việt Nam's first screen (four players) and "Số 1 máy chủ".
        val game = Game(page = Page.BOARD, heroes = 2, length = 3.0).run(wanted = BoardScreens.DEFAULT)
        assertEquals(
            listOf("hero-001-quoc-gia-01", "hero-001-so-1-may-chu-01", "hero-002-quoc-gia-01", "hero-002-so-1-may-chu-01"),
            game.kept
        )
        // The whole server's top ten: four screens, the list scrolled three times.
        val server = Game(page = Page.BOARD, heroes = 1, length = 3.0).run(wanted = listOf(BoardScreens.Want(Tab.SERVER, 10)))
        assertEquals((1..4).map { "hero-001-may-chu-%02d".format(it) }, server.kept)
        assertEquals(listOf(1, 1, 2, 4), listOf(3, 4, 5, 10).map { BoardScreens.Want(Tab.SERVER, it).pages })
    }

    private fun player(hero: Int, tab: Tab, rank: Int) =
        listOf("ho-so", "uid").map { "hero-%03d-%s-hang-%02d-%s".format(hero, tab.slug, rank, it) }

    @Test fun opensEachTopPlayerTheirPageCopiesNameAndUidThenGoesBack() {
        val game = Game(page = Page.BOARD, heroes = 2, length = 3.0).run(wanted = BoardScreens.DEFAULT, openPlayers = true)
        assertTrue(game.outcome.toString(), game.outcome is BoardReader.Outcome.Done)
        // The list as before, then each of Việt Nam's top three, then "Số 1 máy chủ" as before.
        assertEquals(
            (1..2).flatMap { h ->
                listOf("hero-%03d-quoc-gia-01".format(h)) + (1..3).flatMap { player(h, Tab.COUNTRY, it) } +
                    "hero-%03d-so-1-may-chu-01".format(h)
            },
            game.kept
        )
        assertEquals(listOf(1, 2, 3, 1, 2, 3), game.opened)
        val ids = game.calibrationActions.map { it.id }.toSet()
        for (id in listOf(
            "player.row",
            "player.profile",
            "player.copy-name",
            "player.eye",
            "player.copy-uid",
            "player.back",
            "player.card.close"
        )) {
            assertTrue(id, "board.v1.$id" in ids)
        }
        assertTrue("board.v1.player.avatar" !in ids)
        assertTrue(game.calibrationActions.filter { it.id == "board.v1.player.row" }.all { it.relative })
        // Each player's name and UID as the copy icons gave them.
        assertEquals(
            (1..2).flatMap { h ->
                (1..3).map { "hero-%03d-quoc-gia-hang-%02d".format(h, it) to Triple("Người chơi $it", "120000000000%04d".format(it), true) }
            },
            game.ids.toList()
        )
        // Never the card's add-friend or report icons.
        assertTrue(game.taps.none { it.second > .88 && it.first > .83 })
    }

    @Test fun waitsForASlowPageBeforeKeepingIt() {
        val game = Game(page = Page.BOARD, heroes = 1, profileLoads = 6).run(
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
            openPlayers = true
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals(6, game.loadedShots.size)
        assertTrue("taken while loading: ${game.loadedShots}", game.loadedShots.all { it })
        assertTrue(game.notes.none { it.contains("tải lâu") })
    }

    @Test fun keepsWhatIsThereWhenItNeverFinishesLoading() {
        val game = Game(page = Page.BOARD, heroes = 1, profileLoads = 100).run(
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 1)),
            openPlayers = true
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertTrue(game.kept.contains("hero-001-quoc-gia-hang-01-ho-so"))
        assertTrue(game.notes.any { it.contains("hồ sơ tải lâu") })
    }

    @Test fun findsTheServersTopTenAcrossScrolledScreens() {
        val game = Game(page = Page.BOARD, heroes = 1, length = 3.0).run(
            wanted = listOf(BoardScreens.Want(Tab.SERVER, 10)),
            openPlayers = true
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals((1..10).toList(), game.opened)
        assertEquals((1..10).flatMap { player(1, Tab.SERVER, it) }, game.kept.filter { it.contains("-hang-") })
        assertEquals((1..4).map { "hero-001-may-chu-%02d".format(it) }, game.kept.filter { !it.contains("-hang-") })
    }

    @Test fun findsItsPlaceAgainWhenTheListIsBackAtItsTop() {
        val game = Game(page = Page.BOARD, heroes = 1, length = 3.0, resetOnBack = true, cardStays = false).run(
            wanted = listOf(BoardScreens.Want(Tab.SERVER, 10)),
            openPlayers = true
        )
        assertTrue(game.outcome.toString(), game.outcome is BoardReader.Outcome.Done)
        assertEquals((1..10).toList(), game.opened)
        assertEquals((1..10).flatMap { player(1, Tab.SERVER, it) }, game.kept.filter { it.contains("-hang-") })
        // Measured back from where it was when near, from the top after the tab again when far.
        assertTrue(game.notes.toString(), game.notes.any { it.contains("đã đo lại vị trí") })
        assertTrue(game.notes.toString(), game.notes.any { it.contains("mở lại bảng từ đầu") })
        assertTrue(game.notes.none { it.contains("không tìm lại được") })
    }

    @Test fun anEyeThatOpensNoBoxLeavesThePlayerWithTheirName() {
        val game = Game(page = Page.BOARD, heroes = 1, noBox = setOf(2)).run(
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
            openPlayers = true
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals(listOf(1, 2, 3), game.opened)
        assertEquals(
            player(1, Tab.COUNTRY, 1) + "hero-001-quoc-gia-hang-02-ho-so" + player(1, Tab.COUNTRY, 3),
            game.kept.filter { it.contains("-hang-") }
        )
        assertTrue(game.notes.any { it.contains("hạng 2") && it.contains("không hiện hộp UID") })
        assertEquals(Triple("Người chơi 2", null, false), game.ids["hero-001-quoc-gia-hang-02"])
        // Nothing is tapped where the box should have been.
        assertEquals(2, game.calibrationActions.count { it.id == "board.v1.player.copy-uid" })
    }

    /** Android may keep the clipboard from the app: the UID is then the one read in the box, and no name is made up. */
    @Test fun aHiddenClipboardLeavesTheUidReadInTheBox() {
        val game = Game(page = Page.BOARD, heroes = 1, hiddenClipboard = true).run(
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
            openPlayers = true
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals((1..3).map { Triple(null, "120000000000%04d".format(it), false) }, game.ids.values.toList())
        assertTrue(game.notes.any { it.contains("UID đọc từ hộp") })
    }

    @Test fun aShortListEndsWithoutScrolling() {
        val game = Game(page = Page.BOARD, heroes = 1, players = 2).run(
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
            openPlayers = true
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals(listOf(1, 2), game.opened)
        assertEquals("hero-001-quoc-gia-01", game.kept.first())
        assertTrue(game.notes.any { it.contains("chỉ có 2 người") })
        assertTrue(game.calibrationActions.none { it.id == "board.v1.ranking.scroll" })
    }

    @Test fun aResumedStepSkipsThePlayersAlreadyKept() {
        val game = Game(page = Page.BOARD, heroes = 1).run(
            wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)),
            openPlayers = true,
            donePlayers = setOf("hero-001-quoc-gia-hang-01", "hero-001-quoc-gia-hang-02")
        )
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals(listOf(3), game.opened)
    }

    @Test fun aPlayersPageLeftOpenIsClosedOnTheWayIn() {
        val game = Game(page = Page.PROFILE, heroes = 1).run(wanted = listOf(BoardScreens.Want(Tab.COUNTRY, 3)))
        assertTrue(game.outcome is BoardReader.Outcome.Done)
        assertEquals(listOf("hero-001-quoc-gia-01"), game.kept)
        assertTrue(game.calibrationActions.any { it.id == "board.v1.player.back" })
    }
}
