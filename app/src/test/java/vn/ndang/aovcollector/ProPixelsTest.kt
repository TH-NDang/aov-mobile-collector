package vn.ndang.aovcollector

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Đề cử Pro and the two Chọn tướng pickers on the user's 2000x900 screenshots: Chuẩn bị, Chọn tướng over Chuẩn bị
 * and over Chiến khu, Hệ thống đề cử, and the Đề cử Pro list at three places. Recommenders' names and pictures, and
 * the UID, are pixelated in the files.
 */
class ProPixelsTest {
    private class Shot(val width: Int, val height: Int, val px: IntArray) {
        val read = { y: Int, row: IntArray -> System.arraycopy(px, y * width, row, 0, width) }
        val buttons by lazy { ProPixels.buttons(width, height, read) }
        val panel by lazy { ProPixels.panel(width, height, read) }
    }

    private companion object {
        val cache = HashMap<String, Shot>()
    }

    private fun shot(name: String): Shot = cache.getOrPut(name) {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/$name.jpg")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java).invoke(null, it)!!
        }
        val type = image.javaClass
        val w = type.getMethod("getWidth").invoke(image) as Int
        val h = type.getMethod("getHeight").invoke(image) as Int
        val get = type.getMethod(
            "getRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        )
        Shot(w, h, get.invoke(image, 0, 0, w, h, null, 0, w) as IntArray)
    }

    private fun ys(name: String) = shot(name).buttons.map { it.y }

    /**
     * The top of three pictures the app kept from the user's phone, 2400x1080: Chọn tướng at heroes 56 and 104, where
     * the run stopped with the grid whole, and the Pro list in the same frame.
     */
    @Test fun theTitleStripKnowsChonTuongAgainAndNotTheProList() {
        fun title(name: String) = shot(name).let { ProHeroPicker.title(it.width, 1080, it.read) }
        assertTrue(ProHeroPicker.sameTitle(title("pro-picker-head-56"), title("pro-picker-head-104")))
        assertFalse(ProHeroPicker.sameTitle(title("pro-picker-head-104"), title("pro-list-head")))
        // A strip with no title on it tells nothing, even against itself.
        assertFalse(ProHeroPicker.sameTitle(FloatArray(990) { 40f }, FloatArray(990) { 40f }))
        assertFalse(ProHeroPicker.sameTitle(title("pro-picker-head-104"), floatArrayOf()))
        // The header's words as read on one look serve the other: Tất cả underlined, Đã nhận's box empty.
        val words = listOf(
            GameScreens.Word("Chọn tướng", .160, .097, .248, .139),
            GameScreens.Word("Tất cả", .199, .213, .232, .243),
            GameScreens.Word("Đã nhận", .761, .213, .802, .243)
        )
        val later = shot("pro-picker-head-104")
        assertTrue(ProHeroPicker.allSelected(later.width, 1080, words, later.read))
        assertEquals(false, BoardScreens.ownedFilter(later.width, 1080, words, later.read)?.checked)
    }

    @Test fun findsTheDungComboButtonsWhereTheyAre() {
        for ((name, want) in listOf(
            "pro-system" to listOf(.41, .776),
            "pro-list" to listOf(.41, .776),
            "pro-list-scrolled" to listOf(.468, .837),
            "pro-list-scrolled-more" to listOf(.304, .672),
            // From the user's phone, 2400x1080: the card above is cut by the list's top and not taken.
            "pro-list-device" to listOf(.554)
        )) {
            val got = ys(name)
            assertEquals("$name $got", want.size, got.size)
            want.zip(got).forEach { (w, g) -> assertEquals("$name $got", w, g, .012) }
            // Tapped in the middle of the pill.
            assertTrue(shot(name).buttons.all { it.x in .74..0.77 && it.plain == "dungcombo" })
        }
    }

    @Test fun aPillInThePhonesOwnColoursIsOneButton() {
        // The phone's pill: (200, 171, 66) above its label, (213, 190, 84) below, the label's rows mostly white.
        // Red at 200 is not over 200: the first rule saw two short yellow strips there, no button, and lost the list.
        val w = 2400
        val h = 1080
        val px = IntArray(w * h) { 0xff35357c.toInt() }
        for (y in (.524 * h).toInt() until (.583 * h).toInt()) {
            val f = y.toDouble() / h
            for (x in (.705 * w).toInt() until (.81 * w).toInt()) {
                px[y * w + x] = when {
                    f < .548 -> 0xffc8ab42.toInt()
                    f < .566 -> if ((x / 7) % 3 == 0) 0xffd5be54.toInt() else 0xffe6dccb.toInt()
                    else -> 0xffd5be54.toInt()
                }
            }
        }
        val got = Shot(w, h, px).buttons
        assertEquals(got.toString(), 1, got.size)
        assertEquals(.554, got[0].y, .012)
    }

    @Test fun noOtherScreenHasDungComboButtons() {
        for (name in listOf(
            "pro-chuan-bi",
            "pro-picker",
            "board-picker-owned",
            "board-players-vn",
            "board-player-card",
            "board-player-profile",
            "board-player-photo"
        )) {
            assertTrue(name, ys(name).isEmpty())
        }
    }

    @Test fun theButtonsGiveTheRecommendationsPlacesAndTheScroll() {
        // At the top of the list: the first two recommendations.
        assertEquals(listOf(0, 1), ProSimpleReader.ranked(shot("pro-list").buttons, 0.0)!!.map { it.rank })
        // Further down, followed from one frame to the next by how far the buttons moved.
        val before = ProSimpleReader.ranked(shot("pro-list-scrolled").buttons, .308)!!
        assertEquals(listOf(1, 2), before.map { it.rank })
        val moved = ProSimpleReader.shift(before, shot("pro-list-scrolled-more").buttons)
        assertNotNull(moved)
        assertEquals(.164, moved!!, .01)
        assertEquals(listOf(1, 2), ProSimpleReader.ranked(shot("pro-list-scrolled-more").buttons, .308 + moved)!!.map { it.rank })
    }

    @Test fun chuanBiIsToldToHaveSettledByItsPanel() {
        val prep = shot("pro-chuan-bi").panel
        assertTrue(ProPixels.same(prep, prep.copyOf()))
        assertFalse(ProPixels.same(prep, shot("pro-picker").panel))
        assertFalse(ProPixels.same(prep, IntArray(0)))
    }

    /** "Đã nhận" ticked, as the game opens Chọn tướng: found, and where to tap to clear it, on both pickers. */
    @Test fun daNhanIsSeenTickedOnBothPickers() {
        val words = listOf(
            GameScreens.Word("Chọn tướng", .16, .10, .2475, .135),
            GameScreens.Word("Tất cả", .199, .214, .2335, .243),
            GameScreens.Word("Đã nhận", .761, .214, .802, .243)
        )
        for (name in listOf("pro-picker", "board-picker-owned")) {
            val s = shot(name)
            val filter = BoardScreens.ownedFilter(s.width, s.height, words, s.read)
            assertNotNull(name, filter)
            assertTrue(name, filter!!.checked)
            assertEquals(name, .814, filter.x, .012)
            assertEquals(name, .229, filter.y, .012)
            assertTrue(name, ProHeroPicker.allSelected(s.width, s.height, words, s.read))
        }
    }
}
