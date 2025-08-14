package com.autosec.pie

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.use_case.AutoPieUseCases
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import kotlin.getValue

class PackagesProvider : ContentProvider() {

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {

        val apps = readPackages(context!!)

        val matrix = MatrixCursor(arrayOf("packageName"))
        apps.forEach { matrix.addRow(arrayOf(it)) }
        return matrix
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.autosec.pie"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}

class CommandsProvider : ContentProvider() {

    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {

        val commands = useCases.getCommandsList()

        val matrix = MatrixCursor(arrayOf("command"))
        commands.forEach { matrix.addRow(arrayOf(it.name)) }

        return matrix
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.autosec.pie"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}

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

//TODO: Replace with usecase
private fun readPackages(context: Context): List<String> {

    try {
        val binLocation = File(context.filesDir, "build/bin").listFiles()
        val usrBinLocation = File(context.filesDir, "build/usr/bin")
        val autosecBinLocation = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin")

        val packages = listOf(
            binLocation?.toList() ?: emptyList(),
            usrBinLocation.listFiles()?.toList() ?: emptyList(),
            autosecBinLocation.listFiles()?.toList() ?: emptyList()
        ).flatten().toSet().filter { !it.name.startsWith(".") }


        return packages.map{it.path}.toList()
    } catch (e: Exception) {
        Timber.e(e)
        return emptyList()
    }
}