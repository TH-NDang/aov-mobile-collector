package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ImageLoadCacheTest {
    private fun withImage(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("image-cache-test").toFile()
        try {
            test(File(directory, "photo.png").apply { writeText("original") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun warmNavigationBindsWithoutAnotherDecode() = withImage { file ->
        val cache = ImageLoadCache<String>(24) { it.length }
        val request = cache.request(file, 74, 60, true)!!
        var decodes = 0
        assertNull(cache.cached(request))
        assertEquals(
            "bitmap",
            cache.load(request, cache.generation) {
                decodes++
                "bitmap"
            }
        )
        cache.cancelRequests()
        assertEquals("bitmap", cache.cached(cache.request(file, 74, 60, true)!!))
        assertEquals(
            "bitmap",
            cache.load(request, cache.generation) {
                decodes++
                "unneeded"
            }
        )
        assertEquals(1, decodes)
    }

    @Test fun changedAndDeletedFilesCannotReuseOldPixels() = withImage { file ->
        val cache = ImageLoadCache<String>(24) { it.length }
        val old = cache.request(file, 74, 60, true)!!
        cache.load(old, cache.generation) { "old" }
        file.writeText("replacement-image")
        assertNull(cache.cached(old))
        val replacement = cache.request(file, 74, 60, true)!!
        assertNull(cache.cached(replacement))
        cache.load(replacement, cache.generation) { "new" }
        file.delete()
        assertNull(cache.request(file, 74, 60, true))
        assertNull(cache.cached(replacement))
    }

    @Test fun modifiedTimestampInvalidatesSameLengthImage() = withImage { file ->
        val cache = ImageLoadCache<String>(24) { it.length }
        val old = cache.request(file, 74, 60, true)!!
        cache.load(old, cache.generation) { "old" }
        assertTrue(file.setLastModified(old.modified + 2000))
        assertNull(cache.cached(old))
        assertNull(cache.cached(cache.request(file, 74, 60, true)!!))
    }

    @Test fun leastRecentlyUsedEvictionRespectsByteBudgetAndDisplaySize() = withImage { file ->
        val cache = ImageLoadCache<String>(6) { it.length }
        val first = cache.request(file, 74, 60, true)!!
        val second = cache.request(file, 112, 66, true)!!
        val full = cache.request(file, 1080, 2400, false)!!
        cache.load(first, cache.generation) { "aaa" }
        cache.load(second, cache.generation) { "bbb" }
        assertEquals("aaa", cache.cached(first))
        cache.load(full, cache.generation) { "ccc" }
        assertNull(cache.cached(second))
        assertEquals("aaa", cache.cached(first))
        assertEquals("ccc", cache.cached(full))
        cache.load(second, cache.generation) { "oversized" }
        assertNull(cache.cached(second))
    }

    @Test fun obsoleteQueuedAndInFlightRequestsCannotDecodeOrPopulateCache() = withImage { file ->
        val cache = ImageLoadCache<String>(24) { it.length }
        val request = cache.request(file, 74, 60, true)!!
        val oldGeneration = cache.generation
        cache.cancelRequests()
        assertNull(
            cache.load(request, oldGeneration) {
                fail("Stale request decoded")
                "bad"
            }
        )
        assertNull(
            cache.load(request, cache.generation) {
                cache.cancelRequests()
                "stale"
            }
        )
        assertNull(cache.cached(request))
        assertNull(
            cache.load(request, cache.generation) {
                file.delete()
                "deleted"
            }
        )
    }

    @Test fun replacementDuringDecodeCannotPopulateCache() = withImage { file ->
        val cache = ImageLoadCache<String>(24) { it.length }
        val request = cache.request(file, 74, 60, true)!!
        assertNull(
            cache.load(request, cache.generation) {
                file.writeText("changed while decoding")
                "old pixels"
            }
        )
        assertNull(cache.cached(request))
        val replacement = cache.request(file, 74, 60, true)!!
        assertNull(cache.cached(replacement))
        assertEquals("new pixels", cache.load(replacement, cache.generation) { "new pixels" })
    }

    @Test fun destroyReleasesCacheAndInvalidatesPendingWork() = withImage { file ->
        val cache = ImageLoadCache<String>(24) { it.length }
        val request = cache.request(file, 74, 60, true)!!
        cache.load(request, cache.generation) { "bitmap" }
        val oldGeneration = cache.generation
        cache.clear()
        assertNull(cache.cached(request))
        assertNull(
            cache.load(request, oldGeneration) {
                fail("Destroyed request decoded")
                "bad"
            }
        )
    }
}
