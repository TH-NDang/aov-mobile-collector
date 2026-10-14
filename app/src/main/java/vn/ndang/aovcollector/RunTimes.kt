package vn.ndang.aovcollector

import android.content.SharedPreferences

/**
 * How long tasks took when they ran from start to end without a pause, kept on the phone to estimate the next
 * runs: seconds per hero of a list or a single hero (for its speed options), per statistics table, and for the
 * way into the game. Each new clean run moves the kept time halfway towards its own, so one odd run does not rule.
 */
object RunTimes {
    const val ENTRY="time:entry"
    const val TABLE="time:ranking"
    fun hero(batch:Boolean,fast:Boolean,detailOnly:Boolean,directSkills:Boolean)=
        "time:${if(batch) "all" else "single"}:"+listOf(fast,detailOnly,directSkills).joinToString("") { if(it) "1" else "0" }
    fun get(prefs:SharedPreferences,key:String):Double? = prefs.getFloat(key,-1f).takeIf { it>0 }?.toDouble()
    fun record(prefs:SharedPreferences,key:String,seconds:Double) {
        if(!(seconds>0)) return
        prefs.edit().putFloat(key,blend(get(prefs,key),seconds).toFloat()).apply()
    }
    fun blend(kept:Double?,seconds:Double)=if(kept==null) seconds else (kept+seconds)/2
    /**
     * Seconds left for [left] more units when this run took [spent] seconds for [done]: the [kept] pace of earlier
     * clean runs at first, then more and more this run's own, all of it after ten units. Null with neither.
     */
    fun remaining(left:Int,done:Int,spent:Double,kept:Double?):Double? {
        val own=if(done>0&&spent>0) spent/done else null
        val pace=when { kept==null -> own; own==null -> kept; else -> minOf(1.0,done/10.0).let { w -> w*own+(1-w)*kept } }?:return null
        return maxOf(0,left)*pace
    }
}
