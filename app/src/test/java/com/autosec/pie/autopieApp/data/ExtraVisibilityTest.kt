package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.ExtraVisibilityRule
import com.autopi.autopieapp.data.matchesExtraValues
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraVisibilityTest {
    private val gson = Gson()

    @Test
    fun `all evaluates current string and boolean values by extra id`() {
        val rule = parseRule(
            """
            {
              "all": [
                { "extraId": "output_type", "equals": "pdf" },
                { "extraId": "ocr", "equals": true },
                { "extraId": "name", "startsWith": "" }
              ]
            }
            """.trimIndent()
        )

        assertTrue(
            rule.matchesExtraValues(
                mapOf("output_type" to "pdf", "ocr" to "true", "name" to "report")
            )
        )
        assertFalse(
            rule.matchesExtraValues(
                mapOf("output_type" to "pdf", "ocr" to "false", "name" to "report")
            )
        )
    }

    @Test
    fun `or and numeric comparisons are supported`() {
        val rule = parseRule(
            """
            {
              "or": [
                { "extraId": "quality", "gte": 90 },
                { "extraId": "mode", "oneOf": ["auto", "fast"] }
              ]
            }
            """.trimIndent()
        )

        assertTrue(rule.matchesExtraValues(mapOf("quality" to "95", "mode" to "slow")))
        assertTrue(rule.matchesExtraValues(mapOf("quality" to "20", "mode" to "auto")))
        assertFalse(rule.matchesExtraValues(mapOf("quality" to "20", "mode" to "slow")))
    }

    @Test
    fun `not and missing value checks are supported`() {
        val rule = parseRule(
            """
            {
              "all": [
                { "not": { "extraId": "advanced", "equals": true } },
                { "extraId": "removed", "exists": false }
              ]
            }
            """.trimIndent()
        )

        assertTrue(rule.matchesExtraValues(mapOf("advanced" to "false")))
        assertFalse(rule.matchesExtraValues(mapOf("advanced" to "true")))
    }

    private fun parseRule(json: String): ExtraVisibilityRule =
        gson.fromJson(json, ExtraVisibilityRule::class.java)
}
