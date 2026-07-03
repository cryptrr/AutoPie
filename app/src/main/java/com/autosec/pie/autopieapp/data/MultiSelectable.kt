package com.autopi.autopieapp.data

fun resolveMultiSelectableDefaults(
    defaultValue: String,
    options: Map<String, String>,
): List<String> {
    val resolvedDefaults = defaultValue
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { option -> options[option] ?: option }
        .filter { option -> option in options.values }
        .distinct()
        .toList()

    return resolvedDefaults.ifEmpty {
        options.values.firstOrNull()?.let(::listOf).orEmpty()
    }
}

fun Iterable<String>.toMultiSelectableValue(): String = joinToString(separator = "\n")
