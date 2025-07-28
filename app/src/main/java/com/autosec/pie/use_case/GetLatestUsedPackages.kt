package com.autosec.pie.use_case


import com.autosec.pie.autopieapp.data.dbService.AppDatabase


class GetLatestUsedPackages(private val dbService: AppDatabase){
    operator fun invoke(count: Int) : List<String> {

        val packages = dbService.commandHistoryDao().getLatestUsedPackages(count)

        return packages

    }

}