package vn.ndang.aovcollector

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.widget.ImageView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object ImageCacheSmoke {
    fun check(instrumentation: Instrumentation, activity: MainActivity, fixture: File) {
        val decode = MainActivity::class.java.getDeclaredMethod("decodeThumb", ImageLoadCache.Request::class.java).apply {
            isAccessible =
                true
        }
        for (crop in listOf(true, false)) {
            val request = ImageLoadCache.Request(fixture.path, fixture.lastModified(), fixture.length(), 336, 198, crop)
            val displayed = decode.invoke(activity, request) as Bitmap
            check(displayed.width == 128 && displayed.height == 128) { "Small original was sampled down in gallery/viewer" }
            displayed.recycle()
        }
        val bind = MainActivity::class.java.getDeclaredMethod(
            "loadThumb",
            File::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            ImageView::class.java,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        lateinit var cold: ImageView
        instrumentation.runOnMainSync {
            cold = ImageView(activity)
            bind.invoke(activity, fixture, 336, 198, cold, true)
        }
        val deadline = SystemClock.uptimeMillis() + 10_000
        var original: Bitmap? = null
        while (original == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { original = (cold.drawable as? BitmapDrawable)?.bitmap }
            if (original == null) SystemClock.sleep(50)
        }
        check(original != null) { "Cold thumbnail never loaded" }
        // Returning to history invalidates old work, but must preserve warm thumbnails.
        val history = MainActivity::class.java.getDeclaredMethod("showHistory").apply { isAccessible = true }
        instrumentation.runOnMainSync { history.invoke(activity) }
        val executor = MainActivity::class.java.getDeclaredField("imageExecutor").apply {
            isAccessible = true
        }.get(activity) as ThreadPoolExecutor
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        check(entered.await(10, TimeUnit.SECONDS)) { "Thumbnail worker did not reach barrier" }
        try {
            instrumentation.runOnMainSync {
                val queued = executor.queue.size
                val warm = ImageView(activity)
                bind.invoke(activity, fixture, 336, 198, warm, true)
                check((warm.drawable as? BitmapDrawable)?.bitmap === original) { "Warm thumbnail waited for background work" }
                check(executor.queue.size == queued) { "Warm thumbnail enqueued unnecessary decode work" }
            }
        } finally {
            release.countDown()
        }
    }
}
