package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * Đề cử Pro read from pixels, so following the list while it scrolls needs no OCR: the yellow "Dùng combo" buttons,
 * and the Chuẩn bị panel, to tell a combo just applied has finished drawing. Measured on the user's 2000x900
 * screenshots of Chuẩn bị, Chọn tướng and the Đề cử Pro tab; positions are fractions of the screen.
 */
object ProPixels {
    /** The list's window in the popup: a button touching its edges is cut, and not where it seems. */
    const val LIST_TOP = .197
    const val LIST_BOTTOM = .891

    /** A button's pill, measured at x .7025-.8135; its label is dark over the yellow. */
    private const val LEFT = .7025
    private const val RIGHT = .8135

    /**
     * The pill's yellow. On the user's phone its upper half is (200, 171, 66) and its lower half (213, 190, 84); the
     * screenshots sent in chat had red over 210, and a first rule of red over 200 missed the phone's upper half, cut
     * every pill in two around its label, and lost the list after two combos.
     */
    private fun yellow(p: Int): Boolean {
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        return r > 175 && g > 135 && b < 140 && r - b > 90
    }

    private fun lum(p: Int) = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000

    /**
     * The "Dùng combo" buttons wholly in the list, top to bottom, as words where they are. A row is yellow when half
     * of the pill's middle is; the label's rows are not, so a gap of up to .03 of the height is bridged. Measured:
     * pills .058 tall at .41 and .776 with the list at its top; nothing on Chuẩn bị, Chọn tướng or the ranking.
     */
    fun buttons(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): List<GameScreens.Word> {
        val xa = (.715 * width).toInt()
        val xb = (.80 * width).toInt()
        val ya = (.15 * height).toInt()
        val yb = (.95 * height).toInt()
        val bridge = maxOf(2, (.03 * height).toInt())
        val line = IntArray(width)
        val found = ArrayList<GameScreens.Word>()
        var start = -1
        var last = -1

        fun close() {
            val h = (last - start + 1).toDouble() / height
            if (h in .04..0.08 && start > LIST_TOP * height && last < LIST_BOTTOM * height) {
                found += GameScreens.Word("Dùng combo", LEFT, start.toDouble() / height, RIGHT, (last + 1).toDouble() / height)
            }
            start = -1
        }
        for (y in ya until yb) {
            readRow(y, line)
            var n = 0
            var hit = 0
            var x = xa
            while (x < xb) {
                n++
                if (yellow(line[x])) hit++
                x += 2
            }
            if (hit * 2 >= n) {
                if (start >= 0 && y - last > bridge) close()
                if (start < 0) start = y
                last = y
            }
        }
        if (start >= 0) close()
        return found
    }

    /** Grey levels of Chuẩn bị's right panel (combo tabs, items, emblems, arcana, spell) on a 16x12 grid. */
    fun panel(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): IntArray {
        val cols = 16
        val rows = 12
        val xa = (.59 * width).toInt()
        val xb = (.95 * width).toInt()
        val ya = (.15 * height).toInt()
        val yb = (.80 * height).toInt()
        val sums = LongArray(cols * rows)
        val counts = IntArray(cols * rows)
        val line = IntArray(width)
        for (y in ya until yb step 2) {
            readRow(y, line)
            val r = (y - ya) * rows / (yb - ya)
            for (x in xa until xb step 2) {
                val i = r * cols + (x - xa) * cols / (xb - xa)
                sums[i] += lum(line[x])
                counts[i]++
            }
        }
        return IntArray(sums.size) { if (counts[it] == 0) 0 else (sums[it] / counts[it]).toInt() }
    }

    /** The same picture but for a little noise. */
    fun same(a: IntArray, b: IntArray) =
        a.isNotEmpty() && a.size == b.size && a.indices.sumOf { abs(a[it] - b[it]) }.toDouble() / a.size < 3
}
