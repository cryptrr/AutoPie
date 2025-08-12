package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.dbService.AppDatabase
import com.autosec.pie.autopieapp.domain.ViewModelError
import timber.log.Timber

class DeleteUserTag(private val dbService: AppDatabase){
    operator fun invoke(name: String) : Boolean {

        val tag = dbService.userTagsDao().getTagByName(name)

        if(tag != null){
            dbService.userTagsDao().delete(tag)
            Timber.d("Deleted user tag: $name")
        }else{
            Timber.d("Tag: $name cannot be deleted because it is not a user defined tag.")
            throw ViewModelError.TagNotDeletable
        }

        return true

    }
}