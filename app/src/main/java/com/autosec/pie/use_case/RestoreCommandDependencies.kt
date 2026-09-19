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

/** Repairs packages only; never writes command definitions or runs recipe scripts. */
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
                        id to manifest.installDependencies
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        id to null
                    }
                }
            }
        }.awaitAll()
        val pkg = results.flatMap { it.second?.pkg.orEmpty() }.distinct()
        val pip = results.flatMap { it.second?.pip.orEmpty() }.distinct()
        val missing = processManager.findMissingTermuxDependencies(pkg, pip)
        DependencyRestorePlan(
            matched, unidentified + ids.filterNot { it in catalogIds },
            results.filter { it.second == null }.map { it.first },
            pkg, pip, missing.pkg, missing.pip
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
    appendLine("printf '%s\\n' 'Use Settings > Check / retry command packages to recheck, or fix packages here manually.'")
    appendLine("(( \${#failed[@]} == 0 ))")
}
