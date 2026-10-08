package vn.ndang.aovcollector

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

object HeroNames {
    // Run off the UI thread. Only the name region enters OCR.
    fun read(bitmap:Bitmap):String? {
        if(bitmap.width<=bitmap.height) return null
        val crop=Bitmap.createBitmap(bitmap,(bitmap.width*.73).toInt(),(bitmap.height*.295).toInt(),(bitmap.width*.245).toInt(),(bitmap.height*.085).toInt())
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val task=reader.process(InputImage.fromBitmap(crop,0))
            task.addOnCompleteListener { crop.recycle(); reader.close() }
            val result=Tasks.await(task,8,TimeUnit.SECONDS)
            val candidate=result.textBlocks.flatMap { it.lines }.filter { (it.boundingBox?.height()?:0)>bitmap.height*.015 }
                // The crop can catch part of the form-switch icon left of the name, read as ". Flowborn".
                .maxByOrNull { it.boundingBox?.height()?:0 }?.text?.trim { !it.isLetter() }
            candidate?.takeIf { it.length in 2..32 && it.count { c -> c.isLetter() }>=2 && it.matches(Regex("[\\p{L} '\u2019.\\-]+")) }
        } catch(e:Exception) { null }
    }
    fun slug(name:String):String = Normalizer.normalize(name,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        .lowercase(Locale.ROOT).replace('đ','d').replace("'","").replace("\u2019","")
        .replace(Regex("[^a-z0-9]+"),"-").trim('-').take(48).ifEmpty { "chua-ro-ten" }
}
