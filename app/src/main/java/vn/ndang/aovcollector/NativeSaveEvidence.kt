package vn.ndang.aovcollector

/** Evidence from a fresh, successfully OCR'd screenshot, before or after a native Save Photo. */
internal object NativeSaveEvidence {
    enum class Scene { PROMPT, DETAIL, PAGE, UNKNOWN }

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
