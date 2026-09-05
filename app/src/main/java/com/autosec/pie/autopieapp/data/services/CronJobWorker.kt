package com.autopi.autopieapp.data.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autopi.use_case.AutoPieUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class CronJobWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

    override suspend fun doWork(): Result {
        val commandKey = inputData.getString(COMMAND_KEY)
        if (commandKey.isNullOrBlank()) {
            Timber.e("Cron worker started without a command key")
            return Result.failure()
        }

        val processId = (100000..999999).random()

        return try {
            Timber.d("Cron job fired for '$commandKey' (attempt $runAttemptCount)")
            val command = useCases.getCommandDetails(commandKey)
            // The widgets branch needs the CRON-specific runner so lifecycle/output events carry
            // JobType.CRON and update command widgets correctly.
            val receipt = useCases.runCronCommand(command, emptyList(), processId).first()

            if (receipt.success) {
                Timber.d("Cron command '$commandKey' succeeded")
                Result.success()
            } else {
                // A command's non-zero exit is a completed occurrence, not a scheduler outage.
                // WorkManager will keep its next periodic occurrence without an immediate rerun.
                Timber.w("Cron command '$commandKey' exited unsuccessfully")
                Result.failure()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Cron command '$commandKey' could not be executed")
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val COMMAND_KEY = "commandKey"
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
