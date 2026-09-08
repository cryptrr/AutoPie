package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal data class ShareCommandRequest(
    val command: CommandModel,
    val inputText: String?,
    val inputFiles: List<String>,
    val extras: List<CommandExtraInput>
)

internal fun parseShareCommandRequest(
    commandJson: String?,
    inputText: String?,
    filesJson: String?,
    extrasJson: String?
): ShareCommandRequest {
    val gson = Gson()
    val command = requireNotNull(gson.fromJson(commandJson, CommandModel::class.java)) {
        "Missing SHARE command"
    }
    requireNotNull(command.command) { "Missing command script" }
    val files = gson.fromJson<List<String?>?>(filesJson, object : TypeToken<List<String?>>() {}.type)
        .orEmpty().map { requireNotNull(it) { "Null input file" } }
    val extras = gson.fromJson<List<CommandExtraInput?>?>(
        extrasJson, object : TypeToken<List<CommandExtraInput?>>() {}.type
    ).orEmpty().map { requireNotNull(it) { "Null command extra" } }
    return ShareCommandRequest(command, inputText, files, extras)
}
