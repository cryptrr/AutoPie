package com.autopi.use_case

import androidx.compose.runtime.mutableStateOf
import com.autopi.autopieApp.data.services.FakeJSONService
import com.autopi.autopieapp.data.CommandCreationModel
import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.services.SecretsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SecretExtraPersistenceTest {
    @Test
    fun `new command stores named secret under generated command id`() = runTest {
        val jsonService = FakeJSONService()
        val secretsService = mockk<SecretsService>()
        every { secretsService.set(any(), any()) } returns true

        CreateCommand(jsonService, secretsService)(
            CommandCreationModel(
                selectedCommandType = "SHARE",
                commandName = "API request",
                directory = "",
                command = "echo ${'$'}API_SECRET",
                cronInterval = "",
                selectors = "",
                isValidCommand = true,
                commandExtras = listOf(
                    CommandExtra(
                        id = "secret",
                        name = "API_SECRET",
                        type = "STRING",
                        default = "new-token"
                    )
                )
            )
        )

        verify(exactly = 1) {
            secretsService.set("local.api-request@API_SECRET", "new-token")
        }
        val storedExtra = jsonService.readCommandsConfig()!!
            .getAsJsonObject("API request")
            .getAsJsonArray("extras")[0]
            .asJsonObject
        assertEquals("", storedExtra.get("default").asString)
    }

    @Test
    fun `edited command stores secret under persisted command id`() = runTest {
        val jsonService = FakeJSONService().apply {
            writeCommandsConfig(
                """
                {
                  "API request": {
                    "id": "local.api-request",
                    "type": "SHARE",
                    "extras": []
                  }
                }
                """.trimIndent()
            )
        }
        val secretsService = mockk<SecretsService>()
        every { secretsService.set(any(), any()) } returns true
        every { secretsService.delete(any()) } returns true

        ChangeCommandDetails(jsonService, secretsService)(
            key = "API request",
            commandExtras = mutableStateOf(
                listOf(
                    CommandExtra(
                        id = "secret",
                        name = "PASSWORD",
                        type = "STRING",
                        default = "edited-password"
                    )
                )
            ),
            oldCommandName = mutableStateOf("API request"),
            selectors = mutableStateOf(""),
            commandName = mutableStateOf("API request"),
            directory = mutableStateOf(""),
            execFile = mutableStateOf(""),
            command = mutableStateOf("echo ${'$'}PASSWORD"),
            type = mutableStateOf("SHARE"),
            cronInterval = mutableStateOf("")
        )

        verify(exactly = 1) {
            secretsService.set("local.api-request@PASSWORD", "edited-password")
        }
        val storedExtra = jsonService.readCommandsConfig()!!
            .getAsJsonObject("API request")
            .getAsJsonArray("extras")[0]
            .asJsonObject
        assertEquals("", storedExtra.get("default").asString)
    }
}
