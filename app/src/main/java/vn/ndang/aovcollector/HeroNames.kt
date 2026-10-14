package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

object HeroNames {
    private const val COPIES=3
    /**
     * The hero name on an overview screenshot, or null. Run off the UI thread. Only the name region enters OCR.
     * ML Kit returns nothing for a name as short as Qi, even as clean black text on white; it does read the
     * same word within a longer line. When the first pass finds no name, the name is cut out as black on white
     * and drawn [COPIES] times along one line, and the reading most copies agree on is taken.
     * [log] gets the lines each pass read.
     */
    fun read(bitmap:Bitmap,log:(String)->Unit={}):String? {
        if(bitmap.width<=bitmap.height) return null
        fun region()=crop(bitmap,.73,.295,.245,.085)
        val first=ocr(region())
        log("Lượt 1: "+lines(first))
        val candidate=first?.textBlocks?.flatMap { it.lines }?.filter { (it.boundingBox?.height()?:0)>bitmap.height*.015 }
            // The crop can catch part of the form-switch icon left of the name, read as ". Flowborn".
            ?.maxByOrNull { it.boundingBox?.height()?:0 }?.text
        valid(candidate)?.let { return it }
        val line=region().let { try { repeated(it) } finally { it.recycle() } }?:return null
        val second=ocr(line.image)
        log("Lượt 2: "+lines(second))
        // Each copy's words, found by where they sit on the line.
        val words=second?.textBlocks?.flatMap { it.lines }?.flatMap { it.elements }.orEmpty()
        val copies=line.starts.map { start -> words.filter { (it.boundingBox?.centerX()?:-1) in start until start+line.width }.sortedBy { it.boundingBox?.left }.joinToString(" ") { it.text } }
        return copies.mapNotNull { valid(it) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
    private fun lines(text:Text?)=text?.textBlocks?.flatMap { it.lines }?.joinToString(" | ") { "${it.text} (cao ${it.boundingBox?.height()})" }?:"lỗi OCR"
    private fun valid(text:String?)=text?.trim { !it.isLetter() }
        ?.takeIf { it.length in 2..32 && it.count { c -> c.isLetter() }>=2 && it.matches(Regex("[\\p{L} '\u2019.\\-]+")) }
    /** A name drawn [COPIES] times along a line, each copy [width] pixels wide from its x in [starts]. */
    private class Line(val image:Bitmap,val starts:List<Int>,val width:Int)
    /**
     * The name in [region] as black on white, cut tight and repeated along a white line with a word gap between
     * copies. The name is the brightest thing in its region, so pixels brighter than halfway between the
     * background (the median) and the name (the brightest 0.5%) are the name. Null when there is no such contrast.
     */
    private fun repeated(region:Bitmap):Line? {
        val w=region.width;val h=region.height;val pixels=IntArray(w*h);region.getPixels(pixels,0,w,0,0,w,h)
        val luma=IntArray(pixels.size) { val c=pixels[it];(299*Color.red(c)+587*Color.green(c)+114*Color.blue(c))/1000 }
        val counts=IntArray(256);luma.forEach { counts[it]++ }
        fun percentile(p:Double):Int { var seen=0;for(v in 0..255) { seen+=counts[v];if(seen>=luma.size*p) return v };return 255 }
        val background=percentile(.5);val bright=percentile(.995)
        if(bright-background<60) return null
        val threshold=(background+bright)/2
        var left=w;var right=-1;var top=h;var bottom=-1
        for(y in 0 until h) for(x in 0 until w) if(luma[y*w+x]>threshold) { left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y) }
        if(right<0) return null
        val word=right-left+1;val height=bottom-top+1;val gap=height*2/5;val margin=height/2
        val width=2*margin+COPIES*word+(COPIES-1)*gap;val tall=height+2*margin
        val starts=List(COPIES) { margin+it*(word+gap) }
        val out=IntArray(width*tall) { Color.WHITE }
        for(start in starts) for(y in top..bottom) for(x in left..right) if(luma[y*w+x]>threshold) out[(y-top+margin)*width+start+x-left]=Color.BLACK
        return Line(Bitmap.createBitmap(out,width,tall,Bitmap.Config.ARGB_8888),starts,word)
    }
    /** The "owned/total" counter above the All-heroes list (14/129 means 129 heroes). */
    fun readListTotal(bitmap:Bitmap):Int? {
        if(bitmap.width<=bitmap.height) return null
        val text=ocr(crop(bitmap,.60,.015,.11,.075))?.text?:return null
        return Regex("(\\d+)\\s*/\\s*(\\d+)").find(text)?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 10..999 }
    }
    private fun crop(bitmap:Bitmap,x:Double,y:Double,w:Double,h:Double):Bitmap =
        Bitmap.createBitmap(bitmap,(bitmap.width*x).toInt(),(bitmap.height*y).toInt(),(bitmap.width*w).toInt(),(bitmap.height*h).toInt())
    /** Reads [image] and recycles it once ML Kit is done with it, which can be after a timeout here. */
    private fun ocr(image:Bitmap):Text? {
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val task=reader.process(InputImage.fromBitmap(image,0))
            task.addOnCompleteListener { image.recycle(); reader.close() }
            Tasks.await(task,8,TimeUnit.SECONDS)
        } catch(e:Exception) { null }
    }
    fun slug(name:String):String = Normalizer.normalize(name,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        .lowercase(Locale.ROOT).replace('đ','d').replace("'","").replace("\u2019","")
        .replace(Regex("[^a-z0-9]+"),"-").trim('-').take(48).ifEmpty { "chua-ro-ten" }
}
