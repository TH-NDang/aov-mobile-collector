package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Screens met between opening the game and the hero list, on the 2400x1080 landscape layout, told apart from
 * screenshot pixels as [HeroGrid] does: the lobby, and the close button of the event popups shown over it.
 */
object GameScreens {
    enum class Kind { LIST, LOBBY, HERO_PAGE, POPUP, OTHER }
    /** What one screenshot shows; [close] is the popup's close button, [signature] a coarse thumbnail. */
    class Reading(val kind:Kind,val close:Pair<Double,Double>?,val signature:IntArray)

    /** The "Tướng" tab of the lobby's bottom bar (its helmet icon and label), which opens the All-heroes list. */
    const val HEROES_X=.275
    const val HEROES_Y=.922

    fun read(width:Int,height:Int,readRow:(Int,IntArray)->Unit):Reading {
        val signature=signature(width,height,readRow)
        if(width<=height) return Reading(Kind.OTHER,null,signature)   // the game turns the screen when it starts
        if(isList(width,height,readRow)) return Reading(Kind.LIST,null,signature)
        if(isLobby(width,height,readRow)) return Reading(Kind.LOBBY,null,signature)
        if(HeroGrid.hasBackArrow(width,height,readRow)) return Reading(Kind.HERO_PAGE,null,signature)
        val close=closeButton(width,height,readRow)
        return Reading(if(close!=null) Kind.POPUP else Kind.OTHER,close,signature)
    }

    /** The grid of [HeroGrid] with cards in it: a black logo screen has plain gaps between its columns too. */
    fun isList(width:Int,height:Int,readRow:(Int,IntArray)->Unit)=
        HeroGrid.isGrid(width,height,readRow)&&HeroGrid.columns.count { HeroGrid.hasCard(width,height,it,.55,readRow) }>=3

    // The white icons of the bottom bar's tabs Túi đồ, Tướng, Trang bị and N.vụ, between ICON_TOP and ICON_BOTTOM.
    private val tabIcons=arrayOf(doubleArrayOf(.122,.151),doubleArrayOf(.251,.277),doubleArrayOf(.372,.392),doubleArrayOf(.506,.526))
    private const val ICON_TOP=.895
    private const val ICON_BOTTOM=.95
    /**
     * Whether the lobby is in front with nothing over it. Its bottom bar shows white tab icons, 28–64% of each
     * icon's box on the user's screenshots; a popup dims them to nothing, and the loading screen and the list
     * reach 6% at most there. One icon may be covered, by a badge for example.
     */
    fun isLobby(width:Int,height:Int,readRow:(Int,IntArray)->Unit):Boolean {
        val y0=(ICON_TOP*height).toInt(); val y1=(ICON_BOTTOM*height).toInt(); val row=IntArray(width)
        val white=IntArray(tabIcons.size)
        for(y in y0 until y1) { readRow(y,row)
            for(i in tabIcons.indices) for(x in (tabIcons[i][0]*width).toInt() until (tabIcons[i][1]*width).toInt()) if(isWhite(row[x])) white[i]++ }
        return tabIcons.indices.count { i -> white[i]>=.15*(y1-y0)*((tabIcons[i][1]-tabIcons[i][0])*width).toInt() }>=3
    }

    // The close button is looked for in the right part of the upper screen: at (.913, .054) on most popups,
    // further in on some, but always on the right.
    private const val SEARCH_LEFT=.55
    private const val SEARCH_RIGHT=.97
    private const val SEARCH_BOTTOM=.45
    // Half the width of an X, as fractions of the screen height: 19 px of 1080 on the popups seen so far.
    private val halfSizes=doubleArrayOf(.011,.014,.017,.021,.025,.030,.036)
    /**
     * Centre of a popup's close button, or null. The button is an X: two thin strokes crossing at right angles,
     * brighter than what is around them, with nothing touching it, so the letter X of a word does not count.
     * Brightness itself does not count, so the button is found while the popup is still fading in. With
     * several, the one nearest the top right corner.
     */
    fun closeButton(width:Int,height:Int,readRow:(Int,IntArray)->Unit):Pair<Double,Double>? {
        val x0=(SEARCH_LEFT*width).toInt(); val w=(SEARCH_RIGHT*width).toInt()-x0; val h=(SEARCH_BOTTOM*height).toInt()
        val luma=IntArray(w*h); val row=IntArray(width)
        for(y in 0 until h) { readRow(y,row); for(x in 0 until w) luma[y*w+x]=luma(row[x0+x]) }
        val found=ArrayList<DoubleArray>()   // lift, x, y, half size
        for(r0 in halfSizes) {
            val r=r0*height; val reach=(1.5*r).toInt()+3
            var cy=reach; while(cy<h-reach) {
                var cx=reach; while(cx<w-reach) {
                    if(luma[cy*w+cx]>=40) xScore(luma,w,cx.toDouble(),cy.toDouble(),r)?.let { found.add(doubleArrayOf(it,cx.toDouble(),cy.toDouble(),r)) }
                    cx+=2
                }
                cy+=2
            }
        }
        if(found.isEmpty()) return null
        // One button gives a cluster of fits; keep the best fit of each cluster.
        found.sortByDescending { it[0] }
        val buttons=ArrayList<DoubleArray>()
        for(f in found) if(buttons.none { hypot(it[1]-f[1],it[2]-f[2])<maxOf(it[3],f[3]) }) buttons.add(f)
        val best=buttons.minByOrNull { hypot(w-it[1],it[2]) }!!
        return (x0+best[1])/width to best[2]/height
    }
    /** How much brighter the strokes of an X centred at ([cx], [cy]) with half size [r] are than around them, or null if no X is there. */
    private fun xScore(luma:IntArray,w:Int,cx:Double,cy:Double,r:Double):Double? {
        fun at(x:Double,y:Double)=luma[y.roundToInt()*w+x.roundToInt()]
        // A stroke may sit a pixel off the ideal diagonal.
        fun peak(x:Double,y:Double):Int { val xi=x.roundToInt(); val yi=y.roundToInt(); var m=0
            for(dy in -1..1) for(dx in -1..1) m=maxOf(m,luma[(yi+dy)*w+xi+dx]); return m }
        // Quick test: the arms bright and the gaps between them dark at a few points.
        val q=.6*r; val g=.7*r
        val arms=minOf(minOf(peak(cx+q,cy+q),peak(cx-q,cy-q)),minOf(peak(cx+q,cy-q),peak(cx-q,cy+q)))
        val gaps=maxOf(maxOf(at(cx,cy-g),at(cx,cy+g)),maxOf(at(cx-g,cy),at(cx+g,cy)))
        if(arms-gaps<30) return null
        val arm1=IntArray(13) { val k=(-.85+it*1.7/12)*r; peak(cx+k,cy+k) }
        val arm2=IntArray(13) { val k=(-.85+it*1.7/12)*r; peak(cx+k,cy-k) }
        val gap=IntArray(16) { val j=(.5+(it%4)*.4/3)*r; when(it/4) { 0 -> at(cx,cy-j); 1 -> at(cx,cy+j); 2 -> at(cx-j,cy); else -> at(cx+j,cy) } }
        // Two square rings around the X, beyond the ends of its arms: 2 rings x 4 sides x 6 points.
        val ring=IntArray(48) { val R=(if(it<24) 1.2 else 1.5)*r; val t=(-1+(it%6)/2.5)*R
            when(it%24/6) { 0 -> at(cx+t,cy-R); 1 -> at(cx+t,cy+R); 2 -> at(cx-R,cy+t); else -> at(cx+R,cy+t) } }
        val bg=percentile(gap+ring,.75)
        val lift=percentile(arm1+arm2,.5)-bg
        if(lift<30) return null
        val half=bg+.5*lift
        if(arm1.count { it>=half }<.85*arm1.size||arm2.count { it>=half }<.85*arm2.size) return null
        val lit=bg+.35*lift
        if(percentile(gap,.9)>lit) return null
        // The arms end at the size tried: the rings' corners, which lie on the diagonals beyond the arms, are dark.
        if(ring.indices.any { (it%6==0||it%6==5)&&ring[it]>lit }) return null
        if(ring.count { it>lit }>.25*ring.size||(0 until 8).any { side -> (0 until 6).count { ring[side*6+it]>lit }>=3 }) return null
        return if(alone(luma,w,cx.roundToInt(),cy.roundToInt(),r,lit)) lift else null
    }
    /**
     * Whether the bright shape through ([cx], [cy]) is about 2r square and stands alone. The X is followed
     * pixel by pixel from its centre, so a fit a few pixels off its true centre or size still measures the X
     * itself; bright pixels within half its size around it, beyond a few of a nearby icon, mean it is a
     * letter in a word or part of the art.
     */
    private fun alone(luma:IntArray,w:Int,cx:Int,cy:Int,r:Double,lit:Double):Boolean {
        val h=luma.size/w; val reach=(2.2*r).toInt()
        val left=maxOf(0,cx-reach); val right=minOf(w-1,cx+reach); val top=maxOf(0,cy-reach); val bottom=minOf(h-1,cy+reach)
        val ww=right-left+1; val inX=BooleanArray(ww*(bottom-top+1))
        var start=-1; var brightest=lit
        for(y in cy-2..cy+2) for(x in cx-2..cx+2) if(luma[y*w+x]>brightest) { brightest=luma[y*w+x].toDouble(); start=y*w+x }
        if(start<0) return false
        val queue=ArrayDeque<Int>(); queue.add(start); inX[(start/w-top)*ww+start%w-left]=true
        var count=0; var x0=cx; var x1=cx; var y0=cy; var y1=cy
        while(queue.isNotEmpty()) {
            val p=queue.removeFirst(); val px=p%w; val py=p/w; count++
            x0=minOf(x0,px); x1=maxOf(x1,px); y0=minOf(y0,py); y1=maxOf(y1,py)
            // Running into the edge of the window: the shape is larger than an X of this size.
            if(px==left||px==right||py==top||py==bottom) return false
            for(dy in -1..1) for(dx in -1..1) { val nx=px+dx; val ny=py+dy; val i=(ny-top)*ww+nx-left
                if(!inX[i]&&luma[ny*w+nx]>lit) { inX[i]=true; queue.add(ny*w+nx) } }
        }
        val bw=x1-x0+1; val bh=y1-y0+1
        if(bw<1.4*r||bw>2.6*r||bh<1.4*r||bh>2.6*r) return false
        val margin=(.45*maxOf(bw,bh)).toInt(); var near=0
        for(y in maxOf(top,y0-margin)..minOf(bottom,y1+margin)) for(x in maxOf(left,x0-margin)..minOf(right,x1+margin))
            if(!inX[(y-top)*ww+x-left]&&luma[y*w+x]>lit) near++
        return near<=.2*count
    }
    private fun percentile(values:IntArray,p:Double):Double { val s=values.sorted(); return s[((s.size-1)*p).roundToInt()].toDouble() }

    /** A 24x12 thumbnail of mean brightness, to tell a screen that changes from one that stands still. */
    fun signature(width:Int,height:Int,readRow:(Int,IntArray)->Unit):IntArray {
        val sum=LongArray(24*12); val count=IntArray(24*12); val row=IntArray(width)
        var y=0; while(y<height) { readRow(y,row)
            var x=0; while(x<width) { val c=minOf(11,y*12/height)*24+minOf(23,x*24/width); sum[c]+=luma(row[x]).toLong(); count[c]++; x+=8 }
            y+=8 }
        return IntArray(sum.size) { if(count[it]==0) 0 else (sum[it]/count[it]).toInt() }
    }
    /** Mean difference between two [signature]s, 0–255. */
    fun change(a:IntArray,b:IntArray)=if(a.size!=b.size) 255.0 else a.indices.sumOf { abs(a[it]-b[it]) }.toDouble()/a.size

    private fun luma(p:Int)=(299*((p shr 16) and 255)+587*((p shr 8) and 255)+114*(p and 255))/1000
    private fun isWhite(p:Int):Boolean { val r=(p shr 16) and 255; val g=(p shr 8) and 255; val b=p and 255; val lo=minOf(r,g,b)
        return lo>190&&maxOf(r,g,b)-lo<50 }
}

/**
 * Opens Liên Quân and goes from wherever it is to the All-heroes list: waits through the logos and loading,
 * closes event popups by their X, and taps the lobby's "Tướng" tab. Each move is decided on a screenshot.
 * It never sends Android's back key: in the game that does not close popups, and twice in the lobby quits.
 * Whatever it cannot get past pauses the run, with that screen kept in the run. All screen access goes
 * through [Screen], so unit tests run this exact logic on a scripted series of screens.
 */
class GameEntry(private val screen:Screen,private val finish:(Outcome)->Unit) {
    sealed class Outcome { object Ready:Outcome(); class Failed(val reason:String):Outcome() }
    /** One screenshot; call [done] exactly once, with a name to keep it in the run or null to drop it. */
    interface Shot { val reading:GameScreens.Reading; fun done(keep:String?) }
    interface Screen {
        val alive:Boolean
        fun inFront():Boolean
        /** Starts the game or brings it to the front; false when it is not installed or Android refused. */
        fun launch():Boolean
        /** null when no screenshot could be taken, as while the game is still coming up. */
        fun look(then:(Shot?)->Unit)
        fun tap(x:Double,y:Double,then:(Boolean)->Unit)
        /** Taps the game's own back arrow at the top left. */
        fun back(then:(Boolean)->Unit)
        fun later(ms:Long,then:()->Unit)
        fun now():Long
        /** Records a step in the run's events log. */
        fun note(text:String)
        /** Shows what is happening on the panel. */
        fun say(text:String)
    }
    companion object {
        /** Logos, loading and popups together; the game loads within 1–2 minutes on the user's phone. */
        const val LIMIT_MS=300_000L
        /**
         * The game not in front this long after opening it: some phones block apps from opening others in the
         * background without saying so.
         */
        const val NO_GAME_MS=45_000L
        /** A screen that stays the same this long without being recognised is not going to change by itself. */
        const val STILL_MS=90_000L
        /** Popups appear a moment after the lobby: the lobby must stay clear this long before "Tướng" is tapped. */
        const val LOBBY_SETTLE_MS=2000L
        const val MAX_CLOSES=12
        const val MAX_SAME_CLOSE=3
        const val MAX_TABS=3
        const val MAX_BACKS=3
    }
    private var started=0L
    private var closes=0
    private var sameClose=0
    private var tapped:Pair<Double,Double>?=null
    private var tappedLook:IntArray?=null
    private var seen:Pair<Double,Double>?=null
    private var tabs=0
    private var backs=0
    private var lobbySince=-1L
    private var missingSince=-1L
    private var still:IntArray?=null
    private var stillSince=0L

    fun start() {
        started=screen.now()
        if(screen.inFront()) return step()
        screen.note("Mở Liên Quân"); screen.say("Đang mở Liên Quân…")
        if(!screen.launch()) return fail("Không mở được Liên Quân. Kiểm tra game đã cài, mở game bằng tay rồi bấm Tiếp tục.")
        screen.later(4000) { step() }
    }
    private fun fail(reason:String) { finish(Outcome.Failed(reason)) }
    private fun again(ms:Long) { screen.later(ms) { step() } }
    private fun step() {
        if(!screen.alive) return
        val now=screen.now()
        screen.look { s ->
            if(!screen.alive) { s?.done(null); return@look }
            if(s==null) {
                if(missingSince<0) missingSince=now
                if(now-missingSince>NO_GAME_MS) return@look fail("Liên Quân chưa hiện lên sau ${NO_GAME_MS/1000} giây. Nếu máy chặn mở app từ nền, mở game bằng tay rồi bấm Tiếp tục.")
                screen.say("Đang chờ Liên Quân hiện lên…"); return@look again(1500)
            }
            missingSince=-1
            if(now-started>LIMIT_MS) { s.done("entry-unknown"); return@look fail("Sau ${LIMIT_MS/60000} phút vẫn chưa vào được danh sách tướng. Xử lý bằng tay rồi bấm Tiếp tục; ảnh màn hình đã được lưu trong lượt.") }
            val reading=s.reading
            if(reading.kind!=GameScreens.Kind.LOBBY) lobbySince=-1
            if(reading.kind!=GameScreens.Kind.POPUP) { seen=null; tapped=null; sameClose=0 }
            if(reading.kind!=GameScreens.Kind.OTHER) still=null
            when(reading.kind) {
                GameScreens.Kind.LIST -> { s.done(null); screen.note("Đã vào danh sách tướng"); finish(Outcome.Ready) }
                GameScreens.Kind.LOBBY -> lobby(s,now)
                GameScreens.Kind.POPUP -> popup(s,reading.close!!)
                GameScreens.Kind.HERO_PAGE -> {
                    if(++backs>MAX_BACKS) { s.done("entry-page"); return@look fail("Đã bấm nút quay lại của game $MAX_BACKS lần mà chưa về được sảnh hay danh sách tướng. Mở danh sách Tất cả tướng rồi bấm Tiếp tục.") }
                    s.done(null); screen.note("Đang ở một trang khác của game; bấm nút quay lại của game (lần $backs)"); screen.say("Đang quay lại sảnh…")
                    screen.back { ok -> if(ok) again(1500) else fail("Không chạm được nút quay lại của game") }
                }
                GameScreens.Kind.OTHER -> other(s,now)
            }
        }
    }
    private fun lobby(s:Shot,now:Long) {
        if(lobbySince<0) { lobbySince=now; s.done(null); screen.say("Đã vào sảnh; chờ xem còn popup nào không…"); return again(LOBBY_SETTLE_MS) }
        if(now-lobbySince<LOBBY_SETTLE_MS) { s.done(null); return again(LOBBY_SETTLE_MS-(now-lobbySince)) }
        if(++tabs>MAX_TABS) { s.done("entry-lobby"); return fail("Đã bấm Tướng ở sảnh $MAX_TABS lần mà chưa vào được danh sách tướng. Mở danh sách Tất cả tướng rồi bấm Tiếp tục.") }
        s.done(null); lobbySince=-1
        screen.note("Bấm Tướng ở sảnh"); screen.say("Đang vào danh sách tướng…")
        screen.tap(GameScreens.HEROES_X,GameScreens.HEROES_Y) { ok -> if(ok) again(2500) else fail("Không chạm được nút Tướng ở sảnh") }
    }
    private fun near(a:Pair<Double,Double>?,b:Pair<Double,Double>)=a!=null&&abs(a.first-b.first)<.012&&abs(a.second-b.second)<.025
    private fun popup(s:Shot,close:Pair<Double,Double>) {
        // A popup fades in: tap its X only once it is found at the same place twice in a row.
        if(!near(seen,close)) { seen=close; s.done(null); return again(700) }
        seen=null
        // Popups one after another often put their X at the same place; the same popup still there looks the same.
        val look=s.reading.signature; val before=tappedLook
        sameClose=if(near(tapped,close)&&before!=null&&GameScreens.change(before,look)<3.0) sameClose+1 else 1
        if(sameClose>MAX_SAME_CLOSE) { s.done("entry-popup"); return fail("Đã bấm nút X của một popup $MAX_SAME_CLOSE lần mà popup không đóng. Đóng popup bằng tay rồi bấm Tiếp tục.") }
        if(++closes>MAX_CLOSES) { s.done("entry-popup"); return fail("Đã đóng $MAX_CLOSES popup mà vẫn còn popup. Đóng bằng tay rồi bấm Tiếp tục.") }
        tapped=close; tappedLook=look; s.done(null)
        screen.note("Đóng popup $closes: bấm X ở %.0f%%, %.0f%% màn hình".format(close.first*100,close.second*100)); screen.say("Đang đóng popup $closes…")
        screen.tap(close.first,close.second) { ok -> if(ok) again(1500) else fail("Không chạm được nút X của popup") }
    }
    private fun other(s:Shot,now:Long) {
        val signature=s.reading.signature
        val last=still
        if(last==null||GameScreens.change(last,signature)>=3.0) { still=signature; stillSince=now }
        if(now-stillSince>STILL_MS) { s.done("entry-unknown"); return fail("Màn hình đứng yên ${STILL_MS/1000} giây mà app không nhận ra (không phải màn tải, popup có nút X, sảnh hay danh sách tướng). Xử lý bằng tay rồi bấm Tiếp tục; ảnh màn hình đã được lưu trong lượt.") }
        s.done(null); screen.say("Đang chờ game tải…"); again(1500)
    }
}
