package vn.ndang.aovcollector

import android.app.AlertDialog
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CollectorService : AccessibilityService() {
    companion object { var current: CollectorService? = null; @Volatile var libraryBusy=false }
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var panel: LinearLayout? = null
    private var label: TextView? = null
    private var macro: Macro? = null
    private var index = 0
    private var running = false
    private var busy = false
    private var generation = 0
    private var runId = ""
    private var manualBusy = false
    private var stopped = false
    val isWorking: Boolean get() = running || busy || manualBusy
    fun prepareEdit(): Boolean { pause("Tạm dừng để quản lý dữ liệu"); return !busy && !manualBusy }
    private val prefs by lazy { getSharedPreferences("collector", MODE_PRIVATE) }
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onServiceConnected() { current = this; try { val m=readMacro(); if(m.json.optString("collectionMode").isBlank()) MacroStore.archive(this,m.raw) } catch(_:Exception) {} }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (running && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString()
            if (p != null && p != packageName && p != "com.garena.game.kgvn") pause("Đã rời game — tạm dừng")
        }
    }
    override fun onInterrupt() { pause("Dịch vụ bị ngắt") }
    override fun onDestroy() { running=false; generation++; handler.removeCallbacksAndMessages(null); hidePanel(); current = null; io.shutdown(); super.onDestroy() }
    private var compact=false
    private var panelParams:WindowManager.LayoutParams?=null
    private var taskLabel:TextView?=null
    private var taskDetail:TextView?=null
    private var primary:Button?=null
    private var choose:Button?=null
    private var progress:ProgressBar?=null
    private var bubble:TextView?=null
    private var lastStatus="Sẵn sàng"
    private var activeDialog:AlertDialog?=null
    private val uiPrefs by lazy { getSharedPreferences("ui",MODE_PRIVATE) }
    private fun dp(n:Int)=Ui.dp(this,n)
    private var selectionCache:Macro?=null
    private var selectionStamp=0L
    private fun selected():Macro? = try {
        val file=MacroStore.current(this);val stamp=file.lastModified()+file.length()
        if(selectionCache==null||selectionStamp!=stamp) { selectionCache=readMacro();selectionStamp=stamp }
        selectionCache
    } catch(e:Exception) { null }
    fun showPanel() {
        if(panel!=null) { compact=false; rebuildPanel(); return }
        compact=false; rebuildPanel()
    }
    override fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig)
        if(panel!=null) rebuildPanel()
    }
    private fun rebuildPanel() {
        panel?.let { wm.removeView(it) }
        label=null; primary=null; choose=null; taskLabel=null; taskDetail=null; progress=null; bubble=null
        val layout=Ui.stack(this).apply { setPadding(dp(12),dp(10),dp(12),dp(10)); background=Ui.shape(Color.rgb(25,29,48),dp(20)) }
        val params=panelParams ?: WindowManager.LayoutParams().apply {
            type=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            format=android.graphics.PixelFormat.TRANSLUCENT; gravity=Gravity.TOP or Gravity.START; x=dp(8); y=dp(60)
        }
        params.width=dp(if(compact) 64 else 278); params.height=WindowManager.LayoutParams.WRAP_CONTENT
        val bounds=wm.currentWindowMetrics.bounds
        params.x=params.x.coerceIn(0,maxOf(0,bounds.width()-params.width)); params.y=params.y.coerceIn(0,maxOf(0,bounds.height()-dp(if(compact) 60 else 260)))
        panelParams=params
        fun drag(v:View,onTap:(()->Unit)?=null) {
            var x=0f;var y=0f;var sx=0;var sy=0;var moved=false
            v.setOnTouchListener { _,e ->
                when(e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { x=e.rawX;y=e.rawY;sx=params.x;sy=params.y;moved=false;true }
                    MotionEvent.ACTION_MOVE -> {
                        if(kotlin.math.abs(e.rawX-x)+kotlin.math.abs(e.rawY-y)>dp(6)) moved=true
                        params.x=(sx+(e.rawX-x).toInt()).coerceIn(0,maxOf(0,bounds.width()-params.width))
                        params.y=(sy+(e.rawY-y).toInt()).coerceIn(0,maxOf(0,bounds.height()-layout.height))
                        wm.updateViewLayout(layout,params);true
                    }
                    MotionEvent.ACTION_UP -> { if(!moved) { v.performClick();onTap?.invoke() };true }
                    else -> false
                }
            }
        }
        if(compact) {
            bubble=Ui.text(this,"AOV",12f,Color.WHITE,true).apply { gravity=Gravity.CENTER; minHeight=dp(38); contentDescription="Mở bảng điều khiển thu thập" }
            layout.addView(bubble); drag(bubble!!) { compact=false;rebuildPanel() }
        } else {
            val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
            val title=Ui.text(this,"THU THẬP",11f,Color.rgb(181,187,208),true)
            header.addView(title,LinearLayout.LayoutParams(0,dp(34),1f));drag(title)
            val collapse=Ui.button(this,"−") { compact=true;rebuildPanel() }.apply { contentDescription="Thu gọn bảng nổi";setTextColor(Color.WHITE);background=Ui.shape(Color.rgb(47,52,74),dp(10)) }
            header.addView(collapse,LinearLayout.LayoutParams(dp(38),dp(32)));layout.addView(header)
            taskLabel=Ui.text(this,"",19f,Color.WHITE,true).apply { setPadding(0,0,0,dp(4));includeFontPadding=false;maxLines=1 };layout.addView(taskLabel)
            taskDetail=Ui.text(this,"",12f,Color.rgb(181,187,208)).apply { setPadding(0,0,0,dp(5));includeFontPadding=false;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END };layout.addView(taskDetail)
            choose=Ui.button(this,"Đổi tác vụ  ›") { showTaskPicker() }.apply { textSize=12f;setTextColor(Color.rgb(210,204,255));background=Ui.shape(Color.rgb(48,42,79),dp(11)) }
            layout.addView(choose,LinearLayout.LayoutParams(-1,dp(36)))
            progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100;progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(159,139,255)) }
            layout.addView(progress,LinearLayout.LayoutParams(-1,dp(8)).apply { topMargin=dp(10) })
            label=Ui.text(this,"",12f,Color.rgb(205,211,225)).apply { setPadding(0,dp(3),0,dp(5));includeFontPadding=false;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END };layout.addView(label)
            val actions=LinearLayout(this)
            primary=Ui.button(this,"Bắt đầu",true) {
                when { running -> pause("Đã tạm dừng"); busy || manualBusy -> status("Đang hoàn tất thao tác hiện tại…"); ended() -> { reset();status("Lượt mới sẵn sàng. "+(selected()?.let { MacroStore.instruction(it) }?:"")) };else -> start() }
            }
            actions.addView(primary,LinearLayout.LayoutParams(0,dp(45),1f))
            val stopButton=Ui.button(this,"Kết thúc",dangerous=true) { stop() }
            actions.addView(stopButton,LinearLayout.LayoutParams(dp(86),dp(45)).apply { leftMargin=dp(8) });layout.addView(actions)
            val footer=LinearLayout(this)
            footer.addView(Ui.button(this,"Chụp ảnh") { manualCapture() },LinearLayout.LayoutParams(0,dp(36),1f).apply { topMargin=dp(8) })
            footer.addView(Ui.button(this,"Tùy chọn") { showOptions() },LinearLayout.LayoutParams(0,dp(36),1f).apply { topMargin=dp(8);leftMargin=dp(8) });layout.addView(footer)
        }
        panel=layout;wm.addView(layout,params)
        layout.post { if(panel===layout) { val maxY=maxOf(0,wm.currentWindowMetrics.bounds.height()-layout.height);if(params.y>maxY) { params.y=maxY;wm.updateViewLayout(layout,params) } } }
        refreshPanel()
    }
    private fun ended():Boolean {
        val m=selected()?:return false
        return prefs.getString("hash","")==m.hash && (prefs.getBoolean("stopped",false)||prefs.getInt("step",0)>=m.steps.length())
    }
    private fun refreshPanel() {
        val m=selected()
        val step=if(m!=null && prefs.getString("hash","")==m.hash) prefs.getInt("step",0) else 0
        taskLabel?.text=m?.let { MacroStore.title(it) }?:"Chọn tác vụ"
        taskDetail?.text=m?.let { MacroStore.description(it) }?:"Một tướng hoặc danh sách tướng"
        choose?.isEnabled=!isWorking;choose?.alpha=if(isWorking) .45f else 1f
        progress?.progress=if(m!=null) step*100/maxOf(1,m.steps.length()) else 0
        label?.text=lastStatus
        primary?.text=when { running -> "Tạm dừng";busy||manualBusy -> "Đang xử lý…";ended() -> "Tạo lượt mới";step>0 -> "Tiếp tục";else -> "Bắt đầu" }
        primary?.isEnabled=running || (!busy && !manualBusy && m!=null)
        bubble?.text=if(running) "AOV\n${progress?.progress ?: if(m!=null) step*100/maxOf(1,m.steps.length()) else 0}%" else "AOV"
    }
    private fun display(dialog:AlertDialog) {
        activeDialog?.dismiss();activeDialog=dialog
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener { activeDialog=null;refreshPanel() };dialog.show()
    }
    fun showTaskPicker() {
        if(libraryBusy) { status("Đang lưu dữ liệu; vui lòng chờ");return }
        if(isWorking) { status("Tạm dừng trước khi đổi tác vụ");return }
        display(AlertDialog.Builder(this).setTitle("Bạn muốn thu thập gì?")
            .setItems(arrayOf("Một tướng đang mở","Danh sách tướng","Macro đã nhập")) { _,which ->
                handler.post { when(which) { 0 -> taskForm(false);1 -> taskForm(true);else -> savedPicker() } }
            }.setNegativeButton("Đóng",null).create())
    }
    private fun taskForm(batch:Boolean) {
        val body=Ui.stack(this).apply { setPadding(dp(20),dp(6),dp(20),dp(6)) }
        body.addView(Ui.text(this,if(batch) "Mở Tất cả tướng và kéo về đầu danh sách. Nên thử 2 ô trước; dùng 11 ô để thử cả bước vuốt." else "Mở trang chi tiết tướng. App chụp tổng quan, thuộc tính và 4 chiêu.",14f))
        body.addView(Ui.text(this,if(batch) "Số ô tướng cần lấy" else "Tên tướng (không bắt buộc)",13f,Ui.muted,true))
        val field=Ui.field(this,if(batch) "1–200" else "Để trống để tự đọc tên",if(batch) uiPrefs.getInt("count",2).toString() else "",batch);body.addView(field)
        if(batch) {
            body.addView(Ui.text(this,"Tự vuốt còn thử nghiệm; trang cuối có thể lặp. Khoảng 1 phút và 25 MB mỗi ô.",12f,Ui.muted))
            val quick=LinearLayout(this)
            listOf(2,11,129).forEach { n -> quick.addView(Ui.button(this,"$n ô") { field.setText(n.toString()) },LinearLayout.LayoutParams(0,dp(40),1f).apply { marginEnd=dp(4) }) };body.addView(quick)
        } else body.addView(Ui.text(this,"Tên được đọc từ ảnh. Nếu không rõ, app dùng thư mục đánh số; có thể đổi tên trong Ảnh đã lưu.",12f,Ui.muted))
        body.addView(Ui.text(this,"Chọn tác vụ sẽ tạo lượt mới. Ảnh đã chụp vẫn giữ lại.",12f,Ui.muted))
        val formScroll=ScrollView(this).apply { addView(body) }
        val dialog=AlertDialog.Builder(this).setTitle(if(batch) "Danh sách tướng" else "Một tướng").setView(formScroll)
            .setPositiveButton("Chọn tác vụ",null).setNegativeButton("Hủy",null).create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val count=if(batch) field.text.toString().toIntOrNull()?:0 else 1
                require(count in 1..200) { "Nhập số từ 1 đến 200" }
                if(batch) uiPrefs.edit().putInt("count",count).apply()
                if(selectMacro(BuiltInMacros.create(if(batch) "Danh sách tướng" else field.text.toString(),count,batch))) dialog.dismiss()
            } catch(e:Exception) { field.error=e.message }
        }
    }
    private fun savedPicker() {
        val entries=MacroStore.saved(this).mapNotNull { f -> try { Macro(f.readText()) } catch(e:Exception) { null } }
        if(entries.isEmpty()) { status("Chưa có macro. Vào Cài đặt → Nâng cao để nhập.");return }
        display(AlertDialog.Builder(this).setTitle("Macro đã nhập").setItems(entries.map { it.name }.toTypedArray()) { _,i -> selectMacro(entries[i].raw) }.setNegativeButton("Hủy",null).create())
    }
    fun selectMacro(raw:String):Boolean {
        if(libraryBusy) { status("Đang lưu dữ liệu; vui lòng chờ");return false }
        if(isWorking) { status("Chờ thao tác hiện tại hoàn tất");return false }
        try {
            val m=Macro(raw)
            if(runId.isNotBlank()) { stopped=true;save() }
            reset();MacroStore.current(this).writeText(raw);selectionCache=null
            status(MacroStore.instruction(m));return true
        } catch(e:Exception) { status(e.message?:"Không mở được tác vụ");return false }
    }
    private fun showOptions() {
        if(isWorking) { status("Tạm dừng trước khi mở tùy chọn");return }
        display(AlertDialog.Builder(this).setTitle("Tùy chọn")
            .setItems(arrayOf("Tạo lượt mới với tác vụ này","Bỏ một thao tác…","Cách dùng các nút","Đóng bảng nổi")) { _,which -> handler.post {
                when(which) { 0 -> reset();1 -> skipDialog();2 -> display(AlertDialog.Builder(this).setTitle("Điều khiển")
                    .setMessage("Tạm dừng: nghỉ sau thao tác hiện tại; Tiếp tục sẽ chạy tiếp đúng chỗ.\n\nKết thúc: giữ ảnh và đóng lượt. Chọn Tạo lượt mới để chạy lại.\n\nDấu −: thu gọn bảng, tác vụ vẫn chạy. Chạm nút AOV để mở lại.\n\nBỏ một thao tác là tùy chọn nâng cao, không phải bỏ cả tướng.").setPositiveButton("Đã hiểu",null).create());3 -> { pause("Đã đóng bảng");hidePanel() } }
            } }.setNegativeButton("Đóng",null).create())
    }
    private fun skipDialog() {
        val m=macro
        if(isWorking||stopped||m==null||index>=m.steps.length()) { status("Chỉ dùng khi đang tạm dừng một lượt");return }
        display(AlertDialog.Builder(this).setTitle("Bỏ thao tác ${index+1}?")
            .setMessage("Chỉ bỏ một lần chạm, chờ hoặc chụp. Dùng khi bạn đã thực hiện thao tác này bằng tay.")
            .setPositiveButton("Bỏ thao tác") { _,_ -> if(!isWorking&&!stopped) { index++;save();status("Đã bỏ thao tác. Bấm Tiếp tục.") } }
            .setNegativeButton("Hủy",null).create())
    }
    private fun hidePanel() { activeDialog?.dismiss();panel?.let { wm.removeView(it) };panel=null;label=null;primary=null;choose=null;progress=null;bubble=null }
    private fun status(s:String) { lastStatus=s;refreshPanel();Toast.makeText(this,s,Toast.LENGTH_SHORT).show() }
    private fun ready(m:Macro):Boolean {
        if(rootInActiveWindow?.packageName?.toString()!=m.target) { pause("Hãy mở Liên Quân trước"); return false }
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        if(landscape!=(m.orientation=="landscape")) { pause("Xoay ngang điện thoại để tiếp tục"); return false }
        return true
    }
    private fun readMacro() = Macro(File(filesDir,"macro.json").readText())
    fun start() {
        if(libraryBusy) { status("Đang lưu dữ liệu; vui lòng chờ");return }
        if(running || busy || manualBusy || activeDialog!=null) return
        try {
            val m=readMacro(); if(!ready(m)) return
            macro=m
            stopped=prefs.getString("hash","")==m.hash && prefs.getBoolean("stopped",false)
            if(stopped) { status("Lượt đã kết thúc. Bấm Tạo lượt mới."); return }
            if(prefs.getString("hash","")==m.hash) { index=prefs.getInt("step",0); runId=prefs.getString("run","")?:"" } else { index=0; runId="" }
            if(index>=m.steps.length()) { status("Đã hoàn tất. Bấm Tạo lượt mới."); return }
            if(runId.isEmpty()) {
                val mode=m.json.optString("collectionMode","custom")
                val slug=m.json.optString("heroName","").replace(Regex("[^a-zA-Z0-9_-]"),"-").take(40)
                runId="$mode-${slug}-"+SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(Date())
            }
            running=true; generation++; save(); next(generation)
        } catch(e:Exception) { pause("Lỗi macro: ${e.message}") }
    }
    fun reset() { if(busy || manualBusy) { status("Chờ bước hiện tại xong"); return }; running=false; generation++; stopped=false; index=0; runId=""; macro=null; prefs.edit().clear().commit(); status("Sẵn sàng lượt mới") }
    private fun pause(s:String) { running=false; save(); status(s) }
    private fun stop() { running=false; stopped=true; save(); status("Đã kết thúc lượt · ảnh đã được giữ lại") }
    private fun save() {
        val m=macro ?: return
        prefs.edit().putString("hash",m.hash).putInt("step",index).putString("run",runId).putBoolean("stopped",stopped).commit()
        if(runId.isNotEmpty()) {
            val dir=File(filesDir,"captures/$runId"); dir.mkdirs()
            if(!File(dir,"macro.json").exists()) File(dir,"macro.json").writeText(m.raw)
            File(dir,"run.json").writeText(JSONObject().put("collectionMode",m.json.optString("collectionMode","custom")).put("heroName",m.json.optString("heroName","")).put("state",if(stopped) "stopped" else if(index>=m.steps.length()) "completed" else if(running) "running" else "paused").put("macro",m.name).put("hash",m.hash).put("nextStep",index).put("totalSteps",m.steps.length()).put("updatedAt",System.currentTimeMillis()).toString(2))
        }
    }
    private fun next(token:Int) {
        val m=macro ?: return
        if(!running || token!=generation) return
        if(index>=m.steps.length()) { running=false; save(); status("Đã chụp xong · ${m.photoSteps.size} ảnh"); return }
        if(!ready(m)) return
        lastStatus="Đang thu thập · ${m.photoSteps.count { it<index }}/${m.photoSteps.size} ảnh"
        refreshPanel()
        val s=m.steps.getJSONObject(index); busy=true
        fun done(ok:Boolean) {
            if(token!=generation) return
            busy=false
            panel?.visibility=View.VISIBLE
            if(!ok) { pause("Không thực hiện được thao tác. Kiểm tra màn hình rồi tiếp tục."); return }
            index++; save()
            if(running) handler.postDelayed({ next(token) },400)
            else status(if(stopped) "Đã kết thúc lượt · ảnh được giữ lại" else "Đã tạm dừng · bấm Tiếp tục khi sẵn sàng")
        }
        try {
            when(s.getString("type")) {
                "wait" -> handler.postDelayed({ done(true) },s.getLong("ms"))
                "back" -> done(performGlobalAction(GLOBAL_ACTION_BACK))
                "screenshot" -> capture("%04d-%s".format(index+1,s.getString("name")),runId) { done(it) }
                else -> {
                    panel?.visibility=View.INVISIBLE
                    val bounds=wm.currentWindowMetrics.bounds
                    val p=Path().apply { moveTo((s.getDouble("x")*(bounds.width()-1)).toFloat(),(s.getDouble("y")*(bounds.height()-1)).toFloat()) }
                    val swipe=s.getString("type")=="swipe"
                    if(swipe) p.lineTo((s.getDouble("toX")*(bounds.width()-1)).toFloat(),(s.getDouble("toY")*(bounds.height()-1)).toFloat())
                    val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,if(swipe) s.optLong("ms",500) else 70)).build()
                    if(!dispatchGesture(gesture,object:GestureResultCallback(){ override fun onCompleted(g:GestureDescription?) { done(true) }; override fun onCancelled(g:GestureDescription?) { done(false) } },handler)) done(false)
                }
            }
        } catch(e:Exception) { busy=false; pause("Lỗi: ${e.message}") }
    }
    private fun manualCapture() {
        if(libraryBusy) { status("Đang lưu dữ liệu; vui lòng chờ");return }
        if(busy || running || manualBusy) { status("Tạm dừng trước khi chụp riêng"); return }
        try { if(!ready(readMacro())) return } catch(e:Exception) { status(e.message?:"Lỗi macro"); return }
        manualBusy=true;refreshPanel()
        capture("capture-${System.currentTimeMillis()}","manual") { manualBusy=false; status(if(it) "Đã lưu ảnh" else "Chụp ảnh thất bại") }
    }
    private fun photoDirectory(folder:String,name:String,bitmap:Bitmap):File {
        val base=File(filesDir,"captures/$folder").apply { mkdirs() }
        val m=macro ?: return base
        if(folder=="manual" || m.json.optString("collectionMode") !in listOf("single","all")) return base
        val key=Regex("(hero(?:-\\d{3})?)-").find(name)?.groupValues?.get(1) ?: return base
        val mapFile=File(base,"hero-folders.json")
        val mapping=try { JSONObject(mapFile.readText()) } catch(_:Exception) { JSONObject() }
        val existing=mapping.optString(key)
        if(existing.matches(Regex("[a-z0-9_-]{1,100}")) && File(base,existing).isDirectory) return File(base,existing)
        val ocr=if(name.endsWith("-overview")) HeroNames.read(bitmap) else null
        val manual=if(m.json.optString("collectionMode")=="single") m.json.optString("heroName","").trim().takeUnless { it.isBlank()||it=="Chưa đặt tên" } else null
        val displayName=manual?:ocr
        val slug=displayName?.let { HeroNames.slug(it) } ?: "tuong-${key.removePrefix("hero-").takeUnless { it=="hero" }?:"001"}"
        var actual=slug;var suffix=2
        while(File(base,actual).exists()) { actual="$slug-${suffix++}" }
        val dir=File(base,actual).apply { mkdirs() }
        File(dir,"hero.json").writeText(JSONObject().put("name",displayName?:"Chưa rõ tên")
            .put("nameSource",if(manual!=null) "manual" else if(ocr!=null) "ocr" else "unknown")
            .put("ocrText",ocr?:"").put("captureKey",key).toString(2))
        mapping.put(key,actual);mapFile.writeText(mapping.toString(2))
        return dir
    }
    private fun capture(name:String,folder:String,done:(Boolean)->Unit) {
        panel?.visibility=View.INVISIBLE
        handler.postDelayed({
            if(filesDir.usableSpace < 30L*1024*1024) { panel?.visibility=View.VISIBLE; status("Không đủ dung lượng trống để chụp"); done(false); return@postDelayed }
            if(rootInActiveWindow?.packageName?.toString() != "com.garena.game.kgvn") { panel?.visibility=View.VISIBLE; done(false); return@postDelayed }
            takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback {
                override fun onSuccess(result:ScreenshotResult) {
                    val buffer=result.hardwareBuffer
                    val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
                    val bitmap=hardware?.copy(Bitmap.Config.ARGB_8888,false)
                    hardware?.recycle(); buffer.close()
                    io.execute {
                        val ok=try { if(bitmap==null) false else { val dir=photoDirectory(folder,name,bitmap); File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } } catch(e:Exception) { false }
                        bitmap?.recycle()
                        handler.post { panel?.visibility=View.VISIBLE; done(ok) }
                    }
                }
                override fun onFailure(errorCode:Int) { panel?.visibility=View.VISIBLE; status("Screenshot error $errorCode"); done(false) }
            })
        },350)
    }
}
