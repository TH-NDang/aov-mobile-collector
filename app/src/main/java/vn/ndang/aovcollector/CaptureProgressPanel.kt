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
class CaptureProgressPanel(private val c:Context,private val swallowed:()->Unit,private val current:()->Pair<String,String>) {
    private val wm=c.getSystemService(WindowManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var layout:LinearLayout?=null
    internal val view:View? get()=layout
    private var params:WindowManager.LayoutParams?=null
    private var status:TextView?=null
    private var summary:TextView?=null
    private var rows:LinearLayout?=null
    private var scroll:ScrollView?=null
    private var bitmaps=emptyList<Bitmap>()
    @Volatile private var signature=""
    private var epoch=0
    private var loading=false
    private var hidden=false
    private fun dp(n:Int)=Ui.dp(c,n)
    fun toggle() { if(layout==null) show() else close() }
    fun rotate() { if(layout!=null) { close();params=null;show() } }
    fun shield(on:Boolean) {
        hidden=on
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
        rows=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL }
        scroll=ScrollView(c).apply { addView(rows) };box.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        wm.addView(box,p);layout=box;shield(hidden);signature="";epoch++;refresh()
    }
    private val tick=Runnable { refresh() }
    private fun refresh() {
        if(layout==null||loading) return
        loading=true;val token=epoch;val (run,line)=current();status?.text=line
        worker.execute {
            var images=emptyList<Bitmap>()
            try {
                val entries=CaptureHistory.entries(c).takeLast(30)
                val selected=run.ifBlank { entries.lastOrNull()?.run.orEmpty() }
                val after=if(selected.isBlank()) null else AfterRun.state(c,selected)
                val native=if(selected.isBlank()) null else GamePhotoSession.state(c,selected)
                val details=buildString {
                    append(line)
                    if(native!=null) {
                        val pending=((native.optJSONArray("saves")?.length()?:0)-native.optInt("imported")).coerceAtLeast(0)
                        append("\nẢnh gốc: ${native.optInt("imported")} đã chép · $pending đang chờ Pictures")
                    }
                    if(after!=null) append("\n"+after.optString("message"))
                    if(FileJob.running) append("\n"+FileJob.line)
                }
                val key=entries.joinToString { "${it.order}:${it.state}:${it.bytes}" }
                val changed=key!=signature
                val thumbs=if(changed) entries.map { e ->
                    if(e.state!="saved") null else thumbnail(File(c.filesDir,"captures/"+e.path))
                } else emptyList()
                images=thumbs.filterNotNull()
                main.post {
                    loading=false
                    if(token!=epoch||layout==null) { images.forEach { it.recycle() };if(layout!=null) refresh() }
                    else {
                        status?.text=details
                        summary?.text=if(entries.isEmpty()) "Chưa có ảnh. Lịch sử bắt đầu từ bản này."
                            else "${entries.size} ảnh gần nhất · cũ → mới · tự cập nhật"
                        if(changed) { signature=key;render(entries,thumbs);val old=bitmaps;bitmaps=images;old.forEach { it.recycle() } }
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
        val body=rows?:return;val s=scroll?:return
        val follow=s.getChildAt(0).height-s.height-s.scrollY<=dp(32)
        body.removeAllViews()
        val time=SimpleDateFormat("HH:mm:ss",Locale.getDefault())
        entries.forEachIndexed { i,e ->
            val row=LinearLayout(c).apply { setPadding(0,dp(6),0,dp(6));gravity=Gravity.CENTER_VERTICAL }
            row.addView(ImageView(c).apply { setImageBitmap(thumbs[i]);scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription="Ảnh số ${e.order}" },LinearLayout.LayoutParams(dp(56),dp(54)))
            row.addView(Ui.text(c,"#${e.order} · ${time.format(Date(e.time))}\n${CaptureHistory.status(e.state)}"+
                (if(e.bytes>0) " · ${Format.size(e.bytes)}" else "")+"\n${e.path}",10f,
                if(e.state in setOf("failed","missing","interrupted")) Color.rgb(255,170,170) else Color.WHITE).apply {
                    setPadding(dp(6),0,0,0)
                },LinearLayout.LayoutParams(0,-2,1f))
            body.addView(row)
        }
        if(follow) s.post { if(scroll===s) s.fullScroll(View.FOCUS_DOWN) }
    }
    fun close() {
        epoch++;main.removeCallbacks(tick)
        layout?.let { if(it.isAttachedToWindow) wm.removeView(it) };layout=null;rows=null;scroll=null;status=null;summary=null
        bitmaps.forEach { it.recycle() };bitmaps=emptyList()
    }
    fun destroy() { close();worker.shutdown() }
}
