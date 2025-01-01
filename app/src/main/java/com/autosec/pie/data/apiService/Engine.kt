package com.autosec.pie.data.apiService

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json


class Ktor {
    val client = HttpClient(CIO){
        defaultRequest {
            url {
                protocol = URLProtocol.HTTP
            }
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                isLenient = false
            })
        }

//        install(HttpTimeout) {
//            val timeout = 20000L
//            connectTimeoutMillis = timeout
//            requestTimeoutMillis = timeout
//            socketTimeoutMillis = timeout
//        }
    }
}

