package vn.ndang.aovcollector

import android.content.Context

/**
 * Tasks chosen to run one after another ("Chạy nhiều tác vụ"): their macros in order and the one running. Each runs
 * as a run of its own; the ones after the first start with a "home" step, back to the game's lobby. Kept apart from
 * the run's state, which a new run clears.
 */
class TaskQueue(val items:List<String>,val pos:Int) {
    val current get()=items.getOrNull(pos)
    /** "Tác vụ 2/3" while the chain runs. */
    val label get()="Tác vụ ${pos+1}/${items.size}"
    /** Whether [m] is the chain's task now running, by its hash (as [Macro.hash]). */
    fun isCurrent(m:Macro)=current?.let { raw -> java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }==m.hash }==true
    companion object {
        private const val PREFS="task-queue"
        // Macros are JSON, which never holds this character unescaped.
        private const val SEPARATOR="\u0001"
        fun load(c:Context):TaskQueue? { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            val items=p.getString("items",null)?.split(SEPARATOR)?.filter { it.isNotBlank() }?:return null
            return TaskQueue(items,p.getInt("pos",0)).takeIf { items.size>1&&it.pos in items.indices } }
        fun save(c:Context,q:TaskQueue) { c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("items",q.items.joinToString(SEPARATOR)).putInt("pos",q.pos).commit() }
        fun clear(c:Context) { c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
