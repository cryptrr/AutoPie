package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandHistoryEntity
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.dbService.AppDatabase
import com.autopi.autopieapp.data.toEntity
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
            command = command.command,
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