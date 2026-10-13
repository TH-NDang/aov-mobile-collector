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
    /**
     * The hero name on an overview screenshot, or null. Run off the UI thread. Only the name region enters OCR.
     * ML Kit returns nothing for some short names over busy art (Qi); the region is then read again as black
     * text on white. [log] gets the lines each pass read.
     */
    fun read(bitmap:Bitmap,log:(String)->Unit={}):String? {
        if(bitmap.width<=bitmap.height) return null
        fun region()=crop(bitmap,.73,.295,.245,.085)
        val passes=listOf<()->Bitmap?>({ region() },{ region().let { try { contrast(it) } finally { it.recycle() } } })
        for((i,pass) in passes.withIndex()) {
            val text=ocr(pass()?:break)
            log("Lượt ${i+1}: "+(text?.textBlocks?.flatMap { it.lines }?.joinToString(" | ") { "${it.text} (cao ${it.boundingBox?.height()})" }?:"lỗi OCR"))
            name(text,bitmap.height)?.let { return it }
        }
        return null
    }
    private fun name(text:Text?,screenHeight:Int):String? {
        val candidate=text?.textBlocks?.flatMap { it.lines }?.filter { (it.boundingBox?.height()?:0)>screenHeight*.015 }
            // The crop can catch part of the form-switch icon left of the name, read as ". Flowborn".
            ?.maxByOrNull { it.boundingBox?.height()?:0 }?.text?.trim { !it.isLetter() }
        return candidate?.takeIf { it.length in 2..32 && it.count { c -> c.isLetter() }>=2 && it.matches(Regex("[\\p{L} '\u2019.\\-]+")) }
    }
    /**
     * [image] as black on white with a white margin: the name is the brightest thing in its region, so pixels
     * brighter than halfway between the background (the median) and the name (the brightest 0.5%) turn black.
     * Null when the region has no such contrast.
     */
    private fun contrast(image:Bitmap):Bitmap? {
        val w=image.width;val h=image.height;val pixels=IntArray(w*h);image.getPixels(pixels,0,w,0,0,w,h)
        val luma=IntArray(pixels.size) { val c=pixels[it];(299*Color.red(c)+587*Color.green(c)+114*Color.blue(c))/1000 }
        val counts=IntArray(256);luma.forEach { counts[it]++ }
        fun percentile(p:Double):Int { var seen=0;for(v in 0..255) { seen+=counts[v];if(seen>=luma.size*p) return v };return 255 }
        val background=percentile(.5);val bright=percentile(.995)
        if(bright-background<60) return null
        val threshold=(background+bright)/2;val pad=h/4;val width=w+2*pad
        val out=IntArray(width*(h+2*pad)) { Color.WHITE }
        for(y in 0 until h) for(x in 0 until w) if(luma[y*w+x]>threshold) out[(y+pad)*width+x+pad]=Color.BLACK
        return Bitmap.createBitmap(out,width,h+2*pad,Bitmap.Config.ARGB_8888)
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
