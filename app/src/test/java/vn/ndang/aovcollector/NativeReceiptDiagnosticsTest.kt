package vn.ndang.aovcollector

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReceiptDiagnosticsTest {
    @Test fun reportsNumericMismatchEvidenceWithoutLocaleDependentDecimalSeparators() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            val report = NativePhotoReceipt.MatchResult(false, 128, 128, 18.03, .88, .91932, 3645, 972)
            val line = NativeReceiptDiagnostics.describe(report)
            assertEquals("128x128, match=false, rgb=18.0300, close=0.8800, corr=0.9193, coarse=3645, refined=972", line)
            assertFalse(line.contains("file:"))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test fun invalidOrMissingEvidenceIsNotPresentedAsZeroError() {
        val line = NativeReceiptDiagnostics.describe(
            NativePhotoReceipt.MatchResult(false, 0, 0, null, Double.NaN, Double.POSITIVE_INFINITY, 0, 0)
        )
        assertTrue(line.contains("rgb=unknown, close=unknown, corr=unknown"))
    }
}
