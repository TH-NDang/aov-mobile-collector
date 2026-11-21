package vn.ndang.aovcollector

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {
    /**
     * Skill rows shot on the user's phone (2400x1080), cut out as x 160–680, y 583–724 and stacked in this order:
     * both forms of Qi, Volkath, Liliana and Edras, then heroes from a full run, picked for five icons, a ↻ button,
     * or a bright background next to where the button would be.
     */
    private val names = listOf(
        "qi-1", "qi-2", "volkath-1", "volkath-2", "liliana-1", "liliana-2", "edras-1", "edras-2",
        "edras", "capheny", "wonder-woman", "tachi", "jinna", "xeniel", "ilumia", "ignis", "sinestrea", "astrid", "bonnie", "lauriel", "billo", "tee", "wisp"
    )
    private val rows: Map<String, SkillRow.Row> by lazy { stacked("skill-rows.jpg", names) }

    /**
     * Rows cut the same way from the user's run of 10/11: Tachi and Aoi with a paper lantern floating behind their ↻,
     * Edras's second form, whose dark fourth icon was not taken for a row, and Aya, without the button.
     */
    private val later: Map<String, SkillRow.Row> by lazy { stacked("skill-rows-lantern.jpg", listOf("tachi", "aoi", "edras-2", "aya")) }

    private fun stacked(file: String, names: List<String>): Map<String, SkillRow.Row> {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/$file")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java).invoke(null, it)!!
        }
        val type = image.javaClass
        val w = type.getMethod("getWidth").invoke(image) as Int
        val h = type.getMethod("getHeight").invoke(image) as Int
        val px = type.getMethod(
            "getRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        )
            .invoke(image, 0, 0, w, h, null, 0, w) as IntArray
        val band = h / names.size
        return names.withIndex().associate { (n, name) ->
            // The rest of the screen stays black: only the band is read.
            name to
                SkillRow.read(2400, 1080) { y, row ->
                    row.fill(0xff000000.toInt())
                    if (y in
                        583 until 583 + band
                    ) {
                        System.arraycopy(px, (n * band + y - 583) * w, row, 160, w)
                    }
                }
        }
    }

    @Test fun fiveIconsOnlyForEdras() {
        for (name in names) assertEquals(name, if (name.startsWith("edras")) SkillRow.FIVE else SkillRow.FOUR, rows.getValue(name).centres)
    }

    @Test fun findsTheFormButton() {
        val forms = setOf("qi-1", "qi-2", "volkath-1", "volkath-2", "liliana-1", "liliana-2", "edras-1", "edras-2") +
            setOf("edras", "capheny", "wonder-woman", "tachi")
        for (name in names) assertEquals(name, name in forms, rows.getValue(name).swap)
    }

    @Test fun findsTheFormButtonOverALanternAndADarkSecondForm() {
        assertEquals(SkillRow.FOUR, later.getValue("tachi").centres)
        assertTrue(later.getValue("tachi").swap)
        assertEquals(SkillRow.FOUR, later.getValue("aoi").centres)
        assertTrue(later.getValue("aoi").swap)
        assertEquals(SkillRow.FIVE, later.getValue("edras-2").centres)
        assertTrue(later.getValue("edras-2").swap)
        assertEquals(SkillRow.FOUR, later.getValue("aya").centres)
        assertFalse(later.getValue("aya").swap)
    }

    @Test fun tellsWhichIconsAFormChanges() {
        fun changed(hero: String): List<Int> {
            val a = rows.getValue("$hero-1")
            val b = rows.getValue("$hero-2")
            return a.icons.indices.filter { SkillRow.differ(a.icons[it], b.icons[it]) }
        }
        // Qi changes skill 3, Volkath skill 2, Liliana her three skills, Edras everything.
        assertEquals(listOf(3), changed("qi"))
        assertEquals(listOf(2), changed("volkath"))
        assertEquals(listOf(1, 2, 3), changed("liliana"))
        assertEquals(listOf(0, 1, 2, 3, 4), changed("edras"))
        // Edras's first form again, from another day's run, with the hero behind the row in another pose.
        assertTrue(rows.getValue("edras").sameAs(rows.getValue("edras-1")))
        assertFalse(rows.getValue("edras").sameAs(rows.getValue("edras-2")))
    }

    @Test fun noRowOnAnEmptyScreen() {
        val row = SkillRow.read(2400, 1080) { _, line -> line.fill(0xff203040.toInt()) }
        assertNull(row.centres)
        assertFalse(row.swap)
    }

    /** Icons as tiny samples, far apart in colour: equal numbers are the same icon. */
    private fun row(vararg icons: Int, swap: Boolean = false) = SkillRow.Row(
        if (icons.size ==
            5
        ) {
            SkillRow.FIVE
        } else {
            SkillRow.FOUR
        },
        swap,
        icons.map { intArrayOf(40 * it, 40 * it, 40 * it) }
    )

    private val none = SkillRow.Row(null, false, emptyList())

    /** A hero page whose ↻ button steps through [forms]; a skill or attribute panel hides the row until closed. */
    private class Page(
        val forms: List<SkillRow.Row>,
        var panel: Boolean = false,
        var deaf: Int = 0,
        var form: Int = 0,
        val stopAfter: Int = Int.MAX_VALUE,
        /** Icons whose detail text takes this many boxes; the others fit in one. */
        val longText: Map<Int, Int> = emptyMap(),
        val refuseScroll: Boolean = false,
        /** Looks a detail text takes to be drawn after its tab is tapped; looks before the page shows its row. */
        val slowText: Int = 0,
        var loading: Int = 0,
        /**
         * Icons whose text fills its box exactly, which the game will not scroll: with a line under the box's edge
         * (true, Biron's first skill) or none (false). Held past its end, such a text shows what lies below it.
         */
        val fitted: Map<Int, Boolean> = emptyMap(),
        /** The hero shown alone (a tap landed on his model) until the back arrow is tapped. */
        var alone: Boolean = false,
        /** Looks after a tap on ↻ that show no row yet, the icons still changing. */
        val swapLoads: Int = 0
    ) : SkillReader.Screen {
        private var open = -1
        private var changing = 0
        private var tab = ""
        private var scrolled = 0
        private var detailLooks = 0

        /** Text held up past its end, and the lift that ends the hold, run after the screen was looked at. */
        private var held = false
        private var lift: ((Boolean) -> Unit)? = null
        val stretches = ArrayList<Boolean>()

        /** The panel's glyphs: six lines in the box, a seventh under its edge for a hidden line, moved up while held. */
        private fun glyphs(): SkillDescription.Glyphs {
            val columns = 552
            val rows = 523
            val bits = ByteArray(columns * rows)
            val lines = if (fitted[open] == true) 7 else 6
            val up = if (held) 115 else 0
            for (line in 0 until lines) {
                // Each line its own words: columns lit by a pattern of its own.
                val top = 12 + line * 43 - up
                for (y in maxOf(0, top) until minOf(270, top + 20)) {
                    for (x in 0 until columns) if ((x * (line + 3) / 7 + line) % 5 < 2) bits[y * columns + x] = 1
                }
            }
            // The divider and the level table under the box stay where they are.
            for (y in 290 until 292) for (x in 0 until columns) bits[y * columns + x] = 1
            for (y in 320 until 340) for (x in 0 until columns) if (x % 90 < 20) bits[y * columns + x] = 1
            return SkillDescription.Glyphs(columns, rows, bits)
        }

        /** Whether each detail photo was taken once its text was drawn. */
        val drawnShots = ArrayList<Boolean>()
        var stopped = false
        val taps = ArrayList<String>()
        val shots = ArrayList<String>()
        val kept = ArrayList<String>()
        val notes = ArrayList<String>()
        var outcome: SkillReader.Outcome? = null
        override val alive get() = outcome == null && !stopped

        override fun look(then: (SkillReader.Look?) -> Unit) {
            val shown = if (panel || alone || loading-- > 0 || changing-- > 0) SkillRow.Row(null, false, emptyList()) else forms[form]
            val heroAlone = alone
            val parts = longText[open] ?: 1
            if (panel && tab == "detail") detailLooks++
            val text = if (panel && tab == "detail") {
                SkillDescription.Reading(
                    parts > 1 || open in fitted,
                    IntArray(288) { 30 + open * 40 + scrolled * 25 + (if (held) 15 else 0) },
                    detailLooks > slowText,
                    if (open in fitted) glyphs() else null
                )
            } else {
                null
            }
            then(object : SkillReader.Look {
                override val row = shown
                override val description = text
                override val alone = heroAlone

                override fun done(keep: String?) {
                    if (keep != null) kept += keep
                }
            })
        }

        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            val what = when {
                x == HeroGrid.BACK_X && y == HeroGrid.BACK_Y -> {
                    alone = false
                    "back"
                }
                // Alone, the hero's page has no controls: a tap there does nothing.
                alone -> "nothing"
                x == SkillRow.SWAP_X -> {
                    if (deaf > 0) deaf-- else form = (form + 1) % forms.size
                    changing = swapLoads
                    "swap"
                }
                x == SkillReader.CLOSE_X -> {
                    panel = false
                    "close"
                }
                y == SkillReader.TAB_Y -> (if (x == SkillReader.SUMMARY_X) "summary" else "detail").also {
                    tab = it
                    detailLooks = 0
                }
                else -> {
                    panel = true
                    open = (forms[form].centres ?: SkillRow.FOUR).indexOf(x)
                    tab = ""
                    scrolled = 0
                    "icon $open"
                }
            }
            taps += what
            then(true)
        }

        override fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit) {
            assertEquals(SkillDescription.SCROLL_X, x, 0.0)
            if (toY == SkillDescription.STRETCH_TO) {
                // Up inside the text, held: lifted only after the screen was looked at.
                assertTrue("Stays in the text", y > toY && toY > SkillDescription.TOP && y < SkillDescription.BOTTOM)
                taps += "stretch"
                held = true
                lift = then
                return
            }
            assertTrue("Scrolls the text up, inside its box", y > toY && toY > SkillDescription.TOP && y < SkillDescription.BOTTOM)
            taps += "scroll"
            if (refuseScroll) return then(false)
            scrolled = minOf(scrolled + 1, (longText[open] ?: 1) - 1)
            then(true)
        }

        override fun shot(name: String, then: (Boolean) -> Unit) {
            shots += name
            if (name.endsWith("-detail")) drawnShots += detailLooks > slowText
            if (shots.size >= stopAfter) stopped = true
            then(true)
        }

        override fun later(ms: Long, then: () -> Unit) {
            then()
            // The finger held up past the text comes up after the wait in which the screen is read.
            lift?.let {
                lift = null
                stretches += held
                held = false
                it(true)
            }
        }

        override fun note(text: String) {
            notes += text
        }

        fun run(options: SkillReader.Options = SkillReader.Options(), known: SkillRow.Row? = null): Page {
            SkillReader("hero-007", options, this, known) {
                outcome =
                    it
            }.start()
            return this
        }
    }

    @Test fun fourIconsWithoutForms() {
        val page = Page(listOf(row(1, 2, 3, 4))).run()
        assertEquals(
            listOf("passive", "skill-1", "skill-2", "skill-3").flatMap {
                listOf("hero-007-$it-summary", "hero-007-$it-detail")
            },
            page.shots
        )
        assertEquals(listOf("icon 0", "summary", "detail", "close", "icon 1", "summary", "detail", "close"), page.taps.take(8))
        assertTrue(page.taps.none { it == "swap" })
        assertTrue((page.outcome as SkillReader.Outcome.Done).summary.startsWith("4 ô"))
    }

    @Test fun aLongSkillTextIsScrolledAndEachNewPartKept() {
        val page = Page(listOf(row(1, 2, 3, 4)), longText = mapOf(2 to 3)).run()
        assertTrue(page.outcome is SkillReader.Outcome.Done)
        // The usual shots, plus the two further parts of skill-2's detail text.
        assertEquals(8, page.shots.size)
        assertEquals(listOf("skill-2-detail-2", "skill-2-detail-3"), page.kept)
        // Two scrolls move the text, a third leaves it at its end.
        assertEquals(3, page.taps.count { it == "scroll" })
        val skill2 = page.taps.subList(page.taps.indexOf("icon 2"), page.taps.indexOf("icon 3"))
        assertEquals(listOf("icon 2", "summary", "detail", "scroll", "scroll", "scroll", "close"), skill2)
        assertTrue(page.notes.any { it.startsWith("Mô tả skill-2 dài hơn khung") })
    }

    @Test fun shortSkillTextsAreNeverScrolled() {
        val page = Page(listOf(row(1, 2, 3, 4, swap = true), row(1, 2, 3, 9, swap = true))).run(SkillReader.Options(detailOnly = true))
        assertTrue(page.taps.none { it == "scroll" })
        assertEquals(listOf("form-2-icons"), page.kept)
    }

    @Test fun aRefusedScrollKeepsWhatWasTakenAndGoesOn() {
        val page = Page(listOf(row(1, 2, 3, 4)), longText = mapOf(1 to 2), refuseScroll = true).run(SkillReader.Options(detailOnly = true))
        assertTrue(page.outcome is SkillReader.Outcome.Done)
        assertEquals(4, page.shots.size)
        assertTrue(page.kept.isEmpty())
        assertEquals(1, page.taps.count { it == "scroll" })
        assertTrue(page.notes.any { it.startsWith("Không cuộn được mô tả skill-1") })
    }

    @Test fun aDetailTextSlowToDrawIsWaitedForBeforeItsPhoto() {
        val page = Page(listOf(row(1, 2, 3, 4)), slowText = 2).run()
        assertTrue(page.outcome is SkillReader.Outcome.Done)
        assertEquals(4, page.drawnShots.size)
        assertTrue("taken before the text was drawn: ${page.drawnShots}", page.drawnShots.all { it })
        // A text that never shows is still photographed, with a line in the log, not waited for forever.
        val never = Page(listOf(row(1, 2, 3, 4)), slowText = 99).run(SkillReader.Options(detailOnly = true))
        assertEquals(4, never.shots.size)
        assertTrue(never.notes.any { it.contains("chưa hiện chữ") })
    }

    @Test fun aHeroPageStillLoadingIsReadAgainBeforeFallingBackToFourPlaces() {
        val page = Page(listOf(row(1, 2, 3, 4, 5)), loading = 2).run(SkillReader.Options(detailOnly = true))
        assertTrue(page.outcome is SkillReader.Outcome.Done)
        // The five icons of the row, not the four places guessed when no row is read.
        assertEquals(5, page.shots.size)
        assertTrue(page.notes.none { it.contains("Không nhận ra hàng chiêu") })
    }

    /**
     * Biron's first skill: its box a line short, so the game will not scroll it, and its last word lies under the
     * box's edge. Held up past its end, the text shows that line, kept as the second part; a text that only fills its
     * box shows nothing new and keeps one photo.
     */
    @Test fun aLineUnderTheEdgeOfABoxThatWillNotScrollIsShownByHoldingTheTextUp() {
        val page = Page(listOf(row(1, 2, 3, 4)), fitted = mapOf(1 to true, 2 to false)).run(SkillReader.Options(detailOnly = true))
        assertTrue(page.outcome is SkillReader.Outcome.Done)
        assertEquals(listOf("skill-1-detail-2"), page.kept)
        val skill1 = page.taps.subList(page.taps.indexOf("icon 1"), page.taps.indexOf("icon 2"))
        assertEquals(listOf("icon 1", "detail", "scroll", "stretch", "close"), skill1)
        // Both looked at while held, then let go.
        assertEquals(listOf(true, true), page.stretches)
        assertTrue(page.notes.any { it.startsWith("Mô tả skill-1: kéo giữ") })
        assertTrue(page.notes.none { it.startsWith("Mô tả skill-2: kéo giữ quá") })
        assertTrue(page.notes.any { it == "Mô tả skill-2: kéo giữ thì chữ dịch lên nhưng không lộ thêm chữ" })
        assertEquals(4, page.shots.size)
    }

    /**
     * Thane, 10/11: the macro's tap on empty space landed on his legs, and the game showed him alone for photos. The
     * skills are read once his page is back, by its back arrow, not tapped where nothing is.
     */
    @Test fun aHeroShownAloneForPhotosIsBroughtBackToHisPageFirst() {
        val page = Page(listOf(row(1, 2, 3, 4)), alone = true).run(SkillReader.Options(detailOnly = true))
        assertTrue(page.outcome is SkillReader.Outcome.Done)
        assertEquals("back", page.taps.first())
        assertTrue(page.taps.none { it == "nothing" })
        assertEquals(listOf("passive", "skill-1", "skill-2", "skill-3").map { "hero-007-$it-detail" }, page.shots)
        assertTrue(page.notes.any { it.startsWith("Đang ở chế độ chụp ảnh tướng") })
        assertTrue(page.notes.none { it.contains("Không nhận ra hàng chiêu") })
    }

    @Test fun directSkillsScrollALongTextBeforeTheNextIcon() {
        val page = Page(
            listOf(row(1, 2, 3, 4, 5)),
            longText = mapOf(3 to 2)
        ).run(SkillReader.Options(detailOnly = true, directSkills = true))
        assertEquals(listOf("skill-3-detail-2"), page.kept)
        val skill3 = page.taps.subList(page.taps.indexOf("icon 3"), page.taps.indexOf("icon 4"))
        assertEquals(listOf("icon 3", "detail", "scroll", "scroll"), skill3)
        assertEquals("close", page.taps.last())
    }

    @Test fun qiTakesOnlyTheChangedSkillOfHerSecondForm() {
        val page = Page(listOf(row(1, 2, 3, 4, swap = true), row(1, 2, 3, 9, swap = true))).run(SkillReader.Options(detailOnly = true))
        assertEquals(listOf("passive", "skill-1", "skill-2", "skill-3", "form-2-skill-3").map { "hero-007-$it-detail" }, page.shots)
        assertEquals(listOf("form-2-icons"), page.kept)
        // Two taps on ↻: to the second form and back to the first, where the cycle ends.
        assertEquals(2, page.taps.count { it == "swap" })
        assertEquals(0, page.form)
    }

    @Test fun edrasHasFiveIconsAndChangesThemAll() {
        val page = Page(
            listOf(row(1, 2, 3, 4, 5, swap = true), row(6, 7, 8, 9, 10, swap = true))
        ).run(SkillReader.Options(detailOnly = true, directSkills = true))
        assertEquals(
            listOf("passive", "skill-1", "skill-2", "skill-3", "skill-4").let {
                it + it.map { s -> "form-2-$s" }
            }.map { "hero-007-$it-detail" },
            page.shots
        )
        // Chuyển thẳng chiêu: the panel closes only after each form's last icon, before ↻ is tapped.
        assertEquals(
            listOf("icon 0", "detail", "icon 1", "detail", "icon 2", "detail", "icon 3", "detail", "icon 4", "detail", "close", "swap"),
            page.taps.take(12)
        )
    }

    @Test fun aFormRowNotShownYetIsLookedForAgain() {
        val forms = listOf(row(1, 2, 3, 4, 5, swap = true), row(6, 7, 8, 9, 10, swap = true))
        // Edras's second form, not read on the first look after ↻ (the user's run of 10/11): read on a later one.
        val page = Page(forms, swapLoads = 2).run(SkillReader.Options(detailOnly = true))
        assertEquals(10, page.shots.size)
        assertEquals(listOf("form-2-icons"), page.kept)
        assertTrue(page.notes.none { it.contains("Không đọc được hàng chiêu") })
        // Never shown: the screen is kept to look at, and the first form's photos stand.
        val lost = Page(forms, swapLoads = 99).run(SkillReader.Options(detailOnly = true))
        assertEquals(5, lost.shots.size)
        assertEquals(listOf("form-2-unknown"), lost.kept)
        assertTrue(lost.outcome is SkillReader.Outcome.Done)
    }

    @Test fun aThirdFormTakesOnlyIconsNotSeenBefore() {
        val page = Page(
            listOf(row(1, 2, 3, 4, swap = true), row(1, 5, 6, 4, swap = true), row(1, 2, 7, 8, swap = true))
        ).run(SkillReader.Options(detailOnly = true))
        assertEquals(
            listOf("form-2-skill-1", "form-2-skill-2", "form-3-skill-2", "form-3-skill-3").map {
                "hero-007-$it-detail"
            },
            page.shots.drop(4)
        )
        assertEquals(listOf("form-2-icons", "form-3-icons"), page.kept)
        assertEquals(3, page.taps.count { it == "swap" })
    }

    @Test fun aMissedTapOnTheFormButtonIsRepeatedOnce() {
        val page = Page(
            listOf(row(1, 2, 3, 4, swap = true), row(1, 2, 3, 9, swap = true)),
            deaf = 1
        ).run(SkillReader.Options(detailOnly = true))
        assertTrue(page.shots.contains("hero-007-form-2-skill-3-detail"))
        // A button that never changes anything is given up after the second tap.
        val stuck = Page(listOf(row(1, 2, 3, 4, swap = true)), deaf = 99).run(SkillReader.Options(detailOnly = true))
        assertEquals(2, stuck.taps.count { it == "swap" })
        assertTrue(stuck.outcome is SkillReader.Outcome.Done)
        assertTrue(stuck.notes.any { it.contains("không đổi") })
    }

    @Test fun anOpenPanelIsClosedBeforeReadingTheRow() {
        val page = Page(listOf(row(1, 2, 3, 4)), panel = true).run()
        assertEquals("close", page.taps.first())
        assertEquals(8, page.shots.size)
        // No row even then: the four places of earlier versions, with the screen kept to look at later.
        val lost = Page(listOf(none), panel = true).run(SkillReader.Options(detailOnly = true))
        assertEquals(listOf("skill-row-unknown"), lost.kept)
        assertEquals(4, lost.shots.size)
    }

    @Test fun stopsWhenThePauseCutsTheStep() {
        // Tạm dừng after the second photo: nothing more is tapped or shot, and the step does not report back.
        val page = Page(
            listOf(row(1, 2, 3, 4, swap = true), row(1, 2, 3, 9, swap = true)),
            stopAfter = 2
        ).run(SkillReader.Options(detailOnly = true))
        assertEquals(2, page.shots.size)
        assertEquals("detail", page.taps.last())
        assertNull(page.outcome)
    }

    @Test fun aRetryAfterAPauseGoesBackToTheFirstForm() {
        // The pause left Qi on her second form: ↻ first, then the step runs as if new.
        val first = row(1, 2, 3, 4, swap = true)
        val page = Page(listOf(first, row(1, 2, 3, 9, swap = true)), form = 1).run(SkillReader.Options(detailOnly = true), known = first)
        assertEquals("swap", page.taps.first())
        assertEquals(listOf("passive", "skill-1", "skill-2", "skill-3", "form-2-skill-3").map { "hero-007-$it-detail" }, page.shots)
        assertEquals(0, page.form)
    }
}
