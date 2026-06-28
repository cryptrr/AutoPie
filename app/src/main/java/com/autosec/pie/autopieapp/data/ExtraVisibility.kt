package com.autopi.autopieapp.data

import com.google.gson.JsonElement
import java.math.BigDecimal

fun ExtraVisibilityRule.matchesExtraValues(valuesById: Map<String, String>): Boolean {
    all?.let { rules ->
        if (!rules.all { it.matchesExtraValues(valuesById) }) return false
    }

    or?.let { rules ->
        if (!rules.any { it.matchesExtraValues(valuesById) }) return false
    }

    any?.let { rules ->
        if (!rules.any { it.matchesExtraValues(valuesById) }) return false
    }

    not?.let { rule ->
        if (rule.matchesExtraValues(valuesById)) return false
    }

    val referencedExtraId = extraId ?: return true
    val currentValue = valuesById[referencedExtraId]

    exists?.let { shouldExist ->
        if ((currentValue != null) != shouldExist) return false
    }

    if (currentValue == null) return exists == false

    equals?.let { expected ->
        if (!valuesEqual(currentValue, expected)) return false
    }
    notEquals?.let { expected ->
        if (valuesEqual(currentValue, expected)) return false
    }
    startsWith?.let { prefix ->
        if (!currentValue.startsWith(prefix)) return false
    }
    endsWith?.let { suffix ->
        if (!currentValue.endsWith(suffix)) return false
    }
    contains?.let { content ->
        if (!currentValue.contains(content)) return false
    }
    matches?.let { pattern ->
        if (runCatching { Regex(pattern).containsMatchIn(currentValue) }.getOrDefault(false).not()) {
            return false
        }
    }
    gt?.let { expected ->
        if (!compareNumbers(currentValue, expected) { current, target -> current > target }) return false
    }
    gte?.let { expected ->
        if (!compareNumbers(currentValue, expected) { current, target -> current >= target }) return false
    }
    lt?.let { expected ->
        if (!compareNumbers(currentValue, expected) { current, target -> current < target }) return false
    }
    lte?.let { expected ->
        if (!compareNumbers(currentValue, expected) { current, target -> current <= target }) return false
    }
    oneOf?.let { expectedValues ->
        if (expectedValues.none { valuesEqual(currentValue, it) }) return false
    }
    isEmpty?.let { shouldBeEmpty ->
        if (currentValue.isEmpty() != shouldBeEmpty) return false
    }

    return true
}

private fun valuesEqual(currentValue: String, expected: JsonElement): Boolean {
    if (expected.isJsonNull) return false
    val primitive = expected.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive ?: return false

    if (primitive.isBoolean) {
        return currentValue.toBooleanStrictOrNull() == primitive.asBoolean
    }

    if (primitive.isNumber) {
        val currentNumber = currentValue.toBigDecimalOrNull() ?: return false
        val expectedNumber = primitive.asString.toBigDecimalOrNull() ?: return false
        return currentNumber.compareTo(expectedNumber) == 0
    }

    return currentValue == primitive.asString
}

private fun compareNumbers(
    currentValue: String,
    expected: JsonElement,
    comparison: (BigDecimal, BigDecimal) -> Boolean
): Boolean {
    if (!expected.isJsonPrimitive || !expected.asJsonPrimitive.isNumber) return false
    val currentNumber = currentValue.toBigDecimalOrNull() ?: return false
    val expectedNumber = expected.asString.toBigDecimalOrNull() ?: return false
    return comparison(currentNumber, expectedNumber)
}
