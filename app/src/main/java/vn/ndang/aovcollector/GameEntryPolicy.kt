package vn.ndang.aovcollector

/** One entry gate for built-ins and imported macros, without rewriting saved step indexes. */
object GameEntryPolicy {
    enum class Destination { LOBBY, LIST, CURRENT }

    /** A legacy/imported flag cannot opt out of the shared account requirement. */
    fun requiresAccount(macro: Macro): Boolean = macro.target == "com.garena.game.kgvn" &&
        !(
            macro.json.optString("collectionMode") == "event-invite" &&
                macro.name == "anniversary-invite" &&
                macro.steps.length() == 1 &&
                macro.steps.getJSONObject(0).optString("type") == "event-invite"
            )

    /** Steps that find their own way from the lobby and skip what their run already saved. */
    private val fromLobby = setOf("ranking", "board", "chess", "pro-builds")

    /** True when the step being resumed at [index] can safely start again from the lobby. */
    fun resumesFromLobby(macro: Macro, index: Int): Boolean =
        (index until macro.steps.length()).map { macro.steps.getJSONObject(it).optString("type") }
            .firstOrNull { it != "wait" } in fromLobby

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
            in fromLobby, "event-invite" -> Destination.LOBBY
            else -> Destination.CURRENT
        }
    }
}
