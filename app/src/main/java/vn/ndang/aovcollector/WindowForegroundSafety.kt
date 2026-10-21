package vn.ndang.aovcollector

/** Screen-space rectangles, kept independent of Android so ambiguous-window safety is unit-tested. */
internal data class WindowArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val valid: Boolean get() = right > left && bottom > top

    fun covers(other: WindowArea): Boolean = right > left &&
        bottom > top &&
        other.right > other.left &&
        other.bottom > other.top &&
        left <= other.left &&
        top <= other.top &&
        right >= other.right &&
        bottom >= other.bottom
}

/** Closed swept bounds, not just endpoints. Zero width/height is valid for a tap or straight swipe. */
internal data class StrokeArea(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val valid: Boolean get() = left.isFinite() &&
        top.isFinite() &&
        right.isFinite() &&
        bottom.isFinite() &&
        right >= left &&
        bottom >= top

    fun inside(window: WindowArea): Boolean = valid &&
        window.valid &&
        left >= window.left &&
        top >= window.top &&
        right < window.right &&
        bottom < window.bottom

    fun near(window: WindowArea, clearance: Double): Boolean = left <= window.right.toDouble() + clearance &&
        right >= window.left.toDouble() - clearance &&
        top <= window.bottom.toDouble() + clearance &&
        bottom >= window.top.toDouble() - clearance
}

internal data class ForegroundWindow(
    val packageName: String?,
    val application: Boolean,
    val accessibilityOverlay: Boolean,
    val focused: Boolean,
    val active: Boolean,
    val bounds: WindowArea
)

/** Never infer permission from a game window behind another app or from a previously seen package. */
internal object WindowForegroundSafety {
    // This recognizes a small geometric obstruction, NOT a trusted package. Paths still avoid it.
    private const val MAX_EDGE_WINDOWS = 2
    private const val SIDE_STRIP_DIVISOR = 8L
    private const val MAX_HANDLE_HEIGHT_DIVISOR = 3L
    private const val EDGE_ROUNDING_DIVISOR = 400L
    private const val CLEARANCE_DIVISOR = 50L

    private fun underlying(windows: List<ForegroundWindow>, ownPackage: String) = windows.filterNot {
        it.accessibilityOverlay && it.packageName == ownPackage
    }

    private fun selected(windows: List<ForegroundWindow>): ForegroundWindow? {
        val focused = windows.filter { it.focused }
        if (focused.isNotEmpty()) return focused.singleOrNull()
        return windows.filter { it.active }.singleOrNull()
    }

    fun packageInFront(windows: List<ForegroundWindow>, ownPackage: String): String? =
        selected(underlying(windows, ownPackage))?.packageName

    internal data class GameWindowCheck(val target: ForegroundWindow?, val reason: String)

    private fun passiveEdgeSliver(
        window: ForegroundWindow,
        ownPackage: String,
        gamePackage: String,
        display: WindowArea,
        usable: WindowArea
    ): Boolean {
        if (!window.application ||
            window.accessibilityOverlay ||
            window.focused ||
            window.active ||
            window.packageName == null ||
            window.packageName == ownPackage ||
            window.packageName == gamePackage ||
            !display.covers(window.bounds)
        ) {
            return false
        }
        val shortEdge = minOf(display.right.toLong() - display.left, display.bottom.toLong() - display.top)
        // OEM edge handles can straddle the cutout-side inset. Use the larger physical side inset,
        // symmetrically, but never more than one eighth of the short display edge. No inset, no exception.
        val sideInset = maxOf(usable.left.toLong() - display.left, display.right.toLong() - usable.right)
        val strip = minOf(sideInset, shortEdge / SIDE_STRIP_DIVISOR)
        val rounding = maxOf(1L, shortEdge / EDGE_ROUNDING_DIVISOR)
        val b = window.bounds
        if (strip <= 0 || b.bottom.toLong() - b.top > shortEdge / MAX_HANDLE_HEIGHT_DIVISOR) return false
        return (b.left.toLong() - display.left <= rounding && b.right.toLong() <= display.left.toLong() + strip) ||
            (display.right.toLong() - b.right <= rounding && b.left.toLong() >= display.right.toLong() - strip)
    }

    fun inspectGameWindow(
        windows: List<ForegroundWindow>,
        ownPackage: String,
        gamePackage: String,
        usableDisplay: WindowArea,
        display: WindowArea = usableDisplay,
        strokes: List<StrokeArea> = emptyList()
    ): GameWindowCheck {
        val visible = underlying(windows, ownPackage)
        val target = selected(visible)
        val applications = visible.filter { it.application }
        val otherApplications = applications.filter { it !== target }
        val edgeException = target?.application == true &&
            target.packageName == gamePackage &&
            target.focused &&
            target.bounds.covers(display) &&
            otherApplications.size in 1..MAX_EDGE_WINDOWS &&
            otherApplications.all { passiveEdgeSliver(it, ownPackage, gamePackage, display, usableDisplay) }
        val foreignWindows = visible.filter { it.packageName != gamePackage }
        val shortEdge = minOf(display.right.toLong() - display.left, display.bottom.toLong() - display.top)
        val clearance = maxOf(1L, (shortEdge + CLEARANCE_DIVISOR - 1) / CLEARANCE_DIVISOR).toDouble()
        // Full-display calibration still rejects split screen, floating apps, PiP and active sidebars.
        // Small passive edge windows are permitted only with known, collision-free actual strokes.
        val reason = when {
            !display.covers(usableDisplay) -> "foreign-window-geometry-unknown"
            applications.size != 1 && !edgeException -> "application-window-count=${applications.size}"
            applications.any { it.packageName != gamePackage } && !edgeException -> "foreign-or-unknown-application"
            visible.any { it.accessibilityOverlay } -> "foreign-or-unknown-accessibility-overlay"
            visible.any { !it.application && (it.active || it.focused) && it.packageName != gamePackage } -> "foreign-system-window-active"
            target == null -> "missing-or-ambiguous-foreground"
            !target.focused -> "game-not-focused"
            !target.application || target.packageName != gamePackage -> "focused-window-not-game-application"
            !target.bounds.covers(usableDisplay) -> "game-does-not-cover-usable-display"
            foreignWindows.any { !it.bounds.valid } -> "foreign-window-geometry-unknown"
            edgeException && strokes.isEmpty() -> "missing-gesture-path"
            strokes.any { !it.valid } -> "invalid-gesture-path"
            strokes.any { !it.inside(target.bounds) } -> "gesture-path-outside-game"
            strokes.any { stroke -> foreignWindows.any { stroke.near(it.bounds, clearance) } } -> "gesture-path-near-foreign-window"
            else -> "allowed"
        }
        return GameWindowCheck(target.takeIf { reason == "allowed" }, reason)
    }

    fun gameWindow(windows: List<ForegroundWindow>, ownPackage: String, gamePackage: String, usableDisplay: WindowArea): ForegroundWindow? =
        inspectGameWindow(windows, ownPackage, gamePackage, usableDisplay).target
}
