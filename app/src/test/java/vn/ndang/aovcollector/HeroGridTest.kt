package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Runs [ListNavigator] against a drawn All-heroes screen whose swipes slide on by a random amount. */
class HeroGridTest {
    private class FakeList(val heroes:Int,seed:Int,val slide:ClosedFloatingPointRange<Double>) {
        val w=2400; val h=1080; var offset=0.0
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
        // Static gradient background with particles; cards scroll and some are greyed out like unowned heroes.
        fun readRow(y:Int,row:IntArray) {
            for(x in 0 until w) {
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
        var swipes=0
        val screen=object:ListNavigator.Screen {
            override val alive=true
            override fun look(then:(ListNavigator.Shot?)->Unit) {
                val grid=HeroGrid.isGrid(list.w,list.h,list::readRow)
                val profile=if(grid) HeroGrid.profile(list.w,list.h,list::readRow) else FloatArray(0)
                then(object:ListNavigator.Shot {
                    override val profile=profile
                    override val height=list.h
                    override fun hasCard(x:Double,y:Double)=HeroGrid.hasCard(list.w,list.h,x,y,list::readRow)
                    override fun done(keep:Boolean) {}
                })
            }
            override fun drag(d:Double,fling:Boolean,then:(Boolean)->Unit) { swipes++; list.drag(d,fling); then(true) }
            override fun tap(x:Double,y:Double,then:(Boolean)->Unit) { tapped+=list.cardAt((x*(list.w-1)).toInt(),(y*(list.h-1)).toInt()); then(true) }
            override fun later(ms:Long,then:()->Unit) { then() }
        }
        /** Picks cards in order like a batch run; returns the cards actually tapped and how the run ended. */
        fun collect(limit:Int):Pair<List<Int?>,String> {
            for(card in 0 until limit) {
                var outcome:ListNavigator.Outcome?=null
                ListNavigator(card,state,screen) { outcome=it }.start()
                when(val o=outcome) {
                    is ListNavigator.Outcome.Tapped -> {}
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

    @Test fun anotherScreenIsNotTakenForTheList() {
        val busy=IntArray(2400) { x -> (0xff shl 24) or (((x*7919) and 255) shl 8) }
        assertFalse(HeroGrid.isGrid(2400,1080) { _,row -> busy.copyInto(row) })
        val list=FakeList(20,2,1.0..1.0); assertTrue(HeroGrid.isGrid(list.w,list.h,list::readRow))
    }
}
