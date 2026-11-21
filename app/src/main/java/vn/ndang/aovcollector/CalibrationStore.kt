package vn.ndang.aovcollector

import android.content.SharedPreferences

/** Normalized coordinates across the full display, matching gesture dispatch and screenshots. */
data class CalibrationPoint(val x: Double, val y: Double)

/** Exact display geometry; edge values are inset sizes, not rectangle coordinates. */
data class CalibrationProfile(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val key: String get() = "$width:$height:$rotation:$left:$top:$right:$bottom"

    internal fun valid(): Boolean = width > 0 &&
        height > 0 &&
        rotation in 0..3 &&
        left >= 0 &&
        top >= 0 &&
        right >= 0 &&
        bottom >= 0 &&
        left.toLong() + right < width &&
        top.toLong() + bottom < height
}

/** IDs are stable semantic identifiers supplied by code, never user-visible labels. */
data class CalibrationAction(
    val id: String,
    val label: String,
    val points: List<CalibrationPoint>,
    val relative: Boolean = false,
    val durationMs: Long = 100,
    val holdMs: Long = 0
)

/** Versioned, per-display calibration. Corrupt or incompatible records use fresh defaults. */
internal class CalibrationStore(private val prefs: SharedPreferences) {
    fun resolve(profile: CalibrationProfile, action: CalibrationAction): List<CalibrationPoint> {
        // Invalid caller geometry must not become an injectable gesture.
        if (!CalibrationCodec.validPoints(action.points)) return emptyList()
        if (!profile.valid() || !validId(action.id)) return action.points
        return try {
            CalibrationCodec.decode(prefs.getString(key(profile, action.id), null), action) ?: action.points
        } catch (_: RuntimeException) {
            action.points
        }
    }

    fun save(profile: CalibrationProfile, action: CalibrationAction, points: List<CalibrationPoint>): Boolean {
        if (!profile.valid() || !validId(action.id)) return false
        val encoded = CalibrationCodec.encode(action, points) ?: return false
        return try {
            prefs.edit().putString(key(profile, action.id), encoded).commit()
        } catch (_: RuntimeException) {
            false
        }
    }

    fun reset(profile: CalibrationProfile, actionId: String): Boolean {
        if (!profile.valid() || !validId(actionId)) return false
        return try {
            prefs.edit().remove(key(profile, actionId)).commit()
        } catch (_: RuntimeException) {
            false
        }
    }

    fun resetProfile(profile: CalibrationProfile): Boolean {
        if (!profile.valid()) return false
        return try {
            val prefix = prefix(profile)
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
            editor.commit()
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun prefix(profile: CalibrationProfile): String = "gesture_calibration.v1/${profile.key}/"

    private fun key(profile: CalibrationProfile, id: String): String = prefix(profile) + id

    private fun validId(id: String): Boolean = id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
}

/** Pure codec: v1|mode|point-count|x,y|x,y. No coercion or partially accepted records. */
internal object CalibrationCodec {
    private const val MAX_POINTS = 256
    private const val MAX_RECORD_LENGTH = 32768

    fun validPoints(points: List<CalibrationPoint>): Boolean = points.size in 1..MAX_POINTS && points.all { bounded(it, 0.0, 1.0) }

    fun encode(action: CalibrationAction, points: List<CalibrationPoint>): String? {
        if (!validPoints(action.points) || !validPoints(points) || points.size != action.points.size) return null
        val stored = if (action.relative) {
            points.zip(action.points) { point, base -> CalibrationPoint(point.x - base.x, point.y - base.y) }
        } else {
            points
        }
        return "1|${if (action.relative) "relative" else "absolute"}|${stored.size}|" +
            stored.joinToString("|") { "${it.x},${it.y}" }
    }

    fun decode(raw: String?, action: CalibrationAction): List<CalibrationPoint>? {
        if (raw == null || raw.length > MAX_RECORD_LENGTH || !validPoints(action.points)) return null
        val fields = raw.split('|')
        if (fields.size < 4 || fields[0] != "1" || fields[1] != if (action.relative) "relative" else "absolute") return null
        if (fields[2] != action.points.size.toString() || fields.size != action.points.size + 3) return null
        val stored = fields.drop(3).map { field ->
            val xy = field.split(',')
            if (xy.size != 2 || xy.any { it != it.trim() }) return null
            val x = xy[0].toDoubleOrNull() ?: return null
            val y = xy[1].toDoubleOrNull() ?: return null
            CalibrationPoint(x, y).also {
                if (!bounded(it, if (action.relative) -1.0 else 0.0, 1.0)) return null
            }
        }
        val resolved = if (action.relative) {
            stored.zip(action.points) { delta, base -> CalibrationPoint(base.x + delta.x, base.y + delta.y) }
        } else {
            stored
        }
        return resolved.takeIf { validPoints(it) }
    }

    private fun bounded(point: CalibrationPoint, min: Double, max: Double): Boolean =
        point.x.isFinite() && point.y.isFinite() && point.x in min..max && point.y in min..max
}

/** Never turn a queued, old screenshot into an interactive test target. */
internal object CalibrationFreshness {
    const val MAX_AGE_MS = 2500L

    fun valid(capturedAt: Long, now: Long): Boolean = capturedAt >= 0 && now >= capturedAt && now - capturedAt <= MAX_AGE_MS
}
