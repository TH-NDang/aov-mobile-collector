package vn.ndang.aovcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.random.Random

class ZipFilesTest {
    private val root=Files.createTempDirectory("captures").toFile()
    private val random=Random(1)
    private fun file(path:String,bytes:ByteArray)=File(root,path).apply { parentFile!!.mkdirs();writeBytes(bytes) }
    // Random bytes do not compress, like PNG data.
    private fun image(path:String,size:Int)=file(path,random.nextBytes(size))
    // Highly compressible, to tell stored entries from deflated ones.
    private val flat=file("run-1/0002-page-01-list.png",ByteArray(30_000))
    private val run=file("run-1/run.json","{\"state\":\"completed\"}".repeat(2000).toByteArray())
    private val heroes=listOf("zata","aleister","Butterfly","elsu","flowborn-2","flowborn","dirak").flatMap { hero ->
        (1..3).map { image("run-1/$hero/00${it}0-hero-${hero.length}-shot-$it.png",20_000) } }
    private val other=image("run-0/airi/0010-hero-001-overview.png",20_000)
    private val files=(heroes+flat+run+other).shuffled(random)

    /** The part ZIPs inside [zip], in order, each saved to a file of its own. */
    private fun parts(zip:File):List<Pair<String,File>> = ZipFile(zip).use { outer ->
        outer.entries().toList().map { entry -> entry.name to File(root,"read-"+entry.name).apply { writeBytes(outer.getInputStream(entry).readBytes()) } } }

    @Test fun splitsIntoPartsUnderTheLimitInAlphabeticalOrder() {
        val limit=100_000L
        val reports=ArrayList<Triple<Long,Int,Int>>()
        val zipFile=File(root,"out.zip")
        val written=zipFile.outputStream().use { ZipFiles.export(files,root,it,{ false },limit) { done,all,number,part,_ -> assertEquals(files.sumOf { f -> f.length() },all);reports+=Triple(done,number,part) } }
        val parts=parts(zipFile)
        assertEquals(written.map { it.name },parts.map { it.first })
        assertTrue("several parts",parts.size>=3)
        val order=ArrayList<String>()
        for((i,part) in parts.withIndex()) {
            assertTrue(part.first,part.first.startsWith("%02d_".format(i+1))&&part.first.endsWith(".zip"))
            assertTrue("${part.first} is ${part.second.length()} bytes",part.second.length()<=limit)
            assertEquals(written[i].bytes,part.second.length())
            ZipFile(part.second).use { zip -> for(entry in zip.entries()) {
                val source=File(root,entry.name)
                assertTrue(entry.name,zip.getInputStream(entry).readBytes().contentEquals(source.readBytes()))
                order+=entry.name
            } }
        }
        // Every file once; a run's own files first, then its hero folders by name, each folder whole in one part.
        assertEquals(files.size,order.size)
        assertEquals(listOf("run-0/airi","run-1","run-1/aleister","run-1/Butterfly","run-1/dirak","run-1/elsu","run-1/flowborn","run-1/flowborn-2","run-1/zata"),
            order.map { it.substringBeforeLast('/') }.distinct())
        assertEquals(listOf("run-1/0002-page-01-list.png","run-1/run.json"),order.filter { it.count { c -> c=='/' }==1 })
        val folderParts=written.flatMap { part -> part.files.map { it.parentFile!!.path to part.name } }.groupBy({ it.first },{ it.second })
        assertTrue("a folder is split",folderParts.values.all { it.distinct().size==1 })
        // Named by hero folders: the first part holds airi and the files of run-1 itself.
        assertEquals(listOf("01_airi.zip","02_aleister.zip","03_Butterfly.zip"),written.take(3).map { it.name })
        // Progress reaches the total and only grows.
        assertEquals(files.sumOf { it.length() },reports.last().first)
        assertTrue(reports.zipWithNext().all { (a,b) -> b.first>a.first&&b.second>=a.second&&b.third>=a.third })
        assertEquals(files.size to parts.size,reports.last().second to reports.last().third)
        // PNG is stored as it is; other files are still compressed.
        ZipFile(parts[written.indexOfFirst { run in it.files }].second).use { zip ->
            assertTrue(zip.getEntry("run-1/0002-page-01-list.png").compressedSize>=flat.length())
            assertTrue(zip.getEntry("run-1/run.json").compressedSize<run.length()/10)
        }
    }

    @Test fun putsEverythingInOnePartWhenItFits() {
        val written=ZipFiles.plan(files,root)
        assertEquals(1,written.size)
        assertEquals("01_airi_zata.zip",written[0].name)
    }

    @Test fun splitsAFolderLargerThanAPartByFile() {
        val big=(1..5).map { image("run-2/arum/000$it-hero-007-shot.png",30_000) }
        val parts=ZipFiles.plan(big,root,70_000)
        assertEquals(listOf(2,2,1),parts.map { it.files.size })
        assertEquals(big,parts.flatMap { it.files })
    }

    @Test(expected=IllegalArgumentException::class) fun refusesAFileLargerThanAPart() {
        ZipFiles.plan(listOf(image("run-2/arum/0001-hero-007-shot.png",30_000)),root,20_000)
    }

    @Test(expected=ZipFiles.Cancelled::class) fun stopsWhenCancelled() {
        var reports=0
        ZipFiles.export(files,root,ByteArrayOutputStream(),{ reports>0 }) { _,_,_,_,_ -> reports++ }
    }

    @Test(expected=IllegalArgumentException::class) fun refusesFilesOutsideTheCaptures() {
        val outside=File.createTempFile("outside",".png")
        ZipFiles.export(listOf(outside),root,ByteArrayOutputStream(),{ false }) { _,_,_,_,_ -> }
    }
}
