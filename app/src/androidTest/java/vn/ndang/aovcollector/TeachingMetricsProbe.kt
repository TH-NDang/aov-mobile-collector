package vn.ndang.aovcollector

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

/** Fixed, no-input experiment. Recording evidence is not a calibration safety pass. */
internal object TeachingMetricsProbe {
    fun run(test: Instrumentation, activity: Activity, automation: UiAutomation): String {
        val directory = File(activity.filesDir, "smoke").apply { mkdirs() }
        val report = File(directory, "teaching-metrics-probe.json")
        val records = JSONArray()
        val document = JSONObject().put("scope", "one landscape rotation and one service rebind; no input")
            .put("samples", records).put("outcome", "incomplete")
        val overlays = mutableListOf<Pair<WindowManager, View>>()
        val initial = checkNotNull(CollectorService.current)
        check(!initial.isWorking) { "Metrics probe requires an idle connected service" }
        var service = initial
        var displayContext = service.createDisplayContext(service.getSystemService(WindowManager::class.java).defaultDisplay)
        var authorizedView: View? = null
        var displayView: View? = null
        var displayAttachment = "not-attempted"
        val stableSources = mutableListOf<Set<String>>()
        var primaryFailure: Throwable? = null

        fun persist() = report.writeText(document.toString(2))

        fun snapshot(label: String, stableSample: Boolean = false) {
            val sources = main(test) {
                val wm = service.getSystemService(WindowManager::class.java)
                val fresh = displayContext.getSystemService(WindowManager::class.java)
                val physical = Point().also(wm.defaultDisplay::getRealSize)
                val physicalBounds = Rect(0, 0, physical.x, physical.y)
                val current = wm.currentWindowMetrics
                val freshCurrent = fresh.currentWindowMetrics
                val attached = authorizedView?.let(::viewBounds)
                val freshAttached = displayView?.let(::viewBounds)
                val record = JSONObject().put("label", label).put("uptimeMs", SystemClock.uptimeMillis())
                    .put("rotation", wm.defaultDisplay.rotation).put("physicalDisplay", rect(physicalBounds))
                    .put("serviceCurrent", metrics(current)).put("serviceMaximum", metrics(wm.maximumWindowMetrics))
                    .put("displayContextCurrent", metrics(freshCurrent)).put("displayContextMaximum", metrics(fresh.maximumWindowMetrics))
                    .put("serviceResources", configuration(service)).put("applicationResources", configuration(service.applicationContext))
                    .put("displayContextResources", configuration(displayContext)).put("activityResources", configuration(activity))
                    .put("activityDecor", viewState(activity.window.decorView))
                    .put("authorizedOverlay", authorizedView?.let(::viewState) ?: JSONObject.NULL)
                    .put("displayContextOverlay", displayView?.let(::viewState) ?: JSONObject.NULL)
                    .put("displayContextAttachment", displayAttachment)
                val windows = JSONArray()
                for (window in service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }) {
                    val owner = when (window.root?.packageName?.toString()) {
                        service.packageName -> "COLLECTOR"
                        CollectorService.GAME -> "GAME"
                        null -> "UNKNOWN"
                        else -> "OTHER"
                    }
                    windows.put(
                        JSONObject().put("owner", owner).put("bounds", rect(Rect().also(window::getBoundsInScreen)))
                            .put("focused", window.isFocused).put("active", window.isActive)
                    )
                }
                record.put("applicationWindows", windows)
                val matching = buildSet {
                    if (current.bounds == physicalBounds &&
                        attached == physicalBounds &&
                        authorizedView?.rootWindowInsets?.let { insets(it).toString() == insets(current.windowInsets).toString() } == true
                    ) {
                        add("authorized-service")
                    }
                    if (freshCurrent.bounds == physicalBounds &&
                        freshAttached == physicalBounds &&
                        displayView?.rootWindowInsets?.let { insets(it).toString() == insets(freshCurrent.windowInsets).toString() } == true
                    ) {
                        add("display-context")
                    }
                }
                record.put("matchingAttachedSources", JSONArray(matching.toList()))
                records.put(record)
                matching
            }
            if (stableSample) stableSources += sources
            persist()
        }

        fun attach(context: Context, manager: WindowManager): View = main(test) {
            val view = View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                screenOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
            manager.addView(view, params)
            overlays += manager to view
            view
        }

        try {
            snapshot("before-rotation")
            check(main(test) { activity.window.decorView.height > activity.window.decorView.width }) {
                "Metrics probe requires a real portrait starting Activity"
            }
            main(test) { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await("Activity did not actually enter landscape") {
                main(test) {
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        activity.window.decorView.width > activity.window.decorView.height
                }
            }
            displayContext = service.createDisplayContext(service.getSystemService(WindowManager::class.java).defaultDisplay)
            snapshot("before-rebind")
            shell(automation, "settings put secure enabled_accessibility_services null")
            await("Original service did not disconnect") { main(test) { CollectorService.current == null } }
            shell(
                automation,
                "settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService"
            )
            shell(automation, "settings put secure accessibility_enabled 1")
            await("New service did not connect idle") {
                main(test) { CollectorService.current?.let { it !== initial && !it.isWorking } == true }
            }
            service = main(test) { checkNotNull(CollectorService.current) }
            displayContext = service.createDisplayContext(service.getSystemService(WindowManager::class.java).defaultDisplay)
            snapshot("after-rebind-before-attach")
            // The failing full-suite path backgrounds Collector under this positively identified
            // offline app. Opening it changes no calibration state and injects no input.
            TeachingDeviceSmoke.openFixture(test, service, automation, reset = true)
            displayContext = service.createDisplayContext(service.getSystemService(WindowManager::class.java).defaultDisplay)
            snapshot("after-synthetic-app-switch-before-attach")
            authorizedView = attach(service, service.getSystemService(WindowManager::class.java))
            try {
                displayView = attach(displayContext, displayContext.getSystemService(WindowManager::class.java))
                displayAttachment = "attached-with-existing-public-context-route"
            } catch (error: Exception) {
                // An unsupported token route is a result, not permission to manufacture another token.
                displayAttachment = "rejected:${error.javaClass.simpleName}"
            }
            val start = SystemClock.uptimeMillis()
            for (offset in listOf(0L, 250L, 1000L, 3000L)) {
                SystemClock.sleep((start + offset - SystemClock.uptimeMillis()).coerceAtLeast(0))
                snapshot("after-attach-${offset}ms", stableSample = offset >= 1000)
            }
            val matching = stableSources.reduce { left, right -> left.intersect(right) }
            val outcome = if (matching.isEmpty()) "unresolved" else "consistent"
            document.put("outcome", outcome).put("consistentAttachedSourcesAt1000And3000ms", JSONArray(matching.toList()))
            automation.takeScreenshot()?.let { bitmap ->
                document.put("screenshotWidth", bitmap.width).put("screenshotHeight", bitmap.height)
                File(directory, "teaching-metrics-probe.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            persist()
            return "$outcome; ${report.name}; evidence only, not a safety pass"
        } catch (error: Throwable) {
            primaryFailure = error
            document.put("outcome", "setup-failed").put("error", error.javaClass.simpleName)
            persist()
            throw error
        } finally {
            val failures = overlays.asReversed().mapNotNull { (manager, view) ->
                runCatching { main(test) { if (view.isAttachedToWindow) manager.removeViewImmediate(view) } }.exceptionOrNull()
            }
            document.put("cleanupErrors", JSONArray(failures.map { it.javaClass.simpleName }))
            persist()
            val primary = primaryFailure
            if (primary != null) {
                failures.forEach(primary::addSuppressed)
            } else if (failures.isNotEmpty()) {
                throw failures.first()
            }
        }
    }

    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))

    private fun metrics(value: android.view.WindowMetrics) = JSONObject().put("bounds", rect(value.bounds))
        .put("insets", insets(value.windowInsets))

    private fun insets(value: WindowInsets): JSONArray {
        val inset = value.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return JSONArray(listOf(inset.left, inset.top, inset.right, inset.bottom))
    }

    private fun configuration(context: Context) = context.resources.configuration.let {
        JSONObject().put("orientation", it.orientation).put("widthDp", it.screenWidthDp).put("heightDp", it.screenHeightDp)
    }

    private fun viewBounds(view: View): Rect {
        val location = IntArray(2).also(view::getLocationOnScreen)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun viewState(view: View) = JSONObject().put("bounds", rect(viewBounds(view))).put("attached", view.isAttachedToWindow)
        .put("shown", view.isShown).put("layoutRequested", view.isLayoutRequested)
        .put("windowType", (view.layoutParams as? WindowManager.LayoutParams)?.type ?: JSONObject.NULL)
        .put("windowFlags", (view.layoutParams as? WindowManager.LayoutParams)?.flags ?: JSONObject.NULL)
        .put("visibleDisplayFrame", rect(Rect().also(view::getWindowVisibleDisplayFrame)))
        .put("resources", configuration(view.context)).put("rootInsets", view.rootWindowInsets?.let(::insets) ?: JSONObject.NULL)

    private fun shell(automation: UiAutomation, command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun await(message: String, ready: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        do {
            if (ready()) return
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < deadline)
        error(message)
    }

    private fun <T> main(test: Instrumentation, block: () -> T): T {
        val value = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        test.runOnMainSync {
            try {
                value.set(block())
            } catch (failure: Throwable) {
                error.set(failure)
            }
        }
        error.get()?.let { throw it }
        return value.get()
    }
}
