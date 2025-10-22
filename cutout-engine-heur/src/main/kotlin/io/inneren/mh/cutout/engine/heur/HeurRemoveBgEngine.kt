package io.inneren.mh.cutout.engine.heur

import io.inneren.mh.cutout.core.AlphaMask
import io.inneren.mh.cutout.core.CutoutConfig
import io.inneren.mh.cutout.core.CutoutException
import io.inneren.mh.cutout.core.ImageBuffer
import io.inneren.mh.cutout.core.MatteResult
import io.inneren.mh.cutout.core.RemoveBgEngine
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class HeurRemoveBgEngine(
    private val maxPixels: Int = DEFAULT_MAX_PIXELS
) : RemoveBgEngine {

    init {
        require(maxPixels > 0) { "maxPixels must be positive" }
    }

    override fun removeBackground(input: ImageBuffer, config: CutoutConfig): MatteResult {
        validateInput(input)
        val totalPixels = input.width * input.height
        val pixels = input.argb

        val background = estimateBackground(pixels, input.width, input.height)
        val maxDistance = estimateMaxDistance(pixels, background)
        val alpha = if (maxDistance <= 0f) {
            ByteArray(totalPixels) { 0 }
        } else {
            when (val hard = config.hardThreshold) {
                null -> logisticAlpha(pixels, background, maxDistance, config)
                else -> hardAlpha(pixels, background, maxDistance, hard)
            }
        }

        val shouldSmooth = config.hardThreshold == null && input.width > 3 && input.height > 3
        val finalAlpha = if (shouldSmooth) smoothAlpha(alpha, input.width, input.height) else alpha
        val preview = buildPreview(finalAlpha, input)
        val mask = AlphaMask.fromByteArray(finalAlpha, totalPixels)
        return MatteResult(mask, preview)
    }

    private fun validateInput(buffer: ImageBuffer) {
        val pixels = buffer.width.toLong() * buffer.height.toLong()
        if (pixels >= maxPixels) {
            throw CutoutException(
                "Input image is too large (${buffer.width}x${buffer.height}). " +
                        "Limit is ${maxPixels / 1_000_000} MP to avoid memory issues."
            )
        }
    }

    private data class BackgroundColor(val r: Float, val g: Float, val b: Float)

    private fun estimateBackground(pixels: IntArray, width: Int, height: Int): BackgroundColor {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        fun sample(index: Int) {
            val color = pixels[index]
            sumR += color shr 16 and 0xFF
            sumG += color shr 8 and 0xFF
            sumB += color and 0xFF
            count++
        }

        for (x in 0 until width) {
            sample(x)
            sample((height - 1) * width + x)
        }

        for (y in 1 until height - 1) {
            sample(y * width)
            sample(y * width + (width - 1))
        }

        if (count == 0) {
            throw CutoutException("Image is empty")
        }

        val inv = 1f / count
        return BackgroundColor(sumR * inv, sumG * inv, sumB * inv)
    }

    private fun estimateMaxDistance(pixels: IntArray, background: BackgroundColor): Float {
        var maxDist = 0f
        for (pixel in pixels) {
            val dist = colorDistance(pixel, background)
            if (dist > maxDist) {
                maxDist = dist
            }
        }
        return maxDist
    }

    private fun logisticAlpha(
        pixels: IntArray,
        background: BackgroundColor,
        maxDistance: Float,
        config: CutoutConfig
    ): ByteArray {
        val size = pixels.size
        val result = ByteArray(size)
        val softness = 1f - config.softness.coerceIn(0f, 1f)
        val slope = 8f * softness + 2f
        val cut = 0.35f

        fun logistic(value: Float): Float {
            val clamped = value.coerceIn(0f, 1f)
            val exponent = (-slope * (clamped - cut)).toDouble()
            return (1.0 / (1.0 + exp(exponent))).toFloat()
        }

        val minLog = logistic(0f)
        val maxLog = logistic(1f)
        val denom = max(1e-4f, maxLog - minLog)

        for (i in 0 until size) {
            val distance = colorDistance(pixels[i], background)
            val normalized = (distance / maxDistance).coerceIn(0f, 1f)
            val logisticValue = logistic(normalized)
            val mapped = ((logisticValue - minLog) / denom).coerceIn(0f, 1f)
            result[i] = (mapped * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
        return result
    }

    private fun hardAlpha(
        pixels: IntArray,
        background: BackgroundColor,
        maxDistance: Float,
        hardThreshold: Float
    ): ByteArray {
        val threshold = (hardThreshold / 255f).coerceIn(0f, 1f)
        val size = pixels.size
        val result = ByteArray(size)
        for (i in 0 until size) {
            val distance = colorDistance(pixels[i], background)
            val normalized = if (maxDistance > 0f) distance / maxDistance else 0f
            result[i] = if (normalized >= threshold) 0xFF.toByte() else 0x00.toByte()
        }
        return result
    }

    private fun smoothAlpha(alpha: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(alpha.size)
        val sumRows = Array(3) { IntArray(width) }
        val countRows = Array(3) { IntArray(width) }

        fun prepareRow(y: Int) {
            val sumRow = sumRows[y % 3]
            val countRow = countRows[y % 3]
            val offset = y * width
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dx in -1..1) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        sum += alpha[offset + nx].toInt() and 0xFF
                        count++
                    }
                }
                sumRow[x] = sum
                countRow[x] = count
            }
        }

        for (y in 0 until height) {
            prepareRow(y)
            if (y == 0) continue
            val outY = y - 1
            val topIdx = if (y >= 2) (y - 2) % 3 else null
            val midIdx = (y - 1) % 3
            val bottomIdx = y % 3
            val destOffset = outY * width
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                if (topIdx != null) {
                    sum += sumRows[topIdx][x]
                    count += countRows[topIdx][x]
                }
                sum += sumRows[midIdx][x]
                count += countRows[midIdx][x]
                sum += sumRows[bottomIdx][x]
                count += countRows[bottomIdx][x]
                output[destOffset + x] = (sum / count).coerceIn(0, 255).toByte()
            }
        }

        val lastRowIndex = height - 1
        val midIdx = lastRowIndex % 3
        val topIdx = if (height >= 2) (lastRowIndex - 1) % 3 else null
        val destOffset = lastRowIndex * width
        for (x in 0 until width) {
            var sum = sumRows[midIdx][x]
            var count = countRows[midIdx][x]
            if (topIdx != null) {
                sum += sumRows[topIdx][x]
                count += countRows[topIdx][x]
            }
            output[destOffset + x] = (sum / count).coerceIn(0, 255).toByte()
        }

        return output
    }

    private fun buildPreview(alpha: ByteArray, input: ImageBuffer): ImageBuffer {
        val previewPixels = input.argb.copyOf()
        for (i in previewPixels.indices) {
            val rgb = previewPixels[i] and 0x00FFFFFF
            val a = alpha[i].toInt() and 0xFF
            previewPixels[i] = (a shl 24) or rgb
        }
        return ImageBuffer(input.width, input.height, previewPixels)
    }

    private fun colorDistance(pixel: Int, background: BackgroundColor): Float {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        val dr = r - background.r
        val dg = g - background.g
        val db = b - background.b
        return sqrt(dr * dr + dg * dg + db * db)
    }

    companion object {
        const val DEFAULT_MAX_PIXELS: Int = 50_000_000
    }
}