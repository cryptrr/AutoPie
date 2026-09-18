package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandStep
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.firstStepOrSelf
import com.autopi.autopieapp.data.nextStepOrNull
import com.autopi.autopieapp.data.services.InternalConfigService
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreCommandExtraInputsTest {

    private val internalExtra = CommandExtra(
        id = "token",
        name = "TOKEN",
        type = "STRING",
        flags = listOf(ExtraFlags.INTERNAL_CONFIG.value)
    )

    @Test
    fun updatesOnlyFallbackIdentifiedMultistageStep() {
        val store = memoryInternalConfigService()
        val command = pipeline().firstStepOrSelf().nextStepOrNull()!!

        StoreCommandExtraInputs(store)(command, listOf(input("new-value")))

        assertEquals("Pipeline.1", command.id)
        assertEquals("new-value", store.get("Pipeline.1", "token"))
        assertEquals(null, store.get("Pipeline.0", "token"))
    }

    @Test
    fun explicitStepIdIsNamespacedWithoutChangingStoredId() {
        val store = memoryInternalConfigService()
        val command = pipeline(secondStepId = "publish").firstStepOrSelf().nextStepOrNull()!!

        StoreCommandExtraInputs(store)(command, listOf(input("new-value")))

        assertEquals("Pipeline.publish", command.id)
        assertEquals("new-value", store.get("Pipeline.publish", "token"))
    }

    @Test
    fun selectableInternalConfigValueIsPersistedAsDefault() {
        assertSelectableInternalConfigValueIsPersisted("SELECTABLE")
    }

    @Test
    fun flatSelectableInternalConfigValueIsPersistedAsDefault() {
        assertSelectableInternalConfigValueIsPersisted("SELECTABLE_FLAT")
    }

    @Test
    fun multiSelectableInternalConfigValueIsPersistedAsDefault() {
        assertStringDefaultIsPersisted("MULTI_SELECTABLE", "first\nsecond")
    }

    @Test
    fun emptyMultiSelectableInternalConfigValueIsPersistedAsDefault() {
        assertStringDefaultIsPersisted("MULTI_SELECTABLE_FLAT", "")
    }

    @Test
    fun booleanInternalConfigValueIsPersistedAsBooleanDefault() {
        assertBooleanDefaultIsPersisted("BOOLEAN", value = "false")
    }

    @Test
    fun enabledFlagInternalConfigValueIsPersistedAsBooleanDefault() {
        assertBooleanDefaultIsPersisted("FLAG", value = "--force")
    }

    @Test
    fun disabledFlagInternalConfigValueIsPersistedAsBooleanDefault() {
        assertBooleanDefaultIsPersisted("FLAG", value = "")
    }

    @Test
    fun valueMatchingJsonDefaultIsStillPersisted() {
        val store = memoryInternalConfigService()
        val extra = internalExtra.copy(default = "same")

        StoreCommandExtraInputs(store)(
            singleExtraCommand(extra),
            listOf(input(extra, "same"))
        )

        assertEquals("same", store.get("Single", "token"))
    }

    private fun assertSelectableInternalConfigValueIsPersisted(type: String) {
        val extra = internalExtra.copy(
            type = type,
            default = "first",
            selectableOptions = linkedMapOf("First" to "first", "Second" to "second")
        )
        val store = memoryInternalConfigService()
        val command = CommandModel(
            id = "Selector",
            name = "Selector",
            type = CommandType.SHARE,
            extras = listOf(extra)
        )
        val input = CommandExtraInput(
            name = extra.name,
            default = extra.default,
            value = "second",
            type = extra.type,
            defaultBoolean = extra.defaultBoolean,
            id = extra.id,
            description = extra.description
        )

        StoreCommandExtraInputs(store)(command, listOf(input))

        assertEquals("second", store.get("Selector", "token"))
    }

    private fun assertStringDefaultIsPersisted(type: String, value: String) {
        val extra = internalExtra.copy(type = type, default = "old")
        val store = memoryInternalConfigService()
        val command = singleExtraCommand(extra)

        StoreCommandExtraInputs(store)(command, listOf(input(extra, value)))

        assertEquals(value, store.get("Single", "token"))
    }

    private fun assertBooleanDefaultIsPersisted(type: String, value: String) {
        val extra = internalExtra.copy(type = type, default = "--force")
        val store = memoryInternalConfigService()
        val command = singleExtraCommand(extra)

        StoreCommandExtraInputs(store)(command, listOf(input(extra, value)))

        assertEquals(value, store.get("Single", "token"))
    }

    private fun singleExtraCommand(extra: CommandExtra) = CommandModel(
        id = "Single",
        name = "Single",
        type = CommandType.SHARE,
        extras = listOf(extra)
    )

    private fun pipeline(secondStepId: String = "") = CommandModel(
        id = "Pipeline",
        name = "Pipeline",
        type = CommandType.SHARE,
        multiStage = true,
        steps = listOf(
            CommandStep(command = "first", extras = listOf(internalExtra.copy(default = "first"))),
            CommandStep(id = secondStepId, command = "second", extras = listOf(internalExtra.copy(default = "second")))
        )
    )

    private fun input(value: String) = CommandExtraInput(
        name = internalExtra.name,
        default = "second",
        value = value,
        type = internalExtra.type,
        defaultBoolean = true,
        id = internalExtra.id,
        description = ""
    )

    private fun input(extra: CommandExtra, value: String) = CommandExtraInput(
        name = extra.name,
        default = extra.default,
        value = value,
        type = extra.type,
        defaultBoolean = extra.defaultBoolean,
        id = extra.id,
        description = extra.description
    )

    private fun memoryInternalConfigService(): InternalConfigService {
        val values = mutableMapOf<Pair<String, String>, String>()
        return mockk {
            every { get(any(), any()) } answers {
                values[firstArg<String>() to secondArg<String>()]
            }
            every { set(any(), any(), any()) } answers {
                values[firstArg<String>() to secondArg<String>()] = thirdArg()
                true
            }
        }
    }
}
