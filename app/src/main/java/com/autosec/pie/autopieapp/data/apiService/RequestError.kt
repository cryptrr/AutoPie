package com.autosec.pie.autopieapp.data.apiService

sealed class RequestError : Exception() {
    object Decode : RequestError()
    object InvalidURL : RequestError()
    object NoResponse : RequestError()
    object Unauthorized : RequestError()
    object Offline : RequestError()
    object PageNotFound : RequestError()
    object CannotConnectToHost : RequestError()
    object NoSecureConnection : RequestError()
    object BadRequest : RequestError()
    object Timeout : RequestError()
    object Unknown : RequestError()
    object Forbidden : RequestError()

    object Conflict : RequestError()


    object UnknownClientError : RequestError()
    object UnknownServerError : RequestError()



    val customMessage: String
        get() = when(this) {
            is Decode -> "Decode Error"
            is Unauthorized -> "Session expired"
            else -> "Unknown error"
        }
}