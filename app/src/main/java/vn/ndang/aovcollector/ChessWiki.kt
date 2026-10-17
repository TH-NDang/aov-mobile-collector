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
                       val traceEntry:Boolean=true,
                       val heroDetails:Boolean=false,val synergyDetails:Boolean=false,
                       val equipmentDetails:Boolean=false,val nativeGroups:Set<String> =emptySet()) {
        fun topics()=TOPICS.filter { it.key in selected }
        init {
            require(selected.isNotEmpty() && selected.all { key -> TOPICS.any { it.key==key } })
            require(nativeGroups.all { group -> GamePhotoImport.GROUPS.any { it.key==group&&group!="chua-phan-loai" } })
        }
    }
    fun detailType(t:Topic,o:Options)=when(t.key) {
        "tat-ca-quan-co" -> if(o.heroDetails||"chieu-quan-co" in o.nativeGroups) "tuong" else null
        "phe","nghe" -> if(o.synergyDetails) t.key else null
        "tat-ca-trang-bi" -> if(o.equipmentDetails) "vat-pham" else null
        else -> null
    }
    /** The currently selected Bách khoa tab determines each native image's class automatically. */
    fun nativeGroup(t:Topic,o:Options):String? {
        val g=when(t.key) {
            "tat-ca-quan-co" -> "quan-co"
            "phe" -> "phe"
            "nghe" -> "nghe"
            "tat-ca-trang-bi" -> "trang-bi"
            "thuong" -> "chuc-phuc-thuong"
            "huyen-thoai-su-thi" -> "chuc-phuc-huyen-thoai-su-thi"
            "truyen-thuyet" -> "chuc-phuc-truyen-thuyet"
            else -> null
        }
        return g?.takeIf { it in o.nativeGroups }
    }
    fun topicsForNative(groups:Set<String>):Set<String> = groups.mapNotNull {
        when(it) {
            "quan-co","chieu-quan-co" -> "tat-ca-quan-co"
            "phe" -> "phe"
            "nghe" -> "nghe"
            "trang-bi" -> "tat-ca-trang-bi"
            "chuc-phuc-thuong" -> "thuong"
            "chuc-phuc-huyen-thoai-su-thi" -> "huyen-thoai-su-thi"
            "chuc-phuc-truyen-thuyet" -> "truyen-thuyet"
            else -> null
        }
    }.toSet()

    /** Only the exact Save Photo dialog may be confirmed, never a generic YES/OK in game. */
    fun nativeConfirm(words:List<GameScreens.Word>):Pair<Double,Double>? {
        val full=GameScreens.plain(words.joinToString(" ") { it.text })
        if(!full.contains("savephoto") || !full.contains("sure") || !full.contains("cancel")) return null
        val sure=words.firstOrNull { it.plain=="sure"&&it.x in .25.. .50&&it.y in .45.. .70 }
        return sure?.let { it.x to it.y } ?: .393 to .57
    }

    /** A partially recognized Save Photo prompt must be dismissed, not mistaken for a missing menu. */
    fun nativeCancel(words:List<GameScreens.Word>):Pair<Double,Double>? {
        val full=GameScreens.plain(words.joinToString(" ") { it.text })
        if(!full.contains("savephoto")||!full.contains("cancel")) return null
        val cancel=words.firstOrNull { it.plain=="cancel"&&it.x in .49.. .79&&it.y in .45.. .70 }
        return cancel?.let { it.x to it.y } ?: .58 to .56
    }

    fun nativePromptVisible(words:List<GameScreens.Word>):Boolean {
        val full=GameScreens.plain(words.joinToString(" ") { it.text })
        return full.contains("savephoto") &&
            (full.contains("sure")||full.contains("cancel"))
    }

    /** Long-press the actual full-color pictograms, not their text or the category's background. */
    fun nativeTiles(topic:Topic,bitmap:Bitmap,words:List<GameScreens.Word>):List<Tile> {
        if(topic.section!="chuc-phuc") return tiles(topic,bitmap,words)
        val spots=ArrayList<Tile>()
        for(y in listOf(.40,.68,.91)) for(x in listOf(.275,.615)) {
            if(score(bitmap,x,y,.027)>28) spots.add(Tile(x,y,fingerprint(bitmap,x,y)))
        }
        return spots
    }
    fun skillTiles(bitmap:Bitmap):List<Tile> {
        val spots=ArrayList<Tile>()
        // Modal's illustrated skill icon sits around x=.40, y=.53 in provided reference.
        // Scrolling may expose another skill; fingerprint guards against repeats.
        for(y in listOf(.50,.55,.66)) {
            if(score(bitmap,.405,y,.025)>28) spots.add(Tile(.405,y,fingerprint(bitmap,.405,y)))
        }
        return spots.distinctBy { it.id }.take(2)
    }

    /** Nested path tokens are validated before use by the storage adapter. */
    fun filename(topic:Topic,kind:String,page:Int,item:String?=null):String {
        val base="chess__"+topic.section+"__"+topic.key+"__"
        return if(item==null) base+"trang__%03d".format(page)
               else base+kind+"__"+item+"__%03d".format(page)
    }
    fun safeName(text:String)=HeroNames.slug(text).take(45).ifEmpty { "chua-ro-ten" }
    /**
     * "Chọn chế độ" sits immediately to the right of "N.vụ" on the landscape lobby toolbar.
     * Screenshot 2048x921: mode icon center ~= (0.635, 0.895), quest icon ~= (0.515, 0.90).
     * Prefer the text's own horizontal position, but tap the icon vertically rather than its
     * low label; if OCR loses the small label, use the calibrated safe icon center.
     * Refuse to guess unless the bottom lobby toolbar itself is identifiable.
     */
    const val MODE_X=.635
    const val MODE_Y=.895
    fun modeEntryPoint(words:List<GameScreens.Word>):Pair<Double,Double>? {
        val footer=words.filter { it.y in .78.. .98 }
        val label=footer.firstOrNull { it.plain.contains("chonchedo") && it.x in .57.. .71 }
        if(label!=null) return label.x to MODE_Y
        // The small mode text is often unreadable; other lobby labels still identify the bar.
        // "N.vụ" is just to its left: never use that label's x as the tap target.
        if(footer.any { it.plain in setOf("tuido","nvu","tuong","trangbi") }) return MODE_X to MODE_Y
        return null
    }


    data class Tile(val x:Double,val y:Double,val id:String,val label:String?=null)

    // The five card centres in the chess encyclopedia (not the left edges of each card).
    // A portrait is about 17% of the screen ABOVE its hero name. Reading the name first
    // prevents tapping remnants of the previous row when the list has been scrolled.
    private val HERO_COLUMNS=listOf(.275,.421,.567,.713,.858)
    private val HERO_ROLES=setOf("phaothu","dausi","cungthu","phapsu","dodon","satthu","trothu")
    private val NON_HERO_NAMES=setOf(
        "phaothu","dausi","cungthu","phapsu","dodon","satthu","trothu",
        "phuthuy","cuongchien","thuve","chuatet","thanhquan",
        "giaidieuhocduong","doalacgia","mianhduoitrang","langkhach",
        "demdong","chihuy","hothien","hoily","phe","nghe"
    )

    /** OCR can include the role icon as a letter: "A Đấu sĩ" is still a role label. */
    fun isHeroName(name:String):Boolean {
        val plain=GameScreens.plain(name)
        return name.length in 2..35 && name.count { it.isLetter() }>=2 &&
            !name.contains('…') && !name.contains("...") &&
            NON_HERO_NAMES.none { plain==it || (plain.endsWith(it)&&plain.length<=it.length+2) }
    }

    fun heroProgress(saved:Int,current:String?=null)=
        "Quân cờ: đã lưu $saved tướng · tổng chưa xác định"+
            (current?.let { " · đang mở $it" }?:"")

    /**
     * Named cards use their visible names. A role-only observation has no hero label until
     * the popup verifies it. Position is the portrait, safely tappable after scrolling.
     */
    fun heroNameTiles(words:List<GameScreens.Word>):List<Tile> {
        val result=ArrayList<Tile>()
        val unnamed=ArrayList<Tile>()
        for(w in words) {
            val name=w.text.trim().replace(Regex("\\s+\\d+$"),"").trim()
            val slug=HeroNames.slug(name)
            if(w.y !in .49.. .925 || slug.length !in 2..25) continue
            val x=HERO_COLUMNS.minByOrNull { abs(w.x-it) }?:continue
            if(abs(w.x-x)>.047) continue
            if(!isHeroName(name)) {
                val plain=GameScreens.plain(name)
                // If the name itself was missed, a role identifies a card's position only.
                // It sits below the name; never use this role text as the hero's identity.
                if(HERO_ROLES.any { plain.endsWith(it)&&plain.length<=it.length+2 }) {
                    val y=w.y-.23
                    if(y in .29.. .77) unnamed.add(Tile(x,y,
                        "unread-"+HERO_COLUMNS.indexOf(x)+"-"+(y*1000).toInt()))
                }
                continue
            }
            val portraitY=w.y-.172
            if(portraitY !in .29.. .77) continue
            if(result.none { it.id=="hero-"+slug })
                result.add(Tile(x,portraitY,"hero-"+slug,name))
        }
        for(tile in unnamed) if(result.none { abs(it.x-tile.x)<.02&&abs(it.y-tile.y)<.10 })
            result.add(tile)
        return heroRows(result).flatten()
    }

    /** Group OCR detections by approximate Y, then sort INSIDE each row by X. */
    fun heroRows(tiles:List<Tile>):List<List<Tile>> {
        val rows=ArrayList<MutableList<Tile>>()
        for(tile in tiles.sortedWith(compareBy({it.y},{it.x}))) {
            if(rows.isEmpty()||abs(rows.last().first().y-tile.y)>.075) rows.add(ArrayList())
            rows.last().add(tile)
        }
        return rows.map { row -> row.sortedBy { it.x } }
    }

    fun rowKey(topic:Topic,row:List<Tile>)=
        "row:"+topic.key+":"+row.joinToString("|") { it.id }

    /** A cropped screenshot of exactly this row, saved in .../tat-ca-quan-co/hang/. */
    fun rowFilename(topic:Topic,page:Int,row:List<Tile>):String {
        val digest=MessageDigest.getInstance("SHA-256").digest(rowKey(topic,row).toByteArray())
            .take(5).joinToString("") { "%02x".format(it) }
        val first=safeName(row.first().label?:"chua-ro-ten").take(20)
        return "chess__"+topic.section+"__"+topic.key+"__hang__"+
            "%03d".format(page)+"-"+first+"-"+digest
    }

    /** Hero's name lives at the top of the detail drawer; the skill title is lower down. */
    fun modalHeroTitle(words:List<GameScreens.Word>,expected:String?=null):String? {
        // A skill title can be OCR'd before the actual hero name. Restrict candidates
        // to the heading strip ABOVE faction labels and choose the expected card name first.
        val candidates=words.asSequence()
            .filter { it.x in .345.. .67 && it.y in .175.. .345 }
            .map { word -> word to word.text.trim().replace(Regex("\\s+\\d+$"),"").trim() }
            .filter { (_,name) ->
                isHeroName(name)
            }.toList()
        if(expected!=null) candidates.firstOrNull {
            safeName(it.second)==safeName(expected)
        }?.let { return it.second }
        return candidates.minWithOrNull(compareBy<Pair<GameScreens.Word,String>>(
            { abs(it.first.y-.245) }, { it.first.x }))?.second
    }

    /**
     * White title letters distinguish the hero's NAME from cyan faction/job labels.
     * Read within ML Kit's own text bounds; no fixed row Y, so page 2 is recognized.
     */
    private fun looksLikeWhiteTitle(b:Bitmap,w:GameScreens.Word):Boolean {
        val x0=(w.left*b.width).toInt().coerceIn(0,b.width-1)
        val x1=(w.right*b.width).toInt().coerceIn(x0+1,b.width)
        val y0=(w.top*b.height).toInt().coerceIn(0,b.height-1)
        val y1=(w.bottom*b.height).toInt().coerceIn(y0+1,b.height)
        val dx=max(1,(x1-x0)/26)
        val dy=max(1,(y1-y0)/10)
        var nearWhite=0;var total=0
        for(y in y0 until y1 step dy) for(x in x0 until x1 step dx) {
            val p=b.getPixel(x,y)
            val red=Color.red(p);val green=Color.green(p);val blue=Color.blue(p)
            if(red>166&&green>166&&blue>166&&abs(red-green)<48&&abs(green-blue)<48) nearWhite++
            total++
        }
        return total>0 && nearWhite.toDouble()/total>.025
    }

    /** All coordinates are fractions of the full landscape screenshot. */
    fun tiles(t:Topic,b:Bitmap,words:List<GameScreens.Word>):List<Tile> {
        if(t.key=="tat-ca-quan-co") {
            return heroNameTiles(words).filter { tile ->
                if(tile.label==null) return@filter score(b,tile.x,tile.y,.022)>12
                val title=words.firstOrNull { w ->
                    HeroNames.slug(w.text.trim().replace(Regex("\\s+\\d+$"),""))==
                        tile.id.removePrefix("hero-") &&
                    abs(w.y-(tile.y+.172))<.015 && abs(w.x-tile.x)<.047
                }
                title!=null && looksLikeWhiteTitle(b,title) &&
                    score(b,tile.x,tile.y,.022)>12
            }
        }
        val spots=ArrayList<Triple<Double,Double,String?>>()
        when(t.key) {
            "tat-ca-trang-bi" -> {
                val cols=listOf(.324,.388,.453,.520,.586,.651)
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
        fun nativeTiles(topic:ChessWiki.Topic):List<ChessWiki.Tile> = tiles(topic)
        fun skillIcons():List<ChessWiki.Tile> = emptyList()
        fun keep(name:String,then:(Boolean)->Unit)
        /** Crop the card row (with its name + portraits) before saving; also releases this Look. */
        fun keepRow(name:String,top:Double,bottom:Double,then:(Boolean)->Unit) = keep(name,then)
        fun discard()
    }
    interface Screen {
        val alive:Boolean
        /** Previously completed heroes from this run's persistent ledger. */
        val completedHeroes:Int get()=0
        fun look(words:Boolean,then:(Look?)->Unit)
        fun tap(x:Double,y:Double,then:(Boolean)->Unit)
        fun swipe(x:Double,y:Double,toX:Double,toY:Double,then:(Boolean)->Unit)
        /** Long press, identify Save Photo → Sure, and record its current group. */
        fun nativeSave(group:String,item:String,x:Double,y:Double,
                       preserveDetail:Boolean=false,then:(Boolean)->Unit) { then(false) }
        /** Re-read only the hero heading strip when full-screen OCR is ambiguous. */
        fun readHeroTitle(then:(String?)->Unit) { then(null) }
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
    private var veteranAttempts=0;private var tipAttempts=0
    private var heroes=screen.completedHeroes
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
        // ML Kit can split "Chế độ giải trí" across lines; compare both whole normalized text
        // and recognizable fragments rather than requiring one OCR box with the full phrase.
        val all=GameScreens.plain(words.joinToString(" ") { it.text })
        val entertainment=words.any { it.plain.contains("chedogiai")||it.plain.contains("giaitri") } ||
            all.contains("chedogiaitri")
        // A fresh chess account may show these two screens AFTER selecting the game mode.
        // Use exactly the same guarded recognition as the game's initial entry flow.
        val onboarding=GameScreens.refine(
            GameScreens.Reading(GameScreens.Kind.OTHER,null,l.signature),words,null)
        val modeEntry=ChessWiki.modeEntryPoint(words)
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
            onboarding.kind==GameScreens.Kind.CHESS_EXPERIENCE -> {
                if(++veteranAttempts>2) return@look fail("Không qua màn chọn kinh nghiệm Cờ Liên Quân. Chọn Kỳ cựu bằng tay rồi bấm Tiếp tục.")
                screen.note("Tài khoản mới: chọn Kỳ cựu")
                onboarding.tap!!.let { at -> tap(at.first,at.second,2000) { navigate() } }
            }
            onboarding.kind==GameScreens.Kind.CHESS_TIP -> {
                if(++tipAttempts>2) return@look fail("Không qua thông báo Đấu luyện. Bấm Đã rõ bằng tay rồi Tiếp tục.")
                screen.note("Tài khoản mới: bấm Đã rõ")
                onboarding.tap!!.let { at -> tap(at.first,at.second,2000) { navigate() } }
            }
            (all.contains("trangchu")&&all.contains("colienquan")) ->
                tap(words.firstOrNull { it.plain.contains("trangchu")&&it.x<.24 }?.x?:.102,
                    words.firstOrNull { it.plain.contains("trangchu")&&it.x<.24 }?.y?:.305,1800) { navigate() }
            entertainment && all.contains("colienquan") && !selectedChess -> {
                selectedChess=true
                tap(.507,.827,1100) { navigate() }
            }
            entertainment && all.contains("colienquan") && all.contains("batdau") && selectedChess ->
                tap(words.firstOrNull { it.plain=="batdau"&&it.x>.6 }?.x?:.862,
                    words.firstOrNull { it.plain=="batdau"&&it.x>.6 }?.y?:.688,3700) { navigate() }
            entertainment && all.contains("colienquan") ->
                tap(.50,.824,1000) { navigate() }
            entertainment -> tap(.135,.37) { navigate() }
            modeEntry!=null -> {
                screen.note("Sảnh: chọn chế độ ở "+(modeEntry.first*100).toInt()+"%, "+(modeEntry.second*100).toInt()+"% (không bấm N.vụ)")
                tap(modeEntry.first,modeEntry.second,1700) { navigate() }
            }
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
        val native=ChessWiki.nativeGroup(t,options)!=null
        // A row screenshot is mandatory for Quân cờ even when "Mở từng quân cờ" is off.
        val candidates=when {
            t.key=="tat-ca-quan-co" -> l.tiles(t)
            native -> l.nativeTiles(t)
            ChessWiki.detailType(t,options)!=null -> l.tiles(t)
            else -> emptyList()
        }
        val path="chess-page:"+t.key+":"+page
        val next={ collect(at,page,signature,candidates,0) }
        if(screen.seen(path)) { l.discard(); next() }
        else save(l,image) { screen.finished(path);next() }
    }
    /** Save a dedicated screenshot for each fully visible row BEFORE opening its heroes. */
    private fun collect(at:Int,page:Int,signature:IntArray,tiles:List<ChessWiki.Tile>,from:Int) {
        val t=topics[at]
        val kind=ChessWiki.detailType(t,options)
        val originalGroup=ChessWiki.nativeGroup(t,options)
        if(from>=tiles.size||items>=MAX_TILES||
           (kind==null&&originalGroup==null&&t.key!="tat-ca-quan-co"))
            return nextPage(at,page,signature)
        val newRow=t.key=="tat-ca-quan-co" &&
            (from==0 || abs(tiles[from-1].y-tiles[from].y)>.075)
        if(newRow) {
            val row=ChessWiki.heroRows(tiles).firstOrNull { group ->
                group.any { it.id==tiles[from].id }
            }.orEmpty()
            if(row.isNotEmpty()) {
                val marker=ChessWiki.rowKey(t,row)
                if(!screen.seen(marker)) {
                    screen.say("Quân cờ: lưu ảnh hàng "+page+" ("+
                        row.joinToString(", ") { it.label?:"?" }+")")
                    return look(false) { rowShot ->
                        if(rowShot.modal) {
                            rowShot.discard()
                            return@look fail("Có cửa sổ chi tiết khi chụp ảnh hàng. Đóng cửa sổ rồi bấm Tiếp tục.")
                        }
                        val top=(row.first().y-.17).coerceIn(.20,.80)
                        val bottom=(row.first().y+.47).coerceIn(top+.15,.985)
                        rowShot.keepRow(ChessWiki.rowFilename(t,page,row),top,bottom) { ok ->
                            if(!screen.alive||completed) return@keepRow
                            if(!ok) fail("Không lưu được ảnh hàng "+page+
                                ". Ảnh danh sách được giữ lại; kiểm tra dung lượng rồi Tiếp tục.")
                            else {
                                pages++
                                screen.finished(marker)
                                collectItem(at,page,signature,tiles,from)
                            }
                        }
                    }
                }
            }
        }
        collectItem(at,page,signature,tiles,from)
    }

    private fun collectItem(at:Int,page:Int,signature:IntArray,
                            tiles:List<ChessWiki.Tile>,from:Int) {
        val t=topics[at]
        val kind=ChessWiki.detailType(t,options)
        val originalGroup=ChessWiki.nativeGroup(t,options)
        val item=tiles[from]
        val identity="item:"+t.key+":"+item.id
        val observation=if(item.label==null) identity+":page:"+page else identity
        if(!tried.add(observation)) return collect(at,page,signature,tiles,from+1)

        fun detail() {
            if(kind==null||screen.seen(identity)) return collect(at,page,signature,tiles,from+1)
            screen.say(if(kind=="tuong") ChessWiki.heroProgress(heroes,item.label) else
                t.label+": chi tiết "+(from+1)+"/"+tiles.size)
            tap(item.x,item.y,1250) {
                look(true) { l ->
                    if(!l.modal) {
                        l.discard()
                        screen.note("Không mở được chi tiết "+(item.label?:"?")+
                            " tại "+item.x+","+item.y+". Ảnh hàng đã lưu.")
                        return@look collect(at,page,signature,tiles,from+1)
                    }
                    val header=if(kind=="tuong") ChessWiki.modalHeroTitle(l.words,item.label) else
                        l.words.firstOrNull { it.y in .215.. .335 &&
                            it.x in .35.. .79 && it.plain.length in 2..35 &&
                            !it.plain.contains("colienquan") }?.text
                    fun capture(verified:String?) {
                        if(!screen.alive||completed) { l.discard();return }
                        val title=verified?:item.label
                        if(kind=="tuong"&&title==null) {
                            l.discard()
                            return fail("Không đọc được tên quân cờ trong thẻ hoặc vùng tiêu đề. Ảnh hàng vẫn được giữ.")
                        }
                        val slug=ChessWiki.safeName(title?:"muc")
                        val folder=if(kind=="tuong") slug else slug+"-"+item.id.take(8)
                        // The popup heading is authoritative. Never persist a noisy card label
                        // as a completed hero, or count the same popup again after scrolling.
                        val actual=if(kind=="tuong") item.copy(id="hero-"+slug,label=title) else item
                        if(kind=="tuong"&&screen.seen("item:"+t.key+":"+actual.id)) {
                            l.discard()
                            return close(at,page,signature,tiles,from,actual)
                        }
                        if(kind=="tuong") screen.say(ChessWiki.heroProgress(heroes,title))
                        modal(at,page,signature,tiles,from,actual,kind,folder,1,l,null,0)
                    }
                    if(kind!="tuong") return@look capture(header)
                    if(item.label!=null&&header!=null &&
                       ChessWiki.safeName(header)==ChessWiki.safeName(item.label))
                        return@look capture(item.label)

                    // The full-screen pass sometimes reads a skill as the hero's title.
                    // Do not conclude "wrong hero" from that unverified OCR result.
                    screen.note("Tên popup chưa chắc chắn: thẻ="+(item.label?:"?")+
                        ", OCR toàn màn hình="+(header?:"không thấy")+
                        ". Đọc lại riêng vùng tên tướng.")
                    fun verify(attempt:Int,previous:String?=null) {
                        screen.readHeroTitle { precise ->
                            if(!screen.alive||completed) { l.discard();return@readHeroTitle }
                            val valid=precise?.takeIf { ChessWiki.isHeroName(it) }
                            val matchesCard=valid!=null&&item.label!=null&&
                                ChessWiki.safeName(valid)==ChessWiki.safeName(item.label)
                            val confirmed=valid!=null&&previous!=null&&
                                ChessWiki.safeName(valid)==ChessWiki.safeName(previous)
                            if(valid!=null && (matchesCard||confirmed)) {
                                screen.note("Đã xác minh tên quân cờ bằng OCR vùng tiêu đề: "+valid+
                                    if(matchesCard) "" else "; sửa nhãn thẻ "+(item.label?:"?"))
                                capture(valid)
                            } else if(attempt<3) {
                                screen.later(600) { verify(attempt+1,valid) }
                            } else {
                                l.discard()
                                fail("Chưa xác minh được tên quân cờ "+(item.label?:"?")+
                                    " trong vùng tiêu đề sau 3 lần. Ảnh hàng vẫn được giữ; "+
                                    "kiểm tra popup rồi bấm Tiếp tục.")
                            }
                        }
                    }
                    verify(1)
                }
            }
        }

        val nativeKey=originalGroup?.let { "native:"+it+":"+item.id }
        if(originalGroup==null || nativeKey==null || screen.seen(nativeKey)) return detail()
        screen.say(if(t.key=="tat-ca-quan-co") "Quân cờ: lưu ảnh gốc "+
            (item.label?:"đang nhận diện")+" · tổng chưa xác định" else
            t.label+": lưu ảnh gốc "+(from+1)+"/"+tiles.size)
        screen.nativeSave(originalGroup,item.label?:item.id,item.x,item.y,false) { saved ->
            if(saved) {
                screen.finished(nativeKey)
                screen.note("Ảnh gốc "+originalGroup+": "+(item.label?:item.id))
            } else screen.note("Không hiện Save Photo tại "+item.x+","+item.y+"; bỏ qua ảnh gốc này")
            detail()
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
        val skillImages=if("chieu-quan-co" in options.nativeGroups) l.skillIcons() else emptyList()
        save(l,shotName) {
            fun advance() {
                swipe(.75,.82,.75,.42) {
                    look(false) { next ->
                        if(!next.modal) {
                            next.discard()
                            fail("Chi tiết "+folder+" đã đóng ngoài dự kiến, hãy mở đúng màn hình rồi tiếp tục.")
                        } else modal(at,page,signature,tiles,from,tile,kind,folder,number+1,next,current,0)
                    }
                }
            }
            fun nativeSkill(at:Int,images:List<ChessWiki.Tile>,next:()->Unit) {
                if(at>=images.size) return next()
                val candidate=images[at]
                val key="native:chieu-quan-co:"+folder+":"+candidate.id
                if(screen.seen(key)||!tried.add(key)) return nativeSkill(at+1,images,next)
                screen.nativeSave("chieu-quan-co",folder+"-skill-"+candidate.id,
                    candidate.x,candidate.y,true) { ok ->
                    if(ok) screen.finished(key)
                    nativeSkill(at+1,images,next)
                }
            }
            nativeSkill(0,skillImages) { advance() }
        }
    }
    private fun close(at:Int,page:Int,signature:IntArray,tiles:List<ChessWiki.Tile>,from:Int,
                      tile:ChessWiki.Tile,tries:Int=0) {
        if(tries>=3) return fail("Không đóng được bảng chi tiết. Đóng dấu X bằng tay rồi bấm Tiếp tục.")
        tap(.902,.081,900) {
            look(false) { l ->
                val stillOpen=l.modal;l.discard()
                if(stillOpen) close(at,page,signature,tiles,from,tile,tries+1)
                else {
                    val identity="item:"+topics[at].key+":"+tile.id
                    if(!screen.seen(identity)) {
                        screen.finished(identity);items++
                        if(topics[at].key=="tat-ca-quan-co") heroes++
                    }
                    if(topics[at].key=="tat-ca-quan-co") screen.say(ChessWiki.heroProgress(heroes))
                    collect(at,page,signature,tiles,from+1)
                }
            }
        }
    }
    private fun nextPage(at:Int,page:Int,signature:IntArray) {
        swipe(.76,.82,.76,.40,1100) { scan(at,page+1,signature,0,0) }
    }
}

