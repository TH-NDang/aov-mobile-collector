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

    /**
     * Tiny 0/1 or 1/1 label of the "Hỗ trợ bạn bè" popup, centred near y=0.55.
     * The game renders "Hôm nay đã nhập 0/1 mã" in small differently-coloured
     * glyphs. Whole-screen half-resolution OCR may return:
     *   "0/1", "0 / 1", "O/I", or "Hôm nay đã nhập" + "0" + "/" + "1".
     *
     * We accept only evidence in this narrow region of a VERIFIED invite modal.
     * No other number anywhere on the event or game may control a submission.
     */
    fun isQuotaWord(word: GameScreens.Word): Boolean = word.x in 0.36..0.70 && word.y in 0.505..0.605

    fun quota(words: List<GameScreens.Word>): Pair<Int, Int>? {
        val lane = words.filter(::isQuotaWord)
        if (lane.isEmpty()) return null

        fun digit(char: Char): Int? = when (char) {
            '0', 'O', 'o' -> 0
            '1', 'I', 'i', 'l', 'L' -> 1
            else -> null
        }

        // Check each OCR line first: its layout is more reliable than the order in which
        // ML Kit and accessibility visit their text blocks.
        val ratio = Regex("""(?i)(?<!\d)([01oIlL])\s*[/／|:]\s*([1iIlL])(?=$|[^\d])""")

        fun direct(text: String): List<Pair<Int, Int>> = ratio.findAll(text).mapNotNull {
            val a = digit(it.groupValues[1][0])
            val b = digit(it.groupValues[2][0])
            if (a != null && b == 1) a to b else null
        }.toList()

        val fromLines = lane.flatMap { direct(it.text) }.distinct()
        if (fromLines.size > 1) return null
        if (fromLines.size == 1) return fromLines.single()

        // Fragments may be separate OCR words: join them only if they lie on the
        // same baseline, in physical left-to-right order.
        val rows = mutableListOf<MutableList<GameScreens.Word>>()
        for (word in lane.sortedWith(compareBy<GameScreens.Word> { it.y }.thenBy { it.x })) {
            val row = rows.firstOrNull { kotlin.math.abs(it.first().y - word.y) < .017 }
            if (row != null) row.add(word) else rows.add(mutableListOf(word))
        }
        val joined = rows.map { row -> row.sortedBy { it.x }.joinToString(" ") { it.text } }
        val splitRatio = joined.flatMap(::direct).distinct()
        if (splitRatio.size > 1) return null
        if (splitRatio.size == 1) return splitRatio.single()

        // ML Kit occasionally drops the slash. "01" alone is ambiguous, so a
        // slashless value requires the explicit "đã nhập ... mã" sentence.
        val compact = Regex("""danhap([01])1(?:ma|$)""")
        val fromSentence = (lane.map { it.text } + joined).mapNotNull { text ->
            val normalized = GameScreens.plain(text)
            compact.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.let { it to 1 }
        }.distinct()
        return fromSentence.singleOrNull()
    }

    /** Quantity must belong to the central ticket card, not a balance, quota, UID or reward range. */
    fun rewardTickets(words: List<GameScreens.Word>): Int? {
        val labels = words.filter { "venhay" in it.plain && it.x in .37..0.63 && it.y in .43..0.62 }
        if (labels.isEmpty()) return null
        val amount = Regex("""(?i)^(?:[x×]\s*)?(\d{1,4})(?:\s*vé\s+nhảy)?$""")
        val values = words.filter { word ->
            word.x in .37..0.63 &&
                word.y in .30..0.60 &&
                labels.any { abs(word.x - it.x) < .10 && word.y <= it.y + .025 && it.y - word.y < .20 }
        }.mapNotNull { word ->
            amount.matchEntire(word.text.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.filter { it in 1..9999 }.distinct()
        return values.singleOrNull()
    }

    fun classify(words: List<GameScreens.Word>, isLobby: Boolean = false): Scene {
        fun scene(kind: Kind, value: Int? = null): Scene {
            val day = if (kind == Kind.INVITE) quota(words) else null
            return Scene(kind, words, day?.first, day?.second, value)
        }
        val reward = includes(words, "chucmung") &&
            (includes(words, "nhanduoc") || includes(words, "bandanhanduoc"))
        val rewardTicket = includes(words, "venhay")
        if (reward && (includes(words, "chamdetieptuc") || includes(words, "nhandetieptuc"))) {
            return scene(Kind.REWARD, if (rewardTicket) rewardTickets(words) else null)
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
    const val SCHEMA = 2
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
            .put("unknownTickets", 0)
            .put("complete", false)
    }

    fun validate(journal: org.json.JSONObject, accounts: List<String>, codeFingerprint: String): Boolean {
        if (journal.optInt("schema") !in setOf(1, SCHEMA) || journal.optString("codeFingerprint") != codeFingerprint) return false
        val stored = journal.optJSONArray("accounts") ?: return false
        val keys = (0 until stored.length()).map { stored.optString(it) }
        val valid = keys == accounts &&
            journal.optInt("next", -1) in 0..accounts.size &&
            journal.optString("phase") in VALID_STATES &&
            journal.optJSONObject("results") != null &&
            journal.optInt("gifts", -1) >= 0 &&
            journal.optInt("tickets", -1) >= 0
        if (!valid) return false
        // Schema 1 wrote unread receipt amounts as zero. Keep completion and submit
        // checkpoints intact: recovering a count must never replay an invitation.
        val results = journal.getJSONObject("results")
        for (key in results.keys()) {
            val result = results.optJSONObject(key) ?: continue
            if (knownTickets(result, journal.optInt("schema") == 1) == null) result.put("tickets", org.json.JSONObject.NULL)
        }
        journal.put("schema", SCHEMA)
        updateTotals(journal)
        return true
    }

    private fun knownTickets(result: org.json.JSONObject, legacy: Boolean): Int? {
        val raw = result.opt("tickets") as? Number ?: return null
        val count = raw.toInt()
        if (raw.toDouble() != count.toDouble() || count !in 0..9999) return null
        // A confirmed ticket receipt has a positive amount; historical zero is unread.
        return count.takeUnless { it == 0 && (result.optString("status") == "done" || (legacy && result.optString("status") == "used")) }
    }

    private fun ticketTotals(journal: org.json.JSONObject): Pair<Int, Int> {
        val results = journal.optJSONObject("results") ?: return 0 to 1
        var known = 0
        var unknown = (journal.optInt("next", 0) - results.length()).coerceAtLeast(0)
        for (key in results.keys()) {
            val result = results.optJSONObject(key)
            val count = result?.let { knownTickets(it, journal.optInt("schema") == 1) }
            if (count == null) unknown++ else known += count
        }
        return known to unknown
    }

    private fun updateTotals(journal: org.json.JSONObject) {
        val (known, unknown) = ticketTotals(journal)
        journal.put("tickets", known)
        journal.put("unknownTickets", unknown)
    }

    fun ticketSummary(journal: org.json.JSONObject?): String {
        if (journal == null) return "Chưa có số Vé nhảy"
        val (known, unknown) = ticketTotals(journal)
        return if (unknown > 0 && known == 0) {
            "Vé nhảy: chưa rõ · $unknown kết quả chưa rõ số lượng"
        } else if (unknown > 0) {
            "Đã đọc $known Vé nhảy · $unknown kết quả chưa rõ số lượng"
        } else {
            "$known Vé nhảy"
        }
    }

    fun completed(journal: org.json.JSONObject, key: String, status: String, gifts: Int, tickets: Int?) {
        require(status in setOf("done", "used", "rejected"))
        require(gifts in 0..10 && (tickets == null || tickets in 0..9999))
        val results = journal.getJSONObject("results")
        require(!results.has(key)) { "Tài khoản đã ghi nhận kết quả; không cộng quà hai lần" }
        val known = tickets.takeUnless { status == "done" && it == 0 }
        results.put(
            key,
            org.json.JSONObject().put("status", status).put("gifts", gifts)
                .put("tickets", known ?: org.json.JSONObject.NULL)
                .put("time", System.currentTimeMillis())
        )
        journal.put("gifts", journal.getInt("gifts") + gifts)
        journal.put("next", journal.getInt("next") + 1)
        updateTotals(journal)
        journal.put("phase", "pending")
        journal.remove("imeVerifiedFor")
        journal.remove("active")
        journal.put("complete", journal.getInt("next") == journal.getJSONArray("accounts").length())
    }
}
