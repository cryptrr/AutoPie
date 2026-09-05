package com.autopi.autopieapp.data.services

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DispatcherProvider
import com.autopi.utils.Utils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CronService(private val jsonService: JsonService) {

    private val main: MainViewModel by inject(MainViewModel::class.java)
    private val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
    private val application: Application by inject(Context::class.java)
    private val schedulerScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }
    private val reconciliationMutex = Mutex()

    init {
        try {
            main.viewModelScope.launch {
                main.eventFlow.collect { event ->
                    if (event is ViewModelEvent.CommandsConfigChanged) {
                        Timber.d("Commands config changed: reconciling cron commands")
                        setUpCronJobs()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to observe command config changes")
        }
    }

    /**
     * Reconciles persistent WorkManager state with commands.json. WorkManager persists these
     * requests across process death and reboot; UPDATE also preserves an existing job's cadence.
     */
    fun setUpCronJobs() {
        schedulerScope.launch {
            reconciliationMutex.withLock {
                reconcileCronJobs()
            }
        }
    }

    private suspend fun reconcileCronJobs() {
        Timber.d("Reconciling cron jobs")

        val cronConfig = try {
            jsonService.readCommandsConfig()
        } catch (e: Exception) {
            // Do not destroy the durable schedule because of a temporary storage/read failure.
            Timber.e(e, "Could not read commands config; keeping the existing cron schedule")
            return
        }

        if (cronConfig == null) {
            Timber.d("Commands config is not available; keeping the existing cron schedule")
            main.schedulerConfigAvailable = false
            return
        }
        main.schedulerConfigAvailable = true

        val parsedData = Gson().fromJsonObjectEntries(cronConfig, CommandModel::class.java)
        if (parsedData.skippedKeys.isNotEmpty()) {
            Timber.w("Skipped incompatible cron commands: ${parsedData.skippedKeys}")
        }

        val workManager = WorkManager.getInstance(application)
        migrateLegacyCronWorkIfNeeded(workManager)

        val requestedNames = mutableSetOf<String>()
        parsedData.values
            .filterValues { it.type == CommandType.CRON }
            .forEach { (commandKey, command) ->
                val parsedInterval = Utils.parseTimeInterval(command.cronInterval ?: "")
                if (parsedInterval == null) {
                    Timber.w("Skipping cron command '$commandKey': invalid interval '${command.cronInterval}'")
                    return@forEach
                }

                val requestedIntervalMillis = parsedInterval.second.toMillis(parsedInterval.first)
                val intervalMillis = maxOf(
                    requestedIntervalMillis,
                    PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
                )
                val workName = workName(commandKey)
                requestedNames += workName

                if (intervalMillis != requestedIntervalMillis) {
                    Timber.w("Cron '$commandKey' is below Android's 15 minute minimum; using 15 minutes")
                }

                val inputData = Data.Builder()
                    .putString(CronJobWorker.COMMAND_KEY, commandKey)
                    .build()

                val request = PeriodicWorkRequestBuilder<CronJobWorker>(
                    intervalMillis,
                    TimeUnit.MILLISECONDS
                )
                    // Periodic work may otherwise execute immediately the first time it is added.
                    .setInitialDelay(intervalMillis, TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .setInputData(inputData)
                    .addTag(CRON_WORK_TAG)
                    .addTag(workName)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    workName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                Timber.d("Scheduled cron '$commandKey' every ${intervalMillis}ms")
            }

        // WorkManager has no public API for listing unique names, so every cron request also gets
        // a tag containing its unique name. This lets config deletion/renaming cancel stale work.
        try {
            workManager.getWorkInfosByTagFlow(CRON_WORK_TAG).first()
                .filter { info -> info.tags.none(requestedNames::contains) }
                .forEach { info ->
                    Timber.d("Cancelling obsolete cron work ${info.id}")
                    workManager.cancelWorkById(info.id).await()
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove obsolete cron work")
        }
    }

    /**
     * Older releases created untagged work using the raw command key. Cron was the only
     * WorkManager consumer, so one reset is needed to prevent deleted legacy jobs running forever.
     */
    private suspend fun migrateLegacyCronWorkIfNeeded(workManager: WorkManager) {
        val preferences = application.getSharedPreferences(SCHEDULER_PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(LEGACY_WORK_MIGRATED, false)) return

        try {
            workManager.cancelAllWork().await()
            check(preferences.edit().putBoolean(LEGACY_WORK_MIGRATED, true).commit()) {
                "Could not persist cron migration state"
            }
            Timber.d("Cleared legacy untagged cron work")
        } catch (e: Exception) {
            // Leave the flag unset so the migration is retried on the next reconciliation.
            Timber.e(e, "Failed to clear legacy cron work")
            throw e
        }
    }

    private fun workName(commandKey: String) = "$CRON_WORK_NAME_PREFIX$commandKey"

    private companion object {
        const val CRON_WORK_TAG = "autopie.cron"
        const val CRON_WORK_NAME_PREFIX = "autopie.cron."
        const val SCHEDULER_PREFERENCES = "cron_scheduler"
        const val LEGACY_WORK_MIGRATED = "legacy_work_migrated_v1"
        const val RETRY_BACKOFF_MINUTES = 1L
    }
}
