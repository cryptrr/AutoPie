package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.CommandExtra
import com.autopi.autopieapp.data.CommandInterface
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.configCommandIds
import com.autopi.autopieapp.data.hasFlag
import com.autopi.autopieapp.data.isSecretExtra
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

    fun delete(commandId: String, extraId: String): Boolean {
        if (commandId.isBlank() || extraId.isBlank()) return false
        mmkv.removeValueForKey(storageKey(commandId, extraId))
        return true
    }

    fun sync(commandId: String, extra: CommandExtra): Boolean {
        if (!extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) || extra.isSecretExtra()) {
            return delete(commandId, extra.id)
        }

        val value = when (extra.type) {
            "BOOLEAN" -> extra.defaultBoolean.toString()
            "FLAG" -> if (extra.defaultBoolean) extra.default else ""
            "MULTI_SELECTABLE", "MULTI_SELECTABLE_FLAT" -> extra.default
            "STRING", "TEXT", "SELECTABLE", "SELECTABLE_FLAT", "BUTTON" -> extra.default.takeIf(String::isNotBlank)
            else -> null
        }
        return if (value == null) delete(commandId, extra.id) else set(commandId, extra.id, value)
    }

    internal fun resolve(command: CommandInterface, extra: CommandExtra): CommandExtra {
        if (!extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) || extra.isSecretExtra()) return extra
        val value = get(command, extra) ?: return extra
        return extra.withInternalConfigValue(value)
    }

    private fun storageKey(commandId: String, extraId: String): String =
        "${commandId.length}:$commandId${extraId.length}:$extraId"

    private companion object {
        const val STORE_ID = "internal-config"
    }
}

internal fun InternalConfigService.get(command: CommandInterface, extra: CommandExtra): String? =
    command.configCommandIds().firstNotNullOfOrNull { get(it, extra.id) }

private fun CommandExtra.withInternalConfigValue(value: String): CommandExtra = when (type) {
    "BOOLEAN" -> value.trim().lowercase().toBooleanStrictOrNull()
        ?.let { copy(defaultBoolean = it) }
        ?: this
    "FLAG" -> copy(defaultBoolean = value.isNotEmpty())
    "MULTI_SELECTABLE", "MULTI_SELECTABLE_FLAT" -> copy(default = value)
    "STRING", "TEXT", "SELECTABLE", "SELECTABLE_FLAT", "BUTTON" -> {
        if (value.isNotBlank()) copy(default = value) else this
    }
    else -> this
}
