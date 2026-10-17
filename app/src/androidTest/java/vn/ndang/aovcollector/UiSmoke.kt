package vn.ndang.aovcollector

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
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import org.json.JSONObject
import java.io.File

/** Runs the actual UI and bundled OCR without a game, account or network. */
class UiSmoke:Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val result=Bundle()
        try {
            val automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val info=automation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.serviceInfo=info
            // Instrumentation restarts the app process; rebind its accessibility service afterwards.
            fun shell(command:String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() } }
            shell("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(500)
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            val image=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.drawColor(Color.rgb(29,32,60))
            canvas.drawText("Eland'orr",1770f,385f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(248,226,170);textSize=58f })
            val name=HeroNames.read(image)
            check(name!=null&&HeroNames.slug(name)=="elandorr") { "OCR returned $name" }
            // Qi's name region from a real run, where ML Kit read nothing.
            val qi=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888)
            context.assets.open("qi-name.png").use { BitmapFactory.decodeStream(it) }.let { Canvas(qi).apply { drawColor(Color.rgb(20,30,50)) }.drawBitmap(it,1752f,318f,null);it.recycle() }
            val qiLog=ArrayList<String>();val qiName=HeroNames.read(qi) { qiLog+=it };qi.recycle()
            File(targetContext.filesDir,"smoke").apply { mkdirs() }.resolve("ocr-qi.txt").writeText("$qiName\n"+qiLog.joinToString("\n"))
            check(qiName=="Qi") { "OCR read $qiName for Qi: ${qiLog.joinToString("; ")}" }
            // User regression: the popup is Arduin even though card OCR read "A Đấu sĩ".
            context.assets.open("chess-arduin-role-ocr.jpg").use { input ->
                val shot=BitmapFactory.decodeStream(input)
                val title=try { GameText.chessHeroTitle(shot) } finally { shot.recycle() }
                check(title!=null&&HeroNames.slug(title)=="arduin") { "Chess heading OCR read $title for Arduin" }
            }
            // ZIP regression: exactly five heroes, not another row made from truncated cyan factions.
            context.assets.open("chess-first-row.png").use { input ->
                val shot=BitmapFactory.decodeStream(input)
                try {
                    val topic=ChessWiki.TOPICS.first { it.key=="tat-ca-quan-co" }
                    val words=GameText.words(shot)
                    val cards=ChessWiki.tiles(topic,shot,words)
                    File(targetContext.filesDir,"smoke/ocr-chess.txt").writeText(
                        cards.joinToString { it.label?:"?" }+"\n"+
                        words.joinToString("\n") { "%.3f %.3f %s".format(it.x,it.y,it.text) })
                    check(cards.map { it.label }==listOf("Violet","Arduin","Hayate","Raz","Lumburr")) {
                        "Chess first row has false/missing cards: $cards"
                    }
                    // A white faction icon beside cyan letters is not a white hero title.
                    val injected=words+GameScreens.Word("Unknown faction",.218,.672,.326,.709)
                    check(ChessWiki.tiles(topic,shot,injected).size==5) { "Cyan faction became another hero row" }
                } finally { shot.recycle() }
            }
            context.assets.open("chess-violet-price.png").use { input ->
                val shot=BitmapFactory.decodeStream(input)
                val title=try { GameText.chessHeroTitle(shot) } finally { shot.recycle() }
                check(title=="Violet") { "Violet's gold price became part of her name: $title" }
            }
            context.assets.open("chess-save-photo-sure.jpg").use { input ->
                val screenshot=BitmapFactory.decodeStream(input)
                val sure=try { ChessWiki.nativeConfirm(GameText.words(screenshot)) } finally { screenshot.recycle() }
                check(sure!=null&&sure.first in .30.. .45&&sure.second in .48.. .65) {
                    "Real Save Photo dialog did not locate Sure: $sure"
                }
            }
            // Native Save Photo exports go through a real SAF provider, then appear in app history.
            val photoTree=android.provider.DocumentsContract.buildTreeDocumentUri(
                "vn.ndang.aovcollector.test.photos","Pictures")
            context.startActivity(Intent(context,PhotoGrantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync();SystemClock.sleep(700)
            GamePhotoImport.choose(targetContext,photoTree)
            val photoRoot=android.provider.DocumentsContract.buildDocumentUriUsingTree(photoTree,"Pictures")
            fun nativeFile(name:String):android.net.Uri {
                val uri=android.provider.DocumentsContract.createDocument(targetContext.contentResolver,
                    photoRoot,"image/jpeg",name)!!
                // Valid bytes from the bundled screenshot; the game can use .png (1).jpg names.
                context.assets.open("chess-arduin-role-ocr.jpg").use { input ->
                    targetContext.contentResolver.openOutputStream(uri)!!.use { output -> input.copyTo(output) }
                }
                return uri
            }
            val personal=nativeFile("personal-before.jpg")
            val nativeRun="smoke-native-save"
            check(GamePhotoSession.begin(targetContext,nativeRun)==1)
            GamePhotoSession.mark(targetContext,nativeRun,"quan-co","Violet")
            val waiting=GamePhotoSession.importNew(targetContext,nativeRun)
            check(waiting.pending==1&&waiting.copied==0) { "Sure without a visible file was acknowledged" }
            val violet=nativeFile("caYyujwz.png (1).jpg")
            val copied=GamePhotoSession.importNew(targetContext,nativeRun)
            check(copied.copied==1&&copied.pending==0) { "Late Pictures image was not imported: $copied" }
            val imported=File(targetContext.filesDir,"captures/"+GamePhotoImport.ROOT)
            val violetCopy=imported.walkTopDown().first { it.isFile&&it.name.startsWith("violet-") }
            check(violetCopy.extension=="jpg"&&violetCopy.path.contains("quan-co/anh-quan-co"))
            targetContext.contentResolver.openInputStream(violet)!!.use {
                check(it.readBytes().contentEquals(violetCopy.readBytes())) { "Native image bytes changed" }
            }
            // A later different category cannot rename/reassign the already imported Violet.
            SystemClock.sleep(50)
            GamePhotoSession.mark(targetContext,nativeRun,"chieu-quan-co","Violet-skill")
            val skill=nativeFile("skill.png (1).jpg")
            val second=GamePhotoSession.importNew(targetContext,nativeRun)
            check(second.copied==1&&second.already==1&&second.pending==0)
            check(violetCopy.isFile&&imported.walkTopDown().count { GamePhotoImport.isImage(it) }==2)
            check(GamePhotoSession.importNew(targetContext,nativeRun).copied==0) { "Sync duplicated native photos" }
            violetCopy.delete()
            check(GamePhotoSession.importNew(targetContext,nativeRun).copied==1) { "Deleted internal image cannot be restored" }
            SystemClock.sleep(50)
            GamePhotoSession.mark(targetContext,nativeRun,"phe","Doa-lac-gia")
            val faction=nativeFile("faction.png (1).jpg")
            val oldRevision=GamePhotoSync.revision
            GamePhotoSync.request(targetContext)
            val copyDeadline=SystemClock.uptimeMillis()+10_000
            while((GamePhotoSync.revision==oldRevision||GamePhotoSync.busy)&&SystemClock.uptimeMillis()<copyDeadline)
                SystemClock.sleep(100)
            check(GamePhotoSync.revision>oldRevision&&!GamePhotoSync.busy) { "After-Sure background sync did not finish" }
            check(imported.walkTopDown().any { it.isFile&&it.path.contains("tuong-tac/phe") })
            check(GamePhotoImport.scan(targetContext,photoTree).size==4) { "Original Pictures files changed" }
            // Full post-run pipeline: real SAF ZIP/read-back, no ChatGPT before Drive proof,
            // then navigate a separate fixture app's drawer -> dot -> composer -> Send.
            targetContext.contentResolver.takePersistableUriPermission(photoTree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            targetContext.getSharedPreferences("after-run",0).edit().putString("tree",photoTree.toString()).commit()
            val savedCommand="Phân tích kết quả đã lưu trên Drive · smoke"
            AfterRun.saveConfig(targetContext,true,true,savedCommand)
            AfterRun.finished(targetContext,nativeRun)
            fun awaitStage(wanted:String,timeout:Long=25_000) {
                val until=SystemClock.uptimeMillis()+timeout
                while(AfterRun.state(targetContext,nativeRun)?.optString("stage")!=wanted&&SystemClock.uptimeMillis()<until)
                    SystemClock.sleep(150)
                check(AfterRun.state(targetContext,nativeRun)?.optString("stage")==wanted) {
                    "Post-run expected $wanted: ${AfterRun.state(targetContext,nativeRun)}"
                }
            }
            awaitStage("waiting-drive")
            val upload=AfterRun.state(targetContext,nativeRun)!!
            check(upload.optBoolean("verified")&&!upload.optBoolean("driveProof"))
            val zipName=upload.getString("filename")
            AfterRun.driveNotification(targetContext,AfterRunProtocol.DRIVE,"Đang tải lên $zipName")
            check(AfterRun.state(targetContext,nativeRun)!!.getString("stage")=="waiting-drive")
            AfterRun.driveNotification(targetContext,AfterRunProtocol.DRIVE,"Đã tải lên other.zip")
            check(AfterRun.state(targetContext,nativeRun)!!.getString("stage")=="waiting-drive")
            AfterRun.driveNotification(targetContext,AfterRunProtocol.DRIVE,"Đã tải lên $zipName")
            awaitStage("done")
            val sentText=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "run-as com.openai.chatgpt cat files/sent.txt")).use { it.bufferedReader().readText() }
            check(sentText=="dot\n"+savedCommand) { "Command went to the wrong destination: $sentText" }
            AfterRun.finished(targetContext,nativeRun)
            check(AfterRun.state(targetContext,nativeRun)!!.getString("stage")=="done")
            File(targetContext.filesDir,"smoke/after-run.txt").writeText("zip-readback=verified; wait-for-drive=ok; dot-navigation=ok; sent-once=ok")
            android.provider.DocumentsContract.deleteDocument(targetContext.contentResolver,android.net.Uri.parse(upload.getString("uri")))
            targetContext.getSharedPreferences("after-run",0).edit().clear().commit()
            File(targetContext.filesDir,"after-run").deleteRecursively()
            File(targetContext.filesDir,"smoke/native-photos.txt").writeText("late-indexed=ok; bytes=identical; classified=ok; dedup=ok; restore=ok")
            listOf(personal,violet,skill,faction).forEach { android.provider.DocumentsContract.deleteDocument(targetContext.contentResolver,it) }
            targetContext.contentResolver.releasePersistableUriPermission(photoTree,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            targetContext.getSharedPreferences("game-photo-source",0).edit().clear().commit()
            File(targetContext.filesDir,"captures/$nativeRun").deleteRecursively()
            imported.deleteRecursively()
            File(targetContext.filesDir,"pictures-copy-ledger.json").delete()
            val chessOptions=ChessWiki.Options(setOf("tat-ca-quan-co"),openGame=false,heroDetails=true)
            val legacyChess=JSONObject(BuiltInMacros.chess(chessOptions)).apply { remove("fast") }
            check(BuiltInMacros.chessOptions(legacyChess).fast) { "Old chess macro did not default to fast mode" }
            for(fast in listOf(false,true)) {
                val selected=chessOptions.copy(fast=fast)
                check(BuiltInMacros.chessOptions(JSONObject(BuiltInMacros.chess(selected)))==selected) {
                    "Chess speed setting did not survive save/load"
                }
            }
            // The way into the game: a list run that opens the game starts with the entry step, and a popup shot on
            // the user's phone, fading in, is read on Android as in the unit tests.
            check(Macro(BuiltInMacros.create("",2,true,openGame=true)).steps.getJSONObject(0).getString("type")=="enter") { "Tự vào game should start with the entry step" }
            val popup=context.assets.open("game-popup-fest.jpg").use { BitmapFactory.decodeStream(it) }
            val seen=GameScreens.read(popup.width,popup.height) { y,row -> popup.getPixels(row,0,popup.width,0,y,popup.width,1) };popup.recycle()
            check(seen.kind==GameScreens.Kind.POPUP&&Math.abs(seen.close!!.first-.913)<.01&&Math.abs(seen.close!!.second-.056)<.015) { "Popup read as ${seen.kind} ${seen.close}" }
            // Screens known by their words, read by ML Kit on the user's screenshots: where the run would tap.
            fun words(asset:String):GameScreens.Reading { val b=context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                val pixels=GameScreens.read(b.width,b.height) { y,row -> b.getPixels(row,0,b.width,0,y,b.width,1) }
                return GameScreens.refine(pixels,GameText.words(b),null).also { b.recycle() } }
            val reward=words("game-reward-stars.jpg")
            check(reward.kind==GameScreens.Kind.CONTINUE&&Math.abs(reward.tap!!.first-.5)<.05&&Math.abs(reward.tap!!.second-.91)<.04) { "Reward screen read as ${reward.kind} ${reward.tap}: ${reward.words}" }
            val register=words("game-register.jpg")
            check(register.kind==GameScreens.Kind.REGISTER&&Math.abs(register.tap!!.first-.749)<.04&&Math.abs(register.tap!!.second-.194)<.04) { "Registration form read as ${register.kind} ${register.tap}: ${register.words}" }
            val form=words("game-garena-form.jpg")
            check(form.kind==GameScreens.Kind.FORM) { "Garena page read as ${form.kind}: ${form.words}" }
            // The way to the hero statistics, read by ML Kit on the user's screenshots: each page known by its words.
            val rankLog=StringBuilder()
            fun rank(asset:String):List<GameScreens.Word> { val b=context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                return GameText.words(b).also { b.recycle();rankLog.append("$asset\n").append(it.joinToString("\n") { w -> "  %.3f %.3f %s".format(w.x,w.y,w.text) }).append("\n") } }
            val more=rank("rank-more.jpg"); val community=rank("rank-community.jpg"); val heroes=rank("rank-community-heroes.jpg")
            val rates=rank("rank-ranking-rates.jpg"); val figures=rank("rank-ranking-figures.jpg"); val modes=rank("rank-ranking-modes.jpg")
            File(targetContext.filesDir,"smoke").apply { mkdirs() }.resolve("ocr-ranking.txt").writeText(rankLog.toString())
            check(RankScreens.page(more,false)==RankScreens.Page.PANEL&&more.any { it.plain.contains("congdong")&&Math.abs(it.x-.15)<.06&&Math.abs(it.y-.52)<.05 }) { "Tính năng khác read as ${RankScreens.page(more,false)}" }
            check(RankScreens.page(community,false)==RankScreens.Page.COMMUNITY) { "Cộng đồng read as ${RankScreens.page(community,false)}" }
            check(RankScreens.page(heroes,false)==RankScreens.Page.HEROES&&heroes.any { it.plain.contains("bangxephangdaydu")&&Math.abs(it.y-.082)<.04 }) { "Cộng đồng heroes read as ${RankScreens.page(heroes,false)}" }
            for((list,table) in listOf(rates to RankScreens.Table.RATES,figures to RankScreens.Table.FIGURES)) {
                check(RankScreens.page(list,false)==RankScreens.Page.RANKING&&RankScreens.table(list)==table&&RankScreens.mode(RankScreens.modeButton(list))==RankScreens.Mode.ALL) {
                    "Ranking read as ${RankScreens.page(list,false)} ${RankScreens.table(list)} ${RankScreens.mode(RankScreens.modeButton(list))}" } }
            // Chiến khu, read by ML Kit on the user's screenshots (kept at half size, the size ML Kit reads them at).
            val boardLog=StringBuilder()
            fun board(name:String):List<GameScreens.Word> { val small=context.assets.open("board-$name.jpg").use { BitmapFactory.decodeStream(it) }
                val b=Bitmap.createScaledBitmap(small,small.width*2,small.height*2,true); small.recycle()
                return GameText.words(b).also { b.recycle();boardLog.append("$name\n").append(it.joinToString("\n") { w -> "  %.3f %.3f %s".format(w.x,w.y,w.text) }).append("\n") } }
            val boardPages=listOf("ranked","ranked-new","season-memory","season-data","season-heroes","season-open","season-first","season-title","season-reward",
                "region","region-country","region-area","region-province","board-province","board-country","board-valhein","picker","trailer1","top").associateWith { board(it) }
            File(targetContext.filesDir,"smoke").resolve("ocr-board.txt").writeText(boardLog.toString())
            fun boardPage(name:String)=BoardScreens.page(boardPages.getValue(name),false)
            for(name in listOf("ranked","ranked-new")) check(boardPage(name)==BoardScreens.Page.RANKED&&BoardScreens.chienKhu(boardPages.getValue(name))!=null) { "$name read as ${boardPage(name)}" }
            for(name in listOf("season-memory","season-data","season-heroes","season-open","season-first","season-title","season-reward")) check(boardPage(name)==BoardScreens.Page.SEASON) { "$name read as ${boardPage(name)}" }
            for(name in listOf("region","region-country","region-area","region-province")) check(boardPage(name)==BoardScreens.Page.REGION) { "$name read as ${boardPage(name)}" }
            check(BoardScreens.region(boardPages.getValue("region-area")).let { it.open==1&&it.options.any { o -> o.plain.contains("mientrung") } }) { "Area list read as ${BoardScreens.region(boardPages.getValue("region-area")).options}" }
            for(name in listOf("board-province","board-country")) check(boardPage(name)==BoardScreens.Page.BOARD&&BoardScreens.hero(boardPages.getValue(name))?.let { HeroNames.slug(it) }=="flowborn") { "$name read as ${boardPage(name)} ${BoardScreens.hero(boardPages.getValue(name))}" }
            check(boardPage("picker")==BoardScreens.Page.PICKER) { "Chọn tướng read as ${boardPage("picker")}" }
            check(boardPage("board-valhein")==BoardScreens.Page.BOARD&&BoardScreens.hero(boardPages.getValue("board-valhein"))?.let { HeroNames.slug(it) }=="valhein") { "Valhein's ranking read as ${boardPage("board-valhein")} ${BoardScreens.hero(boardPages.getValue("board-valhein"))}" }
            check(boardPage("top")==BoardScreens.Page.TOP) { "Số 1 máy chủ read as ${boardPage("top")}" }
            check(boardPage("trailer1")==BoardScreens.Page.OTHER) { "Trailer read as ${boardPage("trailer1")}" }
            // Việt Nam's top three and "Số 1 máy chủ" by default; the whole server's top ten in four screens when chosen.
            check(BuiltInMacros.boardWants(Macro(BuiltInMacros.board(heroes=2)).json)==BoardScreens.DEFAULT)
            check(BuiltInMacros.boardWants(Macro(BuiltInMacros.board(heroes=2,wanted=listOf(BoardScreens.Want(BoardScreens.Tab.SERVER,10)))).json).single().pages==4)
            // Names on the All-heroes cards, read by ML Kit on the user's screenshot: the card each is on, and where to tap for one.
            val listSmall=context.assets.open("list-all.jpg").use { BitmapFactory.decodeStream(it) }
            val listShot=Bitmap.createScaledBitmap(listSmall,listSmall.width*2,listSmall.height*2,true); listSmall.recycle()
            val listWords=GameText.words(listShot).also { listShot.recycle() }
            File(targetContext.filesDir,"smoke").resolve("ocr-list.txt").writeText(listWords.joinToString("\n") { w -> "%.3f %.3f %s".format(w.x,w.y,w.text) })
            val cards=HeroFinder.cards(listWords,0.0)
            for((card,slug) in listOf(0 to "flowborn",1 to "tamyn",2 to "dyadia",3 to "flowborn",5 to "heino",6 to "biron",7 to "bolt-baron",8 to "billow",9 to "charlotte"))
                check(cards.any { it.first==card&&HeroNames.slug(it.second.text)==slug }) { "Card ${card+1} is not $slug: ${cards.map { "${it.first}=${it.second.text}" }}" }
            val bolt=HeroFinder.spot(listWords,0.0,"bolt-baron",-1)
            check(bolt!=null&&bolt.first==7&&Math.abs(bolt.second.first-.58)<.03&&Math.abs(bolt.second.second-.82)<.05) { "Bolt Baron found at $bolt" }
            // Two cards named Flowborn: the one nearer the card last seen.
            check(HeroFinder.spot(listWords,0.0,"flowborn",3)?.first==3&&HeroFinder.spot(listWords,0.0,"flowborn",0)?.first==0) { "Flowborn cards mixed up" }
            val modeButton=RankScreens.modeButton(modes)
            check(modeButton!=null&&RankScreens.option(modes,modeButton,RankScreens.Mode.ELITE)?.let { Math.abs(it.y-.444)<.03 }==true&&RankScreens.option(modes,modeButton,RankScreens.Mode.FIVE)?.let { Math.abs(it.y-.378)<.03 }==true) { "Drop-down read as $modeButton" }
            // The faint UID line at the bottom of game screens, drawn as the game draws it.
            val strip=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888)
            Canvas(strip).apply { drawColor(Color.rgb(36,40,96));drawText("UID: 17345555466552433",1070f,1072f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(96,100,140);textSize=24f;typeface=android.graphics.Typeface.SERIF }) }
            val uid=GameText.uid(strip);strip.recycle()
            check(uid=="17345555466552433") { "UID line read as $uid" }
            // The test account is kept encrypted by the phone's keystore, and read back.
            Accounts.save(targetContext,"17514483308299377","test@example.com","p@ss word")
            val account=Accounts.load(targetContext)
            check(account!=null&&account.user=="test@example.com"&&account.password=="p@ss word"&&Accounts.uid(targetContext)=="17514483308299377") { "Test account did not survive saving" }
            check(!targetContext.getSharedPreferences("test-account",0).all.values.joinToString().contains("p@ss")) { "The password is stored in clear" }
            val accountPrefs=targetContext.getSharedPreferences("test-account",0)
            val firstRow=org.json.JSONArray(accountPrefs.getString("accounts-v2","[]")).getJSONObject(0)
            // Recreate v0.8.2's single-account layout with the original encrypted fields.
            accountPrefs.edit().clear().putString("uid",firstRow.getString("uid"))
                .putString("user",firstRow.getString("user")).putString("password",firstRow.getString("password")).commit()
            val migrated=Accounts.list(targetContext).single()
            check(migrated.ready&&Accounts.load(targetContext,migrated.id)?.password=="p@ss word") { "Legacy account migration lost credentials" }
            check(!accountPrefs.contains("uid")) { "Legacy fields were not atomically migrated" }
            val secondAccount=Accounts.save(targetContext,"17514483308299378","second@example.com","second-password",label="Tài khoản B")
            check(Accounts.list(targetContext).size==2) { "Saving account B overwrote account A" }
            Accounts.select(targetContext,migrated.id)
            check(Accounts.load(targetContext,secondAccount)?.user=="second@example.com") { "Explicit account selection fell back to default" }
            Accounts.save(targetContext,migrated.uid,"test@example.com","",migrated.id,"Tài khoản A")
            check(Accounts.load(targetContext,migrated.id)?.password=="p@ss word") { "Blank edit erased the old password" }
            // A damaged/lost key must leave an account visible so its password can be repaired.
            val savedRows=org.json.JSONArray(accountPrefs.getString("accounts-v2","[]"))
            for(i in 0 until savedRows.length()) if(savedRows.getJSONObject(i).getString("id")==migrated.id)
                savedRows.getJSONObject(i).put("password","unreadable-ciphertext")
            accountPrefs.edit().putString("accounts-v2",savedRows.toString()).commit()
            check(Accounts.list(targetContext).first { it.id==migrated.id }.let { !it.ready&&it.uid==migrated.uid }) { "Unreadable password hid the old account" }
            Accounts.save(targetContext,migrated.uid,"test@example.com","repaired-password",migrated.id,"Tài khoản A")
            check(Accounts.load(targetContext,migrated.id)?.password=="repaired-password") { "Account repair failed" }
            Accounts.remove(targetContext,secondAccount)
            check(Accounts.list(targetContext).size==1&&Accounts.load(targetContext,migrated.id)!=null) { "Deleting B erased A" }
            check(Accounts.load(targetContext,secondAccount)==null) { "Deleted account silently fell back to A" }
            val validAccountData=accountPrefs.getString("accounts-v2",null)
            accountPrefs.edit().putString("accounts-v2","{").commit()
            check(Accounts.list(targetContext).isEmpty()&&Accounts.problem(targetContext)!=null) { "Unreadable account data was not reported" }
            var preventedOverwrite=false
            try { Accounts.save(targetContext,"17514483308299379","new@example.com","new-password") } catch(_:Exception) { preventedOverwrite=true }
            check(preventedOverwrite&&accountPrefs.getString("accounts-v2",null)=="{") { "Unreadable records were overwritten" }
            accountPrefs.edit().putString("accounts-v2",validAccountData).commit()
            Accounts.clear(targetContext);check(Accounts.load(targetContext)==null) { "Test account was not cleared" }
            check(HeroNames.slug("Điêu Thuyền")=="dieu-thuyen")
            // The skills are one step read at run time: at least the four icons' two tabs, more for five icons or other forms.
            val m=Macro(BuiltInMacros.create(""));check(m.photoTotal==10&&m.photosVary&&m.photoSteps.size==2)
            check(Macro(BuiltInMacros.create("",1,false,fast=true,detailOnly=true)).photoTotal==6) { "Chỉ Chi tiết should keep overview, attributes and 4 details" }
            val direct=Macro(BuiltInMacros.create("",1,false,fast=true,detailOnly=true,directSkills=true))
            check(direct.photoTotal==6&&(0 until direct.steps.length()).map { direct.steps.getJSONObject(it) }.single { it.getString("type")=="skills" }.getBoolean("directSkills")) { "Chuyển thẳng chiêu should reach the skills step" }
            check(Macro(BuiltInMacros.create("",11,true)).steps.toString().contains("\"pick\""))
            // Heroes by name: a "find" step a hero, which skips to the next hero's when the list has no such hero.
            val named=Macro(BuiltInMacros.named(listOf("Bolt Baron"," violet ","Bolt Baron"),listOf(7,-1,7)))
            val finds=(0 until named.steps.length()).filter { named.steps.getJSONObject(it).getString("type")=="find" }
            check(finds.size==2&&named.steps.getJSONObject(finds[0]).getInt("skipTo")==finds[1]&&named.steps.getJSONObject(finds[1]).getInt("skipTo")==named.steps.length()
                &&named.steps.getJSONObject(finds[0]).getInt("hint")==7&&named.steps.getJSONObject(finds[1]).getString("name")=="violet"&&BuiltInMacros.heroNames(named.json)==listOf("Bolt Baron","violet")) { "Heroes by name: ${named.raw}" }
            // Thống kê tướng takes the chosen tables only; one saved before the choice existed takes all six.
            check(BuiltInMacros.rankCombos(Macro(BuiltInMacros.ranking(combos=listOf("ty-le-dau-5v5","chi-so-dau-hang-tinh-anh"))).json).map { it.slug }==listOf("ty-le-dau-5v5","chi-so-dau-hang-tinh-anh"))
            check(BuiltInMacros.rankCombos(JSONObject(BuiltInMacros.ranking()).apply { remove("rankCombos") }).size==6)
            check(BuiltInMacros.secondsPerHero(true,false,false,false) in 30.0..45.0&&BuiltInMacros.secondsPerHero(true,true,true,true) in 15.0..25.0) { "Time estimate per hero is off" }
            val run=File(targetContext.filesDir,"captures/smoke-demo").apply { mkdirs() }
            File(run,"run.json").writeText(JSONObject().put("collectionMode","all").put("label","Buổi thu thập mẫu").put("state","completed").put("updatedAt",System.currentTimeMillis()).toString())
            val hero=File(run,"elandorr").apply { mkdirs() }
            File(hero,"hero.json").writeText(JSONObject().put("name","Eland’orr").put("nameSource","ocr").toString())
            for(name in listOf("0004-hero-overview","0008-hero-attributes","0012-hero-skill-1-summary")) File(hero,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""))
            // A ZIP job left to the background: the foreground service shows its progress, Hủy cancels it, and with no
            // screen in front its result comes as a notification.
            val notifications=targetContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            fun waitFor(what:String,test:()->Boolean) { val until=SystemClock.uptimeMillis()+5000;while(!test()) { check(SystemClock.uptimeMillis()<until) { what };SystemClock.sleep(100) } }
            FileJob.start(targetContext,"Đang lưu ZIP","Lưu ZIP")
            FileJob.report(50,100,"File 1/2","run/hero/0001-hero-001-overview.png")
            waitFor("No progress notification") { notifications.activeNotifications.any { it.id==FileJob.NOTIFICATION&&it.notification.extras.getInt(android.app.Notification.EXTRA_PROGRESS)==500 } }
            var jobService:FileJobService?=null;waitFor("The ZIP job did not enter the foreground") { runOnMainSync { jobService=FileJobService.current };jobService!=null }
            targetContext.startService(Intent(targetContext,FileJobService::class.java).setAction(FileJobService.CANCEL))
            waitFor("Hủy in the notification did not cancel the job") { FileJob.cancelled }
            FileJob.finish("Đã hủy lưu ZIP; file ZIP dở đã được xóa.")
            waitFor("No result notification") { notifications.activeNotifications.let { all -> all.none { it.id==FileJob.NOTIFICATION }&&all.any { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE)=="Lưu ZIP" } } }
            runOnMainSync { jobService=FileJobService.current };check(jobService==null&&!CollectorService.libraryBusy) { "The ZIP job did not stop" }
            notifications.cancelAll()
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val dest=File(targetContext.filesDir,"smoke").apply { mkdirs() }
            fun snap(n:String) { waitForIdleSync();SystemClock.sleep(700);val b=automation.takeScreenshot()?:error("No screenshot");File(dest,"$n.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle() }
            snap("01-history")
            // Viewer: open a photo from history, step to the next one, then return to the top level.
            fun tapText(text:String) { waitForIdleSync();SystemClock.sleep(500)
                var node=automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()?:error("Missing $text")
                while(!node.isClickable) node=node.parent?:error("$text is not clickable")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $text" } }
            tapText("Buổi thu thập mẫu");tapText("Eland’orr");tapText("Tổng quan");snap("01b-viewer")
            tapText("Ảnh sau");waitForIdleSync();SystemClock.sleep(400)
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("2/3")?.isNotEmpty()==true) { "Viewer did not move to the next photo" }
            snap("01c-viewer-next")
            repeat(3) { automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);waitForIdleSync();SystemClock.sleep(500) }
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Buổi thu thập mẫu")?.isNotEmpty()==true) { "Back did not return to history" }
            shell("settings put secure enabled_accessibility_services null")
            SystemClock.sleep(1200)
            shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
            shell("settings put secure accessibility_enabled 1")
            var service:CollectorService?=null
            for(attempt in 0..2) {
                for(i in 0..40) { runOnMainSync { service=CollectorService.current };if(service!=null) break;SystemClock.sleep(250) }
                if(service!=null) break
                shell("settings put secure enabled_accessibility_services null")
                SystemClock.sleep(1500)
                shell("settings put secure enabled_accessibility_services vn.ndang.aovcollector/vn.ndang.aovcollector.CollectorService")
                shell("settings put secure accessibility_enabled 1")
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys accessibility")).use { File(dest,"accessibility.txt").writeBytes(it.readBytes()) }
            check(service!=null) { "Accessibility service not connected" }
            runOnMainSync {
                check(service!!.selectMacro(BuiltInMacros.chess(ChessWiki.Options(
                    setOf("tat-ca-quan-co"),openGame=false,heroDetails=true))))
                service!!.showPanel()
                fun bar(view:View):android.widget.ProgressBar? {
                    if(view is android.widget.ProgressBar) return view
                    if(view is android.view.ViewGroup) for(i in 0 until view.childCount)
                        bar(view.getChildAt(i))?.let { return it }
                    return null
                }
                val progress=bar(service!!.panelView?:error("Missing chess panel"))
                    ?:error("Missing chess progress bar")
                check(progress.visibility==View.GONE) { "Chess showed a percentage before knowing its total" }
            }
            // Choose B for this run, then change the global default to A: resume must stay on B.
            run {
                fun window(title:String)=automation.windows.mapNotNull { it.root }
                    .firstOrNull { it.findAccessibilityNodeInfosByText(title).isNotEmpty() }
                fun press(title:String,text:String) {
                    waitForIdleSync()
                    val node=window(title)?.findAccessibilityNodeInfosByText(text)?.firstOrNull { it.text?.toString()==text }
                        ?:error("Missing $text in $title")
                    check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)||node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
                    waitForIdleSync();SystemClock.sleep(350)
                }
                runOnMainSync {
                    check(service!!.selectMacro(BuiltInMacros.chess(ChessWiki.Options(
                        setOf("tat-ca-quan-co"),openGame=true,checkAccount=true,traceEntry=false))))
                    service!!.start()
                }
                check(window("Chưa có tài khoản đã lưu")!=null) { "Missing account silently disabled UID verification" }
                press("Chưa có tài khoản đã lưu","Hủy")
                val a=Accounts.save(targetContext,"17514483308299377","a@example.com","password-a",label="Tài khoản A")
                val b=Accounts.save(targetContext,"17514483308299378","b@example.com","password-b",label="Tài khoản B")
                Accounts.select(targetContext,a)
                runOnMainSync { service!!.start() }
                val title="Chọn tài khoản cho lượt chạy"
                check(window(title)!=null) { "Run did not offer the account picker" }
                press(title,Accounts.list(targetContext).first { it.id==b }.caption)
                snap("01d-account-picker")
                press(title,"Chạy")
                val runPrefs=targetContext.getSharedPreferences("collector",0)
                waitFor("Chosen account was not pinned to the run") { runPrefs.getString("accountId",null)==b }
                waitFor("Missing game did not end the test entry step") { var idle=false;runOnMainSync { idle=!service!!.isWorking };idle }
                check(runPrefs.getString("accountUid",null)=="17514483308299378") { "Run pinned the wrong UID" }
                Accounts.select(targetContext,a)
                runOnMainSync { service!!.start() }
                waitFor("Resume entry did not settle") { var idle=false;runOnMainSync { idle=!service!!.isWorking };idle }
                check(window(title)==null&&runPrefs.getString("accountId",null)==b) { "Resume switched to the new default account" }
                Accounts.remove(targetContext,b)
                runOnMainSync { service!!.start() }
                // The selected account has gone: no fallback to A and no game interaction.
                var idle=false;runOnMainSync { idle=!service!!.isWorking }
                check(idle&&runPrefs.getString("accountId",null)==b&&Accounts.load(targetContext,a)!=null) { "Deleted run account fell back to A" }
                Accounts.clear(targetContext)
                runOnMainSync { service!!.reset() }
            }
            // A built-in macro saved by an older version is regenerated when a new run is created.
            MacroStore.current(targetContext).writeText(JSONObject(BuiltInMacros.create("Danh sách tướng",2,true)).apply { remove("builtinVersion") }.toString())
            runOnMainSync { service!!.reset() }
            val upgraded=Macro(MacroStore.current(targetContext).readText())
            check(upgraded.json.optInt("builtinVersion")==BuiltInMacros.VERSION&&upgraded.json.optInt("heroCount")==2) { "Old built-in macro was not upgraded" }
            MacroStore.current(targetContext).writeText(BuiltInMacros.create(""));runOnMainSync { service!!.reset() }
            // Legacy users may have saved icon-only mode. It must no longer hide labels.
            targetContext.getSharedPreferences("ui",0).edit().putBoolean("panelShowLabels",false).commit()
            runOnMainSync { service!!.showPanel() }
            check(!targetContext.getSharedPreferences("ui",0).contains("panelShowLabels")) {
                "Obsolete icon-only preference was not removed"
            }
            // Exercise the actual chooser button, repeatedly, then after rotation.
            fun chooserClick() {
                waitForIdleSync();SystemClock.sleep(500)
                val node=automation.windows.mapNotNull { it.root }.flatMap {
                    it.findAccessibilityNodeInfosByText("Lấy thông tin tướng")+
                        it.findAccessibilityNodeInfosByText("Đóng danh sách")
                }.firstOrNull { it.contentDescription?.toString()=="Chọn macro, có tìm kiếm" }
                    ?:error("Missing chooser button")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(250)
            }
            repeat(3) { chooserClick();chooserClick() }
            runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE };SystemClock.sleep(1200)
            chooserClick();snap("02-task-picker")
            fun nodes()=automation.windows.mapNotNull { it.root }
            fun click(text:String) { val node=nodes().flatMap { it.findAccessibilityNodeInfosByText(text) }.firstOrNull { it.text?.toString()?.equals(text,true)==true }?:error("Missing $text");check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)||node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);waitForIdleSync();SystemClock.sleep(500) }
            // Search the actual floating dropdown before selecting.
            fun findEdit(n:AccessibilityNodeInfo):AccessibilityNodeInfo? { if(n.className?.toString()=="android.widget.EditText") return n;for(i in 0 until n.childCount) { val child=n.getChild(i)?:continue;val e=findEdit(child);if(e!=null) return e };return null }
            val search=nodes().firstNotNullOfOrNull { findEdit(it) }?:error("Missing macro search")
            check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"lay thong tin") }))
            waitForIdleSync();SystemClock.sleep(400);snap("02b-search")
            // Heroes by name: one kept from an earlier run, ticked from the search, and a new name typed and added.
            File(targetContext.filesDir,HeroIndex.FILE).writeText("3\tFlowborn\n7\tBolt Baron\n-1\tViolet\n")
            targetContext.getSharedPreferences("ui",0).edit().putInt("heroMode",0).remove("pickNames").commit()
            click("Lấy thông tin tướng");SystemClock.sleep(800);snap("03-hero-form")
            click("Theo tên tướng")
            fun edits(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo> =ArrayList()):List<AccessibilityNodeInfo> { if(n.className?.toString()=="android.widget.EditText") out+=n;for(i in 0 until n.childCount) n.getChild(i)?.let { edits(it,out) };return out }
            fun nameField()=nodes().flatMap { edits(it) }.firstOrNull { it.hintText?.toString()?.startsWith("Gõ tên tướng")==true }?:error("No hero name search")
            fun type(text:String) { check(nameField().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) }));waitForIdleSync();SystemClock.sleep(500) }
            type("bolt");click("Bolt Baron")
            type("Qi");click("Thêm tướng: Qi");snap("03b-hero-names")
            click("Chọn tác vụ")
            val picked=Macro(MacroStore.current(targetContext).readText())
            val firstFind=(0 until picked.steps.length()).map { picked.steps.getJSONObject(it) }.first { it.getString("type")=="find" }
            check(picked.json.optString("collectionMode")=="pick"&&BuiltInMacros.heroNames(picked.json)==listOf("Bolt Baron","Qi")&&firstFind.getInt("hint")==7) { "Heroes by name chose ${picked.raw.take(400)}" }
            // The hero page open now, with its name typed.
            chooserClick();check(nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"lay thong tin") }))
            waitForIdleSync();SystemClock.sleep(400)
            click("Lấy thông tin tướng");SystemClock.sleep(800);click("Tướng đang mở")
            val single=nodes().flatMap { edits(it) }.firstOrNull { it.hintText?.toString()=="Để trống để tự đọc tên" }?:error("No name input")
            check(single.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Violet") }))
            click("Chọn tác vụ")
            check(Macro(MacroStore.current(targetContext).readText()).json.let { it.optString("heroName")=="Violet"&&it.optString("collectionMode")=="single" })
            snap("04-panel")
            // The chosen macro is visible and each action shows its label with its icon.
            fun selectedMacroVisible()=nodes().flatMap { it.findAccessibilityNodeInfosByText("Lấy thông tin tướng") }
                .any { it.text?.toString()=="Lấy thông tin tướng" && it.contentDescription?.toString()=="Chọn macro, có tìm kiếm" }
            check(selectedMacroVisible()) { "Selected macro is not shown in the compact picker" }
            for(caption in listOf("Chụp","Về app","Đóng","Dừng")) {
                check(nodes().flatMap { it.findAccessibilityNodeInfosByText(caption) }
                    .any { it.text?.toString()==caption && it.contentDescription?.toString()==caption }) {
                    "Icon and readable label missing: $caption"
                }
            }
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Ẩn chữ") }
                .none { it.text?.toString()=="Ẩn chữ" }) { "Obsolete hide-label option is still visible" }
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Hiện chữ") }
                .none { it.text?.toString()=="Hiện chữ" }) { "Obsolete show-label option is still visible" }
            snap("04a-always-labeled-controls")
            // No run yet: Dừng has nothing to end.
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Dừng") }.firstOrNull { it.text?.toString()=="Dừng" }?.isEnabled==false) { "Dừng is enabled before any run" }
            // The actual completed-run action renders the restart vector and resets without starting a run.
            val chosen=Macro(MacroStore.current(targetContext).readText())
            targetContext.getSharedPreferences("collector",0).edit().putString("hash",chosen.hash)
                .putInt("step",chosen.steps.length()).commit()
            runOnMainSync {
                service!!.showPanel()
                fun restart(view:View):android.widget.Button? {
                    if(view is android.widget.Button&&view.text.toString()=="Tạo lượt mới") return view
                    if(view is android.view.ViewGroup) for(i in 0 until view.childCount)
                        restart(view.getChildAt(i))?.let { return it }
                    return null
                }
                val button=restart(service!!.panelView?:error("Missing panel"))?:error("Missing new-run action")
                val icon=button.compoundDrawables[0]?:error("Missing restart icon")
                check(icon.bounds.width()>0&&icon.bounds.height()>0) { "Restart icon has no size" }
                val preview=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
                val original=Rect(icon.bounds)
                val canvas=Canvas(preview);canvas.drawColor(Color.rgb(96,75,209))
                icon.setBounds(12,12,84,84);icon.draw(canvas);icon.bounds=original
                File(dest,"reset-icon.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG,100,it) }
                preview.recycle()
            }
            snap("04b-create-new-run")
            click("Tạo lượt mới")
            check(targetContext.getSharedPreferences("collector",0).getInt("step",0)==0) { "New-run action did not reset" }
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Chạy") }.any { it.text?.toString()=="Chạy" }) {
                "New-run action did not return to the ready state"
            }
            runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE };SystemClock.sleep(1500);snap("05-landscape")
            // v0.3.1 regression: a macro tap under the floating panel pressed its hidden buttons
            // (Đóng removed the panel mid-run) instead of reaching the game.
            fun closeButton()=nodes().flatMap { it.findAccessibilityNodeInfosByText("Đóng") }.firstOrNull { it.text?.toString()=="Đóng" }
            val box=Rect().also { (closeButton()?:error("Missing panel close button")).getBoundsInScreen(it) }
            fun tapAt()=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(box.exactCenterX(),box.exactCenterY()) },0,CollectorService.TAP_MS)).build()
            // Old behaviour, recorded only: hide the panel and inject in the same frame.
            runOnMainSync { service!!.panelView?.visibility=View.INVISIBLE;service!!.dispatchGesture(tapAt(),null,null) }
            SystemClock.sleep(1500);var legacyClosed=false
            runOnMainSync { legacyClosed=service!!.panelView==null;if(legacyClosed) service!!.showPanel() else service!!.panelView?.visibility=View.VISIBLE }
            waitForIdleSync();SystemClock.sleep(800)
            val finished=java.util.concurrent.CountDownLatch(1);val delivered=java.util.concurrent.atomic.AtomicBoolean(false)
            runOnMainSync { service!!.touchGame(tapAt(),true) { delivered.set(it);finished.countDown() } }
            check(finished.await(8,java.util.concurrent.TimeUnit.SECONDS)) { "Macro tap never finished" }
            waitForIdleSync();SystemClock.sleep(600)
            var panelKept=false;var swallowed=0
            runOnMainSync { panelKept=service!!.panelView?.visibility==View.VISIBLE;swallowed=service!!.swallowedTaps }
            snap("06-after-macro-tap")
            check(delivered.get()) { "Macro tap was not delivered past the panel (swallowed=$swallowed)" }
            check(panelKept&&closeButton()!=null) { "Macro tap closed or hid the floating panel" }
            // Before a run opens the game, a game left running is ended on its App info page: Buộc dừng, then OK.
            // Checked against the emulator's real Settings, on one of its own apps (English labels there).
            fun output(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { String(it.readBytes()) }.trim()
            val installed=output("pm list packages")
            val (app,label)=listOf("com.android.deskclock" to "Clock","com.android.calculator2" to "Calculator","com.android.contacts" to "Contacts","com.android.camera2" to "Camera")
                .firstOrNull { installed.contains("package:${it.first}\n")||installed.endsWith("package:${it.first}") }?:error("No emulator app to close: $installed")
            shell("monkey -p $app -c android.intent.category.LAUNCHER 1");SystemClock.sleep(3000)
            check(output("pidof $app").isNotEmpty()) { "$app did not start" }
            fun close():GameCloser.Result? { val latch=java.util.concurrent.CountDownLatch(1);var r:GameCloser.Result?=null
                runOnMainSync { service!!.closeApp(app,{ true },label) { r=it;latch.countDown() } };latch.await(30,java.util.concurrent.TimeUnit.SECONDS);return r }
            val closed=close();snap("07-force-stop")
            check(closed==GameCloser.Result.CLOSED&&output("pidof $app").isEmpty()) { "Force stop of $app: $closed, pid ${output("pidof $app")}" }
            val again=close()
            check(again==GameCloser.Result.NOT_RUNNING) { "A stopped $app read as $again" }
            // Giao diện Tối: the history and a task form over the game in the dark look.
            targetContext.getSharedPreferences("ui",0).edit().putString("theme","dark").commit()
            startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            fun luma():Double { waitForIdleSync();SystemClock.sleep(700);val shot=automation.takeScreenshot()?:error("No screenshot");val b=shot.copy(Bitmap.Config.ARGB_8888,false);shot.recycle()
                var sum=0L;var n=0;for(y in 0 until b.height step 12) for(x in 0 until b.width step 12) { val p=b.getPixel(x,y);sum+=(299*Color.red(p)+587*Color.green(p)+114*Color.blue(p))/1000;n++ };b.recycle();return sum.toDouble()/n }
            check(Ui.colors===Ui.DARK) { "Giao diện Tối was not taken" }
            val darkHistory=luma();snap("08-dark-history")
            check(darkHistory<90) { "The dark history is not dark: mean brightness $darkHistory" }
            runOnMainSync { service!!.showPanel() };chooserClick()
            check(nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"lay thong tin") }))
            waitForIdleSync();SystemClock.sleep(400);snap("09-dark-picker")
            click("Lấy thông tin tướng");SystemClock.sleep(800)
            val darkForm=luma();snap("10-dark-form")
            check(darkForm<90) { "The dark task form is not dark: mean brightness $darkForm" }
            click("Hủy")
            // No saved accounts: UID verification remains selectable and turns on game entry.
            targetContext.getSharedPreferences("ui",0).edit().putBoolean("chessOpenGame",false)
                .putBoolean("chessCheckAccount",false).commit()
            chooserClick()
            check(nodes().firstNotNullOfOrNull { findEdit(it) }!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"co lien quan") }))
            waitForIdleSync();SystemClock.sleep(400)
            click("Cờ Liên Quân · Bách khoa")
            val verifyText="Kiểm tra UID · chọn tài khoản khi bấm Chạy"
            val verify=nodes().flatMap { it.findAccessibilityNodeInfosByText(verifyText) }
                .firstOrNull { it.text?.toString()==verifyText }?:error("Missing account verification option")
            check(verify.isEnabled) { "No saved account still disables verification without a setup path" }
            verify.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            waitForIdleSync();SystemClock.sleep(400)
            click(verifyText)
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Tự mở game tới sảnh trước khi vào Cờ Liên Quân") }
                .any { it.isChecked }) { "Enabling account verification did not enable game entry" }
            snap("11-account-setup-enabled")
            click("Hủy")
            click("Ảnh từ Pictures · Chọn thư mục để chép vào app ›")
            for(label in listOf("Chọn / đổi thư mục Pictures","Đồng bộ các ảnh mới sau lượt thu thập",
                "Mở ảnh gốc đã nhập trong Lịch sử")) {
                check(nodes().flatMap { it.findAccessibilityNodeInfosByText(label) }
                    .any { it.text?.toString()==label&&it.isVisibleToUser&&it.isEnabled }) {
                    "Pictures actions are hidden behind the dialog message: $label"
                }
            }
            snap("12-pictures-actions")
            click("Mở ảnh gốc đã nhập trong Lịch sử")
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Chưa có ảnh gốc nào được sao chép.") }.isNotEmpty())
            click("Đã hiểu")
            click("Sau khi hoàn tất · Drive → dot của bạn ›")
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Tự lưu kết quả lên Drive sau khi hoàn tất lượt") }.isNotEmpty())
            check(nodes().flatMap { it.findAccessibilityNodeInfosByText("Drive tải xong → mở ChatGPT → dot của bạn → gửi câu lệnh") }.isNotEmpty())
            snap("13-after-run-settings")
            click("Lưu")
            targetContext.getSharedPreferences("ui",0).edit().remove("theme").commit()
            result.putString("stream","SMOKE_OK: history, viewer, macro upgrade, OCR, task selection, overlay, rotation, tap-through, force stop and dark look passed (legacyTapClosedPanel=$legacyClosed, swallowedRetries=$swallowed)\n")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) { result.putString("stream","SMOKE_FAILED: ${t.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result) }
    }
}

