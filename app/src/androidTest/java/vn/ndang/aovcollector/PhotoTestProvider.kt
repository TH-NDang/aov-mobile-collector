package vn.ndang.aovcollector

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/** A real SAF provider in the test APK only; never included in the collector APK. */
class PhotoTestProvider:DocumentsProvider() {
    private val directory get()=File(context!!.filesDir,"photo-fixtures").apply { mkdirs() }
    override fun onCreate()=true
    private val columns=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_FLAGS)
    private fun row(cursor:MatrixCursor,id:String) {
        val file=if(id=="Pictures") directory else File(directory,id)
        val data=mapOf<String,Any>(Document.COLUMN_DOCUMENT_ID to id,
            Document.COLUMN_DISPLAY_NAME to id,
            Document.COLUMN_MIME_TYPE to if(id=="Pictures") Document.MIME_TYPE_DIR else if(id.endsWith(".zip")) "application/zip" else "image/jpeg",
            Document.COLUMN_SIZE to file.length(),Document.COLUMN_LAST_MODIFIED to file.lastModified(),
            Document.COLUMN_FLAGS to if(id=="Pictures") Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE)
        cursor.addRow(cursor.columnNames.map { data[it] }.toTypedArray())
    }
    override fun queryRoots(projection:Array<out String>?):Cursor =
        MatrixCursor(arrayOf(Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS)).apply {
            addRow(arrayOf("photos","Pictures","Test Pictures",Root.FLAG_SUPPORTS_CREATE))
        }
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor =
        MatrixCursor(projection?:columns).apply { row(this,documentId) }
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor =
        MatrixCursor(projection?:columns).apply {
            require(parentDocumentId=="Pictures")
            directory.listFiles().orEmpty().filter { it.isFile }.forEach { row(this,it.name) }
        }
    override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
        require(parentDocumentId=="Pictures"&&File(displayName).name==displayName)
        File(directory,displayName).writeBytes(byteArrayOf())
        return displayName
    }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
        require(File(documentId).name==documentId)
        return ParcelFileDescriptor.open(File(directory,documentId),ParcelFileDescriptor.parseMode(mode))
    }
    override fun deleteDocument(documentId:String) { File(directory,documentId).delete() }
    override fun isChildDocument(parentDocumentId:String,documentId:String)=parentDocumentId=="Pictures"
}
