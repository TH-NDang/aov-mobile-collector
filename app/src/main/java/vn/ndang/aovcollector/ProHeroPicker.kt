package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * Preparation's eight-column picker. Artwork, not repeating row geometry, measures every scroll. [memory] carries what
 * one pick proved about the list to the next, so a list that opens at its top, or where the last pick left it, is
 * neither probed nor flung back for every hero.
 */
class ProHeroPicker(
    private val screen: Screen,
    private val index: Int,
    private val memory: Memory = Memory(),
    private val finish: (Outcome) -> Unit
) {
    /** The list's verified top and where the last hero was chosen, as portrait profiles of one screen height. */
    class Memory {
        var height = 0
        var rowZero = 0.0
        var top: FloatArray? = null
        var last: FloatArray? = null
        var lastOffset = 0.0

        /** Chọn tướng's header as last read whole, for the looks after whose words the reader misses. */
        var anchor: Anchor? = null
    }

    /** The words above the grid on a look where Chọn tướng, Tất cả and Đã nhận were all read, and its title's picture. */
    class Anchor(val words: List<GameScreens.Word>, val title: FloatArray, val height: Int)

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

        /** The picture of the title strip, as [ProHeroPicker.title]. */
        val title: FloatArray

        /** [allSelected] and [ownedFilter] on this picture with the header's words from [words], read on another look. */
        fun header(words: List<GameScreens.Word>): Pair<Boolean, BoardScreens.OwnedFilter?>

        fun card(col: Int, top: Double, bottom: Double): Boolean

        fun done(keep: String?)
    }

    interface Screen {
        val alive: Boolean

        fun look(then: (Look?) -> Unit)

        fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        fun note(text: String) {}
    }

    companion object {
        /** Flicks up before the top must have been found: a few screens of list each. */
        const val MAX_RESET_FLICKS = 25

        /** Readings of one screen before its text is taken as truly missing: the reader misses words now and then. */
        const val READS = 3

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

        /**
         * The strip holding the title "Chọn tướng", as the brightness of small squares. It does not move or glow: on the
         * user's phone two pictures of it seventeen hours apart are the same to the pixel, while the Pro list's title in
         * the same frame agrees at .45 and Chuẩn bị not at all.
         */
        fun title(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): FloatArray {
            val cell = maxOf(2, height / 270)
            val x0 = (.155 * width).toInt()
            val y0 = (.09 * height).toInt()
            val cols = ((.265 * width).toInt() - x0) / cell
            val rows = ((.148 * height).toInt() - y0) / cell
            if (width <= height || cols <= 0 || rows <= 0) return floatArrayOf()
            val out = FloatArray(cols * rows)
            val row = IntArray(width)
            for (y in 0 until rows * cell) {
                readRow(y0 + y, row)
                for (x in 0 until cols * cell) {
                    val p = row[x0 + x]
                    out[y / cell * cols + x / cell] += ((p shr 16 and 255) * 3 + (p shr 8 and 255) * 6 + (p and 255)) / 10f
                }
            }
            for (i in out.indices) out[i] /= (cell * cell).toFloat()
            return out
        }

        /** Two title strips showing the same title: the same shapes, at the same brightness. */
        fun sameTitle(a: FloatArray, b: FloatArray): Boolean {
            if (a.isEmpty() || a.size != b.size) return false
            val ma = a.average()
            val mb = b.average()
            var ab = 0.0
            var aa = 0.0
            var bb = 0.0
            var apart = 0.0
            for (i in a.indices) {
                ab += (a[i] - ma) * (b[i] - mb)
                aa += (a[i] - ma) * (a[i] - ma)
                bb += (b[i] - mb) * (b[i] - mb)
                apart += abs(a[i] - b[i])
            }
            // Letters on the frame spread the brightness by 40; a plain strip has no title to tell.
            if (aa / a.size < 100 || bb / a.size < 100) return false
            return ab / kotlin.math.sqrt(aa * bb) >= .95 && apart / a.size <= 6
        }
    }

    /** What a look shows of the header: by its own words, or by its picture and the words of [Memory.anchor]. */
    private class Head(val picker: Boolean, val all: GameScreens.Word?, val allSelected: Boolean, val filter: BoardScreens.OwnedFilter?)

    private var headOf: Look? = null
    private var headIs: Head? = null
    private var byPicture = false

    /**
     * The reader gives lines, and now and then it misses one of the header's, or joins it to its neighbour: Chọn tướng,
     * Tất cả, or Đã nhận then is not there by its words, with the frame whole on the picture. Six such readings in a row
     * stopped the user's run at hero 104 twice, after two at hero 56. So once the header was read whole, a look whose
     * title strip is that same picture is that same frame: the underline under Tất cả and the box beside Đã nhận are
     * then looked for where they were read, and the words are not asked again. Another frame, or Chuẩn bị, has another
     * title strip, and is never taken for it.
     */
    private fun head(l: Look): Head {
        if (headOf === l) headIs?.let { return it }
        val own = Head(picker(l.words), allButton(l.words), l.allSelected, l.ownedFilter)
        val anchor = memory.anchor
        val head = if (own.picker && own.all != null && own.filter != null) {
            memory.anchor = Anchor(l.words.filter { it.y < BoardScreens.VIEW_TOP }, l.title, l.height)
            own
        } else if (anchor != null && anchor.height == l.height && sameTitle(anchor.title, l.title)) {
            val (allSelected, filter) = l.header(anchor.words)
            if (!byPicture) {
                byPicture = true
                screen.note("Pro: ${unread(l)}; khung Chọn tướng vẫn đúng hình đã đọc trước nên nhận theo hình")
            }
            Head(true, allButton(anchor.words), allSelected, filter)
        } else {
            own
        }
        headOf = l
        headIs = head
        return head
    }

    /** For the log: which of the header's words a look lacks, and the lines the reader did give above the grid. */
    private fun unread(l: Look): String {
        val lacking = listOfNotNull(
            "Chọn tướng".takeIf { !picker(l.words) },
            "Tất cả".takeIf { allButton(l.words) == null },
            "ô Đã nhận".takeIf { l.ownedFilter == null }
        )
        val read = l.words.filter { it.y < BoardScreens.VIEW_TOP }.joinToString(" | ") { it.text.take(30) }.take(240)
        return "không đọc được ${lacking.joinToString(", ").ifEmpty { "đầu khung" }} (chữ đọc được: ${read.ifEmpty { "không có" }})"
    }

    /** For the log: why a look is not the grid to scroll. Before [Look.done]. */
    private fun why(l: Look): String {
        val h = head(l)
        val wrong = listOfNotNull(
            unread(l).takeIf { !h.picker },
            "Tất cả chưa được chọn".takeIf { h.picker && !h.allSelected },
            "không thấy ô Đã nhận".takeIf { h.picker && h.filter == null },
            "Đã nhận đang bật".takeIf { h.filter?.checked == true },
            "không thấy hàng tướng".takeIf { l.rows.isEmpty() || l.profile.isEmpty() }
        )
        return wrong.joinToString("; ").ifEmpty { "màn hình đổi cỡ" }
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

    /** [fling]: a quick flick; otherwise a drag held still before lifting, so the grid stops where the finger does. */
    private fun action(id: String, points: List<CalibrationPoint>, fling: Boolean = false, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(
            CalibrationAction(
                "pro.v1.picker.$id",
                "Pro: $id",
                points,
                relative = true,
                durationMs = if (points.size == 1) {
                    100
                } else if (fling) {
                    160
                } else {
                    700
                },
                holdMs = if (points.size > 1 && !fling) 450 else 0
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

    private fun tap(id: String, x: Double, y: Double, then: () -> Unit) = action(id, listOf(CalibrationPoint(x, y)), then = then)

    private fun scroll(d: Double, then: () -> Unit) = action(
        if (d < 0) "scroll.reset" else "scroll.next",
        listOf(CalibrationPoint(.62, if (d < 0) .42 else .78), CalibrationPoint(.62, (if (d < 0) .42 else .78) - d)),
        false,
        then
    )

    private fun flick(id: String, from: Double, to: Double, then: () -> Unit) =
        action(id, listOf(CalibrationPoint(.62, from), CalibrationPoint(.62, to)), true, then)

    /** A whole row where the grid's first row is with the list at its top. */
    private fun topRow(first: Pair<Double, Double>) = first.second - first.first >= BoardScreens.CARD - .015 && first.first <= .37

    fun start() {
        require(index in 0 until BuiltInMacros.ALL)
        opening(0)
    }

    /** Read again before giving up: the session saw Chuẩn bị a moment ago, and one reading had missed it. */
    private fun opening(reads: Int): Unit = look { l ->
        val isPicker = head(l).picker
        val preparation = l.preparation
        l.done(if (!isPicker && !preparation && reads + 1 >= READS) "pro-picker-open" else null)
        when {
            isPicker -> filters(0, false, false)
            preparation -> tap("open", .094, .821) { filters(0, false, false) }
            reads + 1 < READS -> screen.later(700) { opening(reads + 1) }
            else -> fail("Mở màn Chuẩn bị hoặc Chọn tướng trước khi thu thập Pro.")
        }
    }

    private fun filters(reads: Int, tappedOwned: Boolean, tappedAll: Boolean): Unit = look { l ->
        val head = head(l)
        if (!head.picker) {
            if (reads >= 3) screen.note("Pro: không mở được Chọn tướng: ${unread(l)}")
            l.done(if (reads >= 3) "pro-picker-unknown" else null)
            if (reads >= 3) fail("Không mở được Chọn tướng.") else screen.later(700) { filters(reads + 1, tappedOwned, tappedAll) }
            return@look
        }
        val filter = head.filter
        val all = head.all
        when {
            !head.allSelected && all != null && !tappedAll -> {
                l.done(null)
                tap("all", all.x, all.y) { filters(0, tappedOwned, true) }
            }
            !head.allSelected || filter == null -> {
                if (reads >= 3) screen.note("Pro: chưa xác minh được Tất cả / Đã nhận: ${why(l)}")
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
            else -> resume(l)
        }
    }

    /**
     * Before any probe: a list still where the last pick left it, or at the top proven before, is used as it is. The
     * user's run probed, slid down and dragged back up for every hero, three to four minutes a hero.
     */
    private fun resume(l: Look) {
        if (valid(l) && l.height == memory.height) {
            val delta = memory.last?.let { BoardPickerMotion.displacement(it, l.profile, l.height, 0) }
            if (delta != null) {
                val position = memory.lastOffset + delta
                if (memory.rowZero + index / 8 * BoardScreens.ROW_PITCH - position >= BoardScreens.VIEW_TOP - .005) {
                    rowZero = memory.rowZero
                    offset = position
                    screen.note("Pro: Chọn tướng còn ở chỗ lượt trước; không vuốt về đầu lại")
                    return seek(l, 0)
                }
            }
            val top = memory.top
            val first = l.rows.firstOrNull()
            if (top != null && first != null && topRow(first) && BoardPickerMotion.sameViewport(top, l.profile)) {
                rowZero = first.first
                offset = 0.0
                screen.note("Pro: Chọn tướng mở ở đầu danh sách như lần trước; không vuốt về đầu lại")
                return seek(l, 0)
            }
        }
        probeReset(l)
    }

    private fun valid(l: Look): Boolean = head(l).let { it.picker && it.allSelected && it.filter?.checked == false } &&
        l.rows.isNotEmpty() &&
        l.profile.isNotEmpty() &&
        l.height >= 400

    /** Exercise a short downward move before reset; an ineffective learned reset must not masquerade as top. */
    private fun probeReset(before: Look) {
        if (!valid(before)) {
            screen.note("Pro: không nhận rõ lưới tướng: ${why(before)}")
            before.done("pro-picker-grid")
            return fail("Không nhận rõ lưới tướng.")
        }
        val profile = before.profile
        val height = before.height
        before.done(null)
        // A quick flick, never a short slow press: the game takes a short slow drag resting on a card for a tap
        // and chooses that hero (Chiến khu on the user's phone, the same Chọn tướng grid). A short one: a flick of
        // .3 slid the list far down, and the way back up cost a look per quarter screen.
        flick("scroll.probe-flick", .80, .70) {
            look { after ->
                val shift = HeroGrid.shift(profile, after.profile)
                if (!valid(after) || after.height != height || (shift.reliable && shift.px < -2)) {
                    after.done("pro-picker-shift")
                    return@look fail("Không đo được thao tác kiểm tra cuộn.")
                }
                // A flick can go further than can be measured: any change proves the grid moves.
                val moved = if (shift.reliable) shift.px > 2 else !BoardPickerMotion.sameViewport(profile, after.profile)
                reset(after, 0, 0, false, moved)
            }
        }
    }

    /**
     * Flicks up until the portraits stop moving: twice in a row unmoved is the top, which is then remembered for the
     * picks after. [movedBack]: a flick has moved the list up; [probeMoved]: the probe moved it down, so the way back
     * must move it too.
     */
    private fun reset(before: Look, attempts: Int, still: Int, movedBack: Boolean, probeMoved: Boolean) {
        if (!valid(before)) {
            screen.note("Pro: không nhận rõ lưới tướng: ${why(before)}")
            before.done("pro-picker-grid")
            return fail("Không nhận rõ lưới tướng; không coi là hết danh sách.")
        }
        if (attempts >= MAX_RESET_FLICKS) {
            before.done("pro-picker-top")
            return fail("Không xác minh được đầu danh sách tướng.")
        }
        val profile = before.profile
        val height = before.height
        before.done(null)
        flick("scroll.reset-flick", .40, .85) {
            look { after ->
                if (!valid(after) ||
                    after.height != height
                ) {
                    screen.note("Pro: lưới tướng thay đổi khi cuộn về đầu: ${why(after)}")
                    after.done("pro-picker-grid")
                    return@look fail("Lưới tướng thay đổi khi cuộn.")
                }
                val same = BoardPickerMotion.sameViewport(profile, after.profile)
                val count = if (same) still + 1 else 0
                val moved = movedBack || !same
                if (count < 2) return@look reset(after, attempts + 1, count, moved, probeMoved)
                if (probeMoved && !moved) {
                    after.done("pro-picker-top")
                    return@look fail("Đường cuộn về đầu không di chuyển lưới; kiểm tra điểm đã chỉnh.")
                }
                val first = after.rows.first()
                if (!topRow(first)) {
                    after.done("pro-picker-top")
                    return@look fail("Không xác minh được hàng đầu đầy đủ.")
                }
                rowZero = first.first
                offset = 0.0
                memory.height = after.height
                memory.rowZero = rowZero
                memory.top = after.profile
                screen.note("Pro: đã xác minh đầu danh sách Chọn tướng; các lượt sau dùng lại")
                seek(after, 0)
            }
        }
    }

    private fun seek(l: Look, still: Int) {
        if (!valid(l)) {
            screen.note("Pro: mất lưới tướng trong lúc tìm: ${why(l)}")
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
                memory.height = l.height
                memory.rowZero = rowZero
                memory.last = l.profile
                memory.lastOffset = offset
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
        // Never shorter than a slow drag that could rest on a card and choose it; overshooting only leaves the row higher.
        val distance = minOf(.24, maxOf(BoardReader.MIN_SLOW_DRAG, bottom - .84))
        l.done(null)
        scroll(distance) { scrolled(profile, height, still, 0) }
    }

    /**
     * The grid after a scroll. A look that is not the grid, by its words or by its picture ([head]), is not a lost
     * grid at once: it is taken again, as on opening, and only [READS] in a row stop the run, the log saying what each
     * lacked.
     */
    private fun scrolled(profile: FloatArray, height: Int, still: Int, reads: Int): Unit = look { after ->
        if (!valid(after) || after.height != height) {
            val why = why(after)
            if (reads + 1 < READS) {
                after.done(null)
                screen.note("Pro: chưa đọc rõ lưới sau khi cuộn ($why); đọc lại (lần ${reads + 2})")
                return@look screen.later(700) { scrolled(profile, height, still, reads + 1) }
            }
            screen.note("Pro: mất lưới sau khi cuộn: $why")
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
        val ok = l.preparation && !head(l).picker
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
