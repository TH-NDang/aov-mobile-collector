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
        switchButton(words)!=null||
            // A readable title, mode and table headings still identify the full table if OCR
            // misses the switch. Never navigate back just because that one label flickers.
            words.any { it.y<.16&&it.plain.contains("bangphanbactuong") }&&
                mode(modeButton(words))!=null&&table(words)!=null -> Page.RANKING
        fullRankingLink(words)!=null||heroesLanding(words)||words.any { it.plain.contains("bangxephangdaydu") } -> Page.HEROES
        // The logo is drawn in its own letters, which OCR may miss; the news page names its author and weekly rewards.
        logo(words)!=null||words.any { w -> listOf("garenalienquanmobile","nhanthuonghangtuan").any { w.plain.contains(it) } } -> Page.COMMUNITY
        words.any { it.plain.contains("tinhnangkhac")||it.plain.contains("congdong") } -> Page.PANEL
        lobby -> Page.LOBBY
        else -> Page.OTHER
    }
    /** Header crop used only to recover the dim full-ranking link, not a fallback tap position. */
    const val LINK_LEFT=.30; const val LINK_TOP=.02; const val LINK_RIGHT=.70; const val LINK_BOTTOM=.16
    private const val FULL_LINK="bangxephangdaydu"

    /** The landing page is still known when OCR misses its small gold link. */
    fun heroesLanding(words:List<GameScreens.Word>):Boolean {
        val title=words.any { it.y<.16&&it.plain.contains("bangphanbactuong") }
        return title&&(logo(words)!=null||words.any { it.y in .16.. .35&&it.plain.contains("bangxephangtylethang") })
    }
    /**
     * A visible, compact header label only. Accessibility can put the entire web page's text in a parent
     * whose centre is far below the link. Reject those boxes, join adjacent OCR fragments on the same line,
     * and prefer the smallest agreeing box. Separate plausible labels are ambiguous: never guess between them.
     */
    fun fullRankingLink(words:List<GameScreens.Word>):GameScreens.Word? {
        val landing=heroesLanding(words)
        fun compact(w:GameScreens.Word)=listOf(w.left,w.top,w.right,w.bottom).all { it.isFinite() }&&
            w.left>=LINK_LEFT&&w.right<=LINK_RIGHT&&w.top>=LINK_TOP&&w.bottom<=LINK_BOTTOM&&
            w.right-w.left in .008.. .28&&w.bottom-w.top in .008.. .075
        fun label(p:String)=p==FULL_LINK||p=="xephangdaydu"||landing&&(p=="hangdaydu"||p=="daydu")
        val header=words.filter { compact(it)&&it.plain.isNotEmpty()&&FULL_LINK.contains(it.plain) }
            .distinctBy { listOf(it.plain,(it.left*1000).toInt(),(it.top*1000).toInt(),(it.right*1000).toInt(),(it.bottom*1000).toInt()) }
            .sortedBy { it.left }.take(40)
        val candidates=ArrayList<GameScreens.Word>()
        for(first in header) {
            var joined=first
            if(label(joined.plain)) candidates+=joined
            // Six words in the label; no fuzzy match against unrelated headings or article text.
            repeat(5) {
                val next=header.filter { it.left>=joined.right-.003&&it.left-joined.right<=.025&&
                    abs(it.y-joined.y)<=.018&&FULL_LINK.contains(joined.plain+it.plain) }
                    .minByOrNull { it.left }
                if(next!=null) {
                    joined=GameScreens.Word(joined.text+" "+next.text,joined.left,minOf(joined.top,next.top),next.right,maxOf(joined.bottom,next.bottom))
                    if(compact(joined)&&label(joined.plain)) candidates+=joined
                }
            }
        }
        val best=candidates.minByOrNull { (it.right-it.left)*(it.bottom-it.top) }?:return null
        // OCR and accessibility duplicates can have slightly different boxes, but must cover the same label.
        if(candidates.any {
            val overlapX=minOf(it.right,best.right)-maxOf(it.left,best.left)
            val overlapY=minOf(it.bottom,best.bottom)-maxOf(it.top,best.top)
            overlapX<.65*minOf(it.right-it.left,best.right-best.left)||overlapY<.55*minOf(it.bottom-it.top,best.bottom-best.top)
        }) return null
        return best
    }
    fun needsFullRankingOcr(words:List<GameScreens.Word>)=fullRankingLink(words)==null&&
        page(words,false) in setOf(Page.HEROES,Page.COMMUNITY)

    /** "LIÊNQUÂN ARENA OF VALOR" at the bottom of the Cộng đồng side bar, its drawn letters read as "uÊNQUÂN" or "UÊNGUÂN". */
    fun logo(words:List<GameScreens.Word>)=words.firstOrNull { it.x<.2&&it.y>.8&&(it.plain.contains("arenaofvalor")||it.plain.contains("lienquan")||it.plain.length in 5..10&&(it.plain.endsWith("quan")||it.plain.endsWith("guan"))) }
    private fun compact(w:GameScreens.Word)=listOf(w.left,w.top,w.right,w.bottom).all { it.isFinite() }&&
        w.left>=0&&w.right<=1&&w.top>=0&&w.bottom<=1&&w.right-w.left in .008.. .40&&w.bottom-w.top in .008.. .12
    fun switchButton(words:List<GameScreens.Word>)=words.filter {
        compact(it)&&it.plain.contains("doibangxephang")&&it.x in .70.. .96&&it.y in .12.. .28
    }.minByOrNull { (it.right-it.left)*(it.bottom-it.top) }
    fun table(words:List<GameScreens.Word>):Table? = when {
        words.any { w -> compact(w)&&w.y in .25.. .40&&w.x in .30.. .72&&listOf("tylethang","tylepick","tyleban").any { w.plain.contains(it) } } -> Table.RATES
        words.any { compact(it)&&it.y in .25.. .40&&it.x>.35&&(it.plain.contains("satthuong")||it.plain=="vang"||it.plain.contains("phaichiu")) } -> Table.FIGURES
        else -> null
    }
    /** The drop-down's button, "Đấu hạng (Tất cả)" or another kind of match, above the table. */
    fun modeButton(words:List<GameScreens.Word>)=words.filter {
        compact(it)&&it.plain.startsWith("dau")&&mode(it)!=null&&it.y in .12.. .27&&it.x in .35.. .8
    }.minByOrNull { (it.right-it.left)*(it.bottom-it.top) }
    fun mode(word:GameScreens.Word?):Mode? = word?.plain?.let { p -> Mode.values().filter { p.contains(it.key) }.singleOrNull() }
    /**
     * An open drop-down's line for [mode], below its button. ML Kit may join a number of the table behind it to the
     * line, as "124.2 Đấu hang (Tinh Anh trở lên)"; its middle stays inside the drop-down.
     */
    fun option(words:List<GameScreens.Word>,button:GameScreens.Word,mode:Mode)=words.filter {
        compact(it)&&abs(it.x-button.x)<.20&&it.y>button.bottom+.02&&it.y<.7&&it.plain.contains("dau")&&RankScreens.mode(it)==mode
    }.minByOrNull { (it.right-it.left)*(it.bottom-it.top) }

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
 * time; or only the [wanted] ones. [skip] holds the combinations an interrupted try of the same step already finished. All screen access goes
 * through [Screen], so unit tests run this exact logic on scripted screens.
 */
class RankingReader(private val screen:Screen,private val skip:Set<String> =emptySet(),private val wanted:List<RankScreens.Combo> =RankScreens.COMBOS,
                     private val finish:(Outcome)->Unit) {
    sealed class Outcome { class Done(val pages:Int):Outcome(); class Failed(val reason:String):Outcome() }
    /** One screenshot: [words] only when asked for; call [done] once, with a name to keep it in the run or null. */
    interface Look {
        val words:List<GameScreens.Word>; val lobby:Boolean; val signature:IntArray
        fun done(keep:String?)
        /** Consumes this frame; complete only after PNG storage has succeeded or failed. */
        fun keep(name:String,then:(Boolean)->Unit)
    }
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
        const val MAX_LINK_TAPS=2
        /** More pages than the table has: five heroes a page, three new ones per scroll. */
        const val MAX_PAGES=80; const val MAX_FLINGS=40
        /** A scroll moves less than the visible rows, so consecutive photos overlap by a row or two. */
        const val SCROLL=.40
        /** Looks and drags at a table that did not move before it counts as at its end. */
        const val STILL_TRIES=3
    }
    private var ended=false; private var pages=0
    private var communityChosen=false; private var fullLinkTaps=0
    private val left=ArrayList<RankScreens.Combo>()

    fun start() = navigate(0,0)

    private fun end(outcome:Outcome) { if(!ended) { ended=true; finish(outcome) } }
    private fun fail(reason:String) = end(Outcome.Failed(reason))
    private fun look(words:Boolean,then:(Look)->Unit) {
        if(!screen.alive||ended) return
        screen.look(words) { l -> when { !screen.alive||ended -> l?.done(null); l==null -> fail("Không chụp được màn hình (máy sắp hết chỗ trống, hoặc Android từ chối chụp). Kiểm tra rồi bấm Tiếp tục."); else -> then(l) } }
    }
    private fun tap(x:Double,y:Double,wait:Long,then:()->Unit) {
        if(!screen.alive) return
        screen.tap(x,y) { ok -> if(screen.alive&&!ended) { if(ok) screen.later(wait,then) else fail("Không chạm được màn hình") } }
    }
    private fun drag(d:Double,fling:Boolean,wait:Long,then:()->Unit) {
        if(!screen.alive) return
        screen.drag(d,fling) { ok -> if(screen.alive&&!ended) { if(ok) screen.later(wait,then) else fail("Không cuộn được bảng xếp hạng") } }
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
            RankScreens.Page.HEROES -> {
                val link=RankScreens.fullRankingLink(words)
                when {
                    // Give the web view time to load after a tap; every retry needs a newly verified label.
                    (link==null||fullLinkTaps>0)&&waits<MAX_WAITS -> waitForNavigation(l,steps,waits)
                    link==null -> missingFullLink(l)
                    fullLinkTaps>=MAX_LINK_TAPS -> { l.done("ranking-link-not-open"); fail("Đã bấm Bảng xếp hạng đầy đủ nhưng bảng chưa mở. Mở bảng rồi bấm Tiếp tục.") }
                    else -> { fullLinkTaps++;go(l,"Bấm Bảng xếp hạng đầy đủ",link.x,link.y,3000,steps) }
                }
            }
            RankScreens.Page.COMMUNITY -> if(communityChosen||fullLinkTaps>0) {
                if(waits<MAX_WAITS) waitForNavigation(l,steps,waits) else missingFullLink(l)
            } else {
                communityChosen=true
                go(l,"Cộng đồng: chọn mục tướng ở thanh bên",RankScreens.logo(words)?.x?:RankScreens.SIDE_X,RankScreens.SIDE_Y,2500,steps)
            }
            RankScreens.Page.PANEL -> { val w=words.firstOrNull { it.plain.contains("congdong") }
                if(w==null) { l.done("ranking-panel"); fail("Không thấy mục Cộng đồng trong Tính năng khác.") } else go(l,"Bấm Cộng đồng",w.x,w.y,3500,steps) }
            RankScreens.Page.LOBBY -> go(l,"Sảnh: bấm nút bên trái Túi đồ",RankScreens.MORE_X,RankScreens.MORE_Y,1500,steps)
            // Web pages take a moment to show their words.
            RankScreens.Page.OTHER -> if(waits<MAX_WAITS) waitForNavigation(l,steps,waits)
                else { l.done("ranking-unknown"); fail("Không nhận ra màn hình. Mở sảnh game hoặc Bảng Phân Bậc Tướng rồi bấm Tiếp tục.") }
        }
    }
    private fun waitForNavigation(l:Look,steps:Int,waits:Int) {
        l.done(null);screen.later(1500) { navigate(steps,waits+1) }
    }
    private fun missingFullLink(l:Look) {
        l.done("ranking-link-unreadable")
        fail("Không xác định được nút Bảng xếp hạng đầy đủ. Mở bảng bằng tay rồi bấm Tiếp tục.")
    }
    /** On the table at last: every combination not done yet, starting with the one shown. */
    private fun ranking(l:Look) {
        val table=RankScreens.table(l.words); val mode=RankScreens.mode(RankScreens.modeButton(l.words))
        val order=wanted.sortedBy { c -> (if(c.table==table) 0 else 2)+(if(c.mode==mode) 0 else 1) }
        left.clear(); left.addAll(order.filter { it.slug !in skip })
        screen.note("Bảng Phân Bậc Tướng: còn ${left.size}/${wanted.size} bảng")
        next(l)
    }
    private fun next(frame:Look?=null) {
        val combo=left.firstOrNull()
        if(combo==null) { frame?.done(null);return end(Outcome.Done(pages)) }
        if(frame!=null) prepareFrame(combo,0,frame) else prepare(combo,0)
    }
    /** Switches the table and the kind of match until both are [combo]'s, checking after each tap. */
    private fun prepare(combo:RankScreens.Combo,tries:Int):Unit = look(true) { prepareFrame(combo,tries,it) }
    private fun prepareFrame(combo:RankScreens.Combo,tries:Int,l:Look) {
        if(!screen.alive||ended) { l.done(null);return }
        val words=l.words
        // A fresh navigation would reset both counters and loop forever on alternating OCR.
        // Keep one bounded preparation attempt, including frames that cannot identify the page.
        if(tries>MAX_TRIES) { l.done(null);return fail("Không xác nhận được bảng ${combo.label}. Mở đúng bảng rồi bấm Tiếp tục.") }
        val table=RankScreens.table(words); val button=RankScreens.modeButton(words); val mode=RankScreens.mode(button)
        fun waitForTable() {
            l.done(null)
            screen.note("Chờ xác nhận ${combo.label}: lần ${tries+1}/${MAX_TRIES+1}, màn hình=${RankScreens.page(words,l.lobby)}, bảng=$table, chế độ=$mode")
            screen.say("Đang xác nhận bảng · ${tries+1}/${MAX_TRIES+1}")
            screen.later(1500) { prepare(combo,tries+1) }
        }
        when {
            // Headings or the drop-down not read yet: the page may still be loading.
            RankScreens.page(words,l.lobby)!=RankScreens.Page.RANKING||table==null||button==null||mode==null -> waitForTable()
            table!=combo.table -> {
                val w=RankScreens.switchButton(words)
                if(w==null) waitForTable() else {
                    l.done(null);screen.note("Đổi bảng xếp hạng");tap(w.x,w.y,2500) { prepare(combo,tries+1) }
                }
            }
            mode!=combo.mode -> { l.done(null); tap(button.x,button.y,1200) { pick(combo,button,tries) } }
            // Save this verified, current frame. No second OCR observation, leaked bitmap, or
            // intervening navigation between verifying the requested table and its first photo.
            else -> { screen.say(combo.label); screen.note("Chụp ${combo.label}"); scanFrame(combo,1,null,0,l) }
        }
    }
    /** The drop-down is open: tap [combo]'s kind of match, or close it again when it is not there. */
    private fun pick(combo:RankScreens.Combo,button:GameScreens.Word,tries:Int) = look(true) { l ->
        val option=RankScreens.option(l.words,button,combo.mode)
        l.done(null)
        if(option!=null) tap(option.x,option.y,3000) { prepare(combo,tries+1) } else tap(button.x,button.y,1200) { prepare(combo,tries+1) }
    }
    /**
     * Photographs the table from where it is, scrolling until scrolls change nothing: the end of the list. On the
     * user's phone a scroll sometimes showed nothing new while the page loaded more rows, which ended two lists
     * a third of the way down; so a still table is looked at again after a wait, then dragged twice more, before
     * it counts as the end.
     */
    private fun scan(combo:RankScreens.Combo,page:Int,last:IntArray?,still:Int=0):Unit = look(false) { scanFrame(combo,page,last,still,it) }
    private fun scanFrame(combo:RankScreens.Combo,page:Int,last:IntArray?,still:Int,l:Look) {
        if(!screen.alive||ended) { l.done(null);return }
        if(last!=null&&RankScreens.same(l.signature,last)) {
            l.done(null)
            if(still<STILL_TRIES) {
                screen.note("${combo.label}: trang ${page-1} chưa đổi sau khi kéo, thử lại (${still+1}/$STILL_TRIES)")
                return if(still==0) screen.later(1500) { scan(combo,page,last,1) } else screen.later(1500) { drag(SCROLL,false,1200) { scan(combo,page,last,still+1) } }
            }
            screen.finished(combo.slug); left.remove(combo)
            screen.note("${combo.label}: ${page-1} ảnh")
            return top(0,null) { next() }
        }
        val signature=l.signature.copyOf()
        l.keep("${combo.slug}-%02d".format(page)) { ok ->
            if(!screen.alive||ended) return@keep
            if(!ok) return@keep fail("Không lưu được ảnh ${combo.label}, trang $page. Kiểm tra dung lượng rồi bấm Tiếp tục.")
            pages++
            screen.say("${combo.label} · đã lưu trang $page")
            screen.note("Đã lưu ${combo.slug}, trang $page")
            if(page>=MAX_PAGES) return@keep fail("Đã lưu $MAX_PAGES trang nhưng chưa xác nhận cuối bảng ${combo.label}.")
            drag(SCROLL,false,1200) { scan(combo,page+1,signature) }
        }
    }
    /** Flicks back to the top of the page, where the switches are, until a flick changes nothing. */
    private fun top(flings:Int,last:IntArray?,then:()->Unit) {
        if(flings>=MAX_FLINGS) return then()
        drag(-.6,true,700) { look(false) { l -> l.done(null)
            if(last!=null&&RankScreens.same(l.signature,last)) then() else top(flings+1,l.signature,then) } }
    }
}
