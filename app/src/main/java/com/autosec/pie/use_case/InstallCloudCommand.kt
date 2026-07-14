package com.autopi.use_case

import android.app.Application
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.domain.ViewModelError
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.yaml.snakeyaml.Yaml
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class InstallCloudCommand(
    private val jsonService: JsonService,
    private val processManagerService: ProcessManagerService,
    private val application: Application
) {
    suspend operator fun invoke(commandId: String, manifestYaml: String? = null) {
        val folderUrl = cloudCommandFolderUrl(commandId)
        val resolvedManifestYaml = manifestYaml ?: fetchCloudCommandText("$folderUrl/manifest.yaml")
        val manifest = cloudManifestToShareCommandJson(resolvedManifestYaml)
        val installScriptName = manifest.installScript?.takeIf(String::isNotBlank)

        if (installScriptName != null) {
            val installScript = fetchCloudCommandText("$folderUrl/$installScriptName")
            runInstallScript(manifest.commandKey, installScript)
        }

        val shareCommands = jsonService.readSharesConfig() ?: throw ViewModelError.ShareConfigUnavailable
        shareCommands.add(manifest.commandKey, manifest.commandObject)

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        jsonService.writeSharesConfig(gson.toJson(shareCommands))
    }

    private fun runInstallScript(commandName: String, installScript: String) {
        val installCommand = installScript.trimEnd() + "\n"
        val command = CommandModel(
            id = "$commandName.install",
            type = CommandType.SHARE,
            name = "Install $commandName",
            path = "",
            exec = "bash",
            command = installCommand
        )

        processManagerService.runCommandInTermuxShell(
            commandObject = command,
            exec = "bash",
            command = installCommand,
            cwd = application.filesDir.absolutePath,
            commandExtraInputs = emptyList(),
            rawInput = "",
            processId = (1000..9999).random(),
            jobType = JobType.STANDALONE,
            usePython = false,
            isShellScript = true
        )
    }

}

class GetCloudCommandDocumentation {
    operator fun invoke(commandId: String): CloudCommandDocumentation {
        val folderUrl = cloudCommandFolderUrl(commandId)
        val manifestYaml = fetchCloudCommandText("$folderUrl/manifest.yaml")
        val docs = cloudManifestDocs(manifestYaml)

        return CloudCommandDocumentation(
            manifestYaml = manifestYaml,
            readme = docs.readme?.let { fetchCloudCommandText("$folderUrl/$it") }.orEmpty(),
            changelog = docs.changelog?.let { fetchCloudCommandText("$folderUrl/$it") }.orEmpty()
        )
    }
}

internal data class CloudManifestCommand(
    val commandKey: String,
    val commandObject: JsonObject,
    val installScript: String?
)

data class CloudCommandDocumentation(
    val manifestYaml: String,
    val readme: String,
    val changelog: String
)

internal data class CloudManifestDocs(
    val readme: String?,
    val changelog: String?
)

internal fun cloudManifestDocs(manifestYaml: String): CloudManifestDocs {
    val manifest = Yaml().load<Map<String, Any?>>(manifestYaml)
        ?: throw ViewModelError.InvalidCommandRepoFile
    val docs = manifest.mapValue("docs", required = false)

    return CloudManifestDocs(
        readme = docs.stringValue("readme", required = false).takeIf(String::isNotBlank),
        changelog = docs.stringValue("changelog", required = false).takeIf(String::isNotBlank)
    )
}

internal fun cloudManifestToShareCommandJson(manifestYaml: String): CloudManifestCommand {
    val manifest = Yaml().load<Map<String, Any?>>(manifestYaml)
        ?: throw ViewModelError.InvalidCommandRepoFile
    val runtime = manifest.mapValue("runtime")
    val install = manifest.mapValue("install", required = false)
    val id = manifest.stringValue("id")
    val version = manifest.stringValue("version", required = false)
    val name = manifest.stringValue("name").ifBlank { id }
    val commandSlug = manifest.stringValue("commandSlug", required = false)
    val commandObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("version", version)
        addProperty("path", "")
        addProperty("exec", commandSlug)
        addProperty("command", "")
    }

    if (runtime.booleanValue("multiStage", required = false)) {
        val steps = runtime.listValue("steps").mapIndexed { index, stepValue ->
            val step = stepValue.asMap()
            JsonObject().apply {
                addProperty("id", index.toString())
                addProperty("path", step.stringValue("path", required = false))
                addProperty(
                    "command",
                    step.stringValue("command", required = false)
                )
                step.stringValue("commandSlug", required = false)
                    .takeIf(String::isNotBlank)
                    ?.let { addProperty("exec", it) }
                step.extrasArray()?.let { add("extras", it) }
            }
        }

        val firstStep = runtime.listValue("steps").firstOrNull()?.asMap()
        commandObject.addProperty("multiStage", true)
        commandObject.addProperty(
            "exec",
            firstStep?.stringValue("commandSlug", required = false).orEmpty().ifBlank { commandSlug }
        )
        commandObject.add("steps", JsonArray().apply { steps.forEach(::add) })
    } else {
        commandObject.addProperty("path", runtime.stringValue("path", required = false))
        commandObject.addProperty("command", runtime.stringValue("command", required = false))
        runtime.stringValue("commandSlug", required = false)
            .takeIf(String::isNotBlank)
            ?.let { commandObject.addProperty("exec", it) }
        runtime.extrasArray()?.let { commandObject.add("extras", it) }
    }

    return CloudManifestCommand(
        commandKey = name,
        commandObject = commandObject,
        installScript = install.stringValue("script", required = false)
    )
}

private fun Map<String, Any?>.stringValue(key: String, required: Boolean = true): String {
    val value = this[key]?.toString().orEmpty()
    if (required && value.isBlank()) throw ViewModelError.InvalidCommandRepoFile
    return value
}

private fun Map<String, Any?>.booleanValue(key: String, required: Boolean = true): Boolean {
    val value = this[key] ?: return if (required) throw ViewModelError.InvalidCommandRepoFile else false
    return when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull() ?: false
        else -> false
    }
}

private fun Map<String, Any?>.mapValue(key: String, required: Boolean = true): Map<String, Any?> {
    val value = this[key] ?: return if (required) throw ViewModelError.InvalidCommandRepoFile else emptyMap()
    return value.asMap()
}

private fun Map<String, Any?>.listValue(key: String): List<Any?> {
    val value = this[key] ?: throw ViewModelError.InvalidCommandRepoFile
    return value as? List<Any?> ?: throw ViewModelError.InvalidCommandRepoFile
}

private fun Map<String, Any?>.extrasArray(): JsonArray? {
    val extras = this["extras"] as? List<*> ?: return null
    return JsonArray().apply {
        extras.mapNotNull { it?.asMap() }.forEach { extra ->
            add(JsonObject().apply {
                addProperty("id", extra.stringValue("id", required = false))
                addProperty("name", extra.stringValue("name", required = false))
                addProperty("type", extra.stringValue("type", required = false))
                addProperty("default", extra.stringValue("default", required = false))
                addProperty("description", extra.stringValue("description", required = false))
                addProperty("required", extra.booleanValue("required", required = false))
                extra.stringListValue("flags")?.let { flags ->
                    add("flags", Gson().toJsonTree(flags))
                }
            })
        }
    }
}

private fun Map<String, Any?>.stringListValue(key: String): List<String>? {
    val value = this[key] ?: return null
    return (value as? List<*>)?.map { it.toString() }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> =
    this as? Map<String, Any?> ?: throw ViewModelError.InvalidCommandRepoFile

internal fun cloudCommandFolderUrl(commandId: String): String {
    val commandPath = commandId.trim().split(".")
        .filter(String::isNotBlank)
        .joinToString("/")

    if (commandPath.isBlank()) throw ViewModelError.CommandNotFound

    return "$COMMANDS_RAW_BASE/$commandPath"
}

internal fun fetchCloudCommandText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 30_000
        readTimeout = 30_000
        requestMethod = "GET"
    }

    try {
        if (connection.responseCode !in 200..299) {
            Timber.w("Failed to fetch cloud command resource: $url (${connection.responseCode})")
            throw ViewModelError.NetworkError
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private const val COMMANDS_RAW_BASE =
    "https://raw.githubusercontent.com/cryptrr/autopie-commands/main/commands"
