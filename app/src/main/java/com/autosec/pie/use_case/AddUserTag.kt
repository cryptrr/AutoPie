package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.UserTagEntity
import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import com.autosec.pie.autopieapp.data.toEntity
import timber.log.Timber
import java.time.Instant

class AddUserTag(private val dbService: AppDatabase){
    operator fun invoke(tag: String) : Boolean {

        val userTag = UserTagEntity(
            id = Instant.now().toString(),
            tag = tag.trim(),
        )

        dbService.userTagsDao().insertAll(listOf(userTag))

        Timber.d("Added user tag")

        return true

    }
}