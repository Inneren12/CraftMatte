package io.inneren.mh.cutout.cli

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import io.inneren.mh.cutout.core.AlphaMask
import io.inneren.mh.cutout.core.CutoutConfig
import io.inneren.mh.cutout.core.ImageBuffer
import io.inneren.mh.cutout.core.MatteResult
import io.inneren.mh.cutout.core.RemoveBgEngine
import io.inneren.mh.cutout.engine.heur.HeurRemoveBgEngine
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cli = CutoutCli(HeurRemoveBgEngine())
    val exitCode = cli.run(args)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

class CutoutCli(private val engine: RemoveBgEngine) {
    fun run(rawArgs: Array<String>): Int {
        if (rawArgs.contains("--help")) {
            printUsage()
            return 0
        }

        val arguments = try {
            CliArguments.parse(rawArgs)
        } catch (e: CliException) {
            System.err.println(e.message)
            printUsage()
            return 1
        }

        return try {
            val inputBuffer = loadImage(arguments.input)
            val config = CutoutConfig(
                mode = arguments.mode,
                softness = arguments.softness,
                hardThreshold = arguments.hardThreshold
            )
            val result = engine.removeBackground(inputBuffer, config)
            val outputBuffer = result.preview ?: applyAlpha(inputBuffer, result.alpha)
            saveAsPng(outputBuffer, arguments.output)
            0
        } catch (e: CliException) {
            System.err.println(e.message)
            2
        } catch (e: Exception) {
            System.err.println("Unexpected error: ${e.message ?: "unknown"}")
            3
        }
    }

    private fun loadImage(path: Path): ImageBuffer {
        if (!Files.exists(path)) {
            throw CliException("Input file does not exist: ${path.absolutePathString()}")
        }
        val file = path.toFile()
        val buffered = readBufferedImage(file)
        val oriented = applyOrientation(buffered, readOrientation(file))
        return toImageBuffer(oriented)
    }

    private fun readBufferedImage(file: File): BufferedImage {
        return try {
            ImageIO.setUseCache(false)
            ImageIO.read(file) ?: throw CliException("Unsupported image format: ${file.absolutePath}")
        } catch (e: IOException) {
            throw CliException("Failed to read image: ${e.message}", e)
        }
    }

    private fun readOrientation(file: File): Int {
        return try {
            val metadata: Metadata = ImageMetadataReader.readMetadata(file)
            val directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            directory?.getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
        } catch (_: Exception) {
            1
        }
    }

    private fun applyOrientation(image: BufferedImage, orientation: Int): BufferedImage {
        return when (orientation) {
            1 -> ensureArgb(image)
            2 -> flipHorizontal(image)
            3 -> rotate180(image)
            4 -> flipVertical(image)
            5 -> flipHorizontal(rotateLeft(image))
            6 -> rotateRight(image)
            7 -> flipHorizontal(rotateRight(image))
            8 -> rotateLeft(image)
            else -> ensureArgb(image)
        }
    }

    private fun rotateRight(image: BufferedImage): BufferedImage {
        val src = ensureArgb(image)
        val width = src.width
        val height = src.height
        val srcPixels = IntArray(width * height)
        src.getRGB(0, 0, width, height, srcPixels, 0, width)
        val rotatedPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = y * width + x
                val destX = height - 1 - y
                val destY = x
                rotatedPixels[destY * height + destX] = srcPixels[srcIndex]
            }
        }
        val rotated = BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB)
        rotated.setRGB(0, 0, height, width, rotatedPixels, 0, height)
        return rotated
    }

    private fun rotateLeft(image: BufferedImage): BufferedImage {
        val src = ensureArgb(image)
        val width = src.width
        val height = src.height
        val srcPixels = IntArray(width * height)
        src.getRGB(0, 0, width, height, srcPixels, 0, width)
        val rotatedPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = y * width + x
                val destX = y
                val destY = width - 1 - x
                rotatedPixels[destY * height + destX] = srcPixels[srcIndex]
            }
        }
        val rotated = BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB)
        rotated.setRGB(0, 0, height, width, rotatedPixels, 0, height)
        return rotated
    }

    private fun rotate180(image: BufferedImage): BufferedImage {
        val src = ensureArgb(image)
        val width = src.width
        val height = src.height
        val srcPixels = IntArray(width * height)
        src.getRGB(0, 0, width, height, srcPixels, 0, width)
        val rotatedPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = y * width + x
                val destX = width - 1 - x
                val destY = height - 1 - y
                rotatedPixels[destY * width + destX] = srcPixels[srcIndex]
            }
        }
        val rotated = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        rotated.setRGB(0, 0, width, height, rotatedPixels, 0, width)
        return rotated
    }

    private fun flipHorizontal(image: BufferedImage): BufferedImage {
        val src = ensureArgb(image)
        val width = src.width
        val height = src.height
        val srcPixels = IntArray(width * height)
        src.getRGB(0, 0, width, height, srcPixels, 0, width)
        val flippedPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = y * width + x
                val destX = width - 1 - x
                flippedPixels[y * width + destX] = srcPixels[srcIndex]
            }
        }
        val flipped = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        flipped.setRGB(0, 0, width, height, flippedPixels, 0, width)
        return flipped
    }

    private fun flipVertical(image: BufferedImage): BufferedImage {
        val src = ensureArgb(image)
        val width = src.width
        val height = src.height
        val srcPixels = IntArray(width * height)
        src.getRGB(0, 0, width, height, srcPixels, 0, width)
        val flippedPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = y * width + x
                val destY = height - 1 - y
                flippedPixels[destY * width + x] = srcPixels[srcIndex]
            }
        }
        val flipped = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        flipped.setRGB(0, 0, width, height, flippedPixels, 0, width)
        return flipped
    }

    private fun ensureArgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB) return image
        val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = converted.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return converted
    }

    private fun toImageBuffer(image: BufferedImage): ImageBuffer {
        val argb = ensureArgb(image)
        val pixels = IntArray(argb.width * argb.height)
        argb.getRGB(0, 0, argb.width, argb.height, pixels, 0, argb.width)
        return ImageBuffer(argb.width, argb.height, pixels)
    }

    private fun applyAlpha(input: ImageBuffer, mask: AlphaMask): ImageBuffer {
        require(mask.data.size == input.argb.size) {
            "Alpha mask size ${mask.data.size} does not match image size ${input.argb.size}"
        }
        val pixels = input.argb.copyOf()
        for (i in pixels.indices) {
            val alpha = mask.data[i].toInt() and 0xFF
            pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
        }
        return ImageBuffer(input.width, input.height, pixels)
    }

    private fun saveAsPng(buffer: ImageBuffer, output: Path) {
        val parent = output.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val image = BufferedImage(buffer.width, buffer.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, buffer.width, buffer.height, buffer.argb, 0, buffer.width)
        val file = output.toFile()
        if (!ImageIO.write(image, "png", file)) {
            throw CliException("Failed to write PNG to ${output.absolutePathString()}")
        }
    }

    private fun printUsage() {
        println("Usage: cutout-cli <in.(png|jpg)> <out.png> [--mode auto|portrait|object] [--soft 0..1] [--hard 0..255]")
    }
}

class CliArguments(
    val input: Path,
    val output: Path,
    val mode: CutoutConfig.Mode,
    val softness: Float,
    val hardThreshold: Float?
) {
    companion object {
        fun parse(args: Array<String>): CliArguments {
            if (args.isEmpty()) {
                throw CliException("Missing arguments")
            }

            val positional = ArrayList<String>()
            var mode: CutoutConfig.Mode = CutoutConfig.Mode.Auto
            var softness = 0.2f
            var hardThreshold: Float? = null

            var index = 0
            while (index < args.size) {
                when (val token = args[index]) {
                    "--mode" -> {
                        index++
                        if (index >= args.size) throw CliException("--mode requires a value")
                        mode = when (args[index].lowercase()) {
                            "auto" -> CutoutConfig.Mode.Auto
                            "portrait" -> CutoutConfig.Mode.Portrait
                            "object" -> CutoutConfig.Mode.Object
                            else -> throw CliException("Unknown mode '${args[index]}'")
                        }
                    }
                    "--soft" -> {
                        index++
                        if (index >= args.size) throw CliException("--soft requires a value")
                        softness = args[index].toFloatOrNull()?.also {
                            if (it !in 0f..1f) throw CliException("--soft must be within 0..1")
                        } ?: throw CliException("--soft requires a numeric value")
                    }
                    "--hard" -> {
                        index++
                        if (index >= args.size) throw CliException("--hard requires a value")
                        val value = args[index].toFloatOrNull() ?: throw CliException("--hard requires a numeric value")
                        if (value !in 0f..255f) throw CliException("--hard must be within 0..255")
                        hardThreshold = value
                    }
                    else -> positional += token
                }
                index++
            }

            if (positional.size < 2) {
                throw CliException("Input and output paths are required")
            }

            val input = Path.of(positional[0])
            val output = Path.of(positional[1])
            return CliArguments(input, output, mode, softness, hardThreshold)
        }
    }
}

class CliException(message: String, cause: Throwable? = null) : Exception(message, cause)