package com.autopi.autopieapp.data

fun List<String>?.hasFlag(name: String): Boolean =
    orEmpty().any { flag -> flag.substringBefore("=").trim() == name }

fun List<String>?.flagValue(name: String): String? =
    orEmpty()
        .firstOrNull { flag -> flag.substringBefore("=").trim() == name }
        ?.substringAfter("=", "")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.takeIf { it.isNotBlank() }
