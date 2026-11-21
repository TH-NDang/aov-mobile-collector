package vn.ndang.aovcollector

/** Internal routing token never becomes the exported screenshot name. */
object ProCaptureFiles {
    private val token = Regex("pro__([a-z0-9-]{1,60})__([a-zA-Z0-9_-]{1,100})")

    fun hero(name: String): String? = token.matchEntire(name)?.groupValues?.get(1)

    fun basename(name: String): String? = token.matchEntire(name)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
}
