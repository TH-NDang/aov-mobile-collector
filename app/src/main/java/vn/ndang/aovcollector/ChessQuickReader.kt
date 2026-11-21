package vn.ndang.aovcollector

import kotlin.math.abs
import kotlin.math.roundToInt
import vn.ndang.aovcollector.ChessWikiReader.Look
import vn.ndang.aovcollector.ChessWikiReader.Outcome

/**
 * The fast Bách khoa capture, started by [ChessWikiReader] once it has found its way in past the popups. It reads
 * pixels only ([ChessPixels]), never OCR, and names what it opens by its order on the page: quan-co-001,
 * vat-pham-004, phe-012. Android allows about one screenshot a second, so that is where the time goes: this takes
 * one per page and per drawer page, drags only while the bottom of the screen shows more, and saves the images while
 * it carries on.
 *
 * What is done is kept in the run's ledger under [PREFIX]: whole tabs, opened items and Save Photo images by their
 * number. A resumed tab is shot again from its first page.
 */
class ChessQuickReader(
    private val screen: ChessWikiReader.Screen,
    private val options: ChessWiki.Options,
    private val finish: (Outcome) -> Unit
) {
    companion object {
        const val PREFIX = "quick:"
        const val TAB_MS = 450L
        const val OPEN_MS = 450L

        /** After a drag, for the list to come to rest; screenshots come about a second apart anyway. */
        const val DRAG_MS = 500L
        const val CLOSE_MS = 400L
        const val HOLD_MS = 300L
        const val SURE_MS = 600L
        const val RELOOK_MS = 400L

        /** Between looks at a page still being drawn, and how many before it is saved as it is. */
        const val LOAD_MS = 600L
        const val MAX_LOAD_LOOKS = 8

        /** Looks at a drawer whose picture is not drawn yet before it is saved as it is. */
        const val MAX_ART_LOOKS = 5

        /** Between looks at the hero list after a drag, and how many before it is measured as it is. */
        const val STILL_MS = 400L
        const val MAX_STILL_LOOKS = 4

        /** How far the hero list is dragged up to see whether it goes on, and how often it is counted again. */
        const val END_DRAG = .35
        const val MAX_END_CHECKS = 2

        /**
         * A held drag moves the hero list no further than the finger, which goes [d] of the screen: measured 413 px
         * for a 432 px drag on the user's phone. Moves are looked for up to this much further, never at 650 px.
         */
        const val HELD_SLACK = .08
        const val MAX_PAGES = 65
        const val MAX_DRAWER_PAGES = 12
        const val MAX_ITEMS = 450
        const val MAX_CLOSES = 3
        const val MAX_STRAYS = 3

        fun topicKey(t: ChessWiki.Topic) = PREFIX + "topic:" + t.key

        fun itemKey(t: ChessWiki.Topic, number: Int) = PREFIX + "item:" + t.key + ":" + number

        fun nativeKey(group: String, item: String) = PREFIX + "native:" + group + ":" + item

        /** What an item is called in file names: its kind and its place in the tab, from 1. */
        fun itemName(t: ChessWiki.Topic, number: Int) = when (t.key) {
            "tat-ca-quan-co" -> "quan-co"
            "tat-ca-trang-bi" -> "vat-pham"
            "phe" -> "phe"
            "nghe" -> "nghe"
            else -> "chuc-phuc"
        } + "-%03d".format(number)
    }

    private val topics = options.topics()
    private var completed = false
    private var pages = 0
    private var items = 0
    private var natives = 0
    private var strays = 0

    /** The section whose tab bar is showing, so a second tab of it needs no tap on the side bar. */
    private var openSection: String? = null

    /** The last drawer page seen: the next drawer showing it again means the X did not close it. */
    private var lastDrawer: IntArray? = null
    private var ordinal = 0
    private val heroState = ListNavigator.State()
    private var heroAt: Pair<Double, Double>? = null
    private var listSignature: IntArray? = null

    /** Times the hero list was counted again from its top after a wrong end ([endOrNot]). */
    private var endChecks = 0

    fun start() = topic(0)

    private fun end(out: Outcome) {
        if (!completed) {
            completed = true
            finish(out)
        }
    }

    private fun fail(reason: String) = end(Outcome.Failed(reason))

    private val alive get() = screen.alive && !completed

    private fun look(words: Boolean = false, then: (Look) -> Unit) {
        if (!alive) return
        screen.look(words) { l ->
            when {
                !alive -> l?.discard()
                l == null -> fail("Không chụp được màn hình. Kiểm tra quyền Trợ năng và dung lượng, rồi bấm Tiếp tục.")
                else -> then(l)
            }
        }
    }

    private fun tap(x: Double, y: Double, wait: Long, id: String?, label: String, relative: Boolean = false, then: () -> Unit) {
        if (!alive) return
        val done = { ok: Boolean ->
            if (!ok) {
                fail("Không chạm được màn hình ($label); lý do ghi trong nhật ký lượt. Kiểm tra màn hình rồi bấm Tiếp tục.")
            } else {
                screen.later(wait) { if (alive) then() }
            }
        }
        if (id == null) {
            screen.tap(x, y, done)
        } else {
            screen.calibrated(CalibrationAction("chess.v1.$id", label, listOf(CalibrationPoint(x, y)), relative), done)
        }
    }

    private fun swipe(x: Double, y: Double, toX: Double, toY: Double, wait: Long, id: String, label: String, then: () -> Unit) {
        if (!alive) return
        val action = CalibrationAction(
            "chess.v1.$id",
            label,
            listOf(CalibrationPoint(x, y), CalibrationPoint(toX, toY)),
            durationMs = 350,
            holdMs = if (kotlin.math.abs(x - toX) < .003) 120 else 0
        )
        screen.calibrated(action) { ok -> if (!ok) fail("Không kéo được nội dung ($label).") else screen.later(wait) { if (alive) then() } }
    }

    /** Hands the screenshot over to be saved in the background; [then] goes on as soon as it is queued. */
    private fun keep(l: Look, name: String, then: () -> Unit) {
        l.keepQuick(name) { ok ->
            if (!alive) return@keepQuick
            if (!ok) {
                fail("Lưu ảnh thất bại: $name; kiểm tra dung lượng rồi bấm Tiếp tục.")
            } else {
                pages++
                then()
            }
        }
    }

    private fun pct(v: Double) = (v * 100).roundToInt()

    /** A Save Photo box nobody asked for, after a stray long press: CANCEL, never Sure. True when it was there. */
    private fun cancelStray(l: Look, again: () -> Unit): Boolean {
        val dialog = l.saveDialog ?: return false
        l.discard()
        if (++strays > MAX_STRAYS) {
            fail("Hộp Save Photo vẫn mở. Bấm CANCEL bằng tay rồi bấm Tiếp tục.")
        } else {
            screen.note("Hộp Save Photo đang mở ngoài dự kiến: bấm CANCEL, không lưu.")
            tap(dialog.cancel.first, dialog.cancel.second, CLOSE_MS, null, "Cờ: CANCEL (Save Photo)", then = again)
        }
        return true
    }

    private fun closeDrawer(then: () -> Unit) = tap(.902, .081, CLOSE_MS, "detail.close", "Cờ: đóng chi tiết", then = then)

    private fun topic(at: Int, reopened: Int = 0) {
        if (!alive) return
        if (at >= topics.size) return screen.flush { if (alive) end(Outcome.Done(pages, items)) }
        val t = topics[at]
        if (screen.seen(topicKey(t))) return topic(at + 1)
        screen.say("Bách khoa: ${t.side} → ${t.label}")
        ordinal = 0
        lastDrawer = null
        val begin = { if (heroWork(t)) heroes(at) else page(at, 1, null, false, null, reopened) }
        val tab = {
            when {
                t.tabX == 0.0 -> begin()
                t.sweepHeader -> swipe(.83, .18, .37, .18, 600, "topic.header", "Cờ: vuốt thanh mục") {
                    tap(t.tabX, .18, TAB_MS, "topic.${t.key}.tab", "Cờ: bảng ${t.label}", then = begin)
                }
                else -> tap(t.tabX, .18, TAB_MS, "topic.${t.key}.tab", "Cờ: bảng ${t.label}", then = begin)
            }
        }
        if (openSection == t.section && reopened == 0) {
            tab()
        } else {
            tap(.129, t.sidebarY, TAB_MS, "topic.${t.key}.sidebar", "Cờ: mục ${t.side}", then = tab)
        }
    }

    private fun topicDone(at: Int) {
        val t = topics[at]
        screen.finished(topicKey(t))
        openSection = t.section
        topic(at + 1)
    }

    /** Tất cả quân cờ is walked card by card only when its cards are to be opened or saved; otherwise it is a page. */
    private fun heroWork(t: ChessWiki.Topic) = t.key == "tat-ca-quan-co" &&
        (heroDrawers || "quan-co" in options.nativeGroups)

    private val heroDrawers get() = options.heroDetails || "chieu-quan-co" in options.nativeGroups

    /**
     * Page [n] of a tab: save it, then do its items, then drag on unless its bottom is plain background. A drag that
     * changed nothing is the end, except a tab's first drag, which is tried twice: a tab still settling can lose it.
     *
     * A page is saved once it is drawn. The game draws a tab's pictures a moment after the tab opens, so a first page
     * is saved when two looks in a row match; on Trang bị, no item may still be its loading square. [before]: the look
     * before this one at the same page while waiting, [waits] so far; after [MAX_LOAD_LOOKS] it is saved as it is.
     */
    private fun page(
        at: Int,
        n: Int,
        previous: IntArray?,
        retried: Boolean,
        shown: Shown?,
        reopened: Int,
        closes: Int = 0,
        before: IntArray? = null,
        waits: Int = 0
    ): Unit = look { l ->
        val t = topics[at]
        if (cancelStray(l) { page(at, n, previous, retried, shown, reopened, closes) }) return@look
        if (l.modal) {
            l.discard()
            if (closes >= MAX_CLOSES) return@look fail("Không đóng được bảng chi tiết ở ${t.label}. Đóng dấu X rồi bấm Tiếp tục.")
            // A drawer left open over a new tab also kept the tab from opening: open it again once closed.
            return@look closeDrawer {
                if (n == 1 && reopened < 2) topic(at, reopened + 1) else page(at, n, previous, retried, shown, reopened, closes + 1)
            }
        }
        if (previous != null && !ChessWiki.changed(previous, l.signature)) {
            l.discard()
            if (n == 2 && !retried) {
                screen.note("${t.label}: lần kéo đầu không làm trang đổi; kéo lại một lần")
                return@look drag(t, 1) { page(at, n, previous, true, shown, reopened) }
            }
            return@look topicDone(at)
        }
        if (n > MAX_PAGES) {
            l.discard()
            return@look fail(
                "Đã tới giới hạn $MAX_PAGES trang ở ${t.label}. Ảnh đã lưu được giữ lại; kiểm tra danh sách rồi bấm Tiếp tục."
            )
        }
        val loading = if (t.key == "tat-ca-trang-bi") l.loadingTiles() else 0
        val drawing = n == 1 && (before == null || ChessWiki.changed(before, l.signature))
        if ((loading > 0 || drawing) && waits < MAX_LOAD_LOOKS) {
            val seen = l.signature.copyOf()
            l.discard()
            if (loading > 0 && waits == 0) screen.note("${t.label}: còn $loading hình trang bị chưa tải; chờ rồi mới chụp.")
            screen.say("${t.label}: chờ tải xong")
            return@look screen.later(LOAD_MS) { page(at, n, previous, retried, shown, reopened, closes, seen, waits + 1) }
        }
        when {
            loading > 0 -> screen.note("${t.label}: còn $loading hình chưa tải sau $waits lần chờ; chụp như đang thấy.")
            drawing -> screen.note("${t.label}: màn hình vẫn đổi sau $waits lần chờ; chụp như đang thấy.")
        }
        screen.say("${t.label}: trang $n")
        val signature = l.signature.copyOf()
        // The cards of Tất cả quân cờ are plain below their names, and from three gold up they are nearly the
        // background's colour: a bottom strip there can be plain with rows still to come. Its end is the drag
        // that no longer moves it.
        val blank = l.blankBelow && t.key != "tat-ca-quan-co"
        // Read from the screenshot before it is handed over to be saved.
        val group = ChessWiki.nativeGroup(t, options)
        val kind = ChessWiki.detailType(t, options)
        val blessed = group != null && t.section == "chuc-phuc"
        val blessings = if (blessed) l.blessingIcons() else emptyList()
        val listProfile = if (blessed) l.blessingProfile() else floatArrayOf()
        // A card the page before showed is told by where the drag took it: one cut by the tabs shows only part of its
        // picture, and the user's run saved Thường's 9th, 10th and 22nd blessings twice that way.
        val moved = shown?.let { ChessPixels.blessingMoved(it.profile, listProfile, l.height) }
        if (shown != null && moved == null && blessed) screen.note("${t.label}: không đo được độ cuộn trang $n; so thẻ đã lưu theo hình")
        val fresh = blessings.filter { tile -> shown?.tiles.orEmpty().none { ChessPixels.sameBlessing(it, tile, moved) } }
        val tiles = when {
            t.section == "chuc-phuc" -> fresh
            t.key == "tat-ca-quan-co" -> emptyList()
            kind != null || group != null -> l.tiles(t)
            else -> emptyList()
        }
        keep(l, ChessWiki.filename(t, "trang", n)) {
            items(at, tiles, 0, signature) {
                if (blank) topicDone(at) else drag(t, 0) { page(at, n + 1, signature, retried, Shown(blessings, listProfile), reopened) }
            }
        }
    }

    /** A Chúc phúc page's cards and its list's profile, so the next page knows the cards it shows again. */
    private class Shown(val tiles: List<ChessWiki.Tile>, val profile: FloatArray)

    /** Drags a tab's content up by .6 of the screen: an overlap of about a sixth stays between pages. */
    private fun drag(t: ChessWiki.Topic, retry: Int, then: () -> Unit) =
        swipe(.76, .90, .76, .30, DRAG_MS, "topic.${t.key}.scroll.$retry", "Cờ: cuộn ${t.label}", then)

    /** The items of one page in order: each one's Save Photo image, then its drawer, as the options ask. */
    private fun items(at: Int, tiles: List<ChessWiki.Tile>, i: Int, pageSignature: IntArray, then: () -> Unit) {
        if (!alive) return
        if (i >= tiles.size) return then()
        if (items >= MAX_ITEMS) return fail("Đã tới giới hạn $MAX_ITEMS mục; chưa xác nhận cuối danh sách.")
        val t = topics[at]
        val tile = tiles[i]
        val number = ++ordinal
        val name = itemName(t, number)
        val group = ChessWiki.nativeGroup(t, options)
        val kind = ChessWiki.detailType(t, options)
        val next = { items(at, tiles, i + 1, pageSignature, then) }
        val open = { first: Look? ->
            when {
                kind != null && !screen.seen(itemKey(t, number)) -> {
                    screen.say("${t.label}: mở $number")
                    drawer(at, kind, name, itemKey(t, number), tile.x, tile.y, first, false, {
                        screen.note("$name: chạm ${pct(tile.x)}%, ${pct(tile.y)}% không mở bảng chi tiết; bỏ qua.")
                        next()
                    }, next)
                }
                first != null -> {
                    first.discard()
                    closeDrawer(next)
                }
                else -> next()
            }
        }
        if (group != null && !screen.seen(nativeKey(group, name))) {
            screen.say("${t.label}: lưu ảnh gốc $number")
            nativeSave(group, name, tile.x, tile.y, pageSignature, false, open)
        } else {
            open(null)
        }
    }

    /**
     * Long-presses (x, y) for the game's Save Photo and confirms it with Sure: the box is found in pixels, or, when the
     * screen changed but no box was seen there, read with OCR. [then] gets the drawer when the press opened one.
     */
    private fun nativeSave(group: String, item: String, x: Double, y: Double, before: IntArray, inDrawer: Boolean, then: (Look?) -> Unit) {
        screen.longPress(x, y) { pressed ->
            if (!alive) return@longPress
            if (!pressed) return@longPress fail("Không nhấn giữ được $item để lưu ảnh gốc; kiểm tra màn hình rồi bấm Tiếp tục.")
            screen.later(HOLD_MS) { pressed(group, item, x, y, before, inDrawer, 0, then) }
        }
    }

    /** What the long press brought up: [tries] looks so far, as a box slow to appear is looked for once more. */
    private fun pressed(
        group: String,
        item: String,
        x: Double,
        y: Double,
        before: IntArray,
        inDrawer: Boolean,
        tries: Int,
        then: (Look?) -> Unit
    ): Unit = look { l ->
        val dialog = l.saveDialog
        when {
            dialog != null -> {
                l.discard()
                confirm(group, item, dialog.sure) { then(null) }
            }
            l.modal && !inDrawer -> {
                screen.note("Nhấn giữ $item mở bảng chi tiết thay vì Save Photo; không có ảnh gốc ở đây.")
                then(l)
            }
            !ChessWiki.changed(before, l.signature) && tries < 1 -> {
                l.discard()
                screen.later(RELOOK_MS) { pressed(group, item, x, y, before, inDrawer, tries + 1, then) }
            }
            !ChessWiki.changed(before, l.signature) -> {
                l.discard()
                screen.note("Không có Save Photo khi nhấn giữ $item ở ${pct(x)}%, ${pct(y)}%; bỏ qua ảnh gốc này.")
                then(null)
            }
            // Something changed, but not into the box as measured: read it before pressing anything.
            else -> {
                l.discard()
                look(true) { w ->
                    val sure = ChessWiki.nativeConfirm(w.words)
                    val cancel = ChessWiki.nativeCancel(w.words)
                    w.discard()
                    when {
                        sure != null -> confirm(group, item, sure) { then(null) }
                        cancel != null -> {
                            screen.note("Save Photo thiếu nút Sure khi lưu $item: bấm CANCEL, không lưu.")
                            tap(cancel.first, cancel.second, CLOSE_MS, null, "Cờ: CANCEL (Save Photo)") { then(null) }
                        }
                        else -> {
                            screen.note("Nhấn giữ $item làm màn hình đổi nhưng không thấy Save Photo; bỏ qua ảnh gốc này.")
                            then(null)
                        }
                    }
                }
            }
        }
    }

    private fun confirm(group: String, item: String, sure: Pair<Double, Double>, then: () -> Unit) {
        tap(sure.first, sure.second, SURE_MS, null, "Cờ: Sure (Save Photo)") {
            screen.markNative(group, item)
            screen.finished(nativeKey(group, item))
            natives++
            screen.note("Ảnh gốc $group: $item")
            then()
        }
    }

    /**
     * Opens a drawer by tapping (x, y), unless [first] already shows it, and saves its pages. A drawer is saved once
     * its picture is drawn, after up to [MAX_ART_LOOKS] more looks. [missed]: the tap opened nothing.
     */
    private fun drawer(
        at: Int,
        kind: String,
        name: String,
        key: String,
        x: Double,
        y: Double,
        first: Look?,
        hero: Boolean,
        missed: () -> Unit,
        then: () -> Unit
    ) {
        val t = topics[at]

        fun opened(l: Look, waits: Int = 0, retaps: Int = 0) {
            if (cancelStray(l) { look { opened(it, waits, retaps) } }) return
            val previous = lastDrawer
            when {
                !l.modal && waits < 1 -> {
                    l.discard()
                    screen.later(RELOOK_MS) { look { opened(it, waits + 1, retaps) } }
                }
                !l.modal -> {
                    l.discard()
                    missed()
                }
                // Showing the last drawer again: its X did not close it, and this tap landed on it.
                previous != null && !ChessWiki.changed(previous, l.signature) && retaps < 1 -> {
                    l.discard()
                    screen.note("$name: bảng chi tiết trước vẫn mở; đóng rồi mở lại.")
                    closeDrawer {
                        tap(x, y, OPEN_MS, "topic.${t.key}.card", "Cờ: mở thẻ ${t.label}", relative = true) {
                            look {
                                opened(
                                    it,
                                    0,
                                    retaps + 1
                                )
                            }
                        }
                    }
                }
                (!l.drawerArt || (hero && !l.skillArt)) && waits < MAX_ART_LOOKS -> {
                    l.discard()
                    screen.later(RELOOK_MS) { look { opened(it, waits + 1, retaps) } }
                }
                else -> {
                    if (!l.drawerArt || (hero && !l.skillArt)) {
                        screen.note("$name: hình trong bảng chi tiết chưa hiện sau $waits lần chờ; chụp như đang thấy.")
                    }
                    drawerPage(at, kind, name, key, l, 1, null, hero, then)
                }
            }
        }
        if (first != null) {
            opened(first)
        } else {
            tap(x, y, OPEN_MS, "topic.${t.key}.card", "Cờ: mở thẻ ${t.label}", relative = true) { look { opened(it) } }
        }
    }

    private fun drawerPage(
        at: Int,
        kind: String,
        name: String,
        key: String,
        l: Look,
        n: Int,
        previous: IntArray?,
        hero: Boolean,
        then: () -> Unit
    ) {
        val t = topics[at]
        val done = {
            screen.finished(key)
            items++
            then()
        }
        if (previous != null) {
            if (cancelStray(l) { look { drawerPage(at, kind, name, key, it, n, previous, hero, then) } }) return
            if (!l.modal) {
                l.discard()
                screen.note("$name: bảng chi tiết đã đóng sau ảnh ${n - 1}.")
                return done()
            }
            if (!ChessWiki.changed(previous, l.signature)) {
                l.discard()
                return closeDrawer(done)
            }
        }
        if (n > MAX_DRAWER_PAGES) {
            l.discard()
            screen.note("$name: bảng chi tiết vẫn cuộn sau $MAX_DRAWER_PAGES ảnh; dừng ở đây.")
            return closeDrawer(done)
        }
        val signature = l.signature.copyOf()
        lastDrawer = signature
        // Only the first page is dragged on for a hero opened just for its skill picture.
        val more = !l.blankBelow && (!hero || options.heroDetails)
        val skill = if (hero && n == 1 && "chieu-quan-co" in options.nativeGroups) l.skillIcons().firstOrNull() else null
        keep(l, ChessWiki.filename(t, kind, n, name)) {
            val goOn = {
                if (!more) {
                    closeDrawer(done)
                } else {
                    swipe(.75, .86, .75, .30, DRAG_MS, "detail.description", "Cờ: cuộn mô tả") {
                        look { drawerPage(at, kind, name, key, it, n + 1, signature, hero, then) }
                    }
                }
            }
            val item = "$name-skill-1"
            if (skill != null && !screen.seen(nativeKey("chieu-quan-co", item))) {
                screen.say("Quân cờ: lưu ảnh chiêu ${name.removePrefix("quan-co-")}")
                nativeSave("chieu-quan-co", item, skill.x, skill.y, signature, true) { goOn() }
            } else {
                goOn()
            }
        }
    }

    /** Tất cả quân cờ card by card, each found and measured by [ListNavigator] on pixels alone. */
    private fun heroes(at: Int) {
        heroState.synced = false
        heroState.ref = null
        endChecks = 0
        hero(at, 0, 0)
    }

    private fun hero(at: Int, index: Int, retries: Int) {
        if (!alive) return
        if (index >= MAX_ITEMS) return fail("Đã tới giới hạn $MAX_ITEMS quân cờ; chưa xác nhận cuối danh sách.")
        val t = topics[at]
        val number = index + 1
        val name = itemName(t, number)
        val head = "$name-head"
        val wantsNative = "quan-co" in options.nativeGroups && !screen.seen(nativeKey("quan-co", head))
        val wantsDrawer = heroDrawers && !screen.seen(itemKey(t, number))
        // Done in an earlier attempt: no need to walk to it. The list's end is still found by walking.
        if (!wantsNative && !wantsDrawer) return hero(at, index + 1, 0)
        screen.say("Quân cờ: ô $number · đã mở $items · ảnh gốc $natives")
        heroAt = null
        ListNavigator(index, heroState, heroScreen(at), null) { outcome ->
            when (outcome) {
                ListNavigator.Outcome.End -> endOrNot(at, index, retries)
                is ListNavigator.Outcome.Failed -> fail(
                    "Quân cờ, ô $number: " + outcome.reason.replace("danh sách Tất cả tướng", "danh sách Tất cả quân cờ")
                        .replace("danh sách tướng", "danh sách quân cờ")
                )
                ListNavigator.Outcome.Missing -> hero(at, index + 1, 0)
                ListNavigator.Outcome.Tapped -> {
                    val (x, y) = heroAt ?: return@ListNavigator fail("Không xác định được ô quân cờ $number.")
                    val nextHero = { hero(at, index + 1, 0) }
                    val afterNative = { first: Look? ->
                        when {
                            wantsDrawer -> drawer(at, "tuong", name, itemKey(t, number), x, y, first, true, {
                                if (retries < 1) {
                                    screen.note("$name: chạm thẻ không mở bảng chi tiết; tìm lại thẻ một lần.")
                                    heroState.synced = false
                                    heroState.ref = null
                                    hero(at, index, retries + 1)
                                } else {
                                    fail("Không mở được ô quân cờ $number; chưa đánh dấu hoàn tất. Kiểm tra danh sách rồi bấm Tiếp tục.")
                                }
                            }, nextHero)
                            first != null -> {
                                first.discard()
                                closeDrawer(nextHero)
                            }
                            else -> nextHero()
                        }
                    }
                    if (wantsNative) {
                        nativeSave("quan-co", head, x, y, listSignature ?: IntArray(0), false, afterNative)
                    } else {
                        afterNative(null)
                    }
                }
            }
        }.start()
    }

    /**
     * No card where card [index] goes. That is the end of the list unless a drag up moves it most of the way and
     * shows cards in the lower third of the screen. Otherwise the count of how far the list is scrolled went wrong, or a
     * portrait was not drawn yet: the list is counted again from its top, at most [MAX_END_CHECKS] times. The user's
     * list stopped this way after the three-gold row, with the next row's cards on screen.
     */
    private fun endOrNot(at: Int, index: Int, retries: Int) {
        val number = index + 1
        look { first ->
            val before = first.heroProfile
            first.discard()
            if (before.isEmpty()) {
                screen.note("Quân cờ: không thấy ô $number và màn hình không còn là danh sách; sang tab sau.")
                return@look topicDone(at)
            }
            screen.dragHeroList(END_DRAG, false) { ok ->
                if (!ok) return@dragHeroList fail("Không vuốt được danh sách quân cờ; kiểm tra màn hình rồi bấm Tiếp tục.")
                screen.later(DRAG_MS) {
                    if (!alive) return@later
                    restingList { l ->
                        val after = l.heroProfile
                        val reach = (-(.03 * l.height).toInt())..((END_DRAG + HELD_SLACK) * l.height).toInt()
                        val shift = if (after.isEmpty()) null else HeroGrid.shift(before, after, reach)
                        // How far the drag took the list, in screen heights; a move too far to measure counts as
                        // the whole drag. Near its end the list moves only the little it has left.
                        val moved = if (shift == null || !shift.reliable) END_DRAG else shift.px.toDouble() / l.height
                        val goesOn = after.isNotEmpty() && moved > END_DRAG * .7 && l.heroCardsBelow
                        l.discard()
                        when {
                            !goesOn -> {
                                screen.note("Quân cờ: hết danh sách sau ô $index.")
                                topicDone(at)
                            }
                            endChecks >= MAX_END_CHECKS -> {
                                screen.note(
                                    "Quân cờ: vẫn không thấy ô $number sau $MAX_END_CHECKS lần đếm lại dù danh sách còn thẻ; sang tab sau."
                                )
                                topicDone(at)
                            }
                            else -> {
                                endChecks++
                                screen.note(
                                    "Quân cờ: không thấy ô $number nhưng danh sách còn thẻ bên dưới; đếm lại từ đầu danh sách (lần $endChecks)."
                                )
                                heroState.synced = false
                                heroState.ref = null
                                hero(at, index, retries)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The hero list once it has come to rest after a drag: looked at again until two looks in a row show it in the
     * same place. A game busy loading the next row's pictures can show a drag late, and a list measured before it
     * moved puts every later card in the wrong place. A screen that is not the list is handed on as it is.
     */
    private fun restingList(previous: FloatArray? = null, looks: Int = 0, then: (Look) -> Unit) {
        look { l ->
            val profile = l.heroProfile
            if (profile.isEmpty()) return@look then(l)
            val shift = previous?.let { HeroGrid.shift(it, profile) }
            when {
                shift != null && shift.reliable && abs(shift.px) <= 2 -> then(l)
                looks >= MAX_STILL_LOOKS -> {
                    screen.note("Quân cờ: danh sách chưa đứng yên sau $looks lần nhìn; đo ở chỗ đang thấy.")
                    then(l)
                }
                else -> {
                    l.discard()
                    screen.later(STILL_MS) { restingList(profile, looks + 1, then) }
                }
            }
        }
    }

    private fun heroScreen(at: Int) = object : ListNavigator.Screen {
        override val alive get() = this@ChessQuickReader.alive
        override val layout = ChessWiki.HERO_LAYOUT

        /** A slow drag went before this look: the list is measured once it has come to rest. */
        private var dragged = false

        override fun look(then: (ListNavigator.Shot?) -> Unit) {
            if (dragged) {
                dragged = false
                restingList { then(HeroShot(at, it)) }
            } else {
                this@ChessQuickReader.look { then(HeroShot(at, it)) }
            }
        }

        override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
            dragged = !fling
            screen.dragHeroList(d, fling, then)
        }

        override fun heldReach(d: Double) = abs(d) + HELD_SLACK

        // The card is pressed or tapped once its Save Photo and drawer are decided, not by the navigator.
        override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
            heroAt = x to y
            then(true)
        }

        override fun back(then: (Boolean) -> Unit) = screen.calibrated(
            CalibrationAction("chess.v1.detail.close", "Cờ: đóng chi tiết", listOf(CalibrationPoint(.902, .081))),
            then
        )

        override fun later(ms: Long, then: () -> Unit) = screen.later(ms, then)

        override fun note(text: String) = screen.note("Quân cờ: $text")
    }

    /** A screenshot of the list for [ListNavigator]; the pages it asks to keep are the list's overview images. */
    private inner class HeroShot(private val at: Int, private val look: Look) : ListNavigator.Shot {
        private var released = false
        override val profile = look.heroProfile
        override val height = look.height
        override val backArrow = look.modal

        override fun hasCard(x: Double, y: Double) = look.hasHeroCard(x, y)

        override fun done(keep: String?) {
            if (released) return
            released = true
            if (profile.isNotEmpty()) listSignature = look.signature.copyOf()
            if (keep != null && keep.startsWith("page-")) {
                this@ChessQuickReader.keep(look, ChessWiki.filename(topics[at], "trang", heroState.page)) {}
            } else {
                look.discard()
            }
        }
    }
}
