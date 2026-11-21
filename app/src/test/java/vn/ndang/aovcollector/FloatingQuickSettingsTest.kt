package vn.ndang.aovcollector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingQuickSettingsTest {
    @Test fun entryCanChangeOnlyBeforeANewRun() {
        assertTrue(FloatingQuickSettings.entryEditable(false, false, false, false))
        assertFalse(FloatingQuickSettings.entryEditable(true, false, false, false))
        assertFalse(FloatingQuickSettings.entryEditable(false, true, false, false))
        assertFalse(FloatingQuickSettings.entryEditable(false, false, true, false))
        assertFalse(FloatingQuickSettings.entryEditable(false, false, false, true))
    }

    @Test fun everyBusyCombinationKeepsTheSnapshotStable() {
        for (mask in 1..15) {
            assertFalse(FloatingQuickSettings.entryEditable(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0))
        }
    }
}
