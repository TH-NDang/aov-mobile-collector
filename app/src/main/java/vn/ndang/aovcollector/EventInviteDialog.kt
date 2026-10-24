package vn.ndang.aovcollector

import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.text.InputType
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Standalone event account picker, intentionally not tied to Accounts/UID.
 * Passwords are entered in a masked field and passed only to the Android Keystore store.
 */
internal class EventInviteDialog(
    private val service: CollectorService,
    private val selectTask: (String) -> Boolean
) {
    private val c: Context = ContextThemeWrapper(service, Ui.windowTheme(service))
    private var codeDraft: String = EventInviteAccounts.loadCode(service).orEmpty()

    private fun display(dialog: AlertDialog) {
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    fun show() {
        val accounts = EventInviteAccounts.list(service)
        val body = Ui.stack(c).apply {
            setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 8))
        }
        body.addView(Ui.text(c, "Tài khoản dùng RIÊNG cho sự kiện; không cần UID/UUID, không dùng danh sách tài khoản thu thập tướng. Thông tin đăng nhập được mã hóa trên điện thoại.", 13f))
        body.addView(Ui.section(c, "Mã mời dùng chung"))
        val code = Ui.field(c, "Nhập mã mời của chương trình", codeDraft)
        code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        body.addView(code)
        body.addView(Ui.section(c, "Chọn tài khoản để thực hiện"))
        val checked = LinkedHashMap<String, CheckBox>()
        if (accounts.isEmpty()) {
            body.addView(Ui.text(c, "Chưa có tài khoản. Bấm Thêm tài khoản bên dưới.", 13f, Ui.warning))
        }
        accounts.forEachIndexed { i, a ->
            checked[a.key] = CheckBox(c).apply {
                text = "${i + 1}. ${a.label}"
                textSize = 14f
                isChecked = true
            }.also { body.addView(it) }
        }
        val buttons = LinearLayout(c)
        buttons.addView(Ui.button(c, "Chọn tất cả") {
            checked.values.forEach { it.isChecked = true }
        }, LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f))
        buttons.addView(Ui.button(c, "Bỏ chọn") {
            checked.values.forEach { it.isChecked = false }
        }, LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f))
        body.addView(buttons)
        val edit = Ui.button(c, "Thêm / sửa / xóa tài khoản sự kiện") {}
        body.addView(edit)
        body.addView(Ui.text(c,
            "Mỗi tài khoản: đăng xuất → đăng nhập Garena → Sự kiện → Tổng hợp sự kiện → Thập kỷ chung sức → Nhập mã mời. " +
                "Xóa mã có sẵn rồi nhập mã đã lưu; chỉ tính quà khi thấy và lưu được kết quả. Tự dừng nếu trạng thái không rõ.",
            12f, Ui.muted
        ))
        val dialog = AlertDialog.Builder(c).setTitle("Nhiệm vụ mã mời · Nhiều tài khoản")
            .setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Chọn tác vụ", null).setNegativeButton("Hủy", null).create()
        display(dialog)
        edit.setOnClickListener {
            codeDraft = code.text.toString().trim()
            dialog.dismiss()
            manage()
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = code.text.toString().trim()
            if (!EventInviteAccounts.validCode(value)) {
                code.error = "Mã mời phải có 4–40 ký tự chữ/số"
                return@setOnClickListener
            }
            val ids = checked.filterValues { it.isChecked }.keys.toList()
            if (!EventInviteAccounts.validateSelection(service, ids)) {
                android.widget.Toast.makeText(c, "Chọn ít nhất một tài khoản đã lưu và còn mật khẩu hợp lệ", android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                val fingerprint = EventInviteAccounts.saveCode(service, value)
                codeDraft = value
                if (selectTask(BuiltInMacros.eventInvite(ids, fingerprint))) dialog.dismiss()
            } catch (e: Exception) {
                code.error = e.message ?: "Không lưu được mã"
            }
        }
    }

    private fun manage() {
        val accounts = EventInviteAccounts.list(service)
        val body = Ui.stack(c).apply { setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 10)) }
        val create = Ui.button(c, "Thêm tài khoản Garena mới") {}
        body.addView(create)
        accounts.forEach { account ->
            val row = LinearLayout(c)
            row.addView(Ui.button(c, account.label) {}, LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f))
            row.addView(Ui.button(c, "Sửa") {}, LinearLayout.LayoutParams(Ui.dp(c, 70), Ui.dp(c, 44)))
            row.addView(Ui.button(c, "Xóa", dangerous = true) {}, LinearLayout.LayoutParams(Ui.dp(c, 70), Ui.dp(c, 44)))
            body.addView(row)
        }
        val dialog = AlertDialog.Builder(c).setTitle("Tài khoản riêng · Sự kiện")
            .setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Trở lại tác vụ") { _, _ -> show() }.create()
        display(dialog)
        create.setOnClickListener {
            dialog.dismiss()
            editAccount(null)
        }
        // The row's children were created as Ui buttons so they remain easy to use on a small phone.
        for (i in accounts.indices) {
            val row = body.getChildAt(i + 1) as LinearLayout
            row.getChildAt(0).setOnClickListener {
                dialog.dismiss()
                editAccount(accounts[i].key)
            }
            row.getChildAt(1).setOnClickListener {
                dialog.dismiss()
                editAccount(accounts[i].key)
            }
            row.getChildAt(2).setOnClickListener {
                val account = accounts[i]
                dialog.dismiss()
                display(
                    AlertDialog.Builder(c).setTitle("Xóa tài khoản sự kiện")
                        .setMessage("Xóa ${account.label} khỏi danh sách sự kiện? Không xóa tài khoản ở phần khác.")
                        .setPositiveButton("Xóa") { _, _ ->
                            try {
                                EventInviteAccounts.remove(service, account.key)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(c, e.message, android.widget.Toast.LENGTH_LONG).show()
                            }
                            manage()
                        }.setNegativeButton("Giữ lại") { _, _ -> manage() }.create()
                )
            }
        }
    }

    private fun editAccount(key: String?) {
        val previous = key?.let { EventInviteAccounts.load(service, it) }
        val body = Ui.stack(c).apply { setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8)) }
        body.addView(Ui.text(c, "Tên hiển thị (không bắt buộc)", 12f, Ui.muted))
        val label = Ui.field(c, "Ví dụ: Acc phụ 1", previous?.label.orEmpty()).also { body.addView(it) }
        body.addView(Ui.text(c, "Tài khoản Garena", 12f, Ui.muted))
        val user = Ui.field(c, "Email hoặc tên đăng nhập Garena", previous?.username.orEmpty()).also { body.addView(it) }
        body.addView(Ui.text(c, "Mật khẩu Garena (nhập lại khi sửa)", 12f, Ui.muted))
        val password = Ui.field(c, "Mật khẩu").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }.also { body.addView(it) }
        body.addView(Ui.text(c,
            "Mật khẩu chỉ lưu đã mã hóa trong Android Keystore; không xuất ra ZIP hoặc nhật ký.",
            12f, Ui.muted
        ))
        val dialog = AlertDialog.Builder(c).setTitle(if (previous == null) "Thêm tài khoản" else "Sửa tài khoản")
            .setView(ScrollView(c).apply { addView(body) })
            .setPositiveButton("Lưu", null).setNegativeButton("Quay lại") { _, _ -> manage() }.create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                EventInviteAccounts.save(service, user.text.toString(), password.text.toString(), label.text.toString())
                dialog.dismiss()
                manage()
            } catch (e: Exception) {
                password.error = e.message ?: "Không lưu được tài khoản"
            }
        }
    }
}
