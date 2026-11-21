package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GamePopupsTest {
    private fun word(text: String, x: Double = .5, y: Double = .7) = GameScreens.Word(text, x - .03, y - .02, x + .03, y + .02)

    @Test fun readsTheLinesThatSayATapLeaves() {
        val lines = listOf(
            "Ấn để tiếp tục",
            "Chạm để tiếp tục",
            "Nhấn vào màn hình để tiếp tục",
            // As ML Kit read the user's reward screens.
            "* Ấn để iếp tục",
            "Ấn đế tiếp tục",
            "Ấn dể tiếp tục",
            "n để tiếp tục",
            "Chạm vào vùng trống để đóng",
            "Nhấn vào bất kỳ đâu để đóng",
            "Bấm ra ngoài để thoát",
            "Nhấn để bỏ qua"
        )
        for (line in lines) assertNotNull(line, GamePopups.tapToLeave(listOf(word(line, y = .91))))
    }

    @Test fun otherLinesAreNotTapsToLeave() {
        val lines = listOf(
            "Tiếp tục",
            "Tiếp tục mua",
            "Nhấn lần nữa để thoát",
            "Chạm để xem chi tiết",
            "Đóng",
            "Hoàn thành thêm ba trận đấu xếp hạng trong tuần này để tiếp tục nhận thưởng"
        )
        for (line in lines) assertNull(line, GamePopups.tapToLeave(listOf(word(line, y = .91))))
    }

    @Test fun aTapLineMustBeLowInTheMiddle() {
        assertNull(GamePopups.tapToLeave(listOf(word("Ấn để tiếp tục", y = .40))))
        assertNull(GamePopups.tapToLeave(listOf(word("Ấn để tiếp tục", x = .95, y = .90))))
    }

    @Test fun findsTheUpdateNotesTitleAndItsX() {
        val title = GamePopups.updateNotes(listOf(word("Trang chủ", .10, .35), word("Ghi chú cập nhật", .34, .186)))!!
        assertEquals(.719 to .186, GamePopups.updateNotesClose(title, .719 to .186))
        // Slightly off where it was measured: the X found in the pixels is believed.
        assertEquals(.74 to .19, GamePopups.updateNotesClose(title, .74 to .19))
        // None found, or an icon of the dimmed top bar: the measured place on the title's row.
        assertEquals(.719 to .186, GamePopups.updateNotesClose(title, null))
        assertEquals(.719 to .186, GamePopups.updateNotesClose(title, .9075 to .042))
        // One letter misread or lost is still the title.
        assertNotNull(GamePopups.updateNotes(listOf(word("Ghi chú cập nhậ", .34, .186))))
        assertNotNull(GamePopups.updateNotes(listOf(word("Gi chú cập nhật", .34, .186))))
        // Words of a page, not its title.
        assertNull(GamePopups.updateNotes(listOf(word("Ghi chú cập nhật", .5, .7))))
        assertNull(GamePopups.updateNotes(listOf(word("Ghi chú", .34, .186))))
    }

    @Test fun picksTheButtonThatOnlyLeaves() {
        assertEquals("Đã rõ", GamePopups.button(listOf(word("Đã rõ")))?.text)
        assertEquals("Nhận thưởng", GamePopups.button(listOf(word("Nhận thưởng")))?.text)
        assertEquals("Đóng", GamePopups.button(listOf(word("Đóng")))?.text)
        assertEquals("Bỏ qua", GamePopups.button(listOf(word("Bỏ qua")))?.text)
        // Acknowledging before claiming before backing out.
        assertEquals("Đã hiểu", GamePopups.button(listOf(word("Đóng"), word("Nhận"), word("Đã hiểu")))?.text)
        assertEquals("Nhận", GamePopups.button(listOf(word("Đóng"), word("Nhận")))?.text)
    }

    @Test fun aLoneConfirmIsPressedOnlyOnADialogThatCommitsToNothing() {
        assertEquals("Xác nhận", GamePopups.button(listOf(word("Chúc mừng bạn", y = .3), word("Xác nhận")))?.text)
        assertEquals("Tiếp tục", GamePopups.button(listOf(word("Tiếp tục")))?.text)
        // The season of the lobby behind is not a purchase.
        assertEquals("Xác nhận", GamePopups.button(listOf(word("Mùa hạng mới", y = .3), word("Xác nhận")))?.text)
    }

    @Test fun neverConfirmsWhatSpendsOrChangesSomething() {
        val dialogs = listOf(
            "Dùng 100 Quân huy để mở?",
            "Mua gói quà này?",
            "Bạn có chắc muốn đăng xuất?",
            "Nạp thêm để nhận",
            "Đổi trang phục này?",
            "Thoát game?",
            "Xóa bạn bè?"
        )
        for (text in dialogs) {
            assertNull(text, GamePopups.button(listOf(word(text, y = .4), word("Xác nhận"))))
            assertNull(text, GamePopups.button(listOf(word(text, y = .4), word("Tiếp tục"))))
        }
    }

    @Test fun refusesRatherThanConfirms() {
        assertEquals("Hủy", GamePopups.button(listOf(word("Mua gói quà này?", y = .4), word("Hủy", .4), word("Xác nhận", .6)))?.text)
        assertEquals("Hủy", GamePopups.button(listOf(word("Hủy", .4), word("Tiếp tục", .6)))?.text)
        // Two confirming choices, none to refuse: not this code's to choose.
        assertNull(GamePopups.button(listOf(word("Xác nhận", .4), word("Tiếp tục", .6))))
    }

    @Test fun otherLabelsAreNeverPressed() {
        val labels = listOf(
            "Đồng ý",
            "Bắt đầu",
            "Đấu thường",
            "Đấu hạng",
            "Lập phòng",
            "Mua",
            "Nạp",
            "Đăng xuất",
            "Thoát",
            "Đi đến",
            "Tham gia",
            "Đăng nhập",
            "Nhận thưởng nhiệm vụ mỗi ngày khi hoàn thành"
        )
        for (label in labels) assertNull(label, GamePopups.button(listOf(word(label))))
    }

    @Test fun aButtonMustBeInTheMiddleOfTheScreen() {
        // The side panels and the top bar of a lobby have labels of their own.
        assertNull(GamePopups.button(listOf(word("Nhận", .05, .5))))
        assertNull(GamePopups.button(listOf(word("Nhận", .95, .5))))
        assertNull(GamePopups.button(listOf(word("Đóng", .5, .1))))
    }
}
