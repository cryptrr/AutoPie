package com.autopi.utils

import android.content.Context
import android.content.ContentUris
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import com.autopi.autopieapp.data.CommandInterface
import com.autopi.autopieapp.data.ScriptFlags
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
                Timber.d("Not a shell script")
                return false
            }

            val firstLine = file.useLines { it.firstOrNull() ?: "" }

            Timber.d("First line: $firstLine")

            // Check if the file has a shebang and is for a shell interpreter
            return firstLine.startsWith("#!") && (firstLine.contains("bash") || firstLine.contains("sh"))
        }

        fun isPythonScript(command: String): Boolean {
            return hasScriptHeader(command, ScriptFlags.PYTHON)
        }

        fun isInteractiveCommand(command: String): Boolean {
            return hasScriptHeader(command, ScriptFlags.INTERACTIVE)
        }

        fun isOpenLogsCommand(command: String): Boolean {
            return hasScriptHeader(command, ScriptFlags.OPEN_LOGS)
        }

        fun setScriptHeader(command: String, flag: ScriptFlags, enabled: Boolean): String {
            return if (enabled) {
                command.withScriptHeader(flag.value)
            } else {
                command.withoutScriptHeader(flag.value)
            }
        }

        fun hasScriptHeader(command: String, flag: ScriptFlags): Boolean {
            return scriptHeaders(command).any { it.startsWith(flag.value) }
        }

        fun stripScriptHeaders(command: String): String {
            return command.lineSequence()
                .dropWhile { it.isScriptHeaderLine() }
                .joinToString("\n")
        }

        private fun scriptHeaders(command: String): List<String> {
            return command.lineSequence()
                .map { it.trim() }
                .takeWhile { it.isScriptHeaderLine() }
                .toList()
        }

        private fun String.isScriptHeaderLine(): Boolean {
            val trimmed = trim()
            return trimmed.startsWith("#@") || trimmed.startsWith("//@")
        }

        private fun String.withScriptHeader(header: String): String {
            if (scriptHeaders(this).any { it.startsWith(header) }) return this

            val lines = lines().toMutableList()
            val insertIndex = lines.indexOfFirst { !it.isScriptHeaderLine() }
                .let { if (it == -1) lines.size else it }

            lines.add(insertIndex, header)
            return lines.joinToString("\n")
        }

        private fun String.withoutScriptHeader(header: String): String {
            var readingHeaders = true
            return lineSequence()
                .filter { line ->
                    val trimmedLine = line.trim()
                    if (readingHeaders && trimmedLine.isScriptHeaderLine()) {
                        !trimmedLine.startsWith(header)
                    } else {
                        readingHeaders = false
                        true
                    }
                }
                .joinToString("\n")
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

        fun sanitizeAndroidFilename(filename: String): String {
            return filename
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "")
                .trim()
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
            val log = Timber.tag("UriResolver")
            log.d(
                "Resolving URI: uri=%s scheme=%s authority=%s mimeType=%s",
                uri,
                uri.scheme,
                uri.authority,
                context.contentResolver.getType(uri)
            )

            if ("content".equals(uri.scheme, ignoreCase = true)) {
                if (isExternalStorageDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":", limit = 2)
                    val type = split[0]
                    val relativePath = split.getOrNull(1)

                    if (relativePath == null) {
                        log.w("External-storage document has no relative path: docId=%s", docId)
                        return null
                    }

                    val fullPath = when (type) {
                        "primary" -> "/storage/emulated/0/$relativePath"
                        else -> "/storage/$type/$relativePath"
                    }
                    log.d(
                        "Resolved external-storage document: docId=%s volume=%s path=%s",
                        docId,
                        type,
                        fullPath
                    )
                    return fullPath
                } else if (isDownloadsDocument(uri)) {
                    val id = DocumentsContract.getDocumentId(uri)
                    log.d("Downloads document branch: documentId=%s", id)
                    if (id.startsWith("raw:")) {
                        val rawPath = id.removePrefix("raw:")
                        log.d("Resolved raw downloads path: %s", rawPath)
                        return rawPath
                    }

                    getMediaStorePath(context, uri)?.let {
                        log.d("Resolved directly from DownloadsProvider URI: %s", it)
                        return it
                    }

                    // DownloadsProvider may return IDs such as "msf:1234" or
                    // "msd:1234". MediaStore expects only the numeric suffix.
                    val mediaStoreId = id.substringAfterLast(":").toLongOrNull()
                    if (mediaStoreId == null) {
                        log.w("Downloads document ID has no numeric MediaStore suffix: %s", id)
                        return null
                    }
                    val candidateUris = listOf(
                        ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            mediaStoreId
                        ),
                        ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"),
                            mediaStoreId
                        )
                    )

                    candidateUris.forEach { contentUri ->
                        log.d("Trying Downloads MediaStore candidate: %s", contentUri)
                        getMediaStorePath(context, contentUri)?.let {
                            log.d("Resolved Downloads MediaStore candidate: %s", it)
                            return it
                        }
                    }
                    log.w("Unable to resolve Downloads document: uri=%s documentId=%s", uri, id)
                    return null
                } else if (isMediaDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":", limit = 2)
                    val type = split[0]
                    val id = split.getOrNull(1)

                    if (id == null) {
                        log.w("Media document has no item ID: documentId=%s", docId)
                        return null
                    }

                    log.d("Media document branch: documentId=%s type=%s itemId=%s", docId, type, id)

                    val contentUri = when (type) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        // PDFs, text files, and other non-media documents use
                        // the generic MediaStore Files collection.
                        "document", "file" -> MediaStore.Files.getContentUri("external")
                        else -> {
                            log.w("Unsupported MediaDocumentsProvider type: %s", type)
                            null
                        }
                    }

                    return contentUri?.let { mediaUri ->
                        getMediaStorePath(context, mediaUri, "_id=?", arrayOf(id)).also { path ->
                            if (path == null) {
                                log.w(
                                    "MediaStore query did not resolve document: type=%s itemId=%s collection=%s",
                                    type,
                                    id,
                                    mediaUri
                                )
                            } else {
                                log.d("Resolved media document path: %s", path)
                            }
                        }
                    }
                } else {
                    log.d("Generic content-provider branch: authority=%s", uri.authority)
                    return getMediaStorePath(context, uri).also { path ->
                        if (path == null) log.w("Generic provider did not expose a filesystem path: %s", uri)
                    }
                }
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                log.d("File URI branch resolved path: %s", uri.path)
                return uri.path
            }
            log.w("Unsupported URI scheme: uri=%s scheme=%s", uri, uri.scheme)
            return null
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun getRelativePathFromUri(context: Context, uri: Uri): String? {
            val log = Timber.tag("UriResolver")
            val absolutePath = getAbsolutePathFromUri2(context, uri)
            if (absolutePath == null) {
                log.w("No absolute path resolved for URI: %s", uri)
                return null
            }

            val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
            return if (absolutePath.startsWith(externalStoragePath)) {
                absolutePath.removePrefix("$externalStoragePath/").also { relativePath ->
                    log.d(
                        "Converted absolute path to relative path: absolute=%s root=%s relative=%s",
                        absolutePath,
                        externalStoragePath,
                        relativePath
                    )
                }
            } else {
                log.w(
                    "Resolved path is outside primary external storage: absolute=%s expectedRoot=%s",
                    absolutePath,
                    externalStoragePath
                )
                null
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun getMediaStorePath(
            context: Context,
            uri: Uri,
            selection: String? = null,
            selectionArgs: Array<String>? = null
        ): String? {
            getDataColumn(context, uri, selection, selectionArgs)?.let { return it }

            val log = Timber.tag("UriResolver")
            val projection = arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME
            )

            return try {
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                    ?.use { cursor ->
                        log.d(
                            "Metadata query: uri=%s rows=%d columns=%s selection=%s args=%s",
                            uri,
                            cursor.count,
                            cursor.columnNames.contentToString(),
                            selection,
                            selectionArgs?.contentToString()
                        )
                        if (!cursor.moveToFirst()) return@use null

                        val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (relativePathIndex < 0 || displayNameIndex < 0) {
                            log.w("Provider omitted relative-path metadata for URI: %s", uri)
                            return@use null
                        }

                        val relativePath = cursor.getString(relativePathIndex)
                        val displayName = cursor.getString(displayNameIndex)
                        if (relativePath.isNullOrBlank() || displayName.isNullOrBlank()) {
                            log.w(
                                "Provider returned incomplete path metadata: relativePath=%s displayName=%s",
                                relativePath,
                                displayName
                            )
                            return@use null
                        }

                        File(Environment.getExternalStorageDirectory(), "$relativePath$displayName")
                            .absolutePath
                            .also { log.d("Resolved path from MediaStore metadata: %s", it) }
                    }
            } catch (exception: Exception) {
                log.w(exception, "Unable to query MediaStore path metadata: uri=%s", uri)
                null
            }
        }

        fun getDataColumn(
            context: Context,
            uri: Uri,
            selection: String?,
            selectionArgs: Array<String>?
        ): String? {
            val log = Timber.tag("UriResolver")
            return try {
                val column = MediaStore.MediaColumns.DATA
                val projection = arrayOf(column)
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                    ?.use { cursor ->
                        log.d(
                            "DATA query: uri=%s rows=%d columns=%s selection=%s args=%s",
                            uri,
                            cursor.count,
                            cursor.columnNames.contentToString(),
                            selection,
                            selectionArgs?.contentToString()
                        )
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(column)
                            if (index >= 0) {
                                cursor.getString(index).also { path ->
                                    log.d("DATA column result: uri=%s path=%s", uri, path)
                                }
                            } else {
                                log.w("Provider omitted DATA column for URI: %s", uri)
                                null
                            }
                        } else {
                            log.w("DATA query returned no rows for URI: %s", uri)
                            null
                        }
                    }
            } catch (exception: Exception) {
                log.w(exception, "Unable to resolve DATA column for URI: %s", uri)
                null
            }
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

fun Modifier.conditional(
    condition: Boolean,
    modifier: Modifier.() -> Modifier
): Modifier {
    return if (condition) {
        then(modifier())
    } else {
        this
    }
}

fun getCommandExec(command: String) : String? {
    return if(Utils.hasScriptHeader(command, ScriptFlags.SHELL)){
        "Shell"
    }
    else if(Utils.hasScriptHeader(command, ScriptFlags.PYTHON)){
        "Python"
    }
    else{
        command.lines()
            .first { !it.startsWith("#") }
            .split(" ")
            .first()
            .takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
    }
}

fun getCommandExec(command: CommandInterface) : String? {
    return if(!command.exec.isNullOrBlank()){
        return command.exec
    }else if(Utils.hasScriptHeader(command.command, ScriptFlags.SHELL)){
        "Shell"
    }
    else if(Utils.hasScriptHeader(command.command, ScriptFlags.PYTHON)){
        "Python"
    }
    else{
        val cmd = if(command.multiStage == true && command.steps.isNotEmpty()){
            command.steps.firstOrNull()?.command ?: return null
        }else{
            command.command
        }

        cmd.lines()
            .first { !it.startsWith("#") }
            .split(" ")
            .first()
            .takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
    }
}
