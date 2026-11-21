package vn.ndang.aovcollector

/** Main-thread lifecycle: only a proven undispatched journal may be cancelled. */
internal class NativeSaveDispatchGuard(private val cancelUndispatched: () -> Unit) {
    private var armed = false
    private var dispatched = false
    private var closed = false
    private var cleanupStarted = false

    fun armed() {
        armed = true
        cleanUp()
    }

    /** Call before handing the gesture to Android, never after its callback. */
    fun dispatch(): Boolean {
        if (closed) return false
        dispatched = true
        return true
    }

    fun close() {
        closed = true
        cleanUp()
    }

    private fun cleanUp() {
        if (closed && armed && !dispatched && !cleanupStarted) {
            cleanupStarted = true
            cancelUndispatched()
        }
    }
}
