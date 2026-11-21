package com.autopi.autopieapp.domain.model

data class CloudCommandCompatibility(
    val autopieMinVersion: String? = null,
    val autopieMaxVersion: String? = null
) {
    /** Bounds are inclusive. A wildcard bound covers its entire version family. */
    fun isCompatibleWith(version: String): Boolean? {
        val current = CompatibilityVersion.parse(version) ?: return null
        val minimum = autopieMinVersion?.let { compareBound(current, it) ?: return null }
        val maximum = autopieMaxVersion?.let { compareBound(current, it) ?: return null }
        return (minimum == null || minimum >= 0) && (maximum == null || maximum <= 0)
    }
}

private fun compareBound(current: CompatibilityVersion, bound: String): Int? {
    val parts = bound.trim().removePrefix("v").split('.')
    val wildcardIndex = parts.indexOfFirst { it.equals("x", ignoreCase = true) || it == "*" }
    if (wildcardIndex >= 0) {
        if (parts.size > 3 || parts.drop(wildcardIndex).any { it != "*" && !it.equals("x", true) }) return null
        val prefix = parts.take(wildcardIndex).map { it.toIntOrNull()?.takeIf { number -> number >= 0 } ?: return null }
        prefix.forEachIndexed { index, number ->
            val comparison = current.numbers[index].compareTo(number)
            if (comparison != 0) return comparison
        }
        return 0
    }
    return CompatibilityVersion.parse(bound)?.let(current::compareTo)
}

private data class CompatibilityVersion(val numbers: List<Int>, val prerelease: List<String>) : Comparable<CompatibilityVersion> {
    override fun compareTo(other: CompatibilityVersion): Int {
        numbers.zip(other.numbers).forEach { (left, right) ->
            if (left != right) return left.compareTo(right)
        }
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        prerelease.zip(other.prerelease).forEach { (left, right) ->
            val leftNumber = left.toBigIntegerOrNull()
            val rightNumber = right.toBigIntegerOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""")

        fun parse(value: String): CompatibilityVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val numbers = (1..3).map { index ->
                match.groupValues[index].ifEmpty { "0" }.toIntOrNull() ?: return null
            }
            val prerelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.').orEmpty()
            return CompatibilityVersion(numbers, prerelease)
        }
    }
}
