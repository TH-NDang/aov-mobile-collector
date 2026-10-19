package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RunImageManifestTest {
    private val source = "a".repeat(64)
    private val otherSource = "b".repeat(64)

    private fun withRun(block: (File) -> Unit) {
        val run = Files.createTempDirectory("run-images-").toFile()
        try {
            block(run)
        } finally {
            run.deleteRecursively()
        }
    }

    private fun image(run: File, path: String, content: String = path): File = File(run, path).apply {
        parentFile.mkdirs()
        writeText(content)
    }

    private fun integrity(file: File) = file.inputStream().use { OriginalPhotoCopy.digest(it) }

    private fun manifest(run: File) = File(run, RunImageManifest.FILE)

    private fun document(run: File) = JSONObject(manifest(run).readText())

    private fun images(run: File) = document(run).getJSONArray("images")

    private fun record(
        run: File,
        file: File,
        group: String = "quan-co",
        item: String = "violet-head",
        sourceId: String = source,
        reused: Boolean = false
    ) = RunImageManifest.record(run, file, group, item, sourceId, integrity(file), reused)

    private fun rejects(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        fail("Invalid manifest operation was accepted")
    }

    @Test fun recordsCommittedBytesWithRelativePathAndOpaqueSource() = withRun { run ->
        val file = image(run, "anh-goc/quan-co/anh-quan-co/violet-head.png", "original image bytes")
        val verified = integrity(file)
        RunImageManifest.record(run, file, "quan-co", "violet-head", source, verified)
        val doc = document(run)
        assertEquals(1, doc.getInt("schemaVersion"))
        assertEquals("chess", doc.getString("collectionMode"))
        assertEquals(1, doc.getJSONArray("images").length())
        val entry = doc.getJSONArray("images").getJSONObject(0)
        assertEquals("anh-goc/quan-co/anh-quan-co/violet-head.png", entry.getString("path"))
        assertEquals("violet-head", entry.getString("item"))
        assertEquals("quan-co", entry.getString("group"))
        assertEquals("portrait", entry.getString("type"))
        assertEquals("violet", entry.getString("hero"))
        assertEquals(verified.bytes, entry.getLong("bytes"))
        assertEquals(verified.sha256, entry.getString("sha256"))
        assertEquals(source, entry.getString("sourceId"))
        assertFalse(entry.getBoolean("reused"))
        assertArrayEquals("original image bytes".toByteArray(), file.readBytes())
        assertFalse(File(run, "images.json.part").exists())
        assertFalse(manifest(run).readText().contains(run.absolutePath))
    }

    @Test fun retriesAreIdempotentAndAnUpsertReplacesTheSamePath() = withRun { run ->
        val file = image(run, "anh-goc/quan-co/anh-quan-co/violet-head.png")
        record(run, file)
        val original = manifest(run).readBytes()
        record(run, file)
        assertArrayEquals(original, manifest(run).readBytes())
        file.writeText("replacement native bytes")
        record(run, file, sourceId = otherSource, reused = true)
        assertEquals(1, images(run).length())
        val entry = images(run).getJSONObject(0)
        assertEquals(otherSource, entry.getString("sourceId"))
        assertEquals(integrity(file).sha256, entry.getString("sha256"))
        assertTrue(entry.getBoolean("reused"))
    }

    @Test fun preservesOtherValidImagesAndMapsSkillAndNativeTypes() = withRun { run ->
        val head = image(run, "anh-goc/quan-co/anh-quan-co/violet-head.png")
        val skill = image(run, "anh-goc/quan-co/chieu-quan-co/violet-skill-1.webp")
        val blessing = image(run, "anh-goc/chuc-phuc/thuong/suc-manh.png")
        record(run, head)
        record(run, skill, "chieu-quan-co", "violet-skill-icon-123")
        record(run, blessing, "chuc-phuc-thuong", "Sức mạnh")
        assertEquals(3, images(run).length())
        val skillEntry = images(run).getJSONObject(1)
        assertEquals("skill", skillEntry.getString("type"))
        assertEquals("violet", skillEntry.getString("hero"))
        assertEquals("violet-skill-icon-123", skillEntry.getString("item"))
        val blessingEntry = images(run).getJSONObject(2)
        assertEquals("native-image", blessingEntry.getString("type"))
        assertEquals("Sức mạnh", blessingEntry.getString("item"))
        assertFalse(blessingEntry.has("hero"))
    }

    @Test fun neverGuessesHeroFromFilenameGroupOrAmbiguousItem() = withRun { run ->
        val cases = listOf(
            "quan-co" to "unknown",
            "quan-co" to "-head",
            "quan-co" to "Violet-head",
            "chieu-quan-co" to "violet-skill-",
            "chieu-quan-co" to "-skill-1",
            "chieu-quan-co" to "violet",
            "phe" to "violet-head",
            "nghe" to "violet-skill-1"
        )
        cases.forEachIndexed { index, (group, item) ->
            val file = image(run, "anh-goc/quan-co/anh-quan-co/violet-head-$index.png")
            record(run, file, group, item)
            val entry = images(run).getJSONObject(index)
            assertEquals(item, entry.getString("item"))
            assertFalse("Unexpected hero for $group/$item", entry.has("hero"))
        }
    }

    @Test fun prunesMissingTruncatedAndSameSizeCorruptedImages() = withRun { run ->
        val missing = image(run, "anh-goc/trang-bi/missing.png", "1234")
        val truncated = image(run, "anh-goc/trang-bi/truncated.png", "1234")
        val corrupted = image(run, "anh-goc/trang-bi/corrupted.png", "1234")
        val valid = image(run, "anh-goc/trang-bi/valid.png", "1234")
        listOf(missing, truncated, corrupted, valid).forEach { record(run, it, "trang-bi", it.name) }
        missing.delete()
        truncated.writeText("12")
        corrupted.writeText("4321")
        val added = image(run, "anh-goc/trang-bi/added.png")
        record(run, added, "trang-bi", "added")
        assertEquals(2, images(run).length())
        assertEquals("anh-goc/trang-bi/valid.png", images(run).getJSONObject(0).getString("path"))
        assertEquals("anh-goc/trang-bi/added.png", images(run).getJSONObject(1).getString("path"))
    }

    @Test fun rejectsUnverifiedFilesAndRawUrisWithoutChangingTheManifest() = withRun { run ->
        val file = image(run, "anh-goc/quan-co/anh-quan-co/violet-head.png")
        record(run, file)
        val before = manifest(run).readBytes()
        val oldIntegrity = integrity(file)
        file.appendText("changed")
        rejects { RunImageManifest.record(run, file, "quan-co", "violet-head", source, oldIntegrity) }
        rejects { record(run, file, sourceId = "content://com.android.externalstorage.documents/private") }
        rejects { record(run, file, sourceId = "f".repeat(14)) }
        rejects { RunImageManifest.record(run, File(run, "anh-goc/trang-bi/missing.png"), "trang-bi", "missing", source, oldIntegrity) }
        assertArrayEquals(before, manifest(run).readBytes())
    }

    @Test fun rejectsOutsideFilesScreenshotsAndUncommittedPartialFiles() = withRun { run ->
        val outside = Files.createTempFile("outside-original-", ".png").toFile()
        try {
            outside.writeText("outside")
            rejects { record(run, outside) }
            rejects { record(run, image(run, "quan-co/violet-detail-1.png")) }
            rejects { record(run, image(run, "anh-goc/trang-bi/item.png.part")) }
            assertFalse(manifest(run).exists())
        } finally {
            outside.delete()
        }
    }

    @Test fun refusesMalformedAndUnsupportedManifestsWithoutOverwritingThem() = withRun { run ->
        val file = image(run, "anh-goc/trang-bi/item.png")
        val invalid = listOf(
            "{",
            "[]",
            "{}",
            "{\"schemaVersion\":1,\"collectionMode\":\"chess\",\"images\":[] } trailing",
            "{\"schemaVersion\":2,\"collectionMode\":\"chess\",\"images\":[]}",
            "{\"schemaVersion\":\"1\",\"collectionMode\":\"chess\",\"images\":[]}",
            "{\"schemaVersion\":1,\"collectionMode\":\"all\",\"images\":[]}",
            "{\"schemaVersion\":1,\"collectionMode\":\"chess\",\"images\":[{}]}"
        )
        invalid.forEach { text ->
            manifest(run).writeText(text)
            rejects { record(run, file, "trang-bi", "item") }
            assertEquals(text, manifest(run).readText())
            assertFalse(File(run, "images.json.part").exists())
        }
    }

    @Test fun refusesStructurallyInvalidExistingEntriesEvenWhenTheirFileIsMissing() = withRun { run ->
        val file = image(run, "anh-goc/trang-bi/item.png")
        record(run, file, "trang-bi", "item")
        val good = manifest(run).readText()
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("path", "../outside.png") }, { it.put("path", "/tmp/outside.png") },
            { it.put("path", "anh-goc/../item.png") }, { it.put("path", "anh-goc//item.png") },
            { it.put("path", "anh-goc/./item.png") }, { it.put("path", "anh-goc/folder\\item.png") },
            { it.put("path", "anh-goc/trang-bi/item.png.part") }, { it.put("bytes", "12") },
            { it.put("bytes", -1) }, { it.put("sha256", "wrong") }, { it.put("type", "portrait") },
            { it.put("sourceId", "content://private/source") }, { it.put("hero", "violet") },
            { it.put("reused", "true") }
        )
        mutations.forEach { mutate ->
            val doc = JSONObject(good)
            mutate(doc.getJSONArray("images").getJSONObject(0))
            val bad = doc.toString()
            manifest(run).writeText(bad)
            rejects { record(run, file, "trang-bi", "item") }
            assertEquals(bad, manifest(run).readText())
        }
    }

    @Test fun refusesDuplicateManifestPathsWithoutOverwriting() = withRun { run ->
        val file = image(run, "anh-goc/trang-bi/item.png")
        record(run, file, "trang-bi", "item")
        val doc = document(run)
        val entry = doc.getJSONArray("images").getJSONObject(0)
        doc.put("images", JSONArray().put(entry).put(entry))
        manifest(run).writeText(doc.toString())
        val before = manifest(run).readBytes()
        rejects { record(run, file, "trang-bi", "item") }
        assertArrayEquals(before, manifest(run).readBytes())
    }

    @Test fun anUnreadableManifestIsNotTreatedAsAnEmptyManifest() = withRun { run ->
        val file = image(run, "anh-goc/trang-bi/item.png")
        manifest(run).mkdir()
        File(manifest(run), "keep").writeText("existing data")
        rejects { record(run, file, "trang-bi", "item") }
        assertEquals("existing data", File(manifest(run), "keep").readText())
    }

    @Test fun aFailedPartWritePreservesTheOldManifestAndCommittedImages() = withRun { run ->
        val first = image(run, "anh-goc/trang-bi/first.png")
        record(run, first, "trang-bi", "first")
        val before = manifest(run).readBytes()
        val partial = File(run, "images.json.part").apply { mkdir() }
        val blocker = File(partial, "keep").apply { writeText("keep") }
        val second = image(run, "anh-goc/trang-bi/second.png")
        rejects { record(run, second, "trang-bi", "second") }
        assertArrayEquals(before, manifest(run).readBytes())
        assertEquals("keep", blocker.readText())
        assertTrue(first.isFile)
        assertTrue(second.isFile)
        partial.deleteRecursively()
        record(run, second, "trang-bi", "second")
        assertEquals(2, images(run).length())
    }

    @Test fun stalePartialManifestIsReplacedOnlyAfterNewContentIsComplete() = withRun { run ->
        File(run, "images.json.part").writeText("interrupted write")
        val file = image(run, "anh-goc/trang-bi/item.png")
        record(run, file, "trang-bi", "item")
        assertEquals(1, images(run).length())
        assertFalse(File(run, "images.json.part").exists())
    }

    @Test fun symlinksCannotPublishOutsideImagesOrOverwriteOtherFiles() = withRun { run ->
        val outside = Files.createTempDirectory("outside-manifest-").toFile()
        try {
            val secret = image(outside, "secret.png", "do not change")
            val link = File(run, "anh-goc/trang-bi/linked.png").apply { parentFile.mkdirs() }
            Files.createSymbolicLink(link.toPath(), secret.toPath())
            rejects { record(run, link, "trang-bi", "linked") }
            val file = image(run, "anh-goc/trang-bi/item.png")
            val externalManifest = image(outside, "existing.json", "do not change manifest")
            Files.createSymbolicLink(manifest(run).toPath(), externalManifest.toPath())
            rejects { record(run, file, "trang-bi", "item") }
            assertEquals("do not change manifest", externalManifest.readText())
            Files.delete(manifest(run).toPath())
            val partial = File(run, "images.json.part")
            Files.createSymbolicLink(partial.toPath(), secret.toPath())
            rejects { record(run, file, "trang-bi", "item") }
            assertEquals("do not change", secret.readText())
            Files.delete(partial.toPath())
            Files.delete(link.toPath())
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test fun aPriorImageReplacedByAnOutsideSymlinkIsNoLongerPublished() = withRun { run ->
        val file = image(run, "anh-goc/trang-bi/item.png", "same original bytes")
        record(run, file, "trang-bi", "item")
        val outside = Files.createTempFile("outside-replaced-", ".png").toFile()
        try {
            outside.writeBytes(file.readBytes())
            file.delete()
            Files.createSymbolicLink(file.toPath(), outside.toPath())
            val next = image(run, "anh-goc/trang-bi/next.png")
            record(run, next, "trang-bi", "next")
            assertEquals(1, images(run).length())
            assertEquals("anh-goc/trang-bi/next.png", images(run).getJSONObject(0).getString("path"))
            assertEquals("same original bytes", outside.readText())
            Files.delete(file.toPath())
        } finally {
            outside.delete()
        }
    }

    @Test fun concurrentRecordsNeverLoseAnotherCommittedEntry() = withRun { run ->
        val files = (1..8).map { image(run, "anh-goc/trang-bi/item-$it.png") }
        val failures = java.util.Collections.synchronizedList(ArrayList<Throwable>())
        val threads = files.map { file ->
            Thread {
                try {
                    record(run, file, "trang-bi", file.name)
                } catch (error: Throwable) {
                    failures.add(error)
                }
            }.apply { start() }
        }
        threads.forEach { it.join() }
        assertTrue("Concurrent record failures: $failures", failures.isEmpty())
        assertEquals(files.size, images(run).length())
        val paths = (0 until images(run).length()).map { images(run).getJSONObject(it).getString("path") }.toSet()
        assertEquals(files.map { it.relativeTo(run).invariantSeparatorsPath }.toSet(), paths)
    }
}
