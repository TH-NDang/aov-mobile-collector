package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Paused-only editor. Moving a handle never dispatches a gesture. */
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
            setPadding(12, 6, 12, 6)
            setBackgroundColor(Color.argb(240, 26, 28, 40))
        }
        val title = TextView(context).apply {
            text = "${position + 1}/$count · ${step.title} · Tạm dừng\n${step.hint}"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        controls.addView(title)
        controls.addView(
            TextView(context).apply {
                text = "Trước/Tiếp chỉ chọn bước. Kéo rồi Lưu trước khi đổi bước. Thử bước mới chạm game."
                setTextColor(Color.YELLOW)
                textSize = 11f
            }
        )

        fun row(): LinearLayout = LinearLayout(context).also { controls.addView(it) }

        fun button(row: LinearLayout, label: String, run: () -> Unit) {
            row.addView(Ui.button(context, label) { if (!testing) run() }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val navigation = row()
        button(navigation, "Trước") { if (position > 0) navigate(position - 1) }
        button(navigation, "Các bước") { choose() }
        button(navigation, "Tiếp") { if (position + 1 < count) navigate(position + 1) }
        button(navigation, "Vào sảnh") { enterLobby() }
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
        button(buttons, "Thử bước") {
            if (drawing.points.isEmpty()) {
                title.text = step.title + " · Mở đúng màn hình rồi bấm Đọc vị trí"
            } else {
                testing = true
                applyVisibility()
                test(action, drawing.points.toList()) { ok ->
                    testing = false
                    if (root === frame) {
                        applyVisibility()
                        title.text =
                            step.title + if (ok) " · Đã thử 1 lần; vẫn tạm dừng" else " · Không thử được; mở đúng màn hình rồi đọc lại"
                    }
                }
            }
        }
        val options = row()
        if (step.recognition != null) {
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
        button(options, "Đổi góc bảng") {
            val lp = controls.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.gravity = if (lp.gravity == Gravity.BOTTOM) Gravity.TOP else Gravity.BOTTOM
            controls.layoutParams = lp
        }
        frame.addView(drawing, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(controls, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        frame.isFocusableInTouchMode = true
        frame.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                false
            }
        }
        val params = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        }
        frame.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (hidden) {
            params.flags =
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return try {
            wm.addView(frame, params)
            root = frame
            frame.requestFocus()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applyVisibility() {
        val frame = root ?: return
        val invisible = hidden || testing
        val params = frame.layoutParams as WindowManager.LayoutParams
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.flags = if (invisible) params.flags or flags else params.flags and flags.inv()
        frame.visibility = if (invisible) View.INVISIBLE else View.VISIBLE
        try {
            wm.updateViewLayout(frame, params)
            if (!invisible) frame.requestFocus()
        } catch (_: IllegalArgumentException) { }
    }

    fun shield(on: Boolean) {
        hidden = on
        applyVisibility()
    }

    fun close() {
        val old = root ?: return
        root = null
        old.visibility = View.INVISIBLE
        try {
            wm.removeViewImmediate(old)
        } catch (_: IllegalArgumentException) { }
        closed()
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
