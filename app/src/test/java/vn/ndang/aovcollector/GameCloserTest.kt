package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GameCloserTest {
    /**
     * A Settings app showing an App info page: [label] names the app, [force] is its Buộc dừng button, greyed when
     * the app is not [running]. Pressing it asks first ([asks]) with [cancel] and [yes] buttons, as Android does.
     */
    private class Settings(
        val label: String = "Liên Quân Mobile",
        var running: Boolean = true,
        val asks: Boolean = true,
        val opens: Boolean = true,
        val force: String = "Buộc dừng",
        val yes: String = "OK",
        val cancel: String = "Hủy",
        val title: String = "Buộc dừng?",
        val message: String = "Nếu bạn buộc dừng một ứng dụng, ứng dụng đó có thể hoạt động sai."
    ) : GameCloser.Page {
        var reads = 0
        var asking = false
        var time = 0L
        var result: GameCloser.Result? = null
        var cut = false
        val pressed = ArrayList<String>()
        override val alive get() = !cut

        override fun open() = opens

        private fun button(text: String, enabled: Boolean = true, act: () -> Unit = {}) = GameCloser.Button(text, enabled) {
            pressed += text
            act()
            true
        }

        override fun read(): GameCloser.Window? {
            // The page takes two reads to come up.
            if (++reads <= 2) return null
            if (asking) {
                return GameCloser.Window(
                    listOf(title, message, cancel, yes),
                    listOf(
                        button(cancel) { asking = false },
                        button(yes) {
                            asking = false
                            running = false
                        }
                    )
                )
            }
            return GameCloser.Window(
                listOf(label, "Đã cài đặt", "Mở", "Gỡ cài đặt", force, "Bộ nhớ"),
                listOf(
                    button("Mở"),
                    button("Gỡ cài đặt"),
                    button(force, running) {
                        if (asks) asking = true else running = false
                    },
                    button("Bộ nhớ")
                )
            )
        }

        override fun later(ms: Long, then: () -> Unit) {
            time += ms
            then()
        }

        fun run(game: String = "Liên Quân Mobile"): Settings {
            GameCloser(game, this) { result = it }.start()
            return this
        }
    }

    @Test fun forceStopsTheRunningGameAndConfirms() {
        val s = Settings().run()
        assertEquals(GameCloser.Result.CLOSED, s.result)
        assertEquals(listOf("Buộc dừng", "OK"), s.pressed)
        assertFalse(s.running)
    }

    @Test fun confirmsWhereTheQuestionRepeatsTheButton() {
        // As on Samsung phones, where the question's own button says Buộc dừng; and in English.
        assertEquals(listOf("Buộc dừng", "Buộc dừng"), Settings(yes = "Buộc dừng").run().pressed)
        val english = Settings(force = "FORCE STOP", yes = "OK", cancel = "Cancel", title = "Force stop?").run()
        assertEquals(GameCloser.Result.CLOSED, english.result)
        assertEquals(listOf("FORCE STOP", "OK"), english.pressed)
    }

    @Test fun leavesAGameThatIsNotRunning() {
        val s = Settings(running = false).run()
        assertEquals(GameCloser.Result.NOT_RUNNING, s.result)
        assertEquals(emptyList<String>(), s.pressed)
    }

    @Test fun aPhoneThatDoesNotAsk() {
        val s = Settings(asks = false).run()
        assertEquals(GameCloser.Result.CLOSED, s.result)
        assertEquals(listOf("Buộc dừng"), s.pressed)
    }

    @Test fun touchesNothingOnAnotherAppsPage() {
        // Only the game's page: another app's Buộc dừng is never pressed, and Gỡ cài đặt never on any page.
        val s = Settings(label = "Facebook").run()
        assertEquals(GameCloser.Result.NO_PAGE, s.result)
        assertEquals(emptyList<String>(), s.pressed)
        assertEquals(GameCloser.Result.NO_PAGE, Settings(opens = false).run().result)
    }

    @Test fun noButtonOrNoQuestionLetsTheGameOpenAsBefore() {
        val s = Settings(force = "Lưu trữ").run()
        assertEquals(GameCloser.Result.NO_BUTTON, s.result)
        assertEquals(emptyList<String>(), s.pressed)
        // A question that does not name Buộc dừng is not answered.
        val other = Settings(title = "Bạn có chắc không?", message = "Thay đổi sẽ được lưu.").run()
        assertEquals(GameCloser.Result.NOT_CONFIRMED, other.result)
        assertEquals(listOf("Buộc dừng"), other.pressed)
    }

    @Test fun stopsWhenThePauseCutsIt() {
        val s = Settings()
        s.cut = true
        s.run()
        assertNull(s.result)
        assertEquals(emptyList<String>(), s.pressed)
    }
}
