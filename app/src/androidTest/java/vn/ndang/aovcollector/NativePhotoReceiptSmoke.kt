package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Real SAF/Bitmap/journal integration; artwork is generated, not a user screenshot. */
internal object NativePhotoReceiptSmoke {
    fun run(context: Context, tree: Uri) {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, "Pictures")

        fun art(x: Int, y: Int): Int {
            val red = (130 + 110 * kotlin.math.sin(x * .10 + y * .07)).toInt().coerceIn(0, 255)
            val green = (30 + y * 2).coerceIn(0, 255)
            val blue = (220 - x - y / 2).coerceIn(0, 255)
            return (255 shl 24) or (red shl 16) or (green shl 8) or blue
        }

        fun write(name: String, mirrored: Boolean = false, target: Uri? = null): Uri {
            val uri = target ?: DocumentsContract.createDocument(context.contentResolver, root, "image/png", name)!!
            val pixels = IntArray(100 * 100) {
                if (mirrored) art(99 - it % 100, 99 - it / 100) else art(it % 100, it / 100)
            }
            val bitmap = Bitmap.createBitmap(pixels, 100, 100, Bitmap.Config.ARGB_8888)
            context.contentResolver.openOutputStream(uri, "wt")!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            return uri
        }
        val signature = NativePhotoReceipt.signature(1000, 600, .5, .5) { x, y ->
            val sx = (x - 482) * 2 + 15
            val sy = (y - 282) * 2
            if (sx in 0..99 && sy in 0..99) art(sx, sy) else 0xff174487.toInt()
        }
        check(signature != null)
        val oldSource = write("receipt-old-identical.png")
        val run = "smoke-native-receipt"
        val group = "chieu-quan-co"
        val item = "lumburr-skill-synthetic"
        GamePhotoSession.begin(context, run)
        val attempt = GamePhotoSession.prepareAttempt(context, run, group, item, expectedSignature = signature)
        check(attempt.status == GamePhotoSession.ReceiptStatus.NONE)
        GamePhotoSession.startAttempt(context, run, attempt.id)
        check(GamePhotoSession.reconcileAttempt(context, run, attempt.id).status == GamePhotoSession.ReceiptStatus.UNCERTAIN)
        check(!GamePhotoSession.hasMark(context, run, group, item)) { "Old matching artwork counted as fresh export" }
        val unrelatedSource = write("receipt-unrelated.png", mirrored = true)
        check(GamePhotoSession.reconcileAttempt(context, run, attempt.id).status == GamePhotoSession.ReceiptStatus.UNCERTAIN)
        val exported = write("receipt-new.png")
        val receipt = GamePhotoSession.reconcileAttempt(context, run, attempt.id)
        check(receipt.status == GamePhotoSession.ReceiptStatus.RECEIPT) { "Fresh native receipt failed: $receipt" }
        check(receipt.documentId == DocumentsContract.getDocumentId(exported)) { "Wrong image pinned" }
        check(GamePhotoSession.hasMark(context, run, group, item))
        check(
            GamePhotoSession.prepareAttempt(context, run, group, item, expectedSignature = signature).status ==
                GamePhotoSession.ReceiptStatus.RECEIPT
        )
        val imported = GamePhotoSession.importNew(context, run)
        check(imported.copied == 1 && imported.pending == 0) { "Receipt did not import exact image: $imported" }
        val target = File(context.filesDir, "captures/$run/anh-goc").walkTopDown().single { it.isFile && it.extension == "png" }
        val original = target.readBytes()
        check(context.contentResolver.openInputStream(exported)!!.use { it.readBytes() }.contentEquals(original))
        write("ignored", mirrored = true, target = exported)
        check(GamePhotoSession.importNew(context, run).errors == 1) { "Mutated receipt source was accepted" }
        check(target.readBytes().contentEquals(original)) { "Pinned copy overwritten by changed source" }
        // Restore synthetic source so this completed fixture does not leave background sync pending.
        context.contentResolver.openOutputStream(exported, "wt")!!.use { it.write(original) }
        check(GamePhotoSession.importNew(context, run).pending == 0)
        // These three documents were created by this fixture; preserve the outer smoke's four originals.
        for (uri in listOf(oldSource, unrelatedSource, exported)) {
            check(DocumentsContract.deleteDocument(context.contentResolver, uri))
        }
        check(GamePhotoSession.importNew(context, run).pending == 0) { "Verified local receipt lost after source cleanup" }
    }
}
