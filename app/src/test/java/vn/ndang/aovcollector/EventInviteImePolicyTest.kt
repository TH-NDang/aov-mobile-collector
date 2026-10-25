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

    private fun window(
        id: Int,
        owner: String = "game",
        ime: Boolean = false,
        focused: Boolean = true,
        nodes: List<EventInviteImePolicy.Node> = nativeDialog()
    ) = EventInviteImePolicy.EditorWindow(id, owner, ime, focused, nodes)

    private fun nativeDialog() = listOf(
        node(0, className = "android.widget.EditText", editable = true, focused = true, left = .04, right = .82),
        node(1, "OK", left = .86, right = .93)
    )

    @Test fun unityOwnsEditorWhileSelectedKeyboardOwnsOnlyKeys() {
        val keyboard = window(0, "keyboard", ime = true, nodes = listOf(node(0, "q", top = .62, bottom = .74)))
        assertEquals(1, EventInviteImePolicy.editorWindow(listOf(keyboard, window(1)), "keyboard", "game"))
    }

    @Test fun selectedImeExtractEditorRemainsSupported() {
        val keyboard = window(0, "keyboard", ime = true, nodes = nativeDialog().take(1))
        val game = window(1, nodes = emptyList())
        assertEquals(0, EventInviteImePolicy.editorWindow(listOf(keyboard, game), "keyboard", "game"))
    }

    @Test fun rejectForeignUnfocusedUnpairedAndMisalignedGameEditors() {
        for (candidate in listOf(
            window(0, owner = "foreign"),
            window(0, focused = false),
            window(0, nodes = nativeDialog().take(1)),
            window(0, nodes = listOf(nativeDialog()[0], node(1, "OK", left = .86, right = .93, top = .44, bottom = .50))),
            window(0, nodes = listOf(nativeDialog()[0].copy(focused = false), nativeDialog()[1])),
            window(0, "foreign", ime = true)
        )) {
            assertNull(EventInviteImePolicy.editorWindow(listOf(candidate), "keyboard", "game"))
        }
    }

    @Test fun ambiguousOwnersAndRootlessWindowsNeverAuthorizeInput() {
        assertNull(EventInviteImePolicy.editorWindow(listOf(window(0), window(1)), "keyboard", "game"))
        assertNull(EventInviteImePolicy.editorWindow(listOf(window(0, nodes = emptyList())), "keyboard", "game"))
        assertNull(
            EventInviteImePolicy.editorWindow(
                listOf(window(0), window(1, "keyboard", ime = true)),
                "keyboard",
                "game"
            )
        )
    }

    @Test fun anImeStillOpenCanNeverAuthorizeGameSubmit() {
        assertFalse(EventInviteImePolicy.gameReady(true, true, true))
        assertFalse(EventInviteImePolicy.gameReady(false, false, true))
        assertFalse(EventInviteImePolicy.gameReady(false, true, false))
        assertTrue(EventInviteImePolicy.gameReady(false, true, true))
    }
}
