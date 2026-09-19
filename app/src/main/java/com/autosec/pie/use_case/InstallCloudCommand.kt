package com.autopi.use_case

import android.app.Application
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.CommandsRepositoryChannel
import com.autopi.autopieapp.data.CommandsRepositoryUrls
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.services.JsonService
import com.autopi.autopieapp.data.services.MissingTermuxDependencies
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.domain.ViewModelError
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.yaml.snakeyaml.Yaml
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class InstallCloudCommand(
    private val jsonService: JsonService,
    private val processManagerService: ProcessManagerService,
    private val application: Application,
    private val appPreferences: AppPreferences
) {
    suspend operator fun invoke(commandId: String, manifestYaml: String? = null) {
        val channel = commandsRepositoryChannel()
        installResolvedCommands(listOf(resolveCloudCommand(commandId, channel, manifestYaml)))
    }

    suspend fun installAll(commandIds: List<String>, runInstallScripts: Boolean = true) {
        val uniqueCommandIds = commandIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        if (uniqueCommandIds.isEmpty()) {
            return
        }

        val channel = commandsRepositoryChannel()
        val fetchSemaphore = Semaphore(MAX_PARALLEL_COMMAND_FETCHES)
        val resolvedCommands = coroutineScope {
            uniqueCommandIds.map { commandId ->
                async(Dispatchers.IO) {
                    fetchSemaphore.withPermit {
                        resolveCloudCommand(
                            commandId,
                            channel,
                            includeInstallation = runInstallScripts
                        )
                    }
                }
            }.awaitAll()
        }

        installResolvedCommands(resolvedCommands)
    }

    private fun resolveCloudCommand(
        commandId: String,
        channel: CommandsRepositoryChannel,
        manifestYaml: String? = null,
        includeInstallation: Boolean = true
    ): ResolvedCloudCommand {
        val folderUrl = cloudCommandFolderUrl(commandId, channel)
        val resolvedManifestYaml = manifestYaml ?: fetchCloudCommandText("$folderUrl/manifest.yaml")
        val manifest = cloudManifestToShareCommandJson(resolvedManifestYaml)
        // Continue accepting script metadata for manifest compatibility, but do not fetch or
        // execute repository install scripts in this client release. Dependencies are the only
        // automatic installation mechanism currently enabled.
        val installScript = if (includeInstallation && CLOUD_INSTALL_SCRIPTS_ENABLED) {
            manifest.installScript
                ?.takeIf(String::isNotBlank)
                ?.let { installScriptName -> fetchCloudCommandText("$folderUrl/$installScriptName") }
        } else {
            null
        }

        val installation = if (includeInstallation &&
            (manifest.installDependencies.isNotEmpty() || installScript != null)
        ) {
            CloudCommandInstallation(
                commandName = manifest.commandKey,
                dependencies = manifest.installDependencies,
                script = installScript,
                installerVersion = manifest.installerVersion
            )
        } else {
            null
        }

        return ResolvedCloudCommand(
            manifest = manifest,
            installation = installation
        )
    }

    private fun commandsRepositoryChannel(): CommandsRepositoryChannel =
        CommandsRepositoryChannel.fromPreference(
            appPreferences.getStringSync(AppPreferences.COMMANDS_REPOSITORY_CHANNEL)
        )

    private suspend fun installResolvedCommands(resolvedCommands: List<ResolvedCloudCommand>) {
        val commands = jsonService.readCommandsConfig() ?: throw ViewModelError.CommandConfigUnavailable
        val installationStates = resolvedCommands.mapNotNull { resolvedCommand ->
            resolvedCommand.installation?.let { installation ->
                CloudCommandInstallationState(
                    installation = installation,
                    installedInstallerVersion = commands.installedInstallerVersionFor(
                        resolvedCommand.manifest
                    )
                )
            }
        }
        val requestedDependencies = installationStates
            .map { it.installation.dependencies }
            .fold(CloudCommandDependencies()) { combined, dependencies ->
                CloudCommandDependencies(
                    pkg = combined.pkg + dependencies.pkg,
                    pip = combined.pip + dependencies.pip
                )
            }
        val missingDependencies = processManagerService.findMissingTermuxDependencies(
            pkgPackages = requestedDependencies.pkg,
            pipPackages = requestedDependencies.pip
        )
        val pendingInstallations = pendingCloudCommandInstallations(
            states = installationStates,
            missingDependencies = missingDependencies
        )
        runInstallations(pendingInstallations)

        resolvedCommands.forEach { resolvedCommand ->
            commands.add(resolvedCommand.manifest.commandKey, resolvedCommand.manifest.commandObject)
        }

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        jsonService.writeCommandsConfig(gson.toJson(commands))
    }

    private suspend fun runInstallations(installations: List<CloudCommandInstallation>) {
        if (installations.isEmpty()) {
            return
        }

        val installCommand = combinedCloudCommandInstallScript(installations)
        val commandName = if (installations.size == 1) {
            installations.single().commandName
        } else {
            "Cloud commands"
        }
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

private data class ResolvedCloudCommand(
    val manifest: CloudManifestCommand,
    val installation: CloudCommandInstallation?
)

internal data class CloudCommandInstallation(
    val commandName: String,
    val dependencies: CloudCommandDependencies = CloudCommandDependencies(),
    val script: String? = null,
    val installerVersion: String? = null
)

internal data class CloudCommandInstallationState(
    val installation: CloudCommandInstallation,
    val installedInstallerVersion: String?
)

internal data class CloudCommandDependencies(
    val pkg: List<String> = emptyList(),
    val pip: List<String> = emptyList()
) {
    fun isNotEmpty(): Boolean = pkg.isNotEmpty() || pip.isNotEmpty()
}

internal fun pendingCloudCommandInstallations(
    states: List<CloudCommandInstallationState>,
    missingDependencies: MissingTermuxDependencies
): List<CloudCommandInstallation> {
    val remainingPkgPackages = missingDependencies.pkg.toMutableSet()
    val remainingPipPackages = missingDependencies.pip.toMutableSet()

    return states.mapNotNull { state ->
        val installation = state.installation
        val pendingDependencies = CloudCommandDependencies(
            pkg = installation.dependencies.pkg.filter(remainingPkgPackages::remove),
            pip = installation.dependencies.pip.filter(remainingPipPackages::remove)
        )
        val installerVersion = installation.installerVersion?.takeIf(String::isNotBlank)
        val pendingScript = installation.script?.takeUnless {
            installerVersion != null && installerVersion == state.installedInstallerVersion
        }

        installation.copy(
            dependencies = pendingDependencies,
            script = pendingScript
        ).takeIf { pendingDependencies.isNotEmpty() || pendingScript != null }
    }
}

internal fun combinedCloudCommandInstallScript(installations: List<CloudCommandInstallation>): String =
    installations.joinToString(separator = "\n\n", postfix = "\n") { installation ->
        buildString {
            append("printf '%s\\n' ")
            append("Installing ${installation.commandName.sanitizedForShellLabel()}".shellSingleQuote())
            installation.dependencies.pkg.takeIf(List<String>::isNotEmpty)?.let { packages ->
                append("\npkg install -y ")
                append(packages.joinToString(" ") { it.shellSingleQuote() })
            }
            installation.dependencies.pip.takeIf(List<String>::isNotEmpty)?.let { packages ->
                append("\npip install ")
                append(packages.joinToString(" ") { it.shellSingleQuote() })
            }
            installation.script?.takeIf(String::isNotBlank)?.let { script ->
                append('\n')
                append(script.trimEnd())
            }
        }
    }

private fun String.sanitizedForShellLabel(): String = replace('\n', ' ').replace('\r', ' ')

private fun String.shellSingleQuote(): String = "'${replace("'", "'\\''")}'"

class GetCloudCommandDocumentation(private val appPreferences: AppPreferences) {
    operator fun invoke(commandId: String): CloudCommandDocumentation {
        val channel = CommandsRepositoryChannel.fromPreference(
            appPreferences.getStringSync(AppPreferences.COMMANDS_REPOSITORY_CHANNEL)
        )
        val folderUrl = cloudCommandFolderUrl(commandId, channel)
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
    val installDependencies: CloudCommandDependencies,
    val installerVersion: String?,
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
    val dependencies = install.mapValue("dependencies", required = false)
    val id = manifest.stringValue("id")
    val version = manifest.stringValue("version", required = false)
    val installerVersion = install.stringValue("installerVersion", required = false)
    val name = manifest.stringValue("name").ifBlank { id }
    val summary = manifest.stringValue("summary", required = false)
    val commandSlug = manifest.stringValue("commandSlug", required = false)
    val commandType = manifest.commandType(runtime)
    val commandObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("version", version)
        installerVersion.takeIf(String::isNotBlank)
            ?.let { addProperty("installerVersion", it) }
        summary.takeIf(String::isNotBlank)?.let { addProperty("summary", it) }
        addProperty("type", commandType.name)
        addProperty("path", "")
        addProperty("exec", commandSlug)
        addProperty("command", "")
    }

    if (runtime.booleanValue("multiStage", required = false)) {
        val steps = runtime.listValue("steps").mapIndexed { index, stepValue ->
            val step = stepValue.asMap()
            JsonObject().apply {
                addProperty(
                    "id",
                    step.stringValue("id", required = false).ifBlank { index.toString() }
                )
                step.stringValue("commandId", required = false)
                    .takeIf(String::isNotBlank)
                    ?.let { addProperty("commandId", it) }
                addProperty("path", step.stringValue("path", required = false))
                addProperty(
                    "command",
                    step.stringValue("command", required = false)
                )
                step.stringValue("commandSlug", required = false)
                    .takeIf(String::isNotBlank)
                    ?.let { addProperty("exec", it) }
                step.stringListValue("flags")?.let { flags ->
                    add("flags", Gson().toJsonTree(flags))
                }
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

    runtime.stringListValue("flags")?.let { flags ->
        commandObject.add("flags", Gson().toJsonTree(flags))
    }

    when (commandType) {
        CommandType.FILE_OBSERVER -> {
            runtime.stringListValue("selectors")?.let {
                commandObject.add("selectors", Gson().toJsonTree(it))
            }
        }
        CommandType.CRON -> {
            commandObject.addProperty("cronInterval", runtime.stringValue("cronInterval"))
        }
        CommandType.SHARE,
        CommandType.MANUAL -> Unit
    }

    return CloudManifestCommand(
        commandKey = name,
        commandObject = commandObject,
        installDependencies = CloudCommandDependencies(
            pkg = dependencies.stringListValue("pkg").orEmpty(),
            pip = dependencies.stringListValue("pip").orEmpty()
        ),
        installerVersion = installerVersion.takeIf(String::isNotBlank),
        installScript = install.stringValue("script", required = false)
    )
}

private fun JsonObject.installedInstallerVersionFor(manifest: CloudManifestCommand): String? {
    val commandId = manifest.commandObject.get("id")?.asString
    val installedCommand = get(manifest.commandKey)
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?: entrySet().asSequence()
            .map { it.value }
            .filter { it.isJsonObject }
            .map { it.asJsonObject }
            .firstOrNull { it.get("id")?.asString == commandId }

    return installedCommand
        ?.get("installerVersion")
        ?.takeIf { it.isJsonPrimitive }
        ?.asString
        ?.takeIf(String::isNotBlank)
}

private fun Map<String, Any?>.commandType(runtime: Map<String, Any?>): CommandType = when (
    runtime.stringValue("type", required = false)
        .ifBlank { stringValue("type", required = false) }
        .uppercase()
) {
    "", "PACKAGE", "SHARE" -> CommandType.SHARE
    "MANUAL" -> CommandType.MANUAL
    "FILE_OBSERVER" -> CommandType.FILE_OBSERVER
    "CRON" -> CommandType.CRON
    else -> throw ViewModelError.InvalidCommandRepoFile
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
                if (extra.containsKey("required")) {
                    addProperty("required", extra.booleanValue("required", required = false))
                }
                if (extra.containsKey("defaultBoolean")) {
                    addProperty("defaultBoolean", extra.booleanValue("defaultBoolean", required = false))
                }
                extra["selectableOptions"]?.let { selectableOptions ->
                    add("selectableOptions", Gson().toJsonTree(selectableOptions))
                }
                extra["visibleWhen"]?.let { visibleWhen ->
                    add("visibleWhen", Gson().toJsonTree(visibleWhen))
                }
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

internal fun cloudCommandFolderUrl(
    commandId: String,
    channel: CommandsRepositoryChannel = CommandsRepositoryChannel.MAIN
): String {
    val commandPath = commandId.trim().split(".")
        .filter(String::isNotBlank)
        .joinToString("/")

    if (commandPath.isBlank()) throw ViewModelError.CommandNotFound

    return CommandsRepositoryUrls.commandFolder(channel, commandPath)
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

private const val MAX_PARALLEL_COMMAND_FETCHES = 4
private const val CLOUD_INSTALL_SCRIPTS_ENABLED = false
