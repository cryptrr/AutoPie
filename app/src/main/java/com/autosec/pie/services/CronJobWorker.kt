package com.autosec.pie.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CronCommandModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class CronJobWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Timber.d("Cron job fired for ${inputData.getString("command")}")
        try {

            val commandString = inputData.getString("command")

            val command: CronCommandModel = Gson().fromJson(commandString, CronCommandModel::class.java)

            ProcessManagerService.runCommand4(command.exec, command.command, command.path)

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }

    }
}