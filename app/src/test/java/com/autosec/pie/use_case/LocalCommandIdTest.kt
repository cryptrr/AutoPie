package com.autopi.use_case

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCommandIdTest {
    @Test
    fun `slugifies command name with local prefix`() {
        assertEquals("local.ytdlp-downloader", localCommandId("  YTDLP Downloader!  "))
    }

    @Test
    fun `normalizes accents and repeated separators`() {
        assertEquals("local.creme-brulee", localCommandId("Crème -- brûlée"))
    }

    @Test
    fun `uses command fallback when name has no slug characters`() {
        assertEquals("local.command", localCommandId("日本語"))
    }
}
