package vn.ndang.aovcollector

/** A stable drawer shell is insufficient while its illustrated skill is still loading. */
internal class ChessDetailReadiness(
    private val started: Long,
    private val requiresIcon: Boolean,
    private val sameDetail: (IntArray, IntArray) -> Boolean,
    private val sameIcon: (String, String) -> Boolean,
    private val timeoutMs: Long = 30_000L,
    private val maxReads: Int = 40
) {
    enum class Decision { WAIT, READY, TIMEOUT, CANCELLED }

    private var previous: IntArray? = null
    private var icons = emptyList<String>()
    private var stableSince = started
    private var stableFrames = 0
    private var reads = 0
    private var terminal: Decision? = null

    fun observe(now: Long, active: Boolean, modal: Boolean, detail: IntArray, renderedIcons: List<String>): Decision {
        terminal?.let { return it }
        if (!active) return Decision.CANCELLED.also { terminal = it }
        reads++
        if (now - started >= timeoutMs || reads > maxReads) return Decision.TIMEOUT.also { terminal = it }
        val eligible = modal && detail.isNotEmpty() && (!requiresIcon || renderedIcons.isNotEmpty())
        val stable = eligible &&
            previous?.let { sameDetail(it, detail) } == true &&
            icons.size == renderedIcons.size &&
            icons.indices.all { sameIcon(icons[it], renderedIcons[it]) }
        if (!stable) {
            stableFrames = if (eligible) 1 else 0
            stableSince = now
            previous = if (eligible) detail.copyOf() else null
            icons = renderedIcons.toList()
        } else {
            stableFrames++
        }
        return if (stableFrames >= 3 && now - stableSince >= 1_600L) {
            Decision.READY.also { terminal = it }
        } else {
            Decision.WAIT
        }
    }
}
