package vn.ndang.aovcollector

import java.io.InputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroViewTest {
    /**
     * From the user's run of 10/11, the title (x 270–570, y 20–120) and camera button (x 2050–2350, y 470–570) regions
     * of Thane shown alone for photos, then of Arduin's page, stacked in that order.
     */
    private val cut: IntArray by lazy {
        System.setProperty("java.awt.headless", "true")
        val image = javaClass.getResourceAsStream("/screens/hero-alone.png")!!.use {
            Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java).invoke(null, it)!!
        }
        image.javaClass.getMethod(
            "getRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        ).invoke(image, 0, 0, 300, 400, null, 0, 300) as IntArray
    }

    /** Shot [n] (0 Thane, 1 Arduin) back in a dark 2400x1080 frame. */
    private fun alone(n: Int) = HeroView.alone(2400, 1080) { y, row ->
        row.fill(0xff101830.toInt())
        if (y in 20 until 120) System.arraycopy(cut, (n * 200 + y - 20) * 300, row, 270, 300)
        if (y in 470 until 570) System.arraycopy(cut, (n * 200 + 100 + y - 470) * 300, row, 2050, 300)
    }

    @Test fun aHeroShownAloneIsToldFromHisPage() {
        assertTrue(alone(0))
        assertFalse(alone(1))
    }
}
