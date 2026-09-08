package com.autopi.autopieapp.data

enum class CommandsRepositoryChannel(val preferenceValue: String, val gitRef: String) {
    MAIN("main", "main"),
    DEV("dev", "dev");

    companion object {
        fun fromPreference(value: String): CommandsRepositoryChannel =
            entries.firstOrNull { it.preferenceValue == value } ?: MAIN
    }
}

object CommandsRepositoryUrls {
    private const val RAW_REPOSITORY_URL =
        "https://raw.githubusercontent.com/cryptrr/autopie-commands"

    fun catalog(channel: CommandsRepositoryChannel): String =
        "$RAW_REPOSITORY_URL/${channel.gitRef}/catalog.json"

    fun commandFolder(channel: CommandsRepositoryChannel, commandPath: String): String =
        "$RAW_REPOSITORY_URL/${channel.gitRef}/commands/$commandPath"
}
