package vn.ndang.aovcollector

import org.json.JSONObject

internal data class CalibrationStep(val title: String, val hint: String, val action: CalibrationAction, val recognition: String? = null)

/** Ordered teaching targets only. Selecting a step never performs a gesture. */
internal object CalibrationCatalog {
    /**
     * Recognized built-in schema metadata is a capability whitelist, not cryptographic provenance.
     * Imported tap/swipe coordinates are never read: all targets come from this fixed catalog.
     */
    fun steps(macro: JSONObject): List<CalibrationStep> {
        val mode = macro.optString("collectionMode")
        val canonicalName = when (mode) {
            "chess" -> "chess-wiki"
            "pro-builds" -> "pro-builds"
            "ranking" -> "hero-ranking"
            "all" -> "batch-heroes"
            "pick" -> "named-heroes"
            "single" -> "single-hero"
            else -> return emptyList()
        }
        if (macro.optInt("builtinVersion", 0) !in 1..BuiltInMacros.VERSION ||
            macro.optString("name") != canonicalName ||
            macro.optString("targetPackage") != "com.garena.game.kgvn"
        ) {
            return emptyList()
        }
        return when (mode) {
            "chess" -> chess(macro)
            "pro-builds" -> proBuilds()
            "ranking" -> ranking(macro)
            else -> heroes()
        }
    }

    private fun tap(id: String, label: String, hint: String, x: Double, y: Double) =
        CalibrationStep(label, hint, CalibrationAction(id, label, listOf(CalibrationPoint(x, y))))

    /** The resolver must provide a newly observed base before this target can be tested or saved. */
    private fun recognized(id: String, label: String, hint: String) =
        CalibrationStep(label, hint, CalibrationAction(id, label, emptyList(), relative = true), id)

    private fun swipe(id: String, label: String, hint: String, x: Double, y: Double, toX: Double, toY: Double, timing: ChessWiki.Timing) =
        CalibrationStep(
            label,
            hint,
            CalibrationAction(
                id,
                label,
                listOf(CalibrationPoint(x, y), CalibrationPoint(toX, toY)),
                durationMs = timing.move,
                holdMs = if (kotlin.math.abs(x - toX) < .003) timing.hold else 0
            )
        )

    private fun proBuilds(): List<CalibrationStep> = listOf(
        recognized("pro.v1.lobby.equipment", "Sảnh: Trang bị", "Mở sảnh game; nhận diện chữ Trang bị ở thanh dưới."),
        tap("pro.v1.picker.open", "Pro: đổi tướng", "Mở Chuẩn bị; đặt vào hai mũi tên dưới danh sách bên trái.", .094, .821),
        recognized("pro.v1.picker.all", "Pro: Tất cả tướng", "Mở Chọn tướng; chọn tab Tất cả."),
        recognized(
            "pro.v1.picker.owned-filter",
            "Pro: bỏ Đã nhận",
            "Chỉ chạy khi đọc rõ ô đang được tick; xác minh tắt trước khi chọn tướng."
        ),
        recognized("pro.v1.picker.card", "Pro: chọn tướng", "Điểm được đo trên thẻ; không chọn nút mua hoặc nhận tướng."),
        recognized("pro.v1.other", "Pro: Phương án khác", "Mở Chuẩn bị của đúng tướng."),
        recognized("pro.v1.tab", "Pro: Đề cử Pro", "Mở Hệ thống đề xuất; chọn tab Đề cử Pro."),
        recognized("pro.v1.apply", "Pro: Dùng combo", "Chỉ trong nút của đúng đề cử đã nhận diện; điểm ngoài nút bị từ chối."),
        tap("pro.v1.popup.close", "Pro: đóng đề xuất", "Đóng cửa sổ Hệ thống đề xuất bằng dấu X.", .824, .111)
    )

    private fun chess(macro: JSONObject): List<CalibrationStep> = buildList {
        val selected = macro.optJSONArray("chessTopics")
        val keys = selected?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        val topics = ChessWiki.TOPICS.filter { it.key in keys }
        if (topics.isEmpty()) return@buildList
        val timing = ChessWiki.Timing(macro.optBoolean("fast", true))
        add(recognized("chess.v1.lobby.mode", "Sảnh: mở chế độ", "Mở sảnh chính Liên Quân; nhận diện nút Chọn chế độ ở thanh dưới."))
        add(tap("chess.v1.mode.entertainment", "Cờ: mục giải trí", "Mở màn Chọn chế độ; chọn mục Giải trí bên trái.", .135, .37))
        add(
            tap(
                "chess.v1.mode.select",
                "Cờ: chọn chế độ",
                "Mở mục Giải trí có thẻ Cờ Liên Quân; đặt điểm vào thẻ Cờ, không đặt vào nút ghép trận.",
                .507,
                .827
            )
        )
        add(
            recognized(
                "chess.v1.lobby.home.detected",
                "Cờ: Trang chủ (nhận diện)",
                "Mở sảnh Cờ có chữ Trang chủ bên trái; đọc vị trí trước khi chỉnh."
            )
        )
        add(tap("chess.v1.lobby.home", "Cờ: Trang chủ", "Vào sảnh Cờ Liên Quân bằng tay; chọn Trang chủ để mở Bách khoa.", .105, .30))
        var headerAdded = false
        for (topic in topics) {
            val hint = "Mở Bách khoa Cờ Liên Quân → ${topic.side} → ${topic.label}."
            add(
                tap(
                    "chess.v1.topic.${topic.key}.sidebar",
                    "Cờ: mục ${topic.side}",
                    "Mở Bách khoa; đặt điểm vào ${topic.side} ở thanh bên trái.",
                    .129,
                    topic.sidebarY
                )
            )
            if (topic.sweepHeader && !headerAdded) {
                add(
                    swipe(
                        "chess.v1.topic.header",
                        "Cờ: vuốt thanh mục",
                        "$hint Kéo thanh tab ngang để thấy mục bị khuất.",
                        .83,
                        .18,
                        .37,
                        .18,
                        timing
                    )
                )
                headerAdded = true
            }
            if (topic.tabX > 0) add(tap("chess.v1.topic.${topic.key}.tab", "Cờ: bảng ${topic.label}", hint, topic.tabX, .18))
            if (topic.key in setOf("tat-ca-quan-co", "phe", "nghe", "tat-ca-trang-bi")) {
                add(
                    recognized(
                        "chess.v1.topic.${topic.key}.card",
                        "Cờ: mở thẻ ${topic.label}",
                        "$hint Đóng bảng chi tiết; nhận diện một thẻ đang hiển thị."
                    )
                )
            }
            if (topic.key == "tat-ca-quan-co") {
                // The list navigator measures distance at runtime; teach offsets from safe baseline paths.
                for ((direction, ys) in listOf("down" to (.92 to .32), "up" to (.24 to .84))) {
                    val label = "Cờ: cuộn quân cờ " + if (direction == "down") "xuống" else "lên"
                    add(
                        CalibrationStep(
                            label,
                            "$hint Đóng bảng chi tiết; chỉnh đường kéo trong danh sách quân cờ.",
                            CalibrationAction(
                                "chess.v1.herolist.$direction",
                                label,
                                listOf(CalibrationPoint(.76, ys.first), CalibrationPoint(.76, ys.second)),
                                relative = true,
                                durationMs = 700,
                                holdMs = 450
                            )
                        )
                    )
                }
            } else {
                for (retry in 0..3) {
                    val x = if (retry % 2 == 1) .55 else .76
                    add(
                        swipe(
                            "chess.v1.topic.${topic.key}.scroll.$retry",
                            "Cờ: cuộn ${topic.label}" + if (retry == 0) "" else " (thử lại $retry)",
                            "$hint Đóng bảng chi tiết; kéo từ điểm đầu đến điểm cuối trong danh sách.",
                            x,
                            if (retry > 0) .84 else .82,
                            x,
                            if (retry > 0) .28 else .40,
                            timing
                        )
                    )
                }
            }
        }
        add(
            swipe(
                "chess.v1.detail.description",
                "Cờ: cuộn mô tả",
                "Mở chi tiết một quân cờ, Phe, Nghề hoặc trang bị; kéo trong vùng mô tả bên phải.",
                .75,
                .82,
                .75,
                .42,
                timing
            )
        )
        add(
            tap(
                "chess.v1.detail.close",
                "Cờ: đóng chi tiết",
                "Mở bảng chi tiết trong Bách khoa; đặt điểm vào dấu X góc trên bên phải.",
                .902,
                .081
            )
        )
    }

    private fun ranking(macro: JSONObject): List<CalibrationStep> = buildList {
        val hint = "Mở Cộng đồng → Bảng Phân Bậc Tướng → Bảng xếp hạng đầy đủ bằng tay."
        add(recognized("ranking.v1.table.switch", "Đổi bảng xếp hạng", "$hint Nhận diện nút Đổi bảng xếp hạng."))
        add(recognized("ranking.v1.mode.open", "Mở danh sách chế độ", "$hint Đóng danh sách chế độ trước khi nhận diện nút mở."))
        val selected = macro.optJSONArray("rankCombos")
        val keys = selected?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
        val modes = RankScreens.COMBOS.filter { keys == null || it.slug in keys }.map { it.mode }.distinct()
        for (mode in modes) {
            add(
                recognized(
                    "ranking.v1.mode.option.${mode.name.lowercase(java.util.Locale.ROOT)}",
                    "Chọn ${mode.label}",
                    "$hint Mở danh sách chế độ để nhận diện dòng ${mode.label}."
                )
            )
        }
    }

    private fun heroes(): List<CalibrationStep> = listOf(
        tap("hero.v1.overview", "Tướng: tổng quan", "Mở trang một tướng; đặt điểm để đóng bảng chiêu và về tổng quan.", .60, .82),
        tap("hero.v1.attributes.open", "Tướng: mở thuộc tính", "Mở trang tổng quan một tướng; đặt điểm vào nút thuộc tính.", .095, .89),
        tap("hero.v1.attributes.tab", "Tướng: bảng thuộc tính", "Mở bảng thuộc tính tướng; chọn tab thuộc tính.", .125, .765)
    ) + listOf(4, 5).flatMap { count ->
        (0 until count).map { slot ->
            recognized(
                "skills.v1.row.$count.slot.$slot",
                "Tướng: ô chiêu ${slot + 1}/$count",
                "Mở tổng quan một tướng có $count biểu tượng chiêu; nhận diện hàng chiêu hiện tại trước khi chỉnh."
            )
        }
    } + listOf(
        tap(
            "skills.v1.summary.tab",
            "Tướng: Tóm tắt chiêu",
            "Mở một chiêu bằng tay; chọn tab Tóm tắt ở trên.",
            SkillReader.SUMMARY_X,
            SkillReader.TAB_Y
        ),
        tap(
            "skills.v1.detail.tab",
            "Tướng: Chi tiết chiêu",
            "Mở một chiêu bằng tay; chọn tab Chi tiết ở trên.",
            SkillReader.DETAIL_X,
            SkillReader.TAB_Y
        ),
        tap(
            "skills.v1.detail.close",
            "Tướng: đóng chiêu",
            "Mở bảng chiêu; đặt điểm vào vùng đóng bảng chiêu.",
            SkillReader.CLOSE_X,
            SkillReader.CLOSE_Y
        ),
        CalibrationStep(
            SkillReader.SCROLL.label,
            "Mở tab Chi tiết của một chiêu có mô tả dài; kéo lên trong vùng chữ mô tả, tránh nút Chú thích.",
            SkillReader.SCROLL
        ),
        tap(
            "skills.v1.form.swap",
            "Tướng: đổi dạng",
            "Mở tổng quan tướng có nhiều dạng; chỉ thử khi nút đổi dạng hiện cạnh hàng chiêu.",
            SkillRow.SWAP_X,
            SkillRow.Y
        ),
        tap(
            "hero.v1.back",
            "Tướng: quay lại danh sách",
            "Mở trang tướng; đặt điểm vào mũi tên trở lại danh sách tướng.",
            HeroGrid.BACK_X,
            HeroGrid.BACK_Y
        )
    )
}
