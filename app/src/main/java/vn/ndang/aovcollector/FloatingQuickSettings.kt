package vn.ndang.aovcollector

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView

/** Settings live inside the bounded, non-focusable panel, never a full-screen touch interceptor. */
internal object FloatingQuickSettings {
    fun create(
        context: Context,
        smartLobby: Boolean,
        gestureDebug: Boolean,
        entryEditable: Boolean,
        accountStatus: String,
        onSmartLobby: (Boolean) -> Unit,
        onGestureDebug: (Boolean) -> Unit,
        onManageAccounts: () -> Unit,
        onClose: () -> Unit
    ): View {
        val body = Ui.stack(context).apply {
            setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), Ui.dp(context, 4))
        }
        body.addView(Ui.text(context, "Cài đặt nhanh", 16f, Ui.ink, true))
        body.addView(Ui.text(context, accountStatus, 12f, Ui.muted))
        body.addView(
            CheckBox(context).apply {
                text = "Nhận diện sảnh đang mở"
                textSize = 13f
                minHeight = Ui.dp(context, 48)
                isChecked = smartLobby
                isEnabled = entryEditable
                contentDescription = "Nhận diện sảnh đang mở; vẫn kiểm tra UID đã cấu hình"
                setOnCheckedChangeListener { _, checked -> onSmartLobby(checked) }
            }
        )
        body.addView(
            Ui.text(
                context,
                if (entryEditable) {
                    "Áp dụng chung từ lượt mới; không bỏ qua kiểm tra UID đã cấu hình."
                } else {
                    "Dừng hoặc hoàn tất lượt hiện tại trước khi đổi cách vào game."
                },
                12f,
                Ui.muted
            )
        )
        body.addView(
            CheckBox(context).apply {
                text = "Hiện điểm chạm và đường vuốt"
                textSize = 13f
                minHeight = Ui.dp(context, 48)
                isChecked = gestureDebug
                setOnCheckedChangeListener { _, checked -> onGestureDebug(checked) }
            }
        )
        body.addView(
            Ui.text(context, "Ảnh theo dõi vào game giữ theo cấu hình macro. Không thay đổi tài khoản hoặc UID tại đây.", 12f, Ui.muted)
        )
        body.addView(
            Ui.button(context, "Tài khoản", action = onManageAccounts).apply {
                isEnabled = entryEditable
                contentDescription = "Quản lý tài khoản; chỉ mở khi không còn lượt đang chạy hoặc tạm dừng"
            },
            LinearLayout.LayoutParams(-1, Ui.dp(context, 48))
        )
        body.addView(Ui.button(context, "Đóng cài đặt", action = onClose), LinearLayout.LayoutParams(-1, Ui.dp(context, 48)))
        return ScrollView(context).apply {
            isFillViewport = false
            addView(body)
            background = Ui.shape(Ui.surface, Ui.dp(context, 12), Ui.border)
        }
    }

    /** Entry policy changes must never reinterpret a paused run or race an in-flight gesture/save. */
    fun entryEditable(working: Boolean, unfinishedRun: Boolean, teaching: Boolean, savingLibrary: Boolean) =
        !working && !unfinishedRun && !teaching && !savingLibrary
}
