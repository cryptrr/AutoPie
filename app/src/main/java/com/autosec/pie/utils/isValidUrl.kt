package com.autosec.pie.utils

import android.util.Patterns
import com.autosec.pie.autopieapp.data.CommandResult
import com.autosec.pie.autopieapp.data.JobType
import com.autosec.pie.autopieapp.data.ProcessResult

fun String?.isValidUrl(): Boolean {
    return this != null && Patterns.WEB_URL.matcher(this).matches()
}

fun String?.containsValidUrl(): Boolean {
    if(this == null) return false
    val matcher = Patterns.WEB_URL.matcher(this)
    return matcher.find()
}

fun String?.containsValidHttpUrl(): Boolean {
    if (this == null) return false
    val matcher = Patterns.WEB_URL.matcher(this)
    while (matcher.find()) {
        val url = matcher.group()
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("www.")) {
            return true
        }
    }
    return false
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
    return urls.joinToString{" "}
}

fun ProcessResult.toCommandResult(jobType: JobType,jobKey: String): CommandResult {
    return CommandResult(
        key = this.key,
        processId = this.processId,
        success = this.success,
        output = this.output,
        jobType = jobType,
        jobKey = jobKey
    )
}