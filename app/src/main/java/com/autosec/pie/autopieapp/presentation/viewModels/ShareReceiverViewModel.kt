package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.ForegroundService
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.use_case.AutoPieUseCases
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.isValidUrl
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class ShareReceiverViewModel(val application1: Application) : AndroidViewModel(application1) {

    val main: MainViewModel by inject(MainViewModel::class.java)
    val useCases: AutoPieUseCases by KoinJavaComponent.inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by KoinJavaComponent.inject(DispatcherProvider::class.java)


    var shareItemsResult by mutableStateOf<List<CommandModel>>(emptyList())
    var filteredShareItemsResult by mutableStateOf<List<CommandModel>>(emptyList())

    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    var searchQuery = mutableStateOf("")

    val currentExtrasDetails = mutableStateOf<Triple<Boolean, CommandModel, ShareInputs>?>(null)


    init {
        try {
            viewModelScope.launch {
                getSharesConfig()
                main.eventFlow.collect{
                    when(it){
                        is ViewModelEvent.SharesConfigChanged -> {
                            Timber.d("Share config changed: Restarting")
                            getSharesConfig()
                        }
                        else -> {}
                    }
                }
            }
        }catch (e:Exception){
            Timber.e(e)
        }
    }

    fun search(query: String) {


        filteredShareItemsResult = shareItemsResult.filter { it.name.contains(query.trim(), ignoreCase = true) || it.command.contains(query.trim(), ignoreCase = true) || it.exec.contains(query.trim(), ignoreCase = true) || it.type.toString().contains(query.trim(), ignoreCase = true) }

    }


    fun getSharesConfig() {

        if(!main.storageManagerPermissionGranted){
            main.showError(ViewModelError.StoragePermissionDenied)
            return
        }

        viewModelScope.launch(dispatchers.io){
            try {
                useCases.getShareCommands().let{
                    shareItemsResult = it
                    filteredShareItemsResult = it
                }
            }catch (e: Exception){
                when(e){
                    is FileNotFoundException -> {}
                    is ViewModelError.ShareConfigUnavailable -> main.showError(ViewModelError.ShareConfigUnavailable)
                    is ViewModelError.InvalidShareConfig -> main.showError(ViewModelError.InvalidShareConfig)
                    else -> main.showError(ViewModelError.Unknown)
                }
            }
        }
    }

    private fun readSharesConfig(): JsonObject? {

        val sharesFilePath =
            Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/shares.json"


        try {
            val file = File(sharesFilePath)
            val inputStream = FileInputStream(file)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer)

            //Timber.d(jsonString)

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if (!dataObject.isJsonObject) {
                Timber.d("Share Sheet config is not valid json")
                throw JsonParseException("Config not valid")
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }


    fun runShareCommand(item: CommandModel, currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int)  {


        Timber.d(item.toString())
        Timber.d(currentLink.toString())


        val inputDir = fileUris.firstOrNull()?.let { File(it) }

        when {
            inputDir?.isDirectory == true -> {
                runShareCommandForDirectory(item,inputDir, commandExtraInputs, processId)
            }
            currentLink.isValidUrl() -> {
                runShareCommandForUrl(item, currentLink!!, fileUris, commandExtraInputs, processId)
            }

            fileUris.isNotEmpty() -> {
                runShareCommandForFiles(item, currentLink, fileUris, commandExtraInputs, processId)
            }

            !currentLink.isNullOrBlank() -> {

            }

            else -> {}
        }

        viewModelScope.launch {
            delay(900L)
            currentExtrasDetails.value = null
        }
    }


    private fun runShareCommandForUrl(
        item: CommandModel,
        currentLink: String,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ) {

        Timber.d("runShareCommandForUrl")


        val inputUrl = URL(currentLink)

        val host = inputUrl.host

        val filename = inputUrl.file

        val inputParsedData = mutableListOf<InputParsedData>().also {
            it.add(InputParsedData(name = "INPUT_FILE", value = "'$currentLink'"))
            it.add(InputParsedData(name = "HOST", value = "'$host'"))
            it.add(InputParsedData(name = "FILENAME", value = "'$filename'"))
            it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
        }

        //val resultString = "\"${item.command.replace("{INPUT_FILE}", "'$currentLink'")}\""
        val resultString = "\"${item.command}\""


        val execFilePath =
            Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec


        val fullExecPath = when{
            File(item.exec).isAbsolute -> {
                item.exec
            }
            File(execFilePath).exists() -> {
                //For packages installed inside autosec/bin
                execFilePath
            }
            else -> {
                //Base case fallback to terminal installed packages such as busybox packages.
                item.exec
            }
        }

        val usePython = !Utils.isShellScript(File(fullExecPath))


        Timber.d("Command to run: ${item.exec} $resultString")


        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = ProcessManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,inputParsedData,commandExtraInputs,processId, usePython)

                if (success) {
                    Timber.d("Process Success".uppercase())
                    autoPieNotification.sendNotification("Command Success", "${item.name} $currentLink")

                } else {
                    Timber.d("Process FAILED".uppercase())
                    autoPieNotification.sendNotification("Command Failed", "${item.name} $currentLink")
                }

                main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))


            }catch (e: Exception){
                main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))
                Timber.e(e)
            }


            //main.dispatchEvent(ViewModelEvent.CloseShareReceiverSheet)

        }

    }

    private fun runShareCommandForFiles(
        item: CommandModel,
        currentLink: String?,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ) {


        Timber.d("runShareCommandForFiles")
        val currentItems = fileUris

        viewModelScope.launch(Dispatchers.IO) {

            try {
                if (item.command.contains("{INPUT_FILES}")) {

                    Timber.d("Multiple Input files detected")


                    val replacedString = item.command



                    Timber.d("Replaced String $replacedString")

                    val execFilePath =
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                    val fullExecPath = when{
                        File(item.exec).isAbsolute -> {
                            item.exec
                        }
                        File(execFilePath).exists() -> {
                            //For packages installed inside autosec/bin
                            execFilePath
                        }
                        else -> {
                            //Base case fallback to terminal installed packages such as busybox packages.
                            item.exec
                        }
                    }

                    val isShellScript = Utils.isShellScript(File(fullExecPath))
                    val usePython = Utils.isZipFile(File(fullExecPath))

                    val inputFiles = if(usePython){
                        currentItems.joinToString(" "){"'${it}'"}.replace("''","'")
                    }else{
                        currentItems.joinToString(" ")
                    }

                    val parsedPath = Path(currentItems.firstOrNull() ?: "")

                    val inputParsedData = mutableListOf<InputParsedData>().also {
                        it.add(InputParsedData(name = "INPUT_FILES", value = "$inputFiles"))
                        it.add(InputParsedData(name = "INPUT_FILE", value = if(usePython) "'${parsedPath.absolutePathString()}'" else parsedPath.absolutePathString()))
                        it.add(InputParsedData(name = "FILENAME", value = if(usePython) "'${parsedPath.fileName}'" else "${parsedPath.fileName}"))
                        it.add(InputParsedData(name = "DIRECTORY", value = if(usePython) "'${parsedPath.parent}'" else "${parsedPath.parent}"))
                        it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(usePython) "'${parsedPath.nameWithoutExtension}'" else parsedPath.nameWithoutExtension))
                        it.add(InputParsedData(name = "FILE_EXT", value =  if(usePython) "'${parsedPath.extension}'" else parsedPath.extension))
                        it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                    }

                    Timber.d("fullExecPath : $fullExecPath")
                    Timber.d("Use Python : $usePython")

                    val resultString = if(usePython) "\"${replacedString}\"" else replacedString

                    Timber.d("Result Command: $resultString")

                    val success = ProcessManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                        inputParsedData,commandExtraInputs,processId, usePython, isShellScript)

                    if (success) {
                        Timber.d("Process Success".uppercase())
                        autoPieNotification.sendNotification("Command Success", "${item.name} $fileUris")

                    } else {
                        Timber.d("Process FAILED".uppercase())
                        autoPieNotification.sendNotification("Command Failed", "${item.name} $fileUris")
                    }

                    main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))

                    //main.dispatchEvent(ViewModelEvent.CloseShareReceiverSheet)

                } else {

                    Timber.d("Single input file")


                    currentItems.map { path ->
                        //val resultString = "\"${item.command.replace("{INPUT_FILE}", "'$path'")}\""

                        val replacedString = item.command

                        val parsedPath = Path(path)

                        val execFilePath =
                            Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                        val fullExecPath = when{
                            File(item.exec).isAbsolute -> {
                                item.exec
                            }
                            File(execFilePath).exists() -> {
                                //For packages installed inside autosec/bin
                                execFilePath
                            }
                            else -> {
                                //Base case fallback to terminal installed packages such as busybox packages.
                                item.exec
                            }
                        }

                        val isShellScript = Utils.isShellScript(File(fullExecPath))
                        val usePython = Utils.isZipFile(File(fullExecPath))

                        val inputParsedData = mutableListOf<InputParsedData>().also {
                            it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.joinToString(" ")))
                            it.add(InputParsedData(name = "INPUT_FILE", value = if(usePython) "'${parsedPath.absolutePathString()}'" else parsedPath.absolutePathString()))
                            it.add(InputParsedData(name = "FILENAME", value = if(usePython) "'${parsedPath.fileName}'" else "${parsedPath.fileName}"))
                            it.add(InputParsedData(name = "DIRECTORY", value = if(usePython) "'${parsedPath.parent}'" else "${parsedPath.parent}"))
                            it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(usePython) "'${parsedPath.nameWithoutExtension}'" else parsedPath.nameWithoutExtension))
                            it.add(InputParsedData(name = "FILE_EXT", value =  if(usePython) "'${parsedPath.extension}'" else parsedPath.extension))
                            it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                        }


                        Timber.d("Replaced String $replacedString")

                        val resultString = "\"${replacedString}\""

                        val success = ProcessManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                            inputParsedData,commandExtraInputs,processId, usePython, isShellScript)

                        if (success) {
                            Timber.d("Process Success".uppercase())
                            autoPieNotification.sendNotification("Command Success", "${item.name} $fileUris")

                        } else {
                            Timber.d("Process FAILED".uppercase())
                            autoPieNotification.sendNotification("Command Failed", "${item.name} $fileUris")
                        }
                    }

                    main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))


                    //main.dispatchEvent(ViewModelEvent.CloseShareReceiverSheet)

                }
            }catch (e: Exception){
                main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))
                Timber.e(e)
            }

        }

    }

    private fun runShareCommandForDirectory(
        item: CommandModel,
        inputDir: File,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ) {


        Timber.d("runShareCommandForDirectory")


        val currentItems = inputDir.listFiles()!!

        viewModelScope.launch(Dispatchers.IO) {

            try {
                val startTime = System.currentTimeMillis()

                var results = mutableListOf<Boolean>()

                currentItems.map { path ->
                    //val resultString = "\"${item.command.replace("{INPUT_FILE}", path.absolutePath)}\""


                    val resultString = "\"${item.command}\""

                    val inputParsedData = mutableListOf<InputParsedData>().also {
                        it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.joinToString(" ")))
                        it.add(InputParsedData(name = "INPUT_FILE", value = "'${path.absolutePath}'"))
                        it.add(InputParsedData(name = "FILENAME", value = "'${path.name}'"))
                        it.add(InputParsedData(name = "DIRECTORY", value = "'${path.parent}'"))
                        it.add(InputParsedData(name = "FILENAME_NO_EXT", value = "'${path.nameWithoutExtension}'"))
                        it.add(InputParsedData(name = "FILE_PATH", value = "'${(path.parent ?: "")}'"))
                        it.add(InputParsedData(name = "FILE_EXT", value = "'${path.extension}'"))
                        it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                    }


                    val execFilePath =
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                    val fullExecPath = when{
                        File(item.exec).isAbsolute -> {
                            item.exec
                        }
                        File(execFilePath).exists() -> {
                            //For packages installed inside autosec/bin
                            execFilePath
                        }
                        else -> {
                            //Base case fallback to terminal installed packages such as busybox packages.
                            item.exec
                        }
                    }

                    val usePython = !Utils.isShellScript(File(fullExecPath))

                    val success = ProcessManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                        inputParsedData,commandExtraInputs,processId, usePython)

                    results.add(success)

                    if (success) {
                        Timber.d("Process Success".uppercase())
                        //autoPieNotification.sendNotification("Command Success", "${item.name} $inputDir")

                        if (item.deleteSourceFile == true) {
                            ProcessManagerService.deleteFile(path.absolutePath)
                        }

                    } else {
                        Timber.d("Process FAILED".uppercase())
                        //autoPieNotification.sendNotification("Command Failed", "${item.name} $inputDir")
                    }
                }

                if(results.all { it == true }) {
                    autoPieNotification.sendNotification("Command Success", "")
                }else{
                    autoPieNotification.sendNotification("Some Commands Failed", "")
                }

                main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))

                val endTime = System.currentTimeMillis()

                Timber.d("Time Elapsed: ${endTime - startTime}")
            }catch (e:Exception){
                main.dispatchEvent(ViewModelEvent.CommandCompleted(processId))
                Timber.e(e)
            }

        }

    }

    fun onCommandClick(card: CommandModel, fileUris: List<String>, currentLink: String?, onComplete: () -> Unit){
        viewModelScope.launch {
            try {
                val commandJson = Gson().toJson(card)
                val fileUrisJson = Gson().toJson(fileUris)

                val intent = Intent(application1, ForegroundService::class.java).apply {
                    putExtra("command", commandJson)
                    putExtra("currentLink", currentLink)
                    putExtra("fileUris", fileUrisJson)
                }

                startForegroundService(application1, intent)

                onComplete()

            }catch (e: Exception){
                Timber.e(e)
            }
        }
    }


}