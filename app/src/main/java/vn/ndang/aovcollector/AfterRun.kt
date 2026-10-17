package vn.ndang.aovcollector

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Durable post-run queue. A SAF write alone is never treated as a completed cloud upload. */
object AfterRun {
    const val REQUEST_FOLDER=16
    const val REQUEST_ZIP=17
    const val OPEN="after-run-settings"
    private const val PREFS="after-run"
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var queued=false
    private var writing=false
    data class Config(val enabled:Boolean,val tree:String,val chat:Boolean,val prompt:String)
    fun config(c:Context):Config=c.getSharedPreferences(PREFS,0).let {
        Config(it.getBoolean("enabled",false),it.getString("tree","").orEmpty(),
            it.getBoolean("chat",false),it.getString("prompt","").orEmpty())
    }
    fun saveConfig(c:Context,enabled:Boolean,chat:Boolean,prompt:String) {
        require(prompt.length<=12_000) { "Câu lệnh tối đa 12.000 ký tự" }
        if(enabled) {
            require(folder(c)!=null) { "Chọn ZIP đích hoặc thư mục Google Drive trước khi bật" }
            require(!chat||prompt.isNotBlank()) { "Nhập câu lệnh để gửi vào dot của bạn" }
        }
        require(c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",enabled)
            .putBoolean("chat",chat).putString("prompt",prompt).commit()) { "Không lưu được cấu hình" }
    }
    fun folder(c:Context,tree:String=config(c).tree):Uri?=tree.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?.takeIf { uri -> c.contentResolver.persistedUriPermissions.any {
            it.uri==uri&&it.isReadPermission&&it.isWritePermission } }
    fun choose(c:Context,uri:Uri) {
        require(DocumentsContract.isTreeUri(uri)&&uri.authority.orEmpty().startsWith("com.google.android.apps.docs.storage")) {
            "Hãy mở Google Drive trong bộ chọn và chọn thư mục đích trên Drive"
        }
        c.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        require(c.getSharedPreferences(PREFS,0).edit().putString("tree",uri.toString()).putString("label",destinationName(c,uri)).commit()) {
            "Không lưu được thư mục Drive"
        }
    }
    fun chooseZip(c:Context,uri:Uri) {
        require(!DocumentsContract.isTreeUri(uri)&&uri.authority.orEmpty().startsWith("com.google.android.apps.docs.storage")) {
            "Hãy mở Google Drive trong bộ chọn và tạo ZIP tại vị trí muốn lưu"
        }
        c.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        require(c.getSharedPreferences(PREFS,0).edit().putString("tree",uri.toString()).putString("label",destinationName(c,uri)).commit()) {
            "Không lưu được ZIP đích trên Drive"
        }
    }
    fun destinationLabel(c:Context)=c.getSharedPreferences(PREFS,0).getString("label","Đã chọn vị trí trên Drive").orEmpty()
    private fun destinationName(c:Context,uri:Uri):String=try {
        val doc=if(DocumentsContract.isTreeUri(uri)) DocumentsContract.buildDocumentUriUsingTree(uri,
            DocumentsContract.getTreeDocumentId(uri)) else uri
        c.contentResolver.query(doc,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst()) it.getString(0) else null
        }?:"Vị trí Drive đã chọn"
    } catch(_:Exception) { "Vị trí Drive đã chọn" }
    private fun home(c:Context)=File(c.filesDir,"after-run").apply { mkdirs() }
    private fun file(c:Context,run:String):File {
        require(File(run).name==run&&run.isNotBlank()&&run!="."&&run!="..")
        return File(home(c),run+".json")
    }
    @Synchronized fun state(c:Context,run:String):JSONObject?=try { JSONObject(file(c,run).readText()) } catch(_:Exception) { null }
    @Synchronized private fun put(c:Context,s:JSONObject) {
        val f=file(c,s.getString("run"));val temp=File(f.parentFile,f.name+".part")
        temp.writeText(s.toString(2));require(temp.renameTo(f)) { "Không lưu được tiến độ sau lượt" }
    }
    private fun states(c:Context)=home(c).listFiles().orEmpty().filter { it.extension=="json" }
        .mapNotNull { try { JSONObject(it.readText()) } catch(_:Exception) { null } }.sortedBy { it.optLong("created") }
    private val activeStages=setOf("queued","writing","waiting-drive","drive-confirmed","chat-opening",
        "prompt-filled","sending","chat-failed","send-uncertain")
    fun active(c:Context):JSONObject?=states(c).firstOrNull { it.optString("stage") in activeStages }
    fun latest(c:Context):JSONObject?=states(c).lastOrNull()
    @Synchronized fun finished(c:Context,run:String) {
        val options=config(c)
        if(!options.enabled||state(c,run)!=null) { resume(c);return }
        put(c,JSONObject().put("run",run).put("stage","queued").put("tree",options.tree)
            .put("chat",options.chat).put("prompt",options.prompt).put("created",System.currentTimeMillis())
            .put("filename","aov-"+run+".zip").put("message","Chờ hoàn tất ảnh để lưu lên Drive"))
        resume(c)
    }
    fun resume(c:Context) {
        val app=c.applicationContext
        main.post {
            if(queued) return@post
            queued=true
            main.postDelayed({ queued=false;advance(app) },1000)
        }
    }
    private fun advance(c:Context) {
        if(writing||CollectorService.current?.isWorking==true) return
        // Serialize batches: a fixed ZIP must not be overwritten by the next run before the
        // current upload/command is confirmed or explicitly skipped.
        val task=active(c)?:return
        if(task.optString("stage") !in setOf("queued","drive-confirmed","chat-opening","prompt-filled")) return
        val run=task.getString("run")
        if(task.optString("stage")!="queued") {
            if(task.optBoolean("chat")) CollectorService.current?.startAfterRunChat(run)
            else { update(c,run,"done","Đã tải lên Drive và xác nhận thành công");resume(c) }
            return
        }
        if(FileJob.running||CollectorService.libraryBusy||GamePhotoSync.busy) { resume(c);return }
        val photos=GamePhotoSession.state(c,run)
        if(photos!=null&&(photos.optJSONArray("saves")?.length()?:0)>photos.optInt("syncedMarks",0)) {
            GamePhotoSync.request(c);resume(c);return
        }
        val tree=folder(c,task.optString("tree"))
        if(tree==null) { update(c,run,"failed","Mất quyền vị trí Drive. Chọn lại ZIP đích hoặc thư mục rồi thử lại.");return }
        writing=true
        update(c,run,"writing","Đang ghi ZIP vào thư mục Drive")
        FileJob.start(c,"Đang lưu kết quả lên Drive","Lưu Drive sau lượt")
        worker.execute {
            val result=try {
                upload(c,run,tree)
                "Đã ghi ZIP và kiểm tra dữ liệu đọc lại. Chờ Google Drive báo tải lên thành công đúng tên file."
            } catch(e:Exception) {
                if(state(c,run)?.optString("stage")!="cancelled") update(c,run,"failed",if(FileJob.cancelled) "Đã hủy lưu Drive; ảnh trong app được giữ lại"
                    else "Chưa lưu được Drive: "+(e.message?:"Lỗi ghi file"))
                "Chưa hoàn tất lưu Drive: "+(e.message?:"Đã hủy")
            }
            FileJob.finish(result)
            main.post { writing=false;resume(c) }
        }
    }
    private fun upload(c:Context,run:String,tree:Uri) {
        val captures=File(c.filesDir,"captures")
        val runDir=File(captures,run)
        require(runDir.isDirectory) { "Không còn dữ liệu của lượt" }
        val nativeRun=HeroNames.slug(run).take(42).ifBlank { "anh" }
        val native=File(captures,GamePhotoImport.ROOT).walkTopDown()
            .filter { it.isDirectory&&it.name==nativeRun }.flatMap { it.walkTopDown() }.filter { it.isFile }.toList()
        val files=(runDir.walkTopDown().filter { it.isFile }.toList()+native).filter { it.extension!="part" }.distinct()
        require(files.isNotEmpty()) { "Lượt chưa có dữ liệu để lưu" }
        val task=state(c,run)?:error("Mất tiến độ lưu Drive")
        val uri=if(DocumentsContract.isTreeUri(tree)) {
            val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(c.contentResolver,parent,"application/zip",task.getString("filename"))
                ?:error("Drive không tạo được file")
        } else tree // User explicitly created this dedicated latest-results ZIP during setup.
        val actual=c.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst()) it.getString(0) else null
        }?:error("Không đọc được tên file Drive")
        synchronized(this) {
            val current=state(c,run)?:error("Mất tiến độ")
            current.put("uri",uri.toString()).put("filename",actual).put("uploadStarted",System.currentTimeMillis())
            put(c,current)
        }
        val digest=MessageDigest.getInstance("SHA-256")
        val output=c.contentResolver.openOutputStream(uri,"wt")?:error("Không ghi được file Drive")
        val counter=Count(output)
        DigestOutputStream(counter,digest).use { stream ->
            ZipFiles.export(files,captures,stream,{ FileJob.cancelled }) { bytes,total,n,_,name ->
                FileJob.report(bytes,total,"Đang lưu file $n · "+Format.sizes(bytes,total),name)
            }
        }
        val expected=digest.digest()
        val readDigest=MessageDigest.getInstance("SHA-256")
        var readBytes=0L
        c.contentResolver.openInputStream(uri)?.use { input ->
            val buffer=ByteArray(256*1024)
            while(true) {
                if(FileJob.cancelled) throw ZipFiles.Cancelled()
                val n=input.read(buffer);if(n<0) break
                readBytes+=n;readDigest.update(buffer,0,n)
            }
        }?:error("Chưa đọc lại được ZIP trên Drive")
        require(counter.bytes>0&&readBytes==counter.bytes&&expected.contentEquals(readDigest.digest())) {
            "ZIP đọc lại chưa trùng với dữ liệu đã ghi"
        }
        synchronized(this) {
            if(FileJob.cancelled) throw ZipFiles.Cancelled()
            val current=state(c,run)?:error("Mất tiến độ")
            current.put("bytes",readBytes).put("verified",true)
            if(current.optBoolean("driveProof")) current.put("stage","drive-confirmed").put("message","Drive đã báo tải lên thành công")
            else current.put("stage","waiting-drive").put("message","Chờ Drive báo tải lên xong: $actual")
            put(c,current)
        }
    }
    private class Count(out:OutputStream):FilterOutputStream(out) {
        var bytes=0L
        override fun write(b:Int) { out.write(b);bytes++ }
        override fun write(b:ByteArray,off:Int,len:Int) { out.write(b,off,len);bytes+=len }
    }
    @Synchronized fun update(c:Context,run:String,stage:String,message:String) {
        val task=state(c,run)?:return
        task.put("stage",stage).put("message",message);put(c,task)
    }
    /** Only a new Drive completion notification naming this exact ZIP is accepted. */
    @Synchronized fun driveNotification(c:Context,packageName:String,text:String,eventTime:Long=System.currentTimeMillis()) {
        for(task in states(c)) {
            if(task.optString("stage") !in setOf("writing","waiting-drive")) continue
            if(eventTime<task.optLong("uploadStarted",Long.MAX_VALUE)) continue
            if(!AfterRunProtocol.driveCompleted(packageName,text,task.optString("filename"))) continue
            task.put("driveProof",true)
            if(task.optBoolean("verified")) task.put("stage","drive-confirmed").put("message","Drive đã tải lên thành công")
            put(c,task);resume(c)
        }
    }
    @Synchronized fun cancel(c:Context,run:String) {
        val task=state(c,run)?:return
        val stage=task.optString("stage")
        if(stage in setOf("done","cancelled")) return
        if(stage=="writing") FileJob.cancel()
        CollectorService.current?.cancelAfterRunChat(run)
        update(c,run,if(stage=="sending") "send-uncertain" else "cancelled",
            if(stage=="sending") "Đã dừng theo dõi lần gửi. Kiểm tra ChatGPT; app không gửi lại."
            else if(stage=="send-uncertain") "Đã bỏ qua theo dõi lần gửi này; không tự gửi lại"
            else "Đã dừng thao tác sau lượt; dữ liệu trong app được giữ lại")
        resume(c)
    }
    @Synchronized fun retry(c:Context,run:String) {
        val task=state(c,run)?:return
        when(task.optString("stage")) {
            "failed" -> {
                task.put("stage","queued").put("tree",config(c).tree).put("verified",false).put("driveProof",false)
                    .put("message","Đang thử lưu Drive lại")
                task.remove("uploadStarted")
            }
            "chat-failed" -> task.put("stage","drive-confirmed").put("message","Đang mở lại dot của bạn")
            else -> return
        }
        put(c,task);resume(c)
    }
    @Synchronized fun recover(c:Context) {
        if(!writing) states(c).filter { it.optString("stage")=="writing" }.forEach {
            it.put("stage","failed").put("message","Lượt lưu Drive bị gián đoạn. Ảnh trong app được giữ lại; có thể thử lại.");put(c,it)
        }
        states(c).filter { it.optString("stage")=="sending" }.forEach {
            it.put("stage","send-uncertain").put("message","Lượt trước đã bấm Gửi. Kiểm tra ChatGPT; không tự gửi lần nữa.");put(c,it)
        }
        resume(c)
    }
    fun notificationText(notification:Notification?):String {
        val extras=notification?.extras?:return ""
        return listOf(extras.getCharSequence(Notification.EXTRA_TITLE),extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"))
            .filterNotNull().joinToString("\n")
    }
}
