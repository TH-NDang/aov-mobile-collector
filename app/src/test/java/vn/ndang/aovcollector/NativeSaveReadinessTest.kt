package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test
import java.util.PriorityQueue

class NativeSaveReadinessTest {
    private data class Evidence(val words:List<GameScreens.Word>,val scene:NativeSaveEvidence.Scene)
    private fun evidence(text:String,scene:NativeSaveEvidence.Scene)=Evidence(
        listOf(GameScreens.Word(text,.3,.5,.4,.6)),scene)
    private val prompt=evidence("Save Photo Sure Cancel",NativeSaveEvidence.Scene.PROMPT)
    private val page=evidence("Bách khoa Cờ Liên Quân",NativeSaveEvidence.Scene.PAGE)
    private val unknown=evidence("",NativeSaveEvidence.Scene.UNKNOWN)

    /** Includes production screenshot throttling, overlay settling and single-worker OCR latency. */
    private class Clock(val ocrMs:Long=150) {
        private data class Event(val time:Long,val order:Int,val action:()->Unit)
        private val queue=PriorityQueue<Event>(compareBy<Event> { it.time }.thenBy { it.order })
        private var order=0
        var now=0L
        var lastShot=-1050L
        var active=true
        var reads=0
        var inFlight=0
        var maxInFlight=0
        val requestedAt=ArrayList<Long>()
        val waits=ArrayList<Long>()
        fun post(ms:Long,action:()->Unit) { queue.add(Event(now+ms,order++,action)) }
        fun later(ms:Long,action:()->Unit) { waits.add(ms);post(ms,action) }
        fun read(value:Evidence?,done:(Evidence?)->Unit) {
            reads++;requestedAt.add(now);inFlight++;maxInFlight=maxOf(maxInFlight,inFlight)
            val shotAt=maxOf(now+250L,lastShot+1050L)
            lastShot=shotAt
            post(shotAt-now+ocrMs) { inFlight--;done(value) }
        }
        fun run() {
            var steps=0
            while(queue.isNotEmpty()) {
                assertTrue("Readiness must terminate",++steps<1000)
                val event=queue.remove();now=event.time;event.action()
            }
        }
        fun await(early:Boolean,wait:Long,values:List<Evidence?>,
                  ready:(Evidence)->Boolean,done:(Evidence?)->Unit) {
            var at=0
            NativeSaveReadiness.read(early,wait,{ now },{ active },::later,
                { callback -> read(values[(at++).coerceAtMost(values.lastIndex)],callback) },ready,done)
        }
    }

    @Test fun shortGroupFastModeOnlyNeverChangesHeroOrSkillTiming() {
        val short=listOf("phe","nghe","trang-bi","chuc-phuc-thuong",
            "chuc-phuc-huyen-thoai-su-thi","chuc-phuc-truyen-thuyet")
        for(group in short) {
            assertTrue(group,NativeSaveReadiness.enabled(group,false,true))
            assertFalse(group,NativeSaveReadiness.enabled(group,false,false))
            assertFalse(group,NativeSaveReadiness.enabled(group,true,true))
        }
        for(group in listOf("quan-co","chieu-quan-co","chua-phan-loai","chuc-phuc-unknown"))
            assertFalse(group,NativeSaveReadiness.enabled(group,false,true))
    }

    @Test fun readyTwentyImageBatchUsesSameFortyReadsAndSavesThirtyFiveSeconds() {
        fun batch(early:Boolean):Clock {
            val clock=Clock()
            fun save(at:Int) {
                if(at==20) return
                // Production touchGame shield 150 ms + long press 850 ms.
                clock.post(1000) {
                    clock.await(early,1000,listOf(prompt),{ ChessWiki.nativeConfirm(it.words)!=null }) { first ->
                        assertNotNull(ChessWiki.nativeConfirm(first!!.words))
                        // Sure: 150 ms shield + 100 ms gesture.
                        clock.post(250) {
                            clock.await(early,1150,listOf(page),{ it.scene==NativeSaveEvidence.Scene.PAGE }) { after ->
                                assertEquals(NativeSaveEvidence.Scene.PAGE,after!!.scene)
                                // Same simulated journal write and mandatory visible Stop/Pause window.
                                clock.post(40+400) { save(at+1) }
                            }
                        }
                    }
                }
            }
            save(0);clock.run();return clock
        }
        val before=batch(false);val after=batch(true)
        assertEquals(92_800L,before.now)
        assertEquals(57_800L,after.now)
        assertEquals(35_000L,before.now-after.now)
        assertEquals(40,before.reads);assertEquals(before.reads,after.reads)
        assertEquals(40,before.waits.size);assertEquals(0,after.waits.size)
        assertEquals(1,after.maxInFlight)
    }

    @Test fun earlyPagePartialPromptAndReadFailureCannotBeCalledAbsentOrConfirmed() {
        val partial=evidence("Save Photo Cancel",NativeSaveEvidence.Scene.PROMPT)
        for(first in listOf(page,partial,unknown,null)) {
            val clock=Clock();var result:Evidence?=null
            clock.await(true,1000,listOf(first,prompt),{ ChessWiki.nativeConfirm(it.words)!=null }) { result=it }
            assertNull(result)
            clock.run()
            assertEquals(prompt,result)
            assertEquals(listOf(0L,1000L),clock.requestedAt)
            assertEquals(2,clock.reads);assertEquals(1,clock.maxInFlight)
        }
    }

    @Test fun postSureRequiresFreshPageReadinessAndRetriesTheOriginalDeadline() {
        val detail=evidence("Quillen",NativeSaveEvidence.Scene.DETAIL)
        for(first in listOf(prompt,unknown,detail,null)) {
            val clock=Clock();var result:Evidence?=null
            clock.await(true,1150,listOf(first,page),{ it.scene==NativeSaveEvidence.Scene.PAGE }) { result=it }
            clock.run()
            assertEquals(page,result)
            assertEquals(listOf(0L,1150L),clock.requestedAt)
            assertEquals(2,clock.reads);assertEquals(1,clock.maxInFlight)
        }
    }

    @Test fun unreadableOrStillOpenFallbackIsReturnedUnchangedForFailureHandling() {
        for(value in listOf(prompt,unknown,null)) {
            val clock=Clock();var calls=0;var result:Evidence?=page
            clock.await(true,1150,listOf(value),{ it.scene==NativeSaveEvidence.Scene.PAGE }) {
                calls++;result=it
            }
            clock.run()
            assertEquals(value,result);assertEquals(1,calls);assertEquals(2,clock.reads)
        }
    }

    @Test fun stopDuringReadOrBeforeFallbackNeverCompletesOrStartsAnotherRead() {
        for(stopAt in listOf(100L,700L)) {
            val clock=Clock();var completed=false
            clock.await(true,1000,listOf(page,prompt),{ ChessWiki.nativeConfirm(it.words)!=null }) { completed=true }
            clock.post(stopAt) { clock.active=false }
            clock.run()
            assertFalse(completed);assertEquals(1,clock.reads)
        }
    }

    @Test fun stopDuringFinalReadDiscardsTheLateConfirmation() {
        val clock=Clock();var completed=false
        clock.await(true,1000,listOf(page,prompt),{ ChessWiki.nativeConfirm(it.words)!=null }) { completed=true }
        clock.post(1100) { clock.active=false };clock.run()
        assertEquals(2,clock.reads);assertFalse(completed)
    }

    @Test fun slowOcrStillGetsOneFreshFallbackWithoutConcurrentReadsOrUnboundedPolling() {
        val clock=Clock(1600);var result:Evidence?=null
        clock.await(true,1000,listOf(page,prompt),{ ChessWiki.nativeConfirm(it.words)!=null }) { result=it }
        clock.run()
        assertEquals(prompt,result)
        assertEquals(listOf(0L,1850L),clock.requestedAt)
        assertEquals(listOf(0L),clock.waits)
        assertEquals(2,clock.reads);assertEquals(1,clock.maxInFlight)
    }

    @Test fun disabledModeKeepsOriginalWaitAndExactlyOneReadEvenWhenUnready() {
        val clock=Clock();var result:Evidence?=null
        clock.await(false,1150,listOf(unknown),{ false }) { result=it };clock.run()
        assertEquals(unknown,result);assertEquals(listOf(1150L),clock.requestedAt)
        assertEquals(1,clock.reads)
    }

    @Test fun duplicateCallbacksNeverQueueExtraFallbacksOrDeliverTwice() {
        val clock=Clock();var calls=0;var reads=0
        NativeSaveReadiness.read(true,1000,{ clock.now },{ clock.active },clock::later,
            { callback:(Evidence?)->Unit -> reads++;callback(page);callback(page) },
            { false }) { calls++ }
        clock.run()
        assertEquals(2,reads);assertEquals(1,calls);assertEquals(1,clock.waits.size)
    }
}
