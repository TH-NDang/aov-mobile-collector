package vn.ndang.aovcollector

/**
 * Short encyclopedia exports may proceed as soon as a fresh frame proves the next state.
 * A negative/ambiguous early frame is never evidence of absence or failure: take one more
 * fresh frame after the original wait. Screenshot throttling and OCR remain in the caller.
 */
internal object NativeSaveReadiness {
    fun enabled(group: String, preserveDetail: Boolean, fast: Boolean) = fast &&
        !preserveDetail &&
        (group in setOf("phe", "nghe", "trang-bi") || ChessBlessings.isGroup(group))

    /** At most two sequential reads; no polling loop and no overlapping OCR work. */
    fun <T> read(
        early: Boolean,
        waitMs: Long,
        now: () -> Long,
        active: () -> Boolean,
        later: (Long, () -> Unit) -> Unit,
        read: ((T?) -> Unit) -> Unit,
        ready: (T) -> Boolean,
        then: (T?) -> Unit
    ) {
        val started = now()
        var ended = false

        fun take(finalAttempt: Boolean) {
            if (ended || !active()) return
            var returned = false
            read callback@{ value ->
                // Ignore a late/duplicated callback after Stop, timeout or completion.
                if (returned || ended || !active()) return@callback
                returned = true
                if (finalAttempt || (value != null && ready(value))) {
                    ended = true
                    then(value)
                } else {
                    val remaining = (waitMs - (now() - started)).coerceAtLeast(0L)
                    later(remaining) { take(true) }
                }
            }
        }
        if (early) take(false) else later(waitMs) { take(true) }
    }

    /** One fresh verification read, without repeating the long-press or confirmation tap. */
    fun <T> verify(
        initial: T?,
        active: () -> Boolean,
        later: (Long, () -> Unit) -> Unit,
        read: ((T?) -> Unit) -> Unit,
        ready: (T) -> Boolean,
        then: (T?) -> Unit
    ) {
        if (!active()) return
        if (initial != null && ready(initial)) {
            then(initial)
            return
        }
        later(700L) {
            if (active()) {
                var returned = false
                read { value ->
                    if (!returned && active()) {
                        returned = true
                        then(value)
                    }
                }
            }
        }
    }
}
