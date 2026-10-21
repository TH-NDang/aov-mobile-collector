package vn.ndang.aovcollector

import kotlin.math.abs

/** Preparation's eight-column picker. Artwork, not repeating row geometry, measures every scroll. */
class ProHeroPicker(private val screen: Screen, private val index: Int, private val finish: (Outcome) -> Unit) {
    sealed class Outcome {
        data class Selected(val index: Int) : Outcome()

        object End : Outcome()

        data class Failed(val reason: String) : Outcome()
    }

    interface Look {
        val words: List<GameScreens.Word>
        val preparation: Boolean
        val ownedFilter: BoardScreens.OwnedFilter?
        val allSelected: Boolean
        val rows: List<Pair<Double, Double>>
        val profile: FloatArray
        val height: Int

        fun card(col: Int, top: Double, bottom: Double): Boolean

        fun done(keep: String?)
    }

    interface Screen {
        val alive: Boolean

        fun look(then: (Look?) -> Unit)

        fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)
    }

    companion object {
        private val layout = HeroGrid.Layout(
            BoardScreens.COLUMNS.take(5).toDoubleArray(), .36, BoardScreens.ROW_PITCH,
            .30, .85, .70, .24, .85, BoardScreens.VIEW_TOP, BoardScreens.VIEW_BOTTOM, .020
        )

        fun profile(width: Int, height: Int, readRow: (Int, IntArray) -> Unit) = HeroGrid.profile(width, height, layout, readRow)

        /** A short final row may have only one card; BoardScreens' majority-of-eight detector loses it. */
        fun rows(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): List<Pair<Double, Double>> {
            val result = ArrayList<Pair<Double, Double>>()
            val row = IntArray(width)
            var start = -1
            var lastHit = -1
            val bottom = (BoardScreens.VIEW_BOTTOM * height).toInt()
            for (y in (BoardScreens.VIEW_TOP * height).toInt()..bottom) {
                readRow(y, row)
                val bg = row[(.2615 * width).toInt()]
                var count = 0
                var total = 0
                for (x in ((BoardScreens.COLUMNS[0] - .022) * width).toInt()..((BoardScreens.COLUMNS[0] + .022) * width).toInt() step 2) {
                    val p = row[x]
                    if (abs((p shr 16 and 255) - (bg shr 16 and 255)) + abs((p shr 8 and 255) - (bg shr 8 and 255)) +
                        abs((p and 255) - (bg and 255)) > 60
                    ) {
                        count++
                    }
                    total++
                }
                val hit = count > .5 * total && y < bottom
                if (hit) {
                    if (start < 0) start = y
                    lastHit = y
                }
                if (start >= 0 && (!hit && y - lastHit > .008 * height || y == bottom)) {
                    if (lastHit - start > .02 * height) result += start.toDouble() / height to (lastHit + 1).toDouble() / height
                    start = -1
                }
            }
            return result
        }

        fun picker(words: List<GameScreens.Word>) = words.any {
            it.plain == "chontuong" && it.x in .14..0.40 && it.y in .065..0.16
        }

        fun allButton(words: List<GameScreens.Word>) = words.firstOrNull {
            it.plain == "tatca" && it.x in .17..0.26 && it.y in .18..0.27 && it.right - it.left < .12
        }

        /** Cyan underline beneath Tất cả proves the selected category, not merely its visible label. */
        fun allSelected(width: Int, height: Int, words: List<GameScreens.Word>, readRow: (Int, IntArray) -> Unit): Boolean {
            if (width <= height || !picker(words) || allButton(words) == null) return false
            val row = IntArray(width)
            var best = 0
            for (y in (.247 * height).toInt()..(.269 * height).toInt()) {
                readRow(y, row)
                var hits = 0
                for (x in (.18 * width).toInt()..(.25 * width).toInt()) {
                    val p = row[x]
                    val r = p shr 16 and 255
                    val g = p shr 8 and 255
                    val b = p and 255
                    if (b > 190 && g > 155 && b - r > 35 && g - r > 25) hits++
                }
                best = maxOf(best, hits)
            }
            return best > .043 * width
        }
    }

    private var ended = false
    private var rowZero = 0.0
    private var offset = 0.0
    private var moves = 0

    private fun end(result: Outcome) {
        if (!ended && screen.alive) {
            ended = true
            finish(result)
        }
    }

    private fun fail(reason: String) = end(Outcome.Failed(reason))

    private fun look(then: (Look) -> Unit) {
        if (!screen.alive || ended) return
        screen.look { value ->
            if (!screen.alive || ended) {
                value?.done(null)
            } else if (value == null) {
                fail("Không đọc được Chọn tướng; chưa xác định hết danh sách.")
            } else {
                then(value)
            }
        }
    }

    private fun action(id: String, points: List<CalibrationPoint>, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(
            CalibrationAction(
                "pro.v1.picker.$id",
                "Pro: $id",
                points,
                relative = true,
                durationMs = if (points.size > 1) 700 else 100,
                holdMs = if (points.size > 1) 450 else 0
            )
        ) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) {
                fail("Không thực hiện được thao tác Chọn tướng.")
            } else {
                screen.later(900) { if (screen.alive && !ended) then() }
            }
        }
    }

    private fun tap(id: String, x: Double, y: Double, then: () -> Unit) = action(id, listOf(CalibrationPoint(x, y)), then)

    private fun scroll(d: Double, then: () -> Unit) = action(
        if (d < 0) "scroll.reset" else "scroll.next",
        listOf(CalibrationPoint(.62, if (d < 0) .42 else .78), CalibrationPoint(.62, (if (d < 0) .42 else .78) - d)),
        then
    )

    fun start() {
        require(index in 0 until BuiltInMacros.ALL)
        look { l ->
            val isPicker = picker(l.words)
            val preparation = l.preparation
            l.done(null)
            when {
                isPicker -> filters(0, false, false)
                preparation -> tap("open", .094, .821) { filters(0, false, false) }
                else -> fail("Mở màn Chuẩn bị hoặc Chọn tướng trước khi thu thập Pro.")
            }
        }
    }

    private fun filters(reads: Int, tappedOwned: Boolean, tappedAll: Boolean): Unit = look { l ->
        if (!picker(l.words)) {
            l.done(if (reads >= 3) "pro-picker-unknown" else null)
            if (reads >= 3) fail("Không mở được Chọn tướng.") else screen.later(700) { filters(reads + 1, tappedOwned, tappedAll) }
            return@look
        }
        val filter = l.ownedFilter
        val all = allButton(l.words)
        when {
            !l.allSelected && all != null && !tappedAll -> {
                l.done(null)
                tap("all", all.x, all.y) { filters(0, tappedOwned, true) }
            }
            !l.allSelected || filter == null -> {
                l.done(if (reads >= 3) "pro-picker-filter" else null)
                if (reads >= 3) {
                    fail("Không xác minh được Tất cả / Đã nhận; chưa chọn tướng.")
                } else {
                    screen.later(700) { filters(reads + 1, tappedOwned, tappedAll) }
                }
            }
            filter.checked && !tappedOwned -> {
                l.done(null)
                tap("owned-filter", filter.x, filter.y) { filters(0, true, tappedAll) }
            }
            filter.checked -> {
                l.done(if (reads >= 3) "pro-picker-filter" else null)
                if (reads >= 3) {
                    fail("Đã nhận vẫn bật sau thao tác; chưa chọn tướng.")
                } else {
                    screen.later(700) { filters(reads + 1, true, tappedAll) }
                }
            }
            else -> probeReset(l)
        }
    }

    private fun valid(l: Look): Boolean = picker(l.words) &&
        l.allSelected &&
        l.ownedFilter?.checked == false &&
        l.rows.isNotEmpty() &&
        l.profile.isNotEmpty() &&
        l.height >= 400

    /** Exercise a short downward move before reset; an ineffective learned reset must not masquerade as top. */
    private fun probeReset(before: Look) {
        if (!valid(before)) {
            before.done("pro-picker-grid")
            return fail("Không nhận rõ lưới tướng.")
        }
        val profile = before.profile
        val height = before.height
        before.done(null)
        scroll(.12) {
            look { after ->
                val shift = HeroGrid.shift(profile, after.profile)
                if (!valid(after) || after.height != height || !shift.reliable || shift.px < -2) {
                    after.done("pro-picker-shift")
                    return@look fail("Không đo được thao tác kiểm tra cuộn.")
                }
                reset(after, 0, 0, shift.px > 2)
            }
        }
    }

    private fun reset(before: Look, attempts: Int, still: Int, mustMove: Boolean = false) {
        if (!valid(before)) {
            before.done("pro-picker-grid")
            return fail("Không nhận rõ lưới tướng; không coi là hết danh sách.")
        }
        if (attempts >= 160) {
            before.done("pro-picker-top")
            return fail("Không xác minh được đầu danh sách tướng.")
        }
        val profile = before.profile
        val height = before.height
        before.done(null)
        scroll(-.24) {
            look { after ->
                if (!valid(after) ||
                    after.height != height
                ) {
                    after.done("pro-picker-grid")
                    return@look fail("Lưới tướng thay đổi khi cuộn.")
                }
                val shift = HeroGrid.shift(profile, after.profile)
                if (!shift.reliable ||
                    shift.px > height * .01
                ) {
                    after.done("pro-picker-shift")
                    return@look fail("Không đo được khoảng cuộn về đầu; chưa chọn tướng.")
                }
                val count = if (abs(shift.px) <= 2) still + 1 else 0
                if (count >= 2) {
                    if (mustMove) {
                        after.done("pro-picker-top")
                        return@look fail("Đường cuộn về đầu không di chuyển lưới; kiểm tra điểm đã chỉnh.")
                    }
                    val first = after.rows.first()
                    if (first.second - first.first < BoardScreens.CARD - .015 || first.first > .37) {
                        after.done("pro-picker-top")
                        return@look fail("Không xác minh được hàng đầu đầy đủ.")
                    }
                    rowZero = first.first
                    seek(after, 0)
                } else {
                    reset(after, attempts + 1, count, mustMove && abs(shift.px) <= 2)
                }
            }
        }
    }

    private fun seek(l: Look, still: Int) {
        if (!valid(l)) {
            l.done("pro-picker-grid")
            return fail("Mất lưới tướng trong lúc tìm; chưa xác định hết danh sách.")
        }
        val top = rowZero + index / 8 * BoardScreens.ROW_PITCH - offset
        val bottom = top + BoardScreens.CARD
        val band = l.rows.firstOrNull { abs(it.first - top) < .018 && it.second - it.first >= BoardScreens.CARD - .015 }
        if (top >= BoardScreens.VIEW_TOP - .005 && bottom <= BoardScreens.VIEW_BOTTOM + .005) {
            if (band == null) {
                l.done("pro-picker-row")
                return fail("Không khớp hàng tướng đã đo; chưa xác định hết danh sách.")
            }
            if (l.card(index % 8, band.first, band.second)) {
                val y = (band.first + band.second) / 2
                l.done(null)
                return tap("card", BoardScreens.COLUMNS[index % 8], y) { selected(0) }
            }
            // A genuine short final row is a contiguous prefix, never a hole among existing heroes.
            val occupied = (0..7).map { l.card(it, band.first, band.second) }
            val count = occupied.takeWhile { it }.size
            val trailing = count in 1..7 &&
                occupied.drop(count).none { it } &&
                index % 8 >= count &&
                l.rows.none { it.first > band.first + .03 }
            if (!trailing) {
                l.done("pro-picker-missing-card")
                return fail("Ô tướng không rõ hoặc có khoảng trống giữa danh sách; chưa kết luận hết.")
            }
            return confirmEnd(l, 0)
        }
        if (top < BoardScreens.VIEW_TOP || ++moves > 160) {
            l.done("pro-picker-seek")
            return fail("Vị trí tướng ngoài vùng đã đo.")
        }
        if (still >= 2) return confirmEnd(l, 0)
        val profile = l.profile
        val height = l.height
        val distance = minOf(.24, maxOf(.06, bottom - .84))
        l.done(null)
        scroll(distance) {
            look { after ->
                if (!valid(after) || after.height != height) {
                    after.done("pro-picker-grid")
                    return@look fail("Mất lưới khi cuộn tìm tướng.")
                }
                val shift = HeroGrid.shift(profile, after.profile)
                if (!shift.reliable || shift.px < -2 || shift.px > height * .36) {
                    after.done("pro-picker-shift")
                    return@look fail("Không đo chắc chắn khoảng cuộn; chưa kết luận hết danh sách.")
                }
                offset += shift.px.toDouble() / height
                seek(after, if (abs(shift.px) <= 2) still + 1 else 0)
            }
        }
    }

    /** A stalled gesture alone is not the end: a whole last row and blank trailing space must agree. */
    private fun confirmEnd(l: Look, still: Int) {
        val last = l.rows.lastOrNull()
        val trailingSpace = last != null &&
            last.second - last.first >= BoardScreens.CARD - .015 &&
            last.second < BoardScreens.VIEW_BOTTOM - .005
        if (!valid(l) || !trailingSpace) {
            l.done("pro-picker-end")
            return fail("Danh sách không cuộn nhưng chưa thấy hàng cuối đầy đủ; kiểm tra đường vuốt đã chỉnh.")
        }
        if (still >= 2) {
            l.done(null)
            return end(Outcome.End)
        }
        val profile = l.profile
        val height = l.height
        l.done(null)
        scroll(.15) {
            look { after ->
                val shift = HeroGrid.shift(profile, after.profile)
                if (!valid(after) || after.height != height || !shift.reliable || abs(shift.px) > 2) {
                    after.done("pro-picker-end")
                    fail("Lưới còn chuyển động hoặc chưa xác minh được cuối danh sách; chưa báo hoàn tất.")
                } else {
                    confirmEnd(after, still + 1)
                }
            }
        }
    }

    private fun selected(reads: Int): Unit = look { l ->
        val ok = l.preparation && !picker(l.words)
        l.done(if (!ok && reads >= 3) "pro-picker-selection" else null)
        if (ok) {
            end(Outcome.Selected(index))
        } else if (reads < 3) {
            screen.later(700) { selected(reads + 1) }
        } else {
            fail("Chọn tướng chưa về Chuẩn bị; không bấm lại hoặc thu thập nhầm tướng.")
        }
    }
}
