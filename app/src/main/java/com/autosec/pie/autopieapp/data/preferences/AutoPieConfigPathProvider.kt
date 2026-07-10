package com.autopi.autopieapp.data.preferences

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File

enum class AutoPieConfigLocation(val preferenceValue: String) {
    EXTERNAL_STORAGE("external_storage"),
    APP_DATA_HOME("app_data_home");

    companion object {
        fun fromPreferenceValue(value: String): AutoPieConfigLocation {
            return values().firstOrNull { it.preferenceValue == value } ?: APP_DATA_HOME
        }
    }
}

class AutoPieConfigPathProvider(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    val locationFlow = appPreferences.getString(AppPreferences.AUTOPIE_CONFIG_LOCATION)
        .map { AutoPieConfigLocation.fromPreferenceValue(it) }

    fun getConfigLocationSync(): AutoPieConfigLocation {
        return AutoPieConfigLocation.fromPreferenceValue(
            appPreferences.getStringSync(AppPreferences.AUTOPIE_CONFIG_LOCATION)
        )
    }

    suspend fun setConfigLocation(location: AutoPieConfigLocation) {
        appPreferences.setString(AppPreferences.AUTOPIE_CONFIG_LOCATION, location.preferenceValue)
    }

    fun moveAutoSecDirectoryTo(location: AutoPieConfigLocation): Boolean {
        val currentLocation = getConfigLocationSync()
        if (currentLocation == location) {
            return true
        }

        val source = getAutoSecDirectory(currentLocation)
        val destination = getAutoSecDirectory(location)

        if (source.absolutePath == destination.absolutePath) {
            return true
        }

        if (!source.exists()) {
            destination.mkdirs()
            return true
        }

        destination.parentFile?.mkdirs()

        return try {
            val moved = if (destination.exists()) {
                source.copyRecursively(destination, overwrite = true)
            } else {
                source.renameTo(destination) || source.copyRecursively(destination, overwrite = true)
            }

            if (!moved) {
                Timber.e("Failed to move AutoSec folder from ${source.absolutePath} to ${destination.absolutePath}")
                return false
            }

            if (source.exists() && !source.deleteRecursively()) {
                Timber.w("AutoSec folder copied but source could not be deleted: ${source.absolutePath}")
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to move AutoSec folder from ${source.absolutePath} to ${destination.absolutePath}")
            false
        }
    }

    fun usesExternalStorage(): Boolean {
        return getConfigLocationSync() == AutoPieConfigLocation.EXTERNAL_STORAGE
    }

    fun getCommandBaseDirectory(location: AutoPieConfigLocation = getConfigLocationSync()): File {
        return when (location) {
            AutoPieConfigLocation.EXTERNAL_STORAGE -> Environment.getExternalStorageDirectory()
            AutoPieConfigLocation.APP_DATA_HOME -> File(context.filesDir, "home")
        }
    }

    fun getAutoSecDirectory(location: AutoPieConfigLocation = getConfigLocationSync()): File {
        return File(getCommandBaseDirectory(location), "AutoSec")
    }

    fun getBinDirectory(): File {
        return File(getAutoSecDirectory(), "bin")
    }

    fun getLogsDirectory(): File {
        return File(getAutoSecDirectory(), "logs")
    }

    fun getLogFile(): File {
        return File(getLogsDirectory(), "autopie.log")
    }

    fun getConfigFile(filename: String): File {
        return File(getAutoSecDirectory(), filename)
    }

    fun getInitArchiveFile(): File {
        return File(getAutoSecDirectory(), "autosec.tar.xz")
    }
}
