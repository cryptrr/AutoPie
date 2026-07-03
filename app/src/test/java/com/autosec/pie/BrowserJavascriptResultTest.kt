package com.autosec.pie

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserJavascriptResultTest {

    @Test
    fun `javascript string result is decoded before shell handoff`() {
        assertEquals(
            "https://example.com/video.mp4?token=a&part=1",
            decodeBrowserJavascriptResult(
                "\"https:\\/\\/example.com\\/video.mp4?token=a&part=1\""
            ).output
        )
    }

    @Test
    fun `javascript null becomes an empty shell value`() {
        assertEquals("", decodeBrowserJavascriptResult("null").output)
    }

    @Test
    fun `object properties become shell environment values`() {
        val result = decodeBrowserJavascriptResult(
            """{"url":"https://example.com/video.mp4","quality":720,"enabled":true,"metadata":{"id":4},"missing":null}"""
        )

        assertEquals(
            """{"url":"https://example.com/video.mp4","quality":720,"enabled":true,"metadata":{"id":4},"missing":null}""",
            result.output
        )
        assertEquals("https://example.com/video.mp4", result.environment["url"])
        assertEquals("720", result.environment["quality"])
        assertEquals("true", result.environment["enabled"])
        assertEquals("""{"id":4}""", result.environment["metadata"])
        assertEquals("", result.environment["missing"])
    }
}
