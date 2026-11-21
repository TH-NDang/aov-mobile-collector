package vn.ndang.aovcollector

/** Keep the complete status visible while its next disk snapshot is loading. Main-thread owned. */
internal class CaptureProgressStatus {
    private var run: String? = null
    private var text = ""

    fun begin(nextRun: String, line: String): String? {
        if (run == nextRun) return null
        run = nextRun
        text = line
        return line
    }

    fun complete(snapshotRun: String, details: String): String? {
        if (run != snapshotRun || text == details) return null
        text = details
        return details
    }

    fun clear() {
        run = null
        text = ""
    }
}
