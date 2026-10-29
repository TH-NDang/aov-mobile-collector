package vn.ndang.aovcollector

import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Complete-combo popup, not the equipment-only recommendations screen. Unknown is never empty. */
object ProComboScreens {
    data class Entry(val id: String, val label: String, val button: GameScreens.Word, val signature: IntArray)

    private fun label(words: List<GameScreens.Word>, text: String) = words.firstOrNull {
        it.plain.contains(text) && it.right - it.left < .35 && it.bottom - it.top < .09
    }

    fun popup(words: List<GameScreens.Word>) = label(words, "hethongdexuat") != null && label(words, "decupro") != null

    fun preparation(words: List<GameScreens.Word>) = !popup(words) &&
        label(words, "chuanbi") != null &&
        label(words, "trangbi") != null &&
        label(words, "phuhieu") != null &&
        label(words, "phutro") != null

    fun heroName(words: List<GameScreens.Word>): String? = words.filter {
        it.x in .26..0.55 && it.y in .87..0.94 && it.right - it.left < .3 && it.plain.length in 2..40
    }.minByOrNull { abs(it.x - .36) }?.text?.trim()?.takeIf { HeroNames.slug(it).isNotBlank() }

    fun otherButton(words: List<GameScreens.Word>) = words.firstOrNull {
        (it.plain.contains("phankhac") || it.plain.contains("phuongan khac".replace(" ", ""))) && it.x > .78 && it.y > .83
    }

    fun proTab(words: List<GameScreens.Word>) = label(words, "decupro")?.takeIf { it.x in .15..0.28 && it.y in .35..0.55 }

    fun blocked(words: List<GameScreens.Word>) = words.any {
        it.x in .30..0.82 && listOf("dangtai", "ketnoi", "thulai", "loihethong").any { key -> it.plain.contains(key) }
    }

    fun empty(words: List<GameScreens.Word>): Boolean = popup(words) &&
        words.any {
            it.x in .30..0.82 &&
                it.y in .20..0.85 &&
                it.plain in setOf("chuacodexuat", "khongcodexuat", "chuacodecu", "khongcodecu", "chuacophuongan", "khongcophuongan")
        } &&
        !blocked(words) &&
        words.none { it.plain.contains("dungcombo") }

    /** The selected Pro tab has a saturated blue glow LEFT of its text. Pale text/icons are not selection.
     * Sampling above/below the narrow glow or requiring green >100 misses the actual supplied UI.
     */
    fun proSelected(width: Int, height: Int, words: List<GameScreens.Word>, readRow: (Int, IntArray) -> Unit): Boolean {
        val tab = proTab(words) ?: return false
        val row = IntArray(width)
        var bright = 0
        var total = 0
        for (dy in listOf(-.006, 0.0, .006)) {
            readRow(((tab.y + dy) * height).toInt().coerceIn(0, height - 1), row)
            for (x in (width * .150).toInt()..(width * .174).toInt() step maxOf(1, width / 500)) {
                val c = row[x]
                val r = c shr 16 and 255
                val g = c shr 8 and 255
                val b = c and 255
                if (b > 180 && b - r > 120 && r < 90 && g < 125) bright++
                total++
            }
        }
        var surround = 0
        var surroundTotal = 0
        for (dy in listOf(-.035, .035)) {
            readRow(((tab.y + dy) * height).toInt().coerceIn(0, height - 1), row)
            for (x in (width * .150).toInt()..(width * .174).toInt() step maxOf(1, width / 500)) {
                val color = row[x]
                val r = color shr 16 and 255
                val g = color shr 8 and 255
                val b = color and 255
                if (b > 180 && b - r > 120 && r < 90 && g < 125) surround++
                surroundTotal++
            }
        }
        return total > 0 && bright.toDouble() / total > .35 && surroundTotal > 0 && surround.toDouble() / surroundTotal < .20
    }

    private fun signature(width: Int, height: Int, points: List<Pair<Double, Double>>, readRow: (Int, IntArray) -> Unit): IntArray {
        val row = IntArray(width)
        val result = ArrayList<Int>()
        for ((x, y) in points) {
            for (dy in listOf(-.014, 0.0, .014)) {
                readRow(((y + dy) * height).toInt().coerceIn(0, height - 1), row)
                for (dx in listOf(-.006, 0.0, .006)) {
                    val p = row[((x + dx) * width).toInt().coerceIn(0, width - 1)]
                    result += p shr 16 and 255
                    result += p shr 8 and 255
                    result += p and 255
                }
            }
        }
        return result.toIntArray()
    }

    private fun rune(words: List<GameScreens.Word>, x: Double, y: Double): Int? = words.filter {
        abs(it.x - x) < .025 && abs(it.y - y) < .04
    }.mapNotNull { it.text.trim().toIntOrNull() }.distinct().singleOrNull()?.takeIf {
        it in
            0..90
    }

    fun preparationSignature(width: Int, height: Int, words: List<GameScreens.Word>, readRow: (Int, IntArray) -> Unit): IntArray {
        val rune = rune(words, .622, .600) ?: return intArrayOf()
        return intArrayOf(rune) + signature(
            width,
            height,
            listOf(.622, .670, .718, .766, .814, .862).map { it to .256 } +
                listOf(.622, .671, .720, .770, .819).map { it to .428 } + listOf(.622 to .776),
            readRow
        )
    }

    fun entries(width: Int, height: Int, words: List<GameScreens.Word>, readRow: (Int, IntArray) -> Unit): List<Entry> {
        if (!popup(words)) return emptyList()
        return words.filter { it.plain == "dungcombo" && it.x in .69..0.82 && it.y in .30..0.84 && it.bottom - it.top < .07 }
            .distinctBy { (it.y * 40).toInt() }.sortedBy { it.y }.mapNotNull { button ->
                // A complete card must include its creator and all component rows, not a clipped bottom card.
                val creatorY = button.y - .185
                val creator = words.filter {
                    it.left in .29..0.57 &&
                        abs(it.y - creatorY) < .045 &&
                        it.right - it.left < .32 &&
                        it.plain.isNotBlank() &&
                        !it.plain.contains("chienluc") &&
                        !it.plain.contains("tylethang")
                }
                    .minByOrNull { abs(it.y - creatorY) } ?: return@mapNotNull null
                if (button.y + .10 > .90 || creator.top < .18) return@mapNotNull null
                val points = listOf(.316, .363, .410, .457, .504, .551).map { it to (button.y - .058) } +
                    listOf(.315, .380, .446, .517, .555).map { it to (button.y + .055) } + listOf(.635 to (button.y - .058))
                val rune = rune(words, .635, button.y + .055) ?: return@mapNotNull null
                Entry(creator.plain, creator.text, button, intArrayOf(rune) + signature(width, height, points, readRow))
            }
    }

    /** Native-resolution list OCR replaces only the list, never popup/tab/hero evidence. */
    fun withFocusedList(ordinary: List<GameScreens.Word>, focused: List<GameScreens.Word>): List<GameScreens.Word> {
        if (focused.none { it.plain == "dungcombo" && it.x in .69..0.82 }) return ordinary

        fun inList(word: GameScreens.Word) = word.x in .28..0.84 && word.y in .18..0.90
        return ordinary.filterNot(::inList) + focused.filter(::inList)
    }

    /** A missing creator/rune is an OCR gap, not a reason to move the list again. */
    fun incompleteCards(words: List<GameScreens.Word>, entries: List<Entry>): Boolean {
        if (empty(words)) return false
        if (entries.isEmpty()) return true
        return words.any { word ->
            word.plain == "dungcombo" &&
                word.x in .69..0.82 &&
                word.y in .38..0.80 &&
                entries.none { abs(it.button.y - word.y) < .015 }
        }
    }

    /** Same image geometry for every list frame; excludes hero animation, side tab, tooltip and footer. */
    fun listSignature(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): IntArray = signature(
        width,
        height,
        (0..11).flatMap { y -> listOf(.32, .42, .52).map { x -> x to (.20 + y * .059) } },
        readRow
    )

    /** Positive trailing interior blank; footer outside the list is deliberately excluded. */
    fun listBottomClear(width: Int, height: Int, entries: List<Entry>, readRow: (Int, IntArray) -> Unit): Boolean {
        val last = entries.lastOrNull() ?: return false
        val from = last.button.y + .125
        if (from > .855) {
            // The content card's lower edge, not the outer modal footer, is aligned with the
            // observed viewport bottom. This evidence is only used after the movement probe.
            val edge = last.button.y + .118
            if (abs(edge - .893) > .012) return false
            val above = IntArray(width)
            val below = IntArray(width)
            readRow(((edge - .006) * height).toInt().coerceIn(0, height - 1), above)
            readRow(((edge + .010) * height).toInt().coerceIn(0, height - 1), below)
            return listOf(.32, .40, .48, .56, .64, .72, .80).count { xf ->
                val x = (xf * width).toInt().coerceIn(0, width - 1)
                listOf(0, 8, 16).sumOf { shift -> abs((above[x] shr shift and 255) - (below[x] shr shift and 255)) } >= 25
            } >= 5
        }
        val row = IntArray(width)
        val colors = ArrayList<Int>()
        for (yf in listOf(from, (from + .885) / 2, .885)) {
            readRow((yf * height).toInt().coerceIn(0, height - 1), row)
            for (xf in listOf(.35, .45, .55, .65, .75, .80)) colors += row[(xf * width).toInt().coerceIn(0, width - 1)]
        }
        for (shift in listOf(0, 8, 16)) {
            val values = colors.map { it shr shift and 255 }
            if (values.max() - values.min() > 28) return false
        }
        return colors.all {
            val r = it shr 16 and 255
            val b = it and 255
            b > r + 25 && b > 90
        }
    }

    fun matches(a: IntArray, b: IntArray): Boolean {
        if (a.size != 325 || b.size != a.size || a[0] != b[0]) return false
        // A single wrong spell/enchantment must not disappear into the mean of twelve icons.
        return (0 until 12).all { icon ->
            val start = 1 + icon * 27
            (start until start + 27).sumOf { abs(a[it] - b[it]) }.toDouble() / 27 < 34.0
        }
    }

    /**
     * Animated background/tooltip/item glows can change pixel snapshots while card positions
     * remain fixed. This checks motion, NOT whether a Pro combo is still safe to apply:
     * the latter must continue using strict [matches] and [uniqueMatch].
     *
     * Require the same creator, rune, Y, and at least 10/12 unchanged icon regions.
     * One or two shimmering icon effects cannot force a long series of upward scrolls.
     * A short list additionally needs top and bottom evidence and BOTH swipe directions.
     */
    fun rowsStationary(before: List<Entry>, after: List<Entry>): Boolean = before.isNotEmpty() &&
        before.size == after.size &&
        before.indices.all { i ->
            val a = before[i]
            val b = after[i]
            sameCreator(a.id, b.id) &&
                abs(a.button.y - b.button.y) < .012 &&
                a.signature.size == 325 &&
                b.signature.size == 325 &&
                a.signature[0] == b.signature[0] &&
                (0 until 12).count { icon ->
                    val start = 1 + icon * 27
                    (start until start + 27).sumOf { abs(a.signature[it] - b.signature[it]) }.toDouble() / 27 < 45.0
                } >= 10
        }

    /**
     * Creator names are decorated ("彡MinhVĩĐại彡", "càrốt€"): from one frame to the next OCR drops such a
     * symbol or reads it as a digit or a letter. The same creator: the same core once a digit read on one side
     * only is dropped at either end ("5minhvidai", "minhvidai5"), one core inside the other with at most two
     * more letters ("carote", "carot"), or one letter apart in a core of five letters (two in one of nine).
     * Different digits at the same end are different names ("creator1", "creator2"); a core under three
     * letters must match exactly. Every use pairs it with the combo's own components.
     */
    fun sameCreator(a: String, b: String): Boolean {
        if (a == b) return true
        val x = a.trim { it.isDigit() }
        val y = b.trim { it.isDigit() }

        fun fits(p: String, q: String) = p.isEmpty() || q.isEmpty() || p == q
        if (!fits(a.takeWhile { it.isDigit() }, b.takeWhile { it.isDigit() })) return false
        if (!fits(a.takeLastWhile { it.isDigit() }, b.takeLastWhile { it.isDigit() })) return false
        if (x.length < 3 || y.length < 3) return false
        if (x == y) return true
        val shorter = minOf(x.length, y.length)
        val longer = if (x.length >= y.length) x else y
        val inner = if (x.length >= y.length) y else x
        if (shorter >= 4 &&
            longer.length - shorter <= 2 &&
            inner in longer &&
            longer.replaceFirst(inner, "").none { it.isDigit() }
        ) {
            return true
        }
        val allowed = if (shorter >= 9) {
            2
        } else if (shorter >= 5) {
            1
        } else {
            0
        }
        return allowed > 0 && longer.length - shorter <= allowed && distance(x, y) <= allowed
    }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diagonal = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val above = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, diagonal + if (a[i - 1] == b[j - 1]) 0 else 1)
                diagonal = above
            }
        }
        return d[b.length]
    }

    /** Fuzzy observations must resolve to one exact frozen descriptor across the whole list, not just top five. */
    fun uniqueMatch(expected: Entry, candidate: Entry, catalog: List<Entry>): Boolean {
        if (!sameCreator(candidate.id, expected.id)) return false
        val matched = catalog.filter { sameCreator(it.id, candidate.id) && matches(it.signature, candidate.signature) }
        return matched.size == 1 && matched[0].id == expected.id && matched[0].signature.contentEquals(expected.signature)
    }

    fun catalog(journal: JSONObject): List<Entry> = try {
        val a = journal.optJSONArray("catalog") ?: JSONArray()
        (0 until a.length()).map { i ->
            val e = a.getJSONObject(i)
            val sig = e.getJSONArray("signature")
            require(sig.length() == 325)
            Entry(
                e.getString("id"),
                e.getString("label"),
                GameScreens.Word("", 0.0, 0.0, 0.0, 0.0),
                IntArray(sig.length()) {
                    sig.getInt(it)
                }
            )
        }
    } catch (_: RuntimeException) {
        emptyList()
    }

    fun still(a: IntArray, b: IntArray): Boolean = a.isNotEmpty() &&
        a.size == b.size &&
        a.indices.sumOf { abs(a[it] - b[it]).toLong() }.toDouble() / a.size < 3.5
}

/** One hero, durable intent before every irreversible apply/capture, no optimistic crash retry. */
class ProComboReader(
    private val screen: Screen,
    private val hero: String,
    private val journal: JSONObject,
    private val finish: (Outcome) -> Unit
) {
    interface Look {
        val words: List<GameScreens.Word>
        val proSelected: Boolean
        val entries: List<ProComboScreens.Entry>
        val preparationSignature: IntArray?
        val listSignature: IntArray
        val listBottomClear: Boolean

        fun done(keep: String?)

        fun keep(name: String, then: (Boolean) -> Unit)
    }

    interface Screen {
        val alive: Boolean

        fun look(then: (Look?) -> Unit)

        /** Adapter must reject a calibrated tap outside [target], including saved offsets. */
        fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit)

        fun later(ms: Long, then: () -> Unit)

        fun persist(journal: JSONObject): Boolean

        fun captureExists(name: String): Boolean

        fun say(text: String) {}
    }

    sealed class Outcome {
        data class Done(val count: Int, val empty: Boolean) : Outcome()

        data class Failed(val reason: String) : Outcome()
    }

    private fun same(a: ProComboScreens.Entry, b: ProComboScreens.Entry) =
        ProComboScreens.sameCreator(a.id, b.id) && ProComboScreens.matches(a.signature, b.signature)

    private fun ambiguous(entries: List<ProComboScreens.Entry>) = entries.indices.any { i ->
        (i + 1 until entries.size).any { same(entries[i], entries[it]) }
    }

    private var ended = false
    private var current = 0
    private var restoring = false
    private var moves = 0
    private var stable = 0
    private var last: IntArray? = null
    private var returnedFromProbe = false
    private var endProbed = false
    private var shortListProof: List<ProComboScreens.Entry>? = null
    private var shortListVerified = false
    private var emptyListProbe = false
    private var previousTopEntries = emptyList<ProComboScreens.Entry>()
    private var previousDiscoveryEntries = emptyList<ProComboScreens.Entry>()
    private var stalledTopProbes = 0
    private var discoveryStep = .22
    private var replayPrefix = emptyList<ProComboScreens.Entry>()
    private val chosen = ArrayList<ProComboScreens.Entry>()
    private val discovered = ArrayList<ProComboScreens.Entry>()

    private fun fail(reason: String) {
        if (!ended) {
            ended = true
            finish(Outcome.Failed(reason))
        }
    }

    private fun save(): Boolean = screen.persist(journal).also {
        if (!it) fail("Không lưu được tiến độ Pro; đã dừng trước thao tác tiếp theo")
    }

    private fun look(reads: Int = 0, then: (Look) -> Unit) {
        if (!screen.alive || ended) return
        screen.look { l ->
            if (!screen.alive || ended) {
                l?.done(null)
            } else if (l == null) {
                fail("Không đọc được màn hình Pro")
            } else {
                if (ProComboScreens.popup(l.words) &&
                    l.proSelected &&
                    !ProComboScreens.blocked(l.words) &&
                    ProComboScreens.incompleteCards(l.words, l.entries)
                ) {
                    val buttons = l.words.filter { it.plain == "dungcombo" && it.x in .69..0.82 && it.y in .38..0.80 }
                        .distinctBy { (it.y * 40).toInt() }.size
                    val cards = l.entries.size
                    l.done(if (reads >= 2) "pro-incomplete-cards" else null)
                    if (reads >= 2) {
                        fail("Chưa đọc đủ thông tin combo Pro (nút=$buttons, dòng đủ=$cards); không cuộn tiếp hoặc bấm nhầm")
                    } else {
                        if (reads == 0) screen.say("Đang đọc lại tên người đề cử và bảng ngọc Pro; giữ nguyên danh sách")
                        later { look(reads + 1, then) }
                    }
                } else {
                    then(l)
                }
            }
        }
    }

    private fun later(then: () -> Unit) = screen.later(900) { if (screen.alive && !ended) then() }

    private fun tap(word: GameScreens.Word, id: String, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(CalibrationAction("pro.v1.$id", "Pro: $id", listOf(CalibrationPoint(word.x, word.y)), true), word) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) fail("Không chạm được $id; kiểm tra vùng an toàn/hiệu chỉnh") else later(then)
        }
    }

    private fun drag(up: Boolean, distance: Double = .22, then: () -> Unit) {
        if (!screen.alive || ended) return
        screen.calibrated(
            CalibrationAction(
                "pro.v1.list." + if (up) "reset" else "scroll",
                "Pro: cuộn danh sách",
                listOf(CalibrationPoint(.70, if (up) .30 else .78), CalibrationPoint(.70, if (up) .30 + distance else .78 - distance)),
                relative = true,
                durationMs = 700,
                holdMs = 450
            ),
            null
        ) { ok ->
            if (!screen.alive || ended) return@calibrated
            if (!ok) fail("Không cuộn được danh sách Pro") else later(then)
        }
    }

    private fun validHero(l: Look) = ProComboScreens.preparation(l.words) &&
        ProComboScreens.heroName(l.words)?.let { HeroNames.slug(it) == HeroNames.slug(hero) } == true

    fun start() {
        if (journal.optString("hero", hero) != hero) return fail("Tiến độ Pro thuộc tướng khác")
        journal.put("hero", hero).put("schema", 2)
        val entries = journal.optJSONArray("order")
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val a = e.getJSONArray("signature")
                chosen +=
                    ProComboScreens.Entry(
                        e.getString("id"),
                        e.getString("label"),
                        GameScreens.Word("", 0.0, 0.0, 0.0, 0.0),
                        IntArray(a.length()) {
                            a.getInt(it)
                        }
                    )
            }
        }
        discovered.addAll(ProComboScreens.catalog(journal))
        if (chosen.isNotEmpty() &&
            (
                discovered.isEmpty() ||
                    chosen.any { expected ->
                        !ProComboScreens.uniqueMatch(expected, expected, discovered)
                    }
                )
        ) {
            return fail("Thiếu hoặc không rõ danh mục nhận diện Pro đầy đủ; không áp dụng từ tiến độ cũ")
        }
        current = journal.optInt("captured", 0)
        if (current !in 0..chosen.size || chosen.size > 5 || ambiguous(chosen)) return fail("Tiến độ Pro không hợp lệ")
        when (journal.optString("phase")) {
            "done" -> return finish(Outcome.Done(current, journal.optBoolean("empty")))
            "apply_pending", "restore_pending" -> return recoverApply()
            "capture_pending" -> {
                if (!screen.captureExists(photo(current))) {
                    restoring = false
                    if (current !in chosen.indices) return fail("Tiến độ ảnh Pro không hợp lệ")
                    return verifyApplied(0)
                }
                current++
                journal.put("captured", current).put("phase", "ready").put("restorationNeeded", true)
                if (!save()) return
            }
            "empty_pending" -> {
                if (!screen.captureExists(emptyPhoto())) return confirmEmpty()
                journal.put("phase", "done").put("empty", true)
                if (save()) finish(Outcome.Done(0, true))
                return
            }
        }
        if (chosen.isEmpty()) open({ reset { discover() } }) else next()
    }

    private fun open(then: () -> Unit, tries: Int = 0): Unit = look { l ->
        if (ProComboScreens.popup(l.words)) {
            if (l.proSelected) {
                l.done(null)
                then()
            } else {
                val tab = ProComboScreens.proTab(l.words)
                l.done(null)
                if (tab == null || tries >= 3) fail("Không xác minh được tab Đề cử Pro") else tap(tab, "tab") { open(then, tries + 1) }
            }
        } else if (validHero(l)) {
            val button = ProComboScreens.otherButton(l.words)
            l.done(null)
            if (button == null ||
                tries >= 3
            ) {
                fail("Không tìm thấy Phương án khác của đúng tướng")
            } else {
                tap(button, "other") { open(then, tries + 1) }
            }
        } else {
            l.done(if (tries >= 3) "pro-unknown" else null)
            if (tries >= 3) fail("Không xác minh được màn Chuẩn bị/đúng tướng; chưa áp dụng Pro") else later { open(then, tries + 1) }
        }
    }

    private fun reset(then: () -> Unit) {
        moves = 0
        stable = 0
        last = null
        returnedFromProbe = false
        endProbed = false
        shortListProof = null
        shortListVerified = false
        emptyListProbe = false
        previousTopEntries = emptyList()
        previousDiscoveryEntries = emptyList()
        stalledTopProbes = 0
        look { before ->
            if (!ProComboScreens.popup(before.words) || !before.proSelected || ProComboScreens.blocked(before.words)) {
                before.done("pro-reset-unknown")
                return@look fail("Không xác minh được danh sách Pro trước khi về đầu")
            }
            screen.say("Kiểm tra đầu Pro: ${before.entries.size} combo đủ thông tin; cuối danh sách=${before.listBottomClear}")
            val original = before.listSignature
            val fitting = before.entries.size in 1..2 && before.listBottomClear && abs(before.entries[0].button.y - .408) < .03
            val originalEntries = before.entries
            val empty = ProComboScreens.empty(before.words)
            val boundary = before.listBottomClear
            before.done(null)
            drag(false) {
                look { after ->
                    val moved = !ProComboScreens.still(original, after.listSignature)
                    val explicitSmall =
                        empty &&
                            ProComboScreens.empty(after.words) ||
                            fitting &&
                            after.entries.size == originalEntries.size &&
                            after.listBottomClear &&
                            ProComboScreens.rowsStationary(originalEntries, after.entries)
                    if (!moved &&
                        !explicitSmall &&
                        !(boundary && after.listBottomClear)
                    ) {
                        after.done("pro-scroll-unproven")
                        return@look fail("Không chứng minh được thao tác cuộn Pro; không coi vị trí hiện tại là đầu/cuối")
                    }
                    last = after.listSignature
                    previousTopEntries = after.entries
                    // Raw pixels can change from hero art/animated item glows. The short-list
                    // candidate is based on anchored, unchanged complete cards instead.
                    returnedFromProbe = !moved && explicitSmall
                    if (fitting && explicitSmall) shortListProof = originalEntries
                    emptyListProbe = empty && ProComboScreens.empty(after.words)
                    after.done(null)
                    drag(true) { scanTop(then) }
                }
            }
        }
    }

    private fun scanTop(then: () -> Unit): Unit = look { l ->
        if (!ProComboScreens.popup(l.words) ||
            !l.proSelected ||
            ProComboScreens.blocked(l.words)
        ) {
            l.done("pro-wrong-tab")
            return@look fail("Đã rời tab Đề cử Pro")
        }
        // The two opposite scrolls have now finished. When both leave the same
        // complete cards anchored at the top and aligned to the real list bottom,
        // prove a short list without requiring any background pixels to be static.
        val beforeProof = shortListProof
        val anchored = l.entries.firstOrNull()?.let { abs(it.button.y - .408) < .03 } == true
        if (
            beforeProof != null &&
            anchored &&
            l.listBottomClear &&
            ProComboScreens.rowsStationary(beforeProof, l.entries)
        ) {
            shortListVerified = true
            previousTopEntries = l.entries
            l.done(null)
            screen.say("Đã xác minh danh sách Pro ngắn bằng vị trí combo và hai hướng cuộn; bắt đầu thu thập")
            then()
            return@look
        }
        if (emptyListProbe && l.entries.isEmpty() && ProComboScreens.empty(l.words)) {
            l.done(null)
            then()
            return@look
        }
        if (beforeProof != null && beforeProof.any { old -> l.entries.any { same(old, it) && abs(old.button.y - it.button.y) > .025 } }) {
            shortListProof = null // The opposite probe moved: this was a longer list parked at its bottom.
            returnedFromProbe = true
        }
        val proof = shortListProof
        if (proof != null &&
            (
                proof.size != l.entries.size ||
                    proof.indices.any { !same(proof[it], l.entries[it]) } ||
                    if (proof.isEmpty()) !ProComboScreens.empty(l.words) else !l.listBottomClear
                )
        ) {
            l.done("pro-short-list-changed")
            return@look fail("Danh sách Pro thay đổi khi kiểm tra hai chiều; chưa xác minh danh sách ngắn")
        }
        val stationaryRows = ProComboScreens.rowsStationary(previousTopEntries, l.entries)
        val unchanged = last?.let { ProComboScreens.still(it, l.listSignature) } == true ||
            (returnedFromProbe && stationaryRows)
        // Tooltip/animation changes are not proof that a scroll moved the list.
        if (previousTopEntries.any { old -> l.entries.any { same(old, it) && it.button.y - old.button.y > .012 } }) {
            returnedFromProbe = true
        }
        previousTopEntries = l.entries
        val atTop = l.entries.firstOrNull()?.let { abs(it.button.y - .408) < .03 } == true || ProComboScreens.empty(l.words)
        stable = if (unchanged) stable + 1 else 0
        stalledTopProbes = if (atTop && stationaryRows && !returnedFromProbe) stalledTopProbes + 1 else 0
        last = l.listSignature
        l.done(null)
        if (stable >= 2 && returnedFromProbe && atTop) {
            moves = 0
            stable = 0
            last = null
            then()
        } else if (stalledTopProbes >= 3) {
            fail("Không xác minh được đầu danh sách Pro: các dòng không dịch chuyển dù đã vuốt hai hướng")
        } else if (++moves > 16) {
            fail(
                "Không xác minh được đầu danh sách Pro: dòng=${l.entries.size}, đầu=$atTop, đứng yên=$stationaryRows, đã dịch=$returnedFromProbe; chưa bấm Dùng combo"
            )
        } else {
            drag(true) { scanTop(then) }
        }
    }

    /** Never bridge a missing overlap. Re-read first, then replay from a positively verified top
     * with a smaller step and the original prefix frozen. No apply has happened at this stage.
     */
    private fun retryDiscovery(l: Look, reads: Int) {
        if (reads < 2) {
            l.done(null)
            later { discover(reads + 1) }
        } else if (discoveryStep > .018 && discovered.isNotEmpty()) {
            l.done("pro-list-replay")
            if (discovered.size > replayPrefix.size) replayPrefix = discovered.toList()
            discovered.clear()
            discoveryStep = maxOf(.018, discoveryStep / 2)
            screen.say("Đang đọc lại Pro từ đầu bằng bước cuộn ngắn hơn; chưa áp dụng combo")
            reset { discover() }
        } else {
            l.done("pro-list-gap")
            fail("Không chứng minh được phần chồng lặp khi cuộn Pro sau khi đọc lại; chưa áp dụng combo")
        }
    }

    private fun discover(reads: Int = 0): Unit = look { l ->
        if (!ProComboScreens.popup(l.words) ||
            !l.proSelected ||
            ProComboScreens.blocked(l.words)
        ) {
            l.done("pro-unknown")
            return@look fail("Mất tab Đề cử Pro")
        }
        if (l.entries.isEmpty()) {
            if (discovered.isEmpty() && replayPrefix.isEmpty() && ProComboScreens.empty(l.words)) {
                // Re-read the explicit status, so loading/error/one missing OCR frame cannot become EMPTY.
                l.done(null)
                return@look later { confirmEmpty() }
            }
            return@look retryDiscovery(l, reads)
        }
        if (ambiguous(l.entries)) {
            l.done("pro-ambiguous")
            return@look fail("Tên người đề cử trùng/không rõ; chưa thể giữ đúng thứ hạng")
        }
        val oldEntries = discovered.toList()
        val newEntries = l.entries
        if (newEntries.any { candidate -> oldEntries.count { same(it, candidate) } > 1 }) {
            l.done("pro-ambiguous-catalog")
            return@look fail("Ảnh combo khớp nhiều mục trong danh sách đã đọc; không đoán thứ hạng")
        }
        val overlap = if (discovered.isEmpty()) {
            0
        } else {
            (1..minOf(oldEntries.size, newEntries.size)).lastOrNull { n ->
                (0 until n).all { same(oldEntries[oldEntries.size - n + it], newEntries[it]) }
            } ?: -1
        }
        if (overlap <
            0
        ) {
            return@look retryDiscovery(l, reads)
        }
        for (e in l.entries.take(overlap)) {
            val old = discovered.first { same(it, e) }
            if (!ProComboScreens.matches(old.signature, e.signature)) {
                l.done("pro-changing-list")
                return@look fail("Combo Pro đổi khi đang đọc; dừng để tránh sai thứ hạng")
            }
        }
        for (e in l.entries.drop(overlap)) {
            if (oldEntries.any { same(it, e) }) {
                l.done("pro-ambiguous")
                return@look fail("Người đề cử lặp lại ở dòng khác; không thể xác định combo duy nhất")
            }
            val frozen = replayPrefix.getOrNull(discovered.size)
            if (frozen != null && !same(frozen, e)) {
                l.done("pro-replay-changed")
                return@look fail("Thứ hạng/combo Pro đổi khi đọc lại; chưa áp dụng combo")
            }
            // A replay must not slowly drift frozen signatures through successive fuzzy matches.
            discovered += frozen ?: e
        }
        val verifiedShort = shortListVerified &&
            shortListProof?.let { proof ->
                l.listBottomClear &&
                    abs(l.entries.firstOrNull()?.button?.y?.minus(.408) ?: 1.0) < .03 &&
                    ProComboScreens.rowsStationary(proof, l.entries)
            } == true
        if (shortListVerified && !verifiedShort) {
            l.done("pro-short-list-changed")
            return@look fail("Đề cử Pro thay đổi sau khi kiểm tra hai hướng cuộn; không áp dụng nhầm combo")
        }
        // A proven list boundary plus fixed, identifiable rows is stronger scroll
        // evidence than comparing pixels that can shimmer or animate indefinitely.
        val unchanged = verifiedShort ||
            last?.let { ProComboScreens.still(it, l.listSignature) } == true ||
            (l.listBottomClear && ProComboScreens.rowsStationary(previousDiscoveryEntries, l.entries))
        if (!unchanged) endProbed = false
        stable = if (unchanged) stable + 1 else 0
        last = l.listSignature
        previousDiscoveryEntries = l.entries
        if (verifiedShort) {
            stable = 2
            endProbed = true // The reset already proved both opposite scrolls did not move content.
        }
        l.done(null)
        if (stable >= 2 && l.listBottomClear && !endProbed) {
            endProbed = true
            return@look drag(false, .22) { discover() } // Fine scans may be below a device's touch slop.
        }
        if (stable >= 2 && l.listBottomClear && endProbed) {
            if (discovered.size < replayPrefix.size) {
                return@look fail("Danh sách Pro ngắn hơn phần đã xác minh; chưa áp dụng combo")
            }
            chosen.addAll(discovered.take(5))
            journal.put(
                "order",
                JSONArray().also { a ->
                    chosen.forEach { e ->
                        a.put(JSONObject().put("id", e.id).put("label", e.label).put("signature", JSONArray(e.signature.toList())))
                    }
                }
            )
                .put(
                    "catalog",
                    JSONArray().also { a ->
                        discovered.forEach { e ->
                            a.put(JSONObject().put("id", e.id).put("label", e.label).put("signature", JSONArray(e.signature.toList())))
                        }
                    }
                )
                .put("phase", "ready").put("captured", 0)
            if (save()) next()
        } else if (stable >= 2) {
            fail("Danh sách đứng yên nhưng chưa thấy khoảng trống cuối; không báo thiếu đề cử")
        } else if (++moves >
            300
        ) {
            fail("Không xác minh được điểm cuối Pro")
        } else {
            drag(false, if (l.entries.size >= 2) discoveryStep else .018) { discover() }
        }
    }

    private fun emptyPhoto() = "empty-pro-list"

    private fun photo(rank: Int) = "top-${rank + 1}"

    private fun confirmEmpty(): Unit = look { l ->
        if (!l.proSelected ||
            !ProComboScreens.empty(l.words) ||
            l.entries.isNotEmpty()
        ) {
            l.done("pro-empty-uncertain")
            return@look fail("Trạng thái Pro trống chưa ổn định")
        }
        journal.put("phase", "empty_pending").put("empty", true)
        if (!save()) {
            l.done(null)
            return@look
        }
        l.keep(emptyPhoto()) { ok ->
            if (screen.alive && !ended) {
                if (!ok) {
                    fail("Không lưu được ảnh Pro trống")
                } else {
                    journal.put("phase", "done")
                    if (save()) finish(Outcome.Done(0, true))
                }
            }
        }
    }

    private fun next() {
        restoring = current == chosen.size
        if (chosen.isEmpty()) return fail("Không có thứ hạng Pro đã xác minh")
        open({ reset { seek() } })
    }

    private fun seek(): Unit = look { l ->
        if (!ProComboScreens.popup(l.words) ||
            !l.proSelected ||
            ProComboScreens.blocked(l.words)
        ) {
            l.done("pro-unknown")
            return@look fail("Mất tab Pro trước khi áp dụng")
        }
        val expected = chosen[if (restoring) 0 else current]
        val matches = l.entries.filter { same(it, expected) }
        if (matches.size == 1 && ProComboScreens.uniqueMatch(expected, matches[0], discovered)) {
            val e = matches[0]
            l.done(null)
            journal.put("phase", if (restoring) "restore_pending" else "apply_pending").put("pendingRank", if (restoring) 0 else current)
            if (save()) tap(e.button, "apply") { verifyApplied(0) }
        } else {
            val unchanged = last?.let { ProComboScreens.still(it, l.listSignature) } == true
            stable = if (unchanged) stable + 1 else 0
            last = l.listSignature
            l.done(null)
            if (matches.isNotEmpty()) {
                fail("Có nhiều combo không phân biệt được; không áp dụng nhầm")
            } else if (stable >= 2 || ++moves > 20) {
                fail("Không tìm lại được combo Pro đã ghi thứ hạng")
            } else {
                drag(false) { seek() }
            }
        }
    }

    private fun recoverApply() {
        restoring = journal.optString("phase") == "restore_pending"
        val rank = journal.optInt("pendingRank", -1)
        if (rank !in chosen.indices || rank != if (restoring) 0 else current) return fail("Tiến độ áp dụng Pro không hợp lệ")
        // Only observation is allowed. If the popup is still open, even a failed callback cannot prove no apply occurred.
        verifyApplied(0)
    }

    private fun verifyApplied(tries: Int, verified: Boolean = false): Unit = look { l ->
        val expected = chosen[if (restoring) 0 else current]
        if (validHero(l) &&
            l.preparationSignature?.let { ProComboScreens.uniqueMatch(expected, expected.copy(signature = it), discovered) } == true
        ) {
            if (!verified) {
                l.done(null)
                return@look later { verifyApplied(tries + 1, true) }
            }
            if (restoring) {
                l.done(null)
                journal.put("phase", "done").put("restoredTop1", true).put("restorationNeeded", false)
                if (save()) finish(Outcome.Done(current, false))
            } else {
                journal.put("phase", "capture_pending")
                if (!save()) {
                    l.done(null)
                    return@look
                }
                l.keep(photo(current)) { ok ->
                    if (screen.alive && !ended) {
                        if (!ok) {
                            fail("Không lưu được ảnh combo Pro đã áp dụng")
                        } else {
                            current++
                            journal.put("captured", current).put("phase", "ready").put("restorationNeeded", true)
                            if (save()) next()
                        }
                    }
                }
            }
        } else {
            l.done(if (tries >= 5) "pro-apply-unverified" else null)
            if (tries >= 5) {
                fail("Chưa xác minh combo đã áp dụng cho đúng tướng. Không chụp ảnh cũ hoặc bấm Dùng combo lần nữa")
            } else {
                later { verifyApplied(tries + 1) }
            }
        }
    }
}
