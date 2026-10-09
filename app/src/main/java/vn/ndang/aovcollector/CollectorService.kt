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
    companion object {
        var current: CollectorService? = null; @Volatile var libraryBusy=false
        const val TAP_MS=100L
        /** Time for the hidden panel's window change to reach input dispatch before touching the game. */
        const val SHIELD_MS=150L
        const val GESTURE_RETRIES=2
        const val GAME="com.garena.game.kgvn"
        /** Pause between steps; each tap is already followed by an explicit wait step. */
        const val STEP_GAP_MS=150L
        /** Screenshots saved in the background at once before the macro waits for the disk. */
        const val MAX_PENDING_SAVES=2
    }
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
    private var pendingSaves = 0
    private val saveWaiters = ArrayDeque<()->Unit>()
    val isWorking: Boolean get() = running || busy || manualBusy || pendingSaves>0
    fun prepareEdit(): Boolean { if(running) pause("Tạm dừng để quản lý dữ liệu"); return !busy && !manualBusy && pendingSaves==0 }
    // Collection time of the current run, pauses excluded.
    private var activeMs=0L
    private var segmentStart=0L
    private fun elapsed()=activeMs+(if(segmentStart>0) android.os.SystemClock.elapsedRealtime()-segmentStart else 0L)
    private fun setRunning(on:Boolean) { if(on==running) return; if(on) segmentStart=android.os.SystemClock.elapsedRealtime() else { activeMs=elapsed();segmentStart=0 }; running=on }
    private var failure:String?=null
    /** Hero count read from the list's "owned/total" counter; null until the first list screenshot. */
    private var listTotal:Int?=null
    private fun clock(ms:Long):String { val t=ms/1000; return if(t>=3600) "%d:%02d:%02d".format(t/3600,t/60%60,t%60) else "%d:%02d".format(t/60,t%60) }
    /** Progress line such as "Tướng 23/129 · 25:12 · còn ~1:30:00", with collection time excluding pauses. */
    private fun progress(m:Macro):String {
        val all=m.json.optString("collectionMode")=="all"; val heroes=m.json.optInt("heroCount",1)
        val perHero=if(all) ((m.steps.length()-1)/heroes).coerceAtLeast(1) else m.steps.length()
        val target=if(all) minOf(heroes,listTotal?:heroes) else 1
        val total=if(all) 1+target*perHero else m.steps.length()
        val done=index.coerceIn(0,total)
        val head=if(all) "Tướng ${minOf(target,(index-1).coerceAtLeast(0)/perHero+1)}/${if(listTotal==null&&heroes>=BuiltInMacros.ALL) "?" else target}"
            else "${m.photoSteps.count { it<index }}/${m.photoSteps.size} ảnh"
        val t=elapsed(); val eta=if(done>=20&&t>60_000) " · còn ~"+clock(t*(total-done)/done) else ""
        return "$head · ${clock(t)}$eta"
    }
    /** Package in front, or null when Android does not report one, which is common with Unity games. */
    private fun foreground():String? = try { rootInActiveWindow?.packageName?.toString() } catch(_:Exception) { null }
    private fun appName(p:String)=if(p==packageName) "AOV Collector" else p
    /** Appends to the run's events.txt, so pauses and failures can be explained after the run. */
    private fun note(text:String) {
        if(runId.isEmpty()) return
        try { File(filesDir,"captures/$runId").apply { mkdirs() }.resolve("events.txt").appendText(SimpleDateFormat("dd/MM HH:mm:ss",Locale.US).format(Date())+" · bước ${index+1} · $text\n") } catch(_:Exception) {}
    }
    private val prefs by lazy { getSharedPreferences("collector", MODE_PRIVATE) }
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onServiceConnected() {
        current = this; CrashLog.install(this)
        upgradeBuiltIn()
        try { val m=readMacro(); macro=m; if(prefs.getString("hash","")==m.hash) { index=prefs.getInt("step",0);runId=prefs.getString("run","")?:"";stopped=prefs.getBoolean("stopped",false);activeMs=prefs.getLong("activeMs",0L) }; if(m.json.optString("collectionMode").isBlank()) MacroStore.archive(this,m.raw) } catch(_:Exception) {}
        // A run that was still marked running means the previous process died mid-step.
        if(prefs.getBoolean("running",false)) {
            CrashLog.record(this,"Dịch vụ bị dừng đột ngột khi đang chạy bước ${index+1} (lượt $runId)",null); note("Dịch vụ bị dừng đột ngột (máy tắt app hoặc app lỗi)")
            prefs.edit().putBoolean("running",false).commit(); save(); lastStatus="Lượt trước bị gián đoạn ở bước ${index+1}. Mở đúng màn hình rồi bấm Tiếp tục."
        }
        if(uiPrefs.getBoolean("panel",false)) handler.post { if(current===this && panel==null) showPanel() }
    }
    private val awayCheck=Runnable { val p=foreground(); if(running&&p!=null&&p!=GAME&&p!=packageName) pause("Đã rời game (đang mở ${appName(p)}) — tạm dừng") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (running && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString()
            // Notifications, keyboards and game boosters report window changes too; only pause if another app stays in front.
            if (p != null && p != packageName && p != GAME) { handler.removeCallbacks(awayCheck); handler.postDelayed(awayCheck,1500) }
        }
    }
    override fun onInterrupt() { pause("Dịch vụ bị ngắt") }
    override fun onDestroy() { if(running) { setRunning(false); save() }; generation++; handler.removeCallbacksAndMessages(null); hidePanel(); current = null; io.shutdown(); super.onDestroy() }
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
    private var macroPicker:View?=null
    private var pickerY:Int?=null
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
        if(panel!=null) { dismissMacroPicker();rebuildPanel() }
    }
    private var shielded=false
    private var shieldSession=0
    private var swallowed=false
    /** Taps that hit the hidden panel and were resent; read by the UI smoke check. */
    internal var swallowedTaps=0
    private var unsavedRetries=0
    internal val panelView:View? get()=panel
    /** While the macro touches the game, the panel is hidden and untouchable; an invisible view still gets touches until the window update lands. */
    private fun shield(on:Boolean) {
        if(on==shielded) return
        shielded=on
        val params=panelParams
        if(params!=null) params.flags=if(on) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        val view=panel?:return
        view.visibility=if(on) View.INVISIBLE else View.VISIBLE
        if(params!=null && view.isAttachedToWindow) try { wm.updateViewLayout(view,params) } catch(e:Exception) { CrashLog.record(this,"Không cập nhật được bảng nổi",e) }
    }
    private fun detach(view:View) { try { wm.removeView(view) } catch(e:Exception) { CrashLog.record(this,"Không gỡ được bảng nổi",e) } }
    private fun rebuildPanel() {
        dismissMacroPicker()
        panel?.let { detach(it) }; panel=null
        label=null; primary=null; choose=null; taskLabel=null; taskDetail=null; progress=null; bubble=null
        val layout=object:LinearLayout(this) {
            // A tap that still reaches the hidden panel must not press its buttons; the macro retries it.
            override fun dispatchTouchEvent(e:MotionEvent):Boolean { if(!shielded) return super.dispatchTouchEvent(e); if(e.actionMasked==MotionEvent.ACTION_DOWN) swallowed=true; return true }
        }.apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)); background=Ui.shape(Color.argb(195,20,24,42),dp(20),Color.argb(70,230,230,255)) }
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
                        if(layout.isAttachedToWindow) wm.updateViewLayout(layout,params);true
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
            val title=Ui.text(this,"AOV  ·  KÉO ĐỂ DI CHUYỂN",10f,Color.rgb(220,224,237),true)
            header.addView(title,LinearLayout.LayoutParams(0,dp(34),1f));drag(title)
            val collapse=Ui.button(this,"−") { compact=true;rebuildPanel() }.apply { contentDescription="Thu gọn bảng nổi";setTextColor(Color.WHITE);background=Ui.shape(Color.rgb(47,52,74),dp(10)) }
            header.addView(collapse,LinearLayout.LayoutParams(dp(38),dp(32)));layout.addView(header)
            choose=Ui.button(this,"Chọn macro  ▾") { showTaskPicker() }.apply {
                textSize=15f;gravity=Gravity.CENTER_VERTICAL or Gravity.START;setTextColor(Color.WHITE)
                background=Ui.shape(Color.argb(165,63,53,104),dp(11),Color.argb(90,208,196,255))
                contentDescription="Chọn macro, có tìm kiếm"
            }
            layout.addView(choose,LinearLayout.LayoutParams(-1,dp(42)))
            taskDetail=Ui.text(this,"",12f,Color.rgb(226,230,240)).apply { setPadding(0,dp(5),0,0);includeFontPadding=false;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END };layout.addView(taskDetail)
            progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100;progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(159,139,255)) }
            layout.addView(progress,LinearLayout.LayoutParams(-1,dp(8)).apply { topMargin=dp(10) })
            label=Ui.text(this,"",12f,Color.rgb(205,211,225)).apply { setPadding(0,dp(3),0,dp(5));includeFontPadding=false;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END };layout.addView(label)
            val actions=LinearLayout(this)
            primary=Ui.button(this,"Chạy",true) {
                when { running -> pause("Đã tạm dừng"); busy || manualBusy -> status("Đang hoàn tất thao tác hiện tại…"); ended() -> { reset();status("Lượt mới sẵn sàng. "+(selected()?.let { MacroStore.instruction(it) }?:"")) };else -> start() }
            }
            actions.addView(primary,LinearLayout.LayoutParams(0,dp(45),1f))
            val stopButton=Ui.button(this,"Dừng",dangerous=true) { stop() }.apply { setTextColor(Color.rgb(255,213,220));background=Ui.shape(Color.argb(190,112,43,64),dp(12)) }
            actions.addView(stopButton,LinearLayout.LayoutParams(dp(86),dp(45)).apply { leftMargin=dp(8) });layout.addView(actions)
            val footer=LinearLayout(this)
            fun foot(text:String,action:()->Unit) {
                val b=Ui.button(this,text,action=action).apply { textSize=12f;setTextColor(Color.WHITE);background=Ui.shape(Color.argb(155,53,60,84),dp(10)) }
                footer.addView(b,LinearLayout.LayoutParams(0,dp(38),1f).apply { topMargin=dp(8);if(footer.childCount>0) leftMargin=dp(6) })
            }
            foot("Chụp") { manualCapture() }
            foot("Về app") { pause("Đã tạm dừng");hidePanel();startActivity(android.content.Intent(this,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
            foot("Đóng") { pause("Đã tạm dừng");hidePanel() };layout.addView(footer)

        }
        if(shielded) layout.visibility=View.INVISIBLE
        try { wm.addView(layout,params) } catch(e:Exception) { CrashLog.record(this,"Không mở được bảng nổi",e); return }
        panel=layout; uiPrefs.edit().putBoolean("panel",true).apply()
        layout.post { if(panel===layout && layout.isAttachedToWindow) { val maxY=maxOf(0,wm.currentWindowMetrics.bounds.height()-layout.height);if(params.y>maxY) { params.y=maxY;wm.updateViewLayout(layout,params) } } }
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
        choose?.text=(m?.let { MacroStore.title(it) }?:"Chọn macro")+"  ▾"
        choose?.isEnabled=!isWorking;choose?.alpha=if(isWorking) .45f else 1f
        progress?.progress=if(m!=null) step*100/maxOf(1,m.steps.length()) else 0
        label?.text=lastStatus
        primary?.text=when { running -> "Tạm dừng";busy||manualBusy -> "Đang xử lý…";ended() -> "Tạo lượt mới";step>0 -> "Tiếp tục";else -> "Chạy" }
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
        if(isWorking) { status("Tạm dừng trước khi đổi macro");return }
        if(panel==null||compact) showPanel()
        if(macroPicker!=null) { dismissMacroPicker();return }
        val host=panel?:return
        val saved=MacroStore.saved(this).mapNotNull { try { Macro(it.readText()) } catch(_:Exception) { null } }
        val choices=listOf("Một tướng đang mở","Danh sách tướng")+saved.map { it.name }
        var visible=choices.indices.toList()
        val body=Ui.stack(this).apply { setPadding(dp(10),dp(10),dp(10),dp(6));background=Ui.shape(Color.argb(248,248,248,254),dp(14),Ui.border) }
        val search=Ui.field(this,"Tìm macro…");body.addView(search,LinearLayout.LayoutParams(-1,dp(46)))
        val list=ListView(this).apply { dividerHeight=0;isVerticalScrollBarEnabled=true }
        body.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        val empty=Ui.text(this,"Không tìm thấy macro",13f,Ui.muted).apply { visibility=View.GONE };body.addView(empty)
        fun filter(query:String) {
            val q=HeroNames.slug(query).takeUnless { query.isBlank() }?:""
            visible=choices.indices.filter { q.isEmpty()||HeroNames.slug(choices[it]).contains(q) }
            list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,visible.map { choices[it] })
            empty.visibility=if(visible.isEmpty()) View.VISIBLE else View.GONE
        }
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { filter(s?.toString()?:"") }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        list.setOnItemClickListener { _,_,position,_ ->
            val i=visible.getOrNull(position)?:return@setOnItemClickListener;dismissMacroPicker()
            handler.post { when(i) { 0 -> taskForm(false);1 -> taskForm(true);else -> selectMacro(saved[i-2].raw) } }
        }
        body.addView(Ui.button(this,"Đóng danh sách") { dismissMacroPicker() },LinearLayout.LayoutParams(-1,dp(38)))
        filter("")
        // Expand inside the existing accessibility window; do not create a popup token.
        for(i in 2 until host.childCount) host.getChildAt(i).visibility=View.GONE
        macroPicker=body
        val height=minOf(dp(250),wm.currentWindowMetrics.bounds.height()-dp(150)).coerceAtLeast(dp(120))
        host.addView(body,2,LinearLayout.LayoutParams(-1,height))
        panelParams?.let { params ->
            params.flags=params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            pickerY=params.y
            params.y=params.y.coerceAtMost(maxOf(0,wm.currentWindowMetrics.bounds.height()-height-dp(110)))
            wm.updateViewLayout(host,params)
        }
        search.requestFocus()
    }
    private fun dismissMacroPicker() {
        val picker=macroPicker?:return
        macroPicker=null
        val host=panel?:return
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(host.windowToken,0)
        host.removeView(picker)
        for(i in 0 until host.childCount) host.getChildAt(i).visibility=View.VISIBLE
        panelParams?.let { params ->
            params.flags=params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            // Return to where the user placed the panel; the list only lifted it to fit.
            pickerY?.let { params.y=it }; pickerY=null
            if(host.isAttachedToWindow) wm.updateViewLayout(host,params)
        }
    }
    private fun taskForm(batch:Boolean) {
        val body=Ui.stack(this).apply { setPadding(dp(20),dp(6),dp(20),dp(6)) }
        body.addView(Ui.text(this,if(batch) "Mở danh sách Tất cả tướng. App tự đưa danh sách về đầu, đo từng lần cuộn và dừng khi hết danh sách." else "Mở trang chi tiết tướng. App chụp tổng quan, thuộc tính và 4 chiêu.",14f))
        body.addView(Ui.text(this,if(batch) "Số ô tướng cần lấy" else "Tên tướng (không bắt buộc)",13f,Ui.muted,true))
        val field=Ui.field(this,if(batch) "1–${BuiltInMacros.ALL}" else "Để trống để tự đọc tên",if(batch) uiPrefs.getInt("count",2).toString() else "",batch);body.addView(field)
        if(batch) {
            body.addView(Ui.text(this,"Khoảng 1 phút và 25 MB mỗi tướng. Chọn Tất cả để lấy đến cuối danh sách.",12f,Ui.muted))
            val quick=LinearLayout(this)
            listOf(2 to "2 ô",11 to "11 ô",BuiltInMacros.ALL to "Tất cả").forEach { (n,text) -> quick.addView(Ui.button(this,text) { field.setText(n.toString()) },LinearLayout.LayoutParams(0,dp(40),1f).apply { marginEnd=dp(4) }) };body.addView(quick)
        } else body.addView(Ui.text(this,"Tên được đọc từ ảnh. Nếu không rõ, app dùng thư mục đánh số; có thể đổi tên trong Ảnh đã lưu.",12f,Ui.muted))
        fun option(text:String,key:String)=CheckBox(this).apply { this.text=text;textSize=14f;isChecked=uiPrefs.getBoolean(key,false) }.also { body.addView(it) }
        val fast=option("Chạy nhanh: chờ ngắn hơn sau mỗi lần chạm (nên thử 2 tướng trước)","fast")
        val detailOnly=option("Chỉ chụp Chi tiết: bỏ ảnh Tóm tắt của nội tại và chiêu","detailOnly")
        body.addView(Ui.text(this,"Chọn tác vụ sẽ tạo lượt mới. Ảnh đã chụp vẫn giữ lại.",12f,Ui.muted))
        val formScroll=ScrollView(this).apply { addView(body) }
        val dialog=AlertDialog.Builder(this).setTitle(if(batch) "Danh sách tướng" else "Một tướng").setView(formScroll)
            .setPositiveButton("Chọn tác vụ",null).setNegativeButton("Hủy",null).create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val count=if(batch) field.text.toString().toIntOrNull()?:0 else 1
                require(count in 1..BuiltInMacros.ALL) { "Nhập số từ 1 đến ${BuiltInMacros.ALL}; ${BuiltInMacros.ALL} là lấy hết danh sách" }
                if(batch) uiPrefs.edit().putInt("count",count).apply()
                uiPrefs.edit().putBoolean("fast",fast.isChecked).putBoolean("detailOnly",detailOnly.isChecked).apply()
                if(selectMacro(BuiltInMacros.create(if(batch) "Danh sách tướng" else field.text.toString(),count,batch,fast.isChecked,detailOnly.isChecked))) dialog.dismiss()
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
            val previous=macro
            if(runId.isNotBlank() && previous!=null && index<previous.steps.length()) { stopped=true;save() }
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
    private fun hidePanel() { dismissMacroPicker();activeDialog?.dismiss();panel?.let { detach(it) };panel=null;label=null;primary=null;choose=null;progress=null;bubble=null;uiPrefs.edit().putBoolean("panel",false).apply() }
    private fun status(s:String) { lastStatus=s;refreshPanel();Toast.makeText(this,s,Toast.LENGTH_SHORT).show() }
    private fun ready(m:Macro):Boolean {
        val p=foreground()
        if(p!=null&&p!=m.target) { pause("Hãy mở Liên Quân trước (đang mở ${appName(p)})"); return false }
        val landscape=screen().let { it.width()>it.height() }
        if(landscape!=(m.orientation=="landscape")) { pause("Xoay ngang điện thoại để tiếp tục"); return false }
        return true
    }
    private fun readMacro() = Macro(File(filesDir,"macro.json").readText())
    fun start() {
        if(libraryBusy) { status("Đang lưu dữ liệu; vui lòng chờ");return }
        if(running || busy || manualBusy || activeDialog!=null || macroPicker!=null) return
        upgradeBuiltIn()
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
                runId="$mode-${slug}-"+SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(Date()); listRef=null; listTotal=null
            }
            if(prefs.getString("hash","")==m.hash) activeMs=prefs.getLong("activeMs",0L) else activeMs=0L
            setRunning(true); generation++; save(); note(if(index==0) "Bắt đầu lượt" else "Tiếp tục"); next(generation)
        } catch(e:Exception) { pause("Lỗi macro: ${e.message}") }
    }
    fun reset() { if(busy || manualBusy) { status("Chờ bước hiện tại xong"); return }; setRunning(false); activeMs=0L; listTotal=null; generation++; stopped=false; index=0; runId=""; macro=null; listRef=null; prefs.edit().clear().commit(); upgradeBuiltIn(); status("Sẵn sàng lượt mới") }
    /**
     * A built-in macro selected before an app update keeps its old steps in macro.json. Regenerate it with the
     * same settings unless a run of it is still unfinished, so a new run always uses the current steps.
     */
    private fun upgradeBuiltIn() {
        if(!MacroStore.current(this).exists()) return   // nothing selected yet on a fresh install
        try {
            val m=readMacro(); val mode=m.json.optString("collectionMode")
            if((mode!="single"&&mode!="all")||m.json.optInt("builtinVersion",1)==BuiltInMacros.VERSION) return
            val step=if(prefs.getString("hash","")==m.hash) prefs.getInt("step",0) else 0
            if(step>0&&step<m.steps.length()&&!prefs.getBoolean("stopped",false)) return
            MacroStore.current(this).writeText(BuiltInMacros.create(m.json.optString("heroName",""),m.json.optInt("heroCount",1),mode=="all",m.json.optBoolean("fast"),m.json.optBoolean("detailOnly")))
            selectionCache=null
        } catch(e:Exception) { CrashLog.record(this,"Không nâng cấp được macro",e) }
    }
    private fun pause(s:String) { val was=running; setRunning(false); save(); if(was) note("Tạm dừng: $s"); status(s) }
    private fun stop() { setRunning(false); stopped=true; save(); note("Kết thúc lượt"); status("Đã kết thúc lượt · ảnh đã được giữ lại") }
    private fun save() {
        val m=macro ?: return
        prefs.edit().putString("hash",m.hash).putInt("step",index).putString("run",runId).putBoolean("stopped",stopped).putBoolean("running",running).putLong("activeMs",elapsed()).commit()
        if(runId.isNotEmpty()) try {
            val dir=File(filesDir,"captures/$runId"); dir.mkdirs()
            if(!File(dir,"macro.json").exists()) File(dir,"macro.json").writeText(m.raw)
            val metadata=File(dir,"run.json")
            val record=try { JSONObject(metadata.readText()) } catch(_:Exception) { JSONObject() }
            if(!record.has("createdAt")) record.put("createdAt",System.currentTimeMillis())
            metadata.writeText(record.put("collectionMode",m.json.optString("collectionMode","custom")).put("heroName",m.json.optString("heroName","")).put("state",if(stopped) "stopped" else if(index>=m.steps.length()) "completed" else if(running) "running" else "paused").put("macro",m.name).put("hash",m.hash).put("nextStep",index).put("totalSteps",m.steps.length()).put("updatedAt",System.currentTimeMillis()).put("activeMs",elapsed())
                // Taps that landed on the hidden panel and had to be resent; non-zero confirms the v0.3.1 failure mode on this device.
                .put("panelTapRetries",record.optInt("panelTapRetries",0)+unsavedRetries).toString(2))
            unsavedRetries=0
        } catch(e:Exception) { CrashLog.record(this,"Không ghi được run.json",e) }
    }
    private fun next(token:Int,away:Int=0) {
        val m=macro ?: return
        if(!running || token!=generation) return
        if(index>=m.steps.length()) { afterSaves { if(token==generation&&running) { setRunning(false); save(); note("Hoàn tất · ${clock(elapsed())}"); status("Đã chụp xong · ${m.photoSteps.size} ảnh · ${clock(elapsed())}") } }; return }
        val front=foreground()
        if(front!=null&&front!=m.target) {
            // A notification or game booster can hold focus for a moment; only stop when another app stays in front.
            if(away<3) { handler.postDelayed({ next(token,away+1) },700); return }
            pause("Liên Quân không còn ở phía trước (đang mở ${appName(front)})"); return
        }
        if(!ready(m)) return
        lastStatus=progress(m)
        refreshPanel()
        failure=null
        val s=m.steps.getJSONObject(index); busy=true
        var finished=false
        fun done(ok:Boolean,reason:String?=null) {
            if(finished || token!=generation) return
            finished=true; busy=false
            shield(false)
            if(!ok) { pause(reason?:failure?:"Không thực hiện được thao tác ${index+1}. Kiểm tra màn hình rồi tiếp tục."); return }
            index++; save()
            if(running) handler.postDelayed({ next(token) },STEP_GAP_MS)
            else status(if(stopped) "Đã kết thúc lượt · ảnh được giữ lại" else "Đã tạm dừng · bấm Tiếp tục khi sẵn sàng")
        }
        fun listEnded(collected:Int) {
            if(finished || token!=generation) return
            finished=true; busy=false; shield(false)
            index=m.steps.length(); setRunning(false); save(); note("Hết danh sách · $collected tướng · ${clock(elapsed())}"); status("Đã hết danh sách · lấy xong $collected tướng · ${clock(elapsed())}")
        }
        try {
            val type=s.getString("type")
            // A step that never reports back must not leave the controller stuck on "Đang xử lý".
            val limit=when(type) { "wait" -> s.getLong("ms")+5000; "screenshot" -> 30000L; "swipe" -> s.optLong("ms",500)+10000; "pick" -> 300000L; else -> 10000L }
            handler.postDelayed({ if(!finished && token==generation) done(false,"Bước ${index+1} ($type) quá ${limit/1000} giây không xong. Kiểm tra màn hình rồi tiếp tục.") },limit)
            when(type) {
                "wait" -> handler.postDelayed({ done(true) },s.getLong("ms"))
                "back" -> done(performGlobalAction(GLOBAL_ACTION_BACK))
                // A failed screenshot is retried once; Android sometimes refuses one right after a tap.
                "screenshot" -> { val name="%04d-%s".format(index+1,s.getString("name"))
                    capture(name,runId) { ok -> if(ok) done(true) else handler.postDelayed({ if(!finished&&token==generation) capture(name,runId) { done(it) } },1000) } }
                "pick" -> { val card=s.getInt("index"); pick(card,"%04d".format(index+1),{ !finished && token==generation }) { outcome -> when(outcome) {
                    is ListNavigator.Outcome.Tapped -> done(true)
                    is ListNavigator.Outcome.End -> listEnded(card)
                    is ListNavigator.Outcome.Failed -> done(false,outcome.reason)
                } } }
                else -> {
                    val bounds=wm.currentWindowMetrics.bounds
                    val p=Path().apply { moveTo((s.getDouble("x")*(bounds.width()-1)).toFloat(),(s.getDouble("y")*(bounds.height()-1)).toFloat()) }
                    val swipe=type=="swipe"
                    if(swipe) p.lineTo((s.getDouble("toX")*(bounds.width()-1)).toFloat(),(s.getDouble("toY")*(bounds.height()-1)).toFloat())
                    val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,if(swipe) s.optLong("ms",500) else TAP_MS)).build()
                    touchGame(gesture,!swipe) { done(it) }
                }
            }
        } catch(e:Exception) { CrashLog.record(this,"Lỗi bước ${index+1}",e); finished=true; busy=false; shield(false); pause("Lỗi: ${e.message}") }
    }
    /**
     * Sends a gesture to the game with the panel out of the way. If the touch still lands on the
     * panel (its window update has not reached input yet) or Android cancels a tap, it is resent.
     * Gestures after the first continue its stroke, as in a drag that holds still before lifting.
     */
    internal fun touchGame(gesture:GestureDescription,tap:Boolean,result:(Boolean)->Unit)=touchGame(listOf(gesture),tap,result)
    internal fun touchGame(gestures:List<GestureDescription>,tap:Boolean,result:(Boolean)->Unit) {
        val session=++shieldSession; shield(true)
        val done={ ok:Boolean -> if(session==shieldSession) shield(false); result(ok) }
        fun send(attempt:Int) {
            if(current!==this) return
            swallowed=false
            fun part(i:Int) {
                val callback=object:GestureResultCallback() {
                    override fun onCompleted(g:GestureDescription?) {
                        if(i>0||!swallowed) { if(i+1<gestures.size) part(i+1) else done(true); return }
                        swallowedTaps++; if(busy) unsavedRetries++
                        if(attempt<GESTURE_RETRIES) handler.postDelayed({ send(attempt+1) },SHIELD_MS) else { failure="Cú chạm rơi vào bảng nổi; kéo bảng sang chỗ khác rồi tiếp tục";done(false) }
                    }
                    // A cancelled swipe may have scrolled part way, so only taps are repeated.
                    override fun onCancelled(g:GestureDescription?) { if(tap && attempt<GESTURE_RETRIES) handler.postDelayed({ send(attempt+1) },SHIELD_MS) else { failure="Android hủy thao tác chạm (có thể do chạm tay vào màn hình)";done(false) } }
                }
                val sent=try { dispatchGesture(gestures[i],callback,handler) } catch(e:Exception) { CrashLog.record(this,"Không gửi được thao tác chạm",e); false }
                if(!sent) { failure="Android không nhận thao tác chạm";done(false) }
            }
            part(0)
        }
        handler.postDelayed({ send(0) },SHIELD_MS)
    }
    private fun screen()=wm.currentWindowMetrics.bounds
    private fun tapAt(x:Double,y:Double):GestureDescription { val b=screen()
        return GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo((x*(b.width()-1)).toFloat(),(y*(b.height()-1)).toFloat()) },0,TAP_MS)).build() }
    /** A drag between two screen fractions; with [holdMs] the finger rests at the end so the list does not keep sliding. */
    private fun drag(x:Double,from:Double,to:Double,moveMs:Long,holdMs:Long):List<GestureDescription> {
        val b=screen(); val px=(x*(b.width()-1)).toFloat(); val y0=(from*(b.height()-1)).toFloat(); val y1=(to*(b.height()-1)).toFloat()
        val move=GestureDescription.StrokeDescription(Path().apply { moveTo(px,y0);lineTo(px,y1) },0,moveMs,holdMs>0)
        if(holdMs<=0) return listOf(GestureDescription.Builder().addStroke(move).build())
        val hold=move.continueStroke(Path().apply { moveTo(px,y1) },0,holdMs,false)
        return listOf(GestureDescription.Builder().addStroke(move).build(),GestureDescription.Builder().addStroke(hold).build())
    }
    private var listRef:FloatArray?=null
    /** Runs a [ListNavigator] for card [card] against the real screen; its state survives in list.json. */
    private fun pick(card:Int,stamp:String,alive:()->Boolean,finish:(ListNavigator.Outcome)->Unit) {
        val file=File(filesDir,"captures/$runId/list.json")
        val saved=try { JSONObject(file.readText()) } catch(_:Exception) { JSONObject() }
        val state=ListNavigator.State(saved.optDouble("offset",0.0),saved.optInt("page",1),saved.optBoolean("synced",false),saved.optDouble("step",1.0),listRef)
        if(listTotal==null) listTotal=saved.optInt("total",0).takeIf { it>0 }
        val screen=object:ListNavigator.Screen {
            override val alive get()=alive()
            override fun look(then:(ListNavigator.Shot?)->Unit) {
                grab { b ->
                    if(b==null) return@grab then(null)
                    try { io.execute {
                        val p=try { if(HeroGrid.isGrid(b)) HeroGrid.profile(b) else FloatArray(0) } catch(e:Throwable) { null }
                        val arrow=p?.isEmpty()==true&&try { HeroGrid.hasBackArrow(b) } catch(e:Throwable) { false }
                        val total=if(listTotal==null&&p?.isNotEmpty()==true) HeroNames.readListTotal(b) else null
                        handler.post { if(total!=null) listTotal=total; if(p==null) { b.recycle(); then(null) } else then(object:ListNavigator.Shot {
                            override val profile:FloatArray=p
                            override val height=b.height
                            override val backArrow=arrow
                            override fun hasCard(x:Double,y:Double)=HeroGrid.hasCard(b,x,y)
                            override fun done(keep:String?) { if(keep!=null) store(b,runId,"$stamp-$keep") {} else b.recycle() }
                        }) }
                    } } catch(e:Exception) { b.recycle(); then(null) }
                }
            }
            override fun drag(d:Double,fling:Boolean,then:(Boolean)->Unit) {
                val from=if(d>0) .88 else .22; val to=from-d-(if(d>0) .02 else -.02)
                touchGame(drag(.70,from,to.coerceIn(.02,.98),if(fling) 160 else 700,if(fling) 0 else 450),false,then)
            }
            override fun tap(x:Double,y:Double,then:(Boolean)->Unit) { touchGame(tapAt(x,y),true,then) }
            override fun back(then:(Boolean)->Unit) { touchGame(tapAt(HeroGrid.BACK_X,HeroGrid.BACK_Y),true,then) }
            override fun later(ms:Long,then:()->Unit) { handler.postDelayed(then,ms) }
            override fun note(text:String) { this@CollectorService.note(text) }
        }
        ListNavigator(card,state,screen) { outcome ->
            listRef=state.ref
            try { file.parentFile?.mkdirs(); file.writeText(JSONObject().put("offset",state.offset).put("page",state.page).put("synced",state.synced).put("step",state.step).put("total",listTotal?:0).toString()) }
            catch(e:Exception) { CrashLog.record(this,"Không ghi được list.json",e) }
            finish(outcome)
        }.start()
    }
    private fun manualCapture() {
        if(libraryBusy) { status("Đang lưu dữ liệu; vui lòng chờ");return }
        if(busy || running || manualBusy) { status("Tạm dừng trước khi chụp riêng"); return }
        try { if(!ready(readMacro())) return } catch(e:Exception) { status(e.message?:"Lỗi macro"); return }
        manualBusy=true;refreshPanel()
        capture("capture-${System.currentTimeMillis()}","manual",wait=true) { manualBusy=false; status(if(it) "Đã lưu ảnh" else failure?:"Chụp ảnh thất bại") }
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
    /** Takes a screenshot with the panel hidden; [done] gets a software bitmap to recycle, or null. */
    private fun grab(done:(Bitmap?)->Unit) {
        val session=++shieldSession; shield(true)
        val finish={ b:Bitmap? -> if(session==shieldSession) shield(false); done(b) }
        fun shoot(retry:Boolean) {
            val p=foreground()
            if(p!=null&&p!=GAME) { if(retry) { handler.postDelayed({ shoot(false) },1000); return }; failure="Không chụp: Liên Quân không ở phía trước (đang mở ${appName(p)})"; finish(null); return }
            val callback=object:TakeScreenshotCallback {
                override fun onSuccess(result:ScreenshotResult) {
                    val buffer=result.hardwareBuffer
                    val bitmap=try { val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace); hardware?.copy(Bitmap.Config.ARGB_8888,false).also { hardware?.recycle() } }
                        catch(e:Throwable) { CrashLog.record(this@CollectorService,"Không đọc được ảnh chụp",e); null } finally { buffer.close() }
                    if(bitmap==null) failure="Không đọc được ảnh chụp màn hình"
                    finish(bitmap)
                }
                override fun onFailure(errorCode:Int) {
                    // Android allows one accessibility screenshot per second.
                    if(errorCode==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retry) { handler.postDelayed({ shoot(false) },1100); return }
                    failure="Chụp màn hình thất bại (mã $errorCode)"; finish(null)
                }
            }
            try { takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,callback) } catch(e:Exception) { CrashLog.record(this,"Không gọi được chụp màn hình",e); finish(null) }
        }
        handler.postDelayed({ shoot(true) },250)
    }
    /**
     * Takes a screenshot and saves it in the background: [done] runs once the image is captured, so the
     * macro does not wait for PNG compression, unless [wait] is set or too many saves are already queued.
     */
    private fun capture(name:String,folder:String,wait:Boolean=false,done:(Boolean)->Unit) {
        if(filesDir.usableSpace < 30L*1024*1024) { failure="Không đủ dung lượng trống để chụp"; done(false); return }
        grab { bitmap ->
            if(bitmap==null) return@grab done(false)
            pendingSaves++
            store(bitmap,folder,name) { ok ->
                pendingSaves--
                if(!ok) note("Không lưu được ảnh $name")
                if(wait) done(ok)
                saveWaiters.removeFirstOrNull()?.invoke()
                refreshPanel()
            }
            if(!wait) { if(pendingSaves<=MAX_PENDING_SAVES) done(true) else saveWaiters.addLast { done(true) } }
        }
    }
    private fun afterSaves(then:()->Unit) { if(pendingSaves==0) then() else saveWaiters.addLast { afterSaves(then) } }
    /** Writes [bitmap] as PNG off the main thread and recycles it. */
    private fun store(bitmap:Bitmap,folder:String,name:String,done:(Boolean)->Unit) {
        try {
            io.execute {
                val ok=try { val dir=photoDirectory(folder,name,bitmap); File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } catch(e:Throwable) { CrashLog.record(this@CollectorService,"Không lưu được ảnh $name",e); false }
                bitmap.recycle()
                handler.post { done(ok) }
            }
        } catch(e:Exception) { bitmap.recycle(); done(false) }
    }
}
