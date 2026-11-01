package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import org.json.JSONObject

/** Per-run orchestration and adapters; service supplies guarded I/O, readers own state transitions. */
class ProCollectionSession(private val host: Host, private val alive: () -> Boolean, private val finish: (String?) -> Unit) {
    interface Host {
        val dryRun: Boolean
        val root: File

        fun read(then: (Bitmap, List<GameScreens.Word>) -> Unit)

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

                override fun look(then: (ProSimpleReader.Look?) -> Unit) = look { b, words ->
                    val pixels = { y: Int, row: IntArray -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                    then(object : ProSimpleReader.Look {
                        override val words = words
                        override val proSelected = ProComboScreens.proSelected(b.width, b.height, words, pixels)
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
                    })
                }
            }
            ProSimpleReader(screen, state) { outcome ->
                if (!alive()) return@ProSimpleReader
                when (outcome) {
                    is ProSimpleReader.Outcome.Failed -> finish(outcome.reason)
                    is ProSimpleReader.Outcome.Done -> {
                        state.put("result", if (outcome.empty) "empty-pro-list" else "collected").put("photos", outcome.count)
                        progress.put("next", progress.getInt("next") + 1).remove("active")
                        if (!save()) finish("Không ghi được tiến độ Pro sau khi thu thập") else later(advance)
                    }
                }
            }.start()
        }

        fun pick(index: Int) {
            val screen = object : ProHeroPicker.Screen {
                override val alive get() = alive()

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
            ProHeroPicker(screen, index) { outcome ->
                if (!alive()) return@ProHeroPicker
                when (outcome) {
                    is ProHeroPicker.Outcome.End -> {
                        progress.put("complete", true)
                        if (save()) finish(null) else finish("Không ghi được kết thúc danh sách Pro")
                    }
                    is ProHeroPicker.Outcome.Failed -> finish(outcome.reason)
                    // Named by place, as pro-001…: a name read on screen only goes to the log.
                    is ProHeroPicker.Outcome.Selected -> look { b, words ->
                        val name = if (ProComboScreens.preparation(words)) ProComboScreens.heroName(words) else null
                        b.recycle()
                        val key = "pro-%03d".format(index + 1)
                        host.note("Pro: tướng ${index + 1} → $key" + (name?.let { " ($it)" } ?: ""))
                        val state = JSONObject().put("hero", name ?: "").put("index", index)
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
                    state != null -> collect(active, state)
                    next >= step.getInt("heroes") || progress.optBoolean("complete") -> finish(null)
                    else -> look { b, words ->
                        val prep = ProComboScreens.preparation(words)
                        val picker = BoardScreens.page(words, false) == BoardScreens.Page.PICKER
                        val popup = ProComboScreens.popup(words)
                        val pixels = { y: Int, row: IntArray -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                        val lobby = GameScreens.read(b.width, b.height, pixels).kind == GameScreens.Kind.LOBBY
                        val equipment = words.firstOrNull { it.plain == "trangbi" && it.y > .70 && it.right - it.left < .2 }
                        b.recycle()
                        when {
                            prep || picker -> {
                                navigation = 0
                                pick(next)
                            }
                            ++navigation > 4 -> finish("Không xác minh được sảnh/Chuẩn bị; mở Trang bị bằng tay rồi Tiếp tục")
                            popup -> touch(
                                CalibrationAction("pro.v1.popup.close", "Pro: đóng đề xuất", listOf(CalibrationPoint(.824, .111))),
                                null
                            ) {
                                if (it) later(advance) else finish("Không đóng được danh sách Pro")
                            }
                            lobby && equipment != null -> touch(
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
