package com.autopi.autopieapp.data.services

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ConfigBackupService {

    fun createBackup(commandsFile: File, destination: OutputStream) {
        if (!commandsFile.isFile) {
            throw ConfigBackupException.CommandsConfigMissing
        }

        ZipOutputStream(destination.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(COMMANDS_ENTRY_NAME))
            commandsFile.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    fun restoreBackup(source: InputStream, commandsFile: File) {
        val restoredBytes = readCommandsConfig(source)
        val restoredJson = try {
            JsonParser.parseString(restoredBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            throw ConfigBackupException.InvalidCommandsConfig
        }
        if (!restoredJson.isJsonObject) {
            throw ConfigBackupException.InvalidCommandsConfig
        }

        val parent = commandsFile.parentFile ?: throw ConfigBackupException.RestoreFailed
        if (!parent.exists() && !parent.mkdirs()) {
            throw ConfigBackupException.RestoreFailed
        }

        val temporaryFile = File.createTempFile("commands-restore-", ".json", parent)
        try {
            temporaryFile.writeBytes(restoredBytes)
            try {
                Files.move(
                    temporaryFile.toPath(),
                    commandsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    commandsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (error: ConfigBackupException) {
            throw error
        } catch (_: Exception) {
            throw ConfigBackupException.RestoreFailed
        } finally {
            temporaryFile.delete()
        }
    }

    private fun readCommandsConfig(source: InputStream): ByteArray {
        ZipInputStream(source.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == COMMANDS_ENTRY_NAME) {
                    return zip.readLimited(MAX_COMMANDS_CONFIG_BYTES)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw ConfigBackupException.CommandsEntryMissing
    }

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw ConfigBackupException.CommandsConfigTooLarge
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val COMMANDS_ENTRY_NAME = "commands.json"
        const val MAX_COMMANDS_CONFIG_BYTES = 20 * 1024 * 1024
    }
}

sealed class ConfigBackupException : Exception() {
    data object CommandsConfigMissing : ConfigBackupException()
    data object CommandsEntryMissing : ConfigBackupException()
    data object CommandsConfigTooLarge : ConfigBackupException()
    data object InvalidCommandsConfig : ConfigBackupException()
    data object RestoreFailed : ConfigBackupException()
}
