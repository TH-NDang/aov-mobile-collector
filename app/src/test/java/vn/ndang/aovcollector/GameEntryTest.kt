package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt

class GameEntryTest {
    /** A screenshot as ARGB pixels. */
    private class Screen(val width:Int,val height:Int,val px:IntArray) {
        val read={ y:Int,row:IntArray -> System.arraycopy(px,y*width,row,0,width) }
        fun reading()=GameScreens.read(width,height,read)
        fun close()=GameScreens.closeButton(width,height,read)
        fun copy()=Screen(width,height,px.copyOf())
    }
    /**
     * Screens on the way into the game, shot on the user's phone (2400x1080), with the UID line and the player's
     * name blanked. JPEG is decoded by the JDK's ImageIO, reached by reflection: unit tests compile against
     * android.jar, which lacks it, but run on the JDK, which has it.
     */
    private fun screen(name:String):Screen {
        System.setProperty("java.awt.headless","true")
        val image=javaClass.getResourceAsStream("/screens/game-$name.jpg")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream::class.java).invoke(null,it)!! }
        val type=image.javaClass
        val w=type.getMethod("getWidth").invoke(image) as Int; val h=type.getMethod("getHeight").invoke(image) as Int
        val get=type.getMethod("getRGB",Int::class.java,Int::class.java,Int::class.java,Int::class.java,IntArray::class.java,Int::class.java,Int::class.java)
        return Screen(w,h,get.invoke(image,0,0,w,h,null,0,w) as IntArray)
    }

    @Test fun readsEveryScreenOnTheWayIn() {
        val expected=mapOf("splash" to GameScreens.Kind.OTHER,"logo" to GameScreens.Kind.OTHER,"loading" to GameScreens.Kind.OTHER,
            "popup-fest" to GameScreens.Kind.POPUP,"popup-shop" to GameScreens.Kind.POPUP,"popup-10fest" to GameScreens.Kind.POPUP,
            "lobby" to GameScreens.Kind.LOBBY,"lobby-clear" to GameScreens.Kind.LOBBY,"hero-list" to GameScreens.Kind.LIST)
        for((name,kind) in expected) {
            val reading=screen(name).reading()
            assertEquals(name,kind,reading.kind)
            // The three popups put their X at the same place; the first was shot while still fading in, dim.
            if(kind==GameScreens.Kind.POPUP) { val (x,y)=reading.close!!; assertTrue("$name: $x,$y",abs(x-.913)<.006&&abs(y-.056)<.01) }
            else assertNull(name,reading.close)
        }
    }

    @Test fun noCloseButtonIsSeenOutsidePopups() {
        // Searched on its own, without the earlier checks that come first in read().
        for(name in listOf("splash","logo","loading","lobby","lobby-clear","hero-list")) assertNull(name,screen(name).close())
    }

    /** Draws an X with a dark backdrop, as a close button, of half size [r] px centred at ([cx], [cy]). */
    private fun Screen.drawX(cx:Int,cy:Int,r:Int,color:Int=0xFFF2C46A.toInt(),backdrop:Boolean=true) {
        if(backdrop) for(y in cy-2*r..cy+2*r) for(x in cx-2*r..cx+2*r) px[y*width+x]=0xFF141018.toInt()
        val t=maxOf(2,r/7)
        for(k in -r..r) for(d in -t..t) { px[(cy+k)*width+cx+k+d]=color; px[(cy-k)*width+cx+k+d]=color }
    }
    private fun Screen.eraseX(cx:Int,cy:Int,r:Int) { for(y in cy-r..cy+r) for(x in cx-r..cx+r) px[y*width+x]=0xFF0A0A12.toInt() }

    @Test fun findsACloseButtonFurtherIn() {
        // Some popups have their X further in, still on the right; it may be smaller or larger than the usual one.
        for((name,at) in listOf("popup-shop" to (.78 to .22),"popup-10fest" to (.62 to .35),"popup-fest" to (.95 to .40))) for(r in listOf(14,19,30)) {
            val s=screen(name).copy()
            s.eraseX((.913*s.width).roundToInt(),(.056*s.height).roundToInt(),30)
            val cx=(at.first*s.width).roundToInt(); val cy=(at.second*s.height).roundToInt()
            s.drawX(cx,cy,r)
            val (x,y)=s.close()?:error("$name r=$r: no X at $at")
            // Within a few pixels: the search moves in steps of 2 px and the best fit may sit beside the centre.
            assertTrue("$name r=$r: $x,$y",abs(x*s.width-cx)<=6&&abs(y*s.height-cy)<=6)
        }
    }

    @Test fun prefersTheButtonNearestTheTopRightCorner() {
        val s=screen("popup-shop").copy()
        s.drawX((.70*s.width).roundToInt(),(.30*s.height).roundToInt(),19)
        val (x,y)=s.close()!!
        assertTrue("$x,$y",abs(x-.913)<.006&&abs(y-.056)<.01)
    }

    @Test fun ignoresAnXThatIsPartOfAWord() {
        val s=screen("popup-shop").copy()
        s.eraseX((.913*s.width).roundToInt(),(.056*s.height).roundToInt(),30)
        val cx=(.80*s.width).roundToInt(); val cy=(.25*s.height).roundToInt(); val r=19
        s.drawX(cx,cy,r)
        // A letter right after it, as in "XỊN": the X is no longer alone.
        for(y in cy-r..cy+r) for(x in cx+r+6..cx+r+11) s.px[y*s.width+x]=0xFFF2C46A.toInt()
        assertNull(s.close())
    }

    @Test fun aDimmedLobbyIsNotTakenForTheLobby() {
        // A popup darkens the lobby under it; tapping "Tướng" through it would hit the popup.
        val s=screen("lobby").copy()
        for(i in s.px.indices) { val p=s.px[i]; s.px[i]=(0xFF shl 24) or ((((p shr 16) and 255)/4) shl 16) or ((((p shr 8) and 255)/4) shl 8) or ((p and 255)/4) }
        assertTrue(s.reading().kind!=GameScreens.Kind.LOBBY)
    }

    /** A scripted game: each look returns the next reading; time moves with every wait. */
    private class Script(val readings:List<GameScreens.Reading?>,val front:Boolean=false,val canLaunch:Boolean=true):GameEntry.Screen {
        var time=0L; var next=0; var launches=0; var backs=0
        val taps=ArrayList<Pair<Double,Double>>(); val kept=ArrayList<String>(); val pending=ArrayDeque<()->Unit>()
        var outcome:GameEntry.Outcome?=null
        override val alive get()=outcome==null
        override fun inFront()=front
        override fun launch():Boolean { launches++; return canLaunch }
        override fun look(then:(GameEntry.Shot?)->Unit) {
            val r=readings[minOf(next++,readings.size-1)]
            time+=1000   // a screenshot takes about a second
            then(r?.let { object:GameEntry.Shot { override val reading=it; override fun done(keep:String?) { if(keep!=null) kept.add(keep) } } })
        }
        override fun tap(x:Double,y:Double,then:(Boolean)->Unit) { taps.add(x to y); then(true) }
        override fun back(then:(Boolean)->Unit) { backs++; then(true) }
        override fun later(ms:Long,then:()->Unit) { time+=ms; pending.addLast(then) }
        override fun now()=time
        override fun note(text:String) {}
        override fun say(text:String) {}
        fun run():GameEntry.Outcome { GameEntry(this) { outcome=it }.start(); var guard=0
            while(outcome==null) { pending.removeFirstOrNull()?.invoke()?:error("stuck"); check(++guard<10000) }; return outcome!! }
    }
    private fun at(kind:GameScreens.Kind,close:Pair<Double,Double>?=null,look:Int=0)=GameScreens.Reading(kind,close,IntArray(288) { look })
    private val other=GameScreens.Kind.OTHER
    private val x1=.913 to .056
    private val x2=.78 to .22
    private val heroes=GameScreens.HEROES_X to GameScreens.HEROES_Y

    @Test fun opensTheGameClosesItsPopupsAndOpensTheHeroList() {
        val popup=GameScreens.Kind.POPUP
        val game=Script(listOf(null,at(other,look=0),at(other,look=40),at(popup,x1),at(popup,x1),at(popup,x2),at(popup,x2),
            at(GameScreens.Kind.LOBBY),at(GameScreens.Kind.LOBBY),at(GameScreens.Kind.LIST)))
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(1,game.launches)
        // Each X once it held still for two looks, then "Tướng" once the lobby stayed clear.
        assertEquals(listOf(x1,x2,heroes),game.taps)
        assertEquals(0,game.backs)
    }

    @Test fun doesNotOpenTheGameAgainWhenItIsInFront() {
        val game=Script(listOf(at(GameScreens.Kind.LIST)),front=true)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(0,game.launches)
    }

    @Test fun waitsForAPopupThatAppearsAfterTheLobby() {
        val popup=GameScreens.Kind.POPUP; val lobby=GameScreens.Kind.LOBBY
        val game=Script(listOf(at(lobby),at(popup,x1),at(popup,x1),at(lobby),at(lobby),at(GameScreens.Kind.LIST)),front=true)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(listOf(x1,heroes),game.taps)
    }

    @Test fun closesPopupsThatFollowEachOtherWithTheirXAtTheSamePlace() {
        // As on the user's phone: every popup puts its X at (.913, .056), and the next one shows as soon as one closes.
        val popups=(1..5).flatMap { listOf(at(GameScreens.Kind.POPUP,x1,look=it*20),at(GameScreens.Kind.POPUP,x1,look=it*20)) }
        val lobby=at(GameScreens.Kind.LOBBY)
        val game=Script(popups+listOf(lobby,lobby,at(GameScreens.Kind.LIST)),front=true)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(List(5) { x1 }+listOf(heroes),game.taps)
    }

    @Test fun stopsAtAPopupThatDoesNotClose() {
        val game=Script(listOf(at(GameScreens.Kind.POPUP,x1)),front=true)
        val outcome=game.run()
        assertTrue(outcome is GameEntry.Outcome.Failed)
        assertEquals(GameEntry.MAX_SAME_CLOSE,game.taps.size)
        assertEquals(listOf("entry-popup"),game.kept)
    }

    @Test fun stopsOnAScreenItDoesNotKnowThatStandsStill() {
        val game=Script(listOf(at(other,look=20)),front=true)
        val outcome=game.run()
        assertTrue(outcome is GameEntry.Outcome.Failed)
        assertTrue(game.time in GameEntry.STILL_MS..GameEntry.STILL_MS+10_000)
        assertEquals(listOf("entry-unknown"),game.kept)
        assertTrue(game.taps.isEmpty())
    }

    @Test fun keepsWaitingWhileTheLoadingScreenChanges() {
        val loading=(0 until 200).map { at(other,look=it%2*10) }
        val game=Script(loading+at(GameScreens.Kind.LIST),front=true)
        assertTrue(game.run() is GameEntry.Outcome.Failed)
        // Waited the whole limit, past the still-screen limit, without touching anything.
        assertTrue(game.time>GameEntry.LIMIT_MS)
        assertTrue(game.taps.isEmpty())
    }

    @Test fun leavesAnotherGamePageByItsBackArrow() {
        val game=Script(listOf(at(GameScreens.Kind.HERO_PAGE),at(GameScreens.Kind.LIST)),front=true)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(1,game.backs)
    }

    @Test fun stopsWhenTheGameDoesNotComeUp() {
        // Android said yes to opening it, but the phone kept it in the background: no screenshot of the game.
        val game=Script(listOf(null))
        assertTrue(game.run() is GameEntry.Outcome.Failed)
        assertEquals(1,game.launches)
        assertTrue(game.time in GameEntry.NO_GAME_MS..GameEntry.NO_GAME_MS+10_000)
    }

    @Test fun stopsWhenTheGameCannotBeOpened() {
        val game=Script(listOf(at(GameScreens.Kind.LIST)),canLaunch=false)
        assertTrue(game.run() is GameEntry.Outcome.Failed)
    }
}
