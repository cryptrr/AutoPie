package com.autopi.autopieapp.data.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.use_case.AutoPieUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class CronJobWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
    private val autoPieNotification: AutoPieNotification by inject(AutoPieNotification::class.java)
    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

    override suspend fun doWork(): Result {
        val commandKey = inputData.getString(COMMAND_KEY)
        if (commandKey.isNullOrBlank()) {
            Timber.e("Cron worker started without a command key")
            return Result.failure()
        }

        val processId = (100000..999999).random()
        val logsFile = File(context.cacheDir, "$processId.log")
        var command: CommandModel? = null

        return try {
            Timber.d("Cron job fired for '$commandKey' (attempt $runAttemptCount)")
            command = useCases.getCommandDetails(commandKey)
            val receipt = useCases.runStandaloneCommand(command, emptyList(), processId).first()

            if (receipt.success) {
                Timber.d("Cron command '$commandKey' succeeded")
                Result.success()
            } else {
                // A command's non-zero exit is a completed run, not a scheduler failure. The next
                // periodic occurrence remains scheduled without immediately repeating side effects.
                Timber.w("Cron command '$commandKey' exited unsuccessfully")
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cron command '$commandKey' could not be executed")
            runCatching {
                autoPieNotification.sendNotification(
                    "Command Failed",
                    commandKey,
                    command,
                    logsFile.absolutePath,
                    processId
                )
                command?.let {
                    mainViewModel.dispatchEvent(
                        ViewModelEvent.CommandFailed(processId, it, logsFile.absolutePath)
                    )
                }
            }.onFailure { notificationError ->
                Timber.e(notificationError, "Could not report cron command failure")
            }

            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val COMMAND_KEY = "commandKey"
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
