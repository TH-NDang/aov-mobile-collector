package vn.ndang.aovcollector

import java.io.InputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillDescriptionTest {
    /**
     * Dyadia's second skill on the user's phone (2400x1080), Chi tiết tab, before and after scrolling its text:
     * cut out as x 1056–2256, y 216–540 and stacked in that order. Nothing below the text box (where the UID is).
     */
    private val parts: List<IntArray> by lazy {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/skill-description.jpg")!!.use {
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
        ).invoke(image, 0, 0, w, h, null, 0, w) as IntArray
        (0 until 2).map { n -> px.copyOfRange(n * w * (h / 2), (n + 1) * w * (h / 2)) }
    }

    /** The cut-out back in a black 2400x1080 frame; [source] picks which cut-out row fills each screen row. */
    private fun read(part: Int, source: (Int) -> Int = { it }) = SkillDescription.read(2400, 1080) { y, row ->
        row.fill(0xff000000.toInt())
        val from = source(y) - 216
        if (from in 0 until 324) System.arraycopy(parts[part], from * 1200, row, 1056, 1200)
    }

    @Test fun aTextRunningPastItsBoxIsSeenBeforeAndAfterScrolling() {
        assertTrue(read(0).reachesBottom)
        assertTrue(read(1).reachesBottom)
    }

    @Test fun aTextEndingAboveTheBottomOfItsBoxIsNotScrolled() {
        // The last band filled with the empty panel just below the box, as when the text ends a line or more higher.
        val tail = (SkillDescription.TAIL_TOP * 1080).toInt()
        val short = read(0) { y -> if (y < tail) y else 514 + (y - tail) % 14 }
        assertFalse(short.reachesBottom)
    }

    /**
     * A synthetic 2000x900 panel: dark violet, with a line of text every .04 from .24 down to [last], [grey] lines in
     * the grey of the user's Billow screenshot (too dim for the first rule), the others white.
     */
    private fun panel(last: Double, grey: Set<Int> = emptySet()) = SkillDescription.read(2000, 900) { y, row ->
        row.fill(0xff3c3270.toInt())
        val f = y / 900.0
        var line = 0
        var top = .24 - .012
        while (top <= last) {
            if (f in top..(top + .024)) {
                val color = if (line in grey) 0xff9a96ae.toInt() else 0xffe8e6f0.toInt()
                // Strokes over part of the line, as glyphs are.
                for (x in 922 until 1700) if ((x / 3) % 3 != 0) row[x] = color
            }
            line++
            top += .04
        }
    }

    @Test fun aTallerBoxCutLowerDownIsSeenToGoOn() {
        // Billow's second skill: its last band holds only a grey line, and the text goes on to .52, cut there.
        assertTrue(panel(.52, grey = setOf(5)).reachesBottom)
        // Text that ends in the upper part of the box is not scrolled, and an empty box shows no text at all.
        assertFalse(panel(.36).reachesBottom)
        assertTrue(panel(.36).text)
        assertFalse(panel(.0).text)
    }

    @Test fun scrollingIsToldFromTheSameTextReadAgain() {
        assertTrue(SkillDescription.changed(read(0).signature, read(1).signature))
        assertFalse(SkillDescription.changed(read(1).signature, read(1).signature))
    }

    /**
     * Biron's first skill on the user's phone (10/11), Chi tiết tab, cut out as x 1056–2256, y 200–760: its text ends
     * "giảm một" at y 490, a line short of its end, with the divider at 523 and the level table under it.
     */
    private val cut: IntArray by lazy {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/skill-description-cut.jpg")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java).invoke(null, it)!!
        }
        val type = image.javaClass
        type.getMethod(
            "getRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        ).invoke(image, 0, 0, 1200, 560, null, 0, 1200) as IntArray
    }

    /** The cut-out back in its frame, each screen row filled from the cut-out's row [source] (screen rows, -1: dark). */
    private fun biron(source: (Int) -> Int = { it }) = SkillDescription.read(2400, 1080) { y, row ->
        row.fill(0xff000000.toInt())
        val from = source(y) - 200
        if (from in 0 until 560) System.arraycopy(cut, from * 1200, row, 1056, 1200)
    }

    /**
     * Held up 115 rows past its end: the text in the box (y 232–510) moves up, and under its old last line comes
     * [below] (a line of text, or the empty panel at y 500); the header, divider and table stay.
     */
    private fun held(below: (Int) -> Int) = biron { y ->
        when {
            y < 232 || y >= 510 -> y
            y + 115 < 510 -> y + 115
            else -> below(y + 115 - 510)
        }
    }

    @Test fun aLineHeldIntoViewFromUnderTheBoxIsSeenAsNew() {
        val before = biron()
        assertTrue(before.reachesBottom)
        // The hidden line: words like those of the line "hồi máu tăng lên…" (y 418–448), then the empty panel.
        val shown = held { d -> if (d < 30) 418 + d else 500 }
        assertTrue(SkillDescription.changed(before.signature, shown.signature))
        assertTrue(SkillDescription.revealed(before.glyphs!!, shown.glyphs!!))
        // A word alone under the box, as Biron's "nửa.": the start of the line "thân." (y 330–360, x up to 1180).
        val word = biron { y ->
            when {
                y < 232 || y >= 510 -> y
                y + 115 < 510 -> y + 115
                y + 115 - 510 < 30 -> 330 + (y + 115 - 510)
                else -> 500
            }
        }
        assertTrue(SkillDescription.revealed(before.glyphs!!, word.glyphs!!))
        // Nothing under it: the same text, only moved up.
        val nothing = held { 500 }
        assertTrue(SkillDescription.changed(before.signature, nothing.signature))
        assertFalse(SkillDescription.revealed(before.glyphs!!, nothing.glyphs!!))
        assertFalse(SkillDescription.revealed(before.glyphs!!, before.glyphs!!))
    }
}
