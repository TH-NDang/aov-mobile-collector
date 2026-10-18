package vn.ndang.aovcollector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small durable journal, independent of the macro's step count. Call from a worker. */
object CaptureHistory {
    const val LIMIT=120
    data class Entry(val order:Long,val run:String,val path:String,val time:Long,val state:String,val bytes:Long,val name:String="")
    private fun file(c:Context)=File(c.filesDir,"capture-history.json")
    private fun read(c:Context):JSONObject=try { JSONObject(file(c).readText()) } catch(_:Exception) { JSONObject() }
    private fun write(c:Context,j:JSONObject) {
        val part=File(c.filesDir,"capture-history.json.part")
        part.writeText(j.toString());check(part.renameTo(file(c)))
    }
    private fun subject(c:Context,path:String):String {
        val f=File(c.filesDir,"captures/$path")
        val hero=try { JSONObject(File(f.parentFile,"hero.json").readText()).optString("name") } catch(_:Exception) { "" }
        if(hero.isNotBlank()&&hero!="Chưa rõ tên") return HeroNames.slug(hero)
        val parts=f.nameWithoutExtension.substringAfter("chess__","").split("__")
        if(parts.size>=4&&parts[2] !in setOf("trang","hang")) return HeroNames.slug(parts[3])
        if(path.startsWith(GamePhotoImport.ROOT+"/"))
            return f.nameWithoutExtension.replace(Regex("-[a-f0-9]{14}$"),"").ifBlank { "anh-goc" }
        return if(parts.size>=2) HeroNames.slug(parts[1]) else "anh-chup"
    }
    /** Names are stored, so removing an earlier file never renumbers a later image. */
    private fun nameMissing(c:Context,j:JSONObject):Boolean {
        val rows=j.optJSONArray("rows")?:return false
        val runs=j.optJSONObject("names")?:JSONObject().also { j.put("names",it) }
        var changed=false
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            val exact=ChessCaptureFiles.exactDisplayName(row.getString("path"))
            if(exact!=null) {
                if(row.optString("name")!=exact) { row.put("name",exact);changed=true }
                continue
            }
            if(row.optString("name").isNotBlank()) continue
            val run=row.getString("run");val counts=runs.optJSONObject(run)?:JSONObject().also { runs.put(run,it) }
            val key=subject(c,row.getString("path")).ifBlank { "anh-chup" }
            val number=counts.optInt(key)+1;counts.put(key,number).put("_updated",System.currentTimeMillis())
            row.put("name","$key-$number");changed=true
        }
        val active=(0 until rows.length()).map { rows.getJSONObject(it).getString("run") }.toSet()
        val older=runs.keys().asSequence().filter { it !in active }.toList().sortedByDescending { runs.getJSONObject(it).optLong("_updated") }.drop(20)
        older.forEach { runs.remove(it) }
        return changed
    }
    @Synchronized fun begin(c:Context,run:String,target:File):Long {
        val j=read(c);val n=j.optLong("next",1L);val rows=j.optJSONArray("rows")?:JSONArray()
        rows.put(JSONObject().put("order",n).put("run",run)
            .put("path",target.relativeTo(File(c.filesDir,"captures")).invariantSeparatorsPath)
            .put("time",System.currentTimeMillis()).put("state","saving"))
        j.put("rows",rows);nameMissing(c,j)
        val tail=JSONArray();for(i in maxOf(0,rows.length()-LIMIT) until rows.length()) tail.put(rows.getJSONObject(i))
        write(c,j.put("next",n+1).put("rows",tail));return n
    }
    @Synchronized fun finish(c:Context,order:Long,ok:Boolean) {
        val j=read(c);val rows=j.optJSONArray("rows")?:return
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            if(row.optLong("order")==order) {
                val f=File(c.filesDir,"captures/"+row.getString("path"))
                row.put("state",if(ok&&f.isFile&&f.length()>0) "saved" else "failed")
                    .put("bytes",if(f.isFile) f.length() else 0L)
            }
        }
        write(c,j)
    }
    @Synchronized fun entries(c:Context):List<Entry> {
        val journal=read(c)
        if(nameMissing(c,journal)) write(c,journal)
        val rows=journal.optJSONArray("rows")?:return emptyList()
        return (0 until rows.length()).map { i ->
            val r=rows.getJSONObject(i);val path=r.getString("path");val f=File(c.filesDir,"captures/$path")
            val state=when {
                r.optString("state")=="saved"&&(!f.isFile||f.length()==0L) -> "missing"
                r.optString("state")=="saving"&&CollectorService.current==null&&!GamePhotoSync.busy -> "interrupted"
                else -> r.optString("state")
            }
            Entry(r.getLong("order"),r.getString("run"),path,r.getLong("time"),state,r.optLong("bytes"),r.optString("name"))
        }
    }
    @Synchronized fun recover(c:Context) {
        val j=read(c);val rows=j.optJSONArray("rows")?:return
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            if(row.optString("state")=="saving") row.put("state","interrupted")
        }
        write(c,j)
    }
    fun status(state:String)=when(state) {
        "saving" -> "Đang lưu…";"saved" -> "Đã lưu trong app";"missing" -> "File đã bị xóa hoặc di chuyển"
        "interrupted" -> "Lưu bị gián đoạn";else -> "Lưu thất bại"
    }
    fun description(path:String):String {
        if(path.startsWith(GamePhotoImport.ROOT+"/")) {
            val p=path.removePrefix(GamePhotoImport.ROOT+"/")
            val group=GamePhotoImport.GROUPS.firstOrNull { it.key!="chua-phan-loai"&&p.startsWith(GamePhotoImport.groupPath(it.key)+"/") }
            return "Ảnh gốc"+(group?.let { " · "+it.title }?:"")
        }
        ChessCaptureFiles.detail(path)?.let { return "Quân cờ · Chi tiết · Trang ${it.page}" }
        val n=File(path).nameWithoutExtension
        val skill=Regex("skill-([1-4])").find(n)?.groupValues?.get(1)
        return when {
            n.endsWith("-overview") -> "Tổng quan"
            n.endsWith("-attributes") -> "Thuộc tính"
            n.contains("passive") -> "Nội tại"
            skill!=null -> "Chiêu $skill"+if(n.endsWith("detail")) " · Chi tiết" else " · Tóm tắt"
            n.contains("chess__") -> {
                val parts=n.substringAfter("chess__").split("__")
                val topic=ChessWiki.TOPICS.firstOrNull { it.key==parts.getOrNull(1) }
                (topic?.label?:"Cờ Liên Quân")+(parts.lastOrNull()?.toIntOrNull()?.let { " · Trang $it" }?:"")
            }
            else -> "Ảnh chụp màn hình"
        }
    }
}
