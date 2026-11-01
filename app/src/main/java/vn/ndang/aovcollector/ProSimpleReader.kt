package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Đề cử Pro by position only: no creator name, rank catalog or replay proof. The recommendations are counted by
 * their "Dùng combo" buttons from the top of the list, half a card per drag so each button is followed from one
 * frame to the next by its own motion. For each of the first [MAX] recommendations it taps Dùng combo and keeps
 * the Chuẩn bị screen as 1, 2…, then applies the first one again so the hero keeps the top recommendation.
 * Something that cannot be followed is written to the run's log and the hero ends with what was taken; the run
 * goes on with the next hero instead of pausing.
 */
class ProSimpleReader(private val screen: Screen, private val state: JSONObject, private val finish: (Outcome) -> Unit) {
    interface Look {
        val words: List<GameScreens.Word>
        val proSelected: Boolean

        fun done(keep: String?)

        fun keep(name: String, then: (Boolean) -> Unit)
    }

    interface Screen {
        val alive: Boolean

        fun look(then: (Look?) -> Unit)

        fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        fun persist(): Boolean

        fun say(text: String) {}

        fun note(text: String) {}
    }

    sealed class Outcome {
        data class Done(val count: Int, val empty: Boolean) : Outcome()

        data class Failed(val reason: String) : Outcome()
    }

    /** A Dùng combo button at [y] and the recommendation's place in the list, counted from 0. */
    data class Row(val y: Double, val rank: Int, val button: GameScreens.Word)

    companion object {
        const val MAX = 5

        /** Two cards fill the list; their buttons are .366 apart. Half of that per drag keeps every button in view twice. */
        const val PITCH = .366
        const val STEP = .17

        /** Half a card down, held still before lifting so the list stops where the finger does. */
        val STEP_DRAG = CalibrationAction(
            "pro.v2.list.step",
            "Pro: cuộn nửa thẻ",
            listOf(CalibrationPoint(.70, .78), CalibrationPoint(.70, .78 - STEP)),
            relative = true,
            durationMs = 600,
            holdMs = 400
        )

        /** A quick flick back towards the top of the list. */
        val TOP_FLICK = CalibrationAction(
            "pro.v2.list.top",
            "Pro: về đầu danh sách",
            listOf(CalibrationPoint(.70, .30), CalibrationPoint(.70, .75)),
            relative = true,
            durationMs = 250
        )

        /** A button the whole of which is in the list, clear of its clipped top and bottom edges. */
        val TAP = .26..0.84

        fun buttons(words: List<GameScreens.Word>): List<GameScreens.Word> = words
            .filter { it.plain == "dungcombo" && it.x in .66..0.86 && it.y in .18..0.92 && it.bottom - it.top < .08 }
            .sortedBy { it.y }
            .fold(ArrayList()) { kept, w ->
                if (kept.isEmpty() || w.y - kept.last().y > .03) kept.add(w)
                kept
            }

        /** At the top of the list the first button is here; the others follow one [PITCH] apart. */
        const val FIRST_Y = .41

        fun stationary(before: List<Row>, after: List<GameScreens.Word>) = before.size == after.size &&
            before.zip(after).all { (a, b) -> abs(a.y - b.y) < .012 }

        /**
         * How far the list moved up between two frames: the shift that lines up most of the old buttons with new
         * ones. A drag of [STEP] moves it less than half a card, so only one shift can fit; null when none does.
         */
        fun shift(before: List<Row>, after: List<GameScreens.Word>): Double? {
            val candidates = before.flatMap { a -> after.map { b -> a.y - b.y } }.filter { it in .02..(PITCH * .75) }
            return candidates.map { d ->
                d to before.count { a -> after.any { b -> abs(a.y - d - b.y) < .025 } }
            }.filter { it.second > 0 }.maxWithOrNull(compareBy<Pair<Double, Int>> { it.second }.thenBy { -abs(it.first - STEP) })?.first
        }

        /**
         * Each button's place from where it sits with the list [scrolled] down from its top, so a button OCR missed
         * cannot shift the count. Null when a button is off the card grid: the list is not where it was thought.
         */
        fun ranked(found: List<GameScreens.Word>, scrolled: Double): List<Row>? {
            val rows = found.map { b ->
                val at = (b.y + scrolled - FIRST_Y) / PITCH
                val rank = at.roundToInt()
                if (rank < 0 || abs(at - rank) * PITCH > .06) return null
                Row(b.y, rank, b)
            }
            return rows.takeIf { r -> r.zipWithNext().all { (a, b) -> b.rank > a.rank } }
        }
    }

    private var ended = false
    private var captured = state.optInt("captured", 0)
    private var total = state.optInt("total", -1)

    private fun end(outcome: Outcome) {
        if (!ended) {
            ended = true
            finish(outcome)
        }
    }

    private fun fail(reason: String) = end(Outcome.Failed(reason))

    private fun save(): Boolean {
        state.put("captured", captured).put("total", total)
        return screen.persist().also { if (!it) fail("Không lưu được tiến độ Pro; đã dừng trước thao tác tiếp theo") }
    }

    private fun look(then: (Look) -> Unit) {
        if (!screen.alive || ended) return
        screen.look { l ->
            when {
                !screen.alive || ended -> l?.done(null)
                l == null -> fail("Không đọc được màn hình Pro")
                else -> then(l)
            }
        }
    }

    private fun later(ms: Long = 900, then: () -> Unit) = screen.later(ms) { if (screen.alive && !ended) then() }

    private fun tap(word: GameScreens.Word, id: String, wait: Long = 900, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(CalibrationAction("pro.$id", "Pro: $id", listOf(CalibrationPoint(word.x, word.y)), true), word) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) fail("Không chạm được $id") else later(wait, then)
        }
    }

    /** [down]: the list moves up to show later cards; otherwise a quick flick back towards the top. */
    private fun drag(down: Boolean, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(if (down) STEP_DRAG else TOP_FLICK, null) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) fail("Không cuộn được danh sách Pro") else later(if (down) 900 else 700, then)
        }
    }

    fun start() {
        if (state.optBoolean("done")) return end(Outcome.Done(captured, state.optBoolean("empty")))
        next()
    }

    private fun next() {
        if (captured >= MAX || (total >= 0 && captured >= total)) return restore()
        screen.say("Pro: lấy đề cử ${captured + 1}" + if (total >= 0) "/${minOf(total, MAX)}" else "")
        open { top { rows -> seek(captured, rows) } }
    }

    /** From Chuẩn bị or the popup, to the Đề cử Pro tab. */
    private fun open(tries: Int = 0, then: () -> Unit): Unit = look { l ->
        val popup = ProComboScreens.popup(l.words)
        val tab = ProComboScreens.proTab(l.words)
        val other = ProComboScreens.otherButton(l.words)
        when {
            popup && l.proSelected -> {
                l.done(null)
                then()
            }
            tries >= 4 -> {
                l.done("pro-open-unknown")
                fail("Không mở được Phương án khác → Đề cử Pro")
            }
            popup && tab != null -> {
                l.done(null)
                tap(tab, "v1.tab") { open(tries + 1, then) }
            }
            !popup && ProComboScreens.preparation(l.words) && other != null -> {
                l.done(null)
                tap(other, "v1.other", 1200) { open(tries + 1, then) }
            }
            else -> {
                l.done(null)
                later { open(tries + 1, then) }
            }
        }
    }

    /** Flicks back until the buttons stop moving on the card grid; null when they never line up with it. */
    private fun top(drags: Int = 0, previous: List<Row>? = null, then: (List<Row>?) -> Unit): Unit = look { l ->
        val found = buttons(l.words)
        l.done(null)
        val rows = ranked(found, 0.0)
        if (rows != null && previous != null && stationary(previous, found)) return@look then(rows)
        if (drags >= 6) return@look then(null)
        drag(false) { top(drags + 1, rows ?: found.mapIndexed { i, b -> Row(b.y, i, b) }, then) }
    }

    /**
     * Drags half a card at a time until recommendation [rank] is whole in the list, [scrolled] being how far the
     * list is from its top. Two drags in a row that move nothing are its end. [tries] counts Dùng combo taps that
     * left the popup open.
     */
    private fun seek(rank: Int, rows: List<Row>?, scrolled: Double = 0.0, steps: Int = 0, still: Int = 0, tries: Int = 0) {
        if (rows == null) return lost(rank, "không đưa được danh sách về đầu")
        val target = rows.firstOrNull { it.rank == rank && it.y in TAP }
        if (target != null) return apply(target, rank, tries)
        if (rows.isEmpty() && rank == 0) return empty(0)
        if (steps >= 4 + rank * 3) return lost(rank, "chưa tới đề cử ${rank + 1} sau $steps lần cuộn")
        // Wanted above what is seen: at the top OCR missed its button, so read again; further down, start over.
        if (rows.isNotEmpty() && (rows.first().rank > rank || rows.any { it.rank == rank && it.y < TAP.start })) {
            if (scrolled < .01) {
                return later {
                    look { l ->
                        val again = ranked(buttons(l.words), scrolled)
                        l.done(null)
                        seek(rank, again, scrolled, steps + 1, 0, tries)
                    }
                }
            }
            return top { r -> seek(rank, r, 0.0, steps + 1, 0, tries) }
        }
        drag(true) {
            look { l ->
                val found = buttons(l.words)
                if (stationary(rows, found)) {
                    l.done(null)
                    if (still < 1) return@look seek(rank, rows, scrolled, steps + 1, still + 1, tries)
                    // The end: nothing below the last card seen.
                    total = (rows.maxOfOrNull { it.rank } ?: -1) + 1
                    if (!save()) return@look
                    screen.note("Pro: danh sách có $total đề cử")
                    return@look if (rank >= total) restore() else lost(rank, "đề cử ${rank + 1} không chạm được ở cuối danh sách")
                }
                val moved = shift(rows, found)
                val next = moved?.let { ranked(found, scrolled + it) }
                if (moved == null || next == null) {
                    // OCR can miss a button once: look again at the same place before giving up on this hero.
                    l.done(null)
                    return@look later {
                        look { again ->
                            val seen = buttons(again.words)
                            val retried = shift(rows, seen)
                            val retracked = retried?.let { ranked(seen, scrolled + it) }
                            again.done(if (retracked == null) "pro-lost" else null)
                            if (retried == null || retracked == null) {
                                lost(rank, "không theo được thẻ khi cuộn")
                            } else {
                                seek(rank, retracked, scrolled + retried, steps + 1, 0, tries)
                            }
                        }
                    }
                }
                l.done(null)
                seek(rank, next, scrolled + moved, steps + 1, 0, tries)
            }
        }
    }

    private fun apply(row: Row, rank: Int, tries: Int) {
        screen.say("Pro: Dùng combo đề cử ${rank + 1}")
        tap(row.button, "v2.apply", 1500) {
            prepared(rank, 0) { applied ->
                when {
                    applied != null -> {
                        captured = rank + 1
                        applied.keep("$captured") { ok ->
                            if (!ok) return@keep fail("Không lưu được ảnh Pro $captured")
                            if (save()) next()
                        }
                    }
                    tries < 1 -> {
                        screen.note("Pro: Dùng combo ${rank + 1} chưa về Chuẩn bị; thử lại một lần")
                        open { top { rows -> seek(rank, rows, tries = tries + 1) } }
                    }
                    else -> lost(rank, "bấm Dùng combo ${rank + 1} hai lần mà chưa về Chuẩn bị")
                }
            }
        }
    }

    /** Chuẩn bị back in front after Dùng combo; null when the popup stays. */
    private fun prepared(rank: Int, reads: Int, then: (Look?) -> Unit): Unit = look { l ->
        when {
            !ProComboScreens.popup(l.words) && ProComboScreens.preparation(l.words) -> then(l)
            reads >= 2 -> {
                l.done("pro-apply-${rank + 1}")
                then(null)
            }
            else -> {
                l.done(null)
                later { prepared(rank, reads + 1, then) }
            }
        }
    }

    /** No recommendation at all: the list itself is kept as the evidence, read twice before believing it. */
    private fun empty(reads: Int): Unit = look { l ->
        if (buttons(l.words).isNotEmpty()) {
            l.done(null)
            return@look top { rows -> seek(0, rows) }
        }
        if (reads < 2 && !ProComboScreens.empty(l.words)) {
            l.done(null)
            return@look later { empty(reads + 1) }
        }
        total = 0
        state.put("empty", true)
        l.keep("khong-co-de-cu") { ok ->
            if (!ok) return@keep fail("Không lưu được ảnh danh sách Pro trống")
            screen.note("Pro: không thấy đề cử nào")
            state.put("done", true)
            if (save()) end(Outcome.Done(0, true))
        }
    }

    /** The rank cannot be reached: log it, keep what was taken and go on with the next hero. */
    private fun lost(rank: Int, why: String) {
        screen.note("Pro: bỏ qua từ đề cử ${rank + 1} ($why); giữ $captured ảnh")
        total = rank
        if (save()) restore()
    }

    /** The hero keeps the first recommendation, as before the run: Dùng combo 1 again, without a photo. */
    private fun restore() {
        if (captured <= 1 || state.optBoolean("restored")) return done()
        screen.say("Pro: dùng lại combo đề cử 1")
        open {
            top { rows ->
                val first = rows?.firstOrNull { it.rank == 0 && it.y in TAP }
                if (first == null) {
                    screen.note("Pro: không thấy đề cử 1 để dùng lại")
                    return@top done()
                }
                tap(first.button, "v2.apply", 1500) {
                    prepared(0, 0) { l ->
                        l?.done(null)
                        if (l == null) screen.note("Pro: dùng lại đề cử 1 chưa về Chuẩn bị")
                        state.put("restored", true)
                        done()
                    }
                }
            }
        }
    }

    private fun done() {
        state.put("done", true)
        if (save()) end(Outcome.Done(captured, false))
    }
}
