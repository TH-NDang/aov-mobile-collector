package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSaveDispatchGuardTest {
    @Test fun stopDuringFreshReadCancelsArmedUndispatchedJournalOnce() {
        var cleanups = 0
        val guard = NativeSaveDispatchGuard { cleanups++ }
        guard.armed()
        guard.close()
        guard.close()
        guard.armed()
        assertEquals(1, cleanups)
        assertFalse(guard.dispatch())
    }

    @Test fun lateArmingCompletionAfterStopStillCleansUpWithoutGesture() {
        var cleanups = 0
        val guard = NativeSaveDispatchGuard { cleanups++ }
        guard.close()
        assertEquals(0, cleanups)
        guard.armed()
        guard.armed()
        assertEquals(1, cleanups)
        assertFalse(guard.dispatch())
    }

    @Test fun stopAfterDispatchRetainsJournalEvenWithoutGestureCallback() {
        var cleanups = 0
        val guard = NativeSaveDispatchGuard { cleanups++ }
        guard.armed()
        assertTrue(guard.dispatch())
        guard.close()
        guard.armed()
        assertEquals(0, cleanups)
    }

    @Test fun failedArmOrReadinessTimeoutDoesNotCancelAnything() {
        var cleanups = 0
        val guard = NativeSaveDispatchGuard { cleanups++ }
        guard.close()
        assertEquals(0, cleanups)
    }
}
