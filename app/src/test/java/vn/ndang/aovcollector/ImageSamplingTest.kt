package vn.ndang.aovcollector

import org.junit.Assert.*
import org.junit.Test

class ImageSamplingTest {
    @Test fun smallNativeIconsRemainAtOriginalResolutionInAllViews() {
        for (size in listOf(48, 96, 128, 256)) {
            assertEquals(1, ImageSampling.sample(size, size, 222, 180, true))
            assertEquals(1, ImageSampling.sample(size, size, 336, 198, true))
            assertEquals(1, ImageSampling.sample(size, size, 1080, 2400, false))
        }
    }

    @Test fun largePhotosDecodeEnoughPixelsForTheViewRatherThanFixedFactors() {
        assertEquals(4, ImageSampling.sample(2400, 1080, 336, 198, true))
        assertEquals(2, ImageSampling.sample(2400, 1080, 1080, 2400, false))
        assertEquals(1, ImageSampling.sample(1080, 2400, 1080, 2400, false))
        assertEquals(1, ImageSampling.sample(0, 0, 100, 100, true))
    }

    @Test fun centreCropNeverUndersamplesTheShorterDimension() {
        assertEquals(1, ImageSampling.sample(1000, 200, 336, 198, true))
    }

    @Test fun panoramicAndMalformedDimensionsCannotAllocateUnboundedBitmaps() {
        for ((width, height) in listOf(100_000 to 128, 1_000_000 to 128, 128 to 1_000_000, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val sample = ImageSampling.sample(width, height, 336, 198, true)
            val pixels = ((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample)
            assertTrue("$width x $height at $sample", pixels <= ImageSampling.MAX_THUMB_PIXELS)
        }
    }
}
