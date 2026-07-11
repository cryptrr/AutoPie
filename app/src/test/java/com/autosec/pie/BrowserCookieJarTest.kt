package com.autosec.pie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCookieJarTest {

    @Test
    fun `webview cookie header is converted to netscape cookie entries`() {
        val entries = netscapeCookieEntries(
            url = "https://example.com/watch/video?id=1",
            cookieHeader = "session=abc123; theme=dark=mode"
        )

        assertEquals(
            listOf(
                "example.com\tFALSE\t/\tTRUE\t0\tsession\tabc123",
                "example.com\tFALSE\t/\tTRUE\t0\ttheme\tdark=mode"
            ),
            entries.map { it.toFileLine() }
        )
    }

    @Test
    fun `merge updates matching cookies and appends new cookies`() {
        val merged = mergeNetscapeCookieFile(
            existingLines = listOf(
                "# Netscape HTTP Cookie File",
                "example.com\tFALSE\t/\tTRUE\t0\tsession\told",
                "other.test\tFALSE\t/\tFALSE\t0\tkeep\tme"
            ),
            newEntries = netscapeCookieEntries(
                url = "https://example.com/page",
                cookieHeader = "session=new; fresh=yes"
            )
        )

        assertEquals(
            """
            # Netscape HTTP Cookie File
            example.com	FALSE	/	TRUE	0	session	new
            other.test	FALSE	/	FALSE	0	keep	me
            example.com	FALSE	/	TRUE	0	fresh	yes
            
            """.trimIndent(),
            merged
        )
    }

    @Test
    fun `merge creates netscape header for new cookie files`() {
        val merged = mergeNetscapeCookieFile(
            existingLines = emptyList(),
            newEntries = netscapeCookieEntries(
                url = "http://example.com",
                cookieHeader = "plain=value"
            )
        )

        assertTrue(merged.startsWith("# Netscape HTTP Cookie File\n"))
        assertTrue(merged.contains("example.com\tFALSE\t/\tFALSE\t0\tplain\tvalue"))
    }
}
