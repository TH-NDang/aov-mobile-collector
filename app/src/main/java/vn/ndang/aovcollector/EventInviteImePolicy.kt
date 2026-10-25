package vn.ndang.aovcollector

/**
 * Pure policy for selecting the Android keyboard's fullscreen extract editor.
 *
 * In landscape Unity opens an INPUT_METHOD window containing an editable white
 * bar and a separate "OK" button. The game popup behind it also has a yellow
 * "Xác nhận" button: never mistake one for the other.
 *
 * The actual AccessibilityService adapter performs all actions; this class only
 * evaluates trusted window and bounded node metadata.
 */
internal object EventInviteImePolicy {
    data class Node(
        val index: Int,
        val label: String,
        val className: String,
        val editable: Boolean,
        val password: Boolean,
        val focused: Boolean,
        val visible: Boolean,
        val enabled: Boolean,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double
    ) {
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        val width get() = right - left
    }

    fun keyboardTrusted(
        selectedImePackage: String?,
        actualImePackage: String?,
        gameWindowBehind: Boolean,
        focusedForeignApp: Boolean
    ): Boolean = !selectedImePackage.isNullOrBlank() &&
        selectedImePackage == actualImePackage &&
        gameWindowBehind &&
        !focusedForeignApp

    fun editor(nodes: List<Node>): Int? {
        val possible = nodes.filter {
            it.visible &&
                it.enabled &&
                it.editable &&
                !it.password &&
                it.centerY in 0.08..0.51 &&
                it.centerX in 0.06..0.89 &&
                it.width > .12
        }
        val focused = possible.filter { it.focused }
        val extracts = possible.filter { it.className.contains("ExtractEditText", ignoreCase = true) }
        return when {
            focused.size == 1 -> focused.single().index
            extracts.size == 1 -> extracts.single().index
            possible.size == 1 -> possible.single().index
            else -> null
        }
    }

    /** Only the right-hand IME action, never the game's underlying confirm button. */
    fun okButton(nodes: List<Node>): Int? {
        val possible = nodes.filter {
            it.visible &&
                it.enabled &&
                !it.editable &&
                it.centerX in 0.77..0.985 &&
                it.centerY in 0.08..0.51 &&
                normalize(it.label) in setOf("ok", "done", "xong", "hoantat")
        }
        // A text node and its clickable parent may both advertise "OK". As long
        // as all refer to the same button position, prefer the narrowest node.
        if (possible.isEmpty()) return null
        val origin = possible.first()
        if (possible.any {
                kotlin.math.abs(it.centerX - origin.centerX) > .035 ||
                    kotlin.math.abs(it.centerY - origin.centerY) > .045
            }
        ) {
            return null
        }
        return possible.minByOrNull { it.width }?.index
    }

    fun gameReady(imeStillVisible: Boolean, focusedGame: Boolean, verifiedRenderedCode: Boolean): Boolean =
        !imeStillVisible && focusedGame && verifiedRenderedCode

    private fun normalize(text: String): String = GameScreens.plain(text.trim()).lowercase(java.util.Locale.ROOT)
}
