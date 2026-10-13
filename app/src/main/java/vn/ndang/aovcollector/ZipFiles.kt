package vn.ndang.aovcollector

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes saved captures as one ZIP holding part ZIPs of at most [PART_LIMIT] bytes each, and reports progress by
 * bytes, so a long export can show how far it is. Folders follow in alphabetical order and a folder is split
 * between parts only when it is larger than a part.
 */
object ZipFiles {
    /** 450 MB in decimal megabytes, which keeps a part under 450 MB however a tool counts megabytes. */
    const val PART_LIMIT=450_000_000L
    // End of central directory, with room for its ZIP64 form.
    private const val END_BYTES=128L
    class Cancelled:Exception("Đã hủy")
    /** A part ZIP: its name in the outer ZIP, its files in order, and its size once written. */
    class Part(val name:String,val files:List<File>) { var bytes=0L; internal set }

    private val byName:Comparator<String> = String.CASE_INSENSITIVE_ORDER.then(naturalOrder())
    // By folder name at each level, so a run's own files come right before its hero folders.
    private val byFolder=Comparator<List<String>> { a,b -> a.zip(b).map { (x,y) -> byName.compare(x,y) }.firstOrNull { it!=0 }?:a.size.compareTo(b.size) }

    /** The most an entry can take in a part: its data, which deflate grows by under 0.1% at worst, and its local
     *  header, data descriptor and central directory record, each with the name and room for extra fields. */
    private fun cost(file:File,name:String)=file.length()+file.length()/1000+256+2L*name.toByteArray().size

    /**
     * Deals [files] out to parts: folders in alphabetical order, files by name within a folder, each folder whole
     * in the current part if it fits, else in a new one. Parts are named by number and their first and last hero
     * folder, for example "01_aleister_elsu.zip".
     */
    fun plan(files:List<File>,root:File,limit:Long=PART_LIMIT):List<Part> {
        val base=root.canonicalPath+File.separator
        val names=files.associateWith { require(it.canonicalPath.startsWith(base)) { "File ngoài thư mục ảnh" };it.relativeTo(root).invariantSeparatorsPath }
        val folders=files.groupBy { names.getValue(it).split('/').dropLast(1) }.toSortedMap(byFolder).values.map { it.sortedWith(compareBy(byName) { f -> f.name }) }
        val budget=limit-END_BYTES
        val parts=ArrayList<MutableList<File>>();var used=0L
        fun add(group:List<File>,bytes:Long) { if(parts.isEmpty()||used+bytes>budget) { parts+=ArrayList<File>();used=0 };parts.last()+=group;used+=bytes }
        for(folder in folders) {
            val costs=folder.map { cost(it,names.getValue(it)) }
            if(costs.sum()<=budget) add(folder,costs.sum())
            else folder.zip(costs).forEach { (file,bytes) -> require(bytes<=budget) { "${file.name} lớn hơn một phần ZIP" };add(listOf(file),bytes) }
        }
        // Hero folders name a part when it has any, rather than the long name of the run their list pages are in.
        fun labels(group:List<File>)=group.map { names.getValue(it).split('/') }.let { paths -> paths.filter { it.size>=3 }.ifEmpty { paths } }.map { (it.getOrNull(it.size-2)?:"anh").take(40) }
        val digits=maxOf(2,parts.size.toString().length)
        return parts.mapIndexed { i,group -> val first=labels(group).first();val last=labels(group).last()
            Part((i+1).toString().padStart(digits,'0')+"_"+(if(first==last) first else first+"_"+last)+".zip",group) }
    }

    /**
     * Writes [files] to [out] as one ZIP of the part ZIPs of [plan], with paths relative to [root], and returns the
     * parts. PNG files are stored without compression: they are already compressed, and deflating them again costs
     * minutes on a phone for nothing. [report] gets the bytes done, the bytes in all, the 1-based number of the file
     * and of its part, and the file's path.
     */
    fun export(files:List<File>,root:File,out:OutputStream,cancelled:()->Boolean,limit:Long=PART_LIMIT,report:(Long,Long,Int,Int,String)->Unit):List<Part> {
        val parts=plan(files,root,limit)
        val total=files.sumOf { it.length() };var done=0L;var number=0;val buffer=ByteArray(256*1024)
        ZipOutputStream(out.buffered(256*1024)).use { outer ->
            // The parts are mostly PNG data, which would not shrink either.
            outer.setLevel(Deflater.NO_COMPRESSION)
            for((p,part) in parts.withIndex()) {
                outer.putNextEntry(ZipEntry(part.name))
                val counter=Counter(outer)
                ZipOutputStream(counter.buffered(256*1024)).use { zip ->
                    for(file in part.files) {
                        number++
                        val name=file.relativeTo(root).invariantSeparatorsPath
                        zip.setLevel(if(file.extension.equals("png",true)) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { input ->
                            while(true) {
                                if(cancelled()) throw Cancelled()
                                val n=input.read(buffer);if(n<0) break
                                zip.write(buffer,0,n);done+=n;report(done,total,number,p+1,name)
                            }
                        }
                        zip.closeEntry()
                    }
                }
                part.bytes=counter.count
                check(part.bytes<=limit) { "${part.name} vượt giới hạn ${Format.size(limit)}" }
                outer.closeEntry()
            }
        }
        return parts
    }

    /** Counts the bytes of a part ZIP and leaves the outer ZIP open when the part is closed. */
    private class Counter(out:OutputStream):FilterOutputStream(out) {
        var count=0L
        override fun write(b:Int) { out.write(b);count++ }
        override fun write(b:ByteArray,off:Int,len:Int) { out.write(b,off,len);count+=len }
        override fun close() { flush() }
    }
}
