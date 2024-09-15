package com.autosec.pie.services

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CronCommandModel
import com.autosec.pie.utils.Utils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class CronJobWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Timber.d("Cron job fired for ${inputData.getString("command")}")
        try {

            val commandString = inputData.getString("command")

            val command: CronCommandModel = Gson().fromJson(commandString, CronCommandModel::class.java)

            var finalCommand = command.command

            val execFilePath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + command.exec

            val fullExecPath = when{
                File(command.exec).isAbsolute -> {
                    command.exec
                }
                File(execFilePath).exists() -> {
                    //For packages installed inside autosec/bin
                    execFilePath
                }
                else -> {
                    //Base case fallback to terminal installed packages such as busybox packages.
                    command.exec
                }
            }
            val usePython = !Utils.isShellScript(File(fullExecPath))


            ProcessManagerService.runCommandWithEnv(command ,fullExecPath, finalCommand, command.path, usePython)

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }

    }
}