package vn.ndang.aovcollector

import java.util.Locale

/** Sizes and durations as shown to the user, in Vietnamese. */
object Format {
    private val vi=Locale("vi","VN")
    private fun unit(bytes:Long)=when { bytes>=1L shl 30 -> 30; bytes>=1L shl 20 -> 20; bytes>=1L shl 10 -> 10; else -> 0 }
    private fun number(bytes:Long,shift:Int):String { val v=bytes.toDouble()/(1L shl shift)
        val digits=when(shift) { 30 -> if(v<10) 2 else 1; 20 -> if(v<10) 1 else 0; else -> 0 }
        return String.format(vi,"%.${digits}f",v) }
    private fun name(shift:Int)=when(shift) { 30 -> "GB"; 20 -> "MB"; 10 -> "KB"; else -> "B" }
    /** Bytes in the largest fitting unit with a Vietnamese decimal comma: "850 KB", "4,2 MB", "24 MB", "2,61 GB". */
    fun size(bytes:Long)=number(bytes,unit(bytes))+" "+name(unit(bytes))
    /** Progress in the unit of [total]: "0,85/2,61 GB". */
    fun sizes(done:Long,total:Long)=unit(total).let { number(done,it)+"/"+number(total,it)+" "+name(it) }
    /** Rough duration: "45 giây", "12 phút", "1 giờ 20 phút". */
    fun duration(seconds:Long):String { if(seconds<60) return "${maxOf(1,seconds)} giây"; val m=(seconds+30)/60
        return if(m<60) "$m phút" else if(m%60==0L) "${m/60} giờ" else "${m/60} giờ ${m%60} phút" }
}
