package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class AfterRunProtocolTest {
    private val name = "aov-chess-20261017-150413-654.zip"

    @Test fun onlySuccessfulDriveNotificationForTheExactZipIsAccepted() {
        for (text in listOf("Đã tải lên 1 tệp\n$name", "Upload complete\n$name", "$name uploaded successfully")) {
            assertTrue(text, AfterRunProtocol.driveCompleted(AfterRunProtocol.DRIVE, text, name))
        }
        for (text in listOf(
            "Đang tải lên $name", "Waiting to upload $name", "Upload failed: $name",
            "Chưa tải lên $name", "Không thể tải lên $name", "Upload incomplete $name",
            "Uploaded $name.old", "Uploaded other-$name", "Uploaded aov-other.zip"
        )) {
            assertFalse(text, AfterRunProtocol.driveCompleted(AfterRunProtocol.DRIVE, text, name))
        }
        assertFalse(AfterRunProtocol.driveCompleted("another.app", "Uploaded $name", name))
        assertFalse(AfterRunProtocol.driveCompleted(AfterRunProtocol.DRIVE, "Uploaded $name", ""))
    }

    @Test fun dotAndSendAreRecognizedWithoutMatchingOtherMenuActions() {
        assertTrue(AfterRunProtocol.dot("dot của bạn"))
        assertTrue(AfterRunProtocol.dot("Your dot"))
        assertFalse(AfterRunProtocol.dot("Không gian"))
        assertTrue(AfterRunProtocol.send("Send prompt"))
        assertTrue(AfterRunProtocol.send("Gửi tin nhắn"))
        assertFalse(AfterRunProtocol.send("Send feedback"))
    }
}
