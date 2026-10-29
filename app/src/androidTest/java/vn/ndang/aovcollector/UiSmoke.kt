package vn.ndang.aovcollector

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import org.json.JSONObject
import java.io.File

/** Runs the actual UI and bundled OCR without a game, account or network. */
class UiSmoke:Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val result=Bundle()
        try {
            val automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val info=automation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.serviceInfo=info
            // Instrumentation restarts the app process; rebind its accessibility service afterwards.
            fun shell(command:String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() } }
            shell("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(500)
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            val image=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.drawColor(Color.rgb(29,32,60))
            canvas.drawText("Eland'orr",1770f,385f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(248,226,170);textSize=58f })
            val name=HeroNames.read(image)
            check(name!=null&&HeroNames.slug(name)=="elandorr") { "OCR returned $name" }
            // Qi's name region from a real run, where ML Kit read nothing.
            val qi=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888)
            context.assets.open("qi-name.png").use { BitmapFactory.decodeStream(it) }.let { Canvas(qi).apply { drawColor(Color.rgb(20,30,50)) }.drawBitmap(it,1752f,318f,null);it.recycle() }
            val qiLog=ArrayList<String>();val qiName=HeroNames.read(qi) { qiLog+=it };qi.recycle()
            File(targetContext.filesDir,"smoke").apply { mkdirs() }.resolve("ocr-qi.txt").writeText("$qiName\n"+qiLog.joinToString("\n"))
            check(qiName=="Qi") { "OCR read $qiName for Qi: ${qiLog.joinToString("; ")}" }
            // The way into the game: a list run that opens the game starts with the entry step, and a popup shot on
            // the user's phone, fading in, is read on Android as in the unit tests.
            check(Macro(BuiltInMacros.create("",2,true,openGame=true)).steps.getJSONObject(0).getString("type")=="enter") { "Tự vào game should start with the entry step" }
            val popup=context.assets.open("game-popup-fest.jpg").use { BitmapFactory.decodeStream(it) }
            val seen=GameScreens.read(popup.width,popup.height) { y,row -> popup.getPixels(row,0,popup.width,0,y,popup.width,1) };popup.recycle()
            check(seen.kind==GameScreens.Kind.POPUP&&Math.abs(seen.close!!.first-.913)<.01&&Math.abs(seen.close!!.second-.056)<.015) { "Popup read as ${seen.kind} ${seen.close}" }
            // Screens known by their words, read by ML Kit on the user's screenshots: where the run would tap.
            fun words(asset:String):GameScreens.Reading { val b=context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                val pixels=GameScreens.read(b.width,b.height) { y,row -> b.getPixels(row,0,b.width,0,y,b.width,1) }
                return GameScreens.refine(pixels,GameText.words(b),null).also { b.recycle() } }
            val reward=words("game-reward-stars.jpg")
            check(reward.kind==GameScreens.Kind.CONTINUE&&Math.abs(reward.tap!!.first-.5)<.05&&Math.abs(reward.tap!!.second-.91)<.04) { "Reward screen read as ${reward.kind} ${reward.tap}: ${reward.words}" }
            val register=words("game-register.jpg")
            check(register.kind==GameScreens.Kind.REGISTER&&Math.abs(register.tap!!.first-.749)<.04&&Math.abs(register.tap!!.second-.194)<.04) { "Registration form read as ${register.kind} ${register.tap}: ${register.words}" }
            val form=words("game-garena-form.jpg")
            check(form.kind==GameScreens.Kind.FORM) { "Garena page read as ${form.kind}: ${form.words}" }
            // The way to the hero statistics, read by ML Kit on the user's screenshots: each page known by its words.
            val rankLog=StringBuilder()
            fun rank(asset:String):List<GameScreens.Word> { val b=context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                return GameText.words(b).also { b.recycle();rankLog.append("$asset\n").append(it.joinToString("\n") { w -> "  %.3f %.3f %s".format(w.x,w.y,w.text) }).append("\n") } }
            val more=rank("rank-more.jpg"); val community=rank("rank-community.jpg"); val heroes=rank("rank-community-heroes.jpg")
            val rates=rank("rank-ranking-rates.jpg"); val figures=rank("rank-ranking-figures.jpg"); val modes=rank("rank-ranking-modes.jpg")
            File(targetContext.filesDir,"smoke").apply { mkdirs() }.resolve("ocr-ranking.txt").writeText(rankLog.toString())
            check(RankScreens.page(more,false)==RankScreens.Page.PANEL&&more.any { it.plain.contains("congdong")&&Math.abs(it.x-.15)<.06&&Math.abs(it.y-.52)<.05 }) { "Tính năng khác read as ${RankScreens.page(more,false)}" }
            check(RankScreens.page(community,false)==RankScreens.Page.COMMUNITY) { "Cộng đồng read as ${RankScreens.page(community,false)}" }
            check(RankScreens.page(heroes,false)==RankScreens.Page.HEROES&&heroes.any { it.plain.contains("bangxephangdaydu")&&Math.abs(it.y-.082)<.04 }) { "Cộng đồng heroes read as ${RankScreens.page(heroes,false)}" }
            for((list,table) in listOf(rates to RankScreens.Table.RATES,figures to RankScreens.Table.FIGURES)) {
                check(RankScreens.page(list,false)==RankScreens.Page.RANKING&&RankScreens.table(list)==table&&RankScreens.mode(RankScreens.modeButton(list))==RankScreens.Mode.ALL) {
                    "Ranking read as ${RankScreens.page(list,false)} ${RankScreens.table(list)} ${RankScreens.mode(RankScreens.modeButton(list))}" } }
            // Chiến khu, read by ML Kit on the user's screenshots (kept at half size, the size ML Kit reads them at).
            val boardLog=StringBuilder()
            fun board(name:String):List<GameScreens.Word> { val small=context.assets.open("board-$name.jpg").use { BitmapFactory.decodeStream(it) }
                val b=Bitmap.createScaledBitmap(small,small.width*2,small.height*2,true); small.recycle()
                return GameText.words(b).also { b.recycle();boardLog.append("$name\n").append(it.joinToString("\n") { w -> "  %.3f %.3f %s".format(w.x,w.y,w.text) }).append("\n") } }
            val boardPages=listOf("ranked","ranked-new","season-memory","season-data","season-heroes","season-open","season-first","season-title","season-reward",
                "region","region-country","region-area","region-province","board-province","board-country","board-valhein","picker","trailer1","top").associateWith { board(it) }
            File(targetContext.filesDir,"smoke").resolve("ocr-board.txt").writeText(boardLog.toString())
            fun boardPage(name:String)=BoardScreens.page(boardPages.getValue(name),false)
            for(name in listOf("ranked","ranked-new")) check(boardPage(name)==BoardScreens.Page.RANKED&&BoardScreens.chienKhu(boardPages.getValue(name))!=null) { "$name read as ${boardPage(name)}" }
            for(name in listOf("season-memory","season-data","season-heroes","season-open","season-first","season-title","season-reward")) check(boardPage(name)==BoardScreens.Page.SEASON) { "$name read as ${boardPage(name)}" }
            for(name in listOf("region","region-country","region-area","region-province")) check(boardPage(name)==BoardScreens.Page.REGION) { "$name read as ${boardPage(name)}" }
            check(BoardScreens.region(boardPages.getValue("region-area")).let { it.open==1&&it.options.any { o -> o.plain.contains("mientrung") } }) { "Area list read as ${BoardScreens.region(boardPages.getValue("region-area")).options}" }
            for(name in listOf("board-province","board-country")) check(boardPage(name)==BoardScreens.Page.BOARD&&BoardScreens.hero(boardPages.getValue(name))?.let { HeroNames.slug(it) }=="flowborn") { "$name read as ${boardPage(name)} ${BoardScreens.hero(boardPages.getValue(name))}" }
            check(boardPage("picker")==BoardScreens.Page.PICKER) { "Chọn tướng read as ${boardPage("picker")}" }
            check(boardPage("board-valhein")==BoardScreens.Page.BOARD&&BoardScreens.hero(boardPages.getValue("board-valhein"))?.let { HeroNames.slug(it) }=="valhein") { "Valhein's ranking read as ${boardPage("board-valhein")} ${BoardScreens.hero(boardPages.getValue("board-valhein"))}" }
            check(boardPage("top")==BoardScreens.Page.TOP) { "Số 1 máy chủ read as ${boardPage("top")}" }
            check(boardPage("trailer1")==BoardScreens.Page.OTHER) { "Trailer read as ${boardPage("trailer1")}" }
            // Việt Nam's top three and "Số 1 máy chủ" by default; the whole server's top ten in four screens when chosen.
            check(BuiltInMacros.boardWants(Macro(BuiltInMacros.board(heroes=2)).json)==BoardScreens.DEFAULT)
            check(BuiltInMacros.boardWants(Macro(BuiltInMacros.board(heroes=2,wanted=listOf(BoardScreens.Want(BoardScreens.Tab.SERVER,10)))).json).single().pages==4)
            // Names on the All-heroes cards, read by ML Kit on the user's screenshot: the card each is on, and where to tap for one.
            val listSmall=context.assets.open("list-all.jpg").use { BitmapFactory.decodeStream(it) }
            val listShot=Bitmap.createScaledBitmap(listSmall,listSmall.width*2,listSmall.height*2,true); listSmall.recycle()
            val listWords=GameText.words(listShot).also { listShot.recycle() }
            File(targetContext.filesDir,"smoke").resolve("ocr-list.txt").writeText(listWords.joinToString("\n") { w -> "%.3f %.3f %s".format(w.x,w.y,w.text) })
            val cards=HeroFinder.cards(listWords,0.0)
            for((card,slug) in listOf(0 to "flowborn",1 to "tamyn",2 to "dyadia",3 to "flowborn",5 to "heino",6 to "biron",7 to "bolt-baron",8 to "billow",9 to "charlotte"))
                check(cards.any { it.first==card&&HeroNames.slug(it.second.text)==slug }) { "Card ${card+1} is not $slug: ${cards.map { "${it.first}=${it.second.text}" }}" }
            val bolt=HeroFinder.spot(listWords,0.0,"bolt-baron",-1)
            check(bolt!=null&&bolt.first==7&&Math.abs(bolt.second.first-.58)<.03&&Math.abs(bolt.second.second-.82)<.05) { "Bolt Baron found at $bolt" }
            // Two cards named Flowborn: the one nearer the card last seen.
            check(HeroFinder.spot(listWords,0.0,"flowborn",3)?.first==3&&HeroFinder.spot(listWords,0.0,"flowborn",0)?.first==0) { "Flowborn cards mixed up" }
            val modeButton=RankScreens.modeButton(modes)
            check(modeButton!=null&&RankScreens.option(modes,modeButton,RankScreens.Mode.ELITE)?.let { Math.abs(it.y-.444)<.03 }==true&&RankScreens.option(modes,modeButton,RankScreens.Mode.FIVE)?.let { Math.abs(it.y-.378)<.03 }==true) { "Drop-down read as $modeButton" }
            // The faint UID line at the bottom of game screens, drawn as the game draws it.
            val strip=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888)
            Canvas(strip).apply { drawColor(Color.rgb(36,40,96));drawText("UID: 17345555466552433",1070f,1072f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(96,100,140);textSize=24f;typeface=android.graphics.Typeface.SERIF }) }
            val uid=GameText.uid(strip);strip.recycle()
            check(uid=="17345555466552433") { "UID line read as $uid" }
            // The test account is kept encrypted by the phone's keystore, and read back.
            Accounts.save(targetContext,"17514483308299377","test@example.com","p@ss word")
            val account=Accounts.load(targetContext)
            check(account!=null&&account.user=="test@example.com"&&account.password=="p@ss word"&&Accounts.uid(targetContext)=="17514483308299377") { "Test account did not survive saving" }
            check(!targetContext.getSharedPreferences("test-account",0).all.values.joinToString().contains("p@ss")) { "The password is stored in clear" }
            Accounts.clear(targetContext);check(Accounts.load(targetContext)==null) { "Test account was not cleared" }
            check(HeroNames.slug("Điêu Thuyền")=="dieu-thuyen")
            // The skills are one step read at run time: at least the four icons' two tabs, more for five icons or other forms.
            val m=Macro(BuiltInMacros.create(""));check(m.photoTotal==10&&m.photosVary&&m.photoSteps.size==2)
            check(Macro(BuiltInMacros.create("",1,false,fast=true,detailOnly=true)).photoTotal==6) { "Chỉ Chi tiết should keep overview, attributes and 4 details" }
            val direct=Macro(BuiltInMacros.create("",1,false,fast=true,detailOnly=true,directSkills=true))
            check(direct.photoTotal==6&&(0 until direct.steps.length()).map { direct.steps.getJSONObject(it) }.single { it.getString("type")=="skills" }.getBoolean("directSkills")) { "Chuyển thẳng chiêu should reach the skills step" }
            check(Macro(BuiltInMacros.create("",11,true)).steps.toString().contains("\"pick\""))
            // Heroes by name: a "find" step a hero, which skips to the next hero's when the list has no such hero.
            val named=Macro(BuiltInMacros.named(listOf("Bolt Baron"," violet ","Bolt Baron"),listOf(7,-1,7)))
            val finds=(0 until named.steps.length()).filter { named.steps.getJSONObject(it).getString("type")=="find" }
            check(finds.size==2&&named.steps.getJSONObject(finds[0]).getInt("skipTo")==finds[1]&&named.steps.getJSONObject(finds[1]).getInt("skipTo")==named.steps.length()
                &&named.steps.getJSONObject(finds[0]).getInt("hint")==7&&named.steps.getJSONObject(finds[1]).getString("name")=="violet"&&BuiltInMacros.heroNames(named.json)==listOf("Bolt Baron","violet")) { "Heroes by name: ${named.raw}" }
            // A chain of tasks: those after the first start by going back to the game's lobby, or on to the hero list.
            fun first(raw:String)=Macro(raw).steps.getJSONObject(0)
            check(first(BuiltInMacros.ranking(home=true)).let { it.getString("type")=="home"&&it.getString("to")=="lobby" }) { "Thống kê in a chain should start at the lobby" }
            check(first(BuiltInMacros.board(heroes=2,home=true)).let { it.getString("type")=="home"&&it.getString("to")=="lobby" }) { "Chiến khu in a chain should start at the lobby" }
            check(first(BuiltInMacros.create("",2,true,home=true)).let { it.getString("type")=="home"&&it.getString("to")=="list" }) { "A list in a chain should start at the hero list" }
            val chainNamed=Macro(BuiltInMacros.named(listOf("Violet"),home=true))
            check(chainNamed.steps.getJSONObject(0).getString("type")=="home"&&(0 until chainNamed.steps.length()).map { chainNamed.steps.getJSONObject(it) }.single { it.getString("type")=="find" }.getInt("skipTo")==chainNamed.steps.length())
            check(first(BuiltInMacros.ranking(openGame=true)).getString("type")=="enter"&&!Macro(BuiltInMacros.ranking()).json.has("home")) { "A task alone should not change" }
            val rankRaw=BuiltInMacros.ranking(home=true)
            TaskQueue.save(targetContext,TaskQueue(listOf(BuiltInMacros.board(openGame=true),rankRaw),1))
            check(TaskQueue.load(targetContext)?.let { it.pos==1&&it.items.size==2&&it.isCurrent(Macro(rankRaw))&&it.label=="Tác vụ 2/2" }==true) { "Task chain did not survive saving" }
            TaskQueue.clear(targetContext);check(TaskQueue.load(targetContext)==null)
            // Files shared to another app: read through the share provider, nothing outside its folder.
            val shared=File(ShareProvider.dir(targetContext).apply { mkdirs() },"aov-test.zip").apply { writeBytes(ByteArray(1234) { it.toByte() }) }
            val sharedUri=ShareProvider.uri(shared)
            check(targetContext.contentResolver.openInputStream(sharedUri)!!.use { it.readBytes() }.contentEquals(shared.readBytes())) { "Shared file read back differently" }
            targetContext.contentResolver.query(sharedUri,null,null,null,null)!!.use { c -> check(c.moveToFirst()&&c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))=="aov-test.zip"&&c.getLong(c.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE))==1234L) { "Shared file's name or size" } }
            check(targetContext.contentResolver.getType(sharedUri)=="application/zip")
            check(runCatching { targetContext.contentResolver.openInputStream(android.net.Uri.parse("content://${ShareProvider.AUTHORITY}/..%2Fshared_prefs%2Fui.xml"))!!.close() }.isFailure) { "The share provider reached outside its folder" }
            ShareProvider.clear(targetContext);check(!shared.exists())
            // Thống kê tướng takes the chosen tables only; one saved before the choice existed takes all six.
            check(BuiltInMacros.rankCombos(Macro(BuiltInMacros.ranking(combos=listOf("ty-le-dau-5v5","chi-so-dau-hang-tinh-anh"))).json).map { it.slug }==listOf("ty-le-dau-5v5","chi-so-dau-hang-tinh-anh"))
            check(BuiltInMacros.rankCombos(JSONObject(BuiltInMacros.ranking()).apply { remove("rankCombos") }).size==6)
            check(BuiltInMacros.secondsPerHero(true,false,false,false) in 30.0..45.0&&BuiltInMacros.secondsPerHero(true,true,true,true) in 15.0..25.0) { "Time estimate per hero is off" }
            val run=File(targetContext.filesDir,"captures/smoke-demo").apply { mkdirs() }
            File(run,"run.json").writeText(JSONObject().put("collectionMode","all").put("label","Buổi thu thập mẫu").put("state","completed").put("updatedAt",System.currentTimeMillis()).toString())
            val hero=File(run,"elandorr").apply { mkdirs() }
            File(hero,"hero.json").writeText(JSONObject().put("name","Eland’orr").put("nameSource","ocr").toString())
            for(name in listOf("0004-hero-overview","0008-hero-attributes","0012-hero-skill-1-summary")) File(hero,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""))
            // A ZIP job left to the background: the foreground service shows its progress, Hủy cancels it, and with no
            // screen in front its result comes as a notification.
            val notifications=targetContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            fun waitFor(what:String,test:()->Boolean) { val until=SystemClock.uptimeMillis()+5000;while(!test()) { check(SystemClock.uptimeMillis()<until) { what };SystemClock.sleep(100) } }
            FileJob.start(targetContext,"Đang lưu ZIP","Lưu ZIP")
            FileJob.report(50,100,"File 1/2","run/hero/0001-hero-001-overview.png")
            waitFor("No progress notification") { notifications.activeNotifications.any { it.id==FileJob.NOTIFICATION&&it.notification.extras.getInt(android.app.Notification.EXTRA_PROGRESS)==500 } }
            var jobService:FileJobService?=null;waitFor("The ZIP job did not enter the foreground") { runOnMainSync { jobService=FileJobService.current };jobService!=null }
            targetContext.startService(Intent(targetContext,FileJobService::class.java).setAction(FileJobService.CANCEL))
            waitFor("Hủy in the notification did not cancel the job") { FileJob.cancelled }
            FileJob.finish("Đã hủy lưu ZIP; file ZIP dở đã được xóa.")
            waitFor("No result notification") { notifications.activeNotifications.let { all -> all.none { it.id==FileJob.NOTIFICATION }&&all.any { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE)=="Lưu ZIP" } } }
            runOnMainSync { jobService=FileJobService.current };check(jobService==null&&!CollectorService.libraryBusy) { "The ZIP job did not stop" }
            notifications.cancelAll()
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val dest=File(targetContext.filesDir,"smoke").apply { mkdirs() }
            fun snap(n:String) { waitForIdleSync();SystemClock.sleep(700);val b=automation.takeScreenshot()?:error("No screenshot");File(dest,"$n.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle() }
            snap("01-history")
            // Viewer: open a photo from history, step to the next one, then return to the top level.
            fun tapText(text:String) { waitForIdleSync();SystemClock.sleep(500)
                var node=automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()?:error("Missing $text")
                while(!node.isClickable) node=node.parent?:error("$text is not clickable")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $text" } }
            tapText("Buổi thu thập mẫu");tapText("Eland’orr");tapText("Tổng quan");snap("01b-viewer")
            tapText("Ảnh sau");waitForIdleSync();SystemClock.sleep(400)
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("2/3")?.isNotEmpty()==true) { "Viewer did not move to the next photo" }
            snap("01c-viewer-next")
            repeat(3) { automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);waitForIdleSync();SystemClock.sleep(500) }
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Buổi thu thập mẫu")?.isNotEmpty()==true) { "Back did not return to history" }
            shell("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(1200)
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            var service:CollectorService?=null
            for(attempt in 0..2) {
                for(i in 0..40) { runOnMainSync { service=CollectorService.current };if(service!=null) break;SystemClock.sleep(250) }
                if(service!=null) break
                shell("settings put secure enabled_accessibility_services null")
                SystemClock.sleep(1500)
                shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
                shell("settings put secure accessibility_enabled 1")
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys accessibility")).use { File(dest,"accessibility.txt").writeBytes(it.readBytes()) }
            check(service!=null) { "Accessibility service not connected" }
            // A built-in macro saved by an older version is regenerated when a new run is created.
            MacroStore.current(targetContext).writeText(JSONObject(BuiltInMacros.create("Danh sách tướng",2,true)).apply { remove("builtinVersion") }.toString())
            runOnMainSync { service!!.reset() }
            val upgraded=Macro(MacroStore.current(targetContext).readText())
            check(upgraded.json.optInt("builtinVersion")==BuiltInMacros.VERSION&&upgraded.json.optInt("heroCount")==2) { "Old built-in macro was not upgraded" }
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""));runOnMainSync { service!!.reset() }
            runOnMainSync { service!!.showPanel() }
            // Exercise the actual chooser button, repeatedly, then after rotation.
            fun chooserClick() {
                waitForIdleSync();SystemClock.sleep(500)
                val node=automation.windows.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("Lấy thông tin tướng") }.firstOrNull { it.contentDescription?.toString()=="Chọn macro, có tìm kiếm" }?:error("Missing chooser button")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(250)
            }
            repeat(3) { chooserClick();chooserClick() }
            runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE };SystemClock.sleep(1200)
            chooserClick();snap("02-task-picker")
            fun nodes()=automation.windows.mapNotNull { it.root }
            fun click(text:String) { val node=nodes().flatMap { it.findAccessibilityNodeInfosByText(text) }.firstOrNull { it.text?.toString()?.equals(text,true)==true }?:error("Missing $text");check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)||node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);waitForIdleSync();SystemClock.sleep(500) }
            // Search the actual floating dropdown before selecting.
            fun findEdit(n:AccessibilityNodeInfo):AccessibilityNodeInfo? { if(n.className?.toString()=="android.widget.EditText") return n;for(i in 0 until n.childCount) { val child=n.getChild(i)?:continue;val e=findEdit(child);if(e!=null) return e };return null }
            val search=nodes().firstNotNullOfOrNull { findEdit(it) }?:error("Missing macro search")
            check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"lay thong tin") }))
            waitForIdleSync();SystemClock.sleep(400);snap("02b-search")
            // Heroes by name: one kept from an earlier run, ticked from the search, and a new name typed and added.
            File(targetContext.filesDir,HeroIndex.FILE).writeText("3\tFlowborn\n7\tBolt Baron\n-1\tViolet\n")
            targetContext.getSharedPreferences("ui",0).edit().putInt("heroMode",0).remove("pickNames").commit()
            click("Lấy thông tin tướng");SystemClock.sleep(800);snap("03-hero-form")
            click("Theo tên tướng")
            fun edits(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo> =ArrayList()):List<AccessibilityNodeInfo> { if(n.className?.toString()=="android.widget.EditText") out+=n;for(i in 0 until n.childCount) n.getChild(i)?.let { edits(it,out) };return out }
            fun nameField()=nodes().flatMap { edits(it) }.firstOrNull { it.hintText?.toString()?.startsWith("Gõ tên tướng")==true }?:error("No hero name search")
            fun type(text:String) { check(nameField().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) }));waitForIdleSync();SystemClock.sleep(500) }
            type("bolt");click("Bolt Baron")
            type("Qi");click("Thêm tướng: Qi");snap("03b-hero-names")
            click("Chọn tác vụ")
            val picked=Macro(MacroStore.current(targetContext).readText())
            val firstFind=(0 until picked.steps.length()).map { picked.steps.getJSONObject(it) }.first { it.getString("type")=="find" }
            check(picked.json.optString("collectionMode")=="pick"&&BuiltInMacros.heroNames(picked.json)==listOf("Bolt Baron","Qi")&&firstFind.getInt("hint")==7) { "Heroes by name chose ${picked.raw.take(400)}" }
            // The hero page open now, with its name typed.
            chooserClick();check(nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"lay thong tin") }))
            waitForIdleSync();SystemClock.sleep(400)
            click("Lấy thông tin tướng");SystemClock.sleep(800);click("Tướng đang mở")
            val single=nodes().flatMap { edits(it) }.firstOrNull { it.hintText?.toString()=="Để trống để tự đọc tên" }?:error("No name input")
            check(single.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Violet") }))
            click("Chọn tác vụ")
            check(Macro(MacroStore.current(targetContext).readText()).json.let { it.optString("heroName")=="Violet"&&it.optString("collectionMode")=="single" })
            snap("04-panel")
            // No run yet: Dừng has nothing to end.
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Dừng") }.firstOrNull { it.text?.toString()=="Dừng" }?.isEnabled==false) { "Dừng is enabled before any run" }
            runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE };SystemClock.sleep(1500);snap("05-landscape")
            // v0.3.1 regression: a macro tap under the floating panel pressed its hidden buttons
            // (Đóng removed the panel mid-run) instead of reaching the game.
            fun closeButton()=nodes().flatMap { it.findAccessibilityNodeInfosByText("Đóng") }.firstOrNull { it.text?.toString()=="Đóng" }
            val box=Rect().also { (closeButton()?:error("Missing panel close button")).getBoundsInScreen(it) }
            fun tapAt()=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(box.exactCenterX(),box.exactCenterY()) },0,CollectorService.TAP_MS)).build()
            // Old behaviour, recorded only: hide the panel and inject in the same frame.
            runOnMainSync { service!!.panelView?.visibility=View.INVISIBLE;service!!.dispatchGesture(tapAt(),null,null) }
            SystemClock.sleep(1500);var legacyClosed=false
            runOnMainSync { legacyClosed=service!!.panelView==null;if(legacyClosed) service!!.showPanel() else service!!.panelView?.visibility=View.VISIBLE }
            waitForIdleSync();SystemClock.sleep(800)
            val finished=java.util.concurrent.CountDownLatch(1);val delivered=java.util.concurrent.atomic.AtomicBoolean(false)
            runOnMainSync { service!!.touchGame(tapAt(),true) { delivered.set(it);finished.countDown() } }
            check(finished.await(8,java.util.concurrent.TimeUnit.SECONDS)) { "Macro tap never finished" }
            waitForIdleSync();SystemClock.sleep(600)
            var panelKept=false;var swallowed=0
            runOnMainSync { panelKept=service!!.panelView?.visibility==View.VISIBLE;swallowed=service!!.swallowedTaps }
            snap("06-after-macro-tap")
            check(delivered.get()) { "Macro tap was not delivered past the panel (swallowed=$swallowed)" }
            check(panelKept&&closeButton()!=null) { "Macro tap closed or hid the floating panel" }
            // Before a run opens the game, a game left running is ended on its App info page: Buộc dừng, then OK.
            // Checked against the emulator's real Settings, on one of its own apps (English labels there).
            fun output(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { String(it.readBytes()) }.trim()
            val installed=output("pm list packages")
            val (app,label)=listOf("com.android.deskclock" to "Clock","com.android.calculator2" to "Calculator","com.android.contacts" to "Contacts","com.android.camera2" to "Camera")
                .firstOrNull { installed.contains("package:${it.first}\n")||installed.endsWith("package:${it.first}") }?:error("No emulator app to close: $installed")
            shell("monkey -p $app -c android.intent.category.LAUNCHER 1");SystemClock.sleep(3000)
            check(output("pidof $app").isNotEmpty()) { "$app did not start" }
            fun close():GameCloser.Result? { val latch=java.util.concurrent.CountDownLatch(1);var r:GameCloser.Result?=null
                runOnMainSync { service!!.closeApp(app,{ true },label) { r=it;latch.countDown() } };latch.await(30,java.util.concurrent.TimeUnit.SECONDS);return r }
            val closed=close();snap("07-force-stop")
            check(closed==GameCloser.Result.CLOSED&&output("pidof $app").isEmpty()) { "Force stop of $app: $closed, pid ${output("pidof $app")}" }
            val again=close()
            check(again==GameCloser.Result.NOT_RUNNING) { "A stopped $app read as $again" }
            // Giao diện Tối: the history and a task form over the game in the dark look.
            targetContext.getSharedPreferences("ui",0).edit().putString("theme","dark").commit()
            startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            fun luma():Double { waitForIdleSync();SystemClock.sleep(700);val shot=automation.takeScreenshot()?:error("No screenshot");val b=shot.copy(Bitmap.Config.ARGB_8888,false);shot.recycle()
                var sum=0L;var n=0;for(y in 0 until b.height step 12) for(x in 0 until b.width step 12) { val p=b.getPixel(x,y);sum+=(299*Color.red(p)+587*Color.green(p)+114*Color.blue(p))/1000;n++ };b.recycle();return sum.toDouble()/n }
            check(Ui.colors===Ui.DARK) { "Giao diện Tối was not taken" }
            val darkHistory=luma();snap("08-dark-history")
            check(darkHistory<90) { "The dark history is not dark: mean brightness $darkHistory" }
            runOnMainSync { service!!.showPanel() };chooserClick()
            check(nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"lay thong tin") }))
            waitForIdleSync();SystemClock.sleep(400);snap("09-dark-picker")
            click("Lấy thông tin tướng");SystemClock.sleep(800)
            val darkForm=luma();snap("10-dark-form")
            check(darkForm<90) { "The dark task form is not dark: mean brightness $darkForm" }
            click("Hủy")
            // Chạy nhiều tác vụ: the three tasks to tick, each with what its form chose last.
            chooserClick()
            check(nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"nhieu tac vu") }))
            waitForIdleSync();SystemClock.sleep(400)
            click("Chạy nhiều tác vụ");SystemClock.sleep(800);snap("11-dark-chain")
            for(task in listOf("Lấy thông tin tướng ·","Thống kê tướng ·","Chiến khu ·")) check(nodes().flatMap { it.findAccessibilityNodeInfosByText(task) }.any { it.className?.toString()=="android.widget.CheckBox" }) { "No $task in Chạy nhiều tác vụ" }
            click("Hủy")
            targetContext.getSharedPreferences("ui",0).edit().remove("theme").commit()
            result.putString("stream","SMOKE_OK: history, viewer, macro upgrade, OCR, task selection, overlay, rotation, tap-through, force stop and dark look passed (legacyTapClosedPanel=$legacyClosed, swallowedRetries=$swallowed)\n")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) { result.putString("stream","SMOKE_FAILED: ${t.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result) }
    }
}
