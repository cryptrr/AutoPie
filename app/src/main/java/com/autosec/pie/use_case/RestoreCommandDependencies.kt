package com.autopi.use_case

import com.autopi.autopieapp.data.CommandsRepositoryChannel
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class DependencyRestorePlan(
    val matchedIds: List<String>,
    val unresolved: List<String>,
    val failedIds: List<String>,
    val pkg: List<String>,
    val pip: List<String>,
    val missingPkg: List<String>,
    val missingPip: List<String>,
    val refreshedCommands: JsonObject? = null,
    val updatedCount: Int = 0,
) {
    val missingCount: Int get() = missingPkg.size + missingPip.size
}

internal fun restoredRecipeIds(commands: JsonObject): Pair<List<String>, List<String>> {
    val ids = linkedSetOf<String>()
    val unidentified = mutableListOf<String>()
    commands.entrySet().forEach { (name, value) ->
        if (!value.isJsonObject) {
            unidentified += name
            return@forEach
        }
        val command = value.asJsonObject
        val id = command.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.trim().orEmpty()
        if (id.isEmpty()) unidentified += name else ids += id
        command.get("steps")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { step ->
            if (step.isJsonObject) {
                step.asJsonObject.get("commandId")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString?.trim()?.takeIf { it.isNotEmpty() }?.let(ids::add)
            }
        }
    }
    return ids.toList() to unidentified
}

/** Resolves command replacements and dependencies from the same recipes. */
class RestoreCommandDependencies(
    private val processManager: ProcessManagerService,
    private val fetchManifest: (String) -> String = ::fetchCloudCommandText,
) {
    suspend fun plan(
        commands: JsonObject,
        catalogIds: Set<String>,
        channel: CommandsRepositoryChannel,
    ): DependencyRestorePlan = coroutineScope {
        val (ids, unidentified) = restoredRecipeIds(commands)
        val matched = ids.filter { it in catalogIds }
        val semaphore = Semaphore(4)
        val results = matched.map { id ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val manifest = cloudManifestToShareCommandJson(
                            fetchManifest("${cloudCommandFolderUrl(id, channel)}/manifest.yaml")
                        )
                        require(manifest.commandObject.get("id").asString == id)
                        id to manifest
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        id to null
                    }
                }
            }
        }.awaitAll()
        val pkg = results.flatMap { it.second?.installDependencies?.pkg.orEmpty() }.distinct()
        val pip = results.flatMap { it.second?.installDependencies?.pip.orEmpty() }.distinct()
        val replacements = results.mapNotNull { it.second }
        val refreshed = replaceRestoredCommands(commands, replacements)
        val missing = processManager.findMissingTermuxDependencies(pkg, pip)
        DependencyRestorePlan(
            matched, unidentified + ids.filterNot { it in catalogIds },
            results.filter { it.second == null }.map { it.first },
            pkg, pip, missing.pkg, missing.pip,
            refreshed,
            replacements.count { manifest -> commands.entrySet().any {
                it.value.isJsonObject && it.value.asJsonObject.get("id") == manifest.commandObject.get("id")
            } }
        )
    }

    suspend fun install(plan: DependencyRestorePlan): Boolean {
        // Check again in case packages were installed since the preview was opened.
        val missing = processManager.findMissingTermuxDependencies(plan.pkg, plan.pip)
        if (missing.pkg.isEmpty() && missing.pip.isEmpty()) return false
        processManager.openRestoreInstallation(restoreInstallScript(missing.pkg, missing.pip))
        return true
    }
}

internal fun replaceRestoredCommands(commands: JsonObject, manifests: List<CloudManifestCommand>): JsonObject {
    val result = commands.deepCopy()
    val replacements = manifests.filter { manifest -> commands.entrySet().any {
        it.value.isJsonObject && it.value.asJsonObject.get("id") == manifest.commandObject.get("id")
    } }
    val ids = replacements.map { it.commandObject.get("id") }.toSet()
    commands.entrySet().forEach { (key, value) ->
        if (value.isJsonObject && value.asJsonObject.get("id") in ids) result.remove(key)
    }
    replacements.forEach { manifest ->
        val id = manifest.commandObject.get("id").asString
        var key = manifest.commandKey
        var suffix = 1
        // A catalog rename must never overwrite an unrelated local command.
        while (result.has(key)) {
            key = "${manifest.commandKey} ($id${if (suffix == 1) "" else "-$suffix"})"
            suffix++
        }
        result.add(key, manifest.commandObject.deepCopy())
    }
    return result
}

internal fun restoreInstallScript(pkg: List<String>, pip: List<String>): String = buildString {
    appendLine("failed=(); installed=0")
    val packages = pkg.distinct().map { "pkg" to it } + pip.distinct().map { "pip" to it }
    packages.forEachIndexed { index, (manager, name) ->
        require(name.isNotBlank() && !name.startsWith("-"))
        val quoted = "'${name.replace("'", "'\\''")}'"
        appendLine("printf '\\n[%s/${packages.size}] %s: %s\\n' '${index + 1}' '$manager' $quoted")
        val install = if (manager == "pkg") "pkg install -y $quoted" else "pip install $quoted"
        appendLine("if $install; then installed=\$((installed + 1)); else failed+=(\"$manager: \"$quoted); fi")
    }
    appendLine("printf '\\nInstallation finished. Successful: %s. Failed: %s.\\n' \"\$installed\" \"\${#failed[@]}\"")
    appendLine("if (( \${#failed[@]} )); then printf 'Failed: %s\\n' \"\${failed[@]}\"; fi")
    appendLine("printf '%s\\n' 'Use Settings > Update commands / retry packages to recheck, or fix packages here manually.'")
    appendLine("(( \${#failed[@]} == 0 ))")
}
