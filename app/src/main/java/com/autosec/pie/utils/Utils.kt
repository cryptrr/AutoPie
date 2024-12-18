package com.autosec.pie.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Utils{
    companion object{
        fun getRandomNumericalId(): String {
            val randomDigits = (1..6).map { Random.nextInt(10) }
            return randomDigits.joinToString("")
        }
        fun parseTimeInterval(input: String): Pair<Long, TimeUnit>? {
            if (input.length < 2) return null

            val timeValue = input.dropLast(1).toLongOrNull()
            val timeUnitChar = input.last()

            if (timeValue == null || timeValue <= 0) return null

            return when (timeUnitChar) {
                's' -> Pair(timeValue, TimeUnit.SECONDS)
                'm' -> Pair(timeValue, TimeUnit.MINUTES)
                'h' -> Pair(timeValue, TimeUnit.HOURS)
                else -> null  // Invalid time unit
            }
        }

        fun checkIfIntervalLessThan15Minutes(interval: Pair<Long, TimeUnit>): Boolean {
            val (timeValue, timeUnit) = interval
            val intervalInMillis = timeUnit.toMillis(timeValue)
            val fifteenMinutesInMillis = TimeUnit.MINUTES.toMillis(15)

            return intervalInMillis < fifteenMinutesInMillis
        }

        fun isShellScript(file: File): Boolean {
            if (!file.exists() || !file.canRead()) {
                Timber.d("File does not exist")
                return false
            }

            val firstLine = file.useLines { it.firstOrNull() ?: "" }

            Timber.d("First line: $firstLine")

            // Check if the file has a shebang and is for a shell interpreter
            return firstLine.startsWith("#!") && (firstLine.contains("bash") || firstLine.contains("sh"))
        }

        fun isZipFile(file: File): Boolean {
            if (!file.isFile) return false

            // Check for .zip extension
            if (file.extension == "zip") {
                return true
            }

            // Check the file's magic number
            try {
                FileInputStream(file).use { inputStream ->
                    val magicNumber = ByteArray(2)
                    if (inputStream.read(magicNumber) == 2) {
                        return magicNumber[0] == 0x50.toByte() && magicNumber[1] == 0x4B.toByte() // PK
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return false
        }

        fun isAPath(file: String): Boolean {
            return file.contains("/")
        }
        fun escapeFilePath(filePath: String): String {
            return "\"$filePath\"".replace("'","")
        }

        fun getPathsFromClipData(context: Context, intent: Intent): List<String> {
            val filePaths = mutableListOf<String>()

            // Check if the intent contains ClipData
            val clipData = intent.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    val uri = item.uri
                    val path = getAbsolutePathFromUri(context, uri)
                    if (path != null) {
                        filePaths.add(path)
                    }
                }
            } else {
                // Fallback to data URI if ClipData is not available
                intent.data?.let { uri ->
                    getAbsolutePathFromUri(context, uri)?.let { filePaths.add(it) }
                }
            }

            return filePaths
        }

        fun getAbsolutePathFromUri(context: Context, uri: Uri): String? {
            // Handle "content" scheme URIs
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        return cursor.getString(columnIndex)
                    }
                }
            }
            // Handle "file" scheme URIs
            else if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path
            }
            return null
        }
    }
}

fun Intent.getIntExtraOrNull(key: String): Int? {
    return if (hasExtra(key)) getIntExtra(key, -1) else null
}

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

class Debouncer(
    private val coroutineScope: CoroutineScope,
    private val delayMillis: Long
) {
    private var debounceJob: Job? = null

    fun debounce(function: () -> Unit) {
        debounceJob?.cancel()  // Cancel any previously scheduled job
        debounceJob = coroutineScope.launch {
            delay(delayMillis)  // Wait for the specified delay
            function()  // Execute the function after the delay
        }
    }
}