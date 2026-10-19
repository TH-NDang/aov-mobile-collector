package vn.ndang.aovcollector

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only preview first. Selection and a separate, explicit final confirmation are mandatory. */
object GamePhotoCleanupDialog {
    const val REQUEST_FOLDER = 18
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val thumbnails = Executors.newSingleThreadExecutor()

    private data class PendingGrant(val runId: String, val tree: String)

    private var pendingGrant: PendingGrant? = null

    fun show(activity: Activity, runId: String) {
        if (!alive(activity)) return
        work(activity, "Đang đối chiếu ảnh Pictures…", { allow ->
            GamePhotoCleanup.preview(activity.applicationContext, runId, allow)
        }) { preview ->
            if (preview.candidates.isEmpty()) {
                message(
                    activity,
                    "Chưa có ảnh đủ điều kiện dọn. Chỉ ảnh gốc của lượt này đã được chép đủ byte và kiểm tra SHA-256 mới xuất hiện; ảnh lỗi, chưa đồng bộ, đã thay đổi hoặc thiếu bản lưu sẽ được giữ lại."
                )
            } else {
                showPreview(activity, preview)
            }
        }
    }

    private fun showPreview(activity: Activity, preview: GamePhotoCleanup.Preview) {
        // Deliberately no select-all and no remembered selection across reopen/regrant.
        val selected = linkedSetOf<String>()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 18), Ui.dp(activity, 8), Ui.dp(activity, 18), 0)
        }
        body.addView(
            TextView(activity).apply {
                text =
                    "${preview.candidates.size} ảnh nguồn đã khớp bản lưu trong lượt. Chọn từng ảnh muốn xóa khỏi Pictures. Bản trong lượt sẽ được giữ."
                textSize = 14f
                setPadding(0, 0, 0, Ui.dp(activity, 10))
            }
        )
        val list = ListView(activity)
        body.addView(list, LinearLayout.LayoutParams(-1, Ui.dp(activity, 330)))
        val dialog = AlertDialog.Builder(activity).setTitle("Dọn ảnh đã đồng bộ").setView(body)
            .setNegativeButton("Đóng", null).setPositiveButton("Xóa đã chọn (0)", null).create()
        val adapter = PreviewAdapter(activity, preview, selected) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
                it.text = "Xóa đã chọn (${selected.size})"
                it.isEnabled = selected.isNotEmpty()
            }
        }
        list.adapter = adapter
        dialog.setOnDismissListener { adapter.closed = true }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            setOnClickListener {
                val picks = preview.candidates.filter { it.identity in selected }
                if (picks.isEmpty()) return@setOnClickListener
                if (!GamePhotoCleanup.canWrite(activity, preview.tree)) {
                    requestWritePermission(activity, preview, dialog)
                } else {
                    confirm(activity, preview, picks, dialog)
                }
            }
        }
    }

    private fun confirm(
        activity: Activity,
        preview: GamePhotoCleanup.Preview,
        picks: List<GamePhotoCleanup.Candidate>,
        previewDialog: AlertDialog
    ) {
        AlertDialog.Builder(activity).setTitle("Xóa ${picks.size} ảnh trong Pictures?")
            .setMessage(
                "Chỉ xóa ${picks.size} ảnh nguồn bạn vừa chọn. Xóa qua nhà cung cấp tệp có thể là vĩnh viễn, không vào thùng rác. Bản đã lưu trong lượt được giữ.\n\nApp sẽ kiểm tra lại từng ảnh ngay trước khi xóa; ảnh thay đổi, thiếu bản lưu hoặc không còn quyền sẽ được bỏ qua."
            )
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa ${picks.size} ảnh") { _, _ ->
                previewDialog.dismiss()
                work(activity, "Đang kiểm tra lại và xóa ảnh đã chọn…", { allow ->
                    GamePhotoCleanup.deleteSelected(activity.applicationContext, preview.runId, picks, allow)
                }) { result ->
                    message(
                        activity,
                        "Đã xóa ${result.deleted} ảnh nguồn khỏi Pictures. " +
                            "Bỏ qua ${result.skipped} ảnh; không xóa được ${result.failed} ảnh." +
                            (if (result.cancelled) " Đã dừng; các ảnh chưa xử lý vẫn được giữ." else "") +
                            " Bản ảnh trong lượt vẫn được giữ."
                    )
                }
            }.show()
    }

    private fun requestWritePermission(activity: Activity, preview: GamePhotoCleanup.Preview, previewDialog: AlertDialog) {
        if (pendingGrant !=
            null
        ) {
            message(activity, "Đang chờ chọn lại thư mục Pictures. Hoàn tất hoặc hủy cửa sổ chọn thư mục trước.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Cấp quyền dọn đúng thư mục Pictures")
            .setMessage(
                "Hiện app chỉ có quyền đọc. Để xóa các ảnh bạn chọn, Android cần bạn chọn lại đúng thư mục Pictures của lượt này và cấp quyền đọc/ghi.\n\nChỉ chọn lại thư mục này: ${Uri.parse(
                    preview.tree
                ).lastPathSegment}. Cấp quyền chưa xóa ảnh nào; sau đó app sẽ đối chiếu và cho bạn chọn, xác nhận lại."
            )
            .setNegativeButton("Hủy", null).setPositiveButton("Chọn lại Pictures") { _, _ ->
                try {
                    pendingGrant = PendingGrant(preview.runId, preview.tree)
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(preview.tree))
                    activity.startActivityForResult(intent, REQUEST_FOLDER)
                    previewDialog.dismiss()
                } catch (e: Exception) {
                    pendingGrant = null
                    message(activity, "Không mở được bộ chọn thư mục: " + (e.message ?: "Hãy thử lại."))
                }
            }.show()
    }

    /** Only persists the exact grant flags returned for the exact original tree; never changes selection. */
    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_FOLDER) return false
        val request = pendingGrant
        pendingGrant = null
        if (resultCode != Activity.RESULT_OK) return true
        if (request == null) {
            message(activity, "Mở lại mục Dọn ảnh đã đồng bộ để xem và chọn ảnh. Chưa xóa ảnh nào.")
            return true
        }
        try {
            val uri = data?.data ?: error("Chưa chọn thư mục.")
            require(DocumentsContract.isTreeUri(uri) && uri.toString() == request.tree) {
                "Thư mục khác với Pictures của lượt này. Chưa lưu quyền mới; hãy mở lại và chọn đúng thư mục."
            }
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            require((flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                "Android chưa cấp quyền ghi/xóa. Không xóa ảnh nào; bạn có thể dọn thủ công bằng ứng dụng Tệp."
            }
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } else {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            require(GamePhotoCleanup.canWrite(activity, request.tree)) { "Chưa giữ được quyền đọc/ghi của đúng thư mục Pictures." }
            show(activity, request.runId)
        } catch (e: Exception) {
            message(activity, e.message ?: "Không nhận được quyền dọn Pictures. Chưa xóa ảnh nào.")
        }
        return true
    }

    /** Acquire/release the same main-thread library lock as existing import, export and file management. */
    private fun <T> work(activity: Activity, title: String, job: ((() -> Boolean)) -> T, finished: (T) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (CollectorService.libraryBusy || GamePhotoSync.busy || FileJob.running) {
            message(activity, "Đang xử lý file. Chờ hoàn tất rồi mở lại mục dọn ảnh.")
            return
        }
        if (CollectorService.current?.prepareEdit() == false) {
            message(activity, "Đang hoàn tất thao tác chụp. Chờ một chút rồi thử lại.")
            return
        }
        CollectorService.libraryBusy = true
        val cancelled = AtomicBoolean(false)
        val progress = AlertDialog.Builder(activity).setTitle(title).setView(ProgressBar(activity))
            .setNegativeButton("Dừng") { _, _ -> cancelled.set(true) }.create()
        progress.setOnCancelListener { cancelled.set(true) }
        progress.show()
        worker.execute {
            var result: T? = null
            var failure: Exception? = null
            try {
                result = job { !cancelled.get() && !activity.isDestroyed }
            } catch (e: Exception) {
                failure = e
            } finally {
                main.post {
                    CollectorService.libraryBusy = false
                    if (alive(activity)) {
                        progress.dismiss()
                        val error = failure
                        if (error != null &&
                            !cancelled.get()
                        ) {
                            message(activity, error.message ?: "Không đối chiếu được ảnh. Chưa xóa ảnh chưa kiểm tra.")
                        } else if (result != null && (!cancelled.get() || result is GamePhotoCleanup.Result)) {
                            @Suppress("UNCHECKED_CAST")
                            finished(result as T)
                        }
                    }
                }
            }
        }
    }

    private fun alive(activity: Activity) = !activity.isFinishing && !activity.isDestroyed

    private fun message(activity: Activity, text: String) {
        if (alive(activity)) AlertDialog.Builder(activity).setMessage(text).setPositiveButton("Đã hiểu", null).show()
    }

    private class PreviewAdapter(
        val activity: Activity,
        val preview: GamePhotoCleanup.Preview,
        val selected: MutableSet<String>,
        val changed: () -> Unit
    ) : BaseAdapter() {
        @Volatile var closed = false
        private val cache = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        override fun getCount() = preview.candidates.size

        override fun getItem(position: Int) = preview.candidates[position]

        override fun getItemId(position: Int) = position.toLong()

        private data class Row(val box: CheckBox, val image: ImageView, val text: TextView)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = convertView as? LinearLayout ?: LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(activity, 5), 0, Ui.dp(activity, 5))
                val box = CheckBox(activity)
                val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                val text = TextView(activity).apply {
                    textSize = 13f
                    setPadding(Ui.dp(activity, 8), 0, 0, 0)
                }
                addView(box, LinearLayout.LayoutParams(Ui.dp(activity, 42), Ui.dp(activity, 64)))
                addView(image, LinearLayout.LayoutParams(Ui.dp(activity, 72), Ui.dp(activity, 72)))
                addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                tag = Row(box, image, text)
            }
            val row = rowView.tag as Row
            val pick = getItem(position)
            row.box.setOnCheckedChangeListener(null)
            row.box.isChecked = pick.identity in selected
            row.box.isEnabled = cache.get(pick.identity) != null
            row.box.contentDescription = "Chọn xóa ảnh nguồn ${pick.source.name}"
            row.text.text = "Pictures/${listOf(pick.source.relativeDir,pick.source.name).filter { it.isNotBlank() }.joinToString("/")}\n" +
                "Bản lưu: ${pick.path}"
            row.box.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(pick.identity) else selected.remove(pick.identity)
                changed()
            }
            rowView.setOnClickListener { if (row.box.isEnabled) row.box.isChecked = !row.box.isChecked }
            row.image.contentDescription = "Xem trước ${pick.source.name}"
            row.image.tag = pick.identity
            row.image.setImageBitmap(cache.get(pick.identity))
            if (cache.get(pick.identity) == null) {
                thumbnails.execute {
                    if (closed) return@execute
                    val bitmap = try {
                        // The local original was verified equal to the source. Never ask a provider for a thumbnail.
                        val file = GamePhotoCleanup.confinedFile(
                            File(activity.filesDir, "captures/${preview.runId}"),
                            pick.path,
                            pick.group
                        )
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                            null
                        } else {
                            val sample = ImageSampling.sample(bounds.outWidth, bounds.outHeight, 144, 144, true)
                            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    } catch (_: Exception) {
                        null
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                    if (bitmap != null && !closed) cache.put(pick.identity, bitmap)
                    main.post {
                        if (!closed && alive(activity) && bitmap == null && selected.remove(pick.identity)) changed()
                        if (!closed && alive(activity) && row.image.tag == pick.identity) {
                            row.image.setImageBitmap(bitmap)
                            row.box.isEnabled = bitmap != null
                            if (bitmap == null) {
                                row.box.isChecked = false
                                row.text.append("\nKhông xem trước được ảnh; không thể chọn xóa.")
                            }
                        }
                    }
                }
            }
            return rowView
        }
    }
}
