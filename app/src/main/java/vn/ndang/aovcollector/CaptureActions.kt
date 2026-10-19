package vn.ndang.aovcollector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Shared image actions; clipboard exposes only a dedicated cache snapshot, never private capture folders. */
object CaptureActions {
    const val EXPORT = "capture-export-token"
    private val exports = ConcurrentHashMap<String, List<File>>()

    fun files(c: Context, entries: List<CaptureHistory.Entry>): List<File> {
        require(entries.isNotEmpty()) { "Chưa chọn ảnh" }
        val root = File(c.filesDir, "captures").canonicalFile
        return entries.map { entry ->
            require(entry.state == "saved") { "Ảnh chưa lưu xong" }
            File(root, entry.path).canonicalFile.also {
                require(it.path.startsWith(root.path + File.separator) && it.isFile && it.length() > 0 && GamePhotoImport.isImage(it)) {
                    "Ảnh không còn đọc được: ${entry.name}"
                }
            }
        }.distinctBy { it.path }
    }

    fun exportIntent(c: Context, entries: List<CaptureHistory.Entry>): Intent {
        val token = UUID.randomUUID().toString()
        exports[token] = files(c, entries)
        return Intent(c, MainActivity::class.java).putExtra(EXPORT, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun takeExport(token: String): List<File>? = exports.remove(token)

    /** Copy file bytes on a worker; setting the clipboard itself is done on the main thread. */
    fun clipboardSnapshot(c: Context, entry: CaptureHistory.Entry): android.net.Uri {
        val source = files(c, listOf(entry)).single()
        val dir = File(c.cacheDir, "clipboard-images").apply { mkdirs() }
        dir.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }.forEach { it.delete() }
        val target = File(dir, HeroNames.slug(entry.name) + "-" + UUID.randomUUID() + "." + source.extension.lowercase())
        source.copyTo(target)
        return FileProvider.getUriForFile(c, c.packageName + ".clipboard", target)
    }

    fun setClipboard(c: Context, name: String, uri: android.net.Uri) {
        c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newUri(c.contentResolver, name, uri))
    }
}
