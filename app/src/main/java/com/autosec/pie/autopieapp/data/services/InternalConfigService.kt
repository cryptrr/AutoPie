package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.hasFlag
import com.tencent.mmkv.MMKV

class InternalConfigService(
    private val mmkv: MMKV = requireNotNull(MMKV.mmkvWithID(STORE_ID))
) {
    fun get(commandId: String, extraId: String): String? {
        if (commandId.isBlank() || extraId.isBlank()) return null
        val key = storageKey(commandId, extraId)
        return if (mmkv.containsKey(key)) mmkv.decodeString(key) else null
    }

    fun set(commandId: String, extraId: String, value: String): Boolean {
        if (commandId.isBlank() || extraId.isBlank()) return false
        return mmkv.encode(storageKey(commandId, extraId), value)
    }

    fun resolve(commandId: String, extra: CommandExtra): CommandExtra {
        if (!extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG)) return extra
        val value = get(commandId, extra.id) ?: return extra
        return extra.withInternalConfigValue(value)
    }

    private fun storageKey(commandId: String, extraId: String): String =
        "${commandId.length}:$commandId${extraId.length}:$extraId"

    private companion object {
        const val STORE_ID = "internal-config"
    }
}

private fun CommandExtra.withInternalConfigValue(value: String): CommandExtra = when (type) {
    "BOOLEAN" -> value.trim().lowercase().toBooleanStrictOrNull()
        ?.let { copy(defaultBoolean = it) }
        ?: this
    "FLAG" -> copy(defaultBoolean = value.isNotEmpty())
    "MULTI_SELECTABLE", "MULTI_SELECTABLE_FLAT" -> copy(default = value)
    "STRING", "SELECTABLE", "SELECTABLE_FLAT" -> {
        if (value.isNotBlank()) copy(default = value) else this
    }
    else -> this
}
