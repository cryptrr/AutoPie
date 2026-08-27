package com.autopi


import android.os.Environment
import com.autopi.autopieApp.data.services.FakeJSONService
import com.autopi.autopieapp.data.CommandCreationModel
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.domain.model.CloudCommandModel
import com.autopi.use_case.CreateCommand
import com.autopi.use_case.GetCommandDetails
import com.autopi.use_case.GetCommandsList
import com.autopi.use_case.GetRepoCommandsList
import com.autopi.autopieapp.presentation.viewModels.isCloudCommandUpdateAvailable
import com.autopi.autopieapp.presentation.viewModels.keywordInstallScriptFor
import com.autopi.autopieapp.presentation.viewModels.matchesAnyCloudKeyword
import com.autopi.autopieapp.presentation.viewModels.sortCloudCommandsForCatalog
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        jsonService.writeCommandsConfig(
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
                "steps": {"unsupported": true}
              }
            }
            """.trimIndent()
        )
        var skippedCommands = emptyList<String>()

        val commands = GetCommandsList(jsonService).invoke { skippedCommands = it }
        val compatibleCommand = GetCommandDetails(jsonService)("Compatible")

        assertEquals(listOf("Compatible"), commands.map { it.name })
        assertEquals(listOf("Command: Requires newer app"), skippedCommands)
        assertEquals("echo ok", compatibleCommand.command)
    }

    @Test
    fun `repo catalog command id comes from object key`() = runTest {
        val jsonService = FakeJSONService()
        jsonService.writeCommandsConfig(
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
              - id: "output"
                name: "OUTPUT_TYPE"
                type: "SELECTABLE"
                default: "pdf"
                description: "Output type."
                required: true
                selectableOptions:
                  PDF: "pdf"
                  Text: "txt"
            install:
              script: "install.sh"
            """.trimIndent()
        )

        val command = manifest.commandObject
        val extras = command.getAsJsonArray("extras")
        val extra = extras.first().asJsonObject
        val selectableExtra = extras[1].asJsonObject

        assertEquals("Change Volume on Mac", manifest.commandKey)
        assertEquals("install.sh", manifest.installScript)
        assertEquals("autopie.change-volume-on-mac", command.get("id").asString)
        assertEquals("1.0.0", command.get("version").asString)
        assertEquals("SHARE", command.get("type").asString)
        assertEquals("AutoSec/scripts", command.get("path").asString)
        assertEquals("openssh", command.get("exec").asString)
        assertEquals("VOLUME", extra.get("name").asString)
        assertEquals("--realtime", extra.getAsJsonArray("flags")[1].asString)
        assertEquals("pdf", selectableExtra.getAsJsonObject("selectableOptions").get("PDF").asString)
        assertEquals("txt", selectableExtra.getAsJsonObject("selectableOptions").get("Text").asString)
    }

    @Test
    fun `observer manifest keeps observer properties in commands config`() = runTest {
        val command = cloudManifestToShareCommandJson(
            """
            id: "autopie.watch-downloads"
            name: "Watch Downloads"
            type: "FILE_OBSERVER"
            runtime:
              path: "Download"
              command: "echo changed"
              selectors: [".*\\.mp4", ".*\\.mkv"]
            """.trimIndent()
        ).commandObject

        assertEquals("FILE_OBSERVER", command.get("type").asString)
        assertEquals(".*\\.mp4", command.getAsJsonArray("selectors")[0].asString)
    }

    @Test
    fun `cron manifest keeps cron properties in commands config`() = runTest {
        val command = cloudManifestToShareCommandJson(
            """
            id: "autopie.hourly-sync"
            name: "Hourly Sync"
            type: "CRON"
            runtime:
              command: "rsync source target"
              cronInterval: "1h"
            """.trimIndent()
        ).commandObject

        assertEquals("CRON", command.get("type").asString)
        assertEquals("1h", command.get("cronInterval").asString)
    }

    @Test
    fun `cloud command update is available when catalog version is newer`() = runTest {
        assertEquals(true, isCloudCommandUpdateAvailable("1.1.0", "1.0.0"))
        assertEquals(false, isCloudCommandUpdateAvailable("1.0.0", "1.0.0"))
        assertEquals(false, isCloudCommandUpdateAvailable("1.0.0", "1.1.0"))
        assertEquals(true, isCloudCommandUpdateAvailable("1.0.0", ""))
    }

    @Test
    fun `cloud commands with updates are sorted first`() = runTest {
        val commands = listOf(
            CloudCommandModel(id = "autopie.z-current", name = "Z Current", version = "1.0.0"),
            CloudCommandModel(id = "autopie.a-update", name = "A Update", version = "1.1.0"),
            CloudCommandModel(id = "autopie.b-new", name = "B New", version = "1.0.0")
        )

        val sorted = sortCloudCommandsForCatalog(
            commands,
            mapOf(
                "autopie.z-current" to "1.0.0",
                "autopie.a-update" to "1.0.0"
            )
        )

        assertEquals(listOf("autopie.a-update", "autopie.b-new", "autopie.z-current"), sorted.map { it.id })
    }

    @Test
    fun `cloud command keyword matching checks catalog metadata`() = runTest {
        val ffmpegCommand = CloudCommandModel(
            id = "autopie.extract-audio",
            name = "Extract Audio",
            summary = "Convert media files",
            tags = listOf("ffmpeg")
        )
        val imageMagickCommand = CloudCommandModel(
            id = "autopie.resize-image",
            name = "Resize Image",
            summary = "ImageMagick powered resize",
            tags = listOf("images")
        )
        val unrelatedCommand = CloudCommandModel(
            id = "autopie.sync-folder",
            name = "Sync Folder",
            tags = listOf("rsync")
        )

        val selectedKeywords = listOf("ffmpeg", "imagemagick")

        assertEquals(true, ffmpegCommand.matchesAnyCloudKeyword(selectedKeywords))
        assertEquals(true, imageMagickCommand.matchesAnyCloudKeyword(selectedKeywords))
        assertEquals(false, unrelatedCommand.matchesAnyCloudKeyword(selectedKeywords))
    }

    @Test
    fun `keyword install script uses fixed commands once`() = runTest {
        val installScript = keywordInstallScriptFor(listOf("ffmpeg", "yt-dlp", "ffmpeg", "unknown"))

        assertTrue(installScript.contains("dpkg -s \"${'$'}package\""))
        assertTrue(installScript.contains("pip show \"${'$'}package\""))
        assertTrue(installScript.contains("pkg install -y \"${'$'}package\""))
        assertTrue(installScript.contains("pip install \"${'$'}package\""))
        assertEquals(1, "autopie_pkg_install_once ffmpeg".toRegex().findAll(installScript).count())
        assertEquals(1, "autopie_pip_install_once yt-dlp".toRegex().findAll(installScript).count())
        assertFalse(installScript.contains("imagemagick"))
        assertFalse(installScript.contains("unknown"))
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
                - id: "output"
                  name: "OUTPUT_TYPE"
                  type: "SELECTABLE"
                  default: "pdf"
                  description: "Output type."
                  required: true
                  selectableOptions:
                    PDF: "pdf"
                    Text: "txt"
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
        assertEquals(
            "pdf",
            secondStep.getAsJsonArray("extras")[1]
                .asJsonObject
                .getAsJsonObject("selectableOptions")
                .get("PDF")
                .asString
        )
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
    fun `raw json adds commands keyed by name`() = runTest {
        val jsonService = FakeJSONService()
        val createCommand = CreateCommand(jsonService)

        createCommand.fromRawJson(
            """
            {
              "First raw command": {"command": "echo first", "type": "SHARE"},
              "Second raw command": {}
            }
            """.trimIndent()
        )

        val commands = jsonService.readCommandsConfig()!!
        assertEquals("echo first", commands.getAsJsonObject("First raw command").get("command").asString)
        assertTrue(commands.get("Second raw command").isJsonObject)
        assertEquals(4, commands.size())
    }

    @Test
    fun `raw json replaces an existing command with the same key`() = runTest {
        val jsonService = FakeJSONService()
        val createCommand = CreateCommand(jsonService)

        createCommand.fromRawJson(
            """{"Extract Audio": {"command": "echo replacement", "type": "SHARE"}}"""
        )

        val command = jsonService.readCommandsConfig()!!.getAsJsonObject("Extract Audio")
        assertEquals("echo replacement", command.get("command").asString)
        assertEquals(2, jsonService.readCommandsConfig()!!.size())
    }

    @Test
    fun `raw json rejects command values that are not objects without writing`() = runTest {
        val jsonService = FakeJSONService()
        val original = jsonService.getRawStorageContent()
        val createCommand = CreateCommand(jsonService)

        assertThrows(ViewModelError.InvalidRawCommandJson::class.java) {
            runBlocking { createCommand.fromRawJson("""{"Broken": "echo nope"}""") }
        }

        assertEquals(original, jsonService.getRawStorageContent())
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
