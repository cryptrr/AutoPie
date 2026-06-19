package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandHistoryEntity
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.UserTagEntity
import com.autopi.autopieapp.data.dbService.AppDatabase
import com.autopi.autopieapp.data.toEntity
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