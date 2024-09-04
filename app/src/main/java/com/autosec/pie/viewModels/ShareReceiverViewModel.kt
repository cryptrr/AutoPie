package com.autosec.pie.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.data.CommandExtraInput
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandType
import com.autosec.pie.data.ShareInputs
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.notifications.AutoPieNotification
import com.autosec.pie.services.ProcessManagerService
import com.autosec.pie.utils.isValidUrl
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class ShareReceiverViewModel(application: Application) : AndroidViewModel(application) {

    val main: MainViewModel by inject(MainViewModel::class.java)

    var shareItemsResult by mutableStateOf<List<CommandModel>>(emptyList())
    var filteredShareItemsResult by mutableStateOf<List<CommandModel>>(emptyList())

    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    var searchQuery = mutableStateOf("")

    val currentExtrasDetails = mutableStateOf<Triple<Boolean, CommandModel, ShareInputs>?>(null)


    init {
        try {
            getSharesConfig()
        }catch (e:Exception){
            Timber.e(e)
        }
    }

    fun search(query: String) {

        filteredShareItemsResult = shareItemsResult.filter {
            it.name.contains(
                query,
                ignoreCase = true
            ) || it.command.contains(query, ignoreCase = true) || it.exec.contains(
                query,
                ignoreCase = true
            )
        }

    }


    fun getSharesConfig() {
        val observerConfig = readSharesConfig()

        if (observerConfig == null) {
            Timber.d("Observers file not available")
            main.sharesConfigAvailable = false
            return
        } else {
            main.schedulerConfigAvailable = true
        }

        val tempList = mutableListOf<CommandModel>()

        //TODO: Need to do some refactoring

        for (entry in observerConfig.entrySet()) {
            val key = entry.key
            val value = entry.value.asJsonObject
            // Process the key-value pair

            val directoryPath = value.get("path").asString
            val exec = value.get("exec").asString
            val command = value.get("command").asString
            val deleteSource = value.get("deleteSourceFile").asBoolean

            val extrasJsonArray = value.getAsJsonArray("extras")

            val extrasListType = object : TypeToken<List<CommandExtra>>() {}.type

            val extras: List<CommandExtra> = try{
                Gson().fromJson(extrasJsonArray, extrasListType)
            }catch(e: Exception){
                emptyList()
            }

            val shareObject = CommandModel(
                type = CommandType.SHARE,
                name = key,
                path = directoryPath,
                command = command,
                exec = exec,
                deleteSourceFile = deleteSource,
                extras = extras
            )

            tempList.add(shareObject)
        }

        shareItemsResult = tempList
        filteredShareItemsResult = tempList
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
            e.printStackTrace()
            return null
        }
    }

    private fun readObserversConfig(): JsonObject? {

        val fileObserverPath =
            Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/observers.json"

        try {
            val file = File(fileObserverPath)
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
                Timber.d("Observers config is not valid json")
                throw JsonParseException("Config not valid")
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }


    fun runShareCommand(item: CommandModel, currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList())  {


        Timber.d(item.toString())
        Timber.d(currentLink.toString())


        val inputDir = fileUris.firstOrNull()?.let { File(it) }

        when {
            inputDir?.isDirectory == true -> {
                runShareCommandForDirectory(item,inputDir, commandExtraInputs)
            }
            fileUris.isNotEmpty() -> {
                runShareCommandForFiles(item, currentLink, fileUris, commandExtraInputs)
            }

            currentLink.isValidUrl() -> {
                runShareCommandForUrl(item, currentLink!!, fileUris, commandExtraInputs)
            }

            !currentLink.isNullOrBlank() -> {

            }

            else -> {}
        }


    }


    private fun runShareCommandForUrl(
        item: CommandModel,
        currentLink: String,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList()
    ) {

        Timber.d("runShareCommandForUrl")


        var resultString = "\"${item.command.replace("{INPUT_FILE}", "'$currentLink'")}\""

        if(commandExtraInputs.isEmpty()){
            for(extra in item.extras ?: emptyList()){
                resultString = resultString.replace("{${extra.name}}", extra.default)
            }
        }else{
            for(extra in commandExtraInputs){
                resultString = resultString.replace("{${extra.name}}", extra.value)
            }
        }

        val fullExecPath =
            File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec)

        if(!fullExecPath.exists()){
            Timber.d("Exec file does not exist")
            return
        }


        Timber.d("Command to run: ${item.exec} $resultString")


        viewModelScope.launch(Dispatchers.IO) {
            val success = ProcessManagerService.runCommandForShare(fullExecPath.absolutePath, resultString, item.path)

            if (success) {
                Timber.d("Process Success".uppercase())
                autoPieNotification.sendNotification("Command Success", "${item.name} $currentLink")

            } else {
                Timber.d("Process FAILED".uppercase())
                autoPieNotification.sendNotification("Command Failed", "${item.name} $currentLink")
            }

            main.dispatchEvent(ViewModelEvent.CommandCompleted)


            //main.dispatchEvent(ViewModelEvent.CloseShareReceiverSheet)

        }

    }

    private fun runShareCommandForFiles(
        item: CommandModel,
        currentLink: String?,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList()
    ) {


        Timber.d("runShareCommandForFiles")
        val currentItems = fileUris

        viewModelScope.launch(Dispatchers.IO) {

            if (item.command.contains("{INPUT_FILES}")) {

                Timber.d("Multiple Input files detected")

                var replacedString = item.command
                    .replace("{INPUT_FILES}", currentItems.joinToString(" "){ "\'$it\'" })
                    .replace("''", "'")
                    .replace("{INPUT_FILE}", "'${currentItems.firstOrNull() ?: ""}'")


                //Handling extras

                if(commandExtraInputs.isEmpty()){
                    for(extra in item.extras ?: emptyList()){
                        replacedString = replacedString.replace("{${extra.name}}", extra.default)
                    }
                }else{
                    for(extra in commandExtraInputs){
                        replacedString = replacedString.replace("{${extra.name}}", extra.value)
                    }
                }


                Timber.d("Replaced String $replacedString")

                val resultString = "\"${replacedString}\""

                val fullExecPath =
                    Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                val success = ProcessManagerService.runCommandForShare(fullExecPath, resultString, item.path)

                if (success) {
                    Timber.d("Process Success".uppercase())
                    autoPieNotification.sendNotification("Command Success", "${item.name} $fileUris")

                } else {
                    Timber.d("Process FAILED".uppercase())
                    autoPieNotification.sendNotification("Command Failed", "${item.name} $fileUris")
                }

                main.dispatchEvent(ViewModelEvent.CommandCompleted)

                //main.dispatchEvent(ViewModelEvent.CloseShareReceiverSheet)

            } else {

                Timber.d("Single input file")


                currentItems.map { path ->
                    var resultString = "\"${item.command.replace("{INPUT_FILE}", "'$path'")}\""

                    if(commandExtraInputs.isEmpty()){
                        for(extra in item.extras ?: emptyList()){
                            resultString = resultString.replace("{${extra.name}}", extra.default)
                        }
                    }else{
                        for(extra in commandExtraInputs){
                            resultString = resultString.replace("{${extra.name}}", extra.value)
                        }
                    }

                    Timber.d("Replaced String $resultString")


                    val fullExecPath =
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                    val success = ProcessManagerService.runCommandForShare(fullExecPath, resultString, item.path)

                    if (success) {
                        Timber.d("Process Success".uppercase())
                        autoPieNotification.sendNotification("Command Success", "${item.name} $fileUris")

                    } else {
                        Timber.d("Process FAILED".uppercase())
                        autoPieNotification.sendNotification("Command Failed", "${item.name} $fileUris")
                    }
                }

                main.dispatchEvent(ViewModelEvent.CommandCompleted)


                //main.dispatchEvent(ViewModelEvent.CloseShareReceiverSheet)

            }


        }

    }

    private fun runShareCommandForDirectory(
        item: CommandModel,
        inputDir: File,
        commandExtraInputs: List<CommandExtraInput> = emptyList()
    ) {


        Timber.d("runShareCommandForDirectory")


        val currentItems = inputDir.listFiles()!!

        viewModelScope.launch(Dispatchers.IO) {

            val startTime = System.currentTimeMillis()


            currentItems.map { path ->
                var resultString = "\"${item.command.replace("{INPUT_FILE}", path.absolutePath)}\""

                if(commandExtraInputs.isEmpty()){
                    for(extra in item.extras ?: emptyList()){
                        resultString = resultString.replace("{${extra.name}}", extra.default)
                    }
                }else{
                    for(extra in commandExtraInputs){
                        resultString = resultString.replace("{${extra.name}}", extra.value)
                    }
                }

                val fullExecPath =
                    Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                val success = ProcessManagerService.runCommandForShare(fullExecPath, resultString, item.path)

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

            main.dispatchEvent(ViewModelEvent.CommandCompleted)

            val endTime = System.currentTimeMillis()

            Timber.d("Time Elapsed: ${endTime - startTime}")


        }

    }


}