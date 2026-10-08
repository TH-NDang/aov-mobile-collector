package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * Brings card [card] of the All-heroes list on screen and taps it. The game keeps sliding after a swipe
 * and moves less at the end of the list, so every scroll is measured from screenshots, never assumed.
 * All screen access goes through [Screen], so unit tests run this exact logic on a drawn list.
 */
class ListNavigator(private val card:Int,private val state:State,private val screen:Screen,private val finish:(Outcome)->Unit) {
    /** Kept between the picks of one run. [step] shrinks when swipes slide further than can be measured. */
    class State(var offset:Double=0.0,var page:Int=1,var synced:Boolean=false,var step:Double=1.0,var ref:FloatArray?=null)
    sealed class Outcome { object Tapped:Outcome(); object End:Outcome(); class Failed(val reason:String):Outcome() }
    /** One screenshot of the list; call [done] exactly once, with keep=true to store it as a page image. */
    interface Shot { val profile:FloatArray; val height:Int; fun hasCard(x:Double,y:Double):Boolean; fun done(keep:Boolean) }
    interface Screen {
        val alive:Boolean
        /** null when no screenshot could be read; an empty profile when the screen is not the hero list. */
        fun look(then:(Shot?)->Unit)
        /** Drags the content up by [d] screen heights (negative: down). Slow drags rest before lifting; [fling] is a quick flick. */
        fun drag(d:Double,fling:Boolean,then:(Boolean)->Unit)
        fun tap(x:Double,y:Double,then:(Boolean)->Unit)
        fun later(ms:Long,then:()->Unit)
    }
    private var moves=0
    private var stalls=0
    private var resyncs=0
    private val maxMoves=16+5*(card/5)

    fun start() {
        if(!state.synced) return toTop(0)
        look { s ->
            // The list should be exactly where the previous pick left it; anything else is a different screen.
            val ref=state.ref
            if(ref!=null) { val shift=HeroGrid.shift(ref,s.profile); if(!shift.reliable) { s.done(false); return@look lost() }; state.offset+=shift.px.toDouble()/s.height }
            state.ref=s.profile; aim(s,false)
        }
    }
    private fun fail(reason:String) { finish(Outcome.Failed(reason)) }
    private fun lost() { state.synced=false; state.ref=null; fail("Màn hình không còn là danh sách tướng như lúc trước. Mở danh sách Tất cả rồi bấm Tiếp tục; app sẽ tự tìm lại vị trí.") }
    private fun look(then:(Shot)->Unit) {
        if(!screen.alive) return
        screen.look { s ->
            when {
                !screen.alive -> s?.done(false)
                s==null -> fail("Không chụp được danh sách tướng")
                s.profile.isEmpty() -> { s.done(false); state.synced=false; state.ref=null; fail("Màn hình hiện tại không phải danh sách Tất cả tướng. Mở danh sách rồi bấm Tiếp tục.") }
                else -> then(s)
            }
        }
    }
    private fun aim(s:Shot,keep:Boolean) {
        when(val plan=HeroGrid.plan(card,state.offset)) {
            is HeroGrid.Plan.Move -> { s.done(keep); move(plan.d*state.step) }
            is HeroGrid.Plan.Tap -> { val has=s.hasCard(plan.x,plan.y); s.done(keep); if(has) tap(plan.x,plan.y) else finish(Outcome.End) }
        }
    }
    private fun tap(x:Double,y:Double) { if(screen.alive) screen.tap(x,y) { ok -> if(ok) finish(Outcome.Tapped) else fail("Không chạm được thẻ tướng") } }
    private fun move(d:Double) {
        if(!screen.alive) return
        if(++moves>maxMoves) return fail("Không cuộn tới được ô tướng ${card+1}")
        val before=state.ref?:return resync(); val offsetBefore=state.offset
        screen.drag(d,false) { ok ->
            if(!ok) return@drag fail("Không vuốt được danh sách")
            screen.later(800) { look { s ->
                val shift=HeroGrid.shift(before,s.profile)
                if(shift.reliable) settle(shift.px.toDouble()/s.height,s,d) else { s.done(false); undo(d,before,offsetBefore) }
            } }
        }
    }
    private fun settle(moved:Double,s:Shot,d:Double) {
        state.offset+=moved; state.ref=s.profile
        if(abs(moved)>=.01) { stalls=0; state.page++; return aim(s,true) }
        if(++stalls<2) return aim(s,false)
        // The list stopped moving: past its end there is nothing more to collect.
        val (x,y)=HeroGrid.centre(card,state.offset)
        val has=d>0&&y<HeroGrid.LAST_ROW_LIMIT&&s.hasCard(x,y); s.done(false)
        if(has) tap(x,y) else if(d>0) finish(Outcome.End) else fail("Danh sách không cuộn được")
    }
    /** The list slid further than one screenshot can follow: drag back, measure against [before], and use smaller steps from now on. */
    private fun undo(d:Double,before:FloatArray,offsetBefore:Double) {
        state.step=maxOf(.25,state.step/2)
        screen.drag(-d,false) { ok ->
            if(!ok) return@drag fail("Không vuốt được danh sách")
            screen.later(800) { look { s ->
                val shift=HeroGrid.shift(before,s.profile)
                if(!shift.reliable) { s.done(false); return@look resync() }
                state.offset=offsetBefore+shift.px.toDouble()/s.height; state.ref=s.profile; aim(s,false)
            } }
        }
    }
    /** Measurement lost track: go back to the top and count again. */
    private fun resync() {
        if(++resyncs>1) return fail("Không xác định được vị trí danh sách. Mở danh sách Tất cả rồi bấm Tiếp tục.")
        state.synced=false; state.ref=null; toTop(0)
    }
    /** Flicks to the top, then checks that one more flick no longer moves the list. */
    private fun toTop(round:Int) {
        if(round>2) return fail("Không đưa được danh sách về đầu. Mở danh sách Tất cả rồi bấm Tiếp tục.")
        fun flick(times:Int,then:()->Unit) {
            if(!screen.alive) return
            if(times==0) return then()
            screen.drag(-.70,true) { ok -> if(!ok) fail("Không vuốt được danh sách") else screen.later(350) { flick(times-1,then) } }
        }
        // Check it is the hero list before flicking: on another screen a swipe can change skins.
        look { first -> first.done(false)
            flick(3) { screen.later(1200) { look { a -> a.done(false)
                flick(1) { screen.later(1200) { look { b ->
                    val shift=HeroGrid.shift(a.profile,b.profile)
                    if(shift.reliable&&abs(shift.px)<6) { state.offset=0.0; state.synced=true; state.page=1; state.ref=b.profile; aim(b,true) }
                    else { b.done(false); toTop(round+1) }
                } } }
            } } }
        }
    }
}
