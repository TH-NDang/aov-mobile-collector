package vn.ndang.aovcollector

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Copies confirmed native saves without blocking gestures or waiting for Activity.onResume. */
object GamePhotoSync {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var queued:Runnable?=null
    @Volatile var busy=false; private set
    @Volatile var revision=0L; private set

    fun request(context:Context) {
        val app=context.applicationContext
        main.post { schedule(app,500) }
    }

    // All scheduling and the library lock are handled on the UI thread, like file management.
    private fun schedule(app:Context,delay:Long) {
        queued?.let { main.removeCallbacks(it) }
        queued=Runnable {
            queued=null
            if(busy) return@Runnable // The current worker schedules the next attempt.
            if(CollectorService.libraryBusy||FileJob.running) {
                schedule(app,3000);return@Runnable
            }
            busy=true
            worker.execute {
                var pending=false
                try {
                    val run=GamePhotoSession.pendingRun(app)
                    if(run!=null) {
                        val result=GamePhotoSession.importNew(app,run)
                        if(result.copied>0) revision++
                    }
                    pending=GamePhotoSession.hasPending(app)
                } catch(e:Exception) {
                    CrashLog.record(app,"Chưa chép được ảnh Save Photo từ Pictures",e)
                    pending=GamePhotoImport.hasSource(app)
                } finally {
                    main.post {
                        busy=false
                        // Providers may publish the new file later than the Sure popup disappears.
                        if(queued==null&&pending) schedule(app,30_000)
                    }
                }
            }
        }.also { main.postDelayed(it,delay) }
    }
}
