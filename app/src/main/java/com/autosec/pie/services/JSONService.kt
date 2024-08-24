package com.autosec.pie.services

import android.os.Environment
import com.autosec.pie.domain.ViewModelError
import com.autosec.pie.viewModels.MainViewModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class JSONService {
    companion object{

        private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

        fun readSharesConfig(): JsonObject? {
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
                    mainViewModel.showError(ViewModelError.InvalidJson("Share"))
                }
                return dataObject.asJsonObject
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun readObserversConfig(): JsonObject? {

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
                    mainViewModel.showError(ViewModelError.InvalidJson("Observers"))
                }
                return dataObject.asJsonObject
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }


        fun writeSharesConfig(jsonString: String) {

            val sharesFilePath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/shares.json"

            try {
                val file = File(sharesFilePath)
                file.writeText(jsonString)

            } catch (e: Exception) {
                e.printStackTrace()
                return
            }
        }

        fun writeObserversConfig(jsonString: String) {
            val fileObserverPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/observers.json"

            try {
                val file = File(fileObserverPath)
                file.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }
        }


    }
}