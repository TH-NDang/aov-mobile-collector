package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * A Chiến khu ranking's players, read from pixels: the rows of the list, the card a tapped row opens on the right,
 * the player's "Thông tin kiện tướng" page and their picture shown large. Measured on the user's 2000x900
 * screenshots, the same shape as the 2400x1080 ones the ranking was first measured on; positions are fractions of
 * the screen. A screenshot is read a row at a time through readRow, so unit tests run this on decoded JPEGs.
 */
object BoardPlayerPixels {
    /** The list shows players between the tabs' line and the user's own row, which stays at the bottom. */
    const val LIST_TOP = .185
    const val LIST_BOTTOM = .855

    /** Rank 1's row centre with the list at its top, and the rows' pitch: measured .284 and .1517 on two tabs. */
    const val ROW0 = .284
    const val PITCH = .1517

    /** A row is tapped only when its picture is wholly on screen: its centre this far inside the window. */
    const val ROW_TOP = .24
    const val ROW_BOTTOM = .80

    /** A row is tapped on the player's name, left of the middle; the card opens on the right, over x .772-.978. */
    const val ROW_X = .45

    /** The card's first icon, the person: "Thông tin kiện tướng". Then add friend and report, never tapped. */
    const val PROFILE_X = .80
    const val PROFILE_Y = .91

    /** Outside the card, with nothing under it: the empty top bar between the title and the region button. */
    const val CARD_CLOSE_X = .60
    const val CARD_CLOSE_Y = .06

    /** The page's back arrow. */
    const val BACK_X = .0915
    const val BACK_Y = .055

    /**
     * The two icons at the top right of the page's left panel, the same on every player's page: copy the player's
     * name, and the eye, which opens a box with their UID and a copy icon after it.
     */
    const val NAME_COPY_X = .254
    const val NAME_COPY_Y = .169
    const val EYE_X = .2775
    const val EYE_Y = .169

    /** The eye's box: its UID row, the number centred at about 48% and the copy icon just after it. */
    const val UID_ROW_TOP = .33
    const val UID_ROW_BOTTOM = .37
    const val UID_COPY_GAP = .011

    /** The X right of the picture shown large. */
    const val PHOTO_CLOSE_X = .7465
    const val PHOTO_CLOSE_Y = .1356

    /** The picture shown large dims everything around it: the left panel fell from 76 to 22. */
    const val POPUP_DIM = .55

    /** A drawn profile picture: measured 9.5, 3.5 pixelated for the tests (a plain placeholder is near 0). */
    const val AVATAR_DRAWN = 2.5

    /** A drawn picture shown large: a smooth selfie measured 1.7, .9 pixelated for the tests (a blank box near 0). */
    const val PHOTO_DRAWN = .6

    /**
     * What one screenshot shows of a player. [rows]: the centres of the list's rows wholly on screen, top to bottom.
     * [profile]: the list's picture column, row by row, to measure how far the list moved. [card]: the card's three
     * icons are there. [panelLum], [panel]: the left panel of the profile page, darkened under the picture shown large,
     * and its grey levels (without the scrolling titles under the numbers) to tell the page has stopped changing.
     * [stats]: the matches and win rate are drawn. [photo], [photoDetail]: the middle box where the picture is shown.
     */
    class Facts(
        val height: Int,
        val rows: List<Double>,
        val profile: FloatArray,
        val card: Boolean,
        val panelLum: Double,
        val panel: IntArray,
        val stats: Boolean,
        val avatarDetail: Double,
        val photo: IntArray,
        val photoDetail: Double,
        val uidCopy: Double? = null
    )

    /**
     * The UID's copy icon in the eye's box: the rightmost small white mark on the UID row, whose number is a dimmer
     * lavender. Measured at 56.1-57.1% across on the user's phone. Null when no such mark is there; read only once the
     * box's words are seen, as a page without the box has its hero's picture there.
     */
    fun uidCopy(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Double? {
        val ya = (UID_ROW_TOP * height).toInt()
        val yb = (UID_ROW_BOTTOM * height).toInt().coerceIn(ya + 1, height)
        val xa = (.40 * width).toInt()
        val xb = (.645 * width).toInt()
        val white = BooleanArray(xb - xa)
        val line = IntArray(width)
        for (y in ya until yb) {
            readRow(y, line)
            for (x in xa until xb) {
                val p = line[x]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                val lo = minOf(r, g, b)
                if (lo > 150 && maxOf(r, g, b) - lo < 70) white[x - xa] = true
            }
        }
        val bridge = maxOf(1, (.002 * width).toInt())
        var found: Double? = null
        var start = -1
        var last = -1

        fun close() {
            val w = (last - start + 1).toDouble() / width
            if (w in .004..0.02) found = (xa + (start + last) / 2.0) / width
            start = -1
        }
        for (i in white.indices) {
            if (!white[i]) continue
            if (start >= 0 && i - last > bridge) close()
            if (start < 0) start = i
            last = i
        }
        if (start >= 0) close()
        return found?.takeIf { it > .45 }
    }

    /** The number in the eye's box: a word of 10 to 20 digits on its UID row, right of the page's left panel. */
    fun infoUid(words: List<GameScreens.Word>): GameScreens.Word? = words.filter {
        it.y in UID_ROW_TOP - .03..UID_ROW_BOTTOM + .03 && it.x in .38..0.64 && GameScreens.uidIn(it.text) != null
    }.maxByOrNull { it.right }

    private fun lum(p: Int) = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000

    /** Mean brightness change between neighbouring pixels along the rows of a box. */
    private fun detail(width: Int, height: Int, x0: Double, x1: Double, y0: Double, y1: Double, readRow: (Int, IntArray) -> Unit): Double {
        val xa = (x0 * width).toInt().coerceIn(0, width - 2)
        val xb = (x1 * width).toInt().coerceIn(xa + 2, width)
        val ya = (y0 * height).toInt().coerceIn(0, height - 1)
        val yb = (y1 * height).toInt().coerceIn(ya + 1, height)
        val row = IntArray(width)
        var sum = 0L
        var n = 0
        for (y in ya until yb) {
            readRow(y, row)
            var previous = lum(row[xa])
            for (x in xa + 1 until xb) {
                val l = lum(row[x])
                sum += abs(l - previous)
                previous = l
                n++
            }
        }
        return if (n == 0) 0.0 else sum.toDouble() / n
    }

    /** Mean grey level of each cell of a [cols] x [rows] grid over a box. */
    private fun grid(
        width: Int,
        height: Int,
        x0: Double,
        x1: Double,
        y0: Double,
        y1: Double,
        cols: Int,
        rows: Int,
        readRow: (Int, IntArray) -> Unit
    ): IntArray {
        val xa = (x0 * width).toInt().coerceIn(0, width - 1)
        val xb = (x1 * width).toInt().coerceIn(xa + 1, width)
        val ya = (y0 * height).toInt().coerceIn(0, height - 1)
        val yb = (y1 * height).toInt().coerceIn(ya + 1, height)
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

    /** Grey levels the same but for a little noise or one small change. */
    fun same(a: IntArray, b: IntArray) =
        a.isNotEmpty() && a.size == b.size && a.indices.sumOf { abs(a[it] - b[it]) }.toDouble() / a.size < 3

    /**
     * The rows of the list, by the rank left of each picture: big medals for the first three, plain numbers after.
     * Between rows and around them the column is plain background (measured 0-1); the numbers measured over 2.
     */
    fun rows(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): List<Double> {
        val xa = (.30 * width).toInt()
        val xb = (.33 * width).toInt()
        val ya = (LIST_TOP * height).toInt()
        val yb = (LIST_BOTTOM * height).toInt()
        val bridge = maxOf(2, (.006 * height).toInt())
        val line = IntArray(width)
        val found = ArrayList<Double>()
        var start = -1
        var last = -1

        fun close() {
            val h = (last - start + 1).toDouble() / height
            val centre = (start + last) / 2.0 / height
            if (h in .025..0.085 && centre in ROW_TOP..ROW_BOTTOM) found += centre
            start = -1
        }
        for (y in ya until yb) {
            readRow(y, line)
            var sum = 0
            var previous = lum(line[xa])
            for (x in xa + 1 until xb) {
                val l = lum(line[x])
                sum += abs(l - previous)
                previous = l
            }
            if (sum.toDouble() / (xb - xa - 1) > 2) {
                if (start >= 0 && y - last > bridge) close()
                if (start < 0) start = y
                last = y
            }
        }
        // A number cut by the user's own row is not a row on screen.
        if (start >= 0 && yb - 1 - last > bridge) close()
        return found
    }

    /** Five thin bands down the middle of the players' pictures: what moves when the list is scrolled. */
    private val LIST_LAYOUT = HeroGrid.Layout(
        columns = doubleArrayOf(.356, .365, .3725, .38, .389),
        row0 = ROW0,
        pitch = PITCH,
        top = ROW_TOP,
        bottom = ROW_BOTTOM,
        park = .5,
        maxStep = .36,
        lastRowLimit = ROW_BOTTOM,
        profileTop = LIST_TOP,
        profileBottom = LIST_BOTTOM,
        band = .004
    )

    fun read(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Facts {
        val panelLum = grid(width, height, .08, .29, .10, .90, 1, 1, readRow)[0].toDouble()
        return Facts(
            height = height,
            rows = rows(width, height, readRow),
            profile = HeroGrid.profile(width, height, LIST_LAYOUT, readRow),
            // The card's icons, white on blue (measured 3.8); the list's own row under them is plain (0.3).
            card = detail(width, height, .78, .93, .885, .94, readRow) > 2.0,
            panelLum = panelLum,
            // Down to the numbers: the titles under them scroll sideways all the time.
            panel = grid(width, height, .08, .29, .12, .76, 8, 24, readRow),
            // "21842 Trận 55.6% Tỷ lệ thắng" measured 4.2; an empty strip is near 0.
            stats = detail(width, height, .09, .29, .67, .72, readRow) > 1.5,
            avatarDetail = detail(width, height, .09, .14, .16, .27, readRow),
            photo = grid(width, height, .33, .67, .11, .89, 16, 16, readRow),
            photoDetail = detail(width, height, .34, .66, .12, .88, readRow),
            uidCopy = uidCopy(width, height, readRow)
        )
    }

    /** The list stands where it stood: the same rows and its pictures not moved (a highlighted row or the card aside). */
    fun samePlace(a: Facts, b: Facts): Boolean {
        if (a.height != b.height || a.rows.size != b.rows.size) return false
        if (a.rows.zip(b.rows).any { (x, y) -> abs(x - y) > .008 }) return false
        if (a.rows.isEmpty()) return true
        val s = HeroGrid.shift(a.profile, b.profile)
        return s.reliable && abs(s.px) <= maxOf(2, a.height / 300)
    }

    /** How far the list moved up from [a] to [b], in screen heights, when the pictures tell it clearly. */
    fun moved(a: Facts, b: Facts): Double? {
        if (a.height != b.height || a.profile.isEmpty() || a.profile.size != b.profile.size) return null
        val s = HeroGrid.shift(a.profile, b.profile)
        return if (s.reliable) s.px.toDouble() / b.height else null
    }
}
