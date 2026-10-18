package vn.ndang.aovcollector

/** Main-thread leases keep overlays hidden until every capture/gesture in a short transaction ends. */
internal class CaptureOverlayShield(private val visibility:(Boolean)->Unit) {
    private var next=0L
    private val active=mutableSetOf<Long>()

    fun acquire():Long {
        val token=++next
        val first=active.isEmpty()
        active.add(token)
        if(first) visibility(true)
        return token
    }

    fun contains(token:Long)=token in active

    fun release(token:Long) {
        if(active.remove(token)&&active.isEmpty()) visibility(false)
    }

    /** Stop, pause and failure invalidate outstanding callbacks as well as restoring the controls. */
    fun reset() {
        val hidden=active.isNotEmpty()
        active.clear()
        if(hidden) visibility(false)
    }
}
