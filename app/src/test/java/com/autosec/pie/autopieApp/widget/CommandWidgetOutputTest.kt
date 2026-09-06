package com.autopi.autopieapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandWidgetOutputTest {
    @Test
    fun `number output selects number presentation`() {
        assertEquals(DisplayOutput.Number("42.5"), parseDisplayOutput("42.5"))
    }

    @Test
    fun `JSON string array selects list presentation`() {
        assertEquals(
            DisplayOutput.Items(listOf("Alpha", "Beta")),
            parseDisplayOutput("[\"Alpha\",\"Beta\"]")
        )
    }

    @Test
    fun `plain output falls back to text presentation`() {
        assertEquals(
            DisplayOutput.PlainText("Build completed"),
            parseDisplayOutput("Build completed")
        )
    }

    @Test
    fun `unset output selects empty presentation`() {
        assertTrue(parseDisplayOutput(null) is DisplayOutput.Empty)
    }

    @Test
    fun `bounding a numeric-looking string preserves its text type`() {
        val bounded = boundWidgetOutput("\"42\"")

        assertEquals(DisplayOutput.PlainText("42"), parseDisplayOutput(bounded))
    }
}
