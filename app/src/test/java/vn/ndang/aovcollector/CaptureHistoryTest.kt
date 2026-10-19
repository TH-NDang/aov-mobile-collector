package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class CaptureHistoryTest {
    @Test fun cleanChessDetailsKeepTheirDescriptionThroughTheFolderMetadata() {
        assertEquals(
            "Quân cờ · Chi tiết · Trang 2",
            CaptureHistory.description(
                "run-1/quan-co/tat-ca-quan-co/tuong/violet/violet-detail-2.png"
            )
        )
        assertEquals("Ảnh chụp màn hình", CaptureHistory.description("run-1/violet-detail-2.png"))
    }

    @Test fun legacyDescriptionsAndNativeGroupsStayCompatible() {
        assertEquals("Tổng quan", CaptureHistory.description("run-1/hero-001-overview.png"))
        assertEquals("Chiêu 2 · Chi tiết", CaptureHistory.description("run-1/hero-001-skill-2-detail.png"))
        assertEquals(
            "Tất cả quân cờ · Trang 1",
            CaptureHistory.description(
                "run-1/chess__quan-co__tat-ca-quan-co__tuong__violet__001.png"
            )
        )
        assertEquals(
            "Ảnh gốc · Quân cờ · Ảnh nhân vật",
            CaptureHistory.description(
                "anh-goc-co-lien-quan/quan-co/anh-quan-co/run-1/violet-head.jpg"
            )
        )
    }

    @Test fun runLocalNativeImagesAreNeverDescribedAsScreenshotsOrSkillSummaries() {
        assertEquals(
            "Ảnh gốc · Quân cờ · Ảnh nhân vật",
            CaptureHistory.description(
                "run-1/anh-goc/quan-co/anh-quan-co/violet-head.jpg"
            )
        )
        assertEquals(
            "Ảnh gốc · Quân cờ · Ảnh chiêu",
            CaptureHistory.description(
                "run-1/anh-goc/quan-co/chieu-quan-co/violet-skill-2.png"
            )
        )
        assertEquals(
            "Ảnh gốc · Trang bị",
            CaptureHistory.description(
                "run-1/anh-goc/trang-bi/trang-bi-icon-3.webp"
            )
        )
        assertEquals(
            "Ảnh gốc · Chúc Phúc · Thường",
            CaptureHistory.description(
                "run-1/anh-goc/chuc-phuc/thuong/giay-phut-huy-hoang.jpg"
            )
        )
        assertEquals("Chiêu 2 · Tóm tắt", CaptureHistory.description("run-1/violet-skill-2.png"))
    }
}
