package vn.ndang.aovcollector

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
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
            check(HeroNames.slug("Điêu Thuyền")=="dieu-thuyen")
            val m=Macro(BuiltInMacros.create(""));check(m.photoSteps.size==10)
            check(Macro(BuiltInMacros.create("",1,false,fast=true,detailOnly=true)).photoSteps.size==6) { "Chỉ Chi tiết should keep overview, attributes and 4 details" }
            check(Macro(BuiltInMacros.create("",11,true)).steps.toString().contains("\"pick\""))
            val run=File(targetContext.filesDir,"captures/smoke-demo").apply { mkdirs() }
            File(run,"run.json").writeText(JSONObject().put("collectionMode","all").put("label","Buổi thu thập mẫu").put("state","completed").put("updatedAt",System.currentTimeMillis()).toString())
            val hero=File(run,"elandorr").apply { mkdirs() }
            File(hero,"hero.json").writeText(JSONObject().put("name","Eland’orr").put("nameSource","ocr").toString())
            for(name in listOf("0004-hero-overview","0008-hero-attributes","0012-hero-skill-1-summary")) File(hero,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""))
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
            result.putString("stream","SMOKE_OK: history, viewer, macro upgrade, OCR, task selection, overlay, rotation and tap-through passed (legacyTapClosedPanel=$legacyClosed, swallowedRetries=$swallowed)\n")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) { result.putString("stream","SMOKE_FAILED: ${t.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result) }
    }
}
