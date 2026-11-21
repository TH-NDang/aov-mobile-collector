package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class CaptureOverlayShieldTest {
    private val changes = mutableListOf<Boolean>()
    private val shield = CaptureOverlayShield { changes.add(it) }

    @Test fun ordinaryScreenshotsAndGesturesStillHideAndRestoreIndividually() {
        repeat(3) {
            val operation = shield.acquire()
            assertTrue(shield.contains(operation))
            shield.release(operation)
            assertFalse(shield.contains(operation))
        }
        assertEquals(listOf(true, false, true, false, true, false), changes)
    }

    @Test fun nativeSaveOnlyHidesOnceAcrossHoldPromptSureAndVerification() {
        val transaction = shield.acquire()
        repeat(4) {
            val operation = shield.acquire()
            shield.release(operation)
            assertEquals(listOf(true), changes)
        }
        shield.release(transaction)
        assertEquals(listOf(true, false), changes)
    }

    @Test fun controlsReturnBetweenTransactionsRatherThanStayingHiddenForTheRun() {
        repeat(3) {
            val transaction = shield.acquire()
            val operation = shield.acquire()
            shield.release(operation)
            shield.release(transaction)
            assertEquals(false, changes.last())
        }
        assertEquals(listOf(true, false, true, false, true, false), changes)
    }

    @Test fun everyNativeFailurePointCanRestoreControlsWithoutAnIntermediateFlash() {
        for (failedOperation in 0..3) {
            changes.clear()
            val transaction = shield.acquire()
            repeat(failedOperation + 1) { shield.release(shield.acquire()) }
            shield.release(transaction)
            assertEquals("Failure at native operation $failedOperation", listOf(true, false), changes)
        }
    }

    @Test fun overlappingOperationsCannotRevealTheOverlayInAnOutstandingScreenshot() {
        val screenshot = shield.acquire()
        val gesture = shield.acquire()
        shield.release(gesture)
        assertEquals(listOf(true), changes)
        shield.release(screenshot)
        assertEquals(listOf(true, false), changes)
    }

    @Test fun stopPauseOrWatchdogResetInvalidatesQueuedOperationsAndRestoresControls() {
        val transaction = shield.acquire()
        val operation = shield.acquire()
        shield.reset()
        assertFalse(shield.contains(transaction))
        assertFalse(shield.contains(operation))
        assertEquals(listOf(true, false), changes)
        shield.release(operation)
        shield.release(transaction)
        shield.reset()
        assertEquals(listOf(true, false), changes)
    }

    @Test fun lateCallbacksAfterCancellationCannotUnhideANewerCapture() {
        val oldTransaction = shield.acquire()
        val oldOperation = shield.acquire()
        shield.reset()
        val newOperation = shield.acquire()
        shield.release(oldTransaction)
        shield.release(oldOperation)
        assertEquals(listOf(true, false, true), changes)
        shield.release(newOperation)
        assertEquals(listOf(true, false, true, false), changes)
    }

    @Test fun duplicateCompletionDoesNotRestoreTwiceOrAffectAnotherOperation() {
        val operation = shield.acquire()
        shield.release(operation)
        val next = shield.acquire()
        shield.release(operation)
        assertTrue(shield.contains(next))
        assertEquals(listOf(true, false, true), changes)
        shield.release(next)
        shield.release(next)
        assertEquals(listOf(true, false, true, false), changes)
    }
}
