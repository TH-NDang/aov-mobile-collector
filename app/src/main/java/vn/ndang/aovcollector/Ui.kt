package vn.ndang.aovcollector

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.*
import java.io.File

object Ui {
    /** The colours of one look of the app: text, accent, surfaces and the colours of states. */
    class Palette(
        val ink: Int,
        val muted: Int,
        val accent: Int,
        val accentFill: Int,
        val onAccent: Int,
        val pale: Int,
        val canvas: Int,
        val surface: Int,
        val border: Int,
        val danger: Int,
        val dangerPale: Int,
        val success: Int,
        val warning: Int,
        val media: Int
    )

    val LIGHT =
        Palette(
            ink = Color.rgb(
                29,
                35,
                56
            ),
            muted = Color.rgb(
                103,
                113,
                134
            ),
            accent = Color.rgb(92, 73, 220), accentFill = Color.rgb(92, 73, 220), onAccent = Color.WHITE,
            pale = Color.rgb(
                239,
                236,
                255
            ),
            canvas = Color.rgb(
                246,
                247,
                251
            ),
            surface = Color.WHITE, border = Color.rgb(228, 232, 241), danger = Color.rgb(185, 48, 70),
            dangerPale = Color.rgb(
                255,
                240,
                243
            ),
            success = Color.rgb(31, 132, 112), warning = Color.rgb(160, 96, 0), media = Color.rgb(24, 26, 40)
        )

    // Dark: deep blue-grey surfaces, light text, the accent lifted so it reads on them.
    val DARK =
        Palette(
            ink = Color.rgb(
                233,
                236,
                245
            ),
            muted = Color.rgb(
                152,
                160,
                182
            ),
            accent = Color.rgb(173, 160, 255), accentFill = Color.rgb(108, 89, 232), onAccent = Color.WHITE,
            pale = Color.rgb(
                40,
                37,
                70
            ),
            canvas = Color.rgb(
                15,
                17,
                26
            ),
            surface = Color.rgb(27, 30, 44), border = Color.rgb(46, 51, 71), danger = Color.rgb(255, 138, 158),
            dangerPale = Color.rgb(
                62,
                30,
                42
            ),
            success = Color.rgb(94, 207, 174), warning = Color.rgb(242, 184, 84), media = Color.rgb(6, 7, 12)
        )

    /** The look of the screens being built; set by [use]. */
    @Volatile var colors = LIGHT
    val ink get() = colors.ink
    val muted get() = colors.muted
    val accent get() = colors.accent
    val pale get() = colors.pale
    val canvas get() = colors.canvas
    val surface get() = colors.surface
    val border get() = colors.border
    val danger get() = colors.danger
    val success get() = colors.success
    val warning get() = colors.warning
    val media get() = colors.media

    /** "Giao diện": follow the phone, or always light or dark. */
    enum class Theme(val key: String, val label: String) { SYSTEM("system", "Theo hệ thống"), LIGHT("light", "Sáng"), DARK("dark", "Tối") }

    fun theme(c: Context) =
        Theme.values().firstOrNull { it.key == c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("theme", null) } ?: Theme.SYSTEM

    fun setTheme(c: Context, t: Theme) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("theme", t.key).apply()
    }

    fun dark(c: Context) = when (theme(c)) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Takes the look chosen for [c] for the views built next; true when dark. */
    fun use(c: Context): Boolean {
        val d = dark(c)
        colors = if (d) DARK else LIGHT
        return d
    }

    /** The window theme of the look chosen: dialogs and controls take its accent. */
    fun windowTheme(c: Context) = if (dark(c)) R.style.Theme_Collector_Dark else R.style.Theme_Collector_Light

    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()

    fun shape(color: Int, radius: Int = 18, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        setStroke(1, stroke)
    }

    fun stack(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun card(c: Context) = stack(c).apply {
        setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16))
        background = shape(surface, dp(c, 20), border)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 14) }
    }

    fun text(c: Context, value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(c).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(c, 3).toFloat(), 1f)
        setPadding(0, dp(c, 4), 0, dp(c, 5))
    }

    /** A small heading over a group of a form's fields. */
    fun section(c: Context, value: String) = text(c, value.uppercase(), 11f, accent, true).apply {
        letterSpacing = .06f
        setPadding(0, dp(c, 14), 0, dp(c, 2))
    }

    /** A small rounded label, such as a run's state, in [color] on a faint wash of it. */
    fun chip(c: Context, value: String, color: Int) = TextView(c).apply {
        text = value
        textSize = 11f
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = shape(Color.argb(if (colors === DARK) 46 else 28, Color.red(color), Color.green(color), Color.blue(color)), dp(c, 9))
        setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 3))
        includeFontPadding = false
    }

    fun button(c: Context, value: String, primary: Boolean = false, dangerous: Boolean = false, action: () -> Unit) = Button(c).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(
            if (primary) {
                colors.onAccent
            } else if (dangerous) {
                danger
            } else {
                accent
            }
        )
        background = shape(
            if (primary) {
                colors.accentFill
            } else if (dangerous) {
                colors.dangerPale
            } else {
                pale
            },
            dp(c, 13)
        )
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(c, 12), 0, dp(c, 12), 0)
        layoutParams = LinearLayout.LayoutParams(-1, dp(c, 48)).apply { topMargin = dp(c, 10) }
        setOnClickListener { action() }
    }

    fun field(c: Context, hintText: String, value: String = "", numeric: Boolean = false) = EditText(c).apply {
        hint = hintText
        setText(value)
        textSize = 16f
        setTextColor(ink)
        setHintTextColor(muted)
        setSingleLine(true)
        setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10))
        background = shape(canvas, dp(c, 12), border)
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        layoutParams = LinearLayout.LayoutParams(-1, dp(c, 52)).apply {
            topMargin = dp(c, 8)
            bottomMargin = dp(c, 8)
        }
    }
}

object MacroStore {
    fun current(c: Context) = File(c.filesDir, "macro.json")

    fun archive(c: Context, raw: String) {
        val m = Macro(raw)
        val folder = File(c.filesDir, "macros").apply { mkdirs() }
        File(folder, "${m.hash}.json").writeText(raw)
    }

    fun saved(c: Context): List<File> = File(c.filesDir, "macros").listFiles()?.filter {
        it.extension == "json"
    }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun title(m: Macro): String = when (m.json.optString("collectionMode")) {
        "single", "all", "pick" -> "Lấy thông tin tướng"
        "ranking" -> "Thống kê tướng"
        "board" -> "Chiến khu"
        "chess" -> "Cờ Liên Quân · Bách khoa"
        "event-invite" -> "Sự kiện · Nhập mã mời nhiều tài khoản"
        else -> m.name // Imported macro: show its real name, not the generic "Macro đã nhập".
    }

    fun description(m: Macro): String = when (m.json.optString("collectionMode")) {
        "board" ->
            "BXH người chơi · " +
                (if (m.json.optInt("heroCount", 1) >= BuiltInMacros.ALL) "tất cả tướng" else "${m.json.optInt("heroCount",1)} tướng") +
                " · " +
                BuiltInMacros.boardWants(m.json).joinToString(", ") { w ->
                    w.tab.label +
                        when {
                            w.tab == BoardScreens.Tab.TOP -> ""
                            w.top >= BoardScreens.ALL -> " (hết)"
                            else -> " top ${w.top}"
                        }
                } +
                (if (m.json.optBoolean("openGame")) " · tự vào game" else "")
        "ranking" -> "Bảng xếp hạng tướng · ${BuiltInMacros.rankCombos(m.json).size}/${RankScreens.COMBOS.size} bảng" +
            (if (m.json.optBoolean("openGame")) " · tự vào game" else "")
        "event-invite" -> "${m.json.optInt("accountCount",0)} tài khoản riêng · Thập kỷ chung sức · đếm quà"
        "chess" -> "${m.json.optJSONArray("chessTopics")?.length() ?: 0} tab đã chọn · luôn chụp đến cuối" +
            (m.json.optJSONArray("chessNativeGroups")?.length()?.let { if (it > 0) " · tải $it nhóm ảnh gốc" else "" } ?: "") +
            (if (m.json.optBoolean("openGame")) " · tự vào game" else "")
        "single", "all", "pick" -> base(m) + (if (m.json.optBoolean("fast")) " · nhanh" else "") +
            (if (m.json.optBoolean("detailOnly")) " · chỉ Chi tiết" else "") +
            (if (m.json.optBoolean("directSkills")) " · chuyển thẳng chiêu" else "") +
            (if (m.json.optBoolean("openGame")) " · tự vào game" else "")
        else -> m.name
    }

    private fun base(m: Macro): String = when (m.json.optString("collectionMode")) {
        "single" -> m.json.optString("heroName", "").ifBlank { "Tướng đang mở" } + " · ${m.photoTotal}${if (m.photosVary) "+" else ""} ảnh"
        "pick" ->
            "Theo tên: " +
                BuiltInMacros.heroNames(m.json).let { n ->
                    n.take(3).joinToString(", ") + (if (n.size > 3) " và ${n.size - 3} tướng khác" else "")
                } +
                " · tìm trong danh sách"
        "all" -> if (m.json.optInt("heroCount", 1) >=
            BuiltInMacros.ALL
        ) {
            "Tất cả tướng · dừng ở cuối danh sách"
        } else {
            "${m.json.optInt("heroCount",1)} ô tướng · cuộn có đo, dừng ở cuối danh sách"
        }
        else -> m.name
    }

    fun instruction(m: Macro): String = if (m.json.optString("collectionMode") == "event-invite") {
        "Từ sảnh Liên Quân, tự đăng xuất/đăng nhập từng tài khoản sự kiện; vào Thập kỷ chung sức, làm sạch và nhập mã, ghi số quà thực nhận."
    } else if (m.json.optString("collectionMode") == "chess") {
        (
            if (m.json.optBoolean("openGame")) {
                "Bấm Chạy: app tự vào sảnh rồi Chế độ giải trí → Cờ Liên Quân → Trang chủ → Bách khoa."
            } else {
                "Mở sảnh Cờ Liên Quân hoặc Bách khoa rồi bấm Chạy."
            }
            )
    } else if (m.json.optString("collectionMode") ==
        "board"
    ) {
        (
            if (m.json.optBoolean(
                    "openGame"
                )
            ) {
                "Bấm Chạy: app tự mở Liên Quân, vào sảnh rồi Đấu hạng → Chiến khu."
            } else {
                "Mở sảnh game (hoặc Chiến khu) rồi bấm Chạy."
            }
            )
    } else if (m.json.optString("collectionMode") ==
        "ranking"
    ) {
        (
            if (m.json.optBoolean(
                    "openGame"
                )
            ) {
                "Bấm Chạy: app tự mở Liên Quân, vào sảnh rồi tới Bảng Phân Bậc Tướng."
            } else {
                "Mở sảnh game hoặc Bảng Phân Bậc Tướng rồi bấm Chạy."
            }
            )
    } else if (m.json.optBoolean("openGame")) {
        "Bấm Chạy: app tự mở Liên Quân, đóng popup sự kiện và vào danh sách Tất cả tướng."
    } else if (m.json.optString("collectionMode") == "all") {
        "Mở danh sách Tất cả tướng; app tự đưa về đầu danh sách."
    } else if (m.json.optString("collectionMode") ==
        "pick"
    ) {
        "Mở danh sách Tất cả tướng; app tự tìm từng tướng theo tên."
    } else if (m.json.optString("collectionMode") ==
        "single"
    ) {
        "Mở trang chi tiết tướng, đóng bảng mô tả chiêu."
    } else {
        "Mở đúng màn hình bắt đầu của macro."
    }
}
