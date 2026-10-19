package vn.ndang.aovcollector

/** Decode enough source pixels for the actual view; small original icons must never be sampled down. */
object ImageSampling {
    // A compressed image can expand far beyond its file size. Bound each decoded bitmap to 16 MiB.
    const val MAX_PIXELS = 4_194_304L
    const val MAX_THUMB_PIXELS = 262_144L

    fun sample(width: Int, height: Int, targetWidth: Int, targetHeight: Int, crop: Boolean): Int {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        val ratio = if (crop) {
            minOf(width.toDouble() / targetWidth, height.toDouble() / targetHeight)
        } else {
            maxOf(width.toDouble() / targetWidth, height.toDouble() / targetHeight)
        }
        var sample = 1
        while (sample <= Int.MAX_VALUE / 2 && sample * 2 <= ratio) sample *= 2
        val budget = if (crop) MAX_THUMB_PIXELS else MAX_PIXELS
        while (sample <= Int.MAX_VALUE / 2 &&
            ((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample) > budget
        ) {
            sample *= 2
        }
        return sample
    }
}
