package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventInviteImePolicyTest {
    private fun node(
        id: Int,
        label: String = "",
        className: String = "android.widget.TextView",
        editable: Boolean = false,
        focused: Boolean = false,
        password: Boolean = false,
        left: Double = .30,
        top: Double = .26,
        right: Double = .72,
        bottom: Double = .38,
        enabled: Boolean = true,
        visible: Boolean = true
    ) = EventInviteImePolicy.Node(
        id,
        label,
        className,
        editable,
        password,
        focused,
        visible,
        enabled,
        left,
        top,
        right,
        bottom
    )

    @Test fun selectWhiteEditorAboveKeyboardAndSeparateOkButton() {
        val screenshotLayout = listOf(
            node(
                0,
                className = "android.inputmethodservice.ExtractEditText",
                editable = true,
                focused = true,
                left = .04,
                top = .237,
                right = .82,
                bottom = .40
            ),
            node(1, "OK", left = .86, top = .27, right = .93, bottom = .37),
            node(2, "q", left = .16, top = .62, right = .24, bottom = .74),
            node(3, "Xác nhận", left = .44, top = .61, right = .57, bottom = .74)
        )
        assertEquals(0, EventInviteImePolicy.editor(screenshotLayout))
        assertEquals(1, EventInviteImePolicy.okButton(screenshotLayout))
        assertTrue(EventInviteImePolicy.keyboardTrusted("com.keyboard", "com.keyboard", true, false))
    }

    @Test fun editorMustBeFromSelectedImeAndGameMustRemainBehind() {
        assertFalse(EventInviteImePolicy.keyboardTrusted("com.keyboard", "com.other", true, false))
        assertFalse(EventInviteImePolicy.keyboardTrusted("com.keyboard", "com.keyboard", false, false))
        assertFalse(EventInviteImePolicy.keyboardTrusted("com.keyboard", "com.keyboard", true, true))
        assertFalse(EventInviteImePolicy.keyboardTrusted(null, "com.keyboard", true, false))
    }

    @Test fun neverTargetPasswordFieldOrGameButtonOrArbitraryDialog() {
        assertNull(EventInviteImePolicy.editor(listOf(node(0, editable = true, focused = true, password = true))))
        assertNull(
            EventInviteImePolicy.editor(
                listOf(node(0, editable = true, focused = true, top = .60, bottom = .78))
            )
        )
        assertNull(EventInviteImePolicy.okButton(listOf(node(4, "Xác nhận", left = .84, right = .93))))
        assertNull(EventInviteImePolicy.okButton(listOf(node(5, "OK", left = .43, right = .60))))
        assertNull(EventInviteImePolicy.okButton(listOf(node(6, "OK", left = .84, right = .94, enabled = false))))
        assertNull(EventInviteImePolicy.okButton(listOf(node(7, "OK", left = .84, right = .94, visible = false))))
    }

    @Test fun rejectUnstableKeyboardFieldAndAmbiguousOkTargets() {
        val twoEditors = listOf(
            node(0, className = "EditText", editable = true),
            node(1, className = "EditText", editable = true, left = .1, right = .33)
        )
        assertNull(EventInviteImePolicy.editor(twoEditors))
        val unrelatedButtons = listOf(
            node(2, "OK", left = .80, right = .87),
            node(3, "OK", left = .91, right = .97)
        )
        assertNull(EventInviteImePolicy.okButton(unrelatedButtons))
    }

    @Test fun anImeStillOpenCanNeverAuthorizeGameSubmit() {
        assertFalse(EventInviteImePolicy.gameReady(true, true, true))
        assertFalse(EventInviteImePolicy.gameReady(false, false, true))
        assertFalse(EventInviteImePolicy.gameReady(false, true, false))
        assertTrue(EventInviteImePolicy.gameReady(false, true, true))
    }
}
