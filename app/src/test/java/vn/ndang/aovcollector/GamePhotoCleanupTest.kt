package vn.ndang.aovcollector

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** These fixtures are local byte arrays. No test can call Android or delete a user's real document. */
class GamePhotoCleanupTest {
    private class Fixture : Closeable {
        val run = Files.createTempDirectory("cleanup-run-").toFile()
        val tree = "content://synthetic/tree/pictures"
        val original = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val integrity = OriginalPhotoCopy.digest(original.inputStream())
        var source = GamePhotoCleanup.Source("pictures/native-one", "game-photo.png", 8, 12345, "Game", true)
        val id = GamePhotoCleanup.identity(tree, source)
        val path = "anh-goc/quan-co/anh-quan-co/valhein.png"
        val target = File(run, path).apply {
            parentFile!!.mkdirs()
            writeBytes(original)
        }
        val assignment = JSONObject().put("group", "quan-co").put("item", "valhein-head")
            .put("time", 12344L).put("sourceIdentity", id)
        val record = JSONObject().put("path", path).put("bytes", integrity.bytes).put("sha256", integrity.sha256)
        val state = JSONObject().put("storageVersion", 2).put("tree", tree).put("baseline", JSONArray())
            .put("saves", JSONArray().put(JSONObject().put("group", "quan-co").put("item", "valhein-head").put("time", 12344L)))
            .put("assignments", JSONObject().put(source.id, assignment))
            .put("originalIntegrity", JSONObject().put(id, record))
        val image = JSONObject().put("path", path).put("group", "quan-co").put("item", "valhein-head")
            .put("type", "portrait").put("sourceId", id).put("bytes", integrity.bytes)
            .put("sha256", integrity.sha256).put("reused", false)
        val manifest = JSONObject().put("schemaVersion", 1).put("collectionMode", "chess").put("images", JSONArray().put(image))
        var sourceBytes = original.copyOf()
        var readable = true
        var writable = true
        var missing = false
        var deleteSucceeds = true
        var openFailure = false
        var deleteFailure = false
        var opened = 0
        var deleteCalls = 0
        var scans = 0
        var onOpen: (() -> Unit)? = null
        val access = object : GamePhotoCleanup.SourceAccess {
            override fun canRead() = readable

            override fun canWrite() = writable

            override fun scan(allow: () -> Boolean): List<GamePhotoCleanup.Source> {
                scans++
                return if (missing) emptyList() else listOf(source)
            }

            override fun current(id: String) = if (missing || id != source.id) null else source

            override fun open(id: String): InputStream {
                opened++
                onOpen?.invoke()
                if (openFailure) error("synthetic provider read failure")
                return sourceBytes.inputStream()
            }

            override fun delete(id: String): Boolean {
                assertEquals(source.id, id)
                deleteCalls++
                if (deleteFailure) error("synthetic provider delete failure")
                if (deleteSucceeds) missing = true
                return deleteSucceeds
            }
        }

        fun candidate() = GamePhotoCleanup.eligible(run, tree, state, manifest, source)

        fun delete(picks: List<GamePhotoCleanup.Candidate>, allow: () -> Boolean = { true }) =
            GamePhotoCleanup.deleteSelected(run, tree, picks, access, { state }, { manifest }, allow)

        override fun close() {
            run.deleteRecursively()
        }
    }

    @Test fun onlyExactVerifiedNativeSourceIsDeletedAndItsLocalOriginalSurvives() {
        Fixture().use { f ->
            val pick = f.candidate()!!
            val result = f.delete(listOf(pick))
            assertEquals(GamePhotoCleanup.Result(1, 0, 0, false), result)
            assertEquals(1, f.deleteCalls)
            assertArrayEquals(f.original, f.target.readBytes())
            assertTrue(f.missing)
        }
    }

    @Test fun emptySelectionDoesNotEvenScanAndDuplicateSelectionsDeleteOnce() {
        Fixture().use { f ->
            assertEquals(GamePhotoCleanup.Result(0, 0, 0, false), f.delete(emptyList()))
            assertEquals(0, f.scans)
            assertEquals(0, f.deleteCalls)
            val pick = f.candidate()!!
            assertEquals(1, f.delete(listOf(pick, pick)).deleted)
            assertEquals(1, f.deleteCalls)
        }
    }

    @Test fun oldSchemasBaselineAndMissingSaveProvenanceAreIneligible() {
        val mutations = listOf<(Fixture) -> Unit>(
            { it.state.put("storageVersion", 1) },
            { it.state.put("storageVersion", 3) },
            { it.state.put("tree", "content://another/tree") },
            { it.state.getJSONArray("baseline").put(it.source.id) },
            { it.state.put("assignments", JSONObject()) },
            { it.assignment.remove("sourceIdentity") },
            { it.assignment.put("sourceIdentity", "a".repeat(64)) },
            { it.assignment.put("time", 999L) },
            { it.state.put("saves", JSONArray()) },
            { it.state.put("originalIntegrity", JSONObject()) }
        )
        for (change in mutations) {
            Fixture().use { f ->
                change(f)
                assertNull(f.candidate())
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun missingFailedOrMismatchedManifestCannotAuthorizeCleanup() {
        val mutations = listOf<(Fixture) -> Unit>(
            { it.manifest.put("images", JSONArray()) },
            { it.manifest.put("schemaVersion", 2) },
            { it.manifest.put("collectionMode", "manual") },
            { it.image.put("sourceId", "b".repeat(64)) },
            { it.image.put("sha256", "c".repeat(64)) },
            { it.image.put("bytes", 1L) },
            { it.image.put("path", "anh-goc/quan-co/anh-quan-co/another.png") },
            { it.image.put("reused", true) },
            { it.manifest.getJSONArray("images").put(JSONObject(it.image.toString())) }
        )
        for (change in mutations) {
            Fixture().use { f ->
                val pick = f.candidate()!!
                change(f)
                assertNull(f.candidate())
                assertEquals(1, f.delete(listOf(pick)).skipped)
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun changedOrMissingSourceAndDestinationAreRecheckedAfterPreview() {
        val mutations = listOf<(Fixture) -> Unit>(
            { it.missing = true },
            { it.source = it.source.copy(modified = 999L) },
            { it.source = it.source.copy(name = "renamed.png") },
            { it.source = it.source.copy(relativeDir = "Other") },
            { it.sourceBytes[0] = 99 },
            { it.sourceBytes = it.sourceBytes.copyOf(4) },
            { it.target.delete() },
            { it.target.writeBytes(it.original.copyOf().apply { this[0] = 99 }) },
            { it.target.writeBytes(it.original.copyOf(4)) },
            { File(it.target.parentFile, it.target.name + ".part").writeText("unfinished") },
            { it.openFailure = true }
        )
        for (change in mutations) {
            Fixture().use { f ->
                val pick = f.candidate()!!
                change(f)
                val result = f.delete(listOf(pick))
                assertEquals(1, result.skipped)
                assertEquals(0, result.deleted)
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun destinationCannotEscapeRunOrCrossIntoAnotherImageGroup() {
        for (path in listOf(
            "../other.png",
            "/tmp/photo.png",
            "anh-goc/../photo.png",
            "anh-goc/quan-co/anh-quan-co/../photo.png",
            "anh-goc/trang-bi/photo.png",
            "anh-goc/quan-co/anh-quan-co/nested/photo.png",
            "anh-goc\\photo.png"
        )) {
            Fixture().use { f ->
                f.record.put("path", path)
                f.image.put("path", path)
                assertNull(f.candidate())
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun aSymlinkCannotSubstituteAnotherCopyAfterPreview() {
        Fixture().use { f ->
            val pick = f.candidate()!!
            val other = File(f.run, "elsewhere.png").apply { writeBytes(f.original) }
            f.target.delete()
            Files.createSymbolicLink(f.target.toPath(), other.toPath())
            assertEquals(1, f.delete(listOf(pick)).skipped)
            assertEquals(0, f.deleteCalls)
            assertTrue(other.isFile)
        }
    }

    @Test fun readWriteAndProviderDeletePermissionsAreAllRequired() {
        for (mode in 0..2) {
            Fixture().use { f ->
                val pick = f.candidate()!!
                when (mode) {
                    0 -> f.readable = false
                    1 -> f.writable = false
                    2 -> f.source = f.source.copy(canDelete = false)
                }
                assertEquals(1, f.delete(listOf(pick)).skipped)
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun revokedGrantOrChangedMetadataDuringHashingSkipsDeletion() {
        for (mode in 0..2) {
            Fixture().use { f ->
                val pick = f.candidate()!!
                f.onOpen = {
                    when (mode) {
                        0 -> f.writable = false
                        1 -> f.source = f.source.copy(modified = 999)
                        2 -> f.target.writeBytes(f.original.copyOf().apply { this[0] = 99 })
                    }
                }
                assertEquals(1, f.delete(listOf(pick)).skipped)
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun providerFailuresAreReportedWithoutClaimingDeletion() {
        for (mode in 0..1) {
            Fixture().use { f ->
                val pick = f.candidate()!!
                if (mode == 0) f.deleteSucceeds = false else f.deleteFailure = true
                val result = f.delete(listOf(pick))
                assertEquals(0, result.deleted)
                assertEquals(1, result.failed)
                assertTrue(f.target.isFile)
                assertFalse(f.missing)
            }
        }
    }

    @Test fun cancellationBeforeAndDuringVerificationNeverDeletes() {
        for (during in listOf(false, true)) {
            Fixture().use { f ->
                val pick = f.candidate()!!
                var allow = during
                if (during) f.onOpen = { allow = false }
                val result = f.delete(listOf(pick)) { allow }
                assertTrue(result.cancelled)
                assertEquals(0, result.deleted)
                assertEquals(0, f.deleteCalls)
            }
        }
    }

    @Test fun unknownProviderLengthStillNeedsExactDigestAndKnownLengthMustMatch() {
        Fixture().use { f ->
            f.source = f.source.copy(size = 0)
            val changedId = GamePhotoCleanup.identity(f.tree, f.source)
            f.assignment.put("sourceIdentity", changedId)
            f.state.getJSONObject("originalIntegrity").put(changedId, f.record)
            f.image.put("sourceId", changedId)
            assertEquals(1, f.delete(listOf(f.candidate()!!)).deleted)
        }
        Fixture().use { f ->
            f.record.put("bytes", 7)
            f.image.put("bytes", 7)
            assertNull(f.candidate())
            assertEquals(0, f.deleteCalls)
        }
    }
}
