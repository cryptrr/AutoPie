package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.dbService.AppDatabase

class GetUserTags(private val dbService: AppDatabase){
    operator fun invoke() : List<String> {

        val tags = dbService.userTagsDao().getAll().map{it.tag}

        return tags

    }

}