package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CaptureExportsTest {
    @Test fun exportKeepsPortableMetadataButOmitsProviderJournalsAndPartialWrites() {
        val run=Files.createTempDirectory("capture-export").toFile()
        try {
            for(name in listOf("images.json","run.json","macro.json","hero.json","cung-thu-page-1.png")) {
                val file=run.resolve(name).apply { writeText("test") }
                assertTrue(name,CaptureExports.include(file))
            }
            for(name in listOf("pictures-session.json","pictures-copy-ledger.json","game-photo-imported.json",
                "blessing-photo-index.json","images.json.part","photo.png.part")) {
                val file=run.resolve(name).apply { writeText("private provider document ID") }
                assertFalse(name,CaptureExports.include(file))
            }
            assertFalse(CaptureExports.include(run))
        } finally { run.deleteRecursively() }
    }
}
