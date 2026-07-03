package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.ScriptFlags
import com.autopi.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserScriptHeaderTest {

    @Test
    fun `browser header is detected in the leading header block`() {
        val command = """
            #@INTERACTIVE
            //@BROWSER
            (() => document.title)();
        """.trimIndent()

        assertTrue(Utils.hasScriptHeader(command, ScriptFlags.BROWSER))
    }

    @Test
    fun `browser header after javascript starts is ignored`() {
        val command = """
            (() => true)();
            //@BROWSER
        """.trimIndent()

        assertFalse(Utils.hasScriptHeader(command, ScriptFlags.BROWSER))
    }

    @Test
    fun `all leading shell and javascript headers are stripped before evaluation`() {
        val command = """
            #@INTERACTIVE
            //@BROWSER
            (() => document.title)();
        """.trimIndent()

        assertEquals("(() => document.title)();", Utils.stripScriptHeaders(command))
    }
}
