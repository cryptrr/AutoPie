package com.autosec.pie.utils

import android.util.Patterns

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