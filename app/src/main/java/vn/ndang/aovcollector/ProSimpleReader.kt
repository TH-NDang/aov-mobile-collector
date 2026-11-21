package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Đề cử Pro by position only: no creator name, rank catalog or replay proof. The recommendations are counted by
 * their "Dùng combo" buttons from the top of the list, two thirds of a card per drag so each button is followed from
 * one frame to the next by its own motion. For each of the first [max] recommendations it taps Dùng combo and keeps
 * the Chuẩn bị screen as 1, 2… once the combo has finished drawing, then applies the first one again so the hero
 * keeps the top recommendation. Something that cannot be followed is written to the run's log and the hero ends
 * with what was taken; the run goes on with the next hero instead of pausing.
 */
class ProSimpleReader(
    private val screen: Screen,
    private val state: JSONObject,
    private val max: Int = MAX,
    private val finish: (Outcome) -> Unit
) {
    interface Look {
        val words: List<GameScreens.Word>
        val proSelected: Boolean

        /** The Dùng combo buttons found by their yellow, when read from pixels; null to find them in [words]. */
        val buttons: List<GameScreens.Word>? get() = null

        /** Chuẩn bị's right panel, to tell an applied combo has stopped changing; empty when not read. */
        val panel: IntArray get() = IntArray(0)

        fun done(keep: String?)

        fun keep(name: String, then: (Boolean) -> Unit)
    }

    interface Screen {
        val alive: Boolean

        fun look(then: (Look?) -> Unit)

        /** A screenshot read from pixels only, no OCR: its words are empty. */
        fun glance(then: (Look?) -> Unit) = look(then)

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
        /** Combos taken per hero unless the task asks for another number. */
        const val MAX = 5

        /**
         * Two cards fill the list; their buttons are .366 apart. A drag of two thirds of that keeps every button in
         * view twice, and is less than the .27 beyond which a move could be taken for one a card shorter.
         */
        const val PITCH = .366
        const val STEP = .24

        /** Chuẩn bị after Dùng combo: looks until its panel is the same twice; looks before calling a list empty. */
        const val MAX_SETTLE = 6
        const val EMPTY_READS = 5
        const val MAX_EMPTY_READS = 10

        /**
         * Flicks towards the list's top before its buttons must stand still on the card grid; when they do not, the
         * pause before as many again, each then waited for [SLOW_WAIT] instead of .7 s.
         */
        const val TOP_FLICKS = 6
        const val SLOW_PAUSE = 2500L
        const val SLOW_WAIT = 1600L

        /** Two thirds of a card down, held still before lifting so the list stops where the finger does. */
        val STEP_DRAG = CalibrationAction(
            "pro.v2.list.step",
            "Pro: cuộn hai phần ba thẻ",
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

    /** The recommendation for which the list was already sought again from its top after losing the cards. */
    private var resought = -1
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

    private fun look(then: (Look) -> Unit) = read(false, then)

    /** A look without OCR, while following the list's buttons or waiting for Chuẩn bị to settle. */
    private fun glance(then: (Look) -> Unit) = read(true, then)

    private fun read(pixels: Boolean, then: (Look) -> Unit) {
        if (!screen.alive || ended) return
        val handle: (Look?) -> Unit = { l ->
            when {
                !screen.alive || ended -> l?.done(null)
                l == null -> fail("Không đọc được màn hình Pro")
                else -> then(l)
            }
        }
        if (pixels) screen.glance(handle) else screen.look(handle)
    }

    /** The buttons of a look: from their yellow when read so, else from the words. */
    private fun found(l: Look) = l.buttons ?: buttons(l.words)

    private fun later(ms: Long = 900, then: () -> Unit) = screen.later(ms) { if (screen.alive && !ended) then() }

    private fun tap(word: GameScreens.Word, id: String, wait: Long = 900, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(CalibrationAction("pro.$id", "Pro: $id", listOf(CalibrationPoint(word.x, word.y)), true), word) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) fail("Không chạm được $id") else later(wait, then)
        }
    }

    /** [down]: the list moves up to show later cards; otherwise a quick flick back towards the top. */
    private fun drag(down: Boolean, wait: Long = if (down) 900 else 700, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(if (down) STEP_DRAG else TOP_FLICK, null) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) fail("Không cuộn được danh sách Pro") else later(wait, then)
        }
    }

    fun start() {
        if (state.optBoolean("done")) return end(Outcome.Done(captured, state.optBoolean("empty")))
        next()
    }

    private fun next() {
        if (captured >= max || (total >= 0 && captured >= total)) return restore()
        screen.say(
            "Pro: lấy đề cử ${captured + 1}" + when {
                total >= 0 -> "/${minOf(total, max)}"
                max < BuiltInMacros.ALL -> "/$max"
                else -> ""
            }
        )
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

    /**
     * Flicks back until the buttons stop moving on the card grid. When they do not in [TOP_FLICKS] flicks, once more
     * after a pause and with longer waits ([slow]): a game answering slowly had not put the list at rest in time, and
     * the user's hero 89 was left with two photos of three. Null when not then either, the last look kept.
     */
    private fun top(drags: Int = 0, previous: List<Row>? = null, slow: Boolean = false, then: (List<Row>?) -> Unit): Unit = glance { l ->
        val found = found(l)
        val rows = ranked(found, 0.0)
        if (rows != null && previous != null && stationary(previous, found)) {
            l.done(null)
            return@glance then(rows)
        }
        if (drags >= TOP_FLICKS) {
            l.done(if (slow) "pro-top" else null)
            if (slow) return@glance then(null)
            screen.note("Pro: danh sách đề cử chưa đứng yên ở đầu sau $TOP_FLICKS lần vuốt; chờ rồi thử lại chậm hơn")
            return@glance later(SLOW_PAUSE) { top(0, null, true, then) }
        }
        l.done(null)
        drag(false, if (slow) SLOW_WAIT else 700) {
            top(drags + 1, rows ?: found.mapIndexed { i, b -> Row(b.y, i, b) }, slow, then)
        }
    }

    /**
     * Drags two thirds of a card at a time until recommendation [rank] is whole in the list, [scrolled] being how far the
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
                    glance { l ->
                        val again = ranked(found(l), scrolled)
                        l.done(null)
                        seek(rank, again, scrolled, steps + 1, 0, tries)
                    }
                }
            }
            return top { r -> seek(rank, r, 0.0, steps + 1, 0, tries) }
        }
        drag(true) {
            glance { l ->
                val found = found(l)
                if (stationary(rows, found)) {
                    l.done(null)
                    if (still < 1) return@glance seek(rank, rows, scrolled, steps + 1, still + 1, tries)
                    // The end: nothing below the last card seen.
                    total = (rows.maxOfOrNull { it.rank } ?: -1) + 1
                    if (!save()) return@glance
                    screen.note("Pro: danh sách có $total đề cử")
                    return@glance if (rank >= total) restore() else lost(rank, "đề cử ${rank + 1} không chạm được ở cuối danh sách")
                }
                val moved = shift(rows, found)
                val next = moved?.let { ranked(found, scrolled + it) }
                if (moved == null || next == null) {
                    // OCR can miss a button once: look again at the same place before giving up on this hero.
                    l.done(null)
                    return@glance later {
                        glance { again ->
                            val seen = found(again)
                            val retried = shift(rows, seen)
                            val retracked = retried?.let { ranked(seen, scrolled + it) }
                            again.done(if (retracked == null) "pro-lost" else null)
                            if (retried == null || retracked == null) {
                                if (resought == rank) return@glance lost(rank, "không theo được thẻ khi cuộn")
                                // Once per recommendation: back to the top and count the cards again, rather than
                                // give up on the hero's other combos.
                                resought = rank
                                screen.note("Pro: mất dấu thẻ khi cuộn tới đề cử ${rank + 1}; về đầu danh sách tìm lại")
                                top { r -> seek(rank, r, 0.0, 0, 0, tries) }
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

    /**
     * Chuẩn bị back in front after Dùng combo; null when the popup stays. With [settle], the look handed on is one
     * whose right panel (items, emblems, arcana, spell) is the same as the look before it: the combo has finished
     * drawing. A panel that keeps changing is handed on after [MAX_SETTLE] looks, with a line in the log.
     */
    private fun prepared(rank: Int, reads: Int, settle: Boolean = true, then: (Look?) -> Unit): Unit = look { l ->
        when {
            !ProComboScreens.popup(l.words) && ProComboScreens.preparation(l.words) -> if (settle) {
                val panel = l.panel
                l.done(null)
                settled(rank, panel, 0, then)
            } else {
                then(l)
            }
            reads >= 3 -> {
                l.done("pro-apply-${rank + 1}")
                then(null)
            }
            else -> {
                l.done(null)
                later { prepared(rank, reads + 1, settle, then) }
            }
        }
    }

    private fun settled(rank: Int, before: IntArray, looks: Int, then: (Look?) -> Unit): Unit = later(500) {
        glance { l ->
            val panel = l.panel
            when {
                // Not read from pixels (or nothing to compare): the look itself is Chuẩn bị.
                panel.isEmpty() || ProPixels.same(before, panel) -> then(l)
                looks + 1 >= MAX_SETTLE -> {
                    screen.note("Pro: Chuẩn bị của đề cử ${rank + 1} vẫn đổi sau $MAX_SETTLE lần nhìn; vẫn chụp")
                    then(l)
                }
                else -> {
                    l.done(null)
                    settled(rank, panel, looks + 1, then)
                }
            }
        }
    }

    /**
     * No button in the list: the tab may still be loading. Read again for about ten seconds (twice that while the
     * game says it is loading or reconnecting) unless it says outright there is no recommendation; then the list
     * itself is kept as the evidence.
     */
    private fun empty(reads: Int): Unit = look { l ->
        if (found(l).isNotEmpty()) {
            l.done(null)
            return@look top { rows -> seek(0, rows) }
        }
        val sure = ProComboScreens.empty(l.words)
        val loading = ProComboScreens.blocked(l.words)
        if (!sure && (reads + 1 < EMPTY_READS || (loading && reads + 1 < MAX_EMPTY_READS))) {
            l.done(null)
            screen.say("Pro: chờ danh sách Đề cử Pro tải")
            return@look later(1500) { empty(reads + 1) }
        }
        total = 0
        state.put("empty", true)
        l.keep("khong-co-de-cu") { ok ->
            if (!ok) return@keep fail("Không lưu được ảnh danh sách Pro trống")
            screen.note(if (sure) "Pro: không có đề cử nào" else "Pro: không thấy đề cử nào sau ${reads + 1} lần đọc")
            state.put("done", true)
            if (save()) end(Outcome.Done(0, true))
        }
    }

    /**
     * The rank cannot be reached: log it, keep what was taken and go on with the next hero. Marked "lost", from which
     * recommendation: not the list's end, so the run comes back to this hero once the others are done
     * ([ProSecondPass]).
     */
    private fun lost(rank: Int, why: String) {
        screen.note("Pro: bỏ qua từ đề cử ${rank + 1} ($why); giữ $captured ảnh, hết danh sách tướng sẽ quay lại")
        total = rank
        state.put("lost", rank + 1)
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
                    prepared(0, 0, settle = false) { l ->
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
