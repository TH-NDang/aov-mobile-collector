package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Upcoming-gesture editor. Moving a handle or the floating panel never dispatches a gesture. */
internal class GestureCalibrationEditor(
    private val context: Context,
    private val step: CalibrationStep,
    private val position: Int,
    private val count: Int,
    private val initial: List<CalibrationPoint>,
    private val navigate: (Int) -> Unit,
    private val choose: () -> Unit,
    private val enterLobby: () -> Unit,
    private val scan: ((CalibrationAction?) -> Unit) -> Unit,
    private val resolved: (CalibrationAction) -> List<CalibrationPoint>,
    private val save: (CalibrationAction, List<CalibrationPoint>) -> Boolean,
    private val reset: () -> Boolean,
    private val test: (CalibrationAction, List<CalibrationPoint>, (Boolean) -> Unit) -> Unit,
    private val closed: () -> Unit
) {
    companion object {
        // Keep the user's panel position when the runner presents its next actual gesture.
        private var panelX = 0f
        private var panelY = 1f
    }

    private val runtime = count == 0
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: android.widget.FrameLayout? = null
    private var testing = false
    private var hidden = false
    private var action = step.action
    private val drawing = Handles(context, initial)
    internal val view: View? get() = root

    fun show(): Boolean {
        val frame = android.widget.FrameLayout(context)
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 8), Ui.dp(context, 5), Ui.dp(context, 8), Ui.dp(context, 5))
            setBackgroundColor(Color.argb(240, 26, 28, 40))
        }
        val dragHandle = TextView(context).apply {
            text = "↔ Kéo bảng để di chuyển"
            contentDescription = "Kéo bảng điều khiển đến vị trí khác"
            setTextColor(Color.CYAN)
            textSize = 12f
            setPadding(0, Ui.dp(context, 3), 0, Ui.dp(context, 3))
        }
        controls.addView(dragHandle)
        val title = TextView(context).apply {
            text = (if (runtime) "Bước ${position + 1}" else "${position + 1}/$count") +
                " · ${step.title} · Tạm dừng" + if (runtime) "" else "\n${step.hint}"
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        controls.addView(title)
        controls.addView(
            TextView(context).apply {
                text = if (runtime) {
                    "Kéo điểm để chỉnh. Lưu để dùng lại. Chạy 1 bước rồi dừng. Hủy để chạm game."
                } else {
                    "Trước/Tiếp chỉ chọn bước. Kéo rồi Lưu trước khi đổi bước. Thử bước mới chạm game."
                }
                setTextColor(Color.YELLOW)
                textSize = 11f
            }
        )

        fun row(): LinearLayout = LinearLayout(context).also { controls.addView(it) }

        fun button(row: LinearLayout, label: String, run: () -> Unit) {
            row.addView(
                Ui.button(context, label) { if (!testing) run() }.apply {
                    textSize = 13f
                    setPadding(Ui.dp(context, 5), 0, Ui.dp(context, 5), 0)
                },
                LinearLayout.LayoutParams(0, Ui.dp(context, 36), 1f).apply {
                    topMargin = Ui.dp(context, 4)
                    if (row.childCount > 0) leftMargin = Ui.dp(context, 4)
                }
            )
        }
        if (!runtime) {
            val navigation = row()
            button(navigation, "Trước") { if (position > 0) navigate(position - 1) }
            button(navigation, "Các bước") { choose() }
            button(navigation, "Tiếp") { if (position + 1 < count) navigate(position + 1) }
            button(navigation, "Vào sảnh") { enterLobby() }
        }
        val buttons = row()
        button(buttons, "Hủy") { close() }
        button(buttons, "Mặc định") {
            if (reset()) {
                drawing.points = action.points.toList()
                drawing.invalidate()
                title.text = step.title + " · Đã khôi phục mặc định"
            } else {
                title.text = "Không lưu được. Hãy thử lại."
            }
        }
        button(buttons, "Lưu") {
            if (drawing.points.isNotEmpty() && save(action, drawing.points)) {
                title.text = step.title + " · Đã lưu cho màn hình này; vẫn tạm dừng"
            } else {
                title.text = "Chưa lưu được. Đọc lại vị trí nếu chưa có điểm chạm."
            }
        }
        val execution = if (runtime) row() else buttons
        button(execution, if (runtime) "Chạy bước / Tiếp" else "Thử bước") {
            if (drawing.points.isEmpty()) {
                title.text = step.title + if (runtime) {
                    " · Chưa có điểm chạm hợp lệ; hãy hủy và mở lại chế độ chỉnh"
                } else {
                    " · Mở đúng màn hình rồi bấm Đọc vị trí"
                }
            } else {
                testing = true
                // Runtime preflight must inspect a stable, identifiable overlay. The service removes
                // this editor only after approval, then shields and checks the target again before input.
                if (!runtime) applyVisibility()
                test(action, drawing.points.toList()) { ok ->
                    testing = false
                    if (root === frame) {
                        applyVisibility()
                        title.text =
                            step.title + if (ok) {
                                if (runtime) " · Đã chạy; đang chuẩn bị thao tác kế" else " · Đã thử 1 lần; vẫn tạm dừng"
                            } else {
                                " · Không chạy được; kiểm tra màn hình game"
                            }
                    }
                }
            }
        }
        val options = if (runtime) execution else row()
        if (!runtime && step.recognition != null) {
            button(options, "Đọc vị trí") {
                testing = true
                applyVisibility()
                scan { fresh ->
                    testing = false
                    if (root === frame) {
                        if (fresh != null) {
                            action = fresh
                            drawing.points = resolved(fresh)
                        } else {
                            drawing.points = emptyList()
                        }
                        drawing.invalidate()
                        applyVisibility()
                        title.text =
                            step.title + if (fresh != null) " · Kéo điểm nhận diện để chỉnh" else " · Chưa nhận ra, hãy mở đúng màn hình"
                    }
                }
            }
        }

        fun placePanel(x: Float, y: Float) {
            val maxX = (frame.width - controls.width).coerceAtLeast(0)
            val maxY = (frame.height - controls.height).coerceAtLeast(0)
            // Position must not reduce WRAP_CONTENT's measurement space. A bottom topMargin
            // constrains the panel to its old height when text grows, leaving its children clipped.
            controls.translationX = x.toInt().coerceIn(0, maxX).toFloat()
            controls.translationY = y.toInt().coerceIn(0, maxY).toFloat()
            panelX = if (maxX > 0) controls.translationX / maxX else 0f
            panelY = if (maxY > 0) controls.translationY / maxY else 0f
        }
        button(options, "Đổi góc bảng") {
            placePanel(controls.x, if (controls.y > 0) 0f else frame.height.toFloat())
        }
        var dragX = 0f
        var dragY = 0f
        var startX = 0f
        var startY = 0f
        dragHandle.setOnTouchListener { _, event ->
            if (testing) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragX = event.rawX
                    dragY = event.rawY
                    startX = controls.x
                    startY = controls.y
                }
                MotionEvent.ACTION_MOVE -> placePanel(startX + event.rawX - dragX, startY + event.rawY - dragY)
                MotionEvent.ACTION_UP -> dragHandle.performClick()
            }
            true
        }
        frame.addView(drawing, android.widget.FrameLayout.LayoutParams(-1, -1))
        val insetTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.mandatorySystemGestures()
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(insetTypes)
        val safeWidth = (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1)
        val panelWidth = Ui.dp(context, 320).coerceAtMost(safeWidth)
        frame.addView(controls, android.widget.FrameLayout.LayoutParams(panelWidth, -2, Gravity.TOP or Gravity.LEFT))

        fun placeAfterLayout() {
            frame.post {
                if (root === frame && frame.width > 0 && frame.height > 0 && controls.height > 0) {
                    placePanel(
                        panelX * (frame.width - controls.width).coerceAtLeast(0),
                        panelY * (frame.height - controls.height).coerceAtLeast(0)
                    )
                }
            }
        }
        // Wait for measured bounds, and re-clamp if status/error text changes the panel height.
        controls.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) placeAfterLayout()
        }
        frame.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) placeAfterLayout()
        }
        val params = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_BEHIND
            // Never own Home/Recents, edge gestures or cutouts, even when the game hides its bars.
            // The remaining game area intentionally guards against changing a pending target by hand.
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            setFitInsetsTypes(insetTypes)
            setFitInsetsIgnoringVisibility(true)
        }
        frame.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (hidden) {
            params.flags =
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return try {
            wm.addView(frame, params)
            root = frame
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applyVisibility() {
        val frame = root ?: return
        val invisible = hidden || testing
        val params = frame.layoutParams as WindowManager.LayoutParams
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (invisible) params.flags or flags else params.flags and flags.inv()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        frame.visibility = if (invisible) View.INVISIBLE else View.VISIBLE
        try {
            wm.updateViewLayout(frame, params)
        } catch (_: IllegalArgumentException) { }
    }

    fun shield(on: Boolean) {
        hidden = on
        applyVisibility()
    }

    fun close(notify: Boolean = true) {
        val old = root ?: return
        root = null
        old.visibility = View.INVISIBLE
        try {
            wm.removeViewImmediate(old)
        } catch (_: IllegalArgumentException) { }
        if (notify) closed()
    }

    private class Handles(context: Context, initial: List<CalibrationPoint>) : View(context) {
        var points = initial.toList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var dragging = -1
        private val origin = IntArray(2)
        private val radius = 22 * resources.displayMetrics.density

        private fun bounds() = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val b = bounds()
            getLocationOnScreen(origin)
            canvas.save()
            canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
            paint.color = Color.CYAN
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            if (points.size == 2) {
                canvas.drawLine(
                    (points[0].x * (b.width() - 1)).toFloat(),
                    (points[0].y * (b.height() - 1)).toFloat(),
                    (points[1].x * (b.width() - 1)).toFloat(),
                    (points[1].y * (b.height() - 1)).toFloat(),
                    paint
                )
            }
            points.forEachIndexed { i, p ->
                val x = (p.x * (b.width() - 1)).toFloat()
                val y = (p.y * (b.height() - 1)).toFloat()
                canvas.drawCircle(x, y, radius, paint)
                canvas.drawLine(x - radius, y, x + radius, y, paint)
                canvas.drawLine(x, y - radius, x, y + radius, paint)
                paint.textSize = 18 * resources.displayMetrics.density
                canvas.drawText(
                    if (points.size == 1) {
                        "Chạm"
                    } else if (i == 0) {
                        "Đầu"
                    } else {
                        "Cuối"
                    },
                    x + radius,
                    y,
                    paint
                )
            }
            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val b = bounds()
            val x = event.rawX / (b.width() - 1)
            val y = event.rawY / (b.height() - 1)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragging = points.indices.minByOrNull { i ->
                    val dx = (points[i].x - x) * b.width()
                    val dy = (points[i].y - y) * b.height()
                    dx * dx + dy * dy
                }?.takeIf { i ->
                    kotlin.math.hypot((points[i].x - x) * b.width(), (points[i].y - y) * b.height()) <= radius * 2
                } ?: -1
                MotionEvent.ACTION_MOVE -> if (dragging >= 0) {
                    points =
                        points.toMutableList().also {
                            it[dragging] =
                                CalibrationPoint(x.toDouble().coerceIn(0.0, 1.0), y.toDouble().coerceIn(0.0, 1.0))
                        }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = -1
            }
            return true
        }
    }
}
