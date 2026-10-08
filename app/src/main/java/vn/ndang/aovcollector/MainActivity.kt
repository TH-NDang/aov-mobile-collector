package vn.ndang.aovcollector

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var state: TextView
    private val macroFile by lazy { File(filesDir,"macro.json") }
    private var exportFolder: File? = null
    private var exporting = false
    private var showingHome = false
    private fun page(title:String):LinearLayout {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,32,24,24); setBackgroundColor(Color.rgb(240,243,250)) }
        val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll)
        text(root,title,24f); return root
    }
    private fun text(root:LinearLayout,s:String,size:Float=15f):TextView = TextView(this).apply {
        text=s; textSize=size; setTextColor(Color.rgb(23,30,52)); setPadding(0,12,0,12); root.addView(this)
    }
    private fun button(root:LinearLayout,s:String,action:()->Unit) { root.addView(Button(this).apply { text=s; setOnClickListener { action() } }) }
    private fun input(root:LinearLayout,hint:String,value:String,numeric:Boolean=false):EditText = EditText(this).apply {
        this.hint=hint; setText(value); if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER; root.addView(this)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(!macroFile.exists()) macroFile.writeText(assets.open("default-macro.json").bufferedReader().use { it.readText() })
        showHome()
    }
    private fun showHome() {
        showingHome=true
        val root=page("AOV Collector 0.2")
        state=text(root,""); updateState()
        button(root,"Bật dịch vụ Trợ năng") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button(root,"Nhập lại ảnh từ ZIP đã xuất") { if(editable()) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/zip"; addCategory(Intent.CATEGORY_OPENABLE) },12) }
        button(root,"Ảnh đã lưu • xem, xuất và xóa") { if(editable()) showRuns() }
        text(root,"Bộ 1 • Một tướng đang mở",20f)
        val name=input(root,"Tên tướng / nhãn bộ ảnh","elandorr")
        text(root,"Mở trang Chi tiết tướng, không mở popup. Mỗi lượt chụp 10 ảnh. Tọa độ đã thử với Eland’orr trên máy 2400×1080.")
        button(root,"Chuẩn bị chụp 1 tướng") { prepareBuiltIn(name.text.toString(),1,false) }
        text(root,"Bộ 2 • Lướt danh sách tướng (thử nghiệm)",20f)
        val count=input(root,"Số ô tướng cần lấy (1–200)","129",true)
        text(root,"Mở Danh sách → Tất cả, kéo về đầu, giữ nguyên sắp xếp. App lấy 5 cột × 2 hàng rồi vuốt. Ảnh danh sách của bạn có 129 ô, kể cả các dạng tướng. Chưa nhận diện tên/loại bỏ trùng; trang cuối có thể lặp. Tướng có nhiều hơn 4 biểu tượng chiêu cần chụp riêng.")
        button(root,"Thử trước 2 tướng") { prepareBuiltIn("thu-danh-sach",2,true) }
        button(root,"Chuẩn bị lấy danh sách theo số ô") {
            try {
                val n=count.text.toString().toInt(); require(n in 1..200) { "Nhập số từ 1 đến 200" }
                AlertDialog.Builder(this).setTitle("Lấy $n ô tướng?")
                    .setMessage("Ước tính ${n} phút, khoảng ${n*25} MB ảnh PNG. Hãy thử 2 tướng trước; phần vuốt chưa kiểm tra trên máy thật. Giữ nguyên danh sách khi tạm dừng, không đổi sắp xếp.")
                    .setPositiveButton("Chuẩn bị") { _,_ -> prepareBuiltIn("danh-sach",n,true) }.setNegativeButton("Hủy",null).show()
            } catch(e:Exception) { message(e.message?:"Số không hợp lệ") }
        }
        button(root,"Hiện bảng nổi / tiếp tục lượt đang có") { try { CollectorService.current?.showPanel() ?: error("Bật Trợ năng trước"); message("Mở lại đúng màn hình game trước khi bấm Chạy/tiếp.") } catch(e:Exception) { message(e.message?:"Lỗi") } }
        button(root,"Bắt đầu lượt mới với macro hiện tại") { if(editable()) { CollectorService.current?.reset(); getSharedPreferences("collector",MODE_PRIVATE).edit().clear().commit(); message("Checkpoint đã xóa; ảnh cũ vẫn còn.") } }
        button(root,"Giải thích các nút điều khiển") { showHelp() }
        text(root,"Nâng cao • Macro JSON",20f)
        editor=EditText(this).apply { setText(macroFile.readText()); typeface=android.graphics.Typeface.MONOSPACE; textSize=12f; minLines=5; gravity=android.view.Gravity.TOP }
        root.addView(editor,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,420))
        button(root,"Nhập macro JSON") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json"; addCategory(Intent.CATEGORY_OPENABLE) },10) }
        button(root,"Lưu JSON & hiện bảng nổi") { try { saveMacro(); CollectorService.current?.showPanel() ?: error("Bật Trợ năng trước") } catch(e:Exception) { message(e.message?:"JSON lỗi") } }
        button(root,"Xuất tất cả ảnh ZIP") { requestExport(null) }
    }
    private fun updateState() { if(showingHome && ::state.isInitialized) state.text=if(CollectorService.current!=null) "● Trợ năng đã kết nối" else "○ Chưa bật Trợ năng" }
    override fun onResume() { super.onResume(); updateState() }
    private fun editable():Boolean {
        if(exporting) { message("Đang xuất ZIP; chờ hoàn tất trước khi thay đổi dữ liệu."); return false }
        if(CollectorService.current?.prepareEdit()==false) { message("Đang hoàn tất bước hiện tại. Chờ một chút rồi bấm lại."); return false }; return true
    }
    private fun prepareBuiltIn(name:String,count:Int,batch:Boolean) {
        try {
            if(!editable()) return
            val raw=BuiltInMacros.create(name,count,batch); Macro(raw)
            CollectorService.current?.reset(); getSharedPreferences("collector",MODE_PRIVATE).edit().clear().commit()
            macroFile.writeText(raw); editor.setText(raw)
            CollectorService.current?.showPanel() ?: error("Đã lưu chế độ. Bật Trợ năng, rồi Hiện bảng nổi.")
            message(if(batch) "Đã chuẩn bị Bộ 2. Mở Danh sách → Tất cả ở đầu trang rồi bấm Chạy/tiếp." else "Đã chuẩn bị Bộ 1. Mở Chi tiết tướng rồi bấm Chạy/tiếp.")
        } catch(e:Exception) { message(e.message?:"Lỗi") }
    }
    private fun saveMacro() {
        val raw=editor.text.toString(); Macro(raw)
        require(editable()) { "Chưa thể lưu" }
        macroFile.writeText(raw)
    }
    private fun showHelp() { message("Chạy/tiếp: bắt đầu hoặc tiếp tục từ checkpoint.\n\nTạm dừng: hoàn tất thao tác đang thực hiện rồi dừng trước bước kế tiếp. Giữ nguyên màn game; bấm Chạy/tiếp để tiếp tục.\n\nBỏ 1 bước: chỉ dùng sau khi tạm dừng; bỏ một lần chạm/chờ/chụp, KHÔNG bỏ tướng. Có xác nhận trước khi bỏ.\n\nDừng: kết thúc lượt, giữ ảnh, không tiếp tục lượt đó. Muốn chạy lại chọn Bắt đầu lượt mới hoặc chuẩn bị bộ mới.\n\nẨn bảng: tạm dừng và ẩn bảng; về app để hiện lại. Chụp: chụp riêng khi macro đang nghỉ.\n\nMột thao tác chạm/vuốt đã gửi cho Android có thể vẫn hoàn tất sau khi bạn bấm dừng.") }
    private fun metadata(dir:File):JSONObject = try { JSONObject(File(dir,"run.json").readText()) } catch(e:Exception) { JSONObject() }
    private fun showRuns() {
        showingHome=false
        val root=page("Ảnh đã lưu")
        button(root,"← Về trang chính") { showHome() }
        text(root,"Bộ 1 và Bộ 2 nằm ở các lượt riêng. Xóa tại đây chỉ xóa dữ liệu trong app, không xóa ZIP đã xuất.")
        val captures=File(filesDir,"captures")
        val dirs=captures.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
        if(dirs.isEmpty()) text(root,"Chưa có ảnh.")
        for(dir in dirs) {
            val files=dir.listFiles()?.filter { it.extension=="png" }.orEmpty()
            val m=metadata(dir); val mode=when(m.optString("collectionMode")) { "single" -> "Bộ 1"; "all" -> "Bộ 2"; else -> "Chụp thủ công / macro cũ" }
            text(root,"$mode • ${m.optString("heroName","")}\n${dir.name}\n${files.size} ảnh • %.1f MB • ${m.optString("state","")}".format(files.sumOf { it.length() }/1048576.0))
            button(root,"Xem ảnh lượt này") { if(editable()) showImages(dir) }
            button(root,"Xuất riêng lượt này") { requestExport(dir) }
            button(root,"Xóa lượt này") { confirmDelete(dir) { showRuns() } }
        }
    }
    private fun showImages(dir:File) {
        showingHome=false
        val root=page("Ảnh: ${dir.name}")
        button(root,"← Danh sách lượt") { showRuns() }
        val files=dir.listFiles()?.filter { it.extension=="png" }?.sortedBy { it.name }.orEmpty()
        if(files.isEmpty()) text(root,"Không còn ảnh.")
        // Page thumbnails in groups of 30 to keep memory bounded on large collections.
        fun showSlice(offset:Int) {
            val grid=page("Ảnh ${offset+1}–${minOf(offset+30,files.size)} / ${files.size}")
            button(grid,"← Danh sách lượt") { showRuns() }
            for(file in files.drop(offset).take(30)) {
                text(grid,"${file.name} • %.1f MB".format(file.length()/1048576.0))
                val bitmap=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=8 })
                grid.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true; setOnClickListener { showPhoto(file,dir) } },LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,180))
                button(grid,"Xóa ảnh này") { confirmDelete(file) { showImages(dir) } }
            }
            if(offset>0) button(grid,"Trang trước") { showSlice(maxOf(0,offset-30)) }
            if(offset+30<files.size) button(grid,"Trang sau") { showSlice(offset+30) }
        }
        if(files.isNotEmpty()) showSlice(0)
    }
    private fun showPhoto(file:File,dir:File) {
        val root=page(file.name)
        button(root,"← Về bộ ảnh") { showImages(dir) }
        val image=ImageView(this).apply { setImageBitmap(BitmapFactory.decodeFile(file.path)); adjustViewBounds=true }
        root.addView(image,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        button(root,"Xóa ảnh này") { confirmDelete(file) { showImages(dir) } }
    }
    private fun confirmDelete(file:File,refresh:()->Unit) {
        if(!editable()) return
        AlertDialog.Builder(this).setTitle(if(file.isDirectory) "Xóa toàn bộ lượt?" else "Xóa ảnh?")
            .setMessage("${file.name}\nKhông thể khôi phục trong app. ZIP đã xuất vẫn giữ nguyên.")
            .setPositiveButton("Xóa") { _,_ ->
                if(editable()) {
                    val prefs=getSharedPreferences("collector",MODE_PRIVATE)
                    if(prefs.getString("run","")== (if(file.isDirectory) file.name else file.parentFile?.name)) {
                        CollectorService.current?.reset(); prefs.edit().clear().commit()
                    }
                    val ok=if(file.isDirectory) file.deleteRecursively() else file.delete()
                    if(!ok) message("Không xóa được file"); refresh()
                }
            }.setNegativeButton("Hủy",null).show()
    }
    private fun requestExport(folder:File?) {
        if(!editable()) return
        exportFolder=folder
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/zip"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"aov-${folder?.name?:"captures"}-${System.currentTimeMillis()}.zip") },11)
    }
    private fun message(s:String) { AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show() }
    @Deprecated("Legacy activity result API, no external dependency")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        try {
            if(requestCode==10) {
                val raw=contentResolver.openInputStream(uri)?.use { input ->
                    val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); var count=input.read(buffer)
                    while(count!=-1) { require(out.size()+count<=1024*1024) { "JSON tối đa 1 MB" }; out.write(buffer,0,count); count=input.read(buffer) }; out.toString("UTF-8")
                } ?: error("Không đọc được file")
                Macro(raw); editor.setText(raw); message("Đã nhập. Bấm Lưu JSON & hiện bảng nổi để dùng.")
            } else if(requestCode==12) {
                if(!editable()) return
                exporting=true
                Thread {
                    try {
                        var images=0; var bytes=0L; var entries=0
                        val prefix="import-${System.currentTimeMillis()}-"
                        val captureRoot=File(filesDir,"captures").apply { mkdirs() }
                        val created=mutableSetOf<File>()
                        try {
                            val stream=contentResolver.openInputStream(uri) ?: error("Không đọc được ZIP")
                            java.util.zip.ZipInputStream(stream).use { zip ->
                                var entry=zip.nextEntry
                                while(entry!=null) {
                                    require(++entries<=10000) { "ZIP quá nhiều file" }
                                    val parts=entry.name.split('/')
                                    require(parts.all { it.isNotEmpty() && it!=".." && it!="." && !it.contains('\\') }) { "Đường dẫn ZIP không hợp lệ" }
                                    if(!entry.isDirectory && parts.size==2 && (parts[1].endsWith(".png") || parts[1] in listOf("run.json","macro.json"))) {
                                        require(parts[0].matches(Regex("[a-zA-Z0-9_-]{1,150}"))) { "Tên thư mục không hợp lệ" }
                                        val dir=File(captureRoot,prefix+parts[0]); dir.mkdirs(); created.add(dir)
                                        val target=File(dir,parts[1]); val buffer=ByteArray(8192)
                                        target.outputStream().use { out ->
                                            var n=zip.read(buffer)
                                            while(n!=-1) {
                                                bytes+=n; require(bytes<=5L*1024*1024*1024 && filesDir.usableSpace>30L*1024*1024) { "ZIP quá lớn hoặc hết dung lượng" }
                                                out.write(buffer,0,n); n=zip.read(buffer)
                                            }
                                        }
                                        if(target.extension=="png") images++
                                    }
                                    zip.closeEntry(); entry=zip.nextEntry
                                }
                            }
                        } catch(e:Exception) { created.forEach { it.deleteRecursively() }; throw e }
                        runOnUiThread { exporting=false; message("Đã nhập $images ảnh. Xem trong Ảnh đã lưu.") }
                    } catch(e:Exception) { runOnUiThread { exporting=false; message("Nhập lỗi: ${e.message}") } }
                }.start()
            } else if(requestCode==11) {
                if(!editable()) return
                val base=File(filesDir,"captures")
                val files=(exportFolder?:base).walkTopDown().filter { it.isFile }.toList()
                val raw=macroFile.readBytes(); exporting=true
                Thread {
                    try {
                        val output=contentResolver.openOutputStream(uri) ?: error("Không mở được file đích")
                        ZipOutputStream(output).use { zip ->
                            for(file in files) { zip.putNextEntry(ZipEntry(file.relativeTo(base).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                            zip.putNextEntry(ZipEntry("macro.json")); zip.write(raw); zip.closeEntry()
                        }
                        runOnUiThread { exporting=false; message("Đã xuất ${files.size} file") }
                    } catch(e:Exception) { runOnUiThread { exporting=false; message("Xuất lỗi: ${e.message}") } }
                }.start()
            }
        } catch(e:Exception) { message(e.message?:"Lỗi file") }
    }
}
