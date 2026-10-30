package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * The scrolling description of a skill's Chi tiết tab. Measured on the user's 2400x1080 screenshots of Dyadia's
 * second skill: the text starts at y .225 and is cut at .47, its lines are .04 apart, and "Chú thích" sits right of
 * x .79 just below the cut. A description longer than the box shows glyphs right down to the cut; a short one
 * leaves its last band empty. [read] tells which, and gives a coarse picture of the text to see a scroll move it.
 */
object SkillDescription {
    const val LEFT = .46
    const val RIGHT = .92
    const val TOP = .225
    const val BOTTOM = .468

    /** The band of the box's last line; read left of "Chú thích". */
    const val TAIL_TOP = .445
    const val TAIL_RIGHT = .78

    /** A drag inside the text that holds still before lifting, so the text stops instead of flinging past lines. */
    const val SCROLL_X = .62
    const val SCROLL_FROM = .44
    const val SCROLL_TO = .27

    /** Glyphs are white, yellow, pink or violet over a dark violet panel; its faded art stays darker than this. */
    private const val BRIGHT = 175
    private const val GLYPH_ROW = 0.06
    private const val GLYPH_ROWS = 3
    private const val COLUMNS = 24
    private const val ROWS = 12

    class Reading(val reachesBottom: Boolean, val signature: IntArray)

    fun read(width: Int, height: Int, pixels: (y: Int, row: IntArray) -> Unit): Reading {
        val line = IntArray(width)
        val left = (LEFT * width).toInt()
        val right = (RIGHT * width).toInt().coerceAtMost(width)
        val top = (TOP * height).toInt()
        val bottom = (BOTTOM * height).toInt().coerceAtMost(height)
        val tailTop = (TAIL_TOP * height).toInt()
        val tailRight = (TAIL_RIGHT * width).toInt()
        val sums = LongArray(COLUMNS * ROWS)
        val counts = IntArray(COLUMNS * ROWS)
        var glyphRows = 0
        for (y in top until bottom) {
            pixels(y, line)
            val cellY = (y - top) * ROWS / (bottom - top)
            var bright = 0
            var samples = 0
            var x = left
            while (x < right) {
                val p = line[x]
                val r = p shr 16 and 255
                val g = p shr 8 and 255
                val b = p and 255
                val cell = cellY * COLUMNS + (x - left) * COLUMNS / (right - left)
                sums[cell] += ((r * 299 + g * 587 + b * 114) / 1000).toLong()
                counts[cell]++
                if (y >= tailTop && x < tailRight) {
                    samples++
                    if (maxOf(r, g, b) > BRIGHT) bright++
                }
                x += 2
            }
            if (samples > 0 && bright >= samples * GLYPH_ROW) glyphRows++
        }
        val signature = IntArray(sums.size) { if (counts[it] == 0) 0 else (sums[it] / counts[it]).toInt() }
        return Reading(glyphRows >= GLYPH_ROWS, signature)
    }

    /** True when the text moved: many cells differ, not the few a compression artefact or a glint changes. */
    fun changed(a: IntArray, b: IntArray): Boolean {
        if (a.size != b.size) return true
        val moved = a.indices.count { abs(a[it] - b[it]) > 10 }
        return moved >= maxOf(8, a.size / 10)
    }
}
