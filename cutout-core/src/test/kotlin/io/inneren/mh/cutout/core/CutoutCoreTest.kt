package io.inneren.mh.cutout.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CutoutCoreTest {
    @Test
    fun `image buffer validates dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            ImageBuffer(0, 10, IntArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            ImageBuffer(2, 2, IntArray(3))
        }
    }

    @Test
    fun `alpha mask factory validates size`() {
        val data = ByteArray(4)
        val mask = AlphaMask.fromByteArray(data, 4)
        assertEquals(4, mask.data.size)

        assertFailsWith<IllegalArgumentException> {
            AlphaMask.fromByteArray(ByteArray(3), 4)
        }
    }

    @Test
    fun `cutout config validates ranges`() {
        assertFailsWith<IllegalArgumentException> {
            CutoutConfig(softness = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            CutoutConfig(softness = 1.1f)
        }
        CutoutConfig(softness = 0.5f)

        assertFailsWith<IllegalArgumentException> {
            CutoutConfig(hardThreshold = -1f)
        }
        assertFailsWith<IllegalArgumentException> {
            CutoutConfig(hardThreshold = 256f)
        }
        CutoutConfig(hardThreshold = 120f)
    }
}