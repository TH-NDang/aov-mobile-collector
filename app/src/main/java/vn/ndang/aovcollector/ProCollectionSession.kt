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

        var observedProEntries: List<ProComboScreens.Entry> = emptyList()
        var frozenProCatalog: List<ProComboScreens.Entry> = emptyList()

        fun touch(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) {
            if (!alive()) return
            val observed = if (action.id == "pro.v1.apply") {
                observedProEntries.singleOrNull {
                    target != null && kotlin.math.abs(it.button.x - target.x) < .01 && kotlin.math.abs(it.button.y - target.y) < .01
                }
            } else {
                null
            }
            val expected = observed?.let { current ->
                frozenProCatalog.filter {
                    ProComboScreens.sameCreator(it.id, current.id) && ProComboScreens.matches(it.signature, current.signature)
                }.singleOrNull()
            }
            if (action.id == "pro.v1.apply" && expected == null) {
                finish("Pro [pro.v1.apply] chưa gửi thao tác: đề cử không khớp duy nhất với danh mục đã xác minh")
                then(false)
                return
            }
            val verify: ((List<CalibrationPoint>, (Boolean) -> Unit) -> Unit)? = expected?.let { original ->
                { points, answer ->
                    look { bitmap, words ->
                        val pixels = { y: Int, row: IntArray -> bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1) }
                        val entries = ProComboScreens.entries(bitmap.width, bitmap.height, words, pixels)
                        val valid = ProApplyEvidence.valid(
                            action,
                            points,
                            original,
                            entries,
                            frozenProCatalog,
                            ProComboScreens.proSelected(bitmap.width, bitmap.height, words, pixels),
                            ProComboScreens.blocked(words)
                        )
                        bitmap.recycle()
                        answer(valid)
                    }
                }
            }
            host.touch(action, target, verify, then)
        }

        fun evidence(b: Bitmap, name: String, then: (Boolean) -> Unit = {}) {
            host.store(b, name, then)
        }
        lateinit var advance: () -> Unit

        fun collect(hero: String, state: JSONObject) {
            val slug = HeroNames.slug(hero).take(60)

            fun token(name: String) = "pro__${slug}__$name"
            val screen = object : ProComboReader.Screen {
                override val alive get() = alive()

                override fun later(ms: Long, then: () -> Unit) {
                    host.post(ms, then)
                }

                override fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) {
                    frozenProCatalog = ProComboScreens.catalog(state)
                    touch(action, target, then)
                }

                override fun say(text: String) = report(text)

                override fun persist(journal: JSONObject): Boolean = save()

                override fun captureExists(name: String): Boolean {
                    if (dryRun) return true
                    val f = File(root, "$slug/$slug-$name.png")
                    if (!f.isFile || f.length() == 0L) return false
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.absolutePath, opts)
                    return opts.outWidth > 0 && opts.outHeight > 0
                }

                override fun look(then: (ProComboReader.Look?) -> Unit) = look { b, words ->
                    val pixels = { y: Int, row: IntArray -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                    then(object : ProComboReader.Look {
                        override val words = words
                        override val proSelected = ProComboScreens.proSelected(b.width, b.height, words, pixels)
                        override val entries = ProComboScreens.entries(b.width, b.height, words, pixels).also { observedProEntries = it }
                        override val listBottomClear = ProComboScreens.listBottomClear(b.width, b.height, entries, pixels)
                        override val preparationSignature = if (ProComboScreens.preparation(
                                words
                            )
                        ) {
                            ProComboScreens.preparationSignature(b.width, b.height, words, pixels)
                        } else {
                            null
                        }
                        override val listSignature = ProComboScreens.listSignature(b.width, b.height, pixels)
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
            ProComboReader(screen, hero, state) { outcome ->
                if (!alive()) return@ProComboReader
                when (outcome) {
                    is ProComboReader.Outcome.Failed -> finish(outcome.reason)
                    is ProComboReader.Outcome.Done -> {
                        state.put("result", if (outcome.empty) "empty-pro-list" else "collected")
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
                    is ProHeroPicker.Outcome.Selected -> look { b, words ->
                        val name = if (ProComboScreens.preparation(words)) ProComboScreens.heroName(words) else null
                        b.recycle()
                        if (name == null) {
                            finish("Không đọc được tên tướng sau khi chọn; không dùng tên số để tránh trùng")
                        } else {
                            val slug = HeroNames.slug(name).take(60)
                            if (heroes.has(slug)) {
                                finish("Tên tướng đã thu thập xuất hiện lại ($name); dừng để tránh ảnh trùng hoặc chọn sai")
                            } else {
                                val state = JSONObject().put("hero", name).put("index", index)
                                heroes.put(slug, state)
                                progress.put("active", slug)
                                if (save()) collect(name, state) else finish("Không lưu được tướng Pro đang xử lý")
                            }
                        }
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
                    state != null -> collect(state.getString("hero"), state)
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
