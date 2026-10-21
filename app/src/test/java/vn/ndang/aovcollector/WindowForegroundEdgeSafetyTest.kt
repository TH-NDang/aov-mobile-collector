package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WindowForegroundEdgeSafetyTest {
    private val own = "collector"
    private val game = "game"
    private val display = WindowArea(0, 0, 2400, 1080)
    private val usable = WindowArea(76, 88, 2284, 1080)
    private val target = ForegroundWindow(game, true, false, true, true, display)
    private val editor = ForegroundWindow(own, false, true, false, false, usable)
    private val first = ForegroundWindow("oem.one", true, false, false, false, WindowArea(1, 67, 88, 331))
    private val second = ForegroundWindow("oem.two", true, false, false, false, WindowArea(1, 101, 66, 365))
    private val safe = StrokeArea(1200.0, 800.0, 1200.0, 800.0)

    private fun inspect(
        extras: List<ForegroundWindow> = listOf(first, second),
        strokes: List<StrokeArea> = listOf(safe),
        gameWindow: ForegroundWindow = target,
        safeDisplay: WindowArea = usable
    ) = WindowForegroundSafety.inspectGameWindow(listOf(editor, gameWindow) + extras, own, game, safeDisplay, display, strokes)

    @Test fun exactPhoneSnapshotAllowsOnlyTheFocusedFullDisplayGameAndClearPaths() {
        assertEquals(game, WindowForegroundSafety.packageInFront(listOf(editor, first, second, target), own))
        assertEquals(target, inspect().target)
        assertEquals(target, inspect(strokes = listOf(StrokeArea(800.0, 450.0, 1800.0, 900.0))).target)
        assertEquals(target, inspect(extras = listOf(second, first), strokes = listOf(safe, safe)).target)
    }

    @Test fun tapsInsideOnOrNearEitherForeignRectangleAreRejectedIncludingZeroAreaBounds() {
        for (window in listOf(first, second)) {
            val b = window.bounds
            for ((x, y) in listOf(
                b.left.toDouble() to b.top.toDouble(),
                b.right.toDouble() to b.bottom.toDouble(),
                (b.left + b.right) / 2.0 to (b.top + b.bottom) / 2.0,
                b.right + 10.0 to (b.top + b.bottom) / 2.0
            )) {
                val result = inspect(strokes = listOf(StrokeArea(x, y, x, y)))
                assertNull("$x,$y", result.target)
                assertEquals("gesture-path-near-foreign-window", result.reason)
            }
        }
        // ceil(1080/50) = 22 pixels; touching that clearance boundary is still unsafe.
        assertNull(inspect(strokes = listOf(StrokeArea(110.0, 200.0, 110.0, 200.0))).target)
        assertNotNull(inspect(strokes = listOf(StrokeArea(111.0, 200.0, 111.0, 200.0))).target)
    }

    @Test fun aCrossingSwipeIsRejectedEvenWhenBothEndpointsAreClear() {
        val start = StrokeArea(150.0, 20.0, 150.0, 20.0)
        val end = StrokeArea(0.0, 450.0, 0.0, 450.0)
        assertNotNull(inspect(strokes = listOf(start)).target)
        assertNotNull(inspect(strokes = listOf(end)).target)
        val swept = StrokeArea(0.0, 20.0, 150.0, 450.0)
        assertEquals("gesture-path-near-foreign-window", inspect(strokes = listOf(swept)).reason)
        assertNull(inspect(strokes = listOf(safe, swept)).target)
        assertNull(inspect(strokes = listOf(swept, safe)).target)
    }

    @Test fun theEdgeExceptionRequiresActualValidPaths() {
        assertEquals("missing-gesture-path", inspect(strokes = emptyList()).reason)
        for (invalid in listOf(
            StrokeArea(Double.NaN, 100.0, 200.0, 200.0),
            StrokeArea(100.0, Double.NEGATIVE_INFINITY, 200.0, 200.0),
            StrokeArea(100.0, 100.0, Double.POSITIVE_INFINITY, 200.0),
            StrokeArea(200.0, 100.0, 100.0, 200.0),
            StrokeArea(100.0, 200.0, 200.0, 100.0)
        )) {
            assertNull(inspect(strokes = listOf(invalid)).target)
            assertEquals("invalid-gesture-path", inspect(strokes = listOf(invalid)).reason)
        }
        assertNull(inspect(strokes = listOf(StrokeArea(-1.0, 500.0, -1.0, 500.0))).target)
        assertNull(inspect(strokes = listOf(StrokeArea(2400.0, 500.0, 2400.0, 500.0))).target)
    }

    @Test fun countBandHeightAndAnchorLimitsAreClosedAndFailImmediatelyBeyondTheLimit() {
        assertNotNull(inspect(extras = listOf(first.copy(bounds = WindowArea(0, 67, 116, 427)))).target)
        assertNotNull(inspect(extras = listOf(first.copy(bounds = WindowArea(2, 67, 88, 331)))).target)
        for (bounds in listOf(
            WindowArea(0, 67, 117, 331),
            WindowArea(1, 67, 88, 428),
            WindowArea(3, 67, 88, 331),
            WindowArea(150, 67, 210, 331),
            WindowArea(0, 0, 88, 1080),
            WindowArea(0, 0, 1200, 1080),
            WindowArea(0, 0, 500, 40)
        )) {
            assertNull("$bounds", inspect(extras = listOf(first.copy(bounds = bounds))).target)
        }
        assertNull(inspect(extras = listOf(first, second, first.copy(packageName = "oem.three"))).target)
        assertNull(inspect(safeDisplay = WindowArea(0, 88, 2400, 1080)).target)
    }

    @Test fun edgeSizeNeverExcusesFocusActivityUnknownOwnershipOrOwnAndSameGameWindows() {
        for (unsafe in listOf(
            first.copy(active = true),
            first.copy(focused = true),
            first.copy(packageName = null),
            first.copy(packageName = own),
            first.copy(packageName = game),
            first.copy(accessibilityOverlay = true)
        )) {
            assertNull("$unsafe", inspect(extras = listOf(unsafe)).target)
        }
        assertNull(inspect(gameWindow = target.copy(focused = false)).target)
        assertNull(inspect(gameWindow = target.copy(bounds = usable)).target)
        assertNull(inspect(gameWindow = target.copy(bounds = WindowArea(0, 0, 1200, 1080))).target)
    }

    @Test fun rightEdgeSliversRequireTheSameClearanceAndAllForeignWindowsAreConsidered() {
        val right = first.copy(bounds = WindowArea(2320, 67, 2399, 331))
        assertNotNull(inspect(extras = listOf(right)).target)
        assertNull(inspect(extras = listOf(right), strokes = listOf(StrokeArea(2310.0, 200.0, 2310.0, 200.0))).target)
        val passiveSystem = first.copy(application = false, bounds = WindowArea(1180, 780, 1220, 820))
        assertNull(inspect(extras = listOf(first, passiveSystem)).target)
        assertNull(inspect(extras = listOf(first, passiveSystem.copy(active = true))).target)
    }

    @Test fun invalidOrUnknownForeignGeometryNeverAuthorizesTheException() {
        for (bad in listOf(WindowArea(0, 0, 0, 200), WindowArea(100, 200, 20, 100), WindowArea(-1, 67, 88, 331))) {
            assertNull(inspect(extras = listOf(first.copy(bounds = bad))).target)
        }
        val unknownSystemGeometry = first.copy(application = false, bounds = WindowArea(0, 0, 0, 0))
        assertEquals("foreign-window-geometry-unknown", inspect(extras = listOf(unknownSystemGeometry)).reason)
    }

    @Test fun sideStripHasAnAbsoluteShortEdgeCapEvenWithOversizedReportedInsets() {
        val hugeInsets = WindowArea(500, 88, 1900, 1080)
        assertNotNull(inspect(extras = listOf(first.copy(bounds = WindowArea(0, 67, 135, 331))), safeDisplay = hugeInsets).target)
        assertNull(inspect(extras = listOf(first.copy(bounds = WindowArea(0, 67, 136, 331))), safeDisplay = hugeInsets).target)
    }

    @Test fun aShiftedPhysicalDisplayUsesGlobalCoordinatesForEdgesAndPaths() {
        fun shift(area: WindowArea) = WindowArea(area.left + 100, area.top + 200, area.right + 100, area.bottom + 200)
        val shiftedGame = target.copy(bounds = shift(display))
        val shiftedEdge = first.copy(bounds = shift(first.bounds))

        fun check(stroke: StrokeArea) = WindowForegroundSafety.inspectGameWindow(
            listOf(shiftedGame, shiftedEdge),
            own,
            game,
            shift(usable),
            shift(display),
            listOf(stroke)
        )
        assertEquals(shiftedGame, check(StrokeArea(1300.0, 1000.0, 1300.0, 1000.0)).target)
        assertNull(check(StrokeArea(188.0, 400.0, 188.0, 400.0)).target)
    }
}
