package com.autopi.autopieapp.data.services

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigBackupServiceTest {

    private val service = ConfigBackupService()

    @Test
    fun backupContainsCommandsJson() {
        val directory = Files.createTempDirectory("autopie-config-backup").toFile()
        try {
            val commandsFile = directory.resolve("commands.json")
            val commandsJson = """{"Example":{"name":"Example"}}"""
            commandsFile.writeText(commandsJson)
            val backup = ByteArrayOutputStream()

            service.createBackup(commandsFile, backup)

            ZipInputStream(ByteArrayInputStream(backup.toByteArray())).use { zip ->
                assertEquals(ConfigBackupService.COMMANDS_ENTRY_NAME, zip.nextEntry.name)
                assertEquals(commandsJson, zip.readBytes().toString(Charsets.UTF_8))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun restoreReplacesCommandsConfigFromBackup() {
        val directory = Files.createTempDirectory("autopie-config-restore").toFile()
        try {
            val commandsFile = directory.resolve("commands.json")
            commandsFile.writeText("{}")
            val restoredJson = """{"Restored":{"name":"Restored"}}""".toByteArray()

            service.restoreBackup(
                ByteArrayInputStream(zipWithCommands(restoredJson)),
                commandsFile
            )

            assertArrayEquals(restoredJson, commandsFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidJsonDoesNotOverwriteCurrentConfig() {
        val directory = Files.createTempDirectory("autopie-invalid-restore").toFile()
        try {
            val commandsFile = directory.resolve("commands.json")
            val currentJson = """{"Current":{}}"""
            commandsFile.writeText(currentJson)

            assertThrows(ConfigBackupException.InvalidCommandsConfig::class.java) {
                service.restoreBackup(
                    ByteArrayInputStream(zipWithCommands("not json".toByteArray())),
                    commandsFile
                )
            }

            assertEquals(currentJson, commandsFile.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun archiveWithoutCommandsJsonIsRejected() {
        val directory = Files.createTempDirectory("autopie-missing-config").toFile()
        try {
            val commandsFile = directory.resolve("commands.json")
            val backup = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("other.json"))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                }
            }

            assertThrows(ConfigBackupException.CommandsEntryMissing::class.java) {
                service.restoreBackup(ByteArrayInputStream(backup.toByteArray()), commandsFile)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun zipWithCommands(contents: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(ConfigBackupService.COMMANDS_ENTRY_NAME))
                zip.write(contents)
                zip.closeEntry()
            }
        }.toByteArray()
}
