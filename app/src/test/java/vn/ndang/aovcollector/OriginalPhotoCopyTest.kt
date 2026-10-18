package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.InputStream

class OriginalPhotoCopyTest {
    @Test fun preservesEveryByteAndHashWithoutDecoding() {
        val file=Files.createTempFile("native-original-",".jpg").toFile()
        try {
            val bytes=ByteArray(137_159) { ((it*37+19)%256).toByte() }
            val result=OriginalPhotoCopy.copy(bytes.inputStream(),file,bytes.size.toLong())
            assertArrayEquals(bytes,file.readBytes())
            assertEquals(OriginalPhotoCopy.digest(bytes.inputStream()),result)
            assertTrue(OriginalPhotoCopy.matches(file,result))
            file.writeBytes(bytes.clone().apply { this[42]=(this[42].toInt() xor 1).toByte() })
            assertFalse(OriginalPhotoCopy.matches(file,result))
        } finally { file.delete() }
    }
    @Test fun truncatedCancelledAndFailedReadsNeverLeaveAcceptableCopies() {
        val file=Files.createTempFile("native-part-",".part").toFile()
        try {
            for(mode in 0..2) {
                file.writeText("old partial")
                val input=if(mode==2) object:InputStream() { override fun read():Int=error("provider failed") }
                    else byteArrayOf(1,2,3).inputStream()
                try {
                    OriginalPhotoCopy.copy(input,file,if(mode==0) 20 else 3) { mode!=1 }
                    fail("Incomplete source was accepted")
                } catch(_:Exception) {}
                assertFalse(file.exists())
            }
        } finally { file.delete() }
    }
    @Test fun zeroProviderSizeIsAllowedButEmptySourceIsNot() {
        val file=Files.createTempFile("native-part-",".part").toFile()
        try {
            assertEquals(3,OriginalPhotoCopy.copy(byteArrayOf(1,2,3).inputStream(),file,0).bytes.toInt())
            try { OriginalPhotoCopy.copy(byteArrayOf().inputStream(),file,0);fail("empty") } catch(_:IllegalArgumentException) {}
            assertFalse(file.exists())
        } finally { file.delete() }
    }
    @Test fun actualImageSignatureWinsOverWrongFilenameAndMime() {
        assertEquals("png",OriginalPhotoCopy.extension(byteArrayOf(137.toByte(),80,78,71,13,10,26,10),"image/jpeg","source.jpg"))
        assertEquals("jpg",OriginalPhotoCopy.extension(byteArrayOf(255.toByte(),216.toByte(),255.toByte()),"image/png","source.png"))
        assertEquals("webp",OriginalPhotoCopy.extension("RIFFxxxxWEBP".toByteArray(),"","source.jpg"))
        assertEquals("png",OriginalPhotoCopy.extension(byteArrayOf(),"image/png","source"))
    }
}
