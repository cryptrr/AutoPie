package com.autosec.pie.data

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
    val type: CommandType,
    val name: String,
    val path: String,
    val command: String,
    val exec: String,
    val deleteSourceFile: Boolean? = false,
    val extras: List<CommandExtra>? = null
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