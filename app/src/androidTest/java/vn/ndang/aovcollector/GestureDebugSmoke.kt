package vn.ndang.aovcollector

import android.accessibilityservice.GestureDescription
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic gestures only; no game, credentials, external storage or uploads. */
internal object GestureDebugSmoke {
    fun run(test: Instrumentation, service: CollectorService) {
        val prefs = service.getSharedPreferences("ui", 0)
        val original = prefs.getBoolean(GestureDebugOverlay.PREFERENCE, false)
        val overlay = GestureDebugOverlay(service, Handler(Looper.getMainLooper()))
        val tap = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(Path().apply { moveTo(120f, 240f) }, 0, 100)
        ).build()
        val swipe = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(
                Path().apply {
                    moveTo(160f, 300f)
                    lineTo(320f, 420f)
                },
                0,
                300
            )
        ).build()
        try {
            test.runOnMainSync {
                prefs.edit().putBoolean(GestureDebugOverlay.PREFERENCE, false).commit()
                service.updateGestureDebug()
                check(service.gestureDebugView == null) { "Disabled debug allocated a window" }
                overlay.show(listOf(tap), true, null, null)
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                val view = checkNotNull(overlay.overlayView)
                val params = view.layoutParams as WindowManager.LayoutParams
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                check(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                check(view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val origin = IntArray(2)
                view.getLocationOnScreen(origin)

                fun hasCyan(x: Int, y: Int): Boolean = (-3..3).any { dx ->
                    (-3..3).any { dy ->
                        val px = x - origin[0] + dx
                        val py = y - origin[1] + dy
                        px in 0 until bitmap.width &&
                            py in 0 until bitmap.height &&
                            bitmap.getPixel(px, py).let {
                                Color.green(it) > 200 && Color.blue(it) > 200 && Color.red(it) < 40 && Color.alpha(it) > 200
                            }
                    }
                }
                view.draw(Canvas(bitmap))
                val radius = (14 * service.resources.displayMetrics.density).toInt()
                check(hasCyan(120 + radius, 240)) { "Tap circle differs from dispatch coordinates" }
                val screen = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
                overlay.show(listOf(tap), true, GameScreens.Word("Synthetic target", .1, .2, .3, .4), "Chọn chế độ")
                bitmap.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(bitmap))
                check(hasCyan((screen.width() * .2).toInt(), (screen.height() * .2).toInt())) {
                    "Recognized target bounds differ from screenshot coordinates"
                }
                overlay.show(listOf(swipe), false, null, "Vuốt thử")
                bitmap.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(bitmap))
                check(hasCyan(240, 360)) { "Swipe midpoint differs from dispatch path" }
                bitmap.recycle()
                overlay.shield(true)
                check(view.visibility == View.INVISIBLE)
                overlay.show(listOf(tap), true, null, null)
                check(view.visibility == View.INVISIBLE) { "New gesture leaked into an outstanding screenshot" }
                overlay.shield(false)
                check(view.visibility == View.VISIBLE)
                overlay.clear()
                overlay.shield(false)
                check(overlay.overlayView == null) { "Cancelled debug reappeared" }
                overlay.shield(true)
                overlay.show(listOf(tap), true, null, null)
            }
            SystemClock.sleep(GestureDebugOverlay.DISPLAY_MS + 100)
            test.runOnMainSync {
                overlay.shield(false)
                check(overlay.overlayView == null) { "Expired debug reappeared after capture" }
                prefs.edit().putBoolean(GestureDebugOverlay.PREFERENCE, true).commit()
                service.updateGestureDebug()
            }
            val touched = CountDownLatch(1)
            val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
            test.runOnMainSync {
                service.touchGame(tap, true) {
                    accepted.set(it)
                    touched.countDown()
                }
            }
            check(touched.await(5, TimeUnit.SECONDS) && accepted.get()) { "Debug gesture did not complete successfully" }
            val captured = CountDownLatch(1)
            val clean = java.util.concurrent.atomic.AtomicBoolean(false)
            val restored = java.util.concurrent.atomic.AtomicBoolean(false)
            test.runOnMainSync {
                check(service.gestureDebugView?.visibility == View.VISIBLE) { "Debug was not visible before capture" }
                service.grab(anyApp = true) { bitmap ->
                    if (bitmap != null) {
                        val radius = (14 * service.resources.displayMetrics.density).toInt()
                        val pixel = bitmap.getPixel(120 + radius, 240)
                        clean.set(!(Color.green(pixel) > 200 && Color.blue(pixel) > 200 && Color.red(pixel) < 40))
                        bitmap.recycle()
                    }
                    restored.set(service.gestureDebugView?.visibility != View.INVISIBLE)
                    captured.countDown()
                }
                check(service.gestureDebugView?.visibility != View.VISIBLE) { "Capture did not hide gesture debug" }
            }
            check(captured.await(8, TimeUnit.SECONDS)) { "Debug screenshot did not finish" }
            check(clean.get()) { "Gesture marker leaked into screenshot or capture failed" }
            check(restored.get()) { "Debug did not restore after capture" }
            val interrupted = CountDownLatch(1)
            test.runOnMainSync {
                service.touchGame(tap, true) { interrupted.countDown() }
                service.onInterrupt()
                check(service.gestureDebugView == null) { "Service interruption retained debug" }
            }
            check(interrupted.await(5, TimeUnit.SECONDS)) { "Interrupted gesture did not finish" }
            test.runOnMainSync {
                check(service.gestureDebugView == null) { "Delayed gesture redrew after interruption" }
            }
        } finally {
            test.runOnMainSync {
                overlay.clear()
                prefs.edit().putBoolean(GestureDebugOverlay.PREFERENCE, original).commit()
                service.updateGestureDebug()
            }
        }
    }
}
