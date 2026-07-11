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
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.SecretsService
import com.autopi.autopieapp.data.withoutStoredSecretDefault
import com.google.gson.GsonBuilder

data class StoreCommandExtraInputsResult(
    val command: CommandModel,
    val updatedConfig: Boolean
)

class StoreCommandExtraInputs(
    private val jsonService: JsonService,
    private val secretsService: SecretsService? = null
) {
    operator fun invoke(
        command: CommandModel,
        commandExtraInputs: List<CommandExtraInput>
    ): StoreCommandExtraInputsResult {
        val extras = command.extras.orEmpty()
        if (extras.isEmpty() || commandExtraInputs.isEmpty()) {
            return StoreCommandExtraInputsResult(command, updatedConfig = false)
        }

        val inputsById = commandExtraInputs.associateBy { it.id }
        val inputsByName = commandExtraInputs.associateBy { it.name }
        var changedConfigExtra = false

        val updatedExtras = extras.map { extra ->
            val input = inputsById[extra.id] ?: inputsByName[extra.name] ?: return@map extra
            val value = input.value.takeUnless { it == SECRET_VALUE_PLACEHOLDER }.orEmpty()

            if (extra.isSecretExtra()) {
                storeSecret(command, extra, value)
                extra
            } else if (extra.shouldStoreInternalConfigDefault(value) && extra.default != value) {
                changedConfigExtra = true
                extra.copy(default = value)
            } else {
                extra
            }
        }

        val updatedConfig = if (changedConfigExtra) {
            storeUpdatedConfigExtras(command, updatedExtras)
        } else {
            false
        }

        return StoreCommandExtraInputsResult(
            command = command.copy(extras = updatedExtras),
            updatedConfig = updatedConfig
        )
    }

    private fun storeSecret(command: CommandModel, extra: CommandExtra, value: String) {
        if (value.isBlank()) return
        val service = secretsService ?: return
        val commandId = command.id.ifBlank { command.name }
        service.set(extra.secretKey(commandId), value)
    }

    private fun CommandExtra.shouldStoreInternalConfigDefault(value: String): Boolean =
        flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) &&
            type == "STRING" &&
            value.isNotBlank()

    private fun storeUpdatedConfigExtras(command: CommandModel, extras: List<CommandExtra>): Boolean {
        val commandKey = command.name.ifBlank { command.id }
        if (commandKey.isBlank()) return false

        val shareCommands = jsonService.readSharesConfig()
        val observerCommands = jsonService.readObserversConfig()
        val cronCommands = jsonService.readCronConfig()

        val target = when (command.type) {
            CommandType.SHARE -> shareCommands?.let { it to jsonService::writeSharesConfig }
            CommandType.FILE_OBSERVER -> observerCommands?.let { it to jsonService::writeObserversConfig }
            CommandType.CRON -> cronCommands?.let { it to jsonService::writeCronConfig }
            null -> listOfNotNull(
                shareCommands?.let { it to jsonService::writeSharesConfig },
                observerCommands?.let { it to jsonService::writeObserversConfig },
                cronCommands?.let { it to jsonService::writeCronConfig }
            ).firstOrNull { (commands, _) -> commands.has(commandKey) }
        } ?: return false

        val (commands, writeConfig) = target
        val commandObject = commands.getAsJsonObject(commandKey) ?: return false
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        commandObject.add("extras", gson.toJsonTree(extras.map { it.withoutStoredSecretDefault() }))
        writeConfig(gson.toJson(commands))
        return true
    }
}
