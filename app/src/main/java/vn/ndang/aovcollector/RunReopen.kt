package vn.ndang.aovcollector

import org.json.JSONObject

/**
 * Whether a run folder ended before its end, by Dừng or by choosing another task, can be opened again: its run.json
 * still names the macro.json beside it, and a step is left. The run open in the panel, still going or paused, is
 * never taken over.
 */
object RunReopen {
    /** [record]: its run.json; [raw]: its macro.json; [open]: the run in the panel, and whether Dừng ended it. */
    fun allowed(name: String, record: JSONObject, raw: String, open: String?, openStopped: Boolean): Boolean = try {
        val m = Macro(raw)
        record.optString("state") in setOf("stopped", "paused") &&
            record.optString("hash") == m.hash &&
            record.optInt("nextStep", -1) in 0 until m.steps.length() &&
            !(name == open && !openStopped)
    } catch (_: Exception) {
        false
    }
}
