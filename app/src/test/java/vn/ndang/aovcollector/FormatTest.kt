package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun sizesUseTheLargestFittingUnit() {
        assertEquals("512 B", Format.size(512))
        assertEquals("850 KB", Format.size(850L * 1024))
        assertEquals("4,2 MB", Format.size((4.2 * 1048576).toLong()))
        assertEquals("24 MB", Format.size(24L * 1048576))
        assertEquals("2,61 GB", Format.size((2.61 * 1073741824).toLong() + 1000))
        assertEquals("12,5 GB", Format.size((12.5 * 1073741824).toLong()))
    }

    @Test fun progressIsInTheUnitOfTheTotal() {
        assertEquals("0,85/2,61 GB", Format.sizes((0.85 * 1073741824).toLong() + 1000, (2.61 * 1073741824).toLong() + 1000))
        assertEquals("3,0/24 MB", Format.sizes(3L * 1048576, 24L * 1048576))
    }

    @Test fun durationsRoundToWhatMatters() {
        assertEquals("45 giây", Format.duration(45))
        assertEquals("12 phút", Format.duration(12 * 60 + 10))
        assertEquals("1 giờ", Format.duration(3590))
        assertEquals("1 giờ 19 phút", Format.duration(79 * 60))
    }
}
