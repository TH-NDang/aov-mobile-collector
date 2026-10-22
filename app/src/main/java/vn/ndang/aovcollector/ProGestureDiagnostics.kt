package vn.ndang.aovcollector

/** Per-attempt evidence. Accepted parts may already have changed the game; never imply safe replay. */
internal data class GestureFailure(val reason: String, val acceptedParts: Int = 0, val detail: String = "")

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
