package com.autopi.use_case


import com.autopi.autopieapp.data.dbService.AppDatabase
import com.autopi.utils.getCommandExec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber


class GetLatestUsedPackages(private val dbService: AppDatabase){
    operator fun invoke(count: Int) : Flow<List<String>> {

        return dbService.commandHistoryDao().getLatestUsedCommands(count).map {
            //Timber.d("Latest used commands: ${it}")
            it.mapNotNull { it.exec.ifEmpty { getCommandExec(it.command) } }
        }

    }

}