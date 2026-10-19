package vn.ndang.aovcollector

/** Statistics names stay descriptive outside their collection folder, including in exports. */
object RankingCaptureFiles {
    data class Page(val combo: RankScreens.Combo, val number: Int) {
        val basename get() = "thong-ke-${combo.slug}-page-$number"
        val title get() = "${combo.label} · Trang $number"
    }

    fun page(basename: String): Page? {
        for (combo in RankScreens.COMBOS) {
            val slug = Regex.escape(combo.slug)
            val match = Regex("(?:[0-9]{4,}-)?$slug-([0-9]{2,})").matchEntire(basename)
                ?: Regex("thong-ke-$slug-page-([0-9]+)").matchEntire(basename)
                ?: continue
            val number = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return Page(combo, number)
        }
        return null
    }

    fun screenshotBase(token: String, rankingSteps: Int = 1) = if (rankingSteps == 1) page(token)?.basename ?: token else token

    fun title(basename: String) = page(basename)?.title

    fun sortKey(filename: String): String {
        val page = page(filename.substringBeforeLast('.')) ?: return filename
        return "thong-ke-${page.combo.slug}-page-${page.number.toString().padStart(10, '0')}.${filename.substringAfterLast('.')}"
    }
}
