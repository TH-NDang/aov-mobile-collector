package vn.ndang.aovcollector

import kotlin.math.abs

/**
 * OCR-driven scene classification for Liên Quân's anniversary / Thập Kỷ Chung Sức.
 * No generic X, yellow button, login field, or reward is accepted without a known scene.
 * The outer event sidebar can change position when a new item is inserted.
 */
object EventInviteScreens {
    enum class Kind {
        LOBBY,
        EVENT_LIST,
        ANNIVERSARY,
        INVITE,
        INTRO,
        EVENT_GIFT,
        EVENT_GIFT_DONE,
        REWARD,
        RETURN_EFFECT,
        UNKNOWN
    }

    data class Scene(
        val kind: Kind,
        val words: List<GameScreens.Word>,
        val used: Int? = null,
        val dailyLimit: Int? = null,
        val tickets: Int? = null
    ) {
        fun find(vararg queries: String): GameScreens.Word? = words.firstOrNull { word -> queries.any { it in word.plain } }
    }

    private fun includes(words: List<GameScreens.Word>, word: String): Boolean = words.any { it.plain.contains(word) }

    private fun match(
        words: List<GameScreens.Word>,
        needle: String,
        left: Double = 0.0,
        right: Double = 1.0,
        top: Double = 0.0,
        bottom: Double = 1.0
    ): GameScreens.Word? = words.firstOrNull {
        needle in it.plain && it.x in left..right && it.y in top..bottom
    }

    private fun used(words: List<GameScreens.Word>): Pair<Int, Int>? {
        // GameScreens.plain strips the slash, so inspect the original OCR line FIRST.
        // A daily-limit badge is not a reward count, and the player's bottom UID is ignored.
        val original = words.filter { it.y in .46..0.76 }.joinToString(" ") { it.text }
        val clean = words.filter { it.y in .46..0.76 }.joinToString("") { it.plain }
        val raw = Regex("(\\d+)\\s*/\\s*(\\d+)").find(original)
        val fallback = Regex("danhap([01])([1-9])(?:ma|$)").find(clean)
        val first = (raw ?: fallback)?.groupValues?.get(1)?.toIntOrNull()
        val second = (raw ?: fallback)?.groupValues?.get(2)?.toIntOrNull()
        return if (first != null &&
            second != null &&
            second in 1..10 &&
            first in 0..second &&
            (clean.contains("danhap") || clean.contains("mamoi"))
        ) {
            first to second
        } else {
            null
        }
    }

    /** Only numbers in the central reward card can count as tickets. Never read the bottom UID. */
    private fun tickets(words: List<GameScreens.Word>): Int? = words
        .filter { it.x in .37..0.63 && it.y in .30..0.59 }
        .mapNotNull { Regex("^(\\d{1,4})$").matchEntire(it.plain)?.groupValues?.get(1)?.toIntOrNull() }
        .filter { it in 1..9999 }
        .firstOrNull()

    fun classify(words: List<GameScreens.Word>, isLobby: Boolean = false): Scene {
        val day = used(words)

        fun scene(kind: Kind, value: Int? = null) = Scene(kind, words, day?.first, day?.second, value)
        val reward = includes(words, "chucmung") &&
            (includes(words, "nhanduoc") || includes(words, "bandanhanduoc"))
        val rewardTicket = includes(words, "venhay")
        if (reward && (includes(words, "chamdetieptuc") || includes(words, "nhandetieptuc"))) {
            return scene(Kind.REWARD, if (rewardTicket) tickets(words) else null)
        }
        if (includes(words, "kichhoathieuungtrolai")) return scene(Kind.RETURN_EFFECT)
        if (includes(words, "hotrobanbe") &&
            (includes(words, "mamoi") || includes(words, "nhapde") || includes(words, "xacnhan"))
        ) {
            return scene(Kind.INVITE)
        }
        if (includes(words, "huongdan") &&
            includes(words, "quytac") &&
            !includes(words, "hotrobanbe")
        ) {
            return scene(Kind.INTRO)
        }
        if (includes(words, "tonghopsukien") && includes(words, "hoathoa10nam")) {
            if (includes(words, "danhan")) return scene(Kind.EVENT_GIFT_DONE)
            if (match(words, "nhan", .34, .67, .60, .83) != null) return scene(Kind.EVENT_GIFT)
        }
        val anniversary = includes(words, "thapkychungsuc") &&
            (includes(words, "nhapmamoi") || includes(words, "nhaynao") || includes(words, "venhay"))
        if (anniversary) return scene(Kind.ANNIVERSARY)
        if (includes(words, "tonghopsukien") ||
            includes(words, "khobauthoigian") ||
            includes(words, "thapkychungsuc")
        ) {
            return scene(Kind.EVENT_LIST)
        }
        if (isLobby) return scene(Kind.LOBBY)
        return scene(Kind.UNKNOWN)
    }

    /** Dynamic location of the anniversary entry in the sidebar (never the X or a matchmaking button). */
    fun anniversaryItem(scene: Scene): GameScreens.Word? = match(scene.words, "thapkychungsuc", .075, .265, .11, .94)

    fun eventIndex(scene: Scene): GameScreens.Word? = match(scene.words, "tonghopsukien", .075, .27, .09, .45)

    fun inviteButton(scene: Scene): GameScreens.Word? = match(scene.words, "nhapmamoi", .53, .78, .77, .96)

    fun eventButton(scene: Scene): GameScreens.Word? = scene.words.filter {
        it.plain.contains("sukien") && it.x in .69..0.96 && it.y in .10..0.48
    }.minByOrNull { abs(it.x - .835) + abs(it.y - .235) }

    fun confirmation(scene: Scene): GameScreens.Word? = match(scene.words, "xacnhan", .36, .66, .59, .82)

    /** Explicitly reject 1/1 daily limit, even when the field still contains a preset code. */
    fun dailyUsed(scene: Scene): Boolean = scene.used != null &&
        scene.dailyLimit != null &&
        scene.used >= scene.dailyLimit

    fun errorMessage(scene: Scene): Boolean = scene.words.any { w ->
        listOf("mamoi khonghople", "makhonghople", "mohomnay", "loihethong", "thulai", "danhap").any { it in w.plain }
    }
}

/** Bounded, deterministic journal metadata; never includes login credentials or the plaintext invitation code. */
object EventInviteLedger {
    const val SCHEMA = 1
    const val MAX_ACCOUNTS = 80
    val VALID_STATES = setOf("pending", "login", "navigating", "submit_pending", "done", "used", "rejected")

    fun new(accountKeys: List<String>, codeFingerprint: String): org.json.JSONObject {
        require(accountKeys.isNotEmpty() && accountKeys.size <= MAX_ACCOUNTS && accountKeys.distinct().size == accountKeys.size)
        require(accountKeys.all { it.matches(Regex("[0-9a-f]{64}")) })
        require(codeFingerprint.matches(Regex("[0-9a-f]{64}")))
        return org.json.JSONObject()
            .put("schema", SCHEMA)
            .put("accounts", org.json.JSONArray(accountKeys))
            .put("codeFingerprint", codeFingerprint)
            .put("next", 0)
            .put("phase", "pending")
            .put("results", org.json.JSONObject())
            .put("gifts", 0)
            .put("tickets", 0)
            .put("complete", false)
    }

    fun validate(journal: org.json.JSONObject, accounts: List<String>, codeFingerprint: String): Boolean {
        if (journal.optInt("schema") != SCHEMA || journal.optString("codeFingerprint") != codeFingerprint) return false
        val stored = journal.optJSONArray("accounts") ?: return false
        val keys = (0 until stored.length()).map { stored.optString(it) }
        return keys == accounts &&
            journal.optInt("next", -1) in 0..accounts.size &&
            journal.optString("phase") in VALID_STATES &&
            journal.optJSONObject("results") != null &&
            journal.optInt("gifts", -1) >= 0 &&
            journal.optInt("tickets", -1) >= 0
    }

    fun completed(journal: org.json.JSONObject, key: String, status: String, gifts: Int, tickets: Int) {
        require(status in setOf("done", "used", "rejected"))
        require(gifts in 0..10 && tickets in 0..9999)
        val results = journal.getJSONObject("results")
        require(!results.has(key)) { "Tài khoản đã ghi nhận kết quả; không cộng quà hai lần" }
        results.put(
            key,
            org.json.JSONObject().put("status", status).put("gifts", gifts).put("tickets", tickets)
                .put("time", System.currentTimeMillis())
        )
        journal.put("gifts", journal.getInt("gifts") + gifts)
        journal.put("tickets", journal.getInt("tickets") + tickets)
        journal.put("next", journal.getInt("next") + 1)
        journal.put("phase", "pending")
        journal.remove("active")
        journal.put("complete", journal.getInt("next") == journal.getJSONArray("accounts").length())
    }
}
