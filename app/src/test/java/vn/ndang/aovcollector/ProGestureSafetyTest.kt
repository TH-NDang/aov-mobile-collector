package vn.ndang.aovcollector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGestureSafetyTest {
    private val swipe = CalibrationAction("pro.v1.list.scroll", "Pro", listOf(CalibrationPoint(.7, .78), CalibrationPoint(.7, .56)), true)

    @Test fun allowsOriginalAndSmallMeasuredAdjustment() {
        assertTrue(ProGestureSafety.valid(swipe, swipe.points))
        assertTrue(ProGestureSafety.valid(swipe, listOf(CalibrationPoint(.69, .77), CalibrationPoint(.69, .55))))
    }

    @Test fun rejectsStationaryReversedOutsideAndExcessivelyLongOverride() {
        for (points in listOf(
            listOf(CalibrationPoint(.7, .78), CalibrationPoint(.7, .78)),
            swipe.points.reversed(),
            listOf(CalibrationPoint(.1, .78), CalibrationPoint(.1, .56)),
            listOf(CalibrationPoint(.7, .78), CalibrationPoint(.7, .3))
        )) {
            assertFalse(ProGestureSafety.valid(swipe, points))
        }
    }

    @Test fun applyAndTeachingMustStayInsideMatchedButton() {
        val a = CalibrationAction("pro.v1.apply", "Dùng combo", listOf(CalibrationPoint(.75, .41)), true)
        val target = GameScreens.Word("Dùng combo", .70, .38, .81, .44)
        assertTrue(ProGestureSafety.valid(a, a.points, target))
        assertFalse(ProGestureSafety.valid(a, listOf(CalibrationPoint(.75, .76)), target))
    }
}
