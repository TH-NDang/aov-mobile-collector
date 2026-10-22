package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGestureAttemptTest {
    private val fine = CalibrationAction("pro.v1.list.scroll", "Pro", listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .762)), true)
    private val unsafeSaved = listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .742))
    private val profile = CalibrationProfile(2400, 1080, 1, 80, 0, 0, 0)

    @Test fun actualAdapterRoutesInvalidSavedOverrideToPreviewButNeverToRuntimeDispatch() {
        var previews = 0
        var runtimeDispatches = 0
        val failures = mutableListOf<String>()
        ProGestureAttempt(fine, unsafeSaved, profile, null, true, { true }, {}, { failures += it }).run(
            verify = { _, _ -> error("Preview must defer final verification until Run") },
            dispatch = { _, _ -> previews++ },
            then = { error("Opening preview must leave continuation pending") }
        )
        assertEquals(1, previews)
        assertTrue(failures.isEmpty())
        ProGestureAttempt(fine, unsafeSaved, profile, null, false, { true }, {}, { failures += it }).run(
            verify = null,
            dispatch = { _, _ -> runtimeDispatches++ },
            then = { assertFalse(it) }
        )
        assertEquals(0, runtimeDispatches)
        assertTrue(failures.single().contains("semantic-bounds"))
        // Reset's effective points are the current action defaults, not the coarse swipe defaults.
        ProGestureAttempt(fine, fine.points, profile, null, false, { true }, {}, { error(it) }).run(
            verify = null,
            dispatch = { _, completed ->
                runtimeDispatches++
                completed(true)
            },
            then = { assertTrue(it) }
        )
        assertEquals(1, runtimeDispatches)
    }

    @Test fun detailedFinishInvalidatesReaderBeforeItsGenericFalseCallback() {
        var alive = true
        val events = mutableListOf<String>()
        val attempt = ProGestureAttempt(fine, fine.points, profile, null, false, { alive }, { events += "report:$it" }, {
            events += "finish:$it"
            alive = false
        })
        attempt.run(null, dispatch = { refused, completed ->
            refused(GestureFailure("application-window-count=2", detail = "windows=[OTHER app=true]"))
            completed(false)
        }, then = {
            if (alive && !it) events += "generic-reader-error"
        })
        assertEquals(2, events.size)
        assertTrue(events[0].startsWith("report:"))
        assertTrue(events[0].contains("windows=[OTHER app=true]"))
        assertTrue(events[1].startsWith("finish:"))
        assertTrue(events[1].contains("W01"))
        assertTrue(events[1].contains(fine.id))
        assertFalse(events.any { it == "generic-reader-error" })
    }

    @Test fun freshAttemptDoesNotInheritRefusalAndMissingCallbackDoesNotClaimNoDispatch() {
        val messages = mutableListOf<String>()

        fun attempt() = ProGestureAttempt(fine, fine.points, profile, null, false, { true }, {}, { messages += it })
        attempt().run(null, { refusal, completed ->
            refusal(GestureFailure("game-not-focused"))
            completed(false)
        }, {})
        attempt().run(null, { _, completed -> completed(false) }, {})
        assertTrue(messages[0].contains("game-not-focused"))
        assertTrue(messages[1].contains("unknown-outcome"))
        assertFalse(messages[1].contains("game-not-focused"))
        assertFalse(messages[1].contains("chưa gửi"))
    }

    @Test fun failedFreshEvidenceNeverDispatchesApply() {
        var sent = false
        var message = ""
        ProGestureAttempt(fine, fine.points, profile, null, false, { true }, {}, { message = it }).run(
            verify = { _, answer -> answer(false) },
            dispatch = { _, _ -> sent = true },
            then = { assertFalse(it) }
        )
        assertFalse(sent)
        assertTrue(message.contains("recommendation-changed"))
    }
}
