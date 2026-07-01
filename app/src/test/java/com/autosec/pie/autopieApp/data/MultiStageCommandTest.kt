package com.autopi.autopieapp.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun multiStageDoesNotRequireSheetWithoutUnsetRequiredExtras() {
        val optionalExtra = firstExtra.copy(required = false, default = "")
        val populatedRequiredExtra = firstExtra.copy(required = true, default = "ready")
        val unsetRequiredExtra = firstExtra.copy(required = true, default = "")

        assertFalse(CommandModel(multiStage = true).hasUnsetRequiredExtras())
        assertFalse(CommandModel(multiStage = true, extras = listOf(optionalExtra)).hasUnsetRequiredExtras())
        assertFalse(CommandModel(multiStage = true, extras = listOf(populatedRequiredExtra)).hasUnsetRequiredExtras())
        assertTrue(CommandModel(multiStage = true, extras = listOf(unsetRequiredExtra)).hasUnsetRequiredExtras())
    }

    @Test
    fun commandIdStepsResolveFromExistingCommands() {
        val referenced = CommandModel(
            id = "autopie/yt-dlp-get-direct-url",
            path = "downloads",
            command = "export URL=resolved",
            flags = listOf("--referenced-command-flag"),
            extras = listOf(firstExtra)
        )
        val pipeline = Gson().fromJson(
            """
                {
                  "id": "autopie/multi-pipeline-demo",
                  "multiStage": true,
                  "flags": ["--show-loading-screen"],
                  "steps": [
                    {"commandId": "autopie/yt-dlp-get-direct-url"}
                  ]
                }
            """.trimIndent(),
            CommandModel::class.java
        )

        val resolved = pipeline.resolveCommandSteps(mapOf(referenced.id to referenced))
        val step = resolved.steps.single()

        assertEquals("autopie/multi-pipeline-demo", resolved.id)
        assertEquals(referenced.id, step.commandId)
        assertEquals(referenced.path, step.path)
        assertEquals(referenced.command, step.command)
        assertEquals(referenced.flags, step.flags)
        assertEquals(referenced.extras, step.extras)
        assertEquals(
            listOf(CommandFlags.SHOW_LOADING_SCREEN.value, "--referenced-command-flag"),
            resolved.firstStepOrSelf().flags
        )
    }

    @Test
    fun pipelineFlagsApplyToFirstStepWithoutLeakingIntoLaterSteps() {
        val pipeline = CommandModel(
            multiStage = true,
            flags = listOf(CommandFlags.SHOW_LOADING_SCREEN.value),
            steps = listOf(
                CommandStep(command = "echo first", flags = listOf("--first-step")),
                CommandStep(command = "echo second")
            )
        )

        val first = pipeline.firstStepOrSelf()
        val second = first.nextStepOrNull()

        assertEquals(
            listOf(CommandFlags.SHOW_LOADING_SCREEN.value, "--first-step"),
            first.flags
        )
        assertTrue(second?.flags.isNullOrEmpty())
    }

    @Test
    fun missingCommandIdFailsResolution() {
        val pipeline = CommandModel(
            multiStage = true,
            steps = listOf(CommandStep(commandId = "autopie/missing"))
        )

        try {
            pipeline.resolveCommandSteps(emptyMap())
            fail("Expected missing command resolution to fail")
        } catch (exception: CommandStepResolutionException) {
            assertEquals("autopie/missing", exception.commandId)
        }
    }
}
