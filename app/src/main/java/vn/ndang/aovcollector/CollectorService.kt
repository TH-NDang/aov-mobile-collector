package vn.ndang.aovcollector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject
import vn.ndang.aovcollector.ChessWikiReader.NativeResult

class CollectorService : AccessibilityService() {
    companion object {
        var current: CollectorService? = null

        @Volatile var libraryBusy = false
        const val TAP_MS = 100L

        /** Time for the hidden panel's window change to reach input dispatch before touching the game. */
        const val SHIELD_MS = 150L
        const val GESTURE_RETRIES = 2
        const val GAME = "com.garena.game.kgvn"

        /** Runs that take heroes one after another from the list: its first cards, or heroes by name. */
        val LISTED = setOf("all", "pick")

        /** Android accepts one accessibility screenshot per second; a little margin avoids a refused one. */
        const val SHOT_INTERVAL_MS = 1050L

        /** Screenshots saved in the background at once before the macro waits for the disk. */
        const val MAX_PENDING_SAVES = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var gestureDebug: GestureDebugOverlay? = null
    private var debugGeneration = 0
    internal val gestureDebugView: View? get() = gestureDebug?.overlayView
    private var debugCaptureHidden = false
    private val debugCaptureShield = CaptureOverlayShield {
        debugCaptureHidden = it
        gestureDebug?.shield(it)
    }

    private fun clearGestureDebug() {
        debugGeneration++
        gestureDebug?.clear()
    }

    internal fun updateGestureDebug() {
        clearGestureDebug()
        gestureDebug = if (uiPrefs.getBoolean(GestureDebugOverlay.PREFERENCE, false)) {
            GestureDebugOverlay(this, handler).also { it.shield(debugCaptureHidden) }
        } else {
            null
        }
    }

    private var panel: LinearLayout? = null
    private val captureProgress by lazy {
        CaptureProgressPanel(this, { swallowed = true }, { exportCaptureHistory(it) }) {
            runId.ifBlank { manualRunId } to
                listOf(lastStatus, timingSummary()).filter { it.isNotBlank() }.joinToString("\n")
        }
    }
    internal val captureProgressView: View? get() = captureProgress.view
    internal val capturePreviewView: View? get() = captureProgress.previewView
    private var label: TextView? = null
    private var macro: Macro? = null
    private var index = 0
    private var running = false
    private var busy = false
    private var generation = 0
    private var runId = ""
    private var manualRunId = ""
    private var runAccountId: String? = null
    private var runAccountUid: String? = null
    private var manualBusy = false
    private var afterRunChat: AfterRunChat? = null
    private var stopped = false
    private var pendingSaves = 0
    private val saveWaiters = ArrayDeque<() -> Unit>()
    val isWorking: Boolean get() = running || busy || manualBusy || pendingSaves > 0 || afterRunChat != null

    fun prepareEdit(): Boolean {
        if (running) pause("Tạm dừng để quản lý dữ liệu")
        return !busy &&
            !manualBusy &&
            pendingSaves == 0 &&
            afterRunChat == null
    }

    // Collection time of the current run, pauses excluded.
    private var activeMs = 0L
    private var segmentStart = 0L

    private fun elapsed() = activeMs + (if (segmentStart > 0) android.os.SystemClock.elapsedRealtime() - segmentStart else 0L)

    private fun setRunning(on: Boolean) {
        if (on ==
            running
        ) {
            return
        }
        if (on) {
            segmentStart = android.os.SystemClock.elapsedRealtime()
        } else {
            activeMs = elapsed()
            segmentStart = 0
        }
        running = on
    }

    private var failure: String? = null

    /** Hero count read from the list's "owned/total" counter; null until the first list screenshot. */
    private var listTotal: Int? = null

    private fun clock(ms: Long): String {
        val t =
            ms / 1000
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    /** Progress line such as "Tướng 23/129 · 25:12 · còn ~1:30:00", with collection time excluding pauses. */
    private fun progress(m: Macro): String {
        // Heroes by name go as the list does, one block of steps a hero.
        val all = m.json.optString("collectionMode") in LISTED
        val heroes = m.json.optInt("heroCount", 1)
        // Steps before the first hero: the opening wait, and the way into the game when the run starts there.
        val lead = if (m.json.optBoolean("openGame")) 2 else 1
        val perHero = if (all) ((m.steps.length() - lead) / heroes).coerceAtLeast(1) else m.steps.length()
        val target = if (!all) {
            1
        } else if (m.json.optString("collectionMode") == "pick") {
            heroes
        } else {
            minOf(heroes, listTotal ?: heroes)
        }
        val total = if (all) lead + target * perHero else m.steps.length()
        val done = index.coerceIn(0, total)
        if (m.json.optString("collectionMode") == "ranking") return "Thống kê tướng · ${clock(elapsed())}" + left(m)
        if (m.json.optString("collectionMode") == "board") return "Chiến khu · ${clock(elapsed())}" + left(m)
        if (m.json.optString("collectionMode") == "chess") return "Cờ Liên Quân · ${photosTaken(m)} ảnh · ${clock(elapsed())}"
        val head = if (all) {
            "Tướng ${minOf(
                target,
                (index - lead).coerceAtLeast(0) / perHero + 1
            )}/${if (listTotal == null && heroes >= BuiltInMacros.ALL) "?" else target}"
        } else {
            "${(0 until index.coerceAtMost(m.steps.length())).sumOf { m.photos(it) }}/${m.photoTotal}${if (m.photosVary) "+" else ""} ảnh"
        }
        val t = elapsed()
        val eta = left(m).ifEmpty { if (done >= 20 && t > 60_000) " · còn ~" + clock(t * (total - done) / done) else "" }
        return "$head · ${clock(t)}$eta"
    }

    /**
     * " · còn ~…" from the times of earlier runs that went through without a pause ([RunTimes]), blended with this
     * run's own pace as it goes; empty when there is nothing to go by.
     */
    private fun left(m: Macro): String {
        val mode = m.json.optString("collectionMode")
        val opening = m.json.optBoolean("openGame")
        val entered = !opening || index > 0
        val spent = (elapsed() - (if (entered) entryMs else elapsed())) / 1000.0
        // The way into the game, while it is still to come or going on.
        val entry = if (entered) 0.0 else maxOf(0.0, (RunTimes.get(uiPrefs, RunTimes.ENTRY) ?: 60.0) - elapsed() / 1000.0)
        val rest = when (mode) {
            "all", "pick" -> {
                val heroes = m.json.optInt("heroCount", 1)
                val lead = if (opening) 2 else 1
                val perHero = ((m.steps.length() - lead) / heroes).coerceAtLeast(1)
                val target = if (mode == "pick") heroes else minOf(heroes, listTotal ?: if (heroes >= BuiltInMacros.ALL) 130 else heroes)
                val doneHeroes = ((index - lead).coerceAtLeast(0) / perHero).coerceAtMost(target)
                RunTimes.remaining(target - doneHeroes, doneHeroes, spent, RunTimes.get(uiPrefs, heroKey(m)))
            }
            "single" -> RunTimes.get(uiPrefs, heroKey(m))?.let { maxOf(0.0, it - elapsed() / 1000.0) }
            "board" -> {
                val heroes = m.json.optInt("heroCount", 1).let { if (it >= BuiltInMacros.ALL) 130 else it }
                val doneHeroes = boardNext?.takeIf { it.first.startsWith("$runId/") }?.second ?: 0
                RunTimes.remaining(heroes - doneHeroes, doneHeroes, spent, RunTimes.get(uiPrefs, boardKey(m)))
            }
            "ranking" -> {
                val tables = BuiltInMacros.rankCombos(m.json).size
                val doneTables = rankingDone?.takeIf { it.first.startsWith("$runId/") }?.second?.size ?: 0
                RunTimes.remaining(tables - doneTables, doneTables, spent, RunTimes.get(uiPrefs, RunTimes.TABLE))
            }
            else -> null
        } ?: return ""
        return " · còn ~" + clock(((rest + entry) * 1000).toLong())
    }

    private fun boardKey(m: Macro) = boardKey(BuiltInMacros.boardWants(m.json))

    /** Kept times of Chiến khu runs are per hero, for the same tabs and depths. */
    private fun boardKey(wanted: List<BoardScreens.Want>) = "time:board:" + wanted.joinToString(",") { "${it.tab.slug}${it.top}" }

    private fun heroKey(m: Macro) = RunTimes.hero(
        m.json.optString("collectionMode") in LISTED,
        m.json.optBoolean("fast"),
        m.json.optBoolean("detailOnly"),
        m.json.optBoolean("directSkills")
    )

    private fun timingSummary(): String {
        val m = selected() ?: return ""
        val estimate = RunTimes.get(uiPrefs, RunTimes.task(m.timingHash)) ?: return "Ước lượng: cần một lượt hoàn tất trước"
        val matching = prefs.getString("hash", "") == m.hash
        if (matching && (stopped || index >= m.steps.length())) return ""
        val spent = if (matching) elapsed() / 1000.0 else 0.0
        val learned = "theo ${RunTimes.taskSamples(uiPrefs,m.timingHash).size} lượt gần đây"
        return when {
            spent <= 0 -> "Ước tính thu thập ~${clock((estimate * 1000).toLong())} · $learned"
            spent < estimate -> "Đã chạy ${clock((spent * 1000).toLong())} · còn ~${clock(((estimate - spent) * 1000).toLong())} · $learned"
            else -> "Đã chạy ${clock((spent * 1000).toLong())} · lâu hơn các lượt trước"
        }
    }

    /** Time the way into the game took in this run, from its start; part of no hero. */
    private var entryMs = 0L

    /**
     * Keeps how long a run that went from start to end without a pause took ([RunTimes]), for the estimates of
     * the next ones. [heroes] is the number of heroes collected by a list run.
     */
    private fun recordTimes(m: Macro, heroes: Int) {
        if (!prefs.getBoolean("clean", false)) return
        RunTimes.recordTask(uiPrefs, m.timingHash, elapsed() / 1000.0)
        val spent = (elapsed() - entryMs) / 1000.0
        when (m.json.optString("collectionMode")) {
            "all", "pick" -> if (heroes > 0) RunTimes.record(uiPrefs, heroKey(m), spent / heroes)
            "single" -> RunTimes.record(uiPrefs, heroKey(m), spent)
            "ranking" -> RunTimes.record(uiPrefs, RunTimes.TABLE, spent / BuiltInMacros.rankCombos(m.json).size)
            "board" -> boardNext?.takeIf { it.first.startsWith("$runId/") && it.second > 0 }?.let {
                RunTimes.record(
                    uiPrefs,
                    boardKey(m),
                    spent / it.second
                )
            }
        }
        note("Lượt chạy trọn không tạm dừng: đã lưu thời gian để ước tính lần sau")
    }

    /** Package in front, or null when Android does not report one, which is common with Unity games. */
    private fun foreground(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (_: Exception) {
        null
    }

    private fun appName(p: String) = if (p == packageName) "AOV Collector" else p

    /** Appends to the run's events.txt, so pauses and failures can be explained after the run. */
    private fun note(text: String) {
        if (runId.isEmpty()) return
        try {
            File(filesDir, "captures/$runId").apply { mkdirs() }.resolve("events.txt").appendText(
                SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date()) + " · bước ${index + 1} · $text\n"
            )
        } catch (_: Exception) {}
    }

    private val prefs by lazy { getSharedPreferences("collector", MODE_PRIVATE) }
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onServiceConnected() {
        current = this
        updateGestureDebug()
        CrashLog.install(this)
        upgradeBuiltIn()
        try {
            val m = readMacro()
            macro = m
            if (prefs.getString("hash", "") ==
                m.hash
            ) {
                index = prefs.getInt("step", 0)
                runId = prefs.getString("run", "") ?: ""
                stopped = prefs.getBoolean("stopped", false)
                activeMs =
                    prefs.getLong("activeMs", 0L)
                entryMs = prefs.getLong("entryMs", 0L)
            }
            if (m.json.optString("collectionMode").isBlank()) MacroStore.archive(this, m.raw)
        } catch (
            _: Exception
        ) {}
        // A run that was still marked running means the previous process died mid-step.
        if (prefs.getBoolean("running", false)) {
            CrashLog.record(this, "Dịch vụ bị dừng đột ngột khi đang chạy bước ${index + 1} (lượt $runId)", null)
            note("Dịch vụ bị dừng đột ngột (máy tắt app hoặc app lỗi)")
            prefs.edit().putBoolean("clean", false).commit()
            prefs.edit().putBoolean("running", false).commit()
            save()
            lastStatus =
                "Lượt trước bị gián đoạn ở bước ${index + 1}. Mở đúng màn hình rồi bấm Tiếp tục."
        }
        GamePhotoSync.request(this)
        io.execute {
            try {
                CaptureHistory.recover(this)
            } catch (
                e: Exception
            ) {
                CrashLog.record(this, "Chưa khôi phục được nhật ký ảnh", e)
            }
        }
        AfterRun.recover(this)
        if (uiPrefs.getBoolean("panel", false)) handler.post { if (current === this && panel == null) showPanel() }
    }

    fun cancelAfterRunChat(run: String) {
        if (afterRunChat?.run == run) afterRunChat?.cancel()
    }

    fun startAfterRunChat(run: String) {
        if (isWorking) return
        hidePanel()
        val runner = AfterRunChat(this, run) {
            afterRunChat = null
            lastStatus = AfterRun.state(this, run)?.optString("message").orEmpty()
            refreshPanel()
            AfterRun.resume(this)
        }
        afterRunChat = runner
        runner.start()
    }

    private val awayCheck = Runnable {
        val p = foreground()
        if (running &&
            !entering &&
            p != null &&
            p != GAME &&
            p != packageName
        ) {
            pause("Đã rời game (đang mở ${appName(p)}) — tạm dừng")
        }
    }

    /** While the run opens the game itself ([GameEntry]), another app in front is expected. */
    private var entering = false

    /** Steps that start outside the game or on its web pages, where another app in front is expected. */
    private fun entryStep(m: Macro, i: Int) =
        i < m.steps.length() && m.steps.getJSONObject(i).optString("type") in setOf("enter", "ranking", "board", "chess")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
            event.packageName?.toString() == AfterRunProtocol.DRIVE
        ) {
            val notification = event.parcelableData as? android.app.Notification
            if (notification == null || notification.flags and android.app.Notification.FLAG_ONGOING_EVENT == 0) {
                AfterRun.driveNotification(
                    this,
                    AfterRunProtocol.DRIVE,
                    event.text.joinToString("\n") + "\n" + AfterRun.notificationText(notification),
                    notification?.`when` ?: 0L
                )
            }
        }
        if (running && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString()
            // Notifications, keyboards and game boosters report window changes too; only pause if another app stays in front.
            if (p != null && p != packageName && p != GAME) {
                handler.removeCallbacks(awayCheck)
                handler.postDelayed(awayCheck, 1500)
            }
        }
    }

    override fun onInterrupt() {
        pause("Dịch vụ bị ngắt")
    }

    override fun onDestroy() {
        clearGestureDebug()
        gestureDebug = null
        afterRunChat?.cancel()
        afterRunChat = null
        if (running) {
            setRunning(false)
            save()
        }
        generation++
        captureShield.reset()
        handler.removeCallbacksAndMessages(null)
        hidePanel()
        captureProgress.destroy()
        current =
            null
        io.shutdown()
        super.onDestroy()
    }

    private var compact = false
    private var panelParams: WindowManager.LayoutParams? = null
    private var taskLabel: TextView? = null
    private var taskDetail: TextView? = null
    private var primary: Button? = null
    private var stopView: Button? = null
    private var choose: Button? = null
    private var progress: ProgressBar? = null
    private var bubble: TextView? = null
    private var lastStatus = "Sẵn sàng"
    private var activeDialog: AlertDialog? = null
    private var macroPicker: View? = null
    private var pickerY: Int? = null
    private val uiPrefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    private fun dp(n: Int) = Ui.dp(this, n)

    private var selectionCache: Macro? = null
    private var selectionStamp = 0L

    private fun selected(): Macro? = try {
        val file = MacroStore.current(this)
        val stamp = file.lastModified() + file.length()
        if (selectionCache == null || selectionStamp != stamp) {
            selectionCache = readMacro()
            selectionStamp = stamp
        }
        selectionCache
    } catch (e: Exception) {
        null
    }

    fun showPanel() {
        if (panel != null) {
            compact = false
            rebuildPanel()
            return
        }
        compact = false
        rebuildPanel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clearGestureDebug()
        if (panel != null) {
            dismissMacroPicker()
            rebuildPanel()
        }
        captureProgress.rotate()
    }

    private var shielded = false
    private val captureShield = CaptureOverlayShield { shield(it) }
    private var swallowed = false

    /** Taps that hit the hidden panel and were resent; read by the UI smoke check. */
    internal var swallowedTaps = 0
    private var unsavedRetries = 0
    internal val panelView: View? get() = panel

    /** While the macro touches the game, the panel is hidden and untouchable; an invisible view still gets touches until the window update lands. */
    private fun shield(on: Boolean) {
        if (on == shielded) return
        shielded = on
        captureProgress.shield(on)
        val params = panelParams
        if (params !=
            null
        ) {
            params.flags =
                if (on) {
                    params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
        }
        val view = panel ?: return
        view.visibility = if (on) View.INVISIBLE else View.VISIBLE
        if (params != null &&
            view.isAttachedToWindow
        ) {
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                CrashLog.record(this, "Không cập nhật được bảng nổi", e)
            }
        }
    }

    private fun detach(view: View) {
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            CrashLog.record(this, "Không gỡ được bảng nổi", e)
        }
    }

    /** Every panel action always shows its vector icon AND readable label. */
    private fun panelButtonIcon(button: Button, icon: Int, caption: String, color: Int = Color.WHITE) {
        val drawable = getDrawable(icon)?.mutate()?.apply {
            setTint(color)
            setBounds(0, 0, dp(18), dp(18))
        }
        button.setCompoundDrawables(drawable, null, null, null)
        button.compoundDrawablePadding = dp(5)
        button.text = caption
        button.gravity = Gravity.CENTER
        button.textSize = 12f
        button.setPadding(dp(6), 0, dp(6), 0)
        button.contentDescription = caption
        button.tooltipText = caption
    }

    private fun rebuildPanel() {
        // Discard obsolete icon-only preference from previous versions.
        if (uiPrefs.contains("panelShowLabels")) uiPrefs.edit().remove("panelShowLabels").apply()
        dismissMacroPicker()
        panel?.let { detach(it) }
        panel = null
        label = null
        primary = null
        stopView = null
        choose = null
        taskLabel = null
        taskDetail = null
        progress = null
        bubble = null
        val layout = object : LinearLayout(this) {
            // A tap that still reaches the hidden panel must not press its buttons; the macro retries it.
            override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                if (!shielded) return super.dispatchTouchEvent(e)
                if (e.actionMasked ==
                    MotionEvent.ACTION_DOWN
                ) {
                    swallowed = true
                }
                return true
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background =
                Ui.shape(Color.argb(232, 20, 24, 42), dp(20), Color.argb(70, 230, 230, 255))
        }
        val params = panelParams ?: WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            format = android.graphics.PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(60)
        }
        params.width = dp(if (compact) 64 else 278)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        val bounds = wm.currentWindowMetrics.bounds
        params.x = params.x.coerceIn(0, maxOf(0, bounds.width() - params.width))
        params.y =
            params.y.coerceIn(0, maxOf(0, bounds.height() - dp(if (compact) 60 else 260)))
        panelParams = params

        fun drag(v: View, onTap: (() -> Unit)? = null) {
            var x = 0f
            var y = 0f
            var sx = 0
            var sy = 0
            var moved = false
            v.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        x = e.rawX
                        y = e.rawY
                        sx = params.x
                        sy = params.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(e.rawX - x) + kotlin.math.abs(e.rawY - y) > dp(6)) moved = true
                        params.x = (sx + (e.rawX - x).toInt()).coerceIn(0, maxOf(0, bounds.width() - params.width))
                        params.y = (sy + (e.rawY - y).toInt()).coerceIn(0, maxOf(0, bounds.height() - layout.height))
                        if (layout.isAttachedToWindow) wm.updateViewLayout(layout, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            v.performClick()
                            onTap?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        if (compact) {
            bubble =
                Ui.text(this, "AOV", 12f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(38)
                    contentDescription =
                        "Mở bảng điều khiển thu thập"
                }
            layout.addView(bubble)
            drag(bubble!!) {
                compact = false
                rebuildPanel()
            }
        } else {
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val title = Ui.text(this, "AOV · KÉO ĐỂ DI CHUYỂN", 10f, Color.rgb(220, 224, 237), true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            header.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
            drag(title)
            val history = Ui.button(this, "Ảnh") { captureProgress.toggle() }.apply {
                panelButtonIcon(this, R.drawable.ic_panel_history, "Ảnh")
                contentDescription = "Mở hoặc đóng tiến trình ảnh"
                setTextColor(Color.WHITE)
                background = Ui.shape(Color.rgb(47, 52, 74), dp(10))
            }
            header.addView(history, LinearLayout.LayoutParams(dp(62), dp(32)).apply { rightMargin = dp(5) })
            val collapse = Ui.button(this, "−") {
                compact = true
                rebuildPanel()
            }.apply {
                contentDescription = "Thu gọn bảng nổi"
                setTextColor(Color.WHITE)
                background = Ui.shape(Color.rgb(47, 52, 74), dp(10))
            }
            header.addView(collapse, LinearLayout.LayoutParams(dp(34), dp(32)))
            layout.addView(header)

            // Small macro selector ABOVE the status/actions. Its text always displays the current macro,
            // including an imported JSON macro's own name.
            val macroRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            macroRow.addView(
                Ui.text(this, "MACRO", 10f, Color.rgb(166, 169, 202), true).apply {
                    includeFontPadding = false
                    setPadding(0, 0, dp(4), 0)
                },
                LinearLayout.LayoutParams(dp(49), dp(32))
            )
            choose = Ui.button(this, "Chọn macro") { showTaskPicker() }.apply {
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTextColor(Color.WHITE)
                background = Ui.shape(Color.argb(165, 63, 53, 104), dp(10), Color.argb(90, 208, 196, 255))
                setPadding(dp(10), 0, dp(6), 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                contentDescription = "Chọn macro, có tìm kiếm"
                getDrawable(R.drawable.ic_panel_chevron)?.mutate()?.apply {
                    setTint(Color.WHITE)
                    setBounds(0, 0, dp(14), dp(14))
                }?.let { setCompoundDrawables(null, null, it, null) }
            }
            macroRow.addView(choose, LinearLayout.LayoutParams(0, dp(36), 1f))
            layout.addView(macroRow, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(5) })

            taskDetail = Ui.text(this, "", 11f, Color.rgb(226, 230, 240)).apply {
                setPadding(0, dp(3), 0, 0)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            layout.addView(taskDetail)
            progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(159, 139, 255))
            }
            layout.addView(progress, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(8) })
            label = Ui.text(this, "", 11f, Color.rgb(205, 211, 225)).apply {
                setPadding(0, dp(3), 0, dp(4))
                includeFontPadding = false
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            layout.addView(label)

            val actions = LinearLayout(this)
            primary = Ui.button(this, "Chạy", true) {
                when {
                    running -> pause("Đã tạm dừng")
                    busy || manualBusy -> status("Đang hoàn tất thao tác hiện tại…")
                    ended() -> {
                        reset()
                        status("Lượt mới sẵn sàng. " + (selected()?.let { MacroStore.instruction(it) } ?: ""))
                    }
                    else -> start()
                }
            }.apply { background = Ui.shape(Color.rgb(96, 75, 209), dp(11)) }
            actions.addView(primary, LinearLayout.LayoutParams(0, dp(42), 1f))
            val stopButton = Ui.button(this, "Dừng", dangerous = true) { stop() }.also { stopView = it }.apply {
                setTextColor(Color.rgb(255, 213, 220))
                background = Ui.shape(Color.argb(190, 112, 43, 64), dp(11))
            }
            actions.addView(stopButton, LinearLayout.LayoutParams(dp(86), dp(42)).apply { leftMargin = dp(7) })
            layout.addView(actions)

            val footer = LinearLayout(this)

            fun foot(caption: String, icon: Int, action: () -> Unit) {
                val b = Ui.button(this, caption, action = action).apply {
                    setTextColor(Color.WHITE)
                    background = Ui.shape(Color.argb(155, 53, 60, 84), dp(10))
                    panelButtonIcon(this, icon, caption)
                }
                footer.addView(
                    b,
                    LinearLayout.LayoutParams(0, dp(39), 1f).apply {
                        topMargin = dp(7)
                        if (footer.childCount > 0) leftMargin = dp(6)
                    }
                )
            }
            foot("Chụp", R.drawable.ic_panel_camera) { manualCapture() }
            foot("Về app", R.drawable.ic_panel_home) {
                pause("Đã tạm dừng")
                hidePanel()
                startActivity(
                    android.content.Intent(this, MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
            foot("Đóng", R.drawable.ic_panel_close) {
                pause("Đã tạm dừng")
                hidePanel()
            }
            layout.addView(footer)
        }
        if (shielded) layout.visibility = View.INVISIBLE
        try {
            wm.addView(layout, params)
        } catch (e: Exception) {
            CrashLog.record(this, "Không mở được bảng nổi", e)
            return
        }
        panel = layout
        uiPrefs.edit().putBoolean("panel", true).apply()
        layout.post {
            if (panel === layout &&
                layout.isAttachedToWindow
            ) {
                val maxY = maxOf(0, wm.currentWindowMetrics.bounds.height() - layout.height)
                if (params.y >
                    maxY
                ) {
                    params.y = maxY
                    wm.updateViewLayout(layout, params)
                }
            }
        }
        refreshPanel()
    }

    private fun ended(): Boolean {
        val m = selected() ?: return false
        return prefs.getString("hash", "") == m.hash && (prefs.getBoolean("stopped", false) || prefs.getInt("step", 0) >= m.steps.length())
    }

    private fun refreshPanel() {
        val m = selected()
        val step = if (m != null && prefs.getString("hash", "") == m.hash) prefs.getInt("step", 0) else 0
        taskLabel?.text = m?.let { MacroStore.title(it) } ?: "Chọn tác vụ"
        taskDetail?.text = m?.let { MacroStore.description(it) } ?: "Thông tin tướng, thống kê tướng hoặc Chiến khu"
        choose?.text = if (macroPicker != null) "Đóng danh sách ▲" else m?.let { MacroStore.title(it) } ?: "Chọn macro"
        choose?.tooltipText = m?.let { MacroStore.title(it) } ?: "Chọn macro"
        choose?.isEnabled = !isWorking
        choose?.alpha = if (isWorking) .45f else 1f
        // Chess discovers heroes while scrolling; macro step count is not collection progress.
        val unknownChessTotal = m != null && m.json.optString("collectionMode") == "chess" && step < m.steps.length()
        progress?.isIndeterminate = unknownChessTotal && running
        progress?.visibility = if (unknownChessTotal && !running) View.GONE else View.VISIBLE
        progress?.progress = if (unknownChessTotal) {
            0
        } else if (m != null) {
            step * 100 / maxOf(1, m.steps.length())
        } else {
            0
        }
        label?.text = listOf(lastStatus, timingSummary()).filter { it.isNotBlank() }.joinToString("\n")
        val caption = when {
            running -> "Tạm dừng"
            busy || manualBusy -> "Đang xử lý…"
            ended() -> "Tạo lượt mới"
            step > 0 -> "Tiếp tục"
            else -> "Chạy"
        }
        val glyph = when {
            running -> R.drawable.ic_panel_pause
            busy || manualBusy -> R.drawable.ic_panel_check
            ended() -> R.drawable.ic_panel_reset
            else -> R.drawable.ic_panel_play
        }
        primary?.let { panelButtonIcon(it, glyph, caption) }
        primary?.isEnabled = running || (!busy && !manualBusy && m != null)
        stopView?.let { panelButtonIcon(it, R.drawable.ic_panel_stop, "Dừng", Color.rgb(255, 213, 220)) }
        // Dừng ends a run: nothing to end before one starts or after it ended.
        val stoppable = canStop()
        stopView?.isEnabled = stoppable
        stopView?.alpha = if (stoppable) 1f else .4f
        bubble?.text = if (running && unknownChessTotal) {
            "AOV\nĐang chạy"
        } else if (running) {
            "AOV\n${progress?.progress ?: if (m != null) step * 100 / maxOf(1,m.steps.length()) else 0}%"
        } else {
            "AOV"
        }
    }

    /** This service in the look chosen in "Giao diện", for the views and dialogs it shows over the game. */
    private fun themed(): android.content.Context {
        Ui.use(this)
        return ContextThemeWrapper(this, Ui.windowTheme(this))
    }

    private fun display(dialog: AlertDialog) {
        activeDialog?.dismiss()
        activeDialog = dialog
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener {
            activeDialog = null
            refreshPanel()
        }
        dialog.show()
    }

    private fun manageAccounts() {
        hidePanel()
        startActivity(
            Intent(this, MainActivity::class.java).putExtra(Accounts.OPEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun accountHelp(c: android.content.Context, body: LinearLayout) {
        val all = Accounts.list(this)
        body.addView(
            Ui.text(
                c,
                Accounts.problem(this) ?: if (all.isEmpty()) {
                    "Chưa có tài khoản. Thêm tài khoản bên dưới; khi bấm Chạy, app sẽ cho chọn tài khoản cần kiểm tra."
                } else {
                    "Đã lưu ${all.size} tài khoản. Kiểm tra UID sẽ bật Tự mở game và cho chọn tài khoản khi bắt đầu lượt."
                },
                12f,
                Ui.muted
            )
        )
        body.addView(Ui.button(c, "Quản lý tài khoản") { manageAccounts() })
    }

    private fun chooseRunAccount() {
        val all = Accounts.list(this)
        if (all.isEmpty()) {
            display(
                AlertDialog.Builder(themed()).setTitle("Chưa có tài khoản đã lưu")
                    .setMessage(
                        Accounts.problem(this) ?: "Vào Quản lý tài khoản để thêm UID và tài khoản Garena, rồi chọn lại khi bấm Chạy."
                    )
                    .setPositiveButton("Quản lý tài khoản") { _, _ -> manageAccounts() }.setNegativeButton("Hủy", null).create()
            )
            return
        }
        var selected = all.indexOfFirst { it.id == Accounts.selectedId(this) }.coerceAtLeast(0)
        val dialog = AlertDialog.Builder(themed()).setTitle("Chọn tài khoản cho lượt chạy")
            .setSingleChoiceItems(all.map { it.caption }.toTypedArray(), selected) { _, which -> selected = which }
            .setPositiveButton("Chạy", null).setNegativeButton("Hủy", null)
            .setNeutralButton("Quản lý tài khoản") { _, _ -> manageAccounts() }.create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val account = all[selected]
            if (Accounts.load(this, account.id) == null) {
                Toast.makeText(this, "Tài khoản này cần nhập lại thông tin. Chọn Quản lý tài khoản → Sửa.", Toast.LENGTH_LONG).show()
            } else {
                dialog.dismiss()
                handler.post { startWithAccount(account.id) }
            }
        }
    }

    fun showTaskPicker() {
        val c = themed()
        if (libraryBusy) {
            status("Đang lưu dữ liệu; vui lòng chờ")
            return
        }
        if (isWorking) {
            status("Tạm dừng trước khi đổi macro")
            return
        }
        if (panel == null || compact) showPanel()
        if (macroPicker != null) {
            dismissMacroPicker()
            return
        }
        val host = panel ?: return
        val saved = MacroStore.saved(this).mapNotNull {
            try {
                Macro(it.readText())
            } catch (_: Exception) {
                null
            }
        }
        // Each task with a line on what it does; the search reads both.
        val choices =
            listOf(
                "Lấy thông tin tướng" to "Theo tên, các ô đầu danh sách hoặc tướng đang mở",
                "Thống kê tướng" to "Bảng xếp hạng tướng trong Cộng đồng",
                "Chiến khu" to "Bảng xếp hạng người chơi của từng tướng",
                "Cờ Liên Quân · Bách khoa" to "Chọn từng mục, tab, chi tiết; cuộn hết trang và lưu theo thư mục"
            ) +
                saved.map { it.name to "Macro đã nhập" }
        var visible = choices.indices.toList()
        val body = Ui.stack(c).apply {
            setPadding(dp(10), dp(10), dp(10), dp(6))
            background = Ui.shape(Ui.surface, dp(14), Ui.border)
        }
        val search = Ui.field(c, "Tìm macro…")
        body.addView(search, LinearLayout.LayoutParams(-1, dp(46)))
        val list = ListView(c).apply {
            dividerHeight = 0
            isVerticalScrollBarEnabled = true
        }
        body.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val empty = Ui.text(c, "Không tìm thấy macro", 13f, Ui.muted).apply { visibility = View.GONE }
        body.addView(empty)

        fun filter(query: String) {
            val q = HeroNames.slug(query).takeUnless { query.isBlank() } ?: ""
            visible = choices.indices.filter { q.isEmpty() || HeroNames.slug(choices[it].first + " " + choices[it].second).contains(q) }
            list.adapter = object : BaseAdapter() {
                override fun getCount() = visible.size

                override fun getItem(position: Int) = choices[visible[position]]

                override fun getItemId(position: Int) = visible[position].toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val (title, line) = choices[visible[position]]
                    return Ui.stack(c).apply {
                        setPadding(dp(10), dp(8), dp(10), dp(8))
                        addView(Ui.text(c, title, 15f, Ui.ink, true).apply { setPadding(0, 0, 0, 0) })
                        addView(
                            Ui.text(c, line, 12f, Ui.muted).apply {
                                setPadding(0, dp(1), 0, 0)
                                maxLines = 2
                            }
                        )
                    }
                }
            }
            empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val i = visible.getOrNull(position) ?: return@setOnItemClickListener
            dismissMacroPicker()
            handler.post {
                when (i) {
                    0 -> heroForm()
                    1 -> rankingForm()
                    2 -> boardForm()
                    3 -> chessForm()
                    else -> selectMacro(saved[i - 4].raw)
                }
            }
        }
        body.addView(Ui.button(c, "Đóng danh sách") { dismissMacroPicker() }, LinearLayout.LayoutParams(-1, dp(38)))
        filter("")
        // Expand inside the existing accessibility window; do not create a popup token.
        // Keep the compact toggle available, but give it a DIFFERENT caption than any row
        // so the selected macro's name cannot intercept a tap on its list entry.
        for (i in 2 until host.childCount) host.getChildAt(i).visibility = View.GONE
        choose?.text = "Đóng danh sách ▲"
        macroPicker = body
        val height = minOf(dp(250), wm.currentWindowMetrics.bounds.height() - dp(150)).coerceAtLeast(dp(120))
        host.addView(body, 2, LinearLayout.LayoutParams(-1, height))
        panelParams?.let { params ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            pickerY = params.y
            params.y = params.y.coerceAtMost(maxOf(0, wm.currentWindowMetrics.bounds.height() - height - dp(110)))
            wm.updateViewLayout(host, params)
        }
        search.requestFocus()
    }

    private fun dismissMacroPicker() {
        val picker = macroPicker ?: return
        macroPicker = null
        val host = panel ?: return
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(host.windowToken, 0)
        host.removeView(picker)
        for (i in 0 until host.childCount) host.getChildAt(i).visibility = View.VISIBLE
        choose?.text = selected()?.let { MacroStore.title(it) } ?: "Chọn macro"
        panelParams?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            // Return to where the user placed the panel; the list only lifted it to fit.
            pickerY?.let { params.y = it }
            pickerY = null
            if (host.isAttachedToWindow) wm.updateViewLayout(host, params)
        }
    }

    /**
     * "Lấy thông tin tướng": heroes by name, found on the All-heroes list ([HeroSearch]) among the names kept from
     * earlier runs ([HeroIndex]) or typed; the first cards of the list; or the hero page open now.
     */
    private fun heroForm() {
        val c = themed()
        val body = Ui.stack(c).apply { setPadding(dp(20), dp(6), dp(20), dp(6)) }
        body.addView(Ui.text(c, "Lấy tổng quan, thuộc tính và chiêu của tướng.", 14f))
        val modes = listOf("Theo tên tướng", "Các ô đầu danh sách", "Tướng đang mở")
        val group = RadioGroup(c).apply { orientation = RadioGroup.VERTICAL }
        val radios = modes.map { text ->
            RadioButton(c).apply {
                this.text = text
                textSize = 14f
                id = View.generateViewId()
            }.also { group.addView(it) }
        }
        body.addView(Ui.section(c, "Cách lấy"))
        body.addView(group)
        // By name: a search over the names kept, a check box each, and the typed name when it is a new one.
        val byName = Ui.stack(c).also { body.addView(it) }
        byName.addView(
            Ui.text(c, "Tìm và tick các tướng cần lấy (tên đã lưu từ các lượt trước), hoặc gõ tên mới rồi bấm Thêm.", 13f, Ui.muted, true)
        )
        val query = Ui.field(c, "Gõ tên tướng, ví dụ Violet").also { byName.addView(it) }
        val chosenLine = Ui.text(c, "", 13f, Ui.ink, true).also { byName.addView(it) }
        val add = Ui.button(c, "Thêm") {}.also { byName.addView(it) }
        val names = Ui.stack(c).also { byName.addView(it) }
        val more = Ui.text(c, "", 12f, Ui.muted).also { byName.addView(it) }
        byName.addView(
            Ui.text(
                c,
                "App tới ô tướng ở lần trước, đọc lại tên trên thẻ; sai thì dò danh sách. Tướng không có trong danh sách được bỏ qua.",
                12f,
                Ui.muted
            )
        )
        // The first cards of the list.
        val byCount = Ui.stack(c).also { body.addView(it) }
        byCount.addView(Ui.text(c, "Số ô tướng cần lấy (từ đầu danh sách Tất cả tướng)", 13f, Ui.muted, true))
        val count = Ui.field(c, "1–${BuiltInMacros.ALL}", uiPrefs.getInt("count", 2).toString(), true).also { byCount.addView(it) }
        val quick = LinearLayout(c)
        listOf(2 to "2 ô", 11 to "11 ô", BuiltInMacros.ALL to "Tất cả").forEach { (n, text) ->
            quick.addView(
                Ui.button(c, text) { count.setText(n.toString()) },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    marginEnd =
                        dp(4)
                }
            )
        }
        byCount.addView(quick)
        byCount.addView(
            Ui.text(
                c,
                "App tự đưa danh sách về đầu, đo từng lần cuộn và dừng khi hết danh sách. Chọn Tất cả để lấy đến cuối.",
                12f,
                Ui.muted
            )
        )
        // The hero page open now.
        val open = Ui.stack(c).also { body.addView(it) }
        open.addView(Ui.text(c, "Mở trang chi tiết tướng trước. Tên tướng (không bắt buộc)", 13f, Ui.muted, true))
        val single = Ui.field(c, "Để trống để tự đọc tên").also { open.addView(it) }
        open.addView(
            Ui.text(c, "Tên được đọc từ ảnh. Nếu không rõ, app dùng thư mục đánh số; có thể đổi tên trong Ảnh đã lưu.", 12f, Ui.muted)
        )
        // Time and space for the whole run; it follows the mode, the heroes and the options.
        val estimate = Ui.text(c, "", 13f, Ui.ink, true).also { body.addView(it) }

        fun option(text: String, key: String) = CheckBox(c).apply {
            this.text = text
            textSize = 14f
            isChecked = uiPrefs.getBoolean(key, false)
        }.also { body.addView(it) }
        body.addView(Ui.section(c, "Tùy chọn chụp"))
        val fast = option("Chạy nhanh: chờ ngắn hơn sau mỗi lần chạm (nên thử 2 tướng trước)", "fast")
        val detailOnly = option("Chỉ chụp Chi tiết: bỏ ảnh Tóm tắt của nội tại và chiêu", "detailOnly")
        val directSkills =
            option("Chuyển thẳng giữa các chiêu: bấm sang chiêu kế tiếp khi bảng chiêu còn mở, chỉ đóng sau chiêu cuối", "directSkills")
        body.addView(Ui.section(c, "Vào game"))
        val openGame =
            option(
                "Tự mở game và vào Danh sách tướng: đăng nhập Garena, bỏ qua màn quà và form đăng ký, đóng popup sự kiện, rồi bấm Tướng ở sảnh",
                "openGame"
            )
        val checkAccount = option("Kiểm tra UID · chọn tài khoản khi bấm Chạy", "checkAccount")
        accountHelp(c, body)
        val traceEntry = CheckBox(c).apply {
            text = "Lưu ảnh từng bước vào game để xem lại (menu ⋮ của lượt)"
            textSize = 14f
            isChecked =
                uiPrefs.getBoolean("traceEntry", true)
        }.also { body.addView(it) }
        var known = emptyList<HeroIndex.Entry>()
        val chosen = LinkedHashSet(uiPrefs.getString("pickNames", "").orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() })

        fun mode() = radios.indexOfFirst { it.isChecked }.coerceAtLeast(0)

        fun typed() = query.text.toString().trim().take(40)

        /** The heroes a by-name run would take: those ticked, else the name typed. */
        fun picked() = chosen.toList().ifEmpty { listOfNotNull(typed().takeIf { it.count { c -> c.isLetter() } >= 2 }) }

        // The way into the game, the account check and the trace: only for runs that start on the list.
        fun follow() {
            val list = mode() < 2
            openGame.isEnabled = list
            val on = list && openGame.isChecked
            checkAccount.isEnabled = list
            if (!on) checkAccount.isChecked = false
            traceEntry.isEnabled = on
        }
        checkAccount.setOnCheckedChangeListener { _, on -> if (on) openGame.isChecked = true }
        val shot = averageShot()
        val total = uiPrefs.getInt("heroTotal", 0).takeIf { it > 0 }

        fun updateEstimate() {
            val m = mode()
            val batch = m < 2
            val asked = when (m) {
                0 -> picked().size
                1 -> count.text.toString().toIntOrNull()?.coerceIn(0, BuiltInMacros.ALL) ?: 0
                else -> 1
            }
            if (asked ==
                0
            ) {
                estimate.text = if (m ==
                    0
                ) {
                    "Chọn hoặc gõ ít nhất một tướng."
                } else {
                    "Nhập số ô tướng."
                }
                estimate.setTextColor(Ui.danger)
                return
            }
            val heroes = if (asked >= BuiltInMacros.ALL) total ?: 130 else asked
            val photos = heroes * (if (detailOnly.isChecked) 6 else 10)
            val need = photos * shot
            val free = filesDir.usableSpace
            // Times of earlier runs that went through without a pause, else the steps' own; the way in about a minute.
            val kept = RunTimes.get(uiPrefs, RunTimes.hero(batch, fast.isChecked, detailOnly.isChecked, directSkills.isChecked))
            val entry = if (batch && openGame.isChecked) RunTimes.get(uiPrefs, RunTimes.ENTRY) ?: 60.0 else 0.0
            // Finding a hero by name reads the cards' names: a few seconds more than tapping a card.
            val each =
                kept ?: (
                    BuiltInMacros.secondsPerHero(
                        batch,
                        fast.isChecked,
                        detailOnly.isChecked,
                        directSkills.isChecked
                    ) + if (m == 0) 1.5 else 0.0
                    )
            val who = when {
                !batch -> ""
                asked >= BuiltInMacros.ALL -> if (total !=
                    null
                ) {
                    "Tất cả $heroes tướng · "
                } else {
                    "Khoảng $heroes tướng · "
                }
                else -> "$heroes tướng · "
            }
            estimate.text =
                "Ước tính: $who$photos ảnh · khoảng ${Format.duration((heroes * each + entry).toLong())}" +
                (if (kept != null) " (theo lượt chạy trọn trước)" else "") +
                "\nCần khoảng ${Format.size(need)} · máy còn trống ${Format.size(free)}" +
                (if (need > free - 30L * 1024 * 1024) "\nKhông đủ chỗ cho cả lượt: app sẽ dừng khi còn dưới 30 MB." else "")
            estimate.setTextColor(if (need > free - 30L * 1024 * 1024) Ui.danger else Ui.ink)
        }

        fun render() {
            val q = typed()
            val slug = HeroNames.slug(q).takeIf { q.isNotEmpty() }
            chosenLine.text =
                if (chosen.isEmpty()) {
                    (
                        if (known.isEmpty()) {
                            "Chưa có tên tướng đã lưu: gõ tên rồi bấm Thêm."
                        } else {
                            "Chưa chọn tướng nào (${known.distinctBy {
                                it.slug
                            }.size} tên đã lưu)."
                        }
                        )
                } else {
                    "Đã chọn ${chosen.size}: ${chosen.joinToString(", ")}"
                }
            val all = (HeroIndex.names(known) + chosen).distinctBy { HeroNames.slug(it) }
            val shown = all.filter { slug == null || HeroNames.slug(it).contains(slug) || HeroFinder.same(HeroNames.slug(it), slug) }
            // A typed name that is none of the names kept can be added as it is.
            val fresh = slug != null && q.count { it.isLetter() } >= 2 && all.none { HeroNames.slug(it) == slug }
            add.text = "Thêm tướng: $q"
            add.visibility = if (fresh) View.VISIBLE else View.GONE
            names.removeAllViews()
            // Ticked names first, then the others, at most 40 at once: typing narrows them.
            val ordered = shown.sortedBy { if (it in chosen) 0 else 1 }
            for (n in ordered.take(40)) {
                names.addView(
                    CheckBox(c).apply {
                        text = n
                        textSize = 14f
                        isChecked = n in chosen
                        setOnCheckedChangeListener { _, on ->
                            if (on) chosen.add(n) else chosen.remove(n)
                            handler.post { render() }
                        }
                    }
                )
            }
            more.text = if (ordered.size >
                40
            ) {
                "… và ${ordered.size - 40} tên khác: gõ để lọc"
            } else {
                ""
            }
            more.visibility = if (ordered.size > 40) View.VISIBLE else View.GONE
            updateEstimate()
        }
        add.setOnClickListener {
            val q = typed()
            if (q.isNotEmpty()) {
                chosen.add(q)
                query.setText("")
            }
        }

        fun show() {
            val m = mode()
            byName.visibility = if (m ==
                0
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
            byCount.visibility = if (m ==
                1
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
            open.visibility = if (m == 2) View.VISIBLE else View.GONE
            follow()
            updateEstimate()
        }
        radios[uiPrefs.getInt("heroMode", 0).coerceIn(0, 2)].isChecked = true
        group.setOnCheckedChangeListener { _, _ -> show() }
        val watch = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                render()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        query.addTextChangedListener(watch)
        count.addTextChangedListener(watch)
        listOf(fast, detailOnly, directSkills).forEach { it.setOnCheckedChangeListener { _, _ -> updateEstimate() } }
        openGame.setOnCheckedChangeListener { _, _ ->
            follow()
            updateEstimate()
        }
        show()
        render()
        // The names kept, rebuilt from the saved runs the first time.
        io.execute {
            val entries = HeroIndex.loadOrRebuild(filesDir, File(filesDir, "captures"))
            handler.post {
                known = entries
                render()
            }
        }
        body.addView(Ui.text(c, "Chọn tác vụ sẽ tạo lượt mới. Ảnh đã chụp vẫn giữ lại.", 12f, Ui.muted))
        val dialog = AlertDialog.Builder(c).setTitle("Lấy thông tin tướng").setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Chọn tác vụ", null).setNegativeButton("Hủy", null).create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val m = mode()
                val list = m < 2
                uiPrefs.edit().putInt(
                    "heroMode",
                    m
                ).putBoolean(
                    "fast",
                    fast.isChecked
                ).putBoolean("detailOnly", detailOnly.isChecked).putBoolean("directSkills", directSkills.isChecked).apply()
                if (list) {
                    uiPrefs.edit().putBoolean(
                        "openGame",
                        openGame.isChecked
                    ).putBoolean("checkAccount", checkAccount.isChecked).putBoolean("traceEntry", traceEntry.isChecked).apply()
                }
                val entering = list && openGame.isChecked
                val checking = entering && checkAccount.isChecked && checkAccount.isEnabled
                val raw = when (m) {
                    0 -> {
                        val heroes = picked()
                        if (heroes.isEmpty()) {
                            query.error = "Chọn hoặc gõ ít nhất một tướng"
                            return@setOnClickListener
                        }
                        uiPrefs.edit().putString("pickNames", chosen.joinToString("\n")).apply()
                        BuiltInMacros.named(
                            heroes,
                            heroes.map { n ->
                                HeroIndex.find(known, n).firstOrNull()?.index ?: -1
                            },
                            fast.isChecked,
                            detailOnly.isChecked,
                            directSkills.isChecked,
                            entering,
                            checking,
                            traceEntry.isChecked
                        )
                    }
                    1 -> {
                        val n = count.text.toString().toIntOrNull() ?: 0
                        if (n !in
                            1..BuiltInMacros.ALL
                        ) {
                            count.error =
                                "Nhập số từ 1 đến ${BuiltInMacros.ALL}; ${BuiltInMacros.ALL} là lấy hết danh sách"
                            return@setOnClickListener
                        }
                        uiPrefs.edit().putInt("count", n).apply()
                        BuiltInMacros.create(
                            "Danh sách tướng", n, true, fast.isChecked, detailOnly.isChecked,
                            directSkills.isChecked, entering, checking, traceEntry.isChecked
                        )
                    }
                    else -> BuiltInMacros.create(
                        single.text.toString(),
                        1,
                        false,
                        fast.isChecked,
                        detailOnly.isChecked,
                        directSkills.isChecked
                    )
                }
                if (selectMacro(raw)) dialog.dismiss()
            } catch (e: Exception) {
                status(e.message ?: "Không tạo được tác vụ")
            }
        }
    }

    /** The hero statistics: the chosen tables of "Bảng Phân Bậc Tướng", for the chosen kinds of match ([RankingReader]). */
    private fun rankingForm() {
        val c = themed()
        val body = Ui.stack(c).apply { setPadding(dp(20), dp(6), dp(20), dp(6)) }
        body.addView(
            Ui.text(
                c,
                "Từ sảnh, app vào Cộng đồng → mục tướng → Bảng xếp hạng đầy đủ, rồi chụp hết danh sách của các bảng và chế độ đã chọn.",
                14f
            )
        )
        val estimate = Ui.text(c, "", 13f, Ui.ink, true).also { body.addView(it) }

        fun check(text: String, on: Boolean) = CheckBox(c).apply {
            this.text = text
            textSize = 14f
            isChecked = on
        }
        // Both tables and all three kinds of match unless chosen otherwise last time.
        val saved = uiPrefs.getString("rankCombos", null)?.split(',')?.toSet()
        body.addView(Ui.section(c, "Bảng"))
        val tables = RankScreens.Table.values().associateWith { t ->
            check(t.label, saved == null || saved.any { it.startsWith(t.slug) }).also { body.addView(it) }
        }
        body.addView(Ui.section(c, "Chế độ"))
        val modes = RankScreens.Mode.values().associateWith { m ->
            check(m.label, saved == null || saved.any { it.endsWith(m.slug) }).also { body.addView(it) }
        }

        fun chosen() = RankScreens.COMBOS.filter { tables.getValue(it.table).isChecked && modes.getValue(it.mode).isChecked }

        fun option(text: String, key: String, default: Boolean = false) = check(text, uiPrefs.getBoolean(key, default)).also {
            body.addView(it)
        }
        body.addView(Ui.section(c, "Vào game"))
        val openGame =
            option(
                "Tự mở game và vào sảnh: đóng game còn chạy, đăng nhập Garena, bỏ qua màn quà và form đăng ký, đóng popup sự kiện",
                "rankOpenGame"
            )
        val checkAccount = option("Kiểm tra UID · chọn tài khoản khi bấm Chạy", "checkAccount")
        accountHelp(c, body)
        val traceEntry = option("Lưu ảnh từng bước vào game để xem lại (menu ⋮ của lượt)", "traceEntry", true)

        fun follow() {
            if (!openGame.isChecked) checkAccount.isChecked = false
            traceEntry.isEnabled = openGame.isChecked
        }
        checkAccount.setOnCheckedChangeListener { _, on -> if (on) openGame.isChecked = true }

        fun updateEstimate() {
            val n = chosen().size
            if (n == 0) {
                estimate.text = "Chọn ít nhất một bảng và một chế độ."
                estimate.setTextColor(Ui.danger)
                return
            }
            // A list of about 28 screens, some 4 seconds each, unless earlier clean runs tell better.
            val kept = RunTimes.get(uiPrefs, RunTimes.TABLE)
            val seconds = n * (kept ?: 28 * 4.0) + (if (openGame.isChecked) RunTimes.get(uiPrefs, RunTimes.ENTRY) ?: 60.0 else 30.0)
            val photos = n * 28
            val need = photos * 900_000L
            estimate.text =
                "Ước tính: $n/${RankScreens.COMBOS.size} bảng · khoảng $photos ảnh · khoảng ${Format.duration(seconds.toLong())}" +
                (if (kept != null) " (theo lượt chạy trọn trước)" else "") +
                "\nCần khoảng ${Format.size(need)} · máy còn trống ${Format.size(filesDir.usableSpace)}"
            estimate.setTextColor(Ui.ink)
        }
        follow()
        updateEstimate()
        openGame.setOnCheckedChangeListener { _, _ ->
            follow()
            updateEstimate()
        }
        (tables.values + modes.values).forEach { it.setOnCheckedChangeListener { _, _ -> updateEstimate() } }
        body.addView(Ui.text(c, "Không tick Tự mở game: mở sảnh game (hoặc trang Bảng Phân Bậc Tướng) trước khi bấm Chạy.", 12f, Ui.muted))
        val dialog = AlertDialog.Builder(c).setTitle("Thống kê tướng").setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Chọn tác vụ", null).setNegativeButton("Hủy", null).create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val combos = chosen().map { it.slug }
            if (combos.isEmpty()) {
                status("Chọn ít nhất một bảng và một chế độ")
                return@setOnClickListener
            }
            uiPrefs.edit().putBoolean(
                "rankOpenGame",
                openGame.isChecked
            ).putBoolean("checkAccount", checkAccount.isChecked).putBoolean("traceEntry", traceEntry.isChecked)
                .putString("rankCombos", combos.joinToString(",")).apply()
            if (selectMacro(
                    BuiltInMacros.ranking(
                        openGame.isChecked,
                        checkAccount.isChecked && checkAccount.isEnabled,
                        traceEntry.isChecked,
                        combos
                    )
                )
            ) {
                dialog.dismiss()
            }
        }
    }

    /** The players' ranking of each hero, "Chiến khu" ([BoardReader]): how many heroes, which tabs, how deep. */
    private fun boardForm() {
        val c = themed()
        val body = Ui.stack(c).apply { setPadding(dp(20), dp(6), dp(20), dp(6)) }
        body.addView(
            Ui.text(
                c,
                "Từ sảnh, app vào Đấu hạng → Chiến khu (qua các màn mở mùa mới và chọn khu vực nếu có), rồi lần lượt chọn từng tướng trong Chọn tướng và chụp bảng xếp hạng người chơi của tướng đó.",
                14f
            )
        )
        body.addView(Ui.section(c, "Số tướng"))
        val count = Ui.field(c, "1–${BuiltInMacros.ALL}", uiPrefs.getInt("boardCount", BuiltInMacros.ALL).toString(), true)
        body.addView(count)
        val quick = LinearLayout(c)
        listOf(2 to "2 tướng", 11 to "11 tướng", BuiltInMacros.ALL to "Tất cả").forEach { (n, text) ->
            quick.addView(
                Ui.button(c, text) { count.setText(n.toString()) },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    marginEnd =
                        dp(4)
                }
            )
        }
        body.addView(quick)
        val estimate = Ui.text(c, "", 13f, Ui.ink, true).also { body.addView(it) }

        fun check(text: String, on: Boolean) = CheckBox(c).apply {
            this.text = text
            textSize = 14f
            isChecked = on
        }.also { body.addView(it) }
        // Việt Nam's top three and "Số 1 máy chủ" unless chosen otherwise last time; the others when chosen.
        val saved =
            uiPrefs.getString("boardWants", null)?.split(',')?.map { it.substringBefore(':') }?.toSet()
                ?: BoardScreens.DEFAULT.map { it.tab.slug }.toSet()
        body.addView(Ui.section(c, "Bảng cần chụp"))
        val order =
            listOf(
                BoardScreens.Tab.COUNTRY,
                BoardScreens.Tab.TOP,
                BoardScreens.Tab.SERVER,
                BoardScreens.Tab.PROVINCE,
                BoardScreens.Tab.AREA
            )
        val tabs = order.associateWith { t ->
            check(
                when (t) {
                    BoardScreens.Tab.COUNTRY -> "Việt Nam (quốc gia) · top 3"
                    BoardScreens.Tab.TOP -> "Số 1 máy chủ (người mạnh nhất mỗi miền)"
                    BoardScreens.Tab.SERVER -> "Toàn máy chủ · ít nhất top 10 (kéo 4 màn)"
                    BoardScreens.Tab.PROVINCE -> "Tỉnh (ví dụ Đắk Lắk) · top 3"
                    BoardScreens.Tab.AREA -> "Miền (ví dụ Miền Trung) · top 3"
                },
                t.slug in saved
            )
        }
        val deep = check("Kéo hết danh sách các bảng đã chọn (lâu hơn nhiều)", uiPrefs.getBoolean("boardDeep", false))
        body.addView(Ui.section(c, "Vào game"))
        val openGame =
            check(
                "Tự mở game và vào sảnh: đóng game còn chạy, đăng nhập Garena, bỏ qua màn quà và form đăng ký, đóng popup sự kiện",
                uiPrefs.getBoolean("boardOpenGame", false)
            )
        val checkAccount = check("Kiểm tra UID · chọn tài khoản khi bấm Chạy", uiPrefs.getBoolean("checkAccount", false))
        accountHelp(c, body)
        val traceEntry = check("Lưu ảnh từng bước vào game để xem lại (menu ⋮ của lượt)", uiPrefs.getBoolean("traceEntry", true))

        fun follow() {
            if (!openGame.isChecked) checkAccount.isChecked = false
            traceEntry.isEnabled = openGame.isChecked
        }
        checkAccount.setOnCheckedChangeListener { _, on -> if (on) openGame.isChecked = true }

        fun chosen() = order.filter { tabs.getValue(it).isChecked }.map {
            BoardScreens.Want(
                it,
                if (deep.isChecked &&
                    it != BoardScreens.Tab.TOP
                ) {
                    BoardScreens.ALL
                } else {
                    BoardScreens.defaultTop(it)
                }
            )
        }

        fun updateEstimate() {
            val asked = count.text.toString().toIntOrNull()?.coerceIn(0, BuiltInMacros.ALL) ?: 0
            val heroes = if (asked >= BuiltInMacros.ALL) 130 else asked
            val n = chosen().size
            if (n == 0 || asked == 0) {
                estimate.text = "Chọn ít nhất một bảng và một tướng."
                estimate.setTextColor(Ui.danger)
                return
            }
            // A hero: about 8 s to choose it, then 3 s a tab and 3.5 s a screen; a whole list about 12 screens.
            val pages = chosen().sumOf { if (it.pages >= BoardScreens.ALL) 12 else it.pages }
            val kept = RunTimes.get(uiPrefs, boardKey(chosen()))
            val seconds = heroes * (kept ?: (8.0 + n * 3.0 + pages * 3.5)) + (
                if (openGame.isChecked) {
                    RunTimes.get(
                        uiPrefs,
                        RunTimes.ENTRY
                    ) ?: 60.0
                } else {
                    20.0
                }
                )
            val photos = heroes * pages
            estimate.text =
                "Ước tính: " + (if (asked >= BuiltInMacros.ALL) "khoảng " else "") +
                "$heroes tướng · khoảng $photos ảnh · khoảng ${Format.duration(seconds.toLong())}" +
                (if (kept != null) " (theo lượt chạy trọn trước)" else "") +
                "\nCần khoảng ${Format.size(photos * averageShot())} · máy còn trống ${Format.size(filesDir.usableSpace)}"
            estimate.setTextColor(Ui.ink)
        }
        follow()
        updateEstimate()
        openGame.setOnCheckedChangeListener { _, _ ->
            follow()
            updateEstimate()
        }
        (tabs.values + deep).forEach { it.setOnCheckedChangeListener { _, _ -> updateEstimate() } }
        count.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                updateEstimate()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        body.addView(
            Ui.text(
                c,
                "Không tick Tự mở game: mở sảnh game (hoặc Chiến khu) trước khi bấm Chạy. App không bao giờ bấm nút Đấu hạng trong trang Đấu hạng (nút đó bắt đầu tìm trận).",
                12f,
                Ui.muted
            )
        )
        val dialog = AlertDialog.Builder(c).setTitle("Chiến khu").setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Chọn tác vụ", null).setNegativeButton("Hủy", null).create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val heroes = count.text.toString().toIntOrNull() ?: 0
            if (heroes !in
                1..BuiltInMacros.ALL
            ) {
                count.error = "Nhập số từ 1 đến ${BuiltInMacros.ALL}; ${BuiltInMacros.ALL} là lấy hết"
                return@setOnClickListener
            }
            val wanted = chosen()
            if (wanted.isEmpty()) {
                status("Chọn ít nhất một bảng")
                return@setOnClickListener
            }
            uiPrefs.edit().putInt("boardCount", heroes).putString(
                "boardWants",
                wanted.joinToString(",") {
                    "${it.tab.slug}:${it.top}"
                }
            ).putBoolean("boardDeep", deep.isChecked)
                .putBoolean(
                    "boardOpenGame",
                    openGame.isChecked
                ).putBoolean("checkAccount", checkAccount.isChecked).putBoolean("traceEntry", traceEntry.isChecked).apply()
            if (selectMacro(
                    BuiltInMacros.board(
                        openGame.isChecked,
                        checkAccount.isChecked && checkAccount.isEnabled,
                        traceEntry.isChecked,
                        heroes,
                        wanted
                    )
                )
            ) {
                dialog.dismiss()
            }
        }
    }

    /**
     * Cờ Liên Quân: user chooses the left categories and the right tabs independently.
     * Detail expansion and scrolling are separate options; none of the twelve tabs is forced on.
     */
    private fun chessForm() {
        val c = themed()
        val body = Ui.stack(c).apply { setPadding(dp(20), dp(6), dp(20), dp(8)) }
        body.addView(
            Ui.text(
                c,
                "Bách khoa Cờ Liên Quân: từ sảnh → Chọn chế độ → Chế độ giải trí → Cờ Liên Quân → Bắt đầu → Trang chủ. Chọn CHÍNH XÁC các mục cần lấy; app không tự thu thập các mục không tick.",
                14f
            )
        )
        val saved = uiPrefs.getString("chessTopics", "")?.split(',')?.toSet().orEmpty()
        val checks = linkedMapOf<String, CheckBox>()
        var section = ""
        for (topic in ChessWiki.TOPICS) {
            if (topic.section != section) {
                section = topic.section
                body.addView(Ui.section(c, topic.side))
            }
            val box = CheckBox(c).apply {
                text = topic.label
                textSize = 14f
                isChecked = topic.key in saved
            }
            checks[topic.key] = box
            body.addView(box)
        }
        val quick = LinearLayout(c)
        quick.addView(
            Ui.button(c, "Chọn tất cả") { checks.values.forEach { it.isChecked = true } },
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )
        quick.addView(
            Ui.button(c, "Bỏ chọn tất cả") { checks.values.forEach { it.isChecked = false } },
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )
        body.addView(quick)

        fun check(text: String, on: Boolean) = CheckBox(c).apply {
            this.text = text
            textSize = 14f
            isChecked = on
        }.also { body.addView(it) }
        body.addView(Ui.section(c, "Thu thập nội dung"))
        val fast = check(
            "Chạy nhanh: tốc độ như Lấy thông tin tướng · Chạy nhanh",
            uiPrefs.getBoolean("chessFast", true)
        )
        val heroDetails = check(
            "Mở từng quân cờ trong Tất cả quân cờ; chụp thuộc tính, kỹ năng và kéo chi tiết",
            uiPrefs.getBoolean("chessHeroDetails", false)
        )
        val synergyDetails = check(
            "Mở từng biểu tượng phe và nghề trong Tương tác",
            uiPrefs.getBoolean("chessSynergyDetails", false)
        )
        val equipmentDetails = check(
            "Mở từng trang bị và chụp bảng mô tả",
            uiPrefs.getBoolean("chessEquipmentDetails", false)
        )
        body.addView(Ui.section(c, "Tải ảnh gốc từ game (tùy chọn)"))
        body.addView(
            Ui.text(
                c,
                "Game lưu ảnh bằng Save Photo → Sure vào Pictures. App ghi số ảnh trước lượt, " +
                    "tự chép ảnh mới vào thư viện app sau Sure và phân loại theo mục đang chụp. " +
                    "Các ảnh cũ và ảnh không xác định sẽ không bị lấy.",
                12f,
                Ui.muted
            )
        )
        body.addView(
            Ui.text(
                c,
                if (GamePhotoImport.hasSource(this)) {
                    "Đã chọn nguồn: " + GamePhotoImport.sourceLabel(this)
                } else {
                    "Chưa chọn Pictures. Về app → Nhập file → Ảnh gốc trong Pictures để cấp quyền trước."
                },
                12f,
                if (GamePhotoImport.hasSource(this)) Ui.success else Ui.warning
            )
        )
        val savedNative = uiPrefs.getString("chessNativeGroups", "").orEmpty().split(',').toSet()
        val nativeChecks = linkedMapOf<String, CheckBox>()
        GamePhotoImport.GROUPS.filter { it.key != "chua-phan-loai" }.forEach { group ->
            nativeChecks[group.key] = check("Tải " + group.title, group.key in savedNative)
        }
        body.addView(
            Ui.text(
                c,
                "Các trang và bảng chi tiết được tự động kéo, chụp đến cuối khi còn nội dung. Không cần bật tùy chọn riêng.",
                12f,
                Ui.muted
            )
        )
        body.addView(Ui.section(c, "Bắt đầu và an toàn"))
        val openGame = check(
            "Tự mở game tới sảnh trước khi vào Cờ Liên Quân",
            uiPrefs.getBoolean("chessOpenGame", true)
        )
        val checkAccount = check("Kiểm tra UID · chọn tài khoản khi bấm Chạy", uiPrefs.getBoolean("chessCheckAccount", false))
        accountHelp(c, body)
        val trace = check("Lưu ảnh các bước đăng nhập / vào sảnh", uiPrefs.getBoolean("chessTraceEntry", true))

        fun update() {
            if (!openGame.isChecked) checkAccount.isChecked = false
            trace.isEnabled = openGame.isChecked
        }
        checkAccount.setOnCheckedChangeListener { _, on -> if (on) openGame.isChecked = true }
        openGame.setOnCheckedChangeListener { _, _ -> update() }
        update()
        body.addView(
            Ui.text(
                c,
                "Nếu bỏ Tự mở game, hãy mở sẵn sảnh Cờ Liên Quân hoặc Bách khoa trước khi bấm Chạy. " +
                    "Lần đầu nên thử Quân cờ → Tất cả quân cờ với 1 mục trước khi chọn toàn bộ. " +
                    "Mọi ảnh nằm trong các thư mục mục bên trái / tab bên phải / từng đối tượng. Khi trang không cuộn thêm, app chuyển sang mục tiếp theo.",
                12f,
                Ui.muted
            )
        )
        val dialog = AlertDialog.Builder(c).setTitle("Thu thập Cờ Liên Quân")
            .setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Chọn tác vụ", null).setNegativeButton("Hủy", null).create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val native = nativeChecks.filterValues { it.isChecked }.keys.toSet()
            if (native.isNotEmpty() && !GamePhotoImport.hasSource(this)) {
                status("Chọn Pictures trong app trước khi tải ảnh gốc")
                return@setOnClickListener
            }
            val selected = checks.filterValues { it.isChecked }.keys.toSet() + ChessWiki.topicsForNative(native)
            if (selected.isEmpty()) {
                status("Chọn ít nhất một mục Bách khoa hoặc nhóm ảnh gốc")
                return@setOnClickListener
            }
            val options = ChessWiki.Options(
                selected, openGame.isChecked, checkAccount.isChecked && checkAccount.isEnabled,
                trace.isChecked, heroDetails.isChecked, synergyDetails.isChecked, equipmentDetails.isChecked, native, fast.isChecked
            )
            uiPrefs.edit().putString("chessTopics", selected.joinToString(","))
                .putString("chessNativeGroups", native.joinToString(","))
                .putBoolean("chessHeroDetails", heroDetails.isChecked)
                .putBoolean("chessFast", fast.isChecked)
                .putBoolean("chessSynergyDetails", synergyDetails.isChecked).putBoolean("chessEquipmentDetails", equipmentDetails.isChecked)
                .putBoolean("chessOpenGame", openGame.isChecked).putBoolean("chessCheckAccount", checkAccount.isChecked)
                .putBoolean("chessTraceEntry", trace.isChecked).apply()
            if (selectMacro(BuiltInMacros.chess(options))) dialog.dismiss()
        }
    }

    /**
     * Adapter to Android's screenshot/gesture APIs. Each image is written before the reader advances.
     * The progress ledger lets a paused/restarted step revisit the encyclopedia without resaving finished topics.
     */
    private fun chess(alive: () -> Boolean, finish: (ChessWikiReader.Outcome) -> Unit) {
        val stamp = "%04d".format(index + 1)
        val ledger = File(filesDir, "captures/" + runId + "/chess-progress.json")
        val progressState = try {
            JSONObject(ledger.readText())
        } catch (_: Exception) {
            JSONObject()
        }
        val doneIds = progressState.optJSONArray("finished").let { array ->
            (0 until (array?.length() ?: 0)).map { ChessWiki.heroIdentity(array!!.getString(it)) }.toMutableSet()
        }
        val detailPages = progressState.optJSONObject("detailPages") ?: JSONObject()

        fun persistProgress(): Boolean = try {
            ledger.parentFile?.mkdirs()
            val part = File(ledger.parentFile, ledger.name + ".part")
            part.writeText(
                progressState.put("finished", org.json.JSONArray(doneIds.sorted()))
                    .put("detailPages", detailPages).toString()
            )
            require(part.renameTo(ledger)) { "Không thay được file tiến độ" }
            true
        } catch (e: Exception) {
            CrashLog.record(this@CollectorService, "Không ghi được tiến độ Bách khoa", e)
            false
        }
        val options = BuiltInMacros.chessOptions(readMacro().json)
        val timing = ChessWiki.Timing(options.fast)
        val screen = object : ChessWikiReader.Screen {
            override val alive get() = alive()
            override val completedHeroes get() = doneIds.count { it.startsWith("item:tat-ca-quan-co:hero-") }
            override val completedHeroSlots get() = ChessWiki.heroSlots(doneIds)

            override fun look(words: Boolean, then: (ChessWikiReader.Look?) -> Unit) {
                if (filesDir.usableSpace < 30L * 1024 * 1024) {
                    note("Không đủ dung lượng trống")
                    return then(null)
                }
                grab(anyApp = true) { b ->
                    if (b == null) return@grab then(null)
                    val nodes = if (words) screenWords() else emptyList()
                    try {
                        io.execute {
                            var itemTitle: String? = null
                            val data: Triple<List<GameScreens.Word>, IntArray, Boolean>? = try {
                                val modal = ChessWiki.modal(b)
                                val readWords = if (words) nodes + GameText.words(b) else nodes
                                if (words && modal && (options.equipmentDetails || options.synergyDetails)) {
                                    itemTitle = ChessPopupTitle.select(readWords) { ChessPopupTitle.whiteHeading(b, it) }
                                        ?: GameText.chessItemTitle(b)
                                }
                                Triple(readWords, ChessWiki.signature(b, modal), modal)
                            } catch (e: Throwable) {
                                CrashLog.record(this@CollectorService, "Không đọc được màn hình Bách khoa", e)
                                null
                            }
                            handler.post {
                                if (!alive()) {
                                    b.recycle()
                                    then(null)
                                } else if (data == null) {
                                    b.recycle()
                                    then(null)
                                } else {
                                    then(object : ChessWikiReader.Look {
                                        override val words = data.first
                                        override val itemTitle = itemTitle
                                        override val signature = data.second
                                        override val modal = data.third
                                        override val height = b.height
                                        override val heroProfile by lazy {
                                            if (!modal && ChessWiki.heroListVisible(this.words)) {
                                                HeroGrid.profile(b, ChessWiki.HERO_LAYOUT)
                                            } else {
                                                floatArrayOf()
                                            }
                                        }

                                        override fun hasHeroCard(x: Double, y: Double) = HeroGrid.hasCard(b.width, b.height, x, y) {
                                                yy,
                                                row
                                            ->
                                            b.getPixels(row, 0, b.width, 0, yy, b.width, 1)
                                        }

                                        override fun tiles(topic: ChessWiki.Topic) = ChessWiki.tiles(topic, b, this.words)

                                        override fun nativeTiles(topic: ChessWiki.Topic) = ChessWiki.nativeTiles(topic, b, this.words)

                                        override fun skillIcons() = ChessWiki.skillTiles(b)

                                        override fun keep(name: String, then: (Boolean) -> Unit) {
                                            store(b, runId, stamp + "-" + name, then)
                                        }

                                        override fun discard() {
                                            b.recycle()
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        b.recycle()
                        then(null)
                    }
                }
            }

            override fun readHeroTitle(then: (String?) -> Unit) {
                grab(anyApp = true) { shot ->
                    if (shot == null) return@grab then(null)
                    try {
                        io.execute {
                            val title = try {
                                GameText.chessHeroTitle(shot)
                            } catch (e: Throwable) {
                                CrashLog.record(this@CollectorService, "Lỗi đọc riêng tiêu đề tướng Cờ Liên Quân", e)
                                null
                            }
                            shot.recycle()
                            handler.post { then(title) }
                        }
                    } catch (e: Exception) {
                        shot.recycle()
                        then(null)
                    }
                }
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                touchGame(tapAt(x, y), true, then)
            }

            override fun dragHeroList(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                val start = if (d > 0) .92 else .24
                val distance = kotlin.math.abs(d).coerceAtMost(.70)
                val end = if (d > 0) start - distance else start + distance
                touchGame(drag(.76, start, end, if (fling) 160 else 700, if (fling) 0 else 450), false, then)
            }

            override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) {
                if (kotlin.math.abs(x - toX) < .003) {
                    touchGame(drag(x, y, toY, timing.move, timing.hold), false, then)
                } else {
                    val b = screen()
                    val path = Path().apply {
                        moveTo((x * (b.width() - 1)).toFloat(), (y * (b.height() - 1)).toFloat())
                        lineTo((toX * (b.width() - 1)).toFloat(), (toY * (b.height() - 1)).toFloat())
                    }
                    val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, timing.move)).build()
                    touchGame(gesture, false, then)
                }
            }

            /**
             * "Save Photo" is a game dialog, NOT our screenshot capture. Never tap Sure without
             * recognizing Save Photo, Sure and Cancel together from a fresh screenshot.
             */
            override fun nativeSave(
                group: String,
                item: String,
                x: Double,
                y: Double,
                preserveDetail: Boolean,
                aliases: Set<String>,
                then: (NativeResult) -> Unit
            ) {
                if (!alive()) return
                val saveRunId = runId
                val earlyReady = NativeSaveReadiness.enabled(group, preserveDetail, options.fast)

                fun currentRun() = alive() && runId == saveRunId

                fun startNativeSave() {
                    if (!currentRun()) return
                    val point = Path().apply {
                        val b = screen()
                        moveTo((x * (b.width() - 1)).toFloat(), (y * (b.height() - 1)).toFloat())
                    }
                    val hold = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(point, 0, 850L)).build()
                    // One short transaction, rather than flashing the panels after each gesture/scan.
                    // The normal screenshot and tap leases still apply, including their settling delays.
                    val transaction = captureShield.acquire()
                    var ended = false
                    var timeout: Runnable? = null

                    fun complete(result: NativeResult) {
                        if (ended) return
                        ended = true
                        timeout?.let { handler.removeCallbacks(it) }
                        captureShield.release(transaction)
                        // Consecutive skill saves otherwise hide again in the same frame. Give Stop/Pause
                        // a real visible, touchable interval between individual native images.
                        if (currentRun()) handler.postDelayed({ if (currentRun()) then(result) }, 400L)
                    }

                    fun active(): Boolean {
                        if (ended) return false
                        if (!currentRun() || !captureShield.contains(transaction)) {
                            complete(NativeResult.FAILED)
                            return false
                        }
                        return true
                    }
                    // A stalled OCR callback must not hide Stop/Pause for the whole chess run.
                    timeout = Runnable {
                        if (active()) {
                            pause("Lưu ảnh gốc quá 15 giây. Kiểm tra Save Photo rồi bấm Tiếp tục.")
                            complete(NativeResult.FAILED)
                        }
                    }
                    handler.postDelayed(timeout!!, 15_000L)

                    fun later(ms: Long, action: () -> Unit) {
                        handler.postDelayed({ if (active()) action() }, ms)
                    }

                    fun tapNative(x: Double, y: Double, done: (Boolean) -> Unit) {
                        if (active()) touchGame(tapAt(x, y), true) { if (active()) done(it) }
                    }

                    fun readPrompt(done: (Pair<List<GameScreens.Word>, NativeSaveEvidence.Scene>?) -> Unit) {
                        if (!active()) return
                        grab(anyApp = true) { image ->
                            if (!active()) {
                                image?.recycle()
                                return@grab
                            }
                            if (image == null) return@grab done(null)
                            try {
                                io.execute {
                                    val evidence = try {
                                        val words = GameText.words(image)
                                        val plain = GameScreens.plain(words.joinToString(" ") { it.text })
                                        val modal = ChessWiki.modal(image)
                                        var detailVerified = false
                                        if (modal && !NativeSaveEvidence.hasPromptText(plain)) {
                                            val expected = item.substringBefore("-skill-")
                                            val title = ChessWiki.modalHeroTitle(words, if (preserveDetail) expected else null)
                                            detailVerified = title != null &&
                                                (
                                                    !preserveDetail ||
                                                        ChessWiki.safeName(title) == ChessWiki.safeName(expected)
                                                    )
                                            if (preserveDetail && !detailVerified) {
                                                val precise = GameText.chessHeroTitle(image)
                                                detailVerified = precise != null &&
                                                    ChessWiki.safeName(precise) == ChessWiki.safeName(expected)
                                            }
                                        }
                                        words to NativeSaveEvidence.scene(plain, modal, detailVerified)
                                    } catch (e: Throwable) {
                                        CrashLog.record(this@CollectorService, "Không đọc được Save Photo", e)
                                        null
                                    } finally {
                                        image.recycle()
                                    }
                                    handler.post {
                                        if (active()) done(evidence)
                                    }
                                }
                            } catch (e: Exception) {
                                image.recycle()
                                done(null)
                            }
                        }
                    }

                    fun settledPrompt(
                        waitMs: Long,
                        ready: (Pair<List<GameScreens.Word>, NativeSaveEvidence.Scene>) -> Boolean,
                        done: (List<GameScreens.Word>, NativeSaveEvidence.Scene) -> Unit
                    ) {
                        NativeSaveReadiness.read(
                            earlyReady,
                            waitMs,
                            SystemClock::uptimeMillis,
                            ::active,
                            ::later,
                            ::readPrompt,
                            ready
                        ) { evidence ->
                            if (evidence == null) {
                                complete(NativeResult.FAILED)
                            } else {
                                done(evidence.first, evidence.second)
                            }
                        }
                    }
                    touchGame(hold, false) { pushed ->
                        if (!active()) return@touchGame
                        if (!pushed) return@touchGame complete(NativeResult.FAILED)
                        settledPrompt(1000, { ChessWiki.nativeConfirm(it.first) != null }) { words, scene ->
                            val answer = ChessWiki.nativeConfirm(words)
                            val cancel = ChessWiki.nativeCancel(words)
                            if (answer == null) {
                                when {
                                    cancel != null -> {
                                        note("Save Photo thiếu nút Sure: bấm CANCEL, không tải nhầm.")
                                        tapNative(cancel.first, cancel.second) { complete(NativeResult.FAILED) }
                                    }
                                    scene == NativeSaveEvidence.Scene.DETAIL && preserveDetail ->
                                        complete(NativeResult.ABSENT)
                                    scene == NativeSaveEvidence.Scene.DETAIL -> {
                                        // This portrait long-press opened a detail drawer instead of Save Photo.
                                        // Do not call it absent until the close is verified on a fresh page scan.
                                        tapNative(.902, .082) { closed ->
                                            if (!closed) {
                                                complete(NativeResult.FAILED)
                                            } else {
                                                later(timing.close) {
                                                    readPrompt { evidence ->
                                                        complete(
                                                            if (evidence?.second == NativeSaveEvidence.Scene.PAGE) {
                                                                NativeResult.ABSENT
                                                            } else {
                                                                NativeResult.FAILED
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    scene == NativeSaveEvidence.Scene.PAGE && !preserveDetail ->
                                        complete(NativeResult.ABSENT)
                                    else -> complete(NativeResult.FAILED)
                                }
                            } else {
                                tapNative(answer.first, answer.second) { pressed ->
                                    if (!pressed) return@tapNative complete(NativeResult.FAILED)
                                    settledPrompt(1150, { it.second == NativeSaveEvidence.Scene.PAGE }) { confirmWords, afterSave ->
                                        if (afterSave == NativeSaveEvidence.Scene.PROMPT) {
                                            val cancelPrompt = ChessWiki.nativeCancel(confirmWords)
                                            if (cancelPrompt != null) {
                                                note("Save Photo vẫn mở sau Sure; đóng CANCEL và bỏ qua.")
                                                tapNative(cancelPrompt.first, cancelPrompt.second) { complete(NativeResult.FAILED) }
                                            } else {
                                                complete(NativeResult.FAILED)
                                            }
                                        } else if (afterSave == NativeSaveEvidence.Scene.UNKNOWN ||
                                            (preserveDetail && afterSave != NativeSaveEvidence.Scene.DETAIL)
                                        ) {
                                            complete(NativeResult.FAILED)
                                        } else {
                                            try {
                                                io.execute {
                                                    val marked = try {
                                                        GamePhotoSession.mark(this@CollectorService, saveRunId, group, item, aliases)
                                                        GamePhotoSync.request(this@CollectorService)
                                                        true
                                                    } catch (e: Throwable) {
                                                        CrashLog.record(this@CollectorService, "Không ghi được ảnh gốc đã lưu", e)
                                                        false
                                                    }
                                                    handler.post {
                                                        if (active()) complete(if (marked) NativeResult.SAVED else NativeResult.FAILED)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                complete(NativeResult.FAILED)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // importNew can hold GamePhotoSession's lock while copying Pictures. Never wait
                // for that lock on the UI thread, and keep Stop/Pause visible during this lookup.
                try {
                    io.execute {
                        val saved = try {
                            GamePhotoSession.hasMark(this@CollectorService, saveRunId, group, item, aliases)
                        } catch (
                            e: Exception
                        ) {
                            CrashLog.record(this@CollectorService, "Không đọc được tiến độ ảnh gốc", e)
                            null
                        }
                        handler.post {
                            if (!currentRun()) return@post
                            when (saved) {
                                true -> then(NativeResult.SAVED)
                                false -> startNativeSave()
                                null -> then(NativeResult.FAILED)
                            }
                        }
                    }
                } catch (e: Exception) {
                    CrashLog.record(this@CollectorService, "Không đọc được tiến độ ảnh gốc", e)
                    if (currentRun()) then(NativeResult.FAILED)
                }
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }

            override fun seen(id: String) = id in doneIds

            override fun finished(id: String) {
                val replaced = if (id.startsWith("hero-slot-v2:")) {
                    val prefix = id.substringBeforeLast(':') + ":"
                    doneIds.removeAll { it != id && it.startsWith(prefix) }
                } else {
                    false
                }
                if (!doneIds.add(id) && !replaced) return
                persistProgress()
            }

            override fun detailSignature(id: String): IntArray? = detailPages.optJSONArray(id)?.let { values ->
                IntArray(values.length()) { values.optInt(it) }
            }

            override fun finishDetail(id: String, signature: IntArray): Boolean {
                val old = detailPages.optJSONArray(id)
                val added = doneIds.add(id)
                detailPages.put(id, org.json.JSONArray(signature.toList()))
                if (persistProgress()) return true
                if (added) doneIds.remove(id)
                if (old == null) detailPages.remove(id) else detailPages.put(id, old)
                return false
            }

            override fun note(message: String) {
                this@CollectorService.note(message)
            }

            override fun say(message: String) {
                if (alive()) {
                    lastStatus = message
                    refreshPanel()
                }
            }
        }
        if (options.nativeGroups.isEmpty()) {
            ChessWikiReader(screen, options, finish).start()
        } else {
            try {
                io.execute {
                    val baseline = try {
                        GamePhotoSession.begin(this@CollectorService, runId)
                    } catch (
                        e: Throwable
                    ) {
                        CrashLog.record(this@CollectorService, "Không đếm được ảnh Pictures trước lượt", e)
                        -1
                    }
                    handler.post {
                        if (!alive()) return@post
                        if (baseline < 0) {
                            finish(
                                ChessWikiReader.Outcome.Failed(
                                    "Không đọc được Pictures. Về app → Nhập file → Ảnh gốc trong Pictures, cấp quyền rồi thử lại."
                                )
                            )
                        } else {
                            note("Ảnh Pictures trước lượt: " + baseline + " · nhóm ảnh gốc: " + options.nativeGroups.joinToString(","))
                            ChessWikiReader(screen, options, finish).start()
                        }
                    }
                }
            } catch (e: Exception) {
                finish(ChessWikiReader.Outcome.Failed("Không khởi tạo được kiểm tra ảnh Pictures: " + e.message))
            }
        }
    }

    /** Mean size of the screenshots saved on this phone, which depends on its screen and the game's art; 1.5 MB before any. */
    private fun averageShot(): Long {
        val sizes = File(filesDir, "captures").walkTopDown().filter {
            it.isFile && it.extension == "png"
        }.take(300).map { it.length() }.toList()
        return if (sizes.size >= 5) sizes.average().toLong() else 1_500_000L
    }

    private fun savedPicker() {
        val entries = MacroStore.saved(this).mapNotNull { f ->
            try {
                Macro(f.readText())
            } catch (e: Exception) {
                null
            }
        }
        if (entries.isEmpty()) {
            status("Chưa có macro. Vào Cài đặt → Nâng cao để nhập.")
            return
        }
        display(
            AlertDialog.Builder(themed()).setTitle("Macro đã nhập").setItems(
                entries.map {
                    it.name
                }.toTypedArray()
            ) { _, i -> selectMacro(entries[i].raw) }.setNegativeButton("Hủy", null).create()
        )
    }

    fun selectMacro(raw: String): Boolean {
        if (libraryBusy) {
            status("Đang lưu dữ liệu; vui lòng chờ")
            return false
        }
        if (isWorking) {
            status("Chờ thao tác hiện tại hoàn tất")
            return false
        }
        try {
            val m = Macro(raw)
            val previous = macro
            if (runId.isNotBlank() && previous != null && index < previous.steps.length()) {
                stopped = true
                save()
            }
            reset()
            MacroStore.current(this).writeText(raw)
            selectionCache = null
            status(MacroStore.instruction(m))
            return true
        } catch (e: Exception) {
            status(e.message ?: "Không mở được tác vụ")
            return false
        }
    }

    private fun showOptions() {
        if (isWorking) {
            status("Tạm dừng trước khi mở tùy chọn")
            return
        }
        display(
            AlertDialog.Builder(themed()).setTitle("Tùy chọn")
                .setItems(arrayOf("Tạo lượt mới với tác vụ này", "Bỏ một thao tác…", "Cách dùng các nút", "Đóng bảng nổi")) { _, which ->
                    handler.post {
                        when (which) {
                            0 -> reset()
                            1 -> skipDialog()
                            2 -> display(
                                AlertDialog.Builder(themed()).setTitle("Điều khiển")
                                    .setMessage(
                                        "Tạm dừng: nghỉ ngay. Bước vào game hay lấy chiêu đang dở sẽ làm lại từ đầu bước đó khi Tiếp tục; thao tác ngắn khác chạy nốt rồi nghỉ.\n\nKết thúc: giữ ảnh và đóng lượt. Chọn Tạo lượt mới để chạy lại.\n\nDấu −: thu gọn bảng, tác vụ vẫn chạy. Chạm nút AOV để mở lại.\n\nBỏ một thao tác là tùy chọn nâng cao, không phải bỏ cả tướng."
                                    ).setPositiveButton("Đã hiểu", null).create()
                            )
                            3 -> {
                                pause("Đã đóng bảng")
                                hidePanel()
                            }
                        }
                    }
                }.setNegativeButton("Đóng", null).create()
        )
    }

    private fun skipDialog() {
        val m = macro
        if (isWorking || stopped || m == null || index >= m.steps.length()) {
            status("Chỉ dùng khi đang tạm dừng một lượt")
            return
        }
        display(
            AlertDialog.Builder(themed()).setTitle("Bỏ thao tác ${index + 1}?")
                .setMessage("Chỉ bỏ một lần chạm, chờ hoặc chụp. Dùng khi bạn đã thực hiện thao tác này bằng tay.")
                .setPositiveButton("Bỏ thao tác") { _, _ ->
                    if (!isWorking && !stopped) {
                        index++
                        save()
                        status("Đã bỏ thao tác. Bấm Tiếp tục.")
                    }
                }
                .setNegativeButton("Hủy", null).create()
        )
    }

    private fun hidePanel() {
        captureProgress.close()
        dismissMacroPicker()
        activeDialog?.dismiss()
        panel?.let { detach(it) }
        panel = null
        label =
            null
        primary = null
        stopView = null
        choose = null
        progress = null
        bubble = null
        uiPrefs.edit().putBoolean("panel", false).apply()
    }

    private fun status(s: String) {
        lastStatus = s
        refreshPanel()
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun ready(m: Macro): Boolean {
        val p = foreground()
        if (p != null && p != m.target) {
            pause("Hãy mở Liên Quân trước (đang mở ${appName(p)})")
            return false
        }
        val landscape = screen().let { it.width() > it.height() }
        if (landscape != (m.orientation == "landscape")) {
            pause("Xoay ngang điện thoại để tiếp tục")
            return false
        }
        return true
    }

    private fun readMacro() = Macro(File(filesDir, "macro.json").readText())

    fun start() = startWithAccount(null)

    private fun startWithAccount(pickedAccount: String?) {
        if (afterRunChat != null) {
            status("Đang gửi câu lệnh sau lượt trong ChatGPT")
            return
        }
        if (libraryBusy) {
            status("Đang lưu dữ liệu; vui lòng chờ")
            return
        }
        if (running || busy || manualBusy || activeDialog != null || macroPicker != null) return
        upgradeBuiltIn()
        try {
            val m = readMacro()
            val same = prefs.getString("hash", "") == m.hash
            val at = if (same) prefs.getInt("step", 0) else 0
            val existing = same && !prefs.getString("run", "").isNullOrBlank()
            val checking = m.json.optBoolean("checkAccount")
            if (same && prefs.getBoolean("stopped", false)) {
                status("Lượt đã kết thúc. Bấm Tạo lượt mới.")
                return
            }
            if (at >= m.steps.length()) {
                status("Đã hoàn tất. Bấm Tạo lượt mới.")
                return
            }
            val pinned = if (existing) prefs.getString("accountId", null) ?: pickedAccount else pickedAccount
            var selectedUid: String? = null
            if (checking) {
                if (!m.json.optBoolean("openGame")) {
                    status("Bật Tự mở game để kiểm tra tài khoản trước khi thu thập.")
                    return
                }
                if (existing && pinned == null && at > 0) {
                    display(
                        AlertDialog.Builder(themed()).setTitle("Chọn tài khoản cho lượt mới")
                            .setMessage(
                                "Lượt cũ chưa ghi tài khoản đã chọn. Tạo lượt mới để kiểm tra đúng UID trước khi chạy; ảnh của lượt cũ được giữ lại."
                            )
                            .setPositiveButton("Tạo lượt mới") { _, _ ->
                                reset()
                                handler.post { start() }
                            }.setNegativeButton("Hủy", null).create()
                    )
                    return
                }
                if (pinned == null) {
                    chooseRunAccount()
                    return
                }
                val account = Accounts.load(this, pinned)
                if (account ==
                    null
                ) {
                    status("Tài khoản của lượt đã bị xóa hoặc cần nhập lại mật khẩu. Vào Quản lý tài khoản để sửa.")
                    return
                }
                val oldUid = if (existing) prefs.getString("accountUid", null) else null
                if (oldUid != null &&
                    oldUid != account.uid
                ) {
                    status("UID tài khoản của lượt đã thay đổi. Bấm Dừng → Tạo lượt mới để chọn lại.")
                    return
                }
                selectedUid = account.uid
            }
            if (!entryStep(m, at) && !ready(m)) return
            macro = m
            stopped = prefs.getString("hash", "") == m.hash && prefs.getBoolean("stopped", false)
            if (stopped) {
                status("Lượt đã kết thúc. Bấm Tạo lượt mới.")
                return
            }
            if (prefs.getString("hash", "") ==
                m.hash
            ) {
                index = prefs.getInt("step", 0)
                runId = prefs.getString("run", "") ?: ""
                entryMs = prefs.getLong("entryMs", 0L)
            } else {
                index =
                    0
                runId = ""
            }
            if (index >= m.steps.length()) {
                status("Đã hoàn tất. Bấm Tạo lượt mới.")
                return
            }
            runAccountId = if (checking) pinned else null
            runAccountUid = selectedUid
            if (runId.isEmpty()) {
                val mode = m.json.optString("collectionMode", "custom")
                val prefix = StorageNames.runPrefix(mode, m.json.optString("heroName", ""))
                runId = "$prefix-" + SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
                listRef = null
                listTotal = null
                // Kept times come only from runs that go through without a pause.
                entryMs = 0L
                prefs.edit().putBoolean("clean", true).putLong("entryMs", 0L).commit()
            }
            if (prefs.getString("hash", "") == m.hash) activeMs = prefs.getLong("activeMs", 0L) else activeMs = 0L
            setRunning(true)
            generation++
            save()
            note(if (index == 0) "Bắt đầu lượt" else "Tiếp tục")
            next(generation)
        } catch (e: Exception) {
            pause("Lỗi macro: ${e.message}")
        }
    }

    fun reset() {
        if (busy ||
            manualBusy
        ) {
            status("Chờ bước hiện tại xong")
            return
        }
        setRunning(false)
        activeMs = 0L
        listTotal = null
        generation++
        stopped =
            false
        index = 0
        runId = ""
        manualRunId = ""
        captureProgress.resetSession()
        runAccountId = null
        runAccountUid = null
        macro = null
        listRef =
            null
        prefs.edit().clear().commit()
        upgradeBuiltIn()
        status("Sẵn sàng lượt mới")
    }

    private fun exportCaptureHistory(entries: List<CaptureHistory.Entry>) {
        if (libraryBusy ||
            FileJob.running ||
            GamePhotoSync.busy ||
            afterRunChat != null
        ) {
            status("Đang xử lý dữ liệu; chờ xong rồi xuất ảnh")
            return
        }
        if (running) pause("Tạm dừng để xuất ảnh đã chọn")
        val token = generation

        fun open(attempt: Int) {
            if (token != generation || running) return
            if (busy || manualBusy || pendingSaves > 0) {
                if (attempt < 30) {
                    handler.postDelayed({ open(attempt + 1) }, 200)
                } else {
                    status("Thao tác chụp chưa xong; ảnh đã chọn được giữ lại")
                }
                return
            }
            try {
                val intent = CaptureActions.exportIntent(this, entries)
                hidePanel()
                startActivity(intent)
            } catch (e: Exception) {
                status(e.message ?: "Không xuất được ảnh đã chọn")
            }
        }
        open(0)
    }

    /**
     * A built-in macro selected before an app update keeps its old steps in macro.json. Regenerate it with the
     * same settings unless a run of it is still unfinished, so a new run always uses the current steps.
     */
    private fun upgradeBuiltIn() {
        if (!MacroStore.current(this).exists()) return // nothing selected yet on a fresh install
        try {
            val m = readMacro()
            val mode = m.json.optString("collectionMode")
            if (mode !in setOf("single", "all", "pick", "ranking", "board", "chess") ||
                m.json.optInt("builtinVersion", 1) == BuiltInMacros.VERSION
            ) {
                return
            }
            val step = if (prefs.getString("hash", "") == m.hash) prefs.getInt("step", 0) else 0
            if (step > 0 && step < m.steps.length() && !prefs.getBoolean("stopped", false)) return
            if (mode == "chess") {
                MacroStore.current(this).writeText(BuiltInMacros.chess(BuiltInMacros.chessOptions(m.json)))
            } else if (mode ==
                "board"
            ) {
                MacroStore.current(this).writeText(
                    BuiltInMacros.board(
                        m.json.optBoolean("openGame"),
                        m.json.optBoolean("checkAccount"),
                        m.json.optBoolean("traceEntry", true),
                        m.json.optInt("heroCount", BuiltInMacros.ALL),
                        BuiltInMacros.boardWants(m.json)
                    )
                )
            } else if (mode ==
                "pick"
            ) {
                MacroStore.current(this).writeText(
                    BuiltInMacros.named(
                        BuiltInMacros.heroNames(m.json),
                        fast = m.json.optBoolean("fast"),
                        detailOnly = m.json.optBoolean("detailOnly"),
                        directSkills = m.json.optBoolean("directSkills"),
                        openGame = m.json.optBoolean("openGame"),
                        checkAccount = m.json.optBoolean("checkAccount"),
                        traceEntry = m.json.optBoolean("traceEntry", true)
                    )
                )
            } else if (mode ==
                "ranking"
            ) {
                MacroStore.current(this).writeText(
                    BuiltInMacros.ranking(
                        m.json.optBoolean("openGame"),
                        m.json.optBoolean("checkAccount"),
                        m.json.optBoolean("traceEntry", true),
                        BuiltInMacros.rankCombos(m.json).map {
                            it.slug
                        }
                    )
                )
            } else {
                MacroStore.current(this).writeText(
                    BuiltInMacros.create(
                        m.json.optString("heroName", ""), m.json.optInt("heroCount", 1),
                        mode == "all",
                        m.json.optBoolean(
                            "fast"
                        ),
                        m.json.optBoolean(
                            "detailOnly"
                        ),
                        m.json.optBoolean(
                            "directSkills"
                        ),
                        m.json.optBoolean("openGame"), m.json.optBoolean("checkAccount"), m.json.optBoolean("traceEntry", true)
                    )
                )
            }
            selectionCache = null
        } catch (e: Exception) {
            CrashLog.record(this, "Không nâng cấp được macro", e)
        }
    }

    /**
     * Ends the step in progress at once when it can run for minutes: the way into the game and a hero's skills.
     * Other steps take a second or two and finish. A cut step starts over on Tiếp tục.
     */
    private var cancelStep: (() -> Unit)? = null

    private fun interrupt() {
        clearGestureDebug()
        cancelStep?.invoke()
        cancelStep = null
    }

    private fun pause(s: String) {
        val was = running
        interrupt()
        entering = false
        setRunning(false)
        if (was) prefs.edit().putBoolean("clean", false).apply()
        save()
        if (was) note("Tạm dừng: $s")
        status(s)
    }

    /** A run to end: going, paused part way, not ended yet. */
    private fun canStop(): Boolean {
        if (running || busy) return true
        val m = selected() ?: return false
        return prefs.getString("hash", "") == m.hash &&
            !prefs.getString("run", "").isNullOrEmpty() &&
            !prefs.getBoolean("stopped", false) &&
            prefs.getInt("step", 0) < m.steps.length()
    }

    private fun stop() {
        if (!canStop()) {
            status("Chưa có lượt nào đang chạy hay tạm dừng để dừng")
            return
        }
        interrupt()
        setRunning(false)
        stopped = true
        prefs.edit().putBoolean("clean", false).apply()
        save()
        note("Kết thúc lượt")
        status("Đã kết thúc lượt · ảnh đã được giữ lại")
    }

    private fun save() {
        val m = macro ?: return
        prefs.edit().putString("hash", m.hash).putInt("step", index).putString("run", runId).putString("accountId", runAccountId)
            .putString(
                "accountUid",
                runAccountUid
            ).putBoolean("stopped", stopped).putBoolean("running", running).putLong("activeMs", elapsed()).commit()
        if (runId.isNotEmpty()) {
            try {
                val dir = File(filesDir, "captures/$runId")
                dir.mkdirs()
                if (!File(dir, "macro.json").exists()) File(dir, "macro.json").writeText(m.raw)
                val metadata = File(dir, "run.json")
                val record = try {
                    JSONObject(metadata.readText())
                } catch (_: Exception) {
                    JSONObject()
                }
                if (!record.has("createdAt")) record.put("createdAt", System.currentTimeMillis())
                metadata.writeText(
                    record.put(
                        "collectionMode",
                        m.json.optString("collectionMode", "custom")
                    ).put("heroName", m.json.optString("heroName", "")).put(
                        "state",
                        if (stopped) {
                            "stopped"
                        } else if (index >=
                            m.steps.length()
                        ) {
                            "completed"
                        } else if (running) {
                            "running"
                        } else {
                            "paused"
                        }
                    ).put(
                        "macro",
                        m.name
                    ).put(
                        "hash",
                        m.hash
                    ).put(
                        "nextStep",
                        index
                    ).put("totalSteps", m.steps.length()).put("updatedAt", System.currentTimeMillis()).put("activeMs", elapsed())
                        // Taps that landed on the hidden panel and had to be resent; non-zero confirms the v0.3.1 failure mode on this device.
                        .put("panelTapRetries", record.optInt("panelTapRetries", 0) + unsavedRetries).toString(2)
                )
                unsavedRetries = 0
            } catch (e: Exception) {
                CrashLog.record(this, "Không ghi được run.json", e)
            }
        }
    }

    private fun next(token: Int, away: Int = 0) {
        val m = macro ?: return
        if (!running || token != generation) return
        if (index >=
            m.steps.length()
        ) {
            afterSaves {
                if (token == generation &&
                    running
                ) {
                    setRunning(false)
                    save()
                    note("Hoàn tất · ${clock(elapsed())}")
                    recordTimes(m, m.json.optInt("heroCount", 1))
                    status("Đã chụp xong · ${photosTaken(m)} ảnh · ${clock(elapsed())}")
                    AfterRun.finished(this, runId)
                }
            }
            return
        }
        val front = foreground()
        val opening = entryStep(m, index)
        if (!opening && front != null && front != m.target) {
            // A notification or game booster can hold focus for a moment; only stop when another app stays in front.
            if (away < 3) {
                handler.postDelayed({ next(token, away + 1) }, 700)
                return
            }
            pause("Liên Quân không còn ở phía trước (đang mở ${appName(front)})")
            return
        }
        if (!opening && !ready(m)) return
        lastStatus = progress(m)
        refreshPanel()
        failure = null
        val s = m.steps.getJSONObject(index)
        busy = true
        var finished = false

        fun done(ok: Boolean, reason: String? = null) {
            if (finished || token != generation) return
            finished = true
            busy = false
            entering = false
            cancelStep = null
            captureShield.reset()
            if (!ok) {
                pause(reason ?: failure ?: "Không thực hiện được thao tác ${index + 1}. Kiểm tra màn hình rồi tiếp tục.")
                return
            }
            index++
            save()
            // Next step at once: taps are followed by their own wait steps, and taps and screenshots hide the panel themselves.
            if (running) {
                handler.post { next(token) }
            } else {
                status(if (stopped) "Đã kết thúc lượt · ảnh được giữ lại" else "Đã tạm dừng · bấm Tiếp tục khi sẵn sàng")
            }
        }

        fun listEnded(collected: Int) {
            if (finished || token != generation) return
            finished = true
            busy = false
            cancelStep = null
            captureShield.reset()
            index = m.steps.length()
            setRunning(false)
            save()
            note("Hết danh sách · $collected tướng · ${clock(elapsed())}")
            recordTimes(m, collected)
            status("Đã hết danh sách · lấy xong $collected tướng · ${clock(elapsed())}")
            val completedRun = runId
            afterSaves { AfterRun.finished(this, completedRun) }
        }
        try {
            val type = s.getString("type")
            // A step that never reports back must not leave the controller stuck on "Đang xử lý".
            val limit = when (type) {
                "wait" -> s.getLong("ms") + 5000
                "screenshot" -> 30000L
                "swipe" -> s.optLong("ms", 500) + 10000
                "pick" -> 300000L
                "find" -> 900_000L
                "enter" ->
                    GameEntry.LIMIT_MS +
                        30000
                "skills" -> 300000L
                "ranking" -> 2_700_000L
                "board" -> 43_200_000L
                "chess" -> 43_200_000L
                else -> 10000L
            }
            handler.postDelayed({
                if (!finished &&
                    token == generation
                ) {
                    done(false, "Bước ${index + 1} ($type) quá ${limit / 1000} giây không xong. Kiểm tra màn hình rồi tiếp tục.")
                }
            }, limit)
            // Its callbacks see the step finished and stop; a tap already sent still lands.
            if (type in
                setOf("enter", "skills", "ranking", "board", "chess", "find")
            ) {
                cancelStep =
                    {
                        if (!finished) {
                            finished = true
                            busy = false
                            entering = false
                            captureShield.reset()
                            note("Ngắt bước ${index + 1} ($type) giữa chừng")
                        }
                    }
            }
            when (type) {
                "wait" -> handler.postDelayed({ done(true) }, s.getLong("ms"))
                "back" -> done(performGlobalAction(GLOBAL_ACTION_BACK))
                "enter" -> {
                    entering = true
                    enterGame(s.optString("to") == "lobby", { !finished && token == generation }) { outcome ->
                        when (outcome) {
                            is GameEntry.Outcome.Ready -> {
                                entryMs = elapsed()
                                prefs.edit().putLong("entryMs", entryMs).apply()
                                if (prefs.getBoolean("clean", false)) RunTimes.record(uiPrefs, RunTimes.ENTRY, entryMs / 1000.0)
                                done(true)
                            }
                            is GameEntry.Outcome.Failed -> done(false, outcome.reason)
                        }
                    }
                }
                // A failed screenshot is retried once; Android sometimes refuses one right after a tap.
                "screenshot" -> {
                    val name = "%04d-%s".format(index + 1, s.getString("name"))
                    capture(name, runId) { ok ->
                        if (ok) {
                            done(true)
                        } else {
                            handler.postDelayed({
                                if (!finished &&
                                    token == generation
                                ) {
                                    capture(name, runId) { done(it) }
                                }
                            }, 1000)
                        }
                    }
                }
                "board" -> {
                    entering = true
                    board(s, { !finished && token == generation }) { outcome ->
                        when (outcome) {
                            is BoardReader.Outcome.Done -> {
                                note("Chiến khu: ${outcome.heroes} tướng")
                                done(true)
                            }
                            is BoardReader.Outcome.Failed -> done(false, outcome.reason)
                        }
                    }
                }
                "chess" -> {
                    entering = true
                    chess({ !finished && token == generation }) { outcome ->
                        when (outcome) {
                            is ChessWikiReader.Outcome.Done -> {
                                note(
                                    "Bách khoa Cờ Liên Quân: " + outcome.pages + " ảnh, " + outcome.items + " mục chi tiết"
                                )
                                done(true)
                            }
                            is ChessWikiReader.Outcome.Failed -> done(false, outcome.reason)
                        }
                    }
                }
                // The web pages of Cộng đồng may belong to another app than the game.
                "ranking" -> {
                    entering = true
                    ranking(s, { !finished && token == generation }) { outcome ->
                        when (outcome) {
                            is RankingReader.Outcome.Done -> {
                                note("Thống kê tướng: ${outcome.pages} ảnh")
                                done(true)
                            }
                            is RankingReader.Outcome.Failed -> done(false, outcome.reason)
                        }
                    }
                }
                "skills" -> skills(s, { !finished && token == generation }) { outcome ->
                    when (outcome) {
                        is SkillReader.Outcome.Done -> {
                            note("Chiêu ${s.getString("prefix")}: ${outcome.summary}")
                            done(true)
                        }
                        is SkillReader.Outcome.Failed -> done(false, outcome.reason)
                    }
                }
                "pick" -> {
                    val card = s.getInt("index")
                    pick(card, "%04d".format(index + 1), { !finished && token == generation }) { outcome ->
                        when (outcome) {
                            is ListNavigator.Outcome.Tapped -> done(true)
                            is ListNavigator.Outcome.End -> listEnded(card)
                            is ListNavigator.Outcome.Missing -> done(false, "Không chạm được ô tướng ${card + 1}")
                            is ListNavigator.Outcome.Failed -> done(false, outcome.reason)
                        }
                    }
                }
                // A hero by name: where it was last seen, its name on the card checked, else looked for down the list.
                "find" -> find(s, { !finished && token == generation }) { outcome ->
                    when (outcome) {
                        is ListNavigator.Outcome.Tapped -> done(true)
                        is ListNavigator.Outcome.Failed -> done(false, outcome.reason)
                        else -> {
                            note("Không tìm thấy tướng ${s.getString("name")} trong danh sách; bỏ qua tướng này")
                            index = s.getInt("skipTo") - 1
                            done(true)
                        }
                    }
                }
                else -> {
                    val bounds = wm.currentWindowMetrics.bounds
                    val p = Path().apply {
                        moveTo((s.getDouble("x") * (bounds.width() - 1)).toFloat(), (s.getDouble("y") * (bounds.height() - 1)).toFloat())
                    }
                    val swipe = type == "swipe"
                    if (swipe) {
                        p.lineTo(
                            (s.getDouble("toX") * (bounds.width() - 1)).toFloat(),
                            (s.getDouble("toY") * (bounds.height() - 1)).toFloat()
                        )
                    }
                    val gesture = GestureDescription.Builder().addStroke(
                        GestureDescription.StrokeDescription(p, 0, if (swipe) s.optLong("ms", 500) else TAP_MS)
                    ).build()
                    touchGame(gesture, !swipe) { done(it) }
                }
            }
        } catch (
            e: Exception
        ) {
            CrashLog.record(this, "Lỗi bước ${index + 1}", e)
            finished = true
            busy = false
            cancelStep = null
            captureShield.reset()
            pause("Lỗi: ${e.message}")
        }
    }

    /**
     * Sends a gesture to the game with the panel out of the way. If the touch still lands on the
     * panel (its window update has not reached input yet) or Android cancels a tap, it is resent.
     * Gestures after the first continue its stroke, as in a drag that holds still before lifting.
     */
    internal fun touchGame(gesture: GestureDescription, tap: Boolean, result: (Boolean) -> Unit) = touchGame(listOf(gesture), tap, result)

    internal fun touchGame(gestures: List<GestureDescription>, tap: Boolean, result: (Boolean) -> Unit) =
        touchGameTarget(gestures, tap, null, null, result)

    private fun touchGameTarget(
        gestures: List<GestureDescription>,
        tap: Boolean,
        target: GameScreens.Word?,
        label: String?,
        result: (Boolean) -> Unit
    ) {
        val debugSession = debugGeneration
        val session = captureShield.acquire()
        var finished = false
        val done = { ok: Boolean ->
            if (!finished) {
                finished = true
                if (!ok) gestureDebug?.clear()
                captureShield.release(session)
                result(ok)
            }
        }

        fun send(attempt: Int) {
            if (current !== this || !captureShield.contains(session)) {
                done(false)
                return
            }
            swallowed = false
            if (debugSession == debugGeneration) gestureDebug?.show(gestures, tap, target, label)

            fun part(i: Int) {
                if (current !== this || !captureShield.contains(session)) {
                    done(false)
                    return
                }
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        if (!captureShield.contains(session)) {
                            done(false)
                            return
                        }
                        if (i > 0 || !swallowed) {
                            if (i + 1 < gestures.size) part(i + 1) else done(true)
                            return
                        }
                        swallowedTaps++
                        if (busy) unsavedRetries++
                        if (attempt <
                            GESTURE_RETRIES
                        ) {
                            handler.postDelayed({ send(attempt + 1) }, SHIELD_MS)
                        } else {
                            failure =
                                "Cú chạm rơi vào bảng nổi; kéo bảng sang chỗ khác rồi tiếp tục"
                            done(false)
                        }
                    }

                    // A cancelled swipe may have scrolled part way, so only taps are repeated.
                    override fun onCancelled(g: GestureDescription?) {
                        if (!captureShield.contains(session)) {
                            done(false)
                            return
                        }
                        if (tap &&
                            attempt < GESTURE_RETRIES
                        ) {
                            handler.postDelayed({ send(attempt + 1) }, SHIELD_MS)
                        } else {
                            failure =
                                "Android hủy thao tác chạm (có thể do chạm tay vào màn hình)"
                            done(false)
                        }
                    }
                }
                val sent = try {
                    dispatchGesture(gestures[i], callback, handler)
                } catch (
                    e: Exception
                ) {
                    CrashLog.record(this, "Không gửi được thao tác chạm", e)
                    false
                }
                if (!sent) {
                    failure = "Android không nhận thao tác chạm"
                    done(false)
                }
            }
            part(0)
        }
        handler.postDelayed({ send(0) }, SHIELD_MS)
    }

    private fun screen() = wm.currentWindowMetrics.bounds

    private fun tapAt(x: Double, y: Double): GestureDescription {
        val b = screen()
        return GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(
                Path().apply {
                    moveTo(
                        (x * (b.width() - 1)).toFloat(),
                        (
                            y *
                                (b.height() - 1)
                            ).toFloat()
                    )
                },
                0,
                TAP_MS
            )
        ).build()
    }

    /** A drag between two screen fractions; with [holdMs] the finger rests at the end so the list does not keep sliding. */
    private fun drag(x: Double, from: Double, to: Double, moveMs: Long, holdMs: Long): List<GestureDescription> {
        val b = screen()
        val px = (x * (b.width() - 1)).toFloat()
        val y0 = (from * (b.height() - 1)).toFloat()
        val y1 = (to * (b.height() - 1)).toFloat()
        val move = GestureDescription.StrokeDescription(
            Path().apply {
                moveTo(px, y0)
                lineTo(px, y1)
            },
            0,
            moveMs,
            holdMs > 0
        )
        if (holdMs <= 0) return listOf(GestureDescription.Builder().addStroke(move).build())
        val hold = move.continueStroke(Path().apply { moveTo(px, y1) }, 0, holdMs, false)
        return listOf(GestureDescription.Builder().addStroke(move).build(), GestureDescription.Builder().addStroke(hold).build())
    }

    private var listRef: FloatArray? = null

    /**
     * Finds the hero named in step [s] on the All-heroes list and taps it ([HeroFinder]): first the place it was
     * last seen ([HeroIndex]), its name read on the card; else down the list from the top, two rows a screen,
     * reading every card's name. The names read are kept in the index for the next time.
     */
    private fun find(s: JSONObject, alive: () -> Boolean, finish: (ListNavigator.Outcome) -> Unit) {
        val name = s.getString("name")
        val stamp = "%04d".format(index + 1)
        val asked = s.optInt("hint", -1)
        val hint = HeroIndex.find(HeroIndex.load(filesDir), name).minByOrNull { kotlin.math.abs(it.index - asked) }?.index ?: asked
        HeroSearch(
            name,
            hint,
            { card, approve, then -> pick(card, stamp, alive, approve, then) },
            seen = { cards -> io.execute { HeroIndex.seen(filesDir, cards.map { it.first to it.second.text }) } },
            note = { note(it) }
        ) { outcome, card ->
            if (outcome is ListNavigator.Outcome.Tapped && card != null) io.execute { HeroIndex.record(filesDir, card, name) }
            finish(outcome)
        }.start()
    }

    /** Runs a [ListNavigator] for card [card] against the real screen; its state survives in list.json. */
    private fun pick(
        card: Int,
        stamp: String,
        alive: () -> Boolean,
        approve: ListNavigator.Approve? = null,
        finish: (ListNavigator.Outcome) -> Unit
    ) {
        val file = File(filesDir, "captures/$runId/list.json")
        val saved = try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
        val state = ListNavigator.State(
            saved.optDouble("offset", 0.0),
            saved.optInt("page", 1),
            saved.optBoolean("synced", false),
            saved.optDouble("step", 1.0),
            listRef
        )
        if (listTotal == null) listTotal = saved.optInt("total", 0).takeIf { it > 0 }
        val screen = object : ListNavigator.Screen {
            override val alive get() = alive()

            override fun look(then: (ListNavigator.Shot?) -> Unit) {
                grab { b ->
                    if (b == null) return@grab then(null)
                    try {
                        io.execute {
                            val p = try {
                                if (HeroGrid.isGrid(b)) HeroGrid.profile(b) else FloatArray(0)
                            } catch (e: Throwable) {
                                null
                            }
                            val arrow = p?.isEmpty() == true &&
                                try {
                                    HeroGrid.hasBackArrow(b)
                                } catch (e: Throwable) {
                                    false
                                }
                            val total = if (listTotal == null && p?.isNotEmpty() == true) HeroNames.readListTotal(b) else null
                            handler.post {
                                if (total !=
                                    null
                                ) {
                                    listTotal = total
                                    uiPrefs.edit().putInt("heroTotal", total).apply()
                                }
                                if (p ==
                                    null
                                ) {
                                    b.recycle()
                                    then(null)
                                } else {
                                    then(object : ListNavigator.Shot {
                                        override val profile: FloatArray = p
                                        override val height = b.height
                                        override val backArrow = arrow

                                        override fun hasCard(x: Double, y: Double) = HeroGrid.hasCard(b, x, y)

                                        override fun done(keep: String?) {
                                            if (keep != null) store(b, runId, "$stamp-$keep") {} else b.recycle()
                                        }

                                        override fun words(then: (List<GameScreens.Word>) -> Unit) {
                                            try {
                                                io.execute {
                                                    val w = try {
                                                        GameText.words(b)
                                                    } catch (
                                                        e: Throwable
                                                    ) {
                                                        emptyList()
                                                    }
                                                    handler.post { then(w) }
                                                }
                                            } catch (e: Exception) {
                                                then(emptyList())
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        b.recycle()
                        then(null)
                    }
                }
            }

            override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                val from = if (d > 0) .88 else .22
                val to = from - d - (if (d > 0) .02 else -.02)
                touchGame(drag(.70, from, to.coerceIn(.02, .98), if (fling) 160 else 700, if (fling) 0 else 450), false, then)
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                touchGame(tapAt(x, y), true, then)
            }

            override fun back(then: (Boolean) -> Unit) {
                touchGame(tapAt(HeroGrid.BACK_X, HeroGrid.BACK_Y), true, then)
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }

            override fun note(text: String) {
                this@CollectorService.note(text)
            }
        }
        ListNavigator(card, state, screen, approve) { outcome ->
            listRef = state.ref
            try {
                file.parentFile?.mkdirs()
                file.writeText(
                    JSONObject().put(
                        "offset",
                        state.offset
                    ).put("page", state.page).put("synced", state.synced).put("step", state.step).put(
                        "total",
                        listTotal ?: 0
                    ).toString()
                )
            } catch (e: Exception) {
                CrashLog.record(this, "Không ghi được list.json", e)
            }
            finish(outcome)
        }.start()
    }

    /**
     * Runs a [SkillReader] on the hero page in front. Its photos share the step's number and follow it with their
     * order, as 0012-03-hero-001-skill-2-detail, so a hero's folder lists them as they were taken.
     */
    private fun skills(s: JSONObject, alive: () -> Boolean, finish: (SkillReader.Outcome) -> Unit) {
        val stamp = "%04d".format(index + 1)
        var order = 0
        val key = "$runId/$stamp"

        fun photo(n: String) = "$stamp-%02d-$n".format(++order)
        val screen = object : SkillReader.Screen {
            override val alive get() = alive()

            override fun look(then: (SkillReader.Look?) -> Unit) {
                grab { b ->
                    if (b == null) return@grab then(null)
                    try {
                        io.execute {
                            val read = try {
                                SkillRow.read(b.width, b.height) { y, line -> b.getPixels(line, 0, b.width, 0, y, b.width, 1) }
                            } catch (
                                e: Throwable
                            ) {
                                CrashLog.record(this@CollectorService, "Không đọc được hàng chiêu", e)
                                null
                            }
                            handler.post {
                                if (read == null) {
                                    b.recycle()
                                    then(null)
                                } else {
                                    then(object : SkillReader.Look {
                                        override val row: SkillRow.Row = read

                                        override fun done(keep: String?) {
                                            if (keep !=
                                                null
                                            ) {
                                                store(b, runId, photo("${s.getString("prefix")}-$keep")) {}
                                            } else {
                                                b.recycle()
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        b.recycle()
                        then(null)
                    }
                }
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                touchGame(tapAt(x, y), true, then)
            }

            override fun shot(name: String, then: (Boolean) -> Unit) {
                capture(photo(name), runId, done = then)
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }

            override fun note(text: String) {
                this@CollectorService.note(text)
            }

            override fun firstForm(row: SkillRow.Row) {
                skillForm = key to row
            }
        }
        val known = skillForm?.takeIf { it.first == key }?.second
        SkillReader(
            s.getString("prefix"),
            SkillReader.Options(s.optBoolean("detailOnly"), s.optBoolean("directSkills"), s.optBoolean("fast")),
            screen,
            known,
            finish
        ).start()
    }

    /**
     * Runs a [RankingReader]: from the lobby to the hero statistics, photographing both tables for each kind of
     * match. The words of each screen come from its accessibility tree (the web pages have one) and from ML Kit.
     */
    private fun ranking(s: JSONObject, alive: () -> Boolean, finish: (RankingReader.Outcome) -> Unit) {
        val stamp = "%04d".format(index + 1)
        val rankingSteps =
            macro?.steps?.let { steps -> (0 until steps.length()).count { steps.getJSONObject(it).optString("type") == "ranking" } } ?: 0
        val key = "$runId/$stamp"
        val done = rankingDone?.takeIf { it.first == key }?.second ?: HashSet<String>().also { rankingDone = key to it }
        val screen = object : RankingReader.Screen {
            override val alive get() = alive()

            override fun look(words: Boolean, then: (RankingReader.Look?) -> Unit) {
                if (filesDir.usableSpace < 30L * 1024 * 1024) {
                    note("Không đủ dung lượng trống để chụp")
                    return then(null)
                }
                grab(anyApp = true) { b ->
                    if (b == null) return@grab then(null)
                    val nodes = if (words) screenWords(visibleOnly = true) else emptyList()
                    try {
                        io.execute {
                            val read = try {
                                val rows = { y: Int, line: IntArray -> b.getPixels(line, 0, b.width, 0, y, b.width, 1) }
                                val lobby = words && GameScreens.read(b.width, b.height, rows).kind == GameScreens.Kind.LOBBY
                                Triple(
                                    if (words) GameText.rankingWords(b, nodes) else nodes,
                                    lobby,
                                    RankScreens.signature(b.width, b.height, rows)
                                )
                            } catch (e: Throwable) {
                                CrashLog.record(this@CollectorService, "Không đọc được màn hình thống kê", e)
                                null
                            }
                            handler.post {
                                if (read == null) {
                                    b.recycle()
                                    then(null)
                                } else {
                                    then(object : RankingReader.Look {
                                        override val words = read.first
                                        override val lobby = read.second
                                        override val signature = read.third
                                        override val menuOverlay by lazy {
                                            RankScreens.menuOverlay(b.width, b.height) { y, row ->
                                                b.getPixels(row, 0, b.width, 0, y, b.width, 1)
                                            }
                                        }
                                        private var consumed = false

                                        override fun done(keep: String?) {
                                            if (consumed) return
                                            consumed = true
                                            if (keep != null) store(b, runId, "$stamp-$keep") {} else b.recycle()
                                        }

                                        override fun keep(name: String, then: (Boolean) -> Unit) {
                                            if (consumed) return then(false)
                                            consumed = true
                                            store(b, runId, RankingCaptureFiles.screenshotBase("$stamp-$name", rankingSteps)) { ok ->
                                                if (!ok) note("Không lưu được ảnh thống kê: $name")
                                                refreshPanel()
                                                then(ok)
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        b.recycle()
                        then(null)
                    }
                }
            }

            override fun tap(word: GameScreens.Word, label: String, then: (Boolean) -> Unit) {
                touchGameTarget(listOf(tapAt(word.x, word.y)), true, word, label, then)
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                touchGame(tapAt(x, y), true, then)
            }

            override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                val from = if (d > 0) .88 else .22
                val to = (from - d).coerceIn(.05, .95)
                touchGame(drag(.55, from, to, if (fling) 160 else 700, if (fling) 0 else 400), false, then)
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }

            override fun note(text: String) {
                this@CollectorService.note(text)
            }

            override fun say(text: String) {
                if (alive()) {
                    lastStatus = text + (macro?.let { left(it) } ?: "")
                    refreshPanel()
                }
            }

            override fun finished(combo: String) {
                done.add(combo)
            }
        }
        RankingReader(screen, done.toSet(), BuiltInMacros.rankCombos(s), finish).start()
    }

    /**
     * Runs a [BoardReader]: from the lobby to "Chiến khu", then each hero's ranking for the tabs chosen. Words come
     * from ML Kit; the "Chọn tướng" grid is measured from its pixels while the screenshot is held.
     */
    private fun board(s: JSONObject, alive: () -> Boolean, finish: (BoardReader.Outcome) -> Unit) {
        val stamp = "%04d".format(index + 1)
        val key = "$runId/$stamp"
        val first = boardNext?.takeIf { it.first == key }?.second ?: 0
        val screen = object : BoardReader.Screen {
            override val alive get() = alive()

            override fun look(words: Boolean, grid: Boolean, then: (BoardReader.Look?) -> Unit) {
                if (filesDir.usableSpace < 30L * 1024 * 1024) {
                    note("Không đủ dung lượng trống để chụp")
                    return then(null)
                }
                grab(anyApp = true) { b ->
                    if (b == null) return@grab then(null)
                    val nodes = if (words) screenWords() else emptyList()
                    try {
                        io.execute {
                            val rows = { y: Int, line: IntArray -> b.getPixels(line, 0, b.width, 0, y, b.width, 1) }
                            var owned: BoardScreens.OwnedFilter? = null
                            val read: Pair<Triple<List<GameScreens.Word>, Boolean, IntArray>, List<Pair<Double, Double>>>? = try {
                                val lobby = words && GameScreens.read(b.width, b.height, rows).kind == GameScreens.Kind.LOBBY
                                val text = if (words) nodes + GameText.words(b) else nodes
                                owned = if (words) BoardScreens.ownedFilter(b.width, b.height, text, rows) else null
                                Triple(text, lobby, BoardScreens.signature(b.width, b.height, rows)) to
                                    (if (grid) BoardScreens.rows(b.width, b.height, rows) else emptyList())
                            } catch (e: Throwable) {
                                CrashLog.record(this@CollectorService, "Không đọc được màn hình Chiến khu", e)
                                null
                            }
                            handler.post {
                                if (read == null) {
                                    b.recycle()
                                    then(null)
                                } else {
                                    then(object : BoardReader.Look {
                                        override val words = read.first.first
                                        override val lobby = read.first.second
                                        override val signature = read.first.third
                                        override val rows = read.second
                                        override val ownedFilter = owned

                                        override fun card(col: Int, top: Double, bottom: Double) =
                                            BoardScreens.card(b.width, b.height, col, top, bottom, rows)

                                        override fun done(keep: String?) {
                                            if (keep != null) store(b, runId, "$stamp-$keep") {} else b.recycle()
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        b.recycle()
                        then(null)
                    }
                }
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                touchGame(tapAt(x, y), true, then)
            }

            override fun drag(x: Double, from: Double, to: Double, fling: Boolean, then: (Boolean) -> Unit) {
                touchGame(drag(x, from, to, if (fling) 160 else 700, if (fling) 0 else 400), false, then)
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }

            override fun note(text: String) {
                this@CollectorService.note(text)
            }

            override fun say(text: String) {
                if (alive()) {
                    lastStatus = text + (macro?.let { left(it) } ?: "")
                    refreshPanel()
                }
            }

            override fun hero(index: Int, name: String?) {
                boardNames["hero-%03d".format(index + 1)] = name
            }

            override fun finished(index: Int) {
                boardNext = key to index + 1
            }
        }
        BoardReader(screen, s.getInt("heroes"), BuiltInMacros.boardWants(s), first, finish).start()
    }

    /** The next hero of the board step last started, by run and step, for a try of it after a pause. */
    private var boardNext: Pair<String, Int>? = null

    /** Names read on the "Chiến khu" card, by photo key (hero-001), for the heroes' folders. */
    private val boardNames = HashMap<String, String?>()

    /** Tables the ranking step last started has finished, by run and step, for a try of it after a pause. */
    private var rankingDone: Pair<String, MutableSet<String>>? = null

    /** The first form of the skills step last started, by run and step, for a try of it after a pause. */
    private var skillForm: Pair<String, SkillRow.Row>? = null

    /** Photos of the run on disk: with skill rows read at run time, the macro alone cannot tell. */
    private fun photosTaken(m: Macro): Int = when {
        !m.photosVary || runId.isEmpty() -> m.photoSteps.size
        m.json.optString("collectionMode") == "chess" -> File(filesDir, "captures/$runId").walk().count {
            it.isFile &&
                it.extension == "png" &&
                (
                    it.name.contains("-chess__") ||
                        ChessCaptureFiles.exactDisplayName(it.relativeTo(File(filesDir, "captures")).invariantSeparatorsPath) != null
                    )
        }
        m.json.optString("collectionMode") == "ranking" -> File(filesDir, "captures/$runId").walk().count {
            it.extension == "png" &&
                RankScreens.COMBOS.any { c -> it.name.contains(c.slug) }
        }
        else -> File(filesDir, "captures/$runId").walk().count { it.extension == "png" && it.name.contains("hero") }
    }

    /**
     * Runs a [GameEntry] against the real screen: opens the game and goes through its login, reward screens and
     * popups to the hero list, checking the account's UID when the run asks for it. With the run's trace on,
     * each screenshot it decided on is kept scaled down in [GameEntry.TRACE_DIR], with the decision.
     */
    private fun enterGame(toLobby: Boolean, alive: () -> Boolean, finish: (GameEntry.Outcome) -> Unit) {
        val stamp = "%04d".format(index + 1)
        val m = macro
        val account = if (m?.json?.optBoolean("checkAccount") == true) Accounts.load(this, runAccountId) else null
        if (m?.json?.optBoolean("checkAccount") == true && (account == null || account.uid != runAccountUid)) {
            finish(GameEntry.Outcome.Failed("Không đọc được tài khoản đã chọn cho lượt. Hãy lưu lại tài khoản trước khi chạy."))
            return
        }
        val expected = account?.uid
        val trace = if (m?.json?.optBoolean("traceEntry", true) != false) File(filesDir, "captures/$runId/${GameEntry.TRACE_DIR}") else null
        var traced = trace?.listFiles()?.count { it.extension == "jpg" } ?: 0

        /** One line of the trace, with the screenshot [small] scaled down when there is one; it is recycled here. */
        fun write(what: String, kind: String, tap: Pair<Double, Double>?, small: Bitmap?) {
            val dir = trace ?: run {
                small?.recycle()
                return
            }
            val n = ++traced
            val line = JSONObject().put("n", n).put("time", System.currentTimeMillis()).put("what", what).put("kind", kind)
                .put("x", tap?.first ?: JSONObject.NULL).put("y", tap?.second ?: JSONObject.NULL).put(
                    "image",
                    if (small !=
                        null
                    ) {
                        "%04d.jpg".format(n)
                    } else {
                        JSONObject.NULL
                    }
                )
            io.execute {
                try {
                    dir.mkdirs()
                    small?.let { bmp ->
                        File(dir, "%04d.jpg".format(n)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                        bmp.recycle()
                    }
                    File(dir, "steps.jsonl").appendText(line.toString() + "\n")
                } catch (e: Exception) {
                    CrashLog.record(this@CollectorService, "Không lưu được bước vào game", e)
                }
            }
        }
        val screen = object : GameEntry.Screen {
            override val alive get() = alive()

            override fun quit(then: (GameCloser.Result) -> Unit) {
                closeApp(GAME, alive = alive) { result ->
                    if (alive()) write(result.text, "SETTINGS", null, null)
                    then(result)
                }
            }

            override fun launch(): Boolean {
                val intent = packageManager.getLaunchIntentForPackage(GAME) ?: return false
                return try {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (
                    e: Exception
                ) {
                    CrashLog.record(this@CollectorService, "Không mở được Liên Quân", e)
                    false
                }
            }

            override fun look(words: Boolean, uid: Boolean, then: (GameEntry.Shot?) -> Unit) {
                // The Garena page and the registration form may come from another app than the game.
                grab(anyApp = true) { b ->
                    if (b == null) return@grab then(null)
                    val nodes = screenWords()
                    try {
                        io.execute {
                            val reading = try {
                                val pixels = GameScreens.read(b.width, b.height) { y, row ->
                                    b.getPixels(row, 0, b.width, 0, y, b.width, 1)
                                }
                                val wanted = words || GameScreens.wordy(pixels.kind)
                                // Logo screens are black: nothing to read.
                                val text = if (wanted && pixels.signature.average() > 12) nodes + GameText.words(b) else nodes
                                val number = if (uid &&
                                    (pixels.kind == GameScreens.Kind.LOBBY || pixels.kind == GameScreens.Kind.LIST)
                                ) {
                                    GameText.uid(b)
                                } else {
                                    null
                                }
                                GameScreens.refine(pixels, text, number)
                            } catch (e: Throwable) {
                                CrashLog.record(this@CollectorService, "Không đọc được màn hình game", e)
                                null
                            }
                            handler.post {
                                if (reading == null) {
                                    b.recycle()
                                    then(null)
                                } else {
                                    then(object : GameEntry.Shot {
                                        override val reading: GameScreens.Reading = reading

                                        override fun record(what: String, tap: Pair<Double, Double>?, image: Boolean) {
                                            if (trace !=
                                                null
                                            ) {
                                                write(
                                                    what,
                                                    reading.kind.name,
                                                    tap,
                                                    if (image) Bitmap.createScaledBitmap(b, 1200, 1200 * b.height / b.width, true) else null
                                                )
                                            }
                                        }

                                        override fun done(keep: String?) {
                                            if (keep != null) store(b, runId, "$stamp-$keep") {} else b.recycle()
                                        }
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        b.recycle()
                        then(null)
                    }
                }
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                touchGame(tapAt(x, y), true, then)
            }

            override fun back(then: (Boolean) -> Unit) {
                touchGame(tapAt(HeroGrid.BACK_X, HeroGrid.BACK_Y), true, then)
            }

            override fun signIn(then: (Boolean) -> Unit) {
                signInGarena(then)
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }

            override fun now() = SystemClock.elapsedRealtime()

            override fun note(text: String) {
                this@CollectorService.note(text)
            }

            override fun say(text: String) {
                if (alive()) {
                    lastStatus = text
                    refreshPanel()
                }
            }
        }
        GameEntry(screen, expected, toLobby, finish).start()
    }

    /**
     * Ends app [pkg] through its App info page in Settings ([GameCloser]). The UI smoke check runs it on an app of
     * the emulator, which this app cannot see by name, with its [known] label.
     */
    internal fun closeApp(pkg: String, alive: () -> Boolean, known: String? = null, then: (GameCloser.Result) -> Unit) {
        val label =
            known ?: try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (
                _: Exception
            ) {
                ""
            }
        if (label.isBlank()) return then(GameCloser.Result.NO_PAGE)
        val page = object : GameCloser.Page {
            override val alive get() = alive()

            override fun open(): Boolean = try {
                // Gone from the back stack once the game opens over it.
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", pkg, null))
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        )
                )
                true
            } catch (e: Exception) {
                CrashLog.record(this@CollectorService, "Không mở được trang thông tin của $label", e)
                false
            }

            override fun read() = frontWindow(pkg)

            override fun later(ms: Long, then: () -> Unit) {
                handler.postDelayed(then, ms)
            }
        }
        GameCloser(label, page, then).start()
    }

    /**
     * The window in front for [GameCloser]: its words, and those that can be pressed (the word's node or a
     * clickable parent close above it). Never this app's window or that of [pkg], the app being closed.
     */
    private fun frontWindow(pkg: String): GameCloser.Window? {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return null
        if (root.packageName == packageName || root.packageName == pkg) return null
        val words = ArrayList<String>()
        val buttons = ArrayList<GameCloser.Button>()
        for (n in nodes(root)) {
            val text = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
            if (text.isEmpty()) continue
            words.add(text)
            var target: android.view.accessibility.AccessibilityNodeInfo? = n
            var up = 0
            while (target != null && !target.isClickable && up < 3) {
                target = target.parent
                up++
            }
            val button = target?.takeIf { it.isClickable } ?: continue
            buttons.add(
                GameCloser.Button(text, button.isEnabled && n.isEnabled) {
                    button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                }
            )
        }
        return GameCloser.Window(words, buttons)
    }

    /** Text of the window in front from its accessibility tree: forms and web pages have one, the game does not. */
    private fun screenWords(visibleOnly: Boolean = false): List<GameScreens.Word> {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        if (root.packageName == packageName) return emptyList()
        val b = screen()
        val out = ArrayList<GameScreens.Word>()
        val r = android.graphics.Rect()
        for (n in nodes(root)) {
            if (visibleOnly && !n.isVisibleToUser) continue
            val text = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
            if (text.isEmpty()) continue
            n.getBoundsInScreen(r)
            out.add(
                GameScreens.Word(
                    text,
                    r.left.toDouble() / b.width(),
                    r.top.toDouble() / b.height(),
                    r.right.toDouble() / b.width(),
                    r.bottom.toDouble() / b.height()
                )
            )
        }
        return out
    }

    private fun nodes(root: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> {
        val out = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty() &&
            out.size < 600
        ) {
            val n = queue.removeFirst()
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        return out
    }

    /**
     * Fills the Garena login page in front with the saved test account and taps "Đăng Nhập Ngay". Only on that
     * page: it must have a password field and its "Quên mật khẩu" or "Tạo tài khoản mới" links, so the account
     * is never typed anywhere else, as into a chat box.
     */
    private fun signInGarena(then: (Boolean) -> Unit) {
        val account = Accounts.load(this, runAccountId)?.takeIf { it.uid == runAccountUid } ?: return then(false)
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return then(false)
        if (root.packageName == packageName) return then(false)
        val all = nodes(root)

        fun plain(n: android.view.accessibility.AccessibilityNodeInfo) =
            GameScreens.plain((n.text ?: n.contentDescription)?.toString().orEmpty())
        // Do not put the saved password into an arbitrary dialog or chat screen.
        if (all.none { plain(it).let { t -> t.contains("quenmatkhau") || t.contains("taotaikhoanmoi") } }) {
            return then(false)
        }
        val fields = all.filter { it.isEditable }
        val password = fields.firstOrNull { it.isPassword } ?: return then(false)
        val user = fields.firstOrNull { !it.isPassword } ?: return then(false)

        // WebView may not expose the visible submit button at all. The two editable fields
        // and the Garena-specific recovery/new-account links must be present first.
        fun type(n: android.view.accessibility.AccessibilityNodeInfo, text: String): Boolean {
            val args = android.os.Bundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
            n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        if (!type(user, account.user) || !type(password, account.password)) return then(false)

        // ACTION_CLICK can return false (or true without a DOM submission) for WebView text nodes.
        // Wait for WebView's change/input event, find the button again and send a real touch instead.
        handler.postDelayed({
            if (!running || !entering) return@postDelayed then(false)
            val current = try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            }
            if (current == null || current.packageName == packageName) return@postDelayed then(false)
            val live = nodes(current)
            val hasForm = live.any { it.isEditable && it.isPassword } &&
                live.any { plain(it).let { p -> p.contains("quenmatkhau") || p.contains("taotaikhoanmoi") } }
            // The page may have submitted/navigated during the delay.
            if (!hasForm) return@postDelayed then(true)
            val button = live.firstOrNull { plain(it).contains("dangnhapngay") }
            val rect = android.graphics.Rect()
            button?.getBoundsInScreen(rect)
            val bounds = screen()
            val bx = rect.centerX().toDouble() / bounds.width()
            val by = rect.centerY().toDouble() / bounds.height()
            // The supplied Garena login screenshot has a red submit bar centered at about (0.50, 0.835).
            // Prefer the label's current rectangle so the same code survives small UI/layout changes.
            val x = if (rect.width() > 0 && rect.height() > 0 && bx in .12..0.94 && by in .62..0.95) bx else .50
            val y = if (rect.width() > 0 && rect.height() > 0 && bx in .12..0.94 && by in .62..0.95) by else .835
            note("Trang Garena: chạm Đăng Nhập Ngay tại (" + (x * 100).toInt() + "%, " + (y * 100).toInt() + "%)")
            touchGame(tapAt(x, y), true) { touched ->
                if (touched) {
                    then(true)
                } else {
                    // Last fallback to accessibility only when the physical gesture was rejected.
                    var parent: android.view.accessibility.AccessibilityNodeInfo? = button
                    var clicked = false
                    while (parent != null && !clicked) {
                        if (parent.isClickable) {
                            clicked = parent.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                            )
                        }
                        parent = if (clicked) null else parent.parent
                    }
                    then(clicked)
                }
            }
        }, 500)
    }

    private fun manualCapture() {
        if (libraryBusy) {
            status("Đang lưu dữ liệu; vui lòng chờ")
            return
        }
        if (busy || running || manualBusy) {
            status("Tạm dừng trước khi chụp riêng")
            return
        }
        try {
            if (!ready(readMacro())) return
        } catch (e: Exception) {
            status(e.message ?: "Lỗi macro")
            return
        }
        manualBusy = true
        refreshPanel()
        if (runId.isBlank() && manualRunId.isBlank()) manualRunId = "manual-${System.currentTimeMillis()}"
        capture("capture-${System.currentTimeMillis()}", runId.ifBlank { manualRunId }, wait = true) {
            manualBusy = false
            status(
                if (it) {
                    "Đã lưu ảnh"
                } else {
                    failure
                        ?: "Chụp ảnh thất bại"
                }
            )
        }
    }

    private fun photoDirectory(folder: String, name: String, bitmap: Bitmap): File {
        val base = File(filesDir, "captures/$folder").apply { mkdirs() }
        val m = macro ?: return base
        // Each table of the hero statistics in a folder of its own.
        if (folder != "manual" && m.json.optString("collectionMode") == "ranking") {
            return RankScreens.COMBOS.firstOrNull { name.contains("-${it.slug}-") }?.let { File(base, it.slug).apply { mkdirs() } } ?: base
        }
        if (folder != "manual" && m.json.optString("collectionMode") == "chess") {
            ChessCaptureFiles.directoryParts(name)?.let { dirs ->
                return dirs.fold(base) { dir, slug -> File(dir, slug).apply { mkdirs() } }
            }
            return base
        }
        if (folder == "manual" || m.json.optString("collectionMode") !in listOf("single", "all", "pick", "board")) return base
        val key = Regex("(hero(?:-\\d{3})?)-").find(name)?.groupValues?.get(1) ?: return base
        val mapFile = File(base, "hero-folders.json")
        val mapping = try {
            JSONObject(mapFile.readText())
        } catch (_: Exception) {
            JSONObject()
        }
        val existing = mapping.optString(key)
        if (existing.matches(Regex("[a-z0-9_-]{1,100}")) && File(base, existing).isDirectory) return File(base, existing)
        val ocr = if (name.endsWith("-overview")) HeroNames.read(bitmap) else null
        val manual = when (m.json.optString("collectionMode")) {
            "single" -> m.json.optString("heroName", "").trim().takeUnless { it.isBlank() || it == "Chưa đặt tên" }
            // The name read on the "Chiến khu" card.
            "board" -> boardNames[key]?.takeIf { it.isNotBlank() }
            // The name asked for, which was read on its card; as read on its page when the two agree.
            "pick" -> BuiltInMacros.heroNames(m.json).getOrNull((key.removePrefix("hero-").toIntOrNull() ?: 1) - 1)
                ?.let { asked -> ocr?.takeIf { HeroFinder.same(HeroNames.slug(it), HeroNames.slug(asked)) } ?: asked }
            else -> null
        }
        val displayName = manual ?: ocr
        // Names for "Lấy thông tin tướng" to offer, and for a whole-list run the card each hero is on.
        displayName?.let {
            HeroIndex.record(
                filesDir,
                if (m.json.optString("collectionMode") == "all" &&
                    ocr != null
                ) {
                    (key.removePrefix("hero-").toIntOrNull() ?: 0) - 1
                } else {
                    -1
                },
                it
            )
        }
        val slug = displayName?.let { HeroNames.slug(it) } ?: "tuong-${key.removePrefix("hero-").takeUnless { it == "hero" } ?: "001"}"
        var actual = slug
        var suffix = 2
        while (File(base, actual).exists()) {
            actual = "$slug-${suffix++}"
        }
        val dir = File(base, actual).apply { mkdirs() }
        File(dir, "hero.json").writeText(
            JSONObject().put("name", displayName ?: "Chưa rõ tên")
                .put(
                    "nameSource",
                    if (manual != null &&
                        m.json.optString("collectionMode") != "board"
                    ) {
                        "manual"
                    } else if (manual != null || ocr != null) {
                        "ocr"
                    } else {
                        "unknown"
                    }
                )
                .put("ocrText", ocr ?: "").put("captureKey", key).toString(2)
        )
        mapping.put(key, actual)
        mapFile.writeText(mapping.toString(2))
        return dir
    }

    private var lastShotAt = 0L

    /** Takes a screenshot with the panel hidden; [done] gets a software bitmap to recycle, or null. */
    internal fun grab(anyApp: Boolean = false, done: (Bitmap?) -> Unit) {
        val debugSession = debugCaptureShield.acquire()
        val session = captureShield.acquire()
        val finish = { b: Bitmap? ->
            debugCaptureShield.release(debugSession)
            captureShield.release(session)
            done(b)
        }

        fun shoot(retry: Boolean) {
            if (current !== this || !captureShield.contains(session)) {
                finish(null)
                return
            }
            val p = foreground()
            if (!anyApp &&
                p != null &&
                p != GAME
            ) {
                if (retry) {
                    handler.postDelayed({ shoot(false) }, 1000)
                    return
                }
                failure =
                    "Không chụp: Liên Quân không ở phía trước (đang mở ${appName(p)})"
                finish(null)
                return
            }
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bitmap = try {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        hardware?.copy(Bitmap.Config.ARGB_8888, false).also { hardware?.recycle() }
                    } catch (
                        e: Throwable
                    ) {
                        CrashLog.record(this@CollectorService, "Không đọc được ảnh chụp", e)
                        null
                    } finally {
                        buffer.close()
                    }
                    if (bitmap == null) failure = "Không đọc được ảnh chụp màn hình"
                    finish(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    // Android allows one accessibility screenshot per second.
                    if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retry) {
                        handler.postDelayed({ shoot(false) }, 1100)
                        return
                    }
                    failure = "Chụp màn hình thất bại (mã $errorCode)"
                    finish(null)
                }
            }
            lastShotAt = SystemClock.uptimeMillis()
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
            } catch (
                e: Exception
            ) {
                CrashLog.record(this, "Không gọi được chụp màn hình", e)
                finish(null)
            }
        }
        // The panel needs 250 ms to disappear; a screenshot soon after the previous one waits out Android's interval instead of being refused.
        handler.postDelayed({ shoot(true) }, maxOf(250L, lastShotAt + SHOT_INTERVAL_MS - SystemClock.uptimeMillis()))
    }

    /**
     * Takes a screenshot and saves it in the background: [done] runs once the image is captured, so the
     * macro does not wait for PNG compression, unless [wait] is set or too many saves are already queued.
     */
    private fun capture(name: String, folder: String, wait: Boolean = false, done: (Boolean) -> Unit) {
        if (filesDir.usableSpace < 30L * 1024 * 1024) {
            failure = "Không đủ dung lượng trống để chụp"
            done(false)
            return
        }
        grab { bitmap ->
            if (bitmap == null) return@grab done(false)
            pendingSaves++
            store(bitmap, folder, name) { ok ->
                pendingSaves--
                if (!ok) note("Không lưu được ảnh $name")
                if (wait) done(ok)
                saveWaiters.removeFirstOrNull()?.invoke()
                refreshPanel()
            }
            if (!wait) {
                if (pendingSaves <= MAX_PENDING_SAVES) done(true) else saveWaiters.addLast { done(true) }
            }
        }
    }

    private fun afterSaves(then: () -> Unit) {
        if (pendingSaves == 0) then() else saveWaiters.addLast { afterSaves(then) }
    }

    /** Writes [bitmap] as PNG off the main thread and recycles it. */
    internal fun store(bitmap: Bitmap, folder: String, name: String, done: (Boolean) -> Unit) {
        try {
            io.execute {
                var record: Long? = null
                val ok = try {
                    val dir = photoDirectory(folder, name, bitmap)
                    val basename = ChessCaptureFiles.screenshotBase(name)
                    val target = File(dir, "$basename.png")
                    // A stop after PNG commit but before the progress journal must not overwrite
                    // an earlier numbered detail. Reuse only a visually matching existing image.
                    if (basename != name && target.isFile && target.length() > 0) {
                        val previous = BitmapFactory.decodeFile(target.absolutePath)
                            ?: error("Không đọc được ảnh chi tiết đã có: " + basename)
                        try {
                            val modal = ChessCaptureFiles.isDetailCapture(name)
                            require(
                                !ChessWiki.changed(
                                    ChessWiki.signature(previous, modal),
                                    ChessWiki.signature(bitmap, modal)
                                )
                            ) { "Ảnh chi tiết đã có nội dung khác: " + basename }
                        } finally {
                            previous.recycle()
                        }
                        true
                    } else {
                        record = try {
                            CaptureHistory.begin(this, folder, target)
                        } catch (_: Exception) {
                            null
                        }
                        val part = File(dir, "$basename.png.part")
                        try {
                            require(part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) })
                            require(part.length() > 0 && part.renameTo(target))
                            true
                        } finally {
                            part.delete()
                        }
                    }
                } catch (e: Throwable) {
                    CrashLog.record(this@CollectorService, "Không lưu được ảnh $name", e)
                    false
                }
                record?.let {
                    try {
                        CaptureHistory.finish(this, it, ok)
                    } catch (_: Exception) {}
                }
                bitmap.recycle()
                handler.post { done(ok) }
            }
        } catch (e: Exception) {
            bitmap.recycle()
            done(false)
        }
    }
}
