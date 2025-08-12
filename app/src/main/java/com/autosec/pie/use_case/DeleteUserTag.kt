package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.UserTagEntity
import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import timber.log.Timber
import java.time.Instant

class DeleteUserTag(private val dbService: AppDatabase){
    operator fun invoke(tag: String) : Boolean {

        //TODO: Delete user tags

        val userTag = UserTagEntity(
            id = Instant.now().toString(),
            tag = tag,
        )

        dbService.userTagsDao().delete(userTag)

        Timber.d("Deleted user tag")

        return true

    }
}