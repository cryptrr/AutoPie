package com.autosec.pie.use_case

import android.os.Environment
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.data.services.JsonService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Flow

class GetCommandsList(private val jsonService: JsonService) {
    suspend operator fun invoke(): List<CommandModel>{
        val sharesConfig = jsonService.readSharesConfig()
        val observersConfig = jsonService.readObserversConfig()
        val cronConfig = jsonService.readCronConfig()

        if(sharesConfig == null){
            Timber.d("Shares file not available")
            throw ViewModelError.ShareConfigUnavailable
        }

        if(observersConfig == null){
            Timber.d("Observers file not available")
            throw ViewModelError.ObserverConfigUnavailable
        }
        if(cronConfig == null){
            Timber.d("Cron file not available")
            throw ViewModelError.CronConfigUnavailable
        }

        val tempList = mutableListOf<CommandModel>()

        for (entry in sharesConfig.entrySet()) {
            val key = entry.key
            val value = entry.value.asJsonObject
            // Process the key-value pair

            val directoryPath = "${Environment.getExternalStorageDirectory().absolutePath}/" + value.get("path").asString
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
                name = key,
                path = directoryPath,
                command = command,
                exec = exec,
                deleteSourceFile = deleteSource,
                type = CommandType.SHARE,
                extras = extras
            )

            tempList.add(shareObject)
        }

        for (entry in observersConfig.entrySet()) {
            val key = entry.key
            val value = entry.value.asJsonObject
            // Process the key-value pair

            val directoryPath = "${Environment.getExternalStorageDirectory().absolutePath}/" + value.get("path").asString
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
                name = key,
                path = directoryPath,
                command = command,
                exec = exec,
                deleteSourceFile = deleteSource,
                type = CommandType.FILE_OBSERVER,
                extras = extras
            )

            tempList.add(shareObject)
        }

        for (entry in cronConfig.entrySet()) {
            val key = entry.key
            val value = entry.value.asJsonObject
            // Process the key-value pair

            val directoryPath = "${Environment.getExternalStorageDirectory().absolutePath}/" + value.get("path").asString
            val exec = value.get("exec").asString
            val command = value.get("command").asString
            val deleteSource = value.get("deleteSourceFile").asBoolean

            val cronObject = CommandModel(
                name = key,
                path = directoryPath,
                command = command,
                exec = exec,
                deleteSourceFile = deleteSource,
                type = CommandType.CRON
            )

            tempList.add(cronObject)
        }


        return tempList
    }
}