package vn.ndang.aovcollector

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a long step has finished, kept in its run's folder by step ([step], as 0001): Thống kê's tables, Chiến khu's
 * heroes and players. The service kept them in memory only, so a run paused with the app still running went on where
 * it was, but one cut by the phone turning off started its step over. Written whole and moved into place, as
 * [ProProgressFile]; a file that cannot be read is an empty one, the step then done again rather than skipped.
 */
class StepProgress(private val file: File) {
    fun read(step: String): JSONObject? = all()?.optJSONObject(step)

    fun write(step: String, data: JSONObject): Boolean = try {
        val all = all() ?: JSONObject()
        all.put(step, data)
        require(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val part = File(file.parentFile, file.name + ".part")
        try {
            FileOutputStream(part).use { out ->
                out.write(all.toString(2).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(part.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            part.delete()
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun all(): JSONObject? = try {
        if (file.isFile) JSONObject(file.readText()) else null
    } catch (_: Exception) {
        null
    }

    companion object {
        const val NAME = "step-progress.json"

        fun strings(data: JSONObject?, key: String): MutableSet<String> {
            val out = LinkedHashSet<String>()
            val a = data?.optJSONArray(key) ?: return out
            for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() }?.let(out::add)
            return out
        }

        fun array(values: Collection<String>) = JSONArray(values.toList())
    }
}
