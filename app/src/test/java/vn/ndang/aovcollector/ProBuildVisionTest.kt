package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ProBuildVisionTest {
    /**
     * Small synthetic screenshot of the same relative geometry as the six reference captures.
     * Tests the recognizer without the game, Android bitmap classes or network access.
     */
    private class Frame(
        private val screen: ProBuildVision.Page,
        private val owned: Boolean = false,
        private val buttons: List<Double> = emptyList()
    ): ProBuildVision.Frame {
        override val width = 1536
        override val height = 691
        private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b
        override fun pixel(x: Int, y: Int): Int {
            val xx = x.toDouble() / width; val yy = y.toDouble() / height
            if (screen == ProBuildVision.Page.HERO_PICKER && xx in .804..825e-3 && yy in .210.. .251) {
                if (owned && xx in .808.. .822 && yy in .218.. .246) return rgb(224,235,247)
                return rgb(58,98,193) // blue checkbox outline
            }
            if (screen == ProBuildVision.Page.PRO_PANEL &&
                xx in .705.. .811 && buttons.any { abs(it - yy) <= .031 }) {
                return rgb(220,190,81)  // yellow "Dùng combo" background
            }
            return when(screen) {
                ProBuildVision.Page.LOBBY -> rgb(237,156,215)
                ProBuildVision.Page.PREPARATION -> rgb(22,35,51)
                ProBuildVision.Page.HERO_PICKER, ProBuildVision.Page.PRO_PANEL -> rgb(48,47,117)
                else -> rgb(80,80,80)
            }
        }
    }

    @Test fun distinguishesTheFourScreensAndRefusesUnknown() {
        assertEquals(ProBuildVision.Page.LOBBY, ProBuildVision.page(Frame(ProBuildVision.Page.LOBBY)))
        assertEquals(ProBuildVision.Page.PREPARATION, ProBuildVision.page(Frame(ProBuildVision.Page.PREPARATION)))
        assertEquals(ProBuildVision.Page.HERO_PICKER, ProBuildVision.page(Frame(ProBuildVision.Page.HERO_PICKER)))
        assertEquals(ProBuildVision.Page.PRO_PANEL, ProBuildVision.page(Frame(ProBuildVision.Page.PRO_PANEL)))
        assertEquals(ProBuildVision.Page.UNKNOWN, ProBuildVision.page(Frame(ProBuildVision.Page.UNKNOWN)))
    }

    @Test fun readsCheckboxWithoutBlindlyToggling() {
        assertEquals(true, ProBuildVision.ownedOnlyChecked(Frame(ProBuildVision.Page.HERO_PICKER, owned=true)))
        assertEquals(false, ProBuildVision.ownedOnlyChecked(Frame(ProBuildVision.Page.HERO_PICKER, owned=false)))
        assertNull(ProBuildVision.ownedOnlyChecked(Frame(ProBuildVision.Page.PRO_PANEL)))
    }

    @Test fun locatesYellowButtonsAfterScrollWithoutFixedRows() {
        val top=ProBuildVision.proposals(Frame(ProBuildVision.Page.PRO_PANEL, buttons=listOf(.409,.777)))
        val scroll=ProBuildVision.proposals(Frame(ProBuildVision.Page.PRO_PANEL, buttons=listOf(.484,.852)))
        assertEquals(2, top.size)
        assertEquals(2, scroll.size)
        assertEquals(.409,top[0].buttonY,.015)
        assertEquals(.777,top[1].buttonY,.015)
        assertEquals(.484,scroll[0].buttonY,.015)
        assertEquals(.852,scroll[1].buttonY,.015)
        assertFalse(ProBuildVision.sameViewport(top,scroll))
        assertTrue(ProBuildVision.sameViewport(top,ProBuildVision.proposals(Frame(ProBuildVision.Page.PRO_PANEL, buttons=listOf(.409,.777)))))
        assertTrue(ProBuildVision.proposals(Frame(ProBuildVision.Page.PRO_PANEL)).isEmpty())
    }

    @Test fun eightColumnGridMovesRatherThanTappingWrongRow() {
        val first=ProHeroGrid.plan(0,0.0) as ProHeroGrid.Plan.Tap
        val ninth=ProHeroGrid.plan(8,0.0) as ProHeroGrid.Plan.Tap
        assertEquals(.222,first.x,1e-6)
        assertEquals(.363,first.y,1e-6)
        assertEquals(.222,ninth.x,1e-6)
        assertEquals(.539,ninth.y,1e-6)
        assertTrue(ProHeroGrid.plan(40,0.0) is ProHeroGrid.Plan.Move)
        val shifted=ProHeroGrid.plan(40,.90) as ProHeroGrid.Plan.Tap
        assertEquals(.343,shifted.y,.002)
    }
}
