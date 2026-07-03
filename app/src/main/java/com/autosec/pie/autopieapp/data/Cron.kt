package com.autopi.autopieapp.data

data class CronCommandModel(
    override val id: String= "",
    override val cronInterval: String,
    override val command: String,
    override val type: CommandType,
    override val exec: String,
    override val name: String,
    override var path: String,
    override val flags: List<String>? = null,
    override val extras: List<CommandExtra>? = null,
    override val selectors: List<String> = emptyList(),
    override val multiStage: Boolean? = false,
    override val steps: List<CommandStep> = emptyList()
): CommandInterface
