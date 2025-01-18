package com.autosec.pie.use_case

import com.autosec.pie.data.CommandCreationModel
import com.autosec.pie.data.CommandModel
import com.autosec.pie.domain.ViewModelError
import com.autosec.pie.services.JsonService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import timber.log.Timber

class CreateCommand(private val jsonService: JsonService) {
    suspend operator fun invoke(newCommand: CommandCreationModel) {
        Timber.tag("ThreadCheck").d("Running on: ${Thread.currentThread().name}")

        val shareCommands = jsonService.readSharesConfig()
        val observerCommands = jsonService.readObserversConfig()
        val cronCommands = jsonService.readCronConfig()

        if (shareCommands == null) throw ViewModelError.ShareConfigUnavailable
        if (observerCommands == null) throw ViewModelError.ObserverConfigUnavailable
        if (cronCommands == null) throw ViewModelError.CronConfigUnavailable


        val commandObject = JsonObject()


        commandObject.addProperty("path", newCommand.directory)
        commandObject.addProperty("exec", newCommand.exec)
        commandObject.addProperty("command", newCommand.command)
        commandObject.addProperty("deleteSourceFile", newCommand.deleteSourceFile)

        val selectorsJson = if(newCommand.selectors.isNotBlank()){
            val jsonArray = JsonArray()

            newCommand.selectors.split(",").map { string ->
                jsonArray.add(string)
            }

            jsonArray

        }else{
            JsonArray()
        }

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        when (newCommand.selectedCommandType) {
            "SHARE" -> {
                if(newCommand.commandExtras.isNotEmpty()){
                    commandObject.add("extras", Gson().toJsonTree(newCommand.commandExtras))
                }else{
                    commandObject.remove("extras")
                }

                shareCommands.add(newCommand.commandName, commandObject)
            }
            "FILE_OBSERVER" -> {

                if(newCommand.commandExtras.isNotEmpty()){
                    commandObject.add("extras", Gson().toJsonTree(newCommand.commandExtras))
                }else{
                    commandObject.remove("extras")
                }

                commandObject.add("selectors", selectorsJson)

                observerCommands.add(newCommand.commandName, commandObject)
            }
            "CRON" -> {

                if(newCommand.commandExtras.isNotEmpty()){
                    commandObject.add("extras", Gson().toJsonTree(newCommand.commandExtras))
                }else{
                    commandObject.remove("extras")
                }

                commandObject.addProperty("cronInterval", newCommand.cronInterval)

                cronCommands.add(newCommand.commandName, commandObject)
            }
        }

        when (newCommand.selectedCommandType) {
            "SHARE" -> {

                val modifiedJsonContent = gson.toJson(shareCommands)

                jsonService.writeSharesConfig(modifiedJsonContent)
            }
            "FILE_OBSERVER" -> {
                val modifiedJsonContent = gson.toJson(observerCommands)

                jsonService.writeObserversConfig(modifiedJsonContent)
            }
            "CRON" -> {
                val modifiedJsonContent = gson.toJson(cronCommands)

                jsonService.writeCronConfig(modifiedJsonContent)
            }
        }
    }
}