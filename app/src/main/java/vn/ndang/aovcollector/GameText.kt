package vn.ndang.aovcollector

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/** Text on game screens, read on the phone by the bundled ML Kit. Run off the UI thread. */
object GameText {
    /** The lines of text on [screen], read at half size, with their boxes as fractions of the screen. */
    fun words(screen:Bitmap):List<GameScreens.Word> {
        val w=screen.width/2; val h=screen.height/2
        val text=ocr(Bitmap.createScaledBitmap(screen,w,h,true))?:return emptyList()
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { b ->
            GameScreens.Word(line.text,b.left.toDouble()/w,b.top.toDouble()/h,b.right.toDouble()/w,b.bottom.toDouble()/h) } }
    }
    /**
     * The account UID the game prints faintly at the bottom centre of most screens ("UID: 1751448330…"), or null.
     * The line is small and dim: it is read cut out, three times larger, as black on white.
     */
    fun uid(screen:Bitmap):String? {
        val x0=(.30*screen.width).toInt(); val y0=(.955*screen.height).toInt()
        val w=(.40*screen.width).toInt(); val h=screen.height-y0
        val pixels=IntArray(w*h); screen.getPixels(pixels,0,w,x0,y0,w,h)
        val luma=IntArray(pixels.size) { val c=pixels[it];(299*Color.red(c)+587*Color.green(c)+114*Color.blue(c))/1000 }
        val sorted=luma.sorted(); val background=sorted[sorted.size/2]; val bright=sorted[(sorted.size*.995).toInt().coerceAtMost(sorted.size-1)]
        if(bright-background<20) return null
        val threshold=(background+bright)/2
        val ink=Bitmap.createBitmap(IntArray(pixels.size) { if(luma[it]>threshold) Color.BLACK else Color.WHITE },w,h,Bitmap.Config.ARGB_8888)
        val large=Bitmap.createScaledBitmap(ink,w*3,h*3,true); ink.recycle()
        return ocr(large)?.text?.let { GameScreens.uidIn(it) }
    }
    /** Reads [image] and recycles it once ML Kit is done with it, which can be after a timeout here. */
    private fun ocr(image:Bitmap):Text? {
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val task=reader.process(InputImage.fromBitmap(image,0))
            task.addOnCompleteListener { image.recycle(); reader.close() }
            Tasks.await(task,8,TimeUnit.SECONDS)
        } catch(e:Exception) { null }
    }
}
