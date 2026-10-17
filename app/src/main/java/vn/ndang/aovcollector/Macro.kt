package vn.ndang.aovcollector

import org.json.JSONObject
import java.security.MessageDigest

class Macro(val raw: String) {
    val json = JSONObject(raw)
    val name: String = json.getString("name")
    val target: String = json.getString("targetPackage")
    val orientation: String = json.optString("orientation", "landscape")
    val steps = json.getJSONArray("steps")
    val photoSteps:List<Int> by lazy { (0 until steps.length()).filter { steps.getJSONObject(it).getString("type")=="screenshot" } }
    /** Photos step [i] takes: a "skills" step at least the four icons' tabs, more for five icons or other forms. */
    fun photos(i:Int):Int = steps.getJSONObject(i).let { s -> when(s.getString("type")) { "screenshot" -> 1; "skills" -> if(s.optBoolean("detailOnly")) 4 else 8; else -> 0 } }
    val photoTotal:Int by lazy { (0 until steps.length()).sumOf { photos(it) } }
    /** True when the photo count can grow at run time, as with a "skills" step. */
    val photosVary:Boolean by lazy { (0 until steps.length()).any { steps.getJSONObject(it).getString("type") in setOf("skills","ranking","board","chess") } }
    val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    /** Timing ignores formatting and a learned card-position hint, which change between identical tasks. */
    val timingHash:String by lazy { RunTimes.profile(json) }
    init {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Tên macro chỉ dùng chữ, số, _ và -" }
        require(target == "com.garena.game.kgvn") { "V1 chỉ hỗ trợ Liên Quân VN" }
        require(orientation in listOf("landscape", "portrait")) { "orientation không hợp lệ" }
        require(steps.length() in 1..20000) { "Cần 1–20000 bước" }
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            when(s.getString("type")) {
                "tap" -> { ratio(s,"x"); ratio(s,"y") }
                "swipe" -> { listOf("x","y","toX","toY").forEach { ratio(s,it) }; require(s.optLong("ms",500) in 100..5000) }
                "wait" -> require(s.getLong("ms") in 0..60000) { "Thời gian chờ tối đa 60 giây" }
                "screenshot" -> require(s.getString("name").matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Tên ảnh không hợp lệ" }
                "back" -> Unit
                "pick" -> require(s.getInt("index") in 0..9999) { "pick: index từ 0 đến 9999" }
                "find" -> { require(s.getString("name").trim().length in 1..40) { "find: cần tên tướng (tối đa 40 ký tự)" }
                    require(s.optInt("hint",-1) in -1..9999) { "find: hint từ -1 đến 9999" }
                    require(s.getInt("skipTo") in i+1..steps.length()) { "find: skipTo phải là một bước sau nó" } }
                "enter" -> { require(i==0) { "enter chỉ dùng ở bước đầu" }; require(s.optString("to","list") in setOf("list","lobby")) { "enter: to là list hoặc lobby" } }
                "board" -> { require(s.getInt("heroes") in 1..BuiltInMacros.ALL) { "board: số tướng từ 1 đến ${BuiltInMacros.ALL}" }
                    val tabs=s.getJSONArray("tabs"); require(tabs.length()>0) { "board: chọn ít nhất một bảng" }
                    for(k in 0 until tabs.length()) { val t=tabs.getJSONObject(k)
                        require(BoardScreens.Tab.values().any { it.slug==t.getString("tab") }&&t.getInt("top") in 1..BoardScreens.ALL) { "board: bảng không hợp lệ" } } }
                "ranking" -> { val combos=s.optJSONArray("combos")
                    if(combos!=null) { require(combos.length()>0) { "ranking: chọn ít nhất một bảng" }
                        for(k in 0 until combos.length()) require(RankScreens.COMBOS.any { it.slug==combos.getString(k) }) { "ranking: bảng không hợp lệ" } } }
                "skills" -> require(s.getString("prefix").matches(Regex("[a-zA-Z0-9_-]{1,40}"))) { "skills: prefix không hợp lệ" }
                "chess" -> { require(json.optString("collectionMode")=="chess") { "chess: không đúng chế độ" }
                    require((json.optJSONArray("chessTopics")?.length()?:0) in 1..ChessWiki.TOPICS.size) { "chess: phải chọn ít nhất một mục" }
                    ChessWiki.Options((0 until json.getJSONArray("chessTopics").length()).map { json.getJSONArray("chessTopics").getString(it) }.toSet())
                }
                else -> error("Bước ${i+1}: type không hỗ trợ")
            }
        }
    }
    private fun ratio(s:JSONObject,k:String) { require(s.getDouble(k).isFinite() && s.getDouble(k) in 0.0..1.0) { "$k phải từ 0 đến 1" } }
}


/** Coordinates calibrated from the user's 2400x1080 VN hero menu. */
object BuiltInMacros {
    /** Bump when the generated steps change; selected built-in macros are then regenerated with the same settings. */
    const val VERSION=6
    /** "Tất cả": more cards than the game has; the run stops at the end of the list. */
    const val ALL=300
    /**
     * [fast] shortens the waits after taps (tab switches most); [detailOnly] skips the summary ("Tóm tắt")
     * shots of the passive and skills; [directSkills] taps the next skill icon while a skill panel is open,
     * which switches the panel, and closes it only after the last skill; the skills are read from the hero page
     * at run time ([SkillReader]), with heroes' other forms. [openGame], for the list only, starts with
     * an "enter" step that opens the game and goes through its popups to the All-heroes list ([GameEntry]);
     * there [checkAccount] makes sure the saved test account is in play, and [traceEntry] keeps each step for
     * review. All stay off unless chosen (the trace is on by default), so the default steps do not change.
     */
    fun create(heroName:String, count:Int = 1, batch:Boolean = false, fast:Boolean = false, detailOnly:Boolean = false, directSkills:Boolean = false,
               openGame:Boolean = false, checkAccount:Boolean = false, traceEntry:Boolean = true):String {
        require(count in 1..ALL)
        val steps=org.json.JSONArray()
        val hero=Hero(steps,fast,detailOnly,directSkills)
        val entering=batch&&openGame
        if(entering) steps.put(JSONObject().put("type","enter"))
        hero.waitFor(if(entering) 1000 else 3000)
        if(!batch) hero.collect("hero") else for(i in 0 until count) {
            // "pick" scrolls the list by measured amounts and taps card i; the run ends early at the end of the list.
            steps.put(JSONObject().put("type","pick").put("index",i)); hero.waitFor(if(fast) 2000 else 2700)
            hero.collect("hero-%03d".format(i+1)); hero.back()
        }
        val safeName=heroName.trim().take(80)
        return JSONObject().put("name",if(batch) "batch-heroes" else "single-hero")
            .put("targetPackage","com.garena.game.kgvn").put("orientation","landscape")
            .put("collectionMode",if(batch) "all" else "single").put("heroName",safeName)
            .put("heroCount",if(batch) count else 1).put("fast",fast).put("detailOnly",detailOnly).put("directSkills",directSkills).put("openGame",entering).put("checkAccount",entering&&checkAccount).put("traceEntry",traceEntry)
            .put("builtinVersion",VERSION).put("steps",steps).toString(2)
    }
    /** The steps that photograph the hero page open: overview, attributes, then the skills read at run time. */
    private class Hero(val steps:org.json.JSONArray,val fast:Boolean,val detailOnly:Boolean,val directSkills:Boolean) {
        val settle=if(fast) 600 else 900
        fun waitFor(ms:Int=settle) { steps.put(JSONObject().put("type","wait").put("ms",ms)) }
        fun tap(x:Double,y:Double,ms:Int=settle) { steps.put(JSONObject().put("type","tap").put("x",x).put("y",y)); waitFor(ms) }
        fun shot(name:String) { steps.put(JSONObject().put("type","screenshot").put("name",name)) }
        fun collect(prefix:String) {
            tap(.60,.82); shot("${prefix}-overview")
            tap(.095,.89); tap(.125,.765); waitFor(600); shot("${prefix}-attributes")
            tap(.60,.82); tap(.60,.82)
            // The skill row is read on the phone: four or five icons, and the other forms behind a ↻ button ([SkillReader]).
            steps.put(JSONObject().put("type","skills").put("prefix",prefix).put("detailOnly",detailOnly).put("directSkills",directSkills).put("fast",fast))
        }
        /** Back from the hero page to the list. */
        fun back() { tap(HeroGrid.BACK_X,HeroGrid.BACK_Y); waitFor(if(fast) 1000 else 1400) }
    }
    /**
     * Heroes by name ([names]), found on the All-heroes list ([HeroSearch]): first at the card [hints] gives (from
     * [HeroIndex], -1 when unknown), each name checked on the card. A hero not on the list is skipped: its "find"
     * step jumps to the next hero's ("skipTo"). Options as for the list ([create]).
     */
    fun named(names:List<String>,hints:List<Int> =names.map { -1 },fast:Boolean=false,detailOnly:Boolean=false,directSkills:Boolean=false,
              openGame:Boolean=false,checkAccount:Boolean=false,traceEntry:Boolean=true):String {
        val chosen=names.mapIndexed { i,n -> n.trim() to (hints.getOrNull(i)?.takeIf { it>=0 }?:-1) }.filter { it.first.isNotEmpty() }.distinctBy { HeroNames.slug(it.first) }
        val heroes=chosen.map { it.first }
        require(heroes.size in 1..ALL) { "Chọn ít nhất một tướng" }
        require(heroes.all { it.length<=40 }) { "Tên tướng tối đa 40 ký tự" }
        val steps=org.json.JSONArray()
        val hero=Hero(steps,fast,detailOnly,directSkills)
        if(openGame) steps.put(JSONObject().put("type","enter"))
        hero.waitFor(if(openGame) 1000 else 3000)
        for((i,pair) in chosen.withIndex()) {
            val find=JSONObject().put("type","find").put("name",pair.first).put("hint",pair.second)
            steps.put(find); hero.waitFor(if(fast) 2000 else 2700)
            hero.collect("hero-%03d".format(i+1)); hero.back()
            find.put("skipTo",steps.length())
        }
        return JSONObject().put("name","named-heroes")
            .put("targetPackage","com.garena.game.kgvn").put("orientation","landscape")
            .put("collectionMode","pick").put("heroName",heroes.joinToString(", ").take(80)).put("heroNames",org.json.JSONArray(heroes))
            .put("heroCount",heroes.size).put("fast",fast).put("detailOnly",detailOnly).put("directSkills",directSkills).put("openGame",openGame).put("checkAccount",openGame&&checkAccount).put("traceEntry",traceEntry)
            .put("builtinVersion",VERSION).put("steps",steps).toString(2)
    }
    /** The names a macro of heroes by name asks for, in order. */
    fun heroNames(json:JSONObject):List<String> { val a=json.optJSONArray("heroNames")?:return emptyList(); return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
    /**
     * The hero statistics ([RankingReader]) for the tables named in [combos] ([RankScreens.Combo.slug]): with
     * [openGame], the game is opened and left at the lobby first, as for the list ([checkAccount], [traceEntry] the same).
     */
    fun ranking(openGame:Boolean=false,checkAccount:Boolean=false,traceEntry:Boolean=true,combos:List<String> =RankScreens.COMBOS.map { it.slug }):String {
        require(combos.isNotEmpty()) { "Chọn ít nhất một bảng" }
        val steps=org.json.JSONArray()
        if(openGame) steps.put(JSONObject().put("type","enter").put("to","lobby"))
        steps.put(JSONObject().put("type","wait").put("ms",if(openGame) 1000 else 3000))
        steps.put(JSONObject().put("type","ranking").put("combos",org.json.JSONArray(combos)))
        return JSONObject().put("name","hero-ranking").put("targetPackage","com.garena.game.kgvn").put("orientation","landscape")
            .put("collectionMode","ranking").put("heroName","").put("openGame",openGame).put("checkAccount",openGame&&checkAccount).put("traceEntry",traceEntry)
            .put("rankCombos",org.json.JSONArray(combos)).put("builtinVersion",VERSION).put("steps",steps).toString(2)
    }
    /**
     * The players' ranking of each hero, "Chiến khu" ([BoardReader]): [heroes] of "Chọn tướng" in order, the tabs
     * [wanted], each down to its top players. With [openGame], the game is opened and left at the lobby first, as for
     * the list ([checkAccount], [traceEntry] the same).
     */
    fun board(openGame:Boolean=false,checkAccount:Boolean=false,traceEntry:Boolean=true,heroes:Int=ALL,wanted:List<BoardScreens.Want> =BoardScreens.DEFAULT):String {
        require(heroes in 1..ALL&&wanted.isNotEmpty())
        val tabs=org.json.JSONArray(); wanted.forEach { tabs.put(JSONObject().put("tab",it.tab.slug).put("top",it.top)) }
        val steps=org.json.JSONArray()
        if(openGame) steps.put(JSONObject().put("type","enter").put("to","lobby"))
        steps.put(JSONObject().put("type","wait").put("ms",if(openGame) 1000 else 3000))
        steps.put(JSONObject().put("type","board").put("heroes",heroes).put("tabs",tabs))
        return JSONObject().put("name","hero-board").put("targetPackage","com.garena.game.kgvn").put("orientation","landscape")
            .put("collectionMode","board").put("heroName","").put("heroCount",heroes).put("boardTabs",tabs)
            .put("openGame",openGame).put("checkAccount",openGame&&checkAccount).put("traceEntry",traceEntry)
            .put("builtinVersion",VERSION).put("steps",steps).toString(2)
    }
    /** Selectable Cờ Liên Quân encyclopedia: no section is implicitly collected. */
    fun chess(options:ChessWiki.Options):String {
        val steps=org.json.JSONArray()
        if(options.openGame) steps.put(JSONObject().put("type","enter").put("to","lobby"))
        steps.put(JSONObject().put("type","wait").put("ms",if(options.openGame) 1000 else 1500))
        steps.put(JSONObject().put("type","chess"))
        return JSONObject().put("name","chess-wiki")
            .put("targetPackage","com.garena.game.kgvn").put("orientation","landscape")
            .put("collectionMode","chess").put("heroName","")
            .put("fast",options.fast)
            .put("chessTopics",org.json.JSONArray(options.topics().map { it.key }))
            .put("chessHeroDetails",options.heroDetails)
            .put("chessSynergyDetails",options.synergyDetails).put("chessEquipmentDetails",options.equipmentDetails)
            .put("chessNativeGroups",org.json.JSONArray(options.nativeGroups.sorted()))
            .put("openGame",options.openGame).put("checkAccount",options.openGame&&options.checkAccount)
            .put("traceEntry",options.traceEntry).put("builtinVersion",VERSION)
            .put("steps",steps).toString(2)
    }
    fun chessOptions(json:JSONObject):ChessWiki.Options {
        val a=json.getJSONArray("chessTopics")
        return ChessWiki.Options((0 until a.length()).map { a.getString(it) }.toSet(),
            json.optBoolean("openGame"),json.optBoolean("checkAccount"),json.optBoolean("traceEntry",true),
            json.optBoolean("chessHeroDetails"),
            json.optBoolean("chessSynergyDetails"),json.optBoolean("chessEquipmentDetails"),
            (json.optJSONArray("chessNativeGroups")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()),
            json.optBoolean("fast",true))
    }

    /** The tabs a Chiến khu macro or its step asks for, with their top players; the default when it names none. */
    fun boardWants(json:JSONObject):List<BoardScreens.Want> {
        val tabs=json.optJSONArray("boardTabs")?:json.optJSONArray("tabs")?:return BoardScreens.DEFAULT
        return (0 until tabs.length()).mapNotNull { k -> tabs.optJSONObject(k)?.let { t -> BoardScreens.Tab.values().firstOrNull { it.slug==t.optString("tab") }?.let { BoardScreens.Want(it,t.optInt("top",BoardScreens.defaultTop(it))) } } }
            .ifEmpty { BoardScreens.DEFAULT }
    }
    /** The tables a ranking macro asks for, all when it names none. */
    fun rankCombos(json:JSONObject):List<RankScreens.Combo> { val names=json.optJSONArray("rankCombos")?:json.optJSONArray("combos")?:return RankScreens.COMBOS
        val wanted=(0 until names.length()).map { names.getString(it) }.toSet(); return RankScreens.COMBOS.filter { it.slug in wanted }.ifEmpty { RankScreens.COMBOS } }
    /**
     * Rough seconds per hero, read from the steps themselves: every wait, plus typical times measured for a tap
     * (hiding the panel first), a screenshot and finding the card in the list.
     */
    fun secondsPerHero(batch:Boolean,fast:Boolean,detailOnly:Boolean,directSkills:Boolean):Double {
        val steps=JSONObject(create("",1,batch,fast,detailOnly,directSkills)).getJSONArray("steps")
        // Step 0 is the wait before the run starts, once per run.
        return (1 until steps.length()).sumOf { i -> val s=steps.getJSONObject(i)
            when(s.getString("type")) { "wait" -> s.getLong("ms")/1000.0; "tap" -> .28; "screenshot" -> .45; "pick" -> 1.5; "find" -> 3.0
                // Four icons; heroes with five icons or other forms take longer.
                "skills" -> SkillReader.seconds(SkillReader.Options(detailOnly,directSkills,fast)); else -> 0.0 } }
    }
}

