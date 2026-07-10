package com.autopi

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.data.services.SecretsService
import com.autopi.use_case.AutoPieUseCases
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import kotlin.getValue

//class PackagesProvider : ContentProvider() {
//
//    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
//
//    override fun query(
//        uri: Uri,
//        projection: Array<String>?,
//        selection: String?,
//        selectionArgs: Array<String>?,
//        sortOrder: String?
//    ): Cursor? {
//
//        if(context == null){
//            return null
//        }
//
//        val apps = useCases.getInstalledPackages(context!!.filesDir)
//
//        val matrix = MatrixCursor(arrayOf("packageName"))
//        apps.forEach { matrix.addRow(arrayOf(it)) }
//        return matrix
//    }
//    override fun onCreate() = true
//    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.autosec.pie"
//    override fun insert(uri: Uri, values: ContentValues?) = null
//    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
//    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
//}
//
//class CommandsProvider : ContentProvider() {
//
//    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
//
//    override fun query(
//        uri: Uri,
//        projection: Array<String>?,
//        selection: String?,
//        selectionArgs: Array<String>?,
//        sortOrder: String?
//    ): Cursor? {
//
//        val commands = useCases.getCommandsList()
//
//        val matrix = MatrixCursor(arrayOf("command"))
//        commands.forEach { matrix.addRow(arrayOf(it.name)) }
//
//        return matrix
//    }
//    override fun onCreate() = true
//    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.autosec.pie"
//    override fun insert(uri: Uri, values: ContentValues?) = null
//    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
//    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
//}

class ProcessStatusProvider : ContentProvider() {

    val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {

        val processId = try {
            uri.pathSegments[0].toInt()
        }catch (_: Exception) {null}

        if(processId == null) {
            return null
        }

        Timber.d("PROCESS ID LIST: ${processManagerService.processIds}")
        Timber.d("SUCCESS ID LIST: ${processManagerService.successProcessIds}")
        Timber.d("FAILED ID LIST: ${processManagerService.failedProcessIds}")

        val status = if(processManagerService.successProcessIds.contains(processId)) "completed"
            else if(processManagerService.failedProcessIds.contains(processId)) "failed"
            else if(processManagerService.processIds.contains(processId)) "running"
            else "unknown"

        val matrix = MatrixCursor(arrayOf("processId", "status"))
        matrix.addRow(arrayOf(processId.toString(), status))

        return matrix
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.autosec.pie"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}

class SecretProvider : ContentProvider() {

    private val secretsService: SecretsService by inject(SecretsService::class.java)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val key = keyFromUri(uri) ?: return null
        val columns = projection ?: arrayOf("key", "value")
        val value = secretsService.get(key) ?: return MatrixCursor(columns)

        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    "key" -> key
                    "value" -> value
                    else -> null
                }
            }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val key = keyFromUri(uri) ?: values?.getAsString("key") ?: return null
        val value = values?.getAsString("value") ?: return null

        return if (secretsService.set(key, value)) uri else null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val key = keyFromUri(uri) ?: selectionArgs?.firstOrNull() ?: return 0
        return if (secretsService.delete(key)) 1 else 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val key = keyFromUri(uri) ?: values?.getAsString("key") ?: return 0
        val value = values?.getAsString("value") ?: return 0

        return if (secretsService.set(key, value)) 1 else 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val key = arg ?: extras?.getString("key")
        val result = Bundle()

        when (method) {
            "get" -> {
                if (key != null) {
                    val value = secretsService.get(key)
                    result.putBoolean("found", value != null)
                    if (value != null) result.putString("value", value)
                } else {
                    result.putBoolean("found", false)
                }
            }
            "set" -> {
                val value = extras?.getString("value")
                result.putBoolean("success", key != null && value != null && secretsService.set(key, value))
            }
            "delete" -> {
                result.putBoolean("success", key != null && secretsService.delete(key))
            }
            else -> result.putString("error", "Unsupported method: $method")
        }

        return result
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.autopie.secret"

    private fun keyFromUri(uri: Uri): String? {
        return uri.pathSegments.firstOrNull()?.takeIf { it.isNotEmpty() }
    }
}
