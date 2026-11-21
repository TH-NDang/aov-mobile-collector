package vn.ndang.aovcollector

/** Real Pro adapter path, separate from Android I/O so refusal/preview ordering is regression-tested. */
internal class ProGestureAttempt(
    private val action: CalibrationAction,
    private val points: List<CalibrationPoint>,
    private val profile: CalibrationProfile,
    private val target: GameScreens.Word?,
    private val previewOnly: Boolean,
    private val alive: () -> Boolean,
    private val report: (String) -> Unit,
    private val finish: (String) -> Unit
) {
    fun run(
        verify: ((List<CalibrationPoint>, (Boolean) -> Unit) -> Unit)?,
        dispatch: ((GestureFailure) -> Unit, (Boolean) -> Unit) -> Unit,
        then: (Boolean) -> Unit
    ) {
        if (!alive()) return
        var rejection: GestureFailure? = null
        val completed: (Boolean) -> Unit = { ok ->
            if (!ok && alive()) {
                val refused = rejection ?: GestureFailure("unknown-outcome", detail = "No per-attempt dispatch result available")
                report(ProGestureDiagnostics.report(action, points, profile, refused))
                // Finish first: the reader's generic false callback must not overwrite this cause.
                finish(ProGestureDiagnostics.message(action, refused))
            }
            then(ok)
        }
        // Preview is repairable even when a saved semantic override is unsafe. The real editor's
        // Run/Next still validates the final points before its separate dispatch.
        if (!ProGestureSafety.canPrepare(action, points, target, previewOnly)) {
            rejection = GestureFailure("semantic-bounds")
            completed(false)
            return
        }
        val send = {
            if (alive()) dispatch({ rejection = it }, completed)
        }
        // Teaching performs this verification on the edited points after the preview pause instead.
        if (previewOnly || verify == null) {
            send()
        } else {
            verify(points) {
                if (it) {
                    send()
                } else {
                    rejection = GestureFailure("recommendation-changed")
                    completed(false)
                }
            }
        }
    }
}
