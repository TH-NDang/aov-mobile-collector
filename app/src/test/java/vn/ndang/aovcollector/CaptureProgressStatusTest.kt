package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureProgressStatusTest {
    @Test fun loadingNeverRemovesNativeStatusBetweenCompletedSnapshots() {
        val status = CaptureProgressStatus()
        assertEquals("Working", status.begin("run", "Working"))
        val details = "Working\nẢnh gốc: 5 đã chép · 0 đang chờ Pictures"
        assertEquals(details, status.complete("run", details))
        repeat(10) {
            assertNull(status.begin("run", "Working"))
            assertNull(status.complete("run", details))
        }
        assertNull(status.begin("run", "Next action"))
        assertEquals("Next action\nẢnh gốc: 6 đã chép", status.complete("run", "Next action\nẢnh gốc: 6 đã chép"))
    }

    @Test fun runChangeAndCloseDiscardOldCountersAndLateCallbacks() {
        val status = CaptureProgressStatus()
        status.begin("old", "Old")
        status.complete("old", "Old\nẢnh gốc: 20")
        assertEquals("New", status.begin("new", "New"))
        assertNull(status.complete("old", "Old\nẢnh gốc: 21"))
        status.clear()
        assertNull(status.complete("new", "Late result"))
        assertEquals("Reopened", status.begin("new", "Reopened"))
    }
}
