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
    val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
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
                else -> error("Bước ${i+1}: type không hỗ trợ")
            }
        }
    }
    private fun ratio(s:JSONObject,k:String) { require(s.getDouble(k).isFinite() && s.getDouble(k) in 0.0..1.0) { "$k phải từ 0 đến 1" } }
}


/** Coordinates calibrated from the user's 2400x1080 VN hero menu. */
object BuiltInMacros {
    /** Bump when the generated steps change; selected built-in macros are then regenerated with the same settings. */
    const val VERSION=2
    fun create(heroName:String, count:Int = 1, batch:Boolean = false):String {
        require(count in 1..200)
        val steps=org.json.JSONArray()
        fun waitFor(ms:Int=900) { steps.put(JSONObject().put("type","wait").put("ms",ms)) }
        fun tap(x:Double,y:Double) { steps.put(JSONObject().put("type","tap").put("x",x).put("y",y)); waitFor() }
        fun shot(name:String) { steps.put(JSONObject().put("type","screenshot").put("name",name)) }
        fun collect(prefix:String) {
            tap(.60,.82); shot("${prefix}-overview")
            tap(.095,.89); tap(.125,.765); waitFor(600); shot("${prefix}-attributes")
            tap(.60,.82); tap(.60,.82)
            val skillNames=listOf("passive","skill-1","skill-2","skill-3")
            val skillX=listOf(.093,.138,.181,.225)
            for(i in skillNames.indices) {
                tap(skillX[i],.605); tap(.817,.05); shot("${prefix}-${skillNames[i]}-summary")
                tap(.878,.05); shot("${prefix}-${skillNames[i]}-detail"); tap(.60,.82)
            }
        }
        waitFor(3000)
        if(!batch) collect("hero") else for(i in 0 until count) {
            // "pick" scrolls the list by measured amounts and taps card i; the run ends early at the end of the list.
            steps.put(JSONObject().put("type","pick").put("index",i)); waitFor(2700)
            collect("hero-%03d".format(i+1))
            tap(.09,.05); waitFor(1400)
        }
        val safeName=heroName.trim().take(80)
        return JSONObject().put("name",if(batch) "batch-heroes" else "single-hero")
            .put("targetPackage","com.garena.game.kgvn").put("orientation","landscape")
            .put("collectionMode",if(batch) "all" else "single").put("heroName",safeName)
            .put("heroCount",if(batch) count else 1).put("builtinVersion",VERSION).put("steps",steps).toString(2)
    }
}
