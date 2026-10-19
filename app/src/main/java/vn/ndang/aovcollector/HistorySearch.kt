package vn.ndang.aovcollector

import java.io.File
import java.util.ArrayDeque

/**
 * File-name and display-title search for the collector's history. The same scanner works in
 * the full library and from any open subfolder; it does not parse image bytes or leave [root].
 * Run on a worker, not Android's main thread. Only folders and PNG captures are visible.
 */
object HistorySearch {
    const val LIMIT = 300
    const val WALK_LIMIT = 80_000

    fun find(root: File, query: String, title: (File) -> String, limit: Int = LIMIT): List<File> {
        require(limit in 1..5000)
        val key = GameScreens.plain(query.trim())
        if (query.isBlank() || !root.isDirectory) return emptyList()
        val found = ArrayList<File>()
        val queue = ArrayDeque<File>()
        root.listFiles()?.sortedBy { it.name }?.asReversed()?.forEach { queue.addLast(it) }
        var walked = 0
        while (queue.isNotEmpty() && found.size < limit && walked < WALK_LIMIT) {
            val file = queue.removeLast()
            if (++walked > WALK_LIMIT) break
            if (file.isDirectory && file.name != GameEntry.TRACE_DIR) {
                // Match a folder itself (e.g. Violet), not every image only because the parent matched.
                if (GameScreens.plain(file.name + " " + title(file)).contains(key)) found.add(file)
                file.listFiles()?.sortedBy { it.name }?.asReversed()?.forEach { queue.addLast(it) }
            } else if (GamePhotoImport.isImage(file)) {
                // Both stored file names and human-readable labels ("Chiêu 1", "Trang 2") work.
                if (GameScreens.plain(file.nameWithoutExtension + " " + title(file)).contains(key)) found.add(file)
            }
        }
        return found.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }
}
