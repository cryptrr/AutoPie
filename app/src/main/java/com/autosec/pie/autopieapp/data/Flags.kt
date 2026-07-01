package com.autopi.autopieapp.data

enum class ExtraFlags(val value: String) {
    INTERNAL_CONFIG("--internal-config"),
    PASSWORD("--password"),
    MULTI_FILE_PICKER("--multi-file-picker"),
    FILE_PICKER("--file-picker"),
    MIME_TYPE("--mime-type"),
    INT("--int"),
    SMALL("--small"),
    LARGE("--large")
}

enum class ScriptFlags(val value: String) {
    PYTHON("#@PYTHON"),
    INTERACTIVE("#@INTERACTIVE"),
    OPEN_LOGS("#@OPEN_LOGS"),
    SHELL("#@SHELL")
}

enum class CommandFlags(val value: String) {
    SHOW_LOADING_SCREEN("--show-loading-screen")
}



fun List<String>?.hasFlag(flag: ExtraFlags): Boolean =
    orEmpty().any { value -> value.substringBefore("=").trim() == flag.value }

fun List<String>?.hasFlag(flag: CommandFlags): Boolean =
    orEmpty().any { value -> value.substringBefore("=").trim() == flag.value }

fun List<String>?.flagValue(flag: ExtraFlags): String? =
    orEmpty()
        .firstOrNull { value -> value.substringBefore("=").trim() == flag.value }
        ?.substringAfter("=", "")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.takeIf { it.isNotBlank() }
