package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSaveWatchdogTest {
    @Test fun fifteenSecondsIsNotEnoughToAbortProgressingNativeSave() {
        var time = 10_000L
        val watchdog = NativeSaveWatchdog({ time })
        time += 15_001L
        assertNull("15 seconds may include Android screenshot throttling and OCR", watchdog.expired())
        watchdog.advanced(NativeSaveWatchdog.Stage.LONG_PRESS)
        time += 15_001L
        assertNull(watchdog.expired())
        watchdog.advanced(NativeSaveWatchdog.Stage.PROMPT_READ)
        time += 15_001L
        assertNull(watchdog.expired())
        watchdog.advanced(NativeSaveWatchdog.Stage.SURE_PRESSED)
        time += 15_001L
        assertNull(watchdog.expired())
    }

    @Test fun trulyHungOcrStillTimesOutWithPhaseAndWithoutBlindRetry() {
        var time = 0L
        val watchdog = NativeSaveWatchdog({ time })
        watchdog.advanced(NativeSaveWatchdog.Stage.PROMPT_READ)
        time = 35_999L
        assertNull(watchdog.expired())
        time = 36_000L
        assertEquals(NativeSaveWatchdog.Expiration.IDLE, watchdog.expired())
        assertTrue(watchdog.explain(NativeSaveWatchdog.Expiration.IDLE).contains("PROMPT_READ"))
        assertTrue(watchdog.explain(NativeSaveWatchdog.Expiration.IDLE).lowercase().contains("không tự"))
    }

    @Test fun waitingForPicturesIndexGetsMoreTimeButNotForever() {
        var time = 0L
        val watchdog = NativeSaveWatchdog({ time })
        watchdog.advanced(NativeSaveWatchdog.Stage.INDEXING_PICTURES)
        time += 45_000L
        assertNull("Android gallery indexing on slower phones needs longer than 15 seconds", watchdog.expired())
        time += 25_000L
        assertEquals(NativeSaveWatchdog.Expiration.IDLE, watchdog.expired())
    }

    @Test fun absoluteLimitIsNotResetEvenByRepeatedSuccessfulOcrCallbacks() {
        var time = 0L
        val watchdog = NativeSaveWatchdog({ time })
        for (i in 1..14) {
            time += 10_000L
            watchdog.advanced(NativeSaveWatchdog.Stage.VERIFYING_GAME)
            assertNull(watchdog.expired())
        }
        time += 10_000L
        assertEquals(NativeSaveWatchdog.Expiration.TOTAL, watchdog.expired())
        assertTrue(watchdog.explain(NativeSaveWatchdog.Expiration.TOTAL).contains("Không tự"))
    }

    @Test fun monotonicClockDoesNotTreatClockJitterAsProgress() {
        var time = 50_000L
        val watchdog = NativeSaveWatchdog({ time })
        time = 48_000L
        assertEquals(0L, watchdog.elapsedMs)
        assertEquals(0L, watchdog.idleMs)
        time = 51_000L
        assertNull(watchdog.expired())
    }
}
