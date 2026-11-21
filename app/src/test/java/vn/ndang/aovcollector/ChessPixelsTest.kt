package vn.ndang.aovcollector

import java.io.InputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fast capture's pixel readings on the user's own 2000x900 screenshots of every Bách khoa screen. */
class ChessPixelsTest {
    private class Screen(val width: Int, val height: Int, val px: IntArray) {
        val read = { y: Int, row: IntArray -> System.arraycopy(px, y * width, row, 0, width) }
    }

    private companion object {
        val PAGES = listOf(
            "basics", "basics-tabs", "heroes", "heroes-scrolled", "factions", "factions-scrolled", "synergies", "items",
            "items-loading", "quests", "blessings-common", "blessings-epic", "blessings-legend"
        )
        val DRAWERS = listOf("hero-drawer", "synergy-drawer", "item-drawer")
        val ALL = PAGES + DRAWERS + "save-photo"
        val cache = HashMap<String, Screen>()
    }

    /** JPEG decoded by the JDK's ImageIO, reached by reflection: unit tests compile against android.jar, which lacks it. */
    private fun screen(name: String): Screen = cache.getOrPut(name) {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/chess-$name.jpg")!!.use {
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
        Screen(w, h, get.invoke(image, 0, 0, w, h, null, 0, w) as IntArray)
    }

    private fun blank(name: String, drawer: Boolean) = screen(name).let { ChessPixels.blankBelow(it.width, it.height, drawer, it.read) }

    @Test fun pagesThatEndOnScreenAreNotDragged() {
        for (name in listOf("basics", "basics-tabs", "synergies", "items", "items-loading")) assertTrue(name, blank(name, false))
        for (name in listOf(
            "heroes",
            "heroes-scrolled",
            "factions",
            "factions-scrolled",
            "quests",
            "blessings-common",
            "blessings-epic",
            "blessings-legend"
        )) {
            assertFalse(name, blank(name, false))
        }
    }

    @Test fun drawersThatEndOnScreenAreNotDragged() {
        assertTrue(blank("item-drawer", true))
        // Violet's numbers and the related pieces under Đấu sĩ go on below.
        assertFalse(blank("hero-drawer", true))
        assertFalse(blank("synergy-drawer", true))
    }

    @Test fun onlyTheHeroListIsTheHeroGrid() {
        for (name in ALL) {
            val s = screen(name)
            assertEquals(name, name.startsWith("heroes"), ChessPixels.heroGrid(s.width, s.height, s.read))
        }
    }

    @Test fun theHeroListCanShowAPlainBottomWithRowsStillToCome() {
        // The user's list at the three-gold row: the cards are blue, a step from the background's colour, and plain
        // below their names. Moved so that part reaches the bottom strip, the strip reads as the end of a page.
        val moved = movedDown(screen("heroes-scrolled"), .37)
        assertTrue(ChessPixels.blankBelow(moved.width, moved.height, false, moved.read))
    }

    /** [s] with its content moved down by [down] of the height; the rows uncovered at the top repeat the first. */
    private fun movedDown(s: Screen, down: Double): Screen {
        val rows = (down * s.height).toInt()
        return Screen(s.width, s.height, IntArray(s.px.size) { i -> s.px[maxOf(0, i - rows * s.width)] })
    }

    @Test fun cardsInTheLowerThirdOfTheHeroListAreSeen() {
        for (name in listOf("heroes", "heroes-scrolled")) {
            val s = screen(name)
            assertTrue(name, ChessPixels.heroCardsBelow(s.width, s.height, s.read))
        }
        // The worst place: the plain stretch from the three-gold row's last line to the next portraits, .25 of the
        // height, moved to the top of the lower third. The next row's portraits still show under it.
        val worst = movedDown(screen("heroes-scrolled"), .23)
        assertTrue(ChessPixels.heroCardsBelow(worst.width, worst.height, worst.read))
        // Past the last row: the lower part is the plain stretch alone.
        val s = screen("heroes-scrolled")
        val plain = (.52 * s.height).toInt()
        val end = Screen(
            s.width,
            s.height,
            IntArray(s.px.size) { i ->
                if (i / s.width <
                    .62 * s.height
                ) {
                    s.px[i]
                } else {
                    s.px[plain * s.width + i % s.width]
                }
            }
        )
        assertFalse(ChessPixels.heroCardsBelow(end.width, end.height, end.read))
    }

    @Test fun itemPicturesStillLoadingAreCounted() {
        // The user's Trang bị shot too early: 15 pictures were still the plain square with a grey diamond.
        val loading = screen("items-loading")
        assertEquals(15, ChessPixels.loadingTiles(loading.width, loading.height, loading.read))
        val loaded = screen("items")
        assertEquals(0, ChessPixels.loadingTiles(loaded.width, loaded.height, loaded.read))
    }

    @Test fun openDrawersHaveTheirArtDrawn() {
        for (name in DRAWERS) {
            val s = screen(name)
            assertTrue(name, ChessPixels.drawerArt(s.width, s.height, s.read))
        }
        val hero = screen("hero-drawer")
        assertTrue(ChessPixels.skillArt(hero.width, hero.height, hero.read))
    }

    @Test fun findsTheButtonsOfSavePhoto() {
        val s = screen("save-photo")
        val dialog = ChessPixels.saveDialog(s.width, s.height, s.read)
        assertNotNull(dialog)
        // Sure at (772, 501) and CANCEL at (1133, 501) of 2000x900.
        assertTrue(dialog.toString(), abs(dialog!!.sure.first - .386) < .02 && abs(dialog.sure.second - .557) < .03)
        assertTrue(dialog.toString(), abs(dialog.cancel.first - .5665) < .02 && abs(dialog.cancel.second - .557) < .03)
    }

    @Test fun noOtherScreenIsTakenForSavePhoto() {
        for (name in ALL - "save-photo") {
            val s = screen(name)
            assertNull(name, ChessPixels.saveDialog(s.width, s.height, s.read))
        }
    }

    @Test fun findsTheBlessingPicturesWhollyOnScreen() {
        for (name in listOf("blessings-common", "blessings-epic", "blessings-legend")) {
            val s = screen(name)
            val icons = ChessPixels.blessingIcons(s.width, s.height, s.read)
            // Two rows of two cards; the third row is cut by the bottom edge.
            assertEquals("$name $icons", 4, icons.size)
            assertEquals(listOf(.277, .627, .277, .627), icons.map { it.first })
            assertTrue("$name $icons", icons.take(2).all { abs(it.second - .40) < .05 })
            assertTrue("$name $icons", icons.drop(2).all { abs(it.second - .66) < .05 })
        }
    }

    @Test fun differentBlessingsHaveDifferentPictures() {
        for (name in listOf("blessings-common", "blessings-epic", "blessings-legend")) {
            val s = screen(name)
            val arts = ChessPixels.blessingIcons(s.width, s.height, s.read).map { (x, y) ->
                ChessPixels.blessingArt(s.width, s.height, x, y, s.read)
            }
            for (i in arts.indices) {
                for (j in arts.indices) if (i != j) assertFalse("$name $i $j", ChessPixels.sameBlessingArt(arts[i], arts[j]))
            }
        }
    }

    @Test fun theSamePictureDraggedAFewPixelsIsTheSame() {
        val s = screen("blessings-common")
        // The second row's left card, which stays wholly on screen.
        val (x, y) = ChessPixels.blessingIcons(s.width, s.height, s.read)[2]
        // The page dragged up by .3 of the screen, plus three pixels the drag did not quite match.
        val shift = (.3 * s.height).toInt() + 3
        val moved = Screen(s.width, s.height, IntArray(s.px.size) { i -> s.px[minOf(s.px.size - 1, i + shift * s.width)] })
        val before = ChessPixels.blessingArt(s.width, s.height, x, y, s.read)
        val icon = ChessPixels.blessingIcons(moved.width, moved.height, moved.read).firstOrNull {
            abs(it.first - x) < .01 &&
                abs(it.second - (y - .3)) < .03
        }
        assertNotNull(icon)
        assertTrue(ChessPixels.sameBlessingArt(before, ChessPixels.blessingArt(s.width, s.height, icon!!.first, icon.second, moved.read)))
    }

    private fun blessings(s: Screen) = ChessPixels.blessingIcons(s.width, s.height, s.read).map { (x, y) ->
        ChessWiki.Tile(x, y, ChessPixels.blessingArt(s.width, s.height, x, y, s.read))
    }

    /**
     * Thường on the user's phone (2400x1080), pages 2 and 3, then 5 and 6 at the list's end. The cards at the bottom of
     * the first page are cut by the tabs on the next, where their pictures matched nothing, and the run saved them twice.
     */
    @Test fun cardsCutByTheTabsAreKnownFromThePageBefore() {
        for ((pages, move, fresh) in listOf(Triple(2 to 3, .585, 4), Triple(5 to 6, .203, 1))) {
            val before = screen("blessings-thuong-${pages.first}")
            val after = screen("blessings-thuong-${pages.second}")
            val moved = ChessPixels.blessingMoved(
                ChessPixels.blessingProfile(before.width, before.height, before.read),
                ChessPixels.blessingProfile(after.width, after.height, after.read),
                after.height
            )
            assertNotNull("$pages", moved)
            assertEquals("$pages", move, moved!!, .01)
            val seen = blessings(before)
            val now = blessings(after)
            // A card at the very top, cut by the tabs, is among those found.
            assertTrue("$pages $now", now.any { it.y < .34 })
            assertEquals("$pages", fresh, now.count { t -> seen.none { ChessPixels.sameBlessing(it, t, moved) } })
            assertTrue("$pages", now.count { t -> seen.none { ChessPixels.sameBlessingArt(it.id, t.id) } } > fresh)
        }
    }

    @Test fun anUnmovedBlessingPageShowsNothingNew() {
        val s = screen("blessings-thuong-3")
        val profile = ChessPixels.blessingProfile(s.width, s.height, s.read)
        val moved = ChessPixels.blessingMoved(profile, profile, s.height)
        assertEquals(0.0, moved!!, .001)
        val icons = blessings(s)
        assertTrue(icons.all { t -> icons.any { ChessPixels.sameBlessing(it, t, moved) } })
    }
}
