package vn.ndang.aovcollector

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the ZIP files made to share to the app chosen in Android's share sheet, read only, for that app alone: the
 * provider is not exported and each file is reached through the read permission the share intent grants.
 */
class ShareProvider:ContentProvider() {
    companion object {
        const val AUTHORITY="vn.ndang.aovcollector.share"
        /** Where the files to share are made; emptied when a new share starts. */
        fun dir(c:Context)=File(c.cacheDir,"share")
        fun uri(file:File):Uri=Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.name).build()
        /** Removes earlier shares' files, which the receiving apps had time to read. */
        fun clear(c:Context,olderThanMs:Long=0) { val now=System.currentTimeMillis(); dir(c).listFiles()?.forEach { if(now-it.lastModified()>=olderThanMs) it.delete() } }
    }
    override fun onCreate()=true
    /** The file of [uri], only a plain name directly in [dir]. */
    private fun file(uri:Uri):File {
        val name=uri.lastPathSegment?:throw FileNotFoundException()
        val c=context?:throw FileNotFoundException()
        val dir=dir(c).canonicalFile; val f=File(dir,name).canonicalFile
        if(uri.pathSegments.size!=1||f.parentFile!=dir||!f.isFile) throw FileNotFoundException(name)
        return f
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(mode!="r") throw SecurityException("Chỉ đọc")
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri:Uri)=if(uri.lastPathSegment?.endsWith(".png",true)==true) "image/png" else "application/zip"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {
        val f=file(uri)
        val columns=(projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).filter { it==OpenableColumns.DISPLAY_NAME||it==OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray(),1).apply { addRow(columns.map { if(it==OpenableColumns.DISPLAY_NAME) f.name else f.length() }.toTypedArray()) }
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri? = null
    override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
}
