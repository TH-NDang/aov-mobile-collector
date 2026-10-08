package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File

object Ui {
    val ink=Color.rgb(29,35,56)
    val muted=Color.rgb(103,113,134)
    val accent=Color.rgb(92,73,220)
    val pale=Color.rgb(239,236,255)
    val canvas=Color.rgb(246,247,251)
    val border=Color.rgb(228,232,241)
    val danger=Color.rgb(185,48,70)
    fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    fun shape(color:Int,radius:Int=18,stroke:Int=Color.TRANSPARENT)=GradientDrawable().apply {
        setColor(color); cornerRadius=radius.toFloat(); setStroke(1,stroke)
    }
    fun stack(c:Context)=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL }
    fun card(c:Context)=stack(c).apply {
        setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16)); background=shape(Color.WHITE,dp(c,20),border)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(c,14) }
    }
    fun text(c:Context,value:String,size:Float=15f,color:Int=ink,bold:Boolean=false)=TextView(c).apply {
        text=value; textSize=size; setTextColor(color); if(bold) typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        setLineSpacing(dp(c,3).toFloat(),1f); setPadding(0,dp(c,4),0,dp(c,5))
    }
    fun button(c:Context,value:String,primary:Boolean=false,dangerous:Boolean=false,action:()->Unit)=Button(c).apply {
        text=value; isAllCaps=false; textSize=14f; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        setTextColor(if(primary) Color.WHITE else if(dangerous) danger else accent)
        background=shape(if(primary) accent else if(dangerous) Color.rgb(255,240,243) else pale,dp(c,13))
        minHeight=0; minimumHeight=0; minWidth=0; minimumWidth=0; setPadding(dp(c,12),0,dp(c,12),0)
        layoutParams=LinearLayout.LayoutParams(-1,dp(c,48)).apply { topMargin=dp(c,10) }
        setOnClickListener { action() }
    }
    fun field(c:Context,hintText:String,value:String="",numeric:Boolean=false)=EditText(c).apply {
        hint=hintText; setText(value); textSize=16f; setTextColor(ink); setHintTextColor(muted)
        setSingleLine(true); setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10))
        background=shape(canvas,dp(c,12),border)
        if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER
        layoutParams=LinearLayout.LayoutParams(-1,dp(c,52)).apply { topMargin=dp(c,8); bottomMargin=dp(c,8) }
    }
}

object MacroStore {
    fun current(c:Context)=File(c.filesDir,"macro.json")
    fun archive(c:Context,raw:String) {
        val m=Macro(raw)
        val folder=File(c.filesDir,"macros").apply { mkdirs() }
        File(folder,"${m.hash}.json").writeText(raw)
    }
    fun saved(c:Context):List<File> = File(c.filesDir,"macros").listFiles()?.filter { it.extension=="json" }?.sortedByDescending { it.lastModified() }.orEmpty()
    fun title(m:Macro):String = when(m.json.optString("collectionMode")) { "single" -> "Một tướng"; "all" -> "Danh sách tướng"; else -> "Macro đã nhập" }
    fun description(m:Macro):String = when(m.json.optString("collectionMode")) {
        "single" -> m.json.optString("heroName","").ifBlank { "Tướng đang mở · 10 ảnh" }
        "all" -> "${m.json.optInt("heroCount",1)} ô tướng · cuộn có đo, dừng ở cuối danh sách"
        else -> m.name
    }
    fun instruction(m:Macro):String = if(m.json.optString("collectionMode")=="all") "Mở danh sách Tất cả tướng; app tự đưa về đầu danh sách." else if(m.json.optString("collectionMode")=="single") "Mở trang chi tiết tướng, đóng bảng mô tả chiêu." else "Mở đúng màn hình bắt đầu của macro."
}
