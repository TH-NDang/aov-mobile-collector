package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.Executor

/** Reads a larger image independently of the thumbnail, with a bounded decode and stale-load guard. */
class CaptureImagePreview(private val c:Context,private val worker:Executor,private val swallowed:()->Unit) {
    private val wm=c.getSystemService(WindowManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private var frame:LinearLayout?=null
    internal val view:View? get()=frame
    private var params:WindowManager.LayoutParams?=null
    private var image:ZoomImage?=null
    private var bitmap:Bitmap?=null
    private var token=0
    private var hidden=false
    private fun dp(n:Int)=Ui.dp(c,n)
    fun show(entry:CaptureHistory.Entry,caption:String) {
        close();val request=++token
        val box=object:LinearLayout(c) {
            override fun dispatchTouchEvent(e:MotionEvent):Boolean {
                if(!hidden) return super.dispatchTouchEvent(e)
                if(e.actionMasked==MotionEvent.ACTION_DOWN) swallowed()
                return true
            }
        }.apply { orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.rgb(12,15,24))
            contentDescription="Khung xem ảnh lớn" }
        val header=LinearLayout(c).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(Ui.text(c,"Ảnh #${entry.order} · $caption",13f,Color.WHITE,true).apply {
            maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        },LinearLayout.LayoutParams(0,-2,1f))
        val zoom=ZoomImage(c).apply { contentDescription="Ảnh lớn số ${entry.order}" }
        fun button(text:String,label:String,action:()->Unit) {
            header.addView(Ui.button(c,text,action=action).apply { contentDescription=label;textSize=14f
                setTextColor(Color.WHITE);background=Ui.shape(Color.rgb(47,52,74),dp(10));setPadding(dp(8),0,dp(8),0)
            },LinearLayout.LayoutParams(if(text=="Đóng") dp(64) else dp(42),dp(42)).apply { leftMargin=dp(5) })
        }
        button("−","Thu nhỏ ảnh") { zoom.changeZoom(1/1.5f) }
        button("+","Phóng to ảnh") { zoom.changeZoom(1.5f) }
        button("Đóng","Đóng ảnh lớn") { close() }
        box.addView(header)
        val message=Ui.text(c,"Đang mở ảnh…",11f,Color.LTGRAY);box.addView(message)
        box.addView(zoom,LinearLayout.LayoutParams(-1,0,1f))
        val bounds=wm.currentWindowMetrics.bounds
        val p=WindowManager.LayoutParams(bounds.width(),bounds.height(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START }
        wm.addView(box,p);frame=box;params=p;image=zoom;shield(hidden)
        worker.execute {
            val loaded=try {
                val file=File(c.filesDir,"captures/"+entry.path)
                val size=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,size)
                val options=BitmapFactory.Options().apply {
                    inSampleSize=1
                    while(maxOf(size.outWidth,size.outHeight)/inSampleSize>4096||
                        (size.outWidth.toLong()/inSampleSize)*(size.outHeight/inSampleSize)>6_000_000L) inSampleSize*=2
                }
                if(!file.isFile) null else BitmapFactory.decodeFile(file.path,options)
            } catch(_:Exception) { null }
            main.post {
                if(request!=token||frame!==box) loaded?.recycle()
                else if(loaded==null) message.text="Không mở được ảnh. File có thể đã bị xóa hoặc không còn đọc được."
                else {
                    bitmap=loaded;zoom.show(loaded)
                    message.text="${loaded.width} × ${loaded.height} · Chụm hai ngón hoặc dùng +/− để phóng to · Kéo để xem"
                }
            }
        }
    }
    fun shield(on:Boolean) {
        hidden=on;val v=frame?:return;val p=params?:return
        p.flags=if(on) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        v.visibility=if(on) View.INVISIBLE else View.VISIBLE
        if(v.isAttachedToWindow) wm.updateViewLayout(v,p)
    }
    fun close() {
        token++;frame?.let { if(it.isAttachedToWindow) wm.removeView(it) };frame=null;params=null
        image?.setImageDrawable(null);image=null;bitmap?.recycle();bitmap=null
    }

    private class ZoomImage(c:Context):android.widget.ImageView(c) {
        private var photo:Bitmap?=null
        private var factor=1f
        private var fit=1f
        private var left=0f
        private var top=0f
        private var lastX=0f
        private var lastY=0f
        private val transform=Matrix()
        private val pinch=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d:ScaleGestureDetector):Boolean { changeZoom(d.scaleFactor,d.focusX,d.focusY);return true }
        })
        private val taps=GestureDetector(c,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:MotionEvent)=true
            override fun onDoubleTap(e:MotionEvent):Boolean { changeZoom(if(factor>1.1f) 1/factor else 2.5f,e.x,e.y);return true }
            override fun onSingleTapUp(e:MotionEvent):Boolean { performClick();return true }
        })
        init { scaleType=ScaleType.MATRIX;isClickable=true }
        fun show(b:Bitmap) { photo=b;setImageBitmap(b);resetFit() }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);resetFit() }
        private fun resetFit() {
            val b=photo?:return;if(width==0||height==0) return
            fit=minOf(width.toFloat()/b.width,height.toFloat()/b.height);factor=1f;left=0f;top=0f;drawPosition()
        }
        fun changeZoom(amount:Float,x:Float=width/2f,y:Float=height/2f) {
            if(photo==null) return
            val next=(factor*amount).coerceIn(1f,6f);val ratio=next/factor
            left=x-(x-left)*ratio;top=y-(y-top)*ratio;factor=next;drawPosition()
        }
        private fun drawPosition() {
            val b=photo?:return;val w=b.width*fit*factor;val h=b.height*fit*factor
            left=if(w<=width) (width-w)/2 else left.coerceIn(width-w,0f)
            top=if(h<=height) (height-h)/2 else top.coerceIn(height-h,0f)
            transform.setScale(fit*factor,fit*factor);transform.postTranslate(left,top);imageMatrix=transform
            stateDescription="Phóng to ${(factor*100).toInt()}%"
        }
        override fun onTouchEvent(e:MotionEvent):Boolean {
            pinch.onTouchEvent(e);taps.onTouchEvent(e)
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_UP -> { lastX=e.x;lastY=e.y }
                MotionEvent.ACTION_MOVE -> {
                    if(!pinch.isInProgress&&e.pointerCount==1) { left+=e.x-lastX;top+=e.y-lastY;drawPosition() }
                    lastX=e.x;lastY=e.y
                }
            }
            return true
        }
        override fun performClick():Boolean { super.performClick();return true }
    }
}
