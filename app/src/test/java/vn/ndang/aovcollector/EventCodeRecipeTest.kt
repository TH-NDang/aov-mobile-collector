package vn.ndang.aovcollector

import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An event with its own code box, Ủy Thác của Shuna's Nhiệm vụ chia sẻ, read from words placed as on the user's
 * screenshots; then the shared runner taking it for several accounts on a scripted game.
 */
class EventCodeRecipeTest {
    private fun w(text: String, x: Double, y: Double, half: Double = .06) = GameScreens.Word(text, x - half, y - .018, x + half, y + .018)

    private val sidebar = listOf(
        w("-Tổng hợp sự kiện Sinh nhật Liên Quân 10 tuổi-", .158, .165, .06),
        w("-Kho Báu Thời Gian-", .155, .392),
        w("-Benimaru X Veldora Tempest-", .155, .50),
        w("-Qi x Milim-", .14, .62)
    )
    private val shunaItem = w("-Ủy Thác của Shuna-", .152, .279)
    private val tabs = listOf(w("Nhiệm vụ hàng ngày", .512, .873), w("Nhiệm vụ chia sẻ", .64, .872), w("0/2", .64, .907, .015))
    private val daily = sidebar + shunaItem + tabs + listOf(
        w("huyền thoại", .38, .10),
        w("Đăng nhập hàng ngày", .39, .55),
        w("Nhận", .44, .66, .03),
        w("Làm", .63, .73, .03)
    )
    private val share = sidebar + shunaItem + tabs + listOf(
        w("Nhiệm vụ chia sẻ", .465, .415),
        w("Nhiệm vụ chia sẻ", .69, .415),
        GameScreens.Word("Nhập mã để nhận nhiệm vụ do bạn bè chia sẻ", .433, .776, .695, .812),
        w("Dùng", .7955, .794, .02)
    )

    @Test fun readsShunasPagesAsPlaced() {
        val r = EventCodeRecipe.SHUNA
        assertEquals(.279, r.item(daily)!!.y, .001)
        // The tab along the bottom, not the cards titled the same above it.
        assertEquals(.872, r.tab(share)!!.y, .001)
        assertNull(r.target(daily))
        val target = r.target(share)!!
        assertEquals(.564, target.tap.first, .01)
        assertEquals(.794, target.tap.second, .01)
        assertEquals(.7955, target.button.x, .001)
        // The code is read back between the box's start and the button.
        assertTrue(target.strip[0] < .433 && target.strip[2] <= target.button.left)
        assertEquals(0 to 2, r.quota(daily))
        assertEquals(0 to 2, r.quota(share))
        assertTrue(r.page(daily) && r.page(share))
        assertTrue(r.list(sidebar))
        // The daily tab's claim button is never the code's button.
        assertNull(r.button(daily, daily.first { it.text == "Đăng nhập hàng ngày" }))
    }

    @Test fun theQuotaIsReadOnlyOnTheTab() {
        val r = EventCodeRecipe.SHUNA
        assertEquals(1 to 2, r.quota(share.map { if (it.text == "0/2") w("1/2", .64, .907, .015) else it }))
        // A number elsewhere on the page does not count; an unreadable tab has no quota.
        assertNull(r.quota(share.filter { it.text != "0/2" } + w("4800/15200", .80, .19)))
    }

    @Test fun acceptedAndRefusedAreToldApart() {
        val r = EventCodeRecipe.SHUNA
        assertTrue(r.rejected(share + w("Mã không hợp lệ", .5, .5)))
        assertTrue(r.rejected(share + w("Không thể dùng mã của chính mình", .5, .5)))
        assertFalse(r.rejected(share))
        assertTrue(r.accepted(share + w("Nhận nhiệm vụ thành công", .5, .5)))
        assertFalse(r.accepted(share))
    }

    @Test fun recipesAreSavedInTheMacroWithoutTheCode() {
        val key = "a".repeat(64)
        val fingerprint = "b".repeat(64)
        val shuna = Macro(BuiltInMacros.eventInvite(listOf(key), fingerprint, EventCodeRecipe.SHUNA))
        assertEquals(EventCodeRecipe.SHUNA, BuiltInMacros.eventRecipe(shuna.json))
        assertEquals(EventCodeRecipe.SHUNA, BuiltInMacros.eventRecipe(shuna.steps.getJSONObject(0)))
        val custom = EventCodeRecipe.Field("Sự kiện mới", "", "Nhập mã quà", "Đổi")
        assertEquals(custom, EventCodeRecipe.from(custom.json()))
        // Macros made before the choice are Thập kỷ chung sức.
        val old = JSONObject(BuiltInMacros.eventInvite(listOf(key), fingerprint)).apply { remove("eventRecipe") }
        old.getJSONArray("steps").getJSONObject(0).remove("recipe")
        assertEquals(EventCodeRecipe.Anniversary, BuiltInMacros.eventRecipe(old))
        // A name too short to find is not a recipe.
        assertEquals(EventCodeRecipe.Anniversary, EventCodeRecipe.from(JSONObject().put("kind", "field").put("event", "a")))
    }

    /** The game for a [EventCodeRecipe.SHUNA] run: [used] codes already taken per account, what the button does. */
    private inner class Game(
        val accounts: Int = 2,
        val used: List<Int> = List(accounts) { 0 },
        val result: String = "count",
        val hidden: Boolean = false,
        val giftOnLogin: Boolean = false
    ) : EventInviteRunner.Screen {
        var state = "lobby"
        var account = -1
        var taken = 0
        var listDown = 0
        var popup: String? = null
        val submits = ArrayList<Int>()
        val typed = ArrayList<Int>()
        val kept = ArrayList<String>()
        val claims = ArrayList<Pair<Double, Double>>()
        var journal: JSONObject? = null
        var failure: String? = null
        var finished = false
        val queue = ArrayDeque<() -> Unit>()
        override val alive get() = !finished

        val keys = List(accounts) { "%064x".format(it + 1) }

        private fun shown(): List<GameScreens.Word> {
            popup?.let {
                return when (it) {
                    "gift" -> listOf(w("Quà đăng nhập", .5, .3), w("Chạm vào vùng trống để đóng", .5, .9, .12))
                    "reward" -> listOf(w("Chúc mừng", .5, .2), w("Bạn đã nhận được", .5, .3), w("Chạm để tiếp tục", .5, .85))
                    "refused" -> share + w("Mã không hợp lệ", .55, .5)
                    else -> emptyList()
                }
            }
            val list =
                sidebar.filter { !hidden || listDown == 0 || it.y > .30 } + if (!hidden || listDown > 0) listOf(shunaItem) else emptyList()
            val quota = w("$taken/2", .64, .907, .015)
            return when (state) {
                "lobby" -> listOf(w("Sự kiện", .82, .247))
                "events" -> list + listOf(w("Tổng hợp sự kiện", .5, .2))
                "daily" -> list + daily.filter { it !in sidebar && it != shunaItem && it.text != "0/2" } + quota
                "share" -> list + share.filter { it !in sidebar && it != shunaItem && it.text != "0/2" } + quota
                else -> emptyList()
            }
        }

        override fun loadLedger() = journal?.let { JSONObject(it.toString()) }

        override fun saveLedger(journal: JSONObject): Boolean {
            this.journal = JSONObject(journal.toString())
            return true
        }

        override fun login(key: String, then: (Boolean, String?) -> Unit) {
            account = keys.indexOf(key)
            taken = used[account]
            state = "lobby"
            listDown = 0
            popup = if (giftOnLogin) "gift" else null
            queue.addLast { then(true, null) }
        }

        override fun look(then: (EventInviteRunner.Look?) -> Unit) = queue.addLast {
            val words = shown()
            val scene = EventInviteScreens.classify(words, state == "lobby" && popup == null)
            then(object : EventInviteRunner.Look {
                override val scene = scene

                override fun done(keep: String?) {
                    if (keep != null) kept += keep
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    kept += name
                    then(true)
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            fun near(a: Double, b: Double, d: Double = .03) = abs(a - b) < d
            when {
                popup != null -> popup = if (popup == "refused") null else null
                state == "lobby" && near(x, .82) && near(y, .247) -> state = "events"
                state != "lobby" && near(x, .083) && near(y, .057) -> state = "lobby"
                state != "lobby" && x < .30 && near(y, .279) -> state = "daily"
                state != "lobby" && near(x, .64) && near(y, .872) -> state = "share"
                state != "lobby" && near(x, .512) && near(y, .873) -> state = "daily"
                state == "share" && near(x, .7955) && near(y, .794) -> {
                    submits += account
                    when (result) {
                        "count" -> taken++
                        "reward" -> {
                            taken++
                            popup = "reward"
                        }
                        "refused" -> popup = "refused"
                    }
                }
                near(x, .44) && near(y, .66) -> claims += x to y
            }
            queue.addLast { then(true) }
        }

        override fun typeCode(
            code: String,
            target: EventCodeRecipe.Target,
            page: (List<GameScreens.Word>) -> Boolean,
            then: (Boolean, String?) -> Unit
        ) {
            val ok = state == "share" && popup == null && page(shown()) && abs(target.tap.second - .794) < .02
            if (ok) typed += account
            queue.addLast { then(ok, if (ok) null else "not on the box") }
        }

        override fun drag(x: Double, from: Double, to: Double, then: (Boolean) -> Unit) {
            if (from > to) listDown++ else listDown = 0
            queue.addLast { then(true) }
        }

        override fun typeInviteCode(code: String, then: (Boolean, String?) -> Unit) = error("the anniversary popup is not this event's")

        override fun capture(name: String, then: (Boolean) -> Unit) {
            kept += name
            queue.addLast { then(true) }
        }

        override fun later(ms: Long, then: () -> Unit) {
            queue.addLast(then)
        }

        override fun say(message: String) {}

        fun run(): Game {
            EventInviteRunner(keys, "SHARE123", "c".repeat(64), this, EventCodeRecipe.SHUNA) {
                failure = it
                finished = true
            }.start()
            var steps = 0
            while (queue.isNotEmpty() && !finished) {
                check(++steps < 50_000) { "stuck" }
                queue.removeFirst().invoke()
            }
            return this
        }

        fun status(i: Int) = journal!!.getJSONObject("results").getJSONObject(keys[i]).getString("status")
    }

    @Test fun eachAccountFindsTheEventTypesTheCodeOnceAndGoesBack() {
        val game = Game(accounts = 3).run()
        assertTrue(game.finished)
        assertNull(game.failure)
        assertEquals(listOf(0, 1, 2), game.submits)
        assertEquals(listOf(0, 1, 2), game.typed)
        assertEquals((0 until 3).map { "done" }, (0 until 3).map { game.status(it) })
        assertTrue(game.kept.containsAll(listOf("event-01-code-done", "event-02-code-done", "event-03-code-done")))
        // The daily tab's Nhận is not this task's to press.
        assertTrue(game.claims.isEmpty())
    }

    @Test fun anAccountThatHasTakenAllItsCodesIsNotTypedInto() {
        val game = Game(accounts = 2, used = listOf(2, 0)).run()
        assertNull(game.failure)
        assertEquals(listOf(1), game.submits)
        assertEquals(listOf("used", "done"), listOf(game.status(0), game.status(1)))
    }

    @Test fun aGiftAfterTheCodeIsKeptAndCounted() {
        val game = Game(accounts = 1, result = "reward").run()
        assertNull(game.failure)
        assertEquals("done", game.status(0))
        assertEquals(1, game.journal!!.getInt("gifts"))
        assertTrue(game.kept.contains("event-01-code-reward"))
    }

    @Test fun aRefusedCodeIsRecordedAndNotSentAgain() {
        val game = Game(accounts = 2, result = "refused").run()
        assertNull(game.failure)
        assertEquals(listOf(0, 1), game.submits)
        assertEquals(listOf("rejected", "rejected"), listOf(game.status(0), game.status(1)))
    }

    @Test fun theEventIsFoundFurtherDownTheListAndPastAGift() {
        val game = Game(accounts = 1, hidden = true, giftOnLogin = true).run()
        assertNull(game.failure)
        assertEquals(listOf(0), game.submits)
        assertTrue(game.listDown > 0 || game.status(0) == "done")
        assertEquals("done", game.status(0))
    }

    @Test fun aSendInterruptedBeforeItsResultIsNeverRepeated() {
        val first = Game(accounts = 1, result = "none")
        first.run()
        // The button pressed, nothing to show for it: stopped with the code marked as sent.
        assertNotNull(first.failure)
        assertEquals(listOf(0), first.submits)
        assertEquals("submit_pending", first.journal!!.getString("phase"))
        val again = Game(accounts = 1, result = "none").apply { journal = first.journal }
        again.run()
        assertTrue(again.submits.isEmpty())
        assertTrue(again.typed.isEmpty())
    }
}
