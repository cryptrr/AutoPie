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

interface JsonService {
    fun readSharesConfig(): JsonObject?
    fun readObserversConfig(): JsonObject?
    fun readCronConfig(): JsonObject?
    fun writeSharesConfig(jsonString: String)
    fun writeObserversConfig(jsonString: String)
    fun writeCronConfig(jsonString: String)
}

class JSONServiceImpl : JsonService {



    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    override fun readSharesConfig(): JsonObject? {

        if(!mainViewModel.storageManagerPermissionGranted){
            return null
        }

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

            if (!dataObject.isJsonObject) {
                Timber.d("Share Sheet config is not valid json")
                mainViewModel.showError(ViewModelError.InvalidJson("Share"))
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            mainViewModel.showError(ViewModelError.InvalidJson("Share"))
            return null
        }
    }

    override fun readObserversConfig(): JsonObject? {

        if(!mainViewModel.storageManagerPermissionGranted){
            return null
        }

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

            if (!dataObject.isJsonObject) {
                Timber.d("Observers config is not valid json")
                mainViewModel.showError(ViewModelError.InvalidJson("Observers"))
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            mainViewModel.showError(ViewModelError.InvalidJson("Observers"))
            return null
        }
    }

    override fun readCronConfig(): JsonObject? {

        if(!mainViewModel.storageManagerPermissionGranted){
            //TODO: Send notification maybe
            return null
        }

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

            if (!dataObject.isJsonObject) {
                Timber.d("Cron config is not valid json")
                mainViewModel.showError(ViewModelError.InvalidJson("Cron"))
            }
            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            mainViewModel.showError(ViewModelError.InvalidJson("Cron"))
            return null
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

class FakeJSONService : JsonService {
    private var inMemoryStorage = mutableMapOf<String, String>()
    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    // Constants
    private val SHARES_KEY = "shares.json"
    private val OBSERVERS_KEY = "observers.json"
    private val CRON_KEY = "cron.json"

    init {
        inMemoryStorage[SHARES_KEY] = """       
                {
                    "Extract Audio": {
                        "path": "",
                        "exec": "ffmpeg",
                        "command": "",
                        "deleteSourceFile": false,
                        "extras": [
                            {
                                "default": "195K",
                                "defaultBoolean": true,
                                "description": "A value from 56K to 320K.\nLarger means better quality.",
                                "id": "715336",
                                "name": "BITRATE",
                                "selectableOptions": [
                                    ""
                                ],
                                "type": "STRING"
                            }
                        ]
                    },
                    "RSYNC Sync Folder": {
                        "path": "",
                        "exec": "ffmpeg",
                        "command": "",
                        "deleteSourceFile": false,
                        "extras": [
                            {
                                "default": "195K",
                                "defaultBoolean": true,
                                "description": "A value from 56K to 320K.\nLarger means better quality.",
                                "id": "715336",
                                "name": "BITRATE",
                                "selectableOptions": [
                                    ""
                                ],
                                "type": "STRING"
                            }
                        ]
                    }
                }
            """.trimIndent()
        inMemoryStorage[OBSERVERS_KEY] = """       
                {}
            """.trimIndent()

        inMemoryStorage[CRON_KEY] = """       
                {}
            """.trimIndent()
    }

    override fun readSharesConfig(): JsonObject? {

        mainViewModel.storageManagerPermissionGranted = true

        if (!mainViewModel.storageManagerPermissionGranted) {
            return null
        }

        try {
            val jsonString = inMemoryStorage[SHARES_KEY] ?: return null

            Timber.d("Json str: ${jsonString}")

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if(dataObject == null) {
                Timber.d("Share Sheet config not available")
                return null
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Share Sheet config is not valid json")
                mainViewModel.showError(ViewModelError.InvalidJson("Share"))
                return null
            }

            Timber.d("Json Obj: ${dataObject.asJsonObject}")

            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            mainViewModel.showError(ViewModelError.InvalidJson("Share"))

            return null
        }
    }

    override fun writeSharesConfig(jsonString: String) {
        try {
            inMemoryStorage[SHARES_KEY] = jsonString
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override fun readObserversConfig(): JsonObject? {
        if (!mainViewModel.storageManagerPermissionGranted) {
            return null
        }

        try {
            val jsonString = inMemoryStorage[OBSERVERS_KEY] ?: return null

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if(dataObject == null) {
                Timber.d("Observer Sheet config not available")
                return null
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Observer Sheet config is not valid json")
                mainViewModel.showError(ViewModelError.InvalidJson("Observer"))
                return null
            }

            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            mainViewModel.showError(ViewModelError.InvalidJson("Observer"))

            return null
        }
    }

    override fun writeObserversConfig(jsonString: String) {
        try {
            inMemoryStorage[OBSERVERS_KEY] = jsonString
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override fun readCronConfig(): JsonObject? {
        if (!mainViewModel.storageManagerPermissionGranted) {
            return null
        }

        try {
            val jsonString = inMemoryStorage[CRON_KEY] ?: return null

            // Parse the JSON string
            val gson = Gson()
            val dataObject = gson.fromJson(jsonString, JsonElement::class.java)

            if(dataObject == null) {
                Timber.d("Cron Sheet config not available")
                return null
            }

            if (!dataObject.isJsonObject) {
                Timber.d("Cron Sheet config is not valid json")
                mainViewModel.showError(ViewModelError.InvalidJson("Cron"))
                return null
            }

            return dataObject.asJsonObject
        } catch (e: Exception) {
            Timber.e(e)
            mainViewModel.showError(ViewModelError.InvalidJson("Cron"))

            return null
        }
    }

    override fun writeCronConfig(jsonString: String) {
        try {
            inMemoryStorage[CRON_KEY] = jsonString
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    // Helper method for tests to clear storage
    fun clearStorage() {
        inMemoryStorage.clear()
    }

    // Helper method for tests to get raw storage content
    fun getRawStorageContent(key: String = SHARES_KEY): String? {
        return inMemoryStorage[key]
    }

}