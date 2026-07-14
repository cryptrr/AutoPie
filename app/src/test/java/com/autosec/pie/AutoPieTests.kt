package com.autopi


import android.os.Environment
import com.autopi.autopieApp.data.services.FakeJSONService
import com.autopi.autopieapp.data.CommandCreationModel
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.use_case.CreateCommand
import com.autopi.use_case.GetCommandDetails
import com.autopi.use_case.GetCommandsList
import com.autopi.use_case.GetRepoCommandsList
import com.autopi.use_case.cloudManifestDocs
import com.autopi.use_case.cloudManifestToShareCommandJson
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.test.KoinTest
import timber.log.Timber
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */


class CommandTests : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()



    @Test
    fun `commands list initially contains two items`() = runTest {

        mockkStatic(Environment::class)

        val mockFile = File("/storage/emulated/0")
        every { Environment.getExternalStorageDirectory() } returns mockFile


        val jsonService = FakeJSONService()
        val getCommandsList = GetCommandsList(jsonService)

        mainDispatcherRule.scheduler.advanceUntilIdle()

        assertEquals(getCommandsList().size, 2)
    }

    @Test
    fun `incompatible command is skipped without hiding compatible commands`() = runTest {
        val jsonService = FakeJSONService()
        jsonService.writeSharesConfig(
            """
            {
              "Compatible": {
                "path": "",
                "command": "echo ok",
                "exec": ""
              },
              "Requires newer app": {
                "path": "",
                "command": "echo new",
                "exec": "",
                "extras": [{
                  "id": "1",
                  "type": "SELECTABLE",
                  "selectableOptions": {"Friendly label": "--raw-value"}
                }]
              }
            }
            """.trimIndent()
        )
        var skippedCommands = emptyList<String>()

        val commands = GetCommandsList(jsonService).invoke { skippedCommands = it }
        val compatibleCommand = GetCommandDetails(jsonService)("Compatible")

        assertEquals(listOf("Compatible"), commands.map { it.name })
        assertEquals(listOf("Share: Requires newer app"), skippedCommands)
        assertEquals("echo ok", compatibleCommand.command)
    }

    @Test
    fun `repo catalog command id comes from object key`() = runTest {
        val jsonService = FakeJSONService()
        jsonService.writeSharesConfig(
            """
            {
              "${'$'}schema": "schema/2026.6.1/catalog.schema.json",
              "commands": {
                "autopie.change-volume-on-mac": {
                  "name": "Change Volume on Mac",
                  "namespace": "autopie",
                  "status": "stable",
                  "summary": "AutoPie command for Change Volume on Mac",
                  "tags": ["openssh"],
                  "version": "1.0.0"
                }
              }
            }
            """.trimIndent()
        )

        val commands = GetRepoCommandsList(jsonService).invoke("unused")

        assertEquals(1, commands.size)
        assertEquals("autopie.change-volume-on-mac", commands.first().id)
        assertEquals("Change Volume on Mac", commands.first().name)
        assertEquals("AutoPie command for Change Volume on Mac", commands.first().summary)
        assertEquals(listOf("openssh"), commands.first().tags)
        assertEquals("", commands.first().command)
    }

    @Test
    fun `cloud manifest converts to share command`() = runTest {
        val manifest = cloudManifestToShareCommandJson(
            """
            schemaVersion: "2026.6.1"
            version: "1.0.0"
            id: "autopie.change-volume-on-mac"
            namespace: "autopie"
            name: "Change Volume on Mac"
            commandSlug: "openssh"
            summary: "AutoPie command for Change Volume on Mac"
            type: "PACKAGE"
            tags: ["openssh"]
            runtime:
              path: "AutoSec/scripts"
              command: "sshpass -p \"${'$'}PASSWORD\" ssh \"${'$'}USER@${'$'}HOST\" \"osascript -e \\\"set volume output volume ${'$'}{VOLUME}\\\"\""
              extras:
              - id: "574538"
                name: "VOLUME"
                type: "SLIDER"
                default: "0,50,100"
                description: "Slider to set volume."
                required: true
                flags: ["--int", "--realtime"]
            install:
              script: "install.sh"
            """.trimIndent()
        )

        val command = manifest.commandObject
        val extra = command.getAsJsonArray("extras").first().asJsonObject

        assertEquals("Change Volume on Mac", manifest.commandKey)
        assertEquals("install.sh", manifest.installScript)
        assertEquals("autopie.change-volume-on-mac", command.get("id").asString)
        assertEquals("AutoSec/scripts", command.get("path").asString)
        assertEquals("openssh", command.get("exec").asString)
        assertEquals("VOLUME", extra.get("name").asString)
        assertEquals("--realtime", extra.getAsJsonArray("flags")[1].asString)
    }

    @Test
    fun `cloud manifest exposes documentation paths`() = runTest {
        val docs = cloudManifestDocs(
            """
            schemaVersion: "2026.6.1"
            id: "autopie.change-volume-on-mac"
            name: "Change Volume on Mac"
            runtime:
              path: "AutoSec/scripts"
              command: "echo ok"
            docs:
              readme: "README.md"
              changelog: "CHANGELOG.md"
            """.trimIndent()
        )

        assertEquals("README.md", docs.readme)
        assertEquals("CHANGELOG.md", docs.changelog)
    }

    @Test
    fun `cloud multistage manifest converts steps to share command`() = runTest {
        val manifest = cloudManifestToShareCommandJson(
            """
            schemaVersion: "2026.6.1"
            version: "1.0.0"
            id: "autopie.change-volume-on-mac-autofetch"
            namespace: "autopie"
            name: "Change Volume on Mac - AutoFetch"
            commandSlug: "change-volume-on-mac-autofetch"
            summary: "AutoPie command for Change Volume on Mac - AutoFetch"
            type: "PACKAGE"
            tags: ["openssh"]
            runtime:
              multiStage: true
              steps:
              - path: "AutoSec/scripts"
                commandSlug: "openssh"
                command: "export SLIDER_OPTIONS=0,50,100"
              - path: "AutoSec/scripts"
                commandSlug: "openssh"
                command: "sshpass -p \"${'$'}PASSWORD\" ssh \"${'$'}USER@${'$'}HOST\""
                extras:
                - id: "574538"
                  name: "VOLUME"
                  type: "SLIDER"
                  default: "${'$'}${'$'}SLIDER_OPTIONS"
                  description: "Slider to set volume."
                  required: true
                  flags: ["--int", "--realtime"]
            install:
              script: "install.sh"
            """.trimIndent()
        )

        val command = manifest.commandObject
        val steps = command.getAsJsonArray("steps")
        val secondStep = steps[1].asJsonObject

        assertEquals("Change Volume on Mac - AutoFetch", manifest.commandKey)
        assertEquals(true, command.get("multiStage").asBoolean)
        assertEquals("openssh", command.get("exec").asString)
        assertEquals("0", steps[0].asJsonObject.get("id").asString)
        assertEquals("1", secondStep.get("id").asString)
        assertEquals("VOLUME", secondStep.getAsJsonArray("extras")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun `adding a command makes it 3`() = runTest {

        mockkStatic(Environment::class)

        val mockFile = File("/storage/emulated/0")
        every { Environment.getExternalStorageDirectory() } returns mockFile


        val jsonService = FakeJSONService()
        val createCommand = CreateCommand(jsonService)

        val newCommand = CommandCreationModel(
            selectedCommandType = "SHARE",
            commandName = "Create Plumbus",
            directory = "",
            command = "plumb --create",
            isValidCommand = true,
            exec = "plumbus",
            commandExtras = emptyList(),
            selectors = "",
            cronInterval = ""
        )

        createCommand(newCommand)


        val getCommandsList = GetCommandsList(jsonService)


        assertEquals(getCommandsList().size, 3)
    }

    @Test
    fun `get a command - success`() = runTest {

        val jsonService = FakeJSONService()
        val getCommand = GetCommandDetails(jsonService)

        val command = getCommand("RSYNC Sync Folder")

        //assertEquals("rsync",command.get("exec").asString)


    }

    @Test
    fun `get a non existent command`() = runTest {

        val jsonService = FakeJSONService()
        val getCommand = GetCommandDetails(jsonService)


        assertThrows(ViewModelError.CommandNotFound::class.java){
            runBlocking {
                getCommand("non existent command")
            }
        }
    }
}

class MainDispatcherRule : TestWatcher() {
    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
