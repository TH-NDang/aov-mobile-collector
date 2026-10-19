package vn.ndang.aovcollector

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/** Optional, user-selected deletion of native sources whose run-local originals are still intact. */
object GamePhotoCleanup {
    data class Source(
        val id: String,
        val name: String,
        val size: Long,
        val modified: Long,
        val relativeDir: String = "",
        val canDelete: Boolean = false
    ) {
        val key: String get() = "$id|$size|$modified"
    }

    data class Candidate(
        val tree: String,
        val source: Source,
        val identity: String,
        val path: String,
        val group: String,
        val item: String,
        val integrity: OriginalPhotoCopy.Integrity
    )

    data class Preview(val runId: String, val tree: String, val candidates: List<Candidate>, val skipped: Int, val canWrite: Boolean)

    data class Result(val deleted: Int, val skipped: Int, val failed: Int, val cancelled: Boolean)

    /** Injectable so destructive-path tests only ever use synthetic documents. */
    interface SourceAccess {
        fun canRead(): Boolean

        fun canWrite(): Boolean

        fun scan(allow: () -> Boolean): List<Source>

        fun current(id: String): Source?

        fun open(id: String): InputStream?

        fun delete(id: String): Boolean
    }

    internal fun identity(tree: String, source: Source): String = MessageDigest.getInstance("SHA-256")
        .digest((tree + "|" + source.key).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun runFolder(context: Context, runId: String): File {
        require(runId.isNotBlank() && File(runId).name == runId && runId != "." && runId != "..") { "Lượt không hợp lệ" }
        val captures = File(context.filesDir, "captures").canonicalFile
        return File(captures, runId).also {
            require(it.isDirectory && it.canonicalFile.parentFile == captures) { "Không tìm thấy lượt này" }
        }
    }

    private fun readState(run: File) = JSONObject(File(run, GamePhotoSession.FILE).readText())

    private fun readManifest(run: File) = JSONObject(File(run, "images.json").readText())

    fun preview(context: Context, runId: String, allow: () -> Boolean = { true }): Preview = synchronized(GamePhotoSession) {
        val run = runFolder(context, runId)
        val state = readState(run)
        val tree = state.getString("tree")
        val access = AndroidSources(context, Uri.parse(tree))
        require(access.canRead()) { "Quyền đọc Pictures đã hết hạn hoặc đã đổi thư mục. Chọn lại đúng thư mục Pictures của lượt này." }
        require(state.optInt("storageVersion") == 2) { "Chỉ dọn ảnh của lượt mới đã lưu ảnh gốc trong cùng lượt." }
        val candidates = ArrayList<Candidate>()
        var skipped = 0
        val manifest = readManifest(run)
        val sources = access.scan(allow).distinctBy { it.id }
        for (source in sources) {
            if (!allow()) break
            val candidate = eligible(run, tree, state, manifest, source)
            if (candidate != null && verify(run, candidate, access, allow, requireWrite = false)) {
                candidates.add(candidate)
            } else {
                skipped++
            }
        }
        Preview(runId, tree, candidates, skipped, access.canWrite())
    }

    fun deleteSelected(
        context: Context,
        runId: String,
        selected: List<Candidate>,
        allow: () -> Boolean = { true },
        progress: (Int, Int) -> Unit = { _, _ -> }
    ): Result = synchronized(GamePhotoSession) {
        val run = runFolder(context, runId)
        val tree = selected.firstOrNull()?.tree ?: return@synchronized Result(0, 0, 0, false)
        deleteSelected(
            run,
            tree,
            selected,
            AndroidSources(context, Uri.parse(tree)),
            { readState(run) },
            { readManifest(run) },
            allow,
            progress
        )
    }

    /** Re-read journals and exact sources after preview; an old checkbox is never deletion evidence. */
    internal fun deleteSelected(
        run: File,
        tree: String,
        selected: List<Candidate>,
        access: SourceAccess,
        state: () -> JSONObject,
        manifest: () -> JSONObject,
        allow: () -> Boolean = { true },
        progress: (Int, Int) -> Unit = { _, _ -> }
    ): Result {
        val picks = selected.distinctBy { it.identity }
        if (picks.isEmpty()) return Result(0, 0, 0, false)
        if (!access.canRead() || !access.canWrite()) return Result(0, picks.size, 0, false)
        val currentSources = access.scan(allow).associateBy { it.id }
        var deleted = 0
        var skipped = 0
        var failed = 0
        for ((index, pick) in picks.withIndex()) {
            if (!allow()) break
            progress(index, picks.size)
            try {
                val source = currentSources[pick.source.id]
                val current = source?.let { eligible(run, tree, state(), manifest(), it) }
                if (current == null ||
                    !sameSelection(pick, current) ||
                    !verify(run, current, access, allow, requireWrite=true) ||
                    !allow()
                ) {
                    skipped++
                    continue
                }
                // No asynchronous work is inserted between verification and this exact-document call.
                if (access.delete(current.source.id)) deleted++ else failed++
            } catch (_: Exception) {
                if (allow()) failed++
            }
        }
        progress(deleted + skipped + failed, picks.size)
        return Result(deleted, skipped, failed, !allow())
    }

    private fun sameSelection(a: Candidate, b: Candidate) = a.tree == b.tree &&
        a.source.id == b.source.id &&
        a.source.key == b.source.key &&
        a.source.name == b.source.name &&
        a.source.relativeDir == b.source.relativeDir &&
        a.identity == b.identity &&
        a.path == b.path &&
        a.group == b.group &&
        a.item == b.item &&
        a.integrity == b.integrity

    /** Failed/pending copies have no matching integrity + manifest pair and cannot enter this list. */
    internal fun eligible(run: File, tree: String, state: JSONObject, manifest: JSONObject, source: Source): Candidate? = try {
        require(state.optInt("storageVersion") == 2 && state.getString("tree") == tree)
        require(manifest.optInt("schemaVersion") == 1 && manifest.optString("collectionMode") == "chess")
        val baseline = state.getJSONArray("baseline")
        require((0 until baseline.length()).none { baseline.getString(it) == source.id })
        val assignment = state.getJSONObject("assignments").getJSONObject(source.id)
        val group = assignment.getString("group")
        val item = assignment.getString("item")
        require(GamePhotoImport.GROUPS.any { it.key == group && it.key != "chua-phan-loai" })
        val saves = state.getJSONArray("saves")
        require(
            (0 until saves.length()).count {
                val save = saves.getJSONObject(it)
                save.optString("group") == group &&
                    save.optString("item") == item &&
                    save.optLong("time") == assignment.getLong("time")
            } == 1
        )
        val identity = identity(tree, source)
        require(assignment.getString("sourceIdentity") == identity)
        val record = state.getJSONObject("originalIntegrity").getJSONObject(identity)
        val path = record.getString("path")
        val integrity = OriginalPhotoCopy.Integrity(record.getLong("bytes"), record.getString("sha256"))
        require(integrity.bytes in 1..OriginalPhotoCopy.MAX_BYTES && Regex("[a-f0-9]{64}").matches(integrity.sha256))
        require(source.size == 0L || source.size == integrity.bytes)
        confinedFile(run, path, group)
        val images = manifest.getJSONArray("images")
        require((0 until images.length()).count { images.getJSONObject(it).optString("path") == path } == 1)
        require(
            (0 until images.length()).count {
                val image = images.getJSONObject(it)
                image.optString("path") == path &&
                    image.optString("sourceId") == identity &&
                    image.optString("group") == group &&
                    image.optString("item") == item &&
                    image.optLong("bytes") == integrity.bytes &&
                    image.optString("sha256") == integrity.sha256 &&
                    !image.optBoolean("reused", false)
            } == 1
        )
        Candidate(tree, source, identity, path, group, item, integrity)
    } catch (_: Exception) {
        null
    }

    internal fun confinedFile(run: File, path: String, group: String): File {
        require(
            !File(path).isAbsolute &&
                !path.contains('\\') &&
                path.split('/').none { it.isBlank() || it == "." || it == ".." }
        )
        require(path.substringBeforeLast('/', "") == "anh-goc/" + GamePhotoImport.groupPath(group))
        require(GamePhotoImport.imageExtension(path.substringAfterLast('.', "")))
        val canonicalRoot = run.canonicalFile
        val expected = File(canonicalRoot, path).absoluteFile
        val file = File(run, path)
        require(file.canonicalFile == expected && file.canonicalPath.startsWith(canonicalRoot.path + File.separator))
        require(!File(file.parentFile, file.name + ".part").exists())
        return file
    }

    /** Hash both current copies, then recheck metadata, grant and confinement immediately before use. */
    private fun verify(run: File, pick: Candidate, access: SourceAccess, allow: () -> Boolean, requireWrite: Boolean): Boolean = try {
        require(allow() && access.canRead() && (!requireWrite || access.canWrite()))
        val before = access.current(pick.source.id) ?: error("Ảnh nguồn không còn")
        require(before.id == pick.source.id && before.key == pick.source.key && before.name == pick.source.name)
        require(!requireWrite || before.canDelete)
        val file = confinedFile(run, pick.path, pick.group)
        require(file.isFile && file.length() == pick.integrity.bytes)
        val original = access.open(before.id)?.use { OriginalPhotoCopy.digest(it, allow) } ?: error("Không đọc được ảnh nguồn")
        require(original == pick.integrity)
        val copied = file.inputStream().use { OriginalPhotoCopy.digest(it, allow) }
        require(copied == pick.integrity)
        val after = access.current(before.id) ?: error("Ảnh nguồn không còn")
        require(after.id == before.id && after.key == before.key && after.name == before.name)
        require(!requireWrite || after.canDelete)
        require(confinedFile(run, pick.path, pick.group).isFile && file.length() == copied.bytes)
        allow() && access.canRead() && (!requireWrite || access.canWrite())
    } catch (_: Exception) {
        false
    }

    fun canWrite(context: Context, tree: String) = AndroidSources(context, Uri.parse(tree)).canWrite()

    private class AndroidSources(context: Context, private val tree: Uri) : SourceAccess {
        private val app = context.applicationContext
        private val resolver = app.contentResolver

        override fun canRead() = GamePhotoImport.chosen(app) == tree &&
            resolver.persistedUriPermissions.any {
                it.uri == tree && it.isReadPermission
            }

        override fun canWrite() = canRead() && resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

        private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

        override fun scan(allow: () -> Boolean) = GamePhotoImport.scan(app, tree, allow).map {
            Source(it.id, it.name, it.size, it.modified, it.relativeDir)
        }

        override fun current(id: String): Source? {
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS
            )
            return resolver.query(uri(id), cols, null, null, null)?.use { c ->
                if (!c.moveToFirst()) {
                    null
                } else {
                    Source(
                        c.getString(0) ?: "",
                        c.getString(1) ?: "",
                        if (c.isNull(2)) 0 else c.getLong(2),
                        if (c.isNull(3)) 0 else c.getLong(3),
                        canDelete = !c.isNull(4) && (c.getInt(4) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
                    )
                }
            }
        }

        override fun open(id: String) = resolver.openInputStream(uri(id))

        override fun delete(id: String) = DocumentsContract.deleteDocument(resolver, uri(id))
    }
}
