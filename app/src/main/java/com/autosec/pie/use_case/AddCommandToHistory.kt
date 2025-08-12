package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import com.autosec.pie.autopieapp.data.toEntity
import timber.log.Timber
import java.time.Instant

class AddCommandToHistory(private val dbService: AppDatabase){
    operator fun invoke(command: CommandModel,currentLink: String?, fileUris: List<String>?, commandExtraInputs: List<CommandExtraInput> = emptyList(),success: Boolean, processId: Int) : Boolean {

        val commandHistoryEntity = CommandHistoryEntity(
            id = Instant.now().toString(),
            commandModelId = command.name,
            commandExtraInputs = commandExtraInputs.map { it.toEntity() },
            currentLink = currentLink,
            fileUris = fileUris,
            processId = processId,
            success = success,
            exec = command.exec
        )

        dbService.commandHistoryDao().insertAll(commandHistoryEntity)

        Timber.d("Added the command to history")

        val allHistoryOfCommand = dbService.commandHistoryDao().getAllWithName(command.name)

        if(allHistoryOfCommand.size > 10){
            Timber.d("Deleting the oldest entry since entries > 10")
            dbService.commandHistoryDao().delete(allHistoryOfCommand.last())
        }

        return true

    }
}