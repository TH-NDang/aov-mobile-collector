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

    @Test fun scrollingIsToldFromTheSameTextReadAgain() {
        assertTrue(SkillDescription.changed(read(0).signature, read(1).signature))
        assertFalse(SkillDescription.changed(read(1).signature, read(1).signature))
    }
}
