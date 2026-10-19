package vn.ndang.aovcollector

/** The first OCR result is not necessarily a heading: descriptions often arrive before titles. */
object ChessPopupTitle {
    fun key(title: String) = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).replace('đ', 'd')
        .replace("'", "").replace("\u2019", "").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun select(words: List<GameScreens.Word>, titleInk: (GameScreens.Word) -> Boolean = { true }): String? = words.asSequence().filter {
        it.left >= .345 &&
            it.x <= .79 &&
            it.y in .14..0.32 &&
            it.plain.length in 2..60 &&
            !it.plain.contains("colienquan") &&
            !isDescription(it.text) &&
            key(it.text).isNotBlank() &&
            it.text.none(Char::isDigit) &&
            it.text.firstOrNull { c -> !c.isWhitespace() }?.isLetter() == true &&
            titleInk(it)
    }.sortedWith(compareBy<GameScreens.Word>({ it.top }, { it.left }, { -it.text.length }))
        .firstOrNull()?.text?.trim()

    /** White headings are separate from cyan descriptions and yellow numerical bonuses. */
    fun whiteHeading(bitmap: android.graphics.Bitmap, word: GameScreens.Word): Boolean {
        val x0 = (word.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = (word.right * bitmap.width).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (word.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (word.bottom * bitmap.height).toInt().coerceIn(y0 + 1, bitmap.height)
        var white = 0
        var colored = 0
        for (y in y0 until y1 step maxOf(1, (y1 - y0) / 16)) {
            for (x in x0 until x1 step maxOf(1, (x1 - x0) / 100)) {
                val p = bitmap.getPixel(x, y)
                val r = p shr 16 and 255
                val g = p shr 8 and 255
                val b = p and 255
                if (minOf(r, g, b) > 170 && maxOf(r, g, b) - minOf(r, g, b) < 50) {
                    white++
                } else if ((g > 125 && b > 150 && b - r > 35 && g - r > 25) || (r > 170 && g > 150 && b < 120)) {
                    colored++
                }
            }
        }
        return white >= 4 && white > colored
    }

    private fun isDescription(text: String): Boolean {
        val slug = HeroNames.slug(text)
        return listOf(
            "cho-dong-doi-",
            "moi-khi-",
            "khi-ban-",
            "nhan-them-",
            "tang-them-",
            "giam-thoi-gian-",
            "sat-thuong-"
        ).any { slug.startsWith(it) }
    }
}
