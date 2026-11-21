package vn.ndang.aovcollector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGestureDiagnosticsTest {
    private val action =
        CalibrationAction("pro.v1.list.scroll", "Pro", listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .56)), true)
    private val profile = CalibrationProfile(2400, 1080, 1, 80, 0, 0, 0)

    @Test fun windowRefusalIdentifiesActionAndNoDispatch() {
        val result = ProGestureDiagnostics.message(action, GestureFailure("application-window-count=2"))
        assertTrue(result.contains(action.id))
        assertTrue(result.contains("W01"))
        assertTrue(result.contains("application-window-count=2"))
        assertTrue(result.contains("chưa gửi"))
    }

    @Test fun cancellationAndLateGuardRefusalNeverImplyNoEffect() {
        for (reason in listOf("android-cancelled", "game-not-focused", "android-rejected", "shield-session-ended")) {
            val result = ProGestureDiagnostics.message(action, GestureFailure(reason, 1))
            assertTrue(result.contains("đã gửi 1 phần"))
            assertFalse(result.contains("chưa gửi"))
        }
    }

    @Test fun unknownOrExceptionDoesNotClaimNoDispatch() {
        for (reason in listOf("unknown-outcome", "android-exception")) {
            val result = ProGestureDiagnostics.message(action, GestureFailure(reason))
            assertTrue(result.contains("chưa rõ"))
            assertFalse(result.contains("chưa gửi"))
        }
    }

    @Test fun reportContainsOnlyScopedGeometryAndCurrentCause() {
        val effective = listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .54))
        val old = ProGestureDiagnostics.report(
            action,
            effective,
            profile,
            GestureFailure("game-not-focused", detail = "reason=game-not-focused")
        )
        val fresh = ProGestureDiagnostics.report(action, action.points, profile, GestureFailure("android-rejected"))
        assertTrue(old.contains("source=saved-override"))
        assertTrue(old.contains("effective=$effective"))
        assertTrue(old.contains("default=${action.points}"))
        assertTrue(old.contains(profile.key))
        assertFalse(fresh.contains("game-not-focused"))
        assertTrue(fresh.contains("source=default-or-identical"))
        assertTrue(
            ProGestureDiagnostics.report(
                action,
                effective,
                profile,
                GestureFailure("semantic-bounds", detail = "x".repeat(20000))
            ).length <=
                8192
        )
    }

    @Test fun invalidFineProbeOverrideCanBeEditedButCannotBeRunUncorrected() {
        val coarseAdjusted = listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .54))
        assertTrue(ProGestureSafety.valid(action, coarseAdjusted))
        val fine = action.copy(points = listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .762)))
        val fineAdjusted = listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .742))
        assertFalse(ProGestureSafety.canPrepare(fine, fineAdjusted, null, previewOnly = false))
        assertTrue(ProGestureSafety.canPrepare(fine, fineAdjusted, null, previewOnly = true))
        assertFalse(ProGestureSafety.canPrepare(fine, emptyList(), null, previewOnly = true))
        assertFalse(
            ProGestureSafety.canPrepare(fine, listOf(CalibrationPoint(Double.NaN, .7), CalibrationPoint(.7, .5)), null, previewOnly = true)
        )
        // Final editor dispatch uses valid(), not the preview gate.
        assertFalse(ProGestureSafety.valid(fine, fineAdjusted))
        assertTrue(ProGestureSafety.valid(fine, fine.points))
    }
}
