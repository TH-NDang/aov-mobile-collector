package vn.ndang.aovcollector

/**
 * Holds screenshots and touches while a notification may cover the game. Android says when one is posted, not when
 * its banner leaves, so a fresh notification starts a watch of the System UI windows on screen. On a phone where
 * the banner shows among them, the wait ends once it is gone; elsewhere it lasts [settleMs], about as long as a
 * banner stays. A banner still up after [blockMs] (a ringing call, a pulled-down shade) stops the action instead:
 * a touch there could open the notification or answer the call.
 */
internal class NotificationGuard(private val settleMs: Long = 6_000L, private val blockMs: Long = 30_000L) {
    sealed class Decision {
        object Go : Decision()

        class Wait(val ms: Long) : Decision()

        object Blocked : Decision()
    }

    private var postedAt: Long? = null

    /** A banner was seen among the windows on this phone, so its absence can be trusted. */
    var bannersVisible = false
        private set

    fun posted(now: Long) {
        postedAt = now
    }

    val watching: Boolean get() = postedAt != null

    fun decide(now: Long, banner: Boolean): Decision {
        val at = postedAt ?: return Decision.Go
        val since = now - at
        if (since < 0) return Decision.Go
        if (banner) {
            bannersVisible = true
            return if (since >= blockMs) Decision.Blocked else Decision.Wait(POLL_MS)
        }
        val wait = when {
            // A banner takes a moment to slide in before it can be seen.
            bannersVisible -> APPEAR_MS - since
            else -> settleMs - since
        }
        if (wait <= 0) {
            postedAt = null
            return Decision.Go
        }
        // Look again now and then: a banner seen later still ends the wait as soon as it leaves.
        return Decision.Wait(minOf(wait, POLL_MS * 5))
    }

    companion object {
        const val POLL_MS = 400L
        const val APPEAR_MS = 800L
        const val SYSTEM_UI = "com.android.systemui"

        /**
         * A System UI window big enough to hide part of the game near its top: a heads-up banner, a call or the
         * shade. The status bar, the navigation bar and edge handles are thin strips and do not count.
         */
        fun banner(windows: List<ForegroundWindow>, width: Int, height: Int): Boolean = width > 0 &&
            height > 0 &&
            windows.any { w ->
                val b = w.bounds
                w.packageName == SYSTEM_UI &&
                    !w.application &&
                    b.valid &&
                    (b.right - b.left) * 4 >= width &&
                    (b.bottom - b.top) * 100 >= height * 6 &&
                    b.top * 2 < height
            }
    }
}
