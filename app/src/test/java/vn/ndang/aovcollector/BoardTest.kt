package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ndang.aovcollector.BoardScreens.Page
import vn.ndang.aovcollector.BoardScreens.Tab
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.floor

class BoardTest {
    /** Screenshots from the user's phone (2400x1080), the UID line and players' names and pictures blurred. */
    private fun pixels(name:String):Triple<Int,Int,IntArray> {
        System.setProperty("java.awt.headless","true")
        val image=javaClass.getResourceAsStream("/screens/board-$name.jpg")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream::class.java).invoke(null,it)!! }
        val type=image.javaClass
        val w=type.getMethod("getWidth").invoke(image) as Int; val h=type.getMethod("getHeight").invoke(image) as Int
        val px=type.getMethod("getRGB",Int::class.java,Int::class.java,Int::class.java,Int::class.java,IntArray::class.java,Int::class.java,Int::class.java).invoke(image,0,0,w,h,null,0,w) as IntArray
        return Triple(w,h,px)
    }
    private fun rows(p:Triple<Int,Int,IntArray>)={ y:Int,row:IntArray -> System.arraycopy(p.third,y*p.first,row,0,p.first) }

    @Test fun findsTheRowsOfTheHeroGrid() {
        val top=pixels("picker"); val bands=BoardScreens.rows(top.first,top.second,rows(top))
        // Three whole rows, 0.1768 apart; the sliver of a fourth at the window's bottom is too thin to count.
        assertEquals(.3426,bands[0].first,.004); assertEquals(.5194,bands[1].first,.004); assertEquals(.6963,bands[2].first,.004)
        assertTrue(bands.take(3).all { abs(it.second-it.first-BoardScreens.CARD)<.005 })
        assertEquals(3,bands.size)
        for(col in 0 until 8) assertTrue("column $col",BoardScreens.card(top.first,top.second,col,bands[0].first,bands[0].second,rows(top)))
        // Scrolled a little: the same rows 0.047 higher.
        val scrolled=pixels("picker-scrolled"); val moved=BoardScreens.rows(scrolled.first,scrolled.second,rows(scrolled))
        assertEquals(.2954,moved[0].first,.004); assertEquals(.4704,moved[1].first,.004)
    }
    @Test fun theRankedPageIsNeverTakenForTheLobby() {
        // Its own "Đấu hạng" sits where the lobby's is: a tap meant for the lobby there would start a match.
        for(name in listOf("ranked","ranked-new")) { val p=pixels(name); assertFalse(name,GameScreens.isLobby(p.first,p.second,rows(p))) }
        assertEquals(Page.RANKED,BoardScreens.page(ranked,true))
        assertEquals(Page.OTHER,BoardScreens.page(listOf(w("ĐẤU HẠNG",.85,.9)),true))
    }
    @Test fun aListScrolledByARowIsTold() {
        val p=pixels("list"); val a=BoardScreens.signature(p.first,p.second,rows(p))
        assertTrue(BoardScreens.same(a,BoardScreens.signature(p.first,p.second,rows(p))))
        val shifted=Triple(p.first,p.second,IntArray(p.third.size) { val y=it/p.first+165; if(y<p.second) p.third[y*p.first+it%p.first] else 0 })
        assertFalse(BoardScreens.same(a,BoardScreens.signature(shifted.first,shifted.second,rows(shifted))))
    }

    private fun w(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.04,y-.015,x+.04,y+.015)
    private val ranked=listOf(w("Thời hạn mùa giải",.2,.141),w("Đấu đỉnh cao",.16,.26),w("Chiến khu",.15,.56),w("Đấu hạng",.795,.867))

    @Test fun readsTheRegionDialog() {
        val dialog=listOf(w("Vào chiến khu",.33,.191),w("Chưa chỉnh",.41,.789),w("Xác nhận",.587,.789))
        val start=BoardScreens.region(dialog+w("Quốc gia",.344,.486))
        assertEquals("quocgia",start.boxes[0]); assertNull(start.open)
        val area=BoardScreens.region(dialog+listOf(w("Việt Nam",.344,.486),w("Khu vực",.49,.486),w("Miền Bắc",.5,.544),w("Miền Trung",.5,.6),w("Miền Nam",.5,.656),w("Khác",.5,.711)))
        assertEquals(1,area.open); assertEquals(listOf("mienbac","mientrung","miennam","khac"),area.options.map { it.plain })
        assertEquals(.587,area.confirm!!.x,.001)
    }

    /**
     * The game from the lobby to the ranking: [seasons] review screens on the way, the region dialog when [regionSet]
     * is false, [heroes] cards in "Chọn tướng", lists [length] screens long.
     */
    private class Game(var page:Page=Page.LOBBY,val heroes:Int=10,val seasons:Boolean=false,var regionSet:Boolean=true,val length:Double=1.0,
                       var gridScroll:Double=0.0,val cut:Int=Int.MAX_VALUE) : BoardReader.Screen {
        var looks=0; var outcome:BoardReader.Outcome?=null
        val kept=ArrayList<String>(); val taps=ArrayList<Pair<Double,Double>>(); val chosen=ArrayList<Int>(); val finished=ArrayList<Int>(); val names=HashMap<Int,String?>()
        val pending=ArrayDeque<()->Unit>()
        override val alive get()=outcome==null&&looks<cut
        // The season's review: its screens in turn, the trailer showing no words.
        var season=if(seasons) listOf("Ấn mở l.sử mùa" to (.5 to .92),"Bước kế" to (.87 to .913),"Bước kế" to (.87 to .913),"Mở mùa mới" to (.5 to .855),
            "Ấn màn hình để tiếp tục" to (.5 to .936),"" to (0.0 to 0.0),"" to (0.0 to 0.0),"Ấn màn hình để tiếp tục" to (.5 to .8),"Ấn màn hình để tiếp tục" to (.5 to .95)) else emptyList()
        var seasonAt=0
        // The region dialog.
        var country:String?=null; var area:String?=null; var province:String?=null; var open=-1; var provinceScroll=0
        val provinces=listOf("Thanh Hóa","Nghệ An","Hà Tĩnh","Quảng Trị","Đà Nẵng","Quảng Ngãi","Khánh Hòa","Gia Lai","Đắk Lắk","Lâm Đồng")
        // The ranking.
        var hero=0; var tab=0; var offset=0.0
        private fun w(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.04,y-.015,x+.04,y+.015)
        private val rowZero=.3426
        private val rowCount get()=(heroes+7)/8
        private val maxScroll get()=maxOf(0.0,rowZero+(rowCount-1)*BoardScreens.ROW_PITCH+BoardScreens.CARD+.02-BoardScreens.VIEW_BOTTOM)
        fun words():List<GameScreens.Word> = when(page) {
            Page.LOBBY -> listOf(w("Túi đồ",.15,.92),w("Tướng",.27,.92),w("ĐẤU HẠNG",.85,.9))
            Page.RANKED -> listOf(w("Thời hạn mùa giải",.2,.141),w("Đấu đỉnh cao",.16,.26),w("Chiến khu",.15,.464),w("Đấu hạng",.795,.867))
            Page.SEASON -> season[seasonAt].let { (t,at) -> if(t.isEmpty()) emptyList() else listOf(w(t,at.first,at.second)) }
            Page.REGION -> buildList {
                add(w("Vào chiến khu",.33,.191)); add(w("Chưa chỉnh",.41,.789)); add(w("Xác nhận",.587,.789))
                add(w(country?:"Quốc gia",.344,.486)); if(country!=null) add(w(area?:"Khu vực",.49,.486)); if(area!=null) add(w(province?:"Khu vực",.64,.486))
                when(open) {
                    0 -> add(w("Việt Nam",.352,.544))
                    1 -> listOf("Miền Bắc","Miền Trung","Miền Nam","Khác").forEachIndexed { i,t -> add(w(t,.5,.544+.0556*i)) }
                    2 -> provinces.drop(provinceScroll).take(7).forEachIndexed { i,t -> add(w(t,.648,.544+.0556*i)) }
                }
            }
            Page.BOARD -> listOf(w("Chiến khu",.17,.057),w("Đắk Lắk",.43,.146),w("Miền Trung",.53,.146),w("Việt Nam",.645,.146),w("Toàn máy chủ",.75,.146),
                w("Hero$hero",.1575,.569),w("L.sử lực chiến cao nhất",.158,.667))
            Page.PICKER -> listOf(w("Chọn tướng",.2,.117),w("Tất cả",.215,.227),w("Đã nhận",.78,.227))
            Page.TOP -> listOf(w("Số 1 máy chủ",.5,.08),w("Top chiến lực",.5,.157),w("Miền Trung",.27,.163),w("Hero$hero",.5,.74),w("Đổi tướng",.5,.853))
            else -> emptyList()
        }
        private fun bands():List<Pair<Double,Double>> = (0 until rowCount).mapNotNull { r ->
            val top=rowZero+r*BoardScreens.ROW_PITCH-gridScroll; val bottom=top+BoardScreens.CARD
            if(bottom<=BoardScreens.VIEW_TOP||top>=BoardScreens.VIEW_BOTTOM) null else maxOf(top,BoardScreens.VIEW_TOP) to minOf(bottom,BoardScreens.VIEW_BOTTOM) }
        private fun rowAt(y:Double)=floor((y-rowZero+gridScroll)/BoardScreens.ROW_PITCH).toInt()
        override fun look(words:Boolean,grid:Boolean,then:(BoardReader.Look?)->Unit) {
            looks++
            val shown=if(words) words() else emptyList(); val bands=if(page==Page.PICKER) bands() else emptyList()
            val signature=IntArray(8) { (hero*100000+tab*10000+(offset*1000).toInt()) }
            then(object:BoardReader.Look {
                override val words=shown; override val lobby=page==Page.LOBBY; override val signature=signature; override val rows=bands
                override fun card(col:Int,top:Double,bottom:Double)=rowAt((top+bottom)/2)*8+col<heroes
                override fun done(keep:String?) { if(keep!=null) kept+=keep } })
        }
        override fun tap(x:Double,y:Double,then:(Boolean)->Unit) {
            taps+=x to y
            val hit=words().firstOrNull { abs(it.x-x)<.01&&abs(it.y-y)<.01 }
            when(page) {
                Page.LOBBY -> if(hit?.plain=="dauhang") page=if(seasons) Page.SEASON else Page.RANKED
                Page.SEASON -> if(hit!=null) { seasonAt++; if(seasonAt>=season.size) page=Page.RANKED }
                Page.RANKED -> if(hit?.plain=="chienkhu") page=if(regionSet) Page.BOARD else Page.REGION
                Page.REGION -> {
                    val col=BoardScreens.REGION_X.indices.firstOrNull { abs(BoardScreens.REGION_X[it]-x)<.02 }
                    when {
                        hit!=null&&abs(hit.y-BoardScreens.REGION_Y)>.03&&hit.plain!="xacnhan"&&hit.plain!="chuachinh" -> { when(open) { 0 -> country=hit.text; 1 -> area=hit.text; 2 -> province=hit.text }; open=-1 }
                        hit?.plain=="xacnhan" -> if(province!=null) { regionSet=true; page=Page.BOARD }
                        col!=null&&abs(y-BoardScreens.REGION_Y)<.01 -> open=col
                    }
                }
                Page.BOARD -> when {
                    abs(x-BoardScreens.SWAP_X)<.01&&abs(y-BoardScreens.SWAP_Y)<.01 -> page=Page.PICKER
                    abs(y-BoardScreens.TAB_Y)<.01 -> { tab=Tab.values().indexOfFirst { abs(it.x-x)<.01 }; offset=0.0 }
                    abs(x-Tab.TOP.x)<.01&&abs(y-Tab.TOP.y)<.01 -> page=Page.TOP
                }
                // Its own "Đổi tướng" must never be tapped: the hero is chosen on the ranking.
                Page.TOP -> { check(abs(y-.853)>.03) { "Đổi tướng tapped" }; if(abs(x-BoardScreens.TOP_BACK_X)<.01&&abs(y-BoardScreens.TOP_BACK_Y)<.01) page=Page.BOARD }
                Page.PICKER -> when {
                    abs(x-BoardScreens.PICKER_CLOSE_X)<.01&&abs(y-BoardScreens.PICKER_CLOSE_Y)<.01 -> page=Page.BOARD
                    y>BoardScreens.VIEW_TOP&&y<BoardScreens.VIEW_BOTTOM -> { val col=BoardScreens.COLUMNS.indexOfFirst { abs(it-x)<.01 }; val i=rowAt(y)*8+col
                        if(col>=0&&i<heroes) { hero=i; chosen+=i; page=Page.BOARD; tab=0; offset=0.0 } }
                }
                else -> Unit
            }
            pending.addLast { then(true) }
        }
        override fun drag(x:Double,from:Double,to:Double,fling:Boolean,then:(Boolean)->Unit) {
            when(page) {
                Page.PICKER -> gridScroll=if(fling&&to>from) 0.0 else (gridScroll+(from-to)).coerceIn(0.0,maxScroll)
                Page.BOARD -> offset=minOf(length,offset+(from-to))
                Page.REGION -> if(open==2) provinceScroll=minOf(provinces.size-7,provinceScroll+3)
                else -> Unit
            }
            pending.addLast { then(true) }
        }
        override fun later(ms:Long,then:()->Unit) { if(page==Page.SEASON&&season[seasonAt].first.isEmpty()) seasonAt++; pending.addLast(then) }
        override fun hero(index:Int,name:String?) { names[index]=name }
        override fun finished(index:Int) { finished+=index }
        fun run(heroesWanted:Int=300,wanted:List<BoardScreens.Want> =Tab.values().map { BoardScreens.Want(it,3) },first:Int=0):Game {
            BoardReader(this,heroesWanted,wanted,first) { outcome=it }.start(); var guard=0
            while(outcome==null&&looks<cut) { (pending.removeFirstOrNull()?:break).invoke(); check(++guard<200000) }; return this
        }
    }

    @Test fun goesFromTheLobbyThroughTheSeasonAndTheRegionToEveryHerosRanking() {
        // Top five: two screens a tab.
        val game=Game(seasons=true,regionSet=false,heroes=10).run(wanted=Tab.values().map { BoardScreens.Want(it,5) })
        assertEquals(10,(game.outcome as BoardReader.Outcome.Done).heroes)
        assertEquals((0 until 10).toList(),game.chosen); assertEquals((0 until 10).toList(),game.finished)
        // Every tab of every hero, two screens each, and its "Số 1 máy chủ".
        assertEquals((1..10).flatMap { h -> Tab.values().flatMap { t -> (1..(if(t==Tab.TOP) 1 else 2)).map { "hero-%03d-%s-%02d".format(h,t.slug,it) } } },game.kept)
        assertEquals("Hero3",game.names[3])
        // Việt Nam, Miền Trung and Đắk Lắk, found after scrolling the provinces.
        assertEquals(listOf("Việt Nam","Miền Trung","Đắk Lắk"),listOf(game.country,game.area,game.province))
        // Never the ranked page's "Đấu hạng", which starts a match, nor "Chưa chỉnh".
        assertTrue(game.taps.none { abs(it.first-.795)<.03&&abs(it.second-.867)<.03 })
        assertTrue(game.taps.none { abs(it.first-.41)<.02&&abs(it.second-.789)<.02 })
    }
    @Test fun scrollsTheGridToEachHeroInTurn() {
        // 30 heroes in four rows, the grid left scrolled part way by an earlier visit.
        val game=Game(page=Page.BOARD,heroes=30,gridScroll=.2).run(wanted=listOf(BoardScreens.Want(Tab.SERVER,3)))
        assertEquals((0 until 30).toList(),game.chosen)
        assertEquals(30,game.kept.size); assertEquals("hero-030-may-chu-01",game.kept.last())
    }
    @Test fun stopsAtTheHeroCountAskedAndResumesAfterTheLastDone() {
        assertEquals(listOf(0,1),Game(page=Page.BOARD,heroes=30).run(heroesWanted=2,wanted=listOf(BoardScreens.Want(Tab.PROVINCE,3))).chosen)
        val resumed=Game(page=Page.RANKED,heroes=30).run(heroesWanted=12,wanted=listOf(BoardScreens.Want(Tab.PROVINCE,3)),first=9)
        assertEquals(listOf(9,10,11),resumed.chosen)
    }
    @Test fun scrollsEachListToItsEnd() {
        // 999 pages asked: down to the end, 1.0 screen below the first at .36 a scroll.
        val game=Game(page=Page.BOARD,heroes=1,length=1.0).run(wanted=listOf(BoardScreens.Want(Tab.COUNTRY,BoardScreens.ALL)))
        assertEquals(listOf("hero-001-quoc-gia-01","hero-001-quoc-gia-02","hero-001-quoc-gia-03","hero-001-quoc-gia-04"),game.kept)
    }
    @Test fun stopsWhenThePauseCutsIt() {
        val game=Game(page=Page.BOARD,heroes=30,cut=12).run()
        assertNull(game.outcome); assertTrue(game.chosen.size<=2)
    }
    @Test fun takesVietNamsTopThreeAndTheServersTopTenWhenAsked() {
        // By default: Việt Nam's first screen (four players) and "Số 1 máy chủ".
        val game=Game(page=Page.BOARD,heroes=2,length=3.0).run(wanted=BoardScreens.DEFAULT)
        assertEquals(listOf("hero-001-quoc-gia-01","hero-001-so-1-may-chu-01","hero-002-quoc-gia-01","hero-002-so-1-may-chu-01"),game.kept)
        // The whole server's top ten: four screens, the list scrolled three times.
        val server=Game(page=Page.BOARD,heroes=1,length=3.0).run(wanted=listOf(BoardScreens.Want(Tab.SERVER,10)))
        assertEquals((1..4).map { "hero-001-may-chu-%02d".format(it) },server.kept)
        assertEquals(listOf(1,1,2,4),listOf(3,4,5,10).map { BoardScreens.Want(Tab.SERVER,it).pages })
    }
}
