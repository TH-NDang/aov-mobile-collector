package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * Image-only recognition for the 1536x691 reference Liên Quân VN "Chuẩn bị" UI.
 * All coordinates are proportional: resolution may change, but aspect ratio/layout must match.
 * This deliberately refuses unknown screens rather than tapping through an arbitrary popup.
 */
object ProBuildVision {
    interface Frame {
        val width: Int
        val height: Int
        /** ARGB pixel. Coordinates are clamped by the implementation. */
        fun pixel(x: Int, y: Int): Int
    }
    enum class Page { LOBBY, PREPARATION, HERO_PICKER, PRO_PANEL, UNKNOWN }
    private fun p(f: Frame, x: Double, y: Double) =
        f.pixel((x * (f.width - 1)).toInt().coerceIn(0, f.width - 1),
            (y * (f.height - 1)).toInt().coerceIn(0, f.height - 1))
    private fun red(c: Int) = (c shr 16) and 255
    private fun green(c: Int) = (c shr 8) and 255
    private fun blue(c: Int) = c and 255

    fun page(f: Frame): Page {
        if (f.width <= f.height || f.width < 800 || f.height < 400) return Page.UNKNOWN
        // Sample an unanimated strip above both modal bodies, away from avatar and tooltips.
        val c = p(f, .55, .12)
        val r = red(c); val g = green(c); val b = blue(c)
        if (r > 110 && g > 75 && b > 125) return Page.LOBBY
        if (r in 28..100 && g in 25..115 && b > 96 && b > r * 1.35) {
            return if (pickerBorder(f)) Page.HERO_PICKER else Page.PRO_PANEL
        }
        if (r < 75 && g < 90 && b < 100) return Page.PREPARATION
        return Page.UNKNOWN
    }

    /**
     * The "Đã nhận" box is outlined bright blue in the hero chooser (with OR without a tick).
     * Its screen location is different from both the setup and pro-proposal screens.
     */
    private fun pickerBorder(f: Frame): Boolean {
        var bluePixels = 0; var sampled = 0
        val x0 = (.804 * f.width).toInt(); val x1 = (.825 * f.width).toInt()
        val y0 = (.210 * f.height).toInt(); val y1 = (.251 * f.height).toInt()
        for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
            val c = f.pixel(x, y); sampled++
            if (blue(c) > 140 && blue(c) > red(c) * 1.20 && blue(c) > green(c) * 1.30) bluePixels++
        }
        return bluePixels * 100 >= sampled * 16
    }

    /** null means NOT on a proven hero-picker; never blindly toggle an unknown checkbox. */
    fun ownedOnlyChecked(f: Frame): Boolean? {
        if (page(f) != Page.HERO_PICKER) return null
        var bright = 0; var n = 0
        for (y in (.218 * f.height).toInt() until (.246 * f.height).toInt() step 2) {
            for (x in (.808 * f.width).toInt() until (.822 * f.width).toInt() step 2) {
                val c = f.pixel(x, y); n++
                if (minOf(red(c), green(c), blue(c)) > 160) bright++
            }
        }
        return bright * 100 >= n * 8
    }

    /** A recommendation row is located by its long yellow "Dùng combo" button, not by fixed row Y. */
    data class Proposal(val buttonY: Double, val avatar: Long, val account: Long) {
        fun same(other: Proposal): Boolean =
            java.lang.Long.bitCount(avatar xor other.avatar) <= 8 &&
                java.lang.Long.bitCount(account xor other.account) <= 12
    }

    private fun yellow(c: Int): Boolean {
        val r = red(c); val g = green(c); val b = blue(c)
        return r > 145 && g > 105 && b < 175 && r > b * 1.35 && g > b * 1.25
    }

    fun proposals(f: Frame): List<Proposal> {
        if (page(f) != Page.PRO_PANEL) return emptyList()
        val output = ArrayList<Proposal>()
        val from = (.23 * f.height).toInt()
        val to = (.885 * f.height).toInt()
        var first = -1
        fun append(last: Int) {
            if (first < 0 || last - first < .027 * f.height) return
            val y = ((first + last) / 2.0) / f.height
            output.add(Proposal(y,
                differenceHash(f, .294, .336, y - .205, y - .124),
                differenceHash(f, .336, .556, y - .205, y - .124)))
        }
        for (y in from..to) {
            var hits = 0
            for (x in doubleArrayOf(.710, .725, .795, .805)) if (yellow(p(f, x, y.toDouble() / f.height))) hits++
            val button = hits >= 2
            if (button && first < 0) first = y
            if (!button && first >= 0) { append(y - 1); first = -1 }
        }
        if (first >= 0) append(to)
        return output
    }

    /** Horizontal comparison hash tolerates brightness changes between screenshots and small UI animation. */
    private fun differenceHash(f: Frame, x0: Double, x1: Double, y0: Double, y1: Double): Long {
        fun light(x: Int, y: Int): Int {
            val c = p(f, x0 + (x1 - x0) * x / 8.0, y0 + (y1 - y0) * y / 7.0)
            return (299 * red(c) + 587 * green(c) + 114 * blue(c)) / 1000
        }
        var result = 0L
        for (y in 0..7) for (x in 0..7) {
            result = (result shl 1) or if (light(x, y) > light(x + 1, y)) 1L else 0L
        }
        return result
    }

    fun sameViewport(a: List<Proposal>, b: List<Proposal>): Boolean =
        a.size == b.size && a.zip(b).all { (x, y) -> x.same(y) && abs(x.buttonY - y.buttonY) < .025 }
}

/**
 * The 8-column scrollable hero chooser inside Chuẩn bị / Trang bị, NOT the older 5-column hero library.
 * The actual drag displacement is measured from images, avoiding repeated/shifted hero selection.
 */
object ProHeroGrid {
    val columns = doubleArrayOf(.222, .301, .381, .461, .542, .621, .701, .780)
    private const val ROW0 = .363
    private const val PITCH = .176
    private const val PARK = .68
    private const val TOP = .31
    private const val BOTTOM = .83
    private const val MAX_STEP = .30
    private const val Y0 = .28
    private const val Y1 = .885
    private const val FEATURES = 24
    private const val MIN_OVERLAP = 120

    sealed class Plan {
        data class Tap(val x: Double, val y: Double): Plan()
        data class Move(val d: Double): Plan()
    }
    fun centre(index: Int, offset: Double) =
        columns[index % columns.size] to ROW0 + (index / columns.size) * PITCH - offset
    fun plan(index: Int, offset: Double): Plan {
        val (x, y) = centre(index, offset)
        return when {
            y > BOTTOM -> Plan.Move(minOf(MAX_STEP, y - PARK))
            y < TOP -> Plan.Move(maxOf(-MAX_STEP, y - PARK))
            else -> Plan.Tap(x, y)
        }
    }
    private fun channelDelta(a: Int, b: Int): Int =
        (abs((a shr 16 and 255) - (b shr 16 and 255)) +
            abs((a shr 8 and 255) - (b shr 8 and 255)) +
            abs((a and 255) - (b and 255))) / 3

    /** Distinguish a portrait (even dark/locked) from empty purple background. */
    fun hasCard(f: ProBuildVision.Frame, x: Double, y: Double): Boolean {
        val step = maxOf(2, (f.height * .006).toInt())
        val cx = (x * f.width).toInt(); val cy = (y * f.height).toInt()
        val x0 = (cx - f.width * .024).toInt().coerceAtLeast(0)
        val x1 = (cx + f.width * .024).toInt().coerceAtMost(f.width - step - 1)
        val y0 = (cy - f.height * .055).toInt().coerceAtLeast(0)
        val y1 = (cy + f.height * .055).toInt().coerceAtMost(f.height - step - 1)
        var sum = 0L; var n = 0
        for (yy in y0 until y1 step step) for (xx in x0 until x1 step step) {
            val a = f.pixel(xx, yy)
            sum += channelDelta(a, f.pixel(xx + step, yy)) + channelDelta(a, f.pixel(xx, yy + step))
            n += 2
        }
        return n > 0 && sum.toDouble() / n > 5.8
    }

    /** 8 columns of 3-channel vertical profiles for screenshot-to-screenshot scroll registration. */
    fun profile(f: ProBuildVision.Frame): FloatArray {
        val y0 = (Y0 * f.height).toInt(); val y1 = (Y1 * f.height).toInt()
        val result = FloatArray((y1 - y0) * FEATURES)
        val half = (.019 * f.width).toInt()
        for (y in y0 until y1) for (i in columns.indices) {
            val cx = (columns[i] * f.width).toInt()
            var r = 0; var g = 0; var b = 0; var n = 0
            for (x in (cx - half)..(cx + half) step 5) {
                val c = f.pixel(x.coerceIn(0, f.width - 1), y)
                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
            }
            val o = (y - y0) * FEATURES + i * 3
            result[o] = r.toFloat() / n; result[o + 1] = g.toFloat() / n; result[o + 2] = b.toFloat() / n
        }
        return result
    }

    data class Shift(val px: Int, val cost: Float, val median: Float) {
        val reliable get() = cost < 22f && cost < .55f * median
    }
    fun shift(before: FloatArray, after: FloatArray): Shift {
        val n = minOf(before.size, after.size) / FEATURES
        val limit = n - MIN_OVERLAP
        if (limit < 1) return Shift(0, Float.MAX_VALUE, Float.MAX_VALUE)
        val costs = FloatArray(2 * limit + 1)
        var best = 0; var bestCost = Float.MAX_VALUE
        for (s in -limit..limit) {
            val from = maxOf(0, -s); val to = minOf(n, n - s)
            var sum = 0.0
            for (y in from until to) {
                val a = (y + s) * FEATURES; val b = y * FEATURES
                for (k in 0 until FEATURES) sum += abs(before[a + k] - after[b + k])
            }
            val cost = (sum / ((to - from) * FEATURES)).toFloat()
            costs[s + limit] = cost
            if (cost < bestCost) { best = s; bestCost = cost }
        }
        costs.sort()
        return Shift(best, bestCost, costs[costs.size / 2])
    }
}
