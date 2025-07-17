package com.autosec.pie.autopieapp.data.services

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CronCommandModel
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.utils.Utils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class CronJobWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
    private val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)
    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    private var processIds : List<Int> = emptyList()

    private val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)


    override fun doWork(): Result {
        Timber.d("Cron job fired for ${inputData.getString("command")}")

        lateinit var command : CommandModel

        try {
            inputData.let {
                val processId = (100000..999999).random()

                processIds = processIds + processId

                Timber.d("ProcessIds at starting command: $processIds")

                val commandKey = it.getString("commandKey")

                if(commandKey == null){
                    Timber.e("Data not received")
                    return@let
                }



               runBlocking {

                   command = useCases.getCommandDetails(commandKey)

                   useCases.runStandaloneCommand(command, emptyList(), processId).first().let { receipt ->

                       if (receipt.success) {
                           Timber.d("Process Success".uppercase())
                           //autoPieNotification.sendNotification("Command Success", "${command.name} ${receipt.jobKey}", logContents = receipt.output)

                           return@runBlocking Result.success()

                       } else {
                           Timber.d("Process FAILED".uppercase())
                           //autoPieNotification.sendNotification("Command Failed", "${command.name} ${receipt.jobKey}", logContents = receipt.output)
                           return@runBlocking Result.failure()
                       }

                   }
               }
            }


        }catch (e: Exception){
            Timber.e(e)
            autoPieNotification.sendNotification("Command Failed", command.name ,null,e.toString())
            return Result.failure()

        }

        return Result.failure()
    }



     fun doWork2(): Result {
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


            processManagerService.runCommandWithEnv(
                command, fullExecPath, finalCommand, command.path,
                emptyList(), usePython
            )

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }

    }
}