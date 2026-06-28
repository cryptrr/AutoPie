package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.CommandExtra
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
    fun `general flag methods parse names and quoted values`() {
        val flags = listOf("--file-picker", "--mime-type=\"application/pdf\"")

        assertTrue(flags.hasFlag("--file-picker"))
        assertTrue(flags.hasFlag("--mime-type"))
        assertEquals("application/pdf", flags.flagValue("--mime-type"))
        assertFalse(flags.hasFlag("--password"))
    }

    @Test
    fun `missing flag value returns null`() {
        assertNull(listOf("--file-picker").flagValue("--mime-type"))
        assertNull(listOf("--mime-type").flagValue("--mime-type"))
    }
}
