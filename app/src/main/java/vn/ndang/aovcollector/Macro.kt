package vn.ndang.aovcollector

import org.json.JSONObject
import java.security.MessageDigest

class Macro(val raw: String) {
    val json = JSONObject(raw)
    val name: String = json.getString("name")
    val target: String = json.getString("targetPackage")
    val orientation: String = json.optString("orientation", "landscape")
    val steps = json.getJSONArray("steps")
    val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    init {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Tên macro chỉ dùng chữ, số, _ và -" }
        require(target == "com.garena.game.kgvn") { "V1 chỉ hỗ trợ Liên Quân VN" }
        require(orientation in listOf("landscape", "portrait")) { "orientation không hợp lệ" }
        require(steps.length() in 1..2000) { "Cần 1–2000 bước" }
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            when(s.getString("type")) {
                "tap" -> { ratio(s,"x"); ratio(s,"y") }
                "swipe" -> { listOf("x","y","toX","toY").forEach { ratio(s,it) }; require(s.optLong("ms",500) in 100..5000) }
                "wait" -> require(s.getLong("ms") in 0..60000) { "Thời gian chờ tối đa 60 giây" }
                "screenshot" -> require(s.getString("name").matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Tên ảnh không hợp lệ" }
                "back" -> Unit
                else -> error("Bước ${i+1}: type không hỗ trợ")
            }
        }
    }
    private fun ratio(s:JSONObject,k:String) { require(s.getDouble(k).isFinite() && s.getDouble(k) in 0.0..1.0) { "$k phải từ 0 đến 1" } }
}
