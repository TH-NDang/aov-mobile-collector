package vn.ndang.aovcollector

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProCombosTest {
    private fun word(text: String, x: Double, y: Double) = GameScreens.Word(text, x - .035, y - .012, x + .035, y + .012)

    private fun signature(n: Int) = IntArray(325) { if (it == 0) 90 else n }

    private fun entry(id: String, y: Double, n: Int = 10) = ProComboScreens.Entry(id, id, word("Dùng combo", .75, y), signature(n))

    private val popup = listOf(word("Hệ thống đề xuất", .23, .11), word("Đề cử Pro", .21, .455))
    private val prep =
        listOf(
            word("Chuẩn bị", .17, .055),
            word("Trang bị", .62, .18),
            word("Phù hiệu", .62, .36),
            word("Phụ trợ", .62, .72),
            word("Ngộ Không", .36, .91),
            word("Ph.án khác", .89, .91)
        )

    private inner class Fake(var rows: List<String>, val journal: JSONObject = JSONObject()) : ProComboReader.Screen {
        override var alive = true
        var popupOpen = false
        var offset = 0
        var actual = signature(10)
        var explicitEmpty = false
        var wrongHero = false
        var saveOk = true
        var applies = 0
        var captures = ArrayList<String>()
        var outcome: ProComboReader.Outcome? = null
        val queue = ArrayDeque<() -> Unit>()
        var dropRows = false
        var cancelOnApply = false
        var badApply = false
        var pauseAfterCapture = false
        var ineffectiveScroll = false
        var geometric = false
        var scroll = 0.0
        var cancelAtApply = -1
        var fullBoundary = false
        var values = rows.map { 10 }
        var seenEntries = emptyList<ProComboScreens.Entry>()
        var mutateOnReset = false
        var resetGestures = 0
        var oversizedDiscovery = false
        var overshot = false
        var mutateOnReplay = false
        var corruptDiscoveryReads = 0
        var discoveryMoved = false
        var cancelOnReplay = false
        var emptyOnReplay = false
        var shortenOnReplay = false
        var pixelOnlyMotion = false
        var animatedBackground = false
        var looks = 0
        var scrollGestures = 0
        var replayNotices = 0

        override fun say(text: String) {
            replayNotices++
            if (shortenOnReplay) {
                rows = rows.take(1)
                scroll = 0.0
            }
            if (emptyOnReplay) {
                explicitEmpty = true
                dropRows = true
                scroll = 0.0
            }
            if (mutateOnReplay) values = values.map { (it + 80) % 220 }
            if (cancelOnReplay) alive = false
        }

        override fun look(then: (ProComboReader.Look?) -> Unit) {
            looks++
            val p = popupOpen
            val visible = if (dropRows) {
                emptyList()
            } else if (geometric) {
                rows.mapIndexedNotNull { i, id ->
                    val y = .41 + .365 * i - scroll
                    if (y in .40..0.79) entry(id, y, values[i]) else null
                }
            } else {
                rows.drop(offset).take(2).mapIndexed { i, id -> entry(id, .41 + .365 * i, values[offset + i]) }
            }
            val observed = if (discoveryMoved && corruptDiscoveryReads > 0 && p) {
                corruptDiscoveryReads--
                visible.map { it.copy(signature = signature(220)) }
            } else {
                visible
            }
            seenEntries = observed
            val words = if (p) {
                popup + if (explicitEmpty) listOf(word("Không có đề cử", .5, .5)) else emptyList()
            } else if (wrongHero) {
                prep.filterNot {
                    it.text ==
                        "Ngộ Không"
                }
            } else {
                prep
            }
            then(object : ProComboReader.Look {
                override val words = words
                override val proSelected = p
                override val entries = if (p) observed else emptyList()
                override val preparationSignature = if (p) null else actual
                override val listSignature = IntArray(40) {
                    if (animatedBackground) {
                        looks * 40
                    } else if (pixelOnlyMotion && scrollGestures > 0) {
                        40 + minOf(resetGestures, 2) * 30
                    } else if (geometric) {
                        (scroll * 1000).toInt()
                    } else {
                        offset * 30 + 10
                    }
                }
                override val listBottomClear =
                    fullBoundary ||
                        p &&
                        (
                            if (geometric) {
                                scroll >= maxOf(0.0, (rows.size - 2) * .365) - .001
                            } else {
                                visible.size == 1 &&
                                    offset == rows.size - 1
                            }
                            )
                var consumed = false

                override fun done(keep: String?) {
                    check(!consumed)
                    consumed = true
                }

                override fun keep(name: String, then: (Boolean) -> Unit) {
                    done(null)
                    captures.add(name)
                    if (pauseAfterCapture) {
                        alive =
                            false
                    }
                    then(true)
                }
            })
        }

        override fun calibrated(action: CalibrationAction, target: GameScreens.Word?, then: (Boolean) -> Unit) {
            when (action.id) {
                "pro.v1.other" -> popupOpen = true
                "pro.v1.tab" -> popupOpen = true
                "pro.v1.list.reset" -> {
                    resetGestures++
                    discoveryMoved = false
                    if (mutateOnReset) values = values.map { 150 }
                    if (geometric) {
                        scroll = maxOf(0.0, scroll - (action.points[1].y - action.points[0].y))
                    } else {
                        offset =
                            maxOf(0, offset - 1)
                    }
                }
                "pro.v1.list.scroll" -> {
                    scrollGestures++
                    if (!ineffectiveScroll) {
                        if (geometric) {
                            val distance = action.points[0].y - action.points[1].y
                            discoveryMoved = resetGestures >= 3 && applies == 0
                            val actualDistance = if (oversizedDiscovery && discoveryMoved && distance > .2) {
                                overshot = true
                                .50
                            } else {
                                distance
                            }
                            scroll = minOf(maxOf(0.0, (rows.size - 2) * .365), scroll + actualDistance)
                        } else {
                            offset = minOf(maxOf(0, rows.size - 1), offset + 1)
                        }
                    }
                }
                "pro.v1.apply" -> {
                    applies++
                    actual =
                        seenEntries.minByOrNull { kotlin.math.abs(it.button.y - (target?.y ?: .41)) }?.signature ?: actual
                    popupOpen = false
                    if (badApply) {
                        actual =
                            signature(100)
                    }
                    if (cancelOnApply || applies == cancelAtApply) alive = false
                }
                else -> error(action.id)
            }
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) {
            queue.addLast(then)
        }

        override fun persist(journal: JSONObject) = saveOk

        override fun captureExists(name: String) = name in captures

        fun run() {
            ProComboReader(this, "Ngộ Không", journal) { outcome = it }.start()
            var count = 0
            while (queue.isNotEmpty()) {
                check(count++ < 2000)
                queue.removeFirst()()
            }
        }
    }

    @Test fun collectFiveAndRestoreWithoutSixthPhoto() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.run()
        assertEquals(ProComboReader.Outcome.Done(5, false), f.outcome)
        assertEquals(listOf("top-1", "top-2", "top-3", "top-4", "top-5"), f.captures)
        assertEquals(6, f.applies)
        assertTrue(f.journal.getBoolean("restoredTop1"))
        assertFalse(f.journal.getBoolean("restorationNeeded"))
    }

    @Test fun clippedCardsRequireAdaptiveOverlapRatherThanAssumingTwoRows() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.run()
        assertEquals(ProComboReader.Outcome.Done(5, false), f.outcome)
        assertEquals(6, f.applies)
    }

    @Test fun oversizedFirstDiscoveryReplaysFrozenPrefixWithSmallerStep() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.oversizedDiscovery = true
        f.run()
        assertEquals(ProComboReader.Outcome.Done(5, false), f.outcome)
        assertTrue(f.replayNotices > 0)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), ProComboScreens.catalog(f.journal).map { it.id })
        assertEquals(6, f.applies)
        assertArrayEquals(signature(10), f.actual)
    }

    @Test fun transientOverlapDescriptorFailureIsRereadWithoutRestartOrApplyRetry() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.corruptDiscoveryReads = 1
        f.run()
        assertEquals(ProComboReader.Outcome.Done(5, false), f.outcome)
        assertEquals(0, f.replayNotices)
        assertEquals(6, f.applies)
    }

    @Test fun changedFrozenRankOnReplayFailsBeforeApply() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.oversizedDiscovery = true
        f.mutateOnReplay = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue((f.outcome as ProComboReader.Outcome.Failed).reason.contains("Thứ hạng/combo"))
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
    }

    @Test fun persistentOverlapFailureStopsAfterBoundedReplaysWithoutApply() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.corruptDiscoveryReads = 10000
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(4, f.replayNotices)
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
    }

    @Test fun shorterNonemptyReplayCannotTruncateFrozenPrefix() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.oversizedDiscovery = true
        f.shortenOnReplay = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue((f.outcome as ProComboReader.Outcome.Failed).reason.contains("ngắn hơn"))
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
        assertFalse(f.journal.has("order"))
    }

    @Test fun changedSameCreatorDescriptorOnReplayCannotSubstituteRank() {
        val f = Fake(listOf("same", "same", "c", "d", "e", "f"))
        f.values = listOf(10, 100, 10, 10, 10, 10)
        f.geometric = true
        f.oversizedDiscovery = true
        f.mutateOnReplay = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue((f.outcome as ProComboReader.Outcome.Failed).reason.contains("Thứ hạng/combo"))
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
    }

    @Test fun emptyReplayCannotEraseAlreadyObservedPrefix() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.oversizedDiscovery = true
        f.emptyOnReplay = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
        assertFalse(f.journal.optBoolean("empty"))
    }

    @Test fun stopDuringReplayNeverAppliesOrCaptures() {
        val f = Fake(listOf("a", "b", "c", "d", "e", "f"))
        f.geometric = true
        f.oversizedDiscovery = true
        f.cancelOnReplay = true
        f.run()
        assertEquals(1, f.replayNotices)
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
    }

    @Test fun shortTwoRowProWithConstantAnimationStillCapturesBoth() {
        val f = Fake(listOf("first", "second"))
        f.geometric = true
        f.fullBoundary = true
        f.ineffectiveScroll = true
        f.animatedBackground = true
        f.run()
        assertEquals(ProComboReader.Outcome.Done(2, false), f.outcome)
        assertEquals(listOf("top-1", "top-2"), f.captures)
        assertEquals(3, f.applies)
        assertTrue("should not need 15 upward attempts", f.resetGestures < 14)
    }

    @Test fun shortListProofRejectsUnstableComboBeforeAnyApply() {
        val f = Fake(listOf("first", "second"))
        f.fullBoundary = true
        f.ineffectiveScroll = true
        f.animatedBackground = true
        f.mutateOnReset = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
    }

    @Test fun animatedLongListWithoutBoundaryStillFailsSafely() {
        val f = Fake(listOf("a", "b", "c", "d"))
        f.ineffectiveScroll = true
        f.animatedBackground = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
        assertTrue(f.captures.isEmpty())
    }

    @Test fun pixelChangesWithoutSharedCardMotionCannotProveTop() {
        val f = Fake(listOf("a", "b", "c"))
        f.ineffectiveScroll = true
        f.pixelOnlyMotion = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue((f.outcome as ProComboReader.Outcome.Failed).reason.contains("đầu danh sách"))
        assertEquals(0, f.applies)
    }

    @Test fun nominalCoarseMoveRetainsCompleteSecondCardDespiteClippedNeighbors() {
        fun frame(offset: Double): List<ProComboScreens.Entry> {
            val words = popup + listOf(.408, .773, 1.138).flatMapIndexed { i, y ->
                listOf(word("creator$i", .40, y - offset - .185), word("Dùng combo", .75, y - offset), word("90", .635, y - offset + .055))
            }
            return ProComboScreens.entries(1000, 500, words) { _, row -> row.fill(0xff303080.toInt()) }
        }
        assertEquals(listOf("creator0", "creator1"), frame(0.0).map { it.id })
        assertEquals(listOf("creator1"), frame(.22).map { it.id })
        assertTrue(ProComboScreens.matches(frame(0.0)[1].signature, frame(.22)[0].signature))
    }

    @Test fun listMotionSignatureExcludesRightSideAttributeTooltip() {
        val plain = ProComboScreens.listSignature(1000, 500) { _, row -> row.fill(0xff303080.toInt()) }
        val tooltip = ProComboScreens.listSignature(1000, 500) { y, row ->
            row.fill(0xff303080.toInt())
            if (y in 160..215) for (x in 620..780) row[x] = 0xffeeeeee.toInt()
        }
        assertArrayEquals(plain, tooltip)
    }

    @Test fun missingPendingCaptureIsReverifiedAndRecapturedWithoutApply() {
        val f = Fake(listOf("a"))
        f.pauseAfterCapture = true
        f.run()
        f.captures.clear()
        f.alive = true
        f.pauseAfterCapture = false
        f.run()
        assertEquals(ProComboReader.Outcome.Done(1, false), f.outcome)
        assertEquals(2, f.applies)
        assertEquals(listOf("top-1"), f.captures)
    }

    @Test fun pendingRestoreObservedWithoutExtraApplyOrPhoto() {
        val f = Fake(listOf("a"))
        f.cancelAtApply = 2
        f.run()
        assertEquals("restore_pending", f.journal.getString("phase"))
        f.alive = true
        f.run()
        assertEquals(ProComboReader.Outcome.Done(1, false), f.outcome)
        assertEquals(2, f.applies)
        assertEquals(listOf("top-1"), f.captures)
    }

    @Test fun ineffectiveScrollDoesNotClaimTopOrShortList() {
        val f = Fake(listOf("a", "b", "c"))
        f.ineffectiveScroll = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
    }

    @Test fun exactFitTwoRowsWithPositiveBoundariesAndOppositeGesturesComplete() {
        val f = Fake(listOf("a", "b"))
        f.ineffectiveScroll = true
        f.fullBoundary = true
        f.run()
        assertEquals(ProComboReader.Outcome.Done(2, false), f.outcome)
        assertEquals(3, f.applies)
    }

    @Test fun fullCardBoundaryRequiresAlignedCoherentEdgeNotBlankFooterAlone() {
        val rows = listOf(entry("a", .41), entry("b", .775))
        assertTrue(
            ProComboScreens.listBottomClear(1000, 500, rows) { y, row ->
                row.fill(
                    if (y <
                        447
                    ) {
                        0xff303080.toInt()
                    } else {
                        0xff6060b0.toInt()
                    }
                )
            }
        )
        assertFalse(ProComboScreens.listBottomClear(1000, 500, rows) { _, row -> row.fill(0xff303080.toInt()) })
        assertFalse(
            ProComboScreens.listBottomClear(1000, 500, listOf(entry("b", .75))) { y, row ->
                row.fill(
                    if (y <
                        447
                    ) {
                        0xff303080.toInt()
                    } else {
                        0xff6060b0.toInt()
                    }
                )
            }
        )
    }

    @Test fun actualFittingGeometryOneThroughFiveCompletesActualCount() {
        for (count in 1..5) {
            val f = Fake((1..count).map { "creator$it" })
            f.geometric = true
            f.run()
            assertEquals("count=$count", ProComboReader.Outcome.Done(count, false), f.outcome)
            assertEquals(count + 1, f.applies)
        }
    }

    @Test fun sameCreatorDistinctVisibleCombosRemainSeparateRanks() {
        val f = Fake(listOf("same", "same", "other"))
        f.geometric = true
        f.values = listOf(10, 100, 190)
        f.run()
        assertEquals(ProComboReader.Outcome.Done(3, false), f.outcome)
        assertEquals(listOf("top-1", "top-2", "top-3"), f.captures)
        assertArrayEquals(signature(10), f.actual)
    }

    @Test fun changedShortListDuringOppositeProbeFailsBeforeApply() {
        val f = Fake(listOf("a", "b"))
        f.geometric = true
        f.mutateOnReset = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
    }

    @Test fun midpointObservationCannotResolveToEitherFrozenSameCreatorCombo() {
        val a = entry("same", .41, 10)
        val b = entry("same", .775, 60)
        val observed = entry("same", .41, 35)
        assertTrue(ProComboScreens.matches(a.signature, observed.signature))
        assertTrue(ProComboScreens.matches(b.signature, observed.signature))
        assertFalse(ProComboScreens.uniqueMatch(a, observed, listOf(a, b)))
        assertFalse(ProComboScreens.uniqueMatch(b, observed, listOf(a, b)))
        assertTrue(ProComboScreens.uniqueMatch(a, a, listOf(a, b)))
    }

    @Test fun fullCatalogBeyondTopFiveSurvivesResumeAndRejectsAmbiguousAppliedFrame() {
        val f = Fake(listOf("same", "b", "c", "d", "e", "same"))
        f.values = listOf(10, 100, 100, 100, 100, 60)
        f.geometric = true
        f.cancelAtApply = 1
        f.run()
        assertEquals(6, ProComboScreens.catalog(f.journal).size)
        assertEquals(5, f.journal.getJSONArray("order").length())
        f.alive = true
        f.actual = signature(35)
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue(f.captures.isEmpty())
        assertEquals(1, f.applies)
    }

    @Test fun actualTwoRowsOnly() {
        val f = Fake(listOf("a", "b"))
        f.run()
        assertEquals(ProComboReader.Outcome.Done(2, false), f.outcome)
        assertEquals(3, f.applies)
    }

    @Test fun emptyNeedsPositiveStatusAndCapturesPopup() {
        val f = Fake(emptyList())
        f.explicitEmpty = true
        f.run()
        assertEquals(ProComboReader.Outcome.Done(0, true), f.outcome)
        assertEquals(listOf("empty-pro-list"), f.captures)
        assertTrue(f.popupOpen)
        assertEquals(0, f.applies)
    }

    @Test fun unknownBlankIsNotEmpty() {
        val f = Fake(emptyList())
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue(f.captures.isEmpty())
        assertEquals(0, f.applies)
    }

    @Test fun duplicateCreatorFailsBeforeApply() {
        val f = Fake(listOf("a", "b", "c", "a"))
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
    }

    @Test fun unreadableHeroCannotApply() {
        val f = Fake(listOf("a"))
        f.wrongHero = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
    }

    @Test fun wrongAppliedComponentsNeverCaptureOldPreparation() {
        val f = Fake(listOf("a"))
        f.badApply = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertTrue(f.captures.isEmpty())
        assertEquals(1, f.applies)
        f.run()
        assertEquals(1, f.applies)
    }

    @Test fun cancellationPendingApplyDoesNotBlindlyReapplyOnResume() {
        val f = Fake(listOf("a"))
        f.cancelOnApply = true
        f.run()
        assertEquals("apply_pending", f.journal.getString("phase"))
        assertTrue(f.captures.isEmpty())
        f.alive = true
        f.cancelOnApply = false
        f.popupOpen = true
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(1, f.applies)
    }

    @Test fun savedPendingCaptureResumesWithoutDuplicatePhoto() {
        val f = Fake(listOf("a"))
        f.pauseAfterCapture = true
        f.run()
        assertEquals("capture_pending", f.journal.getString("phase"))
        assertEquals(listOf("top-1"), f.captures)
        f.alive = true
        f.pauseAfterCapture = false
        f.run()
        assertEquals(ProComboReader.Outcome.Done(1, false), f.outcome)
        assertEquals(listOf("top-1"), f.captures)
        assertEquals(2, f.applies)
    }

    @Test fun contradictoryEmptyAndApplyButtonIsNotEmpty() {
        assertFalse(ProComboScreens.empty(popup + word("Không có đề cử", .5, .5) + word("Dùng combo", .75, .4)))
    }

    @Test fun journalWriteFailurePreventsApply() {
        val f = Fake(listOf("a"))
        f.saveOk = false
        f.run()
        assertTrue(f.outcome is ProComboReader.Outcome.Failed)
        assertEquals(0, f.applies)
    }

    @Test fun singleWrongSpellCannotBeDilutedByOtherElevenComponents() {
        val a = signature(10)
        val b = a.copyOf()
        for (i in 298..324) b[i] = 200
        assertFalse(ProComboScreens.matches(a, b))
        b.fill(10)
        b[0] = 60
        assertFalse(ProComboScreens.matches(a, b))
    }

    @Test fun popupLabelAloneDoesNotProveEmpty() {
        assertFalse(ProComboScreens.empty(popup))
        assertFalse(ProComboScreens.empty(popup + word("Lỗi kết nối", .5, .5)))
        assertFalse(ProComboScreens.preparation(popup + prep))
    }
}
