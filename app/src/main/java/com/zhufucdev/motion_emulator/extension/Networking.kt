package com.zhufucdev.motion_emulator.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json

val defaultKtorClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json()
    }
    install(HttpTimeout) {
        val timeout = 10000L
        connectTimeoutMillis = timeout
        socketTimeoutMillis = timeout
        requestTimeoutMillis = timeout
    }

    defaultRequest {
        accept(ContentType.Application.Json)
    }
}
