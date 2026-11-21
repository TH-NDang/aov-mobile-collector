package vn.ndang.aovcollector

import kotlin.math.abs
import org.json.JSONObject

/**
 * The part of an event-code task that belongs to one event. Everything else is shared by [EventInviteRunner] and
 * does not change from one event to the next: each saved account signed out and in, the way from the lobby to Sự
 * kiện, finding the event by its name in the list on the left, gift and guide popups on the way, the ledger that
 * never sends a code twice, popups after the code, and the way back to the lobby.
 *
 * [Anniversary] is Thập kỷ chung sức's "Hỗ trợ bạn bè" popup, kept exactly as it was. A [Field] is an event whose page
 * has its own code box: the event's name in the list, the tab of its page that shows the box (none when the page
 * opens on it), a word of the box's hint, and the label of the button beside it. A new event of that kind needs no
 * new code: its names are typed in the task's form. One that works differently gets a recipe of its own.
 */
sealed class EventCodeRecipe {
    abstract val label: String

    /** Shown while looking for it, and saved in the macro. */
    abstract fun json(): JSONObject

    object Anniversary : EventCodeRecipe() {
        override val label = "Thập kỷ chung sức"

        override fun json(): JSONObject = JSONObject().put("kind", "anniversary")
    }

    /** Where a code goes on a [Field] event: the box to tap, the strip its text is read back from, the button. */
    class Target(val box: GameScreens.Word, val button: GameScreens.Word) {
        val tap get() = box.x to box.y

        /** From the box's left to the button, a little above and below: where the typed code shows. */
        val strip get() = doubleArrayOf(maxOf(0.0, box.left - .02), box.y - .04, button.left, box.y + .04)
    }

    data class Field(val event: String, val tab: String = "", val hint: String = HINT, val submit: String = SUBMIT) : EventCodeRecipe() {
        override val label get() = event + if (tab.isBlank()) "" else " · $tab"

        override fun json(): JSONObject =
            JSONObject().put("kind", "field").put("event", event).put("tab", tab).put("hint", hint).put("submit", submit)

        private val eventKey = GameScreens.plain(event)
        private val tabKey = GameScreens.plain(tab)
        private val hintKey = GameScreens.plain(hint)
        private val submitKey = GameScreens.plain(submit)

        /** The event's line in the list on the left; "-Ủy Thác của Shuna-" reads with its dashes. */
        fun item(words: List<GameScreens.Word>) = words.filter {
            it.x < .30 &&
                it.y in .10..0.96 &&
                eventKey.isNotEmpty() &&
                it.plain.contains(eventKey)
        }
            .minByOrNull { it.plain.length }

        /** The tab that shows the code box, along the bottom of the page. */
        fun tab(words: List<GameScreens.Word>) = if (tabKey.isEmpty()) {
            null
        } else {
            words.filter { it.x in .30..0.90 && it.y > .60 && it.plain.contains(tabKey) }.minByOrNull { it.plain.length }
        }

        /** The code box, by a word of its hint, on the page's right part. */
        fun box(words: List<GameScreens.Word>) = words.filter { it.x in .30..0.88 && it.y in .45..0.95 && it.plain.contains(hintKey) }
            .maxByOrNull { it.right - it.left }

        /** The button on the box's row, right of it: the whole word is its label. */
        fun button(words: List<GameScreens.Word>, box: GameScreens.Word) = words.firstOrNull {
            it.plain == submitKey && it.x > box.x && it.x < .95 && abs(it.y - box.y) < .045
        }

        fun target(words: List<GameScreens.Word>): Target? {
            val box = box(words) ?: return null
            return button(words, box)?.let { Target(box, it) }
        }

        /** The page is this event's: its code box, or its tab, shows. */
        fun page(words: List<GameScreens.Word>) = box(words) != null || tab(words) != null

        /**
         * Some page of Sự kiện, by its list on the left whose names are written "-Ủy Thác của Shuna-": still known
         * when dragging the list has taken "Tổng hợp sự kiện" off the screen.
         */
        fun list(words: List<GameScreens.Word>) = words.count {
            it.x < .30 && it.y in .10..0.96 && it.text.trim().let { t -> t.length > 3 && (t.startsWith("-") || t.endsWith("-")) }
        } >= 2

        /**
         * Codes taken so far out of how many, as the tab says it ("Nhiệm vụ chia sẻ 0/2"), read on the tab's line or
         * just under it. Null when not read: no number elsewhere on the page counts.
         */
        fun quota(words: List<GameScreens.Word>): Pair<Int, Int>? {
            val tab = tab(words) ?: return null
            val ratio = Regex("""(\d{1,2})\s*/\s*(\d{1,2})""")
            return words.filter { abs(it.x - tab.x) < .10 && it.y in (tab.y - .03)..(tab.y + .07) }
                .mapNotNull { w -> ratio.find(w.text)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() } }
                .filter { it.second in 1..20 && it.first in 0..it.second }
                .distinct()
                .singleOrNull()
        }

        /** The game says the code was not taken. */
        fun rejected(words: List<GameScreens.Word>) = words.any { w ->
            w.y in .15..0.92 && !w.plain.contains(hintKey) && REFUSALS.any { w.plain.contains(it) }
        }

        /** The game says the code was taken. */
        fun accepted(words: List<GameScreens.Word>) = words.any { w -> w.y in .15..0.92 && ACCEPTS.any { w.plain.contains(it) } }
    }

    companion object {
        const val HINT = "Nhập mã"
        const val SUBMIT = "Dùng"

        /** Ủy Thác của Shuna (Liên Quân 10 tuổi): the friend's code on the Nhiệm vụ chia sẻ tab, then Dùng. */
        val SHUNA = Field("Ủy Thác của Shuna", "Nhiệm vụ chia sẻ")

        /** What events offered in the form. */
        val PRESETS = listOf(Anniversary, SHUNA)

        private val REFUSALS = listOf(
            "khonghople",
            "khongtontai",
            "saima",
            "hethan",
            "datoida",
            "dadungma",
            "dasudung",
            "khongthe",
            "chinhminh",
            "loihethong",
            "thulai"
        )
        private val ACCEPTS = listOf("thanhcong", "danhannhiemvu", "nhannhiemvuthanh")

        fun from(json: JSONObject?): EventCodeRecipe {
            val r = json ?: return Anniversary
            return when (r.optString("kind")) {
                "field" -> Field(
                    r.optString("event").trim(),
                    r.optString("tab").trim(),
                    r.optString("hint").trim().ifEmpty { HINT },
                    r.optString("submit").trim().ifEmpty { SUBMIT }
                ).takeIf { GameScreens.plain(it.event).length >= 3 } ?: Anniversary
                else -> Anniversary
            }
        }
    }
}
