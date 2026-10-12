package vn.ndang.aovcollector

import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes saved captures into a ZIP and reports progress by bytes, so a long export can show how far it is. */
object ZipFiles {
    class Cancelled:Exception("Đã hủy")
    /**
     * Writes [files] to [out] with paths relative to [root], and returns the bytes written. PNG files are stored
     * without compression: they are already compressed, and deflating them again costs minutes on a phone for
     * nothing. [report] gets the bytes done, the bytes in all, the 1-based number of the file and its path.
     */
    fun export(files:List<File>,root:File,out:OutputStream,cancelled:()->Boolean,report:(Long,Long,Int,String)->Unit):Long {
        val base=root.canonicalPath+File.separator
        val total=files.sumOf { it.length() }; var done=0L; val buffer=ByteArray(256*1024)
        ZipOutputStream(out.buffered(256*1024)).use { zip ->
            for((i,file) in files.withIndex()) {
                require(file.canonicalPath.startsWith(base)) { "File ngoài thư mục ảnh" }
                val name=file.relativeTo(root).invariantSeparatorsPath
                zip.setLevel(if(file.extension.equals("png",true)) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { input ->
                    while(true) {
                        if(cancelled()) throw Cancelled()
                        val n=input.read(buffer); if(n<0) break
                        zip.write(buffer,0,n); done+=n; report(done,total,i+1,name)
                    }
                }
                zip.closeEntry()
            }
        }
        return total
    }
}
