package com.autopi.autopieapp.output

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI

internal sealed interface OutputElement {
    data class Text(val value: String, val numeric: Boolean = false, val json: Boolean = false) : OutputElement
    data class Image(val source: String, val caption: String?) : OutputElement
    data class Link(val label: String, val url: String) : OutputElement
    data class Items(val title: String?, val values: List<OutputElement>) : OutputElement
}

/** Typed objects opt into presentation; unrecognized or malformed objects retain their JSON. */
internal fun parseOutputPresentation(raw: String?): OutputElement {
    if (raw == null) return OutputElement.Text("No output was produced.")
    if (raw.isBlank()) return OutputElement.Text("Empty output")
    return runCatching { present(JsonParser.parseString(raw), 0) }
        .getOrElse { OutputElement.Text(raw) }
}

private fun present(value: JsonElement, depth: Int): OutputElement {
    fun fallback() = OutputElement.Text(GsonBuilder().setPrettyPrinting().create().toJson(value), json = true)
    if (depth >= 20) return fallback()
    if (value.isJsonPrimitive) {
        val primitive = value.asJsonPrimitive
        return OutputElement.Text(primitive.asString, numeric = primitive.isNumber)
    }
    if (value.isJsonArray) return OutputElement.Items(null, value.asJsonArray.map { present(it, depth + 1) })
    if (!value.isJsonObject) return fallback()
    val obj = value.asJsonObject
    fun string(key: String): String? = obj.get(key)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString
    return when (string("type")) {
        "text" -> string("text")?.let { OutputElement.Text(it) } ?: fallback()
        "image" -> (string("src") ?: string("path"))?.takeIf { it.isNotBlank() }?.let {
            OutputElement.Image(it, string("caption") ?: string("alt"))
        } ?: fallback()
        "link" -> string("url")?.takeIf(::isOutputWebUrl)?.let {
            OutputElement.Link(string("text") ?: string("title") ?: it, it)
        } ?: fallback()
        "list", "column" -> obj.get("items")?.takeIf { it.isJsonArray }?.let {
            OutputElement.Items(string("title"), it.asJsonArray.map { item -> present(item, depth + 1) })
        } ?: fallback()
        else -> fallback()
    }
}

internal fun isOutputWebUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
}.getOrDefault(false)
