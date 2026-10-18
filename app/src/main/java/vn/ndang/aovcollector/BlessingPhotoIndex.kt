package vn.ndang.aovcollector

import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/** Cross-run reuse requires a verified semantic identity AND a still-intact imported original. */
object BlessingPhotoIndex {
    const val FILE="blessing-photo-index.json"

    private fun read(index:File)=if(index.exists()) JSONObject(index.readText()) else JSONObject()
    private fun identity(group:String,key:String)=group+":"+key
    private fun within(root:File,file:File):Boolean =
        file.canonicalPath.startsWith(root.canonicalPath+File.separator)
    private fun sha(file:File):String {
        val hash=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer=ByteArray(32*1024)
            while(true) { val n=input.read(buffer);if(n<0) break;hash.update(buffer,0,n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    /** Called only after the session's source/event pairing has produced a verified local copy. */
    fun record(index:File,root:File,group:String,key:String,file:File,aliases:Set<String> =emptySet()) {
        require(ChessBlessings.isGroup(group)&&ChessBlessings.isKey(key))
        require(within(root,file)&&file.isFile&&file.length()>0&&file.canRead())
        val records=read(index)
        records.put(identity(group,key),JSONObject().put("path",file.relativeTo(root).invariantSeparatorsPath)
            .put("size",file.length()).put("sha256",sha(file)).put("aliases",JSONArray(aliases.sorted())))
        index.parentFile?.mkdirs()
        val temp=File(index.parentFile,index.name+".part")
        temp.writeText(records.toString(2))
        require(temp.renameTo(index)) { "Không lưu được dấu ảnh Chúc Phúc đã nhập" }
    }

    /** Missing, deleted, truncated, replaced or unreadable pictures never suppress a fresh save. */
    fun hasSaved(index:File,root:File,group:String,key:String,aliases:Set<String> =emptySet()):Boolean {
        if(!ChessBlessings.isGroup(group)||!ChessBlessings.isKey(key)) return false
        val records=read(index)
        val exact=identity(group,key)
        val candidates=records.keys().asSequence().filter { stored ->
            val entry=records.optJSONObject(stored)
            val art=entry?.optJSONArray("aliases")
            val previous=(0 until (art?.length()?:0)).map { art!!.getString(it) }.toSet()
            stored.startsWith(group+":")&&ChessBlessings.sameSave(group,
                previous+stored.removePrefix(group+":"),aliases+key)
        }.toList()
        // Ambiguous near-matches must never choose an arbitrary existing blessing.
        val matched=if(records.has(exact)) exact else candidates.singleOrNull()?:return false
        val entry=records.optJSONObject(matched)?:return false
        val path=entry.getString("path")
        val file=File(root,path)
        if(path.isBlank()||!within(root,file)||!file.isFile||!file.canRead()||
            file.length()<=0||file.length()!=entry.getLong("size")) return false
        return try { sha(file)==entry.getString("sha256") } catch(_:java.io.IOException) { false }
    }
}
