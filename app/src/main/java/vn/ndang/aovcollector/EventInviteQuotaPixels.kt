package vn.ndang.aovcollector

/**
 * Secondary, high-confidence reader for the 10th anniversary's 0/1 daily
 * invitation counter. Used ONLY after the full screen identifies the exact
 * "Hỗ trợ bạn bè" popup and OCR confirms the "Hôm nay đã nhập" caption.
 *
 * The two coloured numerals are often missing entirely from ML Kit output:
 * full-resolution images still return "Hôm nay đã nhập / mã". Unlike generic
 * OCR, these small red/gold glyphs can be separated from their purple
 * background by their colour. The left glyph is either a wide "0" or a narrow
 * "1"; the right glyph must be "1" beside an independently verified white slash.
 *
 * No evidence -> null. Nothing here is permitted to tap or submit a code.
 */
object EventInviteQuotaPixels {
    /** Diagnostic scores are non-sensitive and may be used by unit tests. */
    data class Reading(val used: Int, val dailyLimit: Int, val firstInk: Int, val secondInk: Int, val slashInk: Int)

    fun read(width: Int, height: Int, pixel: (Int, Int) -> Int): Reading? {
        if (width < 800 || height < 360 || width <= height) return null

        fun channelRed(rgb: Int) = (rgb ushr 16) and 255

        fun channelGreen(rgb: Int) = (rgb ushr 8) and 255

        fun channelBlue(rgb: Int) = rgb and 255

        fun colouredNumber(rgb: Int): Boolean {
            val r = channelRed(rgb)
            val g = channelGreen(rgb)
            val b = channelBlue(rgb)
            return r > 145 && r + g - b > 48 && g < 185 && r * 100 > 125 * g
        }

        fun whiteSlash(rgb: Int): Boolean {
            val r = channelRed(rgb)
            val g = channelGreen(rgb)
            val b = channelBlue(rgb)
            return r > 190 && g > 160 && b > 185 && r + g + b > 585
        }

        fun region(x0: Double, x1: Double, y0: Double, y1: Double, matches: (Int) -> Boolean): Pair<Int, Int> {
            val left = (width * x0).toInt().coerceIn(0, width - 1)
            val right = (width * x1).toInt().coerceIn(left + 1, width)
            val top = (height * y0).toInt().coerceIn(0, height - 1)
            val bottom = (height * y1).toInt().coerceIn(top + 1, height)
            var hits = 0
            for (y in top until bottom) {
                for (x in left until right) {
                    if (matches(pixel(x, y))) hits++
                }
            }
            return hits to ((right - left) * (bottom - top))
        }

        // All three ROIs use the same UI-relative geometry as the screenshot
        // supplied on the user's phone. Do not search for red pixels anywhere
        // else: a random event decoration is not a daily invitation counter.
        val (first, firstArea) = region(.528, .543, .529, .558, ::colouredNumber)
        val (second, secondArea) = region(.545, .555, .529, .558, ::colouredNumber)
        val (slash, slashArea) = region(.541, .547, .531, .555, ::whiteSlash)

        if (first.toDouble() / firstArea < .07 ||
            second.toDouble() / secondArea < .105 ||
            slash.toDouble() / slashArea < .08
        ) {
            return null
        }
        val widthRatio = first.toDouble() / second
        // Distinct measured bands on the game's coloured numerals: 1/1 has
        // nearly equal ink counts, while 0/1 has a ring-shaped first glyph.
        // The gap is deliberately not classified to reject uncertain frames.
        val used = when {
            widthRatio in .67..1.23 -> 1
            widthRatio in 1.60..2.80 -> 0
            else -> return null
        }
        return Reading(used, 1, first, second, slash)
    }
}
