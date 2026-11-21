package vn.ndang.aovcollector

import java.text.Normalizer
import java.util.Locale

/** ASCII names for newly saved files. Never apply this to existing paths or journal identities. */
object StorageNames {
    /** Keep this folding identical to the historical hero/OCR slug, including apostrophes. */
    fun ascii(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('đ', 'd')
        .replace("'", "").replace("\u2019", "")
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun component(text: String, maxLength: Int = 70): String {
        require(maxLength > 0)
        return ascii(text).take(maxLength).trimEnd('-').ifEmpty { "anh".take(maxLength) }
    }

    fun runPrefix(mode: String, heroName: String): String = listOf(
        component(mode, 24),
        heroName.takeIf { it.isNotBlank() }?.let { component(it, 40) }
    ).filterNotNull().joinToString("-")

    /** Retain the source identity suffix so names that fold alike cannot overwrite one another. */
    fun importedImage(name: String, identity: String, extension: String): String {
        require(identity.matches(Regex("[a-f0-9]{14}")))
        require(extension in setOf("png", "jpg", "jpeg", "webp"))
        return component(name.substringBeforeLast('.', name)) + "-" + identity + "." + extension
    }
}
