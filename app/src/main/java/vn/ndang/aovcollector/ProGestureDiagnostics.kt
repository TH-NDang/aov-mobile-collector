package vn.ndang.aovcollector

/** Per-attempt evidence. Accepted parts may already have changed the game; never imply safe replay. */
internal data class GestureFailure(val reason: String, val acceptedParts: Int = 0, val detail: String = "")

/** Run-log wording for a refused touch, so a pause that only says "could not touch" can be explained. */
internal object GestureFailureText {
    fun describe(reason: String): String = when (reason) {
        "overlay-intercepted" -> "chạm rơi vào bảng nổi của app"
        "android-cancelled" -> "Android hủy cú chạm (thường do tay chạm màn hình, thông báo hoặc cử chỉ hệ thống chen vào)"
        "android-rejected" -> "Android không nhận cú chạm"
        "android-exception" -> "lỗi khi gửi cú chạm"
        "service-changed" -> "dịch vụ Trợ năng vừa khởi động lại"
        "shield-session-ended" -> "bước đã bị ngắt trước khi chạm xong"
        "editor-open" -> "bảng chỉnh từng bước đang mở"
        "invalid-points" -> "điểm chạm không hợp lệ"
        else -> "lý do khác"
    }

    fun note(label: String?, reason: String, attempts: Int): String =
        "Chạm thất bại" + (label?.let { ": $it" } ?: "") + " — " + describe(reason) + " ($reason, $attempts lần thử)"
}

internal object ProGestureDiagnostics {
    fun message(action: CalibrationAction, failure: GestureFailure): String {
        val stage = if (failure.reason in
            setOf("unknown-outcome", "android-exception")
        ) {
            "chưa rõ thao tác đã được gửi hay chưa"
        } else if (failure.acceptedParts ==
            0
        ) {
            "chưa gửi thao tác vào game"
        } else {
            "đã gửi ${failure.acceptedParts} phần; kết quả có thể đã thay đổi"
        }
        val explanation = when (failure.reason) {
            "semantic-bounds" -> "Điểm đã chỉnh vượt vùng/độ dài an toàn; mở Chỉnh từng bước để sửa đúng thao tác này."
            "recommendation-changed" -> "Đề cử không còn khớp ảnh đã xác minh; chưa áp dụng combo."
            "editor-open" -> "Bảng chỉnh vẫn đang mở."
            "service-changed", "shield-session-ended" -> "Phiên thao tác đã kết thúc."
            "unknown-outcome", "android-exception" -> "Chưa xác minh được kết quả; không tự bấm lại."
            "android-rejected" -> "Android không nhận thao tác."
            "android-cancelled" -> "Android hủy thao tác; không tự bấm lại."
            "overlay-intercepted" -> "Thao tác rơi vào bảng nổi; dời bảng trước khi tiếp tục."
            else -> CalibrationDiagnostics.refusal(failure.reason, "", "").let { "${it.code}: ${it.message}" }
        }
        return "Pro [${action.id}] $stage: $explanation (${failure.reason})"
    }

    fun report(action: CalibrationAction, points: List<CalibrationPoint>, profile: CalibrationProfile, failure: GestureFailure): String = (
        message(action, failure) +
            "\nprofile=${profile.key}; source=${if (points == action.points) "default-or-identical" else "saved-override"}; " +
            "default=${action.points}; effective=$points; acceptedParts=${failure.acceptedParts}\n${failure.detail}"
        )
        .take(CalibrationDiagnostics.MAX_REPORT_CHARS)
}
