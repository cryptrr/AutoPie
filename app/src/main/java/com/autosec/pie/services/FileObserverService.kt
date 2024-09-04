package com.autosec.pie.services

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Environment
import android.os.FileObserver
import com.autosec.pie.viewModels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class FileObserverJobService : JobService() {

    private val fileObservers = mutableListOf<DirectoryFileObserver>()

    val mainViewModel: MainViewModel by inject(MainViewModel::class.java)


    override fun onStartJob(params: JobParameters?): Boolean {

        Timber.d("Job is starting")

        CoroutineScope(Dispatchers.Default).launch {

            Timber.d("Thread Running on: ${Thread.currentThread().name}")


            val observerConfig = JSONService.readObserversConfig()


            if (observerConfig == null) {
                Timber.d("Observers file not available")
                mainViewModel.schedulerConfigAvailable = false
                return@launch
            } else {
                mainViewModel.schedulerConfigAvailable = true
            }


            for (entry in observerConfig.entrySet()) {
                val key = entry.key
                val value = entry.value.asJsonObject
                // Process the key-value pair

                val directoryPath = value.get("path").asString
                val exec = value.get("exec").asString
                val command = value.get("command").asString
                val selectors = value.get("selectors").asJsonArray.map { it.asString }
                val deleteSourceFile = value.get("deleteSourceFile").asBoolean



                Timber.d("Starting $key observer for $directoryPath")

                val fileObserver =
                    DirectoryFileObserver(directoryPath, exec, command, selectors, deleteSourceFile)
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

                        val resultString = "\"${command.replace("{INPUT_FILE}", "'$fileName'")}\""

                        Timber.d("Edited command $exec $resultString")

                        val fullExecPath =
                            Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + exec


                        //Checking if file passes selectors list
                        if (regSelectors.any { file.name.matches(it) }) {
                            Timber.d("Selector matched for file")
                            val execSuccess =
                                ProcessManagerService.runCommand4(fullExecPath, resultString, path)

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

        private fun execCommand(file: File) {
            CoroutineScope(Dispatchers.IO).launch {
                Timber.d("File is completely written: " + file.name)

                Timber.d("File abs path " + file.absoluteFile.absolutePath)

                Timber.d("Edited abs path " + path + file.absolutePath)

                //This is to prevent .pending files from causing errors.
                val fileName =
                    if (!file.name.startsWith(".pending")) file.name else file.name.split("-")
                        .subList(2, file.name.split("-").size).joinToString("-")

                val fullFilepath = "$path/$fileName"

                val resultString = "\"${command.replace("{INPUT_FILE}", "'$fileName'")}\""

                Timber.d("Edited command $exec $resultString")

                val fullExecPath =
                    Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + exec

                val regSelectors = selectors.map { it.toRegex() }

                delay(500L)

                //Checking if file passes selectors list
                if (regSelectors.any { file.name.matches(it) }) {
                    Timber.d("Selector matched for file")
                    val execSuccess =
                        ProcessManagerService.runCommand4(fullExecPath, resultString, path)

                    if (deleteSourceFile && execSuccess) {
                        //ProcessManagerService.deleteFile(fullFilepath)
                    }

                } else {
                    Timber.d("File does not match selector")
                }
            }
        }
    }
}