package vn.ndang.aovcollector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small durable journal, independent of the macro's step count. Call from a worker. */
object CaptureHistory {
    const val LIMIT=120
    data class Entry(val order:Long,val run:String,val path:String,val time:Long,val state:String,val bytes:Long)
    private fun file(c:Context)=File(c.filesDir,"capture-history.json")
    private fun read(c:Context):JSONObject=try { JSONObject(file(c).readText()) } catch(_:Exception) { JSONObject() }
    private fun write(c:Context,j:JSONObject) {
        val part=File(c.filesDir,"capture-history.json.part")
        part.writeText(j.toString());check(part.renameTo(file(c)))
    }
    @Synchronized fun begin(c:Context,run:String,target:File):Long {
        val j=read(c);val n=j.optLong("next",1L);val rows=j.optJSONArray("rows")?:JSONArray()
        rows.put(JSONObject().put("order",n).put("run",run)
            .put("path",target.relativeTo(File(c.filesDir,"captures")).invariantSeparatorsPath)
            .put("time",System.currentTimeMillis()).put("state","saving"))
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
        val rows=read(c).optJSONArray("rows")?:return emptyList()
        return (0 until rows.length()).map { i ->
            val r=rows.getJSONObject(i);val path=r.getString("path");val f=File(c.filesDir,"captures/$path")
            val state=when {
                r.optString("state")=="saved"&&(!f.isFile||f.length()==0L) -> "missing"
                r.optString("state")=="saving"&&CollectorService.current==null&&!GamePhotoSync.busy -> "interrupted"
                else -> r.optString("state")
            }
            Entry(r.getLong("order"),r.getString("run"),path,r.getLong("time"),state,r.optLong("bytes"))
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
}
