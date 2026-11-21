package vn.ndang.aovcollector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProSelectionPixelsTest {
    private val words = listOf(GameScreens.Word("Đề cử Pro", .176, .440, .235, .470))

    @Test fun saturatedLeftGlowSelectsAtMultipleResolutions() {
        for ((width, height) in listOf(2048 to 922, 1024 to 461, 2400 to 1080)) {
            val reader = { y: Int, row: IntArray ->
                row.fill(0xff30307a.toInt())
                if (y.toDouble() / height in .446..0.465) {
                    for (x in (width * .150).toInt()..(width * .174).toInt()) row[x] = 0xff3250eb.toInt()
                }
            }
            assertTrue(ProComboScreens.proSelected(width, height, words, reader))
        }
    }

    @Test fun paleUnselectedTextOrIconCannotMasqueradeAsBlueGlow() {
        val reader = { _: Int, row: IntArray -> row.fill(0xff96b4fa.toInt()) }
        assertFalse(ProComboScreens.proSelected(2048, 922, words, reader))
    }

    @Test fun plainBackgroundAndMissingTabAreNotSelected() {
        val reader = { _: Int, row: IntArray -> row.fill(0xff30307a.toInt()) }
        assertFalse(ProComboScreens.proSelected(2048, 922, words, reader))
        assertFalse(ProComboScreens.proSelected(2048, 922, emptyList(), reader))
    }

    @Test fun dimOrBroadBlueFieldIsInconclusiveNotSelected() {
        for (color in listOf(0xff233296.toInt(), 0xff3250eb.toInt())) {
            assertFalse(ProComboScreens.proSelected(2048, 922, words) { _, row -> row.fill(color) })
        }
    }

    @Test fun smallOcrShiftToleratedButWrongRowRejected() {
        val reader = { y: Int, row: IntArray ->
            row.fill(0xff30307a.toInt())
            if (y.toDouble() / 922 in .446..0.465) for (x in (2048 * .150).toInt()..(2048 * .174).toInt()) row[x] = 0xff3250eb.toInt()
        }
        for (dy in listOf(-.003, .003)) {
            assertTrue(
                ProComboScreens.proSelected(2048, 922, listOf(GameScreens.Word("Đề cử Pro", .176, .440 + dy, .235, .470 + dy)), reader)
            )
        }
        assertFalse(ProComboScreens.proSelected(2048, 922, listOf(GameScreens.Word("Đề cử Pro", .176, .500, .235, .530)), reader))
    }
}
