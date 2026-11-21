package vn.ndang.aovcollector

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic compact journal signatures only; no user screenshots or Pictures assets. */
class NativePhotoFineAlignmentTest {
    private fun asset(x: Int, y: Int): Int {
        val red = (125 + 120 * sin(x * .63 + y * .28)).toInt()
        val green = (125 + 120 * sin(y * .51 - x * .19)).toInt()
        val blue = (125 + 120 * sin((x + y) * .43)).toInt()
        return (red shl 16) or (green shl 8) or blue
    }

    private fun journalSignature(left: Double, top: Double, side: Double): String = "native-icon-rgb-v1:" +
        (0 until 100).joinToString("") { index ->
            val pixels = listOf(.25, .75).flatMap { dy ->
                listOf(.25, .75).map { dx ->
                    asset((left + (index % 10 + dx) * side / 10).toInt(), (top + (index / 10 + dy) * side / 10).toInt())
                }
            }
            var averaged = 0
            for (shift in 0..16 step 8) {
                averaged = averaged or ((pixels.sumOf { it shr shift and 255 } / 4) shl shift)
            }
            "%06x".format(averaged)
        }

    @Test fun persisted128PixelIconWithFractionalAlignmentMatchesWithoutWeakerEvidence() {
        // Both crops fail the old 5%-scale/half-grid search, although they came from this exact asset.
        for ((left, top, side) in listOf(Triple(17.3, 21.6, 82.7), Triple(17.7, 20.7, 85.3))) {
            val result = NativePhotoReceipt.matchDetailed(journalSignature(left, top, side), 128, 128, ::asset)
            assertTrue("Crop $left,$top,$side: $result", result.matched)
            assertTrue("This regression must reach pixel-space refinement", result.fineComparisons > 0)
            assertTrue(result.fineComparisons <= 972)
            assertTrue(result.bestRgbError!! <= 18)
            assertTrue(result.bestCloseFraction!! >= .85)
            assertTrue(result.bestCorrelation!! >= .92)
        }
    }

    @Test fun extraAlignmentDoesNotAcceptDifferentContentOrInvalidSignatures() {
        val signature = journalSignature(17.3, 21.6, 82.7)
        val wrong = listOf<(Int, Int) -> Int>(
            { x, y -> asset(127 - x, y) },
            { x, y -> asset(y, 127 - x) },
            { x, y ->
                val original = asset(x, y)
                var changed = 0
                for (shift in 0..16 step 8) {
                    changed = changed or (((original shr shift and 255) + 80).coerceAtMost(255) shl shift)
                }
                changed
            },
            { _, _ -> 0x454DA0 }
        )
        for ((index, candidate) in wrong.withIndex()) {
            val result = NativePhotoReceipt.matchDetailed(signature, 128, 128, candidate)
            assertFalse("Wrong asset $index: $result", result.matched)
            assertTrue(result.fineComparisons <= 972)
        }
        assertFalse(NativePhotoReceipt.matchDetailed(null, 128, 128, ::asset).matched)
        assertFalse(NativePhotoReceipt.matchDetailed(signature, 2400, 1080, ::asset).matched)
    }

    @Test fun fineContentMatchStillRequiresFreshUniqueReadableReceipt() {
        val report = NativePhotoReceipt.matchDetailed(journalSignature(17.3, 21.6, 82.7), 128, 128, ::asset)
        assertTrue(report.toString(), report.matched)
        val fresh = NativePhotoReceipt.Candidate("fresh", 1_500, true, report.matched)

        fun choose(vararg candidates: NativePhotoReceipt.Candidate) =
            NativePhotoReceipt.select(setOf("old"), setOf("claimed"), 1_000, 2_000, candidates.toList())
        assertEquals("fresh", choose(fresh))
        assertNull(choose(fresh.copy(id = "old")))
        assertNull(choose(fresh.copy(id = "claimed")))
        assertNull(choose(fresh.copy(modified = 2_001)))
        assertNull(choose(fresh, fresh.copy(id = "duplicate")))
        assertNull(choose(fresh, fresh.copy(id = "partial", readable = false)))
        assertNull(choose(fresh.copy(ambiguous = true)))
    }
}
