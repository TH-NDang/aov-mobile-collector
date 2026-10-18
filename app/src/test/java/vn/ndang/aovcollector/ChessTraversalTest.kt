package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Drives the real reader, including its ListNavigator adapter, with measured scrolling geometry.
 * Profiles contain the same fifteen RGB-band values per vertical pixel as HeroGrid.profile().
 * No Android Bitmap implementation, guessed page increment, or fake navigation outcome is used.
 */
class ChessTraversalTest {
    private companion object {
        const val HERO_TOPIC = "tat-ca-quan-co"
        const val HEIGHT = 640
        const val HERO_TOPIC_DONE = "hero-grid-v2:topic:$HERO_TOPIC"
        val NAMES = listOf(
            "Violet", "Arduin", "Hayate", "Raz", "Lumburr",
            "Fennik", "Max", "Helen", "Krizzix", "D’Arcy",
            "Valhein", "Alice", "Butterfly", "Maloch", "Chaugnar",
            "Tel’Annas", "Rourke", "Capheny", "Tulen", "Ormarr"
        )
        fun word(text:String,x:Double,y:Double) = GameScreens.Word(text,x-.035,y-.012,x+.035,y+.012)
        fun itemId(name:String) = "item:$HERO_TOPIC:hero-${ChessWiki.safeName(name)}"
        fun slotId(index:Int,name:String) = ChessWiki.heroSlotKey(index,"hero-${ChessWiki.safeName(name)}")

        /** A deterministic textured world sampled at actual integer-pixel scroll offsets. */
        fun profile(offset:Int):FloatArray {
            val layout=ChessWiki.HERO_LAYOUT
            val top=(layout.profileTop*HEIGHT).toInt()
            val bottom=(layout.profileBottom*HEIGHT).toInt()
            return FloatArray((bottom-top)*15) { i ->
                val worldY=top+i/15+offset
                var bits=(worldY*73856093) xor ((i%15+1)*19349663)
                bits=(bits xor (bits ushr 13))*83492791
                (24+(bits ushr 16 and 191)).toFloat()
            }
        }
    }

    private data class Capture(val name:String,val modal:Boolean,val offset:Int,val hero:String?,val detailPage:Int?)
    private data class DrawerDrag(val hero:String,val attempt:Int,val before:Int,val after:Int?)
    private data class Click(val index:Int?,val x:Double,val y:Double,val offset:Int,val lastListLook:Int)
    private data class Drag(val requested:Double,val before:Int,val after:Int)

    private class HeroScreen(
        val names:List<String> = NAMES.take(10),
        oldIds:Set<String> = emptySet(),
        slots:Map<Int,String> = emptyMap(),
        val slide:List<Double> = listOf(1.0),
        val ignoredUpDrags:Set<Int> = emptySet(),
        val closeMotion:Map<Int,Int> = emptyMap(),
        val hiddenLabels:Set<Int> = emptySet(),
        val cardLabels:Map<Int,String> = emptyMap(),
        val wrongPopups:Map<Int,String> = emptyMap(),
        val drawerPages:List<Int> = listOf(0),
        val ignoredDrawerDrags:Set<Int> = emptySet(),
        val closeOnDrawerDrag:Int? = null,
        val skillIconsByPage:Map<Int,List<ChessWiki.Tile>> = emptyMap(),
        val failedNativeItem:String? = null,
        val absentNativeItem:String? = null,
        val interruptAfterNative:Boolean = false,
        val failedDetailPage:Int? = null,
        val failCheckpoint:Boolean = false,
        val savedDetailSignatures:Map<String,IntArray> = emptyMap(),
        val signatureNoise:Int = 0,
        startPartway:Boolean = false,
        initiallyOpen:String? = null
    ):ChessWikiReader.Screen {
        private val layout=ChessWiki.HERO_LAYOUT
        val maxOffset=((layout.row0+((names.size-1)/5)*layout.pitch-.60)*HEIGHT).roundToInt().coerceAtLeast(0)
        var offset=if(startPartway) (maxOffset*.78).roundToInt() else 0
        var opened=initiallyOpen
        private var openedIndex:Int?=null
        private var drawerPageIndex=0
        private var drawerDrags=0
        private var lastListLook=-1
        private var upDrags=0
        private var dragCount=0
        private var looks=0
        private var released=0
        private var steps=0
        private val queue=ArrayDeque<()->Unit>()
        // CollectorService normalizes old item IDs before it exposes its persisted ledger.
        val ledger=oldIds.map(ChessWiki::heroIdentity).toMutableSet().apply {
            slots.forEach { (index,id) -> add(ChessWiki.heroSlotKey(index,id)) }
        }
        val finishedIds=ArrayList<String>()
        val saved=ArrayList<Capture>()
        val clicked=ArrayList<Click>()
        val drags=ArrayList<Drag>()
        val modalDrags=ArrayList<DrawerDrag>()
        val nativePhotos=ArrayList<Pair<Int,String>>()
        val nativeSkills=ArrayList<String>()
        val detailSignatures=savedDetailSignatures.mapValues { it.value.copyOf() }.toMutableMap()
        val notes=ArrayList<String>()
        val progress=ArrayList<String>()
        val events=ArrayList<String>()
        var closeTaps=0
        var croppedTitleReads=0
        var outcome:ChessWikiReader.Outcome?=null
        override var alive=true
        override val completedHeroes get()=ledger.count { it.startsWith("item:$HERO_TOPIC:hero-") }
        override val completedHeroSlots get()=ChessWiki.heroSlots(ledger)

        private fun post(action:()->Unit) { queue.addLast(action) }
        private fun centre(index:Int,at:Int)=layout.row0+(index/5)*layout.pitch-at.toDouble()/HEIGHT
        private fun cardAt(x:Double,y:Double,at:Int):Int? = names.indices.firstOrNull {
            abs(x-layout.columns[it%5])<.025 && abs(y-centre(it,at))<.05 && y in .235.. .985
        }
        override fun look(words:Boolean,then:(ChessWikiReader.Look?)->Unit) = post {
            val at=offset
            val hero=opened
            val isModal=hero!=null
            val detailPage=if(isModal) drawerPages[drawerPageIndex] else null
            looks++
            if(!isModal) { lastListLook=at;events.add("look-list:$at") }
            val tiles=if(isModal) emptyList() else names.indices.mapNotNull { i ->
                val y=centre(i,at)
                val label=cardLabels[i]?:names[i]
                if(y !in .29.. .77) null else ChessWiki.Tile(layout.columns[i%5],y,
                    if(i in hiddenLabels) "unread-$i" else "hero-${ChessWiki.safeName(label)}",
                    if(i in hiddenLabels) null else label)
            }
            val content=if(isModal) listOf(word(hero!!+" O1",.410,.267),word("Pháo đại",.49,.499))
                else listOf(word("Bách khoa",.23,.064),word("Cờ Liên Quân",.44,.064),
                    word("Tất cả quân cờ",.30,.18))+tiles.mapNotNull { tile ->
                    tile.label?.let { word(it,tile.x,tile.y+.172) }
                }
            then(object:ChessWikiReader.Look {
                var consumed=false
                override val words=if(words) content else emptyList()
                override val signature=IntArray(96) { if(isModal) 73+detailPage!!*40+signatureNoise else 30+(at/7)%150 }
                override val modal=isModal
                override val heroProfile=if(isModal) floatArrayOf() else profile(at)
                override val height=HEIGHT
                override fun hasHeroCard(x:Double,y:Double)=!isModal&&cardAt(x,y,at)!=null
                override fun tiles(topic:ChessWiki.Topic)=tiles
                override fun skillIcons()=skillIconsByPage[detailPage].orEmpty()
                private fun release() {
                    assertFalse("Every captured frame must be kept or discarded exactly once",consumed)
                    consumed=true;released++
                }
                override fun keep(name:String,then:(Boolean)->Unit) {
                    release();saved.add(Capture(name,isModal,at,hero,detailPage));events.add("save:$name")
                    post { then(!isModal||detailPage!=failedDetailPage) }
                }
                override fun discard()=release()
            })
        }
        override fun tap(x:Double,y:Double,then:(Boolean)->Unit) = post {
            if(x>.87&&y<.13) {
                closeTaps++
                events.add("close:${opened ?: "none"}")
                val was=openedIndex
                opened=null;openedIndex=null
                if(was!=null) offset=(offset+(closeMotion[was]?:0)).coerceIn(0,maxOffset)
            } else if(y>.28 && layout.columns.any { abs(x-it)<.025 }) {
                val index=cardAt(x,y,offset)
                clicked.add(Click(index,x,y,offset,lastListLook))
                events.add("open:$index")
                if(index!=null) {
                    openedIndex=index
                    opened=wrongPopups[index]?:names[index]
                    drawerPageIndex=0;drawerDrags=0
                }
            }
            then(true)
        }
        override fun swipe(x:Double,y:Double,toX:Double,toY:Double,then:(Boolean)->Unit) = post {
            if(opened==null) {
                val d=y-toY
                val before=offset
                val ignored=d>0&&++upDrags in ignoredUpDrags
                if(!ignored) {
                    val factor=if(d<0) 1.0 else slide[dragCount++%slide.size]
                    offset=(offset+(d*HEIGHT*factor).roundToInt()).coerceIn(0,maxOffset)
                }
                drags.add(Drag(d,before,offset));events.add("drag:$before:$offset")
            } else {
                val hero=opened!!
                val before=drawerPages[drawerPageIndex]
                drawerDrags++
                if(drawerDrags==closeOnDrawerDrag) {
                    opened=null;openedIndex=null
                    events.add("unexpected-close:$hero:$drawerDrags")
                } else if(drawerDrags !in ignoredDrawerDrags) {
                    drawerPageIndex=(drawerPageIndex+1).coerceAtMost(drawerPages.lastIndex)
                }
                modalDrags.add(DrawerDrag(hero,drawerDrags,before,
                    if(opened==null) null else drawerPages[drawerPageIndex]))
            }
            then(true)
        }
        override fun nativeSave(group:String,item:String,x:Double,y:Double,preserveDetail:Boolean,aliases:Set<String>,
                                then:(ChessWikiReader.NativeResult)->Unit) = post {
            if(group=="chieu-quan-co") {
                assertTrue("Skills belong to the open detail",preserveDetail)
                assertNotNull(opened)
                nativeSkills.add(item)
                if(interruptAfterNative) alive=false
                then(when(item) {
                    failedNativeItem -> ChessWikiReader.NativeResult.FAILED
                    absentNativeItem -> ChessWikiReader.NativeResult.ABSENT
                    else -> ChessWikiReader.NativeResult.SAVED
                })
                return@post
            }
            assertEquals("quan-co",group)
            assertFalse("A portrait-only save must remain on the hero list",preserveDetail)
            assertNull(opened)
            val index=cardAt(x,y,offset)
            assertNotNull("Native photo must target a currently visible card",index)
            nativePhotos.add(index!! to item)
            then(ChessWikiReader.NativeResult.SAVED)
        }
        override fun readHeroTitle(then:(String?)->Unit) = post { croppedTitleReads++;then(opened) }
        override fun later(ms:Long,then:()->Unit)=post(then)
        override fun seen(id:String)=id in ledger
        override fun finished(id:String) { ledger.add(id);finishedIds.add(id) }
        override fun detailSignature(id:String)=detailSignatures[id]
        override fun finishDetail(id:String,signature:IntArray):Boolean {
            if(failCheckpoint) return false
            detailSignatures[id]=signature.copyOf();finished(id);return true
        }
        override fun note(message:String) { notes.add(message) }
        override fun say(message:String) { progress.add(message) }

        fun run(details:Boolean=true,native:Boolean=false,skills:Boolean=false,
                expectInterrupted:Boolean=false):ChessWikiReader.Outcome? {
            ChessWikiReader(this,ChessWiki.Options(setOf(HERO_TOPIC),openGame=false,
                heroDetails=details,nativeGroups=(if(native) setOf("quan-co") else emptySet())+
                    (if(skills) setOf("chieu-quan-co") else emptySet()))) { outcome=it }.start()
            while(queue.isNotEmpty()) {
                assertTrue("Reader must terminate rather than loop forever",++steps<10000)
                queue.removeFirst().invoke()
            }
            if(expectInterrupted) { assertFalse(alive);assertNull(outcome) }
            else assertNotNull("Reader must report a terminal result",outcome)
            assertEquals("No owned screenshots may leak",looks,released)
            return outcome
        }
    }

    @Test fun geometricProfilesUseChessBoundsAndMeasureActualPixelMovement() {
        val layout=ChessWiki.HERO_LAYOUT
        assertEquals(((layout.profileBottom*HEIGHT).toInt()-(layout.profileTop*HEIGHT).toInt())*15,
            profile(0).size)
        for(delta in listOf(0,137,-91,249)) {
            val shift=HeroGrid.shift(profile(180),profile(180+delta))
            assertTrue("Expected a reliable shift of $delta pixels: $shift",shift.reliable)
            assertEquals(delta,shift.px)
        }
    }

    @Test fun measuredTraversalSurvivesVariableInertiaAndIgnoredDragsWithoutRepeatingMax() {
        val run=HeroScreen(names=NAMES.take(17),slide=listOf(.72,1.18,1.35,.91),ignoredUpDrags=setOf(1,4))
        val result=run.run()
        assertTrue(result.toString(),result is ChessWikiReader.Outcome.Done)
        assertEquals(run.names.indices.toList(),run.clicked.map { it.index })
        assertEquals(1,run.clicked.count { it.index==6 })
        assertEquals(1,run.saved.count { "__tuong__max__" in it.name })
        assertEquals(17,run.ledger.count { it.startsWith("item:$HERO_TOPIC:hero-") })
        assertEquals(17,ChessWiki.heroSlots(run.ledger).size)
        assertTrue(run.drags.any { it.requested>0&&it.before==it.after&&it.before<run.maxOffset })
        assertTrue(run.drags.any { it.requested>0&&it.after-it.before>it.requested*HEIGHT+15 })
        assertEquals(run.saved.size,run.saved.map { it.name }.toSet().size)
        assertFalse(run.saved.any { "__hang__" in it.name })
    }

    @Test fun everyDrawerCloseReacquiresTheListBeforeChoosingTheNextCardCoordinates() {
        val run=HeroScreen(closeMotion=mapOf(0 to 44,5 to 48,7 to -31))
        assertTrue(run.run() is ChessWikiReader.Outcome.Done)
        assertEquals(run.names.indices.toList(),run.clicked.map { it.index })
        val violet=run.clicked[0]
        val arduin=run.clicked[1]
        assertTrue("Closing Violet must really move the simulated viewport",violet.offset!=arduin.offset)
        assertTrue("The next tap must use freshly measured y",abs(violet.y-arduin.y)>.05)
        for(click in run.clicked) {
            assertEquals("A fresh list look must precede each physical tap",click.offset,click.lastListLook)
            val index=click.index!!
            val expected=ChessWiki.HERO_LAYOUT.centre(index,click.offset.toDouble()/HEIGHT)
            assertEquals(expected.first,click.x,.001)
            assertEquals(expected.second,click.y,.002)
        }
    }

    @Test fun oldCompletedHeroIdsAreNormalizedAndSkippedBeforeOpeningTheDrawer() {
        val run=HeroScreen(oldIds=setOf("item:$HERO_TOPIC:hero-violet-o1",itemId("Max"),
            "topic:$HERO_TOPIC","chess-page:$HERO_TOPIC:1","chess-page:$HERO_TOPIC:2"))
        assertTrue(run.run() is ChessWikiReader.Outcome.Done)
        assertEquals(listOf(1,2,3,4,5,7,8,9),run.clicked.map { it.index })
        assertFalse(run.saved.any { "__tuong__violet__" in it.name||"__tuong__max__" in it.name })
        assertEquals(2,run.saved.count { !it.modal }) // old page markers must not suppress new full frames
        assertTrue(HERO_TOPIC_DONE in run.ledger)
        assertEquals("hero-violet",ChessWiki.heroSlots(run.ledger)[0])
        assertEquals("hero-max",ChessWiki.heroSlots(run.ledger)[6])
        assertEquals(10,run.ledger.count { it.startsWith("item:$HERO_TOPIC:hero-") })
    }

    @Test fun persistedVerifiedSlotsSkipCompletedCardsAfterFreshViewportAndLabelChecks() {
        val completed=listOf(0,1,2,3,4,6)
        val slots=completed.associateWith { "hero-${ChessWiki.safeName(NAMES[it])}" }
        val run=HeroScreen(oldIds=completed.map { itemId(NAMES[it]) }.toSet(),slots=slots,
            startPartway=true)
        assertTrue(run.run() is ChessWikiReader.Outcome.Done)
        assertEquals(listOf(5,7,8,9),run.clicked.map { it.index })
        assertEquals(4,run.saved.count { it.modal })
        assertTrue(run.drags.take(4).all { it.requested<0 })
        assertEquals(0,run.croppedTitleReads)
        assertEquals(10,run.ledger.count { it.startsWith("item:$HERO_TOPIC:hero-") })
    }

    @Test fun ambiguousCompletedSlotVerifiesItsTitleWithoutSavingOrCountingThatHeroAgain() {
        val run=HeroScreen(oldIds=setOf(itemId("Max")),slots=mapOf(6 to "hero-max"),hiddenLabels=setOf(6))
        assertEquals(ChessWikiReader.Outcome.Done(11,9),run.run())
        assertEquals(run.names.indices.toList(),run.clicked.map { it.index })
        assertEquals(2,run.croppedTitleReads)
        assertFalse(run.saved.any { "__tuong__max__" in it.name })
        assertEquals("hero-max",ChessWiki.heroSlots(run.ledger)[6])
        assertTrue(HERO_TOPIC_DONE in run.ledger)
    }

    @Test fun legacyCompletedMaxWithAnUnreadableCardBindsItsVerifiedSlotWithoutRecapturing() {
        val run=HeroScreen(oldIds=setOf(itemId("Max")),hiddenLabels=setOf(6))
        assertEquals(ChessWikiReader.Outcome.Done(11,9),run.run())
        assertEquals(run.names.indices.toList(),run.clicked.map { it.index })
        assertEquals(2,run.croppedTitleReads)
        assertFalse(run.saved.any { "__tuong__max__" in it.name })
        assertEquals("hero-max",ChessWiki.heroSlots(run.ledger)[6])
        assertEquals(1,run.finishedIds.count { it==slotId(6,"Max") })
        assertEquals(10,run.ledger.count { it.startsWith("item:$HERO_TOPIC:hero-") })
    }

    @Test fun legacyCompletedArduinAcceptsAOneLetterCardTypoOnlyAfterVerifyingTheHeading() {
        val run=HeroScreen(oldIds=setOf(itemId("Arduin")),cardLabels=mapOf(1 to "Ardvin"))
        assertEquals(ChessWikiReader.Outcome.Done(11,9),run.run())
        assertEquals(run.names.indices.toList(),run.clicked.map { it.index })
        assertEquals(2,run.croppedTitleReads)
        assertEquals("hero-arduin",ChessWiki.heroSlots(run.ledger)[1])
        assertFalse(itemId("Ardvin") in run.ledger)
        assertFalse(run.saved.any { "__tuong__arduin__" in it.name||"__tuong__ardvin__" in it.name })
        assertEquals(10,run.ledger.count { it.startsWith("item:$HERO_TOPIC:hero-") })
    }

    @Test fun conflictingLegacyCompletedPopupDoesNotBindToAnUnrelatedVisibleCard() {
        val run=HeroScreen(names=listOf("Krizzix"),oldIds=setOf(itemId("Max")),wrongPopups=mapOf(0 to "Max"))
        assertTrue(run.run() is ChessWikiReader.Outcome.Failed)
        assertEquals(listOf(0,0),run.clicked.map { it.index })
        assertEquals(4,run.croppedTitleReads)
        assertTrue(ChessWiki.heroSlots(run.ledger).isEmpty())
        assertFalse(itemId("Krizzix") in run.ledger)
        assertFalse(run.saved.any { it.modal })
        assertFalse(HERO_TOPIC_DONE in run.ledger)
    }

    @Test fun changedOrderFailsSafelyWithoutReplacingAnAlreadyVerifiedOrdinal() {
        val reordered=NAMES.take(10).toMutableList().apply { this[0]="Arduin";this[1]="Violet" }
        val run=HeroScreen(names=reordered,oldIds=setOf(itemId("Violet")),slots=mapOf(0 to "hero-violet"))
        assertTrue(run.run() is ChessWikiReader.Outcome.Failed)
        assertEquals(listOf(0,0),run.clicked.map { it.index })
        assertFalse(run.saved.any { it.modal })
        assertFalse(itemId("Arduin") in run.ledger)
        assertEquals(mapOf(0 to "hero-violet"),ChessWiki.heroSlots(run.ledger))
        assertEquals(listOf(slotId(0,"Violet")),run.ledger.filter { it.startsWith("hero-slot") })
        assertFalse(run.finishedIds.any { it.startsWith("hero-slot") })
        assertFalse(HERO_TOPIC_DONE in run.ledger)
    }

    @Test fun resumeClosesAnOpenModalReturnsFromMidlistAndCollectsEveryRemainingHero() {
        val run=HeroScreen(names=NAMES.take(15),oldIds=setOf(itemId("Violet")),
            startPartway=true,initiallyOpen="Max")
        val initialOffset=run.offset
        assertTrue(run.run() is ChessWikiReader.Outcome.Done)
        assertTrue(initialOffset>0)
        assertEquals((1 until 15).toList(),run.clicked.map { it.index })
        assertEquals("close:Max",run.events.first())
        assertEquals(0,run.clicked.first().offset)
        assertEquals(15,run.closeTaps) // interrupted drawer plus fourteen new heroes
        assertEquals(1,run.saved.count { "__tuong__max__" in it.name })
    }

    @Test fun listOnlyModeKeepsFullFramesForEveryRowAndNeverCreatesHangImagesOrOpensHeroes() {
        val run=HeroScreen(names=NAMES.take(12),slide=listOf(.83,1.22),ignoredUpDrags=setOf(1))
        val outcome=run.run(details=false)
        assertEquals(ChessWikiReader.Outcome.Done(3,0),outcome)
        assertTrue(run.clicked.isEmpty())
        assertEquals((1..3).map { ChessWiki.heroOverviewFilename(ChessWiki.TOPICS.first { it.key==HERO_TOPIC },it) },
            run.saved.map { it.name })
        assertTrue(run.saved.all { !it.modal&&it.hero==null&&"__hang__" !in it.name })
        assertTrue(run.saved.zipWithNext().all { (a,b) -> a.offset<b.offset })
        assertFalse(run.finishedIds.any { it.startsWith("item:")||it.startsWith("hero-slot") })
    }

    @Test fun nativePortraitOnlyVerifiesEveryNameWithoutSavingDetailScreenshots() {
        val run=HeroScreen(names=NAMES.take(12),slide=listOf(.86,1.19))
        assertEquals(ChessWikiReader.Outcome.Done(3,0),run.run(details=false,native=true))
        assertEquals(run.names.indices.toList(),run.nativePhotos.map { it.first })
        assertEquals(run.names.map { ChessWiki.safeName(it)+"-head" },run.nativePhotos.map { it.second })
        assertEquals(run.names.indices.toList(),run.clicked.map { it.index })
        assertEquals(run.names.size,run.closeTaps)
        assertEquals(0,run.croppedTitleReads)
        assertTrue(run.saved.all { !it.modal&&"__tuong__" !in it.name&&"__hang__" !in it.name })
        assertEquals(12,run.ledger.count { it.startsWith("native:quan-co:") })
    }

    @Test fun resumedPortraitOnlyRunDoesNotReopenAlreadyVerifiedNamedHeads() {
        val run=HeroScreen(names=listOf("Violet","Arduin"),oldIds=setOf("native:quan-co:hero-violet"))
        assertTrue(run.run(details=false,native=true) is ChessWikiReader.Outcome.Done)
        assertEquals(listOf(1),run.clicked.map { it.index })
        assertEquals(listOf("arduin-head"),run.nativePhotos.map { it.second })
        assertTrue(run.saved.none { it.modal })
    }

    @Test fun portraitsUseVerifiedDrawerNamesWhenCardNamesAreMissingOrWrong() {
        for(details in listOf(false,true)) {
            val run=HeroScreen(names=listOf("Arduin","Violet"),hiddenLabels=setOf(0),
                cardLabels=mapOf(1 to "Vioiet"),closeMotion=mapOf(0 to 18))
            assertTrue(run.run(details=details,native=true) is ChessWikiReader.Outcome.Done)
            assertEquals(listOf("arduin-head","violet-head"),run.nativePhotos.map { it.second })
            assertFalse(run.nativePhotos.any { "slot-" in it.second||"vioiet" in it.second })
            if(details) assertEquals(listOf("arduin","violet"),
                run.saved.filter { it.modal }.map { it.name.split("__")[4] })
            else assertTrue(run.saved.none { it.modal })
        }
    }

    @Test fun verifiedMaxAtAnotherOrdinalRetriesOnceThenFailsWithoutMarkingTheSkippedHero() {
        val run=HeroScreen(wrongPopups=mapOf(8 to "Max"))
        val result=run.run()
        assertTrue(result.toString(),result is ChessWikiReader.Outcome.Failed)
        assertEquals((0..8).toList()+8,run.clicked.map { it.index })
        assertEquals(1,run.saved.count { "__tuong__max__" in it.name })
        assertEquals(2,run.clicked.count { it.index==8 })
        assertEquals(4,run.croppedTitleReads)
        assertFalse(itemId("Krizzix") in run.ledger)
        assertFalse(itemId("D’Arcy") in run.ledger)
        assertFalse(ChessWiki.heroSlots(run.ledger).containsKey(8))
        assertFalse(run.saved.any { "__tuong__krizzix__" in it.name })
        assertFalse(HERO_TOPIC_DONE in run.ledger)
        assertTrue((result as ChessWikiReader.Outcome.Failed).reason.contains("Max"))
        assertTrue("Recovery must be bounded",run.drags.size<35)
    }

    @Test fun openedHeroDrawersCaptureEachUniquePageOnceAfterAnIgnoredFirstSwipe() {
        val run=HeroScreen(names=listOf("Violet","Max"),drawerPages=listOf(0,1,2),ignoredDrawerDrags=setOf(1))
        assertEquals(ChessWikiReader.Outcome.Done(7,2),run.run())
        assertEquals(listOf(0,1),run.clicked.map { it.index })
        val details=run.saved.filter { it.modal }
        assertEquals(listOf("Violet","Max").flatMap { hero -> (0..2).map { hero to it } },
            details.map { it.hero to it.detailPage })
        assertEquals(6,details.map { it.name }.toSet().size)
        assertEquals(2,run.closeTaps)
        for(hero in listOf("Violet","Max")) {
            val drags=run.modalDrags.filter { it.hero==hero }
            assertEquals(5,drags.size)
            assertEquals(DrawerDrag(hero,1,0,0),drags[0])
            assertEquals(DrawerDrag(hero,2,0,1),drags[1])
            assertEquals(listOf(1,2,3),details.filter { it.hero==hero }.map { it.name.takeLast(3).toInt() })
        }
        assertEquals(2,ChessWiki.heroSlots(run.ledger).size)
    }

    @Test fun unexpectedDrawerCloseDuringAdvanceOrStillPageRetryFailsWithoutCompletingTheHero() {
        for(closeAt in listOf(1,2)) {
            val run=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
                ignoredDrawerDrags=if(closeAt==2) setOf(1) else emptySet(),closeOnDrawerDrag=closeAt)
            val outcome=run.run()
            assertTrue(outcome.toString(),outcome is ChessWikiReader.Outcome.Failed)
            assertEquals(listOf(0),run.clicked.map { it.index })
            assertEquals(listOf(0),run.saved.filter { it.modal }.map { it.detailPage })
            assertEquals(closeAt,run.modalDrags.size)
            assertNull(run.modalDrags.last().after)
            assertEquals(0,run.closeTaps)
            assertFalse(itemId("Violet") in run.ledger)
            assertTrue(ChessWiki.heroSlots(run.ledger).isEmpty())
            assertFalse(HERO_TOPIC_DONE in run.ledger)
            assertTrue((outcome as ChessWikiReader.Outcome.Failed).reason.contains("đóng ngoài dự kiến"))
        }
    }

    @Test fun drawerContentCycleFailsAfterSavingABOnceWithoutSavingAAgain() {
        val run=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1,0))
        val outcome=run.run()
        assertTrue(outcome.toString(),outcome is ChessWikiReader.Outcome.Failed)
        assertEquals(listOf(0,1),run.saved.filter { it.modal }.map { it.detailPage })
        assertEquals(2,run.modalDrags.size)
        assertFalse(run.saved.any { it.modal&&it.name.endsWith("003") })
        assertFalse(itemId("Violet") in run.ledger)
        assertTrue(ChessWiki.heroSlots(run.ledger).isEmpty())
        assertFalse(HERO_TOPIC_DONE in run.ledger)
        assertTrue((outcome as ChessWikiReader.Outcome.Failed).reason.contains("nội dung cũ"))
    }

    @Test fun interruptedSkillSaveResumesWithoutRewritingDetailsOrDownloadingSavedSkillsAgain() {
        val a=ChessWiki.Tile(.405,.525,"skill-a")
        val b=ChessWiki.Tile(.405,.66,"skill-b")
        val icons=mapOf(0 to listOf(a),1 to listOf(a,b))
        val first=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
            skillIconsByPage=icons,failedNativeItem="violet-skill-skill-b")
        assertTrue(first.run(skills=true) is ChessWikiReader.Outcome.Failed)
        assertEquals(listOf("violet-skill-skill-a","violet-skill-skill-b"),first.nativeSkills)
        assertEquals(listOf(0,1),first.saved.filter { it.modal }.map { it.detailPage })
        assertFalse(itemId("Violet") in first.ledger)
        val resumed=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
            skillIconsByPage=icons,oldIds=first.ledger,savedDetailSignatures=first.detailSignatures,
            signatureNoise=2,initiallyOpen="Violet")
        assertTrue(resumed.run(skills=true) is ChessWikiReader.Outcome.Done)
        assertTrue("Small pixel noise on resume cannot rewrite -detail-1 or -detail-2",
            resumed.saved.none { it.modal })
        assertEquals(listOf("violet-skill-skill-b"),resumed.nativeSkills)
        assertTrue(itemId("Violet") in resumed.ledger)
    }

    @Test fun legacySuccessfulSkillProbeSuppressesItsNewMergedTargetOnResume() {
        val merged=ChessWiki.Tile(.405,.525,"new-center",aliases=setOf("old-upper","old-lower"))
        val run=HeroScreen(names=listOf("Violet"),skillIconsByPage=mapOf(0 to listOf(merged)),
            oldIds=setOf("native:chieu-quan-co:violet:old-upper"))
        assertTrue(run.run(skills=true) is ChessWikiReader.Outcome.Done)
        assertTrue(run.nativeSkills.isEmpty())
        assertTrue(itemId("Violet") in run.ledger)
    }

    @Test fun successfulMergedSkillPersistsRawAliasesAcrossAnInterruptedRun() {
        val merged=ChessWiki.Tile(.405,.525,"new-center",aliases=setOf("old-upper","old-lower"))
        val first=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
            skillIconsByPage=mapOf(0 to listOf(merged)),closeOnDrawerDrag=1)
        assertTrue(first.run(skills=true) is ChessWikiReader.Outcome.Failed)
        assertTrue("native:chieu-quan-co:violet:old-upper" in first.ledger)
        assertTrue("native:chieu-quan-co:violet:old-lower" in first.ledger)
        val resumed=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),oldIds=first.ledger,
            savedDetailSignatures=first.detailSignatures,
            skillIconsByPage=mapOf(0 to listOf(ChessWiki.Tile(.405,.50,"old-upper"))))
        assertTrue(resumed.run(skills=true) is ChessWikiReader.Outcome.Done)
        assertTrue(resumed.nativeSkills.isEmpty())
    }

    @Test fun stopDuringNativeSaveCannotAdvanceOrMarkAHeroComplete() {
        val run=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
            skillIconsByPage=mapOf(0 to listOf(ChessWiki.Tile(.405,.525,"skill-a"))),
            interruptAfterNative=true)
        assertNull(run.run(skills=true,expectInterrupted=true))
        assertEquals(1,run.nativeSkills.size)
        assertTrue(run.modalDrags.isEmpty())
        assertTrue(run.ledger.none { it.startsWith("native:chieu-quan-co:") })
        assertFalse(itemId("Violet") in run.ledger)
        assertEquals(1,run.detailSignatures.size)
    }

    @Test fun confirmedAbsentSavePhotoCandidateDoesNotBlockRealSkillsOrDetailPages() {
        val run=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
            skillIconsByPage=mapOf(0 to listOf(ChessWiki.Tile(.405,.525,"real-icon"),
                ChessWiki.Tile(.405,.66,"blue-background"))),
            absentNativeItem="violet-skill-blue-background")
        assertTrue(run.run(skills=true) is ChessWikiReader.Outcome.Done)
        assertEquals(2,run.nativeSkills.size)
        assertTrue("native:chieu-quan-co:violet:real-icon" in run.ledger)
        assertFalse("native:chieu-quan-co:violet:blue-background" in run.ledger)
        assertEquals(listOf(0,1),run.saved.filter { it.modal }.map { it.detailPage })
    }

    @Test fun changedContentOnResumeStopsBeforeOverwritingNumberedDetail() {
        val first=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),closeOnDrawerDrag=1)
        assertTrue(first.run() is ChessWikiReader.Outcome.Failed)
        val resumed=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
            oldIds=first.ledger,savedDetailSignatures=first.detailSignatures,signatureNoise=40)
        assertTrue(resumed.run() is ChessWikiReader.Outcome.Failed)
        assertTrue(resumed.saved.none { it.modal })
        assertTrue(resumed.modalDrags.isEmpty())
        assertFalse(itemId("Violet") in resumed.ledger)
    }

    @Test fun failedImageOrCheckpointNeverAdvancesScrollSkillsOrHeroCompletion() {
        for(checkpoint in listOf(false,true)) {
            val run=HeroScreen(names=listOf("Violet"),drawerPages=listOf(0,1),
                skillIconsByPage=mapOf(0 to listOf(ChessWiki.Tile(.405,.525,"skill-a"))),
                failedDetailPage=if(checkpoint) null else 0,failCheckpoint=checkpoint)
            assertTrue(run.run(skills=true) is ChessWikiReader.Outcome.Failed)
            assertTrue(run.modalDrags.isEmpty())
            assertTrue(run.nativeSkills.isEmpty())
            assertFalse(run.ledger.any { it.startsWith("detail-page-v2:") })
            assertFalse(itemId("Violet") in run.ledger)
        }
    }

    @Test fun sidebarScrollingSavesEachChangedFullPageOnceDespiteIgnoredAndTerminalDrags() {
        val selected=listOf("quy-trinh","tat-ca-trang-bi","thuong")
        val topics=ChessWiki.TOPICS.filter { it.key in selected }
        val completed=HashSet<String>()
        val kept=ArrayList<Pair<String,Pair<String,Int>>>()
        val opened=ArrayList<String>()
        val queue=ArrayDeque<()->Unit>()
        var current=topics.first()
        var page=0
        var drags=0
        var outcome:ChessWikiReader.Outcome?=null
        var looks=0
        var released=0
        val screen=object:ChessWikiReader.Screen {
            override val alive=true
            override fun look(words:Boolean,then:(ChessWikiReader.Look?)->Unit) {
                queue.add {
                    val topic=current.key;val snapshot=page;looks++
                    then(object:ChessWikiReader.Look {
                        var consumed=false
                        override val words=listOf(word("Bách khoa Cờ Liên Quân",.4,.07))
                        override val signature=IntArray(96) { 30+snapshot*40 }
                        override val modal=false
                        override fun tiles(topic:ChessWiki.Topic)=emptyList<ChessWiki.Tile>()
                        fun release() { assertFalse(consumed);consumed=true;released++ }
                        override fun keep(name:String,then:(Boolean)->Unit) {
                            release();kept.add(name to (topic to snapshot));queue.add { then(true) }
                        }
                        override fun discard()=release()
                    })
                }
            }
            override fun tap(x:Double,y:Double,then:(Boolean)->Unit) {
                queue.add {
                    if(abs(x-.129)<.01) {
                        current=topics.single { abs(it.sidebarY-y)<.01 }
                        page=0;drags=0;opened.add(current.key)
                    }
                    then(true)
                }
            }
            override fun swipe(x:Double,y:Double,toX:Double,toY:Double,then:(Boolean)->Unit) {
                queue.add {
                    assertTrue("Sidebar content must scroll upward",y>toY)
                    if(++drags!=1) page=(page+1).coerceAtMost(2)
                    then(true)
                }
            }
            override fun later(ms:Long,then:()->Unit) { queue.add(then) }
            override fun seen(id:String)=id in completed
            override fun finished(id:String) { completed.add(id) }
            override fun note(message:String) {}
            override fun say(message:String) {}
        }
        ChessWikiReader(screen,ChessWiki.Options(selected.toSet(),openGame=false)) { outcome=it }.start()
        var operations=0
        while(queue.isNotEmpty()) {
            assertTrue("Sidebar scan must terminate",++operations<1000)
            queue.removeFirst().invoke()
        }
        assertEquals(ChessWikiReader.Outcome.Done(9,0),outcome)
        assertEquals(selected,opened)
        assertEquals(topics.flatMap { t -> (1..3).map { ChessWiki.filename(t,"trang",it) } },kept.map { it.first })
        assertEquals(topics.flatMap { t -> (0..2).map { t.key to it } },kept.map { it.second })
        assertEquals(9,kept.map { it.first }.toSet().size)
        assertEquals(looks,released)
        assertTrue(topics.all { "topic:${it.key}" in completed })
    }
}
