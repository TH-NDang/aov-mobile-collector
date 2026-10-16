package vn.ndang.aovcollector

import java.io.File

/**
 * Plans a non-destructive copy from the private history into a user-selected Android document tree.
 *
 * Preserve paths relative to captures/, no matter which folder was selected: copying
 * only "Tướng Violet" still produces <run>/<hero>/photos in Pictures. This also prevents
 * two heroes of the same name from distinct runs accidentally overwriting each other.
 * Only image files are copied. ZIP export remains the full backup (including JSON metadata).
 */
object PictureCopyPlan {
    const val MAX_IMAGES=50_000
    data class Entry(val file:File,val path:String,val size:Long)
    val imageExtensions=setOf("png","jpg","jpeg","webp")

    fun create(captures:File,selections:List<File>):List<Entry> {
        require(selections.isNotEmpty()) { "Chưa chọn ảnh hoặc thư mục nào" }
        val root=captures.canonicalFile
        val prefix=root.path+File.separator
        val chosen=selections.distinctBy { it.canonicalPath }.map { it.canonicalFile }
        require(chosen.all { it.path.startsWith(prefix) }) { "Mục cần sao chép nằm ngoài Lịch sử" }
        val images=LinkedHashMap<String,Entry>()
        for(selection in chosen) {
            val found=if(selection.isDirectory) selection.walkTopDown().onEnter { it.name!=GameEntry.TRACE_DIR }
                else sequenceOf(selection)
            for(file in found) {
                if(!file.isFile || file.extension.lowercase() !in imageExtensions) continue
                val real=file.canonicalFile
                require(real.path.startsWith(prefix)) { "Phát hiện ảnh ngoài Lịch sử" }
                val relative=real.relativeTo(root).invariantSeparatorsPath
                require(relative.split('/').all { it.isNotBlank()&&it!="."&&it!=".." }) { "Tên ảnh không hợp lệ" }
                images.putIfAbsent(relative,Entry(real,relative,real.length()))
                require(images.size<=MAX_IMAGES) { "Một lần sao chép tối đa $MAX_IMAGES ảnh" }
            }
        }
        return images.values.sortedBy { it.path }
    }
}
