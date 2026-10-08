package vn.ndang.aovcollector

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val captures by lazy { File(filesDir,"captures").apply { mkdirs() } }
    private var folder:File?=null
    private var selected=linkedSetOf<File>()
    private var selecting=false
    private var pendingExport=listOf<File>()
    private var historyRoot:LinearLayout?=null
    private fun dp(n:Int)=Ui.dp(this,n)
    private fun meta(dir:File):JSONObject = try { JSONObject(File(dir,if(File(dir,"hero.json").exists()) "hero.json" else "run.json").readText()) } catch(_:Exception) { JSONObject() }
    private fun photos(dir:File)=dir.walkTopDown().filter { it.isFile&&it.extension=="png" }.toList()
    private fun title(file:File):String {
        if(!file.isDirectory) {
            val n=file.nameWithoutExtension
            return when {
                n.endsWith("-overview") -> "Tổng quan"
                n.endsWith("-attributes") -> "Thuộc tính"
                n.contains("passive") -> "Nội tại · "+if(n.endsWith("detail")) "Chi tiết" else "Tóm tắt"
                Regex("skill-[123]").containsMatchIn(n) -> "Chiêu ${Regex("skill-([123])").find(n)!!.groupValues[1]} · "+if(n.endsWith("detail")) "Chi tiết" else "Tóm tắt"
                n.contains("page-") -> "Trang danh sách"
                else -> "Ảnh chụp"
            }
        }
        val m=meta(file)
        if(File(file,"hero.json").exists()) return m.optString("name","Chưa rõ tên")
        if(m.optString("label").isNotBlank()) return m.optString("label")
        return when(m.optString("collectionMode")) {
            "single" -> m.optString("heroName").takeIf { it.isNotBlank()&&it!="Chưa đặt tên" }?:"Một tướng"
            "all" -> "Danh sách tướng"
            else -> if(file.name=="manual") "Chụp nhanh" else "Lượt thu thập"
        }
    }
    private fun message(s:String) { AlertDialog.Builder(this).setMessage(s).setPositiveButton("Đã hiểu",null).show() }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        if(!MacroStore.current(this).exists()) MacroStore.current(this).writeText(BuiltInMacros.create(""))
        folder=savedInstanceState?.getString("folder")?.let { File(it) }?.takeIf { it.isDirectory&&it.canonicalPath.startsWith(captures.canonicalPath+"/") }
        pendingExport=savedInstanceState?.getStringArrayList("export")?.map { File(it) }.orEmpty()
        window.statusBarColor=Ui.canvas;window.navigationBarColor=Ui.canvas
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        showHistory()
    }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out);out.putString("folder",folder?.path);out.putStringArrayList("export",ArrayList(pendingExport.map { it.path })) }
    override fun onResume() { super.onResume();if(historyRoot!=null) showHistory();crashNotice() }
    private fun crashNotice() {
        val log=CrashLog.file(this);if(!log.exists()) return
        val prefs=getPreferences(MODE_PRIVATE);if(log.lastModified()<=prefs.getLong("crash-seen",0L)) return
        prefs.edit().putLong("crash-seen",log.lastModified()).apply();showLog("Lần trước có lỗi hoặc bị gián đoạn")
    }
    private fun showLog(title:String) {
        val text=try { CrashLog.file(this).readText() } catch(_:Exception) { "" }
        // A crashed accessibility service stays off until the user re-enables it.
        val hint=if(CollectorService.current==null) "Trợ năng đang tắt hoặc bị Android dừng: bấm Bật Trợ năng, tắt rồi bật lại AOV Collector.\n\n" else ""
        AlertDialog.Builder(this).setTitle(title).setMessage(hint+"Bấm Sao chép để gửi nhật ký khi báo lỗi.\n\n"+text.takeLast(1500)).setPositiveButton("Đã hiểu",null)
            .setNeutralButton("Sao chép") { _,_ -> (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("AOV Collector",text));Toast.makeText(this,"Đã sao chép nhật ký",Toast.LENGTH_SHORT).show() }.show()
    }
    private fun shell():LinearLayout {
        val root=Ui.stack(this).apply { setBackgroundColor(Ui.canvas) }
        root.setOnApplyWindowInsetsListener { v,ins -> val b=ins.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);ins }
        setContentView(root);root.requestApplyInsets();return root
    }
    private fun header(root:LinearLayout) {
        val top=Ui.stack(this).apply { setPadding(dp(20),dp(10),dp(20),dp(12)) }
        val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        heading.addView(Ui.text(this,"AOV Collector",24f,Ui.ink,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(Ui.text(this,"Hướng dẫn",12f,Ui.accent,true).apply { setPadding(dp(8),dp(12),0,dp(12));setOnClickListener { help() } });top.addView(heading)
        top.addView(Ui.text(this,"Thu thập trong game · quản lý ảnh tại đây",13f,Ui.muted))
        val row=LinearLayout(this)
        val connected=CollectorService.current!=null
        headerAction(row,"access",if(connected) "Đã bật Trợ năng" else "Bật Trợ năng",if(connected) Color.rgb(31,132,112) else Ui.accent) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        headerAction(row,"import","Nhập file",Ui.accent) { importMenu() }
        headerAction(row,"float","Bảng nổi",Ui.accent) { val service=CollectorService.current;if(service==null) message("Bấm Bật Trợ năng, bật AOV Collector rồi quay lại.") else { service.showPanel();Toast.makeText(this,"Mở game, bấm Chọn macro trên bảng nổi để chọn cách chụp.",Toast.LENGTH_LONG).show() } }
        top.addView(row);root.addView(top)
    }
    private fun headerAction(row:LinearLayout,kind:String,label:String,color:Int,action:()->Unit) {
        val cell=Ui.stack(this).apply { gravity=Gravity.CENTER;setPadding(dp(3),dp(9),dp(3),0) }
        val icon=ImageButton(this).apply {
            setImageDrawable(ActionIcon(kind,color));background=Ui.shape(Color.WHITE,dp(16),Ui.border);setPadding(dp(12),dp(12),dp(12),dp(12))
            contentDescription=label;tooltipText=label;setOnClickListener { action() }
        }
        cell.addView(icon,LinearLayout.LayoutParams(dp(50),dp(50)))
        cell.addView(Ui.text(this,label,11f,Ui.muted,true).apply { gravity=Gravity.CENTER;setOnClickListener { action() } })
        row.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun items():List<File> = (folder?:captures).listFiles()?.filter { it.isDirectory || it.extension=="png" }?.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { if(folder==null) meta(it).optLong("updatedAt",it.lastModified()) else 0L }.thenBy { it.name }).orEmpty()
    private fun showHistory() {
        val root=shell();historyRoot=root;header(root)
        if(folder?.exists()==false) folder=null
        selected.retainAll(items().toSet())
        val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),0,dp(20),dp(6)) }
        if(folder!=null) bar.addView(Ui.text(this,"‹",30f,Ui.accent).apply { setPadding(0,0,dp(16),0);contentDescription="Quay lại thư mục trước";setOnClickListener { goBack() } })
        bar.addView(Ui.text(this,if(selecting) "Đã chọn ${selected.size}" else folder?.let { title(it) }?:"Lịch sử",20f,Ui.ink,true),LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(Ui.text(this,if(selecting) "Xong" else "Chọn",14f,Ui.accent,true).apply { setPadding(dp(16),dp(12),0,dp(12));setOnClickListener { selecting=!selecting;selected.clear();showHistory() } });root.addView(bar)
        if(selecting) root.addView(Ui.text(this,if(selected.size==items().size&&items().isNotEmpty()) "Bỏ chọn tất cả" else "Chọn tất cả",13f,Ui.accent,true).apply { setPadding(dp(20),0,dp(20),dp(10));setOnClickListener { if(selected.size==items().size) selected.clear() else selected.addAll(items());showHistory() } })
        val scroll=ScrollView(this);val body=Ui.stack(this).apply { setPadding(dp(20),dp(4),dp(20),dp(16)) };scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val list=items()
        if(list.isEmpty()) {
            val empty=Ui.card(this);empty.addView(Ui.text(this,"Ảnh sẽ xuất hiện ở đây",20f,Ui.ink,true));empty.addView(Ui.text(this,"Mở bảng nổi → chọn tác vụ → Chạy. Mỗi lượt được lưu thành một thư mục riêng.",14f,Ui.muted));body.addView(empty)
        }
        // Show 50 rows per page; only those thumbnails are decoded.
        val pageSize=50;val pages=maxOf(1,(list.size+pageSize-1)/pageSize)
        val pageIndex=getPreferences(MODE_PRIVATE).getInt("page-${folder?.path?:"root"}",0).coerceIn(0,pages-1)
        for(file in list.drop(pageIndex*pageSize).take(pageSize)) addRow(body,file)
        if(pages>1) {
            val pager=LinearLayout(this)
            pager.addView(Ui.button(this,"‹ Trước") { getPreferences(MODE_PRIVATE).edit().putInt("page-${folder?.path?:"root"}",maxOf(0,pageIndex-1)).apply();showHistory() },LinearLayout.LayoutParams(0,dp(44),1f))
            pager.addView(Ui.text(this," ${pageIndex+1}/$pages ",14f,Ui.muted).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(dp(64),dp(44)))
            pager.addView(Ui.button(this,"Sau ›") { getPreferences(MODE_PRIVATE).edit().putInt("page-${folder?.path?:"root"}",minOf(pages-1,pageIndex+1)).apply();showHistory() },LinearLayout.LayoutParams(0,dp(44),1f));body.addView(pager)
        }
        if(selecting) {
            val actions=LinearLayout(this).apply { setPadding(dp(20),dp(8),dp(20),dp(12)) }
            actions.addView(Ui.button(this,"Lưu ZIP",true) { requestExport(selected.toList()) }.apply { isEnabled=selected.isNotEmpty();alpha=if(isEnabled) 1f else .45f },LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(Ui.button(this,"Xóa",dangerous=true) { delete(selected.toList()) }.apply { isEnabled=selected.isNotEmpty();alpha=if(isEnabled) 1f else .45f },LinearLayout.LayoutParams(0,dp(48),1f).apply { leftMargin=dp(10) });root.addView(actions)
        }
    }
    private fun addRow(body:LinearLayout,file:File) {
        val card=Ui.card(this).apply { setPadding(dp(12),dp(12),dp(10),dp(12));if(selected.contains(file)) background=Ui.shape(Ui.pale,dp(20),Ui.accent) }
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val all=if(file.isDirectory) photos(file) else listOf(file)
        if(selecting) row.addView(Ui.text(this,if(selected.contains(file)) "✓" else "○",23f,Ui.accent,true),LinearLayout.LayoutParams(dp(30),-2))
        val cover=all.firstOrNull()
        val thumb=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP;background=Ui.shape(Ui.pale,dp(12));clipToOutline=true;contentDescription=null
            if(cover!=null) setImageBitmap(BitmapFactory.decodeFile(cover.path,BitmapFactory.Options().apply { inSampleSize=12 })) else setImageDrawable(ActionIcon("float",Ui.accent)) }
        row.addView(thumb,LinearLayout.LayoutParams(dp(74),dp(60)))
        val info=Ui.stack(this).apply { setPadding(dp(12),0,dp(6),0) }
        info.addView(Ui.text(this,title(file),16f,Ui.ink,true).apply { maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END })
        val size=all.sumOf { it.length() }/1048576.0
        info.addView(Ui.text(this,(if(file.isDirectory) "${all.size} ảnh · " else "PNG · ")+String.format(Locale.US,"%.1f MB",size),12f,Ui.muted))
        val m=meta(file);val date=SimpleDateFormat("dd/MM · HH:mm",Locale.getDefault()).format(Date(m.optLong("updatedAt",file.lastModified())))
        val note=if(m.optString("nameSource")=="ocr") "Tên từ ảnh" else if(m.optString("nameSource")=="unknown") "Cần đặt tên" else when(m.optString("state")) { "completed"->"Hoàn tất";"paused"->"Tạm dừng";"stopped"->"Đã kết thúc";else->date }
        info.addView(Ui.text(this,note,11f,Ui.muted));row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
        if(!selecting) row.addView(Ui.text(this,"⋮",26f,Ui.muted).apply { gravity=Gravity.CENTER;setOnClickListener { itemMenu(file) };contentDescription="Tùy chọn ${title(file)}" },LinearLayout.LayoutParams(dp(36),dp(48)))
        card.addView(row);body.addView(card)
        card.setOnClickListener { if(selecting) { if(!selected.add(file)) selected.remove(file);showHistory() } else if(file.isDirectory) { folder=file;selected.clear();showHistory() } else preview(file) }
        card.setOnLongClickListener { selecting=true;selected.add(file);showHistory();true }
    }
    private fun goBack() { if(selecting) { selecting=false;selected.clear() } else folder=folder?.parentFile?.takeUnless { it==captures };showHistory() }
    @Deprecated("Legacy navigation") override fun onBackPressed() { if(historyRoot==null) showHistory() else if(folder!=null||selecting) goBack() else super.onBackPressed() }
    private fun preview(file:File) {
        historyRoot=null;val root=shell()
        val content=Ui.stack(this).apply { setPadding(dp(20),dp(14),dp(20),dp(16)) }
        content.addView(Ui.button(this,"‹ Về danh sách") { showHistory() });content.addView(Ui.text(this,title(file),23f,Ui.ink,true));content.addView(Ui.text(this,file.name,12f,Ui.muted))
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=2 })) }
        content.addView(image,LinearLayout.LayoutParams(-1,0,1f))
        content.addView(Ui.button(this,"Lưu ảnh trong ZIP",true) { requestExport(listOf(file)) });content.addView(Ui.button(this,"Xóa ảnh",dangerous=true) { delete(listOf(file)) });root.addView(content,LinearLayout.LayoutParams(-1,-1))
    }
    private fun itemMenu(file:File) {
        val labels=if(file.isDirectory) arrayOf("Lưu thư mục thành ZIP","Đổi tên","Xóa thư mục") else arrayOf("Lưu ảnh trong ZIP","Xóa ảnh")
        AlertDialog.Builder(this).setTitle(title(file)).setItems(labels) { _,i -> when { i==0 -> requestExport(listOf(file));file.isDirectory&&i==1 -> rename(file);else -> delete(listOf(file)) } }.show()
    }
    private fun editable():Boolean {
        if(CollectorService.libraryBusy) { message("Đang xử lý file. Vui lòng chờ hoàn tất.");return false }
        if(CollectorService.current?.prepareEdit()==false) { message("Đang hoàn tất thao tác chụp. Chờ một chút rồi thử lại.");return false };return true
    }
    private fun resetIfActive(files:List<File>) {
        val prefs=getSharedPreferences("collector",MODE_PRIVATE);val run=prefs.getString("run","")?:""
        if(run.isBlank()) return
        val active=File(captures,run).canonicalPath
        if(files.any { it.canonicalPath==active||it.canonicalPath.startsWith(active+"/") }) { CollectorService.current?.reset();prefs.edit().clear().commit() }
    }
    private fun delete(files:List<File>) {
        if(files.isEmpty()||!editable()) return
        AlertDialog.Builder(this).setTitle("Xóa ${files.size} mục đã chọn?")
            .setMessage("Ảnh trong các thư mục đã chọn cũng sẽ bị xóa. File ZIP đã lưu bên ngoài app vẫn được giữ.")
            .setPositiveButton("Xóa") { _,_ -> if(editable()) { resetIfActive(files);CollectorService.libraryBusy=true
                Thread { var failed=0;files.forEach { if(!(if(it.isDirectory) it.deleteRecursively() else it.delete())) failed++ }
                    runOnUiThread { CollectorService.libraryBusy=false;selected.clear();selecting=false;showHistory();if(failed>0) message("Không xóa được $failed mục.") } }.start()
            } }.setNegativeButton("Hủy",null).show()
    }
    private fun rename(file:File) {
        if(!editable()) return
        val field=Ui.field(this,"Tên thư mục",title(file));val wrapper=Ui.stack(this).apply { setPadding(dp(20),dp(8),dp(20),dp(8));addView(field) }
        val dialog=AlertDialog.Builder(this).setTitle("Đổi tên").setView(wrapper).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create();dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val value=field.text.toString().trim();require(value.length in 1..80) { "Nhập tên từ 1 đến 80 ký tự" };if(!editable()) return@setOnClickListener
                if(File(file,"hero.json").exists()) {
                    val newDir=File(file.parentFile,HeroNames.slug(value));require(newDir==file||!newDir.exists()) { "Tên thư mục đã tồn tại" }
                    require(newDir==file||file.renameTo(newDir)) { "Không đổi tên được thư mục" }
                    val m=meta(newDir).put("name",value).put("nameSource","manual");File(newDir,"hero.json").writeText(m.toString(2))
                    val mapFile=File(newDir.parentFile,"hero-folders.json");if(mapFile.exists()) { val map=JSONObject(mapFile.readText());map.keys().asSequence().toList().forEach { if(map.optString(it)==file.name) map.put(it,newDir.name) };mapFile.writeText(map.toString(2)) }
                } else File(file,"run.json").writeText(meta(file).put("label",value).toString(2))
                dialog.dismiss();showHistory()
            } catch(e:Exception) { field.error=e.message }
        }
    }
    private fun importMenu() { AlertDialog.Builder(this).setTitle("Nhập file").setItems(arrayOf("Ảnh và thư mục từ ZIP","Macro từ JSON")) { _,i -> if(editable()) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type=if(i==0) "application/zip" else "application/json";addCategory(Intent.CATEGORY_OPENABLE) },if(i==0) 12 else 10) }.show() }
    private fun help() { val dialog=AlertDialog.Builder(this).setMessage("1. Bật Trợ năng cho AOV Collector.\n2. Mở Bảng nổi rồi mở Liên Quân.\n3. Trên bảng nổi: Chọn macro → Một tướng hoặc Danh sách → Chạy.\n\nTạm dừng: nghỉ và giữ vị trí. Tiếp tục: chạy tiếp đúng chỗ. Dừng: đóng lượt, giữ ảnh. Dấu − thu gọn bảng nhưng tác vụ vẫn chạy.\n\nẢnh được nhóm theo lượt và tên tướng đọc từ màn hình. Tên nhận dạng có thể sai; mở menu ⋮ để sửa. Chọn nhiều mục để lưu ZIP hoặc xóa cùng lúc.\n\nDanh sách tự vuốt còn thử nghiệm, có thể trùng hoặc thiếu. Bố cục hiện hỗ trợ màn ngang 2400×1080 và 4 biểu tượng chiêu. Bảng nổi tự ẩn và không nhận chạm khi macro chạm vào game.\n\nAOV Collector 0.3.2").setPositiveButton("Đã hiểu",null)
        if(CrashLog.file(this).exists()) dialog.setNeutralButton("Nhật ký lỗi") { _,_ -> showLog("Nhật ký lỗi") };dialog.show() }
    private fun requestExport(files:List<File>) {
        if(files.isEmpty()||!editable()) return
        pendingExport=files
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/zip";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"aov-${System.currentTimeMillis()}.zip") },11)
    }
    @Deprecated("Legacy activity result API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK) return;val uri=data?.data?:return
        if(!editable()) return
        if(requestCode==10) {
            try {
                val raw=contentResolver.openInputStream(uri)?.use { input -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var n=input.read(buffer);while(n!=-1) { require(out.size()+n<=1024*1024) { "Macro tối đa 1 MB" };out.write(buffer,0,n);n=input.read(buffer) };out.toString("UTF-8") }?:error("Không đọc được file")
                MacroStore.archive(this,raw)
                message("Đã thêm macro. Mở Bảng nổi → Chọn macro → Macro đã nhập để chọn.")
            } catch(e:Exception) { message(e.message?:"Không nhập được macro") };return
        }
        CollectorService.libraryBusy=true
        Thread {
            val result=try {
                if(requestCode==11) {
                    val entries=pendingExport.flatMap { if(it.isDirectory) it.walkTopDown().filter { f->f.isFile }.toList() else listOf(it) }.distinctBy { it.canonicalPath }
                    require(entries.isNotEmpty()) { "Không có file để lưu" }
                    ZipOutputStream(contentResolver.openOutputStream(uri)?:error("Không mở được nơi lưu")).use { zip ->
                        for(file in entries) { require(file.canonicalPath.startsWith(captures.canonicalPath+"/")) { "File ngoài thư mục ảnh" };zip.putNextEntry(ZipEntry(file.relativeTo(captures).invariantSeparatorsPath));file.inputStream().use { it.copyTo(zip) };zip.closeEntry() }
                    };"Đã lưu ZIP gồm ${entries.size} file."
                } else {
                    var total=0L;var count=0;var imageCount=0;val made=linkedSetOf<File>();val prefix="import-${System.currentTimeMillis()}-"
                    try {
                        java.util.zip.ZipInputStream(contentResolver.openInputStream(uri)?:error("Không đọc được ZIP")).use { zip ->
                            var entry=zip.nextEntry
                            while(entry!=null) {
                                require(++count<=20000) { "ZIP có quá nhiều file" }
                                val parts=entry.name.trimEnd('/').split('/')
                                require(parts.all { it.isNotBlank()&&it!=".."&&it!="."&&!it.contains('\\') }) { "Đường dẫn ZIP không hợp lệ" }
                                if(!entry.isDirectory&&parts.size in 2..3&&(parts.last().endsWith(".png")||parts.last().endsWith(".json"))) {
                                    require(parts[0].matches(Regex("[a-zA-Z0-9_-]{1,200}"))) { "Tên thư mục không hợp lệ" }
                                    val dir=File(captures,prefix+parts[0]).apply { mkdirs() };made.add(dir)
                                    val target=File(dir,parts.drop(1).joinToString("/"));require(target.canonicalPath.startsWith(dir.canonicalPath+"/"));target.parentFile?.mkdirs()
                                    target.outputStream().use { out -> val buffer=ByteArray(8192);var n=zip.read(buffer);var fileSize=0L
                                        while(n!=-1) { total+=n;fileSize+=n;require(total<5L*1024*1024*1024&&fileSize<64L*1024*1024&&filesDir.usableSpace>30L*1024*1024) { "File quá lớn hoặc không đủ dung lượng" };out.write(buffer,0,n);n=zip.read(buffer) }
                                    };if(target.extension=="png") imageCount++
                                }
                                zip.closeEntry();entry=zip.nextEntry
                            }
                        }
                    } catch(e:Exception) { made.forEach { it.deleteRecursively() };throw e }
                    "Đã nhập $imageCount ảnh."
                }
            } catch(e:Exception) { "Không hoàn tất: ${e.message}" }
            runOnUiThread { CollectorService.libraryBusy=false;selected.clear();selecting=false;showHistory();message(result) }
        }.start()
    }
    private class ActionIcon(val kind:String,val color:Int):Drawable() {
        override fun draw(canvas:Canvas) {
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=this@ActionIcon.color;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
            when(kind) {
                "access" -> { canvas.drawCircle(12f,4f,2f,p);canvas.drawLine(4f,9f,20f,9f,p);canvas.drawLine(12f,7f,12f,15f,p);canvas.drawLine(12f,15f,7f,22f,p);canvas.drawLine(12f,15f,17f,22f,p) }
                "import" -> { canvas.drawLine(12f,2f,12f,15f,p);canvas.drawLine(7f,10f,12f,15f,p);canvas.drawLine(17f,10f,12f,15f,p);val path=Path().apply { moveTo(3f,15f);lineTo(3f,21f);lineTo(21f,21f);lineTo(21f,15f) };canvas.drawPath(path,p) }
                else -> { canvas.drawRoundRect(2f,3f,21f,20f,3f,3f,p);p.style=Paint.Style.FILL;canvas.drawRoundRect(11f,11f,23f,23f,3f,3f,p) }
            };canvas.restore()
        }
        override fun setAlpha(alpha:Int) {} override fun setColorFilter(filter:ColorFilter?) {} @Deprecated("Deprecated API") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}
