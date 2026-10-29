package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cờ Liên Quân encyclopedia collector. Navigation/capture decisions are in this class, not in the
 * AccessibilityService, so the real controller and scripted tests use the same state machine.
 * This only enters the chess lobby/encyclopedia; it NEVER taps matchmaking buttons.
 */
object ChessWiki {
    data class Topic(
        val key: String,
        val section: String,
        val label: String,
        val tabX: Double,
        val sidebarY: Double,
        val sweepHeader: Boolean = false
    ) {
        val side get() = when (section) {
            "thong-tin" -> "Thông tin cơ bản"
            "quan-co" -> "Quân cờ"
            "tuong-tac" -> "Tương tác"
            "trang-bi" -> "Trang bị"
            "nhiem-vu" -> "Nhiệm vụ"
            else -> "Chúc Phúc"
        }
    }

    val TOPICS = listOf(
        Topic("quy-trinh", "thong-tin", "Quy trình cơ bản", .37, .20),
        Topic("thang-thua-vong", "thong-tin", "Thắng và thua vòng đấu", .68, .20),
        Topic("thang-thua-tran", "thong-tin", "Thắng và thua trận", .78, .20, true),
        Topic("tat-ca-quan-co", "quan-co", "Tất cả quân cờ", .30, .305),
        Topic("phe-nghe", "quan-co", "Phe & Nghề", .47, .305),
        Topic("phe", "tuong-tac", "Phe", .0, .405),
        Topic("nghe", "tuong-tac", "Nghề", .0, .405),
        Topic("tat-ca-trang-bi", "trang-bi", "Tất cả trang bị", .0, .505),
        Topic("danh-sach-nhiem-vu", "nhiem-vu", "Danh sách nhiệm vụ", .0, .61),
        Topic("thuong", "chuc-phuc", "Thường", .30, .715),
        Topic("huyen-thoai-su-thi", "chuc-phuc", "Huyền thoại / Sử thi", .46, .715),
        Topic("truyen-thuyet", "chuc-phuc", "Truyền thuyết", .65, .715)
    )

    data class Options(
        val selected: Set<String>,
        val openGame: Boolean = true,
        val checkAccount: Boolean = false,
        val traceEntry: Boolean = true,
        val heroDetails: Boolean = false,
        val synergyDetails: Boolean = false,
        val equipmentDetails: Boolean = false,
        val nativeGroups: Set<String> = emptySet(),
        val fast: Boolean = true
    ) {
        fun topics() = TOPICS.filter { it.key in selected }

        init {
            require(selected.isNotEmpty() && selected.all { key -> TOPICS.any { it.key == key } })
            require(nativeGroups.all { group -> GamePhotoImport.GROUPS.any { it.key == group && group != "chua-phan-loai" } })
        }
    }

    /** Same tap/tab waits as the hero collector's fast mode; screenshot rate limits still apply. */
    class Timing(fast: Boolean) {
        val tap = if (fast) SkillReader.FAST_SETTLE_MS else 1250L
        val tab = if (fast) SkillReader.FAST_TAB_WAIT_MS else 1000L
        val scroll = if (fast) SkillReader.FAST_TAB_WAIT_MS else 1100L
        val close = if (fast) SkillReader.FAST_SETTLE_MS else 900L
        val move = if (fast) 350L else 700L
        val hold = if (fast) 120L else 400L
        val modalStill = if (fast) 1 else 2
    }

    fun detailType(t: Topic, o: Options) = when (t.key) {
        "tat-ca-quan-co" -> if (o.heroDetails || "chieu-quan-co" in o.nativeGroups || "quan-co" in o.nativeGroups) "tuong" else null
        "phe", "nghe" -> if (o.synergyDetails) t.key else null
        "tat-ca-trang-bi" -> if (o.equipmentDetails) "vat-pham" else null
        else -> null
    }

    /** The currently selected Bách khoa tab determines each native image's class automatically. */
    fun nativeGroup(t: Topic, o: Options): String? {
        val g = when (t.key) {
            "tat-ca-quan-co" -> "quan-co"
            "phe" -> "phe"
            "nghe" -> "nghe"
            "tat-ca-trang-bi" -> "trang-bi"
            "thuong" -> "chuc-phuc-thuong"
            "huyen-thoai-su-thi" -> "chuc-phuc-huyen-thoai-su-thi"
            "truyen-thuyet" -> "chuc-phuc-truyen-thuyet"
            else -> null
        }
        return g?.takeIf { it in o.nativeGroups }
    }

    fun topicsForNative(groups: Set<String>): Set<String> = groups.mapNotNull {
        when (it) {
            "quan-co", "chieu-quan-co" -> "tat-ca-quan-co"
            "phe" -> "phe"
            "nghe" -> "nghe"
            "trang-bi" -> "tat-ca-trang-bi"
            "chuc-phuc-thuong" -> "thuong"
            "chuc-phuc-huyen-thoai-su-thi" -> "huyen-thoai-su-thi"
            "chuc-phuc-truyen-thuyet" -> "truyen-thuyet"
            else -> null
        }
    }.toSet()

    /** Only the exact Save Photo dialog may be confirmed, never a generic YES/OK in game. */
    fun nativeConfirm(words: List<GameScreens.Word>): Pair<Double, Double>? {
        val full = GameScreens.plain(words.joinToString(" ") { it.text })
        if (!full.contains("savephoto") || !full.contains("sure") || !full.contains("cancel")) return null
        val sure = words.firstOrNull { it.plain == "sure" && it.x in .25..0.50 && it.y in .45..0.70 }
        return sure?.let { it.x to it.y } ?: .393 to .57
    }

    /** A partially recognized Save Photo prompt must be dismissed, not mistaken for a missing menu. */
    fun nativeCancel(words: List<GameScreens.Word>): Pair<Double, Double>? {
        val full = GameScreens.plain(words.joinToString(" ") { it.text })
        if (!full.contains("savephoto") || !full.contains("cancel")) return null
        val cancel = words.firstOrNull { it.plain == "cancel" && it.x in .49..0.79 && it.y in .45..0.70 }
        return cancel?.let { it.x to it.y } ?: .58 to .56
    }

    fun nativePromptVisible(words: List<GameScreens.Word>): Boolean {
        val full = GameScreens.plain(words.joinToString(" ") { it.text })
        return full.contains("savephoto") &&
            (full.contains("sure") || full.contains("cancel"))
    }

    /** Long-press the actual full-color pictograms, not their text or the category's background. */
    fun nativeTiles(topic: Topic, bitmap: Bitmap, words: List<GameScreens.Word>): List<Tile> {
        if (topic.section != "chuc-phuc") return tiles(topic, bitmap, words)
        return ChessBlessings.tiles(
            words,
            { ChessBlessings.titleVisible(bitmap, it) },
            { x, y -> ChessBlessings.iconY(bitmap, x, y) }
        ).map { tile ->
            // Keep old sampled IDs as aliases for interrupted v0.13.3 runs, but use the
            // title as the durable identity. A fixed probe never authorizes a new save.
            val old = listOf(.40, .68, .91).filter { abs(it - tile.y) < .105 }
                .map { fingerprint(bitmap, tile.x, it) }.toSet()
            tile.copy(
                aliases = old + fingerprint(bitmap, tile.x, tile.y) +
                    listOfNotNull(ChessBlessings.artwork(bitmap, tile.x, tile.y))
            )
        }
    }

    fun skillTiles(bitmap: Bitmap): List<Tile> {
        val spots = ArrayList<Tile>()
        // These are probes, not separate icons: y=.50 and .55 can fall inside the SAME
        // illustrated skill. Their sampled hashes differ, so distinctBy(id) alone saved twice.
        // Merge overlapping probe footprints before hashing the canonical centre. Separate
        // visible icons are kept; do not impose a one-skill-per-hero assumption.
        for (y in listOf(.50, .55, .66)) {
            val pixels = IntArray(49) { n ->
                val x = .405 + (n % 7 - 3) * .025 / 3
                val yy = y + (n / 7 - 3) * .025 / 3
                bitmap.getPixel(
                    (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
                    (yy * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                )
            }
            if (score(bitmap, .405, y, .025) > 28 && skillProbeTextured(pixels)) {
                spots.add(Tile(.405, y, fingerprint(bitmap, .405, y)))
            }
        }
        return mergeSkillProbes(spots).map { tile ->
            tile.copy(id = fingerprint(bitmap, tile.x, tile.y))
        }.distinctBy { it.id }
    }

    /** Saturated blue backing is not an illustration. Require real luminance edges too. */
    fun skillProbeTextured(pixels: IntArray): Boolean {
        if (pixels.size < 2) return false
        val light = pixels.map { ((it shr 16 and 255) + (it shr 8 and 255) + (it and 255)) / 3 }
        val span = light.maxOrNull()!! - light.minOrNull()!!
        val edges = light.zipWithNext { a, b -> abs(a - b) }.average()
        return span >= 24 && edges >= 5
    }

    /** Probe footprints span .063 screen heights; .075 allows a small sampling margin. */
    fun mergeSkillProbes(probes: List<Tile>): List<Tile> {
        val groups = ArrayList<MutableList<Tile>>()
        for (probe in probes.sortedWith(compareBy({ it.y }, { it.x }))) {
            val group = groups.firstOrNull {
                abs(it.first().x - probe.x) < .025 && abs(it.first().y - probe.y) <= .075
            }
            if (group == null) groups.add(arrayListOf(probe)) else group.add(probe)
        }
        return groups.map { group ->
            group.first().copy(
                x = group.map { it.x }.average(),
                y = group.map { it.y }.average(),
                aliases = group.flatMap { it.aliases + it.id }.toSet()
            )
        }
    }

    /** Nested path tokens are validated before use by the storage adapter. */
    fun filename(topic: Topic, kind: String, page: Int, item: String? = null): String {
        val base = "chess__" + topic.section + "__" + topic.key + "__"
        return if (item == null) {
            base + "trang__%03d".format(page)
        } else {
            base + kind + "__" + item + "__%03d".format(page)
        }
    }

    fun safeName(text: String) = HeroNames.slug(text).take(45).ifEmpty { "chua-ro-ten" }

    /**
     * "Chọn chế độ" sits immediately to the right of "N.vụ" on the landscape lobby toolbar.
     * Screenshot 2048x921: mode icon center ~= (0.635, 0.895), quest icon ~= (0.515, 0.90).
     * Prefer the text's own horizontal position, but tap the icon vertically rather than its
     * low label; if OCR loses the small label, use the calibrated safe icon center.
     * Refuse to guess unless the bottom lobby toolbar itself is identifiable.
     */
    const val MODE_X = .635
    const val MODE_Y = .895

    fun modeEntryPoint(words: List<GameScreens.Word>): Pair<Double, Double>? {
        val footer = words.filter { it.y in .78..0.98 }
        val label = footer.firstOrNull { it.plain.contains("chonchedo") && it.x in .57..0.71 }
        if (label != null) return label.x to MODE_Y
        // The small mode text is often unreadable; other lobby labels still identify the bar.
        // "N.vụ" is just to its left: never use that label's x as the tap target.
        if (footer.any { it.plain in setOf("tuido", "nvu", "tuong", "trangbi") }) return MODE_X to MODE_Y
        return null
    }

    data class Tile(val x: Double, val y: Double, val id: String, val label: String? = null, val aliases: Set<String> = emptySet())

    // The five card centres in the chess encyclopedia (not the left edges of each card).
    // A portrait is about 17% of the screen ABOVE its hero name. Reading the name first
    // prevents tapping remnants of the previous row when the list has been scrolled.
    private val HERO_COLUMNS = listOf(.275, .421, .567, .713, .858)
    val HERO_LAYOUT = HeroGrid.Layout(
        HERO_COLUMNS.toDoubleArray(), .415, .6167,
        .33, .70, .51, .40, .82, profileTop = .235, profileBottom = .985, band = .035
    )

    fun heroListVisible(words: List<GameScreens.Word>): Boolean {
        val text = GameScreens.plain(words.joinToString(" ") { it.text })
        return text.contains("bachkhoa") && text.contains("tatcaquanco")
    }

    fun heroOverviewFilename(topic: Topic, row: Int) = "chess__" + topic.section + "__" + topic.key + "__trang__grid-v2__%03d".format(row)

    fun heroSlotKey(index: Int, id: String) = "hero-slot-v2:tat-ca-quan-co:$index:$id"

    fun heroSlots(ids: Iterable<String>): Map<Int, String> = ids.mapNotNull { id ->
        if (!id.startsWith("hero-slot-v2:tat-ca-quan-co:")) {
            null
        } else {
            val parts = id.split(':', limit = 4)
            parts.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 }?.let { index ->
                parts.getOrNull(3)?.takeIf { it.startsWith("hero-") }?.let { index to it }
            }
        }
    }.groupBy({ it.first }, { it.second }).mapNotNull { (index, names) ->
        names.distinct().singleOrNull()?.let { index to it }
    }.toMap()

    private val HERO_ROLES = setOf("phaothu", "dausi", "cungthu", "phapsu", "dodon", "satthu", "trothu")
    private val NON_HERO_NAMES = setOf(
        "phaothu", "dausi", "cungthu", "phapsu", "dodon", "satthu", "trothu",
        "phuthuy", "cuongchien", "thuve", "chuatet", "thanhquan",
        "giaidieuhocduong", "doalacgia", "mianhduoitrang", "langkhach",
        "demdong", "chihuy", "hothien", "hoily", "phe", "nghe"
    )

    /** Price glyphs may be merged with a title: "Arduin1", "Violet O1", "Violet 01". */
    fun heroName(text: String) = text.trim()
        .replace(Regex("(?:\\s*[oO0©●◉]\\s*)?\\s*[1-5]\\s*$"), "").trim()

    /** Normalize old completed IDs as well, so resuming v0.8.1 does not count Violet O1 twice. */
    fun heroIdentity(id: String): String {
        val prefix = "item:tat-ca-quan-co:hero-"
        return if (id.startsWith(prefix)) prefix + safeName(heroName(id.removePrefix(prefix))) else id
    }

    /** OCR can include the role icon as a letter: "A Đấu sĩ" is still a role label. */
    fun isHeroName(name: String): Boolean {
        val plain = GameScreens.plain(name)
        return name.length in 2..35 &&
            name.count { it.isLetter() } >= 2 &&
            name.none { it.isDigit() } &&
            !name.contains('…') &&
            !name.endsWith('.') &&
            !plain.contains("giaidieu") &&
            !plain.contains("mianhduo") &&
            NON_HERO_NAMES.none { plain == it || (plain.endsWith(it) && plain.length <= it.length + 2) }
    }

    fun heroProgress(saved: Int, current: String? = null) = "Quân cờ: đã lưu $saved tướng · tổng chưa xác định" +
        (current?.let { " · đang mở $it" } ?: "")

    /**
     * Named cards use their visible names. A role-only observation has no hero label until
     * the popup verifies it. Position is the portrait, safely tappable after scrolling.
     */
    fun heroNameTiles(words: List<GameScreens.Word>): List<Tile> {
        val result = ArrayList<Tile>()
        val unnamed = ArrayList<Tile>()
        for (w in words) {
            val name = heroName(w.text)
            val slug = HeroNames.slug(name)
            if (w.y !in .49..0.925 || slug.length !in 2..25) continue
            val x = HERO_COLUMNS.minByOrNull { abs(w.x - it) } ?: continue
            if (abs(w.x - x) > .047) continue
            if (!isHeroName(name)) {
                val plain = GameScreens.plain(name)
                // If the name itself was missed, a role identifies a card's position only.
                // It sits below the name; never use this role text as the hero's identity.
                if (HERO_ROLES.any { plain.endsWith(it) && plain.length <= it.length + 2 }) {
                    val y = w.y - .23
                    if (y in .29..0.77) {
                        unnamed.add(
                            Tile(
                                x,
                                y,
                                "unread-" + HERO_COLUMNS.indexOf(x) + "-" + (y * 1000).toInt()
                            )
                        )
                    }
                }
                continue
            }
            val portraitY = w.y - .172
            if (portraitY !in .29..0.77) continue
            if (result.none { it.id == "hero-" + slug }) {
                result.add(Tile(x, portraitY, "hero-" + slug, name))
            }
        }
        for (tile in unnamed) {
            if (result.none { abs(it.x - tile.x) < .02 && abs(it.y - tile.y) < .10 }) {
                result.add(tile)
            }
        }
        return heroRows(result).flatten()
    }

    /** Group OCR detections by approximate Y, then sort INSIDE each row by X. */
    fun heroRows(tiles: List<Tile>): List<List<Tile>> {
        val rows = ArrayList<MutableList<Tile>>()
        for (tile in tiles.sortedWith(compareBy({ it.y }, { it.x }))) {
            if (rows.isEmpty() || abs(rows.last().first().y - tile.y) > .075) rows.add(ArrayList())
            rows.last().add(tile)
        }
        return rows.map { row -> row.sortedBy { it.x } }
    }

    /** Hero's name lives at the top of the detail drawer; the skill title is lower down. */
    fun modalHeroTitle(words: List<GameScreens.Word>, expected: String? = null): String? {
        // A skill title can be OCR'd before the actual hero name. Restrict candidates
        // to the heading strip ABOVE faction labels and choose the expected card name first.
        val candidates = words.asSequence()
            .filter { it.x in .345..0.67 && it.y in .175..0.345 }
            .map { word -> word to heroName(word.text) }
            .filter { (_, name) ->
                isHeroName(name)
            }.toList()
        if (expected != null) {
            candidates.firstOrNull {
                safeName(it.second) == safeName(expected)
            }?.let { return it.second }
        }
        return candidates.minWithOrNull(
            compareBy<Pair<GameScreens.Word, String>>(
                { abs(it.first.y - .245) },
                { it.first.x }
            )
        )?.second
    }

    /**
     * White title letters distinguish the hero's NAME from cyan faction/job labels.
     * Read within ML Kit's own text bounds; no fixed row Y, so page 2 is recognized.
     */
    private fun looksLikeWhiteTitle(b: Bitmap, w: GameScreens.Word): Boolean {
        val x0 = (w.left * b.width).toInt().coerceIn(0, b.width - 1)
        val x1 = (w.right * b.width).toInt().coerceIn(x0 + 1, b.width)
        val y0 = (w.top * b.height).toInt().coerceIn(0, b.height - 1)
        val y1 = (w.bottom * b.height).toInt().coerceIn(y0 + 1, b.height)
        val dx = max(1, (x1 - x0) / 26)
        val dy = max(1, (y1 - y0) / 10)
        var nearWhite = 0
        var cyan = 0
        for (y in y0 until y1 step dy) {
            for (x in x0 until x1 step dx) {
                val p = b.getPixel(x, y)
                val red = Color.red(p)
                val green = Color.green(p)
                val blue = Color.blue(p)
                if (red > 166 && green > 166 && blue > 166 && abs(red - green) < 48 && abs(green - blue) < 48) nearWhite++
                if (blue > 150 && green > 125 && blue - red > 35 && green - red > 25) cyan++
            }
        }
        // A white faction icon in the same OCR box must not turn cyan faction text into a name.
        return nearWhite >= 3 && nearWhite.toDouble() / (nearWhite + cyan) > .65
    }

    /** All coordinates are fractions of the full landscape screenshot. */
    fun tiles(t: Topic, b: Bitmap, words: List<GameScreens.Word>): List<Tile> {
        if (t.key == "tat-ca-quan-co") {
            return heroNameTiles(words).filter { tile ->
                if (tile.label == null) return@filter score(b, tile.x, tile.y, .022) > 12
                val title = words.firstOrNull { w ->
                    HeroNames.slug(heroName(w.text)) ==
                        tile.id.removePrefix("hero-") &&
                        abs(w.y - (tile.y + .172)) < .015 &&
                        abs(w.x - tile.x) < .047
                }
                title != null &&
                    looksLikeWhiteTitle(b, title) &&
                    score(b, tile.x, tile.y, .022) > 12
            }
        }
        val spots = ArrayList<Triple<Double, Double, String?>>()
        when (t.key) {
            "tat-ca-trang-bi" -> {
                val cols = listOf(.324, .388, .453, .520, .586, .651)
                for (y in listOf(.24, .428, .613, .80)) {
                    for (x in cols) {
                        if (score(b, x, y, .019) > 35) spots.add(Triple(x, y, null))
                    }
                }
            }
            "phe", "nghe" -> {
                val cols = (0..11).map { .236 + .0562 * it }
                val rows = if (t.key == "phe") listOf(.297 to 12) else listOf(.547 to 12, .711 to 3)
                for ((y, n) in rows) {
                    for (x in cols.take(n)) {
                        if (score(b, x, y, .017) > 40) {
                            spots.add(Triple(x, y, null))
                        }
                    }
                }
            }
        }
        val result = ArrayList<Tile>()
        for ((x, y, label) in spots.sortedWith(compareBy({ it.second }, { it.first }))) {
            if (result.any { abs(it.x - x) < .022 && abs(it.y - y) < .12 }) continue
            val id = fingerprint(b, x, y)
            if (result.none { it.id == id }) result.add(Tile(x, y, id, label))
        }
        return result
    }

    /** Patch colour/edge energy: distinguish a vivid icon from the uniform blue/grey backing. */
    private fun score(b: Bitmap, x: Double, y: Double, r: Double): Double {
        var saturated = 0.0
        var contrast = 0.0
        var previous = -1
        var n = 0
        for (dy in -3..3) {
            for (dx in -3..3) {
                val px = b.getPixel(
                    ((x + dx * r / 3) * b.width).toInt().coerceIn(0, b.width - 1),
                    ((y + dy * r / 3) * b.height).toInt().coerceIn(0, b.height - 1)
                )
                val red = Color.red(px)
                val green = Color.green(px)
                val blue = Color.blue(px)
                val hi = max(red, max(green, blue))
                val lo = min(red, min(green, blue))
                saturated += (hi - lo) * .40
                val l = (red + green + blue) / 3
                if (previous >= 0) contrast += abs(l - previous) * .45
                previous = l
                n++
            }
        }
        return (saturated + contrast) / n
    }

    /** Same icon at another position/after scrolling gets the same hash (coarsely quantized pixels). */
    private fun fingerprint(b: Bitmap, x: Double, y: Double): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (row in 0..7) {
            for (col in 0..7) {
                val xx = ((x + (col - 3.5) * .005) * b.width).toInt().coerceIn(0, b.width - 1)
                val yy = ((y + (row - 3.5) * .009) * b.height).toInt().coerceIn(0, b.height - 1)
                val p = b.getPixel(xx, yy)
                md.update(byteArrayOf(((p shr 20) and 15).toByte(), ((p shr 12) and 15).toByte(), ((p shr 4) and 15).toByte()))
            }
        }
        return md.digest().take(7).joinToString("") { "%02x".format(it) }
    }

    /** Dense samples catch thin text moving on the nearly uniform encyclopedia background. */
    fun signature(b: Bitmap, modal: Boolean = false): IntArray {
        val x0 = if (modal) .38 else .24
        val x1 = if (modal) .87 else .92
        return IntArray(64 * 40) { n ->
            val xx = x0 + (x1 - x0) * ((n % 64) + .5) / 64.0
            val yy = .25 + .72 * ((n / 64) + .5) / 40.0
            val p = b.getPixel((xx * b.width).toInt().coerceIn(0, b.width - 1), (yy * b.height).toInt().coerceIn(0, b.height - 1))
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
    }

    fun changed(a: IntArray, b: IntArray): Boolean {
        if (a.size != b.size) return true
        if (a.isEmpty()) return false
        if (a.size < 512) return a.indices.sumOf { abs(a[it] - b[it]) }.toDouble() / a.size > 4.0
        val differences = IntArray(a.size) { b[it] - a[it] }
        val shift = differences.sortedArray()[differences.size / 2]
        // Ignore uniform brightness changes; count local text/edge changes instead of averaging them away.
        return differences.count { abs(it - shift) > 12 } >= maxOf(16, a.size / 100)
    }

    fun modal(b: Bitmap): Boolean {
        // The encyclopedia's dialog has a large white X in the upper right, absent on the page itself.
        val x = (.902 * b.width).toInt()
        val y = (.083 * b.height).toInt()
        val rad = (.019 * b.height).toInt().coerceAtLeast(5)
        var bright = 0
        var dark = 0
        for (i in -8..8) {
            val d = rad * i / 10
            val p = b.getPixel((x + d).coerceIn(0, b.width - 1), (y + d).coerceIn(0, b.height - 1))
            val q = b.getPixel((x + d).coerceIn(0, b.width - 1), (y - d).coerceIn(0, b.height - 1))
            val a = b.getPixel((x + d).coerceIn(0, b.width - 1), (y).coerceIn(0, b.height - 1))
            if (Color.red(p) > 190 && Color.green(p) > 190 && Color.blue(p) > 190) bright++
            if (Color.red(q) > 190 && Color.green(q) > 190 && Color.blue(q) > 190) bright++
            if (Color.red(a) < 175) dark++
        }
        return bright >= 16 && dark >= 8
    }
}

/**
 * Asynchronous reader. A look owns one bitmap, which MUST be kept or discarded exactly once.
 * Completed topics/item IDs are persisted by Screen so restarting an interrupted task will skip them.
 */
class ChessWikiReader(private val screen: Screen, private val options: ChessWiki.Options, private val finish: (Outcome) -> Unit) {
    sealed class Outcome {
        data class Done(val pages: Int, val items: Int) : Outcome()

        data class Failed(val reason: String) : Outcome()
    }

    /**
     * [UNTOUCHED]: the screen never became steady enough to long-press, so no Save Photo gesture was sent and
     * nothing can have been saved. Unlike [FAILED], it leaves nothing to reconcile in Pictures.
     */
    enum class NativeResult { SAVED, ABSENT, DEFERRED, FAILED, UNTOUCHED }

    interface Look {
        val words: List<GameScreens.Word>
        val itemTitle: String? get() = ChessPopupTitle.select(words)
        val signature: IntArray
        val modal: Boolean

        fun tiles(topic: ChessWiki.Topic): List<ChessWiki.Tile>

        fun nativeTiles(topic: ChessWiki.Topic): List<ChessWiki.Tile> = tiles(topic)

        fun skillIcons(): List<ChessWiki.Tile> = emptyList()

        fun nativeIconSignature(x: Double, y: Double): String? = null

        fun keep(name: String, then: (Boolean) -> Unit)

        /** List-only measurements, using the same RGB profile matcher as normal heroes. */
        val heroProfile: FloatArray get() = floatArrayOf()
        val height: Int get() = 0

        fun hasHeroCard(x: Double, y: Double): Boolean = false

        fun discard()
    }

    interface Screen {
        val alive: Boolean
        val monotonicMs: Long get() = System.nanoTime() / 1_000_000L

        /** Previously completed heroes from this run's persistent ledger. */
        val completedHeroes: Int get() = 0
        val completedHeroSlots: Map<Int, String> get() = emptyMap()

        fun look(words: Boolean, then: (Look?) -> Unit)

        fun tap(x: Double, y: Double, then: (Boolean) -> Unit)

        fun swipe(x: Double, y: Double, toX: Double, toY: Double, then: (Boolean) -> Unit)

        fun calibrated(action: CalibrationAction, then: (Boolean) -> Unit) {
            val p = action.points
            if (p.size == 1) tap(p[0].x, p[0].y, then) else swipe(p[0].x, p[0].y, p[1].x, p[1].y, then)
        }

        /** Held list drags use the same slow gesture as the normal hero collector. */
        fun dragHeroList(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
            val distance = abs(d).coerceAtMost(.70)
            val start = if (d > 0) .92 else .24
            swipe(.76, start, .76, if (d > 0) start - distance else start + distance, then)
        }

        /** Long press, identify Save Photo → Sure, and record its current group. */
        fun nativeSave(
            group: String,
            item: String,
            x: Double,
            y: Double,
            preserveDetail: Boolean = false,
            aliases: Set<String> = emptySet(),
            reference: NativeSaveEvidence.Reference? = null,
            then: (NativeResult) -> Unit
        ) {
            then(NativeResult.ABSENT)
        }

        /** Re-read only the hero heading strip when full-screen OCR is ambiguous. */
        fun readHeroTitle(then: (String?) -> Unit) {
            then(null)
        }

        fun later(ms: Long, then: () -> Unit)

        fun seen(id: String): Boolean

        fun finished(id: String)

        /** Durable signature of this hero's numbered screenshot, independent of exact pixel hash. */
        fun detailSignature(id: String): IntArray? = null

        fun finishDetail(id: String, signature: IntArray): Boolean {
            finished(id)
            return true
        }

        fun note(message: String)

        fun say(message: String)
    }

    companion object {
        const val MAX_PAGES = 65
        const val MAX_MODAL_PAGES = 16
        const val MAX_NAV = 14
        const val MAX_STILL = 3
        const val WAIT_MS = 1100L
        const val MAX_TILES = 450
    }

    private val topics = options.topics()
    private var completed = false
    private var pages = 0
    private var items = 0
    private var navigation = 0
    private var selectedChess = false
    private var veteranAttempts = 0
    private var tipAttempts = 0
    private var heroes = screen.completedHeroes
    private val timing = ChessWiki.Timing(options.fast)
    private val tried = HashSet<String>()

    /** Heroes with a skill photo saved (or awaiting its receipt) in this run: later probes of theirs are extras. */
    private val skillHeroes = HashSet<String>()
    private val heroState = ListNavigator.State()
    private val heroSlots = screen.completedHeroSlots.toMutableMap()
    private val heroRetries = HashMap<Int, Int>()
    private val verifiedPortraits = HashMap<Int, ChessWiki.Tile>()
    private val detailSignatures = HashMap<String, MutableList<IntArray>>()
    private var heroTopic = -1
    private var heroIndex = 0

    fun start() = navigate()

    private fun end(out: Outcome) {
        if (!completed) {
            completed = true
            finish(out)
        }
    }

    private fun fail(reason: String) = end(Outcome.Failed(reason))

    private fun look(words: Boolean = false, then: (Look) -> Unit) {
        if (!screen.alive || completed) return
        screen.look(words) { l ->
            when {
                !screen.alive || completed -> l?.discard()
                l == null -> fail("Không chụp được màn hình. Kiểm tra quyền Trợ năng và dung lượng, rồi bấm Tiếp tục.")
                else -> then(l)
            }
        }
    }

    private fun tap(
        x: Double,
        y: Double,
        wait: Long = timing.tap,
        id: String? = null,
        label: String = "Cờ: chạm",
        relative: Boolean = false,
        next: () -> Unit
    ) {
        if (!screen.alive) return
        val done = { ok: Boolean -> if (!ok) fail("Không chạm được màn hình.") else screen.later(wait, next) }
        if (id == null) {
            screen.tap(x, y, done)
        } else {
            screen.calibrated(
                CalibrationAction("chess.v1.$id", label, listOf(CalibrationPoint(x, y)), relative),
                done
            )
        }
    }

    private fun swipe(
        x: Double,
        y: Double,
        toX: Double,
        toY: Double,
        wait: Long = timing.scroll,
        id: String? = null,
        label: String = "Cờ: vuốt",
        next: () -> Unit
    ) {
        if (!screen.alive) return
        val done = { ok: Boolean -> if (!ok) fail("Không cuộn được nội dung.") else screen.later(wait, next) }
        if (id == null) {
            screen.swipe(x, y, toX, toY, done)
        } else {
            screen.calibrated(
                CalibrationAction(
                    "chess.v1.$id",
                    label,
                    listOf(CalibrationPoint(x, y), CalibrationPoint(toX, toY)),
                    durationMs = timing.move,
                    holdMs = if (kotlin.math.abs(x - toX) < .003) timing.hold else 0
                ),
                done
            )
        }
    }

    private fun save(l: Look, name: String, next: () -> Unit) {
        l.keep(name) { ok ->
            if (!screen.alive || completed) return@keep
            if (!ok) {
                fail("Lưu ảnh thất bại: " + name + "; kiểm tra dung lượng và bấm Tiếp tục.")
            } else {
                pages++
                next()
            }
        }
    }

    /** Existing lobby -> mode selection -> entertainment -> chess lobby -> encyclopedia. */
    private fun navigate(): Unit = look(true) { l ->
        val words = l.words
        // ML Kit can split "Chế độ giải trí" across lines; compare both whole normalized text
        // and recognizable fragments rather than requiring one OCR box with the full phrase.
        val all = GameScreens.plain(words.joinToString(" ") { it.text })
        val entertainment = words.any { it.plain.contains("chedogiai") || it.plain.contains("giaitri") } ||
            all.contains("chedogiaitri")
        // A fresh chess account may show these two screens AFTER selecting the game mode.
        // Use exactly the same guarded recognition as the game's initial entry flow.
        val onboarding = GameScreens.refine(
            GameScreens.Reading(GameScreens.Kind.OTHER, null, l.signature),
            words,
            null
        )
        val modeEntry = ChessWiki.modeEntryPoint(words)
        if (l.modal) {
            l.discard()
            if (navigation++ >= 3) return@look fail("Không đóng được bảng chi tiết. Đóng dấu X rồi bấm Tiếp tục.")
            return@look tap(.901, .08, id = "detail.close", label = "Cờ: đóng chi tiết") { navigate() }
        }
        if (all.contains("bachkhoa") && all.contains("colienquan")) {
            l.discard()
            screen.say("Đã vào Bách khoa Cờ Liên Quân")
            return@look topic(0)
        }
        if (navigation++ >= MAX_NAV) {
            return@look save(l, "chess__dieu-huong__khong-nhan-dien__trang__001") {
                fail("Không nhận ra đường vào Bách khoa. Mở Cờ Liên Quân → Trang chủ bằng tay rồi bấm Tiếp tục.")
            }
        }
        l.discard()
        when {
            onboarding.kind == GameScreens.Kind.CHESS_EXPERIENCE -> {
                if (++veteranAttempts >
                    2
                ) {
                    return@look fail("Không qua màn chọn kinh nghiệm Cờ Liên Quân. Chọn Kỳ cựu bằng tay rồi bấm Tiếp tục.")
                }
                screen.note("Tài khoản mới: chọn Kỳ cựu")
                onboarding.tap!!.let { at ->
                    tap(at.first, at.second, 2000, id = "onboarding.veteran", label = "Cờ: chọn Kỳ cựu", relative = true) { navigate() }
                }
            }
            onboarding.kind == GameScreens.Kind.CHESS_TIP -> {
                if (++tipAttempts > 2) return@look fail("Không qua thông báo Đấu luyện. Bấm Đã rõ bằng tay rồi Tiếp tục.")
                screen.note("Tài khoản mới: bấm Đã rõ")
                onboarding.tap!!.let { at ->
                    tap(at.first, at.second, 2000, id = "onboarding.tip", label = "Cờ: Đã rõ", relative = true) { navigate() }
                }
            }
            (all.contains("trangchu") && all.contains("colienquan")) -> {
                val home = words.firstOrNull { it.plain.contains("trangchu") && it.x < .24 }
                if (home !=
                    null
                ) {
                    tap(home.x, home.y, 1800, id = "lobby.home.detected", label = "Cờ: Trang chủ (nhận diện)", relative = true) {
                        navigate()
                    }
                } else {
                    tap(.102, .305, 1800, id = "lobby.home", label = "Cờ: Trang chủ") { navigate() }
                }
            }
            entertainment && all.contains("colienquan") && !selectedChess -> {
                selectedChess = true
                tap(.507, .827, 1100, id = "mode.select", label = "Cờ: chọn chế độ") { navigate() }
            }
            entertainment && all.contains("colienquan") && all.contains("batdau") && selectedChess ->
                tap(
                    words.firstOrNull { it.plain == "batdau" && it.x > .6 }?.x ?: .862,
                    words.firstOrNull { it.plain == "batdau" && it.x > .6 }?.y ?: .688,
                    3700,
                    id = "mode.enter",
                    label = "Cờ: vào sảnh Cờ",
                    relative = true
                ) { navigate() }
            entertainment && all.contains("colienquan") ->
                tap(.50, .824, 1000, id = "mode.select", label = "Cờ: chọn chế độ") { navigate() }
            entertainment -> tap(.135, .37, id = "mode.entertainment", label = "Cờ: mục giải trí") { navigate() }
            modeEntry != null -> {
                screen.note(
                    "Sảnh: chọn chế độ ở " + (modeEntry.first * 100).toInt() + "%, " + (modeEntry.second * 100).toInt() +
                        "% (không bấm N.vụ)"
                )
                tap(modeEntry.first, modeEntry.second, 1700, id = "lobby.mode", label = "Sảnh: mở chế độ", relative = true) { navigate() }
            }
            all.contains("colienquan") -> tap(.105, .30, 2000, id = "lobby.home", label = "Cờ: Trang chủ") { navigate() }
            else -> screen.later(1800) { navigate() }
        }
    }

    private fun topic(at: Int) {
        if (!screen.alive) return
        if (at >= topics.size) return end(Outcome.Done(pages, items))
        val t = topics[at]
        val marker = if (t.key == "tat-ca-quan-co") "hero-grid-v2:topic:" + t.key else "topic:" + t.key
        if (screen.seen(marker)) return topic(at + 1)
        screen.say("Bách khoa: " + t.side + " → " + t.label)
        val scanTopic = { if (t.key == "tat-ca-quan-co") startHeroList(at) else scan(at, 1, null, 0, 0) }
        tap(.129, t.sidebarY, timing.tab, id = "topic.${t.key}.sidebar", label = "Cờ: mục ${t.side}") {
            if (t.tabX == 0.0) {
                scanTopic()
            } else if (t.sweepHeader) {
                swipe(.83, .18, .37, .18, 700, id = "topic.header", label = "Cờ: vuốt thanh mục") {
                    tap(t.tabX, .18, timing.tab, id = "topic.${t.key}.tab", label = "Cờ: bảng ${t.label}") { scanTopic() }
                }
            } else {
                tap(t.tabX, .18, timing.tab, id = "topic.${t.key}.tab", label = "Cờ: bảng ${t.label}") { scanTopic() }
            }
        }
    }

    private fun scan(at: Int, page: Int, previous: IntArray?, still: Int, retries: Int): Unit = look(true) { l ->
        val t = topics[at]
        if (l.modal) {
            l.discard()
            if (retries >= 3) return@look fail("Không đóng được bảng chi tiết ở " + t.label + ". Đóng dấu X rồi Tiếp tục.")
            return@look tap(.902, .082, id = "detail.close", label = "Cờ: đóng chi tiết") { scan(at, page, previous, still, retries + 1) }
        }
        if (page > MAX_PAGES) {
            l.discard()
            return@look fail(
                "Đã tới giới hạn " + MAX_PAGES + " trang ở " + t.label +
                    ". Kiểm tra xem danh sách đã tới cuối hay chưa; ảnh đã lưu được giữ lại."
            )
        }
        if (previous != null && !ChessWiki.changed(previous, l.signature)) {
            l.discard()
            if (still >= MAX_STILL) {
                screen.finished("topic:" + t.key)
                return@look topic(at + 1)
            }
            // Two extra slow drags: a game list sometimes ignores its first drag near a transition.
            screen.note(t.label + ": thử kéo lại danh sách (" + (still + 1) + "/" + MAX_STILL + ")")
            return@look scrollList(at, still + 1) { scan(at, page, previous, still + 1, retries) }
        }
        // The first screenshot of each page is saved *before* clicking any item.
        val image = ChessWiki.filename(t, "trang", page)
        val signature = l.signature.copyOf()
        val native = ChessWiki.nativeGroup(t, options) != null
        val candidates = when {
            native -> l.nativeTiles(t)
            ChessWiki.detailType(t, options) != null -> l.tiles(t)
            else -> emptyList()
        }
        val path = "chess-page:" + t.key + ":" + page
        val next = { collect(at, page, signature, candidates, 0) }
        if (screen.seen(path)) {
            l.discard()
            next()
        } else {
            save(l, image) {
                screen.finished(path)
                next()
            }
        }
    }

    /** Chess uses the normal list navigator, with its own geometry and drawer recovery. */
    private fun startHeroList(at: Int) {
        heroTopic = at
        heroState.synced = false
        heroState.ref = null
        collectHero(at, 0)
    }

    private fun collectHero(at: Int, index: Int) {
        if (!screen.alive || completed) return
        val t = topics[at]
        if (index >= MAX_TILES) return fail("Đã tới giới hạn $MAX_TILES quân cờ; chưa xác nhận cuối danh sách.")
        heroIndex = index
        val detail = ChessWiki.detailType(t, options) != null
        val native = ChessWiki.nativeGroup(t, options) != null
        // Recheck the viewport even for completed slots. A user can resume mid-list or
        // change the ordering; an ordinal alone must never skip a different visible hero.
        var target: ChessWiki.Tile? = null
        var unexpectedLabel: String? = null
        var signature = intArrayOf()

        class Shot(val look: Look) : ListNavigator.Shot {
            var released = false
            override val profile = look.heroProfile
            override val height = look.height
            override val backArrow = look.modal

            override fun hasCard(x: Double, y: Double) = look.hasHeroCard(x, y)

            override fun done(keep: String?) {
                if (!released) {
                    released = true
                    look.discard()
                }
            }

            fun saveOverview(next: () -> Unit) {
                val page = index / 5 + 1
                val marker = "chess-grid-v2-page:" + t.key + ":" + page
                if (screen.seen(marker)) return next()
                released = true
                save(look, ChessWiki.heroOverviewFilename(t, page)) {
                    screen.finished(marker)
                    next()
                }
            }
        }
        val adapter = object : ListNavigator.Screen {
            override val alive get() = screen.alive && !completed
            override val layout = ChessWiki.HERO_LAYOUT

            override fun look(then: (ListNavigator.Shot?) -> Unit) {
                this@ChessWikiReader.look(true) { then(Shot(it)) }
            }

            override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                screen.note(
                    "Quân cờ: ô ${index + 1}, " +
                        (if (fling) "đưa về đầu" else "kéo có đo") + " " + (d * 100).toInt() + "% màn hình"
                )
                screen.dragHeroList(d, fling, then)
            }

            // Approve has already selected fresh coordinates. Defer the physical tap until
            // native-photo work (if enabled) has finished, then open the drawer exactly once.
            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) = then(true)

            override fun back(then: (Boolean) -> Unit) = screen.calibrated(
                CalibrationAction("chess.v1.detail.close", "Cờ: đóng chi tiết", listOf(CalibrationPoint(.902, .081))),
                then
            )

            override fun later(ms: Long, then: () -> Unit) = screen.later(ms, then)

            override fun note(text: String) = screen.note("Quân cờ: " + text)
        }
        val approve = ListNavigator.Approve { raw, x, y, has, _, then ->
            val shot = raw as Shot
            if (!has) {
                then(null)
            } else {
                val nearby = shot.look.tiles(t).filter { abs(it.x - x) < .035 && abs(it.y - y) < .065 }
                // OCR only supplies a name; measured grid position owns the tap and ordinal.
                // Ambiguous labels never become a completed hero without popup verification.
                val label = verifiedPortraits[index]?.label
                    ?: nearby.singleOrNull()?.label?.takeIf { ChessWiki.isHeroName(it) }
                val id = label?.let { "hero-" + ChessWiki.safeName(it) } ?: "slot-$index"
                target = ChessWiki.Tile(x, y, id, label)
                signature = shot.look.signature.copyOf()
                shot.saveOverview {
                    val known = label != null && screen.seen("item:" + t.key + ":" + id)
                    val wrongSlot = heroSlots.any { (slot, saved) -> saved == id && slot != index } ||
                        (known && heroSlots[index] != null && heroSlots[index] != id)
                    if (detail && wrongSlot) {
                        unexpectedLabel = label
                        then(null) // Do not tap an already completed hero at a different ordinal.
                    } else if (detail && known) {
                        heroSlots[index] = id
                        screen.finished(ChessWiki.heroSlotKey(index, id))
                        screen.note("Quân cờ: bỏ qua chi tiết ô ${index + 1} đã lưu " + label)
                        if (native) then(x to y) else then(null)
                    } else {
                        then(x to y)
                    }
                }
            }
        }
        ListNavigator(index, heroState, adapter, approve) { outcome ->
            when (outcome) {
                ListNavigator.Outcome.End -> {
                    heroTopic = -1
                    screen.finished("hero-grid-v2:topic:" + t.key)
                    topic(at + 1)
                }
                ListNavigator.Outcome.Missing -> unexpectedLabel?.let { resyncHero(at, it) }
                    ?: collectHero(at, index + 1)
                is ListNavigator.Outcome.Failed -> fail(
                    "Quân cờ, ô ${index + 1}: " +
                        outcome.reason.replace("danh sách Tất cả tướng", "danh sách Tất cả quân cờ")
                            .replace("danh sách tướng", "danh sách quân cờ")
                )
                ListNavigator.Outcome.Tapped -> {
                    val item = target ?: return@ListNavigator fail("Không xác định được ô quân cờ ${index + 1}.")
                    if (!detail && !native) {
                        collectHero(at, (index / 5 + 1) * 5)
                    } else {
                        collectItem(at, index / 5 + 1, signature, listOf(item), 0)
                    }
                }
            }
        }.start()
    }

    private fun collect(at: Int, page: Int, signature: IntArray, tiles: List<ChessWiki.Tile>, from: Int) {
        if (heroTopic == at) return collectHero(at, heroIndex + 1)
        val t = topics[at]
        if (from >= tiles.size ||
            items >= MAX_TILES ||
            (ChessWiki.detailType(t, options) == null && ChessWiki.nativeGroup(t, options) == null)
        ) {
            return nextPage(at, page, signature)
        }
        collectItem(at, page, signature, tiles, from)
    }

    /** A duplicate popup at another ordinal is a navigation fault, not completion of that slot. */
    private fun retryHero(at: Int, title: String, tries: Int = 0) {
        if (tries >= 3) return fail("Không đóng được bảng chi tiết. Đóng dấu X rồi Tiếp tục.")
        tap(.902, .081, timing.close, id = "detail.close", label = "Cờ: đóng chi tiết") {
            look(false) { l ->
                val open = l.modal
                l.discard()
                if (open) {
                    retryHero(at, title, tries + 1)
                } else {
                    resyncHero(at, title)
                }
            }
        }
    }

    private fun resyncHero(at: Int, title: String) {
        val count = (heroRetries[heroIndex] ?: 0) + 1
        heroRetries[heroIndex] = count
        if (count > 1) {
            fail(
                "Ô quân cờ ${heroIndex + 1} vẫn không khớp vị trí ($title) sau khi tìm lại. " +
                    "Đã dừng để tránh bỏ sót tướng; kiểm tra danh sách rồi Tiếp tục."
            )
        } else {
            screen.note("Ô ${heroIndex + 1} không khớp vị trí: $title; về đầu và đo lại một lần.")
            heroState.synced = false
            heroState.ref = null
            collectHero(at, heroIndex)
        }
    }

    private fun collectItem(at: Int, page: Int, signature: IntArray, tiles: List<ChessWiki.Tile>, from: Int) {
        val t = topics[at]
        val kind = ChessWiki.detailType(t, options)
        val wantsHeroDetails = options.heroDetails || "chieu-quan-co" in options.nativeGroups
        val originalGroup = ChessWiki.nativeGroup(t, options)
        val item = tiles[from]
        val identity = "item:" + t.key + ":" + item.id
        val observation = if (item.label == null) identity + ":page:" + page else identity
        if (heroTopic != at && !tried.add(observation)) return collect(at, page, signature, tiles, from + 1)

        fun detail() {
            if (kind == "tuong" &&
                !wantsHeroDetails &&
                (
                    verifiedPortraits[heroIndex] != null ||
                        screen.seen("native:quan-co:" + item.id)
                    )
            ) {
                return collect(at, page, signature, tiles, from + 1)
            }
            val needsHeadName = originalGroup == "quan-co" &&
                verifiedPortraits[heroIndex] == null &&
                !screen.seen("native:quan-co:" + item.id)
            if (kind == null ||
                (
                    !needsHeadName &&
                        screen.seen(identity) &&
                        (heroTopic != at || heroSlots[heroIndex] == item.id)
                    )
            ) {
                return collect(at, page, signature, tiles, from + 1)
            }
            screen.say(
                if (kind == "tuong") {
                    ChessWiki.heroProgress(heroes, item.label)
                } else {
                    t.label + ": chi tiết " + (from + 1) + "/" + tiles.size
                }
            )
            tap(item.x, item.y, timing.tap, id = "topic.${t.key}.card", label = "Cờ: mở thẻ ${t.label}", relative = true) {
                look(true) { l ->
                    if (!l.modal) {
                        l.discard()
                        screen.note(
                            "Không mở được chi tiết " + (item.label ?: "?") +
                                " tại " + item.x + "," + item.y + ". Ảnh toàn màn hình đã lưu."
                        )
                        if (heroTopic ==
                            at
                        ) {
                            return@look fail(
                                "Không mở được ô quân cờ ${heroIndex + 1}; chưa đánh dấu hoàn tất. Kiểm tra danh sách rồi Tiếp tục."
                            )
                        }
                        return@look collect(at, page, signature, tiles, from + 1)
                    }
                    val header = if (kind == "tuong") {
                        ChessWiki.modalHeroTitle(l.words, item.label)
                    } else {
                        l.itemTitle
                    }

                    fun capture(verified: String?) {
                        if (!screen.alive || completed) {
                            l.discard()
                            return
                        }
                        val title = if (kind == "tuong") {
                            (verified ?: item.label)?.let { ChessWiki.heroName(it) }
                        } else {
                            verified ?: item.label
                        }
                        if (kind == "tuong" && title == null) {
                            l.discard()
                            return fail("Không đọc được tên quân cờ trong thẻ hoặc vùng tiêu đề. Ảnh toàn màn hình vẫn được giữ.")
                        }
                        if (title == null) {
                            l.discard()
                            return fail(
                                "Chưa đọc được tên ở đầu bảng chi tiết. Không dùng mô tả làm tên file; kiểm tra popup rồi Tiếp tục."
                            )
                        }
                        val slug = if (kind == "tuong") ChessWiki.safeName(title) else ChessPopupTitle.key(title)
                        val folder = slug
                        // The popup heading is authoritative. Never persist a noisy card label
                        // as a completed hero, or count the same popup again after scrolling.
                        val actual = item.copy(id = (if (kind == "tuong") "hero-" else "$kind-") + slug, label = title)
                        val verifiedHead = verifiedPortraits[heroIndex]
                        if (heroTopic == at &&
                            (
                                (heroSlots[heroIndex] != null && heroSlots[heroIndex] != actual.id) ||
                                    (verifiedHead != null && verifiedHead.id != actual.id)
                                )
                        ) {
                            l.discard()
                            return retryHero(at, title ?: actual.id)
                        }
                        if (kind == "tuong" &&
                            originalGroup == "quan-co" &&
                            !screen.seen("native:quan-co:" + actual.id) &&
                            verifiedPortraits[heroIndex] == null
                        ) {
                            // Export only under the verified drawer title. Close first, then let
                            // ListNavigator remeasure the card: closing can move the viewport.
                            verifiedPortraits[heroIndex] = actual
                            l.discard()

                            fun returnToCard(tries: Int) {
                                if (tries >= 3) return fail("Không đóng được bảng tên quân cờ; đóng dấu X rồi Tiếp tục.")
                                tap(.902, .081, timing.close, id = "detail.close", label = "Cờ: đóng chi tiết") {
                                    look(false) { check ->
                                        val open = check.modal
                                        check.discard()
                                        if (open) returnToCard(tries + 1) else collectHero(at, heroIndex)
                                    }
                                }
                            }
                            return returnToCard(0)
                        }
                        if (kind == "tuong" && !wantsHeroDetails) {
                            // This portrait was already saved in an earlier interrupted run.
                            verifiedPortraits[heroIndex] = actual
                            l.discard()
                            tap(.902, .081, timing.close, id = "detail.close", label = "Cờ: đóng chi tiết") { collectHero(at, heroIndex) }
                            return
                        }
                        if (kind != "tuong" && screen.seen("item:" + t.key + ":" + actual.id)) {
                            l.discard()
                            return close(at, page, signature, tiles, from, actual)
                        }
                        if (kind == "tuong" && screen.seen("item:" + t.key + ":" + actual.id)) {
                            l.discard()
                            val elsewhere = heroSlots.any { (slot, id) -> slot != heroIndex && id == actual.id }
                            // Old runs have verified names but no ordinal ledger. Bind an unread
                            // or slightly noisy label only after verifying the popup. A clearly
                            // different name is evidence of a wrong tap, never a skipped hero.
                            val compatible = item.label == null ||
                                HeroFinder.same(
                                    ChessWiki.safeName(item.label),
                                    slug
                                )
                            val legacyMatch = heroSlots[heroIndex] == null && !elsewhere && compatible
                            return if (heroTopic == at && heroSlots[heroIndex] != actual.id && !legacyMatch) {
                                retryHero(at, title ?: actual.id)
                            } else {
                                close(at, page, signature, tiles, from, actual)
                            }
                        }
                        if (kind == "tuong") screen.say(ChessWiki.heroProgress(heroes, title))
                        modal(at, page, signature, tiles, from, actual, kind, folder, 1, l, null, 0)
                    }
                    if (kind != "tuong") return@look capture(header)
                    if (item.label != null &&
                        header != null &&
                        ChessWiki.safeName(header) == ChessWiki.safeName(item.label)
                    ) {
                        return@look capture(item.label)
                    }

                    // The full-screen pass sometimes reads a skill as the hero's title.
                    // Do not conclude "wrong hero" from that unverified OCR result.
                    screen.note(
                        "Tên popup chưa chắc chắn: thẻ=" + (item.label ?: "?") +
                            ", OCR toàn màn hình=" + (header ?: "không thấy") +
                            ". Đọc lại riêng vùng tên tướng."
                    )

                    fun verify(attempt: Int, previous: String? = null) {
                        if (!screen.alive || completed) {
                            l.discard()
                            return
                        }
                        screen.readHeroTitle { precise ->
                            if (!screen.alive || completed) {
                                l.discard()
                                return@readHeroTitle
                            }
                            val valid = precise?.let { ChessWiki.heroName(it) }?.takeIf { ChessWiki.isHeroName(it) }
                            val matchesCard = valid != null &&
                                item.label != null &&
                                ChessWiki.safeName(valid) == ChessWiki.safeName(item.label)
                            val confirmed = valid != null &&
                                previous != null &&
                                ChessWiki.safeName(valid) == ChessWiki.safeName(previous)
                            if (valid != null && (matchesCard || confirmed)) {
                                screen.note(
                                    "Đã xác minh tên quân cờ bằng OCR vùng tiêu đề: " + valid +
                                        if (matchesCard) "" else "; sửa nhãn thẻ " + (item.label ?: "?")
                                )
                                capture(valid)
                            } else if (attempt < 3) {
                                screen.later(600) { verify(attempt + 1, valid) }
                            } else {
                                l.discard()
                                fail(
                                    "Chưa xác minh được tên quân cờ " + (item.label ?: "?") +
                                        " trong vùng tiêu đề sau 3 lần. Ảnh toàn màn hình vẫn được giữ; " +
                                        "kiểm tra popup rồi bấm Tiếp tục."
                                )
                            }
                        }
                    }
                    verify(1)
                }
            }
        }

        val nativeKey = originalGroup?.let { "native:" + it + ":" + item.id }
        val nativeAliases = item.aliases + item.id
        val knownIds = if (originalGroup != null && ChessBlessings.isGroup(originalGroup)) setOf(item.id) else nativeAliases
        val alreadySaved = originalGroup != null && knownIds.any { screen.seen("native:" + originalGroup + ":" + it) }
        if (originalGroup == null || nativeKey == null || alreadySaved) return detail()
        if (originalGroup == "quan-co" && verifiedPortraits[heroIndex] == null) return detail()
        screen.say(
            if (t.key == "tat-ca-quan-co") {
                "Quân cờ: lưu ảnh gốc " +
                    (item.label ?: "đang nhận diện") + " · tổng chưa xác định"
            } else {
                t.label + ": lưu ảnh gốc " + (from + 1) + "/" + tiles.size
            }
        )
        val nativeName = if (originalGroup == "quan-co") {
            ChessWiki.safeName(item.label ?: item.id) + "-head"
        } else {
            item.label ?: item.id
        }
        screen.nativeSave(originalGroup, nativeName, item.x, item.y, false, nativeAliases) { saved ->
            if (!screen.alive || completed) return@nativeSave
            if (saved == NativeResult.ABSENT && originalGroup != "quan-co" && !ChessBlessings.isGroup(originalGroup)) {
                screen.note("Không có Save Photo tại vị trí dò " + originalGroup + "; tiếp tục mục kế tiếp.")
                return@nativeSave detail()
            }
            if (saved == NativeResult.UNTOUCHED) {
                return@nativeSave fail(
                    "Chưa bấm giữ được ảnh gốc " + nativeName +
                        " vì màn chưa ổn định; chưa có ảnh nào được lưu. Bấm Tiếp tục để thử lại."
                )
            }
            if (saved != NativeResult.SAVED) {
                return@nativeSave fail(
                    "Chưa lưu được ảnh gốc " + nativeName +
                        "; kiểm tra Save Photo / Pictures rồi bấm Tiếp tục. Chưa đánh dấu hoàn tất."
                )
            }
            knownIds.forEach { screen.finished("native:" + originalGroup + ":" + it) }
            screen.note("Ảnh gốc " + originalGroup + ": " + nativeName)
            detail()
        }
    }

    private fun modal(
        at: Int,
        page: Int,
        signature: IntArray,
        tiles: List<ChessWiki.Tile>,
        from: Int,
        tile: ChessWiki.Tile,
        kind: String,
        folder: String,
        number: Int,
        l: Look,
        previous: IntArray?,
        still: Int
    ) {
        val readiness = ChessDetailReadiness(
            screen.monotonicMs,
            requiresIcon = kind == "tuong" && number == 1,
            sameDetail = { a, b -> !ChessWiki.changed(a, b) },
            sameIcon = NativePhotoReceipt::sameIcon
        )

        var ended = false
        var reads = 0

        fun timeout() {
            if (ended) return
            ended = true
            if (!screen.alive || completed) return
            screen.note("Detail readiness timed out after $reads frames; no capture or advance was authorized.")
            fail("Ảnh chi tiết " + folder + " chưa tải ổn định; chưa lưu hoặc chuyển bước. Chờ ảnh hiện rồi Tiếp tục.")
        }
        screen.note("Waiting for stable detail artwork before capture.")
        screen.later(30_000L) { timeout() }

        fun waitForDetail(frame: Look) {
            if (ended || !screen.alive || completed) {
                ended = true
                frame.discard()
                return
            }
            if (!frame.modal) {
                ended = true
                frame.discard()
                fail("Chi tiết " + folder + " đã đóng ngoài dự kiến; kiểm tra rồi Tiếp tục.")
                return
            }
            reads++
            val icons = frame.skillIcons().mapNotNull { frame.nativeIconSignature(it.x, it.y) }
            when (readiness.observe(screen.monotonicMs, screen.alive && !completed, frame.modal, frame.signature, icons)) {
                ChessDetailReadiness.Decision.READY -> {
                    ended = true
                    modalReady(at, page, signature, tiles, from, tile, kind, folder, number, frame, previous, still)
                }
                ChessDetailReadiness.Decision.WAIT -> {
                    frame.discard()
                    screen.later(800L) { if (!ended) look(false) { waitForDetail(it) } }
                }
                ChessDetailReadiness.Decision.CANCELLED -> frame.discard()
                ChessDetailReadiness.Decision.TIMEOUT -> {
                    frame.discard()
                    timeout()
                }
            }
        }
        waitForDetail(l)
    }

    private fun modalReady(
        at: Int,
        page: Int,
        signature: IntArray,
        tiles: List<ChessWiki.Tile>,
        from: Int,
        tile: ChessWiki.Tile,
        kind: String,
        folder: String,
        number: Int,
        l: Look,
        previous: IntArray?,
        still: Int
    ) {
        val t = topics[at]
        if (!l.modal) {
            l.discard()
            return fail("Chi tiết " + folder + " đã đóng ngoài dự kiến; kiểm tra rồi Tiếp tục.")
        }
        if (number >
            MAX_MODAL_PAGES
        ) {
            l.discard()
            return fail(
                "Chi tiết " + folder + " vẫn còn cuộn sau " + MAX_MODAL_PAGES + " ảnh. Kiểm tra bằng tay rồi tiếp tục."
            )
        }
        if (previous != null && !ChessWiki.changed(previous, l.signature)) {
            l.discard()
            if (still >= timing.modalStill) return close(at, page, signature, tiles, from, tile)
            return swipe(.75, .81, .75, .40, id = "detail.description", label = "Cờ: cuộn mô tả") {
                look(false) { next -> modal(at, page, signature, tiles, from, tile, kind, folder, number, next, previous, still + 1) }
            }
        }
        val shotName = ChessWiki.filename(t, kind, number, folder)
        val current = l.signature.copyOf()
        val history = detailSignatures.getOrPut(t.key + ":" + tile.id) { ArrayList() }
        if (number == 1) history.clear()
        if (history.any { !ChessWiki.changed(it, current) }) {
            l.discard()
            return fail("Chi tiết " + folder + " cuộn quay lại nội dung cũ; đã dừng để tránh lưu lặp. Kiểm tra rồi Tiếp tục.")
        }
        history.add(current)
        val skillImages = if ("chieu-quan-co" in options.nativeGroups) l.skillIcons() else emptyList()
        val nativeReferences = skillImages.associate {
            it.id to NativeSaveEvidence.Reference(
                current.copyOf(),
                l.nativeIconSignature(it.x, it.y),
                NativeSaveEvidence.operationKey(folder, number, it.x, it.y)
            )
        }
        val captureKey = "detail-page-v2:" + t.key + ":" + tile.id + ":" + number
        val legacyKey = "detail-page:" + t.key + ":" + tile.id + ":" + number + ":" + current.contentHashCode()
        val prior = screen.detailSignature(captureKey)
        if (prior != null && ChessWiki.changed(prior, current)) {
            l.discard()
            return fail(
                "Nội dung " + folder + "-detail-" + number +
                    " khác ảnh đã lưu. Đã dừng để giữ ảnh cũ; kiểm tra rồi bắt đầu lượt mới nếu nội dung đã thay đổi."
            )
        }
        val afterSave = {
            fun advance() {
                swipe(.75, .82, .75, .42, id = "detail.description", label = "Cờ: cuộn mô tả") {
                    look(false) { next ->
                        if (!next.modal) {
                            next.discard()
                            fail("Chi tiết " + folder + " đã đóng ngoài dự kiến, hãy mở đúng màn hình rồi tiếp tục.")
                        } else {
                            modal(at, page, signature, tiles, from, tile, kind, folder, number + 1, next, current, 0)
                        }
                    }
                }
            }

            fun nativeSkill(at: Int, images: List<ChessWiki.Tile>, next: () -> Unit, retried: Boolean = false) {
                if (at >= images.size) return next()
                val candidate = images[at]
                val key = "native:chieu-quan-co:" + folder + ":" + candidate.id
                val priorKeys = (candidate.aliases + candidate.id).map { "native:chieu-quan-co:" + folder + ":" + it }
                val deferredKey = "native-deferred-slot:" + NativeSaveEvidence.operationKey(folder, number, candidate.x, candidate.y)
                if (!retried) {
                    if (screen.seen(deferredKey) || priorKeys.any { screen.seen(it) }) skillHeroes.add(folder)
                    if (screen.seen(deferredKey) ||
                        priorKeys.any { screen.seen(it) || it in tried }
                    ) {
                        return nativeSkill(at + 1, images, next)
                    }
                    tried.addAll(priorKeys)
                }
                screen.say("Quân cờ: lưu ảnh chiêu " + folder + " · ảnh chi tiết " + number)
                screen.nativeSave(
                    "chieu-quan-co",
                    folder + "-skill-" + candidate.id,
                    candidate.x,
                    candidate.y,
                    true,
                    candidate.aliases.map { folder + "-skill-" + it }.toSet(),
                    nativeReferences[candidate.id]
                ) { ok ->
                    if (!screen.alive || completed) return@nativeSave
                    if (ok == NativeResult.ABSENT) {
                        screen.note(
                            "Không có Save Photo tại vị trí dò chiêu " + candidate.x + "," + candidate.y +
                                "; giữ ảnh chi tiết và kiểm tra vị trí kế tiếp."
                        )
                        return@nativeSave nativeSkill(at + 1, images, next)
                    }
                    if (ok == NativeResult.UNTOUCHED) {
                        // Nothing was pressed, so nothing can have been saved: no Pictures check is owed.
                        if (folder in skillHeroes) {
                            screen.note(
                                "Bỏ qua vị trí dò chiêu " + candidate.x + "," + candidate.y + " ở ảnh chi tiết " + number +
                                    ": màn chưa ổn định nên chưa bấm giữ, không lưu gì; ảnh chiêu của " + folder + " đã lưu trước đó."
                            )
                            return@nativeSave nativeSkill(at + 1, images, next)
                        }
                        if (!retried) {
                            screen.note("Chưa bấm giữ được ảnh chiêu " + folder + " vì màn chưa ổn định; thử lại một lần.")
                            return@nativeSave screen.later(1_500L) {
                                if (screen.alive && !completed) nativeSkill(at, images, next, retried = true)
                            }
                        }
                        return@nativeSave fail(
                            "Chưa bấm giữ được ảnh chiêu của " + folder + " sau 2 lần vì màn chi tiết chưa ổn định; " +
                                "chưa có ảnh nào được lưu ở bước này. Kiểm tra màn hình rồi bấm Tiếp tục để thử lại."
                        )
                    }
                    if (ok == NativeResult.DEFERRED) {
                        // Attempt-only checkpoint, deliberately NOT a verified native save key.
                        // The receipt journal owns reconciliation and survives a failed checkpoint write.
                        screen.finished(deferredKey)
                        skillHeroes.add(folder)
                        screen.note("Ảnh chiêu " + folder + " đang chờ đối chiếu; đã giữ tiến độ, tiếp tục mục kế tiếp và không tải lại.")
                        return@nativeSave nativeSkill(at + 1, images, next)
                    }
                    if (ok != NativeResult.SAVED) {
                        return@nativeSave fail(
                            "Chưa xác minh xong ảnh chiêu của " + folder +
                                "; ảnh có thể đã lưu. Tiếp tục sẽ đối chiếu Pictures trước, không tải lại khi kết quả chưa rõ."
                        )
                    }
                    (priorKeys + key).toSet().forEach { screen.finished(it) }
                    skillHeroes.add(folder)
                    screen.note("Ảnh gốc chieu-quan-co: " + folder + "-skill-" + candidate.id)
                    nativeSkill(at + 1, images, next)
                }
            }
            nativeSkill(0, skillImages, { advance() })
        }
        if (screen.seen(captureKey) || screen.seen(legacyKey)) {
            l.discard()
            afterSave()
        } else {
            screen.say(
                if (kind == "tuong") {
                    "Quân cờ: đang lưu " + folder + "-detail-" + number
                } else {
                    t.label + ": đang lưu ảnh chi tiết " + number
                }
            )
            save(l, shotName) {
                if (!screen.finishDetail(captureKey, current)) {
                    fail(
                        "Đã lưu ảnh nhưng chưa ghi được tiến độ " + folder + "-detail-" + number +
                            "; kiểm tra dung lượng rồi bấm Tiếp tục."
                    )
                } else {
                    afterSave()
                }
            }
        }
    }

    private fun close(
        at: Int,
        page: Int,
        signature: IntArray,
        tiles: List<ChessWiki.Tile>,
        from: Int,
        tile: ChessWiki.Tile,
        tries: Int = 0
    ) {
        if (tries >= 3) return fail("Không đóng được bảng chi tiết. Đóng dấu X bằng tay rồi bấm Tiếp tục.")
        tap(.902, .081, timing.close, id = "detail.close", label = "Cờ: đóng chi tiết") {
            look(false) { l ->
                val stillOpen = l.modal
                l.discard()
                if (stillOpen) {
                    close(at, page, signature, tiles, from, tile, tries + 1)
                } else {
                    val identity = "item:" + topics[at].key + ":" + tile.id
                    if (!screen.seen(identity)) {
                        screen.finished(identity)
                        items++
                        if (topics[at].key == "tat-ca-quan-co") heroes++
                    }
                    if (heroTopic == at) {
                        heroSlots[heroIndex] = tile.id
                        screen.finished(ChessWiki.heroSlotKey(heroIndex, tile.id))
                    }
                    if (topics[at].key == "tat-ca-quan-co") screen.say(ChessWiki.heroProgress(heroes))
                    collect(at, page, signature, tiles, from + 1)
                }
            }
        }
    }

    private fun scrollList(at: Int, retry: Int = 0, next: () -> Unit) {
        // Real ZIP: card rows are about .616 screen heights apart. A .60 drag brings
        // row 2's name to the same visible band, rather than leaving two partial rows.
        val heroes = topics[at].key == "tat-ca-quan-co"
        val x = if (!heroes && retry % 2 == 1) .55 else .76
        swipe(
            x,
            if (heroes || retry > 0) .84 else .82,
            x,
            if (heroes) {
                .24
            } else if (retry > 0) {
                .28
            } else {
                .40
            },
            id = "topic.${topics[at].key}.scroll.$retry",
            label = "Cờ: cuộn ${topics[at].label}",
            next = next
        )
    }

    private fun nextPage(at: Int, page: Int, signature: IntArray) {
        screen.say(topics[at].label + ": kéo xuống hàng tiếp theo")
        screen.note(topics[at].label + ": cuộn sang trang " + (page + 1))
        scrollList(at) { scan(at, page + 1, signature, 0, 0) }
    }
}
