package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class ChessWikiTest {
    @Test fun selectionUsesOnlyChosenTabs() {
        val opts=ChessWiki.Options(setOf("truyen-thuyet","tat-ca-quan-co"),openGame=false)
        assertEquals(listOf("tat-ca-quan-co","truyen-thuyet"),opts.topics().map { it.key })
        assertNull(ChessWiki.detailType(opts.topics()[0],opts))
        assertEquals("quan-co",opts.topics()[0].section)
    }
    @Test fun detailsRequireExplicitOptIn() {
        val opts=ChessWiki.Options(setOf("tat-ca-quan-co","phe","tat-ca-trang-bi"),
            openGame=false,heroDetails=true,synergyDetails=true,equipmentDetails=true)
        assertEquals("tuong",ChessWiki.detailType(ChessWiki.TOPICS.first { it.key=="tat-ca-quan-co" },opts))
        assertEquals("phe",ChessWiki.detailType(ChessWiki.TOPICS.first { it.key=="phe" },opts))
        assertEquals("vat-pham",ChessWiki.detailType(ChessWiki.TOPICS.first { it.key=="tat-ca-trang-bi" },opts))
    }
    @Test fun invalidSelectionsAreRejected() {
        try { ChessWiki.Options(emptySet());fail("empty selection was accepted") } catch(_:IllegalArgumentException) {}
        try { ChessWiki.Options(setOf("unknown"));fail("unknown tab was accepted") } catch(_:IllegalArgumentException) {}
    }
    @Test fun filenamesDescribeNestedCategoriesAndDetails() {
        val t=ChessWiki.TOPICS.first { it.key=="tat-ca-quan-co" }
        assertEquals("chess__quan-co__tat-ca-quan-co__trang__001",ChessWiki.filename(t,"trang",1))
        assertEquals("chess__quan-co__tat-ca-quan-co__tuong__violet-abc12345__002",
            ChessWiki.filename(t,"tuong",2,"violet-abc12345"))
        assertEquals("quyen-truong-rhea",ChessWiki.safeName("Quyền trượng Rhea"))
    }
    @Test fun scrollChangeIgnoresSmallAnimationAndSeesMovement() {
        val base=IntArray(96) { 55 }
        assertFalse(ChessWiki.changed(base,IntArray(96) { 58 }))
        assertTrue(ChessWiki.changed(base,IntArray(96) { 61 }))
    }
    @Test fun readerCapturesChosenPageThenStopsAtEnd() {
        val titles=listOf(GameScreens.Word("Bách khoa",.2,.02,.4,.08),
                          GameScreens.Word("Cờ Liên Quân",.4,.03,.65,.1))
        val kept=ArrayList<String>();val finished=HashSet<String>()
        var result:ChessWikiReader.Outcome?=null
        val screen=object:ChessWikiReader.Screen {
            override val alive=true
            override fun look(words:Boolean,then:(ChessWikiReader.Look?)->Unit) {
                then(object:ChessWikiReader.Look {
                    override val words=titles
                    override val signature=IntArray(96) { 60 }
                    override val modal=false
                    override fun tiles(topic:ChessWiki.Topic)=emptyList<ChessWiki.Tile>()
                    override fun keep(name:String,then:(Boolean)->Unit) { kept.add(name);then(true) }
                    override fun discard() {}
                })
            }
            override fun tap(x:Double,y:Double,then:(Boolean)->Unit)=then(true)
            override fun swipe(x:Double,y:Double,toX:Double,toY:Double,then:(Boolean)->Unit)=then(true)
            override fun later(ms:Long,then:()->Unit)=then()
            override fun seen(id:String)=id in finished
            override fun finished(id:String) { finished.add(id) }
            override fun note(message:String) {}
            override fun say(message:String) {}
        }
        ChessWikiReader(screen,ChessWiki.Options(setOf("quy-trinh"),openGame=false),{ result=it }).start()
        assertTrue(result is ChessWikiReader.Outcome.Done)
        assertEquals(listOf("chess__thong-tin__quy-trinh__trang__001"),kept)
        assertTrue("topic:quy-trinh" in finished)
    }
    @Test fun modeEntryUsesTheRealButtonEvenNextToQuest() {
        fun w(label:String,x:Double,y:Double)=
            GameScreens.Word(label,x-.035,y-.02,x+.035,y+.02)
        val p=ChessWiki.modeEntryPoint(listOf(
            w("N.vụ",.515,.941),w("Chọn chế độ",.634,.940),w("Đấu thường",.706,.941)))
        assertNotNull(p)
        assertEquals(.634,p!!.first,.001)
        assertEquals(.895,p.second,.001)
        assertTrue(p.first>.57)
    }

    @Test fun modeEntryFallbackDoesNotReuseQuestCoordinatesWhenOcrMissesModeLabel() {
        fun w(label:String,x:Double,y:Double)=
            GameScreens.Word(label,x-.025,y-.025,x+.025,y+.025)
        val fromOtherLobbyWords=ChessWiki.modeEntryPoint(listOf(
            w("N.vụ",.515,.943),w("Túi đồ",.16,.943)))
        assertNotNull(fromOtherLobbyWords)
        assertEquals(.635,fromOtherLobbyWords!!.first,.001)
        assertEquals(.895,fromOtherLobbyWords.second,.001)
    }

    @Test fun modeEntryDoesNotTapOnUnknownScreens() {
        val unrelated=listOf(GameScreens.Word("N.vụ",.47,.20,.53,.25),
                             GameScreens.Word("Chọn",.08,.02,.14,.08))
        assertNull(ChessWiki.modeEntryPoint(unrelated))
    }

    @Test fun modeNavigationHandlesSplitVietnameseOcr() {
        fun word(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.04,y-.02,x+.04,y+.02)
        // The actual screenshot places "Chọn chế độ" to the right of N.vụ, not on its icon.
        val lobby=listOf(word("Túi đồ",.16,.942),word("N.vụ",.515,.94),
                         word("Chọn chế độ",.635,.943))
        val choose=listOf(word("Chế độ giải",.14,.34),word("trí",.14,.39),
                          word("Cờ Liên Quân",.52,.83),word("Bắt đầu",.85,.68))
        val chessLobby=listOf(word("Trang chủ",.105,.30),word("Cờ Liên Quân",.15,.05))
        val wiki=listOf(word("Bách khoa",.15,.06),word("Cờ Liên Quân",.35,.06))
        val taps=ArrayList<Pair<Double,Double>>()
        val completed=HashSet<String>()
        var stage=0;var outcome:ChessWikiReader.Outcome?=null
        val screen=object:ChessWikiReader.Screen {
            override val alive=true
            override fun look(words:Boolean,then:(ChessWikiReader.Look?)->Unit) {
                then(object:ChessWikiReader.Look {
                    override val words=when(stage) {
                        0 -> lobby;1,2 -> choose;3 -> chessLobby;else -> wiki
                    }
                    override val signature=IntArray(96) { 60 }
                    override val modal=false
                    override fun tiles(topic:ChessWiki.Topic)=emptyList<ChessWiki.Tile>()
                    override fun keep(name:String,then:(Boolean)->Unit)=then(true)
                    override fun discard() {}
                })
            }
            override fun tap(x:Double,y:Double,then:(Boolean)->Unit) {
                taps.add(x to y)
                if(stage<4) stage++
                then(true)
            }
            override fun swipe(x:Double,y:Double,toX:Double,toY:Double,then:(Boolean)->Unit)=then(true)
            override fun later(ms:Long,then:()->Unit)=then()
            override fun seen(id:String)=id in completed
            override fun finished(id:String) { completed.add(id) }
            override fun note(message:String) {}
            override fun say(message:String) {}
        }
        ChessWikiReader(screen,ChessWiki.Options(setOf("quy-trinh"),openGame=false),{ outcome=it }).start()
        assertTrue(outcome is ChessWikiReader.Outcome.Done)
        assertEquals(4,stage)
        assertEquals(ChessWiki.MODE_X,taps[0].first,.001)
        assertEquals(ChessWiki.MODE_Y,taps[0].second,.001)
        assertTrue("Must never tap N.vụ",taps[0].first>.57)
        assertEquals(.507,taps[1].first,.001)
        assertEquals(.85,taps[2].first,.05)
        assertTrue("topic:quy-trinh" in completed)
    }

}
