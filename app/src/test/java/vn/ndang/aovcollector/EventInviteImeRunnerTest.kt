package vn.ndang.aovcollector

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventInviteImeRunnerTest {
    private fun word(label: String, x: Double, y: Double) = GameScreens.Word(label, x - .08, y - .022, x + .08, y + .022)

    private val invite = EventInviteScreens.Scene(
        EventInviteScreens.Kind.INVITE,
        listOf(
            word("Hỗ trợ bạn bè", .5, .25),
            word("Hôm nay đã nhập 0/1 mã", .5, .55),
            word("Xác nhận", .50, .68)
        ),
        0,
        1
    )

    private val reward = EventInviteScreens.Scene(
        EventInviteScreens.Kind.REWARD,
        listOf(word("Vé nhảy", .5, .5)),
        tickets = 19
    )

    private inner class Fake(private val typed: Boolean, private val nextScene: EventInviteScreens.Scene? = null) : EventInviteRunner.Screen {
        override val alive get() = true
        var journal: JSONObject? = null
        var gameConfirmTaps = 0
        var typedCalls = 0
        var savedReceipt = false
        var lastFailure: String? = null
        var output = ""

        override fun loadLedger() = journal?.let { JSONObject(it.toString()) }

        override fun saveLedger(journal: JSONObject): Boolean {
            this.journal = JSONObject(journal.toString())
            return true
        }

        override fun login(key: String, then: (Boolean, String?) -> Unit) = then(true, null)

        override fun look(then: (EventInviteRunner.Look?) -> Unit) {
            val observed = if (gameConfirmTaps > 0) nextScene ?: invite else invite
            then(object : EventInviteRunner.Look {
                override val scene: EventInviteScreens.Scene = observed

                override fun done(keep: String?) {}

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    savedReceipt = true
                    then(true)
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            gameConfirmTaps++
            then(true)
        }

        override fun typeInviteCode(code: String, then: (Boolean, String?) -> Unit) {
            typedCalls++
            then(typed, if (typed) null else "Bàn phím chưa chấp nhận mã và nút OK")
        }

        override fun capture(name: String, then: (Boolean) -> Unit) = then(true)

        override fun later(ms: Long, then: () -> Unit) = then()

        override fun say(message: String) {
            output = message
        }
    }

    private val key = "a".repeat(64)
    private val code = "ExampleCode91"

    @Test fun keyboardFailureCannotCreateSubmittedCheckpointOrSendAnyGameConfirm() {
        val fake = Fake(false)
        var failure: String? = null
        EventInviteRunner(listOf(key), code, "b".repeat(64), fake) { failure = it }.start()
        assertNotNull(failure)
        assertTrue(failure!!.contains("Bàn phím"))
        assertEquals("navigating", fake.journal?.getString("phase"))
        assertEquals(0, fake.gameConfirmTaps)
        assertEquals(0, fake.journal?.getInt("gifts"))
        assertEquals(1, fake.typedCalls)
    }

    @Test fun gameCanOnlySubmitAfterConfirmedKeyboardAndReceipt() {
        val fake = Fake(true, reward)
        var callbackCalled = false
        var error: String? = "not delivered"
        EventInviteRunner(listOf(key), code, "b".repeat(64), fake) {
            callbackCalled = true
            error = it
        }.start()
        assertTrue(callbackCalled)
        assertNull(error)
        assertEquals(1, fake.gameConfirmTaps)
        assertTrue(fake.savedReceipt)
        assertEquals(1, fake.journal?.getInt("gifts"))
        assertEquals(19, fake.journal?.getInt("tickets"))
        assertTrue(fake.journal?.getBoolean("complete") == true)
    }

    @Test fun pendingSubmissionIsNeverRetriedWithAnotherKeyboardEntry() {
        val fake = Fake(false, invite)
        val prior = EventInviteLedger.new(listOf(key), "b".repeat(64))
        prior.put("phase", "submit_pending")
        fake.journal = prior
        var finished: String? = null
        EventInviteRunner(listOf(key), code, "b".repeat(64), fake) { finished = it }.start()
        assertNotNull(finished)
        assertTrue(finished!!.contains("submit_pending"))
        assertEquals(0, fake.typedCalls)
        assertEquals(0, fake.gameConfirmTaps)
        assertEquals(0, fake.journal?.getInt("gifts"))
    }
}
