package vn.ndang.aovcollector

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Sync then atomic replace on the same filesystem; never interpret a damaged journal as a fresh run. */
class ProProgressFile(private val file: File) {
    fun exists() = file.exists()

    fun read(): JSONObject = JSONObject(file.readText()).also {
        require(it.optInt("schema") == 1 && it.optJSONObject("heroes") != null && it.optInt("next", -1) >= 0) { "Invalid Pro journal" }
        val heroes = it.getJSONObject("heroes")
        for (key in heroes.keys()) {
            val hero = heroes.getJSONObject(key)
            require(hero.optString("hero").isNotBlank() && hero.optInt("index", -1) >= 0) { "Invalid Pro hero journal" }
        }
    }

    fun write(state: JSONObject) {
        require(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val part = File(file.parentFile, file.name + ".part")
        try {
            FileOutputStream(part).use { out ->
                out.write(state.toString(2).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(part.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            part.delete()
        }
    }
}
