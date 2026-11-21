package vn.ndang.aovcollector

/**
 * The hero page with its controls hidden, for photos of the hero: a tap on the hero's model opens it. The macro taps
 * empty space at .60, .82 to close a panel, and Thane's legs stand there (the user's run of 10/11): from his overview
 * on, every photo showed him alone, the skills were tapped where nothing was, and the run went on to the next hero.
 * The view keeps only the back arrow at the top left, which brings the page back, and a camera button at the right.
 *
 * Measured on that run's 332 hero photos: on Thane's 14 the page's title ("Chi tiết") lights no pixel and the camera
 * glyph 407 of its box's samples; on the 318 others the title lights 30 or more and the box 34 at most.
 */
object HeroView {
    private const val TITLE_LEFT = .12
    private const val TITLE_RIGHT = .21
    private const val TITLE_TOP = .025
    private const val TITLE_BOTTOM = .085
    private const val CAMERA_LEFT = .89
    private const val CAMERA_RIGHT = .915
    private const val CAMERA_TOP = .48
    private const val CAMERA_BOTTOM = .52

    /** Fewer lit pixels of the title than this, and more of the camera glyph than [CAMERA]: the hero alone. */
    private const val TITLE = 15
    private const val CAMERA = 150

    fun alone(width: Int, height: Int, pixels: (y: Int, row: IntArray) -> Unit): Boolean {
        val row = IntArray(width)
        var title = 0
        var camera = 0
        for (y in (TITLE_TOP * height).toInt() until (TITLE_BOTTOM * height).toInt() step 2) {
            pixels(y, row)
            for (x in (TITLE_LEFT * width).toInt() until (TITLE_RIGHT * width).toInt() step 2) {
                val p = row[x]
                // White or gold letters.
                if ((p shr 16 and 255) > 170 && (p shr 8 and 255) > 150 && (p and 255) > 90) title++
            }
        }
        if (title >= TITLE) return false
        for (y in (CAMERA_TOP * height).toInt() until (CAMERA_BOTTOM * height).toInt() step 2) {
            pixels(y, row)
            for (x in (CAMERA_LEFT * width).toInt() until (CAMERA_RIGHT * width).toInt() step 2) {
                val p = row[x]
                // The glyph's pale blue grey over its dark disc.
                if ((p shr 16 and 255) > 130 && (p shr 8 and 255) > 160 && (p and 255) > 190) camera++
            }
        }
        return camera >= CAMERA
    }
}
