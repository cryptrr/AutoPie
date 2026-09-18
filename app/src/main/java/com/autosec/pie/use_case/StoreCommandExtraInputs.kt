package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.SECRET_VALUE_PLACEHOLDER
import com.autopi.autopieapp.data.hasFlag
import com.autopi.autopieapp.data.isSecretExtra
import com.autopi.autopieapp.data.secretKey
import com.autopi.autopieapp.data.services.InternalConfigService
import com.autopi.autopieapp.data.services.SecretsService

class StoreCommandExtraInputs(
    private val internalConfigService: InternalConfigService,
    private val secretsService: SecretsService? = null
) {
    operator fun invoke(
        command: CommandModel,
        commandExtraInputs: List<CommandExtraInput>
    ) {
        val extras = command.extras.orEmpty()
        if (extras.isEmpty() || commandExtraInputs.isEmpty()) {
            return
        }

        val inputsById = commandExtraInputs.associateBy { it.id }
        val inputsByName = commandExtraInputs.associateBy { it.name }
        extras.forEach { extra ->
            val input = inputsById[extra.id] ?: inputsByName[extra.name] ?: return@forEach
            val value = input.value.takeUnless { it == SECRET_VALUE_PLACEHOLDER }.orEmpty()

            if (extra.isSecretExtra()) {
                storeSecret(command, extra, value)
            } else {
                val isPersistableInternalConfig = extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) &&
                    extra.acceptsInternalConfigValue(value)
                if (isPersistableInternalConfig) {
                    internalConfigService.set(command.id, extra.id, value)
                }
            }
        }
    }

    private fun storeSecret(command: CommandModel, extra: CommandExtra, value: String) {
        if (value.isBlank()) return
        val service = secretsService ?: return
        val commandId = command.id.ifBlank { command.name }
        service.set(extra.secretKey(commandId), value)
    }

    private fun CommandExtra.acceptsInternalConfigValue(value: String): Boolean = when (type) {
        "BOOLEAN" -> value.trim().lowercase().toBooleanStrictOrNull() != null
        "FLAG", "MULTI_SELECTABLE", "MULTI_SELECTABLE_FLAT" -> true
        "STRING", "SELECTABLE", "SELECTABLE_FLAT" -> value.isNotBlank()
        else -> false
    }

}
