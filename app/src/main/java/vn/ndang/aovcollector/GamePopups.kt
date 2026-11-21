package vn.ndang.aovcollector

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Guide and gift popups change with every game update but leave in the same few ways: a line telling you to tap
 * the screen, an X, or a button. This names those ways out from the words on screen. [tapToLeave] and [updateNotes]
 * are shown only by popups, so they may be acted on at once. The labels of [button] also occur on other screens:
 * it is for a screen that has already stayed put.
 */
object GamePopups {
    /** A way out of a popup: where to tap, and how to say so in the run's log. */
    class Exit(val at: Pair<Double, Double>, val what: String)

    /** "Ghi chú cập nhật", which the chess lobby opens after a game update: X on the title's row, measured on 2000x900. */
    private const val UPDATE_NOTES_X = .719

    fun updateNotes(words: List<GameScreens.Word>) = words.firstOrNull { it.y < .40 && reads(it.plain, "ghichucapnhat") }

    /** The X found in the pixels when it is on the title's row, which a dimmed icon further out is not. */
    fun updateNotesClose(title: GameScreens.Word, found: Pair<Double, Double>?): Pair<Double, Double> =
        found?.takeIf { it.first > title.right && abs(it.second - title.y) < .05 } ?: (UPDATE_NOTES_X to title.y)

    private val TAP_TO_CLOSE = Regex("(an|nhan|cham|bam)[a-z0-9]*de(dong|thoat|quaylai|boqua)")

    /** Longer lines are sentences of a guide, not the short instruction under a reward. */
    private const val TAP_LINE_MAX = 40

    /**
     * "Ấn để tiếp tục", "Chạm vào vùng trống để đóng": the game says a tap leaves this screen, and the line itself
     * lies outside any button. "Nhấn lần nữa để thoát" is about leaving the game, so it does not count.
     */
    fun tapToLeave(words: List<GameScreens.Word>) = words.firstOrNull { it.y > .60 && it.x in .2..0.8 && saysATapLeaves(it.plain) }

    private fun saysATapLeaves(line: String) = line.length <= TAP_LINE_MAX &&
        !line.contains("lannua") &&
        (reads(line, "detieptuc") || TAP_TO_CLOSE.containsMatchIn(line))

    /**
     * Whether [phrase] is somewhere in [line], with OCR allowed one letter wrong, lost or added: on the user's reward
     * screen ML Kit read "* Ấn để iếp tục". "Tiếp tục" alone, two letters short of "để tiếp tục", stays a button.
     */
    private fun reads(line: String, phrase: String): Boolean {
        // Edit distance from the phrase to the stretch of the line that fits it best.
        var previous = IntArray(line.length + 1)
        for (i in 1..phrase.length) {
            val current = IntArray(line.length + 1)
            current[0] = i
            for (j in 1..line.length) {
                val replace = previous[j - 1] + if (phrase[i - 1] == line[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, replace)
            }
            previous = current
        }
        return previous.min() <= 1
    }

    private val ACKNOWLEDGE = setOf("daro", "dahieu", "dabiet", "toidahieu", "toidabiet", "toibietroi", "bietroi")
    private val CLAIM = setOf("nhan", "nhanthuong", "nhanqua", "nhanngay", "nhantatca")
    private val LEAVE = setOf("dong", "boqua", "desau", "lansau", "huy", "huybo")
    private val CONFIRM = setOf("xacnhan", "tieptuc", "ok", "hoantat")

    /** Words of a dialog that spends or changes something: its confirm button is left for the player. */
    private val COMMITS = listOf("quanhuy", "thanhtoan", "naptien", "napthe", "dangxuat", "doitaikhoan", "thoat", "xoa", "dongy")

    /** Checked as whole words with their accents, because without them "mua" is also "Mùa", the season. */
    private val COMMIT_WORDS = setOf("mua", "nạp", "đổi", "tiêu")
    private val NOT_LETTERS = Regex("[^\\p{L}]+")

    private fun commits(words: List<GameScreens.Word>): Boolean {
        val full = GameScreens.plain(words.joinToString(" ") { it.text })
        return COMMITS.any { it in full } || words.any { hasCommitWord(it.text) }
    }

    private fun hasCommitWord(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFC)
        .split(NOT_LETTERS)
        .any { it in COMMIT_WORDS }

    /**
     * The button that leaves a guide or gift box, or null. The whole line must be the label, in the middle of the
     * screen, so a sentence that mentions it never counts. Buttons that can only acknowledge, claim or back out come
     * first. A confirming button is pressed only as the one choice of a dialog with no commitment in its words: a
     * purchase offers a way to refuse, which is taken instead, and "Đồng ý" is never pressed.
     */
    fun button(words: List<GameScreens.Word>): GameScreens.Word? {
        val shown = words.filter { it.x in .12..0.88 && it.y in .30..0.97 }
        for (labels in listOf(ACKNOWLEDGE, CLAIM, LEAVE)) shown.firstOrNull { it.plain in labels }?.let { return it }
        return shown.singleOrNull { it.plain in CONFIRM }?.takeUnless { commits(words) }
    }
}
