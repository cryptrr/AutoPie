package com.autosec.pie.use_case

import android.os.Environment
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandType
import com.autosec.pie.domain.ViewModelError
import com.autosec.pie.services.JsonService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber

class GetShareCommands(private val jsonService: JsonService) {
    suspend operator fun invoke(): List<CommandModel>{
        val sharesConfig = jsonService.readSharesConfig()

        if (sharesConfig == null) {
            Timber.d("Observers file not available")
            throw ViewModelError.ShareConfigUnavailable
        }

        val tempList = mutableListOf<CommandModel>()

        //TODO: Need to do some refactoring

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
        return tempList
    }
}