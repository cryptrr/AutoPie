package com.autosec.pie.logging
import android.content.Context
import android.os.Environment
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FileLoggingTree(context: Context) : Timber.DebugTree() {
    //private val logFile = File(context.filesDir, "autopie_log.txt")
    private val logFile = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/logs/", "autopie.log")
    private val maxFileSize = 100 * 1024 // 100 KB

    // Format the timestamp for log entries
    private fun getCurrentTimeStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            if (logFile.length() > maxFileSize) {
                trimLogFile()
            }

            val timestamp = getCurrentTimeStamp()
            val logMessage = "$timestamp [${getPriorityString(priority)}] $tag: $message\n"
            appendLogToFile(logMessage)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Append log message to the file
    private fun appendLogToFile(logMessage: String) {
        val writer = FileWriter(logFile, true)
        writer.append(logMessage)
        writer.flush()
        writer.close()
    }

    // Trim log file by keeping only the last 50% of lines (can adjust as needed)
    private fun trimLogFile() {
        val lines = logFile.readLines()
        val halfSize = lines.size / 2
        val writer = FileWriter(logFile)
        lines.takeLast(halfSize).forEach { writer.write(it + "\n") }
        writer.flush()
        writer.close()
    }

    // Convert priority to a readable string
    private fun getPriorityString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.WARN -> "WARN"
            Log.INFO -> "INFO"
            Log.DEBUG -> "DEBUG"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
        }
    }
}