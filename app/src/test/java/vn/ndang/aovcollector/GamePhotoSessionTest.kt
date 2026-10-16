package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class GamePhotoSessionTest {
    private val t=1_790_000_000_000L
    private fun file(id:String,modified:Long)=GamePhotoSession.Scanned(id,14500L,modified)

    @Test fun onlyNewConfirmedFilesAreAssignedAndClassifiedFromSaveEvents() {
        val old=setOf("personal-1","personal-2","personal-3","old-chess")
        val photos=listOf(file("personal-1",t-50_000),file("personal-2",t-20_000),
            file("personal-3",t-2_000),file("old-chess",t-3_000),
            file("new-violet",t+1_000),file("new-skill",t+3_000),
            file("someone-else",t+500_000))
        val marks=listOf(GamePhotoSession.Mark("quan-co","violet",t),
            GamePhotoSession.Mark("chieu-quan-co","violet-skill-1",t+2_500))
        val diff=GamePhotoSession.diff(old,photos,marks)
        assertEquals(4,diff.before)
        assertEquals(7,diff.after)
        assertEquals(3,diff.new)
        assertEquals(1,diff.unrelated)
        assertEquals(mapOf("new-violet" to "quan-co","new-skill" to "chieu-quan-co"),
            diff.matching.associate { it.id to it.group })
        assertFalse(diff.matching.any { it.id.startsWith("personal")||it.id=="someone-else" })
    }

    @Test fun newPicturesWithoutSaveEventsMustNotBeImported() {
        val existing=setOf("photo1","photo2")
        val new=listOf(file("photo1",t),file("photo2",t),file("another-photo",t+1000))
        val result=GamePhotoSession.diff(existing,new,emptyList())
        assertEquals(3,result.after)
        assertEquals(1,result.new)
        assertEquals(1,result.unrelated)
        assertTrue(result.matching.isEmpty())
    }

    @Test fun aMissingTimestampIsOnlyMatchedWhenCountsAgree() {
        val marks=listOf(GamePhotoSession.Mark("phe","phe-1",t),
            GamePhotoSession.Mark("ne","wrong",t+1000))
        val fallback=GamePhotoSession.diff(emptySet(),
            listOf(file("game-1",0),file("game-2",0)),
            listOf(GamePhotoSession.Mark("phe","phe-1",t),
                   GamePhotoSession.Mark("phe","phe-2",t+1000)))
        assertEquals(2,fallback.matching.size)
        assertEquals(setOf("phe"),fallback.matching.map { it.group }.toSet())
        val crossGroup=GamePhotoSession.diff(emptySet(),
            listOf(file("game-1",0),file("game-2",0)),
            listOf(GamePhotoSession.Mark("phe","phe-1",t),
                   GamePhotoSession.Mark("nghe","nghe-1",t+1000)))
        assertTrue(crossGroup.matching.isEmpty())
        assertEquals(2,crossGroup.unrelated)
        val ambiguous=GamePhotoSession.diff(emptySet(),listOf(file("one",0),file("two",0),file("three",0)),
            marks.take(1))
        assertTrue(ambiguous.matching.isEmpty())
        assertEquals(3,ambiguous.unrelated)
    }

    @Test fun chronologicallyNearbyPersonalImageCannotConsumeTwoSaveEvents() {
        val photos=listOf(file("native-1",t+500),file("native-2",t+2000))
        val marks=listOf(GamePhotoSession.Mark("trang-bi","quyen-truong",t),
            GamePhotoSession.Mark("chuc-phuc-thuong","buff",t+1500))
        val result=GamePhotoSession.diff(emptySet(),photos,marks)
        assertEquals(2,result.matching.size)
        assertEquals(2,result.matching.map { it.id }.distinct().size)
        assertEquals(setOf("trang-bi","chuc-phuc-thuong"),result.matching.map { it.group }.toSet())
    }

    @Test fun everyNativeClassMapsToTheCorrectEncyclopediaTab() {
        assertEquals(setOf("tat-ca-quan-co","phe","nghe","tat-ca-trang-bi",
            "thuong","huyen-thoai-su-thi","truyen-thuyet"),
            ChessWiki.topicsForNative(setOf("quan-co","chieu-quan-co","phe","nghe","trang-bi",
                "chuc-phuc-thuong","chuc-phuc-huyen-thoai-su-thi","chuc-phuc-truyen-thuyet")))
        val all=ChessWiki.Options(setOf("tat-ca-quan-co"),nativeGroups=setOf("chieu-quan-co"))
        assertEquals("tuong",ChessWiki.detailType(ChessWiki.TOPICS.first { it.key=="tat-ca-quan-co" },all))
    }

    @Test fun onlyExactSavePhotoDialogCanBeConfirmed() {
        fun w(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.03,y-.02,x+.03,y+.02)
        val screenshot=listOf(w("Save Photo",.35,.42),w("Sure",.391,.56),w("CANCEL",.58,.56))
        val yes=ChessWiki.nativeConfirm(screenshot)
        assertNotNull(yes);assertEquals(.391,yes!!.first,.001)
        assertNull(ChessWiki.nativeConfirm(listOf(w("Sure",.391,.56),w("CANCEL",.58,.56))))
        assertNull(ChessWiki.nativeConfirm(listOf(w("Save Photo",.35,.42),w("Sure",.391,.56))))
    }
}
