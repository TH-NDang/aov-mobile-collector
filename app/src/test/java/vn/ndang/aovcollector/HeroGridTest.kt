package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Runs [ListNavigator] against a drawn All-heroes screen whose swipes slide on by a random amount. */
class HeroGridTest {
    private class FakeList(val heroes:Int,seed:Int,val slide:ClosedFloatingPointRange<Double>) {
        val w=2400; val h=1080; var offset=0.0
        /** What the game shows: the list, a hero page opened from it, or the lobby, which has no back arrow. */
        enum class Page { LIST, HERO, LOBBY }
        var page=Page.LIST
        val rows=(heroes+4)/5
        val maxOffset=maxOf(0.0,(HeroGrid.ROW0+(rows-1)*HeroGrid.PITCH-.78)*h)
        private val random=Random(seed)
        private val particles=List(400) { random.nextInt(w)*h+random.nextInt(h) }.toSet()
        fun cardAt(x:Int,y:Int):Int? {
            if(y<.12*h) return null
            val cy=y+offset
            for(c in 0 until 5) {
                if(abs(x-HeroGrid.columns[c]*w)>.053*w) continue
                val r=Math.round((cy/h-HeroGrid.ROW0)/HeroGrid.PITCH).toInt()
                if(r<0||r>=rows||abs(cy-(HeroGrid.ROW0+r*HeroGrid.PITCH)*h)>.185*h) return null
                return (r*5+c).takeIf { it<heroes }
            }
            return null
        }
        /** The white "<" at the top left, drawn from the stroke ends measured on the game's screens. */
        private fun arrow(x:Int,y:Int):Boolean {
            if(x>.13*w||y>.11*h) return false
            val tx=.0758*w; val ty=.0546*h; val ex=.1054*w
            return listOf(.0296*h,.0806*h).any { ey -> val dx=ex-tx; val dy=ey-ty
                val t=(((x-tx)*dx+(y-ty)*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0); hypot(x-tx-t*dx,y-ty-t*dy)<.005*h }
        }
        // Static gradient background with particles; cards scroll and some are greyed out like unowned heroes.
        fun readRow(y:Int,row:IntArray) {
            if(page!=Page.LIST) {
                // Hero art and the lobby: detailed everywhere, but not white.
                for(x in 0 until w) { val n=((x*73856093) xor (y*19349663)) ushr 8 and 127; row[x]=if(page==Page.HERO&&arrow(x,y)) -1 else (0xff shl 24) or (n shl 16) or ((n/2) shl 8) or (40+n) }
                return
            }
            for(x in 0 until w) {
                if(arrow(x,y)) { row[x]=-1; continue }
                val i=cardAt(x,y)
                row[x]=if(i==null) { if(x*h+y in particles) 0xffd0c8ff.toInt() else (0xff shl 24) or ((30+20*y/h) shl 16) or (26 shl 8) or (60+30*y/h) }
                else { val n=((x*73856093) xor ((y+offset.toInt())*19349663) xor (i*83492791)) ushr 8 and 63; val base=40+(i*37)%150
                    val v=if(i%7==3) base/4+n/6 else (base+n-32).coerceIn(0,255); (0xff shl 24) or (v shl 16) or (((v+i*11)%256) shl 8) or ((v*2+i)%256) }
            }
        }
        /** The game slides on after the finger lifts and loses a few pixels to its drag threshold. */
        fun drag(d:Double,fling:Boolean) {
            val factor=if(fling) random.nextDouble(3.0,5.0) else random.nextDouble(slide.start,slide.endInclusive)
            offset=(offset+d*h*factor-15*Math.signum(d)).coerceIn(0.0,maxOffset)
        }
    }

    private class Run(heroes:Int,seed:Int,slide:ClosedFloatingPointRange<Double>,startAt:Double=0.0) {
        val list=FakeList(heroes,seed,slide).apply { offset=maxOffset*startAt }
        val state=ListNavigator.State()
        val tapped=ArrayList<Int?>()
        val kept=ArrayList<String>()
        var swipes=0
        var backs=0
        /** Looks after which a hero page that is still closing shows the list by itself. */
        var closingLooks=0
        val screen=object:ListNavigator.Screen {
            override val alive=true
            override fun look(then:(ListNavigator.Shot?)->Unit) {
                val grid=HeroGrid.isGrid(list.w,list.h,list::readRow)
                val profile=if(grid) HeroGrid.profile(list.w,list.h,list::readRow) else FloatArray(0)
                val arrow=!grid&&HeroGrid.hasBackArrow(list.w,list.h,list::readRow)
                if(closingLooks>0&&--closingLooks==0) list.page=FakeList.Page.LIST
                then(object:ListNavigator.Shot {
                    override val profile=profile
                    override val height=list.h
                    override val backArrow=arrow
                    override fun hasCard(x:Double,y:Double)=HeroGrid.hasCard(list.w,list.h,x,y,list::readRow)
                    override fun done(keep:String?) { if(keep!=null) kept+=keep }
                })
            }
            override fun drag(d:Double,fling:Boolean,then:(Boolean)->Unit) { swipes++; if(list.page==FakeList.Page.LIST) list.drag(d,fling); then(true) }
            override fun tap(x:Double,y:Double,then:(Boolean)->Unit) {
                val card=if(list.page==FakeList.Page.LIST) list.cardAt((x*(list.w-1)).toInt(),(y*(list.h-1)).toInt()) else null
                tapped+=card; if(card!=null) list.page=FakeList.Page.HERO; then(true)
            }
            override fun back(then:(Boolean)->Unit) { backs++; list.page=if(list.page==FakeList.Page.HERO) FakeList.Page.LIST else FakeList.Page.LOBBY; then(true) }
            override fun later(ms:Long,then:()->Unit) { then() }
        }
        /**
         * Picks cards in order like a batch run, with the macro's back tap after each hero; the game ignores
         * that tap after the cards in [ignoredBack]. Returns the cards actually tapped and how the run ended.
         */
        fun collect(limit:Int,ignoredBack:Set<Int> = emptySet()):Pair<List<Int?>,String> {
            for(card in 0 until limit) {
                var outcome:ListNavigator.Outcome?=null
                ListNavigator(card,state,screen) { outcome=it }.start()
                when(val o=outcome) {
                    is ListNavigator.Outcome.Tapped -> if(card !in ignoredBack) list.page=FakeList.Page.LIST
                    is ListNavigator.Outcome.End -> return tapped to "end at $card"
                    is ListNavigator.Outcome.Failed -> return tapped to "failed at $card: ${o.reason}"
                    null -> return tapped to "no outcome at $card"
                }
            }
            return tapped to "limit"
        }
    }

    private fun assertCollectsAll(heroes:Int,seed:Int,slide:ClosedFloatingPointRange<Double>,startAt:Double=0.0) {
        val (tapped,end)=Run(heroes,seed,slide,startAt).collect(heroes+3)
        assertEquals("tapped cards",(0 until heroes).toList(),tapped)
        assertEquals("end at $heroes",end)
    }

    @Test fun oddLastRowIsCollectedOnceAndTheRunStopsAtTheEnd() = assertCollectsAll(61,6,1.0..1.3)
    @Test fun fullRosterWithFourCardsInTheLastRow() = assertCollectsAll(129,1,1.0..1.3)
    @Test fun shortListNeedsNoScroll() = assertCollectsAll(7,4,1.0..1.3)
    @Test fun startsMidListByReturningToTheTop() = assertCollectsAll(43,8,1.0..1.3,startAt=.6)
    /** If resting the finger does not stop the slide, swipes go 1.5-2.3 times as far; smaller steps must still keep count. */
    @Test fun keepsCountWhenSwipesSlideFarBeyondTheDrag() = assertCollectsAll(129,3,1.5..2.3)

    @Test fun shiftIsMeasuredBothWaysAndRejectedWithoutOverlap() {
        val list=FakeList(60,9,1.0..1.0); list.offset=500.0; val a=HeroGrid.profile(list.w,list.h,list::readRow)
        list.offset=1250.0; assertEquals(750,HeroGrid.shift(a,HeroGrid.profile(list.w,list.h,list::readRow)).px)
        list.offset=320.0; assertEquals(-180,HeroGrid.shift(a,HeroGrid.profile(list.w,list.h,list::readRow)).px)
        list.offset=2000.0; assertFalse(HeroGrid.shift(a,HeroGrid.profile(list.w,list.h,list::readRow)).reliable)
    }

    /** The run in the report stopped on Edras's page: the game had ignored the macro's back tap. */
    @Test fun returnsToTheListWhenTheGameIgnoredTheBackTap() {
        val run=Run(61,5,1.0..1.3)
        val (tapped,end)=run.collect(64,ignoredBack=setOf(4,17,40))
        assertEquals("tapped cards",(0 until 61).toList(),tapped)
        assertEquals("end at 61",end)
        assertEquals(3,run.backs)
        assertEquals("each screen left with the arrow is kept for the log",listOf("not-list-1","not-list-1","not-list-1"),run.kept.filter { it.startsWith("not-list") })
    }
    @Test fun waitsForAListThatIsStillAppearingInsteadOfTappingBack() {
        val run=Run(7,4,1.0..1.3).apply { list.page=FakeList.Page.HERO; closingLooks=1 }
        assertEquals((0 until 7).toList() to "end at 7",run.collect(10))
        assertEquals(0,run.backs)
    }
    @Test fun doesNotTapWhereTheGameShowsNoBackArrow() {
        val run=Run(20,2,1.0..1.3).apply { list.page=FakeList.Page.LOBBY }
        val (tapped,end)=run.collect(5)
        assertTrue(tapped.isEmpty())
        assertEquals("failed at 0: Màn hình hiện tại không phải danh sách Tất cả tướng. Mở danh sách rồi bấm Tiếp tục.",end)
        assertEquals(0,run.backs); assertEquals(0,run.swipes)
    }
    /**
     * Crops of real 2400x1080 screens, placed at the top left of an otherwise black screen. They are binary
     * PPM files (gzipped), since unit tests compile against android.jar, which has no image decoder.
     */
    @Test fun backArrowIsFoundOnRealScreensOnly() {
        fun arrow(name:String):Boolean {
            val input=DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/screens/$name.ppm.gz")!!))
            fun token()=buildString { while(true) { val c=input.readUnsignedByte().toChar(); if(c.isWhitespace()) { if(isNotEmpty()) break } else append(c) } }
            check(token()=="P6"); val w=token().toInt(); val h=token().toInt(); check(token()=="255")
            val rgb=ByteArray(w*h*3).also { input.readFully(it) }
            return HeroGrid.hasBackArrow(2400,1080) { y,row -> for(x in row.indices) row[x]=if(x<w&&y<h) { val i=(y*w+x)*3
                (0xff shl 24) or ((rgb[i].toInt() and 255) shl 16) or ((rgb[i+1].toInt() and 255) shl 8) or (rgb[i+2].toInt() and 255) } else 0xff000000.toInt() }
        }
        for(name in listOf("back-arrow-list","back-arrow-skill-panel","back-arrow-edras")) assertTrue(name,arrow(name))
        for(name in listOf("no-arrow-hair","no-arrow-currency","no-arrow-card")) assertFalse(name,arrow(name))
    }

    @Test fun anotherScreenIsNotTakenForTheList() {
        val busy=IntArray(2400) { x -> (0xff shl 24) or (((x*7919) and 255) shl 8) }
        assertFalse(HeroGrid.isGrid(2400,1080) { _,row -> busy.copyInto(row) })
        val list=FakeList(20,2,1.0..1.0); assertTrue(HeroGrid.isGrid(list.w,list.h,list::readRow))
    }
}
