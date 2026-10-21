package vn.ndang.aovcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Real Android windows/input, but only against a positively identified, offline CI fixture, never a game. */
internal object TeachingDeviceSmoke {
    private const val FIXTURE_ACTIVITY = "vn.ndang.gamefixture.GestureActivity"
    private const val FIXTURE_MARKER = "vn.ndang.aovcollector.SYNTHETIC_GESTURE_FIXTURE"

    fun openFixture(
        test: Instrumentation,
        service: CollectorService,
        automation: UiAutomation,
        reset: Boolean = false,
        immersive: Boolean = false
    ) {
        val component = ComponentName(CollectorService.GAME, FIXTURE_ACTIVITY)
        val fixture = service.packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
        check(fixture.metaData?.getBoolean(FIXTURE_MARKER) == true) {
            "Refusing real input: the explicitly identified synthetic fixture is not installed"
        }
        val result = shell(
            automation,
            "am start -W -n ${CollectorService.GAME}/$FIXTURE_ACTIVITY -f 0x10020000 --ez reset $reset --ez immersive $immersive"
        )
        check("Error" !in result && "Exception" !in result) { "Could not start synthetic receiver: $result" }
        waitFor("Synthetic receiver did not become the underlying app") {
            main(test) { service.foreground() == CollectorService.GAME }
        }
        test.waitForIdleSync()
    }

    fun run(test: Instrumentation, service: CollectorService, automation: UiAutomation) {
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val tap = CalibrationAction("smoke.device.tap", "Synthetic device tap", listOf(CalibrationPoint(.70, .32)))
        val swipe = CalibrationAction(
            "smoke.device.swipe",
            "Synthetic device swipe",
            listOf(CalibrationPoint(.70, .68), CalibrationPoint(.70, .35)),
            durationMs = 400
        )
        val editedTap = CalibrationPoint(.80, .42)
        val editedEnd = CalibrationPoint(.80, .28)
        val wm = service.getSystemService(WindowManager::class.java)
        val store = CalibrationStore(service.getSharedPreferences("gesture-calibration", 0))
        var profile: CalibrationProfile? = null
        val diagnostics = File(service.filesDir, "smoke").apply { mkdirs() }
        val checks = mutableListOf<String>()
        val windowLog = File(diagnostics, "calibration-device-windows.txt").apply { writeText("") }
        var editorSequence = 0

        fun events(): JSONArray = JSONArray(shell(automation, "run-as ${CollectorService.GAME} cat files/gesture-events.json"))

        fun foreground(): String? = main(test) { service.foreground() }

        fun startFixture(reset: Boolean = false) {
            openFixture(test, service, automation, reset)
        }

        fun startCollector() {
            service.startActivity(
                Intent.makeMainActivity(ComponentName(service, MainActivity::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            waitFor("Collector activity did not become the underlying app") { foreground() == service.packageName }
        }

        fun currentButton(label: String): Button =
            main(test) { buttons(checkNotNull(service.calibrationView)).single { it.text.toString() == label } }

        fun click(label: String) {
            test.waitForIdleSync()
            val button = currentButton(label)
            val bounds = main(test) { viewBounds(button) }
            inject(automation, listOf(bounds.exactCenterX() to bounds.exactCenterY()))
            test.waitForIdleSync()
        }

        fun assertPanel(hidden: Boolean) {
            main(test) {
                val panel = checkNotNull(service.panelView)
                val params = panel.layoutParams as WindowManager.LayoutParams
                check(panel.visibility == if (hidden) View.INVISIBLE else View.VISIBLE) {
                    "Normal panel visibility was not restored or hidden correctly"
                }
                check((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) == hidden) {
                    "Normal panel touchability does not match its editing/capture state"
                }
            }
        }

        fun assertControlsWithinEditor() {
            waitFor("Teaching controls extend outside the editor after layout/status change") {
                main(test) {
                    val root = service.calibrationView as? ViewGroup ?: return@main false
                    val controls = root.getChildAt(1)
                    val frame = viewBounds(root)
                    val geometry = "root=$frame layoutRequested=${root.isLayoutRequested}\n" +
                        "controls=${viewBounds(controls)} layoutRequested=${controls.isLayoutRequested} " +
                        "translation=(${controls.translationX},${controls.translationY})\n" +
                        buttons(controls).joinToString("\n") { "${it.text}: ${viewBounds(it)}" }
                    File(diagnostics, "calibration-device-geometry.txt").writeText(geometry)
                    !root.isLayoutRequested &&
                        !controls.isLayoutRequested &&
                        controls.width > 0 &&
                        controls.height > 0 &&
                        frame.contains(viewBounds(controls)) &&
                        buttons(controls).filter { it.isShown }.all { it.width > 0 && it.height > 0 && frame.contains(viewBounds(it)) }
                }
            }
        }

        fun begin(actions: List<CalibrationAction>) {
            main(test) { service.beginTeachingDeviceFixture(actions) }
            waitFor("Real-dispatch fixture did not present its pending editor") {
                main(test) { service.calibrationView?.let { it.isShown && it.width > 0 && it.height > 0 } == true }
            }
            test.waitForIdleSync()
            // A laid-out View does not prove that UiAutomation has received the window metadata.
            // Observe the actual editor window, not an arbitrary earlier collector overlay.
            editorSequence++
            val publicationStarted = SystemClock.uptimeMillis()
            waitFor("Foreground assertion did not exercise the actual own accessibility editor overlay window") {
                val expected = main(test) { service.calibrationView?.takeIf { it.isShown }?.let(::viewBounds) }
                val inventory = StringBuilder(
                    "editor=$editorSequence elapsedMs=${SystemClock.uptimeMillis() - publicationStarted} expected=$expected\n"
                )
                var published = false
                for (window in automation.windows) {
                    val bounds = Rect().also(window::getBoundsInScreen)
                    val owner = window.root?.packageName?.toString()
                    inventory.appendLine(
                        "type=${window.type} owner=$owner bounds=$bounds focused=${window.isFocused} active=${window.isActive}"
                    )
                    if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                        owner == service.packageName &&
                        expected != null &&
                        !expected.isEmpty &&
                        bounds == expected
                    ) {
                        published = true
                    }
                }
                windowLog.appendText(inventory.appendLine("published=$published").toString())
                published
            }
            check(foreground() == CollectorService.GAME) { "Own editor overlay hid the real underlying fixture" }
            assertPanel(hidden = true)
            assertControlsWithinEditor()
        }

        fun navigationBounds(id: String): Rect {
            var result: Rect? = null
            waitFor("Actual SystemUI navigation button is missing: $id") {
                result = automation.windows.asSequence().mapNotNull { it.root }.flatMap {
                    it.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$id").asSequence()
                }.filter { it.isVisibleToUser }.map {
                    Rect().also(it::getBoundsInScreen)
                }.firstOrNull { !it.isEmpty }
                result != null
            }
            return checkNotNull(result)
        }

        fun assertNavigationOutsideEditor(nav: Rect) {
            main(test) {
                val root = checkNotNull(service.calibrationView)
                val params = root.layoutParams as WindowManager.LayoutParams
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
                val reserved = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.mandatorySystemGestures()
                check(params.fitInsetsTypes and reserved == reserved) { "Editor stopped reserving system navigation insets" }
                check(!Rect.intersects(viewBounds(root), nav)) {
                    "Editor still covers the actual system navigation button: editor=${viewBounds(root)}, nav=$nav"
                }
            }
        }

        fun snapshot(name: String) {
            automation.takeScreenshot()?.let { bitmap ->
                File(diagnostics, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }

        fun assertNoReplay(count: Int, stale: Button) {
            startFixture()
            main(test) { stale.performClick() }
            // Includes the old 1.5-second away-check and the shield/gesture completion interval.
            SystemClock.sleep(1800)
            main(test) {
                check(service.calibrationView == null && !service.isWorking) { "Returning resurrected the old pending step" }
            }
            check(events().length() == count) { "A stale gesture replayed after returning from another app" }
        }

        try {
            main(test) { service.onInterrupt() }
            startFixture(reset = true)
            val bounds = Rect(wm.currentWindowMetrics.bounds)
            val insets = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            profile = CalibrationProfile(
                bounds.width(),
                bounds.height(),
                wm.defaultDisplay.rotation,
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )
            main(test) {
                store.reset(checkNotNull(profile), tap.id)
                store.reset(checkNotNull(profile), swipe.id)
                service.showPanel()
            }
            begin(listOf(tap, swipe))
            // Force the bottom edge, regardless of the previous editor's remembered position.
            if (main(test) { (checkNotNull(service.calibrationView) as ViewGroup).getChildAt(1).y > 0f }) {
                click("Đổi góc bảng")
            }
            click("Đổi góc bảng")
            assertControlsWithinEditor()
            main(test) {
                val root = checkNotNull(service.calibrationView) as ViewGroup
                check(abs(viewBounds(root).bottom - viewBounds(root.getChildAt(1)).bottom) <= 1) {
                    "Title-growth regression did not start with controls at the bottom edge"
                }
            }
            assertNavigationOutsideEditor(navigationBounds("home"))
            assertNavigationOutsideEditor(navigationBounds("recent_apps"))
            snapshot("calibration-device-overlay")
            check(events().length() == 0) { "Presenting an editor injected input" }

            fun pixel(point: CalibrationPoint): Pair<Float, Float> =
                (point.x * (bounds.width() - 1)).toFloat() to (point.y * (bounds.height() - 1)).toFloat()

            // The editing guard must absorb ordinary game-area taps without dispatching or changing steps.
            inject(automation, listOf(pixel(CalibrationPoint(.48, .15))))
            check(events().length() == 0 && main(test) { service.calibrationView != null }) {
                "An empty editing-area tap leaked into the synthetic game"
            }
            inject(automation, path(pixel(tap.points.single()), pixel(editedTap)))
            click("Lưu")
            assertControlsWithinEditor()
            main(test) {
                val root = checkNotNull(service.calibrationView) as ViewGroup
                check(abs(viewBounds(root).bottom - viewBounds(root.getChildAt(1)).bottom) <= 1) {
                    "Two-line saved status did not keep bottom controls inside the editor"
                }
            }
            val persistedTap = main(test) { store.resolve(checkNotNull(profile), tap).single() }
            check(abs(persistedTap.x - editedTap.x) < .002 && abs(persistedTap.y - editedTap.y) < .002) {
                "Save did not persist the actual edited tap before Run: $persistedTap"
            }
            check(events().length() == 0) { "Editing or saving a point touched the underlying app" }
            click("Chạy bước / Tiếp")
            waitFor("Edited tap did not reach the actual synthetic receiver") {
                events().length() == 1 &&
                    main(test) {
                        service.calibrationView?.let { it.isShown && it.width > 0 && it.height > 0 } == true
                    }
            }
            test.waitForIdleSync()
            checkInput(events().getJSONObject(0), pixel(editedTap), pixel(editedTap), swipe = false)
            checks += "Edited tap reached the synthetic app at its edited coordinates exactly once."

            inject(automation, path(pixel(swipe.points.last()), pixel(editedEnd)))
            click("Lưu")
            assertControlsWithinEditor()
            check(events().length() == 1) { "Editing or saving a swipe dispatched another gesture" }
            click("Chạy bước / Tiếp")
            waitFor("Edited swipe did not reach the actual synthetic receiver") {
                events().length() == 2 && main(test) { service.calibrationView == null && !service.isWorking }
            }
            checkInput(events().getJSONObject(1), pixel(swipe.points.first()), pixel(editedEnd), swipe = true)
            assertPanel(hidden = false)
            checks += "Edited swipe reached the synthetic app with real move events and the edited endpoint."

            begin(listOf(tap))
            click("Hủy")
            main(test) { check(service.calibrationView == null && !service.isWorking) }
            assertPanel(hidden = false)
            begin(listOf(tap))
            main(test) { service.onInterrupt() }
            assertPanel(hidden = false)
            check(events().length() == 2) { "Cancel or interrupt injected a pending gesture" }
            checks += "Normal panel is hidden/untouchable while editing and restored after completion, Cancel, and interrupt."

            // The foreground resolver must not treat an actual collector activity as an ignorable overlay.
            begin(listOf(tap))
            val switchedButton = currentButton("Chạy bước / Tiếp")
            startCollector()
            waitFor("Switching to the collector activity did not cancel pending teaching") {
                main(test) { service.calibrationView == null && !service.isWorking }
            }
            check(foreground() == service.packageName)
            assertNoReplay(2, switchedButton)
            checks += "The collector activity is rejected as a real underlying app; return never replays its stale step."

            // Exercise the immediate Run guard while a different app is underneath, before event callbacks run.
            startCollector()
            waitFor("Collector application window did not settle before the rejection check") {
                main(test) {
                    val application = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.singleOrNull()
                    application?.isFocused == true && application.root?.packageName?.toString() == service.packageName
                }
            }
            main(test) {
                check(service.foreground() == service.packageName)
                service.beginTeachingDeviceFixture(listOf(tap))
                val root = checkNotNull(service.calibrationView)
                val clipboard = service.getSystemService(android.content.ClipboardManager::class.java)
                val untouched = "Synthetic clipboard must not change without Copy"
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("smoke", untouched))
                check(!buttons(root).single { it.text.toString() == "Chép lý do lỗi" }.isShown)
                buttons(root).single { it.text.toString() == "Chạy bước / Tiếp" }.performClick()
                check(service.calibrationView === root && root.visibility == View.VISIBLE) {
                    "Wrong-underlying-app check let the editor close and enter dispatch"
                }
                val failure = checkNotNull(service.calibrationRefusal)
                check(failure.code == "W02" && failure.report.contains("reason=foreign-or-unknown-application")) {
                    "Wrong underlying app did not report its exact refusal: $failure"
                }
                val title = ((root as ViewGroup).getChildAt(1) as ViewGroup).getChildAt(1) as android.widget.TextView
                check(title.text.contains(failure.code) && title.text.contains(failure.message)) {
                    "Actual preflight refusal stayed generic instead of showing its actionable reason"
                }
                check(clipboard.primaryClip?.getItemAt(0)?.text?.toString() == untouched) {
                    "Refusal copied diagnostics without a user click"
                }
                val copy = buttons(root).single { it.text.toString() == "Chép lý do lỗi" }
                check(copy.isShown)
                copy.performClick()
                val copied =
                    checkNotNull(clipboard.primaryClip?.getItemAt(0)?.text?.toString()) { "Explicit Copy left no text on clipboard" }
                check(copied == failure.report && copied.length <= CalibrationDiagnostics.MAX_REPORT_CHARS) {
                    "Explicit Copy did not place the bounded current refusal report on the clipboard"
                }
                check(!copied.contains(tap.label) && !copied.contains(service.packageName) && !copied.contains(CollectorService.GAME)) {
                    "Refusal report exposed raw window packages or action labels"
                }
                check(copied.contains("Android API") && copied.contains("expectedProfile=") && copied.contains("windowCount="))
                File(diagnostics, "calibration-device-refusal.txt").writeText(copied)
                check(service.calibrationView === root) { "Copying a refusal resumed or closed the pending editor" }
            }
            SystemClock.sleep(CollectorService.SHIELD_MS + 300)
            check(events().length() == 2) { "Wrong-underlying-app guard dispatched a gesture" }
            checks += "Wrong-app refusal shows its exact reason; only explicit Copy writes a bounded metadata-only report, without input."
            main(test) { service.onInterrupt() }
            startFixture()

            for (navigation in listOf("home", "recent_apps")) {
                begin(listOf(tap))
                val stale = currentButton("Chạy bước / Tiếp")
                val button = navigationBounds(navigation)
                assertNavigationOutsideEditor(button)
                inject(automation, listOf(button.exactCenterX() to button.exactCenterY()))
                waitFor("A real $navigation navigation-bar tap did not leave the fixture and cancel teaching") {
                    foreground().let { it != null && it != CollectorService.GAME && it != service.packageName } &&
                        main(test) { service.calibrationView == null && !service.isWorking }
                }
                snapshot("calibration-device-$navigation")
                check(events().length() == 2) { "Navigation unexpectedly injected input into the fixture" }
                assertNoReplay(2, stale)
                checks += "Actual SystemUI $navigation touch leaves the fixture, cancels teaching, and cannot replay on return."
            }
        } finally {
            File(diagnostics, "calibration-device-events.json").writeText(
                runCatching { events().toString(2) }.getOrElse { it.stackTraceToString() }
            )
            File(diagnostics, "calibration-device-checks.txt").writeText(checks.joinToString("\n"))
            File(diagnostics, "calibration-device-safety.txt").writeText(
                main(test) { service.calibrationAttemptDiagnostic + "\n" + service.calibrationSafetyDiagnostic }
            )
            snapshot("calibration-device-final")
            main(test) {
                service.onInterrupt()
                profile?.let {
                    store.reset(it, tap.id)
                    store.reset(it, swipe.id)
                }
            }
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            startCollector()
        }
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun shell(automation: UiAutomation, command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    private fun viewBounds(view: View): Rect {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }

    private fun <T> main(test: Instrumentation, action: () -> T): T {
        val result = AtomicReference<T>()
        test.runOnMainSync { result.set(action()) }
        return result.get()
    }

    private fun waitFor(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        do {
            if (predicate()) return
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < deadline)
        error(message)
    }

    private fun path(start: Pair<Float, Float>, end: Pair<Float, Float>): List<Pair<Float, Float>> = (0..8).map { fraction ->
        val part = fraction / 8f
        (start.first + (end.first - start.first) * part) to (start.second + (end.second - start.second) * part)
    }

    private fun inject(automation: UiAutomation, points: List<Pair<Float, Float>>) {
        val down = SystemClock.uptimeMillis()

        fun event(action: Int, point: Pair<Float, Float>) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                check(automation.injectInputEvent(event, true)) { "Android rejected synthetic UI input" }
            } finally {
                event.recycle()
            }
        }
        event(MotionEvent.ACTION_DOWN, points.first())
        points.drop(1).forEach {
            SystemClock.sleep(25)
            event(MotionEvent.ACTION_MOVE, it)
        }
        SystemClock.sleep(40)
        event(MotionEvent.ACTION_UP, points.last())
    }

    private fun checkInput(event: JSONObject, start: Pair<Float, Float>, end: Pair<Float, Float>, swipe: Boolean) {
        check(event.getInt("action") == MotionEvent.ACTION_UP) { "Injected input was cancelled: $event" }
        for ((name, expected) in listOf("startX" to start.first, "startY" to start.second, "endX" to end.first, "endY" to end.second)) {
            check(abs(event.getDouble(name) - expected) <= 8) { "Wrong injected $name: expected=$expected, event=$event" }
        }
        if (swipe) check(event.getInt("moves") > 0) { "Swipe produced no real motion events: $event" }
    }
}
