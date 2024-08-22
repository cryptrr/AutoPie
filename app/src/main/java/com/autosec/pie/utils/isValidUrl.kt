package com.autosec.pie.utils

import android.util.Patterns

fun String?.isValidUrl(): Boolean {
    return this != null && Patterns.WEB_URL.matcher(this).matches()
}