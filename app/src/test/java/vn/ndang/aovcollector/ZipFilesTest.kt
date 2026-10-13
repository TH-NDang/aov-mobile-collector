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
    private val image=File(root,"run-1/elandorr/0008-hero-001-overview.png").apply { parentFile!!.mkdirs(); writeBytes(Random(1).nextBytes(700_000)) }
    // Highly compressible, to tell stored entries from deflated ones.
    private val flat=File(root,"run-1/0002-page-01-list.png").apply { writeBytes(ByteArray(300_000)) }
    private val run=File(root,"run-1/run.json").apply { writeText("{\"state\":\"completed\"}".repeat(2000)) }
    private val files=listOf(image,flat,run)

    @Test fun writesEveryFileAndReportsProgressUpToTheTotal() {
        val reports=ArrayList<Pair<Long,Int>>()
        val zipFile=File(root,"out.zip")
        val total=zipFile.outputStream().use { ZipFiles.export(files,root,it,{ false }) { done,all,number,_ -> assertEquals(files.sumOf { f -> f.length() },all); reports+=done to number } }
        assertEquals(files.sumOf { it.length() },total)
        assertEquals(total,reports.last().first)
        assertTrue("progress only grows",reports.zipWithNext().all { (a,b) -> b.first>a.first&&b.second>=a.second })
        assertEquals(3,reports.last().second)
        ZipFile(zipFile).use { zip ->
            for(f in files) assertTrue(f.name,zip.getInputStream(zip.getEntry(f.relativeTo(root).invariantSeparatorsPath)).readBytes().contentEquals(f.readBytes()))
            // PNG is stored as it is; other files are still compressed.
            assertTrue(zip.getEntry("run-1/0002-page-01-list.png").compressedSize>=flat.length())
            assertTrue(zip.getEntry("run-1/run.json").compressedSize<run.length()/10)
        }
    }

    @Test(expected=ZipFiles.Cancelled::class) fun stopsWhenCancelled() {
        var reports=0
        ZipFiles.export(files,root,ByteArrayOutputStream(),{ reports>0 }) { _,_,_,_ -> reports++ }
    }

    @Test(expected=IllegalArgumentException::class) fun refusesFilesOutsideTheCaptures() {
        val outside=File.createTempFile("outside",".png")
        ZipFiles.export(listOf(outside),root,ByteArrayOutputStream(),{ false }) { _,_,_,_ -> }
    }
}
