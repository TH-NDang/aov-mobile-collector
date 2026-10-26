package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/** Text on game screens, read on the phone by the bundled ML Kit. Run off the UI thread. */
object GameText {
    /** The lines of text on [screen], read at half size, with their boxes as fractions of the screen. */
    fun words(screen: Bitmap): List<GameScreens.Word> {
        val w = screen.width / 2
        val h = screen.height / 2
        val text = ocr(Bitmap.createScaledBitmap(screen, w, h, true)) ?: return emptyList()
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { b ->
                GameScreens.Word(line.text, b.left.toDouble() / w, b.top.toDouble() / h, b.right.toDouble() / w, b.bottom.toDouble() / h)
            }
        }
    }

    /**
     * Statistics navigation needs the small gold link at full resolution. Only retry an unresolved landing
     * page's header; large accessibility containers remain evidence of the page, never tap targets.
     */
    fun rankingWords(screen: Bitmap, nodes: List<GameScreens.Word> = emptyList()): List<GameScreens.Word> {
        val ordinary = nodes + words(screen)
        if (screen.width <= screen.height) return ordinary
        // Half-size whole-screen OCR can merge a menu row with a table number or split
        // "Đấu" from its mode. Read the small controls separately at native 2x scale.
        if (RankScreens.needsModeOcr(ordinary)) return RankScreens.withFocusedModes(ordinary, rankingModes(screen))
        return if (RankScreens.needsFullRankingOcr(ordinary)) ordinary + rankingHeader(screen) else ordinary
    }

    /** Mode button and all three menu rows, with line AND element boxes in screen coordinates. */
    fun rankingModes(screen: Bitmap) =
        rankingCrop(screen, RankScreens.MODE_LEFT, RankScreens.MODE_TOP, RankScreens.MODE_RIGHT, RankScreens.MODE_BOTTOM)

    /** Read the header at 2x native size and map both OCR lines and elements back into screen coordinates. */
    fun rankingHeader(screen: Bitmap) =
        rankingCrop(screen, RankScreens.LINK_LEFT, RankScreens.LINK_TOP, RankScreens.LINK_RIGHT, RankScreens.LINK_BOTTOM)

    /** Read the small receipt quantity and caption together, only on a verified reward popup. */
    fun eventInviteReward(screen: Bitmap): List<GameScreens.Word> = rankingCrop(screen, .37, .30, .64, .62, scale = 3)

    /**
     * Native-resolution recovery for the small "Hôm nay đã nhập 0/1 mã" text.
     * Called ONLY after whole-screen OCR has verified an "Hỗ trợ bạn bè" modal.
     * Crops the quota line rather than scanning player UID or the user's stored code.
     *
     * First try 3x enlarged colour pixels; if its ratio is unresolved, retry with
     * a high-contrast mask preserving both white label text and pink/yellow digits.
     */
    fun eventInviteQuota(screen: Bitmap): List<GameScreens.Word> {
        if (screen.width <= screen.height) return emptyList()
        val x0 = (screen.width * .375).toInt()
        val y0 = (screen.height * .497).toInt()
        val x1 = (screen.width * .670).toInt()
        val y1 = (screen.height * .612).toInt()
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0 || height <= 0) return emptyList()
        val crop = Bitmap.createBitmap(screen, x0, y0, width, height)

        fun read(source: Bitmap): List<GameScreens.Word> {
            val scale = 3
            val enlarged = Bitmap.createScaledBitmap(source, width * scale, height * scale, true)
            val text = ocr(enlarged) ?: return emptyList()

            fun located(value: String, box: android.graphics.Rect) = GameScreens.Word(
                value,
                (x0 + box.left.toDouble() / scale) / screen.width,
                (y0 + box.top.toDouble() / scale) / screen.height,
                (x0 + box.right.toDouble() / scale) / screen.width,
                (y0 + box.bottom.toDouble() / scale) / screen.height
            )
            return text.textBlocks.flatMap { it.lines }.flatMap { line ->
                buildList {
                    line.boundingBox?.let { add(located(line.text, it)) }
                    line.elements.forEach { element ->
                        element.boundingBox?.let { add(located(element.text, it)) }
                    }
                }
            }
        }

        return try {
            val normal = read(crop)
            if (EventInviteScreens.quota(normal) != null) {
                normal
            } else {
                // The purple modal and its white and orange/pink lettering have similar
                // luminance after resizing. Use hue as well as brightness: only
                // letter-shaped pixels that are light, white, or red-gold become ink.
                val pixels = IntArray(width * height)
                crop.getPixels(pixels, 0, width, 0, 0, width, height)
                val ink = IntArray(pixels.size) { i ->
                    val r = Color.red(pixels[i])
                    val g = Color.green(pixels[i])
                    val b = Color.blue(pixels[i])
                    val letter = r > 155 && (r - b >= 25 || (g > 120 && r * 100 >= b * 88))
                    if (letter) Color.BLACK else Color.WHITE
                }
                val contrasted = Bitmap.createBitmap(ink, width, height, Bitmap.Config.ARGB_8888)
                try {
                    val cleaned = read(contrasted)
                    // Do not let a spurious low-resolution first pass override a
                    // newly resolved quota. A conflict still fails closed in quota().
                    if (EventInviteScreens.quota(cleaned) != null) cleaned else normal + cleaned
                } finally {
                    contrasted.recycle()
                }
            }
        } finally {
            crop.recycle()
        }
    }

    /**
     * The native landscape keyboard shows a white extract editor with a separate
     * upper-right OK button. Read ONLY that right corner, never the editor containing
     * the invitation code, and never store the screenshot.
     */
    fun eventInviteKeyboardOk(screen: Bitmap): Pair<Double, Double>? {
        if (screen.width <= screen.height) return null
        val words = rankingCrop(screen, .775, .175, .987, .485)
        val buttons = words.filter {
            GameScreens.plain(it.text) == "ok" &&
                it.x in 0.77..0.985 &&
                it.y in 0.10..0.51
        }
        if (buttons.isEmpty()) return null
        val first = buttons.first()
        if (buttons.any { kotlin.math.abs(it.x - first.x) > .035 || kotlin.math.abs(it.y - first.y) > .045 }) {
            return null
        }
        return first.x to first.y
    }

    /** Reads the filled invitation text on the game popup AFTER the IME's OK closes. */
    fun eventInviteEnteredCode(screen: Bitmap, expected: String): Boolean {
        if (screen.width <= screen.height || expected.isBlank()) return false
        val codeOnly = rankingCrop(screen, .342, .406, .673, .518)
        val target = GameScreens.plain(expected)
        return codeOnly.any {
            it.x in 0.36..0.67 &&
                it.y in 0.40..0.53 &&
                GameScreens.plain(it.text) == target
        }
    }

    private fun rankingCrop(
        screen: Bitmap,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        scale: Int = 2
    ): List<GameScreens.Word> {
        if (screen.width <= screen.height) return emptyList()
        val x0 = (screen.width * left).toInt()
        val y0 = (screen.height * top).toInt()
        val width = (screen.width * (right - left)).toInt().coerceAtMost(screen.width - x0)
        val height = (screen.height * (bottom - top)).toInt().coerceAtMost(screen.height - y0)
        if (width < 1 || height < 1) return emptyList()
        val crop = Bitmap.createBitmap(screen, x0, y0, width, height)
        val enlarged = Bitmap.createScaledBitmap(crop, width * scale, height * scale, true)
        crop.recycle()
        val text = ocr(enlarged) ?: return emptyList()

        fun located(value: String, b: android.graphics.Rect) = GameScreens.Word(
            value,
            (x0 + b.left / scale.toDouble()) / screen.width,
            (y0 + b.top / scale.toDouble()) / screen.height,
            (x0 + b.right / scale.toDouble()) / screen.width,
            (y0 + b.bottom / scale.toDouble()) / screen.height
        )
        return text.textBlocks.flatMap { it.lines }.flatMap { line ->
            buildList {
                line.boundingBox?.let { add(located(line.text, it)) }
                line.elements.forEach { word -> word.boundingBox?.let { add(located(word.text, it)) } }
            }
        }
    }

    /**
     * Read ONLY the white hero title at the top of a Chess Encyclopedia detail drawer.
     * The large skill heading is below y=.38; cropping before OCR prevents mistaking
     * "Pháo đại" for "Violet" on the same popup. Called only on ambiguous full-screen OCR.
     */
    fun chessHeroTitle(screen: Bitmap): String? {
        if (screen.width <= screen.height) return null
        val x0 = (screen.width * .345).toInt()
        val y0 = (screen.height * .16).toInt()
        val width = (screen.width * .32).toInt().coerceAtMost(screen.width - x0)
        val height = (screen.height * .19).toInt().coerceAtMost(screen.height - y0)
        val crop = Bitmap.createBitmap(screen, x0, y0, width, height)
        val enlarged = Bitmap.createScaledBitmap(crop, width * 2, height * 2, true)
        crop.recycle()
        val text = ocr(enlarged) ?: return null
        val located = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            GameScreens.Word(
                line.text,
                (x0 + b.left / 2.0) / screen.width,
                (y0 + b.top / 2.0) / screen.height,
                (x0 + b.right / 2.0) / screen.width,
                (y0 + b.bottom / 2.0) / screen.height
            )
        }
        return ChessWiki.modalHeroTitle(located)
    }

    /** Non-hero popup heading, retried at native 2x scale only if full-screen OCR is ambiguous. */
    fun chessItemTitle(screen: Bitmap): String? {
        if (screen.width <= screen.height) return null
        val x0 = (screen.width * .345).toInt()
        val y0 = (screen.height * .14).toInt()
        val width = (screen.width * .47).toInt().coerceAtMost(screen.width - x0)
        val height = (screen.height * .18).toInt().coerceAtMost(screen.height - y0)
        val crop = Bitmap.createBitmap(screen, x0, y0, width, height)
        val enlarged = Bitmap.createScaledBitmap(crop, width * 2, height * 2, true)
        crop.recycle()
        val text = ocr(enlarged) ?: return null
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { b ->
                GameScreens.Word(
                    line.text,
                    (x0 + b.left / 2.0) / screen.width,
                    (y0 + b.top / 2.0) / screen.height,
                    (x0 + b.right / 2.0) / screen.width,
                    (y0 + b.bottom / 2.0) / screen.height
                )
            }
        }
        return ChessPopupTitle.select(lines) { ChessPopupTitle.whiteHeading(screen, it) }
    }

    /**
     * The account UID the game prints faintly at the bottom centre of most screens ("UID: 1751448330…"), or null.
     * The line is small and dim: it is read cut out, three times larger, as black on white.
     */
    fun uid(screen: Bitmap): String? {
        val x0 = (.30 * screen.width).toInt()
        val y0 = (.955 * screen.height).toInt()
        val w = (.40 * screen.width).toInt()
        val h = screen.height - y0
        val pixels = IntArray(w * h)
        screen.getPixels(pixels, 0, w, x0, y0, w, h)
        val luma = IntArray(pixels.size) {
            val c = pixels[it]
            (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000
        }
        val sorted = luma.sorted()
        val background = sorted[sorted.size / 2]
        val bright = sorted[
            (sorted.size * .995).toInt().coerceAtMost(
                sorted.size - 1
            )
        ]
        if (bright - background < 20) return null
        val threshold = (background + bright) / 2
        val ink = Bitmap.createBitmap(
            IntArray(pixels.size) {
                if (luma[it] >
                    threshold
                ) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            },
            w,
            h,
            Bitmap.Config.ARGB_8888
        )
        val large = Bitmap.createScaledBitmap(ink, w * 3, h * 3, true)
        ink.recycle()
        return ocr(large)?.text?.let { GameScreens.uidIn(it) }
    }

    /** Reads [image] and recycles it once ML Kit is done with it, which can be after a timeout here. */
    private fun ocr(image: Bitmap): Text? {
        val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val task = reader.process(InputImage.fromBitmap(image, 0))
            task.addOnCompleteListener {
                image.recycle()
                reader.close()
            }
            Tasks.await(task, 8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        }
    }
}
