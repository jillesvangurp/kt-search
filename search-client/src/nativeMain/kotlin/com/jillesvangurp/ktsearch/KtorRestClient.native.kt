package com.jillesvangurp.ktsearch

import com.jillesvangurp.serializationext.DEFAULT_JSON
import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?
): HttpClient {
    return HttpClient(Curl) {
        engine {
            pipelining = true
        }
        if(!user.isNullOrBlank() && !password.isNullOrBlank()) {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(user, password)
                    }
                    sendWithoutRequest {
                        true
                    }
                }
            }
        }
        if(!elasticApiKey.isNullOrBlank()) {
            headers {
                append("Authorization", "ApiKey $elasticApiKey")
            }
        }
        install(ContentNegotiation) {
            json(DEFAULT_JSON)
        }
        if (logging) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }

    }
}