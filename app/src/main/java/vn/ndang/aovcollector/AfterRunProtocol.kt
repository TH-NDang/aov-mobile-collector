package vn.ndang.aovcollector

/** Evidence accepted before a completed run may send its configured ChatGPT command. */
object AfterRunProtocol {
    const val DRIVE = "com.google.android.apps.docs"
    const val CHATGPT = "com.openai.chatgpt"
    const val DOT = "dot của bạn"

    fun driveCompleted(packageName: String, text: String, filename: String): Boolean {
        if (packageName != DRIVE || filename.isBlank()) return false
        val exact = Regex("(?<![A-Za-z0-9_.-])" + Regex.escape(filename) + "(?![A-Za-z0-9_.-])", RegexOption.IGNORE_CASE)
        if (!exact.containsMatchIn(text)) return false
        val plain = GameScreens.plain(text.replace(filename, "", ignoreCase = true))
        if (listOf(
                "uploading", "waitingtoupload", "uploadfailed", "failedtoupload", "dangtailen",
                "chotailen", "khongthetailen", "tailenthatbai", "cancelled", "dahuy", "khong", "chua", "failed", "error", "incomplete", "unsuccessful"
            ).any {
                it in
                    plain
            }
        ) {
            return false
        }
        return listOf(
            "uploaded",
            "uploadcomplete",
            "uploadsuccessful",
            "datailen",
            "tailenhoantat",
            "tailenthanhcong",
            "dataixong"
        ).any { it in plain }
    }

    fun dot(text: String) = GameScreens.plain(text) in setOf("dotcuaban", "yourdot")

    fun send(text: String) = GameScreens.plain(text) in
        setOf("gui", "guitinnhan", "guicaulenh", "send", "sendmessage", "sendprompt")

    fun navigation(text: String) = GameScreens.plain(text) in setOf(
        "opennavigationmenu", "opennavigationdrawer", "opensidebar", "openmenu", "monavigationmenu",
        "mothanhben", "momenu", "menudieuhuong", "navigationmenu", "sidebar"
    )
}
