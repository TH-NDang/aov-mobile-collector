package vn.ndang.aovcollector

/**
 * Guard one native Save Photo transaction without confusing slow Android OCR,
 * screenshot throttling, SAF/Pictures indexing, and SHA-256 receipt verification
 * with a hung step.
 *
 * A 15-second TOTAL watchdog is too short for a completed chain of async Android
 * calls. Instead every *observed completion* gives a fresh idle budget, while
 * the hard limit prevents endless retries on unchanged game screens.
 *
 * This class is pure Kotlin: unit tests use a controlled clock, not sleeps.
 */
internal class NativeSaveWatchdog(
    private val nowMs: () -> Long,
    private val maxTotalMs: Long = 150_000L,
    private val normalIdleMs: Long = 36_000L,
    private val receiptIdleMs: Long = 70_000L
) {
    enum class Stage {
        OPENING,
        LONG_PRESS,
        PROMPT_READ,
        SURE_PRESSED,
        VERIFYING_GAME,
        INDEXING_PICTURES
    }

    enum class Expiration {
        IDLE,
        TOTAL
    }

    private val started = nowMs()
    private var lastAdvance = started
    var stage: Stage = Stage.OPENING
        private set

    val elapsedMs: Long
        get() = (nowMs() - started).coerceAtLeast(0L)

    val idleMs: Long
        get() = (nowMs() - lastAdvance).coerceAtLeast(0L)

    /**
     * Call only after a gesture, screenshot/OCR callback, file verification,
     * or similar observable progress. Posting another timer does not refresh it.
     */
    fun advanced(next: Stage) {
        stage = next
        lastAdvance = nowMs()
    }

    fun expired(): Expiration? {
        if (elapsedMs >= maxTotalMs) return Expiration.TOTAL
        val idleLimit = if (stage == Stage.INDEXING_PICTURES) receiptIdleMs else normalIdleMs
        return if (idleMs >= idleLimit) Expiration.IDLE else null
    }

    fun explain(reason: Expiration): String = when (reason) {
        Expiration.IDLE ->
            "Không có tiến triển trong thao tác ảnh gốc; giai đoạn $stage, chờ ${idleMs / 1000}s. " +
                "Không tự bấm lưu lại; đã giữ tiến độ, cần kiểm tra Save Photo/Pictures trước khi thử lại."
        Expiration.TOTAL ->
            "Thao tác ảnh gốc vượt giới hạn kiểm tra ${maxTotalMs / 1000}s; " +
                "giai đoạn $stage. Không tự bấm lưu lại để tránh trùng ảnh."
    }
}
