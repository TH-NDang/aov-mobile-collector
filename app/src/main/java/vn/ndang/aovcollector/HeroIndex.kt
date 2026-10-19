package vn.ndang.aovcollector

import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Heroes seen in the All-heroes list, in the game's order (by default "Theo ngày", newest first): their names
 * and places, to find one again by name; and names of heroes seen elsewhere, place unknown (-1), to choose from.
 * Kept as "place<TAB>name" lines; rebuilt from the saved runs when missing. A place goes stale when the game adds
 * a hero, so the name on the card is always read before it is tapped ([HeroFinder]).
 */
object HeroIndex {
    class Entry(val index: Int, val name: String) {
        val slug get() = HeroNames.slug(name)
    }

    const val FILE = "hero-index.tsv"

    fun parse(text: String): List<Entry> = tidy(
        text.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 2) null else Entry(p[0].trim().toIntOrNull()?.takeIf { it >= 0 } ?: -1, p[1].trim())
        }.toList()
    )

    fun format(entries: List<Entry>) = tidy(entries).joinToString("\n") { "${it.index}\t${it.name}" }

    /** One name a place, placed names in list order, then each other name once, without names that are no name. */
    private fun tidy(entries: List<Entry>): List<Entry> {
        val good = entries.filter {
            it.name.isNotBlank() && it.name.length <= 40 && it.name.count { c -> c.isLetter() } >= 2 && it.slug != "chua-ro-ten"
        }
        val placed = good.filter { it.index >= 0 }.asReversed().distinctBy { it.index }.sortedBy { it.index }
        val known = placed.map { it.slug }.toSet()
        return placed + good.filter { it.index < 0 && it.slug !in known }.distinctBy { it.slug }.sortedBy { it.slug }
    }

    /** [entries] with [name] at [index], replacing what was there; at -1, only a name to choose from. */
    fun put(entries: List<Entry>, index: Int, name: String) =
        tidy(entries.filter { index < 0 || it.index != index } + Entry(maxOf(-1, index), name.trim()))

    /** The places of [query] among [entries], by its letters without accents; a letter off is allowed in long names. */
    fun find(entries: List<Entry>, query: String): List<Entry> {
        val q = HeroNames.slug(query)
        return entries.filter {
            it.index >= 0 &&
                HeroFinder.same(it.slug, q)
        }
    }

    /** Names to choose from, each once, in alphabetical order. */
    fun names(entries: List<Entry>) = entries.distinctBy { it.slug }.sortedBy { it.slug }.map { it.name }

    fun load(dir: File): List<Entry> = try {
        parse(File(dir, FILE).readText())
    } catch (_: Exception) {
        emptyList()
    }

    fun save(dir: File, entries: List<Entry>) {
        try {
            File(dir, FILE).writeText(format(entries))
        } catch (_: Exception) {}
    }

    /** Keeps [name] at [index] of the list, or among the names to choose from at -1. */
    @Synchronized fun record(dir: File, index: Int, name: String) {
        val all = load(dir)
        if (index < 0 &&
            all.any { it.slug == HeroNames.slug(name) }
        ) {
            return
        }
        save(dir, put(all, index, name))
    }

    /** Keeps the names read on the cards of the list; a name already known there stays as read on the hero's page. */
    @Synchronized fun seen(dir: File, cards: List<Pair<Int, String>>) {
        val old = load(dir)
        val now = cards.fold(old) { all, (i, text) ->
            val had = all.firstOrNull { it.index == i }
            if (had != null && HeroFinder.same(had.slug, HeroNames.slug(text))) all else put(all, i, text)
        }
        if (now !== old) save(dir, now)
    }

    /**
     * The index, rebuilt the first time from the runs saved under [captures]: heroes of whole-list runs at their
     * cards (hero-012 is card 11), the latest run last so it wins, and the heroes of other runs as names alone.
     */
    @Synchronized fun loadOrRebuild(dir: File, captures: File): List<Entry> {
        if (File(dir, FILE).exists()) return load(dir)
        val found = ArrayList<Entry>()
        val runs = captures.listFiles()?.filter { it.isDirectory }?.sortedBy { File(it, "run.json").lastModified() }.orEmpty()
        for (run in runs) {
            try {
                val list = org.json.JSONObject(File(run, "run.json").readText()).optString("collectionMode") == "all"
                val folders = org.json.JSONObject(File(run, "hero-folders.json").readText())
                for (key in folders.keys()) {
                    val hero = org.json.JSONObject(File(run, "${folders.getString(key)}/hero.json").readText())
                    if (hero.optString("nameSource") == "unknown") continue
                    val card = if (list) key.removePrefix("hero-").toIntOrNull()?.minus(1) ?: -1 else -1
                    found += Entry(card, hero.optString("name"))
                }
            } catch (_: Exception) {}
        }
        val entries = tidy(found)
        save(dir, entries)
        return entries
    }
}

/**
 * Hero names read on the cards of the All-heroes list, and where to tap for one. A card's name is written across
 * its bottom, 0.10 of the screen height below the card's centre ([HeroGrid.centre]).
 */
object HeroFinder {
    const val NAME_BELOW = .10

    /** Two names are the same with their letters equal, or one letter off in names of five letters or more. */
    fun same(a: String, b: String): Boolean = a == b || (minOf(a.length, b.length) >= 5 && distance(a, b) <= 1)

    private fun distance(a: String, b: String): Int {
        if (abs(a.length - b.length) > 1) return 2
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    /** The names on the cards of a list screenshot, by card index, with the list scrolled [offset] screens down. */
    fun cards(words: List<GameScreens.Word>, offset: Double): List<Pair<Int, GameScreens.Word>> = words.mapNotNull { w ->
        val col = HeroGrid.columns.indices.minByOrNull { abs(HeroGrid.columns[it] - w.x) }!!
        val letters = w.plain.count { it.isLetter() }
        if (abs(HeroGrid.columns[col] - w.x) > .05 || letters < 2 || w.text.contains('/') || w.y < .12 || w.y > .99) return@mapNotNull null
        // Names sit on one line across the cards' bottoms; other text on the art is elsewhere.
        val at = (w.y - NAME_BELOW - HeroGrid.ROW0 + offset) / HeroGrid.PITCH
        val row = at.roundToInt()
        if (row < 0 || abs(at - row) > .25) null else (row * 5 + col) to w
    }

    /**
     * Where to tap for the hero [slug] on a list screenshot: on its card just above the name, the card at [hint]
     * when more than one card carries the name (as two Flowborn cards do). Null when it is not on screen.
     */
    fun spot(words: List<GameScreens.Word>, offset: Double, slug: String, hint: Int): Pair<Int, Pair<Double, Double>>? {
        val found = cards(words, offset).filter { same(HeroNames.slug(it.second.text), slug) }
        val best = found.minByOrNull { abs(it.first - hint) } ?: return null
        return best.first to (best.second.x to (best.second.y - .06).coerceIn(HeroGrid.TOP, .93))
    }
}

/**
 * Looks for the hero [name] on the All-heroes list and taps it: at card [hint] first when known, its name read
 * on the card (or on any card of that screen); else on, two rows a screen, from there to the end of the list
 * (a new hero pushes the others on), then back towards the top, until it is found. [pick] runs a [ListNavigator]
 * for a card with an [ListNavigator.Approve]; [seen] gets every card name read, for [HeroIndex]; [finish] gets
 * how it ended (Tapped, or End when the list has no such hero) and the card tapped.
 */
class HeroSearch(
    private val name: String,
    private val hint: Int,
    private val pick: (card: Int, approve: ListNavigator.Approve, then: (ListNavigator.Outcome) -> Unit) -> Unit,
    private val seen: (List<Pair<Int, GameScreens.Word>>) -> Unit = {},
    private val note: (String) -> Unit = {},
    private val finish: (ListNavigator.Outcome, Int?) -> Unit
) {
    private val slug = HeroNames.slug(name)
    private var found: Int? = null
    private val approve = ListNavigator.Approve { shot, _, _, _, offset, then ->
        shot.words { words ->
            val cards = HeroFinder.cards(words, offset)
            if (cards.isNotEmpty()) seen(cards)
            val spot = HeroFinder.spot(words, offset, slug, maxOf(0, hint))
            if (spot != null) {
                found = spot.first
                note("Tìm thấy $name ở ô ${spot.first + 1}")
            }
            then(spot?.second)
        }
    }

    /** Screen k shows rows 2k and 2k+1, by asking for the first card of row 2k+1. */
    private fun screen(k: Int) = (2 * k + 1) * 5

    /** The first screen with a row past the hint's, which was seen with the row before it. */
    private val from = if (hint < 0) 0 else maxOf(1, (hint / 5 + 1) / 2)

    fun start() {
        if (hint < 0) {
            note("Chưa biết vị trí của $name: dò danh sách từ đầu")
            return on(0)
        }
        pick(hint, approve) { o ->
            when (o) {
                is ListNavigator.Outcome.Missing, is ListNavigator.Outcome.End -> {
                    note("Ô ${hint + 1} không phải $name: dò tiếp danh sách")
                    on(from)
                }
                else -> finish(o, found)
            }
        }
    }

    private fun on(k: Int) {
        pick(screen(k), approve) { o ->
            when (o) {
                is ListNavigator.Outcome.Missing -> on(k + 1)
                is ListNavigator.Outcome.End -> if (from > 0) back(from - 1) else finish(o, null)
                else -> finish(o, found)
            }
        }
    }

    private fun back(k: Int) {
        if (k < 0) return finish(ListNavigator.Outcome.End, null)
        pick(screen(k), approve) { o -> if (o is ListNavigator.Outcome.Missing) back(k - 1) else finish(o, found) }
    }
}
