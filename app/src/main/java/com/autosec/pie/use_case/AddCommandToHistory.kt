package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import com.autosec.pie.autopieapp.data.toEntity
import timber.log.Timber
import java.time.Instant

class AddCommandToHistory(private val dbService: AppDatabase){
    operator fun invoke(item: CommandModel,currentLink: String?, fileUris: List<String>?, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Boolean {

        val commandHistoryEntity = CommandHistoryEntity(
            id = Instant.now().toString(),
            commandModelId = item.name,
            commandExtraInputs = commandExtraInputs.map { it.toEntity() },
            currentLink = currentLink,
            fileUris = fileUris,
            processId = processId
        )

        dbService.commandHistoryDao().insertAll(commandHistoryEntity)

        Timber.d("Added the command to history")

        return true

    }

}