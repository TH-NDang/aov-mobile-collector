package vn.ndang.aovcollector

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic editor only; tests never inject a gesture into a game or sign in. */
internal object GestureCalibrationSmoke {
    fun run(test: Instrumentation, service: CollectorService) {
        val action = CalibrationAction("smoke.tap", "Điểm thử", listOf(CalibrationPoint(.4, .3)))
        val step = CalibrationStep("Thao tác mặc định", "Kéo để chỉnh", action)
        var tests = 0
        var saves = 0
        var nav = -1
        var saved = action.points
        var closed = 0
        lateinit var editor: GestureCalibrationEditor

        fun buttons(view: View): List<Button> = if (view is ViewGroup) {
            (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        } else if (view is Button) {
            listOf(view)
        } else {
            emptyList()
        }

        fun click(text: String) = buttons(checkNotNull(editor.view)).single { it.text.toString() == text }.performClick()
        test.runOnMainSync {
            editor = GestureCalibrationEditor(
                service, step, 1, 3, action.points,
                navigate = { nav = it }, choose = {}, enterLobby = {}, scan = { it(null) }, resolved = { it.points },
                save = { _, points ->
                    saves++
                    saved = points
                    true
                }, reset = { true },
                test = { _, _, done ->
                    tests++
                    done(true)
                }, closed = { closed++ }
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
                click("Trước")
                check(nav == 0 && tests == 0)
                click("Tiếp")
                check(nav == 2 && tests == 0)
                click("Đổi góc bảng")
                check(tests == 0)
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
                check(saved == action.points && tests == 0)
                click("Thử bước")
                check(tests == 1 && editor.view != null)
                editor.shield(true)
                val params = root.layoutParams as WindowManager.LayoutParams
                check(root.visibility == View.INVISIBLE)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                editor.shield(false)
                check(root.visibility == View.VISIBLE)
                click("Hủy")
                editor.shield(false)
                check(editor.view == null && closed == 1 && tests == 1)
            }
        } finally {
            test.runOnMainSync { editor.close() }
        }

        // Exercise the actual service screenshot shield and interrupted editor lifecycle.
        val macroFile = MacroStore.current(service)
        val original = macroFile.readText()
        val oldRun = service.getSharedPreferences("collector", 0).getString("run", "").orEmpty()
        val oldFolder = java.io.File(service.filesDir, "captures/$oldRun")

        fun oldHistory() = if (oldRun.isBlank() ||
            !oldFolder.exists()
        ) {
            emptyMap()
        } else {
            oldFolder.walkTopDown().filter { it.isFile }.associate {
                it.relativeTo(oldFolder).path to java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
            }
        }
        val before = oldHistory()
        try {
            test.runOnMainSync {
                macroFile.writeText(BuiltInMacros.create("", 1, false))
                service.showCalibration()
                check(service.calibrationView != null) { "Built-in defaults unavailable before a run" }
                check(!service.prepareEdit()) { "Data operations allowed while editor is active" }
            }
            test.waitForIdleSync()
            test.uiAutomation.takeScreenshot()?.let { bitmap ->
                val file = java.io.File(service.filesDir, "smoke/calibration-defaults.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val captured = CountDownLatch(1)
            val hasBitmap = java.util.concurrent.atomic.AtomicBoolean(false)
            test.runOnMainSync {
                service.grab(anyApp = true) { bitmap ->
                    hasBitmap.set(bitmap != null)
                    bitmap?.recycle()
                    captured.countDown()
                }
                val root = checkNotNull(service.calibrationView)
                check(root.visibility == View.INVISIBLE) { "Calibration leaked into screenshot" }
            }
            check(captured.await(8, TimeUnit.SECONDS) && hasBitmap.get()) { "Editor screenshot failed or did not complete" }
            test.runOnMainSync {
                check(service.calibrationView?.visibility == View.VISIBLE)
                service.onInterrupt()
                check(service.calibrationView == null) { "Interrupted editor survived" }
                service.showCalibration()
                check(service.calibrationView != null)
                service.onConfigurationChanged(service.resources.configuration)
                check(service.calibrationView == null) { "Rotation retained old profile editor" }
                check(oldHistory() == before) { "Idle teaching modified old run history" }
                if (service.packageManager.getLaunchIntentForPackage(CollectorService.GAME) == null) {
                    service.showCalibration()
                    buttons(checkNotNull(service.calibrationView)).single { it.text.toString() == "Vào sảnh" }.performClick()
                    check(service.calibrationView == null && !service.isWorking)
                    check(oldHistory() == before) { "Failed teaching entry modified old run history" }
                }
            }
        } finally {
            test.runOnMainSync {
                service.onInterrupt()
                macroFile.writeText(original)
            }
        }
    }
}
