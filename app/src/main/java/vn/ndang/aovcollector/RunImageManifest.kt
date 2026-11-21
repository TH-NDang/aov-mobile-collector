package vn.ndang.aovcollector

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Verified native images belonging to one capture run; no paths outside that run are exported. */
object RunImageManifest {
    const val FILE = "images.json"
    const val SCHEMA_VERSION = 1
    private val fingerprint = Regex("[a-f0-9]{64}")
    private val heroSlug = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")

    /**
     * Call only after the native file has been committed. [sourceId] is the caller's full SHA-256
     * source fingerprint, never a raw provider URI. Integrity is checked again before publishing.
     * Missing or damaged old images are dropped; malformed manifests fail without being replaced.
     */
    @Synchronized
    fun record(
        run: File,
        file: File,
        group: String,
        item: String,
        sourceId: String,
        verified: OriginalPhotoCopy.Integrity,
        reused: Boolean = false
    ) {
        val root = run.canonicalFile
        require(root.isDirectory) { "Thư mục lượt thu thập không tồn tại" }
        require(group.isNotBlank() && item.isNotBlank()) { "Ảnh gốc thiếu nhóm hoặc tên mục" }
        require(fingerprint.matches(sourceId)) { "Mã nguồn ảnh phải là dấu vân tay SHA-256" }
        require(validIntegrity(verified)) { "Thông tin kiểm tra ảnh gốc không hợp lệ" }
        val committed = file.canonicalFile
        require(committed.path.startsWith(root.path + File.separator)) { "Ảnh nằm ngoài lượt thu thập" }
        val path = committed.relativeTo(root).invariantSeparatorsPath
        require(validPath(path)) { "Đường dẫn ảnh gốc không hợp lệ" }
        require(OriginalPhotoCopy.matches(committed, verified)) { "Ảnh gốc chưa được lưu đủ hoặc đã thay đổi" }

        val manifest = File(root, FILE)
        val previous = read(manifest)
        val entries = LinkedHashMap<String, JSONObject>()
        val oldImages = previous.getJSONArray("images")
        val seen = HashSet<String>()
        for (i in 0 until oldImages.length()) {
            val old = oldImages.getJSONObject(i)
            validateEntry(old)
            val oldPath = old.getString("path")
            require(seen.add(oldPath)) { "Danh mục ảnh có đường dẫn trùng nhau" }
            val oldFile = File(root, oldPath).canonicalFile
            val expected = OriginalPhotoCopy.Integrity(old.getLong("bytes"), old.getString("sha256"))
            if (oldFile.path.startsWith(root.path + File.separator) &&
                OriginalPhotoCopy.matches(oldFile, expected)
            ) {
                entries[oldPath] = old
            }
        }
        entries[path] = entry(path, group, item, sourceId, verified, reused)
        previous.put("images", JSONArray().apply { entries.values.forEach { put(it) } })
        writeAtomically(manifest, previous.toString(2))
    }

    private fun entry(
        path: String,
        group: String,
        item: String,
        sourceId: String,
        verified: OriginalPhotoCopy.Integrity,
        reused: Boolean
    ): JSONObject = JSONObject().put("path", path).put("item", item).put("group", group)
        .put("type", imageType(group)).put("bytes", verified.bytes)
        .put("sha256", verified.sha256).put("sourceId", sourceId).put("reused", reused)
        .apply { hero(group, item)?.let { put("hero", it) } }

    private fun imageType(group: String) = when (group) {
        "quan-co" -> "portrait"
        "chieu-quan-co" -> "skill"
        else -> "native-image"
    }

    /** Identity comes from the collector's verified item token, never a guessed filename. */
    private fun hero(group: String, item: String): String? {
        val candidate = when {
            group == "quan-co" && item.endsWith("-head") -> item.removeSuffix("-head")
            group == "chieu-quan-co" &&
                "-skill-" in item &&
                item.substringAfter("-skill-").isNotBlank() -> item.substringBefore("-skill-")
            else -> return null
        }
        return candidate.takeIf { heroSlug.matches(it) }
    }

    private fun validPath(path: String): Boolean {
        val parts = path.split('/')
        return parts.size >= 3 &&
            parts.first() == "anh-goc" &&
            parts.none {
                it.isBlank() ||
                    it == "." ||
                    it == ".." ||
                    '\\' in it ||
                    ':' in it ||
                    it.any { c -> c.code < 32 }
            } &&
            parts.last().substringAfterLast('.', "") in imageExtensions
    }

    private fun validIntegrity(value: OriginalPhotoCopy.Integrity) =
        value.bytes in 1..OriginalPhotoCopy.MAX_BYTES && fingerprint.matches(value.sha256)

    private fun read(file: File): JSONObject {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("collectionMode", "chess")
                .put("images", JSONArray())
        }
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Không đọc được danh mục ảnh" }
        val tokener = JSONTokener(file.readText(Charsets.UTF_8))
        val result = JSONObject(tokener)
        require(tokener.nextClean() == '\u0000') { "Danh mục ảnh có dữ liệu thừa" }
        require(
            integer(result, "schemaVersion") == SCHEMA_VERSION.toLong() &&
                string(result, "collectionMode") == "chess" &&
                result.get("images") is JSONArray
        ) {
            "Định dạng danh mục ảnh không được hỗ trợ"
        }
        return result
    }

    private fun validateEntry(value: JSONObject) {
        val path = string(value, "path")
        val group = string(value, "group")
        val item = string(value, "item")
        require(
            validPath(path) &&
                group.isNotBlank() &&
                item.isNotBlank() &&
                string(value, "type") == imageType(group) &&
                fingerprint.matches(string(value, "sourceId")) &&
                validIntegrity(OriginalPhotoCopy.Integrity(integer(value, "bytes"), string(value, "sha256")))
        ) {
            "Mục trong danh mục ảnh không hợp lệ"
        }
        require(!value.has("reused") || value.get("reused") is Boolean) { "Trạng thái dùng lại ảnh không hợp lệ" }
        require(!value.has("hero") || string(value, "hero") == hero(group, item)) { "Tên quân cờ không khớp mục ảnh" }
    }

    // JSONObject getters coerce strings/numbers; require actual schema types before reading them.
    private fun string(value: JSONObject, key: String): String {
        val result = value.get(key)
        require(result is String) { "Trường $key trong danh mục ảnh không hợp lệ" }
        return result
    }

    private fun integer(value: JSONObject, key: String): Long {
        val result = value.get(key)
        require(result is Number) { "Trường $key trong danh mục ảnh không hợp lệ" }
        return requireNotNull(result.toString().toLongOrNull()) { "Trường $key phải là số nguyên" }
    }

    private fun writeAtomically(file: File, text: String) {
        val partial = File(file.parentFile, file.name + ".part")
        require(!Files.isSymbolicLink(partial.toPath()) && (!partial.exists() || partial.isFile)) {
            "Không ghi được danh mục ảnh tạm"
        }
        try {
            FileOutputStream(partial).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            // Never fall back to delete + rename or non-atomic replacement.
            Files.move(
                partial.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            if (partial.isFile) partial.delete()
        }
    }
}
