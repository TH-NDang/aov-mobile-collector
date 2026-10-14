package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * The game's hero statistics: lobby → the button left of Túi đồ (its icon changes with the season) → Cộng đồng →
 * the second item of the side bar (a hero helmet) → "Bảng xếp hạng đầy đủ". That page, "Bảng Phân Bậc Tướng",
 * scrolls through every hero and shows one of two tables, switched by "Đổi bảng xếp hạng": rates (tier, win, pick
 * and ban rates) or match figures (damage, kills, gold, damage taken, assists), each for three kinds of match
 * picked from a drop-down. Measured on the user's 2400x1080 screenshots. The pages after the lobby are a web
 * view, whose margins move with the side of the navigation bar, so they are found by their words.
 */
object RankScreens {
    enum class Page { LOBBY, PANEL, COMMUNITY, HEROES, RANKING, OTHER }
    enum class Table(val slug:String,val label:String) { RATES("ty-le","Tỷ lệ thắng, pick, ban"), FIGURES("chi-so","Chỉ số mỗi trận") }
    enum class Mode(val slug:String,val key:String,val label:String) {
        ALL("dau-hang-tat-ca","tatca","Đấu hạng (Tất cả)"), FIVE("dau-5v5","5v5","Đấu 5v5"), ELITE("dau-hang-tinh-anh","tinhanh","Đấu hạng (Tinh Anh trở lên)")
    }
    /** One table for one kind of match: its photos go to a folder named [slug]. */
    data class Combo(val table:Table,val mode:Mode) { val slug get()="${table.slug}-${mode.slug}"; val label get()="${table.label} · ${mode.label}" }
    val COMBOS=Table.values().flatMap { t -> Mode.values().map { Combo(t,it) } }

    /** The lobby button left of Túi đồ, which opens "Tính năng khác". */
    const val MORE_X=.084; const val MORE_Y=.89
    /** The side bar's second item on the Cộng đồng page, below its first; above the LIÊNQUÂN logo. */
    const val SIDE_X=.0815; const val SIDE_Y=.348

    fun page(words:List<GameScreens.Word>,lobby:Boolean):Page = when {
        words.any { it.plain.contains("doibangxephang") } -> Page.RANKING
        words.any { it.plain.contains("bangxephangdaydu") } -> Page.HEROES
        // The logo is drawn in its own letters, which OCR may miss; the news page names its author and weekly rewards.
        logo(words)!=null||words.any { w -> listOf("garenalienquanmobile","nhanthuonghangtuan").any { w.plain.contains(it) } } -> Page.COMMUNITY
        words.any { it.plain.contains("tinhnangkhac")||it.plain.contains("congdong") } -> Page.PANEL
        lobby -> Page.LOBBY
        else -> Page.OTHER
    }
    /** "LIÊNQUÂN ARENA OF VALOR" at the bottom of the Cộng đồng side bar, its drawn letters read as "uÊNQUÂN" or "UÊNGUÂN". */
    fun logo(words:List<GameScreens.Word>)=words.firstOrNull { it.x<.2&&it.y>.8&&(it.plain.contains("arenaofvalor")||it.plain.contains("lienquan")||it.plain.length in 5..10&&(it.plain.endsWith("quan")||it.plain.endsWith("guan"))) }
    fun table(words:List<GameScreens.Word>):Table? = when {
        words.any { w -> listOf("tylethang","tylepick","tyleban").any { w.plain.contains(it) } } -> Table.RATES
        words.any { it.plain.contains("satthuong")||it.plain=="vang"||it.plain.contains("phaichiu") } -> Table.FIGURES
        else -> null
    }
    /** The drop-down's button, "Đấu hạng (Tất cả)" or another kind of match, above the table. */
    fun modeButton(words:List<GameScreens.Word>)=words.filter { it.plain.startsWith("dau")&&it.y<.27&&it.x>.35&&it.x<.8 }.minByOrNull { it.y }
    fun mode(word:GameScreens.Word?):Mode? = word?.plain?.let { p -> Mode.values().firstOrNull { p.contains(it.key) } }
    /**
     * An open drop-down's line for [mode], below its button. ML Kit may join a number of the table behind it to the
     * line, as "124.2 Đấu hang (Tinh Anh trở lên)"; its middle stays inside the drop-down.
     */
    fun option(words:List<GameScreens.Word>,button:GameScreens.Word,mode:Mode)=words.firstOrNull { it.y>button.bottom+.02&&it.y<.7&&it.plain.contains("dau")&&it.plain.contains(mode.key) }

    /** Grey levels of the table's area on a 48x24 grid, to tell whether a scroll moved it. */
    fun signature(width:Int,height:Int,readRow:(Int,IntArray)->Unit):IntArray {
        val cols=48; val rows=24; val sums=IntArray(cols*rows); val counts=IntArray(cols*rows)
        val x0=(.08*width).toInt(); val x1=(.92*width).toInt(); val y0=(.30*height).toInt(); val y1=(.97*height).toInt()
        val line=IntArray(width)
        for(y in y0 until y1 step 3) { readRow(y,line); val r=(y-y0)*rows/(y1-y0)
            for(x in x0 until x1 step 3) { val c=line[x]; val i=r*cols+(x-x0)*cols/(x1-x0)
                sums[i]+=(299*(c shr 16 and 255)+587*(c shr 8 and 255)+114*(c and 255))/1000; counts[i]++ } }
        return IntArray(sums.size) { if(counts[it]==0) 0 else sums[it]/counts[it] }
    }
    /** Two signatures of the same rows: a scroll that moved nothing, at the end of the list or its top. */
    fun same(a:IntArray,b:IntArray)=a.size==b.size&&a.indices.sumOf { abs(a[it]-b[it]) }.toDouble()/a.size<3
    /** A folder of the run named after a [Combo], with its label for the app's lists. */
    fun folderTitle(name:String)=COMBOS.firstOrNull { it.slug==name }?.label
}

/**
 * Goes from wherever it is started (lobby, "Tính năng khác", Cộng đồng, or the table itself) to "Bảng Phân Bậc
 * Tướng", then photographs both tables for each of the three kinds of match, scrolling to the end of the list each
 * time. [skip] holds the combinations an interrupted try of the same step already finished. All screen access goes
 * through [Screen], so unit tests run this exact logic on scripted screens.
 */
class RankingReader(private val screen:Screen,private val skip:Set<String> =emptySet(),private val finish:(Outcome)->Unit) {
    sealed class Outcome { class Done(val pages:Int):Outcome(); class Failed(val reason:String):Outcome() }
    /** One screenshot: [words] only when asked for; call [done] once, with a name to keep it in the run or null. */
    interface Look { val words:List<GameScreens.Word>; val lobby:Boolean; val signature:IntArray; fun done(keep:String?) }
    interface Screen {
        val alive:Boolean
        fun look(words:Boolean,then:(Look?)->Unit)
        fun tap(x:Double,y:Double,then:(Boolean)->Unit)
        /** Drags the page up by [d] screen heights (down when negative); [fling] is a quick flick, else it rests before lifting. */
        fun drag(d:Double,fling:Boolean,then:(Boolean)->Unit)
        fun later(ms:Long,then:()->Unit)
        fun note(text:String) {}
        fun say(text:String) {}
        /** [combo] is photographed to the end; a later try of the step can skip it. */
        fun finished(combo:String) {}
    }
    companion object {
        /** Web pages may take ten seconds or more to load over a slow connection. */
        const val MAX_STEPS=10; const val MAX_WAITS=8; const val MAX_TRIES=4
        /** More pages than the table has: five heroes a page, three new ones per scroll. */
        const val MAX_PAGES=80; const val MAX_FLINGS=40
        /** A scroll moves less than the visible rows, so consecutive photos overlap by a row or two. */
        const val SCROLL=.40
    }
    private var ended=false; private var pages=0
    private val left=ArrayList<RankScreens.Combo>()

    fun start() = navigate(0,0)

    private fun end(outcome:Outcome) { if(!ended) { ended=true; finish(outcome) } }
    private fun fail(reason:String) = end(Outcome.Failed(reason))
    private fun look(words:Boolean,then:(Look)->Unit) {
        if(!screen.alive) return
        screen.look(words) { l -> when { !screen.alive -> l?.done(null); l==null -> fail("Không chụp được màn hình (máy sắp hết chỗ trống, hoặc Android từ chối chụp). Kiểm tra rồi bấm Tiếp tục."); else -> then(l) } }
    }
    private fun tap(x:Double,y:Double,wait:Long,then:()->Unit) {
        if(!screen.alive) return
        screen.tap(x,y) { ok -> if(ok) screen.later(wait,then) else fail("Không chạm được màn hình") }
    }
    private fun drag(d:Double,fling:Boolean,wait:Long,then:()->Unit) {
        if(!screen.alive) return
        screen.drag(d,fling) { ok -> if(ok) screen.later(wait,then) else fail("Không cuộn được bảng xếp hạng") }
    }
    private fun go(l:Look,what:String,x:Double,y:Double,wait:Long,steps:Int) {
        l.done(null); screen.note(what); screen.say("$what…"); tap(x,y,wait) { navigate(steps+1,0) }
    }

    /** One tap at a time towards the table, reading where it is after each. */
    private fun navigate(steps:Int,waits:Int):Unit = look(true) { l ->
        if(steps>MAX_STEPS) { l.done("ranking-lost"); return@look fail("Đã bấm $MAX_STEPS lần mà chưa tới Bảng Phân Bậc Tướng. Mở sảnh game rồi bấm Tiếp tục.") }
        val words=l.words
        when(RankScreens.page(words,l.lobby)) {
            RankScreens.Page.RANKING -> ranking(l)
            RankScreens.Page.HEROES -> words.first { it.plain.contains("bangxephangdaydu") }.let { go(l,"Bấm Bảng xếp hạng đầy đủ",it.x,it.y,3000,steps) }
            RankScreens.Page.COMMUNITY -> go(l,"Cộng đồng: chọn mục tướng ở thanh bên",RankScreens.logo(words)?.x?:RankScreens.SIDE_X,RankScreens.SIDE_Y,2500,steps)
            RankScreens.Page.PANEL -> { val w=words.firstOrNull { it.plain.contains("congdong") }
                if(w==null) { l.done("ranking-panel"); fail("Không thấy mục Cộng đồng trong Tính năng khác.") } else go(l,"Bấm Cộng đồng",w.x,w.y,3500,steps) }
            RankScreens.Page.LOBBY -> go(l,"Sảnh: bấm nút bên trái Túi đồ",RankScreens.MORE_X,RankScreens.MORE_Y,1500,steps)
            // Web pages take a moment to show their words.
            RankScreens.Page.OTHER -> if(waits<MAX_WAITS) { l.done(null); screen.later(1500) { navigate(steps,waits+1) } }
                else { l.done("ranking-unknown"); fail("Không nhận ra màn hình. Mở sảnh game hoặc Bảng Phân Bậc Tướng rồi bấm Tiếp tục.") }
        }
    }
    /** On the table at last: every combination not done yet, starting with the one shown. */
    private fun ranking(l:Look) {
        val table=RankScreens.table(l.words); val mode=RankScreens.mode(RankScreens.modeButton(l.words))
        l.done(null)
        val order=RankScreens.COMBOS.sortedBy { c -> (if(c.table==table) 0 else 2)+(if(c.mode==mode) 0 else 1) }
        left.clear(); left.addAll(order.filter { it.slug !in skip })
        screen.note("Bảng Phân Bậc Tướng: còn ${left.size}/${RankScreens.COMBOS.size} bảng")
        next()
    }
    private fun next() {
        val combo=left.firstOrNull()?:return end(Outcome.Done(pages))
        prepare(combo,0)
    }
    /** Switches the table and the kind of match until both are [combo]'s, checking after each tap. */
    private fun prepare(combo:RankScreens.Combo,tries:Int):Unit = look(true) { l ->
        val words=l.words
        if(RankScreens.page(words,l.lobby)!=RankScreens.Page.RANKING) { l.done(null); return@look navigate(0,0) }
        if(tries>MAX_TRIES) { l.done("ranking-switch"); return@look fail("Không chuyển được sang bảng ${combo.label}.") }
        val table=RankScreens.table(words); val button=RankScreens.modeButton(words); val mode=RankScreens.mode(button)
        when {
            // Headings or the drop-down not read yet: the page may still be loading.
            table==null||button==null -> { l.done(null); screen.later(1500) { prepare(combo,tries+1) } }
            table!=combo.table -> { val w=words.first { it.plain.contains("doibangxephang") }
                l.done(null); screen.note("Đổi bảng xếp hạng"); tap(w.x,w.y,2500) { prepare(combo,tries+1) } }
            mode!=combo.mode -> { l.done(null); tap(button.x,button.y,1200) { pick(combo,button,tries) } }
            else -> { screen.say(combo.label); screen.note("Chụp ${combo.label}"); scan(combo,1,null) }
        }
    }
    /** The drop-down is open: tap [combo]'s kind of match, or close it again when it is not there. */
    private fun pick(combo:RankScreens.Combo,button:GameScreens.Word,tries:Int) = look(true) { l ->
        val option=RankScreens.option(l.words,button,combo.mode)
        l.done(null)
        if(option!=null) tap(option.x,option.y,3000) { prepare(combo,tries+1) } else tap(button.x,button.y,1200) { prepare(combo,tries+1) }
    }
    /** Photographs the table from where it is, scrolling until a scroll changes nothing: the end of the list. */
    private fun scan(combo:RankScreens.Combo,page:Int,last:IntArray?):Unit = look(false) { l ->
        if(last!=null&&RankScreens.same(l.signature,last)) {
            l.done(null); screen.finished(combo.slug); left.remove(combo)
            screen.note("${combo.label}: ${page-1} ảnh")
            return@look top(0,null) { next() }
        }
        l.done("${combo.slug}-%02d".format(page)); pages++
        screen.say("${combo.label} · trang $page")
        if(page>=MAX_PAGES) { screen.note("${combo.label}: dừng ở $MAX_PAGES trang"); screen.finished(combo.slug); left.remove(combo); return@look top(0,null) { next() } }
        drag(SCROLL,false,1200) { scan(combo,page+1,l.signature) }
    }
    /** Flicks back to the top of the page, where the switches are, until a flick changes nothing. */
    private fun top(flings:Int,last:IntArray?,then:()->Unit) {
        if(flings>=MAX_FLINGS) return then()
        drag(-.6,true,700) { look(false) { l -> l.done(null)
            if(last!=null&&RankScreens.same(l.signature,last)) then() else top(flings+1,l.signature,then) } }
    }
}
