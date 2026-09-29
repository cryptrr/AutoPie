package com.autopi.utils

import android.util.Patterns
import com.autopi.autopieapp.data.CommandResult
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.ProcessResult

private val HTTP_URL_REGEX = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

fun String?.isValidUrl(): Boolean {
    return this != null && Patterns.WEB_URL.matcher(this).matches()
}

fun String?.containsValidUrl(): Boolean {
    if(this == null) return false
    val matcher = Patterns.WEB_URL.matcher(this)
    return matcher.find()
}

fun String?.containsValidHttpUrl(): Boolean {
    return extractHttpUrls().isNotEmpty()
}

fun String?.extractHttpUrls(): List<String> {
    if (this == null) return emptyList()
    return HTTP_URL_REGEX.findAll(this)
        .map { match -> match.value.trimEnd('.', ',', ';', '!', ')', ']', '}') }
        .filter { it.isNotEmpty() }
        .toList()
}

fun String?.extractFirstUrl(): String? {
    if (this == null) return null
    val matcher = Patterns.WEB_URL.matcher(this)
    return if (matcher.find()) matcher.group() else null
}

fun String?.extractAllUrls(): String? {
    if (this == null) return null
    val matcher = Patterns.WEB_URL.matcher(this)
    val urls = mutableListOf<String>()
    while (matcher.find()) {
        urls.add(matcher.group())
    }
    return urls.joinToString(" ")
}

fun ProcessResult.toCommandResult(jobType: JobType,jobKey: String): CommandResult {
    return CommandResult(
        key = this.key,
        processId = this.processId,
        success = this.success,
        output = this.output,
        jobType = jobType,
        jobKey = jobKey,
        partial = this.partial,
        exportedOutput = this.exportedOutput
    )
}
