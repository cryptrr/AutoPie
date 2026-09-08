package com.autopi.autopieapp.data.services

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Environment
import android.os.FileObserver
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.core.DispatcherProvider
import com.autopi.use_case.AutoPieUseCases
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class FileObserverJobService : JobService() {
    private val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
    private val preferences: AppPreferences by inject(AppPreferences::class.java)
    private val jsonService: JsonService by inject(JsonService::class.java)
    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.main) }
    private val observers = mutableListOf<DirectoryFileObserver>()
    private var session: Job? = null
    private var activeParams: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        activeParams = params
        startSession(params)
        return true
    }

    // Lifecycle changes and observer installation are serialized on the main dispatcher.
    private fun startSession(params: JobParameters) {
        stopSession()
        session = serviceScope.launch {
            try {
                if (!preferences.getBool(AppPreferences.IS_FILE_OBSERVERS_ON).first()) {
                    finishSession(params)
                    return@launch
                }
                val configs = withContext(dispatchers.io) {
                    val config = jsonService.readCommandsConfig() ?: return@withContext emptyList()
                    Gson().fromJsonObjectEntries(config, CommandModel::class.java).values
                        .filterValues { it.type == CommandType.FILE_OBSERVER }
                        .mapNotNull { (key, command) ->
                            try {
                                val directory = resolveObserverDirectory(
                                    Environment.getExternalStorageDirectory(),
                                    command.path
                                )
                                require(directory.isDirectory && directory.canRead()) { "Unreadable observer directory: $directory" }
                                Triple(command.copy(id = command.id.ifBlank { key }, name = key), directory,
                                    ObserverFileEvents(command.selectors.orEmpty()))
                            } catch (e: Exception) {
                                Timber.e(e, "Skipping invalid file observer: $key")
                                null
                            }
                        }
                }
                for ((command, directory, events) in configs) {
                    val observer = DirectoryFileObserver(directory, events) { file ->
                        useCases.runCommandForFiles(command, null, listOf(file.absolutePath), emptyList(),
                            (100000..999999).random()).collect { result ->
                            if (!result.success) Timber.w("File observer command failed: ${command.name}")
                        }
                    }
                    observers.add(observer)
                    observer.startWatching()
                }
                if (observers.isEmpty()) finishSession(params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to start file observers")
                finishSession(params)
            }
        }
    }

    private fun finishSession(params: JobParameters) {
        if (activeParams !== params) return
        activeParams = null
        stopSession()
        jobFinished(params, false)
    }

    private fun stopSession() {
        session?.cancel()
        session = null
        observers.forEach { it.close() }
        observers.clear()
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeParams = null
        stopSession()
        return true
    }

    override fun onDestroy() {
        activeParams = null
        stopSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class DirectoryFileObserver(
        private val directory: File,
        private val events: ObserverFileEvents,
        runFile: suspend (File) -> Unit
    ) : FileObserver(directory.absolutePath, CREATE or CLOSE_WRITE or MOVED_TO or MOVED_FROM or DELETE) {
        private val queue = Channel<String>(64)
        private val worker = serviceScope.launch(dispatchers.io) {
            for (name in queue) {
                try {
                    val file = resolveObservedFile(directory, name) ?: continue
                    runFile(file)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "File observer failed for $name")
                }
            }
        }

        override fun onEvent(event: Int, path: String?) {
            // This callback runs on the shared native observer thread; never block it.
            try {
                val ready = events.onEvent(event, path) ?: return
                if (queue.trySend(ready).isFailure) Timber.w("File observer queue unavailable; skipped $ready")
            } catch (e: Exception) {
                Timber.e(e, "Invalid file observer event")
            }
        }

        fun close() {
            events.close()
            stopWatching()
            queue.cancel()
            worker.cancel()
        }
    }
}
