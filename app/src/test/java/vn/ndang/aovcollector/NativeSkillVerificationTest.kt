package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeSkillVerificationTest {
    @Test fun resolvedEvidenceNeedsNoAdditionalRead() {
        var reads = 0
        var result: String? = null
        NativeSaveReadiness.verify("detail", {
            true
        }, { _, _ -> error("Unexpected wait") }, { reads++ }, { it == "detail" }) { result = it }
        assertEquals("detail", result)
        assertEquals(0, reads)
    }

    @Test fun ambiguousOrUnavailableEvidenceGetsOneFreshRead() {
        for (initial in listOf(null, "unknown", "prompt")) {
            var reads = 0
            var wait = 0L
            var result: String? = null
            NativeSaveReadiness.verify(initial, { true }, { ms, next ->
                wait = ms
                next()
            }, {
                reads++
                it("detail")
            }, { it == "detail" }) { result = it }
            assertEquals("detail", result)
            assertEquals(700L, wait)
            assertEquals(1, reads)
        }
    }

    @Test fun secondUnresolvedReadIsReturnedWithoutInventingSuccessOrLooping() {
        var reads = 0
        var result: String? = "unexpected"
        NativeSaveReadiness.verify("unknown", { true }, { _, next -> next() }, {
            reads++
            it(null)
        }, { it == "detail" }) { result = it }
        assertNull(result)
        assertEquals(1, reads)
    }

    @Test fun cancellationBeforeRetryPreventsRead() {
        var active = true
        var reads = 0
        var calls = 0
        NativeSaveReadiness.verify("unknown", { active }, { _, next ->
            active = false
            next()
        }, { reads++ }, { false }) { calls++ }
        assertEquals(0, reads)
        assertEquals(0, calls)
    }

    @Test fun cancellationDuringOcrIgnoresItsLateResult() {
        var active = true
        var calls = 0
        var callback: ((String?) -> Unit)? = null
        NativeSaveReadiness.verify("unknown", { active }, { _, next -> next() }, { callback = it }, { false }) { calls++ }
        active = false
        callback!!("detail")
        assertEquals(0, calls)
    }

    @Test fun lateAndDuplicateCallbacksCannotCompleteTwice() {
        var active = true
        var calls = 0
        var callback: ((String?) -> Unit)? = null
        NativeSaveReadiness.verify("unknown", { active }, { _, next -> next() }, { callback = it }, { false }) { calls++ }
        callback!!("detail")
        callback!!("detail")
        assertEquals(1, calls)
        active = false
        callback!!("detail")
        assertEquals(1, calls)
    }
}
