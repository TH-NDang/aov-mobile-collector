package vn.ndang.aovcollector

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

object HeroNames {
    // Run off the UI thread. Only the name region enters OCR.
    fun read(bitmap:Bitmap):String? {
        if(bitmap.width<=bitmap.height) return null
        val text=ocr(bitmap,.73,.295,.245,.085)?:return null
        val candidate=text.textBlocks.flatMap { it.lines }.filter { (it.boundingBox?.height()?:0)>bitmap.height*.015 }
            // The crop can catch part of the form-switch icon left of the name, read as ". Flowborn".
            .maxByOrNull { it.boundingBox?.height()?:0 }?.text?.trim { !it.isLetter() }
        return candidate?.takeIf { it.length in 2..32 && it.count { c -> c.isLetter() }>=2 && it.matches(Regex("[\\p{L} '\u2019.\\-]+")) }
    }
    /** The "owned/total" counter above the All-heroes list (14/129 means 129 heroes). */
    fun readListTotal(bitmap:Bitmap):Int? {
        if(bitmap.width<=bitmap.height) return null
        val text=ocr(bitmap,.60,.015,.11,.075)?.text?:return null
        return Regex("(\\d+)\\s*/\\s*(\\d+)").find(text)?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 10..999 }
    }
    private fun ocr(bitmap:Bitmap,x:Double,y:Double,w:Double,h:Double):Text? {
        val crop=Bitmap.createBitmap(bitmap,(bitmap.width*x).toInt(),(bitmap.height*y).toInt(),(bitmap.width*w).toInt(),(bitmap.height*h).toInt())
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val task=reader.process(InputImage.fromBitmap(crop,0))
            task.addOnCompleteListener { crop.recycle(); reader.close() }
            Tasks.await(task,8,TimeUnit.SECONDS)
        } catch(e:Exception) { null }
    }
    fun slug(name:String):String = Normalizer.normalize(name,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        .lowercase(Locale.ROOT).replace('đ','d').replace("'","").replace("\u2019","")
        .replace(Regex("[^a-z0-9]+"),"-").trim('-').take(48).ifEmpty { "chua-ro-ten" }
}
