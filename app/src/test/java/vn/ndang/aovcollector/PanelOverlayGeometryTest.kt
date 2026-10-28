package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelOverlayGeometryTest {
    @Test fun compactWindowIncludesPaddingWithoutReducingTouchTarget() {
        assertEquals(56, PanelOverlayGeometry.COMPACT_SIZE_DP)
        assertEquals(48, PanelOverlayGeometry.COMPACT_SIZE_DP - 2 * PanelOverlayGeometry.COMPACT_PADDING_DP)
        assertEquals(48, PanelOverlayGeometry.HANDLE_SIZE_DP)
    }

    @Test fun fractionalDensityComposesPixelsWithoutShrinkingHandleOrChangingWindowShape() {
        for (density in listOf(1f, 1.5f, 2.625f, 2.75f, 3f)) {
            val handle = (PanelOverlayGeometry.HANDLE_SIZE_DP * density).toInt()
            val padding = (PanelOverlayGeometry.COMPACT_PADDING_DP * density).toInt()
            val composed = PanelOverlayGeometry.compactSize(handle, padding)
            assertEquals(handle, composed - 2 * padding)
            assertTrue(kotlin.math.abs(composed - (PanelOverlayGeometry.COMPACT_SIZE_DP * density).toInt()) <= 1)
            // The same composition also protects callers using rounded Android dimension pixels.
            val roundedHandle = (PanelOverlayGeometry.HANDLE_SIZE_DP * density + .5f).toInt()
            val roundedPadding = (PanelOverlayGeometry.COMPACT_PADDING_DP * density + .5f).toInt()
            assertEquals(roundedHandle, PanelOverlayGeometry.compactSize(roundedHandle, roundedPadding) - 2 * roundedPadding)
        }
    }

    @Test fun targetStaysAboveNavigationAndWithinPortraitLandscapeAndCutoutInsets() {
        for (density in listOf(1, 2, 3)) {
            for ((width, height) in listOf(360 to 800, 800 to 360)) {
                val target = PanelOverlayGeometry.trash(
                    width * density,
                    height * density,
                    24 * density,
                    30 * density,
                    10 * density,
                    24 * density,
                    64 * density,
                    80 * density
                )
                assertEquals(64 * density, target.width)
                assertEquals(80 * density, height * density - 24 * density - target.y - target.height)
                assertTrue(target.x >= 24 * density && target.x + target.width <= (width - 10) * density)
                assertTrue(target.y >= 30 * density)
            }
        }
    }

    @Test fun tinySafeAreaClampsGapAndTargetRatherThanEscapingDisplay() {
        val target = PanelOverlayGeometry.trash(120, 100, 20, 20, 20, 30, 64, 80)
        assertEquals(50, target.width)
        assertEquals(20, target.y)
        assertTrue(target.x >= 20 && target.x + target.width <= 100)
        assertEquals(70, target.y + target.height)
    }
}
