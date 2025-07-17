package com.autosec.pie.autopieapp.data.services

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
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.data.CronCommandModel
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.services.AutoPieCoreService.Companion.dispatchers
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.utils.Utils
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.use_case.AutoPieUseCases
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class FileObserverJobService : JobService() {

    private val fileObservers = mutableListOf<DirectoryFileObserver>()

    val main: MainViewModel by inject(MainViewModel::class.java)
    val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)


    val jsonService: JsonService by inject(JsonService::class.java)

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
        try {
            val componentName = ComponentName(this, FileObserverJobService::class.java)
            val jobInfo = JobInfo.Builder(123, componentName)
                .setPersisted(true) // Keep the job alive after device reboot
                .setRequiresCharging(false)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setRequiresDeviceIdle(false)
                .build()

            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.schedule(jobInfo)
        }catch (e: Exception){
            Timber.e(e)
        }
    }


    override fun onStartJob(params: JobParameters?): Boolean {

        Timber.d("Job is starting")

        CoroutineScope(dispatchers.default).launch {

            Timber.d("Thread Running on: ${Thread.currentThread().name}")


            try {
                if(!main.storageManagerPermissionGranted){
                    Timber.d("Storage permission not granted to start FileObserverService")
                    return@launch
                }

                val observerConfig = try {
                    jsonService.readObserversConfig()
                }catch (e: Exception){
                    Timber.e(e)
                    return@launch
                }


                if (observerConfig == null) {
                    Timber.d("Observers file not available")
                    main.schedulerConfigAvailable = false
                    return@launch
                } else {
                    main.schedulerConfigAvailable = true
                }


                for (entry in observerConfig.entrySet()) {
                    val key = entry.key

                    val commandModel = useCases.getCommandDetails(key)
                    val fullPath = File(Environment.getExternalStorageDirectory().absolutePath, commandModel.path).absolutePath

                    Timber.d("Starting $key observer for ${commandModel.path}")




                    val fileObserver =
                        DirectoryFileObserver(commandModel, fullPath)
                    fileObservers.add(fileObserver)
                    fileObserver.startWatching()
                }
            }catch (e: Exception){
                Timber.e(e)
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
        private val dirPath: String
    ) : FileObserver(dirPath, CREATE) {

        //val activity: Activity by inject(Context::class.java)

        private val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)

        private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

        private var processIds : List<Int> = emptyList()


        override fun onEvent(event: Int, path: String?) {
            Timber.d("Event Fired: $event")
            if (event == CREATE && path != null) {
                Timber.d("New file created: $path")
                try {
                    checkFileCompletion(File("$path"))
                }catch (e: Exception){
                    Timber.e(e)
                }
            }

//            if (event == MODIFY && path != null) {
//                Timber.d("File modified: $path")
//                //checkFileCompletion(File("$path"))
//                execCommand(File("$path"))
//            }

        }

        private fun checkFileCompletion(file: File) {

            CoroutineScope(dispatchers.io).launch {

                var lastSize = -1L

                val regSelectors = commandModel.selectors?.map { it.toRegex() } ?: emptyList()

                if((regSelectors.isNotEmpty() && !regSelectors.any { file.name.matches(it) }) || file.name.contains(".conv")) {
                    Timber.d("File does not pass filter")
                    return@launch
                }

                while (true) {
                    delay(1000)  // Check every 1 second
                    val currentSize = file.length()
                    if (currentSize == lastSize) {

                        //Do operation here

                        Timber.d("File is completely written: " + file.name)

                        Timber.d("File abs path " + file.absoluteFile.absolutePath)

                        Timber.d("Edited abs path " + commandModel.path + file.absolutePath)

                        val processId = (100000..999999).random()

                        processIds = processIds + processId

                        //This is to prevent .pending files from causing errors.
                        val fileName =
                            if (!file.name.startsWith(".pending")) file.name else file.name.split("-")
                                .subList(2, file.name.split("-").size).joinToString("-")

                        val fullFilepath = File(Environment.getExternalStorageDirectory().absolutePath, File(commandModel.path, fileName).absolutePath).absolutePath

                        Timber.d("Edited Filename: $fileName")

                        //Checking if file passes selectors list
                        if (regSelectors.isEmpty() || regSelectors.any { file.name.matches(it) }) {
                            Timber.d("Selector matched for file")

                            val result = useCases.runShareCommandForFiles(commandModel, null, listOf(fullFilepath), emptyList(), processId).first()

                            if (commandModel.deleteSourceFile == true && result.success) {
                                processManagerService.deleteFile(fullFilepath)
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