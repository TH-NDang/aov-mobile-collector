package vn.ndang.aovcollector

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RunBlessingReuseTest {
    @Test fun legacyAndRunLocalIndexPathsResolveWithoutMovingOldImages() {
        val root=Files.createTempDirectory("run-blessing-index").toFile()
        try {
            val oldRoot=File(root,"anh-goc-co-lien-quan").apply { mkdirs() }
            val old=File(oldRoot,"chuc-phuc/thuong/old-run/hoan-phi-5-vang.jpg")
                .apply { parentFile.mkdirs();writeBytes(byteArrayOf(1,2,3)) }
            val index=File(root,"index.json")
            val group="chuc-phuc-thuong";val key=ChessBlessings.key("Hoàn phí 5 vàng")
            val art="blessing-art-v1:500:"+"0000111100001111".repeat(16)
            BlessingPhotoIndex.record(index,oldRoot,group,key,old,setOf(art))
            // Existing installed-app index entries do not carry a schema version.
            val legacy=JSONObject(index.readText())
            legacy.getJSONObject("$group:$key").remove("schemaVersion")
            index.writeText(legacy.toString())
            val found=BlessingPhotoIndex.findRecord(index,root,group,
                ChessBlessings.key("Hoàn phif 5 vàng"),setOf(art),oldRoot)!!
            assertEquals(old.canonicalFile,found.file.canonicalFile)
            assertEquals(key,found.key)
            val run=File(root,"new-run").apply { mkdirs() }
            val copy=File(run,"anh-goc/chuc-phuc/thuong/hoan-phi-5-vang.jpg")
                .apply { parentFile.mkdirs() }
            val expected=old.inputStream().use { OriginalPhotoCopy.digest(it) }
            old.inputStream().use { OriginalPhotoCopy.copy(it,copy,expected.bytes) }
            RunImageManifest.record(run,copy,group,"Hoàn phif 5 vàng","a".repeat(64),expected,true)
            BlessingPhotoIndex.record(index,root,group,found.key,copy,found.aliases)
            assertTrue(old.isFile)
            assertEquals(copy.canonicalFile,BlessingPhotoIndex.findSaved(index,root,group,key,legacyRoot=oldRoot)?.canonicalFile)
            assertNull(BlessingPhotoIndex.findSaved(index,root,group,ChessBlessings.key("Hoàn phiff 5 vàng"),setOf(art),oldRoot))
            old.delete()
            assertTrue(OriginalPhotoCopy.matches(copy,expected))
            assertEquals(copy.canonicalFile,BlessingPhotoIndex.findSaved(index,root,group,key,legacyRoot=oldRoot)?.canonicalFile)
            copy.writeBytes(byteArrayOf(3,2,1))
            assertNull(BlessingPhotoIndex.findSaved(index,root,group,key,legacyRoot=oldRoot))
        } finally { root.deleteRecursively() }
    }
}
