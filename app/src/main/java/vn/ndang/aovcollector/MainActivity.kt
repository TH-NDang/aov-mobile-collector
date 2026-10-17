package vn.ndang.aovcollector

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val captures by lazy { File(filesDir,"captures").apply { mkdirs() } }
    private var folder:File?=null
    private var selected=linkedSetOf<File>()
    private var selecting=false
    private var pendingExport=listOf<File>()
    private var pendingCopy=listOf<File>()
    private val copyFolderRequest=15
    private var historyRoot:LinearLayout?=null
    // Rebuilding the list must not throw the reader back to the top.
    private val scrollMemory=HashMap<String,Int>()
    private var historyScroll:ScrollView?=null
    private var shownKey="root"
    private val historyQueries=HashMap<String,String>()
    private val historyPages=HashMap<String,Int>()
    private val historyTrail=ArrayList<File?>()
    private val historySearchHandler=Handler(Looper.getMainLooper())
    private val historySearchExecutor=Executors.newSingleThreadExecutor()
    private var historySearchTask:Runnable?=null
    private var historySearchToken=0
    private var historyResultsKey=""
    private var historyResults=emptyList<File>()
    private val thumbs=object:android.util.LruCache<String,Bitmap>(24*1024*1024) { override fun sizeOf(key:String,value:Bitmap)=value.byteCount }
    private fun thumb(file:File,sample:Int):Bitmap? { val key="${file.path}:${file.lastModified()}:$sample"
        return thumbs.get(key)?:BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })?.also { thumbs.put(key,it) } }
    private fun createdAt(dir:File):Long {
        val m=meta(dir); if(m.has("createdAt")) return m.optLong("createdAt")
        Regex("\\d{8}-\\d{6}").find(dir.name)?.let { found -> try { return SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).parse(found.value)!!.time } catch(_:Exception) {} }
        return dir.lastModified()
    }
    private fun dp(n:Int)=Ui.dp(this,n)
    private fun meta(dir:File):JSONObject = try { JSONObject(File(dir,if(File(dir,"hero.json").exists()) "hero.json" else "run.json").readText()) } catch(_:Exception) { JSONObject() }
    private fun photos(dir:File)=dir.walkTopDown().filter { GamePhotoImport.isImage(it) }.toList()
    private fun title(file:File):String {
        val photoRoot=File(captures,GamePhotoImport.ROOT)
        val photoPath=if(file.path.startsWith(photoRoot.path+File.separator))
            file.relativeTo(photoRoot).invariantSeparatorsPath else null
        if(!file.isDirectory) {
            val n=file.nameWithoutExtension
            if(photoPath!=null)
                return file.nameWithoutExtension.replace(Regex("-[a-f0-9]{14}$"),"").replace('-',' ')
            // A hero's other form: form-2-skill-3-detail, and its skill row form-2-icons.
            val form=Regex("form-(\\d)-").find(n)?.let { "Dạng ${it.groupValues[1]} · " }.orEmpty()
            return when {
                n.endsWith("-overview") -> "Tổng quan"
                n.endsWith("-attributes") -> "Thuộc tính"
                n.endsWith("-icons") -> form+"Hàng chiêu"
                n.endsWith("skill-row-unknown") -> "Hàng chiêu không nhận ra"
                n.contains("passive") -> form+"Nội tại · "+if(n.endsWith("detail")) "Chi tiết" else "Tóm tắt"
                Regex("skill-[1-4]").containsMatchIn(n) -> form+"Chiêu ${Regex("skill-([1-4])").find(n)!!.groupValues[1]} · "+if(n.endsWith("detail")) "Chi tiết" else "Tóm tắt"
                n.contains("page-") -> "Trang danh sách"
                n.contains("chess__") -> if(n.contains("__trang__")) "Trang "+(n.substringAfterLast("__").toIntOrNull()?:"")
                    else "Chi tiết · trang "+(n.substringAfterLast("__").toIntOrNull()?:"")
                // A screen of a hero's players' ranking: 0003-hero-001-quoc-gia-02.
                Regex("-(so-1-may-chu|tinh|mien|quoc-gia|may-chu)-(\\d{2})$").find(n)!=null -> Regex("-(so-1-may-chu|tinh|mien|quoc-gia|may-chu)-(\\d{2})$").find(n)!!.groupValues.let { g ->
                    BoardScreens.Tab.values().first { it.slug==g[1] }.label+" · trang ${g[2].toInt()}" }
                // A page of the hero statistics: 0003-ty-le-dau-5v5-07.
                RankScreens.COMBOS.any { n.contains(it.slug) } -> "Trang ${n.substringAfterLast('-').toIntOrNull()?:""}"
                else -> "Ảnh chụp"
            }
        }
        RankScreens.folderTitle(file.name)?.let { return it }
        if(file.name==GamePhotoImport.ROOT) return "Ảnh gốc Cờ Liên Quân · Pictures"
        photoPath?.let { path ->
            GamePhotoImport.nativeFolderTitle(path)?.let { return it }
            val parent=path.substringBeforeLast('/',"")
            if(GamePhotoImport.nativeFolderTitle(parent)!=null)
                return "Lượt "+file.name.replace('-',' ')
        }
        // Keep the former flat layout readable for older imported game photos.
        GamePhotoImport.GROUPS.firstOrNull { it.key==file.name }?.let { return it.title }
        if(file.parentFile?.name in GamePhotoImport.GROUPS.map { it.key })
            return "Lượt "+file.name.replace('-',' ')
        ChessWiki.TOPICS.firstOrNull { it.key==file.name }?.let { return it.label }
        when(file.name) {
            "thong-tin" -> return "Thông tin cơ bản"
            "quan-co" -> return "Quân cờ"
            "tuong-tac" -> return "Tương tác"
            "trang-bi" -> return "Trang bị"
            "nhiem-vu" -> return "Nhiệm vụ"
            "chuc-phuc" -> return "Chúc Phúc"
            "tuong" -> return "Chi tiết quân cờ"
            "phe" -> return "Phe"
            "nghe" -> return "Nghề"
            "vat-pham" -> return "Chi tiết trang bị"
        }
        if(Regex("^[a-z0-9_-]+-[a-f0-9]{8}$").matches(file.name))
            return file.name.substringBeforeLast('-').replace('-',' ').replaceFirstChar { it.uppercase() }
        val m=meta(file)
        if(File(file,"hero.json").exists()) return m.optString("name","Chưa rõ tên")
        if(m.optString("label").isNotBlank()) return m.optString("label")
        return when(m.optString("collectionMode")) {
            "single" -> m.optString("heroName").takeIf { it.isNotBlank()&&it!="Chưa đặt tên" }?:"Một tướng"
            "all" -> "Danh sách tướng"
            "pick" -> m.optString("heroName").takeIf { it.isNotBlank() }?:"Lấy thông tin tướng"
            "ranking" -> "Thống kê tướng"
            "board" -> "Chiến khu"
            "chess" -> "Cờ Liên Quân · Bách khoa"
            else -> if(file.name=="manual") "Chụp nhanh" else "Lượt thu thập"
        }
    }
    private fun message(s:String) { AlertDialog.Builder(this).setMessage(s).setPositiveButton("Đã hiểu",null).show() }
    /**
     * Dialog for a long ZIP export or import, showing [FileJob]. "Hủy" stops the job; "Chạy nền" closes the dialog
     * and leaves the job to its notification, which opens the dialog again when tapped.
     */
    private inner class FileProgress:FileJob.Dialog {
        private val bar=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000; isIndeterminate=true; progressTintList=android.content.res.ColorStateList.valueOf(Ui.accent) }
        private val line=Ui.text(this@MainActivity,"",14f)
        private val detail=Ui.text(this@MainActivity,"",12f,Ui.muted).apply { maxLines=2 }
        private val dialog=AlertDialog.Builder(this@MainActivity).setTitle(FileJob.title)
            .setView(Ui.stack(this@MainActivity).apply { setPadding(dp(22),dp(10),dp(22),0); addView(bar,LinearLayout.LayoutParams(-1,dp(16))); addView(line); addView(detail) })
            .setCancelable(false).setNegativeButton("Hủy",null).setPositiveButton("Chạy nền",null).create()
        init {
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { FileJob.cancel() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { close();runInBackground() }
            // A long export must not stop because the screen went off.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            FileJob.dialog=this;progress()
        }
        override fun progress() {
            val total=FileJob.total
            bar.isIndeterminate=total<=0; if(total>0) bar.progress=(FileJob.done*1000/total).toInt().coerceIn(0,1000)
            line.text=FileJob.line; detail.text=FileJob.file
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled=!FileJob.cancelled
        }
        override fun finished() { close() }
        fun close() { if(FileJob.dialog===this) FileJob.dialog=null; if(dialog.isShowing) dialog.dismiss(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); if(this@MainActivity.progress===this) this@MainActivity.progress=null }
    }
    private var progress:FileProgress?=null
    /** Opens the dialog of the job running in the background. */
    private fun showJob() { if(FileJob.running&&FileJob.dialog==null&&!isFinishing) progress=FileProgress() }
    private fun runInBackground() {
        // Android may have refused the service when the job started; the app is surely in front now.
        FileJob.keepRunning()
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),13)
        else Toast.makeText(this,"Đang chạy nền. Xem tiến độ trên thanh thông báo; bấm vào thông báo để mở lại.",Toast.LENGTH_LONG).show()
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode!=13) return
        if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) Toast.makeText(this,"Đang chạy nền. Xem tiến độ trên thanh thông báo; bấm vào thông báo để mở lại.",Toast.LENGTH_LONG).show()
        else message("Chưa cho phép thông báo nên tiến độ không hiện trên thanh thông báo. Việc lưu vẫn chạy nền; mở lại app để xem tiến độ và kết quả.")
    }
    // The screen in front gets the result of a job; with none in front, the result comes as a notification.
    private val jobDone:(String)->Unit={ result -> showHistory();message(result) }
    override fun onCreate(savedInstanceState:Bundle?) {
        // The look chosen in "Giao diện" (or the phone's), before any view or dialog is made.
        Ui.use(this); setTheme(Ui.windowTheme(this))
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        if(!MacroStore.current(this).exists()) MacroStore.current(this).writeText(BuiltInMacros.create(""))
        folder=savedInstanceState?.getString("folder")?.let { File(it) }?.takeIf { it.isDirectory&&it.canonicalPath.startsWith(captures.canonicalPath+"/") }
        savedInstanceState?.getString("history-search")?.let { historyQueries[(folder?:captures).path]=it }
        pendingExport=savedInstanceState?.getStringArrayList("export")?.map { File(it) }.orEmpty()
        pendingCopy=savedInstanceState?.getStringArrayList("copy")?.map { File(it) }.orEmpty()
        window.statusBarColor=Ui.canvas;window.navigationBarColor=Ui.canvas
        window.decorView.systemUiVisibility=if(Ui.colors===Ui.DARK) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        showHistory()
        if(intent.getBooleanExtra(FileJob.SHOW,false)) showJob()
        if(intent.getBooleanExtra(Accounts.OPEN,false)) { intent.removeExtra(Accounts.OPEN);accountDialog() }
    }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out);out.putString("folder",folder?.path);out.putString("history-search",historyQueries[(folder?:captures).path].orEmpty());out.putStringArrayList("export",ArrayList(pendingExport.map { it.path }));out.putStringArrayList("copy",ArrayList(pendingCopy.map { it.path })) }
    override fun onResume() {
        super.onResume();FileJob.screen=jobDone
        if(historyRoot!=null) showHistory()
        crashNotice()
        // Returning from the game: fetch exactly the photos marked by Save Photo in the last run.
        GamePhotoSync.request(this)
        photoRevision=GamePhotoSync.revision
        historySearchHandler.postDelayed(photoRefresh,1500)
    }
    override fun onPause() { historySearchHandler.removeCallbacks(photoRefresh);if(FileJob.screen===jobDone) FileJob.screen=null;super.onPause() }
    override fun onDestroy() { historySearchToken++;historySearchTask?.let { historySearchHandler.removeCallbacks(it) };historySearchExecutor.shutdownNow();progress?.close();super.onDestroy() }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra(FileJob.SHOW,false)) showJob()
        if(intent.getBooleanExtra(Accounts.OPEN,false)) { intent.removeExtra(Accounts.OPEN);accountDialog() } }
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
        Ui.use(this)
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
        headerAction(row,"access",if(connected) "Đã bật Trợ năng" else "Bật Trợ năng",if(connected) Ui.success else Ui.accent) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        headerAction(row,"import","Nhập file",Ui.accent) { importMenu() }
        headerAction(row,"account","Tài khoản ("+Accounts.list(this).size+")",Ui.accent) { accountDialog() }
        headerAction(row,"theme","Giao diện",Ui.accent) { themeDialog() }
        headerAction(row,"float","Bảng nổi",Ui.accent) { val service=CollectorService.current;if(service==null) message("Bấm Bật Trợ năng, bật AOV Collector rồi quay lại.") else { service.showPanel();Toast.makeText(this,"Mở game, bấm Chọn macro trên bảng nổi để chọn cách chụp.",Toast.LENGTH_LONG).show() } }
        top.addView(row)
        top.addView(Ui.text(this,
            if(GamePhotoImport.hasSource(this)) "Ảnh từ Pictures · "+GamePhotoImport.sourceLabel(this)+" ›"
            else "Ảnh từ Pictures · Chọn thư mục để chép vào app ›",12f,Ui.accent,true).apply {
            setPadding(dp(6),dp(8),dp(6),dp(5))
            contentDescription="Đồng bộ ảnh đã lưu trong Pictures vào app"
            setOnClickListener { gamePhotosDialog() }
        })
        root.addView(top)
    }
    private fun headerAction(row:LinearLayout,kind:String,label:String,color:Int,action:()->Unit) {
        val cell=Ui.stack(this).apply { gravity=Gravity.CENTER;setPadding(dp(3),dp(9),dp(3),0) }
        val icon=ImageButton(this).apply {
            setImageDrawable(ActionIcon(kind,color));background=Ui.shape(Ui.surface,dp(16),Ui.border);setPadding(dp(12),dp(12),dp(12),dp(12))
            contentDescription=label;tooltipText=label;setOnClickListener { action() }
        }
        cell.addView(icon,LinearLayout.LayoutParams(dp(48),dp(48)))
        cell.addView(Ui.text(this,label,11f,Ui.muted,true).apply { gravity=Gravity.CENTER;maxLines=2;setOnClickListener { action() } })
        row.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
    }
    // A run's record of the way into the game is opened from the run's menu, not listed as a hero folder.
    private fun items():List<File> = (folder?:captures).listFiles()?.filter { (it.isDirectory&&it.name!=GameEntry.TRACE_DIR) || GamePhotoImport.isImage(it) }?.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { if(folder==null) meta(it).optLong("updatedAt",it.lastModified()) else 0L }.thenBy { it.name }).orEmpty()
    /** Local search in this folder, or every nested folder when searching from history root. */
    private fun showHistory() {
        historyScroll?.let { scrollMemory[shownKey]=it.scrollY }
        ++historySearchToken
        historySearchTask?.let { historySearchHandler.removeCallbacks(it) }
        if(folder?.isDirectory==false) { folder=null;historyTrail.clear() }
        val scope=folder?:captures
        val scopeKey=scope.path
        val query=historyQueries[scopeKey].orEmpty()
        val root=shell();historyRoot=root;header(root)
        selected.removeAll { !it.exists()||!it.path.startsWith(scope.path+"/") }
        var visible=emptyList<File>()
        val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),0,dp(20),dp(6)) }
        if(folder!=null) bar.addView(Ui.text(this,"‹",30f,Ui.accent).apply {
            setPadding(0,0,dp(16),0);contentDescription="Quay lại thư mục trước";setOnClickListener { goBack() }
        })
        bar.addView(Ui.text(this,if(selecting) "Đã chọn "+selected.size else folder?.let { title(it) }?:"Lịch sử",
            20f,Ui.ink,true),LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(Ui.text(this,if(selecting) "Xong" else "Chọn",14f,Ui.accent,true).apply {
            setPadding(dp(16),dp(12),0,dp(12))
            setOnClickListener { selecting=!selecting;selected.clear();showHistory() }
        })
        root.addView(bar)
        val searchRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),0,dp(20),0) }
        val input=Ui.field(this,if(folder==null) "Tìm trong toàn bộ lịch sử…" else "Tìm trong "+title(scope)+"…",query).apply {
            textSize=14f;setSingleLine(true);isEnabled=!selecting
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            contentDescription="Tìm kiếm trong lịch sử và thư mục hiện tại"
        }
        searchRow.addView(input,LinearLayout.LayoutParams(0,dp(46),1f))
        val clear=Ui.text(this,"×",24f,Ui.accent,true).apply {
            gravity=Gravity.CENTER;setPadding(dp(10),0,0,0)
            contentDescription="Xóa tìm kiếm";visibility=if(query.isBlank()) View.GONE else View.VISIBLE
            setOnClickListener { input.setText("") }
        }
        searchRow.addView(clear,LinearLayout.LayoutParams(dp(40),dp(46)))
        root.addView(searchRow)
        val hint=Ui.text(this,"",12f,Ui.muted).apply { includeFontPadding=false;setPadding(dp(20),0,dp(20),dp(4)) }
        root.addView(hint)
        var selectAll:TextView?=null
        if(selecting) {
            selectAll=Ui.text(this,"Chọn tất cả kết quả",13f,Ui.accent,true).apply {
                setPadding(dp(20),0,dp(20),dp(10))
                setOnClickListener {
                    if(visible.isNotEmpty()) {
                        if(selected.containsAll(visible)) selected.removeAll(visible.toSet())
                        else selected.addAll(visible)
                    }
                    showHistory()
                }
            }
            root.addView(selectAll)
        }
        val scroll=ScrollView(this)
        val body=Ui.stack(this).apply { setPadding(dp(20),dp(4),dp(20),dp(16)) }
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        shownKey=scopeKey+"|"+query;historyScroll=scroll
        val savedY=scrollMemory[shownKey]?:0
        if(savedY>0) scroll.viewTreeObserver.addOnGlobalLayoutListener(object:ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                scroll.scrollTo(0,savedY)
            }
        })

        fun render(list:List<File>,filter:String) {
            if(historyRoot!==root) return
            visible=list;body.removeAllViews()
            val searching=filter.isNotBlank()
            hint.text=if(searching) list.size.toString()+
                (if(list.size>=HistorySearch.LIMIT) "+ kết quả · nhập cụ thể hơn" else " kết quả")+
                " trong "+(folder?.let { title(it) }?:"toàn bộ lịch sử")
                else if(folder==null) "Các lượt thu thập đã lưu" else "Nội dung thư mục hiện tại"
            if(list.isEmpty()) {
                val empty=Ui.card(this)
                empty.addView(Ui.text(this,if(searching) "Không tìm thấy kết quả" else "Ảnh sẽ xuất hiện ở đây",19f,Ui.ink,true))
                empty.addView(Ui.text(this,if(searching)
                    "Thử tên tướng, kỹ năng, tab hoặc tên ảnh khác. Có thể tìm không dấu."
                    else "Mở bảng nổi → chọn tác vụ → Chạy. Mỗi lượt được lưu trong một thư mục.",13f,Ui.muted))
                body.addView(empty)
            }
            val pageId=scopeKey+"|"+filter
            val pages=maxOf(1,(list.size+49)/50)
            val requested=if(searching) historyPages[pageId]?:0
                else getPreferences(MODE_PRIVATE).getInt("page-"+(folder?.path?:"root"),0)
            val page=requested.coerceIn(0,pages-1)
            for(file in list.drop(page*50).take(50)) addRow(body,file,if(searching) scope else null)
            if(pages>1) {
                val pager=LinearLayout(this)
                fun turn(to:Int) {
                    if(searching) historyPages[pageId]=to
                    else getPreferences(MODE_PRIVATE).edit().putInt("page-"+(folder?.path?:"root"),to).apply()
                    render(list,filter);scroll.post { scroll.scrollTo(0,0) }
                }
                pager.addView(Ui.button(this,"‹ Trước") { turn(maxOf(0,page-1)) },LinearLayout.LayoutParams(0,dp(44),1f))
                pager.addView(Ui.text(this,(page+1).toString()+"/"+pages,14f,Ui.muted).apply { gravity=Gravity.CENTER },
                    LinearLayout.LayoutParams(dp(66),dp(44)))
                pager.addView(Ui.button(this,"Sau ›") { turn(minOf(pages-1,page+1)) },LinearLayout.LayoutParams(0,dp(44),1f))
                body.addView(pager)
            }
            selectAll?.text=if(list.isNotEmpty()&&selected.containsAll(list)) "Bỏ chọn tất cả kết quả" else "Chọn tất cả kết quả"
            selectAll?.isEnabled=list.isNotEmpty()
        }

        fun startSearch(text:String) {
            val token=++historySearchToken
            historySearchTask?.let { historySearchHandler.removeCallbacks(it) }
            historySearchTask=null
            if(text.isBlank()) { historyResultsKey="";historyResults=emptyList();render(items(),"");return }
            val key=scopeKey+"|"+text
            if(historyResultsKey==key) render(historyResults,text)
            else {
                visible=emptyList();body.removeAllViews()
                hint.text="Đang tìm trong "+(folder?.let { title(it) }?:"toàn bộ lịch sử")+"…"
            }
            if(selecting&&historyResultsKey==key) return
            val task=Runnable {
                historySearchExecutor.execute {
                    val matches=try { HistorySearch.find(scope,text,{ title(it) }) } catch(_:Exception) { emptyList() }
                    runOnUiThread {
                        if(token!=historySearchToken||historyRoot!==root||isFinishing) return@runOnUiThread
                        historyResultsKey=key;historyResults=matches
                        render(matches,text)
                    }
                }
            }
            historySearchTask=task;historySearchHandler.postDelayed(task,250)
        }
        input.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val next=s?.toString().orEmpty()
                historyQueries[scopeKey]=next;historyPages[scopeKey+"|"+next]=0
                selected.clear();clear.visibility=if(next.isBlank()) View.GONE else View.VISIBLE
                scrollMemory[shownKey]=scroll.scrollY;shownKey=scopeKey+"|"+next
                scroll.scrollTo(0,0);startSearch(next)
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })

        val free=" · máy còn trống "+Format.size(filesDir.usableSpace)
        if(selecting) root.addView(Ui.text(this,if(selected.isEmpty()) "Chọn mục để lưu ZIP hoặc xóa"+free
            else "Đã chọn "+Format.size(selected.sumOf { f -> if(f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length() })+" · file ZIP có dung lượng gần như vậy"+free,
            12f,Ui.muted).apply { setPadding(dp(20),dp(6),dp(20),0) })
        else if(folder==null) root.addView(Ui.text(this,"Ảnh đã lưu: "+
            Format.size(captures.walkTopDown().filter { it.isFile }.sumOf { it.length() })+free,12f,Ui.muted)
            .apply { setPadding(dp(20),dp(6),dp(20),dp(6)) })
        if(selecting) {
            val actions=LinearLayout(this).apply { setPadding(dp(20),dp(8),dp(20),dp(12)) }
            actions.addView(Ui.button(this,"Lưu ZIP",true) { requestExport(selected.toList()) }
                .apply { isEnabled=selected.isNotEmpty();alpha=if(isEnabled) 1f else .45f;textSize=12f },
                LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(Ui.button(this,"Sao chép") { requestCopy(selected.toList()) }
                .apply { isEnabled=selected.isNotEmpty();alpha=if(isEnabled) 1f else .45f;textSize=12f },
                LinearLayout.LayoutParams(0,dp(48),1f).apply { leftMargin=dp(7) })
            actions.addView(Ui.button(this,"Xóa",dangerous=true) { delete(selected.toList()) }
                .apply { isEnabled=selected.isNotEmpty();alpha=if(isEnabled) 1f else .45f;textSize=12f },
                LinearLayout.LayoutParams(0,dp(48),1f).apply { leftMargin=dp(7) })
            root.addView(actions)
        }
        startSearch(query)
    }
    private fun addRow(body:LinearLayout,file:File,searchRoot:File?=null) {
        val card=Ui.card(this).apply { setPadding(dp(12),dp(12),dp(10),dp(12));if(selected.contains(file)) background=Ui.shape(Ui.pale,dp(20),Ui.accent) }
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val all=if(file.isDirectory) photos(file) else listOf(file)
        if(selecting) row.addView(Ui.text(this,if(selected.contains(file)) "✓" else "○",23f,Ui.accent,true),LinearLayout.LayoutParams(dp(30),-2))
        val cover=all.firstOrNull()
        val thumb=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP;background=Ui.shape(Ui.pale,dp(12));clipToOutline=true;contentDescription=null
            if(cover!=null) setImageBitmap(thumb(cover,12)) else setImageDrawable(ActionIcon("float",Ui.accent)) }
        row.addView(thumb,LinearLayout.LayoutParams(dp(74),dp(60)))
        val info=Ui.stack(this).apply { setPadding(dp(12),0,dp(6),0) }
        info.addView(Ui.text(this,title(file),16f,Ui.ink,true).apply { maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END })
        if(searchRoot!=null&&file.parentFile!=searchRoot) {
            val relative=try { file.parentFile.relativeTo(searchRoot).invariantSeparatorsPath } catch(_:Exception) { "" }
            if(relative.isNotBlank()) info.addView(Ui.text(this,"Trong: "+relative.split('/').joinToString(" › "),11f,Ui.muted)
                .apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE })
        }
        val size=all.sumOf { it.length() }
        val time=if(file.isDirectory) SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date(createdAt(file)))+" · " else ""
        val m=meta(file)
        val ran=m.optLong("activeMs",0L).takeIf { it>0 }?.let { " · chạy "+duration(it) }?:""
        info.addView(Ui.text(this,time+(if(file.isDirectory) "${all.size} ảnh · " else file.extension.uppercase()+" · ")+Format.size(size)+ran,12f,Ui.muted))
        // What the folder is and how its run ended, as small coloured labels.
        val chips=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(3),0,0) }
        fun chip(text:String,color:Int) { chips.addView(Ui.chip(this,text,color),LinearLayout.LayoutParams(-2,-2).apply { if(chips.childCount>0) leftMargin=dp(6) }) }
        if(file.isDirectory&&!File(file,"hero.json").exists()) kind(file,m)?.takeUnless { k -> title(file).contains(k,true) }?.let { chip(it,Ui.accent) }
        when(m.optString("nameSource")) { "ocr" -> chip("Tên từ ảnh",Ui.muted); "unknown" -> chip("Cần đặt tên",Ui.warning) }
        when(m.optString("state")) { "completed" -> chip("Hoàn tất",Ui.success); "paused" -> chip("Tạm dừng",Ui.warning); "stopped" -> chip("Đã kết thúc",Ui.muted); "running" -> chip("Đang chạy",Ui.accent) }
        if(chips.childCount>0) info.addView(chips);row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
        if(!selecting) row.addView(Ui.text(this,"⋮",26f,Ui.muted).apply { gravity=Gravity.CENTER;setOnClickListener { itemMenu(file) };contentDescription="Tùy chọn ${title(file)}" },LinearLayout.LayoutParams(dp(36),dp(48)))
        card.addView(row);body.addView(card)
        card.setOnClickListener { if(selecting) { if(!selected.add(file)) selected.remove(file);showHistory() } else if(file.isDirectory) { historyTrail.add(folder);folder=file;selected.clear();showHistory() } else preview(file) }
        card.setOnLongClickListener { selecting=true;selected.add(file);showHistory();true }
    }
    /** The task a run folder holds, for its label. */
    private fun kind(file:File,m:JSONObject):String? = when(m.optString("collectionMode")) {
        "single","all","pick" -> "Thông tin tướng"; "ranking" -> "Thống kê"; "board" -> "Chiến khu"; "chess" -> "Cờ Liên Quân"; "custom" -> "Macro"
        else -> if(file.name=="manual") "Chụp nhanh" else null }
    /** "Giao diện": the phone's light or dark mode, or always light or dark; the screen is built again in it. */
    private fun themeDialog() {
        val options=Ui.Theme.values(); val current=Ui.theme(this)
        AlertDialog.Builder(this).setTitle("Giao diện").setSingleChoiceItems(options.map { it.label }.toTypedArray(),options.indexOf(current)) { dialog,i ->
            dialog.dismiss(); if(options[i]!=current) { Ui.setTheme(this,options[i]); recreate() } }.setNegativeButton("Đóng",null).show()
    }
    private fun goBack() {
        if(selecting) { selecting=false;selected.clear() }
        else folder=if(historyTrail.isNotEmpty()) historyTrail.removeAt(historyTrail.lastIndex)
                    else folder?.parentFile?.takeUnless { it==captures }
        showHistory()
    }
    @Deprecated("Legacy navigation") override fun onBackPressed() { if(historyRoot==null) showHistory() else if(folder!=null||selecting) goBack() else super.onBackPressed() }
    private fun preview(file:File) {
        historyRoot=null;val root=shell()
        val images=(file.parentFile?.listFiles()?.filter { GamePhotoImport.isImage(it) }?.sortedBy { it.name }).orEmpty().ifEmpty { listOf(file) }
        var pos=images.indexOf(file).coerceAtLeast(0)
        val content=Ui.stack(this).apply { setPadding(dp(16),dp(6),dp(16),dp(12)) }
        val top=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        top.addView(Ui.text(this,"‹",30f,Ui.accent).apply { setPadding(dp(4),0,dp(14),0);contentDescription="Về danh sách";setOnClickListener { showHistory() } })
        val heading=Ui.text(this,"",19f,Ui.ink,true).apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END };top.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
        val counter=Ui.text(this,"",13f,Ui.muted,true);top.addView(counter);content.addView(top)
        val caption=Ui.text(this,"",11f,Ui.muted).apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE };content.addView(caption)
        val frame=FrameLayout(this)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;background=Ui.shape(Ui.media,dp(14));clipToOutline=true;contentDescription="Ảnh đang xem, vuốt ngang để đổi ảnh" }
        frame.addView(image,FrameLayout.LayoutParams(-1,-1))
        fun arrow(text:String,gravity:Int)=Ui.text(this,text,26f,Color.WHITE,true).apply { this.gravity=Gravity.CENTER;background=Ui.shape(Color.argb(110,0,0,0),dp(22))
            frame.addView(this,FrameLayout.LayoutParams(dp(44),dp(44),gravity or Gravity.CENTER_VERTICAL).apply { marginStart=dp(8);marginEnd=dp(8) }) }
        val prev=arrow("‹",Gravity.START).apply { contentDescription="Ảnh trước" }
        val next=arrow("›",Gravity.END).apply { contentDescription="Ảnh sau" }
        content.addView(frame,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(8) })
        val strip=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false };val cells=LinearLayout(this);strip.addView(cells)
        content.addView(strip,LinearLayout.LayoutParams(-1,dp(66)).apply { topMargin=dp(10) })
        val actions=LinearLayout(this)
        actions.addView(Ui.button(this,"Lưu ZIP",true) { requestExport(listOf(images[pos])) }.apply { textSize=12f },
            LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(Ui.button(this,"Sao chép") { requestCopy(listOf(images[pos])) }.apply { textSize=12f },
            LinearLayout.LayoutParams(0,dp(46),1f).apply { leftMargin=dp(7) })
        actions.addView(Ui.button(this,"Xóa ảnh",dangerous=true) { delete(listOf(images[pos])) }.apply { textSize=12f },
            LinearLayout.LayoutParams(0,dp(46),1f).apply { leftMargin=dp(7) })
        content.addView(actions,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4) });root.addView(content,LinearLayout.LayoutParams(-1,-1))
        val thumbsViews=images.mapIndexed { i,f -> ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP;clipToOutline=true;setPadding(dp(2),dp(2),dp(2),dp(2));contentDescription=title(f)
            cells.addView(this,LinearLayout.LayoutParams(dp(112),-1).apply { if(i>0) leftMargin=dp(6) }) } }
        fun show(i:Int) {
            pos=i.coerceIn(0,images.size-1);val f=images[pos]
            heading.text=title(f);counter.text="${pos+1}/${images.size}";caption.text=f.name
            image.setImageBitmap(BitmapFactory.decodeFile(f.path,BitmapFactory.Options().apply { inSampleSize=2 }));image.alpha=.3f;image.animate().alpha(1f).setDuration(140).start()
            prev.visibility=if(pos>0) View.VISIBLE else View.INVISIBLE;next.visibility=if(pos<images.size-1) View.VISIBLE else View.INVISIBLE
            thumbsViews.forEachIndexed { k,v -> v.background=Ui.shape(if(k==pos) Ui.accent else Color.TRANSPARENT,dp(10));v.alpha=if(k==pos) 1f else .65f }
            val cell=thumbsViews[pos];strip.post { strip.smoothScrollTo(cell.left-(strip.width-cell.width)/2,0) }
        }
        prev.setOnClickListener { show(pos-1) };next.setOnClickListener { show(pos+1) }
        thumbsViews.forEachIndexed { i,v -> v.setOnClickListener { show(i) } }
        val swipe=GestureDetector(this,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:MotionEvent)=true
            override fun onFling(e1:MotionEvent?,e2:MotionEvent,vx:Float,vy:Float):Boolean {
                val dx=e2.x-(e1?.x?:e2.x)
                if(kotlin.math.abs(dx)<dp(48)||kotlin.math.abs(vx)<kotlin.math.abs(vy)) return false
                show(if(dx<0) pos+1 else pos-1);return true
            }
        })
        image.setOnTouchListener { v,e -> if(e.actionMasked==MotionEvent.ACTION_UP) v.performClick();swipe.onTouchEvent(e) }
        show(pos)
        // Filmstrip thumbnails decode off the main thread; the cache makes revisits instant.
        Thread { images.forEachIndexed { i,f -> val b=thumb(f,16);runOnUiThread { if(historyRoot==null) thumbsViews[i].setImageBitmap(b) } } }.start()
    }
    private fun duration(ms:Long):String { val t=ms/1000; return if(t>=3600) "%d:%02d:%02d".format(t/3600,t/60%60,t%60) else "%d:%02d".format(t/60,t%60) }
    private fun itemMenu(file:File) {
        val events=File(file,"events.txt")
        val steps=File(file,"${GameEntry.TRACE_DIR}/steps.jsonl")
        val labels=if(file.isDirectory) listOfNotNull("Lưu thư mục thành ZIP","Sao chép vào Pictures / thư mục…","Đổi tên","Xóa thư mục",if(events.exists()) "Nhật ký lượt" else null,if(steps.exists()) "Xem lại các bước vào game" else null) else listOf("Lưu ảnh trong ZIP","Sao chép vào Pictures / thư mục…","Xóa ảnh")
        AlertDialog.Builder(this).setTitle(title(file)).setItems(labels.toTypedArray()) { _,i -> when(labels[i]) {
            "Lưu thư mục thành ZIP","Lưu ảnh trong ZIP" -> requestExport(listOf(file)); "Sao chép vào Pictures / thư mục…" -> requestCopy(listOf(file)); "Đổi tên" -> rename(file); "Nhật ký lượt" -> runLog(events)
            "Xem lại các bước vào game" -> entrySteps(steps.parentFile!!); else -> delete(listOf(file)) } }.show()
    }
    /** Starts, pauses with their reasons, failures and the end of a run, written by the collector. */
    private fun runLog(events:File) {
        val text=try { events.readText() } catch(_:Exception) { "" }
        AlertDialog.Builder(this).setTitle("Nhật ký lượt").setMessage(text.takeLast(4000).ifBlank { "Chưa có sự kiện." }).setPositiveButton("Đóng",null)
            .setNeutralButton("Sao chép") { _,_ -> (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("AOV Collector",text));Toast.makeText(this,"Đã sao chép nhật ký",Toast.LENGTH_SHORT).show() }.show()
    }
    private fun editable():Boolean {
        if(CollectorService.libraryBusy||GamePhotoSync.busy) {
            if(FileJob.running&&FileJob.dialog==null) AlertDialog.Builder(this).setMessage("${FileJob.title} ở nền. Vui lòng chờ hoàn tất.")
                .setPositiveButton("Xem tiến độ") { _,_ -> showJob() }.setNegativeButton("Đóng",null).show()
            else message("Đang xử lý file. Vui lòng chờ hoàn tất.")
            return false
        }
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
        val stamp=" · "+SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(Date(createdAt(file)));val current=title(file)
        val field=Ui.field(this,"Tên thư mục",current.removeSuffix(stamp))
        val withTime=CheckBox(this).apply { text="Thêm mốc thời gian (${stamp.removePrefix(" · ")})";textSize=14f;isChecked=current.endsWith(stamp)||!File(file,"hero.json").exists() }
        val wrapper=Ui.stack(this).apply { setPadding(dp(20),dp(8),dp(20),dp(8));addView(field);addView(withTime) }
        val dialog=AlertDialog.Builder(this).setTitle("Đổi tên").setView(wrapper).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create();dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val base=field.text.toString().trim();require(base.length in 1..80) { "Nhập tên từ 1 đến 80 ký tự" };if(!editable()) return@setOnClickListener
                val value=if(withTime.isChecked) base+stamp else base
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
    private var photoRevision=0L
    private val photoRefresh=object:Runnable {
        override fun run() {
            if(isFinishing||isDestroyed) return
            if(photoRevision!=GamePhotoSync.revision) {
                photoRevision=GamePhotoSync.revision
                if(!FileJob.running&&!selecting) showHistory()
            }
            historySearchHandler.postDelayed(this,1500)
        }
    }

    /** Import only files newly created by confirmed Cờ Liên Quân Save Photo actions in a run. */
    private fun syncGamePhotoRun(runId:String) {
        if(!editable()) return
        FileJob.start(this,"Đang lấy ảnh mới từ Pictures","Ảnh gốc Cờ Liên Quân")
        selected.clear();selecting=false;showHistory()
        progress=FileProgress()
        Thread {
            val result=try {
                val stats=GamePhotoSession.importNew(this,runId,{ FileJob.cancelled }) { n,total,name ->
                    FileJob.report(n.toLong(),total.toLong(),"Đang chép ảnh "+n+"/"+total,name)
                }
                "Trước lượt: "+stats.before+" ảnh · Sau lượt: "+stats.after+
                    " ảnh · Thêm: "+stats.added+" ảnh.\n"+
                    "Đã sao chép vào app: "+stats.copied+" ảnh theo từng nhóm."+
                    (if(stats.already>0) " "+stats.already+" ảnh đã chép trước đó, không lặp." else "")+
                    (if(stats.uncertain>0) " Không lấy "+stats.uncertain+
                        " ảnh mới không khớp thao tác Save Photo được xác nhận." else "")+
                    (if(stats.errors>0) " Lỗi đọc "+stats.errors+" ảnh." else "")+
                    (if(stats.pending>0) " Còn "+stats.pending+
                        " lần lưu chưa thấy ảnh tương ứng trong Pictures; app sẽ tự thử lại." else "")+
                    (if(stats.cancelled) " Đã hủy giữa chừng, có thể đồng bộ lại." else "")+
                    " Ảnh cá nhân trong Pictures vẫn giữ nguyên."
            } catch(e:Exception) {
                "Chưa đồng bộ được Pictures: "+(e.message?:"Lỗi đọc ảnh")+
                    ". Bạn có thể chọn lại thư mục nguồn và thử lại; ảnh gốc không bị xóa."
            }
            FileJob.finish(result)
            GamePhotoSync.request(this)
        }.start()
    }

    private fun gamePhotosDialog() {
        if(FileJob.running) { message("Đang xử lý ảnh, vui lòng chờ hoàn tất.");return }
        val run=GamePhotoSession.lastRun(this)
        val state=run?.let { GamePhotoSession.state(this,it) }
        val before=state?.optInt("before",-1)?:-1
        val saved=state?.optJSONArray("saves")?.length()?:0
        val pending=state?.optInt("pending",0)?:0
        val info="Game lưu tại /storage/emulated/0/Pictures. Hãy chọn đúng thư mục Pictures trong Bộ nhớ trong.\n\nThư mục nguồn: "+GamePhotoImport.sourceLabel(this)+
            (if(before>=0) "\nLượt gần nhất: "+run+
                "\nSố ảnh trước lượt: "+before+
                "\nSố thao tác Save Photo đã xác nhận: "+saved+
                "\nĐã có trong app: "+state.optInt("imported",0)+
                (if(state?.has("after")==true) "\nSố ảnh sau lượt: "+state.optInt("after") else "")+
                (if(pending>0) "\nĐang chờ ảnh xuất hiện / đọc lại: "+pending else "")
             else "\nChưa có lượt tải ảnh gốc nào được ghi mốc.")+
            "\n\nApp tự phân loại theo mục đang thu thập: Quân cờ, chiêu, Phe, Nghề, Trang bị và ba nhóm Chúc Phúc. "+
            "Chỉ sao chép ảnh mới khớp thao tác lưu, không quét chép ảnh cũ trong Pictures."
        val options=arrayOf("Chọn / đổi thư mục Pictures",
            "Đồng bộ các ảnh mới sau lượt thu thập","Mở ảnh gốc đã nhập trong Lịch sử")
        AlertDialog.Builder(this).setTitle("Ảnh gốc Cờ Liên Quân")
            .setMessage(info).setItems(options) { _,i ->
                when(i) {
                    0 -> {
                        try {
                            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                putExtra(DocumentsContract.EXTRA_INITIAL_URI,android.net.Uri.parse(
                                    "content://com.android.externalstorage.documents/document/primary%3APictures"))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                            },GamePhotoImport.REQUEST_FOLDER)
                        } catch(e:Exception) {
                            message("Không chọn được Pictures: "+(e.message?:"Lỗi mở bộ chọn"))
                        }
                    }
                    1 -> if(!GamePhotoImport.hasSource(this)) gamePhotosDialog()
                         else if(run==null) message("Hãy chạy macro Cờ Liên Quân, bật nhóm Tải ảnh gốc và lưu ảnh trước.")
                         else syncGamePhotoRun(run)
                    2 -> {
                        val images=File(captures,GamePhotoImport.ROOT)
                        if(images.isDirectory) {
                            historyTrail.add(folder);folder=images;selected.clear();showHistory()
                        } else message("Chưa có ảnh gốc nào được sao chép.")
                    }
                }
            }.setNegativeButton("Đóng",null).show()
    }

    private fun importMenu() { AlertDialog.Builder(this).setTitle("Nhập file").setItems(arrayOf("Ảnh và thư mục từ ZIP","Macro từ JSON","Ảnh từ Pictures vào app")) { _,i ->
        if(!editable()) return@setItems
        if(i==2) gamePhotosDialog()
        else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type=if(i==0) "application/zip" else "application/json";addCategory(Intent.CATEGORY_OPENABLE)
        },if(i==0) 12 else 10)
    }.show() }
    private fun help() { val dialog=AlertDialog.Builder(this).setMessage("1. Bật Trợ năng cho AOV Collector.\n2. Mở Bảng nổi rồi mở Liên Quân.\n3. Trên bảng nổi: Chọn macro → Lấy thông tin tướng → chọn Theo tên tướng, Các ô đầu danh sách hoặc Tướng đang mở → Chạy. Theo tên: tick các tướng đã lưu từ lượt trước hoặc gõ tên; app tới ô đã biết, đọc lại tên trên thẻ, sai thì dò danh sách. Với hai cách đầu, tick Tự mở game thì app đóng hẳn Liên Quân nếu còn chạy (Cài đặt → Buộc dừng), mở lại, đóng popup sự kiện và vào danh sách tướng. Thống kê tướng chụp hết Bảng Phân Bậc Tướng (2 bảng × 3 chế độ) từ mục Cộng đồng. Chiến khu chụp bảng xếp hạng người chơi của từng tướng (Đấu hạng → Chiến khu).\n\nTạm dừng: nghỉ và giữ vị trí. Tiếp tục: chạy tiếp đúng chỗ. Dừng: đóng lượt, giữ ảnh. Dấu − thu gọn bảng nhưng tác vụ vẫn chạy.\n\nẢnh được nhóm theo lượt và tên tướng đọc từ màn hình. Tên nhận dạng có thể sai; mở menu ⋮ để sửa. Chọn nhiều mục để lưu ZIP hoặc xóa cùng lúc.\n\nDanh sách tướng được cuộn có đo bằng ảnh chụp và dừng ở cuối danh sách. Bố cục hiện hỗ trợ màn ngang 2400×1080. Hàng chiêu được đọc khi chạy: 4 hoặc 5 biểu tượng, và tướng có nút ↻ được chụp thêm các chiêu đổi ở dạng khác. Bảng nổi tự ẩn và không nhận chạm khi macro chạm vào game.\n\nGiao diện: chọn Sáng, Tối hoặc Theo hệ thống ở nút Giao diện trên đầu màn hình.\n\nAOV Collector 0.8.0").setPositiveButton("Đã hiểu",null)
        if(CrashLog.file(this).exists()) dialog.setNeutralButton("Nhật ký lỗi") { _,_ -> showLog("Nhật ký lỗi") };dialog.show() }
    /** Choose Pictures or any folder in Android Files. Copy originals without creating a ZIP. */
    private fun requestCopy(files:List<File>) {
        if(files.isEmpty()||!editable()) return
        pendingCopy=files
        try {
            val intent=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
            startActivityForResult(intent,copyFolderRequest)
        } catch(e:Exception) {
            message("Không mở được bộ chọn thư mục: "+(e.message?:"Không hỗ trợ"))
        }
    }

    /** Copy runs/folders/selected images to the chosen Android document directory, preserving their tree. */
    private fun copySelectedTo(uri:android.net.Uri,flags:Int) {
        val selections=pendingCopy.toList()
        if(selections.isEmpty()) { message("Chưa chọn ảnh để sao chép.");return }
        if(!editable()) return
        val perms=flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if(perms and Intent.FLAG_GRANT_WRITE_URI_PERMISSION==0) {
            message("Thư mục đã chọn không cấp quyền ghi. Vui lòng chọn thư mục khác.");return
        }
        try {
            contentResolver.takePersistableUriPermission(uri,perms)
        } catch(e:Exception) {
            message("Không giữ được quyền ghi thư mục. Chọn lại thư mục Pictures hoặc thư mục bạn có quyền truy cập.");return
        }
        FileJob.start(this,"Đang sao chép ảnh","Sao chép ảnh")
        selected.clear();selecting=false;showHistory()
        progress=FileProgress()
        Thread {
            val result=try {
                val items=PictureCopyPlan.create(captures,selections)
                val stat=PictureFolderCopy.copy(this,uri,items,{ FileJob.cancelled }) { bytes,total,number,count,name ->
                    FileJob.report(bytes,total,"Đã xử lý "+number+"/"+count+" ảnh · "+Format.sizes(bytes,total),name)
                }
                "Đã sao chép "+stat.copied+" ảnh."+
                    (if(stat.skipped>0) " Bỏ qua "+stat.skipped+" ảnh cùng tên đã có (không ghi đè)." else "")+
                    " Ảnh gốc vẫn còn trong AOV Collector."
            } catch(e:ZipFiles.Cancelled) {
                "Đã hủy sao chép. Các ảnh đã chép vẫn giữ trong thư mục đích; ảnh gốc không thay đổi."
            } catch(e:Exception) {
                "Sao chép chưa hoàn tất: "+(e.message?:"Lỗi truy cập thư mục")+
                    ". Các ảnh đã chép vẫn giữ nguyên; ảnh gốc không bị xóa."
            } finally {
                try { contentResolver.releasePersistableUriPermission(uri,perms) } catch(_:Exception) {}
            }
            FileJob.finish(result)
        }.start()
    }

    private fun requestExport(files:List<File>) {
        if(files.isEmpty()||!editable()) return
        pendingExport=files
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/zip";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"aov-${System.currentTimeMillis()}.zip") },11)
    }
    @Deprecated("Legacy activity result API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK) return;val uri=data?.data?:return
        if(!editable()) return
        if(requestCode==GamePhotoImport.REQUEST_FOLDER) {
            try { GamePhotoImport.choose(this,uri);GamePhotoSync.request(this);showHistory();gamePhotosDialog() }
            catch(e:Exception) { message("Không chọn được thư mục ảnh: "+(e.message?:"Lỗi quyền truy cập")) }
            return
        }
        if(requestCode==copyFolderRequest) { copySelectedTo(uri,data.flags);return }
        if(requestCode==10) {
            try {
                val raw=contentResolver.openInputStream(uri)?.use { input -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var n=input.read(buffer);while(n!=-1) { require(out.size()+n<=1024*1024) { "Macro tối đa 1 MB" };out.write(buffer,0,n);n=input.read(buffer) };out.toString("UTF-8") }?:error("Không đọc được file")
                MacroStore.archive(this,raw)
                message("Đã thêm macro. Mở Bảng nổi → Chọn macro → Macro đã nhập để chọn.")
            } catch(e:Exception) { message(e.message?:"Không nhập được macro") };return
        }
        val exporting=requestCode==11
        // The job may outlive this screen, which loses its access to the chosen file when it closes.
        if(exporting) try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch(_:Exception) {}
        FileJob.start(this,if(exporting) "Đang lưu ZIP" else "Đang nhập ZIP",if(exporting) "Lưu ZIP" else "Nhập ZIP")
        selected.clear();selecting=false;showHistory()
        progress=FileProgress()
        Thread {
            val result=try {
                if(exporting) {
                    val entries=pendingExport.flatMap { if(it.isDirectory) it.walkTopDown().filter { f->f.isFile }.toList() else listOf(it) }.distinctBy { it.canonicalPath }
                    require(entries.isNotEmpty()) { "Không có file để lưu" }
                    val out=contentResolver.openOutputStream(uri)?:error("Không mở được nơi lưu")
                    val parts=try { ZipFiles.export(entries,captures,out,{ FileJob.cancelled }) { done,all,number,part,name ->
                            FileJob.report(done,all,"Phần $part · file $number/${entries.size} · ${Format.sizes(done,all)}",name) } }
                        catch(e:Exception) { if(FileJob.cancelled) try { DocumentsContract.deleteDocument(contentResolver,uri) } catch(_:Exception) {}; throw e }
                    "Đã lưu ZIP gồm ${entries.size} file, chia thành ${parts.size} ZIP nhỏ dưới 450 MB theo thứ tự chữ cái · ${Format.size(parts.sumOf { it.bytes })}."
                } else {
                    var total=0L;var count=0;var imageCount=0;val made=linkedSetOf<File>();val prefix="import-${System.currentTimeMillis()}-"
                    // Progress follows how much of the ZIP has been read.
                    val size=try { contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { if(it.moveToFirst()&&!it.isNull(0)) it.getLong(0) else -1L }?:-1L } catch(_:Exception) { -1L }
                    var read=0L
                    val source=object:java.io.FilterInputStream(contentResolver.openInputStream(uri)?:error("Không đọc được ZIP")) {
                        override fun read():Int = super.read().also { if(it>=0) read++ }
                        override fun read(b:ByteArray,off:Int,len:Int):Int = super.read(b,off,len).also { if(it>0) read+=it }
                    }
                    fun take(zip:java.util.zip.ZipInputStream,outer:Boolean) {
                        var entry=zip.nextEntry
                        while(entry!=null) {
                            require(++count<=20000) { "ZIP có quá nhiều file" }
                            val parts=entry.name.trimEnd('/').split('/')
                            require(parts.all { it.isNotBlank()&&it!=".."&&it!="."&&!it.contains('\\') }) { "Đường dẫn ZIP không hợp lệ" }
                            // An export from v0.4.5 holds part ZIPs, read in turn as they stream out of it.
                            if(outer&&!entry.isDirectory&&parts.size==1&&parts[0].endsWith(".zip",true))
                                java.util.zip.ZipInputStream(object:java.io.FilterInputStream(zip) { override fun close() {} }).use { take(it,false) }
                            else if(!entry.isDirectory&&parts.size in 2..8&&(listOf("png","jpg","jpeg","webp","json","jsonl").any { parts.last().endsWith("."+it,true) })) {
                                require(parts[0].matches(Regex("[a-zA-Z0-9_-]{1,200}"))) { "Tên thư mục không hợp lệ" }
                                val dir=File(captures,prefix+parts[0]).apply { mkdirs() };made.add(dir)
                                val target=File(dir,parts.drop(1).joinToString("/"));require(target.canonicalPath.startsWith(dir.canonicalPath+"/"));target.parentFile?.mkdirs()
                                target.outputStream().use { out -> val buffer=ByteArray(8192);var n=zip.read(buffer);var fileSize=0L
                                    while(n!=-1) { total+=n;fileSize+=n;require(total<5L*1024*1024*1024&&fileSize<64L*1024*1024&&filesDir.usableSpace>30L*1024*1024) { "File quá lớn hoặc không đủ dung lượng" };out.write(buffer,0,n)
                                        if(FileJob.cancelled) throw ZipFiles.Cancelled()
                                        FileJob.report(read,size,"$imageCount ảnh · "+(if(size>0) Format.sizes(read,size) else Format.size(read)),parts.joinToString("/"));n=zip.read(buffer) }
                                };if(GamePhotoImport.isImage(target)) imageCount++
                            }
                            zip.closeEntry();entry=zip.nextEntry
                        }
                    }
                    try { java.util.zip.ZipInputStream(source).use { take(it,true) } } catch(e:Exception) { made.forEach { it.deleteRecursively() };throw e }
                    "Đã nhập $imageCount ảnh."
                }
            } catch(e:Exception) { if(FileJob.cancelled) (if(exporting) "Đã hủy lưu ZIP; file ZIP dở đã được xóa." else "Đã hủy nhập ZIP; phần đã nhập được xóa.") else "Không hoàn tất: ${e.message}" }
            if(exporting) try { contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch(_:Exception) {}
            FileJob.finish(result)
        }.start()
    }
    /**
     * The Garena test account a run with "Kiểm tra UID" logs in with when the game is on another account. The
     * password is encrypted by the phone's keystore and never shown again; only the UID and account name are.
     */
    private fun accountDialog() {
        val accounts=Accounts.list(this)
        val body=Ui.stack(this).apply { setPadding(dp(22),dp(6),dp(22),0) }
        body.addView(Ui.text(this,Accounts.problem(this)?:if(accounts.isEmpty()) "Chưa có tài khoản đã lưu. Bấm Thêm tài khoản để thiết lập."
            else "Đã lưu ${accounts.size} tài khoản. Chọn tài khoản muốn dùng mỗi khi bắt đầu lượt có Kiểm tra UID.",13f,Ui.muted))
        val dialog=AlertDialog.Builder(this).setTitle("Tài khoản đã lưu")
            .setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Thêm tài khoản",null).setNegativeButton("Đóng",null).create()
        for(account in accounts) {
            body.addView(Ui.text(this,account.caption+
                if(account.id==Accounts.selectedId(this)) " · mặc định" else "",14f,Ui.ink,true).apply { setPadding(0,dp(14),0,0) })
            val actions=LinearLayout(this)
            fun action(label:String,then:()->Unit) { actions.addView(Ui.button(this,label,action=then),LinearLayout.LayoutParams(0,dp(44),1f)) }
            action("Mặc định") { try { Accounts.select(this,account.id);dialog.dismiss();showHistory();accountDialog() }
                catch(e:Exception) { Toast.makeText(this,e.message,Toast.LENGTH_SHORT).show() } }
            action("Sửa") { dialog.dismiss();accountEditor(account) }
            action("Xóa") { AlertDialog.Builder(this).setTitle("Xóa tài khoản đã lưu?")
                .setMessage(account.caption).setPositiveButton("Xóa") { _,_ ->
                    try { Accounts.remove(this,account.id);dialog.dismiss();showHistory();accountDialog() }
                    catch(e:Exception) { Toast.makeText(this,e.message,Toast.LENGTH_SHORT).show() }
                }.setNegativeButton("Giữ lại",null).show() }
            body.addView(actions)
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss();accountEditor(null) }
    }
    private fun accountEditor(account:Accounts.Summary?) {
        val body=Ui.stack(this).apply { setPadding(dp(22),dp(6),dp(22),0) }
        body.addView(Ui.text(this,"Bật Kiểm tra UID và Tự mở game trong tác vụ. Khi bấm Chạy, chọn tài khoản muốn dùng; app kiểm tra UID và đăng nhập Garena nếu cần. Mật khẩu được mã hóa trong máy và không xuất vào ZIP.",12f,Ui.muted))
        val label=Ui.field(this,"Tên gợi nhớ (tùy chọn)",account?.label?:"")
        val uid=Ui.field(this,"UID (xem trong game: hồ sơ → UID)",account?.uid?:"",true)
        val user=Ui.field(this,"Tài khoản Garena, email hoặc số điện thoại",account?.user?:"")
        val password=Ui.field(this,"Mật khẩu","").apply { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        if(account?.ready==true) password.hint="Mật khẩu (để trống để giữ mật khẩu cũ)"
        listOf(label,uid,user,password).forEach { body.addView(it) }
        if(account!=null&&!account.ready) body.addView(Ui.text(this,"Tài khoản cũ vẫn còn UID; hãy nhập lại tên đăng nhập và mật khẩu để dùng tiếp.",12f,Ui.warning))
        val dialog=AlertDialog.Builder(this).setTitle(if(account==null) "Thêm tài khoản" else "Sửa tài khoản")
            .setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { Accounts.save(this,uid.text.toString().trim(),user.text.toString(),password.text.toString(),account?.id,label.text.toString())
                dialog.dismiss();showHistory();accountDialog();Toast.makeText(this,"Đã lưu tài khoản",Toast.LENGTH_SHORT).show() }
            catch(e:Exception) { password.error=e.message?:"Không lưu được" }
        }
    }
    /** The way into the game step by step: each screenshot the run decided on, what it decided and where it tapped. */
    private fun entrySteps(dir:File) {
        val steps=try { File(dir,"steps.jsonl").readLines().filter { it.isNotBlank() }.map { JSONObject(it) } } catch(_:Exception) { emptyList() }
        if(steps.isEmpty()) { message("Chưa có bước nào được lưu.");return }
        historyRoot=null;val root=shell()
        val content=Ui.stack(this).apply { setPadding(dp(16),dp(6),dp(16),dp(12)) }
        val top=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        top.addView(Ui.text(this,"‹",30f,Ui.accent).apply { setPadding(dp(4),0,dp(14),0);contentDescription="Về danh sách";setOnClickListener { showHistory() } })
        top.addView(Ui.text(this,"Các bước vào game",19f,Ui.ink,true),LinearLayout.LayoutParams(0,-2,1f))
        val counter=Ui.text(this,"",13f,Ui.muted,true);top.addView(counter);content.addView(top)
        val caption=Ui.text(this,"",14f,Ui.ink).apply { setPadding(0,dp(4),0,0) };content.addView(caption)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;background=Ui.shape(Ui.media,dp(14));clipToOutline=true }
        content.addView(image,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(8) })
        val nav=LinearLayout(this)
        var pos=0
        fun show(i:Int) {
            pos=i.coerceIn(0,steps.size-1); val step=steps[pos]
            counter.text="${pos+1}/${steps.size}"
            val time=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date(step.optLong("time")))
            caption.text="Bước ${step.optInt("n")} · $time · ${step.optString("what")}"
            val file=step.optString("image").takeIf { it.isNotEmpty()&&it!="null" }?.let { File(dir,it) }
            val bitmap=file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.copy(Bitmap.Config.ARGB_8888,true) }
            if(bitmap!=null&&!step.isNull("x")) {
                // Where the run tapped: a red ring.
                val x=(step.optDouble("x")*bitmap.width).toFloat(); val y=(step.optDouble("y")*bitmap.height).toFloat()
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.RED;style=Paint.Style.STROKE;strokeWidth=bitmap.width/160f }
                Canvas(bitmap).apply { drawCircle(x,y,bitmap.width/40f,paint);drawCircle(x,y,bitmap.width/300f,paint.apply { style=Paint.Style.FILL }) }
            }
            if(bitmap!=null) image.setImageBitmap(bitmap) else image.setImageDrawable(null)
            if(bitmap==null) caption.text=caption.text.toString()+"\n(Không lưu ảnh ở bước này)"
        }
        nav.addView(Ui.button(this,"‹ Bước trước") { show(pos-1) },LinearLayout.LayoutParams(0,dp(46),1f))
        nav.addView(Ui.button(this,"Bước sau ›",true) { show(pos+1) },LinearLayout.LayoutParams(0,dp(46),1f).apply { leftMargin=dp(10) })
        content.addView(nav,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) });root.addView(content,LinearLayout.LayoutParams(-1,-1))
        show(0)
    }
    private class ActionIcon(val kind:String,val color:Int):Drawable() {
        override fun draw(canvas:Canvas) {
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=this@ActionIcon.color;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
            when(kind) {
                "access" -> { canvas.drawCircle(12f,4f,2f,p);canvas.drawLine(4f,9f,20f,9f,p);canvas.drawLine(12f,7f,12f,15f,p);canvas.drawLine(12f,15f,7f,22f,p);canvas.drawLine(12f,15f,17f,22f,p) }
                "account" -> { canvas.drawCircle(12f,8f,4.5f,p);val path=Path().apply { moveTo(3f,22f);quadTo(3f,14f,12f,14f);quadTo(21f,14f,21f,22f) };canvas.drawPath(path,p) }
                // Giao diện: a circle, its right half filled, light and dark side by side.
                "theme" -> { canvas.drawCircle(12f,12f,9f,p);p.style=Paint.Style.FILL;canvas.drawPath(Path().apply { arcTo(3f,3f,21f,21f,-90f,180f,true);close() },p) }
                "import" -> { canvas.drawLine(12f,2f,12f,15f,p);canvas.drawLine(7f,10f,12f,15f,p);canvas.drawLine(17f,10f,12f,15f,p);val path=Path().apply { moveTo(3f,15f);lineTo(3f,21f);lineTo(21f,21f);lineTo(21f,15f) };canvas.drawPath(path,p) }
                else -> { canvas.drawRoundRect(2f,3f,21f,20f,3f,3f,p);p.style=Paint.Style.FILL;canvas.drawRoundRect(11f,11f,23f,23f,3f,3f,p) }
            };canvas.restore()
        }
        override fun setAlpha(alpha:Int) {} override fun setColorFilter(filter:ColorFilter?) {} @Deprecated("Deprecated API") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}

