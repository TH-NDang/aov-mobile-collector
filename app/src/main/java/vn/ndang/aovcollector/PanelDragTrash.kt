package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView

/** A visual drop target only: it never consumes touches or deletes collected files. */
internal class PanelDragTrash(private val context: Context, private val windows: WindowManager) {
    var view: View? = null
        private set

    private fun dp(value: Int) = Ui.dp(context, value)

    fun show() {
        if (view != null) return
        val metrics = windows.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val target = ImageView(context).apply {
            setImageResource(R.drawable.ic_panel_trash)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            contentDescription = "Thả để đóng bảng nổi và tạm dừng; không xóa ảnh"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = Ui.shape(Color.rgb(72, 43, 55), dp(32), Color.rgb(244, 166, 177))
        }
        val parameters = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((metrics.bounds.width() - width) / 2).coerceAtLeast(insets.left)
            y = (metrics.bounds.height() - insets.bottom - height - dp(12)).coerceAtLeast(insets.top)
            // Coordinates explicitly exclude system-bar insets above.
            setFitInsetsTypes(0)
        }
        try {
            windows.addView(target, parameters)
            view = target
        } catch (error: Exception) {
            CrashLog.record(context, "Could not show panel drop target", error)
        }
    }

    fun contains(rawX: Float, rawY: Float): Boolean {
        val target = view ?: return false
        if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0) return false
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + target.width, location[1] + target.height)
            .contains(rawX.toInt(), rawY.toInt())
    }

    fun move(rawX: Float, rawY: Float) {
        val target = view ?: return
        val inside = contains(rawX, rawY)
        if (target.isActivated == inside) return
        target.isActivated = inside
        target.background = Ui.shape(
            if (inside) Color.rgb(183, 46, 70) else Color.rgb(72, 43, 55),
            dp(32),
            Color.rgb(244, 166, 177)
        )
    }

    fun hide() {
        val target = view ?: return
        if (!target.isAttachedToWindow) {
            view = null
            return
        }
        try {
            windows.removeView(target)
            view = null
        } catch (error: Exception) {
            // Keep the handle if both removals fail so the next lifecycle cleanup can retry.
            try {
                windows.removeViewImmediate(target)
                view = null
            } catch (retry: Exception) {
                error.addSuppressed(retry)
                CrashLog.record(context, "Could not remove panel drop target", error)
            }
        }
    }
}
