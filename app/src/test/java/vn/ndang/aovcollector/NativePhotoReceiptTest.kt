package vn.ndang.aovcollector

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePhotoReceiptTest {
    private val t = 1_790_000_000_321L
    private val group = "chieu-quan-co"
    private val item = "lumburr-skill-icon-a"

    private fun state() = JSONObject().put("saves", JSONArray()).put("baseline", JSONArray(listOf("old")))

    private fun candidate(id: String, at: Long = t + 800, readable: Boolean = true, matches: Boolean = true) =
        NativePhotoReceipt.Candidate(id, at, readable, matches)

    private fun choose(vararg candidates: NativePhotoReceipt.Candidate) = NativePhotoReceipt.select(
        setOf("old"),
        setOf("claimed", "earlier-attempt"),
        t,
        t + NativePhotoReceipt.WINDOW_MS,
        candidates.toList()
    )

    @Test fun onlyFreshReadableContentMatchedPhotoCreatesReceipt() {
        assertEquals("lumburr", choose(candidate("lumburr")))
        assertNull(choose(candidate("old")))
        assertNull(choose(candidate("claimed")))
        assertNull(choose(candidate("earlier-attempt")))
        assertNull(choose(candidate("unrelated", matches = false)))
        assertNull(choose(candidate("partial", readable = false)))
        assertNull(choose(candidate("undated", at = 0)))
        assertNull(choose(candidate("previous", at = t - 5000)))
        assertNull(choose(candidate("late", at = t + NativePhotoReceipt.WINDOW_MS + 1)))
    }

    @Test fun concurrentPersonalPhotoCannotAuthorizeSaveAndMultipleExportsAreAmbiguous() {
        assertEquals("lumburr", choose(candidate("lumburr"), candidate("personal", matches = false)))
        assertNull(choose(candidate("lumburr"), candidate("second-export")))
        assertNull(choose(candidate("lumburr"), candidate("unfinished", readable = false)))
        assertNull(choose(candidate("lumburr"), candidate("unknown-date", at = 0)))
        assertNull(choose())
    }

    @Test fun absentReceiptRequiresCompleteUnambiguousReadAndNeverTreatsUnknownAsAbsent() {
        fun safe(vararg files: NativePhotoReceipt.Candidate, signature: Boolean = true) =
            NativePhotoReceipt.absenceSafe(t, t + 2000, files.toList(), signature)
        assertTrue(safe())
        assertTrue(safe(candidate("personal", matches = false)))
        assertFalse(safe(candidate("personal", matches = false), signature = false))
        assertFalse(safe(candidate("native")))
        assertFalse(safe(candidate("partial", readable = false, matches = false)))
        assertFalse(safe(candidate("undated", at = 0, matches = false)))
        assertFalse(safe(NativePhotoReceipt.Candidate("prior-owner", t + 100, true, false, ambiguous = true)))
        assertNull(choose(candidate("native"), NativePhotoReceipt.Candidate("prior-owner", t + 100, true, false, ambiguous = true)))
    }

    @Test fun pendingAttemptSurvivesRestartWithoutRefreshingBaselineOrWindow() {
        val original = state()
        val prepared = NativePhotoReceiptJournal.prepare(original, group, item, emptySet(), "signature", setOf("old"), t)
        assertEquals(GamePhotoSession.ReceiptStatus.NONE, prepared.status)
        NativePhotoReceiptJournal.start(original, prepared.id, t)
        val restarted = JSONObject(original.toString())
        val again = NativePhotoReceiptJournal.prepare(restarted, group, item, emptySet(), "different", setOf("old", "saved"), t + 90000)
        assertEquals(prepared.id, again.id)
        assertEquals(GamePhotoSession.ReceiptStatus.UNCERTAIN, again.status)
        val attempt = NativePhotoReceiptJournal.find(restarted, again.id)
        assertEquals(setOf("old"), NativePhotoReceiptJournal.strings(attempt.getJSONArray("baseline")))
        assertEquals("signature", attempt.getString("signature"))
        assertEquals(t + NativePhotoReceipt.WINDOW_MS, attempt.getLong("endsAt"))
        assertEquals(0, restarted.getJSONArray("saves").length())
    }

    @Test fun slowPromptUsesOneTimeSureIntentWindowAndNeverRefreshesItOnResume() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), "signature", setOf("old"), t)
        NativePhotoReceiptJournal.start(journal, first.id, t)
        val sureAt = t + 25_000
        NativePhotoReceiptJournal.confirmIntent(journal, first.id, sureAt)
        val restarted = JSONObject(journal.toString())
        val attempt = NativePhotoReceiptJournal.find(restarted, first.id)
        assertEquals(t, attempt.getLong("startedAt"))
        assertEquals(sureAt + NativePhotoReceipt.WINDOW_MS, attempt.getLong("endsAt"))
        assertEquals(
            "late-save",
            NativePhotoReceipt.select(
                setOf("old"),
                emptySet(),
                t,
                attempt.getLong("endsAt"),
                listOf(candidate("late-save", sureAt + 500))
            )
        )
        val resumed = NativePhotoReceiptJournal.prepare(restarted, group, item, emptySet(), "new", emptySet(), t + 90_000)
        assertEquals(first.id, resumed.id)
        try {
            NativePhotoReceiptJournal.confirmIntent(restarted, first.id, t + 90_000)
            throw AssertionError("Sure must not be dispatched twice")
        } catch (_: IllegalStateException) {}
        assertEquals(sureAt + NativePhotoReceipt.WINDOW_MS, attempt.getLong("endsAt"))
        assertEquals(0, restarted.getJSONArray("saves").length())
    }

    @Test fun unresolvedReceiptDiagnosticDoesNotInventPersistenceFailureOrLeakNames() {
        val reason = NativePhotoReceipt.explain(
            t,
            t + 2000,
            listOf(
                candidate("private-file-name", matches = false),
                candidate("unfinished", readable = false),
                candidate("late", t + 3000),
                candidate("undated", 0)
            ),
            false
        )
        assertTrue(reason.contains("signature=false"))
        assertTrue(reason.contains("matched=0"))
        assertTrue(reason.contains("unreadable=1"))
        assertTrue(reason.contains("outsideWindow=1"))
        assertTrue(reason.contains("undated=1"))
        assertFalse(reason.contains("private-file-name"))
        assertFalse(reason.contains("write failed"))
    }

    @Test fun hashDriftOrDifferentHeroCannotBlindlyStartAnotherSave() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), null, emptySet(), t)
        NativePhotoReceiptJournal.start(journal, first.id, t)
        listOf("lumburr-skill-icon-b", "violet-skill-icon-a").forEach { other ->
            val blocked = NativePhotoReceiptJournal.prepare(journal, group, other, emptySet(), null, setOf("new"), t + 500)
            assertEquals(first.id, blocked.id)
            assertEquals(GamePhotoSession.ReceiptStatus.UNCERTAIN, blocked.status)
            assertFalse(blocked.matchesRequested)
            assertEquals(item, blocked.item)
        }
        assertEquals(1, NativePhotoReceiptJournal.attempts(journal).size)
    }

    @Test fun receiptPinsExactSourceAndSaveTogetherUsingOriginalAttemptTime() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, setOf("alias"), null, emptySet(), t)
        NativePhotoReceiptJournal.start(journal, first.id, t)
        NativePhotoReceiptJournal.receipt(journal, first.id, "native-lumburr", OriginalPhotoCopy.Integrity(1234, "hash"))
        val restarted = JSONObject(journal.toString())
        val mark = restarted.getJSONArray("saves").getJSONObject(0)
        val pin = restarted.getJSONObject("assignments").getJSONObject("native-lumburr")
        assertEquals(t, mark.getLong("time"))
        assertEquals(t, pin.getLong("time"))
        assertEquals(item, pin.getString("item"))
        assertEquals(group, pin.getString("group"))
        assertEquals(1234L, pin.getLong("receiptBytes"))
        assertEquals("hash", pin.getString("receiptSha256"))
        assertEquals(
            GamePhotoSession.ReceiptStatus.RECEIPT,
            NativePhotoReceiptJournal.existing(restarted, group, item, emptySet())!!.status
        )
        NativePhotoReceiptJournal.finish(restarted, first.id, confirmed = true)
        NativePhotoReceiptJournal.finish(restarted, first.id, confirmed = false)
        assertEquals(1, restarted.getJSONArray("saves").length())
        assertTrue(restarted.getJSONObject("assignments").has("native-lumburr"))
        assertEquals("receipt", NativePhotoReceiptJournal.find(restarted, first.id).getString("status"))
    }

    @Test fun apparentAbsentKeepsLateReceiptWindowAndBlocksExactRepeat() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), null, emptySet(), t)
        NativePhotoReceiptJournal.start(journal, first.id, t)
        NativePhotoReceiptJournal.observe(NativePhotoReceiptJournal.find(journal, first.id), setOf("earlier-attempt"))
        NativePhotoReceiptJournal.finish(journal, first.id, confirmed = false, now = t + 950)
        val retry = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), null, emptySet(), t + 1000)
        assertEquals(first.id, retry.id)
        assertEquals(GamePhotoSession.ReceiptStatus.ABSENT, retry.status)
        assertTrue(retry.matchesRequested)
        val next = NativePhotoReceiptJournal.prepare(journal, group, "lumburr-skill-new", emptySet(), null, emptySet(), t + 1000)
        assertEquals(GamePhotoSession.ReceiptStatus.NONE, next.status)
        assertTrue("earlier-attempt" in NativePhotoReceiptJournal.exclusions(journal, next.id))
        assertEquals(1, NativePhotoReceiptJournal.earlierAbsentSignatures(journal, next.id, t + 900).size)
        assertTrue(NativePhotoReceiptJournal.earlierAbsentSignatures(journal, next.id, t + 1000).isEmpty())
        NativePhotoReceiptJournal.receipt(journal, first.id, "late-lumburr", OriginalPhotoCopy.Integrity(1234, "hash"))
        assertEquals(1, journal.getJSONArray("saves").length())
    }

    @Test fun neverDispatchedPreparationCanBeReplacedWithoutAFalsePendingGate() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), "old", emptySet(), t)
        val second = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), "new", setOf("new-baseline"), t + 90000)
        assertNotEquals(first.id, second.id)
        assertEquals(GamePhotoSession.ReceiptStatus.NONE, second.status)
        assertEquals("cancelled", NativePhotoReceiptJournal.find(journal, first.id).getString("status"))
        NativePhotoReceiptJournal.start(journal, second.id, t + 91000)
        assertEquals(t + 91000, NativePhotoReceiptJournal.find(journal, second.id).getLong("startedAt"))
        try {
            NativePhotoReceiptJournal.start(journal, second.id, t + 92000)
            throw AssertionError("A pending attempt must never dispatch twice")
        } catch (_: IllegalStateException) {}
    }

    @Test fun confirmedSureIsIdempotentAndCannotBeUndoneByLateFailure() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), null, emptySet(), t)
        NativePhotoReceiptJournal.start(journal, first.id, t)
        NativePhotoReceiptJournal.finish(journal, first.id, confirmed = true)
        NativePhotoReceiptJournal.finish(journal, first.id, confirmed = true)
        NativePhotoReceiptJournal.finish(journal, first.id, confirmed = false)
        assertEquals(1, journal.getJSONArray("saves").length())
        assertEquals(GamePhotoSession.ReceiptStatus.RECEIPT, NativePhotoReceiptJournal.existing(journal, group, item, emptySet())!!.status)
    }

    // Synthetic artwork only. Never put user screenshots or Pictures files into source/test fixtures.
    private fun art(x: Int, y: Int): Int {
        val red = (130 + 110 * kotlin.math.sin(x * .10 + y * .07)).toInt().coerceIn(0, 255)
        val green = (30 + y * 2).coerceIn(0, 255)
        val blue = (220 - x - y / 2).coerceIn(0, 255)
        return (red shl 16) or (green shl 8) or blue
    }

    @Test fun texturedArtworkMatchesOffCentreExportPatchButNotUnrelatedOrScreenshot() {
        // Screen sampling side is 35 pixels; render that same patch 2x in a 100px native asset.
        val signature = NativePhotoReceipt.signature(1000, 600, .5, .5) { x, y ->
            val sx = (x - 482) * 2 + 15
            val sy = (y - 282) * 2
            if (sx in 0..99 && sy in 0..99) art(sx, sy) else 0x174487
        }
        assertNotNull(signature)
        assertTrue(NativePhotoReceipt.matches(signature, 100, 100, ::art))
        assertFalse(NativePhotoReceipt.matches(signature, 100, 100) { x, y -> art(99 - x, 99 - y) })
        assertFalse(NativePhotoReceipt.matches(signature, 1920, 1080, ::art))
        assertFalse(NativePhotoReceipt.matches(null, 100, 100, ::art))
        assertFalse(NativePhotoReceipt.matches("malformed", 100, 100, ::art))
    }

    @Test fun freshIconMustMatchTexturedReferenceRatherThanBlankOrChangedFrame() {
        fun signature(reverse: Boolean = false) = NativePhotoReceipt.signature(1000, 600, .5, .5) { x, y ->
            val sx = (x - 482) * 2 + 15
            val sy = (y - 282) * 2
            if (sx in 0..99 && sy in 0..99) {
                if (reverse) art(99 - sx, 99 - sy) else art(sx, sy)
            } else {
                0x174487
            }
        }
        val loaded = signature()
        assertNotNull(loaded)
        assertTrue(NativePhotoReceipt.sameIcon(loaded, signature()))
        assertFalse(NativePhotoReceipt.sameIcon(loaded, null))
        assertFalse(NativePhotoReceipt.sameIcon(loaded, signature(true)))
        assertFalse(NativePhotoReceipt.sameIcon(null, null))
    }

    @Test fun knownUndispatchedAttemptCanBeCancelledButSureIntentCannot() {
        val journal = state()
        val first = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), "signature", setOf("old"), t)
        NativePhotoReceiptJournal.start(journal, first.id, t)
        NativePhotoReceiptJournal.cancelBeforeDispatch(journal, first.id)
        val retry = NativePhotoReceiptJournal.prepare(journal, group, item, emptySet(), "signature", setOf("old"), t + 1000)
        assertNotEquals(first.id, retry.id)
        assertEquals(0, journal.getJSONArray("saves").length())
        NativePhotoReceiptJournal.start(journal, retry.id, t + 1000)
        NativePhotoReceiptJournal.confirmIntent(journal, retry.id, t + 1100)
        var rejected = false
        try {
            NativePhotoReceiptJournal.cancelBeforeDispatch(journal, retry.id)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals("pending", NativePhotoReceiptJournal.find(journal, retry.id).getString("status"))
    }

    @Test fun flatBackingOrBlackImageCannotBecomeContentEvidence() {
        assertNull(NativePhotoReceipt.signature(1000, 600, .4, .5) { _, _ -> 0x174487 })
        assertNull(NativePhotoReceipt.signature(1000, 600, .4, .5) { _, _ -> 0 })
        assertNull(NativePhotoReceipt.signature(1000, 600, .001, .5, ::art))
    }
}
