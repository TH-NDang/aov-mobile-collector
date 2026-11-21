package vn.ndang.aovcollector

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EventInviteLedgerTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val c = "c".repeat(64)
    private val fingerprint = "f".repeat(64)

    @Test fun unknownReceiptPersistsAsNullAndCannotBeCountedTwice() {
        val ledger = EventInviteLedger.new(listOf(a), fingerprint)
        EventInviteLedger.completed(ledger, a, "done", 1, null)
        val restored = JSONObject(ledger.toString())
        assertTrue(restored.getJSONObject("results").getJSONObject(a).has("tickets"))
        assertTrue(restored.getJSONObject("results").getJSONObject(a).isNull("tickets"))
        assertEquals(0, restored.getInt("tickets"))
        assertEquals(1, restored.getInt("unknownTickets"))
        assertTrue(restored.getBoolean("complete"))
        assertEquals("Vé nhảy: chưa rõ · 1 kết quả chưa rõ số lượng", EventInviteLedger.ticketSummary(restored))
        val before = restored.toString()
        try {
            EventInviteLedger.completed(restored, a, "done", 1, 19)
            fail("Must not replay an unread receipt")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, restored.toString())
    }

    @Test fun mixedResultsShowOnlyKnownSumAndUnknownCount() {
        val ledger = EventInviteLedger.new(listOf(a, b, c), fingerprint)
        EventInviteLedger.completed(ledger, a, "done", 1, 19)
        EventInviteLedger.completed(ledger, b, "done", 1, null)
        EventInviteLedger.completed(ledger, c, "used", 0, 0)
        assertEquals(19, ledger.getInt("tickets"))
        assertEquals(1, ledger.getInt("unknownTickets"))
        assertEquals("Đã đọc 19 Vé nhảy · 1 kết quả chưa rõ số lượng", EventInviteLedger.ticketSummary(ledger))
        assertTrue(ledger.getBoolean("complete"))
    }

    @Test fun quotaAfterSubmissionCanRemainUnknownWithoutClaimingAReceipt() {
        val ledger = EventInviteLedger.new(listOf(a), fingerprint)
        EventInviteLedger.completed(ledger, a, "used", 0, null)
        assertEquals(0, ledger.getInt("gifts"))
        assertEquals(1, ledger.getInt("unknownTickets"))
        assertTrue(ledger.getJSONObject("results").getJSONObject(a).isNull("tickets"))
    }

    @Test fun legacyZeroReceiptMigrationPreservesNoReplayCheckpoints() {
        val ledger = EventInviteLedger.new(listOf(a, b, c), fingerprint)
        EventInviteLedger.completed(ledger, a, "done", 1, 19)
        EventInviteLedger.completed(ledger, b, "done", 1, null)
        ledger.getJSONObject("results").getJSONObject(b).put("tickets", 0)
        ledger.put("schema", 1).remove("unknownTickets")
        ledger.put("phase", "submit_pending").put("active", c).put("imeVerifiedFor", c)
        assertTrue(EventInviteLedger.validate(ledger, listOf(a, b, c), fingerprint))
        assertEquals(2, ledger.getInt("schema"))
        assertEquals(2, ledger.getInt("next"))
        assertEquals("submit_pending", ledger.getString("phase"))
        assertEquals(c, ledger.getString("active"))
        assertEquals(c, ledger.getString("imeVerifiedFor"))
        assertFalse(ledger.getBoolean("complete"))
        assertTrue(ledger.getJSONObject("results").getJSONObject(b).isNull("tickets"))
        assertEquals(19, ledger.getInt("tickets"))
        assertEquals(1, ledger.getInt("unknownTickets"))
        val once = ledger.toString()
        assertTrue(EventInviteLedger.validate(ledger, listOf(a, b, c), fingerprint))
        assertEquals(once, ledger.toString())
    }

    @Test fun completedLegacyJournalStaysCompleteAndSummaryWorksBeforeMigration() {
        val ledger = EventInviteLedger.new(listOf(a), fingerprint)
        EventInviteLedger.completed(ledger, a, "done", 1, null)
        ledger.getJSONObject("results").getJSONObject(a).put("tickets", 0)
        ledger.put("schema", 1).remove("unknownTickets")
        assertTrue(EventInviteLedger.ticketSummary(ledger).contains("chưa rõ"))
        assertTrue(EventInviteLedger.validate(ledger, listOf(a), fingerprint))
        assertTrue(ledger.getBoolean("complete"))
        assertEquals(1, ledger.getInt("next"))
    }

    @Test fun mismatchedLegacyJournalIsNotMigrated() {
        val ledger = EventInviteLedger.new(listOf(a), fingerprint).put("schema", 1)
        val before = ledger.toString()
        assertFalse(EventInviteLedger.validate(ledger, listOf(b), fingerprint))
        assertEquals(before, ledger.toString())
    }

    @Test fun exactZeroIsOnlyForKnownNonReceiptAndMissingJournalIsUnknown() {
        val ledger = EventInviteLedger.new(listOf(a, b), fingerprint)
        EventInviteLedger.completed(ledger, a, "used", 0, 0)
        assertEquals("0 Vé nhảy", EventInviteLedger.ticketSummary(ledger))
        EventInviteLedger.completed(ledger, b, "done", 1, 0)
        assertTrue(ledger.getJSONObject("results").getJSONObject(b).isNull("tickets"))
        assertEquals(1, ledger.getInt("unknownTickets"))
        assertEquals("Chưa có số Vé nhảy", EventInviteLedger.ticketSummary(null))
    }

    @Test fun missingCompletedResultCannotTurnIntoExactZero() {
        val ledger = EventInviteLedger.new(listOf(a), fingerprint)
            .put("next", 1).put("complete", true).put("tickets", 19).put("schema", 1)
        assertTrue(EventInviteLedger.ticketSummary(ledger).contains("chưa rõ"))
        assertTrue(EventInviteLedger.validate(ledger, listOf(a), fingerprint))
        assertEquals(1, ledger.getInt("unknownTickets"))
        assertTrue(ledger.getBoolean("complete"))
    }

    @Test fun legacyUsedZeroCannotDistinguishSkippedAccountFromUnseenReceipt() {
        val ledger = EventInviteLedger.new(listOf(a), fingerprint)
        EventInviteLedger.completed(ledger, a, "used", 0, 0)
        ledger.put("schema", 1)
        assertTrue(EventInviteLedger.ticketSummary(ledger).contains("chưa rõ"))
        assertTrue(EventInviteLedger.validate(ledger, listOf(a), fingerprint))
        assertTrue(ledger.getJSONObject("results").getJSONObject(a).isNull("tickets"))
        assertTrue(ledger.getBoolean("complete"))
    }
}
