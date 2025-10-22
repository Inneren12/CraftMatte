package io.inneren.mh.cutout.cli

import io.inneren.mh.cutout.core.AlphaMask
import io.inneren.mh.cutout.core.CutoutConfig
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CliArgumentsTest {
    @Test
    fun `parses minimal arguments`() {
        val args = arrayOf("input.jpg", "output.png")
        val parsed = CliArguments.parse(args)
        assertEquals(Path.of("input.jpg"), parsed.input)
        assertEquals(Path.of("output.png"), parsed.output)
        assertEquals(CutoutConfig.Mode.Auto, parsed.mode)
        assertEquals(0.2f, parsed.softness)
        assertEquals(null, parsed.hardThreshold)
    }

    @Test
    fun `parses options`() {
        val args = arrayOf("--mode", "portrait", "--soft", "0.4", "--hard", "120", "in.png", "out.png")
        val parsed = CliArguments.parse(args)
        assertEquals(CutoutConfig.Mode.Portrait, parsed.mode)
        assertEquals(0.4f, parsed.softness)
        assertEquals(120f, parsed.hardThreshold)
    }

    @Test
    fun `requires positional arguments`() {
        assertFailsWith<CliException> {
            CliArguments.parse(arrayOf("--mode", "auto"))
        }
    }

    @Test
    fun `run processes image`() {
        val tempDir = createTempDirectory()
        val input = tempDir.resolve("input.png")
        val output = tempDir.resolve("output.png")
        createTestImage(input)
        val cli = CutoutCli(TestEngine())
        val exitCode = cli.run(arrayOf(input.toString(), output.toString()))
        assertEquals(0, exitCode)
        assertTrue(Files.exists(output))
    }

    private fun createTestImage(path: Path) {
        val image = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFFFFFF)
        image.setRGB(1, 0, 0xFFFFFF)
        image.setRGB(0, 1, 0x000000)
        image.setRGB(1, 1, 0x000000)
        ImageIO.write(image, "png", path.toFile())
    }

    private class TestEngine : io.inneren.mh.cutout.core.RemoveBgEngine {
        override fun removeBackground(input: io.inneren.mh.cutout.core.ImageBuffer, config: io.inneren.mh.cutout.core.CutoutConfig): io.inneren.mh.cutout.core.MatteResult {
            val alphaBytes = ByteArray(input.argb.size) { if (it < input.argb.size / 2) 0 else 255.toByte() }
            return io.inneren.mh.cutout.core.MatteResult(
                AlphaMask(alphaBytes),
                input.copy()
            )
        }
    }
}