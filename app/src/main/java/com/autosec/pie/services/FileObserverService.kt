package com.autosec.pie.services

import android.app.Activity
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Environment
import android.os.FileObserver
import com.autosec.pie.viewModels.MainViewModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class FileObserverJobService : JobService() {

    private val fileObservers = mutableListOf<DirectoryFileObserver>()

    val mainViewModel: MainViewModel by inject(MainViewModel::class.java)


    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.d("Job is starting")


        val observerConfig = readFileObserversConfig()

        if(observerConfig == null){
            Timber.d("Observers file not available")
            mainViewModel.schedulerConfigAvailable = false
            return false
        }else{
            mainViewModel.schedulerConfigAvailable = true
        }


        for (entry in observerConfig.entrySet()) {
            val key = entry.key
            val value = entry.value.asJsonObject
            // Process the key-value pair
            println("Key: $key, Value: $value")

            val directoryPath = value.get("path").asString
            val exec = value.get("exec").asString
            val command = value.get("command").asString
            val selectors = value.get("selectors").asJsonArray.map { it.asString }


            Timber.d("Starting $key observer for $directoryPath")

            val fileObserver = DirectoryFileObserver(directoryPath, exec, command, selectors)
            fileObservers.add(fileObserver)
            fileObserver.startWatching()
        }


        return true // Job is still running
    }

    private fun readFileObserversConfig(): JsonObject? {

        val filePath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/observers.json"

        try {
            val file = File(filePath)
            val inputStream = FileInputStream(file)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer)

            Timber.d(jsonString)

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if (!dataObject.isJsonObject) {
                Timber.d("Observer Config is not valid json object")
                throw JsonParseException("Config not valid")
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle potential exceptions like file not found or permission errors
            return null
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Timber.d("Job is Stopped")

        fileObservers.forEach{
            it.stopWatching()
        }
        return true // Job should be rescheduled
    }

    class DirectoryFileObserver(private val path: String, private val exec: String, private val command: String, private val selectors: List<String>) : FileObserver(path, CREATE) {

        val activity: Activity by inject(Context::class.java)

        override fun onEvent(event: Int, path: String?) {
            if (event == CREATE && path != null) {
                Timber.d("New file created: $path")
                checkFileCompletion(File("$path"))
            }
        }
        @OptIn(DelicateCoroutinesApi::class)
        private fun checkFileCompletion(file: File) {
            GlobalScope.launch(Dispatchers.IO) {
                var lastSize = -1L
                while (true) {
                    delay(1000)  // Check every 1 second
                    val currentSize = file.length()
                    if (currentSize == lastSize) {
                        Timber.d("File is completely written: " + file.name)
                        //Log.d("DirectoryFileObserver", "Absolute file path: ${file.absoluteFile.absolutePath}")

                        //val binaryFile = File(activity.filesDir, "python")

                        Timber.d("File abs path " + file.absoluteFile.absolutePath)

                        Timber.d("Edited abs path " + path + file.absolutePath)

                        val resultString = "\"${command.replace("{INPUT_FILE}", file.name)}\""

                        Timber.d("Edited command $exec $resultString")

                        //val command = "${path}${file.absolutePath} ${path}${}"

                        val regSelectors = selectors.map { it.toRegex() }

                        //Checking if file passes selectors list
                        if(regSelectors.any { file.name.matches(it) }){
                            Timber.d("Selector matched for file")
                            ProcessManagerService.runCommand4(exec, resultString , path)
                        }else{
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