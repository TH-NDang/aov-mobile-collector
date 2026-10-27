package vn.ndang.aovcollector

import java.util.PriorityQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReceiptPollingTest {
    private class Clock {
        private data class Event(val at: Long, val order: Int, val action: () -> Unit)

        private val events = PriorityQueue<Event>(compareBy<Event> { it.at }.thenBy { it.order })
        private var order = 0
        var now = 0L
        var active = true

        fun later(delay: Long, action: () -> Unit) {
            events.add(Event(now + delay, order++, action))
        }

        fun run() {
            var steps = 0
            while (events.isNotEmpty()) {
                assertTrue("Polling must terminate", ++steps < 100)
                val event = events.remove()
                now = event.at
                event.action()
            }
        }
    }

    @Test fun delayedIndexingIsReadWithoutOverlappingOrRepeatingTheSave() {
        val clock = Clock()
        var reads = 0
        var inFlight = false
        var result: String? = null
        var completedAt = 0L
        NativeReceiptPolling.read(
            { clock.now },
            { clock.active },
            clock::later,
            { callback: (String?) -> Unit ->
                assertFalse(inFlight)
                inFlight = true
                reads++
                clock.later(300) {
                    inFlight = false
                    callback(if (clock.now >= 5_000) "receipt" else "pending")
                }
            },
            { it == "receipt" }
        ) {
            result = it
            completedAt = clock.now
        }
        clock.run()
        assertEquals("receipt", result)
        assertEquals(4, reads)
        assertEquals(7_200L, completedAt)
    }

    @Test fun unchangedEvidenceStopsAtDeadlineRatherThanRefreshingItsBudget() {
        val clock = Clock()
        var reads = 0
        var calls = 0
        var completedAt = 0L
        NativeReceiptPolling.read(
            { clock.now },
            { clock.active },
            clock::later,
            { callback: (String?) -> Unit ->
                reads++
                callback("pending")
            },
            { false }
        ) {
            assertEquals("pending", it)
            calls++
            completedAt = clock.now
        }
        clock.run()
        assertEquals(15, reads)
        assertEquals(1, calls)
        assertEquals(30_000L, completedAt)
    }

    @Test fun maximumReadsBoundsPollingEvenIfClockDoesNotAdvanceNormally() {
        val clock = Clock()
        var reads = 0
        var calls = 0
        NativeReceiptPolling.read(
            { 0L },
            { clock.active },
            clock::later,
            { callback: (String?) -> Unit ->
                reads++
                callback(null)
            },
            { false },
            maxReads = 3
        ) {
            calls++
            assertNull(it)
        }
        clock.run()
        assertEquals(3, reads)
        assertEquals(1, calls)
    }

    @Test fun cancellationDuringReadOrBetweenReadsIgnoresLateReceipt() {
        for (stopAt in listOf(100L, 1_000L)) {
            val clock = Clock()
            var reads = 0
            var calls = 0
            NativeReceiptPolling.read(
                { clock.now },
                { clock.active },
                clock::later,
                { callback: (String?) -> Unit ->
                    reads++
                    clock.later(300) { callback("pending") }
                },
                { false }
            ) { calls++ }
            clock.later(stopAt) { clock.active = false }
            clock.run()
            assertEquals(1, reads)
            assertEquals(0, calls)
        }
    }

    @Test fun missingAndAfterDeadlineCallbacksCannotCompleteWithLateReceipt() {
        for (delay in listOf<Long?>(null, 31_000L)) {
            val clock = Clock()
            var calls = 0
            var reads = 0
            NativeReceiptPolling.read(
                { clock.now },
                { clock.active },
                clock::later,
                { callback: (String?) -> Unit ->
                    reads++
                    delay?.let { clock.later(it) { callback("receipt") } }
                },
                { it == "receipt" }
            ) {
                assertNull(it)
                assertEquals(30_000L, clock.now)
                calls++
            }
            clock.run()
            assertEquals(1, reads)
            assertEquals(1, calls)
        }
    }

    @Test fun duplicatedCallbacksNeverQueueExtraReadsOrFinishTwice() {
        val clock = Clock()
        var reads = 0
        var calls = 0
        NativeReceiptPolling.read(
            { clock.now },
            { clock.active },
            clock::later,
            { callback: (String?) -> Unit ->
                reads++
                val value = if (reads == 1) "pending" else "receipt"
                callback(value)
                callback(value)
            },
            { it == "receipt" }
        ) {
            calls++
            assertEquals("receipt", it)
        }
        clock.run()
        assertEquals(2, reads)
        assertEquals(1, calls)
    }

    @Test fun inactiveEntryDoesNotScheduleOrRead() {
        NativeReceiptPolling.read<String>(
            { 0L },
            { false },
            { _, _ -> throw AssertionError("Unexpected timer") },
            { throw AssertionError("Unexpected read") },
            { true }
        ) { throw AssertionError("Unexpected completion") }
    }
}
