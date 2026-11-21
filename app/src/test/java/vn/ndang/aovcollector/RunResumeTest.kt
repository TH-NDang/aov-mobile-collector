package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs cut by the power button or the phone turning off, gone on with by Tiếp tục. */
class RunResumeTest {
    private fun screen(
        preparation: Boolean = false,
        picker: Boolean = false,
        popup: Boolean = false,
        lobby: Boolean = false,
        equipment: Boolean = false,
        hero: String? = null
    ) = ProResume.Screen(preparation, picker, popup, lobby, equipment, hero)

    private val cut = JSONObject().put("hero", "Tachi").put("index", 41).put("captured", 2)

    @Test fun aProHeroCutPartWayGoesOnOnlyOnItsOwnPreparation() {
        // Chuẩn bị with the cut hero: on with his third combo, nothing chosen again.
        assertEquals(ProResume.Move.Collect, ProResume.move(cut, 41, screen(preparation = true, hero = "Tachi")))
        // The lobby trip on Tiếp tục left Chuẩn bị on another hero: chosen again by his place, never collected over.
        assertEquals(ProResume.Move.Pick(41, true), ProResume.move(cut, 41, screen(preparation = true, hero = "Aoi")))
        assertEquals(ProResume.Move.Pick(41, true), ProResume.move(cut, 41, screen(preparation = true, hero = null)))
        assertEquals(ProResume.Move.Pick(41, true), ProResume.move(cut, 41, screen(picker = true)))
        // His name not read when he was chosen: no name proves him, so he is chosen again.
        val unnamed = JSONObject(cut.toString()).put("hero", "")
        assertEquals(ProResume.Move.Pick(41, true), ProResume.move(unnamed, 41, screen(preparation = true, hero = "Tachi")))
        // The Pro list still open, or the lobby: closed, or Trang bị opened, then looked at again.
        assertEquals(ProResume.Move.ClosePopup, ProResume.move(cut, 41, screen(popup = true)))
        assertEquals(ProResume.Move.OpenEquipment, ProResume.move(cut, 41, screen(lobby = true, equipment = true)))
        assertEquals(ProResume.Move.Wait, ProResume.move(cut, 41, screen()))
    }

    @Test fun withNoHeroCutTheNextIsChosen() {
        assertEquals(ProResume.Move.Pick(7, false), ProResume.move(null, 7, screen(preparation = true, hero = "Aoi")))
        assertEquals(ProResume.Move.Pick(7, false), ProResume.move(null, 7, screen(picker = true)))
        assertEquals(ProResume.Move.OpenEquipment, ProResume.move(null, 7, screen(lobby = true, equipment = true)))
    }

    @Test fun aNameMisreadByALetterIsTheSameHero() {
        assertTrue(ProResume.same("Superman", "Supenman"))
        assertFalse(ProResume.same("Keera", "Veera"))
        assertFalse(ProResume.same("", ""))
    }

    /** A hero chosen without his name read keeps an empty name: the journal is whole and Tiếp tục goes on. */
    @Test fun aProJournalWithAHeroWhoseNameWasNotReadIsRead() {
        val dir = Files.createTempDirectory("pro-progress").toFile()
        try {
            val store = ProProgressFile(File(dir, "pro-progress.json"))
            val heroes = JSONObject().put("pro-001", JSONObject().put("hero", "Aoi").put("index", 0).put("done", true))
                .put("pro-002", JSONObject().put("hero", "").put("index", 1).put("captured", 3))
            store.write(JSONObject().put("schema", 1).put("next", 1).put("active", "pro-002").put("heroes", heroes))
            val read = store.read()
            assertEquals("pro-002", read.getString("active"))
            assertEquals(3, read.getJSONObject("heroes").getJSONObject("pro-002").getInt("captured"))
            // A hero with no place is still damage, never a fresh start.
            store.write(
                JSONObject().put("schema", 1).put("next", 0).put("heroes", JSONObject().put("pro-001", JSONObject().put("hero", "Aoi")))
            )
            assertTrue(runCatching { store.read() }.isFailure)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Dừng by a slip ended a Đề cử Pro of 57 heroes: its folder still holds all it needs to go on. */
    @Test fun aRunEndedBeforeItsEndCanBeOpenedAgain() {
        val raw = BuiltInMacros.proBuilds()
        val hash = Macro(raw).hash

        fun record(state: String, next: Int = 0, of: String = hash) =
            JSONObject().put("state", state).put("hash", of).put("nextStep", next).put("totalSteps", 1)
        val run = "pro-builds-20261111-120243-738"
        // Ended by Dừng, and still the panel's run, or no longer it after Tạo lượt mới.
        assertTrue(RunReopen.allowed(run, record("stopped"), raw, run, true))
        assertTrue(RunReopen.allowed(run, record("stopped"), raw, "", false))
        // Left paused when another task was chosen: also open again, unless it is the panel's own run.
        assertTrue(RunReopen.allowed(run, record("paused"), raw, "pro-builds-other", false))
        assertFalse(RunReopen.allowed(run, record("paused"), raw, run, false))
        // Done, still going, or its macro not the one it ran: nothing to go on with.
        assertFalse(RunReopen.allowed(run, record("completed", 1), raw, "", false))
        assertFalse(RunReopen.allowed(run, record("stopped", 1), raw, "", false))
        assertFalse(RunReopen.allowed(run, record("running"), raw, "", false))
        assertFalse(RunReopen.allowed(run, record("stopped", of = "other"), raw, "", false))
        assertFalse(RunReopen.allowed(run, record("stopped"), "{broken", "", false))
    }

    /** The user's hero 89: two photos of three, done. The run goes back to it once, after the last hero. */
    @Test fun aHeroLeftWithFewerPhotosThanAskedIsGoneBackToOnce() {
        fun hero(captured: Int) = JSONObject().put("hero", "x").put("index", 0).put("captured", captured).put("done", true)
        val heroes = JSONObject()
            .put("pro-088", hero(3).put("restored", true))
            .put("pro-089", hero(2).put("total", 2).put("restored", true).put("result", "collected").put("photos", 2))
            .put("pro-090", hero(0).put("total", 0).put("empty", true))
            .put("pro-091", hero(1).put("done", false))
        assertEquals("pro-089", ProSecondPass.next(heroes, 3))
        val florentino = heroes.getJSONObject("pro-089")
        ProSecondPass.begin(florentino)
        // Its two photos stay; the list is read again for the third, and the first combo put back afterwards.
        assertEquals(2, florentino.getInt("captured"))
        assertEquals(-1, florentino.getInt("total"))
        assertFalse(florentino.getBoolean("done"))
        assertFalse(florentino.has("restored"))
        // Done again with two, its list having no more: not gone back to a second time.
        florentino.put("done", true)
        assertNull(ProSecondPass.next(heroes, 3))
        // Every combo asked for: only a hero the reader marked as cut short, not one with a short list.
        val all = JSONObject().put("pro-001", hero(2)).put("pro-002", hero(4).put("lost", 5))
        assertEquals("pro-002", ProSecondPass.next(all, BuiltInMacros.ALL))
        assertEquals("pro-001", ProSecondPass.next(all, 5))
    }

    @Test fun whatAStepFinishedIsReadBackAfterTheAppIsGone() {
        val dir = Files.createTempDirectory("step-progress").toFile()
        try {
            val file = File(dir, "run/${StepProgress.NAME}")
            val first = StepProgress(file)
            assertNull(first.read("ranking-0002"))
            assertTrue(first.write("ranking-0002", JSONObject().put("tables", StepProgress.array(listOf("rank-all", "rank-cao-thu")))))
            assertTrue(first.write("board-0003", JSONObject().put("next", 12).put("players", StepProgress.array(listOf("hero-001-p1")))))
            // Another process, as after the phone was turned off: each step's own.
            val again = StepProgress(file)
            assertEquals(setOf("rank-all", "rank-cao-thu"), StepProgress.strings(again.read("ranking-0002"), "tables"))
            assertEquals(12, again.read("board-0003")!!.getInt("next"))
            assertEquals(setOf("hero-001-p1"), StepProgress.strings(again.read("board-0003"), "players"))
            assertFalse(File(file.parentFile, file.name + ".part").exists())
            // A damaged file is no progress: the step is done again rather than skipped.
            file.writeText("{broken")
            assertNull(StepProgress(file).read("board-0003"))
            assertTrue(StepProgress.strings(null, "tables").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
