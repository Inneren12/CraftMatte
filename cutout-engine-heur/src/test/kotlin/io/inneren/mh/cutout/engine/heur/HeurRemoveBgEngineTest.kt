package io.inneren.mh.cutout.engine.heur

import io.inneren.mh.cutout.core.CutoutConfig
import io.inneren.mh.cutout.core.ImageBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeurRemoveBgEngineTest {

    private fun solid(w: Int, h: Int, rgb: Int): ImageBuffer {
        val argb = IntArray(w * h) { 0xFF000000.toInt() or (rgb and 0x00FFFFFF) }
        return ImageBuffer(w, h, argb)
    }

    private fun withVerticalStripe(bg: Int, fg: Int, w: Int = 32, h: Int = 16, stripeX: Int = w / 2): ImageBuffer {
        val img = solid(w, h, bg)
        val stripe = stripeX.coerceIn(1, w - 2)
        for (y in 1 until h - 1) {
            img.argb[y * w + stripe] = 0xFF000000.toInt() or (fg and 0x00FFFFFF)
        }
        return img
    }

    private fun gradientX(fromRgb: Int, toRgb: Int, w: Int = 64, h: Int = 16): ImageBuffer {
        val fromR = (fromRgb shr 16) and 0xFF
        val fromG = (fromRgb shr 8) and 0xFF
        val fromB = fromRgb and 0xFF
        val toR = (toRgb shr 16) and 0xFF
        val toG = (toRgb shr 8) and 0xFF
        val toB = toRgb and 0xFF
        val argb = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val t = x.toFloat() / (w - 1).toFloat()
                val r = (fromR + (toR - fromR) * t).toInt()
                val g = (fromG + (toG - fromG) * t).toInt()
                val b = (fromB + (toB - fromB) * t).toInt()
                argb[y * w + x] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            }
        }
        return ImageBuffer(w, h, argb)
    }

    @Test
    fun hardThresholdProducesBinaryAlpha() {
        val bg = 0xE0E0E0
        val fg = 0x101010
        val input = withVerticalStripe(bg, fg)

        val eng = HeurRemoveBgEngine()
        val res = eng.removeBackground(input, CutoutConfig(hardThreshold = 5f))
        val alphas = res.alpha.data.map { it.toInt() and 0xFF }.toSet()

        assertEquals(setOf(0, 255), alphas, "Alpha must be strictly binary under hardThreshold mode")
    }

    @Test
    fun higherSoftnessIncreasesSemiTransparentCount() {
        val bg = 0xC8C8C8
        val fg = 0x000000
        val input = gradientX(bg, fg)
        val eng = HeurRemoveBgEngine()

        val a = eng.removeBackground(input, CutoutConfig(softness = 0.1f)).alpha.data
        val b = eng.removeBackground(input, CutoutConfig(softness = 0.8f)).alpha.data

        fun semi(bytes: ByteArray) = bytes.count { v ->
            val x = v.toInt() and 0xFF
            x in 1..254
        }
        val semiA = semi(a)
        val semiB = semi(b)
        assertTrue(semiB > semiA, "With higher softness more pixels should be semi-transparent")
    }

    @Test
    fun deterministicForSameInputAndConfig() {
        val bg = 0xDDDDDD
        val fg = 0x202020
        val input = withVerticalStripe(bg, fg)
        val eng = HeurRemoveBgEngine()
        val cfg = CutoutConfig(softness = 0.3f)

        val r1 = eng.removeBackground(input, cfg).alpha.data
        val r2 = eng.removeBackground(input, cfg).alpha.data
        assertTrue(r1.contentEquals(r2), "Engine must be deterministic")
    }
}
