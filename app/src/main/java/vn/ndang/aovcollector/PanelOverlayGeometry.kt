package vn.ndang.aovcollector

internal object PanelOverlayGeometry {
    const val COMPACT_SIZE_DP = 56
    const val COMPACT_PADDING_DP = 4
    const val HANDLE_SIZE_DP = 48
    const val TRASH_SIZE_DP = 64
    const val TRASH_BOTTOM_GAP_DP = 80

    // Compose converted pixels: converting 56 dp separately can round differently from its children.
    fun compactSize(handlePixels: Int, paddingPixels: Int) = handlePixels + 2 * paddingPixels

    data class Target(val x: Int, val y: Int, val width: Int, val height: Int)

    fun trash(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int, size: Int, gap: Int): Target {
        val safeWidth = (width - left - right).coerceAtLeast(1)
        val safeHeight = (height - top - bottom).coerceAtLeast(1)
        val edge = size.coerceAtLeast(1).coerceAtMost(minOf(safeWidth, safeHeight))
        return Target(
            left + (safeWidth - edge) / 2,
            (height - bottom - edge - gap.coerceAtLeast(0)).coerceIn(top, top + safeHeight - edge),
            edge,
            edge
        )
    }
}
