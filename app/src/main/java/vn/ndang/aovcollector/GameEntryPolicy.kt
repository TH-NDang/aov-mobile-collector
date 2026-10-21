package vn.ndang.aovcollector

/** One entry gate for built-ins and imported macros, without rewriting saved step indexes. */
object GameEntryPolicy {
    enum class Destination { LOBBY, LIST, CURRENT }

    /** A legacy/imported flag cannot opt out of the shared account requirement. */
    fun requiresAccount(macro: Macro) = macro.target == "com.garena.game.kgvn"

    fun destination(macro: Macro, index: Int): Destination {
        // A resumed coordinate cursor must never be replayed after an implicit navigation.
        if (index > 0) return Destination.CURRENT
        val first = macro.steps.getJSONObject(0)
        if (first.optString("type") == "enter") {
            return if (first.optString("to", "list") == "lobby") Destination.LOBBY else Destination.LIST
        }
        // Metadata such as collectionMode/builtinVersion is importable, not proof of a built-in.
        // Only a semantic navigator as the first actionable step authorizes changing the screen.
        val action = (0 until macro.steps.length()).map { macro.steps.getJSONObject(it).optString("type") }
            .firstOrNull { it != "wait" }
        return when (action) {
            "pick", "find" -> Destination.LIST
            "ranking", "board", "chess", "pro-builds" -> Destination.LOBBY
            else -> Destination.CURRENT
        }
    }
}
