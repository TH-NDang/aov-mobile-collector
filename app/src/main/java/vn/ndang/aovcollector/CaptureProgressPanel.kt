package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/** Separate right-side overlay. Disk reads and thumbnail decoding never run on the gesture thread. */
class CaptureProgressPanel(private val c:Context,private val swallowed:()->Unit,private val export:(List<CaptureHistory.Entry>)->Unit,private val current:()->Pair<String,String>) {
    private val wm=c.getSystemService(WindowManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val preview=CaptureImagePreview(c,worker,swallowed)
    internal val previewView:View? get()=preview.view
    private var layout:LinearLayout?=null
    internal val view:View? get()=layout
    private var params:WindowManager.LayoutParams?=null
    private var status:TextView?=null
    private var summary:TextView?=null
    private var rows:LinearLayout?=null
    private var scroll:ScrollView?=null
    @Volatile private var signature=""
    private var epoch=0
    private var loading=false
    private var hidden=false
    private var shownRun:String?=null
    private var selecting=false
    private val selected=linkedMapOf<Long,CaptureHistory.Entry>()
    private var displayed=emptyList<CaptureHistory.Entry>()
    private var displayedThumbs=emptyList<Bitmap?>()
    private var selectButton:Button?=null
    private var allButton:Button?=null
    private var exportButton:Button?=null
    private fun dp(n:Int)=Ui.dp(c,n)
    fun toggle() { if(layout==null) show() else close() }
    fun rotate() { if(layout!=null) {
        val picks=selected.toMap();val mode=selecting;val run=shownRun
        close();selected.putAll(picks);selecting=mode;shownRun=run;params=null;show()
    } }
    fun shield(on:Boolean) {
        hidden=on
        preview.shield(on)
        val v=layout?:return;val p=params?:return
        p.flags=if(on) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        v.visibility=if(on) View.INVISIBLE else View.VISIBLE
        if(v.isAttachedToWindow) wm.updateViewLayout(v,p)
    }
    private fun show() {
        val bounds=wm.currentWindowMetrics.bounds
        val width=minOf(dp(290),bounds.width())
        val height=minOf(dp(440),(bounds.height()-dp(30)).coerceAtLeast(dp(100)))
        val p=params?:WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.LEFT;x=(bounds.width()-width).coerceAtLeast(0);y=dp(30)
        }
        p.width=width;p.height=height;p.x=p.x.coerceIn(0,maxOf(0,bounds.width()-width));p.y=p.y.coerceIn(0,maxOf(0,bounds.height()-height));params=p
        val box=object:LinearLayout(c) {
            override fun dispatchTouchEvent(e:MotionEvent):Boolean {
                if(!hidden) return super.dispatchTouchEvent(e)
                if(e.actionMasked==MotionEvent.ACTION_DOWN) swallowed()
                return true
            }
        }.apply { orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8))
            background=Ui.shape(Color.argb(242,20,24,42),dp(16),Color.argb(90,200,195,255))
            contentDescription="Bảng tiến trình ảnh" }
        val head=LinearLayout(c)
        val title=Ui.text(c,"TIẾN TRÌNH ẢNH · KÉO",11f,Color.WHITE,true)
        head.addView(title,LinearLayout.LayoutParams(0,dp(36),1f))
        head.addView(Ui.button(c,"×") { close() }.apply { contentDescription="Đóng tiến trình ảnh" },LinearLayout.LayoutParams(dp(40),dp(36)))
        var x=0f;var y=0f;var sx=0;var sy=0
        title.setOnTouchListener { _,e -> when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x=e.rawX;y=e.rawY;sx=p.x;sy=p.y;true }
            MotionEvent.ACTION_MOVE -> { p.x=(sx+(e.rawX-x).toInt()).coerceIn(0,maxOf(0,bounds.width()-width));p.y=(sy+(e.rawY-y).toInt()).coerceIn(0,maxOf(0,bounds.height()-height));if(box.isAttachedToWindow) wm.updateViewLayout(box,p);true }
            MotionEvent.ACTION_UP -> { title.performClick();true };else -> false
        } }
        box.addView(head)
        status=Ui.text(c,"",11f,Color.rgb(215,210,255)).apply { maxLines=5 };box.addView(status)
        summary=Ui.text(c,"Đang đọc lịch sử…",10f,Color.LTGRAY);box.addView(summary)
        val tools=LinearLayout(c)
        fun tool(text:String,action:()->Unit)=Ui.button(c,text,action=action).apply {
            textSize=11f;setTextColor(Color.WHITE);background=Ui.shape(Color.rgb(47,52,74),dp(9))
            tools.addView(this,LinearLayout.LayoutParams(0,dp(36),1f).apply { rightMargin=dp(4) })
        }
        selectButton=tool("Chọn") { selecting=!selecting;if(!selecting) selected.clear();render(displayed,displayedThumbs) }
        allButton=tool("Tất cả") {
            val available=displayed.filter { it.state=="saved" }
            if(available.all { it.order in selected }) available.forEach { selected.remove(it.order) }
            else available.forEach { selected[it.order]=it }
            render(displayed,displayedThumbs)
        }
        exportButton=tool("Xuất ZIP") { if(selected.isNotEmpty()) export(selected.values.toList()) }
        selectButton?.contentDescription="Chọn ảnh trong lượt"
        allButton?.contentDescription="Chọn tất cả ảnh đang hiện"
        exportButton?.contentDescription="Xuất ảnh đã chọn"
        box.addView(tools);refreshSelection()
        rows=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL }
        scroll=ScrollView(c).apply { addView(rows) };box.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        wm.addView(box,p);layout=box;shield(hidden);signature="";epoch++;refresh()
    }
    private val tick=Runnable { refresh() }
    private fun refresh() {
        if(layout==null||loading) return
        loading=true;val token=epoch;val (run,line)=current();status?.text=line
        if(shownRun!=run) { shownRun=run;selected.clear();preview.close();signature="";rows?.removeAllViews();displayed=emptyList();displayedThumbs=emptyList();refreshSelection() }
        worker.execute {
            var images=emptyList<Bitmap>()
            try {
                val entries=if(run.isBlank()) emptyList() else CaptureHistory.entries(c).filter { it.run==run }.takeLast(30)
                val selectedRun=run
                val after=if(selectedRun.isBlank()) null else AfterRun.state(c,selectedRun)
                val native=if(selectedRun.isBlank()) null else GamePhotoSession.state(c,selectedRun)
                val details=buildString {
                    append(line)
                    if(native!=null) {
                        val pending=((native.optJSONArray("saves")?.length()?:0)-native.optInt("imported")).coerceAtLeast(0)
                        append("\nẢnh gốc: ${native.optInt("imported")} đã chép · $pending đang chờ Pictures")
                    }
                    if(after!=null) append("\n"+after.optString("message"))
                    if(FileJob.running) append("\n"+FileJob.line)
                }
                val key=run+"|"+entries.joinToString { "${it.order}:${it.state}:${it.bytes}" }
                val changed=key!=signature
                val thumbs=if(changed) entries.map { e ->
                    if(e.state!="saved") null else thumbnail(File(c.filesDir,"captures/"+e.path))
                } else emptyList()
                images=thumbs.filterNotNull()
                main.post {
                    loading=false
                    if(token!=epoch||layout==null||current().first!=run) { images.forEach { it.recycle() };if(layout!=null) refresh() }
                    else {
                        status?.text=details
                        summary?.text=if(entries.isEmpty()) "Chưa có ảnh trong lượt hiện tại."
                            else "${entries.size} ảnh của lượt này · cũ → mới"
                        // Window removal/rotation may still draw the previous view for another frame.
                        // Let Android release displayed bitmaps after its drawables stop referencing them.
                        if(changed) {
                            signature=key
                            entries.filter { it.state!="saved" }.forEach { selected.remove(it.order) }
                            render(entries,thumbs)
                        }
                        main.postDelayed(tick,750)
                    }
                }
            } catch(_:Exception) {
                images.forEach { it.recycle() }
                main.post { loading=false;if(layout!=null) { summary?.text="Chưa đọc được lịch sử; đang thử lại…";main.postDelayed(tick,1500) } }
            }
        }
    }
    private fun thumbnail(f:File):Bitmap?=try {
        val size=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(f.path,size)
        val options=BitmapFactory.Options().apply { inSampleSize=1;while(maxOf(size.outWidth,size.outHeight)/inSampleSize>160) inSampleSize*=2 }
        BitmapFactory.decodeFile(f.path,options)
    } catch(_:Exception) { null }
    private fun render(entries:List<CaptureHistory.Entry>,thumbs:List<Bitmap?>) {
        displayed=entries;displayedThumbs=thumbs;refreshSelection()
        val body=rows?:return;val s=scroll?:return
        val follow=s.getChildAt(0).height-s.height-s.scrollY<=dp(32)
        body.removeAllViews()
        val time=SimpleDateFormat("HH:mm:ss",Locale.getDefault())
        entries.forEachIndexed { i,e ->
            val row=LinearLayout(c).apply { setPadding(0,dp(6),0,dp(6));gravity=Gravity.CENTER_VERTICAL }
            fun pick() { if(e.order in selected) selected.remove(e.order) else if(e.state=="saved") selected[e.order]=e;render(displayed,displayedThumbs) }
            row.setOnClickListener { if(selecting) pick() else if(e.state=="saved") preview.show(displayed,e.order) }
            row.setOnLongClickListener { selecting=true;pick();true }
            if(selecting) row.addView(CheckBox(c).apply {
                contentDescription="Chọn ${e.name}";isEnabled=e.state=="saved";isChecked=e.order in selected
                buttonTintList=android.content.res.ColorStateList.valueOf(Color.rgb(175,157,255))
                setOnCheckedChangeListener { _,_->pick() }
            },LinearLayout.LayoutParams(dp(30),dp(48)))
            row.contentDescription="${e.name} · ${CaptureHistory.status(e.state)}"
            row.addView(ImageView(c).apply { setImageBitmap(thumbs[i]);scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription=if(e.state=="saved") "Mở ảnh ${e.name}" else "Ảnh ${e.name} chưa xem được"
                if(e.state=="saved") setOnClickListener { if(selecting) pick() else preview.show(displayed,e.order) }
            },LinearLayout.LayoutParams(dp(62),dp(62)))
            row.addView(Ui.text(c,"${e.name} · ${time.format(Date(e.time))}\n${CaptureHistory.description(e.path)}\n${CaptureHistory.status(e.state)}"+
                (if(e.bytes>0&&e.state=="saved") " · ${Format.size(e.bytes)}" else ""),11f,
                if(e.state in setOf("failed","missing","interrupted")) Color.rgb(255,170,170) else Color.WHITE).apply {
                    setPadding(dp(6),0,0,0)
                },LinearLayout.LayoutParams(0,-2,1f))
            body.addView(row)
        }
        if(follow) s.post { if(scroll===s) s.fullScroll(View.FOCUS_DOWN) }
    }
    private fun refreshSelection() {
        selectButton?.text=if(selecting) "Xong" else "Chọn"
        allButton?.visibility=if(selecting) View.VISIBLE else View.GONE
        exportButton?.visibility=if(selecting) View.VISIBLE else View.GONE
        exportButton?.text="Xuất (${selected.size})"
        exportButton?.isEnabled=selected.isNotEmpty()
    }
    fun resetSession() {
        shownRun=null;selected.clear();selecting=false;preview.close();signature=""
        rows?.removeAllViews();displayed=emptyList();displayedThumbs=emptyList();refreshSelection()
        summary?.text="Chưa có ảnh trong lượt hiện tại."
    }
    fun close() {
        preview.close()
        epoch++;main.removeCallbacks(tick);selected.clear();selecting=false;shownRun=null;displayed=emptyList();displayedThumbs=emptyList()
        layout?.let { if(it.isAttachedToWindow) wm.removeView(it) };layout=null;rows=null;scroll=null;status=null;summary=null;selectButton=null;allButton=null;exportButton=null
    }
    fun destroy() { close();worker.shutdown() }
}
