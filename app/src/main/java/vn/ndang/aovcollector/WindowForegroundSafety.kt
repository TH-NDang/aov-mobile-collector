package vn.ndang.aovcollector

/** Screen-space rectangles, kept independent of Android so ambiguous-window safety is unit-tested. */
internal data class WindowArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun covers(other: WindowArea): Boolean = right > left &&
        bottom > top &&
        other.right > other.left &&
        other.bottom > other.top &&
        left <= other.left &&
        top <= other.top &&
        right >= other.right &&
        bottom >= other.bottom
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

    fun gameWindow(windows: List<ForegroundWindow>, ownPackage: String, gamePackage: String, usableDisplay: WindowArea): ForegroundWindow? {
        val visible = underlying(windows, ownPackage)
        // Full-display calibration deliberately does not support split screen, floating apps or PiP.
        if (visible.count { it.application } != 1) return null
        if (visible.any { it.application && it.packageName != gamePackage }) return null
        if (visible.any { it.accessibilityOverlay }) return null
        if (visible.any { !it.application && (it.active || it.focused) && it.packageName != gamePackage }) return null
        val target = selected(visible) ?: return null
        return target.takeIf { it.focused && it.application && it.packageName == gamePackage && it.bounds.covers(usableDisplay) }
    }
}
