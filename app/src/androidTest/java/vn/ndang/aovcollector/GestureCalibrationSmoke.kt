package vn.ndang.aovcollector

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button

/** Synthetic editor only; tests never inject a gesture into a game or sign in. */
internal object GestureCalibrationSmoke {
    fun run(test: Instrumentation, service: CollectorService, automation: android.app.UiAutomation) {
        val action = CalibrationAction("smoke.tap", "Điểm thử", listOf(CalibrationPoint(.4, .3)))
        val step = CalibrationStep("Thao tác mặc định", "Kéo để chỉnh", action)
        var tests = 0
        var saves = 0
        var defaults = 0
        var saved = action.points
        var closed = 0
        var testSucceeds = true
        var diagnosticRefusal: CalibrationRefusal? = null
        lateinit var editor: GestureCalibrationEditor

        fun buttons(view: View): List<Button> = if (view is ViewGroup) {
            (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        } else if (view is Button) {
            listOf(view)
        } else {
            emptyList()
        }

        fun click(text: String) = buttons(checkNotNull(editor.view)).single { it.text.toString() == text }.performClick()

        fun screenBounds(view: View): android.graphics.Rect {
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            return android.graphics.Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
        }

        fun awaitControls() {
            val deadline = SystemClock.uptimeMillis() + 5000
            do {
                var fits = false
                test.runOnMainSync {
                    val root = checkNotNull(editor.view) as ViewGroup
                    val controls = root.getChildAt(1)
                    val frame = screenBounds(root)
                    fits = !root.isLayoutRequested &&
                        !controls.isLayoutRequested &&
                        controls.width > 0 &&
                        controls.height > 0 &&
                        frame.contains(screenBounds(controls)) &&
                        buttons(controls).filter { it.isShown }.all { it.width > 0 && it.height > 0 && frame.contains(screenBounds(it)) }
                }
                if (fits) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            error("Teaching controls did not settle within the editor after status/corner change")
        }
        test.runOnMainSync {
            editor = GestureCalibrationEditor(
                service, step, 0, 0, action.points,
                navigate = { error("Runtime teaching must not navigate a catalogue") },
                choose = { error("Runtime teaching must not show a catalogue") },
                enterLobby = { error("Runtime teaching already entered the lobby") }, scan = { it(null) }, resolved = { it.points },
                save = { _, points ->
                    saves++
                    saved = points
                    true
                }, reset = {
                    defaults++
                    true
                },
                test = { _, _, done ->
                    check(editor.view?.visibility == View.VISIBLE) { "Runtime editor hid before service preflight" }
                    tests++
                    done(testSucceeds)
                }, closed = { closed++ }, refusal = { diagnosticRefusal }
            )
            editor.shield(true)
            check(editor.show())
            check(editor.view?.visibility == View.INVISIBLE) { "New editor ignored outstanding capture" }
            editor.shield(false)
        }
        test.waitForIdleSync()
        try {
            test.runOnMainSync {
                val root = checkNotNull(editor.view) as ViewGroup
                check(tests == 0 && saves == 0)
                val labels = buttons(root).map { it.text.toString() }
                check(labels.none { it in listOf("Trước", "Tiếp", "Các bước", "Vào sảnh", "Đọc vị trí") })
                click("Đổi góc bảng")
                check(tests == 0)
                val controls = root.getChildAt(1) as ViewGroup
                val grip = controls.getChildAt(0)
                val startTop = controls.y
                val dragNow = SystemClock.uptimeMillis()
                for ((kind, py) in listOf(
                    MotionEvent.ACTION_DOWN to 10f,
                    MotionEvent.ACTION_MOVE to (if (startTop > 0) -100f else 120f),
                    MotionEvent.ACTION_UP to 120f
                )) {
                    val event = MotionEvent.obtain(dragNow, dragNow + 20, kind, 10f, py, 0)
                    grip.dispatchTouchEvent(event)
                    event.recycle()
                }
                check(controls.y != startTop) { "Floating panel did not move when dragged" }
                check(tests == 0 && saves == 0) { "Moving panel ran or saved a gesture" }
                val drawing = root.getChildAt(0)
                val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
                val x = (.4 * (bounds.width() - 1)).toFloat()
                val y = (.3 * (bounds.height() - 1)).toFloat()
                val now = SystemClock.uptimeMillis()
                for ((kind, px) in listOf(
                    MotionEvent.ACTION_DOWN to x,
                    MotionEvent.ACTION_MOVE to x + 30,
                    MotionEvent.ACTION_UP to x + 30
                )) {
                    val event = MotionEvent.obtain(now, now + 20, kind, px, y, 0)
                    drawing.dispatchTouchEvent(event)
                    event.recycle()
                }
                check(tests == 0 && saves == 0) { "Dragging executed or saved a gesture" }
                click("Lưu")
                check(saves == 1 && saved.single().x > .4 && tests == 0)
                click("Mặc định")
                click("Lưu")
                check(saved == action.points && tests == 0 && defaults == 1)
                click("Chạy bước / Tiếp")
                check(tests == 1 && editor.view != null)
            }
            test.waitForIdleSync()
            awaitControls()
            test.runOnMainSync {
                val root = checkNotNull(editor.view) as ViewGroup
                check(!buttons(root).single { it.text.toString() == "Chép lý do lỗi" }.isShown)
                if (root.getChildAt(1).y > 0f) click("Đổi góc bảng")
                click("Đổi góc bảng")
            }
            test.waitForIdleSync()
            awaitControls()
            test.runOnMainSync {
                val root = checkNotNull(editor.view) as ViewGroup
                check(kotlin.math.abs(screenBounds(root).bottom - screenBounds(root.getChildAt(1)).bottom) <= 1)
                diagnosticRefusal = CalibrationDiagnostics.refusal(
                    "game-does-not-cover-usable-display",
                    "result=window-safety-denied",
                    "reason=game-does-not-cover-usable-display; windowCount=1"
                )
                testSucceeds = false
                click("Chạy bước / Tiếp")
                check(editor.view === root && root.visibility == View.VISIBLE && tests == 2)
                val title = (root.getChildAt(1) as ViewGroup).getChildAt(1) as android.widget.TextView
                check(
                    title.text.contains(checkNotNull(diagnosticRefusal).code) &&
                        title.text.contains(checkNotNull(diagnosticRefusal).message)
                ) {
                    "Refused editor did not show its exact code and actionable explanation"
                }
            }
            test.waitForIdleSync()
            awaitControls()
            test.runOnMainSync {
                val root = checkNotNull(editor.view) as ViewGroup
                val copy = buttons(root).single { it.text.toString() == "Chép lý do lỗi" }
                check(copy.isShown && screenBounds(root).contains(screenBounds(copy))) { "Error report button is hidden or clipped" }
                val title = (root.getChildAt(1) as ViewGroup).getChildAt(1) as android.widget.TextView
                val textLayout = checkNotNull(title.layout)
                check((0 until textLayout.lineCount).all { textLayout.getEllipsisCount(it) == 0 }) {
                    "The longest current refusal explanation is truncated"
                }
                check(kotlin.math.abs(screenBounds(root).bottom - screenBounds(root.getChildAt(1)).bottom) <= 1) {
                    "Specific refusal and copy action pushed bottom controls outside the editor"
                }
                diagnosticRefusal = null
                testSucceeds = true
                click("Chạy bước / Tiếp")
                check(tests == 3 && !copy.isShown) { "Successful retry retained a stale failure copy action" }
            }
            test.waitForIdleSync()
            awaitControls()
            test.runOnMainSync {
                val root = checkNotNull(editor.view) as ViewGroup
                editor.shield(true)
                val params = root.layoutParams as WindowManager.LayoutParams
                check(root.visibility == View.INVISIBLE)
                check(params.screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_BEHIND)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                editor.shield(false)
                check(root.visibility == View.VISIBLE)
                click("Hủy")
                editor.shield(false)
                check(editor.view == null && closed == 1 && tests == 3)
            }
        } finally {
            test.runOnMainSync { editor.close() }
        }

        // Cursor/isolation fixtures use a fake dispatcher; the device fixture uses a synthetic touch receiver.
        // Keep a positively identified synthetic app underneath so production foreground safety stays enabled.
        TeachingDeviceSmoke.openFixture(test, service, automation)
        pendingGestureFixture(test, service, automation)
        stateIsolationFixture(test, service)
        TeachingDeviceSmoke.run(test, service, automation)
        runtimeFixture(test, service)
    }

    private fun pendingGestureFixture(test: Instrumentation, service: CollectorService, automation: android.app.UiAutomation) {
        val tap = CalibrationAction("smoke.runtime.tap", "Runtime tap", listOf(CalibrationPoint(.4, .3)), relative = true)
        val swipe = CalibrationAction("smoke.runtime.swipe", "Runtime swipe", listOf(CalibrationPoint(.7, .8), CalibrationPoint(.7, .2)))
        val dispatched = mutableListOf<Pair<CalibrationAction, List<CalibrationPoint>>>()
        val preferences = service.getSharedPreferences("gesture-calibration", 0)
        val wm = service.getSystemService(WindowManager::class.java)
        val bounds = android.graphics.Rect(wm.maximumWindowMetrics.bounds)
        val profile = service.calibrationProfile()
        val store = CalibrationStore(preferences)
        val normalPreferences = service.getSharedPreferences("collector", 0).all.toMap()
        val captures = java.io.File(service.filesDir, "captures")

        fun history() = if (!captures.exists()) {
            emptyMap()
        } else {
            captures.walkTopDown().filter { it.isFile }.associate {
                it.relativeTo(captures).path to java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
            }
        }
        val beforeHistory = history()

        fun buttons(view: View): List<Button> = if (view is ViewGroup) {
            (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        } else if (view is Button) {
            listOf(view)
        } else {
            emptyList()
        }

        fun click(label: String) = buttons(checkNotNull(service.calibrationView)).single { it.text.toString() == label }.performClick()

        fun begin(actions: List<CalibrationAction>) = service.beginTeachingFixture(actions) { action, points ->
            dispatched.add(action to points)
        }
        try {
            test.runOnMainSync {
                store.reset(profile, tap.id)
                store.reset(profile, swipe.id)
                begin(listOf(tap, swipe))
                check(service.calibrationView != null && dispatched.isEmpty()) { "First pending gesture ran automatically" }
                val pending = service.calibrationView
                check(!service.prepareEdit()) { "Management access must stay blocked while teaching" }
                check(service.calibrationView === pending && service.isWorking && dispatched.isEmpty()) {
                    "Management request cancelled or advanced the pending teaching gesture"
                }
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                val drawing = (checkNotNull(service.calibrationView) as ViewGroup).getChildAt(0)
                val x = (.4 * (bounds.width() - 1)).toFloat()
                val y = (.3 * (bounds.height() - 1)).toFloat()
                val now = SystemClock.uptimeMillis()
                for ((kind, px) in listOf(
                    MotionEvent.ACTION_DOWN to x,
                    MotionEvent.ACTION_MOVE to x + 25,
                    MotionEvent.ACTION_UP to x + 25
                )) {
                    val event = MotionEvent.obtain(now, now + 20, kind, px, y, 0)
                    drawing.dispatchTouchEvent(event)
                    event.recycle()
                }
                click("Lưu")
                check(dispatched.isEmpty() && service.calibrationView != null)
                check(CalibrationStore(preferences).resolve(profile, tap).single().x > .4) { "Save did not persist" }
            }
            val captured = java.util.concurrent.CountDownLatch(1)
            val hasBitmap = java.util.concurrent.atomic.AtomicBoolean(false)
            test.runOnMainSync {
                service.grab(anyApp = true) { bitmap ->
                    hasBitmap.set(bitmap != null)
                    bitmap?.recycle()
                    captured.countDown()
                }
                check(service.calibrationView?.visibility == View.INVISIBLE) { "Pending editor leaked into capture" }
            }
            check(captured.await(8, java.util.concurrent.TimeUnit.SECONDS) && hasBitmap.get())
            test.runOnMainSync {
                check(service.calibrationView?.visibility == View.VISIBLE)
                check(
                    wm.maximumWindowMetrics.bounds == bounds &&
                        service.calibrationProfile() == profile
                ) { "Editor changed orientation" }
            }
            automation.takeScreenshot()?.let { bitmap ->
                val file = java.io.File(service.filesDir, "smoke/calibration-runtime.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            test.runOnMainSync {
                click("Chạy bước / Tiếp")
                check(dispatched.size == 1 && dispatched.single().first == tap)
                check(dispatched.single().second == store.resolve(profile, tap))
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.calibrationView != null && dispatched.size == 1) { "Next action did not pause" }
                click("Hủy")
                check(service.calibrationView == null && !service.isWorking) { "Cancel left runner blocked" }
                val freshTap = tap.copy(points = listOf(CalibrationPoint(.5, .4)))
                begin(listOf(freshTap))
                click("Chạy bước / Tiếp")
                check(dispatched.size == 2)
                check(dispatched.last().second == CalibrationStore(preferences).resolve(profile, freshTap))
                check(dispatched.last().second.single().x > .5) { "Restart ignored fresh relative geometry or persisted override" }
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.calibrationView == null && !service.isWorking)
                begin(listOf(tap))
                click("Mặc định")
                check(store.resolve(profile, tap) == tap.points)
                check(dispatched.size == 2) { "Reset executed input" }
                service.onConfigurationChanged(service.resources.configuration)
                check(service.calibrationView == null && !service.isWorking) { "Rotation kept a stale pending action" }
                begin(listOf(tap))
                click("Chạy bước / Tiếp")
                check(dispatched.last().second == tap.points) { "Restart resurrected reset override" }
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                begin(listOf(tap, swipe))
                service.onInterrupt()
                check(service.calibrationView == null && !service.isWorking)
                check(dispatched.size == 3) { "Interrupt ran a pending action" }
                begin(listOf(tap, swipe))
                click("Chạy bước / Tiếp")
                service.onInterrupt()
                begin(listOf(swipe))
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.calibrationView != null && dispatched.size == 4) { "Cancelled continuation affected restarted teaching" }
                click("Chạy bước / Tiếp")
                check(dispatched.last().first == swipe)
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.calibrationView == null && !service.isWorking)
                check(history() == beforeHistory) { "Runtime teaching wrote capture/history files" }
                check(service.getSharedPreferences("collector", 0).all == normalPreferences) { "Teaching changed normal-run state" }
            }
        } finally {
            test.runOnMainSync {
                service.onInterrupt()
                store.reset(profile, tap.id)
                store.reset(profile, swipe.id)
            }
        }
    }

    private fun stateIsolationFixture(test: Instrumentation, service: CollectorService) {
        val action = CalibrationAction("smoke.isolation", "Isolation", listOf(CalibrationPoint(.4, .3)))
        val names = listOf(
            "index", "runId", "listRef", "listTotal", "rankingDone", "boardNext",
            "activeMs", "entryMs", "stopped", "failure", "unsavedRetries"
        )
        val fields = names.associateWith { name -> CollectorService::class.java.getDeclaredField(name).apply { isAccessible = true } }
        val originals = fields.mapValues { it.value.get(service) }
        val seeded = mapOf<String, Any?>(
            "index" to 7, "runId" to "normal-isolation-fixture", "listRef" to floatArrayOf(1f, 2f), "listTotal" to 89,
            "rankingDone" to ("normal/ranking" to mutableSetOf("completed-combo")), "boardNext" to ("normal/board" to 12),
            "activeMs" to 9876L, "entryMs" to 1234L, "stopped" to true, "failure" to "normal failure", "unsavedRetries" to 4
        )
        val boardNamesField = CollectorService::class.java.getDeclaredField("boardNames").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val boardNames = boardNamesField.get(service) as MutableMap<String, String?>
        val originalNames = boardNames.toMap()
        try {
            test.runOnMainSync {
                seeded.forEach { (name, value) -> fields.getValue(name).set(service, value) }
                boardNames.clear()
                boardNames["hero-001"] = "Normal hero"
                repeat(2) {
                    service.beginTeachingFixture(listOf(action)) { _, _ -> error("Isolation fixture must remain paused") }
                    check(service.calibrationView != null)
                    val prefsBeforeReset = service.getSharedPreferences("collector", 0).all.toMap()
                    service.reset()
                    check(service.calibrationView != null && service.isWorking) { "New-run reset disrupted teaching" }
                    check(service.getSharedPreferences("collector", 0).all == prefsBeforeReset)
                    for (name in listOf("listRef", "listTotal", "rankingDone", "boardNext", "failure")) {
                        check(fields.getValue(name).get(service) == null) { "Teaching reused normal cache: $name" }
                    }
                    check(boardNames.isEmpty())
                    check(fields.getValue("activeMs").getLong(service) == 0L)
                    check(fields.getValue("entryMs").getLong(service) == 0L)
                    check(fields.getValue("unsavedRetries").getInt(service) == 0)
                    fields.getValue("index").setInt(service, 99)
                    fields.getValue("runId").set(service, "teaching-temporary")
                    fields.getValue("rankingDone").set(service, "teaching/ranking" to mutableSetOf("temporary"))
                    fields.getValue("boardNext").set(service, "teaching/board" to 66)
                    boardNames["hero-001"] = "Teaching hero"
                    service.onInterrupt()
                    check(service.calibrationView == null && !service.isWorking)
                    seeded.forEach { (name, expected) ->
                        check(fields.getValue(name).get(service) == expected) { "Teaching changed normal resume state: $name" }
                    }
                    check(boardNames == mapOf("hero-001" to "Normal hero"))
                }
            }
        } finally {
            test.runOnMainSync {
                service.onInterrupt()
                originals.forEach { (name, value) -> fields.getValue(name).set(service, value) }
                boardNames.clear()
                boardNames.putAll(originalNames)
            }
        }
    }

    private fun runtimeFixture(test: Instrumentation, service: CollectorService) {
        // Installed-game entry is intentionally not started by this synthetic instrumentation suite.
        // A failed entry on CI must leave existing collection history and preferences untouched.
        if (service.packageManager.getLaunchIntentForPackage(CollectorService.GAME) != null) return
        val macroFile = MacroStore.current(service)
        val original = macroFile.readText()
        val prefs = service.getSharedPreferences("collector", 0)
        val beforePreferences = prefs.all.toMap()
        val captures = java.io.File(service.filesDir, "captures")

        fun history() = if (!captures.exists()) {
            emptyMap()
        } else {
            captures.walkTopDown().filter { it.isFile }.associate {
                it.relativeTo(captures).path to java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
            }
        }
        val beforeHistory = history()
        try {
            test.runOnMainSync {
                macroFile.writeText(BuiltInMacros.create("", 1, false))
                service.showCalibration()
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.calibrationView == null) { "Missing-game entry must not open an unrelated catalogue" }
                check(!service.isWorking) { "Failed entry left teaching blocked" }
                service.onInterrupt()
                check(history() == beforeHistory) { "Failed teaching modified collection history" }
                check(prefs.all == beforePreferences) { "Teaching changed saved normal-run state" }
            }
        } finally {
            test.runOnMainSync {
                service.onInterrupt()
                macroFile.writeText(original)
            }
        }
    }
}
