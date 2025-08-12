package com.autosec.pie.use_case


import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import kotlinx.coroutines.flow.Flow


class GetLatestUsedPackages(private val dbService: AppDatabase){
    operator fun invoke(count: Int) : Flow<List<String>> {

        return dbService.commandHistoryDao().getLatestUsedPackages(count)

    }

}