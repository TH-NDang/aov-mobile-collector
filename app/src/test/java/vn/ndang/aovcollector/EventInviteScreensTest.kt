package vn.ndang.aovcollector

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventInviteScreensTest {
    private fun word(text: String, x: Double = .5, y: Double = .5): GameScreens.Word =
        GameScreens.Word(text, x - .06, y - .014, x + .06, y + .014)

    private val modal = listOf(
        word("Hỗ trợ bạn bè", .5, .25),
        word("Nhập mã mời nhận 6-38 Vé nhảy", .5, .37),
        word("Nhấn để nhập mã mời", .51, .46),
        word("Hôm nay đã nhập 0/1 mã", .52, .56),
        word("Xác nhận", .5, .68)
    )

    @Test fun classifyInviteAndItsQuotaWithoutStrippingSlash() {
        val open = EventInviteScreens.classify(modal)
        assertEquals(EventInviteScreens.Kind.INVITE, open.kind)
        assertEquals(0, open.used)
        assertEquals(1, open.dailyLimit)
        assertFalse(EventInviteScreens.dailyUsed(open))
        assertNotNull(EventInviteScreens.confirmation(open))

        val used = EventInviteScreens.classify(
            modal.map {
                if (it.text.startsWith("Hôm nay")) word("Hôm nay đã nhập 1/1 mã", .52, .56) else it
            }
        )
        assertEquals(1, used.used)
        assertTrue(EventInviteScreens.dailyUsed(used))
    }

    @Test fun realPopupQuotaSurvivesSplitAndConfusableOcr() {
        val title = listOf(
            word("Hỗ trợ bạn bè", .5, .25),
            word("Nhấn để nhập mã mời", .51, .46),
            word("Xác nhận", .50, .68)
        )

        // ML Kit often keeps the coloured 0/1 as an element separate from its caption.
        val separated = title + listOf(
            word("Hôm nay đã nhập", .47, .55),
            word("0", .52, .55),
            word("/", .54, .55),
            word("1", .56, .55),
            word("mã", .59, .55)
        )
        val split = EventInviteScreens.classify(separated)
        assertEquals(EventInviteScreens.Kind.INVITE, split.kind)
        assertEquals(0, split.used)
        assertEquals(1, split.dailyLimit)

        // Whole-screen downscaling may recognize the magenta zero as an "O"
        // and the yellow one as an uppercase I or lowercase l.
        for (raw in listOf("O/I", "0 / l", "O | 1", "0/1", "Hôm nay đã nhập 0/1 mã")) {
            val recognized = EventInviteScreens.classify(title + word(raw, .54, .545))
            assertEquals("Must read available quota: $raw", 0, recognized.used)
            assertEquals(1, recognized.dailyLimit)
        }
        val consumed = EventInviteScreens.classify(title + word("Hôm nay đã nhập 1 / 1 mã", .52, .55))
        assertEquals(1, consumed.used)
        assertTrue(EventInviteScreens.dailyUsed(consumed))
    }

    @Test fun quotaParserDoesNotGuessWhenUncertainOrOutsideTheVerifiedStrip() {
        val base = listOf(
            word("Hỗ trợ bạn bè", .5, .25),
            word("Nhấn để nhập mã mời", .51, .46),
            word("Xác nhận", .50, .68)
        )
        assertNull(EventInviteScreens.quota(listOf(word("0/1", .2, .55))))
        assertNull(EventInviteScreens.quota(listOf(word("0/1", .5, .85))))
        assertNull(EventInviteScreens.classify(base + word("Hôm nay đã nhập mã", .5, .55)).used)
        assertNull(EventInviteScreens.quota(listOf(word("0/1", .50, .55), word("1/1", .56, .55))))

        // A "01" without a slash can be decoded only with its quota sentence.
        assertNull(EventInviteScreens.quota(listOf(word("01", .53, .55))))
        assertEquals(0, EventInviteScreens.quota(listOf(word("Hôm nay đã nhập 01 mã", .52, .55)))?.first)
        assertEquals(1, EventInviteScreens.quota(listOf(word("Hôm nay đã nhập 11 mã", .52, .55)))?.first)

        val unknown = EventInviteScreens.classify(base + word("1/1", .2, .55))
        assertNull(unknown.used)
        assertFalse(EventInviteScreens.dailyUsed(unknown))
        assertNull(EventInviteScreens.classify(listOf(word("0/1", .51, .55))).used)
    }

    @Test fun classifyRewardSeparatelyFromWelcomeGift() {
        val reward = EventInviteScreens.classify(
            listOf(
                word("Chúc mừng nhận được", .50, .15),
                word("19", .51, .46),
                word("Vé nhảy", .50, .56),
                word("Nhấn để tiếp tục", .49, .90)
            )
        )
        assertEquals(EventInviteScreens.Kind.REWARD, reward.kind)
        assertEquals(19, reward.tickets)
        val welcome = EventInviteScreens.classify(
            listOf(
                word("Tổng hợp sự kiện", .49, .30),
                word("Hoạt họa 10 năm", .52, .55),
                word("Nhận", .50, .72)
            )
        )
        assertEquals(EventInviteScreens.Kind.EVENT_GIFT, welcome.kind)
        assertNull(welcome.tickets)
        val already = EventInviteScreens.classify(
            listOf(
                word("Tổng hợp sự kiện", .49, .30),
                word("Hoạt họa 10 năm", .52, .55),
                word("Đã nhận", .50, .72)
            )
        )
        assertEquals(EventInviteScreens.Kind.EVENT_GIFT_DONE, already.kind)
    }

    @Test fun eventNavigationChoosesActualAnniversarySidebar() {
        val hub = EventInviteScreens.classify(
            listOf(
                word("Tổng hợp sự kiện Sinh nhật Liên Quân 10 tuổi", .15, .16),
                word("Kho Báu Thời Gian", .16, .29),
                word("Thập kỷ chung sức", .16, .74)
            )
        )
        assertEquals(EventInviteScreens.Kind.EVENT_LIST, hub.kind)
        assertNotNull(EventInviteScreens.anniversaryItem(hub))
        assertNotNull(EventInviteScreens.eventIndex(hub))

        val anniversary = EventInviteScreens.classify(
            listOf(
                word("Thập kỷ chung sức", .83, .08),
                word("Nhập mã mời", .67, .89),
                word("Vé nhảy", .44, .89)
            )
        )
        assertEquals(EventInviteScreens.Kind.ANNIVERSARY, anniversary.kind)
        assertNotNull(EventInviteScreens.inviteButton(anniversary))
    }

    @Test fun didNotCountAnUnknownOrAlreadyUsedEventAsReward() {
        assertEquals(EventInviteScreens.Kind.UNKNOWN, EventInviteScreens.classify(listOf(word("Cửa hàng", .2, .5))).kind)
        assertEquals(EventInviteScreens.Kind.LOBBY, EventInviteScreens.classify(listOf(word("Sự kiện", .82, .23)), true).kind)
        val used = EventInviteScreens.classify(
            modal.map {
                if (it.text.startsWith("Hôm nay")) word("Hôm nay đã nhập 1/1 mã", .52, .56) else it
            }
        )
        assertTrue(EventInviteScreens.dailyUsed(used))
        assertNull(used.tickets)
    }

    @Test fun durableProgressCountsOnlyReceiptAndNeverTwice() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val code = "c".repeat(64)
        val ledger = EventInviteLedger.new(listOf(a, b), code)
        assertTrue(EventInviteLedger.validate(ledger, listOf(a, b), code))
        EventInviteLedger.completed(ledger, a, "done", 1, 19)
        assertEquals(1, ledger.getInt("gifts"))
        assertEquals(19, ledger.getInt("tickets"))
        assertEquals(1, ledger.getInt("next"))
        assertFalse(ledger.getBoolean("complete"))
        try {
            EventInviteLedger.completed(ledger, a, "done", 1, 19)
            throw AssertionError("The same account was counted twice")
        } catch (_: IllegalArgumentException) {}
        EventInviteLedger.completed(ledger, b, "used", 0, 0)
        assertEquals(1, ledger.getInt("gifts"))
        assertEquals(19, ledger.getInt("tickets"))
        assertTrue(ledger.getBoolean("complete"))
        assertFalse(EventInviteLedger.validate(ledger, listOf(b, a), code))
        assertFalse(EventInviteLedger.validate(ledger, listOf(a, b), "d".repeat(64)))
        assertTrue(JSONObject(ledger.toString()).getJSONObject("results").has(a))
    }

    @Test fun publicMacroNeverContainsInviteCodeOrPasswordOrUuid() {
        val a = "a".repeat(64)
        val fp = "f".repeat(64)
        val m = Macro(BuiltInMacros.eventInvite(listOf(a), fp))
        assertEquals("event-invite", m.steps.getJSONObject(0).getString("type"))
        assertEquals("event-invite", m.json.getString("collectionMode"))
        assertEquals("anniversary-invite", m.name)
        assertFalse(m.raw.contains("password"))
        assertFalse(m.raw.contains("uuid", ignoreCase = true))
        assertTrue(EventInviteAccounts.validCode("PnkpMF8ugv"))
        assertFalse(EventInviteAccounts.validCode("invitation code with spaces"))
    }
}
