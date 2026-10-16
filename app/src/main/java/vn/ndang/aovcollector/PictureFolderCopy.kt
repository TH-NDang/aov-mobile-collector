package vn.ndang.aovcollector

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Copies original PNG/JPEG/WEBP images into a folder the user grants via ACTION_OPEN_DOCUMENT_TREE.
 * No broad storage permission and no direct /storage/emulated/0 writes. The local originals stay
 * untouched. Existing destination images are never replaced.
 */
object PictureFolderCopy {
    data class Summary(val copied:Int,val skipped:Int,val bytes:Long)
    private data class Child(val uri:Uri,val mime:String)
    private const val DIRECTORY=DocumentsContract.Document.MIME_TYPE_DIR

    fun copy(context:Context,tree:Uri,items:List<PictureCopyPlan.Entry>,
             cancelled:()->Boolean,report:(Long,Long,Int,Int,String)->Unit):Summary {
        require(items.isNotEmpty()) { "Thư mục đã chọn không có ảnh để sao chép" }
        val resolver=context.contentResolver
        require(DocumentsContract.isTreeUri(tree)) { "Hãy chọn một thư mục hợp lệ" }
        val rootDocId=DocumentsContract.getTreeDocumentId(tree)
        val root=DocumentsContract.buildDocumentUriUsingTree(tree,rootDocId)
        val folders=HashMap<String,Uri>()
        folders[""]=root
        val childCache=HashMap<String,MutableMap<String,Child>>()
        fun children(parent:Uri):MutableMap<String,Child> {
            val id=DocumentsContract.getDocumentId(parent)
            return childCache.getOrPut(id) {
                val result=HashMap<String,Child>()
                val list=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id)
                val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE)
                resolver.query(list,columns,null,null,null)?.use { cursor ->
                    val idIndex=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while(cursor.moveToNext()) {
                        val name=cursor.getString(nameIndex)?:continue
                        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(idIndex))
                        result[name]=Child(uri,cursor.getString(mimeIndex)?:"")
                    }
                }?:error("Không liệt kê được thư mục đích")
                result
            }
        }
        fun folder(path:String):Uri {
            folders[path]?.let { return it }
            val parentPath=path.substringBeforeLast('/',"")
            val name=path.substringAfterLast('/')
            val parent=folder(parentPath)
            val current=children(parent)[name]
            val uri=if(current==null) {
                DocumentsContract.createDocument(resolver,parent,DIRECTORY,name)
                    ?:error("Không tạo được thư mục $path")
            } else {
                require(current.mime==DIRECTORY) { "Tên $path đã là một file trong thư mục đích" }
                current.uri
            }
            if(current==null) children(parent)[name]=Child(uri,DIRECTORY)
            folders[path]=uri
            return uri
        }
        var copied=0
        var skipped=0
        var done=0L
        val total=items.sumOf { it.size }
        val buffer=ByteArray(128*1024)
        for((index,item) in items.withIndex()) {
            if(cancelled()) throw ZipFiles.Cancelled()
            val path=item.path
            val parentPath=path.substringBeforeLast('/',"")
            val leaf=path.substringAfterLast('/')
            val dest=folder(parentPath)
            if(children(dest).containsKey(leaf)) {
                // No overwrite on subsequent exports to the same Pictures directory.
                skipped++
                done+=item.size
                report(done,total,index+1,items.size,path+" (đã tồn tại, bỏ qua)")
                continue
            }
            val mime=when(item.file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg","jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> error("Không hỗ trợ ảnh: "+item.file.name)
            }
            val target=DocumentsContract.createDocument(resolver,dest,mime,leaf)
                ?:error("Không tạo được ảnh $path trong thư mục đích")
            try {
                val output=resolver.openOutputStream(target,"w")?:error("Không mở được file đích: $path")
                output.buffered(128*1024).use { out ->
                    item.file.inputStream().buffered(128*1024).use { input ->
                        while(true) {
                            if(cancelled()) throw ZipFiles.Cancelled()
                            val n=input.read(buffer)
                            if(n<0) break
                            out.write(buffer,0,n)
                            done+=n
                            report(done,total,index+1,items.size,path)
                        }
                    }
                }
                copied++
                children(dest)[leaf]=Child(target,mime)
                report(done,total,index+1,items.size,path)
            } catch(e:Exception) {
                try { DocumentsContract.deleteDocument(resolver,target) } catch(_:Exception) {}
                throw e
            }
        }
        return Summary(copied,skipped,done)
    }
}
