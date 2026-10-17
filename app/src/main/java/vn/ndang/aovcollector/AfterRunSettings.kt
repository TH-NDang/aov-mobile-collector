package vn.ndang.aovcollector

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView

object AfterRunSettings {
    fun show(activity:Activity,onSaved:()->Unit={}) {
        val c=activity
        val options=AfterRun.config(c)
        val body=Ui.stack(c).apply { setPadding(Ui.dp(c,20),0,Ui.dp(c,20),Ui.dp(c,12)) }
        fun check(label:String,value:Boolean)=CheckBox(c).apply {
            text=label;isChecked=value;setTextColor(Ui.ink);textSize=14f;body.addView(this)
        }
        val enabled=check("Tự lưu kết quả lên Drive sau khi hoàn tất lượt",options.enabled)
        body.addView(Ui.text(c,"Mỗi lượt thành một ZIP, gồm ảnh chụp và ảnh gốc đã nhập của lượt đó. Chỉ chạy khi lượt hoàn tất; tạm dừng hoặc bấm Dừng sẽ giữ kết quả trong app.",12f,Ui.muted))
        val chat=check("Drive tải xong → mở ChatGPT → dot của bạn → gửi câu lệnh",options.chat)
        val prompt=Ui.field(c,"Câu lệnh đã lưu",options.prompt).apply {
            isSingleLine=false;minLines=3;maxLines=7
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        body.addView(prompt)
        val source=AfterRun.folder(c)
        body.addView(Ui.text(c,if(source==null) "Chưa chọn thư mục Drive" else "Thư mục Drive đã chọn: "+source.lastPathSegment,12f,if(source==null) Ui.warning else Ui.success))
        val dialog=AlertDialog.Builder(c).setTitle("Sau khi hoàn tất")
            .setView(ScrollView(c).apply { addView(body) }).setPositiveButton("Lưu",null).setNegativeButton("Đóng",null).create()
        body.addView(Ui.button(c,"Chọn thư mục Google Drive") {
            c.getSharedPreferences("after-run",0).edit().putBoolean("enabled",enabled.isChecked)
                .putBoolean("chat",chat.isChecked).putString("prompt",prompt.text.toString()).commit()
            try {
                activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                },AfterRun.REQUEST_FOLDER)
                dialog.dismiss()
            } catch(e:Exception) { AlertDialog.Builder(c).setMessage(e.message?:"Không mở được bộ chọn Drive").setPositiveButton("Đã hiểu",null).show() }
        })
        body.addView(Ui.text(c,"Bật thông báo của Google Drive. App đợi thông báo tải thành công có đúng tên ZIP; việc ghi xong file chưa được tính là tải xong. Khi mở ChatGPT, app chỉ gửi trong mục dot của bạn đã nhận diện.",12f,Ui.muted))
        AfterRun.latest(c)?.let { last ->
            body.addView(Ui.text(c,"Lượt gần nhất: "+last.optString("run")+"\n"+last.optString("message"),13f,Ui.ink))
            if(last.optString("stage") in setOf("failed","chat-failed")) body.addView(Ui.button(c,"Thử lại bước chưa xong") {
                AfterRun.retry(c,last.getString("run"));dialog.dismiss();onSaved()
            })
            if(last.optString("stage") in setOf("queued","writing","waiting-drive","drive-confirmed","chat-opening","prompt-filled","sending"))
                body.addView(Ui.button(c,"Dừng thao tác sau lượt",dangerous=true) {
                    AfterRun.cancel(c,last.getString("run"));dialog.dismiss();onSaved()
                })
            if(last.optString("stage")=="waiting-drive") body.addView(Ui.button(c,"Mở Drive kiểm tra") {
                try {
                    val launch=c.packageManager.getLaunchIntentForPackage(AfterRunProtocol.DRIVE)?:error("Chưa cài Google Drive")
                    c.startActivity(launch);dialog.dismiss()
                } catch(e:Exception) { AlertDialog.Builder(c).setMessage(e.message).setPositiveButton("Đã hiểu",null).show() }
            })
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { AfterRun.saveConfig(c,enabled.isChecked,chat.isChecked,prompt.text.toString());dialog.dismiss();onSaved() }
            catch(e:Exception) { prompt.error=e.message }
        }
    }
}
