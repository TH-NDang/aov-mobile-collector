package vn.ndang.aovcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import java.util.concurrent.atomic.AtomicReference

/** Real overlay and SystemUI input, gated by the positively identified offline game fixture. */
internal object QuickSettingsSmoke {
    fun run(test: Instrumentation, service: CollectorService, automation: UiAutomation) {
        fun field(name: String) = CollectorService::class.java.getDeclaredField(name).apply { isAccessible = true }

        fun invoke(name: String) = CollectorService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
        // Never interrupt a real or paused run to make a smoke test fit its preconditions.
        main(test) {
            check(!service.isWorking && invoke("canStop") == false && field("teaching").getBoolean(service).not())
            check(field("activeDialog").get(service) == null && field("macroPicker").get(service) == null)
            check(!field("libraryBusy").getBoolean(service))
            check(service.captureProgressView == null)
        }
        // This helper verifies fixture activity metadata before launching or injecting anything.
        TeachingDeviceSmoke.openFixture(test, service, automation, immersive = false)
        val ui = service.getSharedPreferences("ui", 0)
        val collector = service.getSharedPreferences("collector", 0)
        val oldUi = ui.all.toMap()
        val oldCollector = collector.all.toMap()
        val file = MacroStore.current(service)
        val oldMacro = if (file.exists()) file.readBytes() else null
        val stateNames = listOf("compact", "quickSettingsOpen", "selectionCache", "selectionStamp", "busy", "teaching", "libraryBusy")
        val oldState = main(test) { stateNames.associateWith { field(it).get(service) } }
        val hadPanel = main(test) { service.panelView != null }
        val oldParams = main(test) {
            (field("panelParams").get(service) as? WindowManager.LayoutParams)?.let {
                WindowManager.LayoutParams().apply { copyFrom(it) }
            }
        }
        val originalFlags = automation.serviceInfo.flags
        var failure: Throwable? = null

        fun rebuild() = main(test) { invoke("rebuildPanel") }

        fun nodes(): List<View> = main(test) { descendants(checkNotNull(service.panelView)) }

        fun button(label: String) = nodes().filterIsInstance<Button>().single { it.text.toString() == label }

        fun checkbox(label: String) = nodes().filterIsInstance<CheckBox>().single { it.text.toString() == label }

        fun clickDescription(description: String) {
            val view = nodes().single { it.contentDescription?.toString() == description }
            waitFor("Panel control did not lay out") { main(test) { view.isShown && view.width > 0 && view.height > 0 } }
            val rect = main(test) { bounds(view) }
            tap(automation, rect)
            test.waitForIdleSync()
        }

        fun open() {
            clickDescription("Mở cài đặt nhanh")
            waitFor("Gear did not open settings") { nodes().filterIsInstance<CheckBox>().size == 2 }
        }

        fun close() {
            main(test) { check(button("Đóng cài đặt").performClick()) }
            waitFor("Close did not return to toolbar") { nodes().none { it is CheckBox } }
        }

        fun assertWindow() {
            main(test) {
                val panel = checkNotNull(service.panelView)
                val params = panel.layoutParams as WindowManager.LayoutParams
                check(params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
                check(params.width > 0 && params.height == WindowManager.LayoutParams.WRAP_CONTENT)
                val display = checkNotNull(service.getSystemService(WindowManager::class.java)).currentWindowMetrics.bounds
                val rect = bounds(panel)
                check(rect.width() < display.width() && rect.height() < display.height()) { "Settings became a full-screen interceptor" }
                check(display.contains(rect)) { "Settings escaped the display bounds" }
            }
        }
        val smart = "Nhận diện sảnh đang mở"
        val debug = "Hiện điểm chạm và đường vuốt"
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            val fixture =
                Macro("""{"name":"quick_settings_fixture","targetPackage":"${CollectorService.GAME}","steps":[{"type":"wait","ms":0}]}""")
            file.writeText(fixture.raw)
            collector.edit().clear().commit()
            main(test) {
                field("selectionCache").set(service, null)
                service.showPanel()
            }
            waitFor("Expanded toolbar did not lay out") { main(test) { service.panelView?.height?.let { it > 0 } == true } }
            assertWindow()
            val headerDescriptions = listOf(
                "Mở cài đặt nhanh",
                "Chỉnh từng bước",
                "Mở hoặc đóng tiến trình ảnh",
                "Thu gọn bảng nổi"
            )
            main(test) {
                val headerButtons = headerDescriptions.map { description ->
                    nodes().single { it.contentDescription?.toString() == description }
                }
                val minimum = (48 * service.resources.displayMetrics.density).toInt()
                val rectangles = headerButtons.map(::bounds)
                headerButtons.forEach { check(it.width >= minimum && it.height >= minimum) { "Header target smaller than 48dp" } }
                rectangles.forEachIndexed { at, rect ->
                    check(bounds(checkNotNull(service.panelView)).contains(rect))
                    rectangles.drop(at + 1).forEach { check(!Rect.intersects(rect, it)) { "Header actions overlap" } }
                }
                check(nodes().filterIsInstance<Button>().none { it.text.toString() in listOf("Cài đặt", "Chỉnh từng bước") })
                // The pencil retains showCalibration's busy guard rather than starting a competing action.
                field("busy").setBoolean(service, true)
            }
            clickDescription("Chỉnh từng bước")
            main(test) {
                check(!field("teaching").getBoolean(service)) { "Pencil started teaching while an action was busy" }
                field("busy").setBoolean(service, false)
            }
            clickDescription("Mở hoặc đóng tiến trình ảnh")
            waitFor("Image progress panel did not open") {
                main(test) { service.captureProgressView?.isShown == true && service.captureProgressView!!.height > 0 }
            }
            val progressClose = main(test) {
                val progressPanel = checkNotNull(service.captureProgressView)
                val children = descendants(progressPanel)
                val closeControl = children.single { it.contentDescription?.toString() == "Đóng tiến trình ảnh" }
                val status = children.filterIsInstance<android.widget.TextView>()
                    .single { it.contentDescription?.toString() == "Trạng thái tiến trình ảnh" }
                check(status.minLines == 5 && status.maxLines == 5) { "Progress status can resize during polling" }
                val minimum = (48 * service.resources.displayMetrics.density).toInt()
                check(closeControl.width >= minimum && closeControl.height >= minimum)
                check(closeControl.background is android.graphics.drawable.RippleDrawable)
                check(bounds(progressPanel).contains(bounds(closeControl)))
                closeControl
            }
            tap(automation, main(test) { bounds(progressClose) })
            waitFor("Image progress close icon did not dismiss panel") { main(test) { service.captureProgressView == null } }
            check(nodes().any { it.contentDescription?.toString() == "Mở cài đặt nhanh" })
            open()
            assertWindow()
            val previousSmart = ui.getBoolean("smartGameEntry", true)
            val previousDebug = ui.getBoolean(GestureDebugOverlay.PREFERENCE, false)
            main(test) {
                check(checkbox(smart).isEnabled && button("Tài khoản").isEnabled)
                checkbox(smart).performClick()
                checkbox(debug).performClick()
            }
            val smartValue = ui.getBoolean("smartGameEntry", true)
            val debugValue = ui.getBoolean(GestureDebugOverlay.PREFERENCE, false)
            check(smartValue != previousSmart && debugValue != previousDebug) { "Settings toggles were not persisted" }
            val detachedSmart = checkbox(smart)
            val detachedDebug = checkbox(debug)
            val detachedClose = button("Đóng cài đặt")
            close()
            main(test) {
                detachedSmart.isChecked = !smartValue
                detachedDebug.isChecked = !debugValue
                detachedClose.performClick()
                check(ui.getBoolean("smartGameEntry", true) == smartValue)
                check(ui.getBoolean(GestureDebugOverlay.PREFERENCE, false) == debugValue)
            }
            open()
            main(test) {
                val current = service.panelView
                detachedClose.performClick()
                check(service.panelView === current) { "Stale close dismissed a newer settings session" }
                check(checkbox(smart).isChecked == smartValue)
                check(checkbox(debug).isChecked == debugValue)
            }
            // Detached callbacks must recheck current eligibility rather than trusting an old enabled widget.
            val staleSmart = checkbox(smart)
            main(test) { field("busy").setBoolean(service, true) }
            rebuild()
            main(test) {
                check(!checkbox(smart).isEnabled && !button("Tài khoản").isEnabled)
                staleSmart.isChecked = !smartValue
                check(ui.getBoolean("smartGameEntry", true) == smartValue) { "Stale toggle reinterpreted an active run" }
                field("busy").setBoolean(service, false)
            }
            collector.edit().putString("hash", fixture.hash).putString("run", "quick-settings-synthetic")
                .putInt("step", 0).putBoolean("stopped", false).commit()
            rebuild()
            main(test) { check(!checkbox(smart).isEnabled && !button("Tài khoản").isEnabled) }
            collector.edit().clear().commit()
            for (blocked in listOf("teaching", "libraryBusy")) {
                main(test) { field(blocked).setBoolean(service, true) }
                rebuild()
                main(test) {
                    check(!checkbox(smart).isEnabled && !button("Tài khoản").isEnabled)
                    field(blocked).setBoolean(service, false)
                }
            }
            rebuild()
            close()
            val expandedWidth = main(test) { checkNotNull(service.panelView).width }
            clickDescription("Thu gọn bảng nổi")
            waitFor("Compact toolbar did not resize") {
                main(test) { checkNotNull(service.panelView).width in 1 until expandedWidth }
            }

            fun assertCompactSize() = main(test) {
                val panel = checkNotNull(service.panelView)
                val expected = PanelOverlayGeometry.compactSize(
                    Ui.dp(service, PanelOverlayGeometry.HANDLE_SIZE_DP),
                    Ui.dp(service, PanelOverlayGeometry.COMPACT_PADDING_DP)
                )
                check(panel.width == expected && panel.height == expected) { "Compact window does not fit its handle and padding" }
                val handle = descendants(panel).single { it.contentDescription?.toString() == "Mở bảng điều khiển thu thập" }
                val minimum = Ui.dp(service, PanelOverlayGeometry.HANDLE_SIZE_DP)
                check(handle.width >= minimum && handle.height >= minimum) { "Compact handle lost its 48 dp touch target" }
            }
            assertCompactSize()
            check(nodes().none { it.contentDescription?.toString() in headerDescriptions }) {
                "Compact toolbar still exposes settings or editing actions"
            }
            assertWindow()
            clickDescription("Mở bảng điều khiển thu thập")
            waitFor("Expanded toolbar did not restore header actions") {
                nodes().count { it.contentDescription?.toString() in headerDescriptions } == 4
            }
            open()
            assertWindow()
            close()
            // Both open settings and the closed compact toolbar must leave real navigation usable.
            for (settingsOpen in listOf(true, false)) {
                for (navigation in listOf("home", "recent_apps")) {
                    TeachingDeviceSmoke.openFixture(test, service, automation, immersive = false)
                    if (settingsOpen) open() else clickDescription("Thu gọn bảng nổi")
                    assertWindow()
                    val nav = navigationBounds(automation, navigation)
                    check(!Rect.intersects(main(test) { bounds(checkNotNull(service.panelView)) }, nav))
                    tap(automation, nav)
                    waitFor("Real $navigation touch was intercepted by the floating panel") {
                        main(test) { service.foreground().let { it != null && it != CollectorService.GAME && it != service.packageName } }
                    }
                    if (settingsOpen) close() else clickDescription("Mở bảng điều khiển thu thập")
                }
            }
            open()
            val hiddenSmart = checkbox(smart)
            val hiddenDebug = checkbox(debug)
            val hiddenClose = button("Đóng cài đặt")
            main(test) {
                invoke("hidePanel")
                hiddenSmart.isChecked = !smartValue
                hiddenDebug.isChecked = !debugValue
                hiddenClose.performClick()
                check(service.panelView == null) { "Detached close resurrected a hidden panel" }
                check(ui.getBoolean("smartGameEntry", true) == smartValue)
                check(ui.getBoolean(GestureDebugOverlay.PREFERENCE, false) == debugValue)
                service.showPanel()
            }

            fun pointer(action: Int, x: Float, y: Float, down: Long, scenario: String, requireAccepted: Boolean = true) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    val accepted = automation.injectInputEvent(event, true)
                    val diagnostic = "$scenario action=${MotionEvent.actionToString(action)} accepted=$accepted"
                    android.util.Log.i("QuickSettingsSmoke", diagnostic)
                    check(accepted || !requireAccepted) { "Pointer injection rejected: $diagnostic" }
                } finally {
                    event.recycle()
                }
                SystemClock.sleep(100)
            }
            // Dragging changes only panel visibility/position. No capture files are deletion targets.
            val captureNames = java.io.File(service.filesDir, "captures").walkTopDown()
                .filter { it.isFile }.map { it.relativeTo(service.filesDir).path }.toSet()
            for (compactMode in listOf(false, true)) {
                for (ending in listOf("outside", "cancel", "configuration", "trash")) {
                    val scenario = "compact=$compactMode ending=$ending"
                    main(test) {
                        field("compact").setBoolean(service, compactMode)
                        invoke("rebuildPanel")
                    }
                    val description = if (compactMode) "Mở bảng điều khiển thu thập" else "Kéo để di chuyển bảng nổi"
                    val handle = nodes().single { it.contentDescription?.toString() == description }
                    waitFor("Drag handle not laid out") { main(test) { handle.width > 0 && handle.isShown } }
                    if (compactMode) assertCompactSize()
                    val from = main(test) { bounds(handle) }
                    val display = main(test) { service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds }
                    val down = SystemClock.uptimeMillis()
                    pointer(MotionEvent.ACTION_DOWN, from.exactCenterX(), from.exactCenterY(), down, scenario)
                    val stageX = display.width() * if (from.exactCenterX() < display.exactCenterX()) .75f else .25f
                    pointer(MotionEvent.ACTION_MOVE, stageX, display.exactCenterY(), down, scenario)
                    waitFor("Trash target not shown after drag slop") {
                        main(test) { service.panelTrashView?.let { it.isShown && it.width > 0 } == true }
                    }
                    val trash = main(test) {
                        val target = checkNotNull(service.panelTrashView)
                        val params = target.layoutParams as WindowManager.LayoutParams
                        check(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                        check(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                        bounds(target).also { rect ->
                            check(display.contains(rect))
                            val metrics = service.getSystemService(WindowManager::class.java).currentWindowMetrics
                            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
                            )
                            val expected = PanelOverlayGeometry.trash(
                                display.width(),
                                display.height(),
                                insets.left,
                                insets.top,
                                insets.right,
                                insets.bottom,
                                Ui.dp(service, 64),
                                Ui.dp(service, 80)
                            )
                            check(rect.top == expected.y && rect.bottom == expected.y + expected.height) { "Trash target is too low" }
                        }
                    }
                    val outside = main(test) {
                        val metrics = service.getSystemService(WindowManager::class.java).currentWindowMetrics
                        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                            android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
                        )
                        val xs = listOf(display.left + insets.left + 1, display.right - insets.right - 2)
                        val ys = listOf(display.top + insets.top + 1, display.bottom - insets.bottom - 2)
                        xs.flatMap { xx -> ys.map { yy -> xx to yy } }.first { (xx, yy) -> !trash.contains(xx, yy) }
                    }
                    val x = if (ending == "outside") outside.first.toFloat() else trash.exactCenterX()
                    val y = if (ending == "outside") outside.second.toFloat() else trash.exactCenterY()
                    if (ending == "outside") check(!trash.contains(x.toInt(), y.toInt()))
                    pointer(MotionEvent.ACTION_MOVE, x, y, down, scenario)
                    if (ending != "outside") main(test) { check(service.panelTrashView?.isActivated == true) }
                    if (ending == "configuration") {
                        val oldPanel = main(test) { checkNotNull(service.panelView) }
                        val oldTarget = main(test) { checkNotNull(service.panelTrashView) }
                        // Rotation tears down the touch-owning window and its active drag target.
                        main(test) { service.onConfigurationChanged(android.content.res.Configuration(service.resources.configuration)) }
                        waitFor("Configuration did not replace panel and release drag target: $scenario") {
                            main(test) {
                                !oldPanel.isAttachedToWindow &&
                                    !handle.isAttachedToWindow &&
                                    !oldTarget.isAttachedToWindow &&
                                    service.panelTrashView == null &&
                                    service.panelView?.let { it !== oldPanel && it.isAttachedToWindow } == true
                            }
                        }
                        // Android may already have cancelled this stream when its old window was removed.
                        // Only this proven-detached lifecycle cleanup may reject the final cancellation.
                        pointer(MotionEvent.ACTION_CANCEL, x, y, down, scenario, requireAccepted = false)
                        main(test) {
                            val replacement = checkNotNull(service.panelView)
                            check(replacement !== oldPanel && replacement.isAttachedToWindow)
                            val lateUp = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
                            try {
                                handle.dispatchTouchEvent(lateUp)
                            } finally {
                                lateUp.recycle()
                            }
                            check(service.panelView === replacement && replacement.isAttachedToWindow) {
                                "Detached drag callback closed the replacement panel"
                            }
                            check(service.panelTrashView == null && !oldTarget.isAttachedToWindow)
                        }
                    } else {
                        pointer(if (ending == "cancel") MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x, y, down, scenario)
                    }
                    waitFor("Trash target leaked after drag") { main(test) { service.panelTrashView == null } }
                    main(test) {
                        check((service.panelView == null) == (ending == "trash")) { "Unexpected drop/cancel visibility" }
                        if (service.panelView == null) service.showPanel()
                    }
                    check(captureNames.all { java.io.File(service.filesDir, it).isFile }) { "Drop removed captured files" }
                }
            }
            // Immediate settings deliberately have no staged Save/Cancel lifecycle to replay.
            check(nodes().filterIsInstance<Button>().none { it.text.toString() in listOf("Lưu", "Hủy") })
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val cleanup = runCatching {
                if (oldMacro != null) file.writeBytes(oldMacro) else check(!file.exists() || file.delete())
                restore(collector, oldCollector)
                restore(ui, oldUi)
                main(test) {
                    invoke("hidePanel")
                    oldState.forEach { (name, value) -> field(name).set(service, value) }
                    field("panelParams").set(service, oldParams)
                    if (hadPanel) invoke("rebuildPanel") else invoke("hidePanel")
                    service.updateGestureDebug()
                }
                // Rebuilding/hiding may persist panel metadata; restore exact preference presence as well as values.
                restore(ui, oldUi)
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
                service.startActivity(
                    Intent.makeMainActivity(ComponentName(service, MainActivity::class.java))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }.exceptionOrNull()
            if (cleanup != null) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unexpected preference type")
            }
        }
        check(editor.commit()) { "Could not restore smoke-test preferences" }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun bounds(view: View): Rect {
        val xy = IntArray(2)
        view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }

    private fun navigationBounds(automation: UiAutomation, name: String): Rect {
        var result: Rect? = null
        waitFor("Actual SystemUI navigation button missing: $name (requires three-button fixture navigation)") {
            result = automation.windows.asSequence().mapNotNull { it.root }.flatMap {
                it.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$name").asSequence()
            }.filter { it.isVisibleToUser }.map { Rect().also(it::getBoundsInScreen) }.firstOrNull { !it.isEmpty }
            result != null
        }
        return checkNotNull(result)
    }

    private fun tap(automation: UiAutomation, rect: Rect) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                check(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            SystemClock.sleep(40)
        }
    }

    private fun <T> main(test: Instrumentation, action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        test.runOnMainSync {
            try {
                result.set(action())
            } catch (failure: Throwable) {
                error.set(failure)
            }
        }
        error.get()?.let { throw it }
        return result.get()
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        do {
            if (condition()) return
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < deadline)
        error(message)
    }
}
