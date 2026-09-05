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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        } catch (error: Exception) {
            Timber.e(error, "Failed to observe command config changes")
        }
    }

    /**
     * Reconciles commands.json with WorkManager's durable state. WorkManager persists periodic
     * requests across process death and reboot, while UPDATE preserves an existing job's cadence.
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
        } catch (error: Exception) {
            // A temporary storage/config read error must not destroy the last durable schedule.
            Timber.e(error, "Could not read commands config; keeping the existing cron schedule")
            return
        }

        if (cronConfig == null) {
            main.schedulerConfigAvailable = false
            Timber.d("Commands config is unavailable; keeping the existing cron schedule")
            return
        }
        main.schedulerConfigAvailable = true

        val parsedData = Gson().fromJsonObjectEntries(cronConfig, CommandModel::class.java)
        if (parsedData.skippedKeys.isNotEmpty()) {
            Timber.w("Skipped incompatible cron commands: ${parsedData.skippedKeys}")
        }

        val workManager = WorkManager.getInstance(application)
        migrateLegacyCronWorkIfNeeded(workManager)

        val requestedWorkNames = mutableSetOf<String>()
        parsedData.values
            .filterValues { command -> command.type == CommandType.CRON }
            .forEach { (commandKey, command) ->
                val parsedInterval = Utils.parseTimeInterval(command.cronInterval.orEmpty())
                if (parsedInterval == null) {
                    Timber.w("Skipping cron '$commandKey': invalid interval '${command.cronInterval}'")
                    return@forEach
                }

                val requestedIntervalMillis = parsedInterval.second.toMillis(parsedInterval.first)
                val effectiveIntervalMillis = maxOf(
                    requestedIntervalMillis,
                    PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
                )
                val workName = workName(commandKey)
                requestedWorkNames += workName

                if (effectiveIntervalMillis != requestedIntervalMillis) {
                    Timber.w("Cron '$commandKey' is below Android's 15 minute minimum; using 15 minutes")
                }

                val inputData = Data.Builder()
                    .putString(CronJobWorker.COMMAND_KEY, commandKey)
                    .build()

                val request = PeriodicWorkRequestBuilder<CronJobWorker>(
                    effectiveIntervalMillis,
                    TimeUnit.MILLISECONDS
                )
                    // Without an initial delay, a newly-created periodic request may run at once.
                    .setInitialDelay(effectiveIntervalMillis, TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        RETRY_BACKOFF_MINUTES,
                        TimeUnit.MINUTES
                    )
                    .setInputData(inputData)
                    .addTag(CRON_WORK_TAG)
                    .addTag(workName)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    workName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                Timber.d("Scheduled cron '$commandKey' every ${effectiveIntervalMillis}ms")
            }

        removeObsoleteCronWork(workManager, requestedWorkNames)
    }

    private suspend fun removeObsoleteCronWork(
        workManager: WorkManager,
        requestedWorkNames: Set<String>
    ) {
        try {
            workManager.getWorkInfosByTagFlow(CRON_WORK_TAG).first()
                .filter { workInfo ->
                    !workInfo.state.isFinished && workInfo.tags.none(requestedWorkNames::contains)
                }
                .forEach { workInfo ->
                    Timber.d("Cancelling obsolete cron work ${workInfo.id}")
                    workManager.cancelWorkById(workInfo.id).await()
                }
        } catch (error: Exception) {
            Timber.e(error, "Failed to remove obsolete cron work")
        }
    }

    /**
     * Older builds used untagged raw command keys as WorkManager names. Cron was this app's only
     * WorkManager consumer, so a one-time reset prevents renamed/deleted legacy jobs from surviving.
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
        } catch (error: Exception) {
            // Leave the flag unset so a later reconciliation retries the migration.
            Timber.e(error, "Failed to clear legacy cron work")
            throw error
        }
    }

    private fun workName(commandKey: String) = "$CRON_WORK_NAME_PREFIX$commandKey"

    private companion object {
        const val CRON_WORK_TAG = "autopie.cron"
        const val CRON_WORK_NAME_PREFIX = "autopie.cron."
        const val SCHEDULER_PREFERENCES = "cron_scheduler"
        const val LEGACY_WORK_MIGRATED = "legacy_work_migrated_widgets_v1"
        const val RETRY_BACKOFF_MINUTES = 1L
    }
}
