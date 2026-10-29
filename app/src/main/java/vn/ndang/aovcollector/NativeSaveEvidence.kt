package vn.ndang.aovcollector

import kotlin.math.roundToInt

/** Evidence from a fresh, successfully OCR'd screenshot, before or after a native Save Photo. */
object NativeSaveEvidence {
    data class Reference(val detailSignature: IntArray, val iconSignature: String?, val operationKey: String? = null)

    /** Page + probe slot, not artwork hash. Never repeat a dispatched save after a hash changes. */
    fun operationKey(hero: String, page: Int, x: Double, y: Double): String {
        // The .50/.55 probes can be seen alone or merged at .525: they are ONE icon.
        val slot = if (y in 0.49..0.56) "upper" else "y${(y * 20).roundToInt()}"
        return "native-slot-v1:chieu-quan-co:$hero:page-$page:x${(x * 20).roundToInt()}:$slot"
    }

    enum class Scene { PROMPT, DETAIL, PAGE, UNKNOWN }

    /** An unchanged current drawer is usable even when its title has scrolled out of view.
     * A contradictory readable title always vetoes the visual fallback.
     */
    fun preservedDetail(expected: String, title: String?, before: IntArray?, after: IntArray): Boolean {
        if (title != null) return ChessWiki.safeName(title) == ChessWiki.safeName(expected)
        return before != null && before.size >= 512 && !ChessWiki.changed(before, after)
    }

    /** Be conservative with partial OCR: background encyclopedia text must not hide a Save prompt. */
    fun hasPromptText(plainText: String) = listOf("save", "photo", "sure", "cancel").any { it in plainText }

    /** [detailVerified] means a readable title, matching the owning hero when a skill panel is open. */
    fun scene(plainText: String, modal: Boolean, detailVerified: Boolean): Scene = when {
        plainText.isBlank() -> Scene.UNKNOWN
        hasPromptText(plainText) -> Scene.PROMPT
        modal -> if (detailVerified) Scene.DETAIL else Scene.UNKNOWN
        "bachkhoa" in plainText && ("colienquan" in plainText || "tatcaquanco" in plainText) -> Scene.PAGE
        else -> Scene.UNKNOWN
    }
}
