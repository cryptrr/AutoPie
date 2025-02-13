package com.autosec.pie.autopieapp.data

data class CommandModelList(
    val items: Map<String, CommandModel>
)

data class ShareItemModel(
    val name: String,
    val path: String,
    val command: String,
    val exec: String,
    val deleteSourceFile: Boolean? = false,
    val forUrl: Boolean? = true,
    val forSingleFile: Boolean? = true,
    val forMultipleFiles: Boolean? = true
)

data class CommandModel(
    override val type: CommandType,
    override val name: String,
    override val path: String,
    override val command: String,
    override val exec: String,
    override val deleteSourceFile: Boolean? = false,
    val extrasRequired : Boolean? = false,
    override val extras: List<CommandExtra>? = null
) : CommandInterface

data class CommandCreationModel(
    val selectedCommandType: String,
    val commandName: String,
    val directory: String,
    val command: String,
    val exec: String,
    val deleteSourceFile: Boolean? = false,
    val cronInterval: String,
    val selectors: String,
    val extrasRequired : Boolean? = false,
    val isValidCommand: Boolean,
    val commandExtras: List<CommandExtra>
)

interface CommandInterface {
    val type: CommandType
    val name: String
    val path: String
    val command: String
    val exec: String
    val deleteSourceFile: Boolean?
    val extras: List<CommandExtra>?
}

data class CommandExtra(
    val id: String,
    val name: String = "",
    val type: String = "",
    val default: String = "",
    val description: String = "",
    val defaultBoolean: Boolean = true,
    val selectableOptions: List<String> = emptyList()
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

enum class CommandType{
    SHARE,
    FILE_OBSERVER,
    CRON
}

data class InstalledPackageModel(
    val name: String,
    val path: String,
    val version: String,
    val hasUpdate: Boolean,
)