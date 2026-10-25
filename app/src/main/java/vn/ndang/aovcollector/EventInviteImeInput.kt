package vn.ndang.aovcollector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Completes Unity's native landscape input flow:
 * game popup -> Android fullscreen IME extract editor -> IME "OK" -> game popup.
 *
 * Important: the white editor ABOVE the keyboard belongs to the currently
 * selected IME, not necessarily to the game or to rootInActiveWindow. It may
 * appear well after the game tap and the game's confirm button is still visible
 * behind it. No global Back and no game "Xác nhận" gesture are sent here.
 */
internal class EventInviteImeInput(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val gamePackage: String,
    private val code: String,
    private val active: () -> Boolean,
    private val open: ((Boolean) -> Unit) -> Unit,
    private val visualOk: ((Pair<Double, Double>?) -> Unit) -> Unit,
    private val verifyGameCode: ((Boolean) -> Unit) -> Unit,
    private val finish: (Boolean, String?) -> Unit
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val selectedImePackage = Settings.Secure.getString(
        service.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )?.substringBefore('/')
    private var finished = false
    private val began = SystemClock.uptimeMillis()
    private var stableEditor = 0
    private var stableValue = 0
    private var stableClosed = 0

    private data class Frame(
        val nodes: List<AccessibilityNodeInfo>,
        val metadata: List<EventInviteImePolicy.Node>,
        val editorIndex: Int?,
        val okIndex: Int?,
        val keyboard: AccessibilityWindowInfo,
        val packageName: String
    ) {
        val editor get() = editorIndex?.let(nodes::get)
        val ok get() = okIndex?.let(nodes::get)
    }

    private fun done(ok: Boolean, message: String? = null) {
        if (finished) return
        finished = true
        finish(ok, message)
    }

    private fun valid(): Boolean = !finished && active()

    private fun later(ms: Long, then: () -> Unit) {
        handler.postDelayed({ if (valid()) then() }, ms)
    }

    fun start() {
        if (!valid()) return
        if (selectedImePackage.isNullOrBlank()) {
            return done(false, "Chưa xác định được bàn phím Android đang chọn; chưa nhập mã")
        }
        open { pressed ->
            if (!valid()) return@open
            if (!pressed) {
                done(false, "Không mở được ô Nhập mã mời trong game")
            } else {
                // IME window animation and extract editor may appear a second later.
                later(750) { awaitEditor(0) }
            }
        }
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && result.size < 400) {
            val node = queue.removeFirst()
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return result
    }

    /** Finds ONLY the active keyboard's TYPE_INPUT_METHOD window, not a foreign app's edit box. */
    private fun keyboardFrame(): Frame? {
        val windows = try {
            service.windows.orEmpty()
        } catch (_: Exception) {
            return null
        }
        val ime = windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .singleOrNull() ?: return null
        val root = ime.root ?: return null
        val actualPackage = root.packageName?.toString() ?: return null
        val gameBehind = windows.any {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.root?.packageName?.toString() == gamePackage
        }
        val foreignFocusedApplication = windows.any {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.isFocused &&
                it.root?.packageName?.toString() != gamePackage
        }
        if (!EventInviteImePolicy.keyboardTrusted(
                selectedImePackage,
                actualPackage,
                gameBehind,
                foreignFocusedApplication
            )
        ) {
            return null
        }
        val bounds = windowManager.maximumWindowMetrics.bounds
        if (bounds.width() < 500 || bounds.height() < 300 || bounds.width() <= bounds.height()) return null
        val nodes = collectNodes(root)
        val meta = nodes.mapIndexed { index, node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            EventInviteImePolicy.Node(
                index = index,
                label = (node.text ?: node.contentDescription)?.toString().orEmpty(),
                className = node.className?.toString().orEmpty(),
                editable = node.isEditable,
                password = node.isPassword,
                focused = node.isFocused,
                visible = node.isVisibleToUser,
                enabled = node.isEnabled,
                left = rect.left.toDouble() / bounds.width(),
                top = rect.top.toDouble() / bounds.height(),
                right = rect.right.toDouble() / bounds.width(),
                bottom = rect.bottom.toDouble() / bounds.height()
            )
        }
        return Frame(
            nodes,
            meta,
            EventInviteImePolicy.editor(meta),
            EventInviteImePolicy.okButton(meta),
            ime,
            actualPackage
        )
    }

    private fun awaitEditor(retries: Int) {
        if (!valid()) return
        val frame = keyboardFrame()
        if (frame?.editor != null) {
            if (++stableEditor >= 2) return clearCode()
        } else {
            stableEditor = 0
        }
        if (retries >= 20 || SystemClock.uptimeMillis() - began > 14_000) {
            return done(false, "Bàn phím đã mở nhưng không tìm thấy thanh nhập trắng phía trên; chưa nhập mã")
        }
        later(380) { awaitEditor(retries + 1) }
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun clearCode() {
        val editor = keyboardFrame()?.editor
            ?: return done(false, "Thanh nhập phía trên bàn phím đã biến mất trước khi xóa mã")
        editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val oldSize = editor.text?.length ?: 0
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, oldSize)
        }
        // Explicit select-all, clear, and fresh re-read. Never append to a preset game code.
        editor.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        if (!setText(editor, "")) {
            return done(false, "Bàn phím không cho phép xóa mã mặc định bằng Accessibility")
        }
        later(370) { awaitClear(0) }
    }

    private fun awaitClear(retries: Int) {
        if (!valid()) return
        val frame = keyboardFrame()
        if (frame?.editor == null) {
            return done(false, "Bàn phím đóng trước khi xác minh đã xóa sạch mã cũ")
        }
        val text = frame.editor?.text?.toString()
        if (text != null && text.isEmpty()) return enterCode()
        if (retries >= 10) return done(false, "Không xác minh được ô nhập đã trống; chưa ghi đè mã")
        later(350) { awaitClear(retries + 1) }
    }

    private fun enterCode() {
        val editor = keyboardFrame()?.editor
            ?: return done(false, "Mất thanh nhập phía trên bàn phím trước khi nhập mã")
        if (!setText(editor, code)) {
            return done(false, "Không nhập được mã vào thanh trắng phía trên bàn phím")
        }
        later(450) { awaitEntered(0) }
    }

    private fun awaitEntered(retries: Int) {
        if (!valid()) return
        val editor = keyboardFrame()?.editor
            ?: return done(false, "Bàn phím đóng trước khi xác minh mã vừa nhập")
        // Never log the code, a partial value, or a screenshot while it is visible.
        if (editor.text?.toString() == code) {
            if (++stableValue >= 2) return pressKeyboardOk()
        } else {
            stableValue = 0
        }
        if (retries >= 13) {
            return done(false, "Mã hiển thị trên thanh nhập không khớp mã chương trình")
        }
        later(340) { awaitEntered(retries + 1) }
    }

    private fun pressKeyboardOk() {
        val frame = keyboardFrame()
            ?: return done(false, "Bàn phím đã biến mất trước khi nhấn OK")
        if (frame.editor?.text?.toString() != code) {
            return done(false, "Mã trên thanh nhập thay đổi trước khi bấm OK")
        }

        val ok = frame.ok
        if (ok != null) {
            var target: AccessibilityNodeInfo? = ok
            var steps = 0
            while (target != null && !target.isClickable && steps++ < 3) {
                target = target.parent
            }
            if (target?.isEnabled == true &&
                target.isVisibleToUser &&
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                later(700) { awaitKeyboardClosed(0) }
                return
            }
            // OEM IMEs sometimes draw OK without exposing its Click action.
            val center = frame.metadata[checkNotNull(frame.okIndex)]
            return tapVerifiedImeOk(center.centerX, center.centerY)
        }

        // If the IME button itself is unexposed, OCR ONLY its top-right region.
        // This never reads or captures the secret code in the white editor.
        visualOk { point ->
            if (!valid()) return@visualOk
            val updated = keyboardFrame()
            if (point == null || updated?.editor?.text?.toString() != code) {
                done(false, "Không xác nhận được nút OK của bàn phím; mã chưa được gửi vào game")
            } else {
                tapVerifiedImeOk(point.first, point.second)
            }
        }
    }

    private fun tapVerifiedImeOk(x: Double, y: Double) {
        if (!valid()) return
        val frame = keyboardFrame()
            ?: return done(false, "Bàn phím không còn hiển thị, không chạm nút OK")
        val safeButton = EventInviteImePolicy.Node(
            index = -1,
            label = "OK",
            className = "verified-ocr",
            editable = false,
            password = false,
            focused = false,
            visible = true,
            enabled = true,
            left = x - .008,
            top = y - .018,
            right = x + .008,
            bottom = y + .018
        )
        if (EventInviteImePolicy.okButton(listOf(safeButton)) == null ||
            frame.editor?.text?.toString() != code
        ) {
            return done(false, "Không thấy nút OK đúng vị trí trên thanh bàn phím")
        }
        val rect = Rect()
        frame.keyboard.getBoundsInScreen(rect)
        val display = windowManager.maximumWindowMetrics.bounds
        val px = (display.width() * x).toFloat()
        val py = (display.height() * y).toFloat()
        if (!rect.contains(px.toInt(), py.toInt())) {
            return done(false, "Nút OK nằm ngoài vùng của bàn phím đã xác minh")
        }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(
                Path().apply { moveTo(px, py) },
                0,
                90
            )
        ).build()
        val sent = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    later(650) { awaitKeyboardClosed(0) }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    done(false, "Android hủy cú chạm OK của bàn phím")
                }
            },
            handler
        )
        if (!sent) done(false, "Android không nhận thao tác nhấn OK của bàn phím")
    }

    private fun awaitKeyboardClosed(retries: Int) {
        if (!valid()) return
        val imeStillVisible = keyboardFrame() != null
        if (!imeStillVisible) {
            if (++stableClosed >= 2) {
                return later(600) {
                    verifyGameCode { correct ->
                        if (!valid()) return@verifyGameCode
                        if (EventInviteImePolicy.gameReady(false, true, correct)) {
                            done(true)
                        } else {
                            done(false, "Đã bấm OK nhưng chưa xác minh được mã trong popup Liên Quân; chưa bấm Xác nhận")
                        }
                    }
                }
            }
        } else {
            stableClosed = 0
        }
        if (retries >= 20) {
            return done(false, "Bàn phím chưa đóng sau khi nhấn OK; không chạm Xác nhận trong game")
        }
        later(430) { awaitKeyboardClosed(retries + 1) }
    }
}
