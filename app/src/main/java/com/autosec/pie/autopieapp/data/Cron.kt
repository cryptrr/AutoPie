package com.autopi.autopieapp.data

data class CronCommandModel(
    override val cronInterval: String,
    override val command: String,
    override val type: CommandType,
    override val exec: String,
    override val name: String,
    override var path: String,
    override val flags: List<String>? = null,
    override val extras: List<CommandExtra>? = null,
    override val selectors: List<String> = emptyList()
): CommandInterface
