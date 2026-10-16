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
    fun `JSON object selects pretty printed JSON presentation`() {
        assertEquals(
            DisplayOutput.Json("{\n  \"name\": \"AutoPie\",\n  \"stars\": 42\n}"),
            parseDisplayOutput("{\"name\":\"AutoPie\",\"stars\":42}")
        )
    }

    @Test
    fun `non string JSON array selects JSON presentation`() {
        assertEquals(
            DisplayOutput.Json("[\n  1,\n  true,\n  null\n]"),
            parseDisplayOutput("[1,true,null]")
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
    @Test
    fun `PNG and bitmap paths select image presentation`() {
        listOf("/sdcard/Pictures/result.png", "/tmp/chart.BMP", "file:///tmp/chart.png",
            "content://images/42").forEach { source ->
            assertEquals(DisplayOutput.Image(source), parseDisplayOutput(source))
            assertEquals(DisplayOutput.Image(source), parseDisplayOutput(com.google.gson.Gson().toJson(source)))
        }
    }

    @Test
    fun `typed image supports viewer aliases and bitmap type`() {
        assertEquals(
            DisplayOutput.Image("/tmp/chart.png", "Chart"),
            parseDisplayOutput("""{"type":"image","path":"/tmp/chart.png","caption":"Chart"}""")
        )
        assertEquals(
            DisplayOutput.Image("/tmp/chart.bmp", "Chart"),
            parseDisplayOutput("""{"type":"bitmap","src":"/tmp/chart.bmp","alt":"Chart"}""")
        )
    }

    @Test
    fun `image payloads survive the normal text storage limit`() {
        val source = "data:image/png;base64," + "A".repeat(32 * 1024)
        val bounded = boundWidgetOutput(com.google.gson.Gson().toJson(source))
        assertEquals(DisplayOutput.Image(source), parseDisplayOutput(bounded))
    }

    @Test
    fun `oversized image payload gets a readable error`() {
        val source = "data:image/bmp;base64," + "A".repeat(MAX_WIDGET_IMAGE_SOURCE_LENGTH)
        assertEquals(
            DisplayOutput.PlainText("Image is too large for the widget"),
            parseDisplayOutput(boundWidgetOutput(source))
        )
    }

    @Test
    fun `malformed image descriptors retain JSON presentation`() {
        listOf("""{"type":"image"}""", """{"type":"image","src":42}""",
            """{"type":"bitmap","path":""}""").forEach {
            assertTrue(parseDisplayOutput(it) is DisplayOutput.Json)
        }
    }

    @Test
    fun `ordinary strings and non image files remain text`() {
        listOf("Result is chart.png", "/tmp/result.txt", "https://example.com/chart.png").forEach {
            assertEquals(DisplayOutput.PlainText(it), parseDisplayOutput(it))
        }
    }

    @Test
    fun `sample size bounds large and extreme aspect ratio images`() {
        assertEquals(1, widgetImageSampleSize(100, 200))
        assertEquals(1, widgetImageSampleSize(384, 384))
        assertEquals(2, widgetImageSampleSize(768, 384))
        assertEquals(16, widgetImageSampleSize(4096, 1))
        assertTrue(Int.MAX_VALUE / widgetImageSampleSize(Int.MAX_VALUE, 1) <= WIDGET_IMAGE_MAX_SIDE)
    }
}
