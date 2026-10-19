package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer
import java.util.Locale

class StorageNamesTest {
    @Test fun vietnameseNamesUseLowercaseAsciiHyphensInBothUnicodeForms() {
        val examples=mapOf("Cung thủ" to "cung-thu", "Đỡ Đòn" to "do-don",
            "Giây phút huy hoàng" to "giay-phut-huy-hoang", "TẤT CẢ QUÂN CỜ" to "tat-ca-quan-co",
            "  Sát_thủ / Pháp.sư---  " to "sat-thu-phap-su", "D'Arcy D’Arcy" to "darcy-darcy")
        examples.forEach { (name,expected) ->
            for(form in listOf(Normalizer.Form.NFC,Normalizer.Form.NFD)) {
                val text=Normalizer.normalize(name,form)
                assertEquals(expected,StorageNames.component(text))
                assertEquals(expected,HeroNames.slug(text))
            }
        }
    }

    @Test fun foldingIsLocaleIndependentAndComponentsAreSafeAndBounded() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("chi-so",StorageNames.component("CHỈ SỐ"))
        } finally { Locale.setDefault(old) }
        for(name in listOf("", "  ", "../", "🎮", "\u0301"))
            assertEquals("anh",StorageNames.component(name))
        assertEquals("abc",StorageNames.component("abc def",4))
        assertEquals("a".repeat(70),StorageNames.component("á".repeat(100)))
        // Hero/OCR identity truncation and its historical fallback must not change.
        assertEquals("a".repeat(47)+"-",HeroNames.slug("a".repeat(47)+" b"))
        assertEquals("chua-ro-ten",HeroNames.slug("🎮"))
    }

    @Test fun newRunFoldersUseTheSameNormalizationWithoutTouchingSavedRunIds() {
        assertEquals("single-dieu-thuyen",StorageNames.runPrefix("single","Điêu Thuyền"))
        assertEquals("pick-do-don",StorageNames.runPrefix("PICK","Đỡ_Đòn"))
        assertEquals("chess",StorageNames.runPrefix("chess",""))
    }

    @Test fun importedNamesPreserveExtensionsAndDistinctSourceSuffixes() {
        val first="a".repeat(14);val second="b".repeat(14)
        for(ext in listOf("png","jpg","jpeg","webp")) {
            assertEquals("cung-thu-page-1-$first.$ext",
                StorageNames.importedImage("Cung thủ_page 1.${ext.uppercase()}",first,ext))
            assertEquals("anh-$first.$ext",StorageNames.importedImage("🎮.$ext",first,ext))
            assertNotEquals(StorageNames.importedImage("Đỡ Đòn.$ext",first,ext),
                StorageNames.importedImage("Do Don.$ext",second,ext))
        }
        assertEquals("anh-chup-$first.png",StorageNames.importedImage("Ảnh chụp",first,"png"))
    }

    @Test fun chessNamesAndRoutesKeepTheRequestedPageHeadAndDetailConventions() {
        val hero=HeroNames.slug("Điêu Thuyền")
        assertEquals("dieu-thuyen-head",ChessCaptureFiles.nativeBase("quan-co","$hero-head",1))
        assertEquals("dieu-thuyen-detail-2",ChessCaptureFiles.screenshotBase(
            "0002-chess__quan-co__tat-ca-quan-co__tuong__${hero}__002"))
        val item=ChessPopupTitle.key("Cung thủ")
        val token="0002-chess__tuong-tac__nghe__nghe__${item}__001"
        assertEquals("cung-thu-page-1",ChessCaptureFiles.screenshotBase(token))
        assertEquals(listOf("tuong-tac","nghe"),ChessCaptureFiles.directoryParts(token))
        assertEquals("cung-thu-page-1",ChessCaptureFiles.exactDisplayName(
            "run-1/tuong-tac/nghe/cung-thu-page-1.png"))
        assertEquals("blessing-title-v1:hoan-phi-5-vang",ChessBlessings.key("Hoàn phí 5 vàng"))
    }
}
