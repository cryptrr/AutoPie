package com.autosec.pie.use_case


import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber


class GetLatestUsedPackages(private val dbService: AppDatabase){
    operator fun invoke(count: Int) : Flow<List<String>> {

        return dbService.commandHistoryDao().getLatestUsedCommands(count).map {
            //Timber.d("Latest used commands: ${it}")
            it.map { it.command.lines().first { !it.startsWith("#") }.split(" ").first() }
        }

    }

}