package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Runs the list navigation against a drawn All-heroes screen whose swipes overshoot at random. */
class HeroGridTest {
    private class FakeList(val heroes:Int,seed:Int) {
        val w=2400; val h=1080; var offset=0.0
        val rows=(heroes+4)/5
        val maxOffset=maxOf(0.0,(HeroGrid.ROW0+(rows-1)*HeroGrid.PITCH-.78)*h)
        private val particles=Random(seed).let { r -> List(400) { r.nextInt(w)*h+r.nextInt(h) }.toSet() }
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
        fun profile():FloatArray { assertTrue(HeroGrid.isGrid(w,h,::readRow)); return HeroGrid.profile(w,h,::readRow) }
        fun hasCard(x:Double,y:Double)=HeroGrid.hasCard(w,h,x,y,::readRow)
        fun tapped(x:Double,y:Double)=cardAt((x*(w-1)).toInt(),(y*(h-1)).toInt())
        /** The game slides further than asked and loses a few pixels to its drag threshold. */
        fun swipe(d:Double,r:Random) { offset=(offset+d*h*(1+r.nextDouble(0.0,.3))-15*Math.signum(d)).coerceIn(0.0,maxOffset) }
    }

    /** Mirrors CollectorService.Picker: measure, plan, swipe, and stop when the list stops moving. */
    private fun collectAll(heroes:Int,seed:Int):List<Int> {
        val list=FakeList(heroes,seed); val r=Random(seed)
        var offset=0.0; var ref=list.profile(); val tapped=ArrayList<Int>()
        for(card in 0 until heroes+2) {
            val now=list.profile(); val drift=HeroGrid.shift(ref,now); assertTrue(drift.reliable); offset+=drift.px.toDouble()/list.h; ref=now
            var stalls=0; var outcome:String?=null
            while(outcome==null) when(val plan=HeroGrid.plan(card,offset)) {
                is HeroGrid.Plan.Tap -> outcome=if(list.hasCard(plan.x,plan.y)) { assertEquals(card,list.tapped(plan.x,plan.y)); tapped+=card; "tap" } else "end"
                is HeroGrid.Plan.Move -> {
                    val before=ref; list.swipe(plan.d,r); val p=list.profile(); val s=HeroGrid.shift(before,p)
                    assertTrue("swipe of ${plan.d} could not be measured",s.reliable)
                    val moved=s.px.toDouble()/list.h; offset+=moved; ref=p
                    if(abs(moved)>=.01) stalls=0
                    else if(++stalls>=2) { val (x,y)=HeroGrid.centre(card,offset)
                        outcome=if(plan.d>0&&y<HeroGrid.LAST_ROW_LIMIT&&list.hasCard(x,y)) { assertEquals(card,list.tapped(x,y)); tapped+=card; "tap" } else "end" }
                }
            }
            if(outcome=="end") { assertEquals("list ended at the wrong card",heroes,card); break }
        }
        return tapped
    }

    @Test fun oddLastRowIsCollectedOnceAndTheRunStopsAtTheEnd() { assertEquals((0 until 61).toList(),collectAll(61,6)) }
    @Test fun fullRosterWithFourCardsInTheLastRow() { assertEquals((0 until 129).toList(),collectAll(129,1)) }
    @Test fun shortListNeedsNoScroll() { assertEquals((0 until 7).toList(),collectAll(7,4)) }

    @Test fun shiftIsMeasuredBothWaysAndRejectedWithoutOverlap() {
        val list=FakeList(60,9); list.offset=500.0; val a=list.profile()
        list.offset=880.0; assertEquals(380,HeroGrid.shift(a,list.profile()).px)
        list.offset=320.0; assertEquals(-180,HeroGrid.shift(a,list.profile()).px)
        list.offset=2000.0; assertFalse(HeroGrid.shift(a,list.profile()).reliable)
    }
}
