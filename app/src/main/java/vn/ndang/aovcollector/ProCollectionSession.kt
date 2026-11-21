package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import org.json.JSONObject

/**
 * Where a Đề cử Pro run goes from the screen it is on, its progress file read: the hero cut part way ([active], as
 * pro-progress.json keeps it) is gone on with only on its own Chuẩn bị, its name the one read when it was chosen.
 * Anywhere else it is chosen again by its place in Chọn tướng, its count of combos kept. The user's run was cut by
 * the power button; the game may come back on its lobby, or Chuẩn bị with another hero, whose combos would have gone
 * into the cut hero's folder.
 */
object ProResume {
    class Screen(
        val preparation: Boolean,
        val picker: Boolean,
        val popup: Boolean,
        val lobby: Boolean,
        val equipment: Boolean,
        val hero: String?
    )

    sealed class Move {
        /** On with the hero being collected: its Chuẩn bị is in front. */
        object Collect : Move()

        /** Choose the hero at [index] in Chọn tướng; [again]: the hero cut part way, its photos and count kept. */
        data class Pick(val index: Int, val again: Boolean) : Move()

        object ClosePopup : Move()

        object OpenEquipment : Move()

        object Wait : Move()
    }

    fun move(active: JSONObject?, next: Int, s: Screen): Move = when {
        active != null && s.preparation && same(active.optString("hero"), s.hero) -> Move.Collect
        s.preparation || s.picker -> if (active != null) Move.Pick(active.optInt("index", next), true) else Move.Pick(next, false)
        s.popup -> Move.ClosePopup
        s.lobby && s.equipment -> Move.OpenEquipment
        else -> Move.Wait
    }

    /** The name read when the hero was chosen, read again: none read either time is not the same hero. */
    fun same(saved: String, shown: String?): Boolean {
        // A name with no letters is no name: its slug is "chua-ro-ten", the same for every hero.
        if (saved.none { it.isLetter() } || shown == null || shown.none { it.isLetter() }) return false
        return HeroFinder.same(HeroNames.slug(saved), HeroNames.slug(shown))
    }
}

/**
 * Heroes to go back to once the list of heroes is done: those left with fewer photos than asked because a
 * recommendation could not be reached ("lost", as [ProSimpleReader] marks them) and, when a number of combos is asked,
 * any with fewer than it, as progress files written before the mark have them: the user's hero 89, two photos of
 * three. Each once ("second"); a hero whose list is empty is not one.
 */
object ProSecondPass {
    fun wanted(hero: JSONObject, combos: Int): Boolean = hero.optBoolean("done") &&
        !hero.optBoolean("second") &&
        !hero.optBoolean("empty") &&
        (hero.has("lost") || (combos < BuiltInMacros.ALL && hero.optInt("captured", 0) < combos))

    /** The first such hero by its place, as pro-089; null when none is left. */
    fun next(heroes: JSONObject, combos: Int): String? = heroes.keys().asSequence().toList().sorted()
        .firstOrNull { key -> heroes.optJSONObject(key)?.let { wanted(it, combos) } == true }

    /** Opened again: its photos and their count kept, the list's length and the first combo's return forgotten. */
    fun begin(hero: JSONObject) {
        hero.put("second", true).put("done", false).put("total", -1)
        hero.remove("restored")
        hero.remove("lost")
    }
}

/** Per-run orchestration and adapters; service supplies guarded I/O, readers own state transitions. */
class ProCollectionSession(private val host: Host, private val alive: () -> Boolean, private val finish: (String?) -> Unit) {
    interface Host {
        val dryRun: Boolean
        val root: File

        fun read(then: (Bitmap, List<GameScreens.Word>) -> Unit)

        /** A screenshot without OCR, for what pixels tell: the list's buttons, Chuẩn bị settling. */
        fun glance(then: (Bitmap) -> Unit) = read { b, _ -> then(b) }

        fun post(ms: Long, then: () -> Unit)

        fun touch(
            action: CalibrationAction,
            target: GameScreens.Word?,
            verify: ((List<CalibrationPoint>, (Boolean) -> Unit) -> Unit)?,
            then: (Boolean) -> Unit
        )

        fun store(bitmap: Bitmap, name: String, then: (Boolean) -> Unit)

        fun report(text: String)

        /** A line in the run's log. */
        fun note(text: String) {}

        fun error(error: Exception)
    }

    fun start(step: JSONObject) {
        val dryRun = host.dryRun
        val root = host.root
        val journalFile = ProProgressFile(File(root, "pro-progress.json"))
        val progress = try {
            if (!dryRun && journalFile.exists()) {
                journalFile.read()
            } else {
                JSONObject().put("schema", 1).put("next", 0).put("heroes", JSONObject())
            }
        } catch (_: Exception) {
            finish("Không đọc được tiến độ Pro; không tự ghi đè để tránh áp dụng/chụp trùng")
            return
        }
        val heroes = progress.optJSONObject("heroes") ?: run {
            finish("Tiến độ Pro không hợp lệ")
            return
        }

        fun save(): Boolean {
            if (dryRun) return alive()
            if (!alive()) return false
            return try {
                journalFile.write(progress)
                true
            } catch (e: Exception) {
                host.error(e)
                false
            }
        }

        fun report(text: String) = host.report(text)

        fun later(next: () -> Unit) = host.post(1100, next)

        fun look(then: (Bitmap, List<GameScreens.Word>) -> Unit) = host.read(then)

        fun touch(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) {
            if (alive()) host.touch(action, target, null, then)
        }

        fun evidence(b: Bitmap, name: String, then: (Boolean) -> Unit = {}) {
            host.store(b, name, then)
        }
        lateinit var advance: () -> Unit

        /** Photos go to [key]/[key]-1.png…, by the hero's place in the picker, never by a name read on screen. */
        fun collect(key: String, state: JSONObject) {
            val slug = HeroNames.slug(key).take(60)

            fun token(name: String) = "pro__${slug}__$name"
            val screen = object : ProSimpleReader.Screen {
                override val alive get() = alive()

                override fun later(ms: Long, then: () -> Unit) {
                    host.post(ms, then)
                }

                override fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) =
                    touch(action, target, then)

                override fun say(text: String) = report(text)

                override fun note(text: String) = host.note(text)

                override fun persist(): Boolean = save()

                fun captureExists(name: String): Boolean {
                    if (dryRun) return true
                    val f = File(root, "$slug/$slug-$name.png")
                    if (!f.isFile || f.length() == 0L) return false
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.absolutePath, opts)
                    return opts.outWidth > 0 && opts.outHeight > 0
                }

                override fun look(then: (ProSimpleReader.Look?) -> Unit) = look { b, words -> then(seen(b, words)) }

                override fun glance(then: (ProSimpleReader.Look?) -> Unit) = host.glance { b -> then(seen(b, emptyList())) }

                fun seen(b: Bitmap, words: List<GameScreens.Word>): ProSimpleReader.Look {
                    val pixels = { y: Int, row: IntArray -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                    return object : ProSimpleReader.Look {
                        override val words = words
                        override val proSelected = words.isNotEmpty() && ProComboScreens.proSelected(b.width, b.height, words, pixels)
                        override val buttons = ProPixels.buttons(b.width, b.height, pixels)
                        override val panel = ProPixels.panel(b.width, b.height, pixels)
                        private var consumed = false

                        override fun done(keep: String?) {
                            if (consumed) return
                            consumed = true
                            if (keep == null) b.recycle() else evidence(b, token("diagnostic-$keep"))
                        }

                        override fun keep(name: String, then: (Boolean) -> Unit) {
                            if (consumed) {
                                then(false)
                                return
                            }
                            consumed = true
                            if (captureExists(name)) {
                                b.recycle()
                                then(true)
                            } else {
                                evidence(b, token(name), then)
                            }
                        }
                    }
                }
            }
            ProSimpleReader(screen, state, BuiltInMacros.proCombos(step)) { outcome ->
                if (!alive()) return@ProSimpleReader
                when (outcome) {
                    is ProSimpleReader.Outcome.Failed -> finish(outcome.reason)
                    is ProSimpleReader.Outcome.Done -> {
                        state.put("result", if (outcome.empty) "empty-pro-list" else "collected").put("photos", outcome.count)
                        // A hero gone back to ([ProSecondPass]) is not the next of the list: its place was counted.
                        if (!state.optBoolean("second")) progress.put("next", progress.getInt("next") + 1)
                        progress.remove("active")
                        if (!save()) finish("Không ghi được tiến độ Pro sau khi thu thập") else later(advance)
                    }
                }
            }.start()
        }

        // What one pick proved about Chọn tướng, for the next: its top, and where the last hero was.
        val pickerMemory = ProHeroPicker.Memory()

        /** [again]: the hero cut part way, by its key and state; chosen once more, it keeps both. */
        fun pick(index: Int, again: Pair<String, JSONObject>? = null) {
            val screen = object : ProHeroPicker.Screen {
                override val alive get() = alive()

                override fun note(text: String) = host.note(text)

                override fun later(ms: Long, then: () -> Unit) {
                    host.post(ms, then)
                }

                override fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) = touch(action, null, then)

                override fun look(then: (ProHeroPicker.Look?) -> Unit) = look { b, words ->
                    val pixels = { y: Int, row: IntArray -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                    then(object : ProHeroPicker.Look {
                        override val words = words
                        override val preparation = ProComboScreens.preparation(words)
                        override val ownedFilter = BoardScreens.ownedFilter(b.width, b.height, words, pixels)
                        override val allSelected = ProHeroPicker.allSelected(b.width, b.height, words, pixels)
                        override val rows = ProHeroPicker.rows(b.width, b.height, pixels)
                        override val profile = ProHeroPicker.profile(b.width, b.height, pixels)
                        override val height = b.height
                        override val title = ProHeroPicker.title(b.width, b.height, pixels)

                        override fun header(words: List<GameScreens.Word>) = if (consumed) {
                            false to null
                        } else {
                            ProHeroPicker.allSelected(b.width, b.height, words, pixels) to
                                BoardScreens.ownedFilter(b.width, b.height, words, pixels)
                        }

                        override fun card(col: Int, top: Double, bottom: Double) =
                            BoardScreens.card(b.width, b.height, col, top, bottom, pixels)

                        private var consumed = false

                        override fun done(keep: String?) {
                            if (consumed) return
                            consumed = true
                            if (keep == null) b.recycle() else evidence(b, "pro-diagnostic-picker-$index-$keep")
                        }
                    })
                }
            }
            ProHeroPicker(screen, index, pickerMemory) { outcome ->
                if (!alive()) return@ProHeroPicker
                when (outcome) {
                    // The list's end where a hero already collected was looked for: not the end of the run.
                    is ProHeroPicker.Outcome.End -> if (again != null) {
                        finish("Không tìm lại được tướng ${index + 1} (${again.first}) trong Chọn tướng; chưa chụp tiếp tướng này")
                    } else {
                        progress.put("complete", true)
                        if (save()) later(advance) else finish("Không ghi được kết thúc danh sách Pro")
                    }
                    is ProHeroPicker.Outcome.Failed -> finish(outcome.reason)
                    // Named by place, as pro-001…: a name read on screen only goes to the log.
                    is ProHeroPicker.Outcome.Selected -> look { b, words ->
                        val name = if (ProComboScreens.preparation(words)) ProComboScreens.heroName(words) else null
                        b.recycle()
                        val key = again?.first ?: "pro-%03d".format(index + 1)
                        val state = if (again != null) {
                            again.second.also { if (it.optString("hero").isBlank() && name != null) it.put("hero", name) }
                        } else {
                            JSONObject().put("hero", name ?: "").put("index", index)
                        }
                        host.note(
                            if (again != null) {
                                "Pro: chọn lại tướng ${index + 1} ($key" + (name?.let { ", $it" } ?: "") +
                                    "), làm tiếp từ đề cử ${state.optInt("captured", 0) + 1}"
                            } else {
                                "Pro: tướng ${index + 1} → $key" + (name?.let { " ($it)" } ?: "")
                            }
                        )
                        heroes.put(key, state)
                        progress.put("active", key)
                        if (save()) collect(key, state) else finish("Không lưu được tướng Pro đang xử lý")
                    }
                }
            }.start()
        }
        var navigation = 0
        advance = {
            if (alive()) {
                val active = progress.optString("active")
                val state = heroes.optJSONObject(active)
                val next = progress.optInt("next", -1)
                when {
                    next !in 0..step.getInt("heroes") -> finish("Vị trí tiếp tục Pro không hợp lệ")
                    active.isNotEmpty() && state == null -> finish("Thiếu tiến độ tướng Pro đang xử lý")
                    // The heroes all done: back once to each left with fewer photos than asked, then the end.
                    state == null && (next >= step.getInt("heroes") || progress.optBoolean("complete")) -> {
                        val combos = BuiltInMacros.proCombos(step)
                        val short = ProSecondPass.next(heroes, combos)
                        val hero = short?.let { heroes.optJSONObject(it) }
                        if (short == null || hero == null) {
                            finish(null)
                        } else {
                            host.note(
                                "Pro: hết danh sách tướng; quay lại tướng ${hero.optInt("index") + 1} ($short" +
                                    hero.optString("hero").takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty() +
                                    ") mới có ${hero.optInt("captured", 0)} ảnh" +
                                    (if (combos < BuiltInMacros.ALL) "/$combos" else "")
                            )
                            ProSecondPass.begin(hero)
                            progress.put("active", short)
                            if (save()) advance() else finish("Không lưu được tướng Pro cần chụp tiếp")
                        }
                    }
                    else -> look { b, words ->
                        val prep = ProComboScreens.preparation(words)
                        val picker = BoardScreens.page(words, false) == BoardScreens.Page.PICKER
                        val popup = ProComboScreens.popup(words)
                        val pixels = { y: Int, row: IntArray -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                        val lobby = GameScreens.read(b.width, b.height, pixels).kind == GameScreens.Kind.LOBBY
                        val equipment = words.firstOrNull { it.plain == "trangbi" && it.y > .70 && it.right - it.left < .2 }
                        val shown = if (prep) ProComboScreens.heroName(words) else null
                        b.recycle()
                        val move = ProResume.move(state, next, ProResume.Screen(prep, picker, popup, lobby, equipment != null, shown))
                        when {
                            move is ProResume.Move.Collect && state != null -> {
                                navigation = 0
                                host.note("Pro: làm tiếp ${state.optString("hero")} ($active) từ đề cử ${state.optInt("captured", 0) + 1}")
                                collect(active, state)
                            }
                            move is ProResume.Move.Pick -> {
                                navigation = 0
                                if (move.again && state != null) {
                                    host.note(
                                        "Pro: Chuẩn bị không còn là ${state.optString("hero").ifBlank { active }}; chọn lại theo vị trí " +
                                            "${move.index + 1} để làm tiếp"
                                    )
                                    pick(move.index, active to state)
                                } else {
                                    pick(move.index)
                                }
                            }
                            ++navigation > 4 -> finish("Không xác minh được sảnh/Chuẩn bị; mở Trang bị bằng tay rồi Tiếp tục")
                            move is ProResume.Move.ClosePopup -> touch(
                                CalibrationAction("pro.v1.popup.close", "Pro: đóng đề xuất", listOf(CalibrationPoint(.824, .111))),
                                null
                            ) {
                                if (it) later(advance) else finish("Không đóng được danh sách Pro")
                            }
                            move is ProResume.Move.OpenEquipment && equipment != null -> touch(
                                CalibrationAction(
                                    "pro.v1.lobby.equipment",
                                    "Sảnh: Trang bị",
                                    listOf(CalibrationPoint(equipment.x, equipment.y)),
                                    true
                                ),
                                equipment
                            ) {
                                if (it) later(advance) else finish("Không mở được Trang bị")
                            }
                            else -> later(advance)
                        }
                    }
                }
            }
        }
        advance()
    }
}
