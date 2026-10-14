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
            "lobby" to GameScreens.Kind.LOBBY,"lobby-clear" to GameScreens.Kind.LOBBY,"hero-list" to GameScreens.Kind.LIST,
            // A long-unplayed account: the trailer, three reward screens to tap through, then their summary with an X.
            "trailer" to GameScreens.Kind.POPUP,"reward-stars" to GameScreens.Kind.OTHER,"reward-match" to GameScreens.Kind.OTHER,
            "reward-skins" to GameScreens.Kind.OTHER,"reward-summary" to GameScreens.Kind.POPUP,"lobby-new" to GameScreens.Kind.LOBBY,
            "login" to GameScreens.Kind.LOGIN,"profile" to GameScreens.Kind.HERO_PAGE,"uid-dialog" to GameScreens.Kind.POPUP,"garena-form" to GameScreens.Kind.OTHER)
        for((name,kind) in expected) {
            val reading=screen(name).reading()
            assertEquals(name,kind,reading.kind)
            // The three popups put their X at the same place; the first was shot while still fading in, dim.
            val close=mapOf("trailer" to (.940 to .108),"reward-summary" to (.909 to .094),"uid-dialog" to (.720 to .266))[name]?:(.913 to .056)
            if(kind==GameScreens.Kind.POPUP) { val (x,y)=reading.close!!; assertTrue("$name: $x,$y",abs(x-close.first)<.008&&abs(y-close.second)<.012) }
            else assertNull(name,reading.close)
        }
    }

    @Test fun aWhiteFormOverTheLobbyIsNotTheLobby() {
        // The registration form covers three of the four tab icons with white: tapping "Tướng" there would hit the form.
        val reading=screen("register").reading()
        assertTrue(reading.kind.toString(),reading.kind!=GameScreens.Kind.LOBBY)
        // Its own words make it the registration form, with "Đ.KÝ SAU" (later) to tap.
        val form=GameScreens.refine(reading,listOf(word("Đăng ký thông tin người dùng",.52,.194),word("Đ.KÝ SAU",.749,.194),word("Họ Và Tên",.29,.373)),null)
        assertEquals(GameScreens.Kind.REGISTER,form.kind)
        assertEquals(.749,form.tap!!.first,.001)
    }

    @Test fun wordsNameTheScreensPixelsCannot() {
        val blank=at(other)
        assertEquals(GameScreens.Kind.CONTINUE,GameScreens.refine(blank,listOf(word("Trận đầu nhận thưởng lớn",.5,.09),word("Ấn để tiếp tục",.5,.91)),null).kind)
        // "Tiếp tục" elsewhere, as on a purchase dialog, is not the reward screens' line.
        assertEquals(GameScreens.Kind.OTHER,GameScreens.refine(blank,listOf(word("Tiếp tục",.5,.6)),null).kind)
        assertEquals(GameScreens.Kind.FORM,GameScreens.refine(blank,listOf(word("Đăng nhập",.49,.23),word("Mật khẩu",.1,.5),word("Quên mật khẩu?",.87,.62)),null).kind)
        assertEquals(GameScreens.Kind.PROFILE,GameScreens.refine(at(GameScreens.Kind.HERO_PAGE),listOf(word("Thông tin kiện tướng",.21,.056),word("UID",.35,.45)),null).kind)
        val box=GameScreens.refine(at(GameScreens.Kind.POPUP,.72 to .27),listOf(word("Xem UID",.31,.27),word("UID của bạn là",.5,.43),word("17514483308299377",.5,.5)),null)
        assertEquals(GameScreens.Kind.UID_DIALOG,box.kind)
        assertEquals("17514483308299377",GameScreens.uidIn("UID: 1751 4483 3082 99377"))
        assertNull(GameScreens.uidIn("Lv.12"))
    }

    @Test fun noCloseButtonIsSeenOutsidePopups() {
        // Searched on its own, without the earlier checks that come first in read().
        for(name in listOf("splash","logo","loading","lobby","lobby-clear","hero-list","lobby-new","login","reward-stars","reward-match","reward-skins","garena-form")) assertNull(name,screen(name).close())
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
    /** A scripted game: each look returns the next reading (the last one again at the end); time moves with every wait. */
    private class Script(val readings:List<GameScreens.Reading?>,val canLaunch:Boolean=true,val uid:String?=null,val signs:Boolean=true,
                         val quitting:GameCloser.Result=GameCloser.Result.NOT_RUNNING):GameEntry.Screen {
        var time=0L; var next=0; var launches=0; var backs=0; var signIns=0; var quits=0
        val notes=ArrayList<String>()
        val taps=ArrayList<Pair<Double,Double>>(); val kept=ArrayList<String>(); val pending=ArrayDeque<()->Unit>()
        /** What the run recorded at each step, with "+" when the screenshot was kept with it. */
        val recorded=ArrayList<String>()
        var outcome:GameEntry.Outcome?=null
        override val alive get()=outcome==null
        override fun quit(then:(GameCloser.Result)->Unit) { check(launches==0) { "quit after launch" }; quits++; then(quitting) }
        override fun launch():Boolean { launches++; return canLaunch }
        override fun look(words:Boolean,uid:Boolean,then:(GameEntry.Shot?)->Unit) {
            val r=readings[minOf(next++,readings.size-1)]
            time+=1000   // a screenshot takes about a second
            then(r?.let { object:GameEntry.Shot { override val reading=it
                override fun record(what:String,tap:Pair<Double,Double>?,image:Boolean) { recorded.add((if(image) "+" else "")+what) }
                override fun done(keep:String?) { if(keep!=null) kept.add(keep) } } })
        }
        override fun tap(x:Double,y:Double,then:(Boolean)->Unit) { taps.add(x to y); then(true) }
        override fun back(then:(Boolean)->Unit) { backs++; then(true) }
        override fun signIn():Boolean { signIns++; return signs }
        override fun later(ms:Long,then:()->Unit) { time+=ms; pending.addLast(then) }
        override fun now()=time
        override fun note(text:String) { notes.add(text) }
        override fun say(text:String) {}
        fun run(toLobby:Boolean=false):GameEntry.Outcome { GameEntry(this,uid,toLobby) { outcome=it }.start(); var guard=0
            while(outcome==null) { pending.removeFirstOrNull()?.invoke()?:error("stuck"); check(++guard<10000) }; return outcome!! }
    }
    private fun at(kind:GameScreens.Kind,close:Pair<Double,Double>?=null,look:Int=0,words:List<GameScreens.Word> =emptyList(),uid:String?=null,tap:Pair<Double,Double>?=null)=
        GameScreens.Reading(kind,close,IntArray(288) { look },words,uid,tap)
    private fun word(text:String,x:Double,y:Double)=GameScreens.Word(text,x-.03,y-.02,x+.03,y+.02)
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

    @Test fun endsTheGameLeftRunningBeforeOpeningIt() {
        // Still in the recent apps, the game would come back wherever it was left: it is ended first, then opened.
        val game=Script(listOf(at(GameScreens.Kind.LIST)),quitting=GameCloser.Result.CLOSED)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(1,game.quits); assertEquals(1,game.launches)
        assertTrue(game.notes.first().startsWith("Đã buộc dừng"))
        // Settings may not show the button: the game is opened as before.
        val anyway=Script(listOf(at(GameScreens.Kind.LIST)),quitting=GameCloser.Result.NO_BUTTON)
        assertTrue(anyway.run() is GameEntry.Outcome.Ready); assertEquals(1,anyway.launches)
    }

    @Test fun stopsAtTheLobbyForTheHeroStatistics() {
        val game=Script(listOf(at(GameScreens.Kind.POPUP,x1),at(GameScreens.Kind.POPUP,x1),at(GameScreens.Kind.LOBBY),at(GameScreens.Kind.LOBBY)))
        assertTrue(game.run(toLobby=true) is GameEntry.Outcome.Ready)
        assertEquals(listOf(x1),game.taps)
        // Already on the hero list: back to the lobby.
        val listed=Script(listOf(at(GameScreens.Kind.LIST),at(GameScreens.Kind.LOBBY),at(GameScreens.Kind.LOBBY)))
        assertTrue(listed.run(toLobby=true) is GameEntry.Outcome.Ready); assertEquals(1,listed.backs); assertTrue(listed.taps.isEmpty())
    }

    @Test fun waitsForAPopupThatAppearsAfterTheLobby() {
        val popup=GameScreens.Kind.POPUP; val lobby=GameScreens.Kind.LOBBY
        val game=Script(listOf(at(lobby),at(popup,x1),at(popup,x1),at(lobby),at(lobby),at(GameScreens.Kind.LIST)))
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(listOf(x1,heroes),game.taps)
    }

    @Test fun closesPopupsThatFollowEachOtherWithTheirXAtTheSamePlace() {
        // As on the user's phone: every popup puts its X at (.913, .056), and the next one shows as soon as one closes.
        val popups=(1..5).flatMap { listOf(at(GameScreens.Kind.POPUP,x1,look=it*20),at(GameScreens.Kind.POPUP,x1,look=it*20)) }
        val lobby=at(GameScreens.Kind.LOBBY)
        val game=Script(popups+listOf(lobby,lobby,at(GameScreens.Kind.LIST)))
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(List(5) { x1 }+listOf(heroes),game.taps)
    }

    @Test fun stopsAtAPopupThatDoesNotClose() {
        val game=Script(listOf(at(GameScreens.Kind.POPUP,x1)))
        val outcome=game.run()
        assertTrue(outcome is GameEntry.Outcome.Failed)
        assertEquals(GameEntry.MAX_SAME_CLOSE,game.taps.size)
        assertEquals(listOf("entry-popup"),game.kept)
    }

    @Test fun stopsOnAScreenItDoesNotKnowThatStandsStill() {
        val game=Script(listOf(at(other,look=20)))
        val outcome=game.run()
        assertTrue(outcome is GameEntry.Outcome.Failed)
        assertTrue(game.time in GameEntry.STILL_MS..GameEntry.STILL_MS+10_000)
        assertEquals(listOf("entry-unknown"),game.kept)
        assertTrue(game.taps.isEmpty())
    }

    @Test fun keepsWaitingWhileTheLoadingScreenChanges() {
        val loading=(0 until 200).map { at(other,look=it%2*10) }
        val game=Script(loading+at(GameScreens.Kind.LIST))
        assertTrue(game.run() is GameEntry.Outcome.Failed)
        // Waited the whole limit, past the still-screen limit, without touching anything.
        assertTrue(game.time>GameEntry.LIMIT_MS)
        assertTrue(game.taps.isEmpty())
    }

    @Test fun leavesAnotherGamePageByItsBackArrow() {
        val game=Script(listOf(at(GameScreens.Kind.HERO_PAGE),at(GameScreens.Kind.LIST)))
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

    private val lobby=GameScreens.Kind.LOBBY
    private val listed=GameScreens.Kind.LIST
    private val garena=GameScreens.GARENA_X to GameScreens.GARENA_Y

    @Test fun getsAnUnplayedAccountThroughLoginTrailerAndRewards() {
        val popup=GameScreens.Kind.POPUP; val more=GameScreens.Kind.CONTINUE
        val skip=.749 to .194; val go=.5 to .91; val video=.94 to .108; val summary=.909 to .094
        val game=Script(listOf(at(GameScreens.Kind.LOGIN,tap=garena),at(other,look=10),at(GameScreens.Kind.REGISTER,tap=skip),
            at(popup,video,look=30),at(popup,video,look=30),at(more,look=40,tap=go),at(more,look=41,tap=go),at(more,look=42,tap=go),
            at(popup,summary,look=50),at(popup,summary,look=50),at(lobby),at(lobby),at(listed)))
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(listOf(garena,skip,video,go,go,go,summary,heroes),game.taps)
        assertEquals(0,game.signIns)
        // Every decision is recorded, with its screenshot.
        assertTrue(game.recorded.toString(),game.recorded.count { it.startsWith("+") }>=game.taps.size)
    }

    @Test fun goesOnWhenTheAccountIsTheTestAccount() {
        val game=Script(listOf(at(lobby,uid="17514483308299377"),at(lobby,uid="17514483308299377"),at(listed)),uid="17514483308299377")
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(listOf(heroes),game.taps)
    }

    @Test fun switchesToTheTestAccount() {
        val test="17514483308299377"; val other="17345555466552433"
        val settings=at(GameScreens.Kind.POPUP,.9 to .1,words=listOf(word("Cài đặt",.5,.06),word("Đăng xuất",.12,.86)))
        val confirm=at(GameScreens.Kind.POPUP,.8 to .2,words=listOf(word("Bạn có chắc muốn đăng xuất?",.5,.45),word("Hủy",.4,.7),word("Xác nhận",.6,.7)))
        val form=at(GameScreens.Kind.FORM,words=listOf(word("Mật khẩu",.1,.5),word("Quên mật khẩu?",.87,.62)))
        val game=Script(listOf(at(lobby,uid=other),at(lobby,uid=other),settings,confirm,at(GameScreens.Kind.OTHER,look=5),
            at(GameScreens.Kind.LOGIN,tap=garena),form,at(GameScreens.Kind.OTHER,look=60),at(lobby,uid=test),at(lobby,uid=test),at(listed)),uid=test)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(listOf(GameScreens.SETTINGS_X to GameScreens.SETTINGS_Y,.12 to .86,.6 to .7,garena,heroes),game.taps)
        assertEquals(1,game.signIns)
        // The Garena page is recorded without its screenshot: it shows the account typed in.
        assertTrue(game.recorded.toString(),game.recorded.any { !it.startsWith("+")&&it.contains("Garena") })
    }

    @Test fun readsTheUidInTheProfileWhenTheLobbyLineIsUnreadable() {
        val test="17514483308299377"
        val profile=at(GameScreens.Kind.PROFILE,words=listOf(word("Thông tin kiện tướng",.21,.056),word("UID",.35,.45)))
        val box=at(GameScreens.Kind.UID_DIALOG,.72 to .266,words=listOf(word("UID của bạn là",.5,.43),word(test,.5,.5)))
        val game=Script(listOf(at(lobby),at(lobby),at(lobby),at(lobby),profile,box,profile,at(lobby),at(lobby),at(listed)),uid=test)
        assertTrue(game.run() is GameEntry.Outcome.Ready)
        assertEquals(listOf(GameScreens.AVATAR_X to GameScreens.AVATAR_Y,.35 to .45,.72 to .266,heroes),game.taps)
        assertEquals(1,game.backs)
    }

    @Test fun stopsWhenTheGameComesBackOnTheWrongAccount() {
        // After one logout the game logged the old account back in without its Garena page: no second round.
        val other="17345555466552433"
        val settings=at(GameScreens.Kind.POPUP,.9 to .1,words=listOf(word("Đăng xuất",.12,.86)))
        val confirm=at(GameScreens.Kind.POPUP,.8 to .2,words=listOf(word("Xác nhận",.6,.7)))
        val game=Script(listOf(at(lobby,uid=other),at(lobby,uid=other),settings,confirm,at(GameScreens.Kind.LOGIN,tap=garena),
            at(lobby,uid=other),at(lobby,uid=other)),uid="17514483308299377")
        assertTrue(game.run() is GameEntry.Outcome.Failed)
        assertEquals(listOf("entry-uid"),game.kept)
    }

    @Test fun doesNotTypeIntoAGarenaPageItDidNotAskFor() {
        // Without a test account to check, the Garena page means the player has to log in by hand.
        val form=at(GameScreens.Kind.FORM,words=listOf(word("Mật khẩu",.1,.5),word("Quên mật khẩu?",.87,.62)))
        val game=Script(listOf(at(GameScreens.Kind.LOGIN,tap=garena),form))
        assertTrue(game.run() is GameEntry.Outcome.Failed)
        assertEquals(0,game.signIns)
    }
}
