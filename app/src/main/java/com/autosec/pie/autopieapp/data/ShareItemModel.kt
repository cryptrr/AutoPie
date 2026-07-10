package com.autopi.autopieapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

const val SECRET_VALUE_PLACEHOLDER = "⬤⬤⬤⬤⬤⬤⬤⬤"

data class CommandModel(
    override val id: String = "",
    override val type: CommandType? = null,
    override val name: String = "",
    override val path: String = "",
    override val command: String = "",
    //TODO: Exec - Marked for deletion
    override val exec: String = "",
    override val selectors: List<String>? = emptyList(),
    override val cronInterval: String? = "",
    override val flags: List<String>? = null,
    override val extras: List<CommandExtra>? = null,
    override val multiStage: Boolean? = false,
    override val steps: List<CommandStep> = emptyList(),
    ) : CommandInterface

data class CommandStep(
    val commandId: String? = null,
    val path: String = "",
    val command: String = "",
    val flags: List<String>? = null,
    val extras: List<CommandExtra>? = null
)

class CommandStepResolutionException(val commandId: String) :
    IllegalArgumentException("Command not found: $commandId")

fun CommandModel.resolveCommandSteps(commandsById: Map<String, CommandModel>): CommandModel {
    if (multiStage != true) return this
    return copy(
        steps = steps.map { step ->
            val referencedId = step.commandId?.takeIf(String::isNotBlank) ?: return@map step
            val referencedCommand = commandsById[referencedId]
                ?: throw CommandStepResolutionException(referencedId)
            step.copy(
                path = referencedCommand.path,
                command = referencedCommand.command,
                flags = referencedCommand.flags,
                extras = referencedCommand.extras
            )
        }
    )
}

fun CommandModel.firstStepOrSelf(): CommandModel {
    if (multiStage != true) return this
    val step = steps.firstOrNull() ?: return this
    return copy(
        path = step.path,
        command = step.command,
        flags = (flags.orEmpty() + step.flags.orEmpty())
            .distinct()
            .takeIf { it.isNotEmpty() },
        extras = step.extras
    )
}

fun CommandModel.nextStepOrNull(): CommandModel? {
    if (multiStage != true || steps.size <= 1) return null
    val remainingSteps = steps.drop(1)
    val nextStep = remainingSteps.first()
    return copy(
        path = nextStep.path,
        command = nextStep.command,
        flags = nextStep.flags,
        extras = nextStep.extras,
        steps = remainingSteps
    )
}

fun CommandModel.hasNextStep(): Boolean = multiStage == true && steps.size > 1

fun CommandModel.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true

    return name.contains(query, ignoreCase = true) ||
        command.contains(query, ignoreCase = true) ||
        exec.contains(query, ignoreCase = true) ||
        type.toString().contains(query, ignoreCase = true) ||
        steps.any { step ->
            step.command.contains(query, ignoreCase = true) ||
                step.commandId.orEmpty().contains(query, ignoreCase = true)
        }
}

fun CommandModel.hasUserFacingExtras(): Boolean = extras.orEmpty().any {
    !it.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG)
}

fun CommandModel.hasUnsetRequiredExtras(): Boolean = extras.orEmpty().any {
    !it.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) &&
        !it.isSecretExtra() &&
        it.type == "STRING" &&
        it.default.isEmpty() &&
        it.required
}

data class CommandCreationModel(
    val selectedCommandType: String,
    val commandName: String,
    val directory: String,
    val command: String,
    val exec: String = "",
    val cronInterval: String,
    val selectors: String,
    val isValidCommand: Boolean,
    val commandExtras: List<CommandExtra>
)

interface CommandInterface {
    val id: String
    val type: CommandType?
    val name: String
    val path: String
    val command: String
    val exec: String?
    val selectors: List<String>?
    val cronInterval: String?
    val multiStage: Boolean?
    val steps: List<CommandStep>
    val flags: List<String>?
    val extras: List<CommandExtra>?
}

data class CommandExtra(
    val id: String,
    val name: String = "",
    val type: String = "",
    val default: String = "",
    val description: String = "",
    val defaultBoolean: Boolean = true,
    val required: Boolean = true,
    val flags: List<String>? = null,
    val visibleWhen: ExtraVisibilityRule? = null,
    @field:JsonAdapter(SelectableOptionsAdapter::class)
    val selectableOptions: Map<String, String> = emptyMap()
)

fun CommandExtra.isSecretExtra(): Boolean =
    flags.hasFlag(ExtraFlags.PASSWORD) || flags.hasFlag(ExtraFlags.SECRET)

fun CommandExtra.secretKey(commandId: String): String = "$commandId@$name"

fun CommandExtra.withoutStoredSecretDefault(): CommandExtra =
    if (isSecretExtra()) copy(default = "") else this

class SelectableOptionsAdapter :
    JsonDeserializer<Map<String, String>>,
    JsonSerializer<Map<String, String>> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Map<String, String> = when {
        json.isJsonObject -> json.asJsonObject.entrySet().associateTo(linkedMapOf()) { (label, value) ->
            label to value.asString
        }

        json.isJsonArray -> json.asJsonArray.associateTo(linkedMapOf()) { option ->
            option.asString to option.asString
        }

        else -> emptyMap()
    }

    override fun serialize(
        src: Map<String, String>,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement = context.serialize(src)
}

data class ExtraVisibilityRule(
    val all: List<ExtraVisibilityRule>? = null,
    val or: List<ExtraVisibilityRule>? = null,
    val any: List<ExtraVisibilityRule>? = null,
    val not: ExtraVisibilityRule? = null,
    val extraId: String? = null,
    val equals: JsonElement? = null,
    val notEquals: JsonElement? = null,
    val startsWith: String? = null,
    val endsWith: String? = null,
    val contains: String? = null,
    val matches: String? = null,
    val gt: JsonElement? = null,
    val gte: JsonElement? = null,
    val lt: JsonElement? = null,
    val lte: JsonElement? = null,
    val oneOf: List<JsonElement>? = null,
    val exists: Boolean? = null,
    val isEmpty: Boolean? = null
)

data class ProcessResult(
    val key: String,
    val processId: Int,
    val success: Boolean,
    val output: String,
    val partial: Boolean = false,
)

data class CommandResult(
    val key: String,
    val processId: Int,
    val success: Boolean,
    val output: String,
    val jobType: JobType,
    val jobKey: String,
    val partial: Boolean = false,
)

data class InputParsedData(
    val id: Int = (1000..9999).random(),
    val name: String = "",
    val type: String = "",
    val value: String = "",
    val description: String = "",
    val defaultBoolean: Boolean = true,
    val selectableOptions: List<String> = emptyList()
)

data class CommandExtraInput(
    val name: String,
    val default: String,
    val value: String,
    val type: String,
    val defaultBoolean: Boolean,
    val id: String,
    val description: String
)

enum class CommandType {
    SHARE,
    FILE_OBSERVER,
    CRON
}

enum class JobType {
    URL,
    URLS,
    FILE,
    FILES,
    DIRECTORY,
    TEXT,
    STANDALONE
}

data class ExecAndCommand(
    val type: ExecType,
    val execPath: String,
    val command: String

)

enum class ExecType {
    ABSOLUTE_PATH,
    AUTOPIE_PACKAGE,
    SHELL_INSTALLED
}

data class InstalledPackageModel(
    val name: String,
    val path: String,
    val version: String,
    val hasUpdate: Boolean,
)
