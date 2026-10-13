package vn.ndang.aovcollector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * The ZIP save or import that is running. It is kept apart from the screen that started it, so it can go on in the
 * background: "Chạy nền" closes its dialog while a notification follows the progress, with a button to cancel and a
 * tap to open the dialog again. [FileJobService] keeps the app in the foreground and the CPU awake meanwhile.
 */
object FileJob {
    /** The progress dialog of a job, while one is open. Called on the main thread. */
    interface Dialog { fun progress();fun finished() }
    /** Extra of the intent that opens the app from the notification: show the running job's dialog. */
    const val SHOW="show-file-job"
    const val NOTIFICATION=41
    private const val RESULT=42
    private const val PROGRESS_CHANNEL="file-progress"
    private const val RESULT_CHANNEL="file-result"
    private val main=Handler(Looper.getMainLooper())
    private lateinit var app:Context
    @Volatile var running=false;private set
    @Volatile var cancelled=false;private set
    /** "Đang lưu ZIP" while running; [name], "Lưu ZIP", heads the result. */
    @Volatile var title="";private set
    private var name=""
    // Bytes done of the total (0 or less when unknown), a line about them and the file being worked on.
    @Volatile var done=0L;private set
    @Volatile var total=0L;private set
    @Volatile var line="";private set
    @Volatile var file="";private set
    private var started=0L;private var shownAt=0L;private var notifiedAt=0L
    /** Set on the main thread by the open dialog. */
    var dialog:Dialog?=null
    /** Set on the main thread by the screen in front; it gets the result. Without one the result is a notification. */
    var screen:((String)->Unit)?=null

    fun start(context:Context,title:String,name:String) {
        app=context.applicationContext;this.title=title;this.name=name
        running=true;cancelled=false;done=0;total=0;line="Đang chuẩn bị…";file=""
        started=SystemClock.elapsedRealtime();shownAt=0;notifiedAt=0
        CollectorService.libraryBusy=true
        val manager=notifications()
        manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL,"Tiến độ lưu và nhập ZIP",NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(RESULT_CHANNEL,"Lưu và nhập ZIP xong",NotificationManager.IMPORTANCE_DEFAULT))
        manager.cancel(RESULT)
        keepRunning()
    }
    /** Starts [FileJobService]. Android refuses it when the app is not in front; the job then runs on without it. */
    fun keepRunning() { if(running) try { app.startForegroundService(Intent(app,FileJobService::class.java)) } catch(_:Exception) {} }

    /** From the worker; the dialog shows it at most 5 times a second and the notification once a second. */
    fun report(done:Long,total:Long,text:String,file:String) {
        val now=SystemClock.elapsedRealtime();if(now-shownAt<200&&(total<=0||done<total)) return;shownAt=now
        val elapsed=now-started
        val eta=if(total>0&&done in 1 until total&&elapsed>2000) " · còn khoảng "+Format.duration(elapsed*(total-done)/done/1000) else ""
        this.done=done;this.total=total;this.file=file;if(!cancelled) line=text+eta
        val notify=now-notifiedAt>=1000;if(notify) notifiedAt=now
        main.post { if(running) { dialog?.progress();if(notify) notifyProgress() } }
    }
    fun cancel() { if(!running||cancelled) return;cancelled=true;line="Đang hủy…";main.post { if(running) { dialog?.progress();notifyProgress() } } }

    /** From the worker when the job has ended, with what to tell the user. */
    fun finish(result:String) {
        running=false
        main.post {
            CollectorService.libraryBusy=false
            FileJobService.current?.stop()
            val manager=notifications();manager.cancel(NOTIFICATION)
            dialog?.finished();dialog=null
            val shown=screen
            if(shown!=null) shown(result)
            else manager.notify(RESULT,Notification.Builder(app,RESULT_CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(name).setContentText(result).setStyle(Notification.BigTextStyle().bigText(result))
                .setAutoCancel(true).setContentIntent(open(app)).build())
        }
    }

    private fun notifications()=app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun notifyProgress() { notifications().notify(NOTIFICATION,notification(app)) }
    fun notification(context:Context):Notification {
        val share=if(total>0) (done*1000/total).toInt().coerceIn(0,1000) else 0
        val cancel=PendingIntent.getService(context,1,Intent(context,FileJobService::class.java).setAction(FileJobService.CANCEL),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context,PROGRESS_CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if(total>0) "$title · ${share/10}%" else title).setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(if(file.isEmpty()) line else "$line\n$file"))
            .setProgress(1000,share,total<=0).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setContentIntent(open(context))
            .apply { if(!cancelled) addAction(Notification.Action.Builder(null as Icon?,"Hủy",cancel).build()) }
            .apply { if(Build.VERSION.SDK_INT>=31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }
    private fun open(context:Context)=PendingIntent.getActivity(context,0,
        Intent(context,MainActivity::class.java).putExtra(SHOW,true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Keeps the app in the foreground, showing the job's notification, and the CPU awake while a ZIP is saved or imported. */
class FileJobService:Service() {
    companion object {
        const val CANCEL="vn.ndang.aovcollector.CANCEL_FILE_JOB"
        /** The service once it is in the foreground; used on the main thread. */
        var current:FileJobService?=null;private set
    }
    private var lock:PowerManager.WakeLock?=null
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==CANCEL) { FileJob.cancel();if(current==null) stopSelf(startId);return START_NOT_STICKY }
        // Android ends the app if a service started this way does not enter the foreground, even when the job is over.
        startForeground(FileJob.NOTIFICATION,FileJob.notification(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        current=this
        // The screen may go off during a long job; the CPU must keep going. Released when the job ends.
        if(lock==null) lock=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"aovcollector:zip").apply { acquire(3*60*60*1000L) }
        if(!FileJob.running) stop()
        return START_NOT_STICKY
    }
    fun stop() { current=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    // Android 15 limits data sync in the foreground to 6 hours a day; the job then goes on as an ordinary app.
    override fun onTimeout(startId:Int,fgsType:Int) { stop() }
    override fun onDestroy() { if(current===this) current=null;lock?.let { if(it.isHeld) it.release() };super.onDestroy() }
}
