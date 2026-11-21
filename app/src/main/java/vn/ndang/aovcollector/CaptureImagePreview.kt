package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.Executor

/** Reads a larger image independently of the thumbnail, with a bounded decode and stale-load guard. */
class CaptureImagePreview(private val c: Context, private val worker: Executor, private val swallowed: () -> Unit) {
    private val wm = c.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var frame: LinearLayout? = null
    internal val view: View? get() = frame
    private var params: WindowManager.LayoutParams? = null
    private var image: ZoomImage? = null

    @Volatile private var token = 0
    private var gallery = emptyList<CaptureHistory.Entry>()
    private var hidden = false

    private fun dp(n: Int) = Ui.dp(c, n)

    fun show(entries: List<CaptureHistory.Entry>, order: Long) {
        gallery = entries.filter { it.state == "saved" }
        val position = gallery.indexOfFirst { it.order == order }
        if (position >= 0) showAt(position)
    }

    private fun showAt(position: Int) {
        if (position !in gallery.indices) return
        closeWindow()
        val request = ++token
        val entry = gallery[position]
        val box = object : LinearLayout(c) {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                if (!hidden) return super.dispatchTouchEvent(e)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) swallowed()
                return true
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(12, 15, 24))
            contentDescription = "Khung xem ảnh lớn"
        }
        val header = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            Ui.text(c, "${position + 1}/${gallery.size} · ${entry.name}", 13f, Color.WHITE, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val zoom = ZoomImage(c) { direction -> showAt(position + direction) }.apply { contentDescription = "Ảnh lớn số ${entry.order}" }

        fun button(text: String, label: String, action: () -> Unit) {
            header.addView(
                Ui.button(c, text, action = action).apply {
                    contentDescription = label
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    background = Ui.shape(Color.rgb(47, 52, 74), dp(10))
                    setPadding(dp(8), 0, dp(8), 0)
                },
                LinearLayout.LayoutParams(if (text == "Đóng") dp(64) else dp(42), dp(42)).apply { leftMargin = dp(5) }
            )
        }
        button("−", "Thu nhỏ ảnh") { zoom.changeZoom(1 / 1.5f) }
        button("+", "Phóng to ảnh") { zoom.changeZoom(1.5f) }
        button("Đóng", "Đóng ảnh lớn") { close() }
        box.addView(header)
        val message = Ui.text(c, "Đang mở ảnh…", 11f, Color.LTGRAY)
        box.addView(message)
        box.addView(zoom, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(c)

        fun action(text: String, label: String, enabled: Boolean = true, click: () -> Unit): Button {
            val b = Ui.button(c, text, action = click).apply {
                contentDescription = label
                isEnabled = enabled
                textSize = 12f
                setTextColor(Color.WHITE)
                background = Ui.shape(Color.rgb(47, 52, 74), dp(10))
            }
            actions.addView(
                b,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    topMargin = dp(6)
                    if (actions.childCount > 0) leftMargin = dp(5)
                }
            )
            return b
        }
        action("‹ Trước", "Ảnh trước", position > 0) { showAt(position - 1) }
        action("Sau ›", "Ảnh sau", position + 1 < gallery.size) { showAt(position + 1) }
        lateinit var copy: Button
        copy = action("Chép ảnh", "Sao chép ảnh") {
            copy.isEnabled = false
            worker.execute {
                try {
                    val uri = CaptureActions.clipboardSnapshot(c, entry)
                    main.post {
                        try {
                            CaptureActions.setClipboard(c, entry.name, uri)
                            Toast.makeText(c, "Đã sao chép ${entry.name}; có thể dán sang app khác", Toast.LENGTH_SHORT).show()
                        } catch (
                            _: Exception
                        ) {
                            Toast.makeText(c, "Không đặt được ảnh vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                        }
                        copy.isEnabled = true
                    }
                } catch (
                    _: Exception
                ) {
                    main.post {
                        copy.isEnabled = true
                        Toast.makeText(c, "Không sao chép được ảnh; kiểm tra file còn lưu", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        box.addView(actions)
        // MATCH_PARENT uses the usable display area, including landscape navigation-bar insets.
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(box, p)
        frame = box
        params = p
        image = zoom
        shield(hidden)
        worker.execute {
            if (request != token) return@execute
            val loaded = try {
                val file = File(c.filesDir, "captures/" + entry.path)
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, size)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(size.outWidth, size.outHeight) / inSampleSize > 4096 ||
                        (size.outWidth.toLong() / inSampleSize) * (size.outHeight / inSampleSize) > 6_000_000L
                    ) {
                        inSampleSize *= 2
                    }
                }
                if (!file.isFile) null else BitmapFactory.decodeFile(file.path, options)
            } catch (_: Exception) {
                null
            }
            main.post {
                if (request != token || frame !== box) {
                    loaded?.recycle()
                } else if (loaded == null) {
                    message.text = "Không mở được ảnh. File có thể đã bị xóa hoặc không còn đọc được."
                } else {
                    zoom.show(loaded)
                    message.text = "${loaded.width} × ${loaded.height} · Chụm hai ngón hoặc dùng +/− để phóng to · Kéo để xem"
                }
            }
        }
    }

    fun shield(on: Boolean) {
        hidden = on
        val v = frame ?: return
        val p = params ?: return
        p.flags =
            if (on) {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                p.flags and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
        v.visibility = if (on) View.INVISIBLE else View.VISIBLE
        if (v.isAttachedToWindow) wm.updateViewLayout(v, p)
    }

    fun close() {
        closeWindow()
        gallery = emptyList()
    }

    private fun closeWindow() {
        token++
        frame?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        frame = null
        params = null
        // A removed overlay can still have a pending draw; never recycle an image already displayed.
        image?.clear()
        image = null
    }

    private class ZoomImage(c: Context, private val navigate: (Int) -> Unit) : FrameLayout(c) {
        private val picture = ImageView(c).apply {
            scaleType = ImageView.ScaleType.MATRIX
            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private var photo: Bitmap? = null
        private var factor = 1f
        private var fit = 1f
        private var offsetX = 0f
        private var offsetY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private val transform = Matrix()
        private val pinch = ScaleGestureDetector(
            c,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    changeZoom(d.scaleFactor, d.focusX, d.focusY)
                    return true
                }
            }
        )
        private val taps = GestureDetector(
            c,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onFling(first: MotionEvent?, last: MotionEvent, vx: Float, vy: Float): Boolean {
                    val dx = last.x - (first?.x ?: last.x)
                    if (factor > 1.05f || kotlin.math.abs(dx) < Ui.dp(c, 48) || kotlin.math.abs(vx) < kotlin.math.abs(vy)) return false
                    navigate(if (dx < 0) 1 else -1)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    changeZoom(if (factor > 1.1f) 1 / factor else 2.5f, e.x, e.y)
                    return true
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    performClick()
                    return true
                }
            }
        )

        init {
            addView(picture, LayoutParams(-1, -1))
            isClickable = true
        }

        fun clear() {
            photo = null
            picture.setImageDrawable(null)
        }

        fun show(b: Bitmap) {
            photo = b
            picture.setImageBitmap(b)
            resetFit()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            resetFit()
        }

        private fun resetFit() {
            val b = photo ?: return
            if (width == 0 || height == 0) return
            fit = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
            factor = 1f
            offsetX = 0f
            offsetY = 0f
            drawPosition()
        }

        fun changeZoom(amount: Float, x: Float = width / 2f, y: Float = height / 2f) {
            if (photo == null) return
            val next = (factor * amount).coerceIn(1f, 6f)
            val ratio = next / factor
            offsetX = x - (x - offsetX) * ratio
            offsetY = y - (y - offsetY) * ratio
            factor = next
            drawPosition()
        }

        private fun drawPosition() {
            val b = photo ?: return
            val w = b.width * fit * factor
            val h = b.height * fit * factor
            offsetX = if (w <= width) (width - w) / 2 else offsetX.coerceIn(width - w, 0f)
            offsetY = if (h <= height) (height - h) / 2 else offsetY.coerceIn(height - h, 0f)
            transform.setScale(fit * factor, fit * factor)
            transform.postTranslate(offsetX, offsetY)
            picture.imageMatrix = transform
            stateDescription = "Phóng to ${(factor * 100).toInt()}%"
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            pinch.onTouchEvent(e)
            taps.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x
                    lastY = e.y
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val remaining = if (e.actionIndex == 0 && e.pointerCount > 1) 1 else 0
                    lastX = e.getX(remaining)
                    lastY = e.getY(remaining)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!pinch.isInProgress && e.pointerCount == 1) {
                        offsetX += e.x - lastX
                        offsetY += e.y - lastY
                        drawPosition()
                    }
                    lastX = e.x
                    lastY = e.y
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
