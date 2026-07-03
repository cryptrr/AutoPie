package com.autopi.autopieapp.data.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import timber.log.Timber

data class PartialJsonMap<T>(
    val values: Map<String, T>,
    val skippedKeys: List<String>
)

/** Parses each top-level entry independently so one incompatible item cannot reject the whole file. */
fun <T : Any> Gson.fromJsonObjectEntries(
    jsonObject: JsonObject,
    valueClass: Class<T>
): PartialJsonMap<T> {
    val values = linkedMapOf<String, T>()
    val skippedKeys = mutableListOf<String>()

    jsonObject.entrySet().forEach { (key, json) ->
        try {
            val value = fromJson(json, valueClass)
            if (value == null) {
                skippedKeys += key
            } else {
                values[key] = value
            }
        } catch (exception: Exception) {
            Timber.w(exception, "Skipping incompatible config entry: $key")
            skippedKeys += key
        }
    }

    return PartialJsonMap(values, skippedKeys)
}
