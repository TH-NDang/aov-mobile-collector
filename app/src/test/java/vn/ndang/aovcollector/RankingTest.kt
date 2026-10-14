package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ndang.aovcollector.RankScreens.Mode
import vn.ndang.aovcollector.RankScreens.Page
import vn.ndang.aovcollector.RankScreens.Table
import java.io.InputStream
import kotlin.math.abs

class RankingTest {
    /** Screenshots from the user's phone (2400x1080), the UID line and other players' chat blanked. */
    private fun pixels(name:String):Triple<Int,Int,IntArray> {
        System.setProperty("java.awt.headless","true")
        val image=javaClass.getResourceAsStream("/screens/rank-$name.jpg")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream::class.java).invoke(null,it)!! }
        val type=image.javaClass
        val w=type.getMethod("getWidth").invoke(image) as Int; val h=type.getMethod("getHeight").invoke(image) as Int
        val px=type.getMethod("getRGB",Int::class.java,Int::class.java,Int::class.java,Int::class.java,IntArray::class.java,Int::class.java,Int::class.java).invoke(image,0,0,w,h,null,0,w) as IntArray
        return Triple(w,h,px)
    }
    private fun rows(p:Triple<Int,Int,IntArray>)={ y:Int,row:IntArray -> System.arraycopy(p.third,y*p.first,row,0,p.first) }

    @Test fun theLobbyIsKnownBeforeTappingTheButtonBesideTúiĐồ() {
        // The live game, and a test build whose button shows another icon in the same place.
        for(name in listOf("lobby","lobby-rd")) { val p=pixels(name); assertEquals(name,GameScreens.Kind.LOBBY,GameScreens.read(p.first,p.second,rows(p)).kind) }
        val table=pixels("ranking-rates"); assertTrue(GameScreens.read(table.first,table.second,rows(table)).kind!=GameScreens.Kind.LOBBY)
    }
    @Test fun aScrollThatMovesNothingIsTold() {
        val rates=pixels("ranking-rates"); val figures=pixels("ranking-figures")
        val a=RankScreens.signature(rates.first,rates.second,rows(rates))
        assertTrue(RankScreens.same(a,RankScreens.signature(rates.first,rates.second,rows(rates))))
        assertFalse(RankScreens.same(a,RankScreens.signature(figures.first,figures.second,rows(figures))))
        // The same table one row further down.
        val shifted=Triple(rates.first,rates.second,IntArray(rates.third.size) { val y=it/rates.first+133; if(y<rates.second) rates.third[y*rates.first+it%rates.first] else 0 })
        assertFalse(RankScreens.same(a,RankScreens.signature(shifted.first,shifted.second,rows(shifted))))
    }
    private fun w(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.05,y-.02,x+.05,y+.02)
    @Test fun readsTheTableAndTheKindOfMatchFromTheirWords() {
        // Words as ML Kit reads them on the user's screenshots, accents dropped here and there.
        val rates=listOf(w("Bảng Phân Bậc Tướng",.22,.077),w("Dau hang (Tat ca)",.57,.2),w("Đổi bảng xếp hạng",.835,.2),w("Tỷ lệ thắng",.45,.32),w("Tỷ lệ pick",.56,.32))
        assertEquals(Page.RANKING,RankScreens.page(rates,false)); assertEquals(Table.RATES,RankScreens.table(rates))
        assertEquals(Mode.ALL,RankScreens.mode(RankScreens.modeButton(rates)))
        val figures=listOf(w("Đấu 5v5",.57,.2),w("Sát thương",.48,.29),w("mỗi trận",.48,.33),w("Vàng",.69,.32))
        assertEquals(Table.FIGURES,RankScreens.table(figures)); assertEquals(Mode.FIVE,RankScreens.mode(RankScreens.modeButton(figures)))
        val open=rates+listOf(w("Đấu hạng (Tất cả)",.6,.313),w("Đấu 5v5",.55,.378),w("Đấu hạng (Tinh Anh trở lên)",.6,.444))
        val button=RankScreens.modeButton(open)!!; assertEquals(.2,button.y,.001)
        assertEquals(.444,RankScreens.option(open,button,Mode.ELITE)!!.y,.001)
        assertEquals(Page.HEROES,RankScreens.page(listOf(w("Bảng xếp hạng đầy đủ >",.485,.082)),false))
        assertEquals(Page.COMMUNITY,RankScreens.page(listOf(w("LIÊNQUÂN",.083,.9)),false))
        assertEquals(Page.PANEL,RankScreens.page(listOf(w("Tính năng khác",.12,.21),w("Cộng đồng",.15,.52)),false))
        assertEquals(Page.LOBBY,RankScreens.page(emptyList(),true))
    }

    /**
     * The game from the lobby to the table: taps move between pages, the table scrolls by [length] screens and
     * keeps its headings (and the switches above) only at the top unless [sticky].
     */
    private class Web(var page:Page,var table:Table=Table.RATES,var mode:Mode=Mode.ALL,val length:Double=1.0,val sticky:Boolean=false,val cut:Int=Int.MAX_VALUE) : RankingReader.Screen {
        var offset=0.0; var open=false; var looks=0
        val kept=ArrayList<String>(); val tapped=ArrayList<String>(); val finished=ArrayList<String>(); val pending=ArrayDeque<()->Unit>()
        var outcome:RankingReader.Outcome?=null
        override val alive get()=outcome==null&&looks<cut
        private fun w(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.05,y-.02,x+.05,y+.02)
        fun words():List<GameScreens.Word> = when(page) {
            Page.PANEL -> listOf(w("Tính năng khác",.12,.21),w("Hiệu ứng",.15,.37),w("Clan",.32,.37),w("Cộng đồng",.15,.52))
            Page.COMMUNITY -> listOf(w("MIC CHECK #6",.3,.09),w("LIÊNQUÂN",.083,.9))
            Page.HEROES -> listOf(w("Bảng Phân Bậc Tướng",.25,.08),w("Bảng xếp hạng đầy đủ >",.485,.082),w("LIÊNQUÂN",.083,.9))
            Page.RANKING -> buildList {
                if(offset==0.0||sticky) {
                    add(w("Bảng Phân Bậc Tướng",.22,.077)); add(w("Đổi bảng xếp hạng",.835,.2)); add(w(mode.label,.57,.2))
                    if(table==Table.RATES) { add(w("Tỷ lệ thắng",.45,.32)); add(w("Tỷ lệ pick",.56,.32)) } else { add(w("Sát thương",.48,.29)); add(w("Vàng",.69,.32)) }
                    if(open) Mode.values().forEachIndexed { i,m -> add(w(m.label,.6,.313+.065*i)) }
                }
                add(w("Omega",.13,.41))
            }
            else -> emptyList()
        }
        override fun look(words:Boolean,then:(RankingReader.Look?)->Unit) {
            looks++
            val shown=if(words) words() else emptyList()
            val signature=IntArray(8) { (offset*1000).toInt()+table.ordinal*5000+mode.ordinal*20000 }
            then(object:RankingReader.Look { override val words=shown; override val lobby=page==Page.LOBBY; override val signature=signature
                override fun done(keep:String?) { if(keep!=null) kept+=keep } })
        }
        override fun tap(x:Double,y:Double,then:(Boolean)->Unit) {
            val hit=words().firstOrNull { abs(it.x-x)<.001&&abs(it.y-y)<.001 }
            tapped+=hit?.text?:"%.3f,%.3f".format(x,y)
            when(page) {
                Page.LOBBY -> if(x==RankScreens.MORE_X&&y==RankScreens.MORE_Y) page=Page.PANEL
                Page.PANEL -> if(hit?.plain=="congdong") page=Page.COMMUNITY
                Page.COMMUNITY -> if(abs(y-RankScreens.SIDE_Y)<.001) page=Page.HEROES
                Page.HEROES -> if(hit?.plain?.contains("bangxephangdaydu")==true) page=Page.RANKING
                Page.RANKING -> when {
                    hit==null -> Unit
                    hit.plain.contains("doibangxephang") -> table=if(table==Table.RATES) Table.FIGURES else Table.RATES
                    hit.y>.25&&open -> { mode=Mode.values().first { hit.plain.contains(it.key) }; open=false }
                    hit.plain.startsWith("dau") -> open=!open
                }
                else -> Unit
            }
            pending.addLast { then(true) }
        }
        override fun drag(d:Double,fling:Boolean,then:(Boolean)->Unit) {
            if(page==Page.RANKING) offset=if(d<0) maxOf(0.0,offset-3) else minOf(length,offset+d)
            pending.addLast { then(true) }
        }
        override fun later(ms:Long,then:()->Unit) { pending.addLast(then) }
        override fun finished(combo:String) { finished+=combo }
        fun run(skip:Set<String> =emptySet()):Web { RankingReader(this,skip) { outcome=it }.start(); var guard=0
            while(outcome==null&&looks<cut) { (pending.removeFirstOrNull()?:break).invoke(); check(++guard<100000) }; return this }
    }

    @Test fun goesFromTheLobbyToTheTableAndPhotographsBothTablesForEachKindOfMatch() {
        val web=Web(Page.LOBBY).run()
        assertEquals(24,(web.outcome as RankingReader.Outcome.Done).pages)
        assertEquals(listOf("0.084,0.890","Cộng đồng","0.083,0.348","Bảng xếp hạng đầy đủ >"),web.tapped.take(4))
        // Four photos per table: scrolled .4 of a screen at a time down a list one screen longer than the screen.
        val order=listOf("ty-le-dau-hang-tat-ca","ty-le-dau-5v5","ty-le-dau-hang-tinh-anh","chi-so-dau-hang-tat-ca","chi-so-dau-5v5","chi-so-dau-hang-tinh-anh")
        assertEquals(order,web.finished)
        assertEquals(order.flatMap { c -> (1..4).map { "$c-%02d".format(it) } },web.kept)
        assertEquals(1,web.tapped.count { it=="Đổi bảng xếp hạng" })
    }
    @Test fun startsWithTheTableShownAndSkipsWhatAnEarlierTryFinished() {
        val web=Web(Page.RANKING,Table.FIGURES,Mode.FIVE,length=.5).run(skip=setOf("ty-le-dau-5v5","ty-le-dau-hang-tat-ca"))
        assertEquals("chi-so-dau-5v5",web.finished.first())
        assertEquals(4,web.finished.size); assertTrue(web.kept.none { it.startsWith("ty-le-dau-5v5")||it.startsWith("ty-le-dau-hang-tat-ca") })
        assertEquals(listOf("chi-so-dau-5v5-01","chi-so-dau-5v5-02"),web.kept.take(2))
    }
    @Test fun keepsHeadingsThatStayOnScreen() {
        // A table whose headings stay while the rows scroll under them.
        val web=Web(Page.RANKING,length=.8,sticky=true).run()
        assertEquals(6,web.finished.size); assertEquals(18,(web.outcome as RankingReader.Outcome.Done).pages)
    }
    @Test fun stopsOnAScreenItDoesNotKnow() {
        val web=Web(Page.OTHER).run()
        assertTrue(web.outcome is RankingReader.Outcome.Failed); assertEquals(listOf("ranking-unknown"),web.kept)
        assertTrue(web.tapped.isEmpty())
    }
    @Test fun stopsWhenThePauseCutsIt() {
        val web=Web(Page.RANKING,cut=6).run()
        assertNull(web.outcome); assertTrue(web.kept.size<=4)
    }
}
