package com.autosec.pie.core

import com.autosec.pie.autopieapp.data.apiService.RequestError
import com.autosec.pie.autopieapp.domain.Notification
import com.autosec.pie.autopieapp.domain.ViewModelError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    //data class Error(val exception: Throwable? = null) : Result<Nothing>
    data class Error(val exception: Notification? = null) : Result<Nothing>
    object Loading : Result<Nothing>
    object None : Result<Nothing>


    //object Empty: Result<Nothing>
}
/**
 *  Converts Flow<T> into Flow<Result<T>>
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> {
    return this
        .map<T, Result<T>> {
            Result.Success(it)
        }

        .onStart { emit(Result.Loading) }

        .catch {
            when(it){
                is RequestError.Timeout -> emit(Result.Error(ViewModelError.Timeout))
                is RequestError.Conflict -> emit(Result.Error(ViewModelError.Conflict))
                is RequestError.Forbidden ->  emit(Result.Error(ViewModelError.UserForbidden))
                is RequestError.Unauthorized ->  emit(Result.Error(ViewModelError.Unauthorized))
                else -> emit(Result.Error(ViewModelError.NetworkError))
            }
        }

}