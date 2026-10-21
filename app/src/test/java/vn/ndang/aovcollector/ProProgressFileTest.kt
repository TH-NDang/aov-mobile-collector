package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProProgressFileTest {
    private fun state() = JSONObject().put("schema", 1).put("next", 0).put(
        "heroes",
        JSONObject().put(
            "ngo-khong",
            JSONObject()
                .put("hero", "Ngộ Không").put("index", 0).put("phase", "restore_pending").put("captured", 5).put("restorationNeeded", true)
        )
    )

    @Test fun reloadPreservesRestorationObligationAndCapturedCount() {
        val dir = Files.createTempDirectory("pro-progress").toFile()
        try {
            val store = ProProgressFile(File(dir, "pro-progress.json"))
            store.write(state())
            val hero = store.read().getJSONObject("heroes").getJSONObject("ngo-khong")
            assertEquals("restore_pending", hero.getString("phase"))
            assertEquals(5, hero.getInt("captured"))
            assertTrue(hero.getBoolean("restorationNeeded"))
            store.write(state().put("next", 1))
            assertEquals(1, store.read().getInt("next"))
            assertFalse(File(dir, "pro-progress.json.part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun interruptedPartCannotReplaceCommittedJournal() {
        val dir = Files.createTempDirectory("pro-progress").toFile()
        try {
            val store = ProProgressFile(File(dir, "pro-progress.json"))
            store.write(state())
            File(dir, "pro-progress.json.part").writeText("{broken")
            assertEquals(0, store.read().getInt("next"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun damagedJournalMustNotStartFresh() {
        val dir = Files.createTempDirectory("pro-progress").toFile()
        try {
            val f = File(dir, "pro-progress.json")
            f.writeText("{broken")
            assertTrue(ProProgressFile(f).exists())
            assertTrue(runCatching { ProProgressFile(f).read() }.isFailure)
        } finally {
            dir.deleteRecursively()
        }
    }
}
