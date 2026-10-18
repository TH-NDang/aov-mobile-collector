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
     * Statistics navigation needs the small gold link at full resolution. Only retry an unresolved landing
     * page's header; large accessibility containers remain evidence of the page, never tap targets.
     */
    fun rankingWords(screen:Bitmap,nodes:List<GameScreens.Word> =emptyList()):List<GameScreens.Word> {
        val ordinary=nodes+words(screen)
        if(screen.width<=screen.height) return ordinary
        // Half-size whole-screen OCR can merge a menu row with a table number or split
        // "Đấu" from its mode. Read the small controls separately at native 2x scale.
        if(RankScreens.needsModeOcr(ordinary)) return ordinary+rankingModes(screen)
        return if(RankScreens.needsFullRankingOcr(ordinary)) ordinary+rankingHeader(screen) else ordinary
    }
    /** Mode button and all three menu rows, with line AND element boxes in screen coordinates. */
    fun rankingModes(screen:Bitmap)=rankingCrop(screen,RankScreens.MODE_LEFT,RankScreens.MODE_TOP,RankScreens.MODE_RIGHT,RankScreens.MODE_BOTTOM)
    /** Read the header at 2x native size and map both OCR lines and elements back into screen coordinates. */
    fun rankingHeader(screen:Bitmap)=rankingCrop(screen,RankScreens.LINK_LEFT,RankScreens.LINK_TOP,RankScreens.LINK_RIGHT,RankScreens.LINK_BOTTOM)
    private fun rankingCrop(screen:Bitmap,left:Double,top:Double,right:Double,bottom:Double):List<GameScreens.Word> {
        if(screen.width<=screen.height) return emptyList()
        val x0=(screen.width*left).toInt(); val y0=(screen.height*top).toInt()
        val width=(screen.width*(right-left)).toInt().coerceAtMost(screen.width-x0)
        val height=(screen.height*(bottom-top)).toInt().coerceAtMost(screen.height-y0)
        if(width<1||height<1) return emptyList()
        val crop=Bitmap.createBitmap(screen,x0,y0,width,height)
        val enlarged=Bitmap.createScaledBitmap(crop,width*2,height*2,true);crop.recycle()
        val text=ocr(enlarged)?:return emptyList()
        fun located(value:String,b:android.graphics.Rect)=GameScreens.Word(value,
            (x0+b.left/2.0)/screen.width,(y0+b.top/2.0)/screen.height,
            (x0+b.right/2.0)/screen.width,(y0+b.bottom/2.0)/screen.height)
        return text.textBlocks.flatMap { it.lines }.flatMap { line -> buildList {
            line.boundingBox?.let { add(located(line.text,it)) }
            line.elements.forEach { word -> word.boundingBox?.let { add(located(word.text,it)) } }
        } }
    }

    /**
     * Read ONLY the white hero title at the top of a Chess Encyclopedia detail drawer.
     * The large skill heading is below y=.38; cropping before OCR prevents mistaking
     * "Pháo đại" for "Violet" on the same popup. Called only on ambiguous full-screen OCR.
     */
    fun chessHeroTitle(screen:Bitmap):String? {
        if(screen.width<=screen.height) return null
        val x0=(screen.width*.345).toInt()
        val y0=(screen.height*.16).toInt()
        val width=(screen.width*.32).toInt().coerceAtMost(screen.width-x0)
        val height=(screen.height*.19).toInt().coerceAtMost(screen.height-y0)
        val crop=Bitmap.createBitmap(screen,x0,y0,width,height)
        val enlarged=Bitmap.createScaledBitmap(crop,width*2,height*2,true)
        crop.recycle()
        val text=ocr(enlarged)?:return null
        val located=text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val b=line.boundingBox?:return@mapNotNull null
            GameScreens.Word(line.text,
                (x0+b.left/2.0)/screen.width,(y0+b.top/2.0)/screen.height,
                (x0+b.right/2.0)/screen.width,(y0+b.bottom/2.0)/screen.height)
        }
        return ChessWiki.modalHeroTitle(located)
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
