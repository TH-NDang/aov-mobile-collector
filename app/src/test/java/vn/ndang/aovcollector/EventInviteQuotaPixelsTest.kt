package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventInviteQuotaPixelsTest {
    private val purple = 0xff7453db.toInt()
    private val magenta = 0xffd17a8e.toInt()
    private val white = 0xfff4f4f4.toInt()

    private fun screen(first: Int, slash: Boolean = true, second: Boolean = true, shift: Int = 0): EventInviteQuotaPixels.Reading? =
        EventInviteQuotaPixels.read(1536, 691) { x, y ->
            val xx = x - shift
            when {
                slash && xx in 834..836 && y in 370..380 -> white
                second && xx in 842..844 && y in 369..382 -> magenta
                first == 1 && xx in 824..826 && y in 369..382 -> magenta
                first == 0 &&
                    xx in 818..829 &&
                    y in 369..382 &&
                    (xx <= 819 || xx >= 828 || y <= 370 || y >= 381) -> magenta
                else -> purple
            }
        }

    @Test fun distinguishesRingZeroFromNarrowOneWithIndependentSlashAndDenominator() {
        val notUsed = screen(0)!!
        val used = screen(1)!!
        assertEquals(0, notUsed.used)
        assertEquals(1, notUsed.dailyLimit)
        assertEquals(1, used.used)
        assertEquals(1, used.dailyLimit)
        assertTrue(notUsed.firstInk > used.firstInk * 1.60)
        assertTrue(notUsed.slashInk > 0 && used.slashInk > 0)
    }

    @Test fun refusesMissingOrShiftedEvidenceInsteadOfGuessingWhichAccountCanEnter() {
        assertNull(screen(0, slash = false))
        assertNull(screen(0, second = false))
        assertNull(screen(1, slash = false))
        assertNull(screen(1, second = false))
        assertNull(screen(0, shift = 70))
        assertNull(screen(9))
        assertNull(EventInviteQuotaPixels.read(400, 900) { _, _ -> magenta })
        assertNull(EventInviteQuotaPixels.read(1536, 691) { _, _ -> purple })
    }
}
