package com.autopi.autopieapp.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiStageCommandTest {

    private val firstExtra = CommandExtra(id = "first", name = "FIRST", type = "STRING")
    private val secondExtra = CommandExtra(id = "second", name = "SECOND", type = "STRING")

    private val command = CommandModel(
        name = "Multi stage",
        multiStage = true,
        steps = listOf(
            CommandStep(path = "one", command = "export RESULT=ready", extras = listOf(firstExtra)),
            CommandStep(path = "two", command = "printf '%s' \"\$RESULT\"", extras = listOf(secondExtra))
        )
    )

    @Test
    fun firstStepOverridesExecutableFieldsAndKeepsRemainingSteps() {
        val first = command.firstStepOrSelf()

        assertEquals("one", first.path)
        assertEquals("export RESULT=ready", first.command)
        assertEquals(listOf(firstExtra), first.extras)
        assertEquals(2, first.steps.size)
        assertTrue(first.hasNextStep())
    }

    @Test
    fun nextStepDropsCompletedStepAndEndsAfterFinalStep() {
        val second = command.firstStepOrSelf().nextStepOrNull()

        assertNotNull(second)
        assertEquals("two", second?.path)
        assertEquals(listOf(secondExtra), second?.extras)
        assertEquals(1, second?.steps?.size)
        assertFalse(second!!.hasNextStep())
        assertNull(second.nextStepOrNull())
    }

    @Test
    fun gsonAcceptsMultiStageCommandWithoutTopLevelPathOrCommand() {
        val parsed = Gson().fromJson(
            """
                {
                  "name": "Multi stage",
                  "multiStage": true,
                  "steps": [
                    {"path": "one", "command": "echo first", "extras": []}
                  ]
                }
            """.trimIndent(),
            CommandModel::class.java
        )

        val first = parsed.firstStepOrSelf()
        assertEquals("one", first.path)
        assertEquals("echo first", first.command)
    }

    @Test
    fun onlyStepsWithVisibleExtrasNeedUserInput() {
        val noExtras = CommandModel(extras = null)
        val internalOnly = CommandModel(
            extras = listOf(
                CommandExtra(
                    id = "internal",
                    name = "INTERNAL",
                    type = "STRING",
                    flags = listOf(ExtraFlags.INTERNAL_CONFIG.value)
                )
            )
        )
        val visible = CommandModel(extras = listOf(firstExtra))

        assertFalse(noExtras.hasUserFacingExtras())
        assertFalse(internalOnly.hasUserFacingExtras())
        assertTrue(visible.hasUserFacingExtras())
    }
}
