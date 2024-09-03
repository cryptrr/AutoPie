package com.autosec.pie.data

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
)

data class CommandExtra(
    val id: String,
    val name: String,
    val type: String,
    val default: String,
    val description: String,
    val defaultBoolean: Boolean,
    val selectableOptions: List<String>
)

enum class CommandType{
    SHARE,
    FILE_OBSERVER
}

data class InstalledPackageModel(
    val name: String,
    val path: String,
    val version: String,
    val hasUpdate: Boolean,
)