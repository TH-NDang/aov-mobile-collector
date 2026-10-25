package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The players' ranking of each hero, "Chiến khu": lobby → "Đấu hạng" → "Chiến khu". A season's first visit
 * shows its review first ("Hồi ức mùa…", "Bước kế", "Mở mùa mới", a trailer, "Ấn màn hình để tiếp tục"), and an
 * account without a region is asked for one ("Vào chiến khu": country, area, province). The ranking page shows
 * one hero, changed with the ↻ under its card through the "Chọn tướng" grid, and four tabs: province, area,
 * country and the whole server. Measured on the user's 2400x1080 screenshots; pages are told by their words.
 */
object BoardScreens {
    enum class Page { LOBBY, RANKED, SEASON, REGION, BOARD, PICKER, TOP, OTHER }

    /**
     * The four tabs of the ranking, by place: their names follow the account's region. And "Số 1 máy chủ", a page
     * of its own opened from the bottom left, with the best player of each area for the hero.
     */
    enum class Tab(val slug: String, val label: String, val x: Double, val y: Double = TAB_Y) {
        PROVINCE("tinh", "Tỉnh", .4305),
        AREA("mien", "Miền", .5305),
        COUNTRY("quoc-gia", "Quốc gia", .6445),
        SERVER("may-chu", "Toàn máy chủ", .7515),
        TOP("so-1-may-chu", "Số 1 máy chủ", .215, .911)
    }

    const val TAB_Y = .1456

    /**
     * A tab to photograph down to its [top] players ([ALL] for the whole list). The first screen shows four; each
     * scroll brings a little over two more, so ten take four screens.
     */
    data class Want(val tab: Tab, val top: Int) {
        val pages get() = if (top >= ALL) ALL else 1 + maxOf(0, top - 4).let { (it + 1) / 2 }
    }

    const val ALL = 999

    /** As the user asked: Việt Nam's top three and "Số 1 máy chủ"; the whole server's top ten when chosen. */
    val DEFAULT = listOf(Want(Tab.COUNTRY, 3), Want(Tab.TOP, 1))

    fun defaultTop(tab: Tab) = if (tab == Tab.SERVER) {
        10
    } else if (tab == Tab.TOP) {
        1
    } else {
        3
    }

    /** The back arrow of "Số 1 máy chủ", top left. */
    const val TOP_BACK_X = .095
    const val TOP_BACK_Y = .058

    /** The lobby's big "Đấu hạng" button, used when its stylised letters are not read. */
    const val RANKED_X = .8475
    const val RANKED_Y = .898

    /** ↻ under the hero's card, which opens "Chọn tướng"; the card's hero name above it. */
    const val SWAP_X = .1575
    const val SWAP_Y = .742

    /** The X of "Chọn tướng". */
    const val PICKER_CLOSE_X = .8225
    const val PICKER_CLOSE_Y = .111

    /** A positively identified owned-only checkbox; null means unreadable, never unchecked. */
    data class OwnedFilter(val checked: Boolean, val x: Double, val y: Double)

    /**
     * Find the blue square immediately beside "Đã nhận", then read its bright cyan tick.
     * Work in screenshot pixels: the box is square at every resolution, including system-bar margins.
     * Require all four edges and a dark interior for OFF, so a missing/covered control cannot mean OFF.
     */
    fun ownedFilter(width: Int, height: Int, words: List<GameScreens.Word>, readRow: (Int, IntArray) -> Unit): OwnedFilter? {
        if (width <= height || page(words, false) != Page.PICKER) return null
        // At small text sizes ML Kit can read the final n as i ("Đã nhậi"). The label
        // is only an anchor: all four blue edges and the tick/empty interior still must match.
        val label = words.filter {
            (it.plain.contains("danhan") || it.plain == "danhai") &&
                it.plain.length < 24 &&
                it.left > .68 &&
                it.right < .88 &&
                it.top > .16 &&
                it.bottom < .29 &&
                it.bottom - it.top < .075 &&
                it.right - it.left in .015..0.18
        }
            .minByOrNull { (it.right - it.left) * (it.bottom - it.top) } ?: return null
        val x0 = ((label.right - .012) * width).toInt().coerceIn(0, width - 1)
        val x1 = ((label.right + .04) * width).toInt().coerceIn(x0, width - 1)
        val y0 = ((label.y - .025) * height).toInt().coerceIn(0, height - 1)
        val y1 = ((label.y + .025) * height).toInt().coerceIn(y0, height - 1)
        val cache = HashMap<Int, IntArray>()

        fun pixel(x: Int, y: Int) = cache.getOrPut(y.coerceIn(0, height - 1)) {
            IntArray(width).also { readRow(y.coerceIn(0, height - 1), it) }
        }[
            x.coerceIn(
                0,
                width - 1
            )
        ]

        fun blue(p: Int): Boolean {
            val r = p shr 16 and 255
            val g = p shr 8 and 255
            val b = p and 255
            return b >= 145 && b - r >= 65 && g - r >= 4 && g >= 60
        }

        fun bright(p: Int): Boolean {
            val r = p shr 16 and 255
            val g = p shr 8 and 255
            val b = p and 255
            return r >= 125 && g >= 160 && b >= 180 && g - r >= 12
        }
        val step = maxOf(1, height / 540)
        var best: OwnedFilter? = null
        var bestScore = 0.0
        for (radiusFraction in listOf(.0165, .018, .0195)) {
            val radius = maxOf(5, (height * radiusFraction).roundToInt())
            for (cy in y0..y1 step step) {
                for (cx in x0..x1 step step) {
                    // Avoid rounded corners. Each edge must be present, not just one blue line.
                    val edges = IntArray(4)
                    for (i in -3..3) {
                        val d = i * radius / 4
                        // JPEG and half-pixel scaling can move a thin edge by one raster pixel.
                        if ((-step..step).any { blue(pixel(cx + d, cy - radius + it)) }) edges[0]++
                        if ((-step..step).any { blue(pixel(cx + d, cy + radius + it)) }) edges[1]++
                        if ((-step..step).any { blue(pixel(cx - radius + it, cy + d)) }) edges[2]++
                        if ((-step..step).any { blue(pixel(cx + radius + it, cy + d)) }) edges[3]++
                    }
                    if (edges.any { it < 5 }) continue
                    var lit = 0
                    var dark = 0
                    var total = 0
                    for (dy in -radius * 2 / 3..radius * 2 / 3) {
                        for (dx in -radius * 2 / 3..radius * 2 / 3) {
                            val p = pixel(cx + dx, cy + dy)
                            total++
                            if (bright(p)) lit++
                            if ((p shr 16 and 255) < 110 && (p shr 8 and 255) < 135) dark++
                        }
                    }
                    val fraction = lit.toDouble() / total
                    val checked = when {
                        fraction in .09..0.55 -> true
                        fraction < .02 && dark > .85 * total -> false
                        else -> continue
                    }
                    val score = edges.sum().toDouble() - abs(cy.toDouble() / height - label.y) * 10
                    if (score > bestScore) {
                        bestScore = score
                        best = OwnedFilter(checked, cx.toDouble() / width, cy.toDouble() / height)
                    }
                }
            }
        }
        return best
    }

    /** The region dialog's three drop-downs and their lists. */
    val REGION_X = doubleArrayOf(.3525, .5, .6475)
    const val REGION_Y = .4856

    /** What to choose: Việt Nam, Miền Trung, Đắk Lắk, as the user asked; any will do when one is missing. */
    val REGION_WANTED = arrayOf("vietnam", "mientrung", "daklak")

    fun page(words: List<GameScreens.Word>, lobby: Boolean): Page = when {
        words.any { it.plain.contains("chontuong") } -> Page.PICKER
        words.any { it.plain.contains("vaochienkhu") } -> Page.REGION
        // Before the ranking, whose bottom left names this page too.
        words.any { it.plain.contains("so1maychu") && it.y < .2 } || words.any { it.plain.contains("topchienluc") } -> Page.TOP
        words.any { it.plain.contains("toanmaychu") || it.plain.contains("luchiencaonhat") } ||
            words.any { it.plain.contains("chienkhu") && it.y < .12 && it.x < .35 } -> Page.BOARD
        season(words) != null -> Page.SEASON
        ranked(words) -> Page.RANKED
        // The lobby only with its bottom bar's words read too: a tap there on any other screen could start a match.
        lobby &&
            words.none { it.plain.contains("thoihanmua") } &&
            words.any { w -> w.y > .85 && listOf("tuido", "trangbi", "giaodien", "nvu").any { w.plain.contains(it) } } -> Page.LOBBY
        else -> Page.OTHER
    }

    /** The ranked page: its "Chiến khu" entry on the left, with the season's time or its other entries. */
    fun ranked(words: List<GameScreens.Word>) =
        chienKhu(words) != null && words.any { w -> listOf("thoihanmua", "dinhcao", "hanhtrinh", "khchien").any { w.plain.contains(it) } }

    fun chienKhu(words: List<GameScreens.Word>) = words.firstOrNull {
        it.plain.contains("chienkhu") && it.x < .35 && it.y > .25 && it.y < .8
    }

    /**
     * The word to tap on a season review screen: "Ấn mở l.sử mùa" (read "An mở Isử mùa"), "Bước kế", "Mở mùa
     * mới", "Ấn màn hình để tiếp tục". The shortest line that says it: the button, not "Mở mùa mới nhận Ruby *50"
     * above it. Nothing else on those screens is ever tapped.
     */
    fun season(words: List<GameScreens.Word>) = words.filter { w ->
        listOf("sumua", "buocke", "momuamoi", "manhinhdetieptuc").any { w.plain.contains(it) }
    }.minByOrNull { it.plain.length }

    /** The lobby's "ĐẤU HẠNG" (or "RANKED") button, when its letters are read, at the bottom right. */
    fun rankedButton(words: List<GameScreens.Word>) = words.firstOrNull {
        it.x > .7 &&
            it.y > .8 &&
            (it.plain.contains("dauhang") || it.plain.contains("ranked"))
    }

    /** The hero named on the card left of the ranking. */
    fun hero(words: List<GameScreens.Word>) = words.filter {
        it.x > .05 &&
            it.x < .27 &&
            it.y > .5 &&
            it.y < .62 &&
            it.plain.length >= 2 &&
            it.plain.any { c -> c.isLetter() }
    }
        .minByOrNull { abs(it.y - .569) }?.text?.trim()?.trim('.', ',')

    /** What the region dialog shows: the three drop-downs' texts, and the lines of an open list under one of them. */
    class Region(val boxes: List<String?>, val open: Int?, val options: List<GameScreens.Word>, val confirm: GameScreens.Word?)

    fun region(words: List<GameScreens.Word>): Region {
        val boxes = REGION_X.map { x -> words.firstOrNull { abs(it.y - REGION_Y) < .035 && abs(it.x - x) < .07 }?.plain }
        // A list hangs under the drop-down that opened it, over the dialog's own lines and buttons.
        // Not the dialog's own lines, which ML Kit may cut into pieces such as "ainh sửa vào" or "Xác hn".
        val below = words.filter {
            it.y > REGION_Y + .035 &&
                it.y < .95 &&
                it.plain.length <= 16 &&
                listOf("sau", "thuhai", "xac", "chua", "chinh", "sua", "chienkhu").none { k -> it.plain.contains(k) }
        }
        val open = REGION_X.indices.firstOrNull { i -> below.count { abs(it.x - REGION_X[i]) < .07 } >= 1 }
        val options = if (open == null) emptyList() else below.filter { abs(it.x - REGION_X[open]) < .07 }.sortedBy { it.y }
        return Region(boxes, open, options, words.firstOrNull { it.plain.contains("xacnhan") && it.y > .7 })
    }

    // "Chọn tướng": eight columns of hero cards, 0.137 of the height tall, one row each 0.1768.
    val COLUMNS = doubleArrayOf(.2215, .3015, .3815, .461, .541, .6205, .70, .78)
    const val ROW_PITCH = .1768
    const val CARD = .137

    /** The grid's window: rows above or below are hidden. */
    const val VIEW_TOP = .285
    const val VIEW_BOTTOM = .891

    /**
     * Detect row bands from the FIRST portrait column, not a majority of all eight.
     * The old "more than half of eight" rule erased the last row whenever it had
     * 1–4 heroes and silently marked the roster finished too soon.
     *
     * A short sliver near either clipping edge is allowed here; seeking a card
     * separately requires an entire measured row.
     */
    fun rows(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): List<Pair<Double, Double>> {
        if (height < 400 || width <= height) return emptyList()
        val top = (VIEW_TOP * height).toInt()
        val bottom = (VIEW_BOTTOM * height).toInt()
        val center = (COLUMNS[0] * width).toInt()
        val half = (.022 * width).toInt()
        val row = IntArray(width)
        val result = ArrayList<Pair<Double, Double>>()
        var start = -1
        var lastHit = -1
        for (y in top..bottom) {
            readRow(y, row)
            val bg = row[(.2615 * width).toInt()]
            var changed = 0
            var total = 0
            for (x in center - half..center + half step 2) {
                val p = row[x]
                if (abs((p shr 16 and 255) - (bg shr 16 and 255)) +
                    abs((p shr 8 and 255) - (bg shr 8 and 255)) +
                    abs((p and 255) - (bg and 255)) > 60
                ) {
                    changed++
                }
                total++
            }
            val occupied = changed > total * .5 && y < bottom
            if (occupied) {
                if (start < 0) start = y
                lastHit = y
            }
            if (start >= 0 && ((!occupied && y - lastHit > height * .008) || y == bottom)) {
                if (lastHit - start >= height * .020) {
                    result += start.toDouble() / height to (lastHit + 1).toDouble() / height
                }
                start = -1
            }
        }
        return result
    }

    /** Whether the card at [col] of the band [top]–[bottom] is there: the last row may be short. */
    fun card(width: Int, height: Int, col: Int, top: Double, bottom: Double, readRow: (Int, IntArray) -> Unit): Boolean {
        val y = ((top + bottom) / 2 * height).toInt()
        val line = IntArray(width)
        readRow(y, line)
        val bg = line[(.2615 * width).toInt()]
        val cx = (COLUMNS[col] * width).toInt()
        val half = (.022 * width).toInt()
        var off = 0
        var all = 0
        var x = cx - half
        while (x <=
            cx + half
        ) {
            val p = line[x]
            all++
            if (abs((p shr 16 and 255) - (bg shr 16 and 255)) + abs((p shr 8 and 255) - (bg shr 8 and 255)) +
                abs((p and 255) - (bg and 255)) >
                60
            ) {
                off++
            }
            x += 2
        }
        return off > .5 * all
    }

    /** Grey levels of the ranking's list on a 40x20 grid, to tell whether a scroll moved it. */
    fun signature(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): IntArray {
        val cols = 40
        val rows = 20
        val sums = IntArray(cols * rows)
        val counts = IntArray(cols * rows)
        val x0 = (.28 * width).toInt()
        val x1 = (.90 * width).toInt()
        val y0 = (.20 * height).toInt()
        val y1 = (.84 * height).toInt()
        val line = IntArray(width)
        for (y in y0 until y1 step 3) {
            readRow(y, line)
            val r = (y - y0) * rows / (y1 - y0)
            for (x in x0 until x1 step 3) {
                val c = line[x]
                val i =
                    r * cols + (x - x0) * cols / (x1 - x0)
                sums[i] += (299 * (c shr 16 and 255) + 587 * (c shr 8 and 255) + 114 * (c and 255)) / 1000
                counts[i]++
            }
        }
        return IntArray(sums.size) { if (counts[it] == 0) 0 else sums[it] / counts[it] }
    }

    fun same(a: IntArray, b: IntArray) = RankScreens.same(a, b)
}

/**
 * Goes from wherever it is started on the way (lobby, the season's review, the ranked page, the region dialog)
 * to "Chiến khu", then photographs the ranking of each hero of "Chọn tướng" in order, from hero [first], for the
 * tabs [wanted], each down to its top players (scrolling down the list; at most to its end). Taps only what it means
 * to: never the ranked page's own "Đấu hạng", which would start a match. All screen access goes through
 * [Screen], so unit tests run this exact logic on scripted screens.
 */
class BoardReader(
    private val screen: Screen,
    private val heroes: Int,
    private val wanted: List<BoardScreens.Want>,
    private val first: Int = 0,
    private val finish: (Outcome) -> Unit
) {
    private val tabs = wanted.map { it.tab }

    sealed class Outcome {
        class Done(val heroes: Int) : Outcome()

        class Failed(val reason: String) : Outcome()
    }

    /**
     * One screenshot: [words] only when asked for; [rows] the bands of the "Chọn tướng" grid and [card] whether a
     * card is at a column of a band, when asked for them. Call [done] once, with a name to keep it or null.
     */
    interface Look {
        val words: List<GameScreens.Word>
        val lobby: Boolean
        val signature: IntArray
        val rows: List<Pair<Double, Double>>
        val ownedFilter: BoardScreens.OwnedFilter?

        /** The selected category must be Tất cả, independently of the Đã nhận checkbox. */
        val allSelected: Boolean

        /** Five RGB portrait columns over the scrollable region, never static row borders. */
        val pickerProfile: FloatArray
        val pickerHeight: Int

        fun card(col: Int, top: Double, bottom: Double): Boolean

        fun done(keep: String?)
    }

    interface Screen {
        val alive: Boolean

        fun look(words: Boolean, grid: Boolean, then: (Look?) -> Unit)

        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)

        fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) {
            val first = action.points.first()
            if (action.points.size == 1) {
                tap(first.x, first.y, then)
            } else {
                drag(first.x, first.y, action.points.last().y, action.holdMs == 0L, then)
            }
        }

        /** Drags at [x] from [from] to [to] (screen heights), slowly and resting before lifting unless [fling]. */
        fun drag(x: Double, from: Double, to: Double, fling: Boolean, then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        fun note(text: String) {}

        fun say(text: String) {}

        /** Hero [index] of the grid is [name] (null when its name was not read); its photos follow. */
        fun hero(index: Int, name: String?) {}

        /** Hero [index] is photographed: a later try of the step starts after it. */
        fun finished(index: Int) {}
    }

    companion object {
        const val MAX_STEPS = 30
        const val MAX_WAITS = 30
        const val STILL_TRIES = 3
        const val MAX_PICKER_TRIES = 3
        const val MAX_FILTER_TAPS = 2
        const val FILTER_READS = 3

        /** A list scroll: less than the rows on screen, so photos overlap. */
        const val LIST_FROM = .78
        const val LIST_TO = .42
        const val LIST_X = .75
    }

    private var ended = false
    private var index = first
    private var steps = 0
    private var waits = 0
    private var regionTries = 0
    private var regionScrolls = 0

    /** Top of the grid's first row when it is scrolled to the top, measured each time the grid is opened. */
    private var rowZero = .3426
    private var pickerOffset = 0.0
    private var savedProfile: FloatArray? = null
    private var savedProfileHeight = 0
    private var lastPickedName: String? = null

    fun start() = navigate()

    private fun end(o: Outcome) {
        if (!ended) {
            ended = true
            finish(o)
        }
    }

    private fun fail(reason: String) = end(Outcome.Failed(reason))

    private fun look(words: Boolean, grid: Boolean = false, then: (Look) -> Unit) {
        if (!screen.alive) return
        screen.look(words, grid) { l ->
            when {
                !screen.alive -> l?.done(null)
                l == null -> fail("Không chụp được màn hình (máy sắp hết chỗ trống, hoặc Android từ chối chụp). Kiểm tra rồi bấm Tiếp tục.")
                else -> then(l)
            }
        }
    }

    private fun tap(x: Double, y: Double, wait: Long, id: String, relative: Boolean = false, then: () -> Unit) {
        if (!screen.alive) return
        screen.calibrated(CalibrationAction("board.v1.$id", "Chiến khu: $id", listOf(CalibrationPoint(x, y)), relative)) { ok ->
            if (ok) screen.later(wait, then) else fail("Không chạm được màn hình")
        }
    }

    private fun drag(x: Double, from: Double, to: Double, fling: Boolean, wait: Long, id: String, then: () -> Unit) {
        if (!screen.alive) return
        screen.calibrated(
            CalibrationAction(
                "board.v1.$id",
                "Chiến khu: $id",
                listOf(CalibrationPoint(x, from), CalibrationPoint(x, to)),
                relative = true,
                durationMs = if (fling) 160 else 700,
                holdMs = if (fling) 0 else 400
            )
        ) { ok ->
            if (ok) screen.later(wait, then) else fail("Không kéo được màn hình")
        }
    }

    private fun go(l: Look, what: String, x: Double, y: Double, wait: Long, id: String, relative: Boolean = false) {
        l.done(null)
        screen.note(what)
        screen.say("$what…")
        steps++
        waits = 0
        tap(x, y, wait, id, relative) { navigate() }
    }

    /** One tap at a time towards "Chiến khu", reading where it is after each. */
    private fun navigate(): Unit = look(true) { l ->
        if (steps >
            MAX_STEPS
        ) {
            l.done("board-lost")
            return@look fail("Đã bấm $MAX_STEPS lần mà chưa tới Chiến khu. Mở sảnh game rồi bấm Tiếp tục.")
        }
        val words = l.words
        when (BoardScreens.page(words, l.lobby)) {
            BoardScreens.Page.BOARD -> {
                l.done(null)
                steps = 0
                nextHero()
            }
            BoardScreens.Page.RANKED -> BoardScreens.chienKhu(words)!!.let {
                go(l, "Đấu hạng: bấm Chiến khu", it.x, it.y, 2500, "navigation.warzone", true)
            }
            BoardScreens.Page.SEASON -> BoardScreens.season(words)!!.let {
                go(l, "Mùa giải mới: bấm \"${it.text}\"", it.x, it.y, 2000, "navigation.season", true)
            }
            BoardScreens.Page.REGION -> region(l)
            BoardScreens.Page.LOBBY -> {
                val b = BoardScreens.rankedButton(words)
                go(l, "Sảnh: bấm Đấu hạng", b?.x ?: BoardScreens.RANKED_X, b?.y ?: BoardScreens.RANKED_Y, 3000, "navigation.ranked", true)
            }
            // Left open by an earlier try: close it and start again from the ranking.
            BoardScreens.Page.PICKER -> go(
                l,
                "Đóng bảng Chọn tướng còn mở",
                BoardScreens.PICKER_CLOSE_X,
                BoardScreens.PICKER_CLOSE_Y,
                1200,
                "picker.close"
            )
            BoardScreens.Page.TOP -> go(
                l,
                "Số 1 máy chủ: quay lại Chiến khu",
                BoardScreens.TOP_BACK_X,
                BoardScreens.TOP_BACK_Y,
                1500,
                "top.back"
            )
            // Loading, or the season's trailer: wait for it.
            BoardScreens.Page.OTHER -> if (++waits <=
                MAX_WAITS
            ) {
                l.done(null)
                screen.say("Đang chờ màn hình…")
                screen.later(2000) { navigate() }
            } else {
                l.done("board-unknown")
                fail("Không nhận ra màn hình sau ${MAX_WAITS * 2} giây. Mở sảnh game hoặc Chiến khu rồi bấm Tiếp tục.")
            }
        }
    }

    /** "Vào chiến khu": Việt Nam, Miền Trung, Đắk Lắk, one drop-down at a time, then Xác nhận. */
    private fun region(l: Look) {
        if (++regionTries >
            20
        ) {
            l.done("board-region")
            return fail("Không chọn xong khu vực trong Vào chiến khu. Chọn bằng tay rồi bấm Tiếp tục.")
        }
        val r = BoardScreens.region(l.words)
        val open = r.open
        if (open != null && r.options.isNotEmpty()) {
            val wanted = r.options.firstOrNull { it.plain.contains(BoardScreens.REGION_WANTED[open]) }
            // The province list is long: scroll it a little to find Đắk Lắk, else any province will do.
            if (wanted == null && open == 2 && regionScrolls < 3) {
                regionScrolls++
                l.done(null)
                return drag(BoardScreens.REGION_X[2], .85, .62, false, 1000, "region.scroll") { navigate() }
            }
            val pick = wanted ?: r.options.first()
            return go(l, "Vào chiến khu: chọn ${pick.text}", pick.x, pick.y, 1200, "region.option.$open", true)
        }
        val boxes = r.boxes
        when {
            boxes[0] == null ||
                boxes[0]!!.contains(
                    "quocgia"
                ) -> go(l, "Vào chiến khu: mở ô Quốc gia", BoardScreens.REGION_X[0], BoardScreens.REGION_Y, 1000, "region.country.open")
            boxes[1] == null ||
                boxes[1]!!.contains(
                    "khuvuc"
                ) -> go(l, "Vào chiến khu: mở ô Khu vực", BoardScreens.REGION_X[1], BoardScreens.REGION_Y, 1000, "region.area.open")
            boxes[2] == null ||
                boxes[2]!!.contains(
                    "khuvuc"
                ) -> go(l, "Vào chiến khu: mở ô Tỉnh", BoardScreens.REGION_X[2], BoardScreens.REGION_Y, 1000, "region.province.open")
            r.confirm != null -> go(l, "Vào chiến khu: bấm Xác nhận", r.confirm.x, r.confirm.y, 2500, "region.confirm", true)
            else -> {
                l.done(null)
                screen.later(1000) { navigate() }
            }
        }
    }

    private fun nextHero() {
        if (index >= heroes) return end(Outcome.Done(index - first))
        screen.say("Tướng ${index + 1}: mở Chọn tướng")
        tap(BoardScreens.SWAP_X, BoardScreens.SWAP_Y, 1500, "picker.open") { openPicker(0) }
    }

    /** "Chọn tướng" is open: back to its top, measured, then on to the hero's row. */
    private fun openPicker(tries: Int): Unit = look(true, true) { l ->
        if (BoardScreens.page(l.words, l.lobby) != BoardScreens.Page.PICKER) {
            l.done(null)
            if (tries >= MAX_PICKER_TRIES) return@look fail("Không mở được Chọn tướng. Mở Chiến khu rồi bấm Tiếp tục.")
            if (tries < 2) return@look screen.later(1200) { openPicker(tries + 1) }
            return@look if (BoardScreens.page(l.words, l.lobby) ==
                BoardScreens.Page.BOARD
            ) {
                tap(BoardScreens.SWAP_X, BoardScreens.SWAP_Y, 1500, "picker.open") { openPicker(tries + 1) }
            } else {
                steps = 0
                navigate()
            }
        }
        allHeroes(l, 0, 0)
    }

    /** Recheck Tất cả and Đã nhận EACH time the chooser opens, even after a resume. */
    private fun allHeroes(l: Look, reads: Int, taps: Int, allTaps: Int = 0) {
        if (BoardScreens.page(l.words, l.lobby) != BoardScreens.Page.PICKER) {
            l.done("board-picker")
            return fail("Mất popup Chọn tướng. Mở Chiến khu rồi bấm Tiếp tục.")
        }
        if (!l.allSelected) {
            val all = ProHeroPicker.allButton(l.words)
            l.done(if (all == null && reads >= FILTER_READS) "board-picker-all" else null)
            if (allTaps >= 2 || (all == null && reads >= FILTER_READS)) {
                return fail("Không xác minh được tab Tất cả của Chọn tướng; chưa chọn tướng.")
            }
            if (all == null) {
                return screen.later(700) {
                    look(true, true) { allHeroes(it, reads + 1, taps, allTaps) }
                }
            }
            return tap(all.x, all.y, 900, "picker.all", true) {
                look(true, true) { allHeroes(it, 0, taps, allTaps + 1) }
            }
        }
        val filter = l.ownedFilter
        if (filter != null && !filter.checked) {
            val old = savedProfile
            if (old != null && savedProfileHeight == l.pickerHeight && validPicker(l)) {
                val delta = BoardPickerMotion.displacement(old, l.pickerProfile, l.pickerHeight, 0)
                if (delta != null) {
                    val position = pickerOffset + delta
                    val targetTop = rowZero + index / 8 * BoardScreens.ROW_PITCH - position
                    if (targetTop >= BoardScreens.VIEW_TOP - .005) {
                        screen.note("Chiến khu: giữ vị trí tướng đã xác minh, không vuốt về đầu lại")
                        return seek(l, position, 0)
                    }
                }
            }
            return probeReset(l)
        }
        // A just-cleared checkbox can report a stale ON state during animations.
        if (filter == null || (taps > 0 && reads < FILTER_READS)) {
            l.done(if (reads >= FILTER_READS) "board-picker-filter" else null)
            if (reads >= FILTER_READS) {
                return fail("Không đọc rõ Đã nhận trong Chọn tướng; chưa chọn tướng để tránh lấy thiếu.")
            }
            return screen.later(800) {
                look(true, true) { allHeroes(it, reads + 1, taps, allTaps) }
            }
        }
        if (taps >= MAX_FILTER_TAPS) {
            l.done("board-picker-filter")
            return fail("Đã nhận vẫn bật sau khi bỏ tích; chưa thu thập tướng để tránh bỏ sót.")
        }
        l.done(null)
        screen.note("Chọn tướng: bỏ Đã nhận để lấy cả tướng chưa sở hữu")
        tap(filter.x, filter.y, 1200, "picker.owned-filter", true) {
            look(true, true) { allHeroes(it, 0, taps + 1, allTaps) }
        }
    }

    private fun validPicker(l: Look): Boolean = l.rows.isNotEmpty() &&
        l.pickerProfile.isNotEmpty() &&
        l.pickerHeight >= 400

    /** A fourth row might appear as a sliver below the whole-row detector. */
    private fun bottomSliver(l: Look): Boolean =
        l.card(0, BoardScreens.VIEW_BOTTOM - .025, BoardScreens.VIEW_BOTTOM)

    /**
     * Probe a small downward scroll to prove the viewport moves before resetting.
     * With a truly short, fully visible list the probe may not move at all.
     */
    private fun probeReset(before: Look) {
        if (!validPicker(before)) {
            before.done("board-picker-grid")
            return fail("Không nhận dạng được lưới tướng; chưa thể kết luận hết danh sách.")
        }
        val profile = before.pickerProfile
        val height = before.pickerHeight
        before.done(null)
        drag(.62, .78, .66, false, 820, "picker.scroll.probe") {
            look(false, true) { after ->
                if (!validPicker(after) || after.pickerHeight != height) {
                    after.done("board-picker-probe")
                    return@look fail("Mất lưới tướng khi kiểm tra chiều cuộn.")
                }
                val moved = BoardPickerMotion.displacement(profile, after.pickerProfile, height, 1)
                if (moved == null || moved > .22) {
                    after.done("board-picker-probe")
                    return@look fail("Không đo được cuộn lưới tướng; không tự đoán vị trí.")
                }
                resetToTop(after, 0, 0, resetMoved = false, probeMoved = moved > .004)
            }
        }
    }

    /**
     * Fling upward until the actual portrait artwork stops moving. Repeating row
     * boundaries are NOT a top indicator: each grid row has the same geometry.
     */
    private fun resetToTop(before: Look, attempts: Int, still: Int, resetMoved: Boolean, probeMoved: Boolean) {
        if (!validPicker(before)) {
            before.done("board-picker-grid")
            return fail("Không còn ảnh lưới tướng khi tìm đầu danh sách.")
        }
        if (attempts >= 25) {
            before.done("board-picker-top")
            return fail("Không xác minh được đầu danh sách tướng sau 25 lần kéo; dừng an toàn.")
        }
        val prior = before.pickerProfile
        val height = before.pickerHeight
        before.done(null)
        drag(.62, .40, .85, true, 850, "picker.scroll.reset") {
            look(false, true) { after ->
                if (!validPicker(after) || after.pickerHeight != height) {
                    after.done("board-picker-grid")
                    return@look fail("Lưới tướng thay đổi khi tìm đầu danh sách.")
                }
                val same = BoardPickerMotion.sameViewport(prior, after.pickerProfile)
                val stable = if (same) still + 1 else 0
                val movedBack = resetMoved || !same
                if (stable >= 2) {
                    val fullyVisibleShortList = BoardPickerMotion.validTop(after.rows) &&
                        BoardPickerMotion.clearLastRow(after.rows) &&
                        !bottomSliver(after)
                    if (!movedBack && !probeMoved && !fullyVisibleShortList) {
                        after.done("board-picker-top")
                        return@look fail("Cuộn không dịch chuyển và chưa chứng minh được đầu danh sách; không chọn nhầm tướng.")
                    }
                    if (!movedBack && probeMoved) {
                        after.done("board-picker-top")
                        return@look fail("Đã cuộn xuống nhưng thao tác quay về đầu không di chuyển.")
                    }
                    if (!BoardPickerMotion.validTop(after.rows)) {
                        after.done("board-picker-top")
                        return@look fail("Chưa thấy hàng đầu đủ ảnh tại vị trí chuẩn; chưa xác minh đầu danh sách.")
                    }
                    rowZero = after.rows.first().first
                    pickerOffset = 0.0
                    screen.note("Chiến khu: đã xác minh đầu danh sách bằng ảnh tướng, không dựa vào viền hàng")
                    seek(after, 0.0, 0)
                } else {
                    resetToTop(after, attempts + 1, stable, movedBack, probeMoved)
                }
            }
        }
    }

    /**
     * Use the actual image-registered displacement of portrait artwork, including
     * the short final drag. If registration is uncertain, FAIL rather than Done.
     */
    private fun seek(l: Look, scrolled: Double, moves: Int) {
        if (!validPicker(l)) {
            l.done("board-picker-grid")
            return fail("Mất lưới Chọn tướng trong khi tìm, không coi là đã hết tướng.")
        }
        val row = index / 8
        val col = index % 8
        val top = rowZero + row * BoardScreens.ROW_PITCH - scrolled
        val bottom = top + BoardScreens.CARD
        val band = l.rows.firstOrNull {
            abs(it.first - top) < .052 &&
                it.second - it.first >= BoardScreens.CARD - .018
        }

        if (top >= BoardScreens.VIEW_TOP - .005 && bottom <= BoardScreens.VIEW_BOTTOM + .005) {
            if (band == null) {
                l.done("board-picker-row")
                return fail("Không khớp hàng tướng đã đo với ảnh; không tự coi là hết danh sách.")
            }
            val adjusted = scrolled + top - band.first
            if (l.card(col, band.first, band.second)) {
                val y = (band.first + band.second) / 2
                savedProfile = l.pickerProfile
                savedProfileHeight = l.pickerHeight
                pickerOffset = adjusted
                l.done(null)
                screen.say("Tướng ${index + 1}: chọn ô ${col + 1}/8 của hàng ${row + 1}")
                return tap(BoardScreens.COLUMNS[col], y, 2500, "picker.card", true) { picked(0) }
            }
            // Only contiguous final partial rows are permitted to end. A dark/
            // unloaded middle card or a missing detected row is not end-of-list.
            val occupied = (0..7).map { l.card(it, band.first, band.second) }
            val prefix = BoardPickerMotion.occupiedPrefix(occupied)
            if (prefix != null &&
                col >= prefix &&
                l.rows.last() == band &&
                BoardPickerMotion.clearLastRow(l.rows)
            ) {
                return confirmEnd(l, 0)
            }
            l.done("board-picker-missing-card")
            return fail("Một ô tướng chưa rõ hình hoặc có khoảng trống giữa hàng; chưa kết luận hết danh sách.")
        }

        if (top < BoardScreens.VIEW_TOP - .005 || moves >= 160) {
            l.done("board-picker-seek")
            return fail("Vị trí tướng nằm ngoài phạm vi đo hoặc vượt 160 lần cuộn; không chọn lại hàng cũ.")
        }
        val distance = minOf(.24, maxOf(.075, bottom - (BoardScreens.VIEW_BOTTOM - .025)))
        val profile = l.pickerProfile
        val height = l.pickerHeight
        l.done(null)
        drag(.62, .80, .80 - distance, false, 850, "picker.scroll.next") {
            look(false, true) { after ->
                if (!validPicker(after) || after.pickerHeight != height) {
                    after.done("board-picker-grid")
                    return@look fail("Mất lưới tướng sau khi cuộn.")
                }
                val delta = BoardPickerMotion.displacement(profile, after.pickerProfile, height, 1)
                if (delta == null) {
                    after.done("board-picker-shift")
                    return@look fail("Không đo được khoảng cuộn bằng ảnh tướng; không kết luận đã hết danh sách.")
                }
                if (delta <= .004) {
                    // An ineffective gesture ALONE is never enough to finish a roster.
                    return@look confirmEnd(after, 0)
                }
                seek(after, scrolled + delta, moves + 1)
            }
        }
    }

    /** Prove the end by two independent stationary downward probes with a whole last row. */
    private fun confirmEnd(l: Look, still: Int) {
        if (!validPicker(l) || !BoardPickerMotion.clearLastRow(l.rows) || bottomSliver(l)) {
            l.done("board-picker-end")
            return fail("Danh sách không cuộn nhưng chưa thấy hàng cuối đầy đủ và khoảng trống phía dưới.")
        }
        if (still >= 2) {
            l.done(null)
            screen.note("Đã xác minh hàng cuối và 2 lần cuộn không di chuyển: hết tướng ở ô ${index + 1}")
            return closeAndEnd()
        }
        val profile = l.pickerProfile
        val height = l.pickerHeight
        l.done(null)
        drag(.62, .79, .63, false, 900, "picker.scroll.end-check") {
            look(false, true) { after ->
                if (!validPicker(after) || after.pickerHeight != height) {
                    after.done("board-picker-end")
                    return@look fail("Mất lưới khi xác minh hàng tướng cuối.")
                }
                val delta = BoardPickerMotion.displacement(profile, after.pickerProfile, height, 1)
                when {
                    delta == null -> {
                        after.done("board-picker-end")
                        fail("Không nhận dạng được chuyển động khi xác minh hàng cuối; chưa báo hoàn tất.")
                    }
                    abs(delta) > .004 -> {
                        after.done("board-picker-end")
                        fail("Lưới tướng vẫn cuộn được; không được kết luận hết danh sách.")
                    }
                    else -> confirmEnd(after, still + 1)
                }
            }
        }
    }

    private fun closeAndEnd() = tap(BoardScreens.PICKER_CLOSE_X, BoardScreens.PICKER_CLOSE_Y, 1200, "picker.close") {
        look(true) { l ->
            val back = BoardScreens.page(l.words, l.lobby) == BoardScreens.Page.BOARD
            l.done(null)
            if (back) {
                end(Outcome.Done(index - first))
            } else {
                fail("Đã thấy hàng cuối nhưng chưa đóng Chọn tướng về Chiến khu; không đánh dấu hoàn tất.")
            }
        }
    }

    /** A hero was tapped: "Chọn tướng" closes and the ranking shows it. */
    private fun picked(tries: Int): Unit = look(true) { l ->
        when (BoardScreens.page(l.words, l.lobby)) {
            BoardScreens.Page.BOARD -> {
                val name = BoardScreens.hero(l.words)
                l.done(null)
                screen.hero(index, name)
                screen.note("Tướng ${index + 1}: ${name ?: "chưa đọc được tên"}")
                tab(0, name)
            }
            BoardScreens.Page.PICKER -> {
                l.done(null)
                if (tries <
                    1
                ) {
                    screen.later(1500) { picked(tries + 1) }
                } else {
                    tap(BoardScreens.PICKER_CLOSE_X, BoardScreens.PICKER_CLOSE_Y, 1500, "picker.close") {
                        picked(
                            tries + 1
                        )
                    }
                }
            }
            else -> {
                l.done(null)
                if (tries < 3) {
                    screen.later(1500) { picked(tries + 1) }
                } else {
                    steps = 0
                    navigate()
                }
            }
        }
    }

    /** Tab [t] of the chosen ones for this hero. */
    private fun tab(t: Int, name: String?) {
        if (t >= tabs.size) {
            screen.finished(index)
            index++
            return nextHero()
        }
        val tab = tabs[t]
        screen.say("Tướng ${index + 1} ${name ?: ""} · ${tab.label}")
        if (tab == BoardScreens.Tab.TOP) {
            tap(tab.x, tab.y, 2500, "tab.${tab.name.lowercase(java.util.Locale.ROOT)}") {
                top(t, name, 0)
            }
        } else {
            tap(tab.x, tab.y, 2200, "tab.${tab.name.lowercase(java.util.Locale.ROOT)}") { scan(t, name, 1, null, 0) }
        }
    }

    /** "Số 1 máy chủ" for this hero: one photo, then back to the ranking. Its own "Đổi tướng" is never tapped. */
    private fun top(t: Int, name: String?, tries: Int): Unit = look(true) { l ->
        when (BoardScreens.page(l.words, l.lobby)) {
            BoardScreens.Page.TOP -> {
                val shown = l.words.firstOrNull {
                    it.y > .65 && it.y < .82 && abs(it.x - .5) < .12 && it.plain.any { c -> c.isLetter() }
                }?.text
                if (name != null &&
                    shown != null &&
                    HeroNames.slug(shown) != HeroNames.slug(name)
                ) {
                    screen.note("Số 1 máy chủ hiện $shown, khác tướng đang chọn $name")
                }
                l.done("hero-%03d-%s-01".format(index + 1, BoardScreens.Tab.TOP.slug))
                tap(BoardScreens.TOP_BACK_X, BoardScreens.TOP_BACK_Y, 1800, "top.back") { back(t, name, 0) }
            }
            else -> {
                l.done(null)
                if (tries <
                    2
                ) {
                    screen.later(1500) { top(t, name, tries + 1) }
                } else {
                    screen.note("Không mở được Số 1 máy chủ cho tướng ${index + 1}")
                    back(t, name, 0)
                }
            }
        }
    }

    /** Back on the ranking after "Số 1 máy chủ", for the next tab. */
    private fun back(t: Int, name: String?, tries: Int): Unit = look(true) { l ->
        when (BoardScreens.page(l.words, l.lobby)) {
            BoardScreens.Page.BOARD -> {
                l.done(null)
                tab(t + 1, name)
            }
            BoardScreens.Page.TOP -> {
                l.done(null)
                if (tries <
                    3
                ) {
                    tap(BoardScreens.TOP_BACK_X, BoardScreens.TOP_BACK_Y, 1800, "top.back") { back(t, name, tries + 1) }
                } else {
                    fail("Không quay lại được Chiến khu từ Số 1 máy chủ.")
                }
            }
            else -> {
                l.done(null)
                if (tries < 3) {
                    screen.later(1500) { back(t, name, tries + 1) }
                } else {
                    steps = 0
                    navigate()
                }
            }
        }
    }

    /** Photographs the list from its top, the screens its top players take, scrolling until a scroll changes nothing. */
    private fun scan(t: Int, name: String?, page: Int, last: IntArray?, still: Int): Unit = look(false) { l ->
        val tab = tabs[t]
        if (last != null && BoardScreens.same(l.signature, last)) {
            l.done(null)
            if (still <
                STILL_TRIES
            ) {
                return@look screen.later(1500) {
                    if (still ==
                        0
                    ) {
                        scan(t, name, page, last, 1)
                    } else {
                        drag(LIST_X, LIST_FROM, LIST_TO, false, 1200, "ranking.scroll") { scan(t, name, page, last, still + 1) }
                    }
                }
            }
            return@look tab(t + 1, name)
        }
        l.done("hero-%03d-%s-%02d".format(index + 1, tab.slug, page))
        if (page >= wanted[t].pages) return@look tab(t + 1, name)
        drag(LIST_X, LIST_FROM, LIST_TO, false, 1200, "ranking.scroll") { scan(t, name, page + 1, l.signature, 0) }
    }
}
