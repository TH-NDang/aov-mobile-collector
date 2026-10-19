package vn.ndang.aovcollector

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistorySearchTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun rootSearchFindsNestedFoldersWithVietnameseAndIgnoresMetadata() {
        val root = temp.newFolder("captures")
        val run = File(root, "chess-20261016").apply { mkdirs() }
        val group = File(run, "quan-co").apply { mkdirs() }
        val hero = File(group, "violet").apply { mkdirs() }
        File(hero, "001-overview.png").writeText("image")
        File(hero, "hero.json").writeText("{\"name\":\"Violet\"}")
        File(root, "manual.png").writeText("image")
        val result = HistorySearch.find(root, "VIOLET", { it.name })
        assertEquals(listOf(hero), result)
        assertFalse(result.any { it.extension == "json" })
        assertEquals(
            listOf(group),
            HistorySearch.find(root, "tổng hợp", { file ->
                if (file.name == "quan-co") "Tổng hợp quân cờ" else file.name
            })
        )
    }

    @Test fun nestedScopeExcludesSiblingsAndShowsScreenshotFiles() {
        val root = temp.newFolder("captures")
        val heroes = File(root, "chess-1/quan-co").apply { mkdirs() }
        val tasks = File(root, "chess-1/nhiem-vu").apply { mkdirs() }
        val inside = File(heroes, "001-chess__quan-co__tat-ca-quan-co__trang__001.png").apply { writeText("image") }
        File(tasks, "001-chess__nhiem-vu__danh-sach-nhiem-vu__trang__001.png").writeText("image")
        val results = HistorySearch.find(heroes, "trang", { it.name })
        assertEquals(listOf(inside), results)
        assertTrue(HistorySearch.find(heroes, "nhiệm vụ", { it.name }).isEmpty())
    }

    @Test fun excludesPrivateEntryTraceAndAllowsAccentInsensitiveTitles() {
        val root = temp.newFolder("captures")
        val run = File(root, "run-1").apply { mkdirs() }
        File(run, "buoc-vao-game").apply { mkdirs() }.resolve("001.png").writeText("image")
        val photo = File(run, "other.png").apply { writeText("image") }
        assertTrue(HistorySearch.find(root, "001", { it.name }).isEmpty())
        assertEquals(
            listOf(photo),
            HistorySearch.find(root, "kỹ năng", { file ->
                if (file == photo) "Kỹ năng · Chiêu 1" else file.name
            })
        )
    }

    @Test fun capsResultsAndHandlesEmptySearch() {
        val root = temp.newFolder("captures")
        repeat(30) { File(root, "hero-$it.png").writeText("image") }
        assertEquals(7, HistorySearch.find(root, "hero", { it.name }, 7).size)
        assertTrue(HistorySearch.find(root, "", { it.name }).isEmpty())
        assertTrue(HistorySearch.find(root, "unknown", { it.name }).isEmpty())
    }
}
