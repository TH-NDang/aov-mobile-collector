package vn.ndang.aovcollector

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Local-only visualization of the very paths sent to Android, never a touch target. */
internal class GestureDebugOverlay(private val context: Context, private val handler: Handler) {
    companion object {
        const val PREFERENCE = "gestureDebug"
        const val DISPLAY_MS = 1800L
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: Drawing? = null
    internal val overlayView: View? get() = view
    private var hidden = false
    private val expire = Runnable { clear() }

    fun show(gestures: List<GestureDescription>, tap: Boolean, target: GameScreens.Word?, label: String?) {
        handler.removeCallbacks(expire)
        val drawing = view ?: Drawing(context).also {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
            it.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            try {
                wm.addView(it, params)
                view = it
            } catch (_: Exception) {
                return
            }
        }
        val bounds = wm.currentWindowMetrics.bounds
        drawing.paths = gestures.flatMap { gesture ->
            (0 until gesture.strokeCount).map { Path(gesture.getStroke(it).path) }
        }
        drawing.target = target?.let {
            RectF(
                (it.left * bounds.width()).toFloat(),
                (it.top * bounds.height()).toFloat(),
                (it.right * bounds.width()).toFloat(),
                (it.bottom * bounds.height()).toFloat()
            )
        }
        drawing.label = label ?: if (tap) "Chạm" else "Vuốt / giữ"
        drawing.invalidate()
        handler.postDelayed(expire, DISPLAY_MS)
    }

    fun shield(on: Boolean) {
        hidden = on
        view?.visibility = if (on) View.INVISIBLE else View.VISIBLE
    }

    fun clear() {
        handler.removeCallbacks(expire)
        view?.let {
            it.visibility = View.INVISIBLE
            try {
                if (it.isAttachedToWindow) wm.removeViewImmediate(it)
            } catch (_: IllegalArgumentException) {
                // The service/window may already have been removed during teardown.
            }
        }
        view = null
    }

    private class Drawing(context: Context) : View(context) {
        var paths = emptyList<Path>()
        var target: RectF? = null
        var label = ""
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val origin = IntArray(2)
        private val point = FloatArray(2)
        private val density = resources.displayMetrics.density

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = false
            isFocusable = false
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            getLocationOnScreen(origin)
            canvas.save()
            canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * density
            paint.color = Color.CYAN
            target?.let { canvas.drawRect(it, paint) }
            paths.forEach { path ->
                canvas.drawPath(path, paint)
                val measure = PathMeasure(path, false)
                // PathMeasure cannot locate a zero-length tap; the path bounds still retain its exact point.
                if (!measure.getPosTan(0f, point, null)) {
                    val bounds = RectF()
                    path.computeBounds(bounds, true)
                    point[0] = bounds.left
                    point[1] = bounds.top
                }
                canvas.drawCircle(point[0], point[1], 14 * density, paint)
                if (measure.getPosTan(measure.length, point, null)) {
                    canvas.drawCircle(point[0], point[1], 6 * density, paint)
                }
            }
            paint.style = Paint.Style.FILL
            paint.textSize = 16 * density
            val padding = 8 * density
            val textWidth = paint.measureText(label)
            val x = point[0].coerceIn(padding, maxOf(padding, width - textWidth - padding))
            val y = (point[1] - 24 * density).coerceIn(30 * density, maxOf(30 * density, height - padding))
            paint.color = Color.argb(220, 0, 0, 0)
            canvas.drawRoundRect(x - padding, y - 20 * density, x + textWidth + padding, y + padding, padding, padding, paint)
            paint.color = Color.CYAN
            canvas.drawText(label, x, y, paint)
            canvas.restore()
        }
    }
}
