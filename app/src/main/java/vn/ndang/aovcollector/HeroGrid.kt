package vn.ndang.aovcollector

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The All-heroes grid on the 2400x1080 landscape layout. Positions are fractions of the screen and the
 * list offset is how far the content has scrolled up, in screen heights. Scrolling is never assumed:
 * the game keeps scrolling after a swipe and stops short at the end of the list, so every move is
 * measured by comparing screenshots taken before and after it.
 */
object HeroGrid {
    val columns=doubleArrayOf(.337,.459,.580,.702,.823)
    const val ROW0=.31          // first row's card centre with the list at the top
    const val PITCH=.4167       // row spacing (450 px of 1080)
    const val TOP=.20           // card centres between TOP and BOTTOM can be tapped
    const val BOTTOM=.86
    const val PARK=.70          // a scroll leaves the wanted row here, with the row before it still visible
    const val MAX_STEP=.45      // larger moves leave too little overlap between screenshots to measure
    private const val Y0=.12
    private const val Y1=.99
    private const val BAND=.035
    private const val FEATURES=15
    private const val MIN_OVERLAP=160

    /** Centre of card [index] (reading order from the top of the list) on screen. */
    fun centre(index:Int,offset:Double)=columns[index%5] to ROW0+(index/5)*PITCH-offset

    /** Next action for card [index]: tap it where it is, or move the content up by [Move.d] (negative: down). */
    sealed class Plan { data class Tap(val x:Double,val y:Double):Plan(); data class Move(val d:Double):Plan() }
    fun plan(index:Int,offset:Double):Plan { val (x,y)=centre(index,offset)
        return when { y>BOTTOM -> Plan.Move(minOf(y-PARK,MAX_STEP)); y<TOP -> Plan.Move(maxOf(y-PARK,-MAX_STEP)); else -> Plan.Tap(x,y) } }
    /** After the list stops moving, a card this low is still on screen and tappable. */
    const val LAST_ROW_LIMIT=.95

    /** For each screen row of the grid, the mean colour of every column's band: rows x 15 values. */
    fun profile(width:Int,height:Int,readRow:(Int,IntArray)->Unit):FloatArray {
        val y0=(Y0*height).toInt(); val y1=(Y1*height).toInt(); val out=FloatArray((y1-y0)*FEATURES); val row=IntArray(width)
        for(y in y0 until y1) {
            readRow(y,row)
            for(c in columns.indices) {
                val from=((columns[c]-BAND)*width).toInt(); val to=((columns[c]+BAND)*width).toInt()
                var r=0f;var g=0f;var b=0f;var n=0
                var x=from; while(x<to) { val p=row[x]; r+=(p shr 16) and 255;g+=(p shr 8) and 255;b+=p and 255;n++;x+=4 }
                val o=(y-y0)*FEATURES+c*3; out[o]=r/n;out[o+1]=g/n;out[o+2]=b/n
            }
        }
        return out
    }
    fun profile(b:Bitmap)=profile(b.width,b.height,reader(b))

    /** How far the content moved up between [before] and [after], in pixels (negative: down). */
    data class Shift(val px:Int,val cost:Float,val median:Float) { val reliable get()=cost<20f&&cost<.45f*median }
    fun shift(before:FloatArray,after:FloatArray):Shift {
        val n=minOf(before.size,after.size)/FEATURES; val limit=n-MIN_OVERLAP
        if(limit<0) return Shift(0,Float.MAX_VALUE,Float.MAX_VALUE)
        val costs=FloatArray(2*limit+1); var best=0; var bestCost=Float.MAX_VALUE
        for(s in -limit..limit) {
            // After moving up by s, screen row y shows what row y+s showed before.
            val from=maxOf(0,-s); val to=minOf(n,n-s); var sum=0.0
            for(y in from until to) { val a=(y+s)*FEATURES; val b=y*FEATURES; for(k in 0 until FEATURES) sum+=abs(before[a+k]-after[b+k]) }
            val cost=(sum/((to-from)*FEATURES)).toFloat(); costs[s+limit]=cost
            if(cost<bestCost) { bestCost=cost;best=s }
        }
        costs.sort()
        return Shift(best,bestCost,costs[costs.size/2])
    }

    /** Mean brightness change between neighbouring pixels in a box: card art is busy, the list background is smooth. */
    private fun detail(width:Int,height:Int,cx:Double,hw:Double,cy:Double,hh:Double,readRow:(Int,IntArray)->Unit):Double {
        val x0=((cx-hw)*width).toInt().coerceIn(0,width-2); val x1=((cx+hw)*width).toInt().coerceIn(x0+2,width)
        val y0=((cy-hh)*height).toInt().coerceIn(0,height-2); val y1=((cy+hh)*height).toInt().coerceIn(y0+2,height)
        val row=IntArray(width); var prev:FloatArray?=null; var sum=0.0; var n=0
        for(yy in y0 until y1) {
            readRow(yy,row)
            val lum=FloatArray(x1-x0) { i -> val p=row[x0+i]; .299f*((p shr 16) and 255)+.587f*((p shr 8) and 255)+.114f*(p and 255) }
            for(i in 1 until lum.size) { sum+=abs(lum[i]-lum[i-1]);n++ }
            prev?.let { for(i in lum.indices) { sum+=abs(lum[i]-it[i]);n++ } }
            prev=lum
        }
        return if(n==0) 0.0 else sum/n
    }
    private fun reader(b:Bitmap):(Int,IntArray)->Unit = { y,row -> b.getPixels(row,0,b.width,0,y,b.width,1) }

    /** Card art stays detailed when greyed out; the empty background after the last hero does not. */
    fun hasCard(width:Int,height:Int,x:Double,y:Double,readRow:(Int,IntArray)->Unit)=detail(width,height,x,.032,y,.10,readRow)>.8
    fun hasCard(b:Bitmap,x:Double,y:Double)=hasCard(b.width,b.height,x,y,reader(b))

    private val gaps=doubleArrayOf(.398,.5195,.641,.7625)
    /** Only on the hero list are the gaps between card columns plain background from top to bottom. */
    fun isGrid(width:Int,height:Int,readRow:(Int,IntArray)->Unit)=gaps.all { detail(width,height,it,.004,.55,.40,readRow)<.6 }
    fun isGrid(b:Bitmap)=isGrid(b.width,b.height,reader(b))

    /** The game's back arrow; the macro taps it to leave a hero page. */
    const val BACK_X=.09
    const val BACK_Y=.05
    // The arrow's point and the ends of its two strokes, measured on the list, hero pages and skill panels.
    private const val ARROW_TIP_X=.0758
    private const val ARROW_TIP_Y=.0546
    private const val ARROW_END_X=.1054
    private val arrowEnds=doubleArrayOf(.0296,.0806)
    /**
     * Whether the white back arrow "<" is at the top left. The list, hero pages and skill panels have it;
     * the lobby does not, so a missing arrow means tapping there would do something else.
     */
    fun hasBackArrow(width:Int,height:Int,readRow:(Int,IntArray)->Unit):Boolean {
        val tx=ARROW_TIP_X*width; val ty=ARROW_TIP_Y*height; val ex=ARROW_END_X*width; val ends=arrowEnds.map { it*height }
        val pad=.012*height
        val x0=(tx-pad).toInt().coerceIn(0,width-1); val x1=(ex+pad).toInt().coerceIn(x0+1,width)
        val y0=(ends[0]-2*pad).toInt().coerceIn(0,height-1); val y1=(ends[1]+2*pad).toInt().coerceIn(y0+1,height)
        val w=x1-x0; val white=BooleanArray(w*(y1-y0)); val row=IntArray(width)
        for(y in y0 until y1) { readRow(y,row); for(x in x0 until x1) white[(y-y0)*w+x-x0]=isWhite(row[x]) }
        fun whiteAt(x:Int,y:Int)=x in x0 until x1&&y in y0 until y1&&white[(y-y0)*w+x-x0]
        // Nearly every white pixel lies on the two strokes ...
        var count=0; var near=0
        for(i in white.indices) if(white[i]) {
            count++
            val px=(x0+i%w).toDouble(); val py=(y0+i/w).toDouble()
            if(ends.minOf { segment(px,py,tx,ty,ex,it) }<.0075*height) near++
        }
        // ... and the strokes are white along their whole length.
        val r=maxOf(1,(.002*height).toInt()); var hits=0; var samples=0
        for(ey in ends) for(k in 0..20) {
            val sx=(tx+(ex-tx)*k/20).toInt(); val sy=(ty+(ey-ty)*k/20).toInt(); samples++
            if((-1..1).any { i -> (-1..1).any { j -> whiteAt(sx+i*r,sy+j*r) } }) hits++
        }
        return count>0&&near>.8*count&&hits>=.75*samples
    }
    fun hasBackArrow(b:Bitmap)=hasBackArrow(b.width,b.height,reader(b))
    private fun isWhite(p:Int):Boolean { val r=(p shr 16) and 255; val g=(p shr 8) and 255; val b=p and 255; val lo=minOf(r,g,b)
        return lo>190&&maxOf(r,g,b)-lo<50 }
    private fun segment(px:Double,py:Double,ax:Double,ay:Double,bx:Double,by:Double):Double {
        val dx=bx-ax; val dy=by-ay; val t=(((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)
        return hypot(px-ax-t*dx,py-ay-t*dy)
    }
}
