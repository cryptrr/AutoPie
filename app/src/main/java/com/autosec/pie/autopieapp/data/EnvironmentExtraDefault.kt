package com.autopi.autopieapp.data

private val environmentReference = Regex("\\$\\$([A-Za-z_][A-Za-z0-9_]*)")

internal fun String.environmentVariableReferenceOrNull(): String? =
    environmentReference.matchEntire(this)?.groupValues?.get(1)

internal suspend fun resolveEnvironmentBackedValue(
    configuredValue: String,
    resolveVariable: suspend (String) -> String?
): String {
    val variableName = configuredValue.environmentVariableReferenceOrNull()
        ?: return configuredValue
    return resolveVariable(variableName) ?: configuredValue
}
