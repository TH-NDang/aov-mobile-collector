package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Brings card [card] of the All-heroes list on screen and taps it. The game keeps sliding after a swipe
 * and moves less at the end of the list, so every scroll is measured from screenshots, never assumed.
 * All screen access goes through [Screen], so unit tests run this exact logic on a drawn list.
 * When a hero page is still open (the game ignored the macro's back tap), it taps the game's back arrow.
 */
class ListNavigator(
    private val card: Int,
    private val state: State,
    private val screen: Screen,
    private val approve: Approve? = null,
    private val watch: Watch? = null,
    private val finish: (Outcome) -> Unit
) {
    /**
     * Asked before card [card] is tapped, at (x, y) on shot s, which [has] a card there, with the list [offset]
     * screens down: answers the place to tap (another card of the same screen), or null when the hero sought is
     * not on it. Picks by place alone have none and tap where they are.
     */
    fun interface Approve {
        fun check(s: Shot, x: Double, y: Double, has: Boolean, offset: Double, then: (Pair<Double, Double>?) -> Unit)
    }

    /**
     * Shown every screen the list rests on, with the list [offset] screens down, before the navigator moves on
     * towards [card]: a card to tap there instead, the end of what is sought, or nothing ([HeroSweep]).
     */
    fun interface Watch {
        fun check(s: Shot, offset: Double, then: (Sight) -> Unit)
    }

    /**
     * What a [Watch] makes of a screen. [Done]: nothing more to find on the list; the pick ends as at its end. [drift]:
     * how far past where the list really is the navigator counted it, in screen heights, as the names on it show.
     */
    sealed class Sight {
        open val drift: Double get() = 0.0

        class Next(override val drift: Double = 0.0) : Sight()

        object Done : Sight()

        class Tap(val x: Double, val y: Double, override val drift: Double = 0.0) : Sight()
    }

    /** Kept between the picks of one run. [step] shrinks when swipes slide further than can be measured. */
    class State(
        var offset: Double = 0.0,
        var page: Int = 1,
        var synced: Boolean = false,
        var step: Double = 1.0,
        var ref: FloatArray? = null
    )

    /** [Missing]: the [Approve] did not find the hero sought where the card was. */
    sealed class Outcome {
        object Tapped : Outcome()

        object End : Outcome()

        object Missing : Outcome()

        class Failed(val reason: String) : Outcome()
    }

    /**
     * One screenshot; call [done] exactly once, with a name to store it in the run (pages of the list, and
     * screens the navigator left with the back arrow) or null to drop it. [backArrow] matters off the list only.
     */
    interface Shot {
        val profile: FloatArray
        val height: Int
        val backArrow: Boolean

        fun hasCard(x: Double, y: Double): Boolean

        fun done(keep: String?)

        /** The words on the screenshot, read on demand ([HeroFinder]). */
        fun words(then: (List<GameScreens.Word>) -> Unit) {
            then(emptyList())
        }
    }

    interface Screen {
        val alive: Boolean
        val layout: HeroGrid.Layout get() = HeroGrid.DEFAULT_LAYOUT

        /** null when no screenshot could be read; an empty profile when the screen is not the hero list. */
        fun look(then: (Shot?) -> Unit)

        /** Drags the content up by [d] screen heights (negative: down). Slow drags rest before lifting; [fling] is a quick flick. */
        fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit)

        /**
         * The furthest a slow drag of [d] can move the list, in screen heights, or null when it can glide on. A list
         * that stops where the finger rests is measured only within that reach.
         */
        fun heldReach(d: Double): Double? = null

        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)

        fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) = tap(action.points[0].x, action.points[0].y, then)

        fun calibratedBack(action: CalibrationAction, then: (Boolean) -> Unit) = back(then)

        fun calibratedDrag(action: CalibrationAction, d: Double, fling: Boolean, then: (Boolean) -> Unit) = drag(d, fling, then)

        /**
         * Whether the screens the list only passed through on its way are kept, or just the one a card is tapped on.
         * The chess encyclopedia keeps every page of its list; the hero list keeps the page it tapped: each kept page
         * is a large PNG written on the thread that also measures the next screenshot.
         */
        val keepsPassedPages: Boolean get() = true

        /**
         * Whether a card far up the list is reached by flicking to the top and dragging down, rather than by dragging up
         * all the way ([nearerTheTop]). The hero list's picks by name go anywhere; the chess lists are read down card by
         * card and number their pages from the top.
         */
        val flicksToTop: Boolean get() = false

        /** Taps the game's back arrow at the top left. */
        fun back(then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        /** Records what the navigator did in the run's events log. */
        fun note(text: String) {}
    }

    internal companion object {
        const val MAX_BACKS = 3

        /** A card past the end of any list: a pick for it goes down to the end, its [Watch] shown each screen on the way. */
        const val DOWN = 100_000

        /** Drags allowed beyond twice the ones the distance takes: stalls at the end of the list, short last moves. */
        const val SPARE_MOVES = 6

        /**
         * A card this many screens nearer the top of the list than to where the list is, is reached faster from the
         * top: a few flicks up, then down. On the user's phone a held drag took about 3.7 s and going to the top
         * about 9; the run that stopped had dragged 16 times up from card 102 towards card 1.
         */
        const val TOP_SHORTCUT = 2.0

        /** The drags a list [offset] screens down needs to bring card [card] to [layout]'s park at [step]. */
        fun drags(layout: HeroGrid.Layout, card: Int, offset: Double, step: Double): Int {
            val far = abs(layout.centre(card, offset).second - layout.park)
            return ceil(far / (layout.maxStep * step)).toInt()
        }

        /** Whether card [card] is reached sooner by flicking to the top than by dragging from [offset]. */
        fun nearerTheTop(layout: HeroGrid.Layout, card: Int, offset: Double): Boolean {
            val there = maxOf(0.0, layout.centre(card, 0.0).second - layout.park)
            return offset - there > there + TOP_SHORTCUT
        }
    }

    private var moves = 0
    private var stalls = 0
    private var resyncs = 0
    private var backs = 0
    private var waited = false

    /** Drags this pick may make before it counts again from the top; set at its first drag from where the list is. */
    private var budget = -1

    fun start() {
        if (!state.synced || state.ref == null) return toTop(0)
        look { s ->
            // The list should be exactly where the previous pick left it; anything else is a different screen.
            val ref = state.ref
            if (ref !=
                null
            ) {
                val shift = HeroGrid.shift(ref, s.profile)
                if (!shift.reliable) {
                    s.done(null)
                    return@look lost()
                }
                state.offset +=
                    shift.px.toDouble() / s.height
            }
            state.ref = s.profile
            if (screen.flicksToTop && nearerTheTop(screen.layout, card, state.offset)) {
                s.done(null)
                screen.note("Ô ${card + 1} ở gần đầu danh sách: vuốt nhanh lên đầu rồi kéo xuống")
                return@look toTop(0)
            }
            aim(s, false)
        }
    }

    private fun fail(reason: String) {
        finish(Outcome.Failed(reason))
    }

    private fun lost() {
        state.synced = false
        state.ref = null
        fail("Màn hình không còn là danh sách tướng như lúc trước. Mở danh sách Tất cả rồi bấm Tiếp tục; app sẽ tự tìm lại vị trí.")
    }

    private fun look(then: (Shot) -> Unit) {
        if (!screen.alive) return
        screen.look { s ->
            when {
                !screen.alive -> s?.done(null)
                s == null -> fail("Không chụp được danh sách tướng")
                s.profile.isNotEmpty() -> {
                    waited = false
                    then(s)
                }
                // The list may still be fading in after the macro's back tap: look once more before acting.
                !waited -> {
                    s.done(null)
                    waited = true
                    screen.later(1200) { look(then) }
                }
                // A hero page or panel is still open. Its back arrow leads towards the list; the lobby has none.
                s.backArrow && backs < MAX_BACKS -> {
                    waited = false
                    backs++
                    s.done("not-list-$backs")
                    screen.note("Không thấy danh sách tướng; bấm nút quay lại của game (lần $backs)")
                    screen.calibratedBack(
                        CalibrationAction(
                            "hero.list.v1.back",
                            "Danh sách: quay lại",
                            listOf(CalibrationPoint(HeroGrid.BACK_X, HeroGrid.BACK_Y))
                        )
                    ) { ok ->
                        if (ok) screen.later(1500) { look(then) } else fail("Không chạm được nút quay lại của game")
                    }
                }
                else -> {
                    s.done(null)
                    state.synced = false
                    state.ref = null
                    fail(
                        if (backs >
                            0
                        ) {
                            "Đã bấm nút quay lại $backs lần nhưng vẫn chưa thấy danh sách Tất cả tướng. Mở danh sách rồi bấm Tiếp tục."
                        } else {
                            "Màn hình hiện tại không phải danh sách Tất cả tướng. Mở danh sách rồi bấm Tiếp tục."
                        }
                    )
                }
            }
        }
    }

    private fun page() = "page-%02d-list".format(state.page)

    /** What the pick is for, in the log: a card, or the way down the list. */
    private fun target() = if (card >= DOWN) "đi xuống danh sách" else "ô ${card + 1}"

    private fun aim(s: Shot, keep: Boolean) {
        val w = watch ?: return plan(s, keep)
        w.check(s, state.offset) { sight ->
            // Each measured move may be a pixel off; the names read on the screen say where the list really is.
            state.offset -= sight.drift
            when {
                !screen.alive -> s.done(null)
                sight is Sight.Tap -> {
                    s.done(page())
                    tap(sight.x, sight.y)
                }
                sight is Sight.Done -> {
                    s.done(null)
                    finish(Outcome.End)
                }
                else -> plan(s, keep)
            }
        }
    }

    private fun plan(s: Shot, keep: Boolean) {
        when (val plan = screen.layout.plan(card, state.offset)) {
            is HeroGrid.Plan.Move -> {
                s.done(if (keep && screen.keepsPassedPages) page() else null)
                move(plan.d * state.step)
            }
            is HeroGrid.Plan.Tap -> {
                val has = s.hasCard(plan.x, plan.y)
                decide(s, plan.x, plan.y, has, if (keep) page() else null, true)
            }
        }
    }

    /** Taps the card at ([x], [y]) if there is one and the [approve] agrees; [end]: a missing card is the end of the list. */
    private fun decide(s: Shot, x: Double, y: Double, has: Boolean, keep: String?, end: Boolean) {
        val a = approve
        if (a == null) {
            s.done(keep)
            return if (has) {
                tap(x, y)
            } else if (end) {
                finish(Outcome.End)
            } else {
                fail("Danh sách không cuộn được")
            }
        }
        a.check(s, x, y, has, state.offset) { at ->
            s.done(keep)
            when {
                !screen.alive -> Unit
                at != null -> tap(at.first, at.second)
                has -> finish(Outcome.Missing)
                end -> finish(Outcome.End)
                else -> fail("Danh sách không cuộn được")
            }
        }
    }

    private fun tap(x: Double, y: Double) {
        if (screen.alive) {
            screen.calibrated(
                CalibrationAction("hero.list.v1.card", "Danh sách: chọn thẻ tướng", listOf(CalibrationPoint(x, y)), relative = true)
            ) { ok ->
                if (ok) finish(Outcome.Tapped) else fail("Không chạm được thẻ tướng")
            }
        }
    }

    private fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
        val from = if (d > 0) .88 else .22
        val to = (from - d - (if (d > 0) .02 else -.02)).coerceIn(.02, .98)
        val action = CalibrationAction(
            "hero.list.v1.scroll.${if (d > 0) "down" else "up"}.${if (fling) "fling" else "held"}",
            "Danh sách: cuộn tướng",
            listOf(CalibrationPoint(.70, from), CalibrationPoint(.70, to)),
            relative = true,
            durationMs = if (fling) 160 else 700,
            holdMs = if (fling) 0 else 450
        )
        screen.calibratedDrag(action, d, fling, then)
    }

    private fun move(d: Double) {
        if (!screen.alive) return
        // Never fewer than the 16, and five a row down to the card, a pick was allowed before: the chess lists ran with those.
        if (budget < 0) budget = moves + maxOf(16 + 5 * (card / 5), SPARE_MOVES + 2 * drags(screen.layout, card, state.offset, state.step))
        if (++moves > budget) {
            if (resyncs > 0) return fail("Không cuộn tới được ô tướng ${card + 1}")
            screen.note("Đã kéo ${moves - 1} lần mà chưa xong (${target()}); về đầu danh sách để đếm lại")
            return resync()
        }
        val before = state.ref ?: return resync()
        val offsetBefore = state.offset
        val backsBefore = backs
        drag(d, false) { ok ->
            if (!ok) return@drag fail("Không vuốt được danh sách")
            screen.later(800) {
                look { s ->
                    val shift = HeroGrid.shift(before, s.profile, reach(d, s.height))
                    if (shift.reliable) {
                        settle(shift.px.toDouble() / s.height, s, d, backs > backsBefore)
                    } else {
                        s.done(null)
                        undo(d, before, offsetBefore)
                    }
                }
            }
        }
    }

    /** The moves, in pixels, a slow drag of [d] can have made: from a little back to its reach. Null: any. */
    private fun reach(d: Double, height: Int): IntRange? {
        val far = screen.heldReach(d) ?: return null
        val slack = (.03 * height).toInt()
        val px = (far * height).toInt()
        return if (d > 0) -slack..px else -px..slack
    }

    /** [interrupted]: the navigator had to come back to the list after this swipe, so a list that did not move proves nothing. */
    private fun settle(moved: Double, s: Shot, d: Double, interrupted: Boolean) {
        state.offset += moved
        state.ref = s.profile
        screen.note("Độ cuộn đo được: " + (moved * s.height).toInt() + " px; " + target())
        if (abs(moved) >= .01) {
            stalls = 0
            state.page++
            return aim(s, true)
        }
        if (interrupted || ++stalls < 2) return aim(s, false)
        // The list stopped moving: past its end there is nothing more to collect.
        val (x, y) = screen.layout.centre(card, state.offset)
        val has = d > 0 && y < screen.layout.lastRowLimit && s.hasCard(x, y)
        decide(s, x, y, has, null, d > 0)
    }

    /** The list slid further than one screenshot can follow: drag back, measure against [before], and use smaller steps from now on. */
    private fun undo(d: Double, before: FloatArray, offsetBefore: Double) {
        val step = state.step
        state.step = maxOf(.25, state.step / 2)
        // Smaller steps take more drags: allowed anew from where the list is, at most twice (down to a quarter).
        if (state.step < step) budget = -1
        drag(-d, false) { ok ->
            if (!ok) return@drag fail("Không vuốt được danh sách")
            screen.later(800) {
                look { s ->
                    val shift = HeroGrid.shift(before, s.profile)
                    if (!shift.reliable) {
                        s.done(null)
                        return@look resync()
                    }
                    state.offset = offsetBefore + shift.px.toDouble() / s.height
                    state.ref = s.profile
                    aim(s, false)
                }
            }
        }
    }

    /** Measurement lost track: go back to the top and count again. */
    private fun resync() {
        if (++resyncs > 1) return fail("Không xác định được vị trí danh sách. Mở danh sách Tất cả rồi bấm Tiếp tục.")
        state.synced = false
        state.ref = null
        toTop(0)
    }

    /** Flicks to the top, then checks that one more flick no longer moves the list. */
    private fun toTop(round: Int) {
        if (round > 2) return fail("Không đưa được danh sách về đầu. Mở danh sách Tất cả rồi bấm Tiếp tục.")

        fun flick(times: Int, then: () -> Unit) {
            if (!screen.alive) return
            if (times == 0) return then()
            drag(-.70, true) { ok -> if (!ok) fail("Không vuốt được danh sách") else screen.later(350) { flick(times - 1, then) } }
        }
        // Check it is the hero list before flicking: on another screen a swipe can change skins.
        look { first ->
            first.done(null)
            flick(3) {
                screen.later(1200) {
                    look { a ->
                        a.done(null)
                        flick(1) {
                            screen.later(1200) {
                                look { b ->
                                    val shift = HeroGrid.shift(a.profile, b.profile)
                                    if (shift.reliable &&
                                        abs(shift.px) < 6
                                    ) {
                                        state.offset = 0.0
                                        state.synced = true
                                        state.page = 1
                                        state.ref = b.profile
                                        budget = -1
                                        aim(b, true)
                                    } else {
                                        b.done(null)
                                        toTop(round + 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
