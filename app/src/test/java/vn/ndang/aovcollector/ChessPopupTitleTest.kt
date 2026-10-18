package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class ChessPopupTitleTest {
    private fun w(text:String,y:Double)=GameScreens.Word(text,.36,y-.015,.75,y+.015)
    @Test fun equipmentTitleWinsEvenWhenDescriptionArrivesFirstFromOcr() {
        val words=listOf(w("Cho đồng đội gần lá chắn năng lượng",.305),w("Lệnh bài phong thần",.25))
        assertEquals("Lệnh bài phong thần",ChessPopupTitle.select(words))
        assertEquals("Lệnh bài phong thần",ChessPopupTitle.select(words.reversed()))
    }
    @Test fun bodyTextAndMissingHeadingNeverBecomeAFilename() {
        assertNull(ChessPopupTitle.select(listOf(w("Cho đồng đội gần lá chắn năng lượng",.305))))
        assertNull(ChessPopupTitle.select(listOf(w("Một đoạn mô tả dài",.39))))
        assertNull(ChessPopupTitle.select(emptyList()))
    }
    @Test fun topmostHeadingIsSelectedInsteadOfTheFirstOcrLine() {
        assertEquals("Thánh kiếm",ChessPopupTitle.select(listOf(w("Chí mạng tăng",.30),w("Thánh kiếm",.24))))
    }
    @Test fun professionBonusRowsInUserExampleCannotBecomeNames() {
        for(body in listOf("2 25 tỷ lệ khiến địch giải giới 2 giây","2 500 giáp phép",
            "2 nhận 25 năng lượng","2 toàn bộ kẻ địch 30 giáp")) {
            assertNull(ChessPopupTitle.select(listOf(w(body,.27))))
            assertEquals("Tên nghề",ChessPopupTitle.select(listOf(w(body,.25),w("Tên nghề",.19))))
        }
    }
    @Test fun coloredDescriptionIsRejectedEvenInsideHeadingBand() {
        val heading=w("Thánh kiếm",.24);val body=w("Hiệu ứng trang bị",.22)
        assertEquals("Thánh kiếm",ChessPopupTitle.select(listOf(body,heading)) { it===heading })
    }

    @Test fun cungThuPopupUsesItsVisibleNameInsteadOfTwoTwentyPercentBonus() {
        assertEquals("Cung thủ",ChessPopupTitle.select(listOf(w("2 (2) 20% tỷ lệ liên xạ",.30),w("Cung thủ",.25))))
    }

    @Test fun semanticNamesDoNotCollapseAtFortyFiveCharacters() {
        val prefix="ten-nghe-rat-dai-".repeat(4)
        assertNotEquals(ChessPopupTitle.key(prefix+"mot"),ChessPopupTitle.key(prefix+"hai"))
    }

}
