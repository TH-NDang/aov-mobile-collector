package vn.ndang.aovcollector

/**
 * Captures a hero's passive and skills from the hero page. It reads the skill row first ([SkillRow]), so a hero
 * with five icons gets all five, and when the row has a ↻ button it switches to each other form in turn and
 * captures the icons that changed, until the first form is back. All screen access goes through [Screen], so
 * unit tests run this exact logic on scripted screens.
 *
 * Shots are named after the slot: passive, skill-1… for the first form; form-2-passive, form-2-skill-3… for an
 * icon a later form changed, with the skill row of that form kept as form-2-icons.
 *
 * [known] is the first form an earlier, interrupted try of the same step saw: the page may have been left on
 * another form, which would otherwise be taken for the first.
 */
class SkillReader(
    private val prefix: String,
    private val options: Options,
    private val screen: Screen,
    private val known: SkillRow.Row? = null,
    private val finish: (Outcome) -> Unit
) {
    /** As in [BuiltInMacros.create]: the summary tab is skipped with [detailOnly]; [directSkills] keeps the panel open between icons. */
    class Options(val detailOnly: Boolean = false, val directSkills: Boolean = false, val fast: Boolean = false)

    sealed class Outcome {
        class Done(val summary: String) : Outcome()

        class Failed(val reason: String) : Outcome()
    }

    /** One screenshot; call [done] exactly once, with a name to keep it in the run or null to drop it. */
    interface Look {
        val row: SkillRow.Row

        fun done(keep: String?)
    }

    interface Screen {
        val alive: Boolean

        /** null when no screenshot could be taken. */
        fun look(then: (Look?) -> Unit)

        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)

        /** Takes and keeps a screenshot named [name]. */
        fun shot(name: String, then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        fun note(text: String) {}

        /** The first form, once read: kept for a try of the step after a pause. */
        fun firstForm(row: SkillRow.Row) {}
    }

    companion object {
        const val FAST_SETTLE_MS = 600L
        const val FAST_TAB_WAIT_MS = 450L

        /** Most heroes have two forms; a few cycle through three. */
        const val MAX_FORMS = 4

        /** Empty space on the hero page, which closes a skill or attribute panel. */
        const val CLOSE_X = .60
        const val CLOSE_Y = .82
        const val TAB_Y = .05
        const val SUMMARY_X = .817
        const val DETAIL_X = .878

        /** The slot name of icon [i]: the passive first, then the skills. */
        fun slot(i: Int) = if (i == 0) "passive" else "skill-$i"

        /** Rough seconds for [icons] icons of one form with [o], counted as the macro estimate counts steps (a tap .28, a shot .45). */
        fun seconds(o: Options, icons: Int = 4): Double {
            val settle = if (o.fast) .6 else .9
            val tab = if (o.fast) .45 else .9
            val icon = .28 + settle + (if (o.detailOnly) 0.0 else .28 + tab + .45) + .28 + tab + .45
            // The panel closes after every icon, or only after the last one; reading the row takes a screenshot.
            return icons * icon + (if (o.directSkills) 1 else icons) * (.28 + settle) + .6
        }
    }

    private val settle = if (options.fast) FAST_SETTLE_MS else 900L
    private val tabWait = if (options.fast) FAST_TAB_WAIT_MS else 900L
    private val forms = ArrayList<SkillRow.Row>()
    private val changes = ArrayList<String>()
    private var ended = false

    fun start() {
        first(false, 0)
    }

    private fun end(outcome: Outcome) {
        if (!ended) {
            ended = true
            finish(outcome)
        }
    }

    private fun fail(reason: String) = end(Outcome.Failed(reason))

    private fun look(then: (Look) -> Unit) {
        if (!screen.alive) return
        screen.look { l ->
            when {
                !screen.alive -> l?.done(null)
                l == null -> fail("Không chụp được màn hình tướng")
                else -> then(l)
            }
        }
    }

    private fun tap(x: Double, y: Double, wait: Long, then: () -> Unit) {
        if (!screen.alive) return
        screen.tap(x, y) { ok -> if (!ok) fail("Không chạm được màn hình tướng") else screen.later(wait, then) }
    }

    /** A failed screenshot is tried once more; Android sometimes refuses one right after a tap. */
    private fun shot(name: String, then: () -> Unit) {
        if (!screen.alive) return
        screen.shot("$prefix-$name") { ok ->
            if (ok) {
                then()
            } else {
                screen.later(1000) {
                    if (screen.alive) screen.shot("$prefix-$name") { again -> if (again) then() else fail("Không chụp được ảnh $name") }
                }
            }
        }
    }

    private fun first(closed: Boolean, returns: Int): Unit = look { l ->
        val row = l.row
        when {
            // Left on another form by a pause: ↻ until the first form is back.
            row.centres != null && known != null && !row.sameAs(known) && row.swap && returns < MAX_FORMS - 1 -> {
                l.done(null)
                if (returns == 0) screen.note("Hàng chiêu không ở dạng đầu như lần trước; bấm ↻ để về dạng đầu")
                tap(SkillRow.SWAP_X, SkillRow.Y, settle + 300) { first(true, returns + 1) }
            }
            row.centres != null -> {
                l.done(null)
                forms.add(row)
                screen.firstForm(row)
                if (known != null &&
                    !row.sameAs(
                        known
                    )
                ) {
                    screen.note("Không về được dạng đầu như lần trước; lấy dạng đang hiện làm dạng đầu")
                }
                screen.note("Hàng chiêu: ${row.centres.size} ô" + if (row.swap) ", có nút đổi dạng ↻" else "")
                capture(1, row.centres, row.centres.indices.toList()) { if (row.swap) swap(2, 0) else done() }
            }
            // A panel may still be open over the page: tap empty space once and read again.
            !closed -> {
                l.done(null)
                tap(CLOSE_X, CLOSE_Y, settle) { first(true, returns) }
            }
            else -> {
                l.done("skill-row-unknown")
                screen.note("Không nhận ra hàng chiêu; chụp theo 4 vị trí như trước")
                capture(1, SkillRow.FOUR, SkillRow.FOUR.indices.toList()) { done() }
            }
        }
    }

    /** Opens icons [which] of [centres] one after another and shoots their summary and detail tabs. */
    private fun capture(form: Int, centres: List<Double>, which: List<Int>, then: () -> Unit) {
        fun one(k: Int) {
            if (k == which.size) return then()
            val i = which[k]
            val name = (if (form > 1) "form-$form-" else "") + slot(i)
            val last = k == which.lastIndex
            tap(centres[i], SkillRow.Y, settle) {
                val detail = {
                    tap(DETAIL_X, TAB_Y, tabWait) {
                        shot("$name-detail") {
                            if (options.directSkills && !last) one(k + 1) else tap(CLOSE_X, CLOSE_Y, settle) { one(k + 1) }
                        }
                    }
                }
                if (options.detailOnly) detail() else tap(SUMMARY_X, TAB_Y, tabWait) { shot("$name-summary") { detail() } }
            }
        }
        one(0)
    }

    /**
     * Taps ↻ and reads the row for form [form]. Unchanged icons get one more look and then one more tap, as the
     * game may still be animating or may have missed the tap.
     */
    private fun swap(form: Int, retries: Int) {
        if (form > MAX_FORMS) {
            screen.note("Đã đổi ${MAX_FORMS - 1} lần mà chưa về dạng đầu; dừng đổi dạng")
            return done()
        }
        val act = { next: () -> Unit ->
            if (retries == 1) screen.later(1000, next) else tap(SkillRow.SWAP_X, SkillRow.Y, settle + 300, next)
        }
        act {
            look { l ->
                val row = l.row
                val previous = forms.last()
                when {
                    row.centres == null -> {
                        l.done("form-$form-unknown")
                        screen.note("Không đọc được hàng chiêu sau khi bấm đổi dạng")
                        done()
                    }
                    row.sameAs(previous) -> if (retries < 2) {
                        l.done(null)
                        swap(form, retries + 1)
                    } else {
                        l.done(null)
                        screen.note("Bấm ↻ nhưng hàng chiêu không đổi")
                        done()
                    }
                    // Back to the first form, the end of the cycle; an earlier form again would loop without it.
                    forms.any { row.sameAs(it) } -> {
                        l.done(null)
                        done()
                    }
                    else -> {
                        // Only icons no earlier form showed at that place; a form with another number of icons is new throughout.
                        val changed = row.icons.indices.filter { i ->
                            forms.none { f ->
                                f.icons.size == row.icons.size &&
                                    !SkillRow.differ(f.icons[i], row.icons[i])
                            }
                        }
                        l.done("form-$form-icons")
                        forms.add(row)
                        changes += "dạng $form đổi " + changed.joinToString(", ") { slot(it) }.ifEmpty { "không ô mới" }
                        screen.note("Dạng $form: ${row.centres.size} ô, đổi ${changed.size} ô")
                        capture(form, row.centres, changed) { swap(form + 1, 0) }
                    }
                }
            }
        }
    }

    private fun done() {
        val icons = forms.firstOrNull()?.icons?.size?.takeIf { it > 0 } ?: 4
        end(Outcome.Done("$icons ô chiêu" + if (forms.size > 1) " · ${forms.size} dạng (${changes.joinToString("; ")})" else ""))
    }
}
