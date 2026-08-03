package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.domain.ViewModelError
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

interface JsonService {
    fun readCommandsConfig(): JsonObject?
    fun writeCommandsConfig(jsonString: String)
    fun readRepoList(path: String): JsonObject?

}

class JSONServiceImpl : JsonService {

    private val autoPieConfigPathProvider: AutoPieConfigPathProvider by KoinJavaComponent.inject(
        AutoPieConfigPathProvider::class.java
    )



    override fun readCommandsConfig(): JsonObject? {


        val commandsFilePath = autoPieConfigPathProvider.getConfigFile("commands.json").absolutePath


        try {
            val file = File(commandsFilePath)
            val inputStream = FileInputStream(file)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer)

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if(dataObject == null) {
                Timber.d("Commands config not available")
                throw ViewModelError.CommandConfigUnavailable
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Commands config is not valid json")
                throw ViewModelError.InvalidCommandConfig
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    override fun readRepoList(path: String): JsonObject? {

        try {
            val file = File(path)
            val inputStream = FileInputStream(file)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer)

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if(dataObject == null) {
                Timber.d("Commands Repo config not available")
                throw ViewModelError.CommandRepoUnavailable
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Commands repo config is not valid json")
                throw ViewModelError.InvalidCommandRepoFile
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }


    override fun writeCommandsConfig(jsonString: String) {

        val commandsFilePath = autoPieConfigPathProvider.getConfigFile("commands.json").absolutePath

        try {
            val file = File(commandsFilePath)
            file.writeText(jsonString)

        } catch (e: Exception) {
            Timber.e(e)
            return
        }
    }

}
