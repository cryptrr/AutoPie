package com.autopi.logging
import android.util.Log
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord

class FileLoggingTree(
    private val appPreferences: AppPreferences,
    private val autoPieConfigPathProvider: AutoPieConfigPathProvider,
) : Timber.DebugTree() {
    private val maxFileSize = 100 * 1024 // 100 KB
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var fileLoggingEnabled = appPreferences.getBoolSync(AppPreferences.FILE_LOGGING_ENABLED, false)

    @Volatile
    private var logsDirectory = autoPieConfigPathProvider.getLogsDirectory()

    init {
        scope.launch {
            appPreferences.getBool(AppPreferences.FILE_LOGGING_ENABLED, false).collectLatest {
                fileLoggingEnabled = it
            }
        }

        scope.launch {
            autoPieConfigPathProvider.locationFlow.collectLatest {
                logsDirectory = autoPieConfigPathProvider.getLogsDirectory()
            }
        }

//        @RequiresApi(Build.VERSION_CODES.R)
//        if(Environment.isExternalStorageManager()){
//            try {
//                setupLogRedirector()
//            }catch (e: Exception){
//                e.printStackTrace()
//            }
//        }
    }

    // Format the timestamp for log entries
    private fun getCurrentTimeStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            if (!fileLoggingEnabled) return

            val logFile = File(logsDirectory, "autopie.log")
            logFile.parentFile?.mkdirs()

            if (logFile.length() > maxFileSize) {
                trimLogFile(logFile)
            }

            val timestamp = getCurrentTimeStamp()
            val logMessage = "$timestamp [${getPriorityString(priority)}] $tag: $message\n"
            appendLogToFile(logFile, logMessage)
        } catch (e: Exception) {
            println("Unable to write AutoPie file log")
        }
    }

    // Append log message to the file
    private fun appendLogToFile(logFile: File, logMessage: String) {
        val writer = FileWriter(logFile, true)
        writer.append(logMessage)
        writer.flush()
        writer.close()
    }

    // Trim log file by keeping only the last 50% of lines (can adjust as needed)
    private fun trimLogFile(logFile: File) {
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

    fun setupLogRedirector() {
        // Get the root logger
        val logManager = LogManager.getLogManager()
        val rootLogger = logManager.getLogger("")

        // Remove default handlers
        val handlers = rootLogger.handlers
        for (handler in handlers) {
            rootLogger.removeHandler(handler)
        }

        // Add a custom handler that passes logs to Timber
        rootLogger.addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                // Redirect logs to Timber
                val priority = getLogPriority(record.level)
                Timber.tag(record.loggerName).log(priority, record.message)
            }

            override fun flush() {}

            override fun close() {}
        })
    }

    // Map Java log levels to Android log priorities
    private fun getLogPriority(level: Level): Int {
        return when (level) {
            Level.SEVERE -> Log.ERROR
            Level.WARNING -> Log.WARN
            Level.INFO -> Log.INFO
            Level.FINE -> Log.DEBUG
            Level.FINER, Level.FINEST -> Log.VERBOSE
            else -> Log.DEBUG
        }
    }
}
