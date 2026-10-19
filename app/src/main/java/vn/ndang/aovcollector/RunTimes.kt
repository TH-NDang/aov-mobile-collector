package vn.ndang.aovcollector

import android.content.SharedPreferences
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * How long tasks took when they ran from start to end without a pause, kept on the phone to estimate the next
 * runs: seconds per hero of a list or a single hero (for its speed options), per statistics table, and for the
 * way into the game. Each new clean run moves the kept time halfway towards its own, so one odd run does not rule.
 */
object RunTimes {
    const val ENTRY = "time:entry"
    const val TABLE = "time:ranking"

    fun hero(batch: Boolean, fast: Boolean, detailOnly: Boolean, directSkills: Boolean) =
        "time:${if (batch) "all" else "single"}:" + listOf(fast, detailOnly, directSkills).joinToString("") { if (it) "1" else "0" }

    fun get(prefs: SharedPreferences, key: String): Double? = prefs.getFloat(key, -1f).takeIf { it > 0 }?.toDouble()

    fun record(prefs: SharedPreferences, key: String, seconds: Double) {
        if (!(seconds > 0)) return
        prefs.edit().putFloat(key, blend(get(prefs, key), seconds).toFloat()).apply()
    }

    fun blend(kept: Double?, seconds: Double) = if (kept == null) seconds else (kept + seconds) / 2

    /** Exact macro configuration: topics, speed, native saves and game entry all affect its hash. */
    fun task(hash: String) = "time:task:$hash"

    fun profile(json: JSONObject): String {
        fun canonical(value: Any?): String = when (value) {
            is JSONObject -> value.keys().asSequence().filter { it != "hint" }.sorted().joinToString(",", "{", "}") {
                JSONObject.quote(it) + ":" +
                    canonical(value.get(it))
            }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            is String -> JSONObject.quote(value)
            else -> value?.toString() ?: "null"
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical(json).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun taskSamples(prefs: SharedPreferences, hash: String): List<Double> =
        prefs.getString(task(hash) + ":samples", "").orEmpty().split(',').mapNotNull { it.toDoubleOrNull() }
            .filter { it.isFinite() && it > 0 }.takeLast(8)

    fun recordTask(prefs: SharedPreferences, hash: String, seconds: Double) {
        if (!seconds.isFinite() || seconds <= 0) return
        val samples = (taskSamples(prefs, hash) + seconds).takeLast(8)
        var estimate: Double? = null
        samples.forEach { estimate = blend(estimate, it) }
        prefs.edit().putString(task(hash) + ":samples", samples.joinToString(","))
            .putFloat(task(hash), estimate!!.toFloat()).apply()
    }

    /**
     * Seconds left for [left] more units when this run took [spent] seconds for [done]: the [kept] pace of earlier
     * clean runs at first, then more and more this run's own, all of it after ten units. Null with neither.
     */
    fun remaining(left: Int, done: Int, spent: Double, kept: Double?): Double? {
        val own = if (done > 0 && spent > 0) spent / done else null
        val pace = when {
            kept == null -> own
            own == null -> kept
            else -> minOf(1.0, done / 10.0).let { w -> w * own + (1 - w) * kept }
        } ?: return null
        return maxOf(0, left) * pace
    }
}
