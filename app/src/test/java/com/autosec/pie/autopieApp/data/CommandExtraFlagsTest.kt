package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandFlags
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.flagValue
import com.autopi.autopieapp.data.hasFlag
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandExtraFlagsTest {
    @Test
    fun `extra flags deserialize from string array`() {
        val extra = Gson().fromJson(
            """{"id":"1","flags":["--file-picker","--password"]}""",
            CommandExtra::class.java
        )

        assertEquals(listOf("--file-picker", "--password"), extra.flags)
    }

    @Test
    fun `missing flags remain compatible`() {
        val extra = Gson().fromJson("""{"id":"1"}""", CommandExtra::class.java)

        assertTrue(extra.flags.orEmpty().isEmpty())
    }

    @Test
    fun `internal config flag is deserialized and detected`() {
        val extra = Gson().fromJson(
            """{"id":"internal","flags":["--internal-config"]}""",
            CommandExtra::class.java
        )

        assertTrue(extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG))
    }

    @Test
    fun `general flag methods parse names and quoted values`() {
        val flags = listOf("--file-picker", "--mime-type=\"application/pdf\"")

        assertTrue(flags.hasFlag(ExtraFlags.FILE_PICKER))
        assertTrue(flags.hasFlag(ExtraFlags.MIME_TYPE))
        assertEquals("application/pdf", flags.flagValue(ExtraFlags.MIME_TYPE))
        assertFalse(flags.hasFlag(ExtraFlags.PASSWORD))
    }

    @Test
    fun `missing flag value returns null`() {
        assertNull(listOf("--file-picker").flagValue(ExtraFlags.MIME_TYPE))
        assertNull(listOf("--mime-type").flagValue(ExtraFlags.MIME_TYPE))
    }

    @Test
    fun `int flag is detected`() {
        assertTrue(listOf("--int").hasFlag(ExtraFlags.INT))
        assertFalse(emptyList<String>().hasFlag(ExtraFlags.INT))
    }

    @Test
    fun `extra size flags are detected`() {
        val flags = listOf("--small", "--large")

        assertTrue(flags.hasFlag(ExtraFlags.SMALL))
        assertTrue(flags.hasFlag(ExtraFlags.LARGE))
    }

    @Test
    fun `realtime extra flag is detected`() {
        assertTrue(listOf("--realtime").hasFlag(ExtraFlags.REALTIME))
        assertFalse(emptyList<String>().hasFlag(ExtraFlags.REALTIME))
    }

    @Test
    fun `loading screen command flag is detected`() {
        assertTrue(listOf("--show-loading-screen").hasFlag(CommandFlags.SHOW_LOADING_SCREEN))
        assertFalse(emptyList<String>().hasFlag(CommandFlags.SHOW_LOADING_SCREEN))
    }

    @Test
    fun `realtime command flag is detected`() {
        assertTrue(listOf("--realtime").hasFlag(CommandFlags.REALTIME))
        assertFalse(emptyList<String>().hasFlag(CommandFlags.REALTIME))
    }
}
