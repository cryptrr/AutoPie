package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class GetUserTags(private val dbService: AppDatabase){
    operator fun invoke() : Flow<List<String>> {
        val tags = dbService.userTagsDao().getAll().map { it.map { item -> item.tag } }

        return tags
    }
}