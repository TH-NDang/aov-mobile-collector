package vn.ndang.aovcollector

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var state: TextView
    private val macroFile by lazy { File(filesDir,"macro.json") }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(!macroFile.exists()) macroFile.writeText(assets.open("default-macro.json").bufferedReader().use { it.readText() })
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,40,32,32); setBackgroundColor(Color.rgb(240,243,250)) }
        val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll)
        fun text(s:String,size:Float=15f):TextView { val t=TextView(this).apply { text=s; textSize=size; setTextColor(Color.rgb(23,30,52)); setPadding(0,12,0,12) }; root.addView(t); return t }
        text("AOV Collector",28f)
        text("Bản thử 0.1 • Điện thoại thật • Android 11+\nĐiều hướng menu, chụp ảnh, tiếp tục từ checkpoint.")
        state=text("")
        fun button(s:String,action:()->Unit) { root.addView(Button(this).apply { text=s; setOnClickListener { action() } }) }
        button("1. Bật dịch vụ Trợ năng") {
            AlertDialog.Builder(this).setTitle("Quyền sử dụng")
                .setMessage("AOV Collector dùng Trợ năng để chạm/vuốt theo macro bạn chọn, kiểm tra app đang mở và chụp toàn màn hình. Ảnh chỉ lưu trên máy; app không có quyền Internet. Bật dịch vụ khi cần và tắt sau khi dùng. Tự động chạm vẫn có thể bị game coi là macro; điện thoại thật không bảo đảm an toàn tài khoản.")
                .setPositiveButton("Mở cài đặt") { _,_ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.setNegativeButton("Để sau",null).show()
        }
        button("2. Lưu macro & hiện bảng nổi") {
            try { saveMacro(); val service=CollectorService.current ?: error("Bạn chưa bật Trợ năng"); service.showPanel(); Toast.makeText(this,"Mở Liên Quân, tới menu cần chụp, rồi bấm Chạy",Toast.LENGTH_LONG).show() }
            catch(e:Exception) { message(e.message?:"Lỗi") }
        }
        button("Bắt đầu lượt mới (xóa checkpoint)") { CollectorService.current?.reset() ?: message("Bật Trợ năng trước") }
        button("Nhập macro JSON") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json"; addCategory(Intent.CATEGORY_OPENABLE) },10) }
        button("Xuất toàn bộ ảnh + metadata ZIP") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/zip"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"aov-captures-${System.currentTimeMillis()}.zip") },11) }
        text("Macro JSON",20f)
        text("Tọa độ x/y từ 0 đến 1 theo toàn màn hình. Macro mẫu chỉ chụp một ảnh, không tự bấm tướng. Dùng panel Tạm dừng trước khi sửa hoặc xuất ảnh.")
        editor=EditText(this).apply { setText(macroFile.readText()); typeface=android.graphics.Typeface.MONOSPACE; textSize=13f; minLines=12; gravity=android.view.Gravity.TOP; setSingleLine(false) }
        root.addView(editor,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,700))
        button("Lưu JSON") { try { saveMacro(); message("Đã lưu macro") } catch(e:Exception) { message(e.message?:"JSON lỗi") } }
        text("Cách thử: bật Trợ năng → hiện bảng → mở game thủ công → giữ màn hình ngang → Chạy. Bảng nổi có thể kéo bằng dòng tiêu đề, tự ẩn khi chụp. Ảnh nằm trong bộ nhớ riêng của app; xuất ZIP để lấy ra, gỡ app sẽ xóa dữ liệu.\n\nChưa có Record, nhận diện ảnh, vòng lặp theo tướng hoặc hiệu chỉnh tự động. Cần ảnh màn hình của máy bạn để xây macro chiêu thực tế.")
    }
    override fun onResume() { super.onResume(); if(::state.isInitialized) state.text=if(CollectorService.current!=null) "● Trợ năng đã kết nối" else "○ Chưa bật Trợ năng" }
    private fun saveMacro() {
        val raw=editor.text.toString(); Macro(raw)
        // Stop the current execution before replacing its persisted source.
        CollectorService.current?.onInterrupt()
        macroFile.writeText(raw)
    }
    private fun message(s:String) { AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show() }
    @Deprecated("Legacy activity result API, no external dependency")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        try {
            when(requestCode) {
                10 -> {
                    val raw=contentResolver.openInputStream(uri)?.use { input ->
                        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); var count=input.read(buffer)
                        while(count!=-1) { require(out.size()+count<=1024*1024) { "JSON tối đa 1 MB" }; out.write(buffer,0,count); count=input.read(buffer) }; out.toString("UTF-8")
                    } ?: error("Không đọc được file")
                    Macro(raw); editor.setText(raw); message("Đã nhập. Bấm Lưu JSON để dùng.")
                }
                11 -> {
                    CollectorService.current?.onInterrupt()
                    val files=File(filesDir,"captures").walkTopDown().filter { it.isFile }.toList()
                    Thread {
                        try {
                            val output=contentResolver.openOutputStream(uri) ?: error("Không mở được file đích")
                            ZipOutputStream(output).use { zip ->
                                for(file in files) { zip.putNextEntry(ZipEntry(file.relativeTo(File(filesDir,"captures")).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                                zip.putNextEntry(ZipEntry("macro.json")); zip.write(macroFile.readBytes()); zip.closeEntry()
                            }
                            runOnUiThread { message("Đã xuất ${files.size} file") }
                        } catch(e:Exception) { runOnUiThread { message("Xuất lỗi: ${e.message}") } }
                    }.start()
                }
            }
        } catch(e:Exception) { message(e.message?:"Lỗi file") }
    }
}
