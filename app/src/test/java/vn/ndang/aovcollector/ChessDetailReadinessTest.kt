package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Test

class ChessDetailReadinessTest {
    private fun gate(icon: Boolean = true) = ChessDetailReadiness(
        0L,
        icon,
        { a, b -> a.contentEquals(b) },
        { a, b -> a == b }
    )

    private fun ChessDetailReadiness.frame(time: Long, icons: List<String> = listOf("loaded"), value: Int = 10, active: Boolean = true) =
        observe(time, active, true, intArrayOf(value), icons)

    @Test fun staticPlaceholderCannotAuthorizeCaptureBeforeDelayedImage() {
        val gate = gate()
        for (time in 0L..8_000L step 800L) assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(time, emptyList()))
        assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(8_800L))
        assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(9_600L))
        assertEquals(ChessDetailReadiness.Decision.READY, gate.frame(10_400L))
    }

    @Test fun detailOrIconJitterRestartsStableWindow() {
        val gate = gate()
        gate.frame(0L)
        gate.frame(800L)
        assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(1_600L, value = 20))
        assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(2_400L, listOf("new"), 20))
        assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(3_200L, listOf("new"), 20))
        assertEquals(ChessDetailReadiness.Decision.READY, gate.frame(4_000L, listOf("new"), 20))
    }

    @Test fun timeoutDoesNotAcceptLateLoadedImage() {
        val gate = gate()
        assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(29_999L, emptyList()))
        assertEquals(ChessDetailReadiness.Decision.TIMEOUT, gate.frame(30_000L))
        assertEquals(ChessDetailReadiness.Decision.TIMEOUT, gate.frame(32_000L))
    }

    @Test fun cancellationCannotBecomeReadyOnLateFrame() {
        val gate = gate()
        gate.frame(0L)
        gate.frame(800L)
        assertEquals(ChessDetailReadiness.Decision.CANCELLED, gate.frame(1_600L, active = false))
        assertEquals(ChessDetailReadiness.Decision.CANCELLED, gate.frame(2_400L))
    }

    @Test fun scrolledTextOnlyPageDoesNotNeedInventedSkillProbeSignature() {
        val gate = gate(false)
        gate.frame(0L, emptyList())
        gate.frame(800L, emptyList())
        assertEquals(ChessDetailReadiness.Decision.READY, gate.frame(1_600L, emptyList()))
    }

    @Test fun frozenClockStillHasReadLimit() {
        val gate = gate()
        repeat(40) { assertEquals(ChessDetailReadiness.Decision.WAIT, gate.frame(0L, emptyList())) }
        assertEquals(ChessDetailReadiness.Decision.TIMEOUT, gate.frame(0L))
    }
}
