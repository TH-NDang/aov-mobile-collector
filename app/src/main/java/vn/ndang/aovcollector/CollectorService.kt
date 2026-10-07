package vn.ndang.aovcollector

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
    companion object { var current: CollectorService? = null }
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
    private val prefs by lazy { getSharedPreferences("collector", MODE_PRIVATE) }
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onServiceConnected() { current = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (running && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString()
            if (p != null && p != packageName && p != "com.garena.game.kgvn") pause("Đã rời game — tạm dừng")
        }
    }
    override fun onInterrupt() { pause("Dịch vụ bị ngắt") }
    override fun onDestroy() { running=false; generation++; handler.removeCallbacksAndMessages(null); hidePanel(); current = null; io.shutdown(); super.onDestroy() }
    fun showPanel() {
        if(panel != null) return
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,8,8,8); setBackgroundColor(Color.rgb(24,29,46)) }
        label = TextView(this).apply { text="AOV Collector • kéo để di chuyển"; setTextColor(Color.WHITE); textSize=12f }
        layout.addView(label)
        val params = WindowManager.LayoutParams((240*resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, android.graphics.PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=8; y=80 }
        var downX=0f; var downY=0f; var startX=0; var startY=0
        label!!.setOnTouchListener { _, e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; startX=params.x; startY=params.y; true }
                MotionEvent.ACTION_MOVE -> { params.x=startX+(e.rawX-downX).toInt(); params.y=startY+(e.rawY-downY).toInt(); wm.updateViewLayout(layout,params); true }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
        var row: LinearLayout? = null
        var buttons=0
        fun button(text:String,action:()->Unit) {
            if(buttons%3==0) { row=LinearLayout(this); layout.addView(row) }
            row!!.addView(Button(this).apply { this.text=text; textSize=10f; setPadding(0,0,0,0); setOnClickListener { action() } },LinearLayout.LayoutParams(0,(52*resources.displayMetrics.density).toInt(),1f)); buttons++
        }
        button("▶ Chạy") { start() }
        button("Ⅱ Dừng tạm") { pause("Tạm dừng") }
        button("📷 Chụp") { manualCapture() }
        button("⏭ Bỏ bước") { if(!running && !busy && !manualBusy && macro!=null && index<(macro?.steps?.length()?:0)) { index++; save(); status("Đã bỏ 1 bước") } else status("Tạm dừng và chờ bước hiện tại xong") }
        button("■ Dừng") { stop() }
        button("× Ẩn bảng") { pause("Tạm dừng"); hidePanel() }
        panel=layout; wm.addView(layout,params)
    }
    private fun hidePanel() { panel?.let { wm.removeView(it) }; panel=null; label=null }
    private fun status(s:String) { label?.text=s; Toast.makeText(this,s,Toast.LENGTH_SHORT).show() }
    private fun ready(m:Macro):Boolean {
        if(rootInActiveWindow?.packageName?.toString()!=m.target) { pause("Hãy mở Liên Quân trước"); return false }
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        if(landscape!=(m.orientation=="landscape")) { pause("Sai chiều màn hình: ${m.orientation}"); return false }
        return true
    }
    private fun readMacro() = Macro(File(filesDir,"macro.json").readText())
    fun start() {
        if(running || busy || manualBusy) return
        try {
            val m=readMacro(); if(!ready(m)) return
            macro=m
            if(prefs.getString("hash","")==m.hash) { index=prefs.getInt("step",0); runId=prefs.getString("run","")?:"" } else { index=0; runId="" }
            if(index>=m.steps.length()) { status("Đã hoàn tất. Chọn lượt mới trong app"); return }
            if(runId.isEmpty()) runId=SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(Date())
            running=true; generation++; save(); next(generation)
        } catch(e:Exception) { pause("Lỗi macro: ${e.message}") }
    }
    fun reset() { if(busy || manualBusy) { status("Chờ bước hiện tại xong"); return }; stop(); index=0; runId=""; prefs.edit().clear().commit(); status("Sẵn sàng lượt mới") }
    private fun pause(s:String) { running=false; save(); status(s) }
    private fun stop() { running=false; save(); status("Đã dừng các bước tiếp theo") }
    private fun save() {
        val m=macro ?: return
        prefs.edit().putString("hash",m.hash).putInt("step",index).putString("run",runId).commit()
        if(runId.isNotEmpty()) {
            val dir=File(filesDir,"captures/$runId"); dir.mkdirs()
            File(dir,"run.json").writeText(JSONObject().put("macro",m.name).put("hash",m.hash).put("nextStep",index).put("totalSteps",m.steps.length()).put("updatedAt",System.currentTimeMillis()).toString(2))
        }
    }
    private fun next(token:Int) {
        val m=macro ?: return
        if(!running || token!=generation) return
        if(index>=m.steps.length()) { running=false; save(); status("Hoàn tất ${m.steps.length()} bước"); return }
        if(!ready(m)) return
        label?.text="${m.name} • ${index+1}/${m.steps.length()}"
        val s=m.steps.getJSONObject(index); busy=true
        fun done(ok:Boolean) {
            if(token!=generation) return
            busy=false
            panel?.visibility=View.VISIBLE
            if(!ok) { pause("Bước ${index+1} lỗi — chưa tăng checkpoint"); return }
            index++; save()
            if(running) handler.postDelayed({ next(token) },400)
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
        if(busy || running || manualBusy) { status("Tạm dừng trước khi chụp riêng"); return }
        try { if(!ready(readMacro())) return } catch(e:Exception) { status(e.message?:"Lỗi macro"); return }
        manualBusy=true
        capture("capture-${System.currentTimeMillis()}","manual") { manualBusy=false; status(if(it) "Đã lưu ảnh" else "Chụp ảnh thất bại") }
    }
    private fun capture(name:String,folder:String,done:(Boolean)->Unit) {
        panel?.visibility=View.INVISIBLE
        handler.postDelayed({
            if(rootInActiveWindow?.packageName?.toString() != "com.garena.game.kgvn") { panel?.visibility=View.VISIBLE; done(false); return@postDelayed }
            takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback {
                override fun onSuccess(result:ScreenshotResult) {
                    val buffer=result.hardwareBuffer
                    val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
                    val bitmap=hardware?.copy(Bitmap.Config.ARGB_8888,false)
                    hardware?.recycle(); buffer.close()
                    io.execute {
                        val ok=try { if(bitmap==null) false else { val dir=File(filesDir,"captures/$folder"); dir.mkdirs(); File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } } catch(e:Exception) { false }
                        bitmap?.recycle()
                        handler.post { panel?.visibility=View.VISIBLE; done(ok) }
                    }
                }
                override fun onFailure(errorCode:Int) { panel?.visibility=View.VISIBLE; status("Screenshot error $errorCode"); done(false) }
            })
        },350)
    }
}
