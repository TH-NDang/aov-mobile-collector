package vn.ndang.aovcollector

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONArray

/** Real foreign application window and real injection, only in explicitly marked offline CI fixtures. */
internal object TeachingEdgeDeviceSmoke {
    private const val EDGE_PACKAGE = "com.openai.chatgpt"
    private const val EDGE_ACTIVITY = "vn.ndang.chatfixture.EdgeHandleActivity"
    private const val EDGE_MARKER = "vn.ndang.aovcollector.SYNTHETIC_EDGE_HANDLE_FIXTURE"

    fun run(test: Instrumentation, service: CollectorService, automation: UiAutomation) {
        val component = ComponentName(EDGE_PACKAGE, EDGE_ACTIVITY)
        val fixture = service.packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
        check(fixture.metaData?.getBoolean(EDGE_MARKER) == true) { "Refusing real input without the identified synthetic edge fixture" }
        val diagnostics = File(service.filesDir, "smoke").apply { mkdirs() }
        val log = File(diagnostics, "calibration-edge-windows.txt").apply { writeText("") }
        val checks = mutableListOf<String>()
        val display = Rect(service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds)
        var originalFailure: Throwable? = null

        fun gameEvents() = JSONArray(shell(automation, "run-as ${CollectorService.GAME} cat files/gesture-events.json"))

        fun edgeTouches() = shell(automation, "run-as $EDGE_PACKAGE cat files/edge-touches.txt").trim().toInt()

        fun edgeCommand(close: Boolean) {
            val result = shell(automation, "am start -W -n $EDGE_PACKAGE/$EDGE_ACTIVITY --ez close $close")
            check("Error" !in result && "Exception" !in result) { "Synthetic edge fixture command failed: $result" }
        }

        fun windowSnapshot(): List<ForegroundWindow> = main(test) {
            service.windows.map { window ->
                val bounds = Rect().also(window::getBoundsInScreen)
                ForegroundWindow(
                    window.root?.packageName?.toString(),
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                    window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                    window.isFocused,
                    window.isActive,
                    WindowArea(bounds.left, bounds.top, bounds.right, bounds.bottom)
                )
            }
        }

        fun begin(action: CalibrationAction) {
            main(test) { service.beginTeachingDeviceFixture(listOf(action)) }
            waitFor("Edge test editor did not become visible") {
                main(test) { service.calibrationView?.let { it.isShown && it.width > 0 && it.height > 0 } == true }
            }
            test.waitForIdleSync()
            waitFor("Edge test editor accessibility metadata did not match the actual visible overlay") {
                val expected = main(test) { service.calibrationView?.let(::viewBounds) }
                val windows = windowSnapshot()
                log.appendText("editor=$expected\n" + windows.joinToString("\n") + "\n\n")
                expected != null &&
                    !expected.isEmpty &&
                    windows.none { it.accessibilityOverlay && it.packageName != service.packageName } &&
                    windows.any {
                        it.accessibilityOverlay &&
                            it.packageName == service.packageName &&
                            it.bounds == WindowArea(expected.left, expected.top, expected.right, expected.bottom)
                    }
            }
        }

        fun runButton() = main(test) {
            buttons(checkNotNull(service.calibrationView)).single { it.text.toString() == "Chạy bước / Tiếp" }.performClick()
        }

        fun point(x: Double, y: Double) = CalibrationPoint(x / (display.width() - 1), y / (display.height() - 1))

        try {
            main(test) { service.onInterrupt() }
            TeachingDeviceSmoke.openFixture(test, service, automation, reset = true, immersive = true)
            waitFor("Synthetic game did not enter real immersive full-display mode") {
                val state = shell(automation, "run-as ${CollectorService.GAME} cat files/immersive-state.txt")
                File(diagnostics, "calibration-edge-immersive.txt").writeText(state)
                state == "requested=true,statusVisible=false,navigationVisible=false"
            }
            edgeCommand(close = false)
            var edge: ForegroundWindow? = null
            waitFor("Android did not publish a passive foreign edge application over a focused full-display game") {
                val windows = windowSnapshot()
                log.appendText(windows.joinToString("\n") + "\n\n")
                val apps = windows.filter { it.application }
                val game = apps.singleOrNull { it.packageName == CollectorService.GAME }
                edge = apps.singleOrNull { it.packageName == EDGE_PACKAGE }
                apps.size == 2 &&
                    game?.focused == true &&
                    game.active &&
                    game.bounds == WindowArea(display.left, display.top, display.right, display.bottom) &&
                    edge?.let {
                        !it.focused &&
                            !it.active &&
                            it.bounds.left in 0..2 &&
                            it.bounds.right - it.bounds.left in 1..65 &&
                            it.bounds.bottom - it.bounds.top in 1..205
                    } == true
            }
            val edgeBounds = checkNotNull(edge).bounds

            fun awaitSameEdge() {
                waitFor("The real foreign edge window disappeared or changed focus/geometry during input checks") {
                    val windows = windowSnapshot()
                    log.appendText(windows.joinToString("\n") + "\n\n")
                    val apps = windows.filter { it.application }
                    val game = apps.singleOrNull { it.packageName == CollectorService.GAME }
                    val foreign = apps.singleOrNull { it.packageName == EDGE_PACKAGE }
                    apps.size == 2 &&
                        game?.focused == true &&
                        game.active &&
                        game.bounds == WindowArea(display.left, display.top, display.right, display.bottom) &&
                        foreign?.let { !it.focused && !it.active && it.bounds == edgeBounds } == true
                }
            }
            check(gameEvents().length() == 0 && edgeTouches() == 0)

            val safePoint = CalibrationPoint(.75, .65)
            begin(CalibrationAction("smoke.edge.safe", "Synthetic clear interior tap", listOf(safePoint)))
            awaitSameEdge()
            runButton()
            waitFor("Clear interior tap did not reach the synthetic game past the real foreign edge window") {
                gameEvents().length() == 1 && main(test) { service.calibrationView == null && !service.isWorking }
            }
            val event = gameEvents().getJSONObject(0)
            check(event.getInt("action") == MotionEvent.ACTION_UP)
            check(abs(event.getDouble("startX") - safePoint.x * (display.width() - 1)) <= 8)
            check(abs(event.getDouble("startY") - safePoint.y * (display.height() - 1)) <= 8)
            check(abs(event.getDouble("endX") - safePoint.x * (display.width() - 1)) <= 8)
            check(abs(event.getDouble("endY") - safePoint.y * (display.height() - 1)) <= 8)
            check(edgeTouches() == 0) { "Safe interior tap reached the foreign edge receiver" }
            awaitSameEdge()
            checks +=
                "A real passive foreign TYPE_APPLICATION edge window leaves a clear interior tap reaching the full-display synthetic game."

            val centerY = (edgeBounds.top + edgeBounds.bottom) / 2.0
            val blocked = listOf(
                CalibrationAction(
                    "smoke.edge.inside",
                    "Tap inside foreign edge",
                    listOf(point((edgeBounds.left + edgeBounds.right) / 2.0, centerY))
                ),
                CalibrationAction(
                    "smoke.edge.boundary",
                    "Tap on foreign edge boundary",
                    listOf(point(edgeBounds.right.toDouble(), centerY))
                ),
                CalibrationAction("smoke.edge.near", "Tap within edge safety clearance", listOf(point(edgeBounds.right + 10.0, centerY))),
                CalibrationAction(
                    "smoke.edge.crossing",
                    "Swipe crossing foreign edge with clear endpoints",
                    listOf(point(edgeBounds.right + 80.0, edgeBounds.top - 60.0), point(0.0, edgeBounds.bottom + 60.0)),
                    durationMs = 400
                )
            )
            for (action in blocked) {
                awaitSameEdge()
                begin(action)
                val pending = main(test) { service.calibrationView }
                runButton()
                main(test) {
                    check(service.calibrationView === pending && pending?.visibility == View.VISIBLE) {
                        "Unsafe ${action.id} passed preflight and closed the editor"
                    }
                    check(service.calibrationRefusal?.code == "W11") {
                        "Unsafe ${action.id} did not report edge collision: ${service.calibrationRefusal}"
                    }
                }
                SystemClock.sleep(CollectorService.SHIELD_MS + 300)
                check(gameEvents().length() == 1 && edgeTouches() == 0) { "Unsafe ${action.id} injected into a synthetic receiver" }
                checks += "${action.id}: W11 before editor close; neither receiver received input."
                main(test) { service.onInterrupt() }
            }
        } catch (error: Throwable) {
            originalFailure = error
            throw error
        } finally {
            val reportingFailure = runCatching {
                File(diagnostics, "calibration-edge-checks.txt").writeText(checks.joinToString("\n"))
                File(diagnostics, "calibration-edge-safety.txt").writeText(main(test) { service.calibrationSafetyDiagnostic })
                File(diagnostics, "calibration-edge-game-events.json").writeText(
                    runCatching {
                        gameEvents().toString(2)
                    }.getOrElse { it.toString() }
                )
                File(diagnostics, "calibration-edge-touches.txt").writeText(
                    runCatching {
                        edgeTouches().toString()
                    }.getOrElse { it.toString() }
                )
                automation.takeScreenshot()?.let { image ->
                    File(diagnostics, "calibration-edge-final.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                }
            }.exceptionOrNull()
            val cleanupFailure = runCatching {
                main(test) { service.onInterrupt() }
                edgeCommand(close = true)
                waitFor("Synthetic foreign edge application did not close") {
                    windowSnapshot().none {
                        it.application &&
                            it.packageName == EDGE_PACKAGE
                    }
                }
                TeachingDeviceSmoke.openFixture(test, service, automation, immersive = false)
                waitFor("Synthetic game did not restore visible system bars after edge smoke") {
                    shell(automation, "run-as ${CollectorService.GAME} cat files/immersive-state.txt") ==
                        "requested=false,statusVisible=true,navigationVisible=true"
                }
                service.startActivity(
                    Intent.makeMainActivity(ComponentName(service, MainActivity::class.java))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
                waitFor("Collector did not return after edge smoke cleanup") { main(test) { service.foreground() == service.packageName } }
            }.exceptionOrNull()
            val extraFailures = listOfNotNull(reportingFailure, cleanupFailure)
            val primary = originalFailure
            if (primary != null) {
                extraFailures.forEach(primary::addSuppressed)
            } else if (extraFailures.isNotEmpty()) {
                extraFailures.drop(1).forEach(extraFailures.first()::addSuppressed)
                throw extraFailures.first()
            }
        }
    }

    private fun viewBounds(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun shell(automation: UiAutomation, command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }

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
}
