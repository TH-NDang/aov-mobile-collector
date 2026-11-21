package vn.ndang.aovcollector

/** A refusal is not a gesture retry. The report contains geometry/flags, never screen content. */
internal data class CalibrationRefusal(val code: String, val message: String, val report: String)

internal object CalibrationDiagnostics {
    const val MAX_REPORT_CHARS = 8192
    private const val MAX_WINDOWS = 24

    fun refusal(reason: String, attempt: String, safety: String): CalibrationRefusal {
        val (code, message) = when {
            reason == "teaching-ended" -> "T01" to "Phiên chỉnh đã kết thúc. Hủy rồi mở lại."
            reason == "generation-changed" -> "T02" to "Phiên chạy đã thay đổi. Hủy rồi mở lại."
            reason == "session-changed" -> "T03" to "Bước đang chỉnh đã thay đổi. Hủy rồi mở lại."
            reason == "display-profile-changed" -> "T04" to "Vùng màn hình đã đổi. Hủy rồi mở lại để đọc đúng vùng."
            reason == "gesture-in-flight" -> "T05" to "Android còn đang xử lý thao tác trước. Đợi rồi thử lại."
            reason == "invalid-points" -> "T06" to "Điểm chạm chưa hợp lệ. Chỉnh lại điểm trước khi chạy."
            reason == "missing-gesture-path" -> "T07" to "Chưa có đường chạm để kiểm tra vùng che. Hủy rồi mở lại."
            reason == "invalid-gesture-path" -> "T08" to "Đường chạm chưa hợp lệ. Chỉnh lại điểm trước khi chạy."
            reason == "target-bounds-changed" ->
                "T09" to
                    "Điểm đã chỉnh vượt vùng/độ dài an toàn của thao tác. Chỉnh lại hoặc chọn Mặc định."
            reason == "application-window-count=0" -> "W00" to "Chưa đọc được cửa sổ game. Chép lý do lỗi để kiểm tra."
            reason.startsWith("application-window-count=") -> "W01" to "Có nhiều cửa sổ ứng dụng. Đóng cửa sổ nổi hoặc chia đôi."
            reason == "foreign-or-unknown-application" -> "W02" to "Chưa xác nhận đúng cửa sổ Liên Quân. Hủy và trở lại game."
            reason == "foreign-or-unknown-accessibility-overlay" -> "W03" to "Có lớp trợ năng khác hoặc chưa rõ nguồn. Chép lý do lỗi."
            reason == "foreign-system-window-active" -> "W04" to "Bảng hệ thống đang nhận thao tác. Đóng bảng đó trước."
            reason == "missing-or-ambiguous-foreground" -> "W05" to "Chưa xác định cửa sổ đang nhận thao tác. Chép lý do lỗi."
            reason == "game-not-focused" -> "W06" to "Game chưa nhận tiêu điểm. Hủy và trở lại game."
            reason == "focused-window-not-game-application" -> "W07" to "Cửa sổ nhận thao tác không phải game. Hủy và trở lại game."
            reason == "game-does-not-cover-usable-display" -> "W08" to "Khung game không phủ vùng an toàn. Chép lý do lỗi để kiểm tra."
            reason == "gesture-path-outside-game" -> "W09" to "Điểm hoặc đường vuốt nằm ngoài khung game. Chỉnh lại điểm."
            reason.startsWith("window-query-exception=") -> "W10" to "Android chưa cho đọc cửa sổ. Chép lý do lỗi để kiểm tra."
            reason == "gesture-path-near-foreign-window" -> "W11" to "Đường chạm quá gần cửa sổ khác. Dời điểm hoặc đóng thanh bên."
            reason == "foreign-window-geometry-unknown" -> "W12" to "Chưa xác minh vùng cửa sổ khác. Chép lý do lỗi để kiểm tra."
            else -> "T99" to "Chưa xác minh được thao tác. Chép lý do lỗi để kiểm tra."
        }
        val report = "AOV teaching refusal $code\n$message\n$attempt\n$safety"
        return CalibrationRefusal(code, message, report.take(MAX_REPORT_CHARS))
    }

    /** Do not include raw package names, node labels, window titles, account data or screenshots. */
    fun windows(windows: List<ForegroundWindow>, ownPackage: String, gamePackage: String): String =
        "windowCount=${windows.size}; windows=" + windows.take(MAX_WINDOWS).joinToString { window ->
            val owner = when (window.packageName) {
                gamePackage -> "GAME"
                ownPackage -> "COLLECTOR"
                null -> "UNKNOWN"
                else -> "OTHER"
            }
            "[$owner app=${window.application} overlay=${window.accessibilityOverlay} " +
                "focused=${window.focused} active=${window.active} bounds=${window.bounds}]"
        } + if (windows.size > MAX_WINDOWS) "; remaining-windows-omitted" else ""
}
