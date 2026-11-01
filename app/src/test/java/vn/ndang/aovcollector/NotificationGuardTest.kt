package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGuardTest {
    private val width = 2400
    private val height = 1080

    private fun window(pkg: String?, left: Int, top: Int, right: Int, bottom: Int, application: Boolean = false) =
        ForegroundWindow(pkg, application, false, false, false, WindowArea(left, top, right, bottom))

    private val game = window("com.garena.game.kgvn", 0, 0, width, height, application = true)

    /** The user's screenshot: "Usage limit reached" over the right half of the top of the game. */
    private val headsUp = window(NotificationGuard.SYSTEM_UI, 1200, 55, 2140, 205)

    @Test fun aHeadsUpBannerCountsButSystemBarsAndOtherAppsDoNot() {
        assertTrue(NotificationGuard.banner(listOf(game, headsUp), width, height))
        val statusBar = window(NotificationGuard.SYSTEM_UI, 0, 0, width, 32)
        val navigationBar = window(NotificationGuard.SYSTEM_UI, 2292, 0, width, height)
        val gameTools = window("com.samsung.android.game.gametools", 900, 0, 1500, 300)
        assertFalse(NotificationGuard.banner(listOf(game, statusBar, navigationBar, gameTools), width, height))
        // The shade pulled down covers everything: it holds the run too.
        assertTrue(NotificationGuard.banner(listOf(game, window(NotificationGuard.SYSTEM_UI, 0, 0, width, height)), width, height))
    }

    @Test fun nothingWaitsWithoutAFreshNotification() {
        val guard = NotificationGuard()
        assertFalse(guard.watching)
        assertEquals(NotificationGuard.Decision.Go, guard.decide(10_000, banner = true))
    }

    @Test fun aBannerSeenOnThisPhoneEndsTheWaitAsSoonAsItLeaves() {
        val guard = NotificationGuard()
        guard.posted(1_000)
        assertTrue(guard.decide(1_200, banner = true) is NotificationGuard.Decision.Wait)
        assertTrue(guard.bannersVisible)
        assertTrue(guard.decide(5_000, banner = true) is NotificationGuard.Decision.Wait)
        assertEquals(NotificationGuard.Decision.Go, guard.decide(5_400, banner = false))
        assertFalse(guard.watching)
        // The next notification: a short look for its banner sliding in, then on at once when none came.
        guard.posted(20_000)
        val first = guard.decide(20_100, banner = false)
        assertTrue(first is NotificationGuard.Decision.Wait && first.ms <= NotificationGuard.APPEAR_MS)
        assertEquals(NotificationGuard.Decision.Go, guard.decide(20_900, banner = false))
    }

    @Test fun withoutVisibleBannersTheWaitLastsAsLongAsABannerStays() {
        val guard = NotificationGuard(settleMs = 6_000)
        guard.posted(0)
        var now = 0L
        var waits = 0
        while (true) {
            val decision = guard.decide(now, banner = false)
            if (decision !is NotificationGuard.Decision.Wait) {
                assertEquals(NotificationGuard.Decision.Go, decision)
                break
            }
            assertTrue(decision.ms in 1..NotificationGuard.POLL_MS * 5)
            now += decision.ms
            waits++
        }
        assertEquals(6_000L, now)
        assertTrue(waits > 1)
    }

    @Test fun aBannerThatStaysUpStopsTheActionInsteadOfTouchingIt() {
        val guard = NotificationGuard(blockMs = 30_000)
        guard.posted(0)
        assertTrue(guard.decide(29_000, banner = true) is NotificationGuard.Decision.Wait)
        assertEquals(NotificationGuard.Decision.Blocked, guard.decide(30_000, banner = true))
        // Once the call is over, the run can go on.
        assertEquals(NotificationGuard.Decision.Go, guard.decide(45_000, banner = false))
    }
}
