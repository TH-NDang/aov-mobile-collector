package vn.ndang.aovcollector

import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Reads what the game just copied. Android 10 and later show the clipboard only to the app whose window has input
 * focus, so a one-pixel window of the accessibility service takes the focus for the moment of the read and is removed
 * at once, giving the game its focus back. It takes no touches. Null when Android gives no text within [WAIT_MS].
 */
class ClipboardPeek(private val context: Context, private val handler: Handler) {
    companion object {
        const val WAIT_MS = 1500L
    }

    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    /** Empties the clipboard, which needs no focus, so an old text is never taken for a new copy. */
    fun clear() {
        try {
            clipboard?.clearPrimaryClip()
        } catch (_: Exception) {
        }
    }

    fun read(then: (String?) -> Unit) {
        val wm = context.getSystemService(WindowManager::class.java) ?: return then(null)
        var finished = false
        var view: View? = null

        fun text(): String? = try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        } catch (_: Exception) {
            null
        }

        fun finish(value: String?) {
            if (finished) return
            finished = true
            view?.let {
                try {
                    wm.removeView(it)
                } catch (_: Exception) {
                }
            }
            then(value)
        }
        val window = object : View(context) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) handler.post { finish(text()) }
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            wm.addView(window, params)
            view = window
        } catch (_: Exception) {
            return finish(text())
        }
        // Without the focus the read is tried all the same: some phones show the clipboard to accessibility services.
        handler.postDelayed({ finish(text()) }, WAIT_MS)
    }
}
