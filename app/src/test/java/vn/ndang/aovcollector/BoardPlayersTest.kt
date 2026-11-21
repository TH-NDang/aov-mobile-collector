package vn.ndang.aovcollector

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Chiến khu ranking's players read from pixels, on the user's 2000x900 screenshots: the Việt Nam and Đắk Lắk lists,
 * the card a tapped row opens, "Thông tin kiện tướng" and the picture shown large. Players' names and pictures, the
 * region and the UID are pixelated in the files.
 */
class BoardPlayersTest {
    private class Shot(val width: Int, val height: Int, val px: IntArray) {
        val read = { y: Int, row: IntArray -> System.arraycopy(px, y * width, row, 0, width) }
        val facts by lazy { BoardPlayerPixels.read(width, height, read) }
    }

    private companion object {
        val cache = HashMap<String, Shot>()
    }

    private fun shot(name: String): Shot = cache.getOrPut(name) {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/board-$name.jpg")!!.use {
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

    @Test fun findsTheFourPlayersOfTheFirstScreen() {
        for (name in listOf("players-vn", "players-tinh", "player-card")) {
            val rows = shot(name).facts.rows
            // Ranks 1-4; the fifth is under the user's own row at the bottom.
            assertEquals("$name $rows", 4, rows.size)
            listOf(.284, .436, .5906, .739).zip(rows).forEach { (want, got) -> assertEquals("$name $rows", want, got, .01) }
        }
    }

    @Test fun ranksFollowThePitchFromTheTop() {
        val rows = shot("players-vn").facts.rows
        val pitch = (rows.last() - rows.first()) / (rows.size - 1)
        assertEquals(BoardPlayerPixels.PITCH, pitch, .004)
        assertEquals(BoardPlayerPixels.ROW0, rows.first(), .01)
    }

    @Test fun theCardIsToldFromTheList() {
        assertTrue(shot("player-card").facts.card)
        for (name in listOf("players-vn", "players-tinh", "player-profile", "player-photo")) assertFalse(name, shot(name).facts.card)
    }

    @Test fun theListBehindTheCardIsWhereItWas() {
        // The tapped row is highlighted and the card covers the right: the list has not moved.
        assertTrue(BoardPlayerPixels.samePlace(shot("players-vn").facts, shot("player-card").facts))
        // Another tab's list is not the same place.
        assertFalse(BoardPlayerPixels.samePlace(shot("players-vn").facts, shot("players-tinh").facts))
    }

    @Test fun measuresHowFarTheListMoved() {
        val s = shot("players-vn")
        // The list scrolled up by .2 of the screen: everything under the tabs moved, the rest stayed.
        val shift = (.2 * s.height).toInt()
        val top = (BoardPlayerPixels.LIST_TOP * s.height).toInt()
        val bottom = (BoardPlayerPixels.LIST_BOTTOM * s.height).toInt()
        val moved = Shot(
            s.width,
            s.height,
            IntArray(s.px.size) { i ->
                val y = i / s.width
                if (y in top until bottom && y + shift < bottom) s.px[i + shift * s.width] else s.px[i]
            }
        )
        val d = BoardPlayerPixels.moved(s.facts, moved.facts)
        assertNotNull(d)
        assertEquals(.2, d!!, 2.0 / s.height)
        assertFalse(BoardPlayerPixels.samePlace(s.facts, moved.facts))
    }

    @Test fun theProfileIsDrawn() {
        val f = shot("player-profile").facts
        assertTrue(f.stats)
        assertTrue("${f.avatarDetail}", f.avatarDetail > BoardPlayerPixels.AVATAR_DRAWN)
        assertTrue(BoardPlayerPixels.same(f.panel, f.panel.copyOf()))
    }

    @Test fun thePictureShownLargeDimsThePage() {
        val page = shot("player-profile").facts
        val photo = shot("player-photo").facts
        assertTrue(photo.panelLum < BoardPlayerPixels.POPUP_DIM * page.panelLum)
        assertTrue("${photo.photoDetail}", photo.photoDetail > BoardPlayerPixels.PHOTO_DRAWN)
        // The list and the card do not look like a picture over the page.
        for (name in listOf("players-vn", "player-card")) {
            assertFalse(name, shot(name).facts.panelLum < BoardPlayerPixels.POPUP_DIM * page.panelLum)
        }
        assertFalse(BoardPlayerPixels.same(page.panel, photo.panel))
    }

    @Test fun theProfileIsToldByItsTitle() {
        fun w(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .06, y - .02, x + .06, y + .02)
        val profile = listOf(
            w("Thông tin kiện tướng", .215, .056),
            w("Số 1 máy chủHayate", .17, .34),
            w("Lịch sử đấu", .13, .945),
            w("Trận", .124, .75)
        )
        assertEquals(BoardScreens.Page.PROFILE, BoardScreens.page(profile, false))
        // A word cut by ML Kit is still the title at the top left; with no title, the page's own labels.
        assertEquals(BoardScreens.Page.PROFILE, BoardScreens.page(listOf(w("tin kiện tướng", .24, .056)), false))
        assertEquals(BoardScreens.Page.PROFILE, BoardScreens.page(listOf(w("Tỷ lệ thắng", .186, .75), w("Lịch sử đấu", .13, .945)), false))
        // A player's card over the ranking shows "Hiện…" and "Hạng" only.
        assertEquals(
            BoardScreens.Page.BOARD,
            BoardScreens.page(listOf(w("Chiến khu", .17, .057), w("Toàn máy chủ", .75, .146), w("Hiện tướng", .80, .84)), false)
        )
        // The ranking itself, whose card says "Số 1 máy chủ" too, stays the ranking.
        val board = listOf(w("Chiến khu", .17, .057), w("Toàn máy chủ", .75, .146), w("Số 1 máy chủHayate", .87, .47))
        assertEquals(BoardScreens.Page.BOARD, BoardScreens.page(board, false))
    }

    @Test fun oldMacrosOpenThePlayersAndTheOptionIsKept() {
        val old = org.json.JSONObject().put("collectionMode", "board")
        assertTrue(BuiltInMacros.boardPlayers(old))
        val off = org.json.JSONObject(BuiltInMacros.board(players = false))
        assertFalse(BuiltInMacros.boardPlayers(off))
        assertFalse(BuiltInMacros.boardPlayers(off.getJSONArray("steps").getJSONObject(1)))
        assertTrue(BuiltInMacros.boardPlayers(org.json.JSONObject(BuiltInMacros.board())))
    }

    /** The eye's box on the user's phone, names, pictures and the UID pixelated: its copy icon right of the number. */
    @Test fun findsTheUidCopyIconInTheEyesBox() {
        val s = shot("player-uid-box")
        assertEquals(.566, s.facts.uidCopy!!, .004)
        // The box's number as OCR gives it, on its row.
        val number = GameScreens.Word("UID 1200000000000001", .415, .331, .555, .361)
        assertEquals(number, BoardPlayerPixels.infoUid(listOf(GameScreens.Word("Số 1 máy chủRaz", .12, .32, .29, .36), number)))
        // The user's own UID, faint at the bottom of every page, is not the player's.
        assertEquals(null, BoardPlayerPixels.infoUid(listOf(GameScreens.Word("UID 1200000000000002", .42, .97, .55, .99))))
    }
}
