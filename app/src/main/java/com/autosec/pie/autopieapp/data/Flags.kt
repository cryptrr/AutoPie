package com.autopi.autopieapp.data

enum class ExtraFlags(val value: String) {
    INTERNAL_CONFIG("--internal-config"),
    PASSWORD("--password"),
    MULTI_FILE_PICKER("--multi-file-picker"),
    FILE_PICKER("--file-picker"),
    MIME_TYPE("--mime-type")
}

enum class ScriptFlags(val value: String) {
    PYTHON("#@PYTHON"),
    INTERACTIVE("#@INTERACTIVE"),
    OPEN_LOGS("#@OPEN_LOGS"),
    SHELL("#@SHELL")
}

// Reserved for flags stored in CommandModel.flags. No command flags are implemented yet.
enum class CommandFlags



fun List<String>?.hasFlag(flag: ExtraFlags): Boolean =
    orEmpty().any { value -> value.substringBefore("=").trim() == flag.value }

fun List<String>?.flagValue(flag: ExtraFlags): String? =
    orEmpty()
        .firstOrNull { value -> value.substringBefore("=").trim() == flag.value }
        ?.substringAfter("=", "")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.takeIf { it.isNotBlank() }
