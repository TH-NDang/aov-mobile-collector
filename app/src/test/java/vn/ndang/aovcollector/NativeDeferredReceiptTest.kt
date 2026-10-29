package vn.ndang.aovcollector

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure durable-state regressions; all candidate IDs and signatures are synthetic. */
class NativeDeferredReceiptTest {
    private val t = 1_790_000_000_321L
    private val group = "chieu-quan-co"

    private fun state() = JSONObject().put("saves", JSONArray()).put("baseline", JSONArray(listOf("old")))

    private fun arm(state: JSONObject, item: String, slot: String, at: Long = t): GamePhotoSession.Attempt {
        val attempt = NativePhotoReceiptJournal.prepare(state, group, item, emptySet(), "signature-$item", setOf("old"), at, slot)
        assertEquals(GamePhotoSession.ReceiptStatus.NONE, attempt.status)
        NativePhotoReceiptJournal.start(state, attempt.id, at)
        NativePhotoReceiptJournal.confirmIntent(state, attempt.id, at + 100)
        return attempt
    }

    private fun defer(state: JSONObject, attempt: GamePhotoSession.Attempt, at: Long = t + 200) {
        assertTrue(NativePhotoReceiptJournal.defer(state, attempt.id, "content unresolved", at))
    }

    @Test fun manyDeferredHeroesDoNotBlockNewDistinctSlotsOrInventSavedImages() {
        val state = state()
        val ids = (0 until 25).map { index ->
            val attempt = arm(state, "hero-$index-skill", "hero-$index/page-1/slot-1", t + index * 30_000)
            defer(state, attempt, t + index * 30_000 + 200)
            attempt.id
        }
        assertEquals(25, ids.distinct().size)
        assertEquals(25, NativePhotoReceiptJournal.deferredPending(state).size)
        assertEquals(0, state.getJSONArray("saves").length())
        assertEquals(0, state.optJSONObject("assignments")?.length() ?: 0)
    }

    @Test fun sameSlotSurvivesHashDriftAndProcessRestartWithoutAnotherSave() {
        val state = state()
        val original = arm(state, "lumburr-skill-old-hash", "lumburr/page-1/slot-1")
        defer(state, original)
        val restarted = JSONObject(state.toString())
        val again = NativePhotoReceiptJournal.prepare(
            restarted,
            group,
            "lumburr-skill-new-hash",
            emptySet(),
            "changed-signature",
            setOf("old", "new-file"),
            t + 90_000,
            "lumburr/page-1/slot-1"
        )
        assertEquals(original.id, again.id)
        assertEquals(GamePhotoSession.ReceiptStatus.DEFERRED, again.status)
        assertTrue(again.matchesRequested)
        assertEquals(1, NativePhotoReceiptJournal.attempts(restarted).size)
        val persisted = NativePhotoReceiptJournal.find(restarted, original.id)
        assertEquals("signature-lumburr-skill-old-hash", persisted.getString("signature"))
        assertEquals(setOf("old"), NativePhotoReceiptJournal.strings(persisted.getJSONArray("baseline")))
        assertEquals(t, persisted.getLong("startedAt"))
        assertEquals(t + 100 + NativePhotoReceipt.WINDOW_MS, persisted.getLong("endsAt"))
        assertEquals(0, restarted.getJSONArray("saves").length())
    }

    @Test fun aliasesStillFindDeferredLegacyAttemptWithoutOperationKey() {
        val state = state()
        val attempt = NativePhotoReceiptJournal.prepare(state, group, "old-hash", setOf("alias"), "sig", setOf("old"), t)
        NativePhotoReceiptJournal.start(state, attempt.id, t)
        NativePhotoReceiptJournal.confirmIntent(state, attempt.id, t + 100)
        defer(state, attempt)
        val alias = NativePhotoReceiptJournal.existing(state, group, "alias", emptySet())!!
        assertEquals(attempt.id, alias.id)
        assertEquals(GamePhotoSession.ReceiptStatus.DEFERRED, alias.status)
        assertTrue(alias.matchesRequested)
        assertNull(NativePhotoReceiptJournal.existing(state, "quan-co", "alias", emptySet()))
    }

    @Test fun legacyPendingWithHashDriftBlocksRedispatchWithoutPretendingIdentityMatches() {
        val state = state()
        val first = NativePhotoReceiptJournal.prepare(state, group, "old-hash", emptySet(), "sig", setOf("old"), t)
        NativePhotoReceiptJournal.start(state, first.id, t)
        NativePhotoReceiptJournal.confirmIntent(state, first.id, t + 100)
        val resumed = NativePhotoReceiptJournal.prepare(
            JSONObject(state.toString()),
            group,
            "new-hash",
            emptySet(),
            "new-sig",
            setOf("old", "saved"),
            t + 60_000,
            "newly-added-slot-key"
        )
        assertEquals(first.id, resumed.id)
        assertEquals(GamePhotoSession.ReceiptStatus.UNCERTAIN, resumed.status)
        assertFalse(resumed.matchesRequested)
    }

    @Test fun preparedUndispatchedAndCancelledAttemptsCannotBecomeDeferredSavedClaims() {
        val state = state()
        val attempt = NativePhotoReceiptJournal.prepare(state, group, "hero-skill", emptySet(), "sig", setOf("old"), t)
        assertFalse(NativePhotoReceiptJournal.defer(state, attempt.id, "not sent", t + 1))
        NativePhotoReceiptJournal.start(state, attempt.id, t + 2)
        assertFalse(NativePhotoReceiptJournal.defer(state, attempt.id, "no Sure", t + 3))
        assertEquals("pending", NativePhotoReceiptJournal.find(state, attempt.id).getString("status"))
        NativePhotoReceiptJournal.cancelBeforeDispatch(state, attempt.id)
        assertFalse(NativePhotoReceiptJournal.defer(state, attempt.id, "cancelled", t + 4))
        assertEquals(0, state.getJSONArray("saves").length())
    }

    @Test fun deferredRetriesAreBoundedAcrossRestartAndRepeatedDeferralDoesNotResetBudget() {
        var state = state()
        val original = arm(state, "hero-skill", "hero/page-1/slot-1")
        defer(state, original)
        var now = t + 30_000
        repeat(3) {
            val due = NativePhotoReceiptJournal.dueDeferred(state, now)
            assertEquals(listOf(original.id), due.map { it.getString("id") })
            NativePhotoReceiptJournal.noteDeferredCheck(due.single(), now)
            assertTrue(NativePhotoReceiptJournal.dueDeferred(state, now + 9_999).isEmpty())
            assertTrue(NativePhotoReceiptJournal.defer(state, original.id, "still unresolved", now + 1))
            state = JSONObject(state.toString())
            now += 10_000
        }
        assertTrue(NativePhotoReceiptJournal.dueDeferred(state, now + 86_400_000).isEmpty())
        assertEquals(1, NativePhotoReceiptJournal.deferredPending(state).size)
        assertEquals(0, state.getJSONArray("saves").length())
    }

    @Test fun oneBackgroundPassHasBoundedWorkAndLeavesTheRestDurablyQueued() {
        val state = state()
        repeat(12) { index ->
            val attempt = arm(state, "hero-$index", "slot-$index", t + index * 1000)
            defer(state, attempt, t + index * 1000 + 200)
        }
        val due = NativePhotoReceiptJournal.dueDeferred(state, t + 90_000)
        assertEquals(8, due.size)
        due.forEach { NativePhotoReceiptJournal.noteDeferredCheck(it, t + 90_000) }
        assertEquals(4, NativePhotoReceiptJournal.dueDeferred(state, t + 90_001).size)
        assertEquals(12, NativePhotoReceiptJournal.deferredPending(state).size)
    }

    @Test fun deferredWindowAndObservedIdsCannotLeakIntoLegacyTimestampMatching() {
        val state = state()
        val attempt = arm(state, "hero-skill", "hero/page-1/slot-1")
        NativePhotoReceiptJournal.observe(NativePhotoReceiptJournal.find(state, attempt.id), setOf("observed-undated"))
        defer(state, attempt)
        val photos = listOf(
            GamePhotoSession.Scanned("old", 100, t + 500),
            GamePhotoSession.Scanned("candidate", 100, t + 500),
            GamePhotoSession.Scanned("observed-undated", 100, 0),
            GamePhotoSession.Scanned("outside", 100, t + NativePhotoReceipt.WINDOW_MS + 101),
            GamePhotoSession.Scanned("before", 100, t - 1000)
        )
        val reserved = NativePhotoReceiptJournal.reservedForImport(state, photos)
        assertTrue("candidate" in reserved)
        assertTrue("observed-undated" in reserved)
        assertFalse("outside" in reserved)
        assertFalse("before" in reserved)
        assertFalse("old" in reserved)
        assertEquals(0, state.getJSONArray("saves").length())
    }

    @Test fun overlappingDeferredSignaturesRemainAmbiguityEvidenceForLaterAttempt() {
        val state = state()
        val first = arm(state, "first-skill", "first/page-1/slot-1")
        defer(state, first)
        val second = arm(state, "second-skill", "second/page-1/slot-1", t + 1000)
        assertNotEquals(first.id, second.id)
        assertEquals(
            listOf("signature-first-skill"),
            NativePhotoReceiptJournal.earlierAbsentSignatures(state, second.id, t + 1500)
        )
        assertTrue(NativePhotoReceiptJournal.earlierAbsentSignatures(state, second.id, t + 60_000).isEmpty())
    }

    @Test fun onlyVerifiedReceiptPromotionCreatesAssignmentAndSaveMark() {
        val state = state()
        val attempt = arm(state, "hero-skill", "hero/page-1/slot-1")
        defer(state, attempt)
        NativePhotoReceiptJournal.observe(NativePhotoReceiptJournal.find(state, attempt.id), setOf("candidate-a", "candidate-b"))
        assertEquals(0, state.getJSONArray("saves").length())
        assertEquals(0, state.optJSONObject("assignments")?.length() ?: 0)
        NativePhotoReceiptJournal.receipt(state, attempt.id, "candidate-a", OriginalPhotoCopy.Integrity(1200, "verified-hash"))
        assertEquals(1, state.getJSONArray("saves").length())
        val reserved = NativePhotoReceiptJournal.reservedForImport(
            state,
            listOf(
                GamePhotoSession.Scanned("late-other-candidate", 128, t + 500),
                GamePhotoSession.Scanned("later-hero", 128, t + 60_000)
            )
        )
        assertTrue("candidate-b" in reserved)
        assertTrue("late-other-candidate" in reserved)
        assertFalse("later-hero" in reserved)
        assertEquals(1, state.getJSONObject("assignments").length())
        val assignment = state.getJSONObject("assignments").getJSONObject("candidate-a")
        assertEquals("verified-hash", assignment.getString("receiptSha256"))
        assertEquals(1200L, assignment.getLong("receiptBytes"))
        assertTrue(NativePhotoReceiptJournal.deferredPending(state).isEmpty())
        val resumed = NativePhotoReceiptJournal.existing(state, group, "changed-hash", emptySet(), "hero/page-1/slot-1")!!
        assertEquals(attempt.id, resumed.id)
        assertEquals(GamePhotoSession.ReceiptStatus.RECEIPT, resumed.status)
        assertFalse(NativePhotoReceiptJournal.defer(state, attempt.id, "already verified", t + 90_000))
        NativePhotoReceiptJournal.find(state, attempt.id).put("status", "confirmed")
        assertFalse(NativePhotoReceiptJournal.defer(state, attempt.id, "already confirmed", t + 90_001))
    }
}
