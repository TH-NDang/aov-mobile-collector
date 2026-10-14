package vn.ndang.aovcollector

/**
 * Closes the game before a run opens it, so that it starts from its splash screen instead of wherever it was left
 * in the recent apps. Android lets no app end another's process, so this goes through Settings: the game's App
 * info page, Buộc dừng (Force stop), then OK in Android's question.
 *
 * Only on that page, which must name the game, and only those buttons, matched by their whole label: never Gỡ cài
 * đặt (uninstall) or Tắt (disable) beside them. A greyed Buộc dừng means the game is not running.
 */
class GameCloser(label:String,private val page:Page,private val finish:(Result)->Unit) {
    /** [fresh]: the game will start from its splash screen. */
    enum class Result(val text:String,val fresh:Boolean) {
        CLOSED("Đã buộc dừng Liên Quân để mở lại từ đầu",true),
        NOT_RUNNING("Liên Quân không chạy",true),
        NO_PAGE("Không mở được trang thông tin ứng dụng của Liên Quân; mở game như cũ",false),
        NO_BUTTON("Không thấy nút Buộc dừng của Liên Quân; mở game như cũ",false),
        NOT_CONFIRMED("Không thấy hộp hỏi Buộc dừng; mở game như cũ",false)
    }
    class Button(val text:String,val enabled:Boolean,val press:()->Boolean)
    /** The window in front, read through accessibility: all its words, and those that can be pressed. */
    class Window(val words:List<String>,val buttons:List<Button>)
    interface Page {
        val alive:Boolean
        /** Opens the game's App info page in Settings; false when Android refused. */
        fun open():Boolean
        /** The window in front, or null when there is none to read or it is the game's own. */
        fun read():Window?
        fun later(ms:Long,then:()->Unit)
    }
    companion object {
        const val POLL_MS=500L
        /** Reads while the page opens, while the question shows, and while the page greys the button. */
        const val PAGE_TRIES=16; const val QUESTION_TRIES=8; const val CHECK_TRIES=6
        /** Buộc dừng as phones word it, in Vietnamese and English, accents and spaces removed. */
        private val FORCE=setOf("buocdung","forcestop","dungcuongbuc","dungbatbuoc","buocdunglai")
        private val YES=FORCE+setOf("ok","dongy","xacnhan","dung","co")
        private val NO=setOf("huy","huybo","cancel","khong")
    }
    private val name=GameScreens.plain(label)
    private var ended=false

    fun start() { if(!page.open()) end(Result.NO_PAGE) else page.later(POLL_MS) { find(0) } }

    private fun end(result:Result) { if(!ended) { ended=true; finish(result) } }
    private fun plain(b:Button)=GameScreens.plain(b.text)
    private fun isPage(w:Window)=name.isNotEmpty()&&w.words.any { GameScreens.plain(it).contains(name) }
    private fun force(w:Window)=if(isPage(w)) w.buttons.firstOrNull { plain(it) in FORCE } else null
    private fun find(tries:Int) {
        if(!page.alive) return
        val w=page.read(); val button=w?.let { force(it) }
        when {
            button==null -> if(tries+1<PAGE_TRIES) page.later(POLL_MS) { find(tries+1) } else end(if(w!=null&&isPage(w)) Result.NO_BUTTON else Result.NO_PAGE)
            !button.enabled -> end(Result.NOT_RUNNING)
            !button.press() -> end(Result.NO_BUTTON)
            else -> page.later(POLL_MS) { confirm(0) }
        }
    }
    private fun confirm(tries:Int) {
        if(!page.alive) return
        val w=page.read()
        // Android's question names Buộc dừng and has a cancel button; the App info page has none.
        val question=w?.takeIf { it.buttons.any { b -> plain(b) in NO }&&it.words.any { t -> FORCE.any { f -> GameScreens.plain(t).contains(f) } } }
        val yes=question?.buttons?.firstOrNull { it.enabled&&plain(it) in YES }
        when {
            yes!=null -> if(yes.press()) page.later(POLL_MS) { check(0) } else end(Result.NOT_CONFIRMED)
            // Some phones ask nothing: the button greys at once.
            w!=null&&force(w)?.enabled==false -> end(Result.CLOSED)
            tries+1<QUESTION_TRIES -> page.later(POLL_MS) { confirm(tries+1) }
            else -> end(Result.NOT_CONFIRMED)
        }
    }
    /** Confirmed: the page greys Buộc dừng once the game has stopped, though not every phone does. */
    private fun check(tries:Int) {
        if(!page.alive) return
        val w=page.read()
        if(w!=null&&force(w)?.enabled==false||tries+1>=CHECK_TRIES) end(Result.CLOSED) else page.later(POLL_MS) { check(tries+1) }
    }
}
