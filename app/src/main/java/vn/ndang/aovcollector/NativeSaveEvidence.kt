package vn.ndang.aovcollector

/** Evidence from a fresh, successfully OCR'd screenshot, before or after a native Save Photo. */
object NativeSaveEvidence {
    data class Reference(val detailSignature: IntArray, val iconSignature: String?)

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
