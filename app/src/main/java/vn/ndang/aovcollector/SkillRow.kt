package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The row of skill icons on a hero's page, under the attribute bars: the passive first, then three skills, or
 * four for heroes like Edras, whose five icons share the same width. Heroes that change form (Qi, Volkath,
 * Liliana, Edras…) have a ↻ button after the icons that swaps some or all of them for the other form's.
 * Measured on the user's 2400x1080 screen: positions are fractions of the screen, sizes fractions of its height.
 */
object SkillRow {
    const val Y = .605
    val FOUR = listOf(.093, .138, .1825, .2267)
    val FIVE = listOf(.094, .1275, .161, .194, .228)
    const val SWAP_X = .2635

    /** [centres] of the icons, null when no row of round icons is there (a panel or another screen in front). */
    class Row(val centres: List<Double>?, val swap: Boolean, val icons: List<IntArray>) {
        /** Same icons at the same places, as when the ↻ button did nothing or brought the first form back. */
        fun sameAs(other: Row) = icons.size == other.icons.size && icons.indices.none { differ(icons[it], other.icons[it]) }
    }

    /** Rows of the screen read: the band around the icons, the ring edges reaching .046 of the height from their centre. */
    private const val TOP = .55
    private const val BOTTOM = .66

    /** Of the screen's width, enough for the ↻ button with its margin. */
    private const val LEFT_PART = .30

    fun read(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Row {
        val band = Band(width, height, readRow)
        val four = FOUR.minOf { band.ring(it) }
        val five = FIVE.minOf { band.ring(it) }
        // A correct layout scores .73 or more on its weakest icon and the other at most .65 (129 heroes and Edras).
        val centres = when {
            maxOf(four, five) < .6 -> null
            five > four -> FIVE
            else -> FOUR
        }
        return Row(centres, band.swap() > .75, (centres ?: emptyList()).map { band.icon(it) })
    }

    /** Mean channel difference between two [icon] samples; the same icon reads 0, a changed one over 50. */
    fun differ(a: IntArray, b: IntArray): Boolean = a.size != b.size || a.indices.sumOf { abs(a[it] - b[it]) }.toDouble() / a.size > 25

    private class Band(val width: Int, val height: Int, readRow: (Int, IntArray) -> Unit) {
        val top = (TOP * height).toInt()
        val rows = (BOTTOM * height).toInt() - top
        val w = (LEFT_PART * width).toInt()
        val rgb = IntArray(w * rows)
        val luma = IntArray(w * rows)

        /** Pixel lengths are drawn for a 1080-pixel-high screen. */
        val scale = height / 1080.0

        init {
            val row = IntArray(width)
            for (y in 0 until rows) {
                readRow(top + y, row)
                System.arraycopy(row, 0, rgb, y * w, w)
                for (x in 0 until w) {
                    val c = row[x]
                    luma[y * w + x] = (299 * (c shr 16 and 255) + 587 * (c shr 8 and 255) + 114 * (c and 255)) / 1000
                }
            }
        }

        fun at(x: Int, y: Int): Int = if (x < 0 || x >= w || y < 0 || y >= rows) -1 else luma[y * w + x]

        /**
         * How round an icon at [x] looks: the share of 48 directions where the luma steps by more than 25 across
         * its rim, at the best of the radii icons have (34 pixels for five, 36 to 42 for four).
         */
        fun ring(x: Double): Double {
            val cx = x * width
            val cy = Y * height - top
            val d = 4 * scale
            var best = 0.0
            for (r in 28..45) {
                val radius = r * scale
                var edges = 0
                for (k in 0 until 48) {
                    val a = 2 * Math.PI * k / 48
                    val c = cos(a)
                    val s = sin(a)
                    val inner = at((cx + (radius - d) * c).roundToInt(), (cy + (radius - d) * s).roundToInt())
                    val outer = at(
                        (cx + (radius + d) * c).roundToInt(),
                        (
                            cy +
                                (radius + d) * s
                            ).roundToInt()
                    )
                    if (inner >= 0 && outer >= 0 && abs(inner - outer) > 25) edges++
                }
                best = maxOf(best, edges / 48.0)
            }
            return best
        }

        /** Correlation of the ↻ shape with the screen, at the best shift of up to 4 pixels: .88 or more when there, at most .53 when not. */
        fun swap(): Double {
            val cx = SWAP_X * width
            val cy = Y * height - top
            val mean = SWAP_MASK.sumOf { line -> line.count { it == '#' } }.toDouble() / (SWAP_SIZE * SWAP_SIZE)
            var best = -1.0
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val values = DoubleArray(SWAP_SIZE * SWAP_SIZE)
                    var sum = 0.0
                    for (j in 0 until SWAP_SIZE) {
                        for (i in 0 until SWAP_SIZE) {
                            val v =
                                at(
                                    (cx + dx * scale + (i - SWAP_SIZE / 2) * scale).roundToInt(),
                                    (cy + dy * scale + (j - SWAP_SIZE / 2) * scale).roundToInt()
                                )
                            if (v < 0) return 0.0
                            values[j * SWAP_SIZE + i] = v.toDouble()
                            sum += v
                        }
                    }
                    val average = sum / values.size
                    var cross = 0.0
                    var spread = 0.0
                    var maskSpread = 0.0
                    for (j in 0 until SWAP_SIZE) {
                        for (i in 0 until SWAP_SIZE) {
                            val m = (if (SWAP_MASK[j][i] == '#') 1.0 else 0.0) - mean
                            val v = values[j * SWAP_SIZE + i] - average
                            cross += m * v
                            spread += v * v
                            maskSpread += m * m
                        }
                    }
                    if (spread > 0) best = maxOf(best, cross / sqrt(spread * maskSpread))
                }
            }
            return best
        }

        /** Colours on a 10x10 grid over the middle of the icon at [x], away from its rim, to tell one icon from another. */
        fun icon(x: Double): IntArray {
            val cx = x * width
            val cy = Y * height - top
            val r = .022 * height
            val out = ArrayList<Int>()
            for (j in 0 until 10) {
                for (i in 0 until 10) {
                    val dx = (i + .5) / 10 * 2 - 1
                    val dy = (j + .5) / 10 * 2 - 1
                    if (dx * dx + dy * dy > .8) continue
                    val px = (cx + dx * r).toInt()
                    val py = (cy + dy * r).toInt()
                    val c = if (px in 0 until w && py in 0 until rows) rgb[py * w + px] else 0
                    out.add(c shr 16 and 255)
                    out.add(c shr 8 and 255)
                    out.add(c and 255)
                }
            }
            return out.toIntArray()
        }
    }

    private const val SWAP_SIZE = 45

    /** The ↻ button's light pixels, averaged over six heroes' pages, centred on ([SWAP_X], [Y]). */
    private val SWAP_MASK = listOf(
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        "...................######....................",
        "...........####################..............",
        "...........######################............",
        "..........########################...........",
        ".........##########################..........",
        ".........###########################.........",
        ".........#############.........######........",
        "........############.............#####.......",
        ".......###########.................###.......",
        ".......###########..................##.......",
        ".......###########...................##......",
        "......############...........................",
        ".......##########............................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        ".............................................",
        "...........................############......",
        "......##...................############......",
        "......###..................############......",
        ".......###................#############......",
        ".......#####............##############.......",
        ".......#######........###############........",
        "........#############################........",
        ".........############################........",
        "..........##########################.........",
        "...........########################..........",
        "............#######################..........",
        "..............#####################..........",
        ".................#################...........",
        "...............................##............",
        ".............................................",
        ".............................................",
        "............................................."
    )
}
