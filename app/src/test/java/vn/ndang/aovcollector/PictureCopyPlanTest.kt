package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PictureCopyPlanTest {
    @get:Rule val tmp=TemporaryFolder()

    @Test fun copyOneHeroStillPreservesRunFoldersAndNestedScreenshots() {
        val base=tmp.newFolder("captures")
        val hero=File(base,"chess-20261016/quan-co/tat-ca-quan-co/tuong/violet").apply { mkdirs() }
        val first=File(hero,"0001-page.png").apply { writeText("one") }
        val second=File(hero,"0002-page.png").apply { writeText("two") }
        File(hero,"hero.json").writeText("{\"name\":\"Violet\"}")
        File(hero,"events.txt").writeText("event")
        val plan=PictureCopyPlan.create(base,listOf(hero))
        assertEquals(2,plan.size)
        assertEquals(setOf(first,second),plan.map { it.file }.toSet())
        assertEquals(listOf(
            "chess-20261016/quan-co/tat-ca-quan-co/tuong/violet/0001-page.png",
            "chess-20261016/quan-co/tat-ca-quan-co/tuong/violet/0002-page.png"),plan.map { it.path })
    }

    @Test fun selectingBothParentAndChildNeverCopiesTheSameImageTwice() {
        val base=tmp.newFolder("captures")
        val run=File(base,"run-1").apply { mkdirs() }
        val hero=File(run,"violet").apply { mkdirs() }
        val image=File(hero,"001-hero.png").apply { writeText("capture") }
        val plan=PictureCopyPlan.create(base,listOf(run,hero,image))
        assertEquals(1,plan.size)
        assertEquals("run-1/violet/001-hero.png",plan.single().path)
    }

    @Test fun onlyImageFilesAreExportedAndSensitiveTraceIsSkipped() {
        val base=tmp.newFolder("captures")
        val run=File(base,"run-2").apply { mkdirs() }
        val trace=File(run,"buoc-vao-game").apply { mkdirs() }
        File(trace,"001.jpg").writeText("entry")
        val images=File(run,"abc").apply { mkdirs() }
        File(images,"001.png").writeText("png")
        File(images,"002.webp").writeText("webp")
        File(images,"metadata.json").writeText("json")
        val plan=PictureCopyPlan.create(base,listOf(run))
        assertEquals(setOf("run-2/abc/001.png","run-2/abc/002.webp"),plan.map { it.path }.toSet())
    }

    @Test fun separateRunsWithSameHeroNameStaySeparate() {
        val base=tmp.newFolder("captures")
        val a=File(base,"run-1/violet").apply { mkdirs() }
        val b=File(base,"run-2/violet").apply { mkdirs() }
        File(a,"overview.png").writeText("one")
        File(b,"overview.png").writeText("two")
        val plan=PictureCopyPlan.create(base,listOf(a,b))
        assertEquals(2,plan.size)
        assertNotEquals(plan[0].path,plan[1].path)
    }

    @Test fun outsidePathsAreRejected() {
        val base=tmp.newFolder("captures")
        val outside=tmp.newFile("outside.png")
        try {
            PictureCopyPlan.create(base,listOf(outside))
            fail("Outside file should be rejected")
        } catch(_:IllegalArgumentException) {}
    }
}
