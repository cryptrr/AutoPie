package com.autopi.autopieApp.data.services

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.services.InternalConfigService
import com.tencent.mmkv.MMKV
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class InternalConfigServiceTest {
    private val mmkv = mockk<MMKV>()
    private val service = InternalConfigService(mmkv)

    @Test
    fun `uses command and extra ids for storage`() {
        every { mmkv.encode(any<String>(), "saved") } returns true

        service.set("stable-command-id", "folder", "saved")

        verify { mmkv.encode("17:stable-command-id6:folder", "saved") }
    }

    @Test
    fun `resolves typed internal config`() {
        every { mmkv.containsKey(any()) } returns true
        every { mmkv.decodeString(any()) } returns "false"
        val extra = CommandExtra(
            id = "enabled",
            type = "BOOLEAN",
            defaultBoolean = true,
            flags = listOf(ExtraFlags.INTERNAL_CONFIG.value)
        )

        val resolved = service.resolve("stable-command-id", extra)

        assertEquals(false, resolved.defaultBoolean)
    }
}
