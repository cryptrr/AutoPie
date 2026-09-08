package com.autosec.pie

import com.autopi.autopieapp.data.CommandsRepositoryChannel
import com.autopi.autopieapp.data.CommandsRepositoryUrls
import com.autopi.use_case.cloudCommandFolderUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandsRepositoryTest {
    @Test
    fun `unknown or empty preference uses main channel`() {
        assertEquals(
            CommandsRepositoryChannel.MAIN,
            CommandsRepositoryChannel.fromPreference("")
        )
        assertEquals(
            CommandsRepositoryChannel.MAIN,
            CommandsRepositoryChannel.fromPreference("unknown")
        )
    }

    @Test
    fun `catalog URL uses selected channel`() {
        assertEquals(
            "https://raw.githubusercontent.com/cryptrr/autopie-commands/main/catalog.json",
            CommandsRepositoryUrls.catalog(CommandsRepositoryChannel.MAIN)
        )
        assertEquals(
            "https://raw.githubusercontent.com/cryptrr/autopie-commands/dev/catalog.json",
            CommandsRepositoryUrls.catalog(CommandsRepositoryChannel.DEV)
        )
    }

    @Test
    fun `command URL uses selected channel`() {
        assertEquals(
            "https://raw.githubusercontent.com/cryptrr/autopie-commands/dev/commands/media/download",
            cloudCommandFolderUrl("media.download", CommandsRepositoryChannel.DEV)
        )
    }
}
