package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * Image-registration helpers for the eight-column "Chiến khu → Chọn tướng" popup.
 *
 * Band geometry alone repeats every .1768 screen heights. It cannot establish which
 * eight heroes a row contains or whether a small end-of-list movement occurred.
 * Actual portrait RGB profiles disambiguate the rows, including a last row of 1–7 heroes.
 */
internal object BoardPickerMotion {
    private val layout = HeroGrid.Layout(
        columns = BoardScreens.COLUMNS.take(5).toDoubleArray(),
        row0 = .4111,
        pitch = BoardScreens.ROW_PITCH,
        top = BoardScreens.VIEW_TOP,
        bottom = BoardScreens.VIEW_BOTTOM,
        park = .69,
        maxStep = .24,
        lastRowLimit = BoardScreens.VIEW_BOTTOM,
        profileTop = BoardScreens.VIEW_TOP,
        profileBottom = BoardScreens.VIEW_BOTTOM,
        band = .022
    )

    fun profile(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): FloatArray =
        HeroGrid.profile(width, height, layout, readRow)

    /**
     * Signed movement in screen heights: positive means content moved UP.
     * No reliable registration -> null. Never convert that to zero or to "end".
     */
    fun displacement(before: FloatArray, after: FloatArray, height: Int, direction: Int): Double? {
        if (height < 400 || before.isEmpty() || before.size != after.size || direction !in -1..1) return null
        val match = HeroGrid.shift(before, after)
        if (!match.reliable || abs(match.px) > height * .34) return null
        if (direction > 0 && match.px < -3) return null
        if (direction < 0 && match.px > 3) return null
        return match.px.toDouble() / height
    }

    /**
     * A fast reset fling may move by many rows, beyond the overlap matcher.
     * Same-screen detection compares RGB portraits at their actual positions,
     * rather than confusing repeated row borders with unchanged cards.
     */
    fun sameViewport(before: FloatArray, after: FloatArray): Boolean {
        if (before.isEmpty() || before.size != after.size || before.size % 15 != 0) return false
        val n = before.size / 15
        var total = 0.0
        var samples = 0
        for (y in 0 until n step 3) {
            val at = y * 15
            for (i in 0 until 15) {
                total += abs(before[at + i] - after[at + i])
                samples++
            }
        }
        return samples > 0 && total / samples < 7.5
    }

    fun validTop(rows: List<Pair<Double, Double>>): Boolean {
        val first = rows.firstOrNull() ?: return false
        return first.first in .320..0.373 &&
            first.second - first.first in (BoardScreens.CARD - .018)..(BoardScreens.CARD + .018)
    }

    /**
     * Only a complete, visibly followed-by-blank row can be the final one.
     * This is NOT enough by itself: the reader must also observe two verified
     * nonmoving downward gestures before returning Outcome.Done.
     */
    fun clearLastRow(rows: List<Pair<Double, Double>>): Boolean {
        val last = rows.lastOrNull() ?: return false
        return last.second - last.first in (BoardScreens.CARD - .018)..(BoardScreens.CARD + .018) &&
            last.second < BoardScreens.VIEW_BOTTOM - .009
    }

    /** Genuine final partial rows contain cards from column 0 with no holes. */
    fun occupiedPrefix(occupied: List<Boolean>): Int? {
        if (occupied.size != 8) return null
        val prefix = occupied.takeWhile { it }.size
        if (prefix == 0 || occupied.drop(prefix).any { it }) return null
        return prefix
    }
}
