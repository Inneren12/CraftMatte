package io.inneren.mh.cutout.core

import kotlin.math.roundToInt

/**
 * Immutable container for ARGB pixels stored row-major.
 */
data class ImageBuffer(
    val width: Int,
    val height: Int,
    val argb: IntArray
) {
    init {
        require(width > 0 && height > 0) {
            "Image dimensions must be positive, got ${'$'}width x ${'$'}height"
        }
        require(argb.size == width * height) {
            "ARGB buffer size ${'$'}{argb.size} does not match dimensions ${'$'}width x ${'$'}height"
        }
    }

    val size: Int
        get() = argb.size

    fun copy(): ImageBuffer = ImageBuffer(width, height, argb.copyOf())
}

@JvmInline
value class AlphaMask(val data: ByteArray) {
    init {
        require(data.isNotEmpty()) { "Alpha mask must not be empty" }
    }

    fun toIntArray(): IntArray = IntArray(data.size) { idx ->
        data[idx].toInt() and 0xFF
    }

    companion object {
        fun fromByteArray(data: ByteArray, expectedSize: Int): AlphaMask {
            require(data.size == expectedSize) {
                "Alpha mask size ${'$'}{data.size} does not match expected ${'$'}expectedSize"
            }
            return AlphaMask(data)
        }
    }
}

data class MatteResult(
    val alpha: AlphaMask,
    val preview: ImageBuffer? = null
)

data class CutoutConfig(
    val mode: Mode = Mode.Auto,
    val softness: Float = 0.2f,
    val hardThreshold: Float? = null
) {
    init {
        require(softness in 0f..1f) {
            "Softness must be within [0, 1], got ${'$'}softness"
        }
        if (hardThreshold != null) {
            require(hardThreshold in 0f..255f) {
                "Hard threshold must be within [0, 255], got ${'$'}hardThreshold"
            }
        }
    }

    enum class Mode { Auto, Portrait, Object }
}

fun interface RemoveBgEngine {
    fun removeBackground(input: ImageBuffer, config: CutoutConfig): MatteResult
}

fun RemoveBgEngine.removeBackground(input: ImageBuffer): MatteResult =
    removeBackground(input, CutoutConfig())

open class CutoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)