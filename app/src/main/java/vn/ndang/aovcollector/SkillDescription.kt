package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * The scrolling description of a skill's Chi tiết tab. Measured on the user's 2400x1080 screenshots of Dyadia's
 * second skill: the text starts at y .225 and is cut at .47, its lines are .04 apart, and "Chú thích" sits right of
 * x .79 just below the cut. A description longer than the box shows glyphs right down to the cut; a short one
 * leaves its last band empty. [read] tells which, and gives a coarse picture of the text to see a scroll move it.
 *
 * Other skills have a taller box: Billow's second skill (2000x900) runs to about .53, with its level table from .59.
 * So the last band reaches down to .545, above any table; and grey text, as its "(giảm nửa với đơn vị không phải
 * tướng)" lying across .445-.468, counts as much as white: that line alone, too dim for the first rule, was all the
 * old band saw, and the text was not scrolled.
 */
object SkillDescription {
    const val LEFT = .46
    const val RIGHT = .92
    const val TOP = .225
    const val BOTTOM = .468

    /** The band of the box's last line, down to the bottom of the tallest box; read left of "Chú thích". */
    const val TAIL_TOP = .445
    const val TAIL_BOTTOM = .545
    const val TAIL_RIGHT = .78

    /** A drag inside the text that holds still before lifting, so the text stops instead of flinging past lines. */
    const val SCROLL_X = .62
    const val SCROLL_FROM = .44
    const val SCROLL_TO = .27

    /**
     * Glyphs are white, yellow, pink, violet or grey over a dark violet panel (grey about 150 bright, the panel 50-70);
     * its faded art stays darker than these.
     */
    private const val BRIGHT = 175
    private const val GREY = 125
    private const val GLYPH_ROW = 0.04
    private const val GLYPH_ROWS = 3

    /** Rows of glyphs in the box for it to hold any text at all: about one line. */
    private const val TEXT_ROWS = 6
    private const val COLUMNS = 24
    private const val ROWS = 12

    /**
     * The rows the panel's text can move through, for [revealed]: from under the cost line down past the tallest box
     * (Aoi's second skill, with "Chú thích" under it, ends at .66). The divider and the level table below a shorter box
     * lie in it too; they stay where they are.
     */
    const val PANEL_TOP = .215
    const val PANEL_BOTTOM = .70

    /**
     * [text]: the box holds text, so the panel is open and drawn, not the hero page or a panel still loading. [glyphs]:
     * which pixels of the panel are text.
     */
    class Reading(val reachesBottom: Boolean, val signature: IntArray, val text: Boolean = true, val glyphs: Glyphs? = null)

    /** Which pixels of the panel ([PANEL_TOP] to [PANEL_BOTTOM]) are glyphs, every other one from [LEFT] to [RIGHT]. */
    class Glyphs(val columns: Int, val rows: Int, private val bits: ByteArray) {
        fun at(x: Int, y: Int) = y in 0 until rows && x in 0 until columns && bits[y * columns + x] != 0.toByte()

        fun count(y: Int) = (0 until columns).count { at(it, y) }
    }

    private fun glyph(p: Int): Boolean {
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        return maxOf(r, g, b) > BRIGHT || (r * 299 + g * 587 + b * 114) / 1000 > GREY
    }

    fun read(width: Int, height: Int, pixels: (y: Int, row: IntArray) -> Unit): Reading {
        val line = IntArray(width)
        val left = (LEFT * width).toInt()
        val right = (RIGHT * width).toInt().coerceAtMost(width)
        val top = (TOP * height).toInt()
        val bottom = (BOTTOM * height).toInt().coerceAtMost(height)
        val tailTop = (TAIL_TOP * height).toInt()
        val tailBottom = (TAIL_BOTTOM * height).toInt().coerceAtMost(height)
        val tailRight = (TAIL_RIGHT * width).toInt()
        val panelTop = (PANEL_TOP * height).toInt()
        val panelBottom = (PANEL_BOTTOM * height).toInt().coerceAtMost(height)
        val columns = (right - left + 1) / 2
        val bits = ByteArray(columns * maxOf(0, panelBottom - panelTop))
        val sums = LongArray(COLUMNS * ROWS)
        val counts = IntArray(COLUMNS * ROWS)
        var glyphRows = 0
        var textRows = 0
        for (y in minOf(top, panelTop) until maxOf(bottom, tailBottom, panelBottom)) {
            pixels(y, line)
            val inBox = y in top until bottom
            val inTail = y in top until tailBottom
            val inPanel = y in panelTop until panelBottom
            val cellY = (y - top) * ROWS / (bottom - top)
            var bright = 0
            var samples = 0
            var x = left
            while (x < right) {
                val p = line[x]
                val lit = glyph(p)
                if (inPanel && lit) bits[(y - panelTop) * columns + (x - left) / 2] = 1
                if (inBox) {
                    val cell = cellY * COLUMNS + (x - left) * COLUMNS / (right - left)
                    sums[cell] += ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000L
                    counts[cell]++
                }
                if (inTail && x < tailRight) {
                    samples++
                    if (lit) bright++
                }
                x += 2
            }
            val glyphs = samples > 0 && bright >= samples * GLYPH_ROW
            if (glyphs && y >= tailTop) glyphRows++
            if (glyphs && inBox) textRows++
        }
        val signature = IntArray(sums.size) { if (counts[it] == 0) 0 else (sums[it] / counts[it]).toInt() }
        return Reading(glyphRows >= GLYPH_ROWS, signature, textRows >= TEXT_ROWS, Glyphs(columns, maxOf(0, panelBottom - panelTop), bits))
    }

    /**
     * Where the text is held up to ([SkillReader.STRETCH]), from [SCROLL_FROM]: inside the box. Up to .10, past the box
     * into the skill's title, Biron's first skill showed nothing more (the user's run of 10/11, second try).
     */
    const val STRETCH_TO = .25

    /** A held text moves at least this many rows to be lined up with where it was; less is the jitter of a still one. */
    private const val MIN_SHIFT = 8

    /**
     * Glyphs found nowhere before, in a row of the held text ([FRESH] samples, every other pixel) and in all: a word
     * left under the box's edge, as Biron's "nửa.", is enough, not only a line across the box.
     */
    private const val FRESH = 3
    private const val FRESH_ROWS = 5
    private const val FRESH_ALL = 40

    /**
     * Whether text held past the end of its box ([after]) shows text that was not on screen [before]. Each row of the
     * held panel is lined up with the row it was in before, the shift that matches their glyph counts best; a glyph is
     * old if it was there s rows lower (the text, moved up), or where it is (the frame, the divider, the table). Fresh
     * glyphs in five rows make a word or a line the box hid: Biron's first skill ends "giảm một" above the table, its last word
     * under the box's edge, and a scroll cannot move a text the game thinks fits.
     */
    fun revealed(before: Glyphs, after: Glyphs): Boolean {
        if (before.columns != after.columns || before.rows != after.rows || before.rows == 0) return false
        val n = before.rows
        val a = IntArray(n) { before.count(it) }
        val b = IntArray(n) { after.count(it) }
        var shift = -1
        var least = Double.MAX_VALUE
        for (s in MIN_SHIFT until n / 2) {
            var err = 0L
            var mass = 0L
            for (y in 0 until n - s) {
                err += abs(b[y] - a[y + s])
                mass += b[y] + a[y + s]
            }
            if (mass > 0 && err.toDouble() / mass < least) {
                least = err.toDouble() / mass
                shift = s
            }
        }
        if (shift < 0) return false

        fun moved(x: Int, y: Int) = (-1..1).any { dy -> (-1..1).any { dx -> before.at(x + dx, y + shift + dy) } }
        var rows = 0
        var all = 0
        for (y in 0 until n) {
            val count = (0 until after.columns).count { x -> after.at(x, y) && !before.at(x, y) && !moved(x, y) }
            if (count >= FRESH) rows++
            all += count
        }
        return rows >= FRESH_ROWS && all >= FRESH_ALL
    }

    /** True when the text moved: many cells differ, not the few a compression artefact or a glint changes. */
    fun changed(a: IntArray, b: IntArray): Boolean {
        if (a.size != b.size) return true
        val moved = a.indices.count { abs(a[it] - b[it]) > 10 }
        return moved >= maxOf(8, a.size / 10)
    }
}
