package com.autosec.pie.autopieapp.data.apiService


import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.*
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.*
import io.ktor.serialization.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber


interface HTTPClientService {
    val client: HttpClient
    fun handleResponse(response: HttpResponse)
}

//TODO: Be extra careful about error handling here.
suspend inline fun <reified T : Any> HTTPClientService.sendRequest(
    endpoint: BaseRouter
): T {

    try {
        Timber.d("ENDPOINT: ${endpoint.url} METHOD: ${endpoint.method} QUERY: ${endpoint.parameters}")
        Timber.d(endpoint.token)
        when (endpoint.method) {
            "POST" -> {

                if(endpoint.fileData == null){
                    val response = this.client.post(endpoint.url) {
                        contentType(ContentType.Application.Json)
                        setBody(endpoint.httpBody)
                        url {
                            for (param in endpoint.parameters ?: emptyList()) {
                                parameters.append(param.first, param.second)
                            }
                        }
                        if(endpoint.token.count() > 15){
                            header(HttpHeaders.Authorization, endpoint.token)
                        }
                    }

                    handleResponse(response)
                    Timber.d("RESPONSE: ${response.bodyAsText()}")

                    val data = response.body<T>()
                    return data
                }else{
                    val response = this.client.submitFormWithBinaryData(
                        url = endpoint.url,
                        formData = formData {
                            endpoint.fileData?.let {
                                append("tags", it.tags)
                                append("description", it.description)
                                append("isPrivate", it.isPrivate)

                                Timber.d("IsPrivate ${it.isPrivate}")
                                //append("collectionId", it.collectionId)
                                append("file", it.binaryData, Headers.build {
                                    append(HttpHeaders.ContentType, it.contentType)
                                    append(HttpHeaders.ContentDisposition, "filename=${it.filename}")
                                })
                            }
                        }
                    ){
                        if(endpoint.token.count() > 15){
                            header(HttpHeaders.Authorization, endpoint.token)
                        }
                        onUpload { bytesSentTotal, contentLength ->
                            Timber.d ( "onUpload function" )
                            Timber.d ( "Content Length: $contentLength" )
                            Timber.d ( "Bytes Sent : $bytesSentTotal" )
                        }
                    }

                    handleResponse(response)
                    Timber.d("RESPONSE: ${response.bodyAsText()}")

                    val data = response.body<T>()
                    return data

                }

            }

            "GET" -> {
                Timber.d(endpoint.toString())
                val response = this.client.get(endpoint.url) {
                    url {
                        for (param in endpoint.parameters ?: emptyList()) {
                            parameters.append(param.first, param.second)
                        }
                    }
                    if(endpoint.token.count() > 15){
                        header(HttpHeaders.Authorization, endpoint.token)
                    }
                }
                handleResponse(response)
                Timber.d("RESPONSE: ${response.bodyAsText()}")
                //Timber.d{"RESPONSE TIME: ${response.responseTime}"}
                Timber.d("HEADERS: ${response.headers["Cache-Control"]}")

                val data = response.body<T>()
                return data
            }

            "DELETE" -> {
                Timber.d("DELETE METHOD")
                val response = this.client.delete(endpoint.url) {
                    url {
                        for (param in endpoint.parameters ?: emptyList()) {
                            parameters.append(param.first, param.second)
                        }
                    }
                    if(endpoint.token.count() > 15){
                        header(HttpHeaders.Authorization, endpoint.token)
                    }

                }
                handleResponse(response)
                val data = response.body<T>()
                return data
            }
            "PATCH" -> {
                Timber.d("PATCH METHOD")
                Timber.d(endpoint.httpBody.toString())
                val response = this.client.patch(endpoint.url) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(endpoint.httpBody)
                    url {
                        for (param in endpoint.parameters ?: emptyList()) {
                            parameters.append(param.first, param.second)
                        }
                    }
                    if(endpoint.token.count() > 15){
                        header(HttpHeaders.Authorization, endpoint.token)
                    }

                }
                handleResponse(response)
                val data = response.body<T>()
                return data
            }
            else -> {
                Timber.e("ILLEGAL STATE: ELSE IN HTTP_REQUEST")
                val response = this.client.get(endpoint.url) {
                    url {
                        for (param in endpoint.parameters ?: emptyList()) {
                            parameters.append(param.first, param.second)
                        }
                    }
                    if(endpoint.token.count() > 15){
                        header(HttpHeaders.Authorization, endpoint.token)
                    }

                }
                handleResponse(response)
                val data = response.body<T>()
                return data
            }
        }
    } catch (e: Exception) {
        Timber.e ( e.toString() )
        when (e) {
            is NoTransformationFoundException -> throw RequestError.Decode
            //is RedirectResponseException -> throw data.apiService.RequestError.NoResponse
            //is UnknownHostException -> throw data.apiService.RequestError.CannotConnectToHost
            else -> throw e
        }

    }
}

//suspend inline fun <reified T : Any> HTTPClientService.sendWSSRequest(
//    endpoint: Router
//): Flow<T> = flow {
//
//    try {
//        this@sendWSSRequest.client.webSocket(endpoint.url) {
//            val data = receiveDeserialized<T>()
//
//
//            this@flow.emit(data)
//        }
//    } catch (e: Exception) {
//        print(e.message)
//        when (e) {
//            is WebsocketDeserializeException -> throw RequestError.Decode
//            //is UnknownHostException -> throw data.apiService.RequestError.CannotConnectToHost
//            else -> throw e
//        }
//
//    }
//
//}





class AutoSecHTTPClient() : HTTPClientService, KoinComponent {
    override var client: HttpClient = Ktor().client

    val main by inject<MainViewModel>()

    private var unauthorizedErrorCount = 0

    override fun handleResponse(response: HttpResponse) {

        Timber.d(response.status.toString())


        when (response.status) {
            HttpStatusCode.NotFound -> throw RequestError.PageNotFound
            HttpStatusCode.RequestTimeout -> throw RequestError.Timeout
            HttpStatusCode.Unauthorized -> {
                //Refresh token
                main.dispatchEvent(ViewModelEvent.AuthTokenExpired)

                unauthorizedErrorCount+=1
                Timber.d ( "Token Bug: unauthorizedErrorCount : $unauthorizedErrorCount" )

//                if(unauthorizedErrorCount >= 12){
//                    Timber.d{"Token Bug: Logging out due to Auth Token Issue"}
//                    main.logout()
//                    unauthorizedErrorCount = 0
//                }

                throw RequestError.Unauthorized
            }
            HttpStatusCode.BadRequest -> throw RequestError.BadRequest
            HttpStatusCode.Forbidden -> throw RequestError.Forbidden
            HttpStatusCode.Conflict -> throw RequestError.Conflict
            else -> {
                when {
                    response.status.value.toString()
                        .startsWith("4") -> throw RequestError.UnknownClientError

                    response.status.value.toString()
                        .startsWith("5") -> throw RequestError.UnknownServerError
                    //else -> throw data.apiService.RequestError.Unknown
                }
            }
        }
    }
}