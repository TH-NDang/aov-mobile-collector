package vn.ndang.aovcollector

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Native Pictures imports are opaque bytes, never a decoded bitmap or a provider thumbnail. */
object OriginalPhotoCopy {
    const val MAX_BYTES = 32L * 1024 * 1024

    data class Integrity(val bytes: Long, val sha256: String)

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun digest(input: InputStream, allow: () -> Boolean = { true }): Integrity {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var size = 0L
        while (true) {
            check(allow()) { "Đã hủy sao chép ảnh" }
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            size += count
            require(size <= MAX_BYTES) { "Ảnh vượt giới hạn 32 MB" }
            hash.update(buffer, 0, count)
        }
        require(size > 0) { "Ảnh gốc rỗng" }
        return Integrity(size, hex(hash.digest()))
    }

    fun matches(file: File, expected: Integrity): Boolean = try {
        file.isFile && file.length() == expected.bytes && file.inputStream().use { digest(it) } == expected
    } catch (_: Exception) {
        false
    }

    /** A short provider read is a failure, even if it produced a nonempty image prefix. */
    fun copy(input: InputStream, partial: File, expectedSize: Long, allow: () -> Boolean = { true }): Integrity {
        require(expectedSize in 0..MAX_BYTES) { "Ảnh vượt giới hạn 32 MB" }
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(128 * 1024)
            var size = 0L
            partial.outputStream().use { out ->
                while (true) {
                    check(allow()) { "Đã hủy sao chép ảnh" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    size += count
                    require(size <= MAX_BYTES) { "Ảnh vượt giới hạn 32 MB" }
                    out.write(buffer, 0, count)
                    hash.update(buffer, 0, count)
                }
                out.fd.sync()
            }
            require(size > 0 && (expectedSize == 0L || size == expectedSize)) { "Ảnh gốc chưa đọc đủ byte; hãy thử lại" }
            val result = Integrity(size, hex(hash.digest()))
            require(matches(partial, result)) { "Bản sao không khớp ảnh gốc" }
            return result
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    /** File signatures take priority over provider labels. No format conversion is performed. */
    fun extension(header: ByteArray, mime: String, name: String): String {
        val u = header.map { it.toInt() and 255 }
        if (u.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10)) return "png"
        if (u.size >= 3 && u[0] == 255 && u[1] == 216 && u[2] == 255) return "jpg"
        if (header.size >= 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) {
            return "webp"
        }
        return when (mime.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> name.substringAfterLast('.', "jpg").lowercase().takeIf { it in setOf("png", "jpg", "jpeg", "webp") } ?: "jpg"
        }
    }
}
