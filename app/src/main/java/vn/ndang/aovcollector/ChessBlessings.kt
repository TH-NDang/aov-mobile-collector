package vn.ndang.aovcollector

import android.graphics.Bitmap
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Blessing identity follows the visible cyan title, never a moving patch of icon pixels. */
object ChessBlessings {
    const val PREFIX="blessing-title-v1:"
    private val GROUPS=setOf("chuc-phuc-thuong","chuc-phuc-huyen-thoai-su-thi","chuc-phuc-truyen-thuyet")
    fun isGroup(group:String)=group in GROUPS
    fun key(title:String)=PREFIX+Normalizer.normalize(title,Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace('đ','d')
        .replace("'","").replace("\u2019","").replace(Regex("[^a-z0-9]+"),"-").trim('-')
    fun isKey(value:String)=value.startsWith(PREFIX)&&value.removePrefix(PREFIX).matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))

    /** A recognized title needs the same recognized title in history; artwork alone proves no name. */
    fun sameSave(group:String,previous:Set<String>,requested:Set<String>):Boolean {
        if(ChessBlessings.isGroup(group)) {
            val oldNames=previous.filter(::isKey).toSet()
            val newNames=requested.filter(::isKey).toSet()
            if(newNames.isNotEmpty()) return oldNames.any { it in newNames }
        }
        return previous.any { it in requested }
    }

    /** Grey body copy and blue card backing must not be mistaken for a blessing's cyan heading. */
    fun cyan(pixel:Int):Boolean {
        val r=pixel shr 16 and 255;val g=pixel shr 8 and 255;val b=pixel and 255
        return g>=115&&b>=165&&r<135&&g-r>=45&&b-r>=75
    }
    private fun white(pixel:Int):Boolean {
        val r=pixel shr 16 and 255;val g=pixel shr 8 and 255;val b=pixel and 255
        return minOf(r,g,b)>=175&&maxOf(r,g,b)-minOf(r,g,b)<65
    }

    fun titleVisible(bitmap:Bitmap,word:GameScreens.Word):Boolean {
        val x0=(word.left*bitmap.width).toInt().coerceIn(0,bitmap.width-1)
        val x1=(word.right*bitmap.width).toInt().coerceIn(x0+1,bitmap.width)
        val y0=(word.top*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
        val y1=(word.bottom*bitmap.height).toInt().coerceIn(y0+1,bitmap.height)
        var colored=0;var body=0
        for(y in y0 until y1 step max(1,(y1-y0)/15))
            for(x in x0 until x1 step max(1,(x1-x0)/80)) {
                val p=bitmap.getPixel(x,y)
                if(cyan(p)) colored++ else if(white(p)) body++
            }
        return colored>=4&&colored>body
    }

    /** Locate the whole white pictogram next to a title; reject clipped icons at either edge. */
    fun iconY(bitmap:Bitmap,x:Double,titleY:Double):Double? =
        iconY(bitmap.width,bitmap.height,x,titleY,bitmap::getPixel)

    fun iconY(width:Int,height:Int,x:Double,titleY:Double,pixel:(Int,Int)->Int):Double? {
        val y0=((titleY-.10)*height).toInt().coerceAtLeast(0)
        val y1=((titleY+.14)*height).toInt().coerceAtMost(height-1)
        val x0=((x-.042)*width).toInt().coerceAtLeast(0)
        val x1=((x+.042)*width).toInt().coerceAtMost(width-1)
        val rows=ArrayList<Int>()
        for(y in y0..y1) {
            var count=0
            for(xx in x0..x1 step max(1,width/1100)) if(white(pixel(xx,y))) count++
            if(count>=3) rows.add(y)
        }
        if(rows.isEmpty()) return null
        val first=rows.first().toDouble()/height
        val last=rows.last().toDouble()/height
        if(first<.265||last>.975||last-first !in .065.. .22||
            rows.first()<=y0+1||rows.last()>=y1-1) return null
        return (first+last)/2
    }

    /** Pure geometry/identity stage, also used by deterministic scroll and OCR regression tests. */
    fun tiles(words:List<GameScreens.Word>,isTitle:(GameScreens.Word)->Boolean,
              iconY:(Double,Double)->Double?):List<ChessWiki.Tile> {
        val headings=words.filter { word ->
            word.top>=.275&&word.bottom<=.965&&word.text.count { it.isLetter() }>=3&&
                word.text.length<=70&&
                ((word.left in .325.. .565&&word.right<=.575)||
                 (word.left in .66.. .915&&word.right<=.94))&&isTitle(word)
        }.sortedWith(compareBy({it.y},{it.left}))
        val rows=ArrayList<MutableList<GameScreens.Word>>()
        for(word in headings) {
            val column=if(word.left<.60) 0 else 1
            val row=rows.firstOrNull { group ->
                (if(group.first().left<.60) 0 else 1)==column&&abs(group.first().y-word.y)<.018
            }
            if(row==null) rows.add(arrayListOf(word)) else row.add(word)
        }
        val candidates=rows.mapNotNull { row ->
            val parts=row.distinctBy { it.text to it.left }.sortedBy { it.left }
            val title=parts.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+")," ")
            val x=if(parts.first().left<.60) .275 else .615
            val y=iconY(x,parts.map { it.y }.average())?:return@mapNotNull null
            ChessWiki.Tile(x,y,key(title),title)
        }
        val icons=ArrayList<MutableList<ChessWiki.Tile>>()
        for(tile in candidates) {
            val icon=icons.firstOrNull { it.first().x==tile.x&&abs(it.first().y-tile.y)<.025 }
            if(icon==null) icons.add(arrayListOf(tile)) else icon.add(tile)
        }
        val merged=icons.map { lines ->
            val title=lines.mapNotNull { it.label }.distinct().joinToString(" ")
            lines.first().copy(id=key(title),label=title)
        }.distinctBy { it.id }.sortedBy { it.y }
        val cardRows=ArrayList<MutableList<ChessWiki.Tile>>()
        for(tile in merged) {
            val row=cardRows.firstOrNull { abs(it.first().y-tile.y)<.075 }
            if(row==null) cardRows.add(arrayListOf(tile)) else row.add(tile)
        }
        return cardRows.flatMap { it.sortedBy { tile -> tile.x } }
    }
}
