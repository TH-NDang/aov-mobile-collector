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
}
