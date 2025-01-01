package com.autosec.pie.data.apiService

import com.autosec.pie.data.preferences.AppPreferences
import com.autosec.pie.data.preferences.MyPreferences
import com.autosec.pie.domain.model.FileUploadGenericDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.Clock

sealed class MainRouter(): BaseRouter  {

    data class Login(val username: String, val password: String, val sessionOnly: Boolean) :
        MainRouter()

    data class ReAuthenticate(val refreshToken: String) : MainRouter()

    data class CreateUser(
        val userId: String,
        val username: String,
        val password: String,
        val repeatPassword: String,
        val name: String,
        val locale: Int
    ) : MainRouter()

    data object GetCloudCommandsList : MainRouter()
    data class SearchCloudCommands(val query: String) : MainRouter()
    data class MoreSearchCloudCommands(val query: String, val cursor: String) : MainRouter()


    data class GetMoreCloudCommandsList(val cursor: String) : MainRouter()

    data class GetPackages(val query: String) : MainRouter()


    data class GetMorePackages(val query: String, val cursor: String) : MainRouter()




    override val url: String
        get() = when (this) {
            else -> {
                "$scheme://$host$path"
            }
        }
    override val scheme: String
        get() = when (this) {
            else -> MAIN_API.scheme
        }
    override val host: String get() = MAIN_API.URL
    val rootHost: String get() = MAIN_API.hostUrl

    override val token: String get() = MAIN_API.token


    override val path: String
        get() = when (this) {
            is Login -> "/authenticate"
            is ReAuthenticate -> "/authenticate"
            is GetCloudCommandsList -> "/command/list"
            is GetMoreCloudCommandsList -> "/command/list/more"
            is SearchCloudCommands -> "/command/search"
            is MoreSearchCloudCommands -> "/command/search/more"
            is GetPackages -> "/package/list"
            is GetMorePackages -> "/package/list/more"
            else -> "/"
        }

    override val method: String
        get() = when (this) {
            is Login -> "POST"
            is ReAuthenticate -> "PATCH"
            else -> "GET"
        }

    override val parameters: List<Pair<String, String>>?
        get() = when (this) {
            is SearchCloudCommands -> listOf(
                Pair("query", this.query),
            )
            is MoreSearchCloudCommands -> listOf(
                Pair("query", this.query),
                Pair("cursor", this.cursor),
            )
            is GetPackages  -> listOf(
                Pair("query", this.query),
            )

            is GetMorePackages -> listOf(
                Pair("query", this.query),
                Pair("cursor", this.cursor),
            )

            else -> null
        }


    override val httpBody: Any?
        get() = when (this) {

            else -> null
        }

    override val fileData: FileUploadGenericDto?
        get() = when (this) {
            else -> null
        }
}

object MAIN_API : KoinComponent {
    //static var hash: String { "\(timeStamp)\(privateKey)\(publicKey)".md5 }

    //FIXME: This doesn't change when user changes. Hence request will go to earlier user.
    private val settings: AppPreferences by inject()

    private val access_token = settings.getStringSync(AppPreferences.ACCESS_TOKEN)

    var token: String = "Bearer $access_token"
        get() {
            Timber.d ( "Token Present" )
            return field
        }
        set(token) {
            field = "Bearer $token"
        }

    val privateKey: String get() = "Enter your PRIVATE KEY"
    val timeStamp: String get() = Clock.systemUTC().toString()

    val scheme = "http"

    val schemeURL: String get() = "$scheme://$URL"

    //CHANGE THIS TO CHANGE BACKEND ENDPOINT
    val URL: String get() = "192.168.1.154:3333"


    val hostUrl = "192.68.1.154:3333"

    fun clearUserData() {
        token = ""
    }

    fun setNewAccessToken(accToken: String) {
        token = accToken
    }

}

interface BaseRouter{
    val url : String
    val scheme : String
    val host : String
    val token : String
    val path : String
    val method : String
    val parameters : List<Pair<String, String>>?
    val httpBody : Any?
    val fileData: FileUploadGenericDto?
}