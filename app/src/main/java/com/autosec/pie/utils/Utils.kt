package com.autosec.pie.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

        @RequiresApi(Build.VERSION_CODES.Q)
        fun getAbsolutePathFromUri2(context: Context, uri: Uri): String? {
            // Try MediaStore first
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                if (isExternalStorageDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    val type = split[0]
                    val relativePath = split.getOrNull(1)

                    val fullPath = when (type) {
                        "primary" -> "/storage/emulated/0/$relativePath"
                        else -> "/storage/$type/$relativePath"
                    }
                    return fullPath
                } else if (isDownloadsDocument(uri)) {
                    val id = DocumentsContract.getDocumentId(uri)
                    if (id.startsWith("raw:")) {
                        return id.removePrefix("raw:")
                    }
                    val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    return getDataColumn(context, contentUri, "_id=?", arrayOf(id))
                } else if (isMediaDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    val type = split[0]
                    val id = split[1]

                    val contentUri = when (type) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> null
                    }

                    return contentUri?.let {
                        getDataColumn(context, it, "_id=?", arrayOf(id))
                    }
                } else {
                    return getDataColumn(context, uri, null, null)
                }
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path
            }
            return null
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun getRelativePathFromUri(context: Context, uri: Uri): String? {
            val absolutePath = getAbsolutePathFromUri2(context, uri)
            absolutePath ?: return null

            val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
            return if (absolutePath.startsWith(externalStoragePath)) {
                absolutePath.removePrefix("$externalStoragePath/")
            } else {
                null
            }
        }

        fun getDataColumn(
            context: Context,
            uri: Uri,
            selection: String?,
            selectionArgs: Array<String>?
        ): String? {
            val column = "_data"
            val projection = arrayOf(column)
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndexOrThrow(column)
                        return cursor.getString(index)
                    }
                }
            return null
        }


        fun timeAgo(instantString: String, zone: ZoneId = ZoneId.systemDefault()): String {
            val inputInstant = Instant.parse(instantString)
            val nowInstant = Instant.now()

            val diffSeconds = ChronoUnit.SECONDS.between(inputInstant, nowInstant)

            if (diffSeconds < 0) return "in the future"

            return when {
                diffSeconds < 60 -> "$diffSeconds second${if (diffSeconds != 1L) "s" else ""} ago"
                diffSeconds < 3600 -> {
                    val minutes = diffSeconds / 60
                    "$minutes minute${if (minutes != 1L) "s" else ""} ago"
                }
                diffSeconds < 86400 -> {
                    val hours = diffSeconds / 3600
                    "$hours hour${if (hours != 1L) "s" else ""} ago"
                }
                diffSeconds < 2592000 -> { // ~30 days
                    val days = diffSeconds / 86400
                    "$days day${if (days != 1L) "s" else ""} ago"
                }
                diffSeconds < 31536000 -> { // ~365 days
                    val months = diffSeconds / 2592000
                    "$months month${if (months != 1L) "s" else ""} ago"
                }
                else -> {
                    val years = diffSeconds / 31536000
                    "$years year${if (years != 1L) "s" else ""} ago"
                }
            }
        }

        fun getFileWithPrefix(dirPath: String, prefix: String): File? {
            val dir = File(dirPath)
            return dir.listFiles { file -> Timber.d("LOG FILES in cache: ${file.name}");file.name.startsWith(prefix) }
                ?.firstOrNull()
        }


    }





}

class Throttler(
    private val waitMs: Long,
    private val coroutineScope: CoroutineScope = GlobalScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private var lastRunTime = 0L
    private var lastJob: Job? = null

    fun run(block: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRunTime >= waitMs) {
            lastRunTime = currentTime
            lastJob?.cancel()
            lastJob = coroutineScope.launch(dispatcher) {
                block()
            }
        }
    }
}

fun isExternalStorageDocument(uri: Uri): Boolean =
    "com.android.externalstorage.documents" == uri.authority

fun isDownloadsDocument(uri: Uri): Boolean =
    "com.android.providers.downloads.documents" == uri.authority

fun isMediaDocument(uri: Uri): Boolean =
    "com.android.providers.media.documents" == uri.authority

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