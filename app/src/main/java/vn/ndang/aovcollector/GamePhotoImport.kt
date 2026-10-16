package vn.ndang.aovcollector

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * Copies native Save Photo exports from a user-selected Pictures directory into our own history.
 * Requires only an explicit persisted Android Storage Access Framework read grant.
 * Never deletes, moves, edits or requests broad access to the user's photos.
 */
object GamePhotoImport {
    const val REQUEST_FOLDER=14
    const val ROOT="anh-goc-co-lien-quan"
    private const val PREFS="game-photo-source"
    private const val URI="treeUri"
    private const val SELECTED_AT="selectedAt"
    private const val LEDGER="game-photo-imported.json"
    private const val MAX_ENTRIES=50_000
    private const val MAX_DEPTH=5
    private const val MAX_IMAGE_BYTES=32L*1024*1024
    data class Group(val key:String,val title:String)
    val GROUPS=listOf(
        Group("chua-phan-loai","Chưa phân loại"),
        Group("quan-co","Quân cờ · Ảnh nhân vật"),
        Group("chieu-quan-co","Quân cờ · Ảnh chiêu"),
        Group("phe","Tương tác · Phe"),
        Group("nghe","Tương tác · Nghề"),
        Group("trang-bi","Trang bị"),
        Group("chuc-phuc-thuong","Chúc Phúc · Thường"),
        Group("chuc-phuc-huyen-thoai-su-thi","Chúc Phúc · Huyền thoại / Sử thi"),
        Group("chuc-phuc-truyen-thuyet","Chúc Phúc · Truyền thuyết")
    )
    /** Stable native-photo hierarchy matching Bách khoa's left navigation and subcategories. */
    fun groupPath(group:String):String=when(group) {
        "quan-co" -> "quan-co/anh-quan-co"
        "chieu-quan-co" -> "quan-co/chieu-quan-co"
        "phe" -> "tuong-tac/phe"
        "nghe" -> "tuong-tac/nghe"
        "trang-bi" -> "trang-bi"
        "chuc-phuc-thuong" -> "chuc-phuc/thuong"
        "chuc-phuc-huyen-thoai-su-thi" -> "chuc-phuc/huyen-thoai-su-thi"
        "chuc-phuc-truyen-thuyet" -> "chuc-phuc/truyen-thuyet"
        else -> error("Không phải nhóm ảnh gốc được hỗ trợ: "+group)
    }

    fun nativeFolderTitle(path:String):String?=when(path) {
        "quan-co" -> "Quân cờ"
        "quan-co/anh-quan-co" -> "Tất cả quân cờ"
        "quan-co/chieu-quan-co" -> "Chiêu quân cờ"
        "tuong-tac" -> "Tương tác"
        "tuong-tac/phe" -> "Phe"
        "tuong-tac/nghe" -> "Nghề"
        "trang-bi" -> "Trang bị"
        "chuc-phuc" -> "Chúc Phúc"
        "chuc-phuc/thuong" -> "Thường"
        "chuc-phuc/huyen-thoai-su-thi" -> "Huyền thoại / Sử thi"
        "chuc-phuc/truyen-thuyet" -> "Truyền thuyết"
        else -> null
    }

    fun isImage(file:File)=file.isFile&&imageExtension(file.extension)
    fun imageExtension(ext:String)=ext.lowercase() in setOf("png","jpg","jpeg","webp")
    fun chosen(context:Context):Uri?=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        .getString(URI,null)?.let(Uri::parse)?.takeIf { uri ->
            context.contentResolver.persistedUriPermissions.any { it.isReadPermission&&it.uri==uri }
        }
    fun hasSource(context:Context)=chosen(context)!=null
    fun sourceLabel(context:Context):String=chosen(context)?.lastPathSegment
        ?.substringAfterLast(':')?.take(90)?: "Chưa chọn thư mục Pictures"
    fun choose(context:Context,uri:Uri) {
        require(DocumentsContract.isTreeUri(uri)) { "Hãy chọn một thư mục ảnh, không phải một tệp riêng lẻ" }
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        prefs.edit().putString(URI,uri.toString()).putLong(SELECTED_AT,System.currentTimeMillis()).commit()
    }
    data class Photo(val uri:Uri,val id:String,val name:String,val mime:String,val size:Long,
                     val modified:Long,val relativeDir:String) {
        val key:String get()=id+"|"+size+"|"+modified
    }
    data class Result(val copied:Int,val skipped:Int,val failed:Int,val cancelled:Boolean,val root:File)
    private fun sha(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .take(7).joinToString("") { "%02x".format(it) }
    private fun safe(text:String):String=text.replace(Regex("[^a-zA-Z0-9_.-]"),"-").trim('.','-')
        .take(70).ifBlank { "image" }
    /**
     * A bounded, breadth-first traversal of the folder the user explicitly picked.
     * Does not search unrelated folders on the phone.
     */
    fun scan(context:Context,tree:Uri,allow:()->Boolean={ true }):List<Photo> {
        val rootId=DocumentsContract.getTreeDocumentId(tree)
        val q=ArrayDeque<Triple<String,String,Int>>()
        q.add(Triple(rootId,"",0))
        val found=ArrayList<Photo>()
        var visited=0
        val cols=arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        while(q.isNotEmpty()&&visited<MAX_ENTRIES&&allow()) {
            val (parent,path,depth)=q.removeFirst()
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
            context.contentResolver.query(children,cols,null,null,null)?.use { cursor ->
                val idAt=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameAt=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeAt=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeAt=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modAt=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while(cursor.moveToNext()&&visited<MAX_ENTRIES&&allow()) {
                    visited++
                    val id=cursor.getString(idAt)?:continue
                    val name=cursor.getString(nameAt)?:continue
                    val mime=cursor.getString(mimeAt)?:""
                    if(mime==DocumentsContract.Document.MIME_TYPE_DIR) {
                        if(depth<MAX_DEPTH) q.add(Triple(id,if(path.isBlank()) name else "$path/$name",depth+1))
                        continue
                    }
                    if(!mime.startsWith("image/")&&!imageExtension(name.substringAfterLast('.',"" ))) continue
                    val size=if(cursor.isNull(sizeAt)) 0L else cursor.getLong(sizeAt)
                    if(size<0L||size>MAX_IMAGE_BYTES) continue
                    val modified=if(cursor.isNull(modAt)) 0L else cursor.getLong(modAt)
                    val documentUri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
                    found.add(Photo(documentUri,id,name,mime,size,modified,path))
                }
            }
        }
        require(visited<MAX_ENTRIES) { "Pictures có quá nhiều file để đối chiếu an toàn. Hãy chọn thư mục con chứa ảnh game." }
        return found.sortedWith(compareBy<Photo> { it.modified }.thenBy { it.name })
    }
    /**
     * [existing] includes older images; otherwise only newly exported files since the folder was granted.
     * Duplicate source/size/timestamp combinations are never recopied. Interrupted copies are deleted.
     */
    fun sync(context:Context,group:String,existing:Boolean,cancelled:()->Boolean={ false },
             progress:(Int,Int,String)->Unit={_,_,_->}):Result {
        require(GROUPS.any { it.key==group }) { "Nhóm ảnh không hợp lệ" }
        val tree=chosen(context)?:error("Chưa cấp quyền cho thư mục Pictures. Hãy chọn lại trong app.")
        val cutoff=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong(SELECTED_AT,0L)
        val root=File(context.filesDir,"captures/$ROOT").apply { mkdirs() }
        val dest=File(root,group).apply { mkdirs() }
        val metadata=File(root,"run.json")
        if(!metadata.exists()) metadata.writeText(JSONObject().put("collectionMode","game-photos")
            .put("label","Ảnh gốc Cờ Liên Quân · Pictures")
            .put("createdAt",System.currentTimeMillis()).toString(2))
        val ledgerFile=File(context.filesDir,LEDGER)
        val ledger=try { JSONObject(ledgerFile.readText()) } catch(_:Exception) { JSONObject() }
        val source=scan(context,tree) { !cancelled() }
        var copied=0;var skipped=0;var failed=0
        for((i,item) in source.withIndex()) {
            if(cancelled()) break
            progress(i,source.size,item.relativeDir+"/"+item.name)
            val id=sha(tree.toString()+"|"+item.key)
            if(ledger.has(id)||(!existing&&item.modified>0&&item.modified<cutoff-120_000)) { skipped++;continue }
            val ext=item.name.substringAfterLast('.', "jpg").lowercase().takeIf { imageExtension(it) }
                ?:when(item.mime) { "image/png" -> "png";"image/webp" -> "webp";else -> "jpg" }
            val filename=safe(item.name.substringBeforeLast('.',item.name))+"-"+id+"."+ext
            val target=File(dest,filename)
            val temp=File(dest,filename+".part")
            try {
                if(target.exists()) {
                    ledger.put(id,group+"/"+filename);skipped++;continue
                }
                require(context.filesDir.usableSpace>item.size+30L*1024*1024) { "Không đủ dung lượng trống" }
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    temp.outputStream().use { out -> input.copyTo(out,128*1024) }
                }?:error("Không mở được ảnh: "+item.name)
                require(temp.length()>0L&&temp.length()<=MAX_IMAGE_BYTES) { "Ảnh không hợp lệ" }
                require(temp.renameTo(target)) { "Không chuyển được ảnh đã sao chép" }
                ledger.put(id,group+"/"+filename);copied++
                // Save a checkpoint per photo so a stopped job can resume without duplicates.
                ledgerFile.writeText(ledger.toString())
            } catch(e:Exception) {
                temp.delete()
                if(e.message=="Không đủ dung lượng trống") throw e
                failed++
            }
        }
        metadata.writeText(JSONObject(metadata.readText()).put("updatedAt",System.currentTimeMillis()).toString(2))
        progress(source.size,source.size,"Đã sao chép $copied ảnh")
        return Result(copied,skipped,failed,cancelled(),root)
    }
}
