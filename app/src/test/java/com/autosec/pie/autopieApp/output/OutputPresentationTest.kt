package com.autopi.autopieapp.output

import org.junit.Assert.*
import org.junit.Test

class OutputPresentationTest {
    @Test fun `preserves scalar output types`() {
        assertEquals(OutputElement.Text("42", numeric = true), parseOutputPresentation("42"))
        assertEquals(OutputElement.Text("42"), parseOutputPresentation("\"42\""))
        assertEquals(OutputElement.Text("Done!"), parseOutputPresentation("Done!"))
    }

    @Test fun `renders mixed lists and typed content`() {
        val output = parseOutputPresentation("""{"type":"list","title":"Results","items":["Hello",42,{"type":"image","path":"/tmp/image.png","caption":"Photo"},{"type":"link","text":"Open","url":"https://example.com"}]}""") as OutputElement.Items
        assertEquals("Results", output.title)
        assertEquals(OutputElement.Image("/tmp/image.png", "Photo"), output.values[2])
        assertEquals(OutputElement.Link("Open", "https://example.com"), output.values[3])
        assertEquals(4, output.values.size)
    }

    @Test fun `unknown and invalid typed objects fall back to JSON`() {
        listOf("""{"hello":42}""", """{"type":"image","path":42}""", """{"type":"list","items":false}""", """{"type":"link","url":"javascript:alert(1)"}""").forEach {
            assertTrue((parseOutputPresentation(it) as OutputElement.Text).json)
        }
    }

    @Test fun `handles empty and malformed output`() {
        assertTrue(parseOutputPresentation(null) is OutputElement.Text)
        assertEquals(OutputElement.Items(null, emptyList()), parseOutputPresentation("[]"))
        assertEquals(OutputElement.Text("{broken"), parseOutputPresentation("{broken"))
    }

    @Test fun `only opens web URLs with hosts`() {
        assertTrue(isOutputWebUrl("https://example.com/path?q=1"))
        listOf("file:///tmp/test", "intent://test", "https:missing-host", "javascript:alert(1)").forEach {
            assertFalse(isOutputWebUrl(it))
        }
    }
}
