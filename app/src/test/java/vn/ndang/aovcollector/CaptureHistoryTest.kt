package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class CaptureHistoryTest {
    @Test fun cleanChessDetailsKeepTheirDescriptionThroughTheFolderMetadata() {
        assertEquals("Quân cờ · Chi tiết · Trang 2",CaptureHistory.description(
            "run-1/quan-co/tat-ca-quan-co/tuong/violet/violet-detail-2.png"))
        assertEquals("Ảnh chụp màn hình",CaptureHistory.description("run-1/violet-detail-2.png"))
    }

    @Test fun legacyDescriptionsAndNativeGroupsStayCompatible() {
        assertEquals("Tổng quan",CaptureHistory.description("run-1/hero-001-overview.png"))
        assertEquals("Chiêu 2 · Chi tiết",CaptureHistory.description("run-1/hero-001-skill-2-detail.png"))
        assertEquals("Tất cả quân cờ · Trang 1",CaptureHistory.description(
            "run-1/chess__quan-co__tat-ca-quan-co__tuong__violet__001.png"))
        assertEquals("Ảnh gốc · Quân cờ · Ảnh nhân vật",CaptureHistory.description(
            "anh-goc-co-lien-quan/quan-co/anh-quan-co/run-1/violet-head.jpg"))
    }
}
