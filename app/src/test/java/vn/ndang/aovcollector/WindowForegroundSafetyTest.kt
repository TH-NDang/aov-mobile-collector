package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowForegroundSafetyTest {
    private val own = "vn.ndang.aovcollector"
    private val game = "com.garena.game.kgvn"
    private val other = "synthetic.other"
    private val display = WindowArea(0, 0, 1920, 1080)
    private val usable = WindowArea(10, 24, 1872, 1080)
    private val fullGame = window(game)

    private fun window(
        name: String?,
        application: Boolean = true,
        overlay: Boolean = false,
        focused: Boolean = true,
        active: Boolean = true,
        bounds: WindowArea = display
    ) = ForegroundWindow(name, application, overlay, focused, active, bounds)

    private fun permitted(vararg windows: ForegroundWindow): ForegroundWindow? =
        WindowForegroundSafety.gameWindow(windows.toList(), own, game, usable)

    private fun front(vararg windows: ForegroundWindow): String? = WindowForegroundSafety.packageInFront(windows.toList(), own)

    @Test fun focusedFullDisplayGameAndPassiveNavigationAreAllowed() {
        val navigation = window(
            "com.android.systemui",
            application = false,
            focused = false,
            active = false,
            bounds = WindowArea(1872, 0, 1920, 1080)
        )
        assertEquals(game, front(navigation, fullGame))
        assertEquals(fullGame, permitted(navigation, fullGame))
    }

    @Test fun passiveSystemEdgeHandleDoesNotChangeFullDisplayGamePermission() {
        val handle = window(
            "synthetic.oem.sidebar",
            application = false,
            focused = false,
            active = false,
            bounds = WindowArea(0, 120, 14, 280)
        )
        assertEquals(fullGame, permitted(handle, fullGame))
        assertNull(permitted(handle.copy(active = true), fullGame))
        assertNull(permitted(handle.copy(accessibilityOverlay = true), fullGame))
    }

    @Test fun unexplainedNarrowSideBandsRemainRejectedRatherThanGuessedSafe() {
        val letterboxed = fullGame.copy(bounds = WindowArea(44, 0, 1876, 1080))
        assertEquals(game, front(letterboxed))
        val check = WindowForegroundSafety.inspectGameWindow(listOf(letterboxed), own, game, usable)
        assertNull(check.target)
        assertEquals("game-does-not-cover-usable-display", check.reason)
    }

    @Test fun ownFocusedActiveOverlayNeverHidesTheUnderlyingGame() {
        val editor = window(own, application = false, overlay = true)
        assertEquals(game, front(editor, fullGame))
        assertEquals(fullGame, permitted(editor, fullGame))
        assertEquals(fullGame, permitted(fullGame, editor))
        assertNull(front(editor))
        assertNull(permitted(editor))
    }

    @Test fun anActualCollectorActivityIsNotAnIgnorableOverlay() {
        val activity = window(own)
        assertEquals(own, front(activity, fullGame.copy(focused = false, active = false)))
        assertNull(permitted(activity))
        assertNull(permitted(fullGame, activity.copy(focused = false, active = false)))
    }

    @Test fun focusedForeignApplicationWinsOverHigherOrderedGameWindow() {
        val staleGame = fullGame.copy(focused = false, active = true)
        val foreign = window(other)
        assertEquals(other, front(staleGame, foreign))
        assertEquals(other, front(foreign, staleGame))
        assertNull(permitted(staleGame, foreign))
    }

    @Test fun multipleFocusedWindowsCannotBeDisambiguatedByTheirOrderOrPackage() {
        assertNull(front(fullGame, window(other)))
        assertNull(front(window(other), fullGame))
        assertNull(front(fullGame, fullGame.copy(active = false)))
        assertNull(permitted(fullGame, fullGame.copy(active = false)))
    }

    @Test fun activeOnlyGameMayIdentifyForegroundButNeverAuthorizeInput() {
        val activeOnly = fullGame.copy(focused = false)
        assertEquals(game, front(activeOnly))
        assertNull(permitted(activeOnly))
        assertNull(front(activeOnly.copy(active = false)))
        assertNull(permitted(activeOnly.copy(active = false)))
    }

    @Test fun missingOrAmbiguousActiveWindowsNeverReuseAFormerGamePackage() {
        assertEquals(game, front(fullGame))
        assertNull(front())
        assertNull(permitted())
        val activeOnly = fullGame.copy(focused = false)
        assertNull(front(activeOnly, window(other, focused = false)))
        assertNull(permitted(activeOnly, window(other, focused = false)))
    }

    @Test fun nullPackagesFailClosedIncludingAnUnknownPassiveApplication() {
        assertNull(front(window(null)))
        assertNull(permitted(window(null)))
        assertNull(permitted(fullGame, window(null, focused = false, active = false)))
    }

    @Test fun splitScreenAndAnyOtherVisibleApplicationPreventFullDisplayDispatch() {
        val left = fullGame.copy(bounds = WindowArea(0, 0, 960, 1080))
        val right = window(other, focused = false, active = false, bounds = WindowArea(960, 0, 1920, 1080))
        assertEquals(game, front(left, right))
        assertNull(permitted(left, right))
        assertNull(permitted(fullGame, right))
    }

    @Test fun multipleApplicationWindowsRemainAmbiguousEvenFromTheSameGamePackage() {
        val secondGame = fullGame.copy(focused = false, active = false, bounds = WindowArea(1400, 600, 1900, 1000))
        assertEquals(game, front(fullGame, secondGame))
        assertNull(permitted(fullGame, secondGame))
    }

    @Test fun floatingAndPictureInPictureGameWindowsCannotReceiveFullDisplayCoordinates() {
        for (bounds in listOf(WindowArea(200, 100, 1700, 900), WindowArea(1400, 700, 1900, 1000))) {
            val floating = fullGame.copy(bounds = bounds)
            assertEquals(game, front(floating))
            assertNull(permitted(floating))
        }
    }

    @Test fun foreignAccessibilityOverlayIsRejectedEvenWithoutFocusOrActivity() {
        val foreign = window(other, application = false, overlay = true, focused = false, active = false)
        assertEquals(game, front(fullGame, foreign))
        assertNull(permitted(fullGame, foreign))
        assertNull(permitted(fullGame, foreign.copy(packageName = null)))
    }

    @Test fun activeOrFocusedForeignSystemWindowBlocksGameInput() {
        val system = window("com.android.systemui", application = false)
        assertNull(permitted(fullGame, system))
        assertNull(permitted(fullGame, system.copy(focused = false)))
        assertNull(permitted(fullGame.copy(focused = false), system.copy(active = false)))
        assertEquals("com.android.systemui", front(fullGame.copy(focused = false), system))
    }

    @Test fun onlyAnApplicationWindowCanAuthorizeGameInput() {
        val systemNamedGame = fullGame.copy(application = false)
        assertEquals(game, front(systemNamedGame))
        assertNull(permitted(systemNamedGame))
    }

    @Test fun gameMustCoverEveryUsableDisplayEdgeNotJustHaveTheSameDimensions() {
        assertEquals(fullGame.copy(bounds = usable), permitted(fullGame.copy(bounds = usable)))
        for (bounds in listOf(
            usable.copy(left = usable.left + 1),
            usable.copy(top = usable.top + 1),
            usable.copy(right = usable.right - 1),
            usable.copy(bottom = usable.bottom - 1),
            WindowArea(11, 24, 1873, 1080),
            WindowArea(10, 25, 1872, 1081)
        )) {
            assertFalse(bounds.covers(usable))
            assertNull(permitted(fullGame.copy(bounds = bounds)))
        }
        assertTrue(display.covers(usable))
    }

    @Test fun invalidWindowOrDisplayRectanglesNeverAuthorizeInput() {
        for (invalid in listOf(WindowArea(0, 0, 0, 1080), WindowArea(0, 0, 1920, 0), WindowArea(100, 100, 0, 0))) {
            assertFalse(invalid.covers(usable))
            assertFalse(display.covers(invalid))
            assertNull(permitted(fullGame.copy(bounds = invalid)))
            assertNull(WindowForegroundSafety.gameWindow(listOf(fullGame), own, game, invalid))
        }
    }
}
