package vn.ndang.aovcollector

import org.json.JSONObject

/**
 * One multi-account event-invite macro step. Every branch starts from a fresh OCR screenshot.
 * Progress is committed before pressing the irreversible invitation confirmation button.
 * A process crash during submission must NEVER cause the same account/code to be submitted again.
 */
class EventInviteRunner(
    private val keys: List<String>,
    private val code: String,
    private val codeFingerprint: String,
    private val screen: Screen,
    private val finish: (String?) -> Unit
) {
    interface Look {
        val scene: EventInviteScreens.Scene
        fun done(keep: String? = null)
    }

    interface Screen {
        val alive: Boolean
        fun loadLedger(): JSONObject?
        fun saveLedger(journal: JSONObject): Boolean
        /** Must force an explicit fresh Garena login, not trust the last account already in the lobby. */
        fun login(key: String, then: (Boolean, String?) -> Unit)
        fun look(then: (Look?) -> Unit)
        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)
        fun typeInviteCode(code: String, then: (Boolean) -> Unit)
        /** Screenshot is retained only when it contains proof of a received reward or a known failure. */
        fun capture(name: String, then: (Boolean) -> Unit)
        fun later(ms: Long, then: () -> Unit)
        fun say(message: String)
    }

    private var ended = false
    private lateinit var ledger: JSONObject
    private var actions = 0
    private var unknowns = 0
    private var bonuses = 0
    private var bonusPending = false
    private var confirmations = 0
    private var backSteps = 0
    private var accountIndex = 0
    private var loginAttempts = 0
    private var navigating = false
    private var submittedThisSession = false

    private fun active() = !ended && screen.alive

    private fun stop(reason: String) {
        if (!ended) {
            ended = true
            finish(reason)
        }
    }

    private fun save(): Boolean {
        if (!active()) return false
        if (screen.saveLedger(ledger)) return true
        stop("Không lưu được tiến độ tác vụ sự kiện; đã dừng để tránh nhập mã hai lần")
        return false
    }

    private fun note(message: String) {
        if (active()) screen.say(
            "Sự kiện ${accountIndex + 1}/${keys.size} · $message · " +
                "${ledger.optInt("gifts")} quà · ${ledger.optInt("tickets")} Vé nhảy"
        )
    }

    private fun after(ms: Long, then: () -> Unit) = screen.later(ms) {
        if (active()) then()
    }

    private fun look(then: (Look) -> Unit) {
        if (!active()) return
        screen.look { shot ->
            if (!active()) {
                shot?.done()
            } else if (shot == null) {
                stop("Không đọc được màn hình sự kiện; hãy kiểm tra game rồi Tiếp tục")
            } else {
                then(shot)
            }
        }
    }

    private fun tap(x: Double, y: Double, wait: Long, caption: String, then: () -> Unit) {
        if (++actions > 65) return stop("Quá nhiều thao tác trên một tài khoản; dừng để tránh bấm nhầm")
        if (x !in 0.0..1.0 || y !in 0.0..1.0) return stop("Tọa độ nhận dạng không hợp lệ")
        note(caption)
        screen.tap(x, y) { ok ->
            if (!active()) return@tap
            if (!ok) stop("Không chạm được $caption; giữ màn hình để kiểm tra")
            else after(wait, then)
        }
    }

    fun start() {
        if (keys.isEmpty() || keys.size > EventInviteLedger.MAX_ACCOUNTS || !EventInviteAccounts.validCode(code)) {
            return stop("Danh sách tài khoản hoặc mã mời không hợp lệ")
        }
        val saved = screen.loadLedger()
        ledger = if (saved == null) {
            EventInviteLedger.new(keys, codeFingerprint).also {
                if (!screen.saveLedger(it)) return stop("Không tạo được tiến độ sự kiện")
            }
        } else {
            if (!EventInviteLedger.validate(saved, keys, codeFingerprint)) {
                return stop("Tiến độ cũ không khớp danh sách tài khoản/mã mời. Tạo lượt mới thay vì ghi đè")
            }
            saved
        }
        accountIndex = ledger.optInt("next", 0)
        if (ledger.optBoolean("complete")) {
            success()
            return
        }
        if (ledger.optString("phase") == "submit_pending") {
            // The button may already have succeeded while the app was killed. Do NOT log out,
            // log in again or press Xác nhận until its result is reconciled on this very screen.
            submittedThisSession = true
            note("Đang kiểm tra lượt nhập mã gián đoạn, không nhập lại")
            inspectSubmission(0)
        } else {
            startAccount()
        }
    }

    private fun summary(): String =
        "Hoàn thành ${keys.size} tài khoản · ${ledger.optInt("gifts")} quà · ${ledger.optInt("tickets")} Vé nhảy"

    private fun success() {
        if (ended) return
        screen.say(summary())
        ended = true
        finish(null)
    }

    private fun startAccount() {
        if (!active()) return
        accountIndex = ledger.optInt("next", 0)
        if (accountIndex >= keys.size) {
            success()
            return
        }
        actions = 0
        unknowns = 0
        confirmations = 0
        bonuses = 0
        backSteps = 0
        loginAttempts = 0
        bonusPending = false
        navigating = false
        submittedThisSession = false
        ledger.put("active", keys[accountIndex]).put("phase", "login")
        if (!save()) return
        note("Đang đăng xuất tài khoản cũ và đăng nhập Garena")
        screen.login(keys[accountIndex]) { ok, reason ->
            if (!active()) return@login
            if (!ok) {
                stop(reason ?: "Không đăng nhập được tài khoản sự kiện; chưa nhập mã")
            } else {
                ledger.put("phase", "navigating")
                if (!save()) return@login
                note("Đã đăng nhập, tìm Thập kỷ chung sức")
                after(900) { inspectEvent() }
            }
        }
    }

    private fun inspectEvent() {
        if (!active()) return
        if (++unknowns > 7) return stop("Không xác minh được màn hình Sự kiện sau nhiều lần đọc; không chạm mù")
        look { image ->
            val scene = image.scene
            image.done()
            when (scene.kind) {
                EventInviteScreens.Kind.LOBBY -> {
                    val button = EventInviteScreens.eventButton(scene)
                    val x = button?.x ?: .817
                    val y = button?.y ?: .227
                    unknowns = 0
                    tap(x, y, 1500, "Mở Sự kiện") { inspectEvent() }
                }
                EventInviteScreens.Kind.EVENT_LIST -> {
                    val item = EventInviteScreens.anniversaryItem(scene)
                    val index = EventInviteScreens.eventIndex(scene)
                    when {
                        item != null -> {
                            unknowns = 0
                            tap(item.x, item.y, 1200, "Mở Thập kỷ chung sức") { inspectEvent() }
                        }
                        index != null -> {
                            unknowns = 0
                            tap(index.x, index.y, 1100, "Mở Tổng hợp sự kiện") { inspectEvent() }
                        }
                        else -> after(800) { inspectEvent() }
                    }
                }
                EventInviteScreens.Kind.ANNIVERSARY -> {
                    val button = EventInviteScreens.inviteButton(scene)
                    unknowns = 0
                    tap(button?.x ?: .679, button?.y ?: .885, 900, "Mở Nhập mã mời") { inspectEvent() }
                }
                EventInviteScreens.Kind.INVITE -> {
                    unknowns = 0
                    invite(scene)
                }
                EventInviteScreens.Kind.INTRO -> {
                    unknowns = 0
                    tap(.722, .185, 700, "Đóng hướng dẫn lần đầu") { inspectEvent() }
                }
                EventInviteScreens.Kind.EVENT_GIFT_DONE -> {
                    unknowns = 0
                    tap(.796, .227, 720, "Đóng thông báo quà đã nhận") { inspectEvent() }
                }
                EventInviteScreens.Kind.EVENT_GIFT -> {
                    unknowns = 0
                    val claim = scene.find("nhan")
                    // This is the "Hoạt họa 10 năm" welcome gift, not the code reward.
                    bonusPending = true
                    tap(
                        claim?.takeIf { it.x in .37..0.65 && it.y in .59..0.85 }?.x ?: .500,
                        claim?.takeIf { it.x in .37..0.65 && it.y in .59..0.85 }?.y ?: .730,
                        1050, "Nhận quà giới thiệu sự kiện"
                    ) { inspectEvent() }
                }
                EventInviteScreens.Kind.REWARD -> {
                    unknowns = 0
                    if (bonusPending) {
                        bonusPending = false
                        val name = "event-%02d-welcome".format(accountIndex + 1)
                        screen.capture(name) { saved ->
                            if (!active()) return@capture
                            if (!saved) return@capture stop("Không lưu được bằng chứng quà sự kiện đầu tiên")
                            bonuses++
                            tap(.51, .852, 700, "Tiếp tục sau quà giới thiệu") { inspectEvent() }
                        }
                    } else {
                        // Other login/event rewards are not invite-code receipts.
                        tap(.51, .852, 700, "Đóng phần quà khác, không tính vào mã mời") { inspectEvent() }
                    }
                }
                EventInviteScreens.Kind.RETURN_EFFECT -> {
                    unknowns = 0
                    after(1100) { inspectEvent() }
                }
                EventInviteScreens.Kind.UNKNOWN -> after(1100) { inspectEvent() }
            }
        }
    }

    private fun invite(scene: EventInviteScreens.Scene) {
        if (EventInviteScreens.dailyUsed(scene)) {
            note("Tài khoản đã dùng hết lượt nhập mã hôm nay")
            complete("used", bonuses, 0)
            return
        }
        if (scene.used == null || scene.dailyLimit == null) {
            if (++confirmations >= 3) return stop("Không đọc được 0/1 hoặc 1/1 mã mời; chưa dám nhập")
            return after(900) { inspectEvent() }
        }
        if (++confirmations > 3) return stop("Màn nhập mã chưa ổn định; dừng an toàn")
        note("Chọn tất cả, xóa mã có sẵn và nhập mã chương trình")
        // host must verify a real edit field, select all + clear before typing, AND read it back.
        screen.typeInviteCode(code) { typed ->
            if (!active()) return@typeInviteCode
            if (!typed) return@typeInviteCode stop("Không thay được toàn bộ mã mặc định hoặc chưa xác minh ô nhập")
            after(500) { confirmInvite(0) }
        }
    }

    private fun confirmInvite(recheck: Int) {
        if (recheck >= 4) return stop("Không thấy nút Xác nhận trong popup nhập mã")
        look { image ->
            val scene = image.scene
            image.done()
            if (scene.kind != EventInviteScreens.Kind.INVITE) {
                return@look after(700) { confirmInvite(recheck + 1) }
            }
            if (EventInviteScreens.dailyUsed(scene)) {
                complete("used", bonuses, 0)
                return@look
            }
            val confirm = EventInviteScreens.confirmation(scene)
            if (confirm == null) return@look after(700) { confirmInvite(recheck + 1) }
            // Commit BEFORE the irreversible confirmation. If process dies, inspect; never repeat.
            ledger.put("phase", "submit_pending")
            if (!save()) return@look
            submittedThisSession = true
            tap(confirm.x, confirm.y, 1600, "Xác nhận mã mời (chỉ một lần)") { inspectSubmission(0) }
        }
    }

    private fun inspectSubmission(rechecks: Int) {
        if (rechecks >= 7) {
            return stop("Không xác minh được quà sau khi xác nhận. Đã lưu trạng thái submit_pending; không nhập mã lần hai")
        }
        look { image ->
            val scene = image.scene
            when (scene.kind) {
                EventInviteScreens.Kind.REWARD -> {
                    val tickets = scene.tickets ?: 0
                    val name = "event-%02d-invite-reward".format(accountIndex + 1)
                    // The reward screenshot must be saved successfully before incrementing the gift count.
                    image.done()
                    screen.capture(name) { kept ->
                        if (!active()) return@capture
                        if (!kept) return@capture stop("Chưa lưu được bằng chứng quà, không cộng số quà")
                        complete("done", bonuses + 1, tickets)
                    }
                }
                EventInviteScreens.Kind.INVITE -> {
                    image.done()
                    if (EventInviteScreens.dailyUsed(scene) && rechecks >= 3) {
                        // Limit changed 0/1 -> 1/1, but reward popup was not observed.
                        // Count NO gift without evidence. Do not submit again.
                        complete("used", bonuses, 0)
                    } else {
                        after(900) { inspectSubmission(rechecks + 1) }
                    }
                }
                EventInviteScreens.Kind.RETURN_EFFECT -> {
                    image.done()
                    after(950) { inspectSubmission(rechecks + 1) }
                }
                else -> {
                    image.done()
                    after(1000) { inspectSubmission(rechecks + 1) }
                }
            }
        }
    }

    private fun complete(result: String, gifts: Int, tickets: Int) {
        if (!active()) return
        try {
            EventInviteLedger.completed(ledger, keys[accountIndex], result, gifts, tickets)
        } catch (e: Exception) {
            return stop("Tiến độ quà bị trùng hoặc không hợp lệ: ${e.message}")
        }
        if (!save()) return
        note("Đã ghi nhận tài khoản; quà thực nhận $gifts, Vé nhảy $tickets")
        if (ledger.optBoolean("complete")) {
            success()
        } else {
            backSteps = 0
            after(700) { returnLobby() }
        }
    }

    /** Close known event popups, then back gradually to lobby before switching accounts. */
    private fun returnLobby() {
        if (!active()) return
        if (++backSteps > 10) return stop("Không về được sảnh để đăng xuất; đã giữ kết quả tài khoản trước")
        look { image ->
            val scene = image.scene
            image.done()
            when (scene.kind) {
                EventInviteScreens.Kind.LOBBY -> startAccount()
                EventInviteScreens.Kind.REWARD -> {
                    tap(.50, .85, 650, "Đóng bảng quà sau khi đã ghi nhận") { returnLobby() }
                }
                EventInviteScreens.Kind.INVITE, EventInviteScreens.Kind.EVENT_GIFT,
                EventInviteScreens.Kind.EVENT_GIFT_DONE -> {
                    tap(.796, .198, 620, "Đóng popup sự kiện") { returnLobby() }
                }
                EventInviteScreens.Kind.INTRO -> {
                    tap(.722, .185, 650, "Đóng hướng dẫn") { returnLobby() }
                }
                EventInviteScreens.Kind.RETURN_EFFECT -> after(1300) { returnLobby() }
                EventInviteScreens.Kind.ANNIVERSARY, EventInviteScreens.Kind.EVENT_LIST -> {
                    tap(.083, .057, 800, "Quay lại sảnh từng bước") { returnLobby() }
                }
                EventInviteScreens.Kind.UNKNOWN -> {
                    after(1000) { returnLobby() }
                }
            }
        }
    }
}
