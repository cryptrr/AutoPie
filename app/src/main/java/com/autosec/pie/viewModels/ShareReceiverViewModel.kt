package com.autosec.pie.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.ShareItemModel
import com.autosec.pie.services.ProcessManagerService
import com.autosec.pie.utils.isValidUrl
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class ShareReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    var shareItemsResult by mutableStateOf<List<ShareItemModel>>(emptyList())
    var filteredShareItemsResult by mutableStateOf<List<ShareItemModel>>(emptyList())

    var searchQuery = mutableStateOf("")

    init {
        getSharesConfig()
    }

    fun search(query: String){

        filteredShareItemsResult = shareItemsResult.filter { it.name.contains(query, ignoreCase = true) || it.command.contains(query, ignoreCase = true) || it.exec.contains(query, ignoreCase = true) }

    }


    fun getSharesConfig(){
        val observerConfig = readSharesConfig()

        if(observerConfig == null){
            Timber.d("Observers file not available")
            mainViewModel.sharesConfigAvailable = false
            return
        }else{
            mainViewModel.schedulerConfigAvailable = true
        }

        val tempList = mutableListOf<ShareItemModel>()

        for (entry in observerConfig.entrySet()) {
            val key = entry.key
            val value = entry.value.asJsonObject
            // Process the key-value pair
            println("Key: $key, Value: $value")

            val directoryPath = value.get("path").asString
            val exec = value.get("exec").asString
            val command = value.get("command").asString
            val deleteSource = value.get("deleteSourceFile").asBoolean

            val shareObject = ShareItemModel(
                name = key,
                path = directoryPath,
                command = command,
                exec = exec,
                deleteSourceFile = deleteSource
            )

            tempList.add(shareObject)
        }

        shareItemsResult = tempList
        filteredShareItemsResult = tempList
    }

    private fun readSharesConfig(): JsonObject? {

        val sharesFilePath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/shares.json"


        try {
            val file = File(sharesFilePath)
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

        val fileObserverPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/observers.json"

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


    fun runShareCommand(item: ShareItemModel, currentLink: String?, fileUris: List<String>){

        Timber.d(item.toString())
        Timber.d(currentLink.toString())

        println(item.toString())
        println(fileUris.toString())

        when{
            fileUris.isNotEmpty() -> {
                runShareCommandForFiles(item, currentLink, fileUris)
            }

            currentLink.isValidUrl() -> {
                runShareCommandForUrl(item, currentLink!!, fileUris)
            }
            !currentLink.isNullOrBlank() -> {

            }

            else -> {}
        }


    }


    private fun runShareCommandForUrl(item: ShareItemModel, currentLink: String, fileUris: List<String>){

        println("runShareCommandForUrl")


        val resultString = "\"${item.command.replace("{INPUT_FILE}", currentLink)}\""

        Timber.d("Command to run: ${item.exec} $resultString")


        viewModelScope.launch {
            ProcessManagerService.runCommandForShare(item.exec, item.command , item.path)
        }

    }

    private fun runShareCommandForFiles(
        item: ShareItemModel,
        currentLink: String?,
        fileUris: List<String>
    ){

        println("runShareCommandForFiles")

        val currentItems = fileUris

        viewModelScope.launch {

            currentItems.map { path  ->
                val resultString = "\"${item.command.replace("{INPUT_FILE}", path)}\""

                ProcessManagerService.runCommandForShare(item.exec, resultString , item.path)
            }
        }

    }




}