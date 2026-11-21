package vn.ndang.aovcollector

import kotlin.math.floor
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePhotoAlignmentTest {
    private fun asset(x: Int, y: Int): Int {
        val red = (125 + 65 * sin((x * .17 + y * .08) * 2.1)).toInt()
        val green = (125 + 65 * sin((y * .13 - x * .04) * 2.1)).toInt()
        val blue = (125 + 65 * sin((x + y) * .11 * 2.1)).toInt()
        return (red shl 16) or (green shl 8) or blue
    }

    private fun rendered(x: Int, y: Int): Int {
        // The exact native image rendered at an off-grid scale and translated seven/five pixels.
        val left = 920
        val top = 514
        if (x !in left until left + 117 || y !in top until top + 117) return 0x7D7D7D
        val sx = (x - left + .5) * 192 / 117 - .5
        val sy = (y - top + .5) * 192 / 117 - .5
        val ix = floor(sx).toInt()
        val iy = floor(sy).toInt()
        val fx = sx - ix
        val fy = sy - iy
        var result = 0
        for (shift in 0..16 step 8) {
            fun channel(dx: Int, dy: Int) = asset((ix + dx).coerceIn(0, 191), (iy + dy).coerceIn(0, 191)) shr shift and 255
            val value = channel(0, 0) * (1 - fx) * (1 - fy) + channel(1, 0) * fx * (1 - fy) +
                channel(0, 1) * (1 - fx) * fy + channel(1, 1) * fx * fy
            result = result or (value.toInt() shl shift)
        }
        return result
    }

    private fun signature(): String {
        val signature = NativePhotoReceipt.signature(2400, 1080, .405, .525, ::rendered)
        assertNotNull(signature)
        return signature!!
    }

    @Test fun exactOffGridRenderedAssetNeedsRefinementNotWeakerThresholds() {
        val result = NativePhotoReceipt.matchDetailed(signature(), 192, 192, ::asset)
        assertTrue(result.toString(), result.matched)
        assertTrue("The unchanged coarse pass must fail this regression", result.refinedComparisons > 0)
        assertTrue(result.bestRgbError!! <= 18)
        assertTrue(result.bestCloseFraction!! >= .85)
        assertTrue(result.bestCorrelation!! >= .92)
        assertTrue(result.coarseComparisons <= 3645)
        assertTrue(result.refinedComparisons <= 972)
    }

    @Test fun transformedAndSimilarWrongAssetsStayRejectedWithinBudget() {
        val signature = signature()
        val wrong = listOf<(Int, Int) -> Int>(
            { x, y -> asset(191 - y, x) },
            { x, y -> asset(191 - x, y) },
            { x, y ->
                val original = asset(x, y)
                var shifted = 0
                for (bit in 0..16 step 8) shifted = shifted or (((original shr bit and 255) + 55).coerceAtMost(255) shl bit)
                shifted
            },
            { x, y -> asset((x % 48) * 4 + x / 48, (y % 48) * 4 + y / 48) },
            { _, _ -> 0x454DA0 }
        )
        for ((index, candidate) in wrong.withIndex()) {
            val result = NativePhotoReceipt.matchDetailed(signature, 192, 192, candidate)
            assertFalse("Wrong asset $index: $result", result.matched)
            assertTrue(result.coarseComparisons <= 3645)
            assertTrue(result.refinedComparisons <= 972)
        }
    }

    @Test fun flatLoadingAndNonSquareCandidatesCannotBecomeReceipts() {
        val flat = NativePhotoReceipt.signature(2400, 1080, .405, .525) { _, _ -> 0x454DA0 }
        assertFalse(NativePhotoReceipt.matchDetailed(flat, 192, 192, ::asset).matched)
        val wrongShape = NativePhotoReceipt.matchDetailed(signature(), 2400, 1080) { _, _ -> 0 }
        assertFalse(wrongShape.matched)
        assertTrue(wrongShape.coarseComparisons == 0 && wrongShape.refinedComparisons == 0)
        for (invalid in listOf(null, "", "native-icon-rgb-v1:not-hex")) {
            val result = NativePhotoReceipt.matchDetailed(invalid, 192, 192, ::asset)
            assertFalse(result.matched)
            assertTrue(result.coarseComparisons == 0 && result.refinedComparisons == 0)
        }
        assertFalse(NativePhotoReceipt.matchDetailed(signature(), 16, 16, ::asset).matched)
    }

    @Test fun largeBaselineAndDuplicateFreshMatchesRetainProvenanceRules() {
        val baseline = (0 until 1591).map { "old-$it" }.toSet()
        val old = baseline.map { NativePhotoReceipt.Candidate(it, 10_000L, true, true) }
        val fresh = NativePhotoReceipt.Candidate("fresh", 10_000L, true, true)
        assertEquals("fresh", NativePhotoReceipt.select(baseline, emptySet(), 9_000L, 11_000L, old + fresh))
        val duplicate = NativePhotoReceipt.Candidate("another-fresh", 10_000L, true, true)
        assertNull(NativePhotoReceipt.select(baseline, emptySet(), 9_000L, 11_000L, old + fresh + duplicate))
    }
}
