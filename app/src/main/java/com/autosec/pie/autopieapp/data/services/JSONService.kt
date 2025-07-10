package com.autosec.pie.autopieapp.data.services

import android.os.Environment
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

interface JsonService {
    fun readSharesConfig(): JsonObject?
    fun readObserversConfig(): JsonObject?
    fun readCronConfig(): JsonObject?
    fun writeSharesConfig(jsonString: String)
    fun writeObserversConfig(jsonString: String)
    fun writeCronConfig(jsonString: String)
}

class JSONServiceImpl : JsonService {



    override fun readSharesConfig(): JsonObject? {


        val sharesFilePath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/shares.json"


        try {
            val file = File(sharesFilePath)
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
                Timber.d("Share Sheet config not available")
                throw ViewModelError.ShareConfigUnavailable
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Share Sheet config is not valid json")
                throw ViewModelError.InvalidShareConfig
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    override fun readObserversConfig(): JsonObject? {


        val fileObserverPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/observers.json"

        try {
            val file = File(fileObserverPath)
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
                Timber.d("Observer Sheet config not available")
                throw ViewModelError.ObserverConfigUnavailable
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Observers config is not valid json")
                throw ViewModelError.InvalidObserverConfig
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    override fun readCronConfig(): JsonObject? {


        val cronConfigPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/cron.json"

        try {
            val file = File(cronConfigPath)
            val inputStream = FileInputStream(file)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer)

            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if(dataObject == null) {
                Timber.d("Cron Sheet config not available")
                throw ViewModelError.CronConfigUnavailable
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Cron config is not valid json")
                throw ViewModelError.InvalidCronConfig
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }


    override fun writeSharesConfig(jsonString: String) {

        val sharesFilePath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/shares.json"

        try {
            val file = File(sharesFilePath)
            file.writeText(jsonString)

        } catch (e: Exception) {
            Timber.e(e)
            return
        }
    }

    override fun writeObserversConfig(jsonString: String) {
        val fileObserverPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/observers.json"

        try {
            val file = File(fileObserverPath)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Timber.e(e)
            return
        }
    }

    override fun writeCronConfig(jsonString: String) {
        val fileObserverPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/cron.json"

        try {
            val file = File(fileObserverPath)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Timber.e(e)
            return
        }
    }

}

