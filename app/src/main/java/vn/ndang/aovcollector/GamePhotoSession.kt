package vn.ndang.aovcollector

import android.content.Context
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * A native Cờ Liên Quân Save Photo batch. Pictures may contain unrelated personal images: snapshot all
 * document IDs before the macro begins and copy ONLY new IDs correlated with confirmed in-game Save Photo
 * events. The event's current encyclopedia topic determines the folder, never a manual image group.
 */
object GamePhotoSession {
    const val FILE="pictures-session.json"
    private const val LEDGER="pictures-copy-ledger.json"
    private const val MAX_IMAGE=32L*1024*1024
    private const val CLOCK_WINDOW_MS=20_000L
    private val validGroups=GamePhotoImport.GROUPS.map { it.key }.filter { it!="chua-phan-loai" }.toSet()
    data class Mark(val group:String,val item:String,val time:Long)
    data class Pick(val id:String,val group:String,val item:String,val size:Long)
    data class Scanned(val id:String,val size:Long,val modified:Long)
    data class Diff(val before:Int,val after:Int,val new:Int,val matching:List<Pick>,val unrelated:Int)
    data class Imported(val before:Int,val after:Int,val added:Int,val copied:Int,val already:Int,
                        val uncertain:Int,val errors:Int,val cancelled:Boolean)

    fun diff(baseline:Set<String>,photos:List<Scanned>,marks:List<Mark>):Diff {
        val fresh=photos.filter { it.id !in baseline }.sortedWith(compareBy({it.modified},{it.id}))
        val available=marks.sortedBy { it.time }.toMutableList()
        val assigned=ArrayList<Pick>()
        // Explicit one-to-one matching avoids treating the whole Pictures folder as Cờ Liên Quân.
        for(photo in fresh) {
            val best=available.withIndex().mapNotNull { (index,mark) ->
                if(mark.group !in validGroups) return@mapNotNull null
                val distance=abs(mark.time-photo.modified)
                if(photo.modified<=0L||distance>CLOCK_WINDOW_MS) null else index to distance
            }.minByOrNull { it.second }?.first
            if(best!=null) {
                val mark=available.removeAt(best)
                assigned.add(Pick(photo.id,mark.group,mark.item,photo.size))
            }
        }
        // Providers sometimes omit dates; a count-only fallback is safe ONLY when the number of
        // previously-unseen files equals the number of recorded, unpaired successful saves, AND
        // every save belongs to the same class (without dates, cross-class ordering is ambiguous).
        val leftovers=fresh.filter { photo -> assigned.none { it.id==photo.id } }
        if(leftovers.isNotEmpty()&&leftovers.size==available.size &&
            leftovers.all { it.modified<=0L } && available.all { it.group in validGroups } &&
            available.map { it.group }.distinct().size==1) {
            leftovers.sortedBy { it.id }.zip(available.sortedBy { it.time }).forEach { (photo,mark) ->
                assigned.add(Pick(photo.id,mark.group,mark.item,photo.size))
            }
        }
        return Diff(baseline.size,photos.size,fresh.size,assigned,fresh.size-assigned.size)
    }

    private fun location(context:Context,runId:String)=File(context.filesDir,"captures/$runId/$FILE")
    private fun read(context:Context,runId:String):JSONObject {
        val file=location(context,runId)
        require(file.isFile) { "Chưa có mốc số lượng ảnh trước khi thu thập cho lượt này" }
        return JSONObject(file.readText())
    }
    private fun store(context:Context,runId:String,state:JSONObject) {
        val file=location(context,runId);file.parentFile?.mkdirs()
        val temp=File(file.parentFile,file.name+".part")
        temp.writeText(state.toString(2))
        require(temp.renameTo(file)) { "Không lưu được mốc ảnh Pictures" }
    }
    private fun sha(value:String):String=MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .take(7).joinToString("") { "%02x".format(it) }
    private fun safe(value:String)=HeroNames.slug(value).take(42).ifBlank { "anh" }

    /** Called before taking any native images. Scans the explicitly granted folder, never unrelated storage. */
    @Synchronized fun begin(context:Context,runId:String):Int {
        if(location(context,runId).exists()) return read(context,runId).optInt("before")
        val tree=GamePhotoImport.chosen(context)?:error("Chọn thư mục Pictures trong AOV Collector trước khi chạy tải ảnh")
        val source=GamePhotoImport.scan(context,tree)
        val baseline=JSONArray()
        source.forEach { baseline.put(it.id) }
        val state=JSONObject().put("tree",tree.toString()).put("before",source.size)
            .put("startedAt",System.currentTimeMillis()).put("baseline",baseline)
            .put("saves",JSONArray()).put("status","collecting")
        store(context,runId,state)
        return source.size
    }

    /** Called AFTER Save Photo → Sure has been confirmed on the correct icon/topic. */
    @Synchronized fun mark(context:Context,runId:String,group:String,item:String) {
        require(group in validGroups)
        val state=read(context,runId)
        state.getJSONArray("saves").put(JSONObject().put("group",group).put("item",item.take(80))
            .put("time",System.currentTimeMillis()))
        store(context,runId,state)
    }

    private fun marks(state:JSONObject):List<Mark> {
        val arr=state.getJSONArray("saves")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Mark(it.optString("group"),it.optString("item"),it.optLong("time"))
        }
    }

    /** Called when the user returns to the app, or after the macro finishes. Restart-safe and idempotent. */
    @Synchronized fun importNew(context:Context,runId:String,cancelled:()->Boolean={false},
                                progress:(Int,Int,String)->Unit={_,_,_->}):Imported {
        val state=read(context,runId)
        val tree=GamePhotoImport.chosen(context)?:error("Quyền đọc Pictures đã hết hạn. Hãy chọn lại thư mục.")
        require(tree.toString()==state.optString("tree")) { "Thư mục Pictures đã thay đổi. Chọn lại đúng thư mục trước khi tiếp tục." }
        val baseline=state.getJSONArray("baseline")
        val old=(0 until baseline.length()).map { baseline.getString(it) }.toHashSet()
        val all=GamePhotoImport.scan(context,tree) { !cancelled() }
        if(cancelled()) return Imported(state.optInt("before"),all.size,0,0,0,0,0,true)
        val diff=diff(old,all.map { Scanned(it.id,it.size,it.modified) },marks(state))
        val matches=all.associateBy { it.id }
        val destRoot=File(context.filesDir,"captures/"+GamePhotoImport.ROOT).apply { mkdirs() }
        val meta=File(destRoot,"run.json")
        if(!meta.exists()) meta.writeText(JSONObject().put("collectionMode","game-photos")
            .put("label","Ảnh gốc Cờ Liên Quân · Pictures")
            .put("createdAt",System.currentTimeMillis()).toString(2))
        val ledgerFile=File(context.filesDir,LEDGER)
        val ledger=try { JSONObject(ledgerFile.readText()) } catch(_:Exception) { JSONObject() }
        var copied=0;var already=0;var errors=0
        for((index,pick) in diff.matching.withIndex()) {
            if(cancelled()) break
            progress(index,diff.matching.size,pick.group+"/"+pick.item)
            val image=matches[pick.id]?:continue
            val identity=sha(tree.toString()+"|"+image.key)
            if(ledger.has(identity)) { already++;continue }
            val ext=image.name.substringAfterLast('.', "jpg").lowercase()
                .takeIf { GamePhotoImport.imageExtension(it) }?:"jpg"
            val file=File(destRoot,pick.group+"/"+safe(runId)).apply { mkdirs() }
            val target=File(file,safe(pick.item)+"-"+identity+"."+ext)
            val partial=File(file,target.name+".part")
            try {
                require(image.size>=0 && image.size<=MAX_IMAGE) { "Ảnh vượt giới hạn 32 MB" }
                require(context.filesDir.usableSpace>image.size+30L*1024*1024) { "Bộ nhớ trống quá ít" }
                context.contentResolver.openInputStream(image.uri)?.use { input ->
                    partial.outputStream().use { output -> input.copyTo(output,128*1024) }
                }?:error("Không mở được ảnh mới")
                require(partial.length() in 1..MAX_IMAGE && partial.renameTo(target)) { "Ảnh lưu bị lỗi" }
                ledger.put(identity,target.relativeTo(destRoot).invariantSeparatorsPath)
                ledgerFile.writeText(ledger.toString(2))
                copied++
            } catch(e:Exception) { partial.delete();errors++ }
        }
        state.put("after",all.size).put("newFiles",diff.new)
            .put("unrelated",diff.unrelated).put("imported",copied+already)
            .put("syncedMarks",if(cancelled()) state.optInt("syncedMarks",0) else state.getJSONArray("saves").length())
            .put("status",if(cancelled()) "paused" else "imported")
        store(context,runId,state)
        progress(diff.matching.size,diff.matching.size,"Đã lưu $copied ảnh mới")
        return Imported(diff.before,diff.after,diff.new,copied,already,diff.unrelated,errors,cancelled())
    }

    fun lastRun(context:Context):String? = File(context.filesDir,"captures").listFiles()
        ?.filter { it.isDirectory&&File(it,FILE).exists() }
        ?.maxByOrNull { File(it,FILE).lastModified() }?.name

    fun state(context:Context,runId:String):JSONObject?=try { read(context,runId) } catch(_:Exception) { null }
}
