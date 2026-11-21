package vn.ndang.aovcollector

import kotlin.math.ceil
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

/**
 * One block of a run of heroes by name ([key], as hero-001): the next of [names] met going down the All-heroes list,
 * wherever it is. Every screen the list rests on is read where it shows a row of names not read yet, or a wanted hero
 * read on it before, and the topmost wanted hero on it is tapped; else the list goes on down by the longest drag that
 * can be measured ([HeroGrid.MAX_STEP]). The names that can be read on one screen ([rows]) span more than a drag, so
 * no row is passed unread, and the heroes are taken in the order the list shows them: the list is passed once.
 *
 * Places saved by earlier runs ([HeroIndex]) do not lead the way: they may come from another account, whose list in
 * the "Thông thạo" order is another, and a new hero moves every card after it. A hero the pass did not read is looked
 * for once, at the card the pass or [HeroIndex] last read its name on ([HeroSearch] without the scan), then left out.
 */
class HeroSweep(
    private val state: State,
    private val names: List<String>,
    private val key: String,
    private val total: () -> Int,
    private val saved: (String) -> Int,
    private val pick: (
        card: Int,
        approve: ListNavigator.Approve?,
        watch: ListNavigator.Watch?,
        then: (ListNavigator.Outcome) -> Unit
    ) -> Unit,
    private val seen: (List<Pair<Int, GameScreens.Word>>) -> Unit = {},
    private val note: (String) -> Unit = {},
    private val finish: (ListNavigator.Outcome) -> Unit
) {
    /**
     * A run's pass, kept with the run: the hero each block took ([picked], block key to name as asked), the names
     * not found on the list ([missing]), the name read on each card, and whether the pass reached the end.
     */
    class State(
        val picked: MutableMap<String, String> = LinkedHashMap(),
        val missing: MutableList<String> = ArrayList(),
        val names: MutableMap<Int, String> = HashMap(),
        var ended: Boolean = false
    ) {
        /**
         * The heroes of [all] no block took and not left out, in their order. A name read as one taken is taken too: the
         * user's "Supenman" after "Superman" found Superman's card again (run of 10/11), and he was photographed twice.
         */
        fun left(all: List<String>): List<String> {
            val gone = (picked.values + missing).map { HeroNames.slug(it) }
            return all.filter { name -> HeroNames.slug(name).let { s -> gone.none { HeroFinder.same(it, s) } } }
        }

        fun json(): JSONObject = JSONObject()
            .put("picked", JSONObject(picked as Map<*, *>))
            .put("missing", JSONArray(missing))
            .put("names", JSONObject(names.mapKeys { it.key.toString() } as Map<*, *>))
            .put("ended", ended)

        companion object {
            fun parse(text: String): State {
                val o = JSONObject(text)
                val s = State(ended = o.optBoolean("ended"))
                o.optJSONObject("picked")?.let { p -> for (k in p.keys()) s.picked[k] = p.getString(k) }
                o.optJSONArray("missing")?.let { m -> for (i in 0 until m.length()) s.missing += m.getString(i) }
                o.optJSONObject("names")?.let { n -> for (k in n.keys()) k.toIntOrNull()?.let { s.names[it] = n.getString(k) } }
                return s
            }
        }
    }

    companion object {
        /** Names are read between these heights: above, the list runs under its header; below, the account's UID. */
        const val READ_TOP = .16
        const val READ_BOTTOM = .95

        /** The rows whose names can be read with the list [offset] screens down, of a list of [total] (0: not known). */
        fun rows(offset: Double, total: Int): IntRange {
            val first = ceil((READ_TOP - HeroFinder.NAME_BELOW - HeroGrid.ROW0 + offset) / HeroGrid.PITCH).toInt().coerceAtLeast(0)
            val last = floor((READ_BOTTOM - HeroFinder.NAME_BELOW - HeroGrid.ROW0 + offset) / HeroGrid.PITCH).toInt()
            return first..(if (total > 0) minOf(last, (total - 1) / 5) else last)
        }
    }

    private val wanted = state.left(names)
    private var found: String? = null

    private fun wantedAs(text: String): String? {
        val s = HeroNames.slug(text)
        return wanted.firstOrNull { HeroFinder.same(s, HeroNames.slug(it)) }
    }

    /** Every card of [row] has a name read: five, or what [total] leaves on the last row. */
    private fun read(row: Int, total: Int): Boolean =
        (0 until if (total > 0) minOf(5, total - row * 5) else 5).all { state.names.containsKey(row * 5 + it) }

    private val watch = ListNavigator.Watch { shot, offset, then ->
        val n = total()
        val rows = rows(offset, n)
        val knownHere = rows.any { r -> (0 until 5).any { c -> state.names[r * 5 + c]?.let(::wantedAs) != null } }
        when {
            // Every card read, none of them wanted: nothing more down there.
            n > 0 && (0..(n - 1) / 5).all { read(it, n) } && state.names.values.none { wantedAs(it) != null } ->
                then(ListNavigator.Sight.Done)
            rows.isEmpty() || (!knownHere && rows.all { read(it, n) }) -> then(ListNavigator.Sight.Next())
            else -> shot.words { words ->
                // Where the names are, the list is: its offset, summed from measured moves, is set right by them.
                val reading = HeroFinder.read(words, offset)
                val drift = reading.line ?: 0.0
                val here = rows(offset - drift, n)
                val cards = reading.cards.filter { it.first / 5 in here && (n <= 0 || it.first < n) }
                if (cards.isNotEmpty()) seen(cards)
                for ((card, w) in cards) state.names[card] = w.text
                val hit = cards.mapNotNull { (card, w) -> wantedAs(w.text)?.let { Triple(card, it, w) } }.minByOrNull { it.first }
                if (hit == null) {
                    then(ListNavigator.Sight.Next(drift))
                } else {
                    found = hit.second
                    note("Thấy ${hit.second} ở ô ${hit.first + 1}")
                    then(ListNavigator.Sight.Tap(hit.third.x, (hit.third.y - .06).coerceIn(HeroGrid.TOP, .93), drift))
                }
            }
        }
    }

    fun start() {
        // The run stopped after this block took its hero: the same hero again, so its photos stay in its folder.
        state.picked[key]?.let { return look(it, again = true) }
        if (wanted.isEmpty()) return finish(ListNavigator.Outcome.End)
        if (state.ended) return look(wanted.first(), again = false)
        pick(ListNavigator.DOWN, null, watch) { o ->
            when (o) {
                is ListNavigator.Outcome.Tapped -> {
                    found?.let { state.picked[key] = it }
                    finish(o)
                }
                is ListNavigator.Outcome.End -> {
                    state.ended = true
                    note("Đã đi hết danh sách; chưa thấy: " + wanted.joinToString(", "))
                    look(wanted.first(), again = false)
                }
                else -> finish(o)
            }
        }
    }

    /** [name] at the card its name was read on, by this pass or a run before: one look, no scan. */
    private fun look(name: String, again: Boolean) {
        val slug = HeroNames.slug(name)
        val hint = state.names.entries.filter { HeroFinder.same(HeroNames.slug(it.value), slug) }.minOfOrNull { it.key } ?: saved(name)
        if (hint < 0) return miss(name, again)
        HeroSearch(name, hint, { card, approve, then -> pick(card, approve, null, then) }, seen, note, scan = false) { o, _ ->
            when (o) {
                is ListNavigator.Outcome.Tapped -> {
                    state.picked[key] = name
                    finish(o)
                }
                is ListNavigator.Outcome.Failed -> finish(o)
                else -> miss(name, again)
            }
        }.start()
    }

    private fun miss(name: String, again: Boolean) {
        if (!again) state.missing += name
        note("Không thấy $name trong danh sách; bỏ qua tướng này")
        finish(ListNavigator.Outcome.End)
    }
}
