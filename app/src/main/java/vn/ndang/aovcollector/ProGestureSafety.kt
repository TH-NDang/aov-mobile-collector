package vn.ndang.aovcollector

import kotlin.math.abs

/** Semantic bounds supplement, never replace, foreground/window/path safety. */
object ProGestureSafety {
    fun valid(action: CalibrationAction, points: List<CalibrationPoint>, target: GameScreens.Word? = null): Boolean {
        if (points.size != action.points.size || points.isEmpty()) return false
        if (target != null && points.any { it.x !in target.left..target.right || it.y !in target.top..target.bottom }) return false
        if (points.size == 1) {
            val base = action.points.single()
            val point = points.single()
            return when (action.id) {
                "pro.v1.picker.card" -> abs(point.x - base.x) <= .026 && abs(point.y - base.y) <= .05
                "pro.v1.picker.owned-filter" -> abs(point.x - base.x) <= .008 && abs(point.y - base.y) <= .016
                else -> true
            }
        }
        if (points.size != 2 || points.any { it.x !in .28..0.82 || it.y !in .18..0.90 }) return false
        val expected = action.points[1].y - action.points[0].y
        val actual = points[1].y - points[0].y
        // Retain the measured reader's direction and meaningful motion, including its fine overlap probes.
        return abs(expected) > .005 &&
            actual * expected > 0 &&
            abs(actual) >= abs(expected) * .8 &&
            abs(actual) <= abs(expected) * 1.25 &&
            abs(points[1].x - points[0].x) <= .035
    }
}

/** Fresh screenshot proof used at dispatch, including after an arbitrarily long teaching pause. */
object ProApplyEvidence {
    fun valid(
        action: CalibrationAction,
        points: List<CalibrationPoint>,
        expected: ProComboScreens.Entry,
        entries: List<ProComboScreens.Entry>,
        catalog: List<ProComboScreens.Entry>,
        proSelected: Boolean,
        blocked: Boolean
    ): Boolean {
        if (!proSelected || blocked) return false
        val matched = entries.filter { ProComboScreens.uniqueMatch(expected, it, catalog) }.singleOrNull()
            ?: return false
        return ProGestureSafety.valid(action, points, matched.button)
    }
}
