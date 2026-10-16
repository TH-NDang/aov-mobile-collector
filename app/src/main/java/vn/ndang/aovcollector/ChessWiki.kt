package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cờ Liên Quân encyclopedia collector. Navigation/capture decisions are in this class, not in the
 * AccessibilityService, so the real controller and scripted tests use the same state machine.
 * This only enters the chess lobby/encyclopedia; it NEVER taps matchmaking buttons.
 */
object ChessWiki {
    data class Topic(val key:String,val section:String,val label:String,val tabX:Double,val sidebarY:Double,
                     val sweepHeader:Boolean=false) {
        val side get()=when(section) {
            "thong-tin" -> "Thông tin cơ bản"; "quan-co" -> "Quân cờ"; "tuong-tac" -> "Tương tác"
            "trang-bi" -> "Trang bị"; "nhiem-vu" -> "Nhiệm vụ"; else -> "Chúc Phúc"
        }
    }
    val TOPICS=listOf(
        Topic("quy-trinh","thong-tin","Quy trình cơ bản",.37,.20),
        Topic("thang-thua-vong","thong-tin","Thắng và thua vòng đấu",.68,.20),
        Topic("thang-thua-tran","thong-tin","Thắng và thua trận",.78,.20,true),
        Topic("tat-ca-quan-co","quan-co","Tất cả quân cờ",.30,.305),
        Topic("phe-nghe","quan-co","Phe & Nghề",.47,.305),
        Topic("phe","tuong-tac","Phe",.0,.405),
        Topic("nghe","tuong-tac","Nghề",.0,.405),
        Topic("tat-ca-trang-bi","trang-bi","Tất cả trang bị",.0,.505),
        Topic("danh-sach-nhiem-vu","nhiem-vu","Danh sách nhiệm vụ",.0,.61),
        Topic("thuong","chuc-phuc","Thường",.30,.715),
        Topic("huyen-thoai-su-thi","chuc-phuc","Huyền thoại / Sử thi",.46,.715),
        Topic("truyen-thuyet","chuc-phuc","Truyền thuyết",.65,.715)
    )
    data class Options(val selected:Set<String>,val openGame:Boolean=true,val checkAccount:Boolean=false,
                       val traceEntry:Boolean=true,val allPages:Boolean=true,
                       val heroDetails:Boolean=false,val synergyDetails:Boolean=false,
                       val equipmentDetails:Boolean=false) {
        fun topics()=TOPICS.filter { it.key in selected }
        init { require(selected.isNotEmpty() && selected.all { key -> TOPICS.any { it.key==key } }) }
    }
    fun detailType(t:Topic,o:Options)=when(t.key) {
        "tat-ca-quan-co" -> if(o.heroDetails) "tuong" else null
        "phe","nghe" -> if(o.synergyDetails) t.key else null
        "tat-ca-trang-bi" -> if(o.equipmentDetails) "vat-pham" else null
        else -> null
    }
    /** Nested path tokens are validated before use by the storage adapter. */
    fun filename(topic:Topic,kind:String,page:Int,item:String?=null):String {
        val base="chess__"+topic.section+"__"+topic.key+"__"
        return if(item==null) base+"trang__%03d".format(page)
               else base+kind+"__"+item+"__%03d".format(page)
    }
    fun safeName(text:String)=HeroNames.slug(text).take(45).ifEmpty { "chua-ro-ten" }

    data class Tile(val x:Double,val y:Double,val id:String,val label:String?=null)
    /** All coordinates are fractions of the complete landscape screenshot, including the game's side margins. */
    fun tiles(t:Topic,b:Bitmap,words:List<GameScreens.Word>):List<Tile> {
        val spots=ArrayList<Triple<Double,Double,String?>>()
        when(t.key) {
            "tat-ca-quan-co" -> {
                val cols=listOf(.292,.438,.583,.729,.875)
                val banned=setOf("phaothu","dau si","dausi","cungthu","phapsu","dod on","dod on",
                                 "satthu","trothu","langkhach","chuchien","phe","nghe")
                for(w in words) {
                    val p=w.plain
                    if(w.y !in .43.. .91 || w.x !in .25.. .94 || p.length !in 2..22 || p in banned) continue
                    val col=cols.minByOrNull { abs(it-w.x) }?:continue
                    if(abs(col-w.x)>.047) continue
                    val cy=w.y-.16
                    if(cy !in .31.. .80) continue
                    if(score(b,col,cy,.022) > 13) spots.add(Triple(col,cy,w.text))
                }
                // OCR can miss names on decorative cards. Scan for the vivid circular portraits in each column.
                for(x in cols) {
                    val peaks=(32..82 step 2).map { it/100.0 }.map { y -> y to score(b,x,y,.025) }
                        .filter { it.second>30 && (it.first<.58 || it.second>35) }.sortedByDescending { it.second }
                    for((y,_) in peaks) if(spots.none { abs(it.first-x)<.025&&abs(it.second-y)<.17 })
                        spots.add(Triple(x,y,null))
                }
            }
            "tat-ca-trang-bi" -> {
                val cols=listOf(.324,.388,.453,.520,.586,.651)
                // Four rows in the provided screenshot; subsequent pages use the same safe content area.
                for(y in listOf(.24,.428,.613,.80)) for(x in cols)
                    if(score(b,x,y,.019)>35) spots.add(Triple(x,y,null))
            }
            "phe","nghe" -> {
                val cols=(0..11).map { .236+.0562*it }
                val rows=if(t.key=="phe") listOf(.297 to 12) else listOf(.547 to 12,.711 to 3)
                for((y,n) in rows) for(x in cols.take(n)) if(score(b,x,y,.017)>40)
                    spots.add(Triple(x,y,null))
            }
        }
        val result=ArrayList<Tile>()
        for((x,y,label) in spots.sortedWith(compareBy({it.second},{it.first}))) {
            if(result.any { abs(it.x-x)<.022&&abs(it.y-y)<.12 }) continue
            val id=fingerprint(b,x,y)
            if(result.none { it.id==id }) result.add(Tile(x,y,id,label))
        }
        return result
    }
    /** Patch colour/edge energy: distinguish a vivid icon from the uniform blue/grey backing. */
    private fun score(b:Bitmap,x:Double,y:Double,r:Double):Double {
        var saturated=0.0;var contrast=0.0;var previous=-1;var n=0
        for(dy in -3..3) for(dx in -3..3) {
            val px=b.getPixel(((x+dx*r/3)*b.width).toInt().coerceIn(0,b.width-1),
                              ((y+dy*r/3)*b.height).toInt().coerceIn(0,b.height-1))
            val red=Color.red(px);val green=Color.green(px);val blue=Color.blue(px)
            val hi=max(red,max(green,blue));val lo=min(red,min(green,blue))
            saturated+=(hi-lo)*.40
            val l=(red+green+blue)/3
            if(previous>=0) contrast+=abs(l-previous)*.45
            previous=l;n++
        }
        return (saturated+contrast)/n
    }
    /** Same icon at another position/after scrolling gets the same hash (coarsely quantized pixels). */
    private fun fingerprint(b:Bitmap,x:Double,y:Double):String {
        val md=MessageDigest.getInstance("SHA-256")
        for(row in 0..7) for(col in 0..7) {
            val xx=((x+(col-3.5)*.005)*b.width).toInt().coerceIn(0,b.width-1)
            val yy=((y+(row-3.5)*.009)*b.height).toInt().coerceIn(0,b.height-1)
            val p=b.getPixel(xx,yy)
            md.update(byteArrayOf(((p shr 20) and 15).toByte(),((p shr 12) and 15).toByte(),((p shr 4) and 15).toByte()))
        }
        return md.digest().take(7).joinToString("") { "%02x".format(it) }
    }
    /** 96 brightness samples only from the scrolling area (not static menu or header). */
    fun signature(b:Bitmap,modal:Boolean=false):IntArray {
        val x0=if(modal) .38 else .24
        val x1=if(modal) .87 else .92
        return IntArray(96) { n ->
            val xx=x0+(x1-x0)*((n%12)+.5)/12.0;val yy=.25+.64*((n/12)+.5)/8.0
            val p=b.getPixel((xx*b.width).toInt().coerceIn(0,b.width-1),(yy*b.height).toInt().coerceIn(0,b.height-1))
            (Color.red(p)*299+Color.green(p)*587+Color.blue(p)*114)/1000
        }
    }
    fun changed(a:IntArray,b:IntArray):Boolean =
        a.size!=b.size || a.indices.sumOf { abs(a[it]-b[it]) }.toDouble()/a.size>4.0
    fun modal(b:Bitmap):Boolean {
        // The encyclopedia's dialog has a large white X in the upper right, absent on the page itself.
        val x=(.902*b.width).toInt();val y=(.083*b.height).toInt()
        val rad=(.019*b.height).toInt().coerceAtLeast(5)
        var bright=0;var dark=0
        for(i in -8..8) {
            val d=rad*i/10
            val p=b.getPixel((x+d).coerceIn(0,b.width-1),(y+d).coerceIn(0,b.height-1))
            val q=b.getPixel((x+d).coerceIn(0,b.width-1),(y-d).coerceIn(0,b.height-1))
            val a=b.getPixel((x+d).coerceIn(0,b.width-1),(y).coerceIn(0,b.height-1))
            if(Color.red(p)>190&&Color.green(p)>190&&Color.blue(p)>190) bright++
            if(Color.red(q)>190&&Color.green(q)>190&&Color.blue(q)>190) bright++
            if(Color.red(a)<175) dark++
        }
        return bright>=16&&dark>=8
    }
}

/**
 * Asynchronous reader. A look owns one bitmap, which MUST be kept or discarded exactly once.
 * Completed topics/item IDs are persisted by Screen so restarting an interrupted task will skip them.
 */
class ChessWikiReader(private val screen:Screen,private val options:ChessWiki.Options,
                      private val finish:(Outcome)->Unit) {
    sealed class Outcome { data class Done(val pages:Int,val items:Int):Outcome();data class Failed(val reason:String):Outcome() }
    interface Look {
        val words:List<GameScreens.Word>
        val signature:IntArray
        val modal:Boolean
        fun tiles(topic:ChessWiki.Topic):List<ChessWiki.Tile>
        fun keep(name:String,then:(Boolean)->Unit)
        fun discard()
    }
    interface Screen {
        val alive:Boolean
        fun look(words:Boolean,then:(Look?)->Unit)
        fun tap(x:Double,y:Double,then:(Boolean)->Unit)
        fun swipe(x:Double,y:Double,toX:Double,toY:Double,then:(Boolean)->Unit)
        fun later(ms:Long,then:()->Unit)
        fun seen(id:String):Boolean
        fun finished(id:String)
        fun note(message:String)
        fun say(message:String)
    }
    companion object {
        const val MAX_PAGES=65
        const val MAX_MODAL_PAGES=16
        const val MAX_NAV=14
        const val MAX_STILL=3
        const val WAIT_MS=1100L
        const val MAX_TILES=450
    }
    private val topics=options.topics()
    private var completed=false;private var pages=0;private var items=0;private var navigation=0;private var selectedChess=false
    private val tried=HashSet<String>()
    fun start()=navigate()
    private fun end(out:Outcome) { if(!completed) { completed=true;finish(out) } }
    private fun fail(reason:String)=end(Outcome.Failed(reason))
    private fun look(words:Boolean=false,then:(Look)->Unit) {
        if(!screen.alive||completed) return
        screen.look(words) { l ->
            when { !screen.alive||completed -> l?.discard()
                l==null -> fail("Không chụp được màn hình. Kiểm tra quyền Trợ năng và dung lượng, rồi bấm Tiếp tục.")
                else -> then(l) }
        }
    }
    private fun tap(x:Double,y:Double,wait:Long=WAIT_MS,next:()->Unit) {
        if(!screen.alive) return
        screen.tap(x,y) { ok -> if(!ok) fail("Không chạm được màn hình.") else screen.later(wait,next) }
    }
    private fun swipe(x:Double,y:Double,toX:Double,toY:Double,wait:Long=WAIT_MS,next:()->Unit) {
        if(!screen.alive) return
        screen.swipe(x,y,toX,toY) { ok -> if(!ok) fail("Không cuộn được nội dung.") else screen.later(wait,next) }
    }
    private fun save(l:Look,name:String,next:()->Unit) {
        l.keep(name) { ok -> if(!screen.alive||completed) return@keep
            if(!ok) fail("Lưu ảnh thất bại: "+name+"; kiểm tra dung lượng và bấm Tiếp tục.")
            else { pages++;next() }
        }
    }
    /** Existing lobby -> mode selection -> entertainment -> chess lobby -> encyclopedia. */
    private fun navigate():Unit=look(true) { l ->
        val words=l.words
        val all=words.joinToString(" ") { it.plain }
        if(l.modal) { l.discard();return@look tap(.901,.08) { navigate() } }
        if(all.contains("bachkhoa") && all.contains("colienquan")) {
            l.discard();screen.say("Đã vào Bách khoa Cờ Liên Quân"); return@look topic(0)
        }
        if(navigation++>=MAX_NAV) {
            return@look save(l,"chess__dieu-huong__khong-nhan-dien__trang__001") {
                fail("Không nhận ra đường vào Bách khoa. Mở Cờ Liên Quân → Trang chủ bằng tay rồi bấm Tiếp tục.")
            }
        }
        l.discard()
        when {
            (all.contains("trangchu")&&all.contains("colienquan")) ->
                tap(words.firstOrNull { it.plain.contains("trangchu")&&it.x<.24 }?.x?:.102,
                    words.firstOrNull { it.plain.contains("trangchu")&&it.x<.24 }?.y?:.305,1800) { navigate() }
            all.contains("chedogiaitri") && all.contains("colienquan") && !selectedChess -> {
                selectedChess=true
                tap(.507,.827,1100) { navigate() }
            }
            all.contains("chedogiaitri") && all.contains("colienquan") && all.contains("batdau") && selectedChess ->
                tap(words.firstOrNull { it.plain=="batdau"&&it.x>.6 }?.x?:.862,
                    words.firstOrNull { it.plain=="batdau"&&it.x>.6 }?.y?:.688,3700) { navigate() }
            all.contains("chedogiaitri") && all.contains("colienquan") ->
                tap(.50,.824,1000) { navigate() }
            all.contains("chedogiaitri") -> tap(.135,.37) { navigate() }
            all.contains("chonchedo") || all.contains("tuid o") || all.contains("tuido") ->
                tap(.515,.91,1700) { navigate() }
            all.contains("colienquan") -> tap(.105,.30,2000) { navigate() }
            else -> screen.later(1800) { navigate() }
        }
    }
    private fun topic(at:Int) {
        if(!screen.alive) return
        if(at>=topics.size) return end(Outcome.Done(pages,items))
        val t=topics[at]
        if(screen.seen("topic:"+t.key)) return topic(at+1)
        screen.say("Bách khoa: "+t.side+" → "+t.label)
        tap(.129,t.sidebarY,1000) {
            if(t.tabX==0.0) scan(at,1,null,0,0)
            else if(t.sweepHeader) swipe(.83,.18,.37,.18,700) {
                tap(t.tabX,.18) { scan(at,1,null,0,0) }
            } else tap(t.tabX,.18) { scan(at,1,null,0,0) }
        }
    }
    private fun scan(at:Int,page:Int,previous:IntArray?,still:Int,retries:Int):Unit=look(true) { l ->
        val t=topics[at]
        if(l.modal) { l.discard();return@look tap(.902,.082) { scan(at,page,previous,still,retries+1) } }
        if(page>MAX_PAGES) {
            l.discard();return@look fail("Đã tới giới hạn "+MAX_PAGES+" trang ở "+t.label+
                ". Kiểm tra xem danh sách đã tới cuối hay chưa; ảnh đã lưu được giữ lại.")
        }
        if(previous!=null&&!ChessWiki.changed(previous,l.signature)) {
            l.discard()
            if(still>=MAX_STILL) {
                screen.finished("topic:"+t.key)
                return@look topic(at+1)
            }
            // Two extra slow drags: a game list sometimes ignores its first drag near a transition.
            return@look screen.later(650) { swipe(.76,.80,.76,.38) { scan(at,page,previous,still+1,retries) } }
        }
        // The first screenshot of each page is saved *before* clicking any item.
        val image=ChessWiki.filename(t,"trang",page)
        val signature=l.signature.copyOf()
        val candidates=if(ChessWiki.detailType(t,options)!=null) l.tiles(t) else emptyList()
        val path="chess-page:"+t.key+":"+page
        val next={ collect(at,page,signature,candidates,0) }
        if(screen.seen(path)) { l.discard(); next() }
        else save(l,image) { screen.finished(path);next() }
    }
    private fun collect(at:Int,page:Int,signature:IntArray,tiles:List<ChessWiki.Tile>,from:Int) {
        val t=topics[at];val kind=ChessWiki.detailType(t,options)
        if(kind==null || from>=tiles.size || items>=MAX_TILES) return nextPage(at,page,signature)
        val item=tiles[from];val id="item:"+t.key+":"+item.id
        if(screen.seen(id) || !tried.add(id)) return collect(at,page,signature,tiles,from+1)
        screen.say(t.label+": chi tiết "+(from+1)+"/"+tiles.size)
        tap(item.x,item.y,1250) {
            look(true) { l ->
                if(!l.modal) {
                    l.discard();screen.note("Không mở được chi tiết ở "+item.x+","+item.y+
                        ". Giữ ảnh trang danh sách, bỏ qua thẻ này.")
                    return@look collect(at,page,signature,tiles,from+1)
                }
                val heading=l.words.firstOrNull { it.y in .215.. .335 && it.x in .35.. .79 &&
                    it.plain.length in 2..35 && !it.plain.contains("colienquan") }?.text
                val slug=ChessWiki.safeName(heading?:item.label?:"muc")
                val folder=slug+"-"+item.id.take(8)
                modal(at,page,signature,tiles,from,item,kind,folder,1,l,null,0)
            }
        }
    }
    private fun modal(at:Int,page:Int,signature:IntArray,tiles:List<ChessWiki.Tile>,from:Int,
                      tile:ChessWiki.Tile,kind:String,folder:String,number:Int,l:Look,
                      previous:IntArray?,still:Int) {
        val t=topics[at]
        if(number>MAX_MODAL_PAGES) { l.discard();return fail("Chi tiết "+folder+" vẫn còn cuộn sau "+MAX_MODAL_PAGES+" ảnh. Kiểm tra bằng tay rồi tiếp tục.") }
        if(previous!=null&&!ChessWiki.changed(previous,l.signature)) {
            l.discard()
            if(still>=2) return close(at,page,signature,tiles,from,tile)
            return swipe(.75,.81,.75,.40) {
                look(false) { next -> modal(at,page,signature,tiles,from,tile,kind,folder,number,next,previous,still+1) }
            }
        }
        val shotName=ChessWiki.filename(t,kind,number,folder)
        val current=l.signature.copyOf()
        save(l,shotName) {
            if(!options.allPages) close(at,page,signature,tiles,from,tile)
            else swipe(.75,.82,.75,.42) {
                look(false) { next ->
                    if(!next.modal) { next.discard();fail("Chi tiết "+folder+" đã đóng ngoài dự kiến, hãy mở đúng màn hình rồi tiếp tục.") }
                    else modal(at,page,signature,tiles,from,tile,kind,folder,number+1,next,current,0)
                }
            }
        }
    }
    private fun close(at:Int,page:Int,signature:IntArray,tiles:List<ChessWiki.Tile>,from:Int,tile:ChessWiki.Tile) {
        tap(.902,.081,900) {
            screen.finished("item:"+topics[at].key+":"+tile.id);items++
            collect(at,page,signature,tiles,from+1)
        }
    }
    private fun nextPage(at:Int,page:Int,signature:IntArray) {
        if(!options.allPages) { screen.finished("topic:"+topics[at].key);return topic(at+1) }
        swipe(.76,.82,.76,.40,1100) { scan(at,page+1,signature,0,0) }
    }
}
