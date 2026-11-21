package vn.ndang.aovcollector

import java.util.Locale

/** Numeric content evidence only: no gallery names, paths, IDs or image bytes. */
internal object NativeReceiptDiagnostics {
    fun describe(report: NativePhotoReceipt.MatchResult): String {
        fun number(value: Double?) = value?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "unknown"
        return "${report.width}x${report.height}, match=${report.matched}, " +
            "rgb=${number(report.bestRgbError)}, close=${number(report.bestCloseFraction)}, corr=${number(report.bestCorrelation)}, " +
            "coarse=${report.coarseComparisons}, refined=${report.refinedComparisons}, fine=${report.fineComparisons}"
    }
}
