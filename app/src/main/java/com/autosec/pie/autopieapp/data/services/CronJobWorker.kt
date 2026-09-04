package com.autopi.autopieapp.data.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.autopi.use_case.AutoPieUseCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class CronJobWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)


    override fun doWork(): Result {
        Timber.d("Cron job fired for ${inputData.getString("command")}")
        val commandKey = inputData.getString("commandKey")
        if (commandKey == null) {
            Timber.e("Data not received")
            return Result.failure()
        }

        val processId = (100000..999999).random()
        return try {
            runBlocking {
                val command = useCases.getCommandDetails(commandKey)
                val receipt = useCases.runCronCommand(command, emptyList(), processId).first()
                if (receipt.success) {
                    Timber.d("Process Success".uppercase())
                    Result.success()
                } else {
                    Timber.d("Process FAILED".uppercase())
                    Result.failure()
                }
            }
        } catch (error: Exception) {
            Timber.e(error)
            Result.failure()
        }
    }

}
