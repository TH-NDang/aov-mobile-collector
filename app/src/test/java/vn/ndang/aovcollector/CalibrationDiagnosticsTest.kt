package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationDiagnosticsTest {
    @Test fun everyCurrentRefusalHasAStableCodeAndSpecificActionHint() {
        val reasons = mapOf(
            "teaching-ended" to "T01",
            "generation-changed" to "T02",
            "session-changed" to "T03",
            "display-profile-changed" to "T04",
            "gesture-in-flight" to "T05",
            "invalid-points" to "T06",
            "missing-gesture-path" to "T07",
            "invalid-gesture-path" to "T08",
            "application-window-count=0" to "W00",
            "application-window-count=2" to "W01",
            "application-window-count=12" to "W01",
            "foreign-or-unknown-application" to "W02",
            "foreign-or-unknown-accessibility-overlay" to "W03",
            "foreign-system-window-active" to "W04",
            "missing-or-ambiguous-foreground" to "W05",
            "game-not-focused" to "W06",
            "focused-window-not-game-application" to "W07",
            "game-does-not-cover-usable-display" to "W08",
            "gesture-path-outside-game" to "W09",
            "window-query-exception=SecurityException" to "W10",
            "window-query-exception=IllegalStateException" to "W10",
            "gesture-path-near-foreign-window" to "W11",
            "foreign-window-geometry-unknown" to "W12"
        )
        for ((reason, code) in reasons) {
            val refusal = CalibrationDiagnostics.refusal(reason, "result=window-safety-denied", "reason=$reason")
            assertEquals(reason, code, refusal.code)
            assertTrue(reason, refusal.message.length in 20..100)
            assertFalse(reason, refusal.message.contains("Không chạy được; kiểm tra màn hình game"))
            assertTrue(reason, refusal.report.startsWith("AOV teaching refusal $code\n${refusal.message}\n"))
            assertTrue(reason, refusal.report.contains("result=window-safety-denied"))
            assertTrue(reason, refusal.report.contains("reason=$reason"))
        }
    }

    @Test fun unknownAndUncheckedReasonsStayExplicitAndDoNotEchoUntrustedReasonText() {
        for (reason in listOf("not-checked", "window-safety-denied", "unknown private label\nuser@example.invalid")) {
            val refusal = CalibrationDiagnostics.refusal(reason, "result=refused", "windowCount=0")
            assertEquals("T99", refusal.code)
            assertTrue(refusal.message.contains("Chép lý do lỗi"))
            assertFalse(refusal.report.contains(reason))
            assertFalse(refusal.report.contains("user@example.invalid"))
        }
    }

    @Test fun copiedReportIsBoundedEvenForUnexpectedlyLargeDiagnosticInputs() {
        val refusal = CalibrationDiagnostics.refusal("display-profile-changed", "x".repeat(20_000), "y".repeat(20_000))
        assertEquals(CalibrationDiagnostics.MAX_REPORT_CHARS, refusal.report.length)
        assertTrue(refusal.report.startsWith("AOV teaching refusal T04\n"))
        assertTrue(refusal.report.contains(refusal.message))
    }

    @Test fun windowReportClassifiesOwnersWithoutLeakingPackageNamesOrScreenContent() {
        val own = "private.collector.package"
        val game = "private.game.package"
        val foreign = "private.owner@example.invalid\naccount-label"
        val windows = listOf(game, own, null, foreign).map { owner ->
            ForegroundWindow(owner, true, false, true, false, WindowArea(44, 0, 1876, 1080))
        }
        val report = CalibrationDiagnostics.windows(windows, own, game)
        assertTrue(report.startsWith("windowCount=4; windows="))
        for (owner in listOf("GAME", "COLLECTOR", "UNKNOWN", "OTHER")) assertTrue(report.contains("[$owner app="))
        for (privateText in listOf(own, game, foreign, "account-label")) assertFalse(report.contains(privateText))
        assertTrue(report.contains("WindowArea(left=44, top=0, right=1876, bottom=1080)"))
        assertTrue(report.contains("overlay=false focused=true active=false"))
    }

    @Test fun oversizedWindowInventoryIsExplicitlyTruncatedButKeepsTotalCount() {
        val window = ForegroundWindow("private.package", false, true, false, false, WindowArea(0, 10, 12, 50))
        val report = CalibrationDiagnostics.windows(List(1_000) { window }, "collector", "game")
        assertTrue(report.startsWith("windowCount=1000; windows="))
        assertEquals(24, Regex("\\[OTHER app=").findAll(report).count())
        assertTrue(report.endsWith("; remaining-windows-omitted"))
        assertFalse(report.contains("private.package"))
        val refusal = CalibrationDiagnostics.refusal("foreign-or-unknown-accessibility-overlay", "result=refused", report)
        assertTrue(refusal.report.length <= CalibrationDiagnostics.MAX_REPORT_CHARS)
    }
}
