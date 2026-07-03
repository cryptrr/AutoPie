package com.autopi.autopieapp.data.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessManagerScriptTest {

    @Test
    fun multiStagePreambleConsumesOutputIntoInputBeforeTracing() {
        assertEquals(
            """
                if [ "${'$'}{OUTPUT+x}" = x ]; then
                    export INPUT="${'$'}OUTPUT"
                    unset OUTPUT
                fi
                set -x
            """.trimIndent() + "\n",
            commandScriptPreamble(multiStage = true)
        )
    }

    @Test
    fun regularCommandPreambleOnlyEnablesTracing() {
        assertEquals("set -x\n", commandScriptPreamble(multiStage = false))
    }
}
