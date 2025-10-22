package io.inneren.mh.cutout.engine.heur

import io.inneren.mh.cutout.core.CutoutConfig
import io.inneren.mh.cutout.core.CutoutException
import io.inneren.mh.cutout.core.ImageBuffer
import io.inneren.mh.cutout.core.removeBackground
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeurRemoveBgEngineTest {
    private val engine = HeurRemoveBgEngine()

    @Test
    fun `detects simple foreground`() {
        val width = 3
        val height = 3
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        pixels[4] = 0xFF000000.toInt()
        val input = ImageBuffer(width, height, pixels)

        val result = engine.removeBackground(input, CutoutConfig())
        val alpha = result.alpha.data.map { it.toInt() and 0xFF }

        assertTrue(alpha[4] > 240, "Center pixel expected to be mostly opaque: ${alpha[4]}")
        assertTrue(alpha[0] < 40, "Background pixel expected to be mostly transparent: ${alpha[0]}")
        assertEquals(width * height, result.preview?.argb?.size)
    }

    @Test
    fun `hard threshold produces binary mask`() {
        val width = 4
        val height = 4
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        for (y in 1..2) {
            for (x in 1..2) {
                pixels[y * width + x] = 0xFF000000.toInt()
            }
        }
        val input = ImageBuffer(width, height, pixels)

        val result = engine.removeBackground(input, CutoutConfig(hardThreshold = 128f))
        val alpha = result.alpha.data.map { it.toInt() and 0xFF }

        val uniqueValues = alpha.toSet()
        assertEquals(setOf(0, 255), uniqueValues)
        val opaqueCount = alpha.count { it == 255 }
        assertEquals(4, opaqueCount)
    }

    @Test
    fun `fails on too many pixels`() {
        val largeEngine = HeurRemoveBgEngine(maxPixels = 10)
        val pixels = IntArray(12) { 0xFFFFFFFF.toInt() }
        val input = ImageBuffer(3, 4, pixels)
        assertFailsWith<CutoutException> {
            largeEngine.removeBackground(input)
        }
    }
}