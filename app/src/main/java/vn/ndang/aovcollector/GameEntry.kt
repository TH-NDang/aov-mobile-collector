package vn.ndang.aovcollector

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Screens met between opening the game and the hero list, on the 2400x1080 landscape layout. Most are told
 * apart from screenshot pixels as [HeroGrid] does: the lobby, the login screen and the close button of event
 * popups. Screens known by their words (the reward screens' "Ấn để tiếp tục", the registration form, the
 * Garena login page, the player's profile) are told apart in [refine], from text read on the phone.
 */
object GameScreens {
    enum class Kind { LIST, LOBBY, HERO_PAGE, POPUP, OTHER, LOGIN, REGISTER, CONTINUE, FORM, PROFILE, UID_DIALOG, CHESS_EXPERIENCE, CHESS_TIP }

    /** A line of text on screen, from OCR or from the accessibility tree, with its box as screen fractions. */
    class Word(val text: String, val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val plain = plain(text)
        val x get() = (left + right) / 2
        val y get() = (top + bottom) / 2

        override fun toString() = "\"$text\" ${(x * 100).roundToInt()}%,${(y * 100).roundToInt()}%"
    }

    /**
     * What one screenshot shows: [close] is a popup's close button, [signature] a coarse thumbnail, [tap] where
     * to press to get past the screen, [words] the text read on it and [uid] the account UID printed on it.
     */
    class Reading(
        val kind: Kind,
        val close: Pair<Double, Double>?,
        val signature: IntArray,
        val words: List<Word> = emptyList(),
        val uid: String? = null,
        val tap: Pair<Double, Double>? = null
    ) {
        fun find(phrase: String, where: (Word) -> Boolean = { true }) = words.firstOrNull { it.plain.contains(phrase) && where(it) }
    }

    /** The "Tướng" tab of the lobby's bottom bar (its helmet icon and label), which opens the All-heroes list. */
    const val HEROES_X = .275
    const val HEROES_Y = .922

    /** The player's avatar at the top left of the lobby, which opens their profile. */
    const val AVATAR_X = .0825
    const val AVATAR_Y = .083

    /** The settings gear at the top right of the lobby. */
    const val SETTINGS_X = .88
    const val SETTINGS_Y = .041

    /** "Đăng nhập Garena", the first of the login screen's three buttons. */
    const val GARENA_X = .264
    const val GARENA_Y = .822

    fun read(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Reading {
        val signature = signature(width, height, readRow)
        if (width <= height) return Reading(Kind.OTHER, null, signature) // the game turns the screen when it starts
        if (isList(width, height, readRow)) return Reading(Kind.LIST, null, signature)
        if (isLobby(width, height, readRow)) return Reading(Kind.LOBBY, null, signature)
        if (isLogin(width, height, readRow)) return Reading(Kind.LOGIN, null, signature, tap = GARENA_X to GARENA_Y)
        if (HeroGrid.hasBackArrow(width, height, readRow)) return Reading(Kind.HERO_PAGE, null, signature)
        val close = closeButton(width, height, readRow)
        return Reading(if (close != null) Kind.POPUP else Kind.OTHER, close, signature)
    }

    /** Whether [refine] can tell more about a screen of this kind from its words. */
    fun wordy(kind: Kind) = kind == Kind.OTHER || kind == Kind.POPUP || kind == Kind.HERO_PAGE

    /**
     * A [read] made more precise by the words on the screen, and by the [uid] read at its bottom. Words only
     * name screens whose pixels say little: the Garena login page (its form is filled through accessibility),
     * the registration form with its "Đ.KÝ SAU" (later) button, the reward screens' "Ấn để tiếp tục", the
     * player's profile and its UID box.
     */

    /** Two optional new-account screens from the Cờ Liên Quân onboarding flow (2048×921). */
    const val CHESS_VETERAN_X = .332
    const val CHESS_VETERAN_Y = .490
    const val CHESS_GOT_IT_X = .250
    const val CHESS_GOT_IT_Y = .577

    fun refine(r: Reading, words: List<Word>, uid: String?): Reading {
        fun with(kind: Kind, tap: Pair<Double, Double>? = null) = Reading(kind, r.close, r.signature, words, uid, tap)
        val all = words.map { it.plain }
        // OCR can split a sentence between lines. Require screen-specific words, never just "Kỳ cựu"
        // or "Đã rõ": those labels can also occur elsewhere in the game.
        val full = plain(words.joinToString(" ") { it.text })
        val chessExperience = (full.contains("hoannghenh") || full.contains("autochess")) &&
            full.contains("kycuu") &&
            (full.contains("tanthu") || full.contains("autochess"))
        if (chessExperience) {
            val name = words.firstOrNull { it.plain.contains("kycuu") && it.x in .25..0.46 && it.y in .55..0.75 }
            return with(Kind.CHESS_EXPERIENCE, (name?.x ?: CHESS_VETERAN_X) to CHESS_VETERAN_Y)
        }
        val chessTip = full.contains("daro") &&
            full.contains("colienquan") &&
            (full.contains("dauluyen") || full.contains("nangcaotrinhdo"))
        if (chessTip) {
            val confirm = words.firstOrNull { it.plain.contains("daro") && it.x in .13..0.44 && it.y in .46..0.70 }
            return with(Kind.CHESS_TIP, (confirm?.x ?: CHESS_GOT_IT_X) to (confirm?.y ?: CHESS_GOT_IT_Y))
        }
        if (all.any {
                it.contains("matkhau")
            } &&
            all.any { it.contains("quenmatkhau") || it.contains("taotaikhoanmoi") }
        ) {
            return with(Kind.FORM)
        }
        words.firstOrNull { it.plain.contains("kysau") && it.x > .55 && it.y < .35 }?.let { return with(Kind.REGISTER, it.x to it.y) }
        words.firstOrNull {
            it.plain.contains("detieptuc") && it.y > .72 && it.x in .3..0.7
        }?.let { return with(Kind.CONTINUE, it.x to it.y) }
        if (all.any { it.contains("uidcuabanla") }) return with(Kind.UID_DIALOG)
        if (all.any { it.contains("thongtinkientuong") }) return with(Kind.PROFILE)
        return Reading(r.kind, r.close, r.signature, words, uid, r.tap)
    }

    /** An account UID in [text]: 10–20 digits, spaces and separators removed. */
    fun uidIn(text: String): String? = Regex("\\d{10,20}").find(text.replace(Regex("[\\s.,]"), ""))?.value

    /** Lower case without accents, đ as d, letters and digits only: "Đ.KÝ SAU" is "dkysau". */
    fun plain(text: String) = Normalizer.normalize(
        text,
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('đ', 'd').replace(Regex("[^a-z0-9]"), "")

    // The three login buttons in a row: Garena orange, Facebook blue, Google white, measured on the user's phone.
    private val loginButtons =
        arrayOf(
            doubleArrayOf(.16, .37, 206.0, 151.0, 94.0),
            doubleArrayOf(.395, .605, 77.0, 129.0, 202.0),
            doubleArrayOf(.63, .84, 255.0, 255.0, 255.0)
        )

    /** The login screen: its three buttons of very different colours side by side, whatever the art behind. */
    fun isLogin(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Boolean {
        val y0 = (.795 * height).toInt()
        val y1 = (.85 * height).toInt()
        val row = IntArray(width)
        val near = IntArray(3)
        val count = IntArray(3)
        for (y in y0 until y1) {
            readRow(y, row)
            for (i in 0 until 3) {
                val b = loginButtons[i]
                var x = (b[0] * width).toInt()
                while (x < (b[1] * width).toInt()) {
                    val p = row[x]
                    count[i]++
                    if (abs(((p shr 16) and 255) - b[2]) + abs(((p shr 8) and 255) - b[3]) + abs((p and 255) - b[4]) < 90) near[i]++
                    x += 2
                }
            }
        }
        return (0 until 3).all { near[it] >= .5 * count[it] }
    }

    /** The grid of [HeroGrid] with cards in it: a black logo screen has plain gaps between its columns too. */
    fun isList(width: Int, height: Int, readRow: (Int, IntArray) -> Unit) =
        HeroGrid.isGrid(width, height, readRow) && HeroGrid.columns.count { HeroGrid.hasCard(width, height, it, .55, readRow) } >= 3

    // The white icons of the bottom bar's tabs Túi đồ, Tướng, Trang bị and N.vụ, between ICON_TOP and ICON_BOTTOM.
    private val tabIcons =
        arrayOf(doubleArrayOf(.122, .151), doubleArrayOf(.251, .277), doubleArrayOf(.372, .392), doubleArrayOf(.506, .526))
    private const val ICON_TOP = .895
    private const val ICON_BOTTOM = .95

    /**
     * Whether the lobby is in front with nothing over it. Its bottom bar shows white tab icons, 28–64% of each
     * icon's box on the user's screenshots; a popup dims them to nothing, and the loading screen and the list
     * reach 6% at most there. A white form over the lobby fills the boxes entirely, so a box counts only when
     * an icon shape, not a white sheet, is in it. One icon may be covered, by a badge for example.
     */
    fun isLobby(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Boolean {
        val y0 = (ICON_TOP * height).toInt()
        val y1 = (ICON_BOTTOM * height).toInt()
        val row = IntArray(width)
        val white = IntArray(tabIcons.size)
        for (y in y0 until y1) {
            readRow(y, row)
            for (i in tabIcons.indices) {
                for (x in (tabIcons[i][0] * width).toInt() until (tabIcons[i][1] * width).toInt()) {
                    if (isWhite(
                            row[x]
                        )
                    ) {
                        white[i]++
                    }
                }
            }
        }
        return tabIcons.indices.count { i ->
            val area =
                (y1 - y0) * ((tabIcons[i][1] - tabIcons[i][0]) * width).toInt()
            white[i] >= .15 * area && white[i] <= .85 * area
        } >=
            3
    }

    // The close button is looked for in the right part of the upper screen: at (.913, .054) on most popups,
    // further in on some, but always on the right.
    private const val SEARCH_LEFT = .55
    private const val SEARCH_RIGHT = .97
    private const val SEARCH_BOTTOM = .45

    // Half the width of an X, as fractions of the screen height: 19 px of 1080 on the popups seen so far.
    private val halfSizes = doubleArrayOf(.011, .014, .017, .021, .025, .030, .036)

    // Radius of the circle around a circled X: 27 px of 1080 on the live match popup.
    private val circleSizes = doubleArrayOf(.019, .022, .025, .028, .032, .036)

    /**
     * Centre of a popup's close button, or null. The button is an X: two thin strokes crossing at right angles,
     * brighter than what is around them, with nothing touching it, so the letter X of a word does not count;
     * or, on the live match popup, a thick X inside a circle ([circledX]). Brightness itself does not count, so
     * the button is found while the popup is still fading in. With several, the one nearest the top right corner.
     */
    fun closeButton(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Pair<Double, Double>? {
        val x0 = (SEARCH_LEFT * width).toInt()
        val w = (SEARCH_RIGHT * width).toInt() - x0
        val h = (SEARCH_BOTTOM * height).toInt()
        val luma = IntArray(w * h)
        val row = IntArray(width)
        for (y in 0 until h) {
            readRow(y, row)
            for (x in 0 until w) luma[y * w + x] = luma(row[x0 + x])
        }
        val found = ArrayList<DoubleArray>() // lift, x, y, half size
        for (r0 in halfSizes) {
            val r = r0 * height
            val reach = (1.5 * r).toInt() + 3
            var cy = reach
            while (cy < h - reach) {
                var cx = reach
                while (cx < w - reach) {
                    if (luma[cy * w + cx] >=
                        40
                    ) {
                        xScore(luma, w, cx.toDouble(), cy.toDouble(), r)?.let {
                            found.add(doubleArrayOf(it, cx.toDouble(), cy.toDouble(), r))
                        }
                    }
                    cx += 2
                }
                cy += 2
            }
        }
        for (r0 in circleSizes) {
            val r = r0 * height
            val reach = (1.35 * r).toInt() + 3
            var cy = reach
            while (cy < h - reach) {
                var cx = reach
                while (cx < w - reach) {
                    if (luma[cy * w + cx] >=
                        40
                    ) {
                        circledX(luma, w, cx.toDouble(), cy.toDouble(), r)?.let {
                            found.add(doubleArrayOf(it, cx.toDouble(), cy.toDouble(), r))
                        }
                    }
                    cx += 2
                }
                cy += 2
            }
        }
        if (found.isEmpty()) return null
        // One button gives a cluster of fits; keep the best fit of each cluster.
        found.sortByDescending { it[0] }
        val buttons = ArrayList<DoubleArray>()
        for (f in found) if (buttons.none { hypot(it[1] - f[1], it[2] - f[2]) < maxOf(it[3], f[3]) }) buttons.add(f)
        val best = buttons.minByOrNull { hypot(w - it[1], it[2]) }!!
        return (x0 + best[1]) / width to best[2] / height
    }

    /** How much brighter the strokes of an X centred at ([cx], [cy]) with half size [r] are than around them, or null if no X is there. */
    private fun xScore(luma: IntArray, w: Int, cx: Double, cy: Double, r: Double): Double? {
        fun at(x: Double, y: Double) = luma[y.roundToInt() * w + x.roundToInt()]

        // A stroke may sit a pixel off the ideal diagonal.
        fun peak(x: Double, y: Double): Int {
            val xi = x.roundToInt()
            val yi = y.roundToInt()
            var m = 0
            for (dy in -1..1) for (dx in -1..1) m = maxOf(m, luma[(yi + dy) * w + xi + dx])
            return m
        }
        // Quick test: the arms bright and the gaps between them dark at a few points.
        val q = .6 * r
        val g = .7 * r
        val arms = minOf(minOf(peak(cx + q, cy + q), peak(cx - q, cy - q)), minOf(peak(cx + q, cy - q), peak(cx - q, cy + q)))
        val gaps = maxOf(maxOf(at(cx, cy - g), at(cx, cy + g)), maxOf(at(cx - g, cy), at(cx + g, cy)))
        if (arms - gaps < 30) return null
        val arm1 = IntArray(13) {
            val k = (-.85 + it * 1.7 / 12) * r
            peak(cx + k, cy + k)
        }
        val arm2 = IntArray(13) {
            val k = (-.85 + it * 1.7 / 12) * r
            peak(cx + k, cy - k)
        }
        val gap = IntArray(16) {
            val j =
                (.5 + (it % 4) * .4 / 3) * r
            when (it / 4) {
                0 -> at(cx, cy - j)
                1 -> at(cx, cy + j)
                2 -> at(cx - j, cy)
                else -> at(cx + j, cy)
            }
        }
        // Two square rings around the X, beyond the ends of its arms: 2 rings x 4 sides x 6 points.
        val ring = IntArray(48) {
            val R = (if (it < 24) 1.2 else 1.5) * r
            val t = (-1 + (it % 6) / 2.5) * R
            when (it % 24 / 6) {
                0 -> at(cx + t, cy - R)
                1 -> at(cx + t, cy + R)
                2 -> at(cx - R, cy + t)
                else -> at(cx + R, cy + t)
            }
        }
        val bg = percentile(gap + ring, .75)
        val lift = percentile(arm1 + arm2, .5) - bg
        if (lift < 30) return null
        val half = bg + .5 * lift
        if (arm1.count { it >= half } < .85 * arm1.size || arm2.count { it >= half } < .85 * arm2.size) return null
        val lit = bg + .35 * lift
        if (percentile(gap, .9) > lit) return null
        // The arms end at the size tried: the rings' corners, which lie on the diagonals beyond the arms, are dark.
        if (ring.indices.any { (it % 6 == 0 || it % 6 == 5) && ring[it] > lit }) return null
        if (ring.count {
                it > lit
            } > .25 * ring.size ||
            (0 until 8).any { side -> (0 until 6).count { ring[side * 6 + it] > lit } >= 3 }
        ) {
            return null
        }
        return if (alone(luma, w, cx.roundToInt(), cy.roundToInt(), r, lit)) lift else null
    }

    /**
     * How much brighter a circled X centred at ([cx], [cy]) is than around it, or null if there is none: a bright
     * circle of radius [r], the X's arms bright on the diagonals inside it, dark between the arms on the straight
     * directions, and mostly dark just outside the circle.
     */
    private fun circledX(luma: IntArray, w: Int, cx: Double, cy: Double, r: Double): Double? {
        fun at(x: Double, y: Double) = luma[y.roundToInt() * w + x.roundToInt()]

        fun peak(x: Double, y: Double): Int {
            val xi = x.roundToInt()
            val yi = y.roundToInt()
            var m = 0
            for (dy in -1..1) for (dx in -1..1) m = maxOf(m, luma[(yi + dy) * w + xi + dx])
            return m
        }
        val d = r / Math.sqrt(2.0)
        // Quick test at a few points: the circle at its four diagonal-free points, the arms, the gaps between them.
        val quick =
            minOf(
                minOf(peak(cx + r, cy), peak(cx - r, cy)),
                minOf(peak(cx, cy + r), peak(cx, cy - r)),
                minOf(
                    peak(cx + .4 * d, cy + .4 * d),
                    peak(
                        cx - .4 * d,
                        cy - .4 * d
                    )
                )
            )
        if (quick - maxOf(at(cx + .6 * r, cy), at(cx - .6 * r, cy), at(cx, cy + .6 * r), at(cx, cy - .6 * r)) < 30) return null
        val circle = IntArray(36) {
            val a = 2 * Math.PI * it / 36
            peak(cx + r * cos(a), cy + r * sin(a))
        }
        val arms = IntArray(12) {
            val k =
                (.2 + (it / 4) * .2) * d
            val sx = if (it % 4 == 0 || it % 4 == 3) 1 else -1
            val sy = if (it % 2 == 0) 1 else -1
            peak(cx + sx * k, cy + sy * k)
        }
        val gaps = IntArray(16) {
            val j =
                (.5 + (it % 4) * .08) * r
            when (it / 4) {
                0 -> at(cx, cy - j)
                1 -> at(cx, cy + j)
                2 -> at(cx - j, cy)
                else -> at(cx + j, cy)
            }
        }
        val outside = IntArray(36) {
            val a = 2 * Math.PI * it / 36
            at(cx + 1.3 * r * cos(a), cy + 1.3 * r * sin(a))
        }
        val bg = percentile(gaps + outside, .75)
        val lift = percentile(circle + arms, .5) - bg
        if (lift < 30) return null
        val half = bg + .5 * lift
        val lit = bg + .35 * lift
        if (circle.count { it >= half } < .85 * circle.size || arms.any { it < half }) return null
        if (percentile(gaps, .9) > lit || outside.count { it > lit } > .25 * outside.size) return null
        return lift
    }

    /**
     * Whether the bright shape through ([cx], [cy]) is about 2r square and stands alone. The X is followed
     * pixel by pixel from its centre, so a fit a few pixels off its true centre or size still measures the X
     * itself; bright pixels within half its size around it, beyond a few of a nearby icon, mean it is a
     * letter in a word or part of the art.
     */
    private fun alone(luma: IntArray, w: Int, cx: Int, cy: Int, r: Double, lit: Double): Boolean {
        val h = luma.size / w
        val reach = (2.2 * r).toInt()
        val left = maxOf(0, cx - reach)
        val right = minOf(w - 1, cx + reach)
        val top = maxOf(0, cy - reach)
        val bottom = minOf(h - 1, cy + reach)
        val ww = right - left + 1
        val inX = BooleanArray(ww * (bottom - top + 1))
        var start = -1
        var brightest = lit
        for (y in cy - 2..cy + 2) {
            for (x in cx - 2..cx + 2) {
                if (luma[y * w + x] > brightest) {
                    brightest = luma[y * w + x].toDouble()
                    start = y * w + x
                }
            }
        }
        if (start < 0) return false
        val queue = ArrayDeque<Int>()
        queue.add(start)
        inX[(start / w - top) * ww + start % w - left] = true
        var count = 0
        var x0 = cx
        var x1 = cx
        var y0 = cy
        var y1 = cy
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val px = p % w
            val py = p / w
            count++
            x0 = minOf(x0, px)
            x1 = maxOf(x1, px)
            y0 = minOf(y0, py)
            y1 = maxOf(y1, py)
            // Running into the edge of the window: the shape is larger than an X of this size.
            if (px == left || px == right || py == top || py == bottom) return false
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = px + dx
                    val ny = py + dy
                    val i = (ny - top) * ww + nx - left
                    if (!inX[i] && luma[ny * w + nx] > lit) {
                        inX[i] = true
                        queue.add(ny * w + nx)
                    }
                }
            }
        }
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        if (bw < 1.4 * r || bw > 2.6 * r || bh < 1.4 * r || bh > 2.6 * r) return false
        val margin = (.45 * maxOf(bw, bh)).toInt()
        var near = 0
        for (y in maxOf(top, y0 - margin)..minOf(bottom, y1 + margin)) {
            for (x in maxOf(left, x0 - margin)..minOf(right, x1 + margin)) {
                if (!inX[(y - top) * ww + x - left] && luma[y * w + x] > lit) near++
            }
        }
        return near <= .2 * count
    }

    private fun percentile(values: IntArray, p: Double): Double {
        val s = values.sorted()
        return s[((s.size - 1) * p).roundToInt()].toDouble()
    }

    /** A 24x12 thumbnail of mean brightness, to tell a screen that changes from one that stands still. */
    fun signature(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): IntArray {
        val sum = LongArray(24 * 12)
        val count = IntArray(24 * 12)
        val row = IntArray(width)
        var y = 0
        while (y < height) {
            readRow(y, row)
            var x = 0
            while (x < width) {
                val c = minOf(11, y * 12 / height) * 24 + minOf(23, x * 24 / width)
                sum[c] += luma(row[x]).toLong()
                count[c]++
                x += 8
            }
            y += 8
        }
        return IntArray(sum.size) { if (count[it] == 0) 0 else (sum[it] / count[it]).toInt() }
    }

    /** Mean difference between two [signature]s, 0–255. */
    fun change(a: IntArray, b: IntArray) = if (a.size != b.size) 255.0 else a.indices.sumOf { abs(a[it] - b[it]) }.toDouble() / a.size

    private fun luma(p: Int) = (299 * ((p shr 16) and 255) + 587 * ((p shr 8) and 255) + 114 * (p and 255)) / 1000

    private fun isWhite(p: Int): Boolean {
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val lo = minOf(r, g, b)
        return lo > 190 && maxOf(r, g, b) - lo < 50
    }
}

/**
 * Opens Liên Quân and goes from wherever it is to the All-heroes list: waits through the logos, loading and
 * trailer, logs in with the last Garena account, skips the registration form and the reward screens, closes
 * event popups by their X, and taps the lobby's "Tướng" tab, or stops at the lobby [toLobby]. With an [expectedUid], it first checks the UID of
 * the account in play; another account is logged out and the saved test account logged in through the
 * Garena page, once. Each move is decided on a screenshot and recorded with it ([Shot.record]), so the way
 * in can be reviewed afterwards. It never sends Android's back key: in the game that does not close popups,
 * and twice in the lobby quits. Whatever it cannot get past pauses the run with that screen kept. All screen
 * access goes through [Screen], so unit tests run this exact logic on a scripted series of screens.
 */
class GameEntry(
    private val screen: Screen,
    private val expectedUid: String? = null,
    private val toLobby: Boolean = false,
    private val smartLobby: Boolean = true,
    private val preserveScreen: Boolean = false,
    private val readOnlyAccount: Boolean = false,
    /**
     * Only the dedicated event-invite runner sets this. A UID-less run must prove it
     * actively used the supplied Garena credentials instead of trusting a previous login.
     */
    private val forceFreshLogin: Boolean = false,
    /** With [preserveScreen]: the paused step finds its own way from the lobby, where the UID can be checked. */
    private val resumeFromLobby: Boolean = false,
    private val finish: (Outcome) -> Unit
) {
    sealed class Outcome {
        object Ready : Outcome()

        class Failed(val reason: String) : Outcome()
    }

    /**
     * One screenshot. [record] keeps it, scaled down, with what was decided on it and where it was tapped,
     * or only the decision when [image] is false; then [done] exactly once, with a name to keep it in full in
     * the run or null to drop it.
     */
    interface Shot {
        val reading: GameScreens.Reading

        fun record(what: String, tap: Pair<Double, Double>? = null, image: Boolean = true) {}

        fun done(keep: String?)
    }

    interface Screen {
        val alive: Boolean

        /** Fresh foreground evidence, never a cached package name. */
        val gameInFront: Boolean get() = false

        /** Ends the game if it is still running, through Settings ([GameCloser]); [then] gets what happened. */
        fun quit(then: (GameCloser.Result) -> Unit)

        /** Starts the game or brings it to the front; false when it is not installed or Android refused. */
        fun launch(): Boolean

        /**
         * null when no screenshot could be taken, as while the game is still coming up. [words] asks for the text
         * on screen even when its pixels do not call for it, [uid] for the UID printed at its bottom.
         */
        fun look(words: Boolean, uid: Boolean, then: (Shot?) -> Unit)

        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)

        fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) = tap(action.points[0].x, action.points[0].y, then)

        /** Taps the game's own back arrow at the top left. */
        fun back(then: (Boolean) -> Unit)

        /** Fills the validated Garena form and delivers the tap result asynchronously. */
        fun signIn(then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        fun now(): Long

        /** Records a step in the run's events log. */
        fun note(text: String)

        /** Shows what is happening on the panel. */
        fun say(text: String)
    }

    companion object {
        /** Logos, loading and popups, and switching accounts; the game loads within 1–2 minutes on the user's phone. */
        const val LIMIT_MS = 480_000L

        /**
         * The game not in front this long after opening it: some phones block apps from opening others in the
         * background without saying so.
         */
        const val NO_GAME_MS = 45_000L

        /** A screen that stays the same this long without being recognised is not going to change by itself. */
        const val STILL_MS = 90_000L

        /** Popups appear a while after the lobby: the lobby must stay clear this long before "Tướng" is tapped. */
        const val LOBBY_SETTLE_MS = 4000L
        const val MAX_CLOSES = 12
        const val MAX_SAME_CLOSE = 3
        const val MAX_TABS = 3
        const val MAX_BACKS = 3
        const val MAX_CONTINUES = 10
        const val MAX_LOGINS = 2

        /** Looks at the lobby for its UID before opening the profile to read it there. */
        const val UID_LOOKS = 3

        /** Folder of a run that keeps the screenshots and decisions of the way into the game. */
        const val TRACE_DIR = "buoc-vao-game"
    }

    /** Checking the account: [Phase.LOOKUP] reads the UID in the profile, [Phase.LOGOUT] leaves the wrong account. */
    private enum class Phase { ENTER, LOOKUP, LOGOUT }

    private var phase = Phase.ENTER
    private var started = 0L
    private var closes = 0
    private var sameClose = 0
    private var tapped: Pair<Double, Double>? = null
    private var tappedLook: IntArray? = null
    private var seen: Pair<Double, Double>? = null
    private var tabs = 0
    private var backs = 0
    private var continues = 0
    private var registers = 0
    private var chessVeteranTaps = 0
    private var chessTipTaps = 0
    private var lobbySince = -1L
    private var missingSince = -1L
    private var still: IntArray? = null
    private var stillSince = 0L
    private var uidOk = expectedUid == null
    private var uidMisses = 0
    private var wrongUid: String? = null
    private var lookups = 0
    private var logouts = 0
    private var confirms = 0
    private var searches = 0
    private var logins = 0
    private var signedInAt = -1L
    private var switched = false
    private var leftOnce = false

    private var firstLook = true
    private var fastLobby = false
    private var finished = false

    /** Inspect an already foreground game; otherwise bring it forward without force stopping it. */
    fun start() {
        if (!screen.alive) return
        started = screen.now()
        if (screen.gameInFront) {
            screen.say("Đang nhận diện màn hình và kiểm tra UID…")
            step()
        } else if (preserveScreen) {
            fail("Hãy mở đúng màn hình đang thu thập trong Liên Quân rồi bấm Tiếp tục; không tự đổi màn hình của bước đang dở.")
        } else {
            screen.note("Mở / đưa Liên Quân ra phía trước, không buộc dừng")
            screen.say("Đang mở Liên Quân…")
            if (!screen.launch()) return fail("Không mở được Liên Quân. Kiểm tra game đã cài, mở game bằng tay rồi bấm Tiếp tục.")
            // A newly launched/resumed activity still gets the normal popup settling path.
            firstLook = false
            screen.later(4000) { step() }
        }
    }

    private fun ready() {
        if (finished || !screen.alive) return
        finished = true
        finish(Outcome.Ready)
    }

    private fun fail(reason: String) {
        if (finished || !screen.alive) return
        finished = true
        finish(Outcome.Failed(reason))
    }

    private fun again(ms: Long) {
        screen.later(ms) { step() }
    }

    private fun tap(s: Shot, what: String, at: Pair<Double, Double>, wait: Long, keep: String? = null) {
        s.record(what, at)
        s.done(keep)
        screen.note(what)
        screen.say("$what…")
        screen.tap(at.first, at.second) { ok -> if (ok) again(wait) else fail("Không chạm được: $what") }
    }

    private fun stop(s: Shot, keep: String, reason: String) {
        s.record("Tạm dừng: $reason")
        s.done(keep)
        fail(reason)
    }

    private fun step() {
        if (finished || !screen.alive) return
        val now = screen.now()
        screen.look(phase != Phase.ENTER, !uidOk) { s ->
            if (finished || !screen.alive) {
                s?.done(null)
                return@look
            }
            if (s == null) {
                if (missingSince < 0) missingSince = now
                if (now - missingSince >
                    NO_GAME_MS
                ) {
                    return@look fail(
                        "Liên Quân chưa hiện lên sau ${NO_GAME_MS / 1000} giây. Nếu máy chặn mở app từ nền, mở game bằng tay rồi bấm Tiếp tục."
                    )
                }
                screen.say("Đang chờ Liên Quân hiện lên…")
                return@look again(1500)
            }
            missingSince = -1
            if (now - started >
                LIMIT_MS
            ) {
                return@look stop(
                    s,
                    "entry-unknown",
                    "Sau ${LIMIT_MS / 60000} phút vẫn chưa vào được danh sách tướng. Xử lý bằng tay rồi bấm Tiếp tục; ảnh màn hình đã được lưu trong lượt."
                )
            }
            val reading = s.reading
            if (firstLook) {
                fastLobby = smartLobby && screen.gameInFront && reading.kind == GameScreens.Kind.LOBBY
                firstLook = false
            }
            if (preserveScreen) {
                // Never move a coordinate macro or a paused cursor to another screen to check identity.
                val known = reading.kind in setOf(GameScreens.Kind.LOBBY, GameScreens.Kind.LIST, GameScreens.Kind.HERO_PAGE)
                if (!screen.gameInFront || !known || (expectedUid != null && reading.uid != expectedUid)) {
                    return@look stop(
                        s,
                        "entry-resume",
                        if (resumeFromLobby && screen.gameInFront && !known) {
                            // E.g. Bách khoa Cờ: no UID on screen. Its step restarts from the lobby and skips saved work.
                            "Màn hình hiện tại không hiện UID nên chưa kiểm tra được tài khoản; chưa thao tác gì. " +
                                "Bấm mũi tên quay lại của game cho đến sảnh chính (có UID ở cuối màn hình) rồi bấm Tiếp tục: " +
                                "bước này tự đi lại từ sảnh và bỏ qua phần đã lưu, không bắt đầu lại từ đầu."
                        } else {
                            "Chưa xác minh được đúng UID trên màn hình hiện tại. Giữ nguyên bước; mở đúng màn hình và UID rồi thử lại. " +
                                "Nếu UID bị ẩn, tạo lượt mới từ sảnh với tác vụ tự điều hướng; không tự quay về sảnh rồi chạy tọa độ đang dở."
                        }
                    )
                }
                s.record("Đã kiểm tra màn hình hiện tại và UID; giữ nguyên vị trí")
                s.done(null)
                return@look ready()
            }
            if (reading.kind != GameScreens.Kind.LOBBY) fastLobby = false
            if (reading.kind != GameScreens.Kind.LOBBY) lobbySince = -1
            if (reading.kind != GameScreens.Kind.POPUP) {
                seen = null
                tapped = null
                sameClose = 0
            }
            if (reading.kind != GameScreens.Kind.OTHER) still = null
            if (phase == Phase.LOGOUT && reading.kind != GameScreens.Kind.LOGIN) return@look logout(s, reading)
            when (reading.kind) {
                GameScreens.Kind.LIST -> list(s, reading)
                GameScreens.Kind.LOBBY -> lobby(s, reading, now)
                GameScreens.Kind.POPUP -> popup(s, reading.close!!)
                GameScreens.Kind.LOGIN -> {
                    if (++logins >
                        MAX_LOGINS
                    ) {
                        return@look stop(
                            s,
                            "entry-login",
                            "Đã bấm Đăng nhập Garena $MAX_LOGINS lần mà vẫn ở màn đăng nhập. Đăng nhập bằng tay rồi bấm Tiếp tục."
                        )
                    }
                    phase = Phase.ENTER
                    tap(s, "Màn đăng nhập: bấm Đăng nhập Garena", reading.tap!!, 5000)
                }
                GameScreens.Kind.FORM -> form(s, now)
                GameScreens.Kind.REGISTER -> {
                    if (++registers >
                        3
                    ) {
                        return@look stop(
                            s,
                            "entry-register",
                            "Đã bấm Đ.KÝ SAU 3 lần mà form đăng ký vẫn còn. Xử lý bằng tay rồi bấm Tiếp tục."
                        )
                    }
                    tap(s, "Form đăng ký thông tin: bấm Đ.KÝ SAU", reading.tap!!, 2500)
                }
                GameScreens.Kind.CHESS_EXPERIENCE -> {
                    if (++chessVeteranTaps > 2) {
                        return@look stop(
                            s,
                            "entry-chess-experience",
                            "Màn hỏi kinh nghiệm Cờ Liên Quân vẫn còn sau 2 lần bấm Kỳ cựu. Chọn bằng tay rồi bấm Tiếp tục."
                        )
                    }
                    tap(s, "Cờ Liên Quân lần đầu: chọn Kỳ cựu", reading.tap!!, 2000)
                }
                GameScreens.Kind.CHESS_TIP -> {
                    if (++chessTipTaps > 2) {
                        return@look stop(
                            s,
                            "entry-chess-tip",
                            "Thông báo Đấu luyện vẫn còn sau 2 lần bấm Đã rõ. Xử lý bằng tay rồi bấm Tiếp tục."
                        )
                    }
                    tap(s, "Cờ Liên Quân lần đầu: bấm Đã rõ", reading.tap!!, 2000)
                }
                GameScreens.Kind.CONTINUE -> {
                    if (++continues >
                        MAX_CONTINUES
                    ) {
                        return@look stop(
                            s,
                            "entry-continue",
                            "Đã bấm Ấn để tiếp tục $MAX_CONTINUES lần mà vẫn chưa qua. Xử lý bằng tay rồi bấm Tiếp tục."
                        )
                    }
                    tap(s, "Màn quà: bấm Ấn để tiếp tục", reading.tap!!, 2000)
                }
                GameScreens.Kind.PROFILE -> profile(s, reading)
                GameScreens.Kind.UID_DIALOG -> uidDialog(s, reading)
                GameScreens.Kind.HERO_PAGE -> back(s, "Đang ở một trang khác của game: bấm nút quay lại")
                GameScreens.Kind.OTHER -> other(s, now)
            }
        }
    }

    private fun back(s: Shot, what: String) {
        if (++backs >
            MAX_BACKS
        ) {
            return stop(
                s,
                "entry-page",
                "Đã bấm nút quay lại của game $MAX_BACKS lần mà chưa về được sảnh hay danh sách tướng. Mở danh sách Tất cả tướng rồi bấm Tiếp tục."
            )
        }
        s.record(what, HeroGrid.BACK_X to HeroGrid.BACK_Y)
        s.done(null)
        screen.note("$what (lần $backs)")
        screen.say("Đang quay lại sảnh…")
        screen.back { ok -> if (ok) again(1500) else fail("Không chạm được nút quay lại của game") }
    }

    private fun list(s: Shot, reading: GameScreens.Reading) {
        if (toLobby) return back(s, "Danh sách tướng: quay lại sảnh")
        if (!uidOk) {
            // The list shows the UID too: a match saves going back; anything else is checked from the lobby.
            if (reading.uid == expectedUid) {
                uidOk = true
                screen.note("UID $expectedUid khớp tài khoản test")
            } else {
                return back(s, "Danh sách tướng: chưa xác nhận được UID, quay lại sảnh để kiểm tra")
            }
        }
        s.record("Đã vào danh sách tướng")
        s.done(null)
        screen.note("Đã vào danh sách tướng")
        ready()
    }

    private fun lobby(s: Shot, reading: GameScreens.Reading, now: Long) {
        if (!fastLobby &&
            lobbySince <
            0
        ) {
            lobbySince = now
            s.record("Sảnh: chờ xem còn popup nào không")
            s.done(null)
            screen.say("Đã vào sảnh; chờ xem còn popup nào không…")
            return again(LOBBY_SETTLE_MS)
        }
        if (!fastLobby && now - lobbySince < LOBBY_SETTLE_MS) {
            s.done(null)
            return again(LOBBY_SETTLE_MS - (now - lobbySince))
        }
        if (readOnlyAccount && !uidOk && reading.uid != expectedUid) {
            return stop(
                s,
                "entry-teaching-uid",
                "Chế độ chỉnh chỉ kiểm tra UID, không thao tác tài khoản. Hãy vào sảnh đúng tài khoản đã chọn với UID đọc được rồi mở Chỉnh từng bước lại."
            )
        }
        val wrong = wrongUid
        if (wrong != null) return wrongAccount(s, wrong)
        if (!uidOk) {
            val uid = reading.uid
            if (uid == null) {
                if (++uidMisses < UID_LOOKS) {
                    s.record("Sảnh: chưa đọc được UID ở cuối màn hình, xem lại")
                    s.done(null)
                    return again(1200)
                }
                if (++lookups >
                    2
                ) {
                    return stop(
                        s,
                        "entry-uid",
                        "Không đọc được UID của tài khoản, cả ở sảnh lẫn trong hồ sơ. Kiểm tra bằng tay rồi bấm Tiếp tục."
                    )
                }
                phase = Phase.LOOKUP
                lobbySince = -1
                return tap(
                    s,
                    "Sảnh: không đọc được UID ở cuối màn hình, mở hồ sơ để xem",
                    GameScreens.AVATAR_X to GameScreens.AVATAR_Y,
                    2500
                )
            }
            if (uid != expectedUid) return wrongAccount(s, uid)
            uidOk = true
            screen.note("UID $uid khớp tài khoản test")
        }
        if (forceFreshLogin && !switched) {
            // We have no account UID by design. Prove account selection through a fresh
            // credential submission instead of silently reusing whichever account is logged in.
            if (leftOnce) {
                return stop(
                    s,
                    "entry-event-login",
                    "Đã đăng xuất nhưng game tự vào lại tài khoản cũ; cần mở màn đăng nhập Garena trước khi tiếp tục."
                )
            }
            leftOnce = true
            phase = Phase.LOGOUT
            lobbySince = -1
            return tap(
                s,
                "Sự kiện: mở Cài đặt để đăng xuất tài khoản đang dùng",
                GameScreens.SETTINGS_X to GameScreens.SETTINGS_Y,
                2500
            )
        }
        // The hero statistics start from the lobby.
        if (toLobby) {
            s.record("Đã vào sảnh")
            s.done(null)
            screen.note("Đã vào sảnh")
            return ready()
        }
        if (++tabs >
            MAX_TABS
        ) {
            return stop(
                s,
                "entry-lobby",
                "Đã bấm Tướng ở sảnh $MAX_TABS lần mà chưa vào được danh sách tướng. Mở danh sách Tất cả tướng rồi bấm Tiếp tục."
            )
        }
        lobbySince = -1
        s.record("Sảnh: bấm Tướng", GameScreens.HEROES_X to GameScreens.HEROES_Y)
        s.done(null)
        screen.note("Sảnh: bấm Tướng")
        screen.say("Sảnh: bấm Tướng…")
        screen.calibrated(
            CalibrationAction(
                "entry.v1.lobby.heroes",
                "Sảnh: bấm Tướng",
                listOf(CalibrationPoint(GameScreens.HEROES_X, GameScreens.HEROES_Y))
            )
        ) { ok ->
            if (ok) again(2500) else fail("Không chạm được: Sảnh: bấm Tướng")
        }
    }

    /** The account in play is not the test account: log it out from the lobby's settings, once per entry. */
    private fun wrongAccount(s: Shot, uid: String) {
        wrongUid = null
        if (switched) {
            return stop(
                s,
                "entry-uid",
                "Đã đăng nhập tài khoản test nhưng UID trong game là $uid, khác UID đã lưu $expectedUid. Kiểm tra tài khoản đã lưu rồi bấm Tiếp tục."
            )
        }
        // Garena may log the last account back in without showing its page: one logout per entry.
        if (leftOnce) {
            return stop(
                s,
                "entry-uid",
                "Đã đăng xuất một lần nhưng game lại vào tài khoản UID $uid, không phải tài khoản test $expectedUid. Đăng nhập tài khoản test bằng tay rồi bấm Tiếp tục."
            )
        }
        leftOnce = true
        screen.note("UID trong game là $uid, khác UID tài khoản test $expectedUid: đăng xuất")
        phase = Phase.LOGOUT
        lobbySince = -1
        tap(s, "UID $uid khác tài khoản test: mở cài đặt để đăng xuất", GameScreens.SETTINGS_X to GameScreens.SETTINGS_Y, 2500)
    }

    /** Settings, then "Đăng xuất", then its confirmation, until the login screen shows. */
    private fun logout(s: Shot, reading: GameScreens.Reading) {
        val button = reading.find("dangxuat") { it.plain.length <= 12 }
        val confirm = reading.words.firstOrNull { it.plain == "xacnhan" || it.plain == "dongy" || it.plain == "ok" || it.plain == "co" }
        when {
            button != null && logouts < 2 -> {
                logouts++
                tap(s, "Cài đặt: bấm Đăng xuất", button.x to button.y, 2500)
            }
            confirm != null && logouts > 0 && confirms < 2 -> {
                confirms++
                tap(s, "Xác nhận đăng xuất: bấm ${confirm.text}", confirm.x to confirm.y, 4000)
            }
            // Logged out: the game goes back through its loading to the login screen.
            confirms > 0 || logouts >= 2 -> {
                s.record("Đã đăng xuất: chờ màn đăng nhập")
                s.done(null)
                screen.say("Đang chờ màn đăng nhập…")
                again(1500)
            }
            ++searches <= 6 -> {
                // Settings may open on another tab: the account one holds the logout button.
                val tab = if (searches % 3 == 0) reading.find("taikhoan") ?: reading.find("khac") else null
                if (tab != null) {
                    tap(s, "Cài đặt: mở thẻ ${tab.text}", tab.x to tab.y, 2000)
                } else {
                    s.record("Đang tìm nút Đăng xuất")
                    s.done(null)
                    again(1500)
                }
            }
            else -> stop(
                s,
                "entry-logout",
                "Không tìm thấy nút Đăng xuất hoặc nút xác nhận. Đăng xuất bằng tay rồi bấm Tiếp tục; ảnh các bước đã được lưu trong lượt."
            )
        }
    }

    private fun form(s: Shot, now: Long) {
        if (expectedUid == null && !forceFreshLogin) {
            return stop(
                s,
                "entry-form",
                "Game mở trang đăng nhập Garena. Lưu tài khoản test trong app và bật Kiểm tra UID, hoặc đăng nhập bằng tay rồi bấm Tiếp tục."
            )
        }
        if (signedInAt >= 0) {
            if (now - signedInAt < 15_000) {
                s.record("Trang Garena: chờ đăng nhập xong", image = false)
                s.done(null)
                return again(2000)
            }
            s.record("Trang Garena vẫn còn sau khi gửi", image = false)
            s.done(null)
            return fail(
                "Đăng nhập Garena không thành công: trang đăng nhập vẫn còn. Kiểm tra tài khoản đã lưu, đăng nhập bằng tay rồi bấm Tiếp tục."
            )
        }
        if (logins == 0) {
            s.record("Trang Garena xuất hiện khi app chưa bấm đăng nhập", image = false)
            s.done(null)
            return fail("Trang đăng nhập Garena hiện ra khi app chưa bấm đăng nhập. Xử lý bằng tay rồi bấm Tiếp tục.")
        }
        // The page shows the account typed in: kept as a decision only, without its screenshot.
        s.record("Trang Garena: điền tài khoản test và bấm Đăng Nhập Ngay", image = false)
        s.done(null)
        screen.note("Điền tài khoản test vào trang đăng nhập Garena")
        screen.say("Đang đăng nhập tài khoản test…")
        screen.signIn { submitted ->
            if (!screen.alive) return@signIn
            if (!submitted) {
                return@signIn fail(
                    "Không nhấn được Đăng Nhập Ngay trên trang Garena. Hãy kiểm tra màn hình và đăng nhập bằng tay, rồi bấm Tiếp tục."
                )
            }
            signedInAt = screen.now()
            switched = true
            again(5000)
        }
    }

    private fun profile(s: Shot, reading: GameScreens.Reading) {
        if (phase != Phase.LOOKUP) return back(s, "Hồ sơ người chơi: quay lại sảnh")
        val uid = reading.words.firstOrNull { it.plain == "uid" && it.x < .5 }
            ?: return stop(s, "entry-uid", "Đã mở hồ sơ nhưng không thấy nút UID. Kiểm tra bằng tay rồi bấm Tiếp tục.")
        tap(s, "Hồ sơ: bấm UID", uid.x to uid.y, 2000)
    }

    private fun uidDialog(s: Shot, reading: GameScreens.Reading) {
        val close = reading.close ?: return stop(s, "entry-uid", "Không thấy nút đóng của hộp Xem UID. Đóng bằng tay rồi bấm Tiếp tục.")
        val uid = reading.words.firstNotNullOfOrNull { GameScreens.uidIn(it.text) }
        if (phase == Phase.LOOKUP && uid != null) {
            phase = Phase.ENTER
            if (uid == expectedUid) {
                uidOk = true
                screen.note("UID $uid khớp tài khoản test")
            } else {
                wrongUid = uid
            }
        }
        tap(s, "Hộp Xem UID${if (uid != null) " ($uid)" else ""}: bấm X", close, 1500)
    }

    private fun near(a: Pair<Double, Double>?, b: Pair<Double, Double>) =
        a != null && abs(a.first - b.first) < .012 && abs(a.second - b.second) < .025

    private fun popup(s: Shot, close: Pair<Double, Double>) {
        // A popup fades in: tap its X only once it is found at the same place twice in a row.
        if (!near(seen, close)) {
            seen = close
            s.done(null)
            return again(700)
        }
        seen = null
        // Popups one after another often put their X at the same place; the same popup still there looks the same.
        val look = s.reading.signature
        val before = tappedLook
        sameClose = if (near(tapped, close) && before != null && GameScreens.change(before, look) < 3.0) sameClose + 1 else 1
        if (sameClose >
            MAX_SAME_CLOSE
        ) {
            return stop(
                s,
                "entry-popup",
                "Đã bấm nút X của một popup $MAX_SAME_CLOSE lần mà popup không đóng. Đóng popup bằng tay rồi bấm Tiếp tục."
            )
        }
        if (++closes > MAX_CLOSES) {
            return stop(
                s,
                "entry-popup",
                "Đã đóng $MAX_CLOSES popup mà vẫn còn popup. Đóng bằng tay rồi bấm Tiếp tục."
            )
        }
        tapped = close
        tappedLook = look
        tap(s, "Đóng popup $closes: bấm X ở %.0f%%, %.0f%% màn hình".format(close.first * 100, close.second * 100), close, 1500)
    }

    private fun other(s: Shot, now: Long) {
        val signature = s.reading.signature
        val last = still
        if (last == null ||
            GameScreens.change(last, signature) >= 3.0
        ) {
            still = signature
            stillSince = now
            s.record("Đang chờ game (logo, màn tải, video)")
        }
        if (now - stillSince >
            STILL_MS
        ) {
            return stop(
                s,
                "entry-unknown",
                "Màn hình đứng yên ${STILL_MS / 1000} giây mà app không nhận ra. Xử lý bằng tay rồi bấm Tiếp tục; ảnh màn hình đã được lưu trong lượt."
            )
        }
        s.done(null)
        screen.say("Đang chờ game tải…")
        again(1500)
    }
}
