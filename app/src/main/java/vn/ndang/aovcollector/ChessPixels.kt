package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * What the Bách khoa screens show, read from pixels alone so the fast capture needs no OCR. Measured on the user's
 * 2000x900 screenshots of every encyclopedia screen; positions are fractions of the screen. A screenshot is read a row
 * at a time through [readRow], so unit tests run the same code on decoded JPEGs.
 */
object ChessPixels {
    private fun lum(p: Int) = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000

    /** Brightness steps above 20 along the rows of a box: text, art and card edges make many, plain background none. */
    private fun steps(width: Int, height: Int, x0: Double, x1: Double, y0: Double, y1: Double, readRow: (Int, IntArray) -> Unit): Int {
        val xa = (x0 * width).toInt().coerceIn(0, width - 2)
        val xb = (x1 * width).toInt().coerceIn(xa + 2, width)
        val ya = (y0 * height).toInt().coerceIn(0, height - 1)
        val yb = (y1 * height).toInt().coerceIn(ya + 1, height)
        val row = IntArray(width)
        var count = 0
        for (y in ya until yb) {
            readRow(y, row)
            var previous = lum(row[xa])
            for (x in xa + 1 until xb) {
                val l = lum(row[x])
                if (abs(l - previous) > 20) count++
                previous = l
            }
        }
        return count
    }

    /** Mean brightness change between neighbouring pixels of a box, across and down. */
    private fun detail(width: Int, height: Int, x0: Double, x1: Double, y0: Double, y1: Double, readRow: (Int, IntArray) -> Unit): Double {
        val xa = (x0 * width).toInt().coerceIn(0, width - 2)
        val xb = (x1 * width).toInt().coerceIn(xa + 2, width)
        val ya = (y0 * height).toInt().coerceIn(0, height - 2)
        val yb = (y1 * height).toInt().coerceIn(ya + 2, height)
        val row = IntArray(width)
        var above: IntArray? = null
        var sum = 0L
        var n = 0
        for (y in ya until yb) {
            readRow(y, row)
            val l = IntArray(xb - xa) { lum(row[xa + it]) }
            for (i in 1 until l.size) {
                sum += abs(l[i] - l[i - 1])
                n++
            }
            above?.let { a ->
                for (i in l.indices) {
                    sum += abs(l[i] - a[i])
                    n++
                }
            }
            above = l
        }
        return if (n == 0) 0.0 else sum.toDouble() / n
    }

    /** Fewer steps than this in the bottom strip: nothing more below. Every continuing screen measured had over 1300. */
    private const val BLANK_STEPS = 60

    /**
     * The bottom strip of the page, or of an open drawer, is plain background: everything there is to see is on
     * screen, so dragging further would only cost a screenshot. Measured: Thông tin cơ bản, Tương tác, Trang bị and
     * a short item drawer had no steps there; the hero list, Phe & Nghề, Nhiệm vụ, Chúc phúc and long drawers had
     * well over a thousand.
     */
    fun blankBelow(width: Int, height: Int, drawer: Boolean, readRow: (Int, IntArray) -> Unit) =
        steps(width, height, if (drawer) .37 else .22, .93, .86, .995, readRow) < BLANK_STEPS

    /** Midway between the five card columns of Tất cả quân cờ, measured on the user's list. */
    private val HERO_GAPS = doubleArrayOf(.3485, .494, .640, .7855)

    /**
     * Tất cả quân cờ: between its card columns the background shows from the tabs to the bottom. Measured at most
     * .13 there on the list; every other screen, the open drawer and Save Photo over the list, had a gap over .45.
     */
    fun heroGrid(width: Int, height: Int, readRow: (Int, IntArray) -> Unit) =
        HERO_GAPS.all { detail(width, height, it - .003, it + .003, .25, .98, readRow) < .3 }

    /** Trang bị's columns of combined items, right of the basic item that starts each row. */
    private val ITEM_COLUMNS = doubleArrayOf(.3235, .389, .4545, .520, .5855, .651)

    private fun meanLum(row: IntArray, from: Int, to: Int): Double {
        var sum = 0L
        for (x in from until to) sum += lum(row[x])
        return sum.toDouble() / maxOf(1, to - from)
    }

    /**
     * A row through a loading square in the item column at [cx]: lighter than the background just beside the column
     * and nearly plain along the row. Measured on the user's half-loaded Trang bị: the squares stand 19 to 37 above
     * the background, with a mean step of 0.5 to 3.9 through their faint diamond; a drawn picture steps 9 or more,
     * and an empty place is the background itself.
     */
    private fun loadingRow(row: IntArray, width: Int, cx: Double): Boolean {
        val xa = ((cx - .015) * width).toInt().coerceIn(0, width - 2)
        val xb = ((cx + .015) * width).toInt().coerceIn(xa + 2, width)
        var sum = 0L
        var steps = 0L
        var previous = lum(row[xa])
        for (x in xa + 1 until xb) {
            val l = lum(row[x])
            sum += l
            steps += abs(l - previous)
            previous = l
        }
        val n = xb - xa - 1
        val beside = (
            meanLum(row, ((cx - .038) * width).toInt().coerceIn(0, width), ((cx - .027) * width).toInt().coerceIn(0, width)) +
                meanLum(row, ((cx + .027) * width).toInt().coerceIn(0, width), ((cx + .038) * width).toInt().coerceIn(0, width))
            ) / 2
        return sum.toDouble() / n - beside > 10 && steps.toDouble() / n < 6
    }

    /**
     * How many item pictures on Trang bị are still loading. The game first puts a plain lighter square with a faint
     * grey diamond where each picture goes and swaps the picture in when it has loaded, so a page shot too early shows
     * squares. Each column is read from top to bottom, so a dragged page is read as well; a square is at least .04 of
     * the height tall, which a row's separator line is not. Measured: 15 squares on the user's half-loaded tab, none
     * on the loaded one.
     */
    fun loadingTiles(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Int {
        val row = IntArray(width)
        val runs = IntArray(ITEM_COLUMNS.size)
        val tall = (.04 * height).toInt()
        var found = 0
        for (y in (.20 * height).toInt() until (.97 * height).toInt()) {
            readRow(y, row)
            for ((c, cx) in ITEM_COLUMNS.withIndex()) {
                if (loadingRow(row, width, cx)) {
                    runs[c]++
                } else {
                    if (runs[c] >= tall) found++
                    runs[c] = 0
                }
            }
        }
        return found + runs.count { it >= tall }
    }

    /** The centres of the five card columns of Tất cả quân cờ. */
    private val HERO_CARDS = doubleArrayOf(.275, .421, .567, .713, .858)

    /**
     * Tất cả quân cờ shows a card in the lower third of the screen (.66 to the bottom). Every card has rows of text
     * or art, where a row through its column steps over 3 on average; the longest plain stretch between them, from
     * the last line of a card to the next card's portrait, measured .19 to .25 of the height on the user's lists. A
     * stretch a third of the screen tall therefore holds part of a card unless the list has ended above it.
     */
    fun heroCardsBelow(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Boolean {
        val row = IntArray(width)
        val busy = IntArray(HERO_CARDS.size)
        for (y in (.66 * height).toInt() until (.995 * height).toInt()) {
            readRow(y, row)
            for ((c, cx) in HERO_CARDS.withIndex()) {
                val xa = ((cx - .05) * width).toInt().coerceIn(0, width - 2)
                val xb = ((cx + .05) * width).toInt().coerceIn(xa + 2, width)
                var steps = 0L
                var previous = lum(row[xa])
                for (x in xa + 1 until xb) {
                    val l = lum(row[x])
                    steps += abs(l - previous)
                    previous = l
                }
                if (steps > 3L * (xb - xa - 1)) busy[c]++
            }
        }
        return busy.any { it >= .012 * height }
    }

    /** The round picture at an open drawer's top left is drawn (measured 3.1 to 7.4; a plain disc is below 1). */
    fun drawerArt(width: Int, height: Int, readRow: (Int, IntArray) -> Unit) = detail(width, height, .37, .44, .06, .20, readRow) > 1.5

    /** A hero drawer's skill picture is drawn (measured 9.4 on Violet). */
    fun skillArt(width: Int, height: Int, readRow: (Int, IntArray) -> Unit) = detail(width, height, .37, .44, .45, .75, readRow) > 1.5

    /** The game's Save Photo box: where its Sure and CANCEL buttons are. */
    data class Dialog(val sure: Pair<Double, Double>, val cancel: Pair<Double, Double>)

    /**
     * Save Photo, the box the game shows on a long press: a black title bar over two light grey buttons, Sure on the
     * left and CANCEL on the right, measured at .373-.487 and .505-.605 of the height on the user's screenshot. Found
     * by scanning, not at fixed places, so a slightly different size still matches; nothing in the encyclopedia's
     * purple screens has this black bar over light buttons.
     */
    fun saveDialog(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Dialog? {
        val row = IntArray(width)
        val samples = (0 until 30).map { ((.33 + .30 * it / 29) * width).toInt().coerceIn(0, width - 1) }

        fun share(y: Int, test: (Int) -> Boolean): Double {
            readRow(y, row)
            return samples.count { test(lum(row[it])) }.toDouble() / samples.size
        }
        val top = (.22 * height).toInt()
        val bottom = (.80 * height).toInt()
        // Runs of black rows: the title bar is 0-8 dark, the dimmed game around the box 25-50.
        val black = BooleanArray(bottom) { y -> y >= top && share(y) { it < 15 } >= .7 }
        var y = top
        while (y < bottom) {
            if (!black[y]) {
                y++
                continue
            }
            val start = y
            while (y < bottom && black[y]) y++
            if (y - 1 - start in (.06 * height).toInt()..(.20 * height).toInt()) {
                buttons(width, height, y - 1, bottom, readRow)?.let { return it }
            }
        }
        return null
    }

    /** The two buttons right under a title bar ending at [barEnd], if they are there. */
    private fun buttons(width: Int, height: Int, barEnd: Int, bottom: Int, readRow: (Int, IntArray) -> Unit): Dialog? {
        val row = IntArray(width)
        val samples = (0 until 30).map { ((.33 + .30 * it / 29) * width).toInt().coerceIn(0, width - 1) }

        fun share(y: Int, test: (Int) -> Boolean): Double {
            readRow(y, row)
            return samples.count { test(lum(row[it])) }.toDouble() / samples.size
        }
        // Runs of light rows under the bar: a thin light border first, then the buttons, .1 of the height tall.
        // Rows through the labels are a third dark: half light is still a button row.
        val end = minOf(bottom, barEnd + (.25 * height).toInt())
        var buttonStart = -1
        var buttonEnd = -1
        var y = barEnd + 1
        while (y < end && buttonStart < 0) {
            if (share(y) { it > 170 } < .5) {
                y++
                continue
            }
            val start = y
            while (y < end && share(y) { it > 170 } >= .5) y++
            if (start - barEnd > .05 * height) return null
            if (y - 1 - start >= .05 * height) {
                buttonStart = start
                buttonEnd = y - 1
            }
        }
        if (buttonStart < 0) return null
        // Along a row above the labels, the buttons are bright (over 210) and the seam between them is not (161-174).
        readRow(buttonStart + (buttonEnd - buttonStart) * 15 / 100, row)
        val stretches = ArrayList<IntRange>()
        var from = -1
        val x0 = (.15 * width).toInt()
        val x1 = (.85 * width).toInt()
        for (x in x0..x1) {
            val light = x < x1 && lum(row[x]) > 195
            if (light && from < 0) from = x
            if (!light && from >= 0) {
                stretches += from until x
                from = -1
            }
        }
        val two = stretches.filter {
            it.last - it.first > .10 * width
        }.sortedByDescending { it.last - it.first }.take(2).sortedBy { it.first }
        if (two.size != 2) return null
        val middle = (buttonStart + buttonEnd) / 2.0 / height
        return Dialog(
            (two[0].first + two[0].last) / 2.0 / width to middle,
            (two[1].first + two[1].last) / 2.0 / width to middle
        )
    }

    /** The two columns of Chúc phúc cards, at their pictures' centres. */
    private val BLESSING_COLUMNS = doubleArrayOf(.277, .627)

    private fun white(p: Int): Boolean {
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val lo = minOf(r, g, b)
        return lo > 185 && maxOf(r, g, b) - lo < 60
    }

    /**
     * The white line pictures of the Chúc phúc cards wholly on screen, top to bottom and left to right. Each is a run
     * of rows with white in its column, about .15 of the height, with a clean gap of over .1 between cards; one cut by
     * the tabs or the bottom edge is left for the next page.
     */
    fun blessingIcons(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): List<Pair<Double, Double>> {
        val row = IntArray(width)
        val top = (.20 * height).toInt()
        val inked = Array(BLESSING_COLUMNS.size) { BooleanArray(height) }
        for (y in top until height) {
            readRow(y, row)
            for ((c, cx) in BLESSING_COLUMNS.withIndex()) {
                val xa = ((cx - .04) * width).toInt()
                val xb = ((cx + .04) * width).toInt()
                var n = 0
                var w = 0
                var x = xa
                while (x < xb) {
                    n++
                    if (white(row[x])) w++
                    x += 2
                }
                inked[c][y] = w * 100 >= n * 3
            }
        }
        val found = ArrayList<Pair<Double, Double>>()
        val bridge = (.02 * height).toInt()
        for ((c, cx) in BLESSING_COLUMNS.withIndex()) {
            var start = -1
            var last = -1

            fun close() {
                if (start < 0) return
                val h = (last - start).toDouble() / height
                // Wholly on screen: not touching the tabs' strip above or the bottom edge.
                if (h in .09..0.24 && start > .23 * height && last < .975 * height) {
                    found += cx to (start + last) / 2.0 / height
                }
                start = -1
            }
            for (y in top until height) {
                if (inked[c][y]) {
                    if (start >= 0 && y - last > bridge) close()
                    if (start < 0) start = y
                    last = y
                }
            }
            if (start >= 0) {
                // A run reaching the bottom edge is cut there.
                if (height - 1 - last <= bridge) start = -1 else close()
            }
        }
        // Reading order: cards less than .08 apart in height share a row.
        val rows = ArrayList<MutableList<Pair<Double, Double>>>()
        for (icon in found.sortedBy { it.second }) {
            if (rows.isEmpty() || icon.second - rows.last().first().second > .08) rows += ArrayList<Pair<Double, Double>>()
            rows.last() += icon
        }
        return rows.flatMap { r -> r.sortedBy { it.first } }
    }

    /**
     * A blessing picture as 16x16 cells, each white or not, in a box around the centre [blessingIcons] gave. The
     * centre comes from the picture's own white rows, so the same card after a drag gives nearly the same cells.
     */
    fun blessingArt(width: Int, height: Int, x: Double, y: Double, readRow: (Int, IntArray) -> Unit): String {
        val x0 = ((x - .05) * width).toInt().coerceIn(0, width - 16)
        val x1 = ((x + .05) * width).toInt().coerceIn(x0 + 16, width)
        val y0 = ((y - .09) * height).toInt().coerceIn(0, height - 16)
        val y1 = ((y + .09) * height).toInt().coerceIn(y0 + 16, height)
        val white = IntArray(256)
        val total = IntArray(256)
        val row = IntArray(width)
        for (yy in y0 until y1) {
            readRow(yy, row)
            val r = (yy - y0) * 16 / (y1 - y0)
            for (xx in x0 until x1) {
                val c = r * 16 + (xx - x0) * 16 / (x1 - x0)
                total[c]++
                if (white(row[xx])) white[c]++
            }
        }
        return String(CharArray(256) { if (white[it] * 4 >= total[it] && total[it] > 0) '1' else '0' })
    }

    /** At most 16 of 256 cells differ: the same picture, give or take a pixel of drag. */
    fun sameBlessingArt(a: String, b: String) = a.length == b.length && a.indices.count { a[it] != b[it] } <= 16

    /** The Chúc phúc list below its tabs, profiled by [HeroGrid] through its pictures, names and lines of text. */
    private val BLESSING_LIST = HeroGrid.Layout(
        doubleArrayOf(.277, .40, .627, .75, .85), .5, 1.0, 0.0, 1.0, .5, .6, 1.0,
        profileTop = .275, profileBottom = .995, band = .035
    )

    fun blessingProfile(width: Int, height: Int, readRow: (Int, IntArray) -> Unit) = HeroGrid.profile(width, height, BLESSING_LIST, readRow)

    /**
     * How far a page drag moved the Chúc phúc list, in screen heights; null when it cannot be told. A drag goes .6 of
     * the screen and the list .59 on the user's phone, less at its end, so only a ninth of the list stays on both
     * pages: they are compared down to that much shared, and only over the moves a drag up can make.
     */
    fun blessingMoved(before: FloatArray, after: FloatArray, height: Int): Double? {
        if (before.isEmpty() || before.size != after.size) return null
        val s = HeroGrid.shift(before, after, -(.02 * height).toInt()..(.64 * height).toInt(), (.11 * height).toInt())
        return if (s.reliable) s.px.toDouble() / height else null
    }

    /**
     * Whether [tile] is the card [seen] was on the page before, the list having [moved] up since. By place: a card cut
     * by the tabs shows only part of its picture, which then matches nothing, and different blessings can share one
     * picture (three of Truyền thuyết's). By the picture when the move is not known.
     */
    fun sameBlessing(seen: ChessWiki.Tile, tile: ChessWiki.Tile, moved: Double?) = if (moved == null) {
        sameBlessingArt(seen.id, tile.id)
    } else {
        abs(seen.x - tile.x) < .05 && abs(seen.y - moved - tile.y) < .07
    }
}
