package com.jillesvangurp.ktsearch

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*

actual fun defaultHttpClient(logging: Boolean): HttpClient {
    return HttpClient(Js) {
        if(logging) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }
        install(ContentNegotiation) {
            json()
        }
    }
}