package com.jillesvangurp.ktsearch

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*

actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?
): HttpClient {
    return HttpClient(Js) {
        if (logging) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }
        if (!user.isNullOrBlank() && !password.isNullOrBlank()) {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(user,password)
                    }
                }
            }
        }
        install(ContentNegotiation) {
            json()
        }
    }
}