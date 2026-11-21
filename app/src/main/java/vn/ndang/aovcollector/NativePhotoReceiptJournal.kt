package vn.ndang.aovcollector

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Pure journal transitions, persisted together with source assignments and save marks. */
internal object NativePhotoReceiptJournal {
    fun strings(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }

    fun attempts(state: JSONObject): List<JSONObject> {
        val entries = state.optJSONArray("nativeAttempts") ?: return emptyList()
        return (0 until entries.length()).map { entries.getJSONObject(it) }
    }

    fun find(state: JSONObject, id: String): JSONObject = attempts(state).firstOrNull { it.getString("id") == id }
        ?: error("Không tìm thấy lần lưu ảnh gốc")

    private fun same(attempt: JSONObject, group: String, names: Set<String>) = attempt.getString("group") == group &&
        ChessBlessings.sameSave(
            group,
            strings(attempt.optJSONArray("aliases")) + attempt.getString("item"),
            names
        )

    fun existing(
        state: JSONObject,
        group: String,
        item: String,
        aliases: Set<String>,
        operationKey: String? = null
    ): GamePhotoSession.Attempt? {
        fun requested(attempt: JSONObject) = same(attempt, group, aliases + item) ||
            (attempt.getString("group") == group && !operationKey.isNullOrBlank() && attempt.optString("operationKey") == operationKey)
        // A pending operation blocks all later saves. Never reset its baseline on resume or hash drift.
        val pending = attempts(state).firstOrNull { it.getString("status") == "pending" }
        if (pending != null) {
            return GamePhotoSession.Attempt(
                pending.getString("id"),
                GamePhotoSession.ReceiptStatus.UNCERTAIN,
                pending.getString("item"),
                requested(pending)
            )
        }
        val deferred = attempts(state).lastOrNull { it.getString("status") == "deferred" && requested(it) }
        if (deferred != null) {
            return GamePhotoSession.Attempt(
                deferred.getString("id"),
                GamePhotoSession.ReceiptStatus.DEFERRED,
                deferred.getString("item"),
                true
            )
        }
        val unresolvedAbsent = attempts(state).lastOrNull {
            it.getString("status") == "absent" && requested(it)
        }
        if (unresolvedAbsent != null) {
            return GamePhotoSession.Attempt(
                unresolvedAbsent.getString("id"),
                GamePhotoSession.ReceiptStatus.ABSENT,
                unresolvedAbsent.getString("item"),
                requested(unresolvedAbsent)
            )
        }
        val saved = attempts(state).lastOrNull {
            it.getString("status") in setOf("receipt", "confirmed") && requested(it)
        } ?: return null
        return GamePhotoSession.Attempt(saved.getString("id"), GamePhotoSession.ReceiptStatus.RECEIPT, saved.getString("item"), true)
    }

    fun prepare(
        state: JSONObject,
        group: String,
        item: String,
        aliases: Set<String>,
        signature: String?,
        ids: Set<String>,
        now: Long,
        operationKey: String? = null
    ): GamePhotoSession.Attempt {
        existing(state, group, item, aliases, operationKey)?.let { return it }
        attempts(state).filter { it.getString("status") == "prepared" }.forEach { it.put("status", "cancelled") }
        val entries = state.optJSONArray("nativeAttempts") ?: JSONArray()
        val id = UUID.randomUUID().toString()
        entries.put(
            JSONObject().put("id", id).put("group", group).put("item", item)
                .put("aliases", JSONArray(aliases.sorted())).put("signature", signature ?: "")
                .put("startedAt", now).put("endsAt", now + NativePhotoReceipt.WINDOW_MS)
                .put("baseline", JSONArray(ids.sorted())).put("observed", JSONArray()).put("status", "prepared")
                .put("operationKey", operationKey ?: "")
        )
        state.put("nativeAttempts", entries)
        return GamePhotoSession.Attempt(id, GamePhotoSession.ReceiptStatus.NONE, item, true)
    }

    fun start(state: JSONObject, id: String, now: Long) {
        val attempt = find(state, id)
        check(attempt.getString("status") == "prepared") { "Lần lưu ảnh đã được thực hiện; không lặp lại" }
        check(attempts(state).none { it.getString("status") == "pending" }) { "Còn lần lưu ảnh chưa đối chiếu" }
        attempt.put("status", "pending").put("startedAt", now).put("endsAt", now + NativePhotoReceipt.WINDOW_MS)
    }

    /** Caller must still own the operation and know no gesture was dispatched. */
    fun cancelBeforeDispatch(state: JSONObject, id: String) {
        val attempt = find(state, id)
        check(attempt.getString("status") == "pending" && !attempt.has("sureIntentAt"))
        attempt.put("status", "cancelled")
    }

    /** Record once before Sure, not after slow OCR or during a resumed lookup. */
    fun confirmIntent(state: JSONObject, id: String, now: Long) {
        val attempt = find(state, id)
        check(attempt.getString("status") == "pending") { "Native save is not pending" }
        check(!attempt.has("sureIntentAt")) { "Native confirmation must not be dispatched twice" }
        check(now >= attempt.getLong("startedAt")) { "Native confirmation clock moved backwards" }
        attempt.put("sureIntentAt", now).put("endsAt", now + NativePhotoReceipt.WINDOW_MS)
    }

    const val DEFERRED_RETRY_MS = 10_000L
    const val DEFERRED_MAX_CHECKS = 3

    /** Defer verification, never claim a save or dispatch another Sure. */
    fun defer(state: JSONObject, id: String, reason: String, now: Long): Boolean {
        val attempt = find(state, id)
        if (attempt.getString("status") in setOf("receipt", "confirmed")) return false
        if (attempt.getString("status") !in setOf("pending", "deferred") || !attempt.has("sureIntentAt")) return false
        if (attempt.getString("status") != "deferred") {
            attempt.put("deferredAt", now).put("deferredChecks", 0).put("deferredLastCheck", 0L)
        }
        attempt.put("status", "deferred").put("reviewReason", reason.take(1200))
        return true
    }

    fun deferredPending(state: JSONObject): List<JSONObject> = attempts(state).filter { it.optString("status") == "deferred" }

    fun dueDeferred(state: JSONObject, now: Long, limit: Int = 8): List<JSONObject> = deferredPending(state).filter {
        it.optInt("deferredChecks") < DEFERRED_MAX_CHECKS &&
            now - maxOf(it.optLong("deferredLastCheck"), it.optLong("deferredAt")) >= DEFERRED_RETRY_MS
    }.take(limit.coerceIn(0, 8))

    fun noteDeferredCheck(attempt: JSONObject, now: Long) {
        check(attempt.optString("status") == "deferred")
        check(attempt.optInt("deferredChecks") < DEFERRED_MAX_CHECKS)
        attempt.put("deferredChecks", attempt.optInt("deferredChecks") + 1).put("deferredLastCheck", now)
    }

    fun autoDeferredPending(state: JSONObject): Boolean = deferredPending(state).any { it.optInt("deferredChecks") < DEFERRED_MAX_CHECKS }

    /** Unverified native exports must never fall into legacy timestamp/count-only matching. */
    fun reservedForImport(state: JSONObject, photos: List<GamePhotoSession.Scanned>): Set<String> {
        val batchBaseline = strings(state.optJSONArray("baseline"))
        // Keep all armed receipt windows quarantined after promotion too: a late second
        // candidate is not evidence for an unrelated legacy mark. Exact pins bypass this filter.
        return attempts(state).filter { it.has("sureIntentAt") }.flatMap { attempt ->
            val baseline = batchBaseline + strings(attempt.optJSONArray("baseline"))
            val start = attempt.getLong("startedAt") / 1000 * 1000
            val end = attempt.getLong("endsAt")
            strings(attempt.optJSONArray("observed")) + photos.filter {
                it.id !in baseline && (it.modified <= 0 || it.modified in start..end)
            }.map { it.id }
        }.toSet()
    }

    fun earlierAbsentSignatures(state: JSONObject, id: String, modified: Long): List<String> = attempts(state).filter {
        it.getString("id") != id &&
            it.getString("status") in setOf("absent", "deferred", "pending") &&
            modified >= it.getLong("startedAt") / 1000 * 1000 &&
            modified <= it.getLong("endsAt")
    }.map { it.optString("signature") }

    fun exclusions(state: JSONObject, id: String): Set<String> {
        val claims = state.optJSONObject("assignments")
        return (claims?.keys()?.asSequence()?.toSet() ?: emptySet()) + attempts(state).filter { it.getString("id") != id }
            .flatMap { strings(it.optJSONArray("observed")) + it.optString("documentId").takeIf(String::isNotBlank).orEmpty() }
    }

    fun observe(attempt: JSONObject, ids: Set<String>) {
        attempt.put("observed", JSONArray((strings(attempt.optJSONArray("observed")) + ids).sorted()))
    }

    private fun save(state: JSONObject, attempt: JSONObject): Long {
        val saves = state.getJSONArray("saves")
        val group = attempt.getString("group")
        val item = attempt.getString("item")
        val aliases = strings(attempt.optJSONArray("aliases"))
        val previous = (0 until saves.length()).map { saves.getJSONObject(it) }.firstOrNull {
            same(it, group, aliases + item)
        }
        if (previous != null) return previous.getLong("time")
        val used = (0 until saves.length()).map { saves.getJSONObject(it).getLong("time") }.toSet()
        var time = attempt.getLong("startedAt")
        while (time in used) time++
        saves.put(JSONObject().put("group", group).put("item", item).put("aliases", JSONArray(aliases.sorted())).put("time", time))
        return time
    }

    fun finish(state: JSONObject, id: String, confirmed: Boolean, now: Long = System.currentTimeMillis()) {
        val attempt = find(state, id)
        if (attempt.getString("status") in setOf("receipt", "confirmed")) return
        val status = attempt.getString("status")
        check(status in setOf("pending", "prepared", "absent")) { "Lần lưu ảnh đã đóng" }
        check(!confirmed || status == "pending") { "Chưa thực hiện thao tác lưu ảnh" }
        if (confirmed) save(state, attempt)
        if (!confirmed && status != "prepared") attempt.put("endsAt", minOf(attempt.getLong("endsAt"), now))
        attempt.put(
            "status",
            if (confirmed) {
                "confirmed"
            } else if (status == "prepared") {
                "cancelled"
            } else {
                "absent"
            }
        )
    }

    fun receipt(state: JSONObject, id: String, documentId: String, verified: OriginalPhotoCopy.Integrity) {
        val attempt = find(state, id)
        check(attempt.getString("status") in setOf("pending", "absent", "deferred")) { "Lần lưu ảnh đã đóng" }
        val assignments = state.optJSONObject("assignments") ?: JSONObject()
        check(!assignments.has(documentId)) { "Ảnh đã thuộc lần lưu khác" }
        val time = save(state, attempt)
        assignments.put(
            documentId,
            JSONObject().put("group", attempt.getString("group")).put("item", attempt.getString("item"))
                .put("time", time).put("receiptAttempt", id)
                .put("receiptBytes", verified.bytes).put("receiptSha256", verified.sha256)
        )
        state.put("assignments", assignments)
        attempt.put("status", "receipt").put("documentId", documentId)
    }
}
