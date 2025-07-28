package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class GetHistoryOfCommand(private val dbService: AppDatabase){
    operator fun invoke(commandName: String) : Flow<List<CommandHistoryEntity>> {

        return flow {
            val history = dbService.commandHistoryDao().getAllWithName(commandName)

            emit(history)
        }

    }
}