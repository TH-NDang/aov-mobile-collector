package vn.ndang.aovcollector

import java.io.File

/** Versioned, byte-bounded cache. A warm lookup never opens the image decoder. */
internal class ImageLoadCache<T>(private val budget: Int, private val weight: (T) -> Int) {
    data class Request(val path: String, val modified: Long, val bytes: Long, val width: Int, val height: Int, val crop: Boolean) {
        fun unchanged(): Boolean {
            val file = File(path)
            return file.isFile && file.length() == bytes && file.lastModified() == modified
        }
    }

    private val entries = LinkedHashMap<Request, T>(16, 0.75f, true)
    private var bytes = 0L

    @Volatile var generation = 0L
        private set

    fun request(file: File, width: Int, height: Int, crop: Boolean): Request? =
        if (file.isFile) Request(file.path, file.lastModified(), file.length(), width, height, crop) else null

    @Synchronized fun cached(request: Request): T? {
        if (!request.unchanged()) {
            entries.remove(request)?.let { bytes -= weight(it) }
            return null
        }
        return entries[request]
    }

    fun load(request: Request, generation: Long, decode: () -> T?): T? {
        if (generation != this.generation || !request.unchanged()) return null
        cached(request)?.let { return it }
        val value = decode() ?: return null
        synchronized(this) {
            if (generation != this.generation || !request.unchanged()) return null
            val size = weight(value)
            if (size in 1..budget) {
                entries.remove(request)?.let { bytes -= weight(it) }
                entries[request] = value
                bytes += size
                val oldest = entries.entries.iterator()
                while (bytes > budget && oldest.hasNext()) {
                    bytes -= weight(oldest.next().value)
                    oldest.remove()
                }
            }
            return value
        }
    }

    @Synchronized fun cancelRequests() {
        generation++
    }

    @Synchronized fun clear() {
        generation++
        entries.clear()
        bytes = 0
    }
}
