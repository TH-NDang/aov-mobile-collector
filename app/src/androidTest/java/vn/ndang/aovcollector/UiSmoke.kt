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
                val node=automation.windows.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("Một tướng") }.firstOrNull { it.contentDescription?.toString()=="Chọn macro, có tìm kiếm" }?:error("Missing chooser button")
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
            check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"mot tuong") }))
            waitForIdleSync();SystemClock.sleep(400);snap("02b-search")
            click("Một tướng đang mở");snap("03-single-form")
            fun edit(n:AccessibilityNodeInfo):AccessibilityNodeInfo? { if(n.className?.toString()=="android.widget.EditText") return n;for(i in 0 until n.childCount) { val child=n.getChild(i)?:continue;val e=edit(child);if(e!=null) return e };return null }
            val field=nodes().firstNotNullOfOrNull { edit(it) }?:error("No name input")
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Violet") }))
            click("Chọn tác vụ")
            check(Macro(MacroStore.current(targetContext).readText()).json.optString("heroName")=="Violet")
            snap("04-panel")
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
            result.putString("stream","SMOKE_OK: history, viewer, macro upgrade, OCR, task selection, overlay, rotation, tap-through and force stop passed (legacyTapClosedPanel=$legacyClosed, swallowedRetries=$swallowed)\n")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) { result.putString("stream","SMOKE_FAILED: ${t.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result) }
    }
}
