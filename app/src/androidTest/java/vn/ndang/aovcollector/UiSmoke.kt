package vn.ndang.aovcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import org.json.JSONObject

/** Runs the actual UI and bundled OCR without a game, account or network. */
class UiSmoke : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val info = automation.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo =
                info

            // Instrumentation restarts the app process; rebind its accessibility service afterwards.
            fun shell(command: String) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
            }
            shell("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(500)
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            val image = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            canvas.drawColor(Color.rgb(29, 32, 60))
            canvas.drawText(
                "Eland'orr",
                1770f,
                385f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(248, 226, 170)
                    textSize = 58f
                }
            )
            val name = HeroNames.read(image)
            check(name != null && HeroNames.slug(name) == "elandorr") { "OCR returned $name" }
            // Qi's name region from a real run, where ML Kit read nothing.
            val qi = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888)
            context.assets.open("qi-name.png").use { BitmapFactory.decodeStream(it) }.let {
                Canvas(qi).apply { drawColor(Color.rgb(20, 30, 50)) }.drawBitmap(it, 1752f, 318f, null)
                it.recycle()
            }
            val qiLog = ArrayList<String>()
            val qiName = HeroNames.read(qi) { qiLog += it }
            qi.recycle()
            File(targetContext.filesDir, "smoke").apply { mkdirs() }.resolve("ocr-qi.txt").writeText("$qiName\n" + qiLog.joinToString("\n"))
            check(qiName == "Qi") { "OCR read $qiName for Qi: ${qiLog.joinToString("; ")}" }
            // User regression: the popup is Arduin even though card OCR read "A Đấu sĩ".
            context.assets.open("chess-arduin-role-ocr.jpg").use { input ->
                val shot = BitmapFactory.decodeStream(input)
                val title = try {
                    GameText.chessHeroTitle(shot)
                } finally {
                    shot.recycle()
                }
                check(title != null && HeroNames.slug(title) == "arduin") { "Chess heading OCR read $title for Arduin" }
            }
            // ZIP regression: exactly five heroes, not another row made from truncated cyan factions.
            context.assets.open("chess-first-row.png").use { input ->
                val shot = BitmapFactory.decodeStream(input)
                try {
                    val topic = ChessWiki.TOPICS.first { it.key == "tat-ca-quan-co" }
                    val words = GameText.words(shot)
                    val cards = ChessWiki.tiles(topic, shot, words)
                    File(targetContext.filesDir, "smoke/ocr-chess.txt").writeText(
                        cards.joinToString { it.label ?: "?" } + "\n" +
                            words.joinToString("\n") { "%.3f %.3f %s".format(it.x, it.y, it.text) }
                    )
                    check(cards.map { it.label } == listOf("Violet", "Arduin", "Hayate", "Raz", "Lumburr")) {
                        "Chess first row has false/missing cards: $cards"
                    }
                    // A white faction icon beside cyan letters is not a white hero title.
                    val injected = words + GameScreens.Word("Unknown faction", .218, .672, .326, .709)
                    check(ChessWiki.tiles(topic, shot, injected).size == 5) { "Cyan faction became another hero row" }
                } finally {
                    shot.recycle()
                }
            }
            context.assets.open("chess-violet-price.png").use { input ->
                val shot = BitmapFactory.decodeStream(input)
                val title = try {
                    GameText.chessHeroTitle(shot)
                } finally {
                    shot.recycle()
                }
                check(title == "Violet") { "Violet's gold price became part of her name: $title" }
            }
            for (fixture in listOf("chess-violet-price.png", "chess-arduin-role-ocr.jpg")) {
                context.assets.open(fixture).use { input ->
                    val shot = BitmapFactory.decodeStream(input)
                    try {
                        val icons = ChessWiki.skillTiles(shot)
                        check(icons.size == 1 && icons.single().y in .50..0.55) {
                            "Duplicate skill probes or plain background in $fixture: $icons"
                        }
                    } finally {
                        shot.recycle()
                    }
                }
            }
            context.assets.open("chess-save-photo-sure.jpg").use { input ->
                val screenshot = BitmapFactory.decodeStream(input)
                val sure = try {
                    ChessWiki.nativeConfirm(GameText.words(screenshot))
                } finally {
                    screenshot.recycle()
                }
                check(sure != null && sure.first in .30..0.45 && sure.second in .48..0.65) {
                    "Real Save Photo dialog did not locate Sure: $sure"
                }
            }
            // User ZIP: a text tab has more content below its first screenshot.
            context.assets.open("chess-round-bottom.jpg").use { input ->
                val before = BitmapFactory.decodeStream(input)
                val moved = before.copy(Bitmap.Config.ARGB_8888, true)
                val region =
                    Rect(
                        (before.width * .24).toInt(),
                        (before.height * .25).toInt(),
                        (before.width * .92).toInt(),
                        (before.height * .97).toInt()
                    )
                val source = Rect(region).apply { top += 12 }
                val target = Rect(region).apply { bottom -= 12 }
                Canvas(moved).drawBitmap(before, source, target, null)
                check(ChessWiki.changed(ChessWiki.signature(before), ChessWiki.signature(moved))) {
                    "Real text scroll was treated as the end of the tab"
                }
                check(!ChessWiki.changed(ChessWiki.signature(moved), ChessWiki.signature(moved)))
                before.recycle()
                moved.recycle()
            }
            // Profession/equipment descriptions and numeric bonuses must not become filenames.
            val itemShot = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888)
            Canvas(itemShot).apply {
                drawColor(Color.rgb(65, 70, 170))
                drawText(
                    "Lenh bai phong than",
                    870f,
                    275f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 45f
                    }
                )
                drawText(
                    "Cho dong doi gan la chan nang luong",
                    870f,
                    335f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.CYAN
                        textSize = 31f
                    }
                )
            }
            val preciseItem = GameText.chessItemTitle(itemShot)
            check(preciseItem != null && HeroNames.slug(preciseItem) == "lenh-bai-phong-than") {
                "Item title OCR selected body: $preciseItem"
            }
            itemShot.recycle()
            // Native Save Photo exports go through a real SAF provider, then appear in app history.
            val photoTree = android.provider.DocumentsContract.buildTreeDocumentUri(
                "vn.ndang.aovcollector.test.photos",
                "Pictures"
            )
            targetContext.startActivity(Intent(context, PhotoGrantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            SystemClock.sleep(700)
            GamePhotoImport.choose(targetContext, photoTree)
            val photoRoot = android.provider.DocumentsContract.buildDocumentUriUsingTree(photoTree, "Pictures")

            fun nativeFile(name: String, tiny: Boolean = false): android.net.Uri {
                val uri = android.provider.DocumentsContract.createDocument(
                    targetContext.contentResolver,
                    photoRoot,
                    "image/jpeg",
                    name
                )!!
                // Valid bytes from the bundled screenshot; the game can use .png (1).jpg names.
                if (tiny) {
                    val icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                    Canvas(icon).apply {
                        drawColor(Color.BLUE)
                        drawCircle(64f, 64f, 36f, Paint().apply { color = Color.YELLOW })
                    }
                    targetContext.contentResolver.openOutputStream(uri)!!.use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    icon.recycle()
                } else {
                    context.assets.open("chess-arduin-role-ocr.jpg").use { input ->
                        targetContext.contentResolver.openOutputStream(uri)!!.use { output -> input.copyTo(output) }
                    }
                }
                return uri
            }
            val personal = nativeFile("personal-before.jpg")
            val nativeRun = "smoke-native-save"
            check(GamePhotoSession.begin(targetContext, nativeRun) == 1)
            GamePhotoSession.mark(targetContext, nativeRun, "quan-co", "violet-head")
            GamePhotoSession.mark(targetContext, nativeRun, "quan-co", "violet-head")
            check(GamePhotoSession.hasMark(targetContext, nativeRun, "quan-co", "violet-head"))
            val waiting = GamePhotoSession.importNew(targetContext, nativeRun)
            check(waiting.pending == 1 && waiting.copied == 0) { "Sure without a visible file was acknowledged" }
            val violet = nativeFile("caYyujwz.png (1).jpg")
            val copied = GamePhotoSession.importNew(targetContext, nativeRun)
            check(copied.copied == 1 && copied.pending == 0) { "Late Pictures image was not imported: $copied" }
            val imported = File(targetContext.filesDir, "captures/$nativeRun/anh-goc")
            val violetCopy = imported.walkTopDown().first { it.isFile && it.name.startsWith("violet-") }
            check(violetCopy.name == "violet-head.jpg" && violetCopy.path.contains("quan-co/anh-quan-co"))
            check(CaptureHistory.entries(targetContext).any { it.name == "violet-head" })
            targetContext.contentResolver.openInputStream(violet)!!.use {
                check(it.readBytes().contentEquals(violetCopy.readBytes())) { "Native image bytes changed" }
            }
            // A later different category cannot rename/reassign the already imported Violet.
            SystemClock.sleep(50)
            GamePhotoSession.mark(
                targetContext,
                nativeRun,
                "chieu-quan-co",
                "violet-skill-nativehash",
                setOf("Violet-upper", "Violet-lower")
            )
            GamePhotoSession.mark(targetContext, nativeRun, "chieu-quan-co", "Violet-upper")
            check(GamePhotoSession.hasMark(targetContext, nativeRun, "chieu-quan-co", "Violet-lower"))
            val skill = nativeFile("skill.png (1).jpg", tiny = true)
            val second = GamePhotoSession.importNew(targetContext, nativeRun)
            check(second.copied == 1 && second.already == 1 && second.pending == 0)
            check(violetCopy.isFile && imported.walkTopDown().count { GamePhotoImport.isImage(it) } == 2)
            val skillCopy = imported.walkTopDown().single { it.isFile && it.name == "violet-skill-1.png" }
            val sourceBytes = targetContext.contentResolver.openInputStream(skill)!!.use { it.readBytes() }
            check(sourceBytes.contentEquals(skillCopy.readBytes())) { "Native PNG was re-encoded" }
            val sourceBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, sourceBounds)
            val storedBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(skillCopy.path, storedBounds)
            check(
                sourceBounds.outWidth == 128 &&
                    sourceBounds.outHeight == 128 &&
                    storedBounds.outWidth == sourceBounds.outWidth &&
                    storedBounds.outHeight == sourceBounds.outHeight &&
                    storedBounds.outMimeType == sourceBounds.outMimeType
            ) { "Native resolution/MIME changed" }
            // Same-size corruption must not be treated as an already-imported original.
            skillCopy.writeBytes(sourceBytes.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
            check(GamePhotoSession.importNew(targetContext, nativeRun).copied == 1) { "Corrupt copy was accepted" }
            check(sourceBytes.contentEquals(skillCopy.readBytes())) { "Corrupt copy was not repaired" }
            val qualityFixture = File(targetContext.filesDir, "smoke/tiny-original.png")
            qualityFixture.writeBytes(sourceBytes)

            check(GamePhotoSession.importNew(targetContext, nativeRun).copied == 0) { "Sync duplicated native photos" }
            violetCopy.delete()
            check(GamePhotoSession.importNew(targetContext, nativeRun).copied == 1) { "Deleted internal image cannot be restored" }
            SystemClock.sleep(50)
            GamePhotoSession.mark(targetContext, nativeRun, "phe", "Doa-lac-gia")
            val faction = nativeFile("faction.png (1).jpg")
            val oldRevision = GamePhotoSync.revision
            GamePhotoSync.request(targetContext)
            val copyDeadline = SystemClock.uptimeMillis() + 10_000
            while ((GamePhotoSync.revision == oldRevision || GamePhotoSync.busy) && SystemClock.uptimeMillis() < copyDeadline) {
                SystemClock.sleep(100)
            }
            check(GamePhotoSync.revision > oldRevision && !GamePhotoSync.busy) { "After-Sure background sync did not finish" }
            check(imported.walkTopDown().any { it.isFile && it.path.contains("tuong-tac/phe") })
            check(GamePhotoImport.scan(targetContext, photoTree).size == 4) { "Original Pictures files changed" }
            val nativeRunDir = File(targetContext.filesDir, "captures/$nativeRun")
            val manifest = JSONObject(File(nativeRunDir, "images.json").readText())
            val manifestImages = manifest.getJSONArray("images")
            check(manifest.getString("collectionMode") == "chess" && manifestImages.length() == 3)
            for (i in 0 until manifestImages.length()) {
                val entry = manifestImages.getJSONObject(i)
                val path = entry.getString("path")
                check(path.startsWith("anh-goc/") && !path.contains(".."))
                check(
                    OriginalPhotoCopy.matches(
                        File(nativeRunDir, path),
                        OriginalPhotoCopy.Integrity(entry.getLong("bytes"), entry.getString("sha256"))
                    )
                )
            }
            check(JSONObject(File(nativeRunDir, "run.json").readText()).getString("collectionMode") == "chess")
            // Cross-run reuse copies verified bytes into this run, without another game save event.
            val capturesRoot = File(targetContext.filesDir, "captures")
            val blessingKey = ChessBlessings.key("Giây phút huy hoàng")
            BlessingPhotoIndex.record(
                File(targetContext.filesDir, BlessingPhotoIndex.FILE),
                capturesRoot,
                "chuc-phuc-thuong",
                blessingKey,
                violetCopy,
                setOf(blessingKey)
            )
            val reuseRun = "smoke-native-reuse"
            GamePhotoSession.begin(targetContext, reuseRun)
            check(GamePhotoSession.hasMark(targetContext, reuseRun, "chuc-phuc-thuong", "Giây phút huy hoàng", setOf(blessingKey)))
            val reuseDir = File(capturesRoot, reuseRun)
            val reused = File(reuseDir, "anh-goc/chuc-phuc/thuong/giay-phut-huy-hoang.jpg")
            check(reused.isFile && reused.readBytes().contentEquals(violetCopy.readBytes()))
            check(GamePhotoSession.state(targetContext, reuseRun)!!.getJSONArray("saves").length() == 0)
            val reusedImages = JSONObject(File(reuseDir, "images.json").readText()).getJSONArray("images")
            check(reusedImages.length() == 1)
            val reusedImage = reusedImages.getJSONObject(0)
            val reusedIntegrity = reused.inputStream().use { OriginalPhotoCopy.digest(it) }
            check(reusedImage.getBoolean("reused"))
            check(reusedImage.getLong("bytes") == reusedIntegrity.bytes && reusedImage.getString("sha256") == reusedIntegrity.sha256)
            val reusedClaims = GamePhotoSession.state(targetContext, reuseRun)!!.getJSONObject("targetClaims")
            check(reusedClaims.getString(reusedImage.getString("path")) == reusedImage.getString("sourceId"))
            check(reuseDir.walkTopDown().none { it.name.endsWith(".part") }) { "Reused original left a partial file" }
            check(GamePhotoSession.hasMark(targetContext, reuseRun, "chuc-phuc-thuong", "Giây phút huy hoàng", setOf(blessingKey)))
            check(reuseDir.walkTopDown().count { GamePhotoImport.isImage(it) } == 1)
            check(GamePhotoImport.scan(targetContext, photoTree).size == 4)
            // Full post-run pipeline: real SAF ZIP/read-back, no ChatGPT before Drive proof,
            // then navigate a separate fixture app's drawer -> dot -> composer -> Send.
            targetContext.contentResolver.takePersistableUriPermission(
                photoTree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            targetContext.getSharedPreferences("after-run", 0).edit().putString("tree", photoTree.toString()).commit()
            val savedCommand = "Phân tích kết quả đã lưu trên Drive · smoke"
            AfterRun.saveConfig(targetContext, true, true, savedCommand)
            AfterRun.finished(targetContext, nativeRun)

            fun awaitStage(wanted: String, timeout: Long = 25_000, run: String = nativeRun) {
                val until = SystemClock.uptimeMillis() + timeout
                while (AfterRun.state(targetContext, run)?.optString("stage") != wanted && SystemClock.uptimeMillis() < until) {
                    SystemClock.sleep(150)
                }
                check(AfterRun.state(targetContext, run)?.optString("stage") == wanted) {
                    "Post-run expected $wanted: ${AfterRun.state(targetContext,run)}"
                }
            }
            awaitStage("waiting-drive")
            val upload = AfterRun.state(targetContext, nativeRun)!!
            check(upload.optBoolean("verified") && !upload.optBoolean("driveProof"))
            val zipName = upload.getString("filename")
            AfterRun.driveNotification(targetContext, AfterRunProtocol.DRIVE, "Đang tải lên $zipName")
            check(AfterRun.state(targetContext, nativeRun)!!.getString("stage") == "waiting-drive")
            AfterRun.driveNotification(targetContext, AfterRunProtocol.DRIVE, "Đã tải lên other.zip")
            check(AfterRun.state(targetContext, nativeRun)!!.getString("stage") == "waiting-drive")
            AfterRun.driveNotification(targetContext, AfterRunProtocol.DRIVE, "Đã tải lên $zipName")
            awaitStage("done")
            val sentText = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(
                    "run-as com.openai.chatgpt cat files/sent.txt"
                )
            ).use { it.bufferedReader().readText() }
            check(sentText == "dot\n" + savedCommand) { "Command went to the wrong destination: $sentText" }
            AfterRun.finished(targetContext, nativeRun)
            check(AfterRun.state(targetContext, nativeRun)!!.getString("stage") == "done")
            // The next launch may resume an already open dot page, with a non-clickable heading.
            val dotAgain = "smoke-dot-already-open"
            val nextCommand = "Smoke · dot đã mở sẵn"
            AfterRun.saveConfig(targetContext, true, true, nextCommand)
            File(targetContext.filesDir, "captures/$dotAgain").apply { mkdirs() }.resolve("result.txt").writeText(dotAgain)
            AfterRun.finished(targetContext, dotAgain)
            awaitStage("waiting-drive", run = dotAgain)
            val nextUpload = AfterRun.state(targetContext, dotAgain)!!
            AfterRun.driveNotification(targetContext, AfterRunProtocol.DRIVE, "Uploaded " + nextUpload.getString("filename"))
            awaitStage("done", run = dotAgain)
            val sentAgain = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(
                    "run-as com.openai.chatgpt cat files/sent.txt"
                )
            ).use { it.bufferedReader().readText() }
            check(sentAgain == "dot\n" + nextCommand) { "Already-open dot page was not handled: $sentAgain" }
            android.provider.DocumentsContract.deleteDocument(
                targetContext.contentResolver,
                android.net.Uri.parse(nextUpload.getString("uri"))
            )
            File(targetContext.filesDir, "captures/$dotAgain").deleteRecursively()
            // Google Drive can expose create-file without exposing folder selection. Persist a
            // dedicated ZIP URI and overwrite only that explicitly chosen latest-results file.
            val fixedTreeUri = android.provider.DocumentsContract.createDocument(
                targetContext.contentResolver,
                photoRoot,
                "application/zip",
                "aov-latest.zip"
            )!!
            val fixed = android.provider.DocumentsContract.buildDocumentUri("vn.ndang.aovcollector.test.photos", "aov-latest.zip")
            targetContext.startActivity(
                Intent(context, PhotoGrantActivity::class.java).setData(fixed)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            waitForIdleSync()
            SystemClock.sleep(700)
            targetContext.contentResolver.takePersistableUriPermission(
                fixed,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            targetContext.getSharedPreferences("after-run", 0).edit().putString("tree", fixed.toString()).commit()
            AfterRun.saveConfig(targetContext, true, false, "")
            var previousBytes = byteArrayOf()
            for (fixedRun in listOf("smoke-fixed-first", "smoke-fixed-second")) {
                File(targetContext.filesDir, "captures/$fixedRun").apply { mkdirs() }.resolve("result.txt").writeText(fixedRun)
                if (AfterRun.state(targetContext, fixedRun) == null) AfterRun.finished(targetContext, fixedRun)
                awaitStage("waiting-drive", run = fixedRun)
                val saved = AfterRun.state(targetContext, fixedRun)!!
                check(saved.getString("uri") == fixed.toString() && saved.getString("filename") == "aov-latest.zip")
                val bytes = targetContext.contentResolver.openInputStream(fixed)!!.use { it.readBytes() }
                check(bytes.isNotEmpty() && !bytes.contentEquals(previousBytes)) { "Fixed Drive ZIP did not update" }
                previousBytes = bytes
                if (fixedRun == "smoke-fixed-first") {
                    File(targetContext.filesDir, "captures/smoke-fixed-second").apply { mkdirs() }
                        .resolve("result.txt").writeText("smoke-fixed-second")
                    AfterRun.finished(targetContext, "smoke-fixed-second")
                    SystemClock.sleep(1600)
                    check(AfterRun.state(targetContext, "smoke-fixed-second")!!.getString("stage") == "queued") {
                        "Next run overwrote a ZIP still waiting for Drive confirmation"
                    }
                    targetContext.contentResolver.openInputStream(fixed)!!.use { check(it.readBytes().contentEquals(bytes)) }
                }
                AfterRun.driveNotification(
                    targetContext,
                    AfterRunProtocol.DRIVE,
                    "Uploaded aov-latest.zip",
                    saved.getLong("uploadStarted") - 1
                )
                check(AfterRun.state(targetContext, fixedRun)!!.getString("stage") == "waiting-drive")
                AfterRun.driveNotification(targetContext, AfterRunProtocol.DRIVE, "Uploaded aov-latest.zip")
                awaitStage("done", run = fixedRun)
                File(targetContext.filesDir, "captures/$fixedRun").deleteRecursively()
            }
            targetContext.contentResolver.releasePersistableUriPermission(
                fixed,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            android.provider.DocumentsContract.deleteDocument(targetContext.contentResolver, fixedTreeUri)
            val interrupted = "smoke-after-interrupted"
            AfterRun.finished(targetContext, interrupted)
            AfterRun.update(targetContext, interrupted, "sending", "Simulate process death after send attempt")
            AfterRun.recover(targetContext)
            check(AfterRun.state(targetContext, interrupted)!!.getString("stage") == "send-uncertain")
            AfterRun.retry(targetContext, interrupted)
            check(AfterRun.state(targetContext, interrupted)!!.getString("stage") == "send-uncertain")
            val cancelled = "smoke-after-cancelled"
            AfterRun.finished(targetContext, cancelled)
            AfterRun.cancel(targetContext, cancelled)
            check(AfterRun.state(targetContext, cancelled)!!.getString("stage") == "cancelled")
            File(
                targetContext.filesDir,
                "smoke/after-run.txt"
            ).writeText("zip-readback=verified; wait-for-drive=ok; dot-navigation=ok; sent-once=ok")
            android.provider.DocumentsContract.deleteDocument(targetContext.contentResolver, android.net.Uri.parse(upload.getString("uri")))
            targetContext.getSharedPreferences("after-run", 0).edit().clear().commit()
            File(targetContext.filesDir, "after-run").deleteRecursively()
            File(
                targetContext.filesDir,
                "smoke/native-photos.txt"
            ).writeText(
                "late-indexed=ok; bytes-and-SHA=identical; resolution-and-MIME=original; corruption-repair=ok; classified=ok; dedup=ok; restore=ok"
            )
            // Cleanup preview is opt-in, starts with nothing selected, and cancelling deletes nothing.
            val cleanupActivity = startActivitySync(
                Intent(targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val cleanupIdleDeadline = SystemClock.uptimeMillis() + 15_000
            var cleanupOpened = false
            while (!cleanupOpened && SystemClock.uptimeMillis() < cleanupIdleDeadline) {
                runOnMainSync {
                    if (!CollectorService.libraryBusy && !GamePhotoSync.busy && !FileJob.running) {
                        GamePhotoCleanupDialog.show(cleanupActivity, nativeRun)
                        cleanupOpened = true
                    }
                }
                if (!cleanupOpened) SystemClock.sleep(150)
            }
            check(cleanupOpened) {
                "Cleanup remained busy: library=${CollectorService.libraryBusy}, sync=${GamePhotoSync.busy}, file=${FileJob.running}"
            }
            val previewDeadline = SystemClock.uptimeMillis() + 15_000

            fun cleanupNodes() = automation.windows.mapNotNull { it.root }
            while (cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Xóa đã chọn (0)") }.isEmpty() &&
                SystemClock.uptimeMillis() < previewDeadline
            ) {
                SystemClock.sleep(150)
            }
            val deleteNone = cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Xóa đã chọn (0)") }.firstOrNull()
                ?: error("Cleanup preview did not appear")
            check(!deleteNone.isEnabled) { "Cleanup preselected originals" }
            automation.takeScreenshot()?.let { screen ->
                File(targetContext.filesDir, "smoke/native-cleanup-preview.png").outputStream().use {
                    screen.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screen.recycle()
            }
            val selectionDeadline = SystemClock.uptimeMillis() + 10_000

            fun selectablePhoto() = cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Chọn xóa ảnh nguồn") }
                .firstOrNull { it.isCheckable && it.isEnabled }
            while (selectablePhoto() == null && SystemClock.uptimeMillis() < selectionDeadline) {
                SystemClock.sleep(150)
            }
            val photo = selectablePhoto() ?: error("Cleanup thumbnail did not become selectable")
            val photoDescription = photo.contentDescription?.toString()
            check(photo.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForIdleSync()
            val deleteOne = cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Xóa đã chọn (1)") }.firstOrNull()
                ?: error("Cleanup selection did not update the delete count")
            check(deleteOne.isEnabled) { "Cleanup selection did not enable deletion" }
            val selectedPhoto = cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Chọn xóa ảnh nguồn") }
                .first { it.contentDescription?.toString() == photoDescription && it.isChecked }
            check(selectedPhoto.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForIdleSync()
            val deleteUnselected = cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Xóa đã chọn (0)") }.firstOrNull()
                ?: error("Cleanup deselection did not reset the delete count")
            check(!deleteUnselected.isEnabled) { "Cleanup deselection left deletion enabled" }
            val closePreview = cleanupNodes().flatMap { it.findAccessibilityNodeInfosByText("Đóng") }.first {
                it.text?.toString()?.equals("Đóng", true) ==
                    true
            }
            check(closePreview.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            runOnMainSync { cleanupActivity.finish() }
            waitForIdleSync()
            SystemClock.sleep(300)
            check(FileJob.screen == null) { "Cleanup preview left its activity in front of background-job tests" }
            check(GamePhotoImport.scan(targetContext, photoTree).size == 4) { "Preview cancellation deleted a source" }
            // Destructive engine check is confined to this synthetic provider, never user Pictures.
            val eligible = GamePhotoCleanup.preview(targetContext, nativeRun)
            check(eligible.candidates.size == 3 && eligible.candidates.none { it.source.name == "personal-before.jpg" })
            val selectedSkill = eligible.candidates.single { it.group == "chieu-quan-co" }
            val cleaned = GamePhotoCleanup.deleteSelected(targetContext, nativeRun, listOf(selectedSkill))
            check(cleaned.deleted == 1 && cleaned.failed == 0 && cleaned.skipped == 0) { "Verified cleanup failed: $cleaned" }
            check(skillCopy.readBytes().contentEquals(sourceBytes)) { "Cleanup changed the run-local copy" }
            val afterCleanup = GamePhotoSession.importNew(targetContext, nativeRun)
            check(afterCleanup.pending == 0 && afterCleanup.already == 3) { "Cleanup made the completed run pending: $afterCleanup" }
            check(GamePhotoImport.scan(targetContext, photoTree).size == 3)
            File(
                targetContext.filesDir,
                "smoke/native-cleanup.txt"
            ).writeText(
                "preview-none-selected=ok; cancel=no-delete; personal=excluded; selected-synthetic-delete=ok; local-copy=intact; resync=complete"
            )
            listOf(personal, violet, skill, faction).forEach {
                android.provider.DocumentsContract.deleteDocument(targetContext.contentResolver, it)
            }
            targetContext.contentResolver.releasePersistableUriPermission(
                photoTree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            targetContext.getSharedPreferences("game-photo-source", 0).edit().clear().commit()
            File(targetContext.filesDir, "captures/$nativeRun").deleteRecursively()
            File(targetContext.filesDir, "captures/$reuseRun").deleteRecursively()
            imported.deleteRecursively()
            File(targetContext.filesDir, "pictures-copy-ledger.json").delete()
            val chessOptions = ChessWiki.Options(setOf("tat-ca-quan-co"), openGame = false, heroDetails = true)
            val legacyChess = JSONObject(BuiltInMacros.chess(chessOptions)).apply { remove("fast") }
            check(BuiltInMacros.chessOptions(legacyChess).fast) { "Old chess macro did not default to fast mode" }
            for (fast in listOf(false, true)) {
                val selected = chessOptions.copy(fast = fast)
                check(BuiltInMacros.chessOptions(JSONObject(BuiltInMacros.chess(selected))) == selected) {
                    "Chess speed setting did not survive save/load"
                }
            }
            // The way into the game: a list run that opens the game starts with the entry step, and a popup shot on
            // the user's phone, fading in, is read on Android as in the unit tests.
            check(Macro(BuiltInMacros.create("", 2, true, openGame = true)).steps.getJSONObject(0).getString("type") == "enter") {
                "Tự vào game should start with the entry step"
            }
            val popup = context.assets.open("game-popup-fest.jpg").use { BitmapFactory.decodeStream(it) }
            val seen = GameScreens.read(popup.width, popup.height) { y, row -> popup.getPixels(row, 0, popup.width, 0, y, popup.width, 1) }
            popup.recycle()
            check(
                seen.kind == GameScreens.Kind.POPUP &&
                    Math.abs(
                        seen.close!!.first - .913
                    ) < .01 &&
                    Math.abs(seen.close!!.second - .056) < .015
            ) {
                "Popup read as ${seen.kind} ${seen.close}"
            }

            // Screens known by their words, read by ML Kit on the user's screenshots: where the run would tap.
            fun words(asset: String): GameScreens.Reading {
                val b = context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                val pixels = GameScreens.read(b.width, b.height) { y, row -> b.getPixels(row, 0, b.width, 0, y, b.width, 1) }
                return GameScreens.refine(pixels, GameText.words(b), null).also { b.recycle() }
            }
            val reward = words("game-reward-stars.jpg")
            check(
                reward.kind == GameScreens.Kind.CONTINUE &&
                    Math.abs(
                        reward.tap!!.first - .5
                    ) < .05 &&
                    Math.abs(reward.tap!!.second - .91) < .04
            ) {
                "Reward screen read as ${reward.kind} ${reward.tap}: ${reward.words}"
            }
            val register = words("game-register.jpg")
            check(
                register.kind == GameScreens.Kind.REGISTER &&
                    Math.abs(
                        register.tap!!.first - .749
                    ) < .04 &&
                    Math.abs(register.tap!!.second - .194) < .04
            ) {
                "Registration form read as ${register.kind} ${register.tap}: ${register.words}"
            }
            val form = words("game-garena-form.jpg")
            check(form.kind == GameScreens.Kind.FORM) { "Garena page read as ${form.kind}: ${form.words}" }
            // The way to the hero statistics, read by ML Kit on the user's screenshots: each page known by its words.
            val rankLog = StringBuilder()

            fun rank(asset: String): List<GameScreens.Word> {
                val b = context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                return GameText.rankingWords(b).also {
                    b.recycle()
                    rankLog.append("$asset\n").append(it.joinToString("\n") { w -> "  %.3f %.3f %s".format(w.x, w.y, w.text) }).append("\n")
                }
            }
            val more = rank("rank-more.jpg")
            val community = rank("rank-community.jpg")
            val heroes = rank("rank-community-heroes.jpg")
            val rates = rank("rank-ranking-rates.jpg")
            val figures = rank("rank-ranking-figures.jpg")
            val modes = rank("rank-ranking-modes.jpg")
            // Synthetic generic header, rendered at runtime; new user attachments remain local only.
            // A giant accessibility node must not take the visible header link's tap.
            val parent = GameScreens.Word("Bảng Phân Bậc Tướng Bảng xếp hạng đầy đủ Đánh giá", .03, .02, .95, .98)
            val blockedBitmap = Bitmap.createBitmap(2048, 922, Bitmap.Config.ARGB_8888)
            Canvas(blockedBitmap).apply {
                drawColor(Color.rgb(58, 58, 144))
                drawText(
                    "Bảng Phân Bậc Tướng",
                    310f,
                    88f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 40f
                    }
                )
                drawText(
                    "Bảng xếp hạng đầy đủ",
                    830f,
                    86f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(205, 163, 95)
                        textSize = 32f
                    }
                )
            }
            val blockedHeroes = GameText.rankingWords(blockedBitmap, listOf(parent))
            val header = try {
                GameText.rankingHeader(blockedBitmap)
            } finally {
                blockedBitmap.recycle()
            }
            rankLog.append("synthetic full-resolution header\n").append(
                header.joinToString("\n") { w ->
                    "  %.3f %.3f %s".format(w.x, w.y, w.text)
                }
            ).append("\n")
            for ((source, list) in listOf("combined" to blockedHeroes, "header-only" to header)) {
                val target = RankScreens.fullRankingLink(listOf(parent) + list)
                check(target != null && target.x in .405..0.60 && target.y in .06..0.105) {
                    "Full-ranking link $source resolved outside synthetic label: $target"
                }
            }
            File(targetContext.filesDir, "smoke").apply { mkdirs() }.resolve("ocr-ranking.txt").writeText(rankLog.toString())
            check(
                RankScreens.page(more, false) == RankScreens.Page.PANEL &&
                    more.any {
                        it.plain.contains("congdong") && Math.abs(it.x - .15) < .06 && Math.abs(it.y - .52) < .05
                    }
            ) { "Tính năng khác read as ${RankScreens.page(more,false)}" }
            check(RankScreens.page(community, false) == RankScreens.Page.COMMUNITY) {
                "Cộng đồng read as ${RankScreens.page(community,false)}"
            }
            check(
                RankScreens.page(heroes, false) == RankScreens.Page.HEROES &&
                    RankScreens.fullRankingLink(heroes)?.let {
                        Math.abs(it.y - .082) < .04
                    } == true
            ) { "Cộng đồng heroes read as ${RankScreens.page(heroes,false)}" }
            for ((list, table) in listOf(rates to RankScreens.Table.RATES, figures to RankScreens.Table.FIGURES)) {
                check(
                    RankScreens.page(list, false) == RankScreens.Page.RANKING &&
                        RankScreens.table(list) == table &&
                        RankScreens.mode(RankScreens.modeButton(list)) == RankScreens.Mode.ALL
                ) {
                    "Ranking read as ${RankScreens.page(
                        list,
                        false
                    )} ${RankScreens.table(list)} ${RankScreens.mode(RankScreens.modeButton(list))}"
                }
            }
            // Chiến khu, read by ML Kit on the user's screenshots (kept at half size, the size ML Kit reads them at).
            val boardLog = StringBuilder()

            fun board(name: String): List<GameScreens.Word> {
                val small = context.assets.open("board-$name.jpg").use { BitmapFactory.decodeStream(it) }
                val b = Bitmap.createScaledBitmap(small, small.width * 2, small.height * 2, true)
                small.recycle()
                return GameText.words(b).also {
                    b.recycle()
                    boardLog.append("$name\n").append(it.joinToString("\n") { w -> "  %.3f %.3f %s".format(w.x, w.y, w.text) }).append("\n")
                }
            }
            val boardPages = listOf(
                "ranked", "ranked-new", "season-memory", "season-data", "season-heroes", "season-open", "season-first",
                "season-title", "season-reward",
                "region", "region-country", "region-area", "region-province", "board-province", "board-country", "board-valhein",
                "picker", "trailer1", "top"
            ).associateWith {
                board(it)
            }
            File(targetContext.filesDir, "smoke").resolve("ocr-board.txt").writeText(boardLog.toString())

            fun boardPage(name: String) = BoardScreens.page(boardPages.getValue(name), false)
            for (name in listOf("ranked", "ranked-new")) {
                check(
                    boardPage(name) == BoardScreens.Page.RANKED && BoardScreens.chienKhu(boardPages.getValue(name)) != null
                ) {
                    "$name read as ${boardPage(name)}"
                }
            }
            for (name in listOf(
                "season-memory",
                "season-data",
                "season-heroes",
                "season-open",
                "season-first",
                "season-title",
                "season-reward"
            )) {
                check(
                    boardPage(name) == BoardScreens.Page.SEASON
                ) { "$name read as ${boardPage(name)}" }
            }
            for (name in listOf(
                "region",
                "region-country",
                "region-area",
                "region-province"
            )) {
                check(boardPage(name) == BoardScreens.Page.REGION) {
                    "$name read as ${boardPage(name)}"
                }
            }
            check(
                BoardScreens.region(boardPages.getValue("region-area")).let {
                    it.open == 1 &&
                        it.options.any { o -> o.plain.contains("mientrung") }
                }
            ) { "Area list read as ${BoardScreens.region(boardPages.getValue("region-area")).options}" }
            for (name in listOf("board-province", "board-country")) {
                check(
                    boardPage(name) == BoardScreens.Page.BOARD &&
                        BoardScreens.hero(boardPages.getValue(name))?.let {
                            HeroNames.slug(it)
                        } == "flowborn"
                ) { "$name read as ${boardPage(name)} ${BoardScreens.hero(boardPages.getValue(name))}" }
            }
            check(boardPage("picker") == BoardScreens.Page.PICKER) { "Chọn tướng read as ${boardPage("picker")}" }
            // Synthetic generic label, blue outline and tick, drawn entirely at test runtime.
            val filterBitmap = Bitmap.createBitmap(2048, 922, Bitmap.Config.ARGB_8888)
            Canvas(filterBitmap).apply {
                drawColor(Color.rgb(51, 51, 126))
                drawText(
                    "Đã nhận",
                    1558f,
                    220f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(170, 170, 207)
                        textSize = 28f
                    }
                )
                drawRect(1651f, 194f, 1686f, 229f, Paint().apply { color = Color.rgb(76, 97, 194) })
                drawRect(1654f, 197f, 1683f, 226f, Paint().apply { color = Color.rgb(41, 39, 101) })
                drawPath(
                    Path().apply {
                        moveTo(1656f, 211f)
                        lineTo(1665f, 220f)
                        lineTo(1679f, 203f)
                    },
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(201, 245, 255)
                        style = Paint.Style.STROKE
                        strokeWidth = 5f
                    }
                )
            }
            val filterWords = listOf(GameScreens.Word("Chọn tướng", .16, .09, .25, .14)) + GameText.words(filterBitmap)

            fun ownedFilter() = BoardScreens.ownedFilter(filterBitmap.width, filterBitmap.height, filterWords) { y, row ->
                filterBitmap.getPixels(row, 0, filterBitmap.width, 0, y, filterBitmap.width, 1)
            }
            check(ownedFilter()?.let { it.checked && Math.abs(it.x - .814) < .004 && Math.abs(it.y - .229) < .006 } == true) {
                "Owned-only checkbox or its OCR label unreadable: $filterWords"
            }
            Canvas(filterBitmap).drawRect(1654f, 197f, 1683f, 226f, Paint().apply { color = Color.rgb(41, 39, 101) })
            check(ownedFilter()?.checked == false) { "Empty owned-only checkbox must be OFF" }
            filterBitmap.recycle()
            check(
                boardPage("board-valhein") == BoardScreens.Page.BOARD &&
                    BoardScreens.hero(boardPages.getValue("board-valhein"))?.let {
                        HeroNames.slug(it)
                    } == "valhein"
            ) {
                "Valhein's ranking read as ${boardPage("board-valhein")} ${BoardScreens.hero(boardPages.getValue("board-valhein"))}"
            }
            check(boardPage("top") == BoardScreens.Page.TOP) { "Số 1 máy chủ read as ${boardPage("top")}" }
            check(boardPage("trailer1") == BoardScreens.Page.OTHER) { "Trailer read as ${boardPage("trailer1")}" }
            // Việt Nam's top three and "Số 1 máy chủ" by default; the whole server's top ten in four screens when chosen.
            check(BuiltInMacros.boardWants(Macro(BuiltInMacros.board(heroes = 2)).json) == BoardScreens.DEFAULT)
            check(
                BuiltInMacros.boardWants(
                    Macro(BuiltInMacros.board(heroes = 2, wanted = listOf(BoardScreens.Want(BoardScreens.Tab.SERVER, 10)))).json
                ).single().pages ==
                    4
            )
            // Names on the All-heroes cards, read by ML Kit on the user's screenshot: the card each is on, and where to tap for one.
            val listSmall = context.assets.open("list-all.jpg").use { BitmapFactory.decodeStream(it) }
            val listShot = Bitmap.createScaledBitmap(listSmall, listSmall.width * 2, listSmall.height * 2, true)
            listSmall.recycle()
            val listWords = GameText.words(listShot).also { listShot.recycle() }
            File(targetContext.filesDir, "smoke").resolve("ocr-list.txt").writeText(
                listWords.joinToString("\n") { w ->
                    "%.3f %.3f %s".format(w.x, w.y, w.text)
                }
            )
            val cards = HeroFinder.cards(listWords, 0.0)
            for ((card, slug) in listOf(
                0 to "flowborn", 1 to "tamyn", 2 to "dyadia", 3 to "flowborn", 5 to "heino", 6 to "biron", 7 to "bolt-baron",
                8 to "billow", 9 to "charlotte"
            )) {
                check(
                    cards.any {
                        it.first == card && HeroNames.slug(it.second.text) == slug
                    }
                ) { "Card ${card + 1} is not $slug: ${cards.map { "${it.first}=${it.second.text}" }}" }
            }
            val bolt = HeroFinder.spot(listWords, 0.0, "bolt-baron", -1)
            check(bolt != null && bolt.first == 7 && Math.abs(bolt.second.first - .58) < .03 && Math.abs(bolt.second.second - .82) < .05) {
                "Bolt Baron found at $bolt"
            }
            // Two cards named Flowborn: the one nearer the card last seen.
            check(
                HeroFinder.spot(listWords, 0.0, "flowborn", 3)?.first == 3 && HeroFinder.spot(listWords, 0.0, "flowborn", 0)?.first == 0
            ) {
                "Flowborn cards mixed up"
            }
            val modeButton = RankScreens.modeButton(modes)
            check(
                modeButton != null &&
                    RankScreens.option(modes, modeButton, RankScreens.Mode.ELITE)?.let { Math.abs(it.y - .444) < .03 } == true &&
                    RankScreens.option(modes, modeButton, RankScreens.Mode.FIVE)?.let {
                        Math.abs(it.y - .378) < .03
                    } == true
            ) { "Drop-down read as $modeButton" }
            // The faint UID line at the bottom of game screens, drawn as the game draws it.
            val strip = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888)
            Canvas(strip).apply {
                drawColor(Color.rgb(36, 40, 96))
                drawText(
                    "UID: 17345555466552433",
                    1070f,
                    1072f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color =
                            Color.rgb(96, 100, 140)
                        textSize = 24f
                        typeface = android.graphics.Typeface.SERIF
                    }
                )
            }
            val uid = GameText.uid(strip)
            strip.recycle()
            check(uid == "17345555466552433") { "UID line read as $uid" }
            // The test account is kept encrypted by the phone's keystore, and read back.
            Accounts.save(targetContext, "17514483308299377", "test@example.com", "p@ss word")
            val account = Accounts.load(targetContext)
            check(
                account != null &&
                    account.user == "test@example.com" &&
                    account.password == "p@ss word" &&
                    Accounts.uid(targetContext) == "17514483308299377"
            ) { "Test account did not survive saving" }
            check(!targetContext.getSharedPreferences("test-account", 0).all.values.joinToString().contains("p@ss")) {
                "The password is stored in clear"
            }
            val accountPrefs = targetContext.getSharedPreferences("test-account", 0)
            val firstRow = org.json.JSONArray(accountPrefs.getString("accounts-v2", "[]")).getJSONObject(0)
            // Recreate v0.8.2's single-account layout with the original encrypted fields.
            accountPrefs.edit().clear().putString("uid", firstRow.getString("uid"))
                .putString("user", firstRow.getString("user")).putString("password", firstRow.getString("password")).commit()
            val migrated = Accounts.list(targetContext).single()
            check(migrated.ready && Accounts.load(targetContext, migrated.id)?.password == "p@ss word") {
                "Legacy account migration lost credentials"
            }
            check(!accountPrefs.contains("uid")) { "Legacy fields were not atomically migrated" }
            val secondAccount = Accounts.save(
                targetContext,
                "17514483308299378",
                "second@example.com",
                "second-password",
                label = "Tài khoản B"
            )
            check(Accounts.list(targetContext).size == 2) { "Saving account B overwrote account A" }
            Accounts.select(targetContext, migrated.id)
            check(Accounts.load(targetContext, secondAccount)?.user == "second@example.com") {
                "Explicit account selection fell back to default"
            }
            Accounts.save(targetContext, migrated.uid, "test@example.com", "", migrated.id, "Tài khoản A")
            check(Accounts.load(targetContext, migrated.id)?.password == "p@ss word") { "Blank edit erased the old password" }
            // A damaged/lost key must leave an account visible so its password can be repaired.
            val savedRows = org.json.JSONArray(accountPrefs.getString("accounts-v2", "[]"))
            for (i in 0 until savedRows.length()) {
                if (savedRows.getJSONObject(i).getString("id") == migrated.id) {
                    savedRows.getJSONObject(i).put("password", "unreadable-ciphertext")
                }
            }
            accountPrefs.edit().putString("accounts-v2", savedRows.toString()).commit()
            check(
                Accounts.list(targetContext).first {
                    it.id == migrated.id
                }.let { !it.ready && it.uid == migrated.uid }
            ) { "Unreadable password hid the old account" }
            Accounts.save(targetContext, migrated.uid, "test@example.com", "repaired-password", migrated.id, "Tài khoản A")
            check(Accounts.load(targetContext, migrated.id)?.password == "repaired-password") { "Account repair failed" }
            Accounts.remove(targetContext, secondAccount)
            check(Accounts.list(targetContext).size == 1 && Accounts.load(targetContext, migrated.id) != null) { "Deleting B erased A" }
            check(Accounts.load(targetContext, secondAccount) == null) { "Deleted account silently fell back to A" }
            val validAccountData = accountPrefs.getString("accounts-v2", null)
            accountPrefs.edit().putString("accounts-v2", "{").commit()
            check(Accounts.list(targetContext).isEmpty() && Accounts.problem(targetContext) != null) {
                "Unreadable account data was not reported"
            }
            var preventedOverwrite = false
            try {
                Accounts.save(targetContext, "17514483308299379", "new@example.com", "new-password")
            } catch (
                _: Exception
            ) {
                preventedOverwrite =
                    true
            }
            check(preventedOverwrite && accountPrefs.getString("accounts-v2", null) == "{") { "Unreadable records were overwritten" }
            accountPrefs.edit().putString("accounts-v2", validAccountData).commit()
            Accounts.clear(targetContext)
            check(Accounts.load(targetContext) == null) { "Test account was not cleared" }
            check(HeroNames.slug("Điêu Thuyền") == "dieu-thuyen")
            // The skills are one step read at run time: at least the four icons' two tabs, more for five icons or other forms.
            val m = Macro(BuiltInMacros.create(""))
            check(m.photoTotal == 10 && m.photosVary && m.photoSteps.size == 2)
            check(Macro(BuiltInMacros.create("", 1, false, fast = true, detailOnly = true)).photoTotal == 6) {
                "Chỉ Chi tiết should keep overview, attributes and 4 details"
            }
            val direct = Macro(BuiltInMacros.create("", 1, false, fast = true, detailOnly = true, directSkills = true))
            check(
                direct.photoTotal == 6 &&
                    (0 until direct.steps.length()).map {
                        direct.steps.getJSONObject(it)
                    }.single {
                        it.getString("type") == "skills"
                    }.getBoolean("directSkills")
            ) { "Chuyển thẳng chiêu should reach the skills step" }
            check(Macro(BuiltInMacros.create("", 11, true)).steps.toString().contains("\"pick\""))
            // Heroes by name: a "find" step a hero, which skips to the next hero's when the list has no such hero.
            val named = Macro(BuiltInMacros.named(listOf("Bolt Baron", " violet ", "Bolt Baron"), listOf(7, -1, 7)))
            val finds = (0 until named.steps.length()).filter { named.steps.getJSONObject(it).getString("type") == "find" }
            check(
                finds.size == 2 &&
                    named.steps.getJSONObject(finds[0]).getInt("skipTo") == finds[1] &&
                    named.steps.getJSONObject(finds[1]).getInt("skipTo") == named.steps.length() &&
                    named.steps.getJSONObject(finds[0]).getInt("hint") == 7 &&
                    named.steps.getJSONObject(finds[1]).getString("name") == "violet" &&
                    BuiltInMacros.heroNames(named.json) == listOf("Bolt Baron", "violet")
            ) { "Heroes by name: ${named.raw}" }
            // Thống kê tướng takes the chosen tables only; one saved before the choice existed takes all six.
            check(
                BuiltInMacros.rankCombos(
                    Macro(BuiltInMacros.ranking(combos = listOf("ty-le-dau-5v5", "chi-so-dau-hang-tinh-anh"))).json
                ).map {
                    it.slug
                } ==
                    listOf("ty-le-dau-5v5", "chi-so-dau-hang-tinh-anh")
            )
            check(BuiltInMacros.rankCombos(JSONObject(BuiltInMacros.ranking()).apply { remove("rankCombos") }).size == 6)
            check(
                BuiltInMacros.secondsPerHero(true, false, false, false) in 30.0..45.0 &&
                    BuiltInMacros.secondsPerHero(true, true, true, true) in 15.0..25.0
            ) { "Time estimate per hero is off" }
            val run = File(targetContext.filesDir, "captures/smoke-demo").apply { mkdirs() }
            File(
                run,
                "run.json"
            ).writeText(
                JSONObject().put(
                    "collectionMode",
                    "all"
                ).put("label", "Buổi thu thập mẫu").put("state", "completed").put("updatedAt", System.currentTimeMillis()).toString()
            )
            val hero = File(run, "elandorr").apply { mkdirs() }
            File(hero, "hero.json").writeText(JSONObject().put("name", "Eland’orr").put("nameSource", "ocr").toString())
            for (name in listOf(
                "0004-hero-overview",
                "0008-hero-attributes",
                "0012-hero-skill-1-summary"
            )) {
                File(hero, "$name.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            image.recycle()
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""))
            // A ZIP job left to the background: the foreground service shows its progress, Hủy cancels it, and with no
            // screen in front its result comes as a notification.
            val notifications = targetContext.getSystemService(
                android.content.Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager

            fun waitFor(what: String, test: () -> Boolean) {
                val until =
                    SystemClock.uptimeMillis() + 5000
                while (!test()) {
                    check(SystemClock.uptimeMillis() < until) { what }
                    SystemClock.sleep(100)
                }
            }
            FileJob.start(targetContext, "Đang lưu ZIP", "Lưu ZIP")
            FileJob.report(50, 100, "File 1/2", "run/hero/0001-hero-001-overview.png")
            waitFor("No progress notification") {
                notifications.activeNotifications.any {
                    it.id == FileJob.NOTIFICATION &&
                        it.notification.extras.getInt(android.app.Notification.EXTRA_PROGRESS) == 500
                }
            }
            var jobService: FileJobService? = null
            waitFor("The ZIP job did not enter the foreground") {
                runOnMainSync {
                    jobService =
                        FileJobService.current
                }
                jobService != null
            }
            targetContext.startService(Intent(targetContext, FileJobService::class.java).setAction(FileJobService.CANCEL))
            waitFor("Hủy in the notification did not cancel the job") { FileJob.cancelled }
            FileJob.finish("Đã hủy lưu ZIP; file ZIP dở đã được xóa.")
            waitFor("No result notification") {
                notifications.activeNotifications.let { all ->
                    all.none { it.id == FileJob.NOTIFICATION } &&
                        all.any { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == "Lưu ZIP" }
                }
            }
            runOnMainSync { jobService = FileJobService.current }
            check(jobService == null && !CollectorService.libraryBusy) { "The ZIP job did not stop" }
            notifications.cancelAll()
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ImageCacheSmoke.check(this, activity as MainActivity, qualityFixture)

            val professionFolder = File(targetContext.filesDir, "captures/title-test/tuong-tac/nghe/nghe/dau-si-1234abcd").apply {
                mkdirs()
            }
            val titleMethod = MainActivity::class.java.getDeclaredMethod("title", File::class.java).apply { isAccessible = true }
            check(titleMethod.invoke(activity, professionFolder) == "Dau si") { "Profession folder mislabeled as a native run" }
            File(targetContext.filesDir, "captures/title-test").deleteRecursively()
            val dest = File(targetContext.filesDir, "smoke").apply { mkdirs() }

            fun snap(n: String) {
                waitForIdleSync()
                SystemClock.sleep(700)
                val b =
                    automation.takeScreenshot() ?: error("No screenshot")
                File(dest, "$n.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle()
            }
            snap("01-history")

            // Viewer: open a photo from history, step to the next one, then return to the top level.
            fun tapText(text: String) {
                waitForIdleSync()
                SystemClock.sleep(500)
                var node = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull() ?: error("Missing $text")
                while (!node.isClickable) node = node.parent ?: error("$text is not clickable")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $text" }
            }
            tapText("Buổi thu thập mẫu")
            tapText("Eland’orr")
            tapText("Tổng quan")
            snap("01b-viewer")
            tapText("Ảnh sau")
            waitForIdleSync()
            SystemClock.sleep(400)
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("2/3")?.isNotEmpty() == true) {
                "Viewer did not move to the next photo"
            }
            snap("01c-viewer-next")
            repeat(3) {
                automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                waitForIdleSync()
                SystemClock.sleep(500)
            }
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Buổi thu thập mẫu")?.isNotEmpty() == true) {
                "Back did not return to history"
            }
            shell("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(1200)
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            var service: CollectorService? = null
            for (attempt in 0..2) {
                for (i in 0..40) {
                    runOnMainSync { service = CollectorService.current }
                    if (service != null) break
                    SystemClock.sleep(250)
                }
                if (service != null) break
                shell("settings put secure enabled_accessibility_services null")
                SystemClock.sleep(1500)
                shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
                shell("settings put secure accessibility_enabled 1")
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys accessibility")).use {
                File(dest, "accessibility.txt").writeBytes(it.readBytes())
            }
            check(service != null) { "Accessibility service not connected" }
            rankingCaptureSmoke(service!!)
            rankingSelectionSmoke(service!!)
            runOnMainSync {
                check(
                    service!!.selectMacro(
                        BuiltInMacros.chess(
                            ChessWiki.Options(
                                setOf("tat-ca-quan-co"),
                                openGame = false,
                                heroDetails = true
                            )
                        )
                    )
                )
                service!!.showPanel()

                fun bar(view: View): android.widget.ProgressBar? {
                    if (view is android.widget.ProgressBar) return view
                    if (view is android.view.ViewGroup) {
                        for (i in 0 until view.childCount) {
                            bar(view.getChildAt(i))?.let { return it }
                        }
                    }
                    return null
                }
                val progress = bar(service!!.panelView ?: error("Missing chess panel"))
                    ?: error("Missing chess progress bar")
                check(progress.visibility == View.GONE) { "Chess showed a percentage before knowing its total" }
            }
            chessNamedCaptureSmoke(service!!)
            // Exact task timing persists across new Macro objects, without borrowing another configuration.
            val timingMacro = Macro(MacroStore.current(targetContext).readText())
            val timingPrefs = targetContext.getSharedPreferences("ui", 0)
            check(RunTimes.get(timingPrefs, RunTimes.task(timingMacro.timingHash)) == null)
            RunTimes.recordTask(timingPrefs, timingMacro.timingHash, 120.0)
            RunTimes.recordTask(timingPrefs, timingMacro.timingHash, 180.0)
            check(RunTimes.taskSamples(timingPrefs, timingMacro.timingHash) == listOf(120.0, 180.0))
            check(RunTimes.get(timingPrefs, RunTimes.task(Macro(JSONObject(timingMacro.raw).toString()).timingHash)) == 150.0)
            val namedTimingA = Macro(BuiltInMacros.named(listOf("Violet"), listOf(-1)))
            val namedTimingB = Macro(BuiltInMacros.named(listOf("Violet"), listOf(8)))
            check(namedTimingA.hash != namedTimingB.hash && namedTimingA.timingHash == namedTimingB.timingHash) {
                "Learned card position invalidated the same task's timing"
            }
            val differentTiming = Macro(BuiltInMacros.chess(ChessWiki.Options(setOf("phe"), openGame = false)))
            check(RunTimes.get(timingPrefs, RunTimes.task(differentTiming.timingHash)) == null)
            runOnMainSync { service!!.showPanel() }
            waitFor("Learned duration missing from floating controller") {
                automation.windows.mapNotNull {
                    it.root
                }.any { it.findAccessibilityNodeInfosByText("Ước tính thu thập ~2:30").isNotEmpty() }
            }
            timingPrefs.edit().remove(
                RunTimes.task(timingMacro.timingHash)
            ).remove(RunTimes.task(timingMacro.timingHash) + ":samples").commit()
            // Choose B for this run, then change the global default to A: resume must stay on B.
            run {
                fun window(title: String) = automation.windows.mapNotNull { it.root }
                    .firstOrNull { it.findAccessibilityNodeInfosByText(title).isNotEmpty() }

                fun press(title: String, text: String) {
                    waitForIdleSync()
                    val node =
                        window(title)?.findAccessibilityNodeInfosByText(text)?.firstOrNull {
                            it.text?.toString()?.equals(text, true) == true
                        }
                            ?: error("Missing $text in $title")
                    check(
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    )
                    waitForIdleSync()
                    SystemClock.sleep(350)
                }
                runOnMainSync {
                    check(
                        service!!.selectMacro(
                            BuiltInMacros.chess(
                                ChessWiki.Options(
                                    setOf("tat-ca-quan-co"),
                                    openGame = true,
                                    checkAccount = true,
                                    traceEntry = false
                                )
                            )
                        )
                    )
                    service!!.start()
                }
                waitFor("Missing account silently disabled UID verification") { window("Chưa có tài khoản đã lưu") != null }
                press("Chưa có tài khoản đã lưu", "Hủy")
                val a = Accounts.save(targetContext, "17514483308299377", "a@example.com", "password-a", label = "Tài khoản A")
                val b = Accounts.save(targetContext, "17514483308299378", "b@example.com", "password-b", label = "Tài khoản B")
                Accounts.select(targetContext, a)
                runOnMainSync { service!!.start() }
                val title = "Chọn tài khoản cho lượt chạy"
                waitFor("Run did not offer the account picker") { window(title) != null }
                press(title, Accounts.list(targetContext).first { it.id == b }.caption)
                snap("01d-account-picker")
                press(title, "Chạy")
                val runPrefs = targetContext.getSharedPreferences("collector", 0)
                waitFor("Chosen account was not pinned to the run") { runPrefs.getString("accountId", null) == b }
                waitFor("Missing game did not end the test entry step") {
                    var idle = false
                    runOnMainSync { idle = !service!!.isWorking }
                    idle
                }
                check(runPrefs.getString("accountUid", null) == "17514483308299378") { "Run pinned the wrong UID" }
                Accounts.select(targetContext, a)
                runOnMainSync { service!!.start() }
                waitFor("Resume entry did not settle") {
                    var idle = false
                    runOnMainSync { idle = !service!!.isWorking }
                    idle
                }
                check(window(title) == null && runPrefs.getString("accountId", null) == b) { "Resume switched to the new default account" }
                Accounts.remove(targetContext, b)
                runOnMainSync { service!!.start() }
                // The selected account has gone: no fallback to A and no game interaction.
                var idle = false
                runOnMainSync { idle = !service!!.isWorking }
                check(idle && runPrefs.getString("accountId", null) == b && Accounts.load(targetContext, a) != null) {
                    "Deleted run account fell back to A"
                }
                Accounts.clear(targetContext)
                runOnMainSync { service!!.reset() }
            }
            // A built-in macro saved by an older version is regenerated when a new run is created.
            MacroStore.current(targetContext).writeText(
                JSONObject(BuiltInMacros.create("Danh sách tướng", 2, true)).apply {
                    remove("builtinVersion")
                }.toString()
            )
            runOnMainSync { service!!.reset() }
            val upgraded = Macro(MacroStore.current(targetContext).readText())
            check(upgraded.json.optInt("builtinVersion") == BuiltInMacros.VERSION && upgraded.json.optInt("heroCount") == 2) {
                "Old built-in macro was not upgraded"
            }
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""))
            runOnMainSync { service!!.reset() }
            // Legacy users may have saved icon-only mode. It must no longer hide labels.
            targetContext.getSharedPreferences("ui", 0).edit().putBoolean("panelShowLabels", false).commit()
            runOnMainSync { service!!.showPanel() }
            check(!targetContext.getSharedPreferences("ui", 0).contains("panelShowLabels")) {
                "Obsolete icon-only preference was not removed"
            }

            // Exercise the actual chooser button, repeatedly, then after rotation.
            fun chooserClick() {
                waitForIdleSync()
                SystemClock.sleep(500)
                val node = automation.windows.mapNotNull { it.root }.flatMap {
                    it.findAccessibilityNodeInfosByText("Lấy thông tin tướng") +
                        it.findAccessibilityNodeInfosByText("Đóng danh sách")
                }.firstOrNull { it.contentDescription?.toString() == "Chọn macro, có tìm kiếm" }
                    ?: error("Missing chooser button")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
                SystemClock.sleep(250)
            }
            repeat(3) {
                chooserClick()
                chooserClick()
            }
            runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(1200)
            chooserClick()
            snap("02-task-picker")

            fun nodes() = automation.windows.mapNotNull { it.root }

            fun click(text: String) {
                val node =
                    nodes().flatMap {
                        it.findAccessibilityNodeInfosByText(text)
                    }.firstOrNull { it.text?.toString()?.equals(text, true) == true }
                        ?: error("Missing $text")
                check(
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                        node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                )
                waitForIdleSync()
                SystemClock.sleep(500)
            }

            // Search the actual floating dropdown before selecting.
            fun findEdit(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (n.className?.toString() ==
                    "android.widget.EditText"
                ) {
                    return n
                }
                for (i in 0 until n.childCount) {
                    val child =
                        n.getChild(i) ?: continue
                    val e = findEdit(child)
                    if (e != null) return e
                }
                return null
            }
            val search = nodes().firstNotNullOfOrNull { findEdit(it) } ?: error("Missing macro search")
            check(
                search.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "lay thong tin")
                    }
                )
            )
            waitForIdleSync()
            SystemClock.sleep(400)
            snap("02b-search")
            // Heroes by name: one kept from an earlier run, ticked from the search, and a new name typed and added.
            File(targetContext.filesDir, HeroIndex.FILE).writeText("3\tFlowborn\n7\tBolt Baron\n-1\tViolet\n")
            targetContext.getSharedPreferences("ui", 0).edit().putInt("heroMode", 0).remove("pickNames").commit()
            click("Lấy thông tin tướng")
            SystemClock.sleep(800)
            snap("03-hero-form")
            click("Theo tên tướng")

            fun edits(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo> = ArrayList()): List<AccessibilityNodeInfo> {
                if (n.className?.toString() ==
                    "android.widget.EditText"
                ) {
                    out += n
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { edits(it, out) }
                return out
            }

            fun nameField() = nodes().flatMap { edits(it) }.firstOrNull { it.hintText?.toString()?.startsWith("Gõ tên tướng") == true }
                ?: error("No hero name search")

            fun type(text: String) {
                check(
                    nameField().performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        }
                    )
                )
                waitForIdleSync()
                SystemClock.sleep(500)
            }
            type("bolt")
            click("Bolt Baron")
            type("Qi")
            click("Thêm tướng: Qi")
            snap("03b-hero-names")
            click("Chọn tác vụ")
            val picked = Macro(MacroStore.current(targetContext).readText())
            val firstFind = (0 until picked.steps.length()).map { picked.steps.getJSONObject(it) }.first { it.getString("type") == "find" }
            check(
                picked.json.optString("collectionMode") == "pick" &&
                    BuiltInMacros.heroNames(picked.json) == listOf("Bolt Baron", "Qi") &&
                    firstFind.getInt("hint") == 7
            ) { "Heroes by name chose ${picked.raw.take(400)}" }
            // The hero page open now, with its name typed.
            chooserClick()
            check(
                nodes().firstNotNullOfOrNull {
                    findEdit(it)
                }!!.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "lay thong tin")
                    }
                )
            )
            waitForIdleSync()
            SystemClock.sleep(400)
            click("Lấy thông tin tướng")
            SystemClock.sleep(800)
            click("Tướng đang mở")
            val single =
                nodes().flatMap { edits(it) }.firstOrNull { it.hintText?.toString() == "Để trống để tự đọc tên" } ?: error("No name input")
            check(
                single.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Violet")
                    }
                )
            )
            click("Chọn tác vụ")
            check(
                Macro(MacroStore.current(targetContext).readText()).json.let {
                    it.optString("heroName") == "Violet" &&
                        it.optString("collectionMode") == "single"
                }
            )
            snap("04-panel")

            // The chosen macro is visible and each action shows its label with its icon.
            fun selectedMacroVisible() = nodes().flatMap { it.findAccessibilityNodeInfosByText("Lấy thông tin tướng") }
                .any { it.text?.toString() == "Lấy thông tin tướng" && it.contentDescription?.toString() == "Chọn macro, có tìm kiếm" }
            check(selectedMacroVisible()) { "Selected macro is not shown in the compact picker" }
            for (caption in listOf("Chụp", "Về app", "Đóng", "Dừng")) {
                check(
                    nodes().flatMap { it.findAccessibilityNodeInfosByText(caption) }
                        .any { it.text?.toString() == caption && it.contentDescription?.toString() == caption }
                ) {
                    "Icon and readable label missing: $caption"
                }
            }
            check(
                nodes().flatMap { it.findAccessibilityNodeInfosByText("Ẩn chữ") }
                    .none { it.text?.toString() == "Ẩn chữ" }
            ) { "Obsolete hide-label option is still visible" }
            check(
                nodes().flatMap { it.findAccessibilityNodeInfosByText("Hiện chữ") }
                    .none { it.text?.toString() == "Hiện chữ" }
            ) { "Obsolete show-label option is still visible" }
            snap("04a-always-labeled-controls")
            // Restore an unfinished current run; unrelated old runs must stay out of its popup.
            val currentRunMacro = Macro(MacroStore.current(targetContext).readText())
            targetContext.getSharedPreferences("collector", 0).edit().putString("hash", currentRunMacro.hash)
                .putString("run", "progress-smoke").putInt("step", 0).putBoolean("stopped", false).commit()
            shell("settings put secure enabled_accessibility_services null")
            waitFor("Old capture service did not stop") {
                runOnMainSync { service = CollectorService.current }
                service == null
            }
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            waitFor("Service did not restore current capture run") {
                runOnMainSync { service = CollectorService.current }
                service != null
            }
            GestureDebugSmoke.run(this, service!!)
            runOnMainSync { service!!.showPanel() }
            // The real asynchronous image writer feeds a durable, ordered second overlay.
            val historyDir = File(targetContext.filesDir, "captures/progress-smoke").apply { mkdirs() }
            File(historyDir, "hero.json").writeText(JSONObject().put("name", "Violet").toString())

            fun savePreviewImage(name: String): CaptureHistory.Entry {
                val ready = java.util.concurrent.CountDownLatch(1)
                val success = java.util.concurrent.atomic.AtomicBoolean(false)
                runOnMainSync {
                    service!!.store(
                        Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) },
                        "progress-smoke",
                        name
                    ) {
                        success.set(it)
                        ready.countDown()
                    }
                }
                check(ready.await(10, java.util.concurrent.TimeUnit.SECONDS) && success.get()) { "History writer did not save" }
                return CaptureHistory.entries(targetContext).last()
            }
            val savedRow = savePreviewImage("hero-001-overview")
            val secondRow = savePreviewImage("hero-001-skill-1-detail")
            check(savedRow.name == "violet-1" && secondRow.name == "violet-2") {
                "Unfriendly or unordered image names: ${savedRow.name}, ${secondRow.name}"
            }
            check(savedRow.state == "saved" && savedRow.bytes > 0 && File(targetContext.filesDir, "captures/" + savedRow.path).isFile)
            val oldDir = File(targetContext.filesDir, "captures/previous-progress").apply { mkdirs() }
            File(oldDir, "hero.json").writeText(JSONObject().put("name", "OldHero").toString())
            val oldImage = File(oldDir, "old.png")
            File(targetContext.filesDir, "captures/" + savedRow.path).copyTo(oldImage, true)
            val oldOrder = CaptureHistory.begin(targetContext, "previous-progress", oldImage)
            CaptureHistory.finish(targetContext, oldOrder, true)
            val failedOrder = CaptureHistory.begin(
                targetContext,
                "progress-smoke",
                File(targetContext.filesDir, "captures/progress-errors/failed.png")
            )
            CaptureHistory.finish(targetContext, failedOrder, false)
            val interruptedCapture = CaptureHistory.begin(
                targetContext,
                "progress-smoke",
                File(targetContext.filesDir, "captures/progress-errors/interrupted.png")
            )
            CaptureHistory.recover(targetContext)
            val journal = CaptureHistory.entries(targetContext)
            check(
                journal.zipWithNext().all { (a, b) -> a.order < b.order } &&
                    journal.last().order == interruptedCapture &&
                    journal.last().state == "interrupted"
            )
            check(journal.first { it.order == failedOrder }.state == "failed")
            click("Ảnh")
            waitFor("Missing live saved-image row") { nodes().any { it.findAccessibilityNodeInfosByText(savedRow.name).isNotEmpty() } }
            check(nodes().any { it.findAccessibilityNodeInfosByText("Đã lưu trong app").isNotEmpty() })
            check(
                nodes().none {
                    it.findAccessibilityNodeInfosByText("oldhero-1").isNotEmpty()
                }
            ) { "Current popup includes an earlier run" }
            check(nodes().any { it.findAccessibilityNodeInfosByText("Lưu thất bại").isNotEmpty() })
            snap("04c-live-capture-history")
            check(nodes().none { it.findAccessibilityNodeInfosByText("progress-smoke/").isNotEmpty() }) { "History exposes storage paths" }

            fun openHistoryImage(name: String) {
                val node = nodes().flatMap { it.findAccessibilityNodeInfosByText("Mở ảnh $name") }
                    .firstOrNull { it.contentDescription?.toString() == "Mở ảnh $name" } ?: error("Missing image $name")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
            }

            fun previewImage() = nodes().flatMap { it.findAccessibilityNodeInfosByText("Ảnh lớn số ${savedRow.order}") }
                .firstOrNull { it.contentDescription?.toString() == "Ảnh lớn số ${savedRow.order}" }
            openHistoryImage(savedRow.name)
            waitFor("Large image did not load") { previewImage()?.stateDescription?.toString() == "Phóng to 100%" }
            val largeBounds = Rect().also { previewImage()!!.getBoundsInScreen(it) }
            check(largeBounds.width() > 400 && largeBounds.height() > 200) { "Image preview is not enlarged: $largeBounds" }
            val closePreviewNode = nodes().flatMap { it.findAccessibilityNodeInfosByText("Đóng ảnh lớn") }
                .first { it.contentDescription?.toString() == "Đóng ảnh lớn" }
            val closePreviewBounds = Rect().also { closePreviewNode.getBoundsInScreen(it) }
            check(closePreviewBounds.width() >= Ui.dp(targetContext, 60)) { "Preview close button is clipped: $closePreviewBounds" }

            fun previewAction(label: String) {
                waitFor("Missing preview action $label") {
                    nodes().any { root ->
                        root.findAccessibilityNodeInfosByText(label).any { it.contentDescription?.toString() == label }
                    }
                }
                val node = nodes().flatMap { it.findAccessibilityNodeInfosByText(label) }
                    .firstOrNull { it.contentDescription?.toString() == label } ?: error("Missing preview action $label")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
            }
            previewAction("Phóng to ảnh")
            waitFor("Image did not zoom") { previewImage()?.stateDescription?.toString() == "Phóng to 150%" }
            snap("04d-large-image-preview")
            previewAction("Thu nhỏ ảnh")
            waitFor("Image did not zoom out") { previewImage()?.stateDescription?.toString() == "Phóng to 100%" }
            previewAction("Ảnh sau")
            waitFor("Next image did not load") {
                nodes().any { it.findAccessibilityNodeInfosByText("Ảnh lớn số ${secondRow.order}").isNotEmpty() }
            }
            previewAction("Ảnh trước")
            waitFor("Previous image did not return") { previewImage()?.stateDescription?.toString() == "Phóng to 100%" }
            previewAction("Sao chép ảnh")
            var clipboardURI: android.net.Uri? = null
            waitFor("Image was not placed on clipboard") {
                runOnMainSync {
                    clipboardURI = targetContext.getSystemService(
                        android.content.ClipboardManager::class.java
                    ).primaryClip?.getItemAt(0)?.uri
                }
                clipboardURI != null
            }
            val originalBytes = File(targetContext.filesDir, "captures/" + savedRow.path).readBytes()
            check(targetContext.contentResolver.openInputStream(clipboardURI!!)!!.use { it.readBytes() }.contentEquals(originalBytes))
            // The receiver is a separate application/UID and receives access through the clipboard grant.
            targetContext.startActivity(
                Intent().setClassName(
                    "com.openai.chatgpt",
                    "vn.ndang.chatfixture.ClipboardActivity"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            waitFor("Another app could not paste clipboard image") {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    automation.executeShellCommand("run-as com.openai.chatgpt cat files/pasted-image.bin")
                ).use {
                    it.readBytes()
                }.contentEquals(originalBytes)
            }
            targetContext.startActivity(
                Intent(targetContext, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            waitFor("Collector did not return to landscape after pasting") {
                targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            waitForIdleSync()
            SystemClock.sleep(500)
            // The receiving app can rotate the device; rotation intentionally closes the large preview.
            var needsPreview = false
            runOnMainSync { needsPreview = service!!.capturePreviewView == null }
            if (needsPreview) openHistoryImage(savedRow.name)
            waitFor("Preview did not reopen after clipboard app") { previewImage()?.stateDescription?.toString() == "Phóng to 100%" }
            val previewShotDone = java.util.concurrent.CountDownLatch(1)
            val previewShotOK = java.util.concurrent.atomic.AtomicBoolean(false)
            var previewHidden = false
            runOnMainSync {
                service!!.grab(true) {
                    previewShotOK.set(it != null)
                    it?.recycle()
                    previewShotDone.countDown()
                }
                previewHidden = service!!.capturePreviewView?.visibility == View.INVISIBLE
            }
            check(previewHidden) { "Large preview not hidden for screenshot" }
            check(previewShotDone.await(10, java.util.concurrent.TimeUnit.SECONDS) && previewShotOK.get())
            runOnMainSync { check(service!!.capturePreviewView?.visibility == View.VISIBLE) }
            previewAction("Đóng ảnh lớn")
            runOnMainSync { check(service!!.capturePreviewView == null && service!!.captureProgressView != null) }
            // Select exactly two current-run images and exercise the existing SAF ZIP writer.
            targetContext.startActivity(Intent(context, PhotoGrantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            SystemClock.sleep(700)
            targetContext.contentResolver.takePersistableUriPermission(
                photoTree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            targetContext.startActivity(
                Intent(targetContext, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            waitForIdleSync()
            val chosenZip = android.provider.DocumentsContract.createDocument(
                targetContext.contentResolver,
                photoRoot,
                "application/zip",
                "selected-current-images.zip"
            )!!
            val resultIntent = Intent().setData(chosenZip).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val exportFilter = android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("application/zip")
            }
            val exportMonitor = addMonitor(exportFilter, Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent), true)
            try {
                previewAction("Chọn ảnh trong lượt")
                previewAction("Chọn ${savedRow.name}")
                previewAction("Chọn ${secondRow.name}")
                previewAction("Xuất ảnh đã chọn")
                waitFor("Popup export did not open the file picker") { exportMonitor.hits > 0 }
                waitFor("Selected image ZIP export did not finish") {
                    nodes().any { it.findAccessibilityNodeInfosByText("Đã lưu ZIP gồm 2 file").isNotEmpty() }
                }
                val zipBytes = targetContext.contentResolver.openInputStream(chosenZip)!!.use { it.readBytes() }
                val names = linkedSetOf<String>()
                java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { outer ->
                    check(outer.nextEntry != null)
                    val innerBytes = outer.readBytes()
                    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(innerBytes)).use { inner ->
                        var item = inner.nextEntry
                        while (item != null) {
                            names += item.name
                            inner.closeEntry()
                            item = inner.nextEntry
                        }
                    }
                }
                check(names == setOf(savedRow.path, secondRow.path)) { "Export included unselected/older images: $names" }
                click("Đã hiểu")
            } finally {
                removeMonitor(exportMonitor)
            }
            android.provider.DocumentsContract.deleteDocument(targetContext.contentResolver, chosenZip)
            runOnMainSync { service!!.showPanel() }
            previewAction("Mở hoặc đóng tiến trình ảnh")
            waitFor("Current images did not return after export") {
                nodes().any { it.findAccessibilityNodeInfosByText(savedRow.name).isNotEmpty() }
            }
            runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(1000)
            runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(1000)
            waitFor("Current images did not survive rotation") {
                nodes().any { it.findAccessibilityNodeInfosByText(savedRow.name).isNotEmpty() }
            }
            // Removing an older file must not renumber the next image or break a stale thumbnail tap.
            File(targetContext.filesDir, "captures/" + savedRow.path).delete()
            check(CaptureHistory.entries(targetContext).first { it.order == secondRow.order }.name == "violet-2")
            val thirdRow = savePreviewImage("hero-001-attributes")
            check(thirdRow.name == "violet-3") { "Image counter reset after deleting an older image" }
            File(targetContext.filesDir, "captures/" + savedRow.path).delete()
            waitFor("Removed image still reported as saved") {
                nodes().any { it.findAccessibilityNodeInfosByText("File đã bị xóa").isNotEmpty() }
            }

            fun closeHistory() {
                val node = nodes().flatMap { it.findAccessibilityNodeInfosByText("×") }
                    .firstOrNull { it.contentDescription?.toString() == "Đóng tiến trình ảnh" } ?: error("Missing history close")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
            }
            closeHistory()
            runOnMainSync { check(service!!.captureProgressView == null) }
            // This restored unfinished run can still be ended after exporting its images.
            check(
                nodes().flatMap {
                    it.findAccessibilityNodeInfosByText("Dừng")
                }.firstOrNull { it.text?.toString() == "Dừng" }?.isEnabled == true
            ) { "Dừng is disabled for the unfinished current run" }
            // The actual completed-run action renders the restart vector and resets without starting a run.
            val chosen = Macro(MacroStore.current(targetContext).readText())
            targetContext.getSharedPreferences("collector", 0).edit().putString("hash", chosen.hash)
                .putInt("step", chosen.steps.length()).commit()
            runOnMainSync {
                service!!.showPanel()

                fun restart(view: View): android.widget.Button? {
                    if (view is android.widget.Button && view.text.toString() == "Tạo lượt mới") return view
                    if (view is android.view.ViewGroup) {
                        for (i in 0 until view.childCount) {
                            restart(view.getChildAt(i))?.let { return it }
                        }
                    }
                    return null
                }
                val button = restart(service!!.panelView ?: error("Missing panel")) ?: error("Missing new-run action")
                val icon = button.compoundDrawables[0] ?: error("Missing restart icon")
                check(icon.bounds.width() > 0 && icon.bounds.height() > 0) { "Restart icon has no size" }
                val preview = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val original = Rect(icon.bounds)
                val canvas = Canvas(preview)
                canvas.drawColor(Color.rgb(96, 75, 209))
                icon.setBounds(12, 12, 84, 84)
                icon.draw(canvas)
                icon.bounds = original
                File(dest, "reset-icon.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                preview.recycle()
            }
            snap("04b-create-new-run")
            click("Tạo lượt mới")
            check(targetContext.getSharedPreferences("collector", 0).getInt("step", 0) == 0) { "New-run action did not reset" }
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Chạy") }.any { it.text?.toString() == "Chạy" }) {
                "New-run action did not return to the ready state"
            }
            check(
                nodes().flatMap { it.findAccessibilityNodeInfosByText("Dừng") }.firstOrNull { it.text?.toString() == "Dừng" }?.isEnabled ==
                    false
            ) { "Dừng is enabled before any new run" }
            runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(1500)
            snap("05-landscape")
            click("Ảnh")
            waitFor("New run still shows earlier images") {
                nodes().any { it.findAccessibilityNodeInfosByText("Chưa có ảnh trong lượt hiện tại").isNotEmpty() }
            }
            runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(1000)
            runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(1000)
            waitFor("Empty current run did not restore after rotation") {
                nodes().any { it.findAccessibilityNodeInfosByText("Chưa có ảnh trong lượt hiện tại").isNotEmpty() }
            }
            snap("05b-right-progress-panel")
            val shotFinished = java.util.concurrent.CountDownLatch(1)
            var cleanShot: Bitmap? = null
            runOnMainSync {
                service!!.grab(true) {
                    cleanShot = it
                    shotFinished.countDown()
                }
                check(service!!.panelView?.visibility == View.INVISIBLE && service!!.captureProgressView?.visibility == View.INVISIBLE)
            }
            check(shotFinished.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "Shielded screenshot did not finish" }
            val screenshot = cleanShot ?: error("Shielded screenshot failed")
            File(dest, "05c-capture-without-overlays.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            runOnMainSync {
                check(service!!.panelView?.visibility == View.VISIBLE && service!!.captureProgressView?.visibility == View.VISIBLE)
            }

            // v0.3.1 regression: a macro tap under the floating panel pressed its hidden buttons
            // (Đóng removed the panel mid-run) instead of reaching the game.
            fun closeButton() = nodes().flatMap {
                it.findAccessibilityNodeInfosByText("Đóng")
            }.firstOrNull { it.text?.toString() == "Đóng" }
            val box = Rect().also { (closeButton() ?: error("Missing panel close button")).getBoundsInScreen(it) }

            fun tapAt() = GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply {
                        moveTo(box.exactCenterX(), box.exactCenterY())
                    },
                    0,
                    CollectorService.TAP_MS
                )
            ).build()
            // Old behaviour, recorded only: hide the panel and inject in the same frame.
            runOnMainSync {
                service!!.panelView?.visibility = View.INVISIBLE
                service!!.dispatchGesture(tapAt(), null, null)
            }
            SystemClock.sleep(1500)
            var legacyClosed = false
            runOnMainSync {
                legacyClosed = service!!.panelView == null
                if (legacyClosed) {
                    service!!.showPanel()
                } else {
                    service!!.panelView?.visibility =
                        View.VISIBLE
                }
            }
            waitForIdleSync()
            SystemClock.sleep(800)
            val finished = java.util.concurrent.CountDownLatch(1)
            val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
            runOnMainSync {
                service!!.touchGame(tapAt(), true) {
                    delivered.set(it)
                    finished.countDown()
                }
                check(service!!.captureProgressView?.visibility == View.INVISIBLE) { "Progress overlay was not shielded" }
            }
            check(finished.await(8, java.util.concurrent.TimeUnit.SECONDS)) { "Macro tap never finished" }
            waitForIdleSync()
            SystemClock.sleep(600)
            var panelKept = false
            var swallowed = 0
            runOnMainSync {
                panelKept = service!!.panelView?.visibility == View.VISIBLE
                swallowed = service!!.swallowedTaps
            }
            snap("06-after-macro-tap")
            check(delivered.get()) { "Macro tap was not delivered past the panel (swallowed=$swallowed)" }
            check(panelKept && closeButton() != null) { "Macro tap closed or hid the floating panel" }
            runOnMainSync { check(service!!.captureProgressView?.visibility == View.VISIBLE) { "Progress overlay did not return" } }
            closeHistory()

            // Before a run opens the game, a game left running is ended on its App info page: Buộc dừng, then OK.
            // Checked against the emulator's real Settings, on one of its own apps (English labels there).
            fun output(command: String) =
                android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                    String(it.readBytes())
                }.trim()
            val installed = output("pm list packages")
            val (app, label) = listOf(
                "com.android.deskclock" to "Clock",
                "com.android.calculator2" to "Calculator",
                "com.android.contacts" to "Contacts",
                "com.android.camera2" to "Camera"
            )
                .firstOrNull { installed.contains("package:${it.first}\n") || installed.endsWith("package:${it.first}") }
                ?: error("No emulator app to close: $installed")
            shell("monkey -p $app -c android.intent.category.LAUNCHER 1")
            SystemClock.sleep(3000)
            check(output("pidof $app").isNotEmpty()) { "$app did not start" }

            fun close(): GameCloser.Result? {
                val latch = java.util.concurrent.CountDownLatch(1)
                var r: GameCloser.Result? = null
                runOnMainSync {
                    service!!.closeApp(app, { true }, label) {
                        r = it
                        latch.countDown()
                    }
                }
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                return r
            }
            val closed = close()
            snap("07-force-stop")
            check(closed == GameCloser.Result.CLOSED && output("pidof $app").isEmpty()) {
                "Force stop of $app: $closed, pid ${output("pidof $app")}"
            }
            val again = close()
            check(again == GameCloser.Result.NOT_RUNNING) { "A stopped $app read as $again" }
            // Giao diện Tối: the history and a task form over the game in the dark look.
            targetContext.getSharedPreferences("ui", 0).edit().putString("theme", "dark").commit()
            startActivitySync(
                Intent(targetContext, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )

            fun luma(): Double {
                waitForIdleSync()
                SystemClock.sleep(700)
                val shot =
                    automation.takeScreenshot() ?: error("No screenshot")
                val b = shot.copy(Bitmap.Config.ARGB_8888, false)
                shot.recycle()
                var sum = 0L
                var n = 0
                for (y in 0 until b.height step 12) {
                    for (x in 0 until b.width step 12) {
                        val p = b.getPixel(x, y)
                        sum +=
                            (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000
                        n++
                    }
                }
                b.recycle()
                return sum.toDouble() / n
            }
            check(Ui.colors === Ui.DARK) { "Giao diện Tối was not taken" }
            val darkHistory = luma()
            snap("08-dark-history")
            check(darkHistory < 90) { "The dark history is not dark: mean brightness $darkHistory" }
            runOnMainSync { service!!.showPanel() }
            chooserClick()
            check(
                nodes().firstNotNullOfOrNull {
                    findEdit(it)
                }!!.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "lay thong tin")
                    }
                )
            )
            waitForIdleSync()
            SystemClock.sleep(400)
            snap("09-dark-picker")
            click("Lấy thông tin tướng")
            SystemClock.sleep(800)
            val darkForm = luma()
            snap("10-dark-form")
            check(darkForm < 90) { "The dark task form is not dark: mean brightness $darkForm" }
            click("Hủy")
            // No saved accounts: UID verification remains selectable and turns on game entry.
            targetContext.getSharedPreferences("ui", 0).edit().putBoolean("chessOpenGame", false)
                .putBoolean("chessCheckAccount", false).commit()
            chooserClick()
            check(
                nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "co lien quan") }
                )
            )
            waitForIdleSync()
            SystemClock.sleep(400)
            click("Cờ Liên Quân · Bách khoa")
            val verifyText = "Kiểm tra UID · chọn tài khoản khi bấm Chạy"
            val verify = nodes().flatMap { it.findAccessibilityNodeInfosByText(verifyText) }
                .firstOrNull { it.text?.toString() == verifyText } ?: error("Missing account verification option")
            check(verify.isEnabled) { "No saved account still disables verification without a setup path" }
            verify.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            waitForIdleSync()
            SystemClock.sleep(400)
            click(verifyText)
            check(
                nodes().flatMap { it.findAccessibilityNodeInfosByText("Tự mở game tới sảnh trước khi vào Cờ Liên Quân") }
                    .any { it.isChecked }
            ) { "Enabling account verification did not enable game entry" }
            snap("11-account-setup-enabled")
            click("Hủy")
            click("Ảnh gốc theo lượt · Chọn Pictures một lần ›")
            for (label in listOf(
                "Chọn / đổi thư mục Pictures",
                "Thử đồng bộ lại ảnh đang chờ",
                "Mở ảnh gốc của lượt gần nhất"
            )) {
                check(
                    nodes().flatMap { it.findAccessibilityNodeInfosByText(label) }
                        .any { it.text?.toString() == label && it.isVisibleToUser && it.isEnabled }
                ) {
                    "Pictures actions are hidden behind the dialog message: $label"
                }
            }
            snap("12-pictures-actions")
            click("Mở ảnh gốc của lượt gần nhất")
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Chưa có ảnh gốc nào được sao chép.") }.isNotEmpty())
            click("Đã hiểu")
            click("Sau khi hoàn tất · Drive → dot của bạn ›")
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Tự lưu kết quả lên Drive sau khi hoàn tất lượt") }.isNotEmpty())
            check(
                nodes().flatMap {
                    it.findAccessibilityNodeInfosByText("Drive tải xong → mở ChatGPT → dot của bạn → gửi câu lệnh")
                }.isNotEmpty()
            )
            snap("13-after-run-settings")
            click("Tự lưu kết quả lên Drive sau khi hoàn tất lượt")
            val zipButton = "Chọn ZIP đích trên Google Drive"
            nodes().flatMap { it.findAccessibilityNodeInfosByText(zipButton) }.first { it.text?.toString() == zipButton }
                .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            waitForIdleSync()
            SystemClock.sleep(300)
            click(zipButton)
            check(!AfterRun.config(targetContext).enabled) { "Picking a location applied an unsaved automation setting" }

            fun afterSettingsVisible() = nodes().flatMap { it.findAccessibilityNodeInfosByText("Sau khi hoàn tất") }
                .any { it.isVisibleToUser }
            repeat(3) {
                if (!afterSettingsVisible()) {
                    automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    waitForIdleSync()
                    SystemClock.sleep(450)
                }
            }
            waitFor("Drive picker cancellation did not return to settings") { afterSettingsVisible() }
            check(!AfterRun.config(targetContext).enabled) { "Cancelling the Drive picker enabled automation" }
            click("Đóng")
            targetContext.getSharedPreferences("ui", 0).edit().remove("theme").commit()
            result.putString(
                "stream",
                "SMOKE_OK: history, viewer, macro upgrade, OCR, task selection, current-run capture progress, clipboard image paste across apps, selected ZIP export, quick image navigation, named images, enlarged preview and zoom, learned task time, saved/missing/failed files, shielded screenshot, overlay, rotation, tap-through, force stop and dark look passed (legacyTapClosedPanel=$legacyClosed, swallowedRetries=$swallowed)\n"
            )
            finish(Activity.RESULT_OK, result)
        } catch (t: Throwable) {
            try {
                val auto = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                val out = File(targetContext.filesDir, "smoke").apply { mkdirs() }
                auto.takeScreenshot()?.let { shot ->
                    File(out, "failure.png").outputStream().use {
                        shot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    shot.recycle()
                }
                val tree = StringBuilder()

                fun visit(n: AccessibilityNodeInfo, depth: Int) {
                    if (depth > 24 || tree.length > 100_000) return
                    tree.append("  ".repeat(depth)).append(n.className).append(" text=")
                        .append(if (n.isPassword) "[password]" else n.text).append(" desc=").append(n.contentDescription).append('\n')
                    for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it, depth + 1) }
                }
                auto.windows.mapNotNull { it.root }.forEach { visit(it, 0) }
                File(out, "failure-ui.txt").writeText(tree.toString())
            } catch (_: Throwable) {}
            result.putString("stream", "SMOKE_FAILED: ${t.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    /** Synthetic statistics page only: real ML Kit, controller, PNG writer and bitmap ownership. */
    private fun rankingCaptureSmoke(service: CollectorService) {
        val fixture = Bitmap.createBitmap(2048, 922, Bitmap.Config.ARGB_8888)
        Canvas(fixture).apply {
            drawColor(Color.rgb(58, 65, 145))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
            }
            drawText("Bảng Phân Bậc Tướng", 220f, 90f, paint)
            drawText("Đấu hạng (Tất cả)", 990f, 205f, paint)
            drawText("Tỷ lệ thắng", 820f, 315f, paint)
            drawText("Tỷ lệ pick", 1060f, 315f, paint)
            drawText("Tỷ lệ Ban", 1280f, 315f, paint)
            drawText("Synthetic fixture", 220f, 440f, paint)
            drawText("50,00%", 840f, 440f, paint)
            drawText("22,54%", 1060f, 440f, paint)
            drawText("4,69%", 1280f, 440f, paint)
        }
        val fixtureWords = GameText.rankingWords(fixture)
        check(RankScreens.switchButton(fixtureWords) == null && RankScreens.page(fixtureWords, false) == RankScreens.Page.RANKING) {
            "Synthetic statistics page needs its missing switch: $fixtureWords"
        }
        val ended = java.util.concurrent.CountDownLatch(1)
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        var outcome: RankingReader.Outcome? = null
        var acquired = 0
        var consumed = 0
        var saved = 0
        var finished = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val folder = "ranking-save-smoke"
        val screen = object : RankingReader.Screen {
            override val alive get() = active.get() && outcome == null

            override fun look(words: Boolean, then: (RankingReader.Look?) -> Unit) {
                acquired++
                val shot = fixture.copy(Bitmap.Config.ARGB_8888, false)
                val text = if (words) fixtureWords else emptyList<GameScreens.Word>()
                then(object : RankingReader.Look {
                    private var released = false
                    override val words = text
                    override val lobby = false
                    override val signature = IntArray(96) { 42 }

                    private fun consume() {
                        check(!released)
                        released = true
                        consumed++
                    }

                    override fun done(keep: String?) {
                        check(keep == null)
                        consume()
                        shot.recycle()
                    }

                    override fun keep(name: String, then: (Boolean) -> Unit) {
                        consume()
                        service.store(shot, folder, name) { ok ->
                            if (ok) saved++
                            then(ok)
                        }
                    }
                })
            }

            override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                error("Verified synthetic table must not tap")
            }

            override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                check(saved == 1)
                handler.post { then(true) }
            }

            override fun later(ms: Long, then: () -> Unit) {
                handler.post(then)
            }

            override fun finished(combo: String) {
                check(saved == 1)
                finished++
            }
        }
        runOnMainSync {
            RankingReader(screen, wanted = listOf(RankScreens.Combo(RankScreens.Table.RATES, RankScreens.Mode.ALL))) {
                outcome = it
                ended.countDown()
            }.start()
        }
        try {
            check(ended.await(15, java.util.concurrent.TimeUnit.SECONDS)) { "Synthetic ranking capture did not finish" }
            check(outcome is RankingReader.Outcome.Done && saved == 1 && finished == 1 && acquired == consumed) {
                "Ranking capture: outcome=$outcome acquired=$acquired consumed=$consumed saved=$saved finished=$finished"
            }
            val png = File(targetContext.filesDir, "captures/$folder").walkTopDown().filter { it.extension == "png" }.single()
            val stored = BitmapFactory.decodeFile(png.absolutePath) ?: error("Ranking PNG is unreadable")
            try {
                check(stored.width == 2048 && stored.height == 922)
            } finally {
                stored.recycle()
            }
            File(targetContext.filesDir, "smoke/ranking-capture.txt").writeText(
                "Verified synthetic page without switch label: saved=$saved, consumed=$consumed/$acquired, bytes=${png.length()}\n" +
                    fixtureWords.joinToString("\n")
            )
        } finally {
            runOnMainSync {
                active.set(false)
                fixture.recycle()
            }
        }
    }

    /** Synthetic mode popup at the measured geometry: OCR → actual reader → clean PNG, all six choices. */
    private fun rankingSelectionSmoke(service: CollectorService) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val report = StringBuilder()

        fun fixture(table: RankScreens.Table, mode: RankScreens.Mode, open: Boolean) =
            Bitmap.createBitmap(2048, 922, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                canvas.drawColor(Color.rgb(58, 65, 145))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 34f
                }
                canvas.drawText("Bảng Phân Bậc Tướng", 250f, 88f, paint)
                canvas.drawText("Đổi bảng xếp hạng", 1580f, 204f, paint)
                paint.textSize = 30f
                canvas.drawText(mode.label, 1015f, 205f, paint)
                if (table == RankScreens.Table.RATES) {
                    canvas.drawText("Tỷ lệ thắng", 840f, 310f, paint)
                    canvas.drawText("Tỷ lệ pick", 1070f, 310f, paint)
                    canvas.drawText("Tỷ lệ Ban", 1290f, 310f, paint)
                } else {
                    canvas.drawText("Sát thương", 910f, 285f, paint)
                    canvas.drawText("mỗi trận", 925f, 320f, paint)
                    canvas.drawText("Vàng", 1380f, 310f, paint)
                    canvas.drawText("Sát thương phải chịu", 1540f, 310f, paint)
                }
                canvas.drawText("Synthetic row", 250f, 405f, paint)
                canvas.drawText("123,45", 915f, 405f, paint)
                canvas.drawText("45,67", 1150f, 405f, paint)
                canvas.drawText("8,90", 1370f, 405f, paint)
                if (open) {
                    paint.color = Color.rgb(45, 48, 110)
                    canvas.drawRect(990f, 245f, 1450f, 460f, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 29f
                    RankScreens.Mode.values().forEachIndexed { i, m -> canvas.drawText(m.label, 1025f, 300f + 60f * i, paint) }
                }
            }
        val misplaced = GameScreens.Word("Đấu hạng (Tất cả)", .32, .12, .40, .14)
        val source = fixture(RankScreens.Table.RATES, RankScreens.Mode.ALL, false)
        for ((width, height) in listOf(1280 to 720, 2048 to 922, 2400 to 1080)) {
            val resized = Bitmap.createScaledBitmap(source, width, height, true)
            try {
                val controls = GameText.rankingWords(resized, listOf(misplaced))
                val target = RankScreens.modeButton(controls) ?: error("Resized mode control unreadable: ${width}x$height")
                check(target.x in .49..0.63 && target.y in .17..0.24) {
                    "Resized mode target escaped visible control: ${width}x$height at ${target.x},${target.y}"
                }
                report.append("PASS resized mode target ${width}x$height: ${target.x},${target.y}\n")
            } finally {
                if (resized !== source) resized.recycle()
            }
        }
        source.recycle()
        for (combo in RankScreens.COMBOS) {
            val menu = fixture(combo.table, RankScreens.Mode.ALL, true)
            val initial = fixture(combo.table, RankScreens.Mode.ALL, false)
            val closed = fixture(combo.table, combo.mode, false)
            val menuWords = GameText.rankingWords(menu, listOf(misplaced))
            val initialWords = GameText.rankingWords(initial, listOf(misplaced))
            val closedWords = GameText.rankingWords(closed)
            check(RankScreens.menuOverlay(menu.width, menu.height) { y, row -> menu.getPixels(row, 0, menu.width, 0, y, menu.width, 1) })
            check(
                !RankScreens.menuOverlay(closed.width, closed.height) { y, row ->
                    closed.getPixels(row, 0, closed.width, 0, y, closed.width, 1)
                }
            )
            val button = RankScreens.modeButton(menuWords) ?: error("Synthetic mode button missing: $menuWords")
            check(RankScreens.menuModes(menuWords, button).size == 3) { "Synthetic options missing: $menuWords" }
            check(RankScreens.table(closedWords) == combo.table && RankScreens.mode(RankScreens.modeButton(closedWords)) == combo.mode) {
                "Synthetic closed selection unreadable for ${combo.slug}: $closedWords"
            }
            val active = java.util.concurrent.atomic.AtomicBoolean(true)
            val ended = java.util.concurrent.CountDownLatch(1)
            var outcome: RankingReader.Outcome? = null
            var open = combo.mode == RankScreens.Mode.ALL
            var picked: RankScreens.Mode? = null
            var saves = 0
            var consumed = 0
            var acquired = 0
            var taps = 0
            val folder = "ranking-selection-${combo.slug}"
            val screen = object : RankingReader.Screen {
                override val alive get() = active.get() && outcome == null

                override fun look(words: Boolean, then: (RankingReader.Look?) -> Unit) {
                    acquired++
                    val wasOpen = open
                    val shot = (
                        if (wasOpen) {
                            menu
                        } else if (picked == null) {
                            initial
                        } else {
                            closed
                        }
                        ).copy(Bitmap.Config.ARGB_8888, false)
                    val read = if (words) {
                        if (wasOpen) {
                            menuWords
                        } else if (picked == null) {
                            initialWords
                        } else {
                            closedWords
                        }
                    } else {
                        emptyList<GameScreens.Word>()
                    }
                    then(object : RankingReader.Look {
                        var released = false
                        override val words = read
                        override val menuOverlay = RankScreens.menuOverlay(shot.width, shot.height) { y, row ->
                            shot.getPixels(row, 0, shot.width, 0, y, shot.width, 1)
                        }
                        override val lobby = false
                        override val signature = IntArray(96) { 42 }

                        fun release() {
                            check(!released)
                            released = true
                            consumed++
                        }

                        override fun done(keep: String?) {
                            check(keep == null)
                            release()
                            shot.recycle()
                        }

                        override fun keep(name: String, then: (Boolean) -> Unit) {
                            check(!wasOpen && picked == combo.mode) { "Saved a menu or wrong mode: ${combo.slug}" }
                            release()
                            service.store(shot, folder, name) { ok ->
                                if (ok) saves++
                                then(ok)
                            }
                        }
                    })
                }

                override fun tap(x: Double, y: Double, then: (Boolean) -> Unit) {
                    if (!open) {
                        check(picked == null && x in .49..0.63 && y in .17..0.24) { "Dropdown opening missed visible control: $x,$y" }
                        open = true
                    } else {
                        check(x in .48..0.72 && y in .27..0.49) { "Unexpected dropdown option tap $x,$y" }
                        val row = RankScreens.Mode.values().minBy { kotlin.math.abs(y - (.31 + .065 * it.ordinal)) }
                        check(row == combo.mode) { "Selected $row instead of ${combo.mode}" }
                        picked = row
                        open = false
                    }
                    taps++
                    handler.post { then(true) }
                }

                override fun drag(d: Double, fling: Boolean, then: (Boolean) -> Unit) {
                    check(saves == 1)
                    handler.post { then(true) }
                }

                override fun later(ms: Long, then: () -> Unit) {
                    handler.post(then)
                }

                override fun note(text: String) {
                    report.append(text).append('\n')
                }
            }
            runOnMainSync {
                RankingReader(screen, wanted = listOf(combo)) {
                    outcome = it
                    ended.countDown()
                }.start()
            }
            try {
                check(ended.await(15, java.util.concurrent.TimeUnit.SECONDS)) { "Selection timed out: ${combo.slug}" }
                val expectedTaps = if (combo.mode == RankScreens.Mode.ALL) 1 else 2
                check(outcome is RankingReader.Outcome.Done && saves == 1 && taps == expectedTaps && acquired == consumed) {
                    "Selection ${combo.slug}: $outcome saves=$saves taps=$taps consumed=$consumed/$acquired"
                }
                val png = File(targetContext.filesDir, "captures/$folder").walkTopDown().filter { it.extension == "png" }.single()
                val actual = BitmapFactory.decodeFile(png.absolutePath) ?: error("Selection PNG unreadable")
                try {
                    check(actual.sameAs(closed)) { "Stored selection contains a menu/wrong frame: ${combo.slug}" }
                } finally {
                    actual.recycle()
                }
                report.append("PASS ${combo.slug}: $taps verified mode taps, one closed-menu PNG, $consumed/$acquired frames released\n")
            } finally {
                runOnMainSync {
                    active.set(false)
                    menu.recycle()
                    initial.recycle()
                    closed.recycle()
                }
            }
        }
        File(targetContext.filesDir, "smoke/ranking-selection.txt").writeText(report.toString())
    }

    /** Real PNG storage under synthetic chess content; no game/account access. */
    private fun chessNamedCaptureSmoke(service: CollectorService) {
        // Configure only the storage routing context; never start the macro or game gestures.
        val macroField = CollectorService::class.java.getDeclaredField("macro").apply { isAccessible = true }
        var previous: Any? = null
        runOnMainSync {
            previous = macroField.get(service)
            macroField.set(
                service,
                Macro(
                    BuiltInMacros.chess(
                        ChessWiki.Options(
                            setOf("tat-ca-quan-co"),
                            openGame = false,
                            heroDetails = true
                        )
                    )
                )
            )
        }
        try {
            val folder = "chess-naming-smoke"
            val token = "0001-chess__quan-co__tat-ca-quan-co__tuong__violet__001"

            fun fixture(shade: Int) = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(60, 60, 60))
                Canvas(this).drawRect(320f, 100f, 520f, 260f, Paint().apply { color = Color.rgb(shade, shade, shade) })
            }

            fun save(shade: Int): Boolean {
                val ended = java.util.concurrent.CountDownLatch(1)
                var result = false
                val shot = fixture(shade)
                runOnMainSync {
                    service.store(shot, folder, token) { ok ->
                        result = ok
                        ended.countDown()
                    }
                }
                check(ended.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "Chess PNG writer stalled" }
                check(shot.isRecycled) { "Chess PNG frame leaked" }
                return result
            }
            check(save(60)) { "Chess detail PNG failed" }
            val root = File(targetContext.filesDir, "captures/$folder")
            val target = File(root, "quan-co/tat-ca-quan-co/tuong/violet/violet-detail-1.png")
            check(target.isFile && root.walkTopDown().count { it.extension == "png" } == 1) {
                "Chess detail filename/routing mismatch"
            }
            val original = target.readBytes()
            check(save(62) && original.contentEquals(target.readBytes())) { "Resume overwrote numbered detail" }
            // Expected storage rejection must not become a later UI crash-notice dialog.
            val crash = CrashLog.file(targetContext)
            val priorLog = crash.takeIf { it.isFile }?.readBytes()
            val priorTime = crash.lastModified()
            try {
                check(!save(160) && original.contentEquals(target.readBytes())) { "Changed content overwrote numbered detail" }
            } finally {
                if (priorLog == null) {
                    crash.delete()
                } else {
                    crash.writeBytes(priorLog)
                    crash.setLastModified(priorTime)
                }
            }
            check(CaptureHistory.entries(targetContext).any { it.name == "violet-detail-1" && it.state == "saved" })
            File(targetContext.filesDir, "smoke/chess-capture.txt").writeText(
                "Clean filename: violet-detail-1.png; verified retry preserved bytes; changed content refused.\n"
            )
        } finally {
            runOnMainSync { macroField.set(service, previous) }
        }
    }
}
