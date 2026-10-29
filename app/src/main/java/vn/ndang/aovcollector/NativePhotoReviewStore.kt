package vn.ndang.aovcollector

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Unverified source bytes, isolated from named hero images, manifests and cleanup eligibility. */
internal object NativePhotoReviewStore {
    private const val MAX_PER_ATTEMPT = 8
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024
    private const val MAX_RUN_BYTES = 64L * 1024 * 1024

    fun stage(
        run: File,
        attempt: JSONObject,
        sourceId: String,
        modified: Long,
        width: Int,
        height: Int,
        bytes: ByteArray,
        allow: () -> Boolean = { true }
    ) {
        check(allow()) { "Đã hủy giữ ảnh chờ đối chiếu" }
        if (width !in 32..512 ||
            height !in 32..512 ||
            width.toDouble() / height !in .75..1.34 ||
            bytes.isEmpty() ||
            bytes.size > MAX_FILE_BYTES
        ) {
            attempt.put("reviewSkipped", true)
            return
        }
        val expected = OriginalPhotoCopy.digest(bytes.inputStream(), allow)
        val candidates = attempt.optJSONArray("reviewCandidates") ?: JSONArray()
        val old = (0 until candidates.length()).map { candidates.getJSONObject(it) }
            .firstOrNull { it.optString("sourceId") == sourceId && it.optString("sha256") == expected.sha256 }
        if (old != null) {
            val file = File(run, old.getString("path"))
            require(file.canonicalPath.startsWith(run.canonicalPath + File.separator))
            if (OriginalPhotoCopy.matches(file, expected)) return
        }
        val root = File(run, "native-review")
        val currentBytes = if (root.exists()) root.walkTopDown().filter(File::isFile).sumOf(File::length) else 0L
        if ((old == null && candidates.length() >= MAX_PER_ATTEMPT) || currentBytes + bytes.size > MAX_RUN_BYTES) {
            attempt.put("reviewSkipped", true)
            return
        }
        val id = attempt.getString("id")
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val token = MessageDigest.getInstance("SHA-256").digest((sourceId + expected.sha256).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val extension = OriginalPhotoCopy.extension(bytes.take(16).toByteArray(), "", "")
        val directory = File(root, id)
        require(directory.isDirectory || directory.mkdirs()) { "Không tạo được thư mục giữ ảnh chờ đối chiếu" }
        val target = File(directory, "$token.$extension")
        val partial = File(directory, "$token.part")
        val actual = OriginalPhotoCopy.copy(bytes.inputStream(), partial, bytes.size.toLong(), allow)
        require(actual == expected)
        require(partial.renameTo(target)) { "Không giữ được ảnh chờ đối chiếu" }
        require(OriginalPhotoCopy.matches(target, expected)) { "Bản giữ ảnh chờ đối chiếu không khớp" }
        if (old == null) {
            candidates.put(
                JSONObject().put("sourceId", sourceId).put("modified", modified)
                    .put("width", width).put("height", height).put("bytes", expected.bytes).put("sha256", expected.sha256)
                    .put("path", target.relativeTo(run).invariantSeparatorsPath).put("verifiedMapping", false)
            )
            attempt.put("reviewCandidates", candidates)
        }
    }
}
