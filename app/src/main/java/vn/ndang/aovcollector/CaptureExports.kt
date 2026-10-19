package vn.ndang.aovcollector

import java.io.File

/** Export image metadata, never private Android provider grants or source document journals. */
object CaptureExports {
    private val privateJournals=setOf("pictures-session.json","pictures-copy-ledger.json",
        "game-photo-imported.json","blessing-photo-index.json")
    fun include(file:File)=file.isFile&&file.extension!="part"&&file.name !in privateJournals
}
