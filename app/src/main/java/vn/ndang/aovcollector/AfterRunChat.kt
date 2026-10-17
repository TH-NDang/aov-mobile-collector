package vn.ndang.aovcollector

import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Operates only inside ChatGPT, and persists a send attempt before touching its send button. */
class AfterRunChat(private val service:CollectorService,val run:String,private val done:()->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var ended=false
    private var deadline=0L
    private var openedMenu=false
    private var choseDot=false
    private var filled=false
    private var attempted=false
    private var command=""
    private var echoBefore=0
    private val startTime=SystemClock.uptimeMillis()
    fun start() {
        val task=AfterRun.state(service,run)?:return finish("chat-failed","Không đọc được lệnh sau lượt")
        if(!task.optBoolean("verified")||!task.optBoolean("driveProof"))
            return finish("chat-failed","Chưa có xác nhận tải Drive, chưa gửi ChatGPT")
        command=task.optString("prompt")
        if(command.isBlank()) return finish("chat-failed","Chưa lưu câu lệnh ChatGPT")
        if(task.optString("stage")=="prompt-filled") {
            // A previous process may have died just before persisting the send attempt.
            return finish("chat-failed","Lượt trước đã điền lệnh nhưng bị gián đoạn. Kiểm tra ChatGPT trước khi thử lại.")
        }
        try {
            val intent=service.packageManager.getLaunchIntentForPackage(AfterRunProtocol.CHATGPT)
                ?:error("Chưa cài app ChatGPT")
            AfterRun.update(service,run,"chat-opening","Đang mở ChatGPT → dot của bạn")
            service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            deadline=SystemClock.uptimeMillis()+60_000
            handler.postDelayed({ step() },1800)
        } catch(e:Exception) { finish("chat-failed",e.message?:"Không mở được ChatGPT") }
    }
    fun cancel() { if(!ended) finish(if(attempted) "send-uncertain" else "chat-failed","Thao tác ChatGPT bị gián đoạn; hãy kiểm tra trước khi thử lại") }
    private fun nodes(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo> {
        val result=ArrayList<AccessibilityNodeInfo>()
        fun visit(n:AccessibilityNodeInfo,depth:Int) {
            if(depth>24||result.size>=1500) return
            result.add(n)
            for(i in 0 until n.childCount) n.getChild(i)?.let { visit(it,depth+1) }
        }
        visit(root,0);return result
    }
    private fun click(n:AccessibilityNodeInfo):Boolean {
        var target:AccessibilityNodeInfo?=n
        repeat(5) {
            val node=target?:return false
            if(node.isEnabled&&node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target=node.parent
        }
        return false
    }
    private fun later() { if(!ended) handler.postDelayed({ step() },800) }
    private fun step() {
        try { readAndAct() }
        catch(e:Exception) { finish(if(attempted) "send-uncertain" else "chat-failed",
            "Chưa hoàn tất thao tác ChatGPT: "+(e.message?:"Giao diện thay đổi")) }
    }
    private fun readAndAct() {
        if(ended) return
        if(CollectorService.current!==service) return cancel()
        if(SystemClock.uptimeMillis()>deadline) return finish(if(attempted) "send-uncertain" else "chat-failed",
            if(attempted) "Đã bấm gửi nhưng chưa xác nhận được tin nhắn. Không tự gửi lại."
            else "Chưa tìm được mục dot của bạn hoặc ô nhập lệnh trong ChatGPT")
        val root=service.rootInActiveWindow
        if(root?.packageName?.toString()!=AfterRunProtocol.CHATGPT) {
            if(SystemClock.uptimeMillis()-startTime>12_000) return finish(if(attempted) "send-uncertain" else "chat-failed","ChatGPT không ở phía trước; đã dừng gửi lệnh")
            later();return
        }
        val all=nodes(root).filter { it.isVisibleToUser }
        val dot=all.firstOrNull { AfterRunProtocol.dot(it.text?.toString().orEmpty())||AfterRunProtocol.dot(it.contentDescription?.toString().orEmpty()) }
        if(!choseDot) {
            if(dot!=null) {
                if(click(dot)) { choseDot=true;openedMenu=false }
            } else if(!openedMenu) {
                val menu=all.firstOrNull { AfterRunProtocol.navigation(it.contentDescription?.toString().orEmpty()) }
                if(menu!=null) openedMenu=click(menu)
                // Known ChatGPT toolbar: a left navigation button, not a guessed item in a conversation.
                else if(all.any { it.text?.toString()=="ChatGPT" }) {
                    val bounds=Rect();root.getBoundsInScreen(bounds)
                    val path=Path().apply { moveTo(bounds.left+bounds.width()*.065f,bounds.top+bounds.height()*.045f) }
                    openedMenu=service.dispatchGesture(GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path,0,100)).build(),null,null)
                }
            }
            later();return
        }
        // The destination heading must remain visible after the menu has closed.
        val drawer=all.any { GameScreens.plain(it.text?.toString().orEmpty()) in
            setOf("khonggian","dalenglich","tuychinh","khampha") }
        if(dot==null||drawer) { later();return }
        val edits=all.filter { it.isEditable&&it.isEnabled }
        if(edits.size!=1) { later();return }
        val editor=edits.single()
        if(attempted) {
            val echoed=all.count { !it.isEditable&&it.text?.toString()==command }>echoBefore
            if(echoed&&(editor.text?.toString().orEmpty().isBlank()||editor.isShowingHintText))
                finish("done","Đã tải Drive và gửi câu lệnh vào dot của bạn")
            else later()
            return
        }
        if(!filled) {
            val existing=editor.text?.toString().orEmpty()
            if(existing.isNotBlank()&&existing!=command&& !editor.isShowingHintText)
                return finish("chat-failed","Ô nhập ChatGPT đang có nội dung khác. Giữ nguyên để bạn kiểm tra.")
            filled=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,command)
            })
            if(filled) AfterRun.update(service,run,"prompt-filled","Đã điền lệnh vào dot của bạn; đang kiểm tra nút Gửi")
            later();return
        }
        if(editor.text?.toString()!=command) return finish("chat-failed","Nội dung ô nhập đã thay đổi, chưa gửi")
        val send=all.firstOrNull { it.isEnabled&&(AfterRunProtocol.send(it.contentDescription?.toString().orEmpty())||AfterRunProtocol.send(it.text?.toString().orEmpty())) }
        if(send==null) { later();return }
        echoBefore=all.count { !it.isEditable&&it.text?.toString()==command }
        AfterRun.update(service,run,"sending","Đã ghi nhận lần gửi; không tự bấm gửi lại khi bị gián đoạn")
        attempted=true
        if(!click(send)) return finish("send-uncertain","Không xác nhận được thao tác Gửi. Kiểm tra ChatGPT; app không tự gửi lại.")
        later()
    }
    private fun finish(stage:String,message:String) {
        if(ended) return
        ended=true;handler.removeCallbacksAndMessages(null)
        AfterRun.update(service,run,stage,message);done()
    }
}
