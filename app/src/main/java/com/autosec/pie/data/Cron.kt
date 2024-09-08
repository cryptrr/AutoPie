package com.autosec.pie.data

data class CronCommandModel(
    val cronInterval: String,
    override val command: String,
    override val type: CommandType,
    override val exec: String,
    override val name: String,
    override val path: String,
    override val deleteSourceFile: Boolean? = false,
    override val extras: List<CommandExtra>? = null
): CommandInterface