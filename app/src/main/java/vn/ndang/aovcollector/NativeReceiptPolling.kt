package vn.ndang.aovcollector

/** Sequential receipt reads only: waiting for Pictures never repeats an in-game save. */
internal object NativeReceiptPolling {
    fun <T> read(
        now: () -> Long,
        active: () -> Boolean,
        later: (Long, () -> Unit) -> Unit,
        read: ((T?) -> Unit) -> Unit,
        settled: (T) -> Boolean,
        timeoutMs: Long = 30_000L,
        intervalMs: Long = 2_000L,
        maxReads: Int = 16,
        then: (T?) -> Unit
    ) {
        require(timeoutMs > 0 && intervalMs > 0 && maxReads > 0)
        val started = now()
        var ended = false
        var inFlight = false
        var count = 0
        var last: T? = null

        fun current(): Boolean {
            if (ended) return false
            if (!active()) {
                ended = true
                return false
            }
            return true
        }

        fun remaining() = (timeoutMs - (now() - started).coerceAtLeast(0)).coerceAtLeast(0)

        fun finish() {
            if (!current()) return
            ended = true
            then(last)
        }

        fun take() {
            if (!current() || inFlight) return
            if (remaining() == 0L || count >= maxReads) return finish()
            count++
            inFlight = true
            var returned = false
            read callback@{ value ->
                if (returned || !current()) return@callback
                returned = true
                inFlight = false
                // A slow callback cannot turn evidence arriving after the deadline into success.
                if (remaining() == 0L) return@callback finish()
                last = value
                if (value != null && settled(value) || count >= maxReads) {
                    finish()
                } else {
                    later(minOf(intervalMs, remaining())) { take() }
                }
            }
        }

        if (!current()) return
        // A missing callback is bounded too; a late callback is ignored after this completion.
        later(timeoutMs) { finish() }
        take()
    }
}
