package vn.ndang.aovcollector

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local diagnostics only: crashes and interrupted runs, shown in the app so the user can copy them. */
object CrashLog {
    @Volatile private var installed=false
    private const val LIMIT=64*1024
    fun file(c:Context)=File(c.filesDir,"crash-log.txt")
    fun install(c:Context) {
        if(installed) return; installed=true
        val app=c.applicationContext;val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,e -> record(app,"Ứng dụng bị lỗi (luồng ${thread.name})",e);previous?.uncaughtException(thread,e) }
    }
    @Synchronized fun record(c:Context,title:String,e:Throwable?) {
        try {
            val version=try { c.packageManager.getPackageInfo(c.packageName,0).versionName } catch(_:Exception) { "?" }
            val entry="── ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())} · v$version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n$title\n"+(e?.stackTraceToString()?.take(6000)?:"")+"\n"
            val log=file(c);val text=(if(log.exists()) log.readText() else "")+entry
            log.writeText(if(text.length>LIMIT) text.takeLast(LIMIT) else text)
        } catch(_:Throwable) {}
    }
}
