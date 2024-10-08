package com.autosec.pie.services

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Environment
import android.os.FileObserver
import androidx.lifecycle.viewModelScope
import androidx.work.Configuration
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandType
import com.autosec.pie.data.CronCommandModel
import com.autosec.pie.data.InputParsedData
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.utils.Utils
import com.autosec.pie.viewModels.MainViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class FileObserverJobService : JobService() {

    private val fileObservers = mutableListOf<DirectoryFileObserver>()

    val main: MainViewModel by inject(MainViewModel::class.java)

    init {
        Configuration.Builder().setJobSchedulerJobIdRange(0, 1000).build()

        try {
            main.viewModelScope.launch {
                main.eventFlow.collect{
                    when(it){
                        is ViewModelEvent.ObserversConfigChanged -> {
                            Timber.d("Observers config changed: Restarting")
                            restart()
                        }
                        else -> {}
                    }
                }
            }
        }catch (e:Exception){
            Timber.e(e)
        }
    }

    private fun restart(){
        val componentName = ComponentName(this, FileObserverJobService::class.java)
        val jobInfo = JobInfo.Builder(123, componentName)
            .setPersisted(true) // Keep the job alive after device reboot
            .setRequiresCharging(false)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setRequiresDeviceIdle(false)
            .build()

        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(jobInfo)
    }


    override fun onStartJob(params: JobParameters?): Boolean {

        Timber.d("Job is starting")

        CoroutineScope(Dispatchers.Default).launch {

            Timber.d("Thread Running on: ${Thread.currentThread().name}")


            val observerConfig = JSONService.readObserversConfig()


            if (observerConfig == null) {
                Timber.d("Observers file not available")
                main.schedulerConfigAvailable = false
                return@launch
            } else {
                main.schedulerConfigAvailable = true
            }


            for (entry in observerConfig.entrySet()) {
                val key = entry.key
                val value = entry.value.asJsonObject
                // Process the key-value pair

                val directoryPath = "${Environment.getExternalStorageDirectory().absolutePath}/" + value.get("path").asString
                val exec = value.get("exec").asString
                val command = value.get("command").asString
                val selectors = value.get("selectors").asJsonArray.map { it.asString }
                val deleteSourceFile = value.get("deleteSourceFile").asBoolean


                Timber.d("Starting $key observer for $directoryPath")

                val commandModel: CommandModel = Gson().fromJson(value, CommandModel::class.java)

                val fileObserver =
                    DirectoryFileObserver(commandModel, directoryPath, exec, command, selectors, deleteSourceFile)
                fileObservers.add(fileObserver)
                fileObserver.startWatching()
            }
        }


        return true
    }


    override fun onStopJob(params: JobParameters?): Boolean {
        Timber.d("Job is Stopped")

        fileObservers.forEach {
            it.stopWatching()
        }
        return true // Job should be rescheduled
    }

    class DirectoryFileObserver(
        private val commandModel: CommandModel,
        private val path: String,
        private val exec: String,
        private val command: String,
        private val selectors: List<String>,
        private val deleteSourceFile: Boolean
    ) : FileObserver(path, CREATE) {

        //val activity: Activity by inject(Context::class.java)

        override fun onEvent(event: Int, path: String?) {
            Timber.d("Event Fired: $event")
            if (event == CREATE && path != null) {
                Timber.d("New file created: $path")
                checkFileCompletion(File("$path"))
                //execCommand(File("$path"))
            }

//            if (event == MODIFY && path != null) {
//                Timber.d("File modified: $path")
//                //checkFileCompletion(File("$path"))
//                execCommand(File("$path"))
//            }

        }

        private fun checkFileCompletion(file: File) {
            CoroutineScope(Dispatchers.IO).launch {
                var lastSize = -1L

                val regSelectors = selectors.map { it.toRegex() }

                if(!regSelectors.any { file.name.matches(it) }) {
                    return@launch
                }

                while (true) {
                    delay(1000)  // Check every 1 second
                    val currentSize = file.length()
                    if (currentSize == lastSize) {

                        //Do operation here

                        Timber.d("File is completely written: " + file.name)

                        Timber.d("File abs path " + file.absoluteFile.absolutePath)

                        Timber.d("Edited abs path " + path + file.absolutePath)


                        //This is to prevent .pending files from causing errors.
                        val fileName =
                            if (!file.name.startsWith(".pending")) file.name else file.name.split("-")
                                .subList(2, file.name.split("-").size).joinToString("-")

                        val fullFilepath = "$path/$fileName"

                        Timber.d("Edited Filename: $fileName")

                        //val resultString = "\"${command.replace("{INPUT_FILE}", "'$fileName'")}\""
                        val resultString = "\"${command}\""

                        val parsedPath = Path(fileName)

                        val inputParsedData = mutableListOf<InputParsedData>().also {
                            it.add(InputParsedData(name = "INPUT_FILE", value = "'${fileName}'"))
                            it.add(InputParsedData(name = "FILENAME", value = parsedPath.fileName.toString()))
                            it.add(InputParsedData(name = "FILE_EXT", value = parsedPath.extension))
                            it.add(InputParsedData(name = "FILENAME_NO_EXT", value = parsedPath.nameWithoutExtension))
                            it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                        }

                        Timber.d("Edited command $exec $resultString")

                        val execFilePath =
                            Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + exec

                        val fullExecPath = when{
                            File(exec).isAbsolute -> {
                                exec
                            }
                            File(execFilePath).exists() -> {
                                //For packages installed inside autosec/bin
                                execFilePath
                            }
                            else -> {
                                //Base case fallback to terminal installed packages such as busybox packages.
                                exec
                            }
                        }

                        val usePython = !Utils.isShellScript(File(fullExecPath))


                        //Checking if file passes selectors list
                        if (regSelectors.any { file.name.matches(it) }) {
                            Timber.d("Selector matched for file")
                            val execSuccess =
                                ProcessManagerService.runCommandWithEnv(commandModel,fullExecPath, resultString, path,inputParsedData,usePython)

                            if (deleteSourceFile && execSuccess) {
                                ProcessManagerService.deleteFile(fullFilepath)
                            }
                        } else {
                            Timber.d("File does not match selector")
                        }

                        break
                    }
                    lastSize = currentSize
                }
            }
        }

    }
}