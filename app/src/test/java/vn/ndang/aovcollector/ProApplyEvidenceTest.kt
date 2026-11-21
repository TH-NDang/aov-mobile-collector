package vn.ndang.aovcollector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProApplyEvidenceTest {
    private val action = CalibrationAction("pro.v1.apply", "Pro", listOf(CalibrationPoint(.75, .41)), true)

    private fun entry(y: Double = .41, id: String = "creator", color: Int = 20) = ProComboScreens.Entry(
        id,
        id,
        GameScreens.Word("Dùng combo", .70, y - .03, .81, y + .03),
        IntArray(325) { if (it == 0) 90 else color }
    )

    @Test fun unchangedFreshRowCanDispatch() {
        val expected = entry()
        assertTrue(ProApplyEvidence.valid(action, action.points, expected, listOf(expected), listOf(expected), true, false))
    }

    @Test fun scrollingReorderingOrChangedComboDuringTeachingRefusesDispatch() {
        val expected = entry()
        for (observed in listOf(entry(.76), entry(id = "different"), entry(color = 100))) {
            assertFalse(ProApplyEvidence.valid(action, action.points, expected, listOf(observed), listOf(expected), true, false))
        }
    }

    @Test fun missingAmbiguousUnselectedOrBlockedFreshSceneRefusesDispatch() {
        val expected = entry()
        assertFalse(ProApplyEvidence.valid(action, action.points, expected, emptyList(), listOf(expected), true, false))
        assertFalse(ProApplyEvidence.valid(action, action.points, expected, listOf(expected, expected), listOf(expected), true, false))
        assertFalse(ProApplyEvidence.valid(action, action.points, expected, listOf(expected), listOf(expected), false, false))
        assertFalse(ProApplyEvidence.valid(action, action.points, expected, listOf(expected), listOf(expected), true, true))
    }

    @Test fun fuzzyMidpointCannotMatchAnotherFrozenRecommendationOffscreen() {
        val first = entry(color = 10)
        val sixth = entry(color = 60)
        assertFalse(ProApplyEvidence.valid(action, action.points, first, listOf(entry(color = 35)), listOf(first, sixth), true, false))
    }
}
