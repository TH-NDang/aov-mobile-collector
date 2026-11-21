package vn.ndang.aovcollector

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePhotoReviewStoreTest {
    private fun attempt() = JSONObject().put("id", "synthetic-attempt").put("status", "deferred")

    private val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10) + ByteArray(1024) { (it % 251).toByte() }

    private fun withRun(block: (File) -> Unit) {
        val run = Files.createTempDirectory("native-review-test").toFile()
        try {
            block(run)
        } finally {
            run.deleteRecursively()
        }
    }

    private fun stage(run: File, attempt: JSONObject, id: String = "synthetic-source") =
        NativePhotoReviewStore.stage(run, attempt, id, 1234, 128, 128, bytes)

    @Test fun stagesExactBytesAndIntegrityWithoutClaimingVerifiedMapping() = withRun { run ->
        val attempt = attempt()
        stage(run, attempt)
        val entry = attempt.getJSONArray("reviewCandidates").getJSONObject(0)
        val file = File(run, entry.getString("path"))
        assertArrayEquals(bytes, file.readBytes())
        val expected = OriginalPhotoCopy.digest(bytes.inputStream())
        assertEquals(expected.sha256, entry.getString("sha256"))
        assertEquals(expected.bytes, entry.getLong("bytes"))
        assertEquals("synthetic-source", entry.getString("sourceId"))
        assertEquals(1234L, entry.getLong("modified"))
        assertFalse(entry.getBoolean("verifiedMapping"))
        assertEquals("deferred", attempt.getString("status"))
        assertFalse(attempt.has("documentId"))
        assertTrue(entry.getString("path").startsWith("native-review/synthetic-attempt/"))
        assertFalse(run.walkTopDown().any { it.extension == "part" })
    }

    @Test fun repeatedSourceAndBytesAreIdempotentButCorruptedCopyIsRepaired() = withRun { run ->
        val attempt = attempt()
        stage(run, attempt)
        val entry = attempt.getJSONArray("reviewCandidates").getJSONObject(0)
        val file = File(run, entry.getString("path"))
        stage(run, attempt)
        assertEquals(1, attempt.getJSONArray("reviewCandidates").length())
        assertEquals(1, run.walkTopDown().count { it.isFile })
        file.writeBytes(byteArrayOf(1, 2, 3))
        stage(run, attempt)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(1, attempt.getJSONArray("reviewCandidates").length())
    }

    @Test fun rejectsOversizedEmptyAndNonNativeDimensionsWithoutWriting() = withRun { run ->
        val invalid = listOf(31 to 128, 513 to 128, 128 to 31, 128 to 513, 32 to 128, 128 to 32)
        invalid.forEach { (w, h) ->
            val attempt = attempt()
            NativePhotoReviewStore.stage(run, attempt, "source", 1234, w, h, bytes)
            assertTrue(attempt.getBoolean("reviewSkipped"))
            assertFalse(attempt.has("reviewCandidates"))
        }
        listOf(ByteArray(0), ByteArray(2 * 1024 * 1024 + 1)).forEach { data ->
            val attempt = attempt()
            NativePhotoReviewStore.stage(run, attempt, "source", 1234, 128, 128, data)
            assertTrue(attempt.getBoolean("reviewSkipped"))
            assertFalse(attempt.has("reviewCandidates"))
        }
        assertFalse(run.walkTopDown().any { it.isFile })
    }

    @Test fun perAttemptLimitPreservesFirstEightWithoutFalseMapping() = withRun { run ->
        val attempt = attempt()
        repeat(9) { stage(run, attempt, "source-$it") }
        assertEquals(8, attempt.getJSONArray("reviewCandidates").length())
        assertEquals(8, run.walkTopDown().count { it.isFile })
        assertTrue(attempt.getBoolean("reviewSkipped"))
        assertEquals("deferred", attempt.getString("status"))
    }

    @Test fun runByteCapCannotBeExceeded() = withRun { run ->
        val root = File(run, "native-review").apply { mkdirs() }
        RandomAccessFile(File(root, "synthetic-existing"), "rw").use { it.setLength(64L * 1024 * 1024) }
        val attempt = attempt()
        stage(run, attempt)
        assertTrue(attempt.getBoolean("reviewSkipped"))
        assertFalse(attempt.has("reviewCandidates"))
        assertEquals(1, run.walkTopDown().count { it.isFile })
    }

    @Test fun cancellationPropagatesWithoutMetadataOrPartialCopy() = withRun { run ->
        val attempt = attempt()
        assertThrows(IllegalStateException::class.java) {
            NativePhotoReviewStore.stage(run, attempt, "source", 1234, 128, 128, bytes) { false }
        }
        assertFalse(attempt.has("reviewCandidates"))
        assertFalse(run.walkTopDown().any { it.isFile })
    }

    @Test fun cancellationDuringCopyPropagatesAndRemovesPartial() = withRun { run ->
        val attempt = attempt()
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            NativePhotoReviewStore.stage(run, attempt, "source", 1234, 128, 128, bytes) { ++calls < 5 }
        }
        assertTrue(calls >= 5)
        assertFalse(attempt.has("reviewCandidates"))
        assertFalse(run.walkTopDown().any { it.isFile })
    }

    @Test fun directoryFailurePropagatesWithoutInventingStagedCandidate() = withRun { run ->
        File(run, "native-review").writeText("not a directory")
        val attempt = attempt()
        assertThrows(IllegalArgumentException::class.java) { stage(run, attempt) }
        assertFalse(attempt.has("reviewCandidates"))
        assertEquals("deferred", attempt.getString("status"))
    }
}
