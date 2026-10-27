package vn.ndang.aovcollector

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/** Content evidence for a native icon export. Only a compact sampled signature is journalled, not a full screenshot or photo. */
object NativePhotoReceipt {
    const val WINDOW_MS = 20_000L
    private const val GRID = 10
    private const val PREFIX = "native-icon-rgb-v1:"

    data class Candidate(val id: String, val modified: Long, val readable: Boolean, val matches: Boolean, val ambiguous: Boolean = false)

    /** Missing timestamps, unreadable images and multiple matches never constitute a receipt. */
    fun select(baseline: Set<String>, excluded: Set<String>, startedAt: Long, endsAt: Long, candidates: List<Candidate>): String? {
        val fresh = candidates.filter { it.id !in baseline && it.id !in excluded }
        // SAF dates often have one-second resolution, but the upper boundary never grows on resume.
        val possible = fresh.filter { it.modified > 0L && it.modified >= startedAt / 1000 * 1000 && it.modified <= endsAt }
        val matches = possible.filter { it.readable && it.matches }
        return matches.singleOrNull()?.id?.takeIf {
            // A partially indexed second document could become a second matching export later.
            possible.none { !it.readable || it.ambiguous } && fresh.none { it.modified <= 0L }
        }
    }

    /** An already verified no-save scene is safe to resume only after a complete, unambiguous scan. */
    fun absenceSafe(startedAt: Long, endsAt: Long, candidates: List<Candidate>, hasSignature: Boolean): Boolean {
        if (candidates.any { it.modified <= 0L }) return false
        val possible = candidates.filter { it.modified >= startedAt / 1000 * 1000 && it.modified <= endsAt }
        return possible.none { !it.readable || it.matches || it.ambiguous } && (possible.isEmpty() || hasSignature)
    }

    /** Diagnostics contain counts only, never gallery filenames or document IDs. */
    fun explain(startedAt: Long, endsAt: Long, candidates: List<Candidate>, hasSignature: Boolean): String {
        val possible = candidates.filter { it.modified > 0 && it.modified >= startedAt / 1000 * 1000 && it.modified <= endsAt }
        return "Pictures receipt unresolved: signature=$hasSignature, fresh=${candidates.size}, " +
            "inWindow=${possible.size}, matched=${possible.count { it.readable && it.matches }}, " +
            "unreadable=${possible.count { !it.readable }}, ambiguous=${possible.count { it.ambiguous }}, " +
            "undated=${candidates.count { it.modified <= 0 }}, outsideWindow=${candidates.count { it.modified > 0 } - possible.size}"
    }

    fun signature(bitmap: Bitmap, x: Double, y: Double): String? = signature(bitmap.width, bitmap.height, x, y, bitmap::getPixel)

    /** The same central patch used by skill identity, without assuming the probe is icon-centred. */
    fun signature(width: Int, height: Int, x: Double, y: Double, pixel: (Int, Int) -> Int): String? {
        if (width < GRID || height < GRID || x !in 0.0..1.0 || y !in 0.0..1.0) return null
        val side = minOf(width * .035, height * .063)
        // A probe may touch the icon's lower edge. Keep a few strongly textured local patches,
        // without moving far enough to reach the next skill or retaining full screenshot images.
        val variants = listOf(-.04, -.02, 0.0, .02, .04).mapNotNull { offset ->
            sample(width, height, x * width - side / 2, (y + offset) * height - side / 2, side, pixel)
        }.filter(::textured).sortedByDescending(::textureScore).take(3)
        if (variants.isEmpty()) return null
        return PREFIX + variants.joinToString("|") { values -> values.joinToString("") { "%06x".format(it and 0xffffff) } }
    }

    /** Fresh screen patches must retain the same textured image before any save gesture. */
    fun sameIcon(before: String?, after: String?): Boolean {
        val a = decode(before) ?: return false
        val b = decode(after) ?: return false
        return a.any { expected -> b.any { actual -> similar(expected, actual) } }
    }

    fun matches(signature: String?, bitmap: Bitmap): Boolean = matches(signature, bitmap.width, bitmap.height, bitmap::getPixel)

    /**
     * Native skill images are near-square assets, not game screenshots. Search a bounded patch
     * range to accommodate .50/.55 probes on the same illustrated icon and renderer scaling.
     * Both absolute RGB agreement and correlated spatial detail are required.
     */
    fun matches(signature: String?, width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean {
        val expected = decode(signature) ?: return false
        if (width < 32 || height < 32 || width.toDouble() / height !in .75..1.34) return false
        val edge = minOf(width, height).toDouble()
        for (percent in 30..100 step 5) {
            val side = edge * percent / 100
            for (row in 0..8) {
                for (column in 0..8) {
                    val left = (width - side) * column / 8
                    val top = (height - side) * row / 8
                    val actual = sample(width, height, left, top, side, pixel) ?: continue
                    if (expected.any { similar(it, actual) }) return true
                }
            }
        }
        return false
    }

    private fun decode(value: String?): List<IntArray>? {
        if (value == null || !value.startsWith(PREFIX)) return null
        val variants = value.removePrefix(PREFIX).split('|')
        if (variants.size !in 1..3 || variants.any { it.length != GRID * GRID * 6 }) return null
        return try {
            variants.map { hex -> IntArray(GRID * GRID) { hex.substring(it * 6, it * 6 + 6).toInt(16) } }
                .takeIf { it.all(::textured) }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun textureScore(values: IntArray): Double {
        val lights = values.map(::light)
        val mean = lights.average()
        return lights.sumOf { (it - mean) * (it - mean) } / lights.size
    }

    private fun sample(width: Int, height: Int, left: Double, top: Double, side: Double, pixel: (Int, Int) -> Int): IntArray? {
        if (side < GRID || left < 0 || top < 0 || left + side > width || top + side > height) return null
        return IntArray(GRID * GRID) { index ->
            val col = index % GRID
            val row = index / GRID
            var red = 0
            var green = 0
            var blue = 0
            // Average four interior samples per cell, reducing JPEG and scale/resampling noise.
            for (dy in listOf(.25, .75)) {
                for (dx in listOf(.25, .75)) {
                    val p = pixel(
                        (left + (col + dx) * side / GRID).toInt().coerceIn(0, width - 1),
                        (top + (row + dy) * side / GRID).toInt().coerceIn(0, height - 1)
                    )
                    red += p shr 16 and 255
                    green += p shr 8 and 255
                    blue += p and 255
                }
            }
            (red / 4 shl 16) or (green / 4 shl 8) or (blue / 4)
        }
    }

    private fun light(pixel: Int) = ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)) / 3.0

    private fun textured(values: IntArray): Boolean {
        val lights = values.map(::light)
        val mean = lights.average()
        val deviation = sqrt(lights.sumOf { (it - mean) * (it - mean) } / lights.size)
        val edges = (0 until GRID).sumOf { row ->
            (0 until GRID - 1).sumOf { col -> abs(lights[row * GRID + col] - lights[row * GRID + col + 1]) }
        } / (GRID * (GRID - 1))
        return deviation >= 16 && edges >= 7 && lights.maxOrNull()!! - lights.minOrNull()!! >= 65
    }

    private fun similar(expected: IntArray, actual: IntArray): Boolean {
        var error = 0L
        var close = 0
        for (i in expected.indices) {
            var cell = 0
            for (shift in listOf(0, 8, 16)) cell += abs((expected[i] shr shift and 255) - (actual[i] shr shift and 255))
            error += cell
            if (cell <= 90) close++
        }
        if (error.toDouble() / (expected.size * 3) > 18 || close < expected.size * .85) return false
        val a = expected.map(::light)
        val b = actual.map(::light)
        val meanA = a.average()
        val meanB = b.average()
        var covariance = 0.0
        var energyA = 0.0
        var energyB = 0.0
        for (i in a.indices) {
            val x = a[i] - meanA
            val y = b[i] - meanB
            covariance += x * y
            energyA += x * x
            energyB += y * y
        }
        return energyA > 0 && energyB > 0 && covariance / sqrt(energyA * energyB) >= .92
    }
}
