package vn.ndang.aovcollector

import android.content.Context
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * A native Cờ Liên Quân Save Photo batch. Pictures may contain unrelated personal images: snapshot all
 * document IDs before the macro begins and copy ONLY new IDs correlated with confirmed in-game Save Photo
 * events. The event's current encyclopedia topic determines the folder, never a manual image group.
 */
object GamePhotoSession {
    const val FILE = "pictures-session.json"
    private const val LEDGER = "pictures-copy-ledger.json"
    private const val MAX_IMAGE = 32L * 1024 * 1024
    private const val CLOCK_WINDOW_MS = 20_000L
    private val validGroups = GamePhotoImport.GROUPS.map { it.key }.filter { it != "chua-phan-loai" }.toSet()

    enum class ReceiptStatus { RECEIPT, NONE, UNCERTAIN, ABSENT }

    data class Attempt(val id: String, val status: ReceiptStatus, val item: String, val matchesRequested: Boolean)

    data class ReceiptResult(
        val status: ReceiptStatus,
        val documentId: String? = null,
        val reason: String? = null,
        val absenceSafe: Boolean = false
    )

    data class Mark(val group: String, val item: String, val time: Long)

    data class Pick(val id: String, val group: String, val item: String, val size: Long)

    data class Scanned(val id: String, val size: Long, val modified: Long)

    data class Diff(val before: Int, val after: Int, val new: Int, val matching: List<Pick>, val unrelated: Int)

    data class Imported(
        val before: Int,
        val after: Int,
        val added: Int,
        val copied: Int,
        val already: Int,
        val uncertain: Int,
        val errors: Int,
        val cancelled: Boolean,
        val pending: Int = 0
    )

    /** Do not acknowledge a batch until every Save Photo event has a corresponding readable file. */
    fun checkpoint(previous: Int, totalMarks: Int, resolved: Int, failed: Int, cancelled: Boolean): Int =
        if (!cancelled && failed == 0 && resolved >= totalMarks) {
            totalMarks
        } else {
            previous.coerceIn(0, totalMarks)
        }

    /** Gives providers time to index a freshly saved game image before automatic retrying. */
    private const val RETRY_MS = 30_000L

    fun needsRetry(totalMarks: Int, syncedMarks: Int, lastAttemptedMarks: Int, lastSyncAt: Long, now: Long): Boolean =
        totalMarks > syncedMarks &&
            (
                totalMarks > lastAttemptedMarks ||
                    now - lastSyncAt >= RETRY_MS
                )

    fun diff(baseline: Set<String>, photos: List<Scanned>, marks: List<Mark>): Diff {
        val fresh = photos.filter { it.id !in baseline }.sortedWith(compareBy({ it.modified }, { it.id }))
        val available = marks.sortedBy { it.time }.toMutableList()
        val assigned = ArrayList<Pick>()
        // Explicit one-to-one matching avoids treating the whole Pictures folder as Cờ Liên Quân.
        for (photo in fresh) {
            val best = available.withIndex().mapNotNull { (index, mark) ->
                if (mark.group !in validGroups) return@mapNotNull null
                val distance = abs(mark.time - photo.modified)
                if (photo.modified <= 0L || distance > CLOCK_WINDOW_MS) null else index to distance
            }.minByOrNull { it.second }?.first
            if (best != null) {
                val mark = available.removeAt(best)
                assigned.add(Pick(photo.id, mark.group, mark.item, photo.size))
            }
        }
        // Providers sometimes omit dates; a count-only fallback is safe ONLY when the number of
        // previously-unseen files equals the number of recorded, unpaired successful saves, AND
        // every save belongs to the same class (without dates, cross-class ordering is ambiguous).
        val leftovers = fresh.filter { photo -> assigned.none { it.id == photo.id } }
        if (leftovers.isNotEmpty() &&
            leftovers.size == available.size &&
            leftovers.all { it.modified <= 0L } &&
            available.all { it.group in validGroups } &&
            available.map { it.group }.distinct().size == 1
        ) {
            leftovers.sortedBy { it.id }.zip(available.sortedBy { it.time }).forEach { (photo, mark) ->
                assigned.add(Pick(photo.id, mark.group, mark.item, photo.size))
            }
        }
        return Diff(baseline.size, photos.size, fresh.size, assigned, fresh.size - assigned.size)
    }

    private fun location(context: Context, runId: String) = File(context.filesDir, "captures/$runId/$FILE")

    private fun read(context: Context, runId: String): JSONObject {
        val file = location(context, runId)
        require(file.isFile) { "Chưa có mốc số lượng ảnh trước khi thu thập cho lượt này" }
        return JSONObject(file.readText())
    }

    private fun store(context: Context, runId: String, state: JSONObject) {
        val file = location(context, runId)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".part")
        temp.outputStream().use { output ->
            output.write(state.toString(2).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        require(temp.renameTo(file)) { "Không lưu được mốc ảnh Pictures" }
    }

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun safe(value: String) = HeroNames.slug(value).take(42).ifBlank { "anh" }

    /** Called before taking any native images. Scans the explicitly granted folder, never unrelated storage. */
    @Synchronized fun begin(context: Context, runId: String): Int {
        if (location(context, runId).exists()) return read(context, runId).optInt("before")
        val tree = GamePhotoImport.chosen(context) ?: error("Chọn thư mục Pictures trong AOV Collector trước khi chạy tải ảnh")
        val source = GamePhotoImport.scan(context, tree)
        val baseline = JSONArray()
        source.forEach { baseline.put(it.id) }
        val state = JSONObject().put("tree", tree.toString()).put("before", source.size)
            .put("startedAt", System.currentTimeMillis()).put("baseline", baseline)
            .put("saves", JSONArray()).put("status", "collecting").put("storageVersion", 2)
        store(context, runId, state)
        return source.size
    }

    /** Persist the exact granted-tree baseline before dispatching any native long-press. */
    @Synchronized fun prepareAttempt(
        context: Context,
        runId: String,
        group: String,
        item: String,
        aliases: Set<String> = emptySet(),
        expectedSignature: String? = null
    ): Attempt {
        require(group in validGroups)
        val state = read(context, runId)
        val tree = GamePhotoImport.chosen(context) ?: error("Quyền đọc Pictures đã hết hạn")
        require(tree.toString() == state.getString("tree")) { "Thư mục Pictures đã thay đổi" }
        NativePhotoReceiptJournal.existing(state, group, item, aliases)?.let { return it }
        val images = GamePhotoImport.scan(context, tree)
        val attempt = NativePhotoReceiptJournal.prepare(
            state,
            group,
            item,
            aliases,
            expectedSignature,
            images.map { it.id }.toSet(),
            System.currentTimeMillis()
        )
        store(context, runId, state)
        return attempt
    }

    /** Durably arm the attempt before dispatch; a crash after this write cannot authorize a retry. */
    @Synchronized fun startAttempt(context: Context, runId: String, attemptId: String) {
        val state = read(context, runId)
        val tree = GamePhotoImport.chosen(context) ?: error("Quyền đọc Pictures đã hết hạn")
        require(tree.toString() == state.getString("tree")) { "Thư mục Pictures đã thay đổi" }
        NativePhotoReceiptJournal.start(state, attemptId, System.currentTimeMillis())
        store(context, runId, state)
    }

    @Synchronized fun cancelBeforeDispatch(context: Context, runId: String, attemptId: String) {
        val state = read(context, runId)
        NativePhotoReceiptJournal.cancelBeforeDispatch(state, attemptId)
        store(context, runId, state)
    }

    @Synchronized fun confirmIntent(context: Context, runId: String, attemptId: String) {
        val state = read(context, runId)
        NativePhotoReceiptJournal.confirmIntent(state, attemptId, System.currentTimeMillis())
        store(context, runId, state)
    }

    /** Close only a positively confirmed Sure or positively verified no-save outcome. */
    @Synchronized fun finishAttempt(context: Context, runId: String, attemptId: String, confirmed: Boolean) {
        val state = read(context, runId)
        NativePhotoReceiptJournal.finish(state, attemptId, confirmed)
        store(context, runId, state)
    }

    /**
     * Pending attempts survive pause/process death. Timestamp/count alone never prove a save:
     * require one full, readable, content-matching native asset, then pin and mark in one write.
     */
    @Synchronized fun reconcileAttempt(context: Context, runId: String, attemptId: String): ReceiptResult = try {
        val state = read(context, runId)
        val attempt = NativePhotoReceiptJournal.find(state, attemptId)
        val tree = GamePhotoImport.chosen(context) ?: error("Quyền đọc Pictures đã hết hạn")
        require(tree.toString() == state.getString("tree")) { "Thư mục Pictures đã thay đổi" }
        when (attempt.getString("status")) {
            "receipt", "confirmed" -> ReceiptResult(ReceiptStatus.RECEIPT, attempt.optString("documentId").takeIf(String::isNotBlank))
            "prepared", "cancelled" -> ReceiptResult(ReceiptStatus.NONE)
            else -> {
                val all = GamePhotoImport.scan(context, tree)
                val baseline = NativePhotoReceiptJournal.strings(attempt.getJSONArray("baseline")) +
                    NativePhotoReceiptJournal.strings(state.getJSONArray("baseline"))
                val excluded = NativePhotoReceiptJournal.exclusions(state, attemptId)
                val startedAt = attempt.getLong("startedAt")
                val endsAt = attempt.getLong("endsAt")
                val fresh = all.filter { it.id !in baseline && it.id !in excluded }
                NativePhotoReceiptJournal.observe(attempt, fresh.map { it.id }.toSet())
                val verified = HashMap<String, OriginalPhotoCopy.Integrity>()
                val candidates = fresh.map { photo ->
                    val eligible = photo.modified >= startedAt / 1000 * 1000 && photo.modified <= endsAt && photo.modified > 0
                    if (!eligible) {
                        NativePhotoReceipt.Candidate(photo.id, photo.modified, false, false)
                    } else {
                        try {
                            val bytes = context.contentResolver.openInputStream(photo.uri)?.use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    require(output.size().toLong() + n <= MAX_IMAGE) { "Ảnh vượt giới hạn" }
                                    output.write(buffer, 0, n)
                                }
                                output.toByteArray()
                            } ?: error("Không đọc được ảnh")
                            require(bytes.isNotEmpty() && (photo.size == 0L || photo.size == bytes.size.toLong())) { "Ảnh chưa đọc đủ" }
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            require(bounds.outWidth >= 32 && bounds.outHeight >= 32) { "Ảnh chưa giải mã được" }
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 1
                                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 384) inSampleSize *= 2
                            }
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Ảnh chưa giải mã được")
                            var ambiguous = false
                            val matching = try {
                                val matches = NativePhotoReceipt.matches(attempt.optString("signature"), bitmap)
                                ambiguous =
                                    matches &&
                                    NativePhotoReceiptJournal.earlierAbsentSignatures(state, attemptId, photo.modified).any {
                                        it.isBlank() || NativePhotoReceipt.matches(it, bitmap)
                                    }
                                matches && !ambiguous
                            } finally {
                                bitmap.recycle()
                            }
                            if (matching) verified[photo.id] = OriginalPhotoCopy.digest(bytes.inputStream())
                            NativePhotoReceipt.Candidate(photo.id, photo.modified, true, matching, ambiguous)
                        } catch (_: Exception) {
                            NativePhotoReceipt.Candidate(photo.id, photo.modified, false, false)
                        }
                    }
                }
                val id = NativePhotoReceipt.select(baseline, excluded, startedAt, endsAt, candidates)
                if (id != null) NativePhotoReceiptJournal.receipt(state, attemptId, id, verified.getValue(id))
                store(context, runId, state)
                val absenceSafe = id == null &&
                    NativePhotoReceipt.absenceSafe(startedAt, endsAt, candidates, attempt.optString("signature").isNotBlank())
                if (absenceSafe && attempt.getString("status") == "absent") {
                    ReceiptResult(ReceiptStatus.ABSENT, absenceSafe = true)
                } else if (id == null) {
                    ReceiptResult(
                        ReceiptStatus.UNCERTAIN,
                        reason = NativePhotoReceipt.explain(startedAt, endsAt, candidates, attempt.optString("signature").isNotBlank()),
                        absenceSafe = absenceSafe
                    )
                } else {
                    ReceiptResult(ReceiptStatus.RECEIPT, id)
                }
            }
        }
    } catch (e: Exception) {
        ReceiptResult(
            ReceiptStatus.UNCERTAIN,
            reason = "Pictures reconciliation failed (${e.javaClass.simpleName}); receipt not committed"
        )
    }

    /** Called AFTER Save Photo → Sure has been confirmed on the correct icon/topic. */
    @Synchronized fun mark(context: Context, runId: String, group: String, item: String, aliases: Set<String> = emptySet()) {
        require(group in validGroups)
        val state = read(context, runId)
        val saves = state.getJSONArray("saves")
        val names = aliases + item
        for (i in 0 until saves.length()) {
            val saved = saves.getJSONObject(i)
            if (saved.getString("group") != group) continue
            val previous = savedItems(saved)
            if (!ChessBlessings.sameSave(group, previous, names)) continue
            val merged = ChessBlessings.mergeSavedNames(group, previous, names)
            if (merged != previous) {
                saved.put("aliases", JSONArray((merged - saved.getString("item")).sorted()))
                store(context, runId, state)
            }
            return
        }
        saves.put(
            JSONObject().put("group", group).put("item", item)
                .put("aliases", JSONArray((aliases - item).sorted())).put("time", System.currentTimeMillis())
        )
        store(context, runId, state)
    }

    /** A corrupt/unreadable journal must stop a retry rather than authorize a duplicate Save Photo. */
    @Synchronized fun hasMark(context: Context, runId: String, group: String, item: String, aliases: Set<String> = emptySet()): Boolean {
        val names = aliases + item
        val saves = if (location(context, runId).exists()) read(context, runId).getJSONArray("saves") else JSONArray()
        if ((0 until saves.length()).any { index ->
                val saved = saves.getJSONObject(index)
                saved.getString("group") == group && ChessBlessings.sameSave(group, savedItems(saved), names)
            }
        ) {
            return true
        }
        // An earlier run's save event alone is insufficient. Reuse only a named blessing
        // whose imported original is still readable and byte-for-byte intact.
        val captures = File(context.filesDir, "captures")
        return names.filter(ChessBlessings::isKey).any { key ->
            val original = BlessingPhotoIndex.findRecord(
                File(context.filesDir, BlessingPhotoIndex.FILE),
                captures,
                group,
                key,
                names,
                File(captures, GamePhotoImport.ROOT)
            ) ?: return@any false
            val state = read(context, runId)
            if (state.optInt("storageVersion", 1) >= 2) {
                reuseInRun(context, runId, state, group, item, original)
            }
            true
        }
    }

    /** Reuse verified bytes locally so exporting this run does not depend on another run. */
    private fun reuseInRun(
        context: Context,
        runId: String,
        state: JSONObject,
        group: String,
        item: String,
        saved: BlessingPhotoIndex.Saved
    ) {
        val original = saved.file
        val run = File(context.filesDir, "captures/$runId")
        val verified = saved.integrity
        require(OriginalPhotoCopy.matches(original, verified)) { "Ảnh nguồn đã thay đổi" }
        val identity = fingerprint("reused|$group|${saved.key}|${verified.sha256}")
        val destination = reuseDestination(run, state, group, saved, identity)
        val target = destination.file
        val claims = destination.claims
        val path = target.relativeTo(run).invariantSeparatorsPath
        if (!OriginalPhotoCopy.matches(target, verified)) {
            require(!target.exists() || claims.optString(path) == identity) { "Tên ảnh đã thuộc về ảnh khác" }
            require(context.filesDir.usableSpace > verified.bytes + 30L * 1024 * 1024) { "Bộ nhớ trống quá ít" }
            claims.put(path, identity)
            state.put("targetClaims", claims)
            store(context, runId, state)
            copyReusedOriginal(context, runId, original, target, verified)
        }
        RunImageManifest.record(run, target, group, item, identity, verified, reused = true)
        // Keep the original verified title anchor; a noisy request must not chain OCR aliases.
        BlessingPhotoIndex.record(
            File(context.filesDir, BlessingPhotoIndex.FILE),
            File(context.filesDir, "captures"),
            group,
            saved.key,
            target,
            saved.aliases
        )
    }

    private data class ReuseDestination(val file: File, val claims: JSONObject)

    private fun reuseDestination(
        run: File,
        state: JSONObject,
        group: String,
        saved: BlessingPhotoIndex.Saved,
        identity: String
    ): ReuseDestination {
        val folder = File(run, "anh-goc/" + GamePhotoImport.groupPath(group)).apply { mkdirs() }
        val directory = folder.relativeTo(run).invariantSeparatorsPath + "/"
        val claims = state.optJSONObject("targetClaims") ?: JSONObject()
        val localClaims = claims.keys().asSequence().filter { it.startsWith(directory) }
            .associate { it.removePrefix(directory) to claims.getString(it) }
        val base = ChessCaptureFiles.nativeBase(group, saved.key.removePrefix(ChessBlessings.PREFIX), 1)
        val name = ChessCaptureFiles.nativeTarget(
            base,
            saved.file.extension.lowercase(),
            identity,
            folder.listFiles().orEmpty().map { it.name }.toSet(),
            localClaims
        )
        return ReuseDestination(File(folder, name), claims)
    }

    private fun copyReusedOriginal(context: Context, runId: String, original: File, target: File, verified: OriginalPhotoCopy.Integrity) {
        val part = File(target.parentFile, target.name + ".part")
        val history = try {
            CaptureHistory.begin(context, runId, target)
        } catch (_: Exception) {
            null
        }
        var committed = false
        try {
            val copied = original.inputStream().use { OriginalPhotoCopy.copy(it, part, verified.bytes) }
            require(copied == verified) { "Ảnh nguồn đã thay đổi" }
            require(part.renameTo(target)) { "Không lưu được bản sao trong lượt" }
            committed = true
        } finally {
            part.delete()
            history?.let {
                try {
                    CaptureHistory.finish(context, it, committed)
                } catch (_: Exception) {}
            }
        }
    }

    /** The stable alias proves the item came from a recognized cyan blessing heading. */
    internal fun recordImportedBlessing(context: Context, state: JSONObject, pick: Pick, file: File) {
        if (!ChessBlessings.isGroup(pick.group)) return
        val saves = state.getJSONArray("saves")
        val key = ChessBlessings.key(pick.item)
        val verified = (0 until saves.length()).map { saves.getJSONObject(it) }.firstOrNull { saved ->
            saved.getString("group") == pick.group && saved.getString("item") == pick.item && key in savedItems(saved)
        }
        if (verified != null) {
            BlessingPhotoIndex.record(
                File(context.filesDir, BlessingPhotoIndex.FILE),
                File(context.filesDir, "captures"),
                pick.group,
                key,
                file,
                savedItems(verified)
            )
        }
    }

    private fun savedItems(saved: JSONObject): Set<String> {
        val aliases = if (saved.has("aliases")) saved.getJSONArray("aliases") else JSONArray()
        return (0 until aliases.length()).map { aliases.getString(it) }.toSet() + saved.getString("item")
    }

    private fun marks(state: JSONObject): List<Mark> {
        val arr = state.getJSONArray("saves")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Mark(it.optString("group"), it.optString("item"), it.optLong("time"))
        }
    }

    /** Called when the user returns to the app, or after the macro finishes. Restart-safe and idempotent. */
    @Synchronized fun importNew(
        context: Context,
        runId: String,
        cancelled: () -> Boolean = { false },
        progress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Imported {
        val state = read(context, runId)
        val tree = GamePhotoImport.chosen(context) ?: error("Quyền đọc Pictures đã hết hạn. Hãy chọn lại thư mục.")
        require(tree.toString() == state.optString("tree")) { "Thư mục Pictures đã thay đổi. Chọn lại đúng thư mục trước khi tiếp tục." }
        val baseline = state.getJSONArray("baseline")
        val old = (0 until baseline.length()).map { baseline.getString(it) }.toHashSet()
        val all = GamePhotoImport.scan(context, tree) { !cancelled() }
        if (cancelled()) return Imported(state.optInt("before"), all.size, 0, 0, 0, 0, 0, true)
        // Pin successful source/event pairs. A late-indexed image must not reassign an earlier
        // imported picture to another hero or category on the next scan.
        val assignments = state.optJSONObject("assignments") ?: JSONObject()
        val targetClaims = state.optJSONObject("targetClaims") ?: JSONObject()
        val recorded = marks(state)
        val pinned = assignments.keys().asSequence().mapNotNull { id ->
            val a = assignments.optJSONObject(id) ?: return@mapNotNull null
            Pick(id, a.optString("group"), a.optString("item"), 0L)
        }.toList()
        val usedTimes = assignments.keys().asSequence().map { assignments.getJSONObject(it).optLong("time") }.toSet()
        val freshDiff = diff(
            old + assignments.keys().asSequence().toSet(),
            all.map { Scanned(it.id, it.size, it.modified) },
            recorded.filter { it.time !in usedTimes }
        )
        val diff = Diff(
            old.size,
            all.size,
            all.count { it.id !in old },
            pinned + freshDiff.matching,
            freshDiff.unrelated
        )
        val matches = all.associateBy { it.id }
        val runLocal = state.optInt("storageVersion", 1) >= 2
        val run = File(context.filesDir, "captures/$runId")
        val destRoot = if (runLocal) run else File(context.filesDir, "captures/" + GamePhotoImport.ROOT)
        destRoot.mkdirs()
        val meta = File(destRoot, "run.json")
        if (!meta.exists()) {
            meta.writeText(
                JSONObject().put("collectionMode", if (runLocal) "chess" else "game-photos")
                    .put("label", if (runLocal) "Cờ Liên Quân · Bách khoa" else "Ảnh gốc Cờ Liên Quân · Pictures")
                    .put("createdAt", System.currentTimeMillis()).toString(2)
            )
        }
        val ledgerFile = File(if (runLocal) run else context.filesDir, LEDGER)
        val ledger = try {
            JSONObject(ledgerFile.readText())
        } catch (_: Exception) {
            JSONObject()
        }
        val integrity = state.optJSONObject("originalIntegrity") ?: JSONObject()

        fun remember(pick: Pick, identity: String, target: File, verified: OriginalPhotoCopy.Integrity) {
            val path = target.relativeTo(destRoot).invariantSeparatorsPath
            integrity.put(identity, JSONObject().put("path", path).put("bytes", verified.bytes).put("sha256", verified.sha256))
            ledger.put(identity, path)
            val ledgerPart = File(ledgerFile.parentFile, ledgerFile.name + ".part")
            ledgerPart.writeText(ledger.toString(2))
            require(ledgerPart.renameTo(ledgerFile)) { "Không lưu được sổ sao chép ảnh" }
            pin(assignments, pick, recorded)
            assignments.optJSONObject(pick.id)?.put("sourceIdentity", identity)
            state.put("assignments", assignments).put("originalIntegrity", integrity)
            store(context, runId, state)
            if (runLocal) RunImageManifest.record(run, target, pick.group, pick.item, identity, verified)
            recordImportedBlessing(context, state, pick, target)
        }

        fun sourceHash(image: GamePhotoImport.Photo) = context.contentResolver.openInputStream(image.uri)?.use {
            OriginalPhotoCopy.digest(it) { !cancelled() }
        } ?: error("Không mở được ảnh gốc")
        var copied = 0
        var already = 0
        var errors = 0
        for ((index, pick) in diff.matching.withIndex()) {
            if (cancelled()) break
            progress(index, diff.matching.size, pick.group + "/" + pick.item)
            val image = matches[pick.id]
            if (image == null) {
                // The user may have cleaned Pictures after a verified copy. That does not undo
                // a completed save or require another in-game export while the local copy is intact.
                val savedId = assignments.optJSONObject(pick.id)?.optString("sourceIdentity").orEmpty()
                val saved = integrity.optJSONObject(savedId)
                if (runLocal && saved != null) {
                    try {
                        val local = File(run, saved.getString("path"))
                        val expected = OriginalPhotoCopy.Integrity(saved.getLong("bytes"), saved.getString("sha256"))
                        if (local.canonicalPath.startsWith(run.canonicalPath + File.separator) &&
                            OriginalPhotoCopy.matches(local, expected)
                        ) {
                            RunImageManifest.record(run, local, pick.group, pick.item, savedId, expected)
                            already++
                        }
                    } catch (_: Exception) {
                        errors++
                    }
                }
                continue
            }
            val receipt = assignments.optJSONObject(pick.id)
            val sourceKey = if (receipt?.has("receiptSha256") == true) {
                "receipt|" + image.id + "|" + receipt.getString("receiptSha256")
            } else {
                image.key
            }
            val identity = fingerprint(tree.toString() + "|" + sourceKey)
            var partial: File? = null
            var target: File? = null
            var history: Long? = null
            var committed = false
            try {
                val receiptExpected = if (receipt?.has("receiptSha256") == true) {
                    OriginalPhotoCopy.Integrity(receipt.getLong("receiptBytes"), receipt.getString("receiptSha256"))
                } else {
                    null
                }
                require(receiptExpected == null || sourceHash(image) == receiptExpected) {
                    "Ảnh gốc không còn khớp biên nhận đã kiểm tra"
                }
                val oldKey = if (ChessCaptureFiles.nativeHeadBase(pick.group, pick.item) != null) identity else identity.take(14)
                val oldPath = ledger.optString(identity).ifBlank { ledger.optString(oldKey) }
                val known = oldPath.takeIf { it.isNotBlank() }?.let { File(destRoot, it) }?.takeIf {
                    it.canonicalPath.startsWith(destRoot.canonicalPath + File.separator)
                }
                val prior = integrity.optJSONObject(identity)
                if (known != null && known.isFile) {
                    val expected = if (prior != null && prior.optString("path") == oldPath) {
                        OriginalPhotoCopy.Integrity(prior.getLong("bytes"), prior.getString("sha256"))
                    } else {
                        sourceHash(image)
                    }
                    require(receiptExpected == null || expected == receiptExpected) { "Bản sao không khớp biên nhận ảnh gốc" }
                    if ((image.size <= 0 || expected.bytes == image.size) && OriginalPhotoCopy.matches(known, expected)) {
                        remember(pick, identity, known, expected)
                        already++
                        continue
                    }
                }
                // Read the original document header, never DocumentsContract's thumbnail API.
                val header =
                    context.contentResolver.openInputStream(image.uri)?.use { input ->
                        val bytes = ByteArray(16)
                        var count = 0
                        while (count <
                            bytes.size
                        ) {
                            val n = input.read(bytes, count, bytes.size - count)
                            if (n <= 0) break
                            count += n
                        }
                        bytes.copyOf(count)
                    }
                        ?: error("Không mở được ảnh gốc")
                val ext = OriginalPhotoCopy.extension(header, image.mime, image.name)
                val directoryPath = if (runLocal) {
                    "anh-goc/" + GamePhotoImport.groupPath(pick.group)
                } else {
                    GamePhotoImport.groupPath(pick.group) + "/" + safe(runId)
                }
                val file = File(destRoot, directoryPath).apply { mkdirs() }
                val directory = file.relativeTo(destRoot).invariantSeparatorsPath + "/"
                val claims = targetClaims.keys().asSequence().filter { it.startsWith(directory) }
                    .associate { it.removePrefix(directory) to targetClaims.getString(it) }
                val base = ChessCaptureFiles.nativeBase(pick.group, pick.item, nativeOrdinal(recorded, pick.group, pick.item))
                target = known ?: File(
                    file,
                    ChessCaptureFiles.nativeTarget(
                        base,
                        ext,
                        identity,
                        file.listFiles().orEmpty().map { it.name }.toSet(),
                        claims
                    )
                )
                val output = target
                output.parentFile?.mkdirs()
                val path = output.relativeTo(destRoot).invariantSeparatorsPath
                val owned = known == output || targetClaims.optString(path) == identity
                if (output.isFile && output.length() > 0 && owned) {
                    val expected = sourceHash(image)
                    require(receiptExpected == null || expected == receiptExpected) { "Ảnh gốc đã thay đổi sau đối chiếu" }
                    if ((image.size <= 0 || expected.bytes == image.size) && OriginalPhotoCopy.matches(output, expected)) {
                        remember(pick, identity, output, expected)
                        already++
                        continue
                    }
                }
                require(!output.exists() || owned) { "Tên ảnh đã thuộc về một ảnh gốc khác" }
                targetClaims.put(path, identity)
                pin(assignments, pick, recorded)
                state.put("targetClaims", targetClaims).put("assignments", assignments)
                store(context, runId, state)
                history = try {
                    CaptureHistory.begin(context, runId, output)
                } catch (_: Exception) {
                    null
                }
                require(image.size in 0..MAX_IMAGE) { "Ảnh vượt giới hạn 32 MB" }
                require(context.filesDir.usableSpace > image.size + 30L * 1024 * 1024) { "Bộ nhớ trống quá ít" }
                partial = File(output.parentFile, output.name + ".part")
                val verified = context.contentResolver.openInputStream(image.uri)?.use { input ->
                    OriginalPhotoCopy.copy(input, partial, image.size) {
                        if (cancelled()) throw ZipFiles.Cancelled()
                        require(context.filesDir.usableSpace > 30L * 1024 * 1024) { "Thiếu dung lượng khi sao chép ảnh" }
                        true
                    }
                } ?: error("Không mở được ảnh mới")
                require(receiptExpected == null || verified == receiptExpected) { "Ảnh gốc đã thay đổi trong khi sao chép" }
                require(partial.renameTo(output)) { "Không chuyển được ảnh đã kiểm tra" }
                committed = true
                history?.let {
                    try {
                        CaptureHistory.finish(context, it, true)
                    } catch (_: Exception) {}
                }
                remember(pick, identity, output, verified)
                copied++
            } catch (e: ZipFiles.Cancelled) {
                partial?.delete()
                history?.let {
                    try {
                        CaptureHistory.finish(context, it, false)
                    } catch (_: Exception) {}
                }
                break
            } catch (e: Exception) {
                partial?.delete()
                history?.let {
                    try {
                        CaptureHistory.finish(context, it, committed && target?.isFile == true)
                    } catch (_: Exception) {}
                }
                if (cancelled()) break
                errors++
            }
        }
        val totalMarks = state.getJSONArray("saves").length()
        val resolved = copied + already
        val newCheckpoint = checkpoint(state.optInt("syncedMarks", 0), totalMarks, resolved, errors, cancelled())
        val pending = (totalMarks - resolved).coerceAtLeast(0)
        meta.writeText(JSONObject(meta.readText()).put("updatedAt", System.currentTimeMillis()).toString(2))
        state.put("assignments", assignments).put("after", all.size).put("newFiles", diff.new)
            .put("unrelated", diff.unrelated).put("imported", resolved)
            .put("syncedMarks", newCheckpoint)
            .put("lastAttemptedMarks", totalMarks)
            .put("lastSyncAt", System.currentTimeMillis())
            .put("pending", pending)
            .put(
                "status",
                when {
                    cancelled() -> "paused"
                    errors > 0 || pending > 0 -> "waiting-for-pictures"
                    else -> "imported"
                }
            )
        store(context, runId, state)
        progress(diff.matching.size, diff.matching.size, "Đã lưu $copied ảnh mới")
        return Imported(diff.before, diff.after, diff.new, copied, already, diff.unrelated, errors, cancelled(), pending)
    }

    fun nativeOrdinal(marks: List<Mark>, group: String, item: String): Int {
        val hero = if (group == "chieu-quan-co") item.substringBefore("-skill-", "") else ""
        val family = marks.filter {
            it.group == group &&
                (hero.isBlank() || it.item.substringBefore("-skill-", "") == hero)
        }
        return family.indexOfFirst { it.item == item }.takeIf { it >= 0 }?.plus(1) ?: 1
    }

    private fun pin(assignments: JSONObject, pick: Pick, marks: List<Mark>) {
        if (assignments.has(pick.id)) return
        val used = assignments.keys().asSequence().map { assignments.getJSONObject(it).optLong("time") }.toSet()
        val mark = marks.firstOrNull { it.time !in used && it.group == pick.group && it.item == pick.item } ?: return
        assignments.put(pick.id, JSONObject().put("group", pick.group).put("item", pick.item).put("time", mark.time))
    }

    fun hasPending(context: Context): Boolean {
        val source = GamePhotoImport.chosen(context)?.toString() ?: return false
        return sessions(context).any { (_, s) ->
            s.optString("tree") == source &&
                (s.optJSONArray("saves")?.length() ?: 0) > s.optInt("syncedMarks", 0)
        }
    }

    private fun sessions(context: Context): List<Pair<File, JSONObject>> = File(context.filesDir, "captures").listFiles().orEmpty()
        .filter { it.isDirectory && File(it, FILE).exists() }
        .mapNotNull { dir ->
            try {
                dir to JSONObject(File(dir, FILE).readText())
            } catch (_: Exception) {
                null
            }
        }

    /** A re-sync of an older run must not make it look like a new capture run. */
    fun lastRun(context: Context): String? = sessions(context)
        .maxByOrNull { (_, state) -> state.optLong("startedAt", 0) }?.first?.name

    /** Auto-sync recent pending batches; a missing image can be indexed by Android a little later. */
    fun pendingRun(context: Context, now: Long = System.currentTimeMillis()): String? {
        val source = GamePhotoImport.chosen(context)?.toString() ?: return null
        return sessions(context).filter { (_, state) ->
            state.optString("tree") == source &&
                needsRetry(
                    state.optJSONArray("saves")?.length() ?: 0,
                    state.optInt("syncedMarks", 0),
                    state.optInt("lastAttemptedMarks", 0),
                    state.optLong("lastSyncAt", 0),
                    now
                )
        }.maxByOrNull { (_, state) -> state.optLong("startedAt", 0) }?.first?.name
    }

    fun state(context: Context, runId: String): JSONObject? = try {
        read(context, runId)
    } catch (_: Exception) {
        null
    }
}
