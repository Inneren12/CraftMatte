package io.inneren.mh.cutout.cli

import io.inneren.mh.cutout.engine.heur.HeurRemoveBgEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class CliExitCodeTest {
    @Test
    fun helpReturnsZero() {
        val code = CutoutCli(HeurRemoveBgEngine()).run(arrayOf("--help"))
        assertEquals(0, code)
    }

    @Test
    fun missingInputReturnsError() {
        val code = CutoutCli(HeurRemoveBgEngine()).run(arrayOf("no-such-file.png", "out.png"))
        assertEquals(2, code)
    }
}
